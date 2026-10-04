package com.kvieta.companion.connection

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Base64

enum class LinkStatus { IDLE, CONNECTING, WAITING, CONNECTED, OFFLINE, REJECTED, INVALID }
enum class DecisionStatus { IDLE, SENDING, SENT, FAILED }
enum class PairingFailure { NETWORK, CERTIFICATE, REJECTED, OTHER }

internal fun pairingFailure(error: Exception): PairingFailure = when (error) {
    is DesktopRejected -> PairingFailure.REJECTED
    is javax.net.ssl.SSLException -> PairingFailure.CERTIFICATE
    is java.io.IOException -> PairingFailure.NETWORK
    else -> PairingFailure.OTHER
}

class ConnectionModel @JvmOverloads constructor(application: Application,
    private val identity: CompanionIdentity = DeviceIdentity(application),
    private val readRelay: (RelaySettings, Long, (Long) -> Unit) -> DesktopSnapshot = RelayClient::snapshot,
) : AndroidViewModel(application) {
    private val client = DesktopClient(identity, onRemote = identity::saveRemote)
    // Pairing, refresh, decision and forgetting share one operation gate. Refresh never cancels a decision.
    private val operations = Mutex()
    private val initialized = CompletableDeferred<Unit>()
    private var active: DesktopConnection? = null
    private var polling: Job? = null
    private var requestJob: Job? = null
    private var outgoing: PendingDecision? = null
    var input by mutableStateOf(""); private set
    var status by mutableStateOf(LinkStatus.IDLE); private set
    var snapshot by mutableStateOf<DesktopSnapshot?>(null); private set
    var code by mutableStateOf(""); private set
    var saved by mutableStateOf(false); private set
    var ready by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var decisionStatus by mutableStateOf(DecisionStatus.IDLE); private set
    var remoteAvailable by mutableStateOf(false); private set
    var remoteEnabled by mutableStateOf(false); private set
    var replacementInvite by mutableStateOf<String?>(null); private set
    var pairingIssue by mutableStateOf<PairingFailure?>(null); private set
    var pairingAddress by mutableStateOf(""); private set
    var planSaving by mutableStateOf(false); private set
    var planSaved by mutableStateOf(false); private set
    var remoteActionExecuting by mutableStateOf(false); private set
    var remoteActionMessage by mutableStateOf<String?>(null); private set
    var ruleSaving by mutableStateOf(false); private set

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    active = identity.connection()
                    outgoing = identity.pendingDecision()
                }
                saved = active != null || withContext(Dispatchers.IO) { identity.remote() } != null
                snapshot = withContext(Dispatchers.IO) { identity.cached() }
                if (saved) status = if (snapshot != null) LinkStatus.CONNECTED else LinkStatus.OFFLINE else snapshot = null
                readRemoteState()
            } catch (ex: CancellationException) { throw ex }
            catch (_: Exception) { status = LinkStatus.OFFLINE }
            finally { ready = true; initialized.complete(Unit) }
        }
    }

    fun setForeground(foreground: Boolean) {
        polling?.cancel()
        if (!foreground) return
        polling = viewModelScope.launch {
            initialized.await()
            while (isActive) {
                if (saved && requestJob?.isActive != true && status != LinkStatus.REJECTED) refreshOnce()
                val hasPending = snapshot?.timeRequest?.isPending() == true
                delay(if (hasPending) 4000 else 8000)
            }
        }
    }

    fun edit(value: String) { if (!refreshing) input = value.take(2048) }
    fun connectInvite(value: String) {
        viewModelScope.launch {
            initialized.await()
            if (decisionStatus == DecisionStatus.SENDING) return@launch
            if (saved) {
                if (runCatching { PairingInvite.parse(value) }.isSuccess) replacementInvite = value
                return@launch
            }
            if (requestJob?.isActive == true) return@launch
            edit(value)
            connect()
        }
    }

    fun cancelReplacement() { replacementInvite = null }

    fun confirmReplacement() {
        val invitation = replacementInvite ?: return
        if (decisionStatus == DecisionStatus.SENDING) return
        if (runCatching { PairingInvite.parse(invitation) }.isFailure) {
            replacementInvite = null
            status = LinkStatus.INVALID
            return
        }
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            initialized.await()
            try {
                operations.withLock {
                    withContext(Dispatchers.IO) { identity.forget() }
                    active = null; saved = false; snapshot = null; code = ""; outgoing = null
                    remoteAvailable = false; remoteEnabled = false; decisionStatus = DecisionStatus.IDLE
                    input = invitation; status = LinkStatus.IDLE; replacementInvite = null
                }
                requestJob = null
                connect()
            } catch (ex: CancellationException) { throw ex }
            catch (_: Exception) { status = LinkStatus.OFFLINE }
        }
    }

    fun connect() {
        if (saved || requestJob?.isActive == true) return
        val invite = runCatching { PairingInvite.parse(input) }.getOrNull()
        if (invite == null) { status = LinkStatus.INVALID; return }
        requestJob = viewModelScope.launch {
            initialized.await()
            operations.withLock {
                refreshing = true
                status = LinkStatus.CONNECTING
                pairingIssue = null
                pairingAddress = invite.relay?.origin ?: invite.connection.origin
                try {
                    if (invite.relay != null) {
                        // Save the relay identity before the first fetch so a process restart can resume.
                        // DeviceIdentity uses a small encrypted preferences write here; keeping it inline also
                        // makes the state transition atomic from Compose's point of view.
                        identity.saveRemote(invite.relay)
                        active = null
                        status = LinkStatus.WAITING
                        var last: Exception? = null
                        while (System.currentTimeMillis() / 1000 < invite.expires) {
                            try {
                                val received = withContext(Dispatchers.IO) {
                                    readRelay(invite.relay, identity.remoteSequence(), identity::acceptRemoteSequence)
                                }
                                accept(received)
                                saved = true
                                input = ""
                                // Announce paired phone to PC relay so PC marks phone as paired and clears QR
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val deviceName = android.os.Build.MODEL ?: "Android"
                                        val pairDecision = RelayClient.prepareDevicePairDecision(invite.relay, deviceName)
                                        RelayClient.sendPreparedDecision(invite.relay, pairDecision)
                                    }
                                }
                                return@withLock
                            } catch (ex: DesktopRejected) {
                                throw ex
                            } catch (ex: java.io.IOException) {
                                last = ex
                                delay(2000)
                            }
                        }
                        throw last ?: java.io.IOException("Relay invitation expired")
                    }
                    code = withContext(Dispatchers.IO) {
                        val comparison = client.pair(invite)
                        identity.save(invite.connection)
                        comparison
                    }
                    active = invite.connection
                    saved = true
                    status = LinkStatus.WAITING
                    while (System.currentTimeMillis() / 1000 < invite.expires) {
                        delay(2000)
                        val received = withContext(Dispatchers.IO) { fetchSnapshot(invite.connection) }
                        if (received != null) { accept(received); input = ""; return@withLock }
                    }
                    reject()
                } catch (ex: CancellationException) { throw ex }
                catch (ex: DesktopRejected) { pairingIssue = pairingFailure(ex); reject() }
                catch (ex: Exception) { pairingIssue = pairingFailure(ex); status = LinkStatus.OFFLINE }
                finally { refreshing = false }
            }
        }
    }

    fun refresh() {
        if (refreshing || decisionStatus == DecisionStatus.SENDING || requestJob?.isActive == true) return
        requestJob = viewModelScope.launch { initialized.await(); refreshOnce() }
    }

    private suspend fun refreshOnce() = operations.withLock {
        val peer = active
        val relay = withContext(Dispatchers.IO) { identity.remote() }
        if (peer == null && relay == null) return@withLock
        refreshing = true
        try {
            val received = withContext(Dispatchers.IO) { fetchSnapshot(peer, relay) }
            if (received == null) {
                status = LinkStatus.WAITING
                code = withContext(Dispatchers.IO) {
                    MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(identity.publicKey))
                        .joinToString("") { "%02X".format(it) }.take(12)
                }
            } else accept(received)
        } catch (ex: CancellationException) { throw ex }
        catch (_: DesktopRejected) { reject() }
        catch (_: Exception) {
            if (snapshot == null) status = LinkStatus.OFFLINE
        }
        finally { refreshing = false }
    }

    private suspend fun accept(received: DesktopSnapshot) {
        val request = received.timeRequest
        val pending = outgoing
        if (pending == null && snapshot?.timeRequest?.id != request?.id) decisionStatus = DecisionStatus.IDLE
        if (pending != null && (request?.id != pending.requestId || !request.isPending())) {
            outgoing = null
            withContext(Dispatchers.IO) { identity.saveDecision(null) }
            decisionStatus = DecisionStatus.IDLE
        }
        snapshot = if (request != null && request.isPending() && outgoing?.sent == true) {
            received.copy(timeRequest = request.copy(
                status = if (outgoing!!.approve) "ApprovalSent" else "RejectionSent", grantedMinutes = outgoing!!.minutes))
        } else received
        if (outgoing != null && request?.isPending() == true) {
            decisionStatus = if (outgoing!!.sent) DecisionStatus.SENT else DecisionStatus.FAILED
        }
        withContext(Dispatchers.IO) { identity.cache(received) }
        readRemoteState()
        code = ""
        status = LinkStatus.CONNECTED
        pairingIssue = null
    }

    private suspend fun readRemoteState() {
        val remote = withContext(Dispatchers.IO) { identity.remote() }
        remoteEnabled = remote != null
        remoteAvailable = remote?.decisionToken?.isNotBlank() == true
    }

    private suspend fun reject() {
        snapshot = null
        outgoing = null
        remoteAvailable = false
        status = LinkStatus.REJECTED
        withContext(Dispatchers.IO) {
            identity.cache(null)
            identity.saveDecision(null)
            identity.saveRemote(null)
        }
        remoteEnabled = false
    }

    fun forget() {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            operations.withLock {
                try {
                    withContext(Dispatchers.IO) { identity.forget() }
                    active = null; saved = false; snapshot = null; code = ""; input = ""; outgoing = null
                    status = LinkStatus.IDLE; decisionStatus = DecisionStatus.IDLE; replacementInvite = null
                    pairingIssue = null; pairingAddress = ""
                    remoteAvailable = false; remoteEnabled = false
                } catch (ex: CancellationException) { throw ex }
                catch (_: Exception) { status = LinkStatus.OFFLINE }
            }
        }
    }

    fun decide(request: TimeRequest, approve: Boolean, minutes: Int? = null) {
        if (decisionStatus == DecisionStatus.SENDING || !request.isPending() ||
            snapshot?.timeRequest?.id != request.id || !remoteAvailable || approve && (minutes == null || minutes !in 1..180)) return
        decisionStatus = DecisionStatus.SENDING
        viewModelScope.launch {
            operations.withLock {
                try {
                    val decision = withContext(Dispatchers.IO) {
                        check(request.isPending()) { "Request expired" }
                        val remote = identity.remote() ?: error("Remote access unavailable")
                        val existing = identity.pendingDecision()?.takeIf { it.requestId == request.id && it.room == remote.room }
                        // An uncertain send retries exactly the same encrypted decision; no contradictory action can replace it.
                        val prepared = existing ?: RelayClient.prepareDecision(remote, request.id, approve, minutes)
                        identity.saveDecision(prepared)
                        RelayClient.sendPreparedDecision(remote, prepared)
                        prepared.copy(sent = true).also { identity.saveDecision(it) }
                    }
                    outgoing = decision
                    snapshot = snapshot?.copy(timeRequest = request.copy(
                        status = if (decision.approve) "ApprovalSent" else "RejectionSent", grantedMinutes = decision.minutes))
                    decisionStatus = DecisionStatus.SENT
                } catch (ex: CancellationException) { throw ex }
                catch (_: DesktopRejected) { reject(); decisionStatus = DecisionStatus.FAILED }
                catch (_: Exception) { decisionStatus = DecisionStatus.FAILED }
            }
        }
    }

    fun submitPlan(schedule: List<PlanDay>) {
        if (planSaving || !remoteAvailable || schedule.size != 7) return
        planSaving = true; planSaved = false
        viewModelScope.launch {
            operations.withLock {
                try {
                    val remote = withContext(Dispatchers.IO) { identity.remote() ?: error("Remote access unavailable") }
                    val prepared = withContext(Dispatchers.IO) {
                        val decision = RelayClient.preparePlanDecision(remote, schedule)
                        identity.saveDecision(decision)
                        RelayClient.sendPreparedDecision(remote, decision)
                        decision.copy(sent = true).also { identity.saveDecision(it) }
                    }
                    outgoing = prepared
                    planSaved = true
                } catch (ex: CancellationException) { throw ex }
                catch (_: Exception) { planSaved = false }
                finally { planSaving = false }
            }
        }
    }

    fun submitSessionAction(command: String) {
        if (remoteActionExecuting || !remoteAvailable || command !in listOf("lock", "pause", "resume")) return
        remoteActionExecuting = true
        remoteActionMessage = null
        viewModelScope.launch {
            operations.withLock {
                try {
                    val remote = withContext(Dispatchers.IO) { identity.remote() ?: error("Remote access unavailable") }
                    val prepared = withContext(Dispatchers.IO) {
                        val decision = RelayClient.prepareSessionActionDecision(remote, command)
                        identity.saveDecision(decision)
                        RelayClient.sendPreparedDecision(remote, decision)
                        decision.copy(sent = true).also { identity.saveDecision(it) }
                    }
                    outgoing = prepared
                    remoteActionMessage = when (command) {
                        "lock" -> "Cihaz kilitlendi"
                        "pause" -> "Mola verildi"
                        "resume" -> "Oturum devam ettirildi"
                        else -> "İşlem iletildi"
                    }
                    withContext(Dispatchers.IO) { delay(1200) }
                    var received = withContext(Dispatchers.IO) { runCatching { fetchSnapshot(active, remote) }.getOrNull() }
                    if (received == null || (command == "lock" && !received.isRemotelyLocked)) {
                        withContext(Dispatchers.IO) { delay(1800) }
                        received = withContext(Dispatchers.IO) { runCatching { fetchSnapshot(active, remote) }.getOrNull() }
                    }
                    if (received != null) accept(received)
                } catch (ex: CancellationException) { throw ex }
                catch (ex: Exception) { remoteActionMessage = "İşlem iletilemedi: ${ex.message}" }
                finally { remoteActionExecuting = false }
            }
        }
    }

    fun submitAppRule(appName: String, mode: String, dailyLimitMinutes: Int) {
        if (ruleSaving || !remoteAvailable || appName.isBlank()) return
        ruleSaving = true
        viewModelScope.launch {
            operations.withLock {
                try {
                    val remote = withContext(Dispatchers.IO) { identity.remote() ?: error("Remote access unavailable") }
                    val prepared = withContext(Dispatchers.IO) {
                        val decision = RelayClient.prepareAppRuleDecision(remote, appName, mode, dailyLimitMinutes)
                        identity.saveDecision(decision)
                        RelayClient.sendPreparedDecision(remote, decision)
                        decision.copy(sent = true).also { identity.saveDecision(it) }
                    }
                    outgoing = prepared
                    withContext(Dispatchers.IO) { delay(1500) }
                    val received = withContext(Dispatchers.IO) { fetchSnapshot(active, remote) }
                    if (received != null) accept(received)
                } catch (ex: CancellationException) { throw ex }
                catch (_: Exception) {}
                finally { ruleSaving = false }
            }
        }
    }

    private fun fetchSnapshot(connection: DesktopConnection?, relay: RelaySettings? = null): DesktopSnapshot? {
        val result = try { if (relay != null) RelayClient.snapshot(relay, identity.remoteSequence(), identity::acceptRemoteSequence)
            else client.snapshot(requireNotNull(connection)) }
        catch (ex: DesktopRejected) { throw ex }
        catch (ex: java.io.IOException) {
            val remote = identity.remote() ?: throw ex
            RelayClient.snapshot(remote, identity.remoteSequence(), identity::acceptRemoteSequence)
        }
        result?.remoteDecisionToken?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let { token ->
            identity.remote()?.let { if (it.decisionToken != token) identity.saveRemote(it.copy(decisionToken = token)) }
        }
        return result
    }
}

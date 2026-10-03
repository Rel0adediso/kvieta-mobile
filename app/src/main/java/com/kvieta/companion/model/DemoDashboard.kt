package com.kvieta.companion.model

/** Presentation fixture, never a paired device or an authorization model. */
enum class DemoRole { PERSONAL, PARENT }
enum class DemoDecision { PENDING, APPROVED_AWAITING_DEVICE, REJECTED }
enum class DemoAction { APPROVE, REJECT }

data class AppUsage(val name: String, val minutes: Int)

data class DemoDashboard(
    val role: DemoRole,
    val offline: Boolean = false,
    val decision: DemoDecision = DemoDecision.PENDING,
) {
    val usedMinutes = 168
    val remainingMinutes = 72
    val applications = listOf(AppUsage("Visual Studio Code", 84), AppUsage("Firefox", 56), AppUsage("Spotify", 28))
    val canDecide: Boolean get() = role == DemoRole.PARENT && !offline && decision == DemoDecision.PENDING

    /** Deliberately cannot produce APPLIED: only a future authenticated desktop receipt can. */
    fun decide(action: DemoAction): DemoDashboard = if (!canDecide) this else copy(
        decision = when (action) {
            DemoAction.APPROVE -> DemoDecision.APPROVED_AWAITING_DEVICE
            DemoAction.REJECT -> DemoDecision.REJECTED
        }
    )
}

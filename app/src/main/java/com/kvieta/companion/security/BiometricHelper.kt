package com.kvieta.companion.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat

object BiometricHelper {

    fun isDeviceSecure(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure == true
    }

    fun authenticate(
        activity: Activity,
        title: String,
        subtitle: String = "",
        negativeButtonText: String = "İptal",
        onSuccess: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        if (!isDeviceSecure(activity)) {
            // No device security configured (simulator or unconfigured lock) -> allow directly
            onSuccess()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cancellationSignal = CancellationSignal()
            val executor = ContextCompat.getMainExecutor(activity)
            val builder = BiometricPrompt.Builder(activity)
                .setTitle(title)

            if (subtitle.isNotEmpty()) {
                builder.setSubtitle(subtitle)
            }

            builder.setNegativeButton(negativeButtonText, executor) { _: DialogInterface, _: Int ->
                onCancel()
            }

            val prompt = builder.build()
            prompt.authenticate(cancellationSignal, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    onCancel()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })
        } else {
            onSuccess()
        }
    }
}

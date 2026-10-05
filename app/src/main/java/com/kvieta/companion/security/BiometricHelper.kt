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
        negativeButtonText: String = "PIN ile Onayla",
        onSuccess: () -> Unit,
        onUsePin: () -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        if (!isDeviceSecure(activity)) {
            // No device biometric security configured -> fall back to PIN verification
            onUsePin()
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

            var handled = false
            builder.setNegativeButton(negativeButtonText, executor) { _: DialogInterface, _: Int ->
                if (!handled) {
                    handled = true
                    onUsePin()
                }
            }

            val prompt = builder.build()
            prompt.authenticate(cancellationSignal, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    super.onAuthenticationSucceeded(result)
                    if (!handled) {
                        handled = true
                        onSuccess()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    super.onAuthenticationError(errorCode, errString)
                    if (handled) return
                    handled = true
                    val errorNegativeButton = 13 // BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                    if (errorCode == errorNegativeButton) {
                        onUsePin()
                    } else if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                        onCancel()
                    } else {
                        // Other errors (e.g. lockout, hardware error) -> fall back to PIN
                        onUsePin()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })
        } else {
            onUsePin()
        }
    }
}

package com.kvieta.companion.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kvieta.companion.R

@Composable
fun KvietaPinDialog(
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    canUseBiometric: Boolean = false,
    onUseBiometric: (() -> Unit)? = null,
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidFormat = stringResource(R.string.pin_incorrect)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.pin_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.pin_dialog_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() }.take(8)
                        pin = filtered
                        if (errorMessage != null) errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.pin_input_label)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                            exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
                        ) {
                            if (errorMessage != null) {
                                Text(
                                    errorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (pin.length in 4..8) {
                                val success = onVerifyPin(pin)
                                if (!success) {
                                    errorMessage = invalidFormat
                                }
                            } else {
                                errorMessage = invalidFormat
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                if (canUseBiometric && onUseBiometric != null) {
                    TextButton(
                        onClick = onUseBiometric,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.biometric_security_label))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pin.length in 4..8) {
                        val success = onVerifyPin(pin)
                        if (!success) {
                            errorMessage = invalidFormat
                        }
                    } else {
                        errorMessage = invalidFormat
                    }
                },
                enabled = pin.length >= 4
            ) {
                Text(stringResource(R.string.pin_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pin_cancel))
            }
        },
        shape = RoundedCornerShape(26.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

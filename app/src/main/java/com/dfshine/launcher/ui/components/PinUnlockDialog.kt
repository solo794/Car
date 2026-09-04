package com.dfshine.launcher.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun PinUnlockDialog(
    appLabel: String,
    correctPin: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("فتح قفل $appLabel") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 6) { input = it.filter { c -> c.isDigit() }; error = false } },
                label = { Text(if (error) "رمز غير صحيح، حاول مرة أخرى" else "أدخل الرمز") },
                isError = error,
                visualTransformation = PasswordVisualTransformation()
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (input == correctPin) onUnlocked() else error = true
            }) { Text("فتح") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

package com.liorapps.archerytrainer.screens.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
//    val dateText = remember(session.dateTimeUtc) { formatDate(session.dateTimeUtc) }

    AlertDialog(
        onDismissRequest = onDismiss,
//        title = { Text("Delete Session?") },
        title = { Text(title) },
//        text  = {
//            Text("The session from $dateText will be permanently deleted.")
//        },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


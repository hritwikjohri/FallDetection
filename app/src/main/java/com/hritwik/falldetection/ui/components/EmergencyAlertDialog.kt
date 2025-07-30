package com.hritwik.falldetection.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun EmergencyAlertDialog(
    onEmergencyCall: () -> Unit,
    onDismiss: () -> Unit,
    countdownSeconds: Int = 10,
    showFullScreen: Boolean = true
) {
    var countdown by remember { mutableIntStateOf(countdownSeconds) }
    val progress = (countdownSeconds - countdown).toFloat() / countdownSeconds.toFloat()

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        if (countdown == 0) {
            onEmergencyCall()
        }
    }

    if (showFullScreen) {
        // Full screen emergency dialog
        Dialog(
            onDismissRequest = { /* Don't allow dismissing by clicking outside */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            EmergencyAlertContent(
                countdown = countdown,
                progress = progress,
                onEmergencyCall = onEmergencyCall,
                onDismiss = onDismiss,
                isFullScreen = true
            )
        }
    } else {
        // Standard alert dialog
        AlertDialog(
            onDismissRequest = { /* Don't allow dismissing by clicking outside */ },
            icon = {
                Icon(
                    Icons.Default.Emergency,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "🚨 FALL DETECTED!",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                EmergencyAlertContent(
                    countdown = countdown,
                    progress = progress,
                    onEmergencyCall = onEmergencyCall,
                    onDismiss = onDismiss,
                    isFullScreen = false
                )
            },
            confirmButton = {
                Button(
                    onClick = onEmergencyCall,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Now")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("I'm OK - Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmergencyAlertContent(
    countdown: Int,
    progress: Float,
    onEmergencyCall: () -> Unit,
    onDismiss: () -> Unit,
    isFullScreen: Boolean
) {
    if (isFullScreen) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Emergency Icon
                Icon(
                    Icons.Default.Emergency,
                    contentDescription = null,
                    tint = Color.Red,
                    modifier = Modifier.size(120.dp)
                )

                // Title
                Text(
                    text = "🚨 EMERGENCY ALERT 🚨",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                // Alert Message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "A fall has been detected!",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Emergency services will be contacted automatically in:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                // Countdown Display
                CountdownDisplay(countdown = countdown, progress = progress)

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("I'm OK - Cancel")
                    }

                    Button(
                        onClick = onEmergencyCall,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red
                        )
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call Now")
                    }
                }

                // Warning Text
                Text(
                    text = "If you're unable to respond, emergency services will be contacted automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        // Compact version for standard dialog
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "A fall has been detected!",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Emergency services will be called automatically in:",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            CountdownDisplay(countdown = countdown, progress = progress, compact = true)

            Text(
                text = "Are you okay?",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CountdownDisplay(
    countdown: Int,
    progress: Float,
    compact: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp)
    ) {
        // Countdown Number
        Text(
            text = "$countdown",
            style = if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge,
            color = Color.Red,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = if (countdown == 1) "second" else "seconds",
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            color = Color.Red
        )

        // Progress Bar
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            color = Color.Red,
            trackColor = Color.Red.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun ManualSOSDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.Red,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "Emergency SOS",
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Are you sure you want to trigger an emergency SOS?")
                Text(
                    text = "This will:",
                    fontWeight = FontWeight.Medium
                )
                Text("• Send your location to emergency contacts")
                Text("• Notify emergency services")
                Text("• Record this as an emergency event")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Icon(Icons.Default.Emergency, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Send SOS")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
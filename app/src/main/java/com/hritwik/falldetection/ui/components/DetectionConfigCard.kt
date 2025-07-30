package com.hritwik.falldetection.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@SuppressLint("DefaultLocale")
@Composable
fun DetectionConfigCard(
    onUpdateThresholds: (Float, Float, Float) -> Unit,
    onResetDetector: () -> Unit,
    modifier: Modifier = Modifier
) {
    var freeFallThreshold by remember { mutableFloatStateOf(3.0f) }
    var impactThreshold by remember { mutableFloatStateOf(20.0f) }
    var rotationThreshold by remember { mutableFloatStateOf(3.0f) }
    var showAdvanced by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Detection Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Description
            Text(
                text = "Adjust sensitivity settings for fall detection algorithm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Free Fall Threshold
            ThresholdSlider(
                label = "Free Fall Threshold",
                value = freeFallThreshold,
                onValueChange = { freeFallThreshold = it },
                valueRange = 1.0f..8.0f,
                unit = "m/s²",
                description = "Lower values = more sensitive to free fall detection",
                warningCondition = freeFallThreshold < 2.0f,
                warningMessage = "Very sensitive - may cause false positives"
            )

            // Impact Threshold
            ThresholdSlider(
                label = "Impact Threshold",
                value = impactThreshold,
                onValueChange = { impactThreshold = it },
                valueRange = 10.0f..40.0f,
                unit = "m/s²",
                description = "Higher values = less sensitive to impact detection",
                warningCondition = impactThreshold > 35.0f,
                warningMessage = "Less sensitive - may miss some falls"
            )

            // Rotation Threshold
            ThresholdSlider(
                label = "Rotation Threshold",
                value = rotationThreshold,
                onValueChange = { rotationThreshold = it },
                valueRange = 1.0f..10.0f,
                unit = "rad/s",
                description = "Rotation sensitivity during fall analysis",
                warningCondition = rotationThreshold > 8.0f,
                warningMessage = "High threshold - may reduce accuracy"
            )

            // Advanced Settings Toggle
            OutlinedButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (showAdvanced) "Hide Advanced Settings" else "Show Advanced Settings")
            }

            if (showAdvanced) {
                AdvancedSettings()
            }

            // Preset Configurations
            PresetConfigurations(
                onPresetSelected = { preset ->
                    when (preset) {
                        DetectionPreset.SENSITIVE -> {
                            freeFallThreshold = 2.5f
                            impactThreshold = 15.0f
                            rotationThreshold = 2.0f
                        }
                        DetectionPreset.BALANCED -> {
                            freeFallThreshold = 3.0f
                            impactThreshold = 20.0f
                            rotationThreshold = 3.0f
                        }
                        DetectionPreset.CONSERVATIVE -> {
                            freeFallThreshold = 4.0f
                            impactThreshold = 25.0f
                            rotationThreshold = 4.0f
                        }
                    }
                }
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onUpdateThresholds(freeFallThreshold, impactThreshold, rotationThreshold)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Update, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update")
                }

                OutlinedButton(
                    onClick = {
                        // Reset to default values
                        freeFallThreshold = 3.0f
                        impactThreshold = 20.0f
                        rotationThreshold = 3.0f
                        onUpdateThresholds(freeFallThreshold, impactThreshold, rotationThreshold)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // Reset Detector Button
            OutlinedButton(
                onClick = onResetDetector,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset Detector State")
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
private fun ThresholdSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    description: String,
    warningCondition: Boolean = false,
    warningMessage: String = ""
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Label and Value
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${String.format("%.1f", value)} $unit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = ((valueRange.endInclusive - valueRange.start) * 2).toInt() - 1
        )

        // Description
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Warning Message
        if (warningCondition && warningMessage.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.padding(0.dp)
                )
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun AdvancedSettings() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Advanced Settings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Additional configuration options could go here
            Text(
                text = "• Window Size: 50 samples",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Min Fall Duration: 200ms",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Max Fall Duration: 2000ms",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "• Cooldown Period: 5 seconds",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PresetConfigurations(
    onPresetSelected: (DetectionPreset) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Quick Presets",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetButton(
                preset = DetectionPreset.SENSITIVE,
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
            PresetButton(
                preset = DetectionPreset.BALANCED,
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
            PresetButton(
                preset = DetectionPreset.CONSERVATIVE,
                onPresetSelected = onPresetSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PresetButton(
    preset: DetectionPreset,
    onPresetSelected: (DetectionPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = { onPresetSelected(preset) },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = preset.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

enum class DetectionPreset(
    val displayName: String,
    val description: String
) {
    SENSITIVE("Sensitive", "High sensitivity"),
    BALANCED("Balanced", "Recommended"),
    CONSERVATIVE("Conservative", "Low sensitivity")
}
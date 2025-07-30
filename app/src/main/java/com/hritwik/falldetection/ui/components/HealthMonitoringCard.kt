package com.hritwik.falldetection.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hritwik.falldetection.model.HeartRateData
import com.hritwik.falldetection.model.LocationData
import com.hritwik.falldetection.model.OxygenData

@SuppressLint("DefaultLocale")
@Composable
fun HealthMonitoringCard(
    heartRateData: HeartRateData?,
    oxygenData: OxygenData?,
    locationData: LocationData?,
    isTransmitting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTransmitting) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = if (isTransmitting) Color.Green else Color.Gray
                )
                Text(
                    text = "Health Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isTransmitting) {
                    Text(
                        text = "● LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Heart Rate Section
            heartRateData?.let { data ->
                VitalSignCard(
                    title = "Heart Rate",
                    value = "${data.heartRate}",
                    unit = "BPM",
                    isNormal = data.isNormal,
                    icon = Icons.Default.Favorite,
                    normalRange = "60-100 BPM",
                    color = Color.Red
                )
            }

            // Oxygen Section
            oxygenData?.let { data ->
                VitalSignCard(
                    title = "Blood Oxygen",
                    value = "${data.oxygenSaturation}",
                    unit = "%",
                    isNormal = data.isNormal,
                    icon = Icons.Default.Sensors,
                    normalRange = "95-100%",
                    color = Color.Blue
                )
            }

            // Location Section
            locationData?.let { data ->
                LocationCard(locationData = data)
            }

            if (heartRateData == null && oxygenData == null && locationData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start monitoring to see health data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun VitalSignCard(
    title: String,
    value: String,
    unit: String,
    isNormal: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    normalRange: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isNormal) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                Color.Red.copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = normalRange,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNormal) color else Color.Red
                        )
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (isNormal) "Normal" else "Abnormal",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNormal) Color.Green else Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (!isNormal) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun LocationCard(locationData: LocationData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Current Location",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Lat: ${String.format("%.6f", locationData.latitude)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Lng: ${String.format("%.6f", locationData.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                )

                locationData.accuracy?.let { accuracy ->
                    Text(
                        text = "Accuracy: ±${String.format("%.1f", accuracy)}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                locationData.address?.let { address ->
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun HealthDataChart(
    heartRateHistory: List<HeartRateData>,
    oxygenHistory: List<OxygenData>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Health Data Trends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (heartRateHistory.isNotEmpty()) {
                HealthTrendChart(
                    title = "Heart Rate Trend",
                    data = heartRateHistory.map { it.heartRate.toFloat() },
                    color = Color.Red,
                    normalMin = 60f,
                    normalMax = 100f,
                    unit = "BPM"
                )
            }

            if (oxygenHistory.isNotEmpty()) {
                HealthTrendChart(
                    title = "Oxygen Saturation Trend",
                    data = oxygenHistory.map { it.oxygenSaturation.toFloat() },
                    color = Color.Blue,
                    normalMin = 95f,
                    normalMax = 100f,
                    unit = "%"
                )
            }

            if (heartRateHistory.isEmpty() && oxygenHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data available yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun HealthTrendChart(
    title: String,
    data: List<Float>,
    color: Color,
    normalMin: Float,
    normalMax: Float,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            data.lastOrNull()?.let { lastValue ->
                Text(
                    text = "${String.format("%.0f", lastValue)} $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (data.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height
            val padding = 20f

            // Calculate bounds
            val minValue = (data.minOrNull() ?: 0f).coerceAtMost(normalMin - 5f)
            val maxValue = (data.maxOrNull() ?: 100f).coerceAtLeast(normalMax + 5f)
            val range = maxValue - minValue

            if (range == 0f) return@Canvas

            // Draw normal range background
            val normalMinY = height - padding - ((normalMin - minValue) / range) * (height - 2 * padding)
            val normalMaxY = height - padding - ((normalMax - minValue) / range) * (height - 2 * padding)

            drawRect(
                color = Color.Green.copy(alpha = 0.1f),
                topLeft = Offset(padding, normalMaxY),
                size = androidx.compose.ui.geometry.Size(width - 2 * padding, normalMinY - normalMaxY)
            )

            // Draw data line
            drawHealthDataLine(
                data = data,
                color = color,
                width = width,
                height = height,
                padding = padding,
                minValue = minValue,
                maxValue = maxValue
            )
        }
    }
}

private fun DrawScope.drawHealthDataLine(
    data: List<Float>,
    color: Color,
    width: Float,
    height: Float,
    padding: Float,
    minValue: Float,
    maxValue: Float
) {
    val range = maxValue - minValue
    val stepX = (width - 2 * padding) / (data.size - 1).coerceAtLeast(1)

    val path = Path()

    data.forEachIndexed { index, value ->
        val x = padding + index * stepX
        val y = height - padding - ((value - minValue) / range) * (height - 2 * padding)

        if (index == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 3.dp.toPx())
    )

    // Draw data points
    data.forEachIndexed { index, value ->
        val x = padding + index * stepX
        val y = height - padding - ((value - minValue) / range) * (height - 2 * padding)

        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(x, y)
        )
    }
}

@Composable
fun SOSButton(
    onSOSPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onSOSPressed,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Red,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = "EMERGENCY SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
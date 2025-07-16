package com.hritwik.falldetection.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.hritwik.falldetection.sensors.FallDetectionSensorManager

@Composable
fun SensorVisualizationCard(
    sensorManager: FallDetectionSensorManager,
    isMonitoring: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sensor Data Visualization",
                style = MaterialTheme.typography.titleMedium
            )

            if (isMonitoring) {
                // Real-time accelerometer chart
                AccelerometerChart(
                    data = sensorManager.getRecentAccelerations(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                // Real-time gyroscope chart
                GyroscopeChart(
                    data = sensorManager.getRecentRotations(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start monitoring to see real-time data",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AccelerometerChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Accelerometer Magnitude (m/s²)",
            style = MaterialTheme.typography.labelMedium
        )

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

            // Draw background
            drawRect(
                color = surfaceColor,
                size = size
            )

            // Calculate data bounds
            val minValue = data.minOrNull() ?: 0f
            val maxValue = data.maxOrNull() ?: 1f
            val range = maxValue - minValue

            if (range == 0f) return@Canvas

            // Draw threshold lines
            val fallThreshold = 3.0f
            val impactThreshold = 20.0f

            // Fall threshold line
            val fallY = height - padding - ((fallThreshold - minValue) / range) * (height - 2 * padding)
            drawLine(
                color = Color(0xffFFA500),
                start = Offset(padding, fallY),
                end = Offset(width - padding, fallY),
                strokeWidth = 2.dp.toPx()
            )

            // Impact threshold line
            val impactY = height - padding - ((impactThreshold - minValue) / range) * (height - 2 * padding)
            if (impactY > padding) {
                drawLine(
                    color = Color.Red,
                    start = Offset(padding, impactY),
                    end = Offset(width - padding, impactY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw data line
            drawSensorDataLine(
                data = data,
                color = primaryColor,
                width = width,
                height = height,
                padding = padding,
                minValue = minValue,
                maxValue = maxValue
            )
        }
    }
}

@Composable
fun GyroscopeChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Gyroscope Magnitude (rad/s)",
            style = MaterialTheme.typography.labelMedium
        )

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

            // Draw background
            drawRect(
                color = surfaceColor,
                size = size
            )

            // Calculate data bounds
            val minValue = data.minOrNull() ?: 0f
            val maxValue = data.maxOrNull() ?: 1f
            val range = maxValue - minValue

            if (range == 0f) return@Canvas

            // Draw rotation threshold line
            val rotationThreshold = 3.0f
            val thresholdY = height - padding - ((rotationThreshold - minValue) / range) * (height - 2 * padding)
            if (thresholdY > padding && thresholdY < height - padding) {
                drawLine(
                    color = Color.Magenta,
                    start = Offset(padding, thresholdY),
                    end = Offset(width - padding, thresholdY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw data line
            drawSensorDataLine(
                data = data,
                color = secondaryColor,
                width = width,
                height = height,
                padding = padding,
                minValue = minValue,
                maxValue = maxValue
            )
        }
    }
}

private fun DrawScope.drawSensorDataLine(
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
fun FallDetectionMetrics(
    sensorManager: FallDetectionSensorManager,
    isMonitoring: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Detection Metrics",
                style = MaterialTheme.typography.titleMedium
            )

            if (isMonitoring) {
                val accelerations = sensorManager.getRecentAccelerations()
                val rotations = sensorManager.getRecentRotations()

                MetricRow(
                    label = "Avg Acceleration",
                    value = "${String.format("%.2f", accelerations.average())} m/s²",
                    color = MaterialTheme.colorScheme.primary
                )

                MetricRow(
                    label = "Max Acceleration",
                    value = "${String.format("%.2f", accelerations.maxOrNull() ?: 0f)} m/s²",
                    color = MaterialTheme.colorScheme.primary
                )

                MetricRow(
                    label = "Avg Rotation",
                    value = "${String.format("%.2f", rotations.average())} rad/s",
                    color = MaterialTheme.colorScheme.secondary
                )

                MetricRow(
                    label = "Max Rotation",
                    value = "${String.format("%.2f", rotations.maxOrNull() ?: 0f)} rad/s",
                    color = MaterialTheme.colorScheme.secondary
                )

                val variance = calculateVariance(accelerations)
                MetricRow(
                    label = "Acceleration Variance",
                    value = String.format("%.2f", variance),
                    color = MaterialTheme.colorScheme.tertiary
                )

            } else {
                Text(
                    text = "Start monitoring to see metrics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

private fun calculateVariance(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val mean = values.average().toFloat()
    return values.map { (it - mean) * (it - mean) }.average().toFloat()
}

@Composable
fun FallRiskIndicator(
    currentAcceleration: Float,
    currentRotation: Float,
    freeFallThreshold: Float,
    impactThreshold: Float,
    rotationThreshold: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                currentAcceleration < freeFallThreshold -> Color(0xFFFFEBEE) // Light red
                currentAcceleration > impactThreshold -> Color(0xFFFFEBEE) // Light red
                currentRotation > rotationThreshold -> Color(0xFFFFF3E0) // Light orange
                else -> Color(0xFFE8F5E8) // Light green
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Fall Risk Indicator",
                style = MaterialTheme.typography.titleMedium
            )

            val riskLevel = when {
                currentAcceleration < freeFallThreshold -> "HIGH RISK - Free Fall Detected"
                currentAcceleration > impactThreshold -> "HIGH RISK - Impact Detected"
                currentRotation > rotationThreshold -> "MEDIUM RISK - High Rotation"
                else -> "LOW RISK - Normal Movement"
            }

            val riskColor = when {
                currentAcceleration < freeFallThreshold -> Color.Red
                currentAcceleration > impactThreshold -> Color.Red
                currentRotation > rotationThreshold -> Color(0xFFFF9800)
                else -> Color.Green
            }

            Text(
                text = riskLevel,
                style = MaterialTheme.typography.bodyLarge,
                color = riskColor
            )

            // Risk indicators
            RiskIndicatorBar(
                label = "Acceleration Risk",
                value = when {
                    currentAcceleration < freeFallThreshold -> 1.0f
                    currentAcceleration > impactThreshold -> 1.0f
                    else -> 0.0f
                },
                color = Color.Red
            )

            RiskIndicatorBar(
                label = "Rotation Risk",
                value = (currentRotation / (rotationThreshold * 2)).coerceIn(0f, 1f),
                color = Color(0xFFFF9800)
            )
        }
    }
}

@Composable
fun RiskIndicatorBar(
    label: String,
    value: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall
            )
        }

        LinearProgressIndicator(
            progress = value,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.3f)
        )
    }
}
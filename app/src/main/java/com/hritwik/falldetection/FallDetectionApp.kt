package com.hritwik.falldetection

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hritwik.falldetection.health.HealthDataManager
import com.hritwik.falldetection.location.LocationManager
import com.hritwik.falldetection.model.AlertType
import com.hritwik.falldetection.model.EmergencyAlert
import com.hritwik.falldetection.model.FallEvent
import com.hritwik.falldetection.model.FallPhase
import com.hritwik.falldetection.model.MqttConnectionStatus
import com.hritwik.falldetection.model.SensorReading
import com.hritwik.falldetection.model.TransmissionStats
import com.hritwik.falldetection.mqtt.MqttManager
import com.hritwik.falldetection.sensors.FallDetectionSensorManager
import com.hritwik.falldetection.service.DataTransmissionService
import com.hritwik.falldetection.ui.components.*
import com.hritwik.falldetection.viewmodel.FallDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FallDetectionApp(
    sensorManager: FallDetectionSensorManager,
    emergencySystem: EmergencySystem,
    healthDataManager: HealthDataManager,
    locationManager: LocationManager,
    mqttManager: MqttManager,
    dataTransmissionService: DataTransmissionService,
    viewModel: FallDetectionViewModel = viewModel { FallDetectionViewModel(sensorManager) }
) {
    // Sensor states
    val isMonitoring by viewModel.isMonitoring.collectAsState()
    val fallDetected by viewModel.fallDetected.collectAsState()
    val currentPhase by viewModel.currentPhase.collectAsState()
    val fallEvents by viewModel.fallEvents.collectAsState()
    val currentSensorReading by viewModel.currentSensorReading.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()

    // Health data states
    val currentHeartRate by healthDataManager.currentHeartRate.collectAsState()
    val currentOxygen by healthDataManager.currentOxygen.collectAsState()
    val heartRateHistory by healthDataManager.heartRateHistory.collectAsState()
    val oxygenHistory by healthDataManager.oxygenHistory.collectAsState()

    // Location states
    val currentLocation by locationManager.currentLocation.collectAsState()
    val isLocationEnabled by locationManager.isLocationEnabled.collectAsState()

    // MQTT states
    val mqttConnectionStatus by mqttManager.connectionStatus.collectAsState()
    val messagesSent by mqttManager.messagesSent.collectAsState()

    // Transmission states
    val isTransmitting by dataTransmissionService.isTransmitting.collectAsState()
    val transmissionStats by dataTransmissionService.transmissionStats.collectAsState()

    // Emergency states
    val isEmergencyMode by emergencySystem.isEmergencyMode.collectAsState()

    // Handle fall detection
    LaunchedEffect(fallDetected) {
        if (fallDetected) {
            emergencySystem.triggerEmergencyAlert(AlertType.FALL_DETECTION)
            // Also send fall data via MQTT if we have recent fall events
            fallEvents.lastOrNull()?.let { fallEvent ->
                dataTransmissionService.triggerFallDetectedSOS(fallEvent)
            }
        }
    }

    // Show emergency alert dialog
    if (isEmergencyMode) {
        EmergencyAlertDialog(
            onEmergencyCall = {
                val contacts = emergencySystem.emergencyContacts.value
                val primaryContact = contacts.find { it.isPrimary }
                primaryContact?.let { contact ->
                    emergencySystem.makeEmergencyCall(contact.phoneNumber,
                        EmergencyAlert(
                            timestamp = System.currentTimeMillis(),
                            location = null,
                            alertType = AlertType.FALL_DETECTION
                        )
                    )
                }
            },
            onDismiss = {
                emergencySystem.cancelEmergencyAlert()
                viewModel.resetFallAlert()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Fall Detection")
                        Text(
                            text = when (currentPhase) {
                                FallPhase.NORMAL -> "Monitoring"
                                FallPhase.FREE_FALL -> "Free Fall Detected"
                                FallPhase.IMPACT -> "Impact Detected"
                                FallPhase.POST_IMPACT -> "Analyzing..."
                                FallPhase.FALL_DETECTED -> "FALL DETECTED!"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = when (currentPhase) {
                        FallPhase.NORMAL -> MaterialTheme.colorScheme.primary
                        FallPhase.FREE_FALL -> Color(0xFFFF9800)
                        FallPhase.IMPACT -> Color(0xFFF44336)
                        FallPhase.POST_IMPACT -> Color(0xFF9C27B0)
                        FallPhase.FALL_DETECTED -> Color(0xFFD32F2F)
                    }
                ),
                actions = {
                    // MQTT Connection Status Indicator
                    Icon(
                        imageVector = when (mqttConnectionStatus) {
                            MqttConnectionStatus.CONNECTED -> Icons.Default.CloudDone
                            MqttConnectionStatus.CONNECTING -> Icons.Default.CloudSync
                            MqttConnectionStatus.DISCONNECTED -> Icons.Default.CloudOff
                        },
                        contentDescription = "MQTT Status",
                        tint = when (mqttConnectionStatus) {
                            MqttConnectionStatus.CONNECTED -> Color.Green
                            MqttConnectionStatus.CONNECTING -> Color.Blue
                            MqttConnectionStatus.DISCONNECTED -> Color.Red
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SOS Button - Always visible at top
            item {
                SOSButton(
                    onSOSPressed = {
                        dataTransmissionService.triggerManualSOS()
                        emergencySystem.triggerEmergencyAlert(AlertType.MANUAL_TRIGGER)
                    }
                )
            }

            // System Control Card
            item {
                SystemControlCard(
                    isMonitoring = isMonitoring,
                    isTransmitting = isTransmitting,
                    mqttConnectionStatus = mqttConnectionStatus,
                    onStartMonitoring = viewModel::startMonitoring,
                    onStopMonitoring = viewModel::stopMonitoring,
                    onStartTransmission = { dataTransmissionService.startTransmission() },
                    onStopTransmission = { dataTransmissionService.stopTransmission() },
                    onConnectMqtt = { mqttManager.connect() },
                    onDisconnectMqtt = { mqttManager.disconnect() }
                )
            }

            // Health Monitoring Card
            item {
                HealthMonitoringCard(
                    heartRateData = currentHeartRate,
                    oxygenData = currentOxygen,
                    locationData = currentLocation,
                    isTransmitting = isTransmitting
                )
            }

            // Transmission Statistics
            if (isTransmitting) {
                item {
                    TransmissionStatsCard(
                        stats = transmissionStats,
                        messagesSent = messagesSent
                    )
                }
            }

            // Health Data Charts
            if (heartRateHistory.isNotEmpty() || oxygenHistory.isNotEmpty()) {
                item {
                    HealthDataChart(
                        heartRateHistory = heartRateHistory,
                        oxygenHistory = oxygenHistory
                    )
                }
            }

            // Original fall detection components
            if (isMonitoring) {
                // Live Sensor Data
                item {
                    LiveSensorDataCard(
                        sensorReading = currentSensorReading,
                        sensorManager = sensorManager
                    )
                }

                // Sensor Visualization
                item {
                    SensorVisualizationCard(
                        sensorManager = sensorManager,
                        isMonitoring = true
                    )
                }

                // Fall Risk Indicator
                currentSensorReading?.let { reading ->
                    item {
                        FallRiskIndicator(
                            currentAcceleration = sensorManager.calculateMagnitude(reading.accelerometer),
                            currentRotation = sensorManager.calculateMagnitude(reading.gyroscope),
                            freeFallThreshold = 3.0f,
                            impactThreshold = 20.0f,
                            rotationThreshold = 3.0f
                        )
                    }
                }

                // Detection Metrics
                item {
                    FallDetectionMetrics(
                        sensorManager = sensorManager,
                        isMonitoring = true
                    )
                }
            }

            // Detection Configuration
            item {
                DetectionConfigCard(
                    onUpdateThresholds = viewModel::updateThresholds,
                    onResetDetector = viewModel::resetDetector
                )
            }

            // Debug Information
            if (isMonitoring) {
                item {
                    DebugInfoCard(debugInfo = debugInfo)
                }
            }

            // Fall Events History
            if (fallEvents.isNotEmpty()) {
                item {
                    FallEventsCard(events = fallEvents)
                }
            }

            // Sensor Information
            item {
                SensorInfoCard(sensorManager = sensorManager)
            }
        }
    }
}

@Composable
fun SystemControlCard(
    isMonitoring: Boolean,
    isTransmitting: Boolean,
    mqttConnectionStatus: MqttConnectionStatus,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onStartTransmission: () -> Unit,
    onStopTransmission: () -> Unit,
    onConnectMqtt: () -> Unit,
    onDisconnectMqtt: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text(
                    text = "System Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Monitoring Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartMonitoring,
                    enabled = !isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Monitor")
                }

                Button(
                    onClick = onStopMonitoring,
                    enabled = isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }

            // Transmission Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartTransmission,
                    enabled = !isTransmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Data")
                }

                Button(
                    onClick = onStopTransmission,
                    enabled = isTransmitting,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop Data")
                }
            }

            // MQTT Status and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (mqttConnectionStatus) {
                            MqttConnectionStatus.CONNECTED -> Icons.Default.CloudDone
                            MqttConnectionStatus.CONNECTING -> Icons.Default.CloudSync
                            MqttConnectionStatus.DISCONNECTED -> Icons.Default.CloudOff
                        },
                        contentDescription = null,
                        tint = when (mqttConnectionStatus) {
                            MqttConnectionStatus.CONNECTED -> Color.Green
                            MqttConnectionStatus.CONNECTING -> Color.Blue
                            MqttConnectionStatus.DISCONNECTED -> Color.Red
                        }
                    )
                    Text(
                        text = "MQTT: ${mqttConnectionStatus.name}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onConnectMqtt,
                        enabled = mqttConnectionStatus != MqttConnectionStatus.CONNECTED
                    ) {
                        Text("Connect")
                    }

                    if (mqttConnectionStatus == MqttConnectionStatus.CONNECTED) {
                        OutlinedButton(onClick = onDisconnectMqtt) {
                            Text("Disconnect")
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun TransmissionStatsCard(
    stats: TransmissionStats,
    messagesSent: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null)
                Text(
                    text = "Transmission Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Statistics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Heart Rate",
                    value = "${stats.heartRateMessagesSent}",
                    color = Color.Red
                )
                StatItem(
                    label = "Oxygen",
                    value = "${stats.oxygenMessagesSent}",
                    color = Color.Blue
                )
                StatItem(
                    label = "Location",
                    value = "${stats.locationMessagesSent}",
                    color = Color.Green
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "SOS",
                    value = "${stats.sosMessagesSent}",
                    color = Color.Red
                )
                StatItem(
                    label = "Falls",
                    value = "${stats.fallDetectionMessagesSent}",
                    color = Color.Magenta
                )
                StatItem(
                    label = "Errors",
                    value = "${stats.errorCount}",
                    color = if (stats.errorCount > 0) Color.Red else Color.Gray
                )
            }

            if (stats.errorCount == 0 && messagesSent > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.Green
                    )
                    Text(
                        text = "All systems operational",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Keep all the existing composables from the original file
@Composable
fun MonitoringControlCard(
    isMonitoring: Boolean,
    currentPhase: FallPhase,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoring) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = if (isMonitoring) Color.Green else Color.Gray
                )
                Text(
                    text = "Status: ${if (isMonitoring) "MONITORING" else "STOPPED"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isMonitoring) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (currentPhase) {
                            FallPhase.NORMAL -> Icons.Default.Check
                            FallPhase.FREE_FALL -> Icons.Default.Warning
                            FallPhase.IMPACT -> Icons.Default.Error
                            FallPhase.POST_IMPACT -> Icons.Default.Info
                            FallPhase.FALL_DETECTED -> Icons.Default.Emergency
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (currentPhase) {
                            FallPhase.NORMAL -> Color.Green
                            FallPhase.FREE_FALL -> Color(0xFFFF9800)
                            FallPhase.IMPACT -> Color.Red
                            FallPhase.POST_IMPACT -> Color(0xFF9C27B0)
                            FallPhase.FALL_DETECTED -> Color.Red
                            else -> Color.Black
                        }
                    )
                    Text(
                        text = "Phase: ${currentPhase.name.replace('_', ' ')}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartMonitoring,
                    enabled = !isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }

                Button(
                    onClick = onStopMonitoring,
                    enabled = isMonitoring,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
fun LiveSensorDataCard(
    sensorReading: SensorReading?,
    sensorManager: FallDetectionSensorManager
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Text(
                    text = "Live Sensor Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            sensorReading?.let { reading ->
                // Accelerometer Data
                SensorDataRow(
                    label = "Accelerometer",
                    values = reading.accelerometer,
                    unit = "m/s²",
                    magnitude = sensorManager.calculateMagnitude(reading.accelerometer)
                )

                // Gyroscope Data
                SensorDataRow(
                    label = "Gyroscope",
                    values = reading.gyroscope,
                    unit = "rad/s",
                    magnitude = sensorManager.calculateMagnitude(reading.gyroscope)
                )

                HorizontalDivider()

                Text(
                    text = "Last Update: ${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date(reading.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun SensorDataRow(
    label: String,
    values: Triple<Float, Float, Float>,
    unit: String,
    magnitude: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "X: ${String.format("%.2f", values.first)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Y: ${String.format("%.2f", values.second)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Z: ${String.format("%.2f", values.third)} $unit",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Magnitude: ${String.format("%.2f", magnitude)} $unit",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DebugInfoCard(debugInfo: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Text(
                    text = "Debug Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = debugInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun FallEventsCard(events: List<FallEvent>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Text(
                    text = "Fall Events (${events.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            events.takeLast(5).forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = event.phase.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(event.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = event.details,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorInfoCard(sensorManager: FallDetectionSensorManager) {
    val sensorInfo by sensorManager.sensorInfo.collectAsState()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text(
                    text = "Sensor Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (sensorManager.isSensorAvailable()) "✅ All required sensors available" else "❌ Required sensors missing",
                style = MaterialTheme.typography.bodyMedium,
                color = if (sensorManager.isSensorAvailable()) Color.Green else Color.Red
            )

            if (sensorInfo.isNotEmpty()) {
                Text(
                    text = sensorInfo,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
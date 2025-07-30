package com.hritwik.falldetection.mqtt

import android.content.Context
import com.hritwik.falldetection.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.UUID
import com.hritwik.falldetection.model.MqttMessage as CustomMqttMessage

class MqttManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val deviceId = UUID.randomUUID().toString()

    // HiveMQ Cloud configuration
    private val serverHost = "10.10.2.224" // Free public broker
    private val serverPort = 1883
    private val username = "" // For HiveMQ Cloud, add your credentials
    private val password = ""
    private val keepAliveInterval = 60 // seconds

    // Topic structure matching your reference
    private fun getHeartRateTopic() = "health/$deviceId/heartrate"
    private fun getSpO2Topic() = "health/$deviceId/spo2"
    private fun getSOSTopic() = "emergency/$deviceId/sos"
    private fun getLocationTopic() = "location/$deviceId/gps"
    private fun getGeofenceTopic() = "location/$deviceId/geofence"
    private fun getWatchHealthTopic() = "health/$deviceId/watch"
    private fun getDeviceStatusTopic() = "device/$deviceId/status"
    private fun getFallDetectionTopic() = "emergency/$deviceId/fall"

    private val _connectionStatus = MutableStateFlow(MqttConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<MqttConnectionStatus> = _connectionStatus.asStateFlow()

    private val _messagesSent = MutableStateFlow(0)
    val messagesSent: StateFlow<Int> = _messagesSent.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _topicStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val topicStats: StateFlow<Map<String, Int>> = _topicStats.asStateFlow()

    private var packetId = 1
    private var isKeepAliveRunning = false

    companion object {
        private const val TAG = "HiveMqttManager"

        // MQTT Message Types
        private const val MQTT_CONNECT = 0x10.toByte()
        private const val MQTT_CONNACK = 0x20.toByte()
        private const val MQTT_PUBLISH = 0x30.toByte()
        private const val MQTT_PUBACK = 0x40.toByte()
        private const val MQTT_PINGREQ = 0xC0.toByte()
        private const val MQTT_PINGRESP = 0xD0.toByte()
        private const val MQTT_DISCONNECT = 0xE0.toByte()
    }

    fun connect() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                _connectionStatus.value = MqttConnectionStatus.CONNECTING
                _lastError.value = null
                // Create socket connection
                socket = Socket(serverHost, serverPort)
                writer = BufferedWriter(OutputStreamWriter(socket?.getOutputStream()))
                reader = BufferedReader(InputStreamReader(socket?.getInputStream()))

                // Send MQTT CONNECT packet
                sendConnectPacket()

                // Read CONNACK
                if (readConnAck()) {
                    _connectionStatus.value = MqttConnectionStatus.CONNECTED

                    startKeepAlive()

                    // Send initial device status
                    publishDeviceStatus("ONLINE")
                } else {
                    throw Exception("Connection rejected by broker")
                }

            } catch (e: Exception) {
                _connectionStatus.value = MqttConnectionStatus.DISCONNECTED
                _lastError.value = e.message
                cleanup()
            }
        }
    }

    fun disconnect() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Send offline status before disconnecting
                publishDeviceStatus("OFFLINE")

                // Send DISCONNECT packet
                sendDisconnectPacket()

                // Stop keep alive
                isKeepAliveRunning = false

                cleanup()
                _connectionStatus.value = MqttConnectionStatus.DISCONNECTED

            } catch (e: Exception) {
                cleanup()
            }
        }
    }

    private fun sendConnectPacket() {
        val clientId = "FallDetection_$deviceId"
        val protocolName = "MQTT"
        val protocolLevel = 4.toByte()
        val connectFlags = if (username.isNotEmpty()) 0xC2.toByte() else 0x02.toByte() // Clean session + username/password if provided

        // Variable header
        val variableHeader = mutableListOf<Byte>()

        // Protocol name
        variableHeader.addAll(encodeString(protocolName))

        // Protocol level
        variableHeader.add(protocolLevel)

        // Connect flags
        variableHeader.add(connectFlags)

        // Keep alive
        variableHeader.addAll(encodeShort(keepAliveInterval))

        // Payload
        val payload = mutableListOf<Byte>()

        // Client ID
        payload.addAll(encodeString(clientId))

        // Username and password if provided
        if (username.isNotEmpty()) {
            payload.addAll(encodeString(username))
            payload.addAll(encodeString(password))
        }

        // Fixed header
        val remainingLength = variableHeader.size + payload.size
        val fixedHeader = mutableListOf<Byte>()
        fixedHeader.add(MQTT_CONNECT)
        fixedHeader.addAll(encodeRemainingLength(remainingLength))

        // Send complete packet
        val packet = fixedHeader + variableHeader + payload
        socket?.getOutputStream()?.write(packet.toByteArray())
        socket?.getOutputStream()?.flush()

    }

    private fun readConnAck(): Boolean {
        return try {
            val byte1 = socket?.getInputStream()?.read() ?: return false
            val byte2 = socket?.getInputStream()?.read() ?: return false
            val byte3 = socket?.getInputStream()?.read() ?: return false
            val byte4 = socket?.getInputStream()?.read() ?: return false

            if (byte1 == MQTT_CONNACK.toInt() && byte2 == 2) {
                val returnCode = byte4
                if (returnCode == 0) {
                    return true
                } else {
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun startKeepAlive() {
        isKeepAliveRunning = true
        coroutineScope.launch(Dispatchers.IO) {
            while (isKeepAliveRunning && _connectionStatus.value == MqttConnectionStatus.CONNECTED) {
                try {
                    kotlinx.coroutines.delay(keepAliveInterval * 1000L)
                    if (isKeepAliveRunning) {
                        sendPingRequest()
                    }
                } catch (e: Exception) {
                    _connectionStatus.value = MqttConnectionStatus.DISCONNECTED
                    break
                }
            }
        }
    }

    private fun sendPingRequest() {
        try {
            val pingPacket = byteArrayOf(MQTT_PINGREQ, 0x00)
            socket?.getOutputStream()?.write(pingPacket)
            socket?.getOutputStream()?.flush()
        } catch (e: Exception) {
            throw e
        }
    }

    private fun sendDisconnectPacket() {
        try {
            val disconnectPacket = byteArrayOf(MQTT_DISCONNECT, 0x00)
            socket?.getOutputStream()?.write(disconnectPacket)
            socket?.getOutputStream()?.flush()
        } catch (e: Exception) {
        }
    }

    // Publishing methods
    fun publishHeartRate(heartRateData: HeartRateData) {
        val customMessage = createCustomMqttMessage("HEART_RATE", heartRateData)
        val payload = createHeartRatePayload(heartRateData)
        publishMessage(getHeartRateTopic(), payload, qos = 1, messageType = "HEART_RATE")
    }

    fun publishOxygen(oxygenData: OxygenData) {
        val customMessage = createCustomMqttMessage("OXYGEN", oxygenData)
        val payload = createSpO2Payload(oxygenData)
        publishMessage(getSpO2Topic(), payload, qos = 1, messageType = "SPO2")
    }

    fun publishLocation(locationData: LocationData) {
        val customMessage = createCustomMqttMessage("LOCATION", locationData)
        val payload = createLocationPayload(locationData)
        publishMessage(getLocationTopic(), payload, qos = 1, messageType = "LOCATION")
    }

    fun publishSOS(sosData: SOSData) {
        val customMessage = createCustomMqttMessage("SOS", sosData)
        val payload = createSOSPayload(sosData)
        publishMessage(getSOSTopic(), payload, qos = 2, messageType = "SOS")
    }

    fun publishFallDetected(
        fallEvent: FallEvent,
        locationData: LocationData?,
        heartRate: Int?,
        oxygen: Int?
    ) {
        val fallData = mapOf(
            "fallEvent" to fallEvent,
            "location" to locationData,
            "heartRate" to heartRate,
            "oxygen" to oxygen
        )
        val customMessage = createCustomMqttMessage("FALL_DETECTED", fallData)
        val payload = createFallDetectionPayload(fallEvent, locationData, heartRate, oxygen)
        publishMessage(getFallDetectionTopic(), payload, qos = 2, messageType = "FALL_DETECTION")
    }

    fun publishWatchHealthData(
        heartRateData: HeartRateData?,
        oxygenData: OxygenData?,
        activityLevel: String = "moderate"
    ) {
        val healthData = mapOf(
            "heartRate" to heartRateData,
            "oxygen" to oxygenData,
            "activity" to activityLevel
        )
        val customMessage = createCustomMqttMessage("WATCH_HEALTH", healthData)
        val payload = createWatchHealthPayload(heartRateData, oxygenData, activityLevel)
        publishMessage(getWatchHealthTopic(), payload, qos = 1, messageType = "WATCH_HEALTH")
    }

    fun publishDeviceStatus(status: String, batteryLevel: Int? = null, signalStrength: Int? = null) {
        val statusData = mapOf(
            "status" to status,
            "battery" to batteryLevel,
            "signal" to signalStrength
        )
        val customMessage = createCustomMqttMessage("DEVICE_STATUS", statusData)
        val payload = createDeviceStatusPayload(status, batteryLevel, signalStrength)
        publishMessage(getDeviceStatusTopic(), payload, qos = 0, messageType = "DEVICE_STATUS")
    }

    fun publishGeofenceEvent(locationData: LocationData, event: String, zoneName: String) {
        val geofenceData = mapOf(
            "location" to locationData,
            "event" to event,
            "zone" to zoneName
        )
        val customMessage = createCustomMqttMessage("GEOFENCE", geofenceData)
        val payload = createGeofencePayload(locationData, event, zoneName)
        publishMessage(getGeofenceTopic(), payload, qos = 2, messageType = "GEOFENCE")
    }

    private fun createCustomMqttMessage(messageType: String, data: Any): CustomMqttMessage {
        return CustomMqttMessage(
            deviceId = deviceId,
            messageType = messageType,
            timestamp = System.currentTimeMillis(),
            data = data
        )
    }

    private fun publishMessage(topic: String, payload: String, qos: Int = 1, messageType: String) {
        if (_connectionStatus.value != MqttConnectionStatus.CONNECTED) {
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                sendPublishPacket(topic, payload, qos)

                _messagesSent.value = _messagesSent.value + 1
                updateTopicStats(topic)

            } catch (e: Exception) {
                _lastError.value = "Publish error: ${e.message}"
            }
        }
    }

    private fun sendPublishPacket(topic: String, payload: String, qos: Int) {
        val topicBytes = topic.toByteArray()
        val payloadBytes = payload.toByteArray()

        // Variable header
        val variableHeader = mutableListOf<Byte>()

        // Topic name
        variableHeader.addAll(encodeString(topic))

        // Packet identifier (only for QoS > 0)
        if (qos > 0) {
            variableHeader.addAll(encodeShort(packetId++))
        }

        // Fixed header
        val flags = when (qos) {
            0 -> 0x30.toByte()  // QoS 0
            1 -> 0x32.toByte()  // QoS 1
            2 -> 0x34.toByte()  // QoS 2
            else -> 0x30.toByte()
        }

        val remainingLength = variableHeader.size + payloadBytes.size
        val fixedHeader = mutableListOf<Byte>()
        fixedHeader.add(flags)
        fixedHeader.addAll(encodeRemainingLength(remainingLength))

        // Complete packet
        val packet = fixedHeader + variableHeader + payloadBytes.toList()
        socket?.getOutputStream()?.write(packet.toByteArray())
        socket?.getOutputStream()?.flush()
    }

    // Helper functions for MQTT packet encoding
    private fun encodeString(str: String): List<Byte> {
        val bytes = str.toByteArray()
        val result = mutableListOf<Byte>()
        result.addAll(encodeShort(bytes.size))
        result.addAll(bytes.toList())
        return result
    }

    private fun encodeShort(value: Int): List<Byte> {
        return listOf(
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun encodeRemainingLength(length: Int): List<Byte> {
        val result = mutableListOf<Byte>()
        var value = length

        do {
            var byte = (value and 0x7F).toByte()
            value = value shr 7
            if (value > 0) {
                byte = (byte.toInt() or 0x80).toByte()
            }
            result.add(byte)
        } while (value > 0)

        return result
    }

    // All payload creation methods (same as before)
    private fun createHeartRatePayload(data: HeartRateData): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", data.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("data", JSONObject().apply {
                put("heartRate", data.heartRate)
                put("isNormal", data.isNormal)
                put("unit", "bpm")
                put("quality", if (data.isNormal) "good" else "warning")
                put("trend", calculateHeartRateTrend(data.heartRate))
            })
            put("metadata", createMetadata("health", "heartrate"))
        }.toString()
    }

    private fun createSpO2Payload(data: OxygenData): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", data.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("data", JSONObject().apply {
                put("spO2", data.oxygenSaturation)
                put("isNormal", data.isNormal)
                put("unit", "percent")
                put("quality", if (data.isNormal) "good" else "warning")
                put("alertLevel", when {
                    data.oxygenSaturation < 90 -> "critical"
                    data.oxygenSaturation < 95 -> "warning"
                    else -> "normal"
                })
            })
            put("metadata", createMetadata("health", "spo2"))
        }.toString()
    }

    private fun createLocationPayload(data: LocationData): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", data.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("data", JSONObject().apply {
                put("latitude", data.latitude)
                put("longitude", data.longitude)
                put("accuracy", data.accuracy)
                put("address", data.address)
                put("provider", "gps")
                put("speed", 0)
                put("bearing", 0)
            })
            put("metadata", createMetadata("location", "gps"))
        }.toString()
    }

    private fun createSOSPayload(data: SOSData): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", data.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("priority", "critical")
            put("data", JSONObject().apply {
                put("message", data.message)
                put("latitude", data.latitude)
                put("longitude", data.longitude)
                put("heartRate", data.heartRate)
                put("spO2", data.oxygenSaturation)
                put("triggerType", "manual")
                put("emergencyContacts", JSONObject().apply {
                    put("primary", "911")
                })
            })
            put("metadata", createMetadata("emergency", "sos"))
        }.toString()
    }

    private fun createFallDetectionPayload(
        fallEvent: FallEvent,
        locationData: LocationData?,
        heartRate: Int?,
        oxygen: Int?
    ): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", fallEvent.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("priority", "critical")
            put("data", JSONObject().apply {
                put("fallDetected", true)
                put("confidence", fallEvent.confidence)
                put("phase", fallEvent.phase.name)
                put("details", fallEvent.details)
                put("severity", when {
                    fallEvent.confidence > 0.8f -> "high"
                    fallEvent.confidence > 0.6f -> "medium"
                    else -> "low"
                })

                locationData?.let { location ->
                    put("location", JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("accuracy", location.accuracy)
                        put("address", location.address)
                    })
                }

                put("vitals", JSONObject().apply {
                    put("heartRate", heartRate)
                    put("spO2", oxygen)
                })
            })
            put("metadata", createMetadata("emergency", "fall"))
        }.toString()
    }

    private fun createWatchHealthPayload(
        heartRateData: HeartRateData?,
        oxygenData: OxygenData?,
        activityLevel: String
    ): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("messageId", UUID.randomUUID().toString())
            put("data", JSONObject().apply {
                heartRateData?.let { hr ->
                    put("heartRate", JSONObject().apply {
                        put("value", hr.heartRate)
                        put("isNormal", hr.isNormal)
                        put("unit", "bpm")
                    })
                }

                oxygenData?.let { spo2 ->
                    put("spO2", JSONObject().apply {
                        put("value", spo2.oxygenSaturation)
                        put("isNormal", spo2.isNormal)
                        put("unit", "percent")
                    })
                }

                put("activity", JSONObject().apply {
                    put("level", activityLevel)
                    put("steps", 0)
                    put("calories", 0)
                })

                put("battery", JSONObject().apply {
                    put("level", 85)
                    put("charging", false)
                })
            })
            put("metadata", createMetadata("health", "watch"))
        }.toString()
    }

    private fun createDeviceStatusPayload(status: String, batteryLevel: Int?, signalStrength: Int?): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                put("status", status)
                put("batteryLevel", batteryLevel ?: 85)
                put("signalStrength", signalStrength ?: -45)
                put("uptime", System.currentTimeMillis())
                put("version", "1.0.0")
            })
        }.toString()
    }

    private fun createGeofencePayload(locationData: LocationData, event: String, zoneName: String): String {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", locationData.timestamp)
            put("messageId", UUID.randomUUID().toString())
            put("data", JSONObject().apply {
                put("event", event)
                put("zoneName", zoneName)
                put("latitude", locationData.latitude)
                put("longitude", locationData.longitude)
                put("accuracy", locationData.accuracy)
                put("dwellTime", if (event == "dwell") 300 else 0)
            })
            put("metadata", createMetadata("location", "geofence"))
        }.toString()
    }

    private fun createMetadata(category: String, type: String): JSONObject {
        return JSONObject().apply {
            put("category", category)
            put("type", type)
            put("version", "1.0")
            put("source", "android_app")
            put("deviceModel", android.os.Build.MODEL)
            put("osVersion", android.os.Build.VERSION.RELEASE)
            put("appVersion", "1.0.0")
            put("processingTime", System.currentTimeMillis())
        }
    }

    private fun calculateHeartRateTrend(currentRate: Int): String {
        return when {
            currentRate > 100 -> "increasing"
            currentRate < 60 -> "decreasing"
            else -> "stable"
        }
    }

    private fun updateTopicStats(topic: String) {
        val currentStats = _topicStats.value.toMutableMap()
        val topicName = topic.split("/").takeLast(2).joinToString("/")
        currentStats[topicName] = (currentStats[topicName] ?: 0) + 1
        _topicStats.value = currentStats
    }

    fun cleanup() {
        try {
            isKeepAliveRunning = false
            writer?.close()
            reader?.close()
            socket?.close()
            publishDeviceStatus("OFFLINE")
            disconnect()
        } catch (e: Exception) {
        }
        writer = null
        reader = null
        socket = null
    }

    fun isConnected(): Boolean {
        return _connectionStatus.value == MqttConnectionStatus.CONNECTED
    }

    fun getDeviceId(): String = deviceId

    fun getTopicStatistics(): Map<String, Int> = _topicStats.value
}

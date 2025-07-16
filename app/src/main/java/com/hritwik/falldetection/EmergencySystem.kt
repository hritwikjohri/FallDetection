package com.hritwik.falldetection

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.hritwik.falldetection.mdoel.AlertType
import com.hritwik.falldetection.mdoel.EmergencyAlert
import com.hritwik.falldetection.mdoel.EmergencyContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EmergencySystem(private val context: Context) {

    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(
        listOf(
            EmergencyContact("Emergency Services", "911", "Emergency", isPrimary = true),
            EmergencyContact("Family Doctor", "555-0123", "Medical", isPrimary = false)
        )
    )
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _activeAlerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val activeAlerts: StateFlow<List<EmergencyAlert>> = _activeAlerts.asStateFlow()

    private val _isEmergencyMode = MutableStateFlow(false)
    val isEmergencyMode: StateFlow<Boolean> = _isEmergencyMode.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    companion object {
        private const val TAG = "EmergencySystem"
        private const val EMERGENCY_COUNTDOWN_SECONDS = 30
    }

    fun triggerEmergencyAlert(alertType: AlertType) {
        Log.w(TAG, "Emergency alert triggered: $alertType")

        _isEmergencyMode.value = true

        // Get current location if available
        val location = getCurrentLocation()

        // Create emergency alert
        val alert = EmergencyAlert(
            timestamp = System.currentTimeMillis(),
            location = location,
            alertType = alertType
        )

        _activeAlerts.value = _activeAlerts.value + alert

        // Start emergency response sequence
        startEmergencyResponse(alert)
    }

    private fun startEmergencyResponse(alert: EmergencyAlert) {
        // 1. Play emergency sound
        playEmergencySound()

        // 2. Vibrate device
        startEmergencyVibration()

        // 3. Start countdown for automatic emergency call
        startEmergencyCountdown(alert)
    }

    private fun playEmergencySound() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer.create(context, alertUri)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing emergency sound", e)
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun startEmergencyVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                val effect = VibrationEffect.createWaveform(pattern, 0)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val pattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                vibrator.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun startEmergencyCountdown(alert: EmergencyAlert) {
        // In a real app, this would be a proper countdown timer
        // For now, we'll simulate it
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (_isEmergencyMode.value) {
                // Auto-call primary emergency contact
                val primaryContact = _emergencyContacts.value.find { it.isPrimary }
                primaryContact?.let { contact ->
                    makeEmergencyCall(contact.phoneNumber, alert)
                }
            }
        }, EMERGENCY_COUNTDOWN_SECONDS * 1000L)
    }

    fun makeEmergencyCall(phoneNumber: String, alert: EmergencyAlert) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent)
                Log.i(TAG, "Emergency call initiated to: $phoneNumber")
            } else {
                // Fallback to dial intent
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                Log.i(TAG, "Emergency dial initiated to: $phoneNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making emergency call", e)
        }
    }

    fun sendEmergencyText(phoneNumber: String, alert: EmergencyAlert) {
        try {
            val message = buildEmergencyMessage(alert)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Emergency text prepared for: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending emergency text", e)
        }
    }

    private fun buildEmergencyMessage(alert: EmergencyAlert): String {
        val alertTypeText = when (alert.alertType) {
            AlertType.FALL_DETECTION -> "FALL DETECTED"
            AlertType.MANUAL_TRIGGER -> "MANUAL EMERGENCY"
            AlertType.PANIC_BUTTON -> "PANIC BUTTON"
        }

        val locationText = alert.location?.let { location ->
            "Location: ${location.latitude}, ${location.longitude}"
        } ?: "Location: Unknown"

        val timeText = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(alert.timestamp))

        return """
            🚨 EMERGENCY ALERT 🚨
            
            Type: $alertTypeText
            Time: $timeText
            $locationText
            
            This is an automated message from Fall Detection App.
            Please check on the user immediately.
        """.trimIndent()
    }

    fun cancelEmergencyAlert() {
        Log.i(TAG, "Emergency alert cancelled by user")

        _isEmergencyMode.value = false

        // Stop sounds and vibration
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        vibrator.cancel()

        // Mark alerts as inactive
        _activeAlerts.value = _activeAlerts.value.map { it.copy(isActive = false) }
    }

    fun addEmergencyContact(contact: EmergencyContact) {
        _emergencyContacts.value = _emergencyContacts.value + contact
    }

    fun removeEmergencyContact(contact: EmergencyContact) {
        _emergencyContacts.value = _emergencyContacts.value - contact
    }

    fun updateEmergencyContact(oldContact: EmergencyContact, newContact: EmergencyContact) {
        _emergencyContacts.value = _emergencyContacts.value.map {
            if (it == oldContact) newContact else it
        }
    }

    private fun getCurrentLocation(): Location? {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

                return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
        }
        return null
    }

    fun cleanup() {
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator.cancel()
        _isEmergencyMode.value = false
    }
}
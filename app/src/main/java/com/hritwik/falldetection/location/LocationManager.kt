package com.hritwik.falldetection.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.hritwik.falldetection.model.LocationData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : LocationListener {

    private val androidLocationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
    private val geocoder = Geocoder(context, Locale.getDefault())

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _locationHistory = MutableStateFlow<List<LocationData>>(emptyList())
    val locationHistory: StateFlow<List<LocationData>> = _locationHistory.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(false)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private var isRequestingUpdates = false

    companion object {
        private const val TAG = "LocationManager"
        private const val MIN_TIME_BETWEEN_UPDATES = 30000L // 30 seconds
        private const val MIN_DISTANCE_CHANGE = 0.001f // 10 meters
        private const val HISTORY_LIMIT = 5000
    }

    fun startLocationUpdates(): Boolean {
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            return false
        }

        if (!isLocationProviderEnabled()) {
            Log.e(TAG, "Location provider not enabled")
            return false
        }

        try {
            // Try GPS first, then Network
            if (androidLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                androidLocationManager.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
                )
                Log.d(TAG, "GPS location updates started")
            }

            if (androidLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                androidLocationManager.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    MIN_TIME_BETWEEN_UPDATES,
                    MIN_DISTANCE_CHANGE,
                    this
                )
                Log.d(TAG, "Network location updates started")
            }

            isRequestingUpdates = true
            _isLocationEnabled.value = true

            // Get last known location immediately
            getLastKnownLocation()

            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location updates", e)
            return false
        }
    }

    fun stopLocationUpdates() {
        try {
            androidLocationManager.removeUpdates(this)
            isRequestingUpdates = false
            _isLocationEnabled.value = false
            Log.d(TAG, "Location updates stopped")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping location updates", e)
        }
    }

    private fun getLastKnownLocation() {
        if (!hasLocationPermission()) return

        try {
            val gpsLocation = androidLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            val networkLocation = androidLocationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)

            // Use the more recent location
            val bestLocation = when {
                gpsLocation != null && networkLocation != null -> {
                    if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                }
                gpsLocation != null -> gpsLocation
                networkLocation != null -> networkLocation
                else -> null
            }

            bestLocation?.let { location ->
                processLocationUpdate(location)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last known location", e)
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "Location changed: ${location.latitude}, ${location.longitude}")
        processLocationUpdate(location)
    }

    private fun processLocationUpdate(location: Location) {
        coroutineScope.launch {
            val address = getAddressFromLocation(location.latitude, location.longitude)

            val locationData = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                address = address
            )

            _currentLocation.value = locationData
            updateLocationHistory(locationData)

            Log.d(TAG, "Location updated: ${locationData.latitude}, ${locationData.longitude}")
            address?.let { Log.d(TAG, "Address: $it") }
        }
    }

    private suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
                    addresses?.firstOrNull()?.let { address ->
                        buildString {
                            if (address.featureName != null) append("${address.featureName}, ")
                            if (address.thoroughfare != null) append("${address.thoroughfare}, ")
                            if (address.locality != null) append("${address.locality}, ")
                            if (address.adminArea != null) append("${address.adminArea}, ")
                            if (address.countryName != null) append(address.countryName)
                        }.trimEnd(',', ' ')
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting address", e)
                null
            }
        }
    }

    private fun updateLocationHistory(locationData: LocationData) {
        val currentHistory = _locationHistory.value.toMutableList()
        currentHistory.add(locationData)

        // Keep only recent data
        if (currentHistory.size > HISTORY_LIMIT) {
            currentHistory.removeAt(0)
        }

        _locationHistory.value = currentHistory
    }

    // Get immediate location for SOS
    suspend fun getCurrentLocationForSOS(): LocationData? {
        return withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) {
                Log.e(TAG, "No location permission for SOS")
                return@withContext null
            }

            try {
                // Try to get the most recent location
                val gpsLocation = androidLocationManager.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
                val networkLocation = androidLocationManager.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)

                val bestLocation = when {
                    gpsLocation != null && networkLocation != null -> {
                        if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
                    }
                    gpsLocation != null -> gpsLocation
                    networkLocation != null -> networkLocation
                    else -> null
                }

                bestLocation?.let { location ->
                    val address = getAddressFromLocation(location.latitude, location.longitude)
                    LocationData(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        address = address
                    )
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception getting SOS location", e)
                null
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationProviderEnabled(): Boolean {
        return androidLocationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
                androidLocationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(TAG, "Location provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d(TAG, "Location provider disabled: $provider")
    }

    fun cleanup() {
        stopLocationUpdates()
        Log.d(TAG, "Location manager cleaned up")
    }
}
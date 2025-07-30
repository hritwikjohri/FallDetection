package com.hritwik.falldetection.model

data class EmergencyContact(
    val name: String,
    val phoneNumber: String,
    val relationship: String,
    val isPrimary: Boolean = false
)
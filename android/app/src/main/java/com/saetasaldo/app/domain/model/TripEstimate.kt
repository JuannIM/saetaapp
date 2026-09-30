package com.saetasaldo.app.domain.model

data class TripEstimate(
    val regularTrips: Int,
    val emergencyTrips: Int,
    val totalPossibleTrips: Int,
    val remainingSubBalance: Double,
    val isInEmergencyNegative: Boolean
)

package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.TripEstimate
import kotlin.math.floor

class CalculateRemainingTripsUseCase {

    operator fun invoke(balance: Double, fare: Double): TripEstimate {
        val safeFare = if (fare <= 0.0 || fare.isNaN()) 1450.0 else fare
        val isInNegative = balance < 0.0

        val regularTrips = if (balance > 0.0) {
            floor(balance / safeFare).toInt()
        } else {
            0
        }

        val remainingSubBalance = if (balance > 0.0) {
            balance - (regularTrips * safeFare)
        } else {
            0.0
        }

        // Option 1: Calculate trips strictly based on available balance.
        // Standard non-nominated SAETA cards have 0 emergency trips allowance.
        val emergencyTrips = 0

        return TripEstimate(
            regularTrips = regularTrips,
            emergencyTrips = emergencyTrips,
            totalPossibleTrips = regularTrips,
            remainingSubBalance = remainingSubBalance,
            isInEmergencyNegative = isInNegative
        )
    }
}

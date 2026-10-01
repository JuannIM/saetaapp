package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardType
import com.saetasaldo.app.domain.model.TripEstimate
import kotlin.math.floor
import kotlin.math.max

class CalculateRemainingTripsUseCase {

    operator fun invoke(balance: Double, fare: Double, cardType: CardType): TripEstimate {
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

        val emergencyAllowance = if (cardType == CardType.AZUL_COMUN) 2 else 0

        val emergencyTrips = if (cardType == CardType.AZUL_COMUN) {
            if (balance >= 0.0) {
                emergencyAllowance
            } else {
                val debtTrips = kotlin.math.ceil(-balance / safeFare).toInt()
                max(0, emergencyAllowance - debtTrips)
            }
        } else {
            0
        }

        return TripEstimate(
            regularTrips = regularTrips,
            emergencyTrips = emergencyTrips,
            totalPossibleTrips = regularTrips + emergencyTrips,
            remainingSubBalance = remainingSubBalance,
            isInEmergencyNegative = isInNegative
        )
    }
}

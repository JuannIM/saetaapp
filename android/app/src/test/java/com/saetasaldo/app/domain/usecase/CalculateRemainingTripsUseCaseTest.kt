package com.saetasaldo.app.domain.usecase

import com.saetasaldo.app.domain.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateRemainingTripsUseCaseTest {

    private val useCase = CalculateRemainingTripsUseCase()

    @Test
    fun `calculates remaining trips correctly with positive balance`() {
        val result = useCase(balance = 2900.0, fare = 1450.0, cardType = CardType.AZUL_COMUN)
        assertEquals(2, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(2, result.totalPossibleTrips)
        assertFalse(result.isInEmergencyNegative)
    }

    @Test
    fun `calculates remaining trips when balance is partial`() {
        val result = useCase(balance = 2000.0, fare = 1450.0, cardType = CardType.AZUL_COMUN)
        assertEquals(1, result.regularTrips)
        assertEquals(550.0, result.remainingSubBalance, 0.01)
        assertEquals(0, result.emergencyTrips)
        assertEquals(1, result.totalPossibleTrips)
    }

    @Test
    fun `negative balance results in zero trips and marks negative state`() {
        val result = useCase(balance = -500.0, fare = 1450.0, cardType = CardType.AZUL_COMUN)
        assertEquals(0, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(0, result.totalPossibleTrips)
        assertTrue(result.isInEmergencyNegative)
    }

    @Test
    fun `green card calculates regular trips strictly based on balance`() {
        val result = useCase(balance = 2900.0, fare = 1450.0, cardType = CardType.VERDE_BENEFICIARIO)
        assertEquals(2, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(2, result.totalPossibleTrips)
    }

    @Test
    fun `zero balance has zero trips and zero emergency trips`() {
        val result = useCase(balance = 0.0, fare = 1450.0, cardType = CardType.AZUL_COMUN)
        assertEquals(0, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(0, result.totalPossibleTrips)
        assertEquals(0.0, result.remainingSubBalance, 0.01)
        assertFalse(result.isInEmergencyNegative)
    }

    @Test
    fun `fallback to default fare when non-positive fare provided`() {
        val result = useCase(balance = 2900.0, fare = 0.0, cardType = CardType.AZUL_COMUN)
        assertEquals(2, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(2, result.totalPossibleTrips)
    }

    @Test
    fun `negative balance for green card has zero emergency trips`() {
        val result = useCase(balance = -500.0, fare = 1450.0, cardType = CardType.VERDE_BENEFICIARIO)
        assertEquals(0, result.regularTrips)
        assertEquals(0, result.emergencyTrips)
        assertEquals(0, result.totalPossibleTrips)
        assertTrue(result.isInEmergencyNegative)
    }
}

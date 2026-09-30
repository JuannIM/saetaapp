package com.saetasaldo.app.data.local

import com.saetasaldo.app.domain.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `converts CardType to and from String`() {
        val type = CardType.VERDE_BENEFICIARIO
        val str = converters.fromCardType(type)
        assertEquals("VERDE_BENEFICIARIO", str)
        assertEquals(type, converters.toCardType(str))
    }

    @Test
    fun `handles null CardType gracefully`() {
        assertNull(converters.fromCardType(null))
        assertEquals(CardType.AZUL_COMUN, converters.toCardType(null))
    }

    @Test
    fun `falls back to AZUL_COMUN on invalid string`() {
        assertEquals(CardType.AZUL_COMUN, converters.toCardType("UNKNOWN_TYPE_XYZ"))
    }
}

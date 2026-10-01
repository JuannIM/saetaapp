package com.saetasaldo.app.data.remote.dto

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SaldoResponseDtoTest {

    private val gson = Gson()

    @Test
    fun `deserializes saldos when value is string containing pasajes 1070_00`() {
        val json = """
            {
                "error": 0,
                "mensaje": null,
                "numeroTarjeta": "12345678",
                "tipoTarjeta": "Público",
                "estadoTarjeta": "ACTIVA",
                "saldos": [
                    {
                        "name": "Pasajes",
                        "value": "pasajes 1070.00"
                    }
                ],
                "fechaSaldo": "2026-09-30 23:45"
            }
        """.trimIndent()

        val dto = gson.fromJson(json, SaldoResponseDto::class.java)
        assertEquals(0, dto.error)
        val balances = dto.balances
        assertNotNull(balances)
        assertEquals(1, balances?.size)
        assertEquals(1070.0, balances?.first()?.amount ?: 0.0, 0.01)
        assertEquals(1070.0, dto.effectiveBalance, 0.01)
    }

    @Test
    fun `deserializes saldos when saldos is array of strings`() {
        val json = """
            {
                "error": 0,
                "saldos": ["pasajes 1070.00"]
            }
        """.trimIndent()

        val dto = gson.fromJson(json, SaldoResponseDto::class.java)
        assertEquals(0, dto.error)
        assertEquals(1070.0, dto.effectiveBalance, 0.01)
    }

    @Test
    fun `deserializes saldos when value is raw numeric`() {
        val json = """
            {
                "error": 0,
                "saldos": [
                    {
                        "monto": 1450.50,
                        "fecha": "2026-09-30"
                    }
                ]
            }
        """.trimIndent()

        val dto = gson.fromJson(json, SaldoResponseDto::class.java)
        assertEquals(1450.50, dto.effectiveBalance, 0.01)
        assertEquals("2026-09-30", dto.balances?.first()?.date)
    }

    @Test
    fun `parseAmount handles negative balances and currency signs`() {
        assertEquals(1070.0, SaldoResponseDto.parseAmount("pasajes 1070.00"), 0.01)
        assertEquals(1070.0, SaldoResponseDto.parseAmount("Pasajes: $ 1070.00"), 0.01)
        assertEquals(-690.0, SaldoResponseDto.parseAmount("pasajes -690.00"), 0.01)
        assertEquals(-1070.0, SaldoResponseDto.parseAmount("-pasajes 1070.00"), 0.01)
        assertEquals(1070.0, SaldoResponseDto.parseAmount("1.070,00"), 0.01)
        assertEquals(1070.50, SaldoResponseDto.parseAmount("1070,50"), 0.01)
        assertEquals(0.0, SaldoResponseDto.parseAmount(""), 0.01)
    }
}

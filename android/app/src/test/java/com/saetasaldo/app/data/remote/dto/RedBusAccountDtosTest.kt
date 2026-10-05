package com.saetasaldo.app.data.remote.dto

import com.google.gson.Gson
import com.saetasaldo.app.domain.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedBusAccountDtosTest {

    private val gson = Gson()

    private fun loadFixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("redbus/$name")) {
            "Missing test fixture redbus/$name"
        }.readText()

    @Test
    fun `connected fixture recognizes error zero`() {
        val dto = gson.fromJson(loadFixture("session-connected.json"), RedBusSessionDto::class.java)
        assertEquals(0, dto.error)
    }

    @Test
    fun `disconnected fixture recognizes error one`() {
        val dto = gson.fromJson(loadFixture("session-disconnected.json"), RedBusSessionDto::class.java)
        assertEquals(1, dto.error)
    }

    @Test
    fun `card fixture maps external number state type and description`() {
        val dto = gson.fromJson(loadFixture("cards-success.json"), RedBusCardListDto::class.java)

        val cards = dto.toDomainCards()

        assertEquals(1, cards.size)
        val card = cards[0]
        assertEquals("12345678", card.cardNumber)
        assertEquals(1450.5, card.balance, 0.001)
        assertEquals(CardType.AZUL_COMUN, card.cardType)
        assertEquals("ACTIVA", card.cardState)
        assertEquals("Tarjeta de ejemplo", card.suggestedName)
    }

    @Test
    fun `card fixture maps only Principal Dinero balance`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000001",
                        "relationship": "TITULAR",
                        "description": "Tarjeta de ejemplo",
                        "estadoTarjeta": "ACTIVA",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 9999.99,
                                    "monedero": { "nombre": "Beneficio Estudiantil" }
                                },
                                {
                                    "saldo": 1450.5,
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "estadoTarjeta": "ACTIVA",
                            "codExterno": 12345678
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertEquals(1, cards.size)
        assertEquals(1450.5, cards[0].balance, 0.001)
    }

    @Test
    fun `card without Principal Dinero is omitted`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000001",
                        "description": "Tarjeta de ejemplo",
                        "estadoTarjeta": "ACTIVA",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 500.0,
                                    "monedero": { "nombre": "Beneficio Estudiantil" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": 12345678
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `malformed card entries are omitted without rejecting valid siblings`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000001",
                        "description": "Tarjeta rota",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 1450.5,
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "codExterno": { "invalido": true }
                        }
                    },
                    {
                        "nroInterno": "90000002",
                        "description": "Tarjeta valida",
                        "estadoTarjeta": "ACTIVA",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 200.0,
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": 87654321
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertEquals(1, cards.size)
        assertEquals("87654321", cards[0].cardNumber)
        assertEquals(200.0, cards[0].balance, 0.001)
    }

    @Test
    fun `session expired fixture recognizes error ninety nine`() {
        val dto = gson.fromJson(loadFixture("cards-session-expired.json"), RedBusCardListDto::class.java)

        assertEquals(99, dto.error)
        assertTrue(dto.toDomainCards().isEmpty())
    }

    @Test
    fun `string external code and string balance are accepted`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000003",
                        "description": "Tarjeta string",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": "1450.50",
                                    "monedero": { "nombre": "  principal (dinero)  " }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": " 12345678 "
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertEquals(1, cards.size)
        assertEquals("12345678", cards[0].cardNumber)
        assertEquals(1450.5, cards[0].balance, 0.01)
    }

    @Test
    fun `boolean external code is omitted`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000004",
                        "description": "Tarjeta booleana",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 1450.5,
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": true
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `non numeric principal balance is omitted`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000005",
                        "description": "Tarjeta sin saldo",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": "not-a-balance",
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": 12345678
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `zero string principal balance remains valid`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000006",
                        "description": "Tarjeta en cero",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": "0.00",
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "COMUN",
                            "codExterno": 12345678
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertEquals(1, cards.size)
        assertEquals(0.0, cards[0].balance, 0.001)
    }

    @Test
    fun `blank nested state falls back to trimmed outer state and blank type stays null`() {
        val json = """
            {
                "error": 0,
                "tarjetas": [
                    {
                        "nroInterno": "90000007",
                        "description": "Tarjeta valida",
                        "estadoTarjeta": "  ACTIVA  ",
                        "tarjetaDatosAdicionales": {
                            "saldos": [
                                {
                                    "saldo": 100.0,
                                    "monedero": { "nombre": "Principal (Dinero)" }
                                }
                            ],
                            "tipoTarjeta": "   ",
                            "estadoTarjeta": "   ",
                            "codExterno": 12345678
                        }
                    }
                ]
            }
        """.trimIndent()

        val cards = gson.fromJson(json, RedBusCardListDto::class.java).toDomainCards()

        assertEquals(1, cards.size)
        assertEquals("ACTIVA", cards[0].cardState)
        assertNull(cards[0].cardType)
    }
}

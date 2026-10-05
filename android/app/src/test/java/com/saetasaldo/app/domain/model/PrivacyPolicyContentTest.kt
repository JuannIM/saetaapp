package com.saetasaldo.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyContentTest {

    @Test
    fun `privacy policy contains all essential compliance sections`() {
        val sections = PrivacyPolicyContent.sections
        assertTrue(sections.isNotEmpty())
        
        val titles = sections.map { it.title }
        assertTrue(titles.any { it.contains("Almacenamiento Local", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Permisos", ignoreCase = true) })
        assertTrue(titles.any { it.contains("RedBus", ignoreCase = true) })
        assertTrue(titles.any { it.contains("Privacidad", ignoreCase = true) || it.contains("Datos", ignoreCase = true) })
    }

    @Test
    fun `permission explanations cover NFC and INTERNET`() {
        val permissions = PrivacyPolicyContent.permissionsJustification
        assertTrue(permissions.containsKey("NFC"))
        assertTrue(permissions.containsKey("INTERNET"))
        assertFalse(permissions.getValue("NFC").isBlank())
        assertFalse(permissions.getValue("INTERNET").isBlank())
    }

    @Test
    fun `policy explains the optional RedBus account connection`() {
        val text = allPolicyText()
        assertTrue(text.contains("cuenta de RedBus", ignoreCase = true))
        assertTrue(text.contains("opcional", ignoreCase = true))
        assertTrue(text.contains("modo anónimo", ignoreCase = true))
        // The account flow syncs linked card number and principal balance.
        assertTrue(text.contains("número de tarjeta", ignoreCase = true))
        assertTrue(text.contains("saldo principal", ignoreCase = true))
    }

    @Test
    fun `policy states credentials are entered only on the official site`() {
        val text = allPolicyText()
        assertTrue(text.contains("página oficial", ignoreCase = true))
        assertTrue(text.contains("WebView"))
        assertTrue(
            text.contains("nunca recibe, lee ni almacena tu contraseña", ignoreCase = true)
        )
    }

    @Test
    fun `policy states session cookies are removable via disconnect`() {
        val text = allPolicyText()
        assertTrue(text.contains("cookies de sesión", ignoreCase = true))
        assertTrue(text.contains("Desconectar", ignoreCase = true))
        assertTrue(text.contains("elimina las cookies de sesión", ignoreCase = true))
    }

    @Test
    fun `policy does not claim no accounts exist`() {
        val text = allPolicyText()
        assertFalse(text.contains("Sin Cuentas", ignoreCase = true))
        assertFalse(text.contains("cuentas de usuario", ignoreCase = true))
    }

    @Test
    fun `policy does not claim all processing is fully local`() {
        val text = allPolicyText()
        assertFalse(text.contains("100% local", ignoreCase = true))
        assertFalse(text.contains("100 % local", ignoreCase = true))
    }

    private fun allPolicyText(): String =
        PrivacyPolicyContent.sections.joinToString("\n") { "${it.title}\n${it.content}" }
}

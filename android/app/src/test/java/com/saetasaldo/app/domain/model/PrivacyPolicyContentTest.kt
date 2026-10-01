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
}

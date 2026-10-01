package com.saetasaldo.app.data.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNfcManagerTest {

    @Test
    fun `bytesToHex converts byte array to uppercase hex string`() {
        val bytes = byteArrayOf(0x04.toByte(), 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        val hex = AndroidNfcManager.bytesToHex(bytes)
        assertEquals("04A1B2C3", hex)
    }

    @Test
    fun `bytesToHex handles empty array`() {
        assertEquals("", AndroidNfcManager.bytesToHex(byteArrayOf()))
    }

    @Test
    fun `extractUidFromTag extracts uppercase hex from tag id`() {
        val tag = io.mockk.mockk<android.nfc.Tag>()
        io.mockk.every { tag.id } returns byteArrayOf(0x04.toByte(), 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        val uid = AndroidNfcManager.extractUidFromTag(tag)
        assertEquals("04A1B2C3", uid)
    }

    @Test
    fun `extractUidFromTag returns empty string when tag id is null`() {
        val tag = io.mockk.mockk<android.nfc.Tag>()
        io.mockk.every { tag.id } returns null
        val uid = AndroidNfcManager.extractUidFromTag(tag)
        assertEquals("", uid)
    }
}

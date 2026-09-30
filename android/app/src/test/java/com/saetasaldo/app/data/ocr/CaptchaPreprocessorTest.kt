package com.saetasaldo.app.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaPreprocessorTest {

    @Test
    fun `cleanOcrOutput filters unwanted noise and symbols`() {
        val dirty = " 4B-8Y. "
        val cleaned = CaptchaPreprocessor.cleanOcrOutput(dirty)
        assertEquals("4B8Y", cleaned)
    }

    @Test
    fun `cleanOcrOutput retains valid alphanumeric characters`() {
        val valid = "k8M2"
        val cleaned = CaptchaPreprocessor.cleanOcrOutput(valid)
        assertEquals("K8M2", cleaned)
    }

    @Test
    fun `cleanOcrOutput handles lowercase and special characters`() {
        val dirty = "a#b$ c%9! "
        val cleaned = CaptchaPreprocessor.cleanOcrOutput(dirty)
        assertEquals("ABC9", cleaned)
    }

    @Test
    fun `isLikelyValidCaptcha checks length between 4 and 6`() {
        assertFalse(CaptchaPreprocessor.isLikelyValid(""))
        assertFalse(CaptchaPreprocessor.isLikelyValid("12"))
        assertFalse(CaptchaPreprocessor.isLikelyValid("123"))
        assertTrue(CaptchaPreprocessor.isLikelyValid("4A8K"))
        assertTrue(CaptchaPreprocessor.isLikelyValid("AB12C"))
        assertTrue(CaptchaPreprocessor.isLikelyValid("AB12C3"))
        assertFalse(CaptchaPreprocessor.isLikelyValid("AB12C34"))
    }
}

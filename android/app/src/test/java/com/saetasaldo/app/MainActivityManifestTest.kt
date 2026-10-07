package com.saetasaldo.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MainActivityManifestTest {

    @Test
    fun `main activity handles rotation and dark mode without being recreated`() {
        val app = RuntimeEnvironment.getApplication()
        val info = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0)
        val required = ActivityInfo.CONFIG_ORIENTATION or ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_UI_MODE
        assertEquals(required, info.configChanges and required)
    }
}

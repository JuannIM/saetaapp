package com.saetasaldo.app.data.local

import com.saetasaldo.app.domain.model.CardWallet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FareStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `set persists the fare so a new instance reads it back`() {
        FareStore(context).set(1700.0)
        assertEquals(1700.0, FareStore(context).get(), 0.001)
    }

    @Test
    fun `non-positive or NaN input is ignored`() {
        val store = FareStore(context)
        store.set(0.0)
        store.set(-50.0)
        store.set(Double.NaN)
        assertEquals(CardWallet.DEFAULT_FARE, store.get(), 0.001)
    }

    @Test
    fun `get defaults to the card wallet default fare`() {
        assertEquals(CardWallet.DEFAULT_FARE, FareStore(context).get(), 0.001)
    }
}

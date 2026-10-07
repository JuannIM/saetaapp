package com.saetasaldo.app.data.repository

import androidx.room.Room
import com.saetasaldo.app.data.local.SaetaDatabase
import com.saetasaldo.app.domain.model.CardBalanceUpdate
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CardRepositoryRoomTest {

    private lateinit var db: SaetaDatabase
    private lateinit var repository: CardRepositoryImpl

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, SaetaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CardRepositoryImpl(
            db.cardDao(),
            db.balanceHistoryDao(),
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `balance updates and card edits keep earlier history`() = runBlocking {
        val first = repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 1000.0, null, null, null))
        repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 800.0, null, null, null))
        repository.saveCard(repository.getCardById(first.id)!!.copy(name = "Renombrada"))

        val history = repository.getHistoryForCard(first.id).first()
        assertEquals(listOf(800.0, 1000.0), history.map { it.balance })
        assertEquals(listOf(-200.0, 0.0), history.map { it.difference })
        val card = repository.getCardById(first.id)!!
        assertEquals("Renombrada", card.name)
        assertEquals(800.0, card.currentBalance!!, 0.001)
    }

    @Test
    fun `deleting a card still removes its history`() = runBlocking {
        val card = repository.applyBalanceUpdate(CardBalanceUpdate("12345678", 1000.0, null, null, null))
        repository.deleteCard(card)
        assertTrue(repository.getHistoryForCard(card.id).first().isEmpty())
    }
}

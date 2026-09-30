package com.saetasaldo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.saetasaldo.app.data.local.entity.CardEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY isFavorite DESC, name ASC")
    fun getAllCardsFlow(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT * FROM cards WHERE cardNumber = :cardNumber LIMIT 1")
    suspend fun getCardByNumber(cardNumber: String): CardEntity?

    @Query("SELECT * FROM cards WHERE nfcUid = :uid LIMIT 1")
    suspend fun getCardByNfcUid(uid: String): CardEntity?

    @Query("SELECT * FROM cards WHERE isFavorite = 1 LIMIT 1")
    suspend fun getFavoriteCard(): CardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity)

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("UPDATE cards SET isFavorite = 0")
    suspend fun clearFavorites()

    @Query("UPDATE cards SET isFavorite = 1 WHERE id = :id")
    suspend fun setFavorite(id: String)
}

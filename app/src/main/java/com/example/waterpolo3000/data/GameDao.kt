package com.example.waterpolo3000.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM game")
    fun getAll(): Flow<List<Game>>

    @Query("SELECT * FROM game WHERE guid = :guid")
    fun getGame(guid: String): Flow<Game>

    @Query("SELECT * FROM game WHERE guid = :guid LIMIT 1")
    suspend fun getGameDirect(guid: String): Game?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(game: List<Game>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(game: Game)
}
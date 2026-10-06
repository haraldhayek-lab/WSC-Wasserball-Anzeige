package com.example.waterpolo3000.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Query("SELECT * FROM player ORDER BY playerLastName, playerFirstName")
    fun getAll(): Flow<List<Player>>

    @Query("SELECT * FROM player WHERE guid = :guid")
    fun getPlayer(guid: String): Flow<Player>

    @Query("SELECT * FROM player WHERE guid IN (:guids)")
    suspend fun getPlayersByGuidsDirect(guids: List<String>): List<Player>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(player: Array<Player>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: Player)

    @Query("UPDATE player SET playerFirstName=:firstName WHERE player.guid=:guid")
    suspend fun updatePlayerFirstName(guid: String, firstName: String)

    @Query("UPDATE player SET playerFirstName=:firstName, playerLastName=:lastName, lastUpdated=:lastUpdated WHERE player.guid=:guid")
    suspend fun updatePlayer(guid: String, firstName: String, lastName: String, lastUpdated: Long)
}
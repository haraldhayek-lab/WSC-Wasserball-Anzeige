package com.example.waterpolo3000.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participant")
    fun getAll(): Flow<List<Participant>>

    @Query("SELECT * FROM participant WHERE game = :guid")
    fun getAllParticipantFromGame(guid: String): Flow<List<Participant>>

    @Query("SELECT * FROM participant WHERE game = :guid")
    suspend fun getAllParticipantFromGameDirect(guid: String): List<Participant>

    @Query("SELECT * FROM participant WHERE guid = :guid")
    fun getParticipant(guid: String): Participant

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(participant: List<Participant>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(participant: Participant)
}
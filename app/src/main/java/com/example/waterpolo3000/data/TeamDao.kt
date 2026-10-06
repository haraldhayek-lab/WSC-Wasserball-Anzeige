package com.example.waterpolo3000.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM team ORDER BY teamName")
    fun getAll(): Flow<List<Team>>

    @Query("SELECT * FROM team WHERE guid = :guid")
    fun getTeam(guid: String): Flow<Team>

    @Query("SELECT * FROM team WHERE guid IN (:guids)")
    suspend fun getTeamsByGuidsDirect(guids: List<String>): List<Team>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(team: List<Team>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(team: Team)
}
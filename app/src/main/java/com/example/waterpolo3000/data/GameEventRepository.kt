package com.example.waterpolo3000.data

import android.content.ContentValues
import android.util.Log
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.EXCLUSION_TYPE_MAXIMUM
import com.example.waterpolo3000.utilities.EXCLUSION_TYPE_MINIMUM
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameEventRepository @Inject constructor(private val gameEventDao: GameEventDao) {

    fun getGameEvents() = gameEventDao.getAllGameEventPlayer()

    fun getGameEventsByGuid(guids: List<String>) =  gameEventDao.getAllGameEventPlayerByGuid(guids)

    fun getAll() = gameEventDao.getAll()

    fun getProtocolForTeam(cap: String, exclTypeMin: Int, exclTypeMax: Int, goalTypeMin: Int, goalTypeMax: Int) =
        gameEventDao.getProtocolTeamWithPlayerId(cap, exclTypeMin, exclTypeMax, goalTypeMin, goalTypeMax)

    fun getProtocolByGameEventType(typeMin: Int, typeMax: Int) = gameEventDao.getProtocolOrderedByGameEventType(typeMin, typeMax)

    fun getProtocolGoalType(typeMin: Int, typeMax: Int) = gameEventDao.getProtocolOrderedByGoals(typeMin, typeMax)

    fun getGameResult(goalTypeMin: Int, goalTypeMax: Int, gameSection: IntArray) =
        gameEventDao.getResult(goalTypeMin, goalTypeMax, gameSection)

    fun getExByPlayer(cap: String, number: Int) =
        gameEventDao.getExclusionByPlayer(cap, number, EXCLUSION_TYPE_MINIMUM, EXCLUSION_TYPE_MAXIMUM)

    fun getPlayerNames(cap: String) = gameEventDao.getNamesPlayer(cap)

    fun getTimeoutByTeam(cap: String) = gameEventDao.getTimeout(cap)

    suspend fun getVisibleLogLineCountBySection(gameSection: Int) =
        gameEventDao.getVisibleLogLineCountBySection(gameSection)

    suspend fun deleteGameEventsBySection(gameSection: Int, lastUpdated: Long) =
        gameEventDao.updateToDeleteBySection(gameSection, lastUpdated)

}
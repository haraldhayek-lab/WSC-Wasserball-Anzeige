package com.example.waterpolo3000.utilities

import android.content.Context

data class GameMeta(
    val competition: String,
    val gameNumber: String
)

object GameMetaCache {
    private const val PREFS_NAME = "wsc_game_meta"
    private const val KEY_COMPETITION = "competition"
    private const val KEY_GAME_NUMBER = "game_number"

    fun load(context: Context): GameMeta {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GameMeta(
            competition = preferences.getString(KEY_COMPETITION, "") ?: "",
            gameNumber = preferences.getString(KEY_GAME_NUMBER, "") ?: ""
        )
    }

    fun save(context: Context, gameMeta: GameMeta) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COMPETITION, gameMeta.competition)
            .putString(KEY_GAME_NUMBER, gameMeta.gameNumber)
            .apply()
    }
}
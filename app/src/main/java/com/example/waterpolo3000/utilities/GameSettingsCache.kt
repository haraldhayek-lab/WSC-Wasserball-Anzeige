package com.example.waterpolo3000.utilities

import android.content.Context

data class GameStandards(
    val numberOfGameSection: Int,
    val overtimeEnabled: Boolean,
    val psoEnabled: Boolean,
    val timeIsBrutto: Boolean = DEFAULT_TIME_IS_BRUTTO,
    val gameSectionLength: Int,
    val shotclockLongLength: Int,
    val shotclockShortLength: Int,
    val pauseLongLength: Int,
    val pauseShortLength: Int,
    val pauseOtPsoLength: Int,
    val timeoutLength: Int,
    val maxTimeout: Int
)

object GameSettingsCache {
    private const val PREFS_NAME = "wsc_game_settings"
    private const val KEY_NUMBER_OF_GAME_SECTION = "number_of_game_section"
    private const val KEY_OVERTIME_ENABLED = "overtime_enabled"
    private const val KEY_PSO_ENABLED = "pso_enabled"
    private const val KEY_GAME_SECTION_LENGTH = "game_section_length"
    private const val KEY_SHOTCLOCK_LONG_LENGTH = "shotclock_long_length"
    private const val KEY_SHOTCLOCK_SHORT_LENGTH = "shotclock_short_length"
    private const val KEY_PAUSE_LONG_LENGTH = "pause_long_length"
    private const val KEY_PAUSE_SHORT_LENGTH = "pause_short_length"
    private const val KEY_PAUSE_OT_PSO_LENGTH = "pause_ot_pso_length"
    private const val KEY_TIMEOUT_LENGTH = "timeout_length"
    private const val KEY_MAX_TIMEOUT = "max_timeout"
    private const val KEY_TIME_IS_BRUTTO = "time_is_brutto"

    fun load(context: Context): GameStandards {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GameStandards(
            numberOfGameSection = preferences.getInt(KEY_NUMBER_OF_GAME_SECTION, DEFAULT_NUMBER_OF_GAME_SECTION),
            overtimeEnabled = preferences.getBoolean(KEY_OVERTIME_ENABLED, DEFAULT_OVERTIME_ENABLED),
            psoEnabled = preferences.getBoolean(KEY_PSO_ENABLED, DEFAULT_PSO_ENABLED),
            timeIsBrutto = preferences.getBoolean(KEY_TIME_IS_BRUTTO, DEFAULT_TIME_IS_BRUTTO),
            gameSectionLength = preferences.getInt(KEY_GAME_SECTION_LENGTH, DEFAULT_GAME_SECTION_LENGTH),
            shotclockLongLength = preferences.getInt(KEY_SHOTCLOCK_LONG_LENGTH, DEFAULT_SHOTCLOCK_BIG_LENGTH),
            shotclockShortLength = preferences.getInt(KEY_SHOTCLOCK_SHORT_LENGTH, DEFAULT_SHOTCLOCK_SMALL_LENGTH),
            pauseLongLength = preferences.getInt(KEY_PAUSE_LONG_LENGTH, DEFAULT_PAUSE_LONG_LENGTH),
            pauseShortLength = preferences.getInt(KEY_PAUSE_SHORT_LENGTH, DEFAULT_PAUSE_SHORT_LENGTH),
            pauseOtPsoLength = preferences.getInt(KEY_PAUSE_OT_PSO_LENGTH, DEFAULT_PAUSE_OT_PSO_LENGTH),
            timeoutLength = preferences.getInt(KEY_TIMEOUT_LENGTH, DEFAULT_TIMEOUT_LENGTH),
            maxTimeout = preferences.getInt(KEY_MAX_TIMEOUT, DEFAULT_MAX_TIMEOUT)
        )
    }

    fun save(context: Context, standards: GameStandards) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_NUMBER_OF_GAME_SECTION, standards.numberOfGameSection)
            .putBoolean(KEY_OVERTIME_ENABLED, standards.overtimeEnabled)
            .putBoolean(KEY_PSO_ENABLED, standards.psoEnabled)
            .putBoolean(KEY_TIME_IS_BRUTTO, standards.timeIsBrutto)
            .putInt(KEY_GAME_SECTION_LENGTH, standards.gameSectionLength)
            .putInt(KEY_SHOTCLOCK_LONG_LENGTH, standards.shotclockLongLength)
            .putInt(KEY_SHOTCLOCK_SHORT_LENGTH, standards.shotclockShortLength)
            .putInt(KEY_PAUSE_LONG_LENGTH, standards.pauseLongLength)
            .putInt(KEY_PAUSE_SHORT_LENGTH, standards.pauseShortLength)
            .putInt(KEY_PAUSE_OT_PSO_LENGTH, standards.pauseOtPsoLength)
            .putInt(KEY_TIMEOUT_LENGTH, standards.timeoutLength)
            .putInt(KEY_MAX_TIMEOUT, standards.maxTimeout)
            .apply()
    }
}
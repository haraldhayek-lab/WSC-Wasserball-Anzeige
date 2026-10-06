package com.example.waterpolo3000.utilities

/**
 * Constants used throughout the app.
 */

// database
const val DATABASE_NAME             = "waterpolo-db"
const val GAME_EVENT_TYPES_FILENAME = "gameEventTypeValues.json"
const val FUNCTION_TYPES_FILENAME   = "functionTypeValues.json"

// default values
var DEFAULT_NUMBER_OF_GAME_SECTION = 4
var DEFAULT_OVERTIME_ENABLED      = false
var DEFAULT_PSO_ENABLED           = false
var DEFAULT_GAME_SECTION_LENGTH    = 480
var DEFAULT_SHOTCLOCK_BIG_LENGTH   = 30
var DEFAULT_SHOTCLOCK_SMALL_LENGTH = 20
var DEFAULT_PAUSE_LONG_LENGTH      = 180
var DEFAULT_PAUSE_SHORT_LENGTH     = 120
var DEFAULT_PAUSE_OT_PSO_LENGTH    = 120
var DEFAULT_TIMEOUT_LENGTH         = 60
var DEFAULT_MAX_TIMEOUT            = 2
var DEFAULT_TIME_IS_BRUTTO         = false
const val PLAYER_COUNT             = 14
const val BLUE  = "blue"
const val WHITE = "white"
var GAME_SNAPSHOT_FILENAME_PREFIX = "wsc_game_"

// goal and exclusion types
const val GOAL_TYPE_MINIMUM      = 100
const val GOAL_TYPE_MAXIMUM      = 199
const val EXCLUSION_TYPE_MINIMUM = 200
const val EXCLUSION_TYPE_MAXIMUM = 299

// gameEvent types
const val NEW_SHOTCLOCK_SMALL = 1
const val NEW_SHOTCLOCK_BIG   = 2
const val START_TIME          = 3
const val STOP_TIME           = 4
const val SHOTCLOCK_EXPIRED   = 5
const val GAMESECTION_EXPIRED = 6
const val START_GAME          = 7
const val END_GAME            = 8
const val TIMEOUT             = 9

// function types
const val FUNCTION_TYPE_GOALKEEPER       = 1
const val FUNCTION_TYPE_FIELDPLAYER      = 2
const val FUNCTION_TYPE_COACH            = 3
const val FUNCTION_TYPE_PLAYER_COACH     = 4
const val FUNCTION_TYPE_GOALKEEPER_COACH = 5
const val FUNCTION_TYPE_REFEREE          = 6
const val FUNCTION_TYPE_OTHER            = 7

// led boards
const val MAIN_BOARD_BT_NAME  = "mainboard"
const val SHOTCLOCK_1_BT_NAME = "sh1"
const val SHOTCLOCK_2_BT_NAME = "sh2"
const val SHOTCLOCK_3_BT_NAME = "sh3"
const val SHOTCLOCK_4_BT_NAME = "sh4"
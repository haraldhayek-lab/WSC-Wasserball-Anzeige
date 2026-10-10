package com.example.waterpolo3000.game

import android.app.Application
import android.content.Context
import android.content.ContentValues.TAG
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import android.util.Log
import com.example.waterpolo3000.data.Game
import com.example.waterpolo3000.data.Participant
import com.example.waterpolo3000.data.Player
import com.example.waterpolo3000.data.Team
import com.example.waterpolo3000.utilities.*
import com.example.waterpolo3000.viewmodels.GameViewModel
import java.util.*

class GameControl() {

    companion object {
        private data class ExclusionTracking(val offsetMillis: Long, val eventType: Int)

        lateinit var myViewModel: GameViewModel

        // time related stuff
        var gameSectionLength = DEFAULT_GAME_SECTION_LENGTH
        var currentCountdown: Long = gameSectionLength.toLong() * 1000

        private var shotclockLongLength = DEFAULT_SHOTCLOCK_BIG_LENGTH
        private var shotclockShortLength = DEFAULT_SHOTCLOCK_SMALL_LENGTH
        var currentCountdownShotclock: Long = shotclockLongLength.toLong() * 1000

        var gameStarted = false
        private var timeIsRunning = false
        private var shotclockIsRunning = false
        private var currentGameSection = 1
        var numberOfGameSection = DEFAULT_NUMBER_OF_GAME_SECTION
        private var overtimeEnabled = DEFAULT_OVERTIME_ENABLED
        private var psoEnabled = DEFAULT_PSO_ENABLED
        private var maxGameSection = calculateMaxGameSection()
        private var selectedContinuationMode = ""
        private var gameFinished = false
        private val continuationSectionLabels = mutableMapOf<Int, String>()
        private var overtimeCounter = 0
        private val psoEventTimeBySection = mutableMapOf<Int, Long>()

        var timerCountdown: CountDownTimer? = null
        private var timerShotclock: CountDownTimer? = null
        var timerPause: CountDownTimer? = null
        var currentGameGuid = UUID.randomUUID().toString()
        var savedCountdown: Long = 0
        private var toneGenerator: ToneGenerator? = null
        private var pauseWarningPlayed = false
        private var activePauseMode = 0
        private var timeoutSelectionWasRunning = false
        private var timeoutSelectionShotclockWasRunning = false
        private var displayedGoalsWhite = "0"
        private var displayedGoalsBlue = "0"
        private val mainBoardPlayerExclusionCounts = mutableMapOf<String, Int>()
        var competitionName = ""
        var gameNumberLabel = ""
        var gameEndMode = ""
        var gameSpecialNotes = ""
        private var protocolSaveEnabled = false

        var game = Game(currentGameGuid)
        var teamBlue = Team(UUID.randomUUID().toString())
        var teamWhite = Team(UUID.randomUUID().toString())
        var playersListBlue = Array(PLAYER_COUNT) { Player(UUID.randomUUID().toString()) }
        var playersListWhite = Array(PLAYER_COUNT) { Player(UUID.randomUUID().toString()) }
        var playersListAll = playersListBlue + playersListWhite
        private var participantListBlueTemp = Array(PLAYER_COUNT) {i -> Participant(UUID.randomUUID().toString(), game.guid, playersListBlue[i].guid, BLUE, i+1, teamBlue.guid, if(i==0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER)}
        private var participantTeamBlue = Participant(UUID.randomUUID().toString(), game.guid, teamBlue.guid, BLUE, 0, teamBlue.guid, FUNCTION_TYPE_OTHER)
        var participantListBlue = listOf(participantTeamBlue) + participantListBlueTemp
        private var participantListWhiteTemp = Array(PLAYER_COUNT) {i -> Participant(UUID.randomUUID().toString(), game.guid, playersListWhite[i].guid, WHITE, i+1, teamWhite.guid, if(i==0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER)}
        private var participantTeamWhite = Participant(UUID.randomUUID().toString(), game.guid, teamWhite.guid, WHITE, 0, teamWhite.guid, FUNCTION_TYPE_OTHER)
        var participantListWhite = listOf(participantTeamWhite) + participantListWhiteTemp
        var participantListAll = participantListBlue + participantListWhite

        private val excutionTimeOffset = mutableMapOf<String, ExclusionTracking>()

        fun init() {
            setDefaults()
            recalculateMaxGameSection()
            resetDisplaySyncState()
            applyGameMetaToCurrentGame()

            // create all initial records for the db
            teamBlue.teamName = "BLUE"
            teamBlue.teamLocation = "Blaudorf"
            teamWhite.teamName = "WHITE"
            teamWhite.teamLocation = "Weissdorf"
            myViewModel.initAll(game, listOf(teamBlue, teamWhite), playersListAll, participantListAll)
        }

        fun newGame() {
            resetDisplaySyncState()
            clearProtocolSaveState()
            activePauseMode = 0
            currentCountdown = gameSectionLength.toLong() * 1000
            currentCountdownShotclock = shotclockLongLength.toLong() * 1000
            gameStarted = false
            timeIsRunning = false
            shotclockIsRunning = false
            currentGameSection = 1
            selectedContinuationMode = ""
            gameFinished = false
            continuationSectionLabels.clear()
            overtimeCounter = 0
            psoEventTimeBySection.clear()
            recalculateMaxGameSection()
            myViewModel.setCurrentGameSection(currentGameSection)
            timerCountdown = null
            timerShotclock = null
            timerPause = null
            timeoutSelectionWasRunning = false
            timeoutSelectionShotclockWasRunning = false
            currentGameGuid = UUID.randomUUID().toString()
            savedCountdown = 0
            game = Game(currentGameGuid)
            applyGameMetaToCurrentGame()
            teamBlue = Team(UUID.randomUUID().toString())
            teamWhite = Team(UUID.randomUUID().toString())
            teamBlue.teamName = "BLUE"
            teamBlue.teamLocation = "Blaudorf"
            teamWhite.teamName = "WHITE"
            teamWhite.teamLocation = "Weissdorf"
            createPlayerList()
            playersListAll = playersListBlue + playersListWhite
            createParticipantList()
            participantListAll = participantListBlue + participantListWhite
            myViewModel.initAll(game, listOf(teamBlue, teamWhite), playersListAll, participantListAll)
            myViewModel.timeControlAvailable(true)
            excutionTimeOffset.clear()

            setMainTimeDefaults()
            setShotclockDefaults(shotclockLongLength)

            val seconds = gameSectionLength % 60
            val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
            ProcessBT.sendMessageToMainBoard("timeGame%${gameSectionLength / 60}:$secondsString%default")
            ProcessBT.sendMessageToAllShotClock("time%${gameSectionLength / 60}:$secondsString%default")
            Thread.sleep(200)
            ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockLongLength%default%0")

            Thread.sleep(100)
            playersListBlue.forEachIndexed { index, _ ->
                ProcessBT.sendMessageToMainBoard("player%$BLUE%${index + 1}%0")
                Thread.sleep(100)
            }
            playersListWhite.forEachIndexed { index, _ ->
                ProcessBT.sendMessageToMainBoard("player%$WHITE%${index + 1}%0")
                Thread.sleep(100)
            }
        }

        private fun createPlayerList() {
            playersListBlue = Array(PLAYER_COUNT) { Player(UUID.randomUUID().toString()) }
            playersListWhite = Array(PLAYER_COUNT) { Player(UUID.randomUUID().toString()) }
        }

        private fun createParticipantList() {
            participantListBlueTemp = Array(PLAYER_COUNT) {i -> Participant(UUID.randomUUID().toString(), game.guid, playersListBlue[i].guid, BLUE, i+1, teamBlue.guid, if(i==0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER)}
            participantTeamBlue = Participant(UUID.randomUUID().toString(), game.guid, teamBlue.guid, BLUE, 0, teamBlue.guid, FUNCTION_TYPE_OTHER)
            participantListBlue = listOf(participantTeamBlue) + participantListBlueTemp
            participantListWhiteTemp = Array(PLAYER_COUNT) {i -> Participant(UUID.randomUUID().toString(), game.guid, playersListWhite[i].guid, WHITE, i+1, teamWhite.guid, if(i==0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER)}
            participantTeamWhite = Participant(UUID.randomUUID().toString(), game.guid, teamWhite.guid, WHITE, 0, teamWhite.guid, FUNCTION_TYPE_OTHER)
            participantListWhite = listOf(participantTeamWhite) + participantListWhiteTemp
        }

        private fun setDefaults() {

            // main time
            setMainTimeDefaults()
            // shotclock
            setShotclockDefaults(DEFAULT_SHOTCLOCK_BIG_LENGTH)
        }

        fun setGameMeta(competition: String, gameNumber: String) {
            competitionName = competition.trim()
            gameNumberLabel = gameNumber.trim()
            applyGameMetaToCurrentGame()
        }

        fun getGameMetaDisplayText(): String {
            val competitionDisplay = if (competitionName.isBlank()) "-" else competitionName
            val gameNumberDisplay = if (gameNumberLabel.isBlank()) "-" else gameNumberLabel
            return "Bewerb: $competitionDisplay | Spiel-Nr.: $gameNumberDisplay"
        }

        private fun applyGameMetaToCurrentGame() {
            game.competition = competitionName
        }

        private fun setMainTimeDefaults() {
            myViewModel.setNewMainTime(gameSectionLength / 60, gameSectionLength % 60, 0)
        }

        private fun resetDisplaySyncState() {
            displayedGoalsWhite = "0"
            displayedGoalsBlue = "0"
            mainBoardPlayerExclusionCounts.clear()
            for (number in 1..PLAYER_COUNT) {
                mainBoardPlayerExclusionCounts["$BLUE:$number"] = 0
                mainBoardPlayerExclusionCounts["$WHITE:$number"] = 0
            }
        }

        private fun setShotclockDefaults(default: Int) {
            myViewModel.setNewShotclock(default % 60, 0)
        }

        private fun recalculateMaxGameSection() {
            maxGameSection = calculateMaxGameSection()
        }

        private fun calculateMaxGameSection(): Int {
            return numberOfGameSection
        }

        fun restoreGameStandards(standards: GameStandards) {
            numberOfGameSection = standards.numberOfGameSection
            overtimeEnabled = standards.overtimeEnabled
            psoEnabled = standards.psoEnabled
            gameSectionLength = standards.gameSectionLength
            shotclockLongLength = standards.shotclockLongLength
            shotclockShortLength = standards.shotclockShortLength

            DEFAULT_NUMBER_OF_GAME_SECTION = standards.numberOfGameSection
            DEFAULT_OVERTIME_ENABLED = standards.overtimeEnabled
            DEFAULT_PSO_ENABLED = standards.psoEnabled
            DEFAULT_GAME_SECTION_LENGTH = standards.gameSectionLength
            DEFAULT_SHOTCLOCK_BIG_LENGTH = standards.shotclockLongLength
            DEFAULT_SHOTCLOCK_SMALL_LENGTH = standards.shotclockShortLength
            DEFAULT_PAUSE_LONG_LENGTH = standards.pauseLongLength
            DEFAULT_PAUSE_SHORT_LENGTH = standards.pauseShortLength
            DEFAULT_PAUSE_OT_PSO_LENGTH = standards.pauseOtPsoLength
            DEFAULT_TIMEOUT_LENGTH = standards.timeoutLength
            DEFAULT_MAX_TIMEOUT = standards.maxTimeout
            DEFAULT_TIME_IS_BRUTTO = standards.timeIsBrutto

            recalculateMaxGameSection()
            currentCountdown = (gameSectionLength * 1000).toLong()
            currentCountdownShotclock = (shotclockLongLength * 1000).toLong()
        }

        fun applyGameStandards(
            newNumberOfGameSection: Int,
            newOvertimeEnabled: Boolean,
            newPsoEnabled: Boolean,
            newGameSectionLength: Int,
            newShotclockLongLength: Int,
            newShotclockShortLength: Int,
            newPauseLongLength: Int,
            newPauseShortLength: Int,
            newPauseOtPsoLength: Int,
            newTimeoutLength: Int,
            newMaxTimeout: Int,
            newTimeIsBrutto: Boolean
        ) {
            numberOfGameSection = newNumberOfGameSection
            overtimeEnabled = newOvertimeEnabled
            psoEnabled = newPsoEnabled
            gameSectionLength = newGameSectionLength
            shotclockLongLength = newShotclockLongLength
            shotclockShortLength = newShotclockShortLength

            DEFAULT_NUMBER_OF_GAME_SECTION = newNumberOfGameSection
            DEFAULT_OVERTIME_ENABLED = newOvertimeEnabled
            DEFAULT_PSO_ENABLED = newPsoEnabled
            DEFAULT_GAME_SECTION_LENGTH = newGameSectionLength
            DEFAULT_SHOTCLOCK_BIG_LENGTH = newShotclockLongLength
            DEFAULT_SHOTCLOCK_SMALL_LENGTH = newShotclockShortLength
            DEFAULT_PAUSE_LONG_LENGTH = newPauseLongLength
            DEFAULT_PAUSE_SHORT_LENGTH = newPauseShortLength
            DEFAULT_PAUSE_OT_PSO_LENGTH = newPauseOtPsoLength
            DEFAULT_TIMEOUT_LENGTH = newTimeoutLength
            DEFAULT_MAX_TIMEOUT = newMaxTimeout
            DEFAULT_TIME_IS_BRUTTO = newTimeIsBrutto
            recalculateMaxGameSection()

            myViewModel.setShotclockButtonLabels(newShotclockLongLength, newShotclockShortLength)

            if (!gameStarted) {
                currentCountdown = (gameSectionLength * 1000).toLong()
                currentCountdownShotclock = (shotclockLongLength * 1000).toLong()
                setGameTimeEdit()
                setShotclockEdit()
            }
        }

        fun setDisplayedResult(white: String, blue: String) {
            displayedGoalsWhite = white
            displayedGoalsBlue = blue
        }

        fun setDisplayedExclusionCount(cap: String, number: Int, count: Int) {
            mainBoardPlayerExclusionCounts["$cap:$number"] = count
        }

        fun getDisplayedResultCommand(): String {
            return "result%$displayedGoalsWhite:$displayedGoalsBlue"
        }

        fun isDrawByDisplayedResult(): Boolean {
            val white = displayedGoalsWhite.toIntOrNull() ?: return false
            val blue = displayedGoalsBlue.toIntOrNull() ?: return false
            return white == blue
        }

        fun markGameEnded(endMode: String, specialNotes: String) {
            gameEndMode = endMode.trim()
            gameSpecialNotes = specialNotes.trim()
            protocolSaveEnabled = true
            if (gameEndMode == "OT" || gameEndMode == "PSO") {
                selectedContinuationMode = gameEndMode
            }
            gameFinished = true
        }

        fun isProtocolSaveEnabled(): Boolean {
            return protocolSaveEnabled
        }

        fun clearProtocolSaveState() {
            gameEndMode = ""
            gameSpecialNotes = ""
            protocolSaveEnabled = false
        }

        fun reopenEndedGameForCorrection() {
            if (!gameFinished && !protocolSaveEnabled) {
                return
            }
            gameFinished = false
            selectedContinuationMode = ""
            clearProtocolSaveState()
            myViewModel.timeControlAvailable(true)
        }

        fun getContinuationModeLabel(): String {
            return selectedContinuationMode
        }

        fun getSectionLabel(section: Int): String {
            if (section <= numberOfGameSection) {
                return section.toString()
            }
            continuationSectionLabels[section]?.let { return it }
            return when (selectedContinuationMode) {
                "OT" -> "OT-${section - numberOfGameSection}"
                "PSO" -> "PSO"
                else -> section.toString()
            }
        }

        fun getContinuationSectionNumbers(): IntArray {
            return continuationSectionLabels.keys.sorted().toIntArray()
        }

        fun getAvailableContinuationModes(): List<String> {
            if (gameFinished) {
                return emptyList()
            }
            val options = mutableListOf<String>()
            if (overtimeEnabled && selectedContinuationMode != "PSO") {
                options.add("OT")
            }
            if (psoEnabled) {
                options.add("PSO")
            }
            return options
        }

        private fun getTotalElapsedGameMillis(): Long {
            val sectionsCompleted = (currentGameSection - 1).coerceAtLeast(0)
            val elapsedInSection = ((gameSectionLength * 1000L) - currentCountdown).coerceAtLeast(0L)
            return sectionsCompleted * gameSectionLength * 1000L + elapsedInSection
        }

        private fun formatTeamNameForMainBoard(name: String): String {
            return name.replace("%", " ").trim().take(5)
        }

        fun getTeamNameCommands(): List<String> {
            val whiteName = formatTeamNameForMainBoard(teamWhite.teamName)
            val blueName = formatTeamNameForMainBoard(teamBlue.teamName)
            return listOf(
                "team%$WHITE%$whiteName",
                "team%$BLUE%$blueName",
                "teamWhite%$whiteName",
                "teamBlue%$blueName"
            )
        }

        fun getPlayerExclusionCommands(): List<String> {
            val commands = mutableListOf<String>()
            for (number in 1..PLAYER_COUNT) {
                commands.add("player%$BLUE%$number%${mainBoardPlayerExclusionCounts["$BLUE:$number"] ?: 0}")
            }
            for (number in 1..PLAYER_COUNT) {
                commands.add("player%$WHITE%$number%${mainBoardPlayerExclusionCounts["$WHITE:$number"] ?: 0}")
            }
            return commands
        }

        fun processGameEvent(event: String, player: String) {
            if (event.toInt() in GOAL_TYPE_MINIMUM..GOAL_TYPE_MAXIMUM) {
                addGoal(event.toInt(), player)
            } else {
                addExclusion(event.toInt(), player)
                if (isTrackedExclusionEventType(event.toInt())) {
                    excutionTimeOffset[player] = ExclusionTracking(getTotalElapsedGameMillis(), event.toInt())
                    val duration = getExclusionDurationMillisForType(event.toInt())
                    myViewModel.exclusionTime.value = "$player:${formatExclusionTime(duration)}"
                }
            }
        }

        private fun addGoal(event: Int, player: String) {
            val participant =
                if (player.split("_")[1] == "B") participantListBlue[player.split("_")[2].toInt()] else participantListWhite[player.split("_")[2].toInt()]
            myViewModel.storeGameEvent(event, getEventTimeForStorage(currentCountdown), currentGameSection, participant.guid)
        }

        private fun addExclusion(exclusionEvent: Int, player: String) {
            val index = player.split("_")[2].toInt()
            val participant = if (player.split("_")[1] == "B") participantListBlue[index] else participantListWhite[index]
            Log.d(TAG, "addExclusion: $exclusionEvent")
            myViewModel.storeGameEvent(exclusionEvent, getEventTimeForStorage(currentCountdown), currentGameSection, participant.guid)
        }

        private fun getEventTimeForStorage(defaultTime: Long): Long {
            if (getSectionLabel(currentGameSection) != "PSO") {
                return defaultTime
            }
            val next = psoEventTimeBySection[currentGameSection] ?: 0L
            psoEventTimeBySection[currentGameSection] = next + 1000L
            return next
        }

        fun startStopCounter() {
            if (gameFinished) {
                return
            }
            if (!gameStarted) {
                gameStarted = true
                myViewModel.storeGameEvent(START_GAME, currentCountdown, currentGameSection, "-")
            }
            if (DEFAULT_TIME_IS_BRUTTO) {
                if (timeIsRunning) {
                    timeIsRunning = false
                    timerCountdown?.cancel()
                    timerCountdown = null
                    shotclockIsRunning = false
                    timerShotclock?.cancel()
                    timerShotclock = null
                } else {
                    timeIsRunning = true
                    createTimerCountdown()
                    timerCountdown?.start()
                    if (currentCountdownShotclock > 0L) {
                        shotclockIsRunning = true
                        timerShotclock?.cancel()
                        createShotclockTimer()
                        timerShotclock?.start()
                    }
                }
                return
            }
            if (timeIsRunning) {
                timeIsRunning = false
                timerCountdown?.cancel()
                timerCountdown = null
//                myViewModel.storeGameEvent(STOP_TIME, currentCountdown, currentGameSection, "-")
            } else {
                timeIsRunning = true
                createTimerCountdown()
//                myViewModel.storeGameEvent(START_TIME, currentCountdown, currentGameSection, "-")
                timerCountdown?.start()
            }
        }

        fun startStopShotclock() {
            if (gameFinished || !DEFAULT_TIME_IS_BRUTTO) {
                return
            }
            if (!gameStarted) {
                gameStarted = true
                myViewModel.storeGameEvent(START_GAME, currentCountdown, currentGameSection, "-")
            }
            if (shotclockIsRunning) {
                shotclockIsRunning = false
                timerShotclock?.cancel()
                timerShotclock = null
            } else {
                shotclockIsRunning = true
                createShotclockTimer()
                timerShotclock?.start()
            }
        }

        // edit main time
        fun setGameTimeEdit() {
            val temp = currentCountdown
            val minutes = MyTimeConverter.getMinutesFromLong(temp)
            val seconds = MyTimeConverter.getSecondsFromLong(temp)
            myViewModel.setNewMainTime(minutes, seconds, MyTimeConverter.getSecondsSmallFromLong(temp))
            val mainBoardSecondsString = if (seconds < 10) "0$seconds" else seconds.toString()
            val color = if (minutes < 1) "red" else "default"
            ProcessBT.sendMessageToMainBoard("timeGame%$minutes:$mainBoardSecondsString%$color")
            ProcessBT.sendMessageToAllShotClock("time%$minutes:$mainBoardSecondsString%$color")
        }

        // edit shotclock
        fun setShotclockEdit(){
            val temp = currentCountdownShotclock
            val shotclockSeconds = MyTimeConverter.getSecondsFromLong(temp)
            val shotclockSecondsSmall = MyTimeConverter.getSecondsSmallFromLong(temp)
            val color = if (shotclockSeconds < 6) "red" else "default"
            val shotclockSecondsString = if (shotclockSeconds < 10) "0$shotclockSeconds" else "$shotclockSeconds"
            myViewModel.setNewShotclock(shotclockSeconds, shotclockSecondsSmall)
            ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSecondsString%$color%$shotclockSecondsSmall")
        }

        fun setPauseTimeEdit(newCountdown: Long): Boolean {
            if (activePauseMode == 0 || timerPause == null) {
                return false
            }

            currentCountdown = newCountdown
            timerPause?.cancel()
            if (activePauseMode == 1) {
                createTimerPause(currentCountdown)
            } else {
                createTimerTimeout(currentCountdown)
            }
            timerPause?.start()
            return true
        }

        fun isPauseTimerRunning(): Boolean {
            return activePauseMode != 0 && timerPause != null
        }

        fun isMainTimeRunning(): Boolean {
            return timeIsRunning
        }

        fun isShotclockRunning(): Boolean {
            return shotclockIsRunning
        }

        fun setGameTime() {
            // main time
            val currentCountdownTemp = currentCountdown
            val currentCountdownShotclockTemp = currentCountdownShotclock

            val minutes = MyTimeConverter.getMinutesFromLong(currentCountdownTemp)
            val seconds = MyTimeConverter.getSecondsFromLong(currentCountdownTemp)
            val secondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownTemp)
            myViewModel.setNewMainTime(minutes, seconds, secondsSmall)
            // set led boards
            if (secondsSmall == 9) {
                val mainBoardSecondsString = if (seconds < 10) "0$seconds" else seconds.toString()
                val color = if (minutes < 1) "red" else "default"
                ProcessBT.sendMessageToMainBoard("timeGame%$minutes:$mainBoardSecondsString%$color")
                ProcessBT.sendMessageToAllShotClock("time%$minutes:$mainBoardSecondsString%$color")
            }

            // shotclock
            val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclockTemp)
            val shotclockSecondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownShotclockTemp)
            val color = if (shotclockSeconds < 6) "red" else "default"
            val shotclockSecondsString = if (shotclockSeconds < 10) "0$shotclockSeconds" else "$shotclockSeconds"
            myViewModel.setNewShotclock(shotclockSeconds, shotclockSecondsSmall)
            ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSecondsString%$color%$shotclockSecondsSmall")

            // stop timer if shotclock reached zero
            if ((shotclockSeconds == 0 && shotclockSecondsSmall == 0) && (secondsSmall > 0 || minutes > 0 || seconds > 0) && (currentCountdownShotclockTemp != currentCountdownTemp)) {
                startStopCounter()
                playSound(1)
                if (currentCountdownTemp < (DEFAULT_SHOTCLOCK_BIG_LENGTH * 1000)) {
                    currentCountdownShotclock = currentCountdownTemp
                } else {
                    currentCountdownShotclock += DEFAULT_SHOTCLOCK_BIG_LENGTH * 1000
                }
            }

            updateExclusionCountdownState()
        }

        private fun updateExclusionCountdownState() {
            //set player btn exclusion time
            val toBeRemove = mutableListOf<String>()
            if(excutionTimeOffset.isNotEmpty()){
                val currentElapsed = getTotalElapsedGameMillis()
                excutionTimeOffset.forEach {
                    val elapsedTime = (currentElapsed - it.value.offsetMillis).coerceAtLeast(0L)
                    val exclusionDuration = getExclusionDurationMillisForType(it.value.eventType)
                    val remainingExclusionTime = (exclusionDuration - elapsedTime).coerceAtLeast(0L)
                    myViewModel.exclusionTime.value = "${it.key}:${formatExclusionTime(remainingExclusionTime)}"
                    if (remainingExclusionTime == 0L) {
                        toBeRemove.add(it.key)
                    }
                }
            }
            toBeRemove.forEach {
                excutionTimeOffset.remove(it)
            }
        }

        private fun getExclusionDurationMillis(): Long {
            return shotclockShortLength.toLong() * 1000L
        }

        private fun isTrackedExclusionEventType(eventType: Int): Boolean {
            return eventType == 200 || eventType == 202 || eventType == 203
        }

        private fun getExclusionDurationMillisForType(eventType: Int): Long {
            return when (eventType) {
                203 -> 240_000L
                200, 202 -> getExclusionDurationMillis()
                else -> 0L
            }
        }

        private fun getExclusionPlayerKey(cap: String, number: Int?): String? {
            if (number == null || number <= 0) {
                return null
            }
            val capShort = if (cap.equals(BLUE, ignoreCase = true)) "B" else "W"
            return "btn_${capShort}_$number"
        }

        private fun getElapsedGameMillisForEvent(section: Int, eventCountdownMillis: Long): Long {
            val sectionsCompleted = (section - 1).coerceAtLeast(0)
            val elapsedInSection = ((gameSectionLength * 1000L) - eventCountdownMillis).coerceAtLeast(0L)
            return sectionsCompleted * gameSectionLength * 1000L + elapsedInSection
        }

        private fun getRemainingExclusionMillisForOffset(offsetMillis: Long): Long {
            val currentElapsed = getTotalElapsedGameMillis()
            val elapsedSinceEvent = (currentElapsed - offsetMillis).coerceAtLeast(0L)
            return (getExclusionDurationMillis() - elapsedSinceEvent).coerceAtLeast(0L)
        }

        fun updateExclusionTrackingAfterGameEventEdit(
            oldEventType: Int,
            oldCap: String,
            oldNumber: Int?,
            oldSection: Int,
            oldTime: Long,
            newEventType: Int,
            newCap: String,
            newNumber: Int?,
            newSection: Int,
            newTime: Long,
        ) {
            val oldIsTracked = isTrackedExclusionEventType(oldEventType)
            val newIsTracked = isTrackedExclusionEventType(newEventType)
            val oldKey = getExclusionPlayerKey(oldCap, oldNumber)
            val newKey = getExclusionPlayerKey(newCap, newNumber)
            val oldOffset = getElapsedGameMillisForEvent(oldSection, oldTime)
            val newOffset = getElapsedGameMillisForEvent(newSection, newTime)

            if (oldIsTracked && oldKey != null) {
                if (!newIsTracked || oldKey != newKey || oldOffset != newOffset || oldEventType != newEventType) {
                    excutionTimeOffset.remove(oldKey)
                    myViewModel.exclusionTime.value = "$oldKey:${formatExclusionTime(0L)}"
                }
            }

            if (newIsTracked && newKey != null) {
                excutionTimeOffset[newKey] = ExclusionTracking(newOffset, newEventType)
                val exclusionDuration = getExclusionDurationMillisForType(newEventType)
                val currentElapsed = getTotalElapsedGameMillis()
                val elapsedSinceEvent = (currentElapsed - newOffset).coerceAtLeast(0L)
                val remaining = (exclusionDuration - elapsedSinceEvent).coerceAtLeast(0L)
                myViewModel.exclusionTime.value = "$newKey:${formatExclusionTime(remaining)}"
            }
        }

        private fun formatExclusionTime(remainingMillis: Long): String {
            val seconds = (remainingMillis / 1000L).toInt()
            val tenths = ((remainingMillis / 100L) % 10L).toInt()
            return "$seconds.$tenths"
        }

        fun newShotclockBig() {
            if ((currentCountdownShotclock == currentCountdown) || (currentCountdownShotclock == (DEFAULT_SHOTCLOCK_BIG_LENGTH * 1000).toLong())) return

            if (currentCountdown <= (DEFAULT_SHOTCLOCK_BIG_LENGTH * 1000)) {
                currentCountdownShotclock = currentCountdown
                myViewModel.setNewShotclock(null, null)
                val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclock)
                val shotclockSecondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownShotclock)
                val shotclockSecondsString = if (shotclockSeconds < 10) "0$shotclockSeconds" else "$shotclockSeconds"
                val color = if (shotclockSeconds < 6) "red" else "default"
                ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSecondsString%$color%$shotclockSecondsSmall")
            } else {
                currentCountdownShotclock = shotclockLongLength.toLong() * 1000
                setShotclockDefaults(shotclockLongLength)
                val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclock)
                ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSeconds%default%0")
            }
            restartShotclockTimerIfNeeded()
//            myViewModel.storeGameEvent(NEW_SHOTCLOCK_BIG, currentCountdown, currentGameSection, "-")
        }

        fun newShotclockSmall() {
            if ((currentCountdownShotclock == currentCountdown) || (currentCountdownShotclock == (DEFAULT_SHOTCLOCK_SMALL_LENGTH * 1000).toLong())) return

            if (currentCountdown <= (DEFAULT_SHOTCLOCK_SMALL_LENGTH * 1000)) {
                currentCountdownShotclock = currentCountdown
                myViewModel.setNewShotclock(null, null)
                val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclock)
                val shotclockSecondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownShotclock)
                val shotclockSecondsString = if (shotclockSeconds < 10) "0$shotclockSeconds" else "$shotclockSeconds"
                val color = if (shotclockSeconds < 6) "red" else "default"
                ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSecondsString%$color%$shotclockSecondsSmall")
            } else {
                currentCountdownShotclock = shotclockShortLength.toLong() * 1000
                setShotclockDefaults(shotclockShortLength)
                val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclock)
                ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSeconds%default%0")
            }
            restartShotclockTimerIfNeeded()
        }

        private fun restartShotclockTimerIfNeeded() {
            if (DEFAULT_TIME_IS_BRUTTO && shotclockIsRunning) {
                timerShotclock?.cancel()
                createShotclockTimer()
                timerShotclock?.start()
            }
        }

        private fun createShotclockTimer() {
            timerShotclock = object : CountDownTimer(currentCountdownShotclock, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    currentCountdownShotclock = millisUntilFinished
                    setShotclockOnlyTime()
                }

                override fun onFinish() {
                    shotclockIsRunning = false
                    timerShotclock = null
                    currentCountdownShotclock = 0L
                    myViewModel.setNewShotclock(0, 0)
                    ProcessBT.sendMessageToAllShotClock("shotclock%00%red%0")
                    playSound(1)
                }
            }
        }

        private fun setMainOnlyTime() {
            val currentCountdownTemp = currentCountdown
            val minutes = MyTimeConverter.getMinutesFromLong(currentCountdownTemp)
            val seconds = MyTimeConverter.getSecondsFromLong(currentCountdownTemp)
            val secondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownTemp)
            myViewModel.setNewMainTime(minutes, seconds, secondsSmall)
            if (secondsSmall == 9) {
                val mainBoardSecondsString = if (seconds < 10) "0$seconds" else seconds.toString()
                val color = if (minutes < 1) "red" else "default"
                ProcessBT.sendMessageToMainBoard("timeGame%$minutes:$mainBoardSecondsString%$color")
                ProcessBT.sendMessageToAllShotClock("time%$minutes:$mainBoardSecondsString%$color")
            }
            updateExclusionCountdownState()
        }

        private fun setShotclockOnlyTime() {
            val shotclockSeconds = MyTimeConverter.getSecondsFromLong(currentCountdownShotclock)
            val shotclockSecondsSmall = MyTimeConverter.getSecondsSmallFromLong(currentCountdownShotclock)
            val color = if (shotclockSeconds < 6) "red" else "default"
            val shotclockSecondsString = if (shotclockSeconds < 10) "0$shotclockSeconds" else "$shotclockSeconds"
            myViewModel.setNewShotclock(shotclockSeconds, shotclockSecondsSmall)
            ProcessBT.sendMessageToAllShotClock("shotclock%$shotclockSecondsString%$color%$shotclockSecondsSmall")
        }

        private fun createTimerCountdown() {
            timerCountdown = object : CountDownTimer(currentCountdown, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    if (DEFAULT_TIME_IS_BRUTTO) {
                        currentCountdown = millisUntilFinished
                        setMainOnlyTime()
                    } else {
                        currentCountdownShotclock -= (currentCountdown - millisUntilFinished)
                        currentCountdown = millisUntilFinished
                        setGameTime()
                    }
                }

                override fun onFinish() {

                    timeIsRunning = false
                    shotclockIsRunning = false
                    timerShotclock?.cancel()
                    timerShotclock = null
                    currentCountdown = 0L
                    currentCountdownShotclock = 0L
                    myViewModel.timeControlAvailable(false)
                    myViewModel.setNewMainTime(0, 0, 0)
                    myViewModel.setNewShotclock(0, 0)

                    handleSectionExpired()
                    playSound(2)
                }
            }
        }

        private fun handleSectionExpired() {
            if (currentGameSection < maxGameSection) {
                startPauseBeforeNextSection()
                return
            }

            if (currentGameSection >= numberOfGameSection && isDrawByDisplayedResult()) {
                val availableModes = getAvailableContinuationModes()
                if (availableModes.isNotEmpty()) {
                    myViewModel.requestContinuationChoice()
                } else {
                    finishGameFlow()
                }
                return
            }

            finishGameFlow()
        }

        fun applyContinuationChoice(mode: String) {
            val normalizedMode = mode.trim().uppercase(Locale.ROOT)
            if (normalizedMode != "OT" && normalizedMode != "PSO") {
                finishGameFlow()
                return
            }

            selectedContinuationMode = normalizedMode
            val nextSection = currentGameSection + 1
            val sectionLabel = when (selectedContinuationMode) {
                "OT" -> {
                    overtimeCounter += 1
                    "OT-$overtimeCounter"
                }
                "PSO" -> {
                    "PSO"
                }
                else -> nextSection.toString()
            }
            continuationSectionLabels[nextSection] = sectionLabel
            maxGameSection = nextSection
            startPauseBeforeNextSection()
        }

        fun clearCurrentContinuationSectionLabel(): String? {
            if (currentGameSection <= numberOfGameSection) {
                return null
            }

            val sectionLabel = getSectionLabel(currentGameSection)
            val normalizedSectionLabel = sectionLabel.trim().uppercase(Locale.ROOT)
            val isPsoSection = normalizedSectionLabel == "PSO"
            val isOtSection = normalizedSectionLabel.startsWith("OT-")
            if (!isPsoSection && !isOtSection) {
                return null
            }

            continuationSectionLabels.remove(currentGameSection)
            psoEventTimeBySection.remove(currentGameSection)

            if (isPsoSection && selectedContinuationMode == "PSO") {
                selectedContinuationMode = ""
            }
            if (isOtSection && selectedContinuationMode == "OT") {
                selectedContinuationMode = ""
            }

            val normalizedEndMode = gameEndMode.trim().uppercase(Locale.ROOT)
            if (isPsoSection && normalizedEndMode == "PSO") {
                gameEndMode = ""
            }
            if (isOtSection && normalizedEndMode == "OT") {
                gameEndMode = ""
            }

            myViewModel.setCurrentGameSection(currentGameSection)
            return sectionLabel
        }

        private fun startPauseBeforeNextSection() {
            val nextSection = currentGameSection + 1
            val nextLabel = getSectionLabel(nextSection).trim().uppercase(Locale.ROOT)
            val pauseSeconds = when {
                nextSection > numberOfGameSection && (nextLabel == "PSO" || nextLabel == "OT-1") -> DEFAULT_PAUSE_OT_PSO_LENGTH
                currentGameSection == 2 -> DEFAULT_PAUSE_LONG_LENGTH
                else -> DEFAULT_PAUSE_SHORT_LENGTH
            }
            currentCountdown = (pauseSeconds * 1000L)
            createTimerPause(currentCountdown)
            timerPause?.start()
        }

        private fun finishGameFlow() {
            gameFinished = true
            myViewModel.setNewShotclock(0, 0)
            myViewModel.storeGameEvent(END_GAME, currentCountdown, currentGameSection, "-")
        }

        private fun createTimerPause(pause: Long) {
            activePauseMode = 1
            pauseWarningPlayed = false
            timerPause = object : CountDownTimer(pause, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    currentCountdown = millisUntilFinished
                    val minutes = MyTimeConverter.getMinutesFromLong(currentCountdown)
                    val seconds = MyTimeConverter.getSecondsFromLong(currentCountdown)
                    myViewModel.setPauseTime(minutes, seconds)

                    val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
                    ProcessBT.sendMessageToMainBoard("timeGame%$minutes:$secondsString%red")
                    ProcessBT.sendMessageToAllShotClock("time%$minutes:$secondsString%red")

                    if (!pauseWarningPlayed && currentCountdown <= 15000L) {
                        pauseWarningPlayed = true
                        playSound(2)
                    }
                }

                override fun onFinish() {
                    activePauseMode = 0
                    currentGameSection += 1
                    myViewModel.setCurrentGameSection(currentGameSection)
                    val isPsoSection = getSectionLabel(currentGameSection) == "PSO"
                    if (isPsoSection) {
                        currentCountdown = 0L
                        currentCountdownShotclock = 0L
                        myViewModel.setNewMainTime(0, 0, 0)
                        myViewModel.setNewShotclock(0, 0)
                    } else {
                        currentCountdown = gameSectionLength.toLong() * 1000
                        currentCountdownShotclock = (shotclockLongLength * 1000).toLong()
                        setMainTimeDefaults()
                        setShotclockDefaults(shotclockLongLength)
                    }
                    createTimerCountdown()
                    playSound(2)
                    timerPause?.cancel()
                    myViewModel.timeControlAvailable(true)
                    val sectionStartMainTime = if (isPsoSection) 0L else gameSectionLength * 1000L
                    val mainMinutes = MyTimeConverter.getMinutesFromLong(sectionStartMainTime)
                    val mainSeconds = MyTimeConverter.getSecondsFromLong(sectionStartMainTime)
                    val mainSecondsString = if (mainSeconds < 10) "0$mainSeconds" else "$mainSeconds"
                    ProcessBT.sendMessageToMainBoard("timeGame%$mainMinutes:$mainSecondsString%default")
                    ProcessBT.sendMessageToAllShotClock("time%$mainMinutes:$mainSecondsString%default")
                    Thread.sleep(1000)
                    val shotclockValue = if (isPsoSection) 0L else (shotclockLongLength * 1000).toLong()
                    ProcessBT.sendMessageToAllShotClock("shotclock%${MyTimeConverter.getSecondsFromLong(shotclockValue)}%default%0")
                }
            }
        }

        private fun createTimerTimeout(timeout: Long) {
            activePauseMode = 2
            pauseWarningPlayed = false
            timerPause = object : CountDownTimer(timeout, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    currentCountdown = millisUntilFinished
                    val minutes = MyTimeConverter.getMinutesFromLong(currentCountdown)
                    val seconds = MyTimeConverter.getSecondsFromLong(currentCountdown)
                    myViewModel.setPauseTime(minutes, seconds)

                    val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
                    ProcessBT.sendMessageToMainBoard("timeGame%$minutes:$secondsString%red")
                    ProcessBT.sendMessageToAllShotClock("time%$minutes:$secondsString%red")

                    if (!pauseWarningPlayed && currentCountdown <= 15000L) {
                        pauseWarningPlayed = true
                        playSound(2)
                    }
                }

                override fun onFinish() {
                    activePauseMode = 0
                    currentCountdown = savedCountdown
                    savedCountdown = 0
                    timerPause?.cancel()
                    createTimerCountdown()

                    val mainMinutes = MyTimeConverter.getMinutesFromLong(currentCountdown)
                    val mainSeconds = MyTimeConverter.getSecondsFromLong(currentCountdown)
                    val mainSecondsString = if (mainSeconds < 10) "0$mainSeconds" else "$mainSeconds"
                    val color = if (mainMinutes < 1) "red" else "default"
                    ProcessBT.sendMessageToMainBoard("timeGame%$mainMinutes:$mainSecondsString%$color")
                    ProcessBT.sendMessageToAllShotClock("time%$mainMinutes:$mainSecondsString%$color")

                    playSound(2)
                    myViewModel.timeControlAvailable(true)
                    myViewModel.setCurrentGameSection(currentGameSection)
                }
            }
        }

        fun startTimeout() {
            timerCountdown?.cancel()
            timerShotclock?.cancel()
            playSound(2)
            timeIsRunning = false
            shotclockIsRunning = false
            timeoutSelectionWasRunning = false
            timeoutSelectionShotclockWasRunning = false
            myViewModel.timeControlAvailable(false)
            savedCountdown = currentCountdown
            createTimerTimeout((DEFAULT_TIMEOUT_LENGTH * 1000).toLong())
            timerPause?.start()
        }

        fun pauseClocksForTimeoutSelection() {
            if (gameFinished || activePauseMode != 0) {
                return
            }
            timeoutSelectionWasRunning = timeIsRunning
            timeoutSelectionShotclockWasRunning = shotclockIsRunning
            if (timeIsRunning) {
                timeIsRunning = false
                timerCountdown?.cancel()
            }
            if (shotclockIsRunning) {
                shotclockIsRunning = false
                timerShotclock?.cancel()
            }
        }

        fun cancelTimeoutSelectionResumeIfNeeded(): Boolean {
            if (gameFinished || activePauseMode != 0) {
                timeoutSelectionWasRunning = false
                timeoutSelectionShotclockWasRunning = false
                return false
            }

            if (timeoutSelectionWasRunning && !timeIsRunning) {
                timeIsRunning = true
                createTimerCountdown()
                timerCountdown?.start()
                timeoutSelectionWasRunning = false
            }

            if (DEFAULT_TIME_IS_BRUTTO && timeoutSelectionShotclockWasRunning && !shotclockIsRunning) {
                shotclockIsRunning = true
                createShotclockTimer()
                timerShotclock?.start()
            }

            timeoutSelectionWasRunning = false
            timeoutSelectionShotclockWasRunning = false
            myViewModel.timeControlAvailable(true)
            return timeIsRunning || shotclockIsRunning
        }

        fun cancelActiveTimeoutTimer(): Boolean {
            if (activePauseMode != 2 || timerPause == null) {
                return false
            }

            timerPause?.cancel()
            timerPause = null
            activePauseMode = 0
            currentCountdown = savedCountdown
            savedCountdown = 0
            createTimerCountdown()
            shotclockIsRunning = false
            timerShotclock?.cancel()
            timerShotclock = null

            val mainMinutes = MyTimeConverter.getMinutesFromLong(currentCountdown)
            val mainSeconds = MyTimeConverter.getSecondsFromLong(currentCountdown)
            val mainSecondsString = if (mainSeconds < 10) "0$mainSeconds" else "$mainSeconds"
            val color = if (mainMinutes < 1) "red" else "default"
            ProcessBT.sendMessageToMainBoard("timeGame%$mainMinutes:$mainSecondsString%$color")
            ProcessBT.sendMessageToAllShotClock("time%$mainMinutes:$mainSecondsString%$color")

            myViewModel.timeControlAvailable(true)
            myViewModel.setCurrentGameSection(currentGameSection)
            return true
        }

        fun playWarningSignal() {
            playSound(2)
        }

        private fun playSound(version: Int) {
            ensureMaximumSoundVolume()
            val soundGenerator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also {
                toneGenerator = it
            }
            when (version) {
                1 -> soundGenerator.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_SP_PRI, 700)
                2 -> soundGenerator.startTone(ToneGenerator.TONE_SUP_PIP, 1500)
            }
        }

        fun startManualHorn() {
            ensureMaximumSoundVolume()
            val soundGenerator = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also {
                toneGenerator = it
            }
            soundGenerator.startTone(ToneGenerator.TONE_SUP_PIP, 30000)
        }

        fun stopManualHorn() {
            toneGenerator?.stopTone()
        }

        private fun ensureMaximumSoundVolume() {
            val audioManager = myViewModel.getApplication<Application>()
                .getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val streamsToBoost = listOf(
                AudioManager.STREAM_MUSIC,
                AudioManager.STREAM_RING,
                AudioManager.STREAM_NOTIFICATION,
                AudioManager.STREAM_ALARM
            )
            streamsToBoost.forEach { streamType ->
                val maxVolume = audioManager.getStreamMaxVolume(streamType)
                audioManager.setStreamVolume(streamType, maxVolume, 0)
            }
        }

        fun getcountdownShotclock(): Long {
            return currentCountdownShotclock
        }

        fun getCurrentGameSection(): Int {
            return currentGameSection
        }

        fun isGameFinished(): Boolean {
            return gameFinished
        }

        fun getDisplayedGoalsWhite(): String {
            return displayedGoalsWhite
        }

        fun getDisplayedGoalsBlue(): String {
            return displayedGoalsBlue
        }

        fun setCurrentGameSectionManually(section: Int): Boolean {
            if (section < 1) {
                return false
            }
            val maxRegularSection = numberOfGameSection.coerceAtLeast(1)
            val targetSection = section.coerceIn(1, maxRegularSection)
            if (currentGameSection == targetSection) {
                return true
            }
            currentGameSection = targetSection
            myViewModel.setCurrentGameSection(currentGameSection)
            return true
        }

        fun loadImportedGameState(
            importedGame: Game,
            importedTeams: List<Team>,
            importedPlayers: List<Player>,
            importedParticipants: List<Participant>,
            importedCurrentSection: Int,
            importedMainCountdown: Long,
            importedShotclockCountdown: Long,
            importedGameStarted: Boolean,
            importedContinuationMode: String,
            importedGameFinished: Boolean,
            importedGoalsWhite: String,
            importedGoalsBlue: String
        ): Boolean {
            val participantsByCapNumber = importedParticipants
                .filter { it.number > 0 }
                .associateBy { "${it.cap}:${it.number}" }
            val playersByGuid = importedPlayers.associateBy { it.guid }

            val blueTeamParticipant = importedParticipants.firstOrNull {
                it.cap.equals(BLUE, ignoreCase = true) && it.number == 0
            }
            val whiteTeamParticipant = importedParticipants.firstOrNull {
                it.cap.equals(WHITE, ignoreCase = true) && it.number == 0
            }

            val teamsByGuid = importedTeams.associateBy { it.guid }
            val fallbackBlueTeam = Team(UUID.randomUUID().toString()).apply {
                teamName = "BLUE"
                teamLocation = ""
            }
            val fallbackWhiteTeam = Team(UUID.randomUUID().toString()).apply {
                teamName = "WHITE"
                teamLocation = ""
            }

            val resolvedBlueTeam =
                blueTeamParticipant?.team?.let { teamsByGuid[it] } ?: importedTeams.firstOrNull { it.teamName.equals("BLUE", ignoreCase = true) } ?: fallbackBlueTeam
            val resolvedWhiteTeam =
                whiteTeamParticipant?.team?.let { teamsByGuid[it] } ?: importedTeams.firstOrNull { it.teamName.equals("WHITE", ignoreCase = true) } ?: fallbackWhiteTeam

            val resolvedBluePlayers = Array(PLAYER_COUNT) { index ->
                val number = index + 1
                val participant = participantsByCapNumber["$BLUE:$number"]
                participant?.player?.let { playersByGuid[it] } ?: Player(UUID.randomUUID().toString())
            }
            val resolvedWhitePlayers = Array(PLAYER_COUNT) { index ->
                val number = index + 1
                val participant = participantsByCapNumber["$WHITE:$number"]
                participant?.player?.let { playersByGuid[it] } ?: Player(UUID.randomUUID().toString())
            }

            val resolvedBlueTeamParticipant = blueTeamParticipant ?: Participant(
                UUID.randomUUID().toString(),
                importedGame.guid,
                resolvedBluePlayers[0].guid,
                BLUE,
                0,
                resolvedBlueTeam.guid,
                FUNCTION_TYPE_OTHER
            )
            val resolvedWhiteTeamParticipant = whiteTeamParticipant ?: Participant(
                UUID.randomUUID().toString(),
                importedGame.guid,
                resolvedWhitePlayers[0].guid,
                WHITE,
                0,
                resolvedWhiteTeam.guid,
                FUNCTION_TYPE_OTHER
            )

            val resolvedBlueParticipants = Array(PLAYER_COUNT) { index ->
                val number = index + 1
                participantsByCapNumber["$BLUE:$number"] ?: Participant(
                    UUID.randomUUID().toString(),
                    importedGame.guid,
                    resolvedBluePlayers[index].guid,
                    BLUE,
                    number,
                    resolvedBlueTeam.guid,
                    if (index == 0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER
                )
            }
            val resolvedWhiteParticipants = Array(PLAYER_COUNT) { index ->
                val number = index + 1
                participantsByCapNumber["$WHITE:$number"] ?: Participant(
                    UUID.randomUUID().toString(),
                    importedGame.guid,
                    resolvedWhitePlayers[index].guid,
                    WHITE,
                    number,
                    resolvedWhiteTeam.guid,
                    if (index == 0) FUNCTION_TYPE_GOALKEEPER else FUNCTION_TYPE_FIELDPLAYER
                )
            }

            timerCountdown?.cancel()
            timerShotclock?.cancel()
            timerPause?.cancel()
            timerCountdown = null
            timerShotclock = null
            timerPause = null
            activePauseMode = 0
            timeIsRunning = false
            shotclockIsRunning = false
            timeoutSelectionWasRunning = false
            timeoutSelectionShotclockWasRunning = false
            pauseWarningPlayed = false

            game = importedGame
            currentGameGuid = importedGame.guid
            teamBlue = resolvedBlueTeam
            teamWhite = resolvedWhiteTeam
            playersListBlue = resolvedBluePlayers
            playersListWhite = resolvedWhitePlayers
            playersListAll = playersListBlue + playersListWhite

            participantTeamBlue = resolvedBlueTeamParticipant
            participantTeamWhite = resolvedWhiteTeamParticipant
            participantListBlueTemp = resolvedBlueParticipants
            participantListWhiteTemp = resolvedWhiteParticipants
            participantListBlue = listOf(participantTeamBlue) + participantListBlueTemp
            participantListWhite = listOf(participantTeamWhite) + participantListWhiteTemp
            participantListAll = participantListBlue + participantListWhite

            recalculateMaxGameSection()
            currentGameSection = importedCurrentSection.coerceIn(1, maxGameSection.coerceAtLeast(1))
            currentCountdown = importedMainCountdown.coerceAtLeast(0L)
            currentCountdownShotclock = importedShotclockCountdown.coerceAtLeast(0L)
            gameStarted = importedGameStarted
            selectedContinuationMode = importedContinuationMode.trim().uppercase(Locale.ROOT).let {
                if (it == "OT" || it == "PSO") it else ""
            }
            gameFinished = importedGameFinished
            continuationSectionLabels.clear()
            overtimeCounter = 0
            psoEventTimeBySection.clear()
            setDisplayedResult(importedGoalsWhite, importedGoalsBlue)

            myViewModel.setCurrentGameSection(currentGameSection)
            myViewModel.setShotclockButtonLabels(shotclockLongLength, shotclockShortLength)
            setGameTimeEdit()
            setShotclockEdit()
            ProcessBT.sendTeamNamesToMainBoardReliable()
            myViewModel.timeControlAvailable(true)
            return true
        }

        fun getMaxGameSection(): Int {
            return maxGameSection
        }

        fun isOvertimeEnabled(): Boolean {
            return overtimeEnabled
        }

        fun isPsoEnabled(): Boolean {
            return psoEnabled
        }

        fun getTeamBlueParticipantGuid(): String {
            return participantListBlue[0].guid
        }

        fun getTeamWhiteParticipantGuid(): String {
            return participantListWhite[0].guid
        }

        fun getParticipantByCapNumber(cap: String, number: Int): String {
            return if (cap == BLUE) participantListBlue[number].guid else participantListWhite[number].guid
        }

        fun clearExclusionCountdownForPlayer(cap: String, number: Int?) {
            val key = getExclusionPlayerKey(cap, number) ?: return
            excutionTimeOffset.remove(key)
            // Push zero so UI resets the player button label/color immediately.
            myViewModel.exclusionTime.value = "$key:${formatExclusionTime(0L)}"
        }

        fun isTrackedExclusionTypeForCountdown(eventType: Int): Boolean {
            return isTrackedExclusionEventType(eventType)
        }
    }
}
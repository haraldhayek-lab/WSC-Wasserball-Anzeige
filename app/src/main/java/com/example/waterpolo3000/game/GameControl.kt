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
        lateinit var myViewModel: GameViewModel

        // time related stuff
        var gameSectionLength = DEFAULT_GAME_SECTION_LENGTH
        var currentCountdown: Long = gameSectionLength.toLong() * 1000

        private var shotclockLongLength = DEFAULT_SHOTCLOCK_BIG_LENGTH
        private var shotclockShortLength = DEFAULT_SHOTCLOCK_SMALL_LENGTH
        var currentCountdownShotclock: Long = shotclockLongLength.toLong() * 1000

        var gameStarted = false
        private var timeIsRunning = false
        private var currentGameSection = 1
        var numberOfGameSection = DEFAULT_NUMBER_OF_GAME_SECTION
        private var overtimeEnabled = DEFAULT_OVERTIME_ENABLED
        private var psoEnabled = DEFAULT_PSO_ENABLED
        private var maxGameSection = calculateMaxGameSection()

        var timerCountdown: CountDownTimer? = null
        var timerPause: CountDownTimer? = null
        var currentGameGuid = UUID.randomUUID().toString()
        var savedCountdown: Long = 0
        private var toneGenerator: ToneGenerator? = null
        private var pauseWarningPlayed = false
        private var activePauseMode = 0
        private var displayedGoalsWhite = "0"
        private var displayedGoalsBlue = "0"
        private val mainBoardPlayerExclusionCounts = mutableMapOf<String, Int>()

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

        val excutionTimeOffset = mutableMapOf<String, Long>()

        fun init() {
            setDefaults()
            recalculateMaxGameSection()
            resetDisplaySyncState()

            // create all initial records for the db
            teamBlue.teamName = "Mannschaft blau"
            teamBlue.teamLocation = "Blaudorf"
            teamWhite.teamName = "Mannschaft weiss"
            teamWhite.teamLocation = "Weissdorf"
            myViewModel.initAll(game, listOf(teamBlue, teamWhite), playersListAll, participantListAll)
        }

        fun newGame() {
            resetDisplaySyncState()
            activePauseMode = 0
            currentCountdown = gameSectionLength.toLong() * 1000
            currentCountdownShotclock = shotclockLongLength.toLong() * 1000
            gameStarted = false
            timeIsRunning = false
            currentGameSection = 1
            recalculateMaxGameSection()
            myViewModel.currentGameSection.value = currentGameSection.toString()
            timerCountdown = null
            timerPause = null
            currentGameGuid = UUID.randomUUID().toString()
            savedCountdown = 0
            game = Game(currentGameGuid)
            teamBlue = Team(UUID.randomUUID().toString())
            teamWhite = Team(UUID.randomUUID().toString())
            teamBlue.teamName = "Mannschaft blau"
            teamBlue.teamLocation = "Blaudorf"
            teamWhite.teamName = "Mannschaft weiss"
            teamWhite.teamLocation = "Weissdorf"
            createPlayerList()
            playersListAll = playersListBlue + playersListWhite
            createParticipantList()
            participantListAll = participantListBlue + participantListWhite
            myViewModel.initAll(game, listOf(teamBlue, teamWhite), playersListAll, participantListAll)
            myViewModel.timeControlAvailable(true)

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
            val overtimeSections = if (overtimeEnabled) 2 else 0
            val psoSections = if (psoEnabled) 1 else 0
            return numberOfGameSection + overtimeSections + psoSections
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
            DEFAULT_TIMEOUT_LENGTH = standards.timeoutLength
            DEFAULT_MAX_TIMEOUT = standards.maxTimeout

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
            newTimeoutLength: Int,
            newMaxTimeout: Int
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
            DEFAULT_TIMEOUT_LENGTH = newTimeoutLength
            DEFAULT_MAX_TIMEOUT = newMaxTimeout
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
                if(event.toInt() == 200) { // simple exclusion id  = 200
                    excutionTimeOffset[player] = currentCountdown
                    myViewModel.exclusionTime.value = "$player:${formatExclusionTime(getExclusionDurationMillis())}"
                }
            }
        }

        private fun addGoal(event: Int, player: String) {
            val participant =
                if (player.split("_")[1] == "B") participantListBlue[player.split("_")[2].toInt()] else participantListWhite[player.split("_")[2].toInt()]
            myViewModel.storeGameEvent(event, currentCountdown, currentGameSection, participant.guid)
        }

        private fun addExclusion(exclusionEvent: Int, player: String) {
            val index = player.split("_")[2].toInt()
            val participant = if (player.split("_")[1] == "B") participantListBlue[index] else participantListWhite[index]
            Log.d(TAG, "addExclusion: $exclusionEvent")
            myViewModel.storeGameEvent(exclusionEvent, currentCountdown, currentGameSection, participant.guid)
        }

        fun startStopCounter() {
            if (!gameStarted) {
                gameStarted = true
                myViewModel.storeGameEvent(START_GAME, currentCountdown, currentGameSection, "-")
            }
            if (timeIsRunning) {
                timeIsRunning = false
                timerCountdown?.cancel()
//                myViewModel.storeGameEvent(STOP_TIME, currentCountdown, currentGameSection, "-")
            } else {
                timeIsRunning = true
                createTimerCountdown()
//                myViewModel.storeGameEvent(START_TIME, currentCountdown, currentGameSection, "-")
                timerCountdown?.start()
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

            //set player btn exclusion time
            val toBeRemove = mutableListOf<String>()
            if(excutionTimeOffset.isNotEmpty()){
                excutionTimeOffset.forEach {
                    val elapsedTime = it.value - currentCountdownTemp
                    val remainingExclusionTime = (getExclusionDurationMillis() - elapsedTime).coerceAtLeast(0L)
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

        private fun formatExclusionTime(remainingMillis: Long): String {
            val seconds = MyTimeConverter.getSecondsFromLong(remainingMillis)
            val tenths = MyTimeConverter.getSecondsSmallFromLong(remainingMillis)
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
        }

        private fun createTimerCountdown() {
            timerCountdown = object : CountDownTimer(currentCountdown, 100) {
                override fun onTick(millisUntilFinished: Long) {
                    currentCountdownShotclock -= (currentCountdown - millisUntilFinished)
                    currentCountdown = millisUntilFinished
                    setGameTime()
                }

                override fun onFinish() {

                    timeIsRunning = false
                    myViewModel.timeControlAvailable(false)
                    myViewModel.setNewMainTime(0, 0, 0)

                    if (currentGameSection < maxGameSection) {
//                        myViewModel.storeGameEvent(GAMESECTION_EXPIRED, currentCountdown, currentGameSection, "-")
                        currentCountdown = if (currentGameSection == 2)
                            ((DEFAULT_PAUSE_LONG_LENGTH) * 1000).toLong()
                        else ((DEFAULT_PAUSE_SHORT_LENGTH) * 1000).toLong()
                        createTimerPause(currentCountdown)
                        timerPause?.start()
                    } else {
                        myViewModel.setNewShotclock(0, 0)
                        myViewModel.storeGameEvent(END_GAME, currentCountdown, currentGameSection, "-")
                    }
                    playSound(2)
                }
            }
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
                    myViewModel.setCurrentGameSection(++currentGameSection)
                    currentCountdown = gameSectionLength.toLong() * 1000
                    currentCountdownShotclock = (shotclockLongLength * 1000).toLong()
                    setMainTimeDefaults()
                    setShotclockDefaults(shotclockLongLength)
                    createTimerCountdown()
                    playSound(2)
                    timerPause?.cancel()
                    myViewModel.timeControlAvailable(true)
                    val mainMinutes = MyTimeConverter.getMinutesFromLong((gameSectionLength * 1000).toLong())
                    val mainSeconds = MyTimeConverter.getSecondsFromLong((gameSectionLength * 1000).toLong())
                    val mainSecondsString = if (mainSeconds < 10) "0$mainSeconds" else "$mainSeconds"
                    ProcessBT.sendMessageToMainBoard("timeGame%$mainMinutes:$mainSecondsString%default")
                    ProcessBT.sendMessageToAllShotClock("time%$mainMinutes:$mainSecondsString%default")
                    Thread.sleep(1000)
                    ProcessBT.sendMessageToAllShotClock("shotclock%${MyTimeConverter.getSecondsFromLong((shotclockLongLength * 1000).toLong())}%default%0")
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
            playSound(2)
            timeIsRunning = false
            myViewModel.timeControlAvailable(false)
            savedCountdown = currentCountdown
            createTimerTimeout((DEFAULT_TIMEOUT_LENGTH * 1000).toLong())
            timerPause?.start()
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
    }
}
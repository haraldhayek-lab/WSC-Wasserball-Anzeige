package com.example.waterpolo3000.viewmodels

import android.app.Application
import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.*
import com.example.waterpolo3000.R
import com.example.waterpolo3000.data.*
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import okhttp3.internal.wait
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

// viewModel for game fragment
@HiltViewModel
class GameViewModel @Inject internal constructor(gameEventRepository: GameEventRepository, application: Application) : AndroidViewModel(application) {

    private val gERepository = gameEventRepository
    private var database = Firebase.database.reference

    private var liveCounter = 0
    private var liveUnsent = mutableMapOf<Int, String>()
    var liveGame = false
    private var liveGameKey = ""
    private var liveGameStatus = ""

    // for the performance
    private var helperMin = -1
    private var helperSec = -1

    var btSearchExecuted = false
    var db: AppDatabase = AppDatabase.getInstance(getApplication<Application>().applicationContext)
    var mainboardBrightness = 80
    var allBrightness = 80
    val shotclockBrightness = MutableList(4) { 80 }

    // set the recyclerview for the gameEvents
    val gameEvents: LiveData<List<GameEventView>> = gameEventRepository.getGameEvents().asLiveData()

    val goals: LiveData<GameResult> = gameEventRepository.getGameResult(
        GOAL_TYPE_MINIMUM,
        GOAL_TYPE_MAXIMUM,
        intArrayOf(1, 2, 3, 4, 5, 6, 7)
    ).asLiveData()

    val timeoutForWhite: LiveData<TimeoutCount> = gameEventRepository.getTimeoutByTeam(WHITE).asLiveData()
    val timeoutForBlue: LiveData<TimeoutCount> = gameEventRepository.getTimeoutByTeam(BLUE).asLiveData()

    val exclusionsBlue: Array<LiveData<ExclResult>> = Array(PLAYER_COUNT) { i -> gameEventRepository.getExByPlayer(BLUE, i + 1).asLiveData() }
    val exclusionsWhite: Array<LiveData<ExclResult>> = Array(PLAYER_COUNT) { i -> gameEventRepository.getExByPlayer(WHITE, i + 1).asLiveData() }

    // Create a LiveData with a String
    val mainMinutes: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val mainSeconds: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val mainSecondsSmall: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val shotclockSeconds: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val shotclockSecondsSmall: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val shotclockBigButtonLabel: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val shotclockSmallButtonLabel: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val currentGameSection: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val timeClickable: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val exclusionTime: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val connectTextview: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val theConnectViewsVisibility: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }

//    fun setExclusionTime(player: String, value: Int){
//        Log.d(TAG, "ExclusionTime for $player: $value")
//    }

    init {
        GameControl.myViewModel = this
        val cachedStandards = GameSettingsCache.load(getApplication<Application>().applicationContext)
        GameControl.restoreGameStandards(cachedStandards)
        timeClickable.postValue(true)

        // main time
        Log.d(
            TAG,
            "min set init: ${
                MyTimeConverter.getMinutesFromLong(GameControl.currentCountdown)
            }"
        )
        mainMinutes.postValue(
            MyTimeConverter.getMinutesFromLong(GameControl.currentCountdown)
                .toString()
        )
        val seconds = MyTimeConverter.getSecondsFromLong(GameControl.currentCountdown)
        mainSeconds.postValue(if (seconds.toString().length < 2) "0$seconds" else "$seconds")
        mainSecondsSmall.postValue("0")

        // shotclock
        val shSeconds = MyTimeConverter.getSecondsFromLong(GameControl.currentCountdownShotclock)
        shotclockSeconds.postValue(if (shSeconds.toString().length < 2) "0$shSeconds" else "$shSeconds")
        shotclockSecondsSmall.postValue("0")
        setShotclockButtonLabels(cachedStandards.shotclockLongLength, cachedStandards.shotclockShortLength)

        // other
        currentGameSection.postValue("1")
        GameControl.init()
    }

    fun bluetoothConnectAll() {
        btSearchExecuted = true
        val btHandler = ProcessBT()
        btHandler.searchAllDevice()

        Thread(Runnable {
            theConnectViewsVisibility.postValue(true)

            connectTextview.postValue(
                getApplication<Application>().resources.getString(R.string.wait_for_connection)
                    .plus(" (1/5): Haupt Tafel")
            )
            if (!ProcessBT.mainBoardConnected) {
                btHandler.connectMainBoard()
            }

            ProcessBT.shotClocksConnected.forEachIndexed { index, connected ->
                connectTextview.postValue(
                    getApplication<Application>().resources.getString(R.string.wait_for_connection)
                        .plus(" (${index + 2}/5): shotclock ${index + 1}")
                )
                if (!connected) {
                    btHandler.connectShotClock(index)
                }
            }

            theConnectViewsVisibility.postValue(false)
        }).start()
    }

    fun connectMainBoard(text: String) {
        val btHandler = ProcessBT()
        btHandler.searchAllDevice()

        if (!ProcessBT.mainBoardConnected) {
            Thread(Runnable {
                theConnectViewsVisibility.postValue(true)
                connectTextview.postValue(getApplication<Application>().resources.getString(R.string.wait_for_connection).plus(text))
                btHandler.connectMainBoard()
                theConnectViewsVisibility.postValue(false)
            }).start()
        }
    }

    fun connectShotclock(myIndex: Int, text: String) {
        val btHandler = ProcessBT()
        btHandler.searchAllDevice()

        ProcessBT.shotClocksConnected.forEachIndexed { index, b ->
            if (!b && ((myIndex == 0) || ((myIndex - 1) == index))) {
                Thread(Runnable {
                    theConnectViewsVisibility.postValue(true)
                    connectTextview.postValue(getApplication<Application>().resources.getString(R.string.wait_for_connection).plus(text))
                    btHandler.connectShotClock(index)
                    theConnectViewsVisibility.postValue(false)
                }).start()
            }
        }
    }

    fun setAll() {
        Log.d(TAG, "SetAll")
        // main time
        val min = MyTimeConverter.getMinutesFromLong(GameControl.currentCountdown)
        val sec = MyTimeConverter.getSecondsFromLong(GameControl.currentCountdown)
        val secSmall = MyTimeConverter.getSecondsSmallFromLong(GameControl.currentCountdown)
        setNewMainTime(min, sec, secSmall)

        // shotclock
        val shSec = MyTimeConverter.getSecondsFromLong(GameControl.currentCountdownShotclock)
        val shSecSmall =
            MyTimeConverter.getSecondsSmallFromLong(GameControl.currentCountdownShotclock)
        setNewShotclock(shSec, shSecSmall)

        // gamesection
        setCurrentGameSection(GameControl.getCurrentGameSection())

        // time control
        timeControlAvailable(true)
    }

    fun processGameEvent(event: String, player: String) {
        Log.d(TAG, "GameViewModel.processGameEvent")
        GameControl.processGameEvent(event, player)
    }

    fun processTime(time: String) {
        when (time) {
            "StartStop" -> GameControl.startStopCounter()
            "ShotclockSmall" -> GameControl.newShotclockSmall()
            "ShotclockBig" -> GameControl.newShotclockBig()
            "timeout" -> GameControl.startTimeout()
        }
    }

    fun setPauseTime(minutes: Int, seconds: Int) {
        currentGameSection.value = "$minutes:$seconds"
    }

    fun setNewMainTime(minutes: Int, seconds: Int, secondsSmall: Int) {
        if (helperMin != minutes) {
            helperMin = minutes
            mainMinutes.postValue(minutes.toString())
        }
        if (helperSec != seconds) {
            helperSec = seconds
            mainSeconds.postValue(if (seconds < 10) "0$seconds" else "$seconds")
        }
        mainSecondsSmall.postValue("$secondsSmall")
    }

    fun setNewShotclock(seconds: Int?, secondsSmall: Int?) {
        if (seconds == null) {
            shotclockSeconds.postValue(mainSeconds.value)
            shotclockSecondsSmall.postValue(mainSecondsSmall.value)
        } else {
            shotclockSeconds.postValue(if (seconds < 10) "0$seconds" else seconds.toString())
            shotclockSecondsSmall.postValue(secondsSmall.toString())
        }
    }

    fun setShotclockButtonLabels(longSeconds: Int, shortSeconds: Int) {
        shotclockBigButtonLabel.postValue(longSeconds.toString())
        shotclockSmallButtonLabel.postValue(shortSeconds.toString())
    }

    fun applyAndPersistGameStandards(standards: GameStandards) {
        GameControl.applyGameStandards(
            newNumberOfGameSection = standards.numberOfGameSection,
            newOvertimeEnabled = standards.overtimeEnabled,
            newPsoEnabled = standards.psoEnabled,
            newGameSectionLength = standards.gameSectionLength,
            newShotclockLongLength = standards.shotclockLongLength,
            newShotclockShortLength = standards.shotclockShortLength,
            newPauseLongLength = standards.pauseLongLength,
            newPauseShortLength = standards.pauseShortLength,
            newTimeoutLength = standards.timeoutLength,
            newMaxTimeout = standards.maxTimeout
        )
        GameSettingsCache.save(getApplication<Application>().applicationContext, standards)
    }

    fun timeControlAvailable(clickable: Boolean) {
        timeClickable.postValue(clickable)
    }

    fun setCurrentGameSection(currentSection: Int) {
//        currentGameSection.value = currentSection.toString()
        currentGameSection.postValue(currentSection.toString())
    }

    // store in db
    fun addGame(game: Game) {
        viewModelScope.launch {
            db.gameDao().insert(game)
        }
    }

    // store in db
    fun addPlayer(player: Player) {
        viewModelScope.launch {
            db.playerDao().insert(player)
        }
    }

    // store in db
//    fun addPlayerList(playerList: List<Player>) {
//        viewModelScope.launch {
//            db.playerDao().insertAll(playerList)
//        }
//    }

    // store in db
    fun addParticipant(participant: Participant) {
        viewModelScope.launch {
            db.participantDao().insert(participant)
        }
    }

    // store in db
    fun addParticipantList(participantList: List<Participant>) {
        viewModelScope.launch {
            db.participantDao().insertAll(participantList)
        }
    }

    // store in db
    fun addTeam(team: Team) {
        viewModelScope.launch {
            db.teamDao().insert(team)
        }
    }

    // store in db
    fun addTeamList(teamList: List<Team>) {
        viewModelScope.launch {
            db.teamDao().insertAll(teamList)
        }
    }

    fun storeTimeout(cap: String, currentCountdown: Long) {
        storeGameEventInternal(
            9,
            currentCountdown,
            GameControl.getCurrentGameSection(),
            if (cap == BLUE) GameControl.getTeamBlueParticipantGuid() else GameControl.getTeamWhiteParticipantGuid(),
            UUID.randomUUID().toString()
        )
    }

    fun createFirstFirebaseEntry() {
        viewModelScope.launch {
            val checkNetworkConnection = CheckForInternet()
            if (liveGame && checkNetworkConnection.isOnline(getApplication()) && liveGameKey.isEmpty()) {
                Log.d(TAG, "WRITE TO FIREBASE_0")
                liveGameStatus = if(GameControl.gameStarted) "live" else "pending"
                liveGameKey = createLiveGameKey()
                database.child("games").child(liveGameKey).setValue(liveGameStatus)
            }

            if (liveUnsent.isNotEmpty()) {
                Log.d(TAG, "process unsent")
                QueryDb().start()
            }
        }
    }

    fun storeGameEvent(
        gameEventType: Int,
        countdown: Long,
        gameSection: Int,
        participant: String
    ) {

        viewModelScope.launch {
            val checkNetworkConnection = CheckForInternet()
            val tempGuid = UUID.randomUUID().toString()
            storeGameEventInternal(gameEventType, countdown, gameSection, participant, tempGuid)

            if (gameEventType in GOAL_TYPE_MINIMUM..EXCLUSION_TYPE_MAXIMUM ||
                gameEventType == START_GAME
            ) {
                liveCounter++
                liveUnsent[liveCounter] = tempGuid
                Log.d(TAG, "liveUnsent: ${liveUnsent.size}")
                if (liveGame && checkNetworkConnection.isOnline(getApplication())) {
                    if (liveGameKey.isEmpty()) {
//                        Log.d(TAG, "WRITE TO FIREBASE_1")
//                        liveGameStatus = "live"
//                        liveGameKey = createLiveGameKey()
//                        database.child("games").child(liveGameKey).setValue(liveGameStatus)
                        createFirstFirebaseEntry()
                    }

                    if (gameEventType == START_GAME) {
                        val eventKey = "${liveCounter}_${tempGuid}"
                        val eventValue = "1_8_00_0_0_STARTGAME"
                        liveGameStatus = "live"

                        database.child("games").child(liveGameKey).setValue(liveGameStatus)
                        database.child("events").child(liveGameKey).child(eventKey).setValue(eventValue)
                    }

//                    // send unsent
//                    if (liveUnsent.isNotEmpty()) {
//                        Log.d(TAG, "process unsent")
//                        QueryDb().start()
//                    }
                }
            }
        }
    }

    private fun createLiveGameKey(): String {
        val competition = "bl" // create competition in GameControl
        val dateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        val formatted = dateTime.format(formatter)
        val startTime = "12_00" // create in GameControl start time of game

        return "${competition}_${formatted}_${startTime}_${GameControl.teamWhite.teamName}_${GameControl.teamBlue.teamName}_${GameControl.currentGameGuid}"
    }

    private fun storeGameEventInternal(
        gameEventType: Int,
        countdown: Long,
        gameSection: Int,
        participant: String,
        guid: String
    ) {
        val currentGameEvent = GameEvent(
            guid,
            GameControl.game.guid,
            gameSection,
            countdown,
            participant,
            gameEventType,
        )
        addGameEvent(currentGameEvent)
    }

    // store in db
    private fun addGameEvent(gameEvent: GameEvent) {
        Log.d(TAG, "GameViewModel.addGameEvent")
        val job = viewModelScope.launch {
            db.gameEventDao().insert(gameEvent)
        }
        job.invokeOnCompletion {
            Log.d(TAG, "COMPLETED")
            val checkNetworkConnection = CheckForInternet()
            if(liveGame && checkNetworkConnection.isOnline(getApplication())) {
                // send unsent
                if (liveUnsent.isNotEmpty()) {
                    Log.d(TAG, "process unsent")
                    QueryDb().start()
                }
            }
        }
    }

    // update the deleted column(set to true) of the specified gameEvent
    fun deleteGameEvent(guid: String) {
        Log.d(TAG, "GameViewModel.DeleteGameEvent")
        viewModelScope.launch {
            db.gameEventDao().updateToDelete(guid, System.currentTimeMillis())
        }
    }

    fun initAll(
        game: Game,
        teamList: List<Team>,
        playerList: Array<Player>,
        participantList: List<Participant>
    ) {
        viewModelScope.launch {
            db.gameDao().insert(game)
            db.teamDao().insertAll(teamList)
            db.playerDao().insertAll(playerList)
            db.participantDao().insertAll(participantList)
        }
    }

    fun updateGameEventTime(gameEvent: GameEventView, newTime: Long) {
        Log.d(TAG, "Check: ${gameEvent.number}")
        viewModelScope.launch {
            val newGameEvent = GameEvent(
                UUID.randomUUID().toString(),
                GameControl.game.guid,
                gameEvent.gameSection.toInt(),
                newTime,
                GameControl.getParticipantByCapNumber(
                    gameEvent.cap,
                    if (gameEvent.number == "") 0 else gameEvent.number.toInt()
                ),
                gameEvent.gameEventType
            )
            db.gameEventDao().insert(newGameEvent)
            db.gameEventDao().updateToDelete(gameEvent.guid, System.currentTimeMillis())
        }
    }

    fun newGame() {
        GameControl.newGame()
    }

    private inner class QueryDb() : Thread() {

        override fun run() {
            if (liveUnsent.isNotEmpty()) {
                val unsentGameEvents = gERepository.getGameEventsByGuid(ArrayList(liveUnsent.values))
                Log.d(TAG, "THE RESULT: ${unsentGameEvents.count()}")

                Log.d(TAG, "${liveUnsent.values},${liveUnsent.keys}")
                unsentGameEvents.forEach { gameEventView ->
                    val tempCount = liveUnsent.filterValues { it == gameEventView.guid }.keys.first()
                    Log.d(TAG, "tempCount: $tempCount")
                    if (tempCount > 0) {
                        val eventKey = "${tempCount}_${gameEventView.guid}"
                        val min = MyTimeConverter.getMinutesFromLong(gameEventView.time)
                        val sec = MyTimeConverter.getSecondsFromLong(gameEventView.time)
                        val secSmall = MyTimeConverter.getSecondsSmallFromLong(gameEventView.time)
                        val typeString = if (gameEventView.gameEventType <= GOAL_TYPE_MAXIMUM) "goal" else "exclusion"
                        val eventValue = "${gameEventView.gameSection}_${min}_${sec}_${secSmall}_${gameEventView.number}_${gameEventView.cap}_${typeString}_${gameEventView.gameEventTypeString}"
                        Log.d(TAG, "WRITE TO FIREBASE unsent")
                        database.child("events").child(liveGameKey).child(eventKey).setValue(eventValue)
                        liveUnsent.remove(tempCount)
                    }
                }
            }
        }
    }
}
package com.example.waterpolo3000.viewmodels

import android.app.Application
import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.*
import com.example.waterpolo3000.R
import com.example.waterpolo3000.data.*
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.*
import com.google.gson.Gson
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.internal.wait
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

// viewModel for game fragment
@HiltViewModel
class GameViewModel @Inject internal constructor(gameEventRepository: GameEventRepository, application: Application) : AndroidViewModel(application) {

    data class ContinuationSectionDeleteResult(
        val removedLabel: String? = null,
        val blockedLabel: String? = null,
        val blockedLogLineCount: Int = 0
    )

    private data class SnapshotRuntimeState(
        val currentSection: Int,
        val currentCountdown: Long,
        val currentShotclockCountdown: Long,
        val gameStarted: Boolean,
        val continuationMode: String,
        val gameFinished: Boolean,
        val displayedGoalsWhite: String,
        val displayedGoalsBlue: String
    )

    private data class GameSnapshot(
        val version: Int,
        val game: Game,
        val teams: List<Team>,
        val players: List<Player>,
        val participants: List<Participant>,
        val gameEvents: List<GameEvent>,
        val standards: GameStandards,
        val meta: GameMeta,
        val runtime: SnapshotRuntimeState
    )

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
    val continuationChoiceRequest: MutableLiveData<Int> by lazy { MutableLiveData<Int>() }
    val timeClickable: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val exclusionTime: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val connectTextview: MutableLiveData<String> by lazy { MutableLiveData<String>() }
    val theConnectViewsVisibility: MutableLiveData<Boolean> by lazy { MutableLiveData<Boolean>() }
    val gameEventEditRequest: MutableLiveData<GameEventView?> by lazy { MutableLiveData<GameEventView?>(null) }

//    fun setExclusionTime(player: String, value: Int){
//        Log.d(TAG, "ExclusionTime for $player: $value")
//    }

    init {
        GameControl.myViewModel = this
        val cachedStandards = GameSettingsCache.load(getApplication<Application>().applicationContext)
        val cachedGameMeta = GameMetaCache.load(getApplication<Application>().applicationContext)
        GameControl.setGameMeta(cachedGameMeta.competition, cachedGameMeta.gameNumber)
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
        continuationChoiceRequest.postValue(0)
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
            "StartStopShotclock" -> GameControl.startStopShotclock()
            "ShotclockSmall" -> GameControl.newShotclockSmall()
            "ShotclockBig" -> GameControl.newShotclockBig()
            "timeout" -> GameControl.startTimeout()
        }
    }

    fun pauseClocksForTimeoutSelection() {
        GameControl.pauseClocksForTimeoutSelection()
    }

    fun cancelTimeoutSelectionResumeIfNeeded(): Boolean {
        return GameControl.cancelTimeoutSelectionResumeIfNeeded()
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
            newPauseOtPsoLength = standards.pauseOtPsoLength,
            newTimeoutLength = standards.timeoutLength,
            newMaxTimeout = standards.maxTimeout,
            newTimeIsBrutto = standards.timeIsBrutto
        )
        GameSettingsCache.save(getApplication<Application>().applicationContext, standards)
    }

    fun createGameSnapshotFileName(): String {
        val competition = if (GameControl.competitionName.isBlank()) "bewerb" else GameControl.competitionName
        val gameNumber = if (GameControl.gameNumberLabel.isBlank()) "spiel" else GameControl.gameNumberLabel
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        val normalizedCompetition = competition.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        val normalizedGameNumber = gameNumber.replace("[^A-Za-z0-9_-]".toRegex(), "_")
        return "${GAME_SNAPSHOT_FILENAME_PREFIX}${normalizedCompetition}_${normalizedGameNumber}_$timestamp.json"
    }

    suspend fun exportCurrentGameSnapshotJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val gameGuid = GameControl.currentGameGuid
            val game = db.gameDao().getGameDirect(gameGuid) ?: GameControl.game

            val participants = db.participantDao()
                .getAllParticipantFromGameDirect(gameGuid)
                .filter { !it.deleted }

            val teamGuids = participants.map { it.team }.distinct()
            val teams = if (teamGuids.isEmpty()) {
                listOf(GameControl.teamWhite, GameControl.teamBlue)
            } else {
                db.teamDao().getTeamsByGuidsDirect(teamGuids).filter { !it.deleted }
            }

            val playerGuids = participants.map { it.player }.distinct()
            val players = if (playerGuids.isEmpty()) {
                GameControl.playersListAll.toList()
            } else {
                db.playerDao().getPlayersByGuidsDirect(playerGuids).filter { !it.deleted }
            }

            val events = db.gameEventDao()
                .getAllByGameGuidDirect(gameGuid)
                .filter { !it.deleted }

            val context = getApplication<Application>().applicationContext
            val standards = GameSettingsCache.load(context)
            val meta = GameMetaCache.load(context)

            val runtime = SnapshotRuntimeState(
                currentSection = GameControl.getCurrentGameSection(),
                currentCountdown = GameControl.currentCountdown,
                currentShotclockCountdown = GameControl.currentCountdownShotclock,
                gameStarted = GameControl.gameStarted,
                continuationMode = GameControl.getContinuationModeLabel(),
                gameFinished = GameControl.isGameFinished(),
                displayedGoalsWhite = GameControl.getDisplayedGoalsWhite(),
                displayedGoalsBlue = GameControl.getDisplayedGoalsBlue()
            )

            val snapshot = GameSnapshot(
                version = 1,
                game = game,
                teams = teams,
                players = players,
                participants = participants,
                gameEvents = events,
                standards = standards,
                meta = meta,
                runtime = runtime
            )

            Gson().toJson(snapshot)
        }
    }

    suspend fun importGameSnapshotJson(
        json: String,
        overrideCompetition: String? = null,
        overrideGameNumber: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val snapshot = Gson().fromJson(json, GameSnapshot::class.java)
                ?: throw IllegalArgumentException("Ungueltige Spieldatei")

            if (snapshot.participants.isEmpty()) {
                throw IllegalArgumentException("Spieldatei enthaelt keine Teilnehmer")
            }

            val now = System.currentTimeMillis()
            val newGameGuid = UUID.randomUUID().toString()
            val newGame = Game(newGameGuid).apply {
                competition = snapshot.game.competition
                competitionType = snapshot.game.competitionType
                gameStart = snapshot.game.gameStart
                gameEnd = snapshot.game.gameEnd
                gameLocation = snapshot.game.gameLocation
                settings = snapshot.game.settings
                deleted = false
                created = now
                lastUpdated = 0
            }

            val sourceTeams = snapshot.teams.associateBy { it.guid }
            val sourcePlayers = snapshot.players.associateBy { it.guid }
            val teamMap = mutableMapOf<String, Team>()
            val playerMap = mutableMapOf<String, Player>()

            val rebuiltTeams = mutableListOf<Team>()
            val rebuiltPlayers = mutableListOf<Player>()

            fun ensureTeam(oldGuid: String, cap: String): Team {
                teamMap[oldGuid]?.let { return it }
                val source = sourceTeams[oldGuid]
                val fallbackName = if (cap.equals(WHITE, ignoreCase = true)) "WHITE" else "BLUE"
                val createdTeam = Team(UUID.randomUUID().toString()).apply {
                    teamName = source?.teamName ?: fallbackName
                    teamLocation = source?.teamLocation ?: ""
                    deleted = false
                    created = now
                    lastUpdated = 0
                }
                teamMap[oldGuid] = createdTeam
                rebuiltTeams.add(createdTeam)
                return createdTeam
            }

            fun ensurePlayer(oldGuid: String): Player {
                playerMap[oldGuid]?.let { return it }
                val source = sourcePlayers[oldGuid]
                val createdPlayer = Player(UUID.randomUUID().toString()).apply {
                    playerFirstName = source?.playerFirstName ?: "Vorname"
                    playerLastName = source?.playerLastName ?: "Nachname"
                    playerLicense = source?.playerLicense ?: 0
                    deleted = false
                    created = now
                    lastUpdated = 0
                }
                playerMap[oldGuid] = createdPlayer
                rebuiltPlayers.add(createdPlayer)
                return createdPlayer
            }

            val rebuiltParticipants = mutableListOf<Participant>()
            val participantGuidMap = mutableMapOf<String, String>()

            snapshot.participants.forEach { sourceParticipant ->
                val resolvedTeam = ensureTeam(sourceParticipant.team, sourceParticipant.cap)
                val resolvedPlayer = ensurePlayer(sourceParticipant.player)
                val newParticipantGuid = UUID.randomUUID().toString()
                participantGuidMap[sourceParticipant.guid] = newParticipantGuid

                rebuiltParticipants.add(
                    Participant(
                        guid = newParticipantGuid,
                        game = newGameGuid,
                        player = resolvedPlayer.guid,
                        cap = sourceParticipant.cap,
                        number = sourceParticipant.number,
                        team = resolvedTeam.guid,
                        function = sourceParticipant.function
                    ).apply {
                        deleted = false
                        created = now
                        lastUpdated = 0
                    }
                )
            }

            val rebuiltEvents = snapshot.gameEvents.map { sourceEvent ->
                GameEvent(
                    guid = UUID.randomUUID().toString(),
                    game = newGameGuid,
                    gameSection = sourceEvent.gameSection,
                    time = sourceEvent.time,
                    participant = participantGuidMap[sourceEvent.participant] ?: sourceEvent.participant,
                    gameEventType = sourceEvent.gameEventType
                ).apply {
                    deleted = false
                    created = now
                    lastUpdated = 0
                }
            }

            db.gameDao().insert(newGame)
            if (rebuiltTeams.isNotEmpty()) {
                db.teamDao().insertAll(rebuiltTeams)
            }
            if (rebuiltPlayers.isNotEmpty()) {
                db.playerDao().insertAll(rebuiltPlayers.toTypedArray())
            }
            if (rebuiltParticipants.isNotEmpty()) {
                db.participantDao().insertAll(rebuiltParticipants)
            }
            if (rebuiltEvents.isNotEmpty()) {
                db.gameEventDao().insertAll(rebuiltEvents)
            }

            val context = getApplication<Application>().applicationContext
            val importedStandards = snapshot.standards
            val normalizedStandards = importedStandards.copy(
                pauseOtPsoLength = if (importedStandards.pauseOtPsoLength > 0) {
                    importedStandards.pauseOtPsoLength
                } else {
                    importedStandards.pauseShortLength
                },
                timeIsBrutto = importedStandards.timeIsBrutto
            )
            val effectiveMeta = GameMeta(
                competition = overrideCompetition?.trim().takeUnless { it.isNullOrBlank() }
                    ?: snapshot.meta.competition,
                gameNumber = overrideGameNumber?.trim().takeUnless { it.isNullOrBlank() }
                    ?: snapshot.meta.gameNumber
            )
            GameSettingsCache.save(context, normalizedStandards)
            GameMetaCache.save(context, effectiveMeta)
            GameControl.setGameMeta(effectiveMeta.competition, effectiveMeta.gameNumber)
            GameControl.restoreGameStandards(normalizedStandards)
            newGame.competition = effectiveMeta.competition

            GameControl.loadImportedGameState(
                importedGame = newGame,
                importedTeams = rebuiltTeams,
                importedPlayers = rebuiltPlayers,
                importedParticipants = rebuiltParticipants,
                importedCurrentSection = snapshot.runtime.currentSection,
                importedMainCountdown = snapshot.runtime.currentCountdown,
                importedShotclockCountdown = snapshot.runtime.currentShotclockCountdown,
                importedGameStarted = snapshot.runtime.gameStarted,
                importedContinuationMode = snapshot.runtime.continuationMode,
                importedGameFinished = snapshot.runtime.gameFinished,
                importedGoalsWhite = snapshot.runtime.displayedGoalsWhite,
                importedGoalsBlue = snapshot.runtime.displayedGoalsBlue
            )

            val displayGameNumber = if (effectiveMeta.gameNumber.isBlank()) "-" else effectiveMeta.gameNumber
            "Spiel geladen (Spiel-Nr.: $displayGameNumber)"
        }
    }

    fun timeControlAvailable(clickable: Boolean) {
        timeClickable.postValue(clickable)
    }

    fun setCurrentGameSection(currentSection: Int) {
        currentGameSection.postValue(GameControl.getSectionLabel(currentSection))
    }

    fun requestContinuationChoice() {
        val nextValue = (continuationChoiceRequest.value ?: 0) + 1
        continuationChoiceRequest.postValue(nextValue)
    }

    fun applyContinuationChoice(mode: String) {
        GameControl.applyContinuationChoice(mode)
    }

    suspend fun deleteCurrentContinuationSectionIfEmpty(): ContinuationSectionDeleteResult {
        val section = GameControl.getCurrentGameSection()
        if (section <= GameControl.numberOfGameSection) {
            return ContinuationSectionDeleteResult()
        }

        val label = GameControl.getSectionLabel(section)
        val normalizedLabel = label.trim().uppercase(Locale.ROOT)
        val isContinuation = normalizedLabel == "PSO" || normalizedLabel.startsWith("OT-")
        if (!isContinuation) {
            return ContinuationSectionDeleteResult()
        }

        val logLineCount = gERepository.getVisibleLogLineCountBySection(section)
        if (logLineCount > 0) {
            return ContinuationSectionDeleteResult(
                blockedLabel = label,
                blockedLogLineCount = logLineCount
            )
        }

        return ContinuationSectionDeleteResult(
            removedLabel = GameControl.clearCurrentContinuationSectionLabel()
        )
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
        val competition = if (GameControl.competitionName.isBlank()) "bl" else GameControl.competitionName
        val gameNumber = if (GameControl.gameNumberLabel.isBlank()) "-" else GameControl.gameNumberLabel
        val dateTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        val formatted = dateTime.format(formatter)
        val startTime = "12_00" // create in GameControl start time of game

        return "${competition}_${gameNumber}_${formatted}_${startTime}_${GameControl.teamWhite.teamName}_${GameControl.teamBlue.teamName}_${GameControl.currentGameGuid}"
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

    fun deleteGameEvent(gameEvent: GameEventView) {
        if (GameControl.isGameFinished()) {
            GameControl.reopenEndedGameForCorrection()
        }
        if (gameEvent.gameEventType == TIMEOUT) {
            GameControl.cancelActiveTimeoutTimer()
        }
        if (GameControl.isTrackedExclusionTypeForCountdown(gameEvent.gameEventType)) {
            GameControl.clearExclusionCountdownForPlayer(
                gameEvent.cap,
                gameEvent.number.toIntOrNull()
            )
        }
        deleteGameEvent(gameEvent.guid)
    }

    fun requestGameEventEdit(gameEvent: GameEventView) {
        gameEventEditRequest.postValue(gameEvent)
    }

    fun clearGameEventEditRequest() {
        gameEventEditRequest.postValue(null)
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

    fun updateGameEvent(
        gameEvent: GameEventView,
        newTime: Long,
        participantGuid: String,
        eventType: Int,
        oldCap: String,
        oldNumber: Int?,
        oldSection: Int,
        oldTime: Long,
        newCap: String,
        newNumber: Int?,
        newSection: Int,
    ) {
        if (GameControl.isGameFinished()) {
            GameControl.reopenEndedGameForCorrection()
        }
        GameControl.updateExclusionTrackingAfterGameEventEdit(
            oldEventType = gameEvent.gameEventType,
            oldCap = oldCap,
            oldNumber = oldNumber,
            oldSection = oldSection,
            oldTime = oldTime,
            newEventType = eventType,
            newCap = newCap,
            newNumber = newNumber,
            newSection = newSection,
            newTime = newTime
        )

        viewModelScope.launch {
            val newGameEvent = GameEvent(
                UUID.randomUUID().toString(),
                GameControl.game.guid,
                gameEvent.gameSection.toInt(),
                newTime,
                participantGuid,
                eventType,
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
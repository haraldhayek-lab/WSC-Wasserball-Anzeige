package com.example.waterpolo3000.viewmodels

import android.content.ContentValues
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.waterpolo3000.data.*
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinuationSectionResult(
    val label: String,
    val whiteGoals: String,
    val blueGoals: String
)

// viewModel for protocol fragment

@HiltViewModel
class ProtocolViewModel @Inject internal constructor(
    gameEventRepository: GameEventRepository
) : ViewModel() {
    lateinit var db: AppDatabase
    var playerToBeUpdated: MutableList<Player> = mutableListOf()
    private var latestPersonalFoulRows: List<ProtocolGameEventType> = emptyList()
    private var latestGoalRows: List<ProtocolGoalType> = emptyList()
    private val knownContinuationSections = mutableSetOf<Int>()
    private val knownContinuationLabels = mutableMapOf<Int, String>()
    private var knownGameGuid: String = ""

    private val protocolRefreshTrigger: MutableLiveData<Int> = MutableLiveData(0)

    val protocolForTeamBlue: LiveData<List<ProtocolTeam>> =
        gameEventRepository.getProtocolForTeam(
            BLUE,
            EXCLUSION_TYPE_MINIMUM,
            EXCLUSION_TYPE_MAXIMUM,
            GOAL_TYPE_MINIMUM,
            GOAL_TYPE_MAXIMUM
        ).asLiveData()
    val protocolForTeamWhite: LiveData<List<ProtocolTeam>> =
        gameEventRepository.getProtocolForTeam(
            WHITE,
            EXCLUSION_TYPE_MINIMUM,
            EXCLUSION_TYPE_MAXIMUM,
            GOAL_TYPE_MINIMUM,
            GOAL_TYPE_MAXIMUM
        ).asLiveData()

    private val protocolForPersonalFoulRaw: LiveData<List<ProtocolGameEventType>> =
        gameEventRepository.getProtocolByGameEventType(EXCLUSION_TYPE_MINIMUM, EXCLUSION_TYPE_MAXIMUM).asLiveData()

    val protocolForPersonalFoul: LiveData<List<ProtocolGameEventType>> =
        MediatorLiveData<List<ProtocolGameEventType>>().apply {
            addSource(protocolForPersonalFoulRaw) { rows ->
                latestPersonalFoulRows = rows
                value = transformProtocolPersonalFoulRows(rows)
            }
            addSource(protocolRefreshTrigger) {
                value = transformProtocolPersonalFoulRows(latestPersonalFoulRows)
            }
        }

    private val protocolGoalTypeRaw: LiveData<List<ProtocolGoalType>> =
        gameEventRepository.getProtocolGoalType(
            GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM
        ).asLiveData()

    val protocolGoalType: LiveData<List<ProtocolGoalType>> =
        MediatorLiveData<List<ProtocolGoalType>>().apply {
            addSource(protocolGoalTypeRaw) { rows ->
                latestGoalRows = rows
                value = transformProtocolGoalRows(rows)
            }
            addSource(protocolRefreshTrigger) {
                value = transformProtocolGoalRows(latestGoalRows)
            }
        }

    val editTeamWhite: LiveData<List<EditTeam>> =
        gameEventRepository.getPlayerNames(WHITE).asLiveData()
    val editTeamBlue: LiveData<List<EditTeam>> =
        gameEventRepository.getPlayerNames(BLUE).asLiveData()

    // result
    val goals: LiveData<GameResult> =
        gameEventRepository.getGameResult(GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM, intArrayOf(1, 2, 3, 4, 5, 6)).asLiveData()

    val goalsFirstQuarter: LiveData<GameResult> =
        gameEventRepository.getGameResult(GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM, intArrayOf(1)).asLiveData()

    val goalsSecondQuarter: LiveData<GameResult> =
        gameEventRepository.getGameResult(GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM, intArrayOf(2)).asLiveData()

    val goalsThirdQuarter: LiveData<GameResult> =
        gameEventRepository.getGameResult(GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM, intArrayOf(3)).asLiveData()

    val goalsFourthQuarter: LiveData<GameResult> =
        gameEventRepository.getGameResult(GOAL_TYPE_MINIMUM, GOAL_TYPE_MAXIMUM, intArrayOf(4)).asLiveData()

    val continuationSectionLabel: MutableLiveData<String> = MutableLiveData("")
    val continuationGoalsWhite: MutableLiveData<String> = MutableLiveData("0")
    val continuationGoalsBlue: MutableLiveData<String> = MutableLiveData("0")
    val continuationSectionLabel2: MutableLiveData<String> = MutableLiveData("")
    val continuationGoalsWhite2: MutableLiveData<String> = MutableLiveData("0")
    val continuationGoalsBlue2: MutableLiveData<String> = MutableLiveData("0")
    val continuationSectionResults: MutableLiveData<List<ContinuationSectionResult>> = MutableLiveData(emptyList())
    val totalGoalsWhite: MutableLiveData<String> = MutableLiveData("0")
    val totalGoalsBlue: MutableLiveData<String> = MutableLiveData("0")

    fun updatePlayerName(guid: String, column: String, value: String) {
        var found = false
        playerToBeUpdated.forEach { player ->
            when (column) {
                "firstName" -> {
                    if (player.guid == guid) {
                        player.playerFirstName = value
                        found = true
                    }
                }
                "lastName" -> {
                    if (player.guid == guid) {
                        player.playerLastName = value
                        found = true
                    }
                }
            }
        }
        if (!found) {
            val player = Player(guid)
            when (column) {
                "firstName" -> {
                    player.playerFirstName = value
                    playerToBeUpdated.add(player)
                }
                "lastName" -> {
                    player.playerLastName = value
                    playerToBeUpdated.add(player)
                }
            }
        }
        Log.d(ContentValues.TAG, "size: ${playerToBeUpdated.size}")
    }

    fun storePlayerUpdated() {
        if(playerToBeUpdated.size>0) {
            playerToBeUpdated.forEach {
                viewModelScope.launch {
                    db.playerDao().updatePlayer(it.guid,it.playerFirstName,it.playerLastName,System.currentTimeMillis())
                }
            }
            playerToBeUpdated = mutableListOf()
        }
    }

    fun clearPlayerUpdate(){
        playerToBeUpdated = mutableListOf()
    }

    fun refreshContinuationContext() {
        continuationSectionLabel.postValue("")
        continuationGoalsWhite.postValue("0")
        continuationGoalsBlue.postValue("0")
        continuationSectionLabel2.postValue("")
        continuationGoalsWhite2.postValue("0")
        continuationGoalsBlue2.postValue("0")
        continuationSectionResults.postValue(emptyList())
    }

    fun refreshProtocolRowsForCurrentSettings() {
        protocolRefreshTrigger.postValue((protocolRefreshTrigger.value ?: 0) + 1)
    }

    fun notifySectionSettingsChanged() {
        refreshProtocolRowsForCurrentSettings()
    }

    private fun transformProtocolPersonalFoulRows(rows: List<ProtocolGameEventType>): List<ProtocolGameEventType> {
        val normalizedRows = rows.map { row ->
            if (row.guid.length < 20) {
                row.copy(guid = normalizeSectionHeader(row.guid))
            } else {
                row
            }
        }

        val continuationSections = resolveContinuationSections(
            normalizedRows
                .filter { it.guid.length >= 20 }
                .map { it.gameSection }
                .toSet()
        )

        val regularHeaders = (1..GameControl.numberOfGameSection).map { section ->
            ProtocolGameEventType(section, toRegularSectionHeader(section), 1_000_000L, "", "", "")
        }

        val regularEventRows = normalizedRows
            .filter { it.guid.length >= 20 && it.gameSection in 1..GameControl.numberOfGameSection }
            .sortedWith(compareBy<ProtocolGameEventType> { it.gameSection }.thenByDescending { it.time })

        val regularRows = mutableListOf<ProtocolGameEventType>()
        regularHeaders.forEach { header ->
            regularRows.add(header)
            regularRows.addAll(regularEventRows.filter { it.gameSection == header.gameSection })
        }

        if (continuationSections.isEmpty()) {
            return regularRows
        }

        val continuationRows = mutableListOf<ProtocolGameEventType>()
        continuationSections.toList().sorted().forEach { section ->
            val sectionRowsRaw = normalizedRows
                .filter { it.guid.length >= 20 && it.gameSection == section }
                .sortedByDescending { it.time }
            val sectionLabel = getContinuationSectionLabel(section)
            val sectionRows = if (sectionLabel == "PSO") {
                sectionRowsRaw.mapIndexed { index, row ->
                    row.copy(time = index * 1000L)
                }
            } else {
                sectionRowsRaw
            }
            continuationRows.add(
                ProtocolGameEventType(section, sectionLabel, 1_000_000L, "", "", "")
            )
            continuationRows.addAll(sectionRows)
        }

        return regularRows + continuationRows
    }

    private fun transformProtocolGoalRows(rows: List<ProtocolGoalType>): List<ProtocolGoalType> {
        val normalizedRows = rows.map { row ->
            if (row.guid.length < 20) {
                row.copy(guid = normalizeSectionHeader(row.guid))
            } else {
                row
            }
        }

        val continuationSections = resolveContinuationSections(
            normalizedRows
                .filter { it.guid.length >= 20 }
                .map { it.gameSection }
                .toSet()
        )

        val regularHeaders = (1..GameControl.numberOfGameSection).map { section ->
            ProtocolGoalType(section, toRegularSectionHeader(section), 1_000_000L, "", "", "", "", "")
        }

        val regularEventRows = normalizedRows.filter {
            it.guid.length >= 20 && it.gameSection <= GameControl.numberOfGameSection
        }
        val continuationRawRows = normalizedRows.filter {
            it.guid.length >= 20 && it.gameSection in continuationSections
        }

        val regularWhite = regularEventRows.count { it.numberWhite.isNotBlank() }
        val regularBlue = regularEventRows.count { it.numberBlue.isNotBlank() }
        val continuationWhite = continuationRawRows.count { it.numberWhite.isNotBlank() }
        val continuationBlue = continuationRawRows.count { it.numberBlue.isNotBlank() }
        val totalWhite = regularWhite + continuationWhite
        val totalBlue = regularBlue + continuationBlue

        totalGoalsWhite.postValue(totalWhite.toString())
        totalGoalsBlue.postValue(totalBlue.toString())

        val regularEventRowsForDisplay = normalizedRows
            .filter { it.guid.length >= 20 && it.gameSection in 1..GameControl.numberOfGameSection }
            .sortedWith(compareBy<ProtocolGoalType> { it.gameSection }.thenByDescending { it.time })

        val regularRows = mutableListOf<ProtocolGoalType>()
        regularHeaders.forEach { header ->
            regularRows.add(header)
            regularRows.addAll(regularEventRowsForDisplay.filter { it.gameSection == header.gameSection })
        }

        if (continuationSections.isEmpty()) {
            // Keep last known continuation summary during transient UI/data refresh states.
            // It is fully reset in resolveContinuationSections() when a new game GUID is detected.
            return regularRows
        }

        var runningWhite = regularWhite
        var runningBlue = regularBlue
        val continuationRows = mutableListOf<ProtocolGoalType>()
        val continuationSummary = mutableListOf<Triple<String, Int, Int>>()
        val sectionLabels = mutableMapOf<Int, String>()

        continuationSections.toList().sorted().forEach { section ->
            val sectionRowsRaw = continuationRawRows
                .filter { it.gameSection == section }
                .sortedByDescending { it.time }

            val sectionLabel = getContinuationSectionLabel(section)
            sectionLabels[section] = sectionLabel
            val sectionRows = if (sectionLabel == "PSO") {
                sectionRowsRaw.mapIndexed { index, row ->
                    row.copy(time = index * 1000L)
                }
            } else {
                sectionRowsRaw
            }

            continuationRows.add(
                ProtocolGoalType(section, sectionLabel, 1_000_000L, "", "", "", "", "")
            )

            sectionRows.forEach { row ->
                if (row.numberWhite.isNotBlank()) {
                    runningWhite += 1
                }
                if (row.numberBlue.isNotBlank()) {
                    runningBlue += 1
                }
                continuationRows.add(
                    row.copy(goalWhite = runningWhite.toString(), goalBlue = runningBlue.toString())
                )
            }
        }

        // Section sums are derived from the same goal rows shown in the "Tore" area.
        continuationSections.toList().sorted().forEach { section ->
            val label = sectionLabels[section] ?: getContinuationSectionLabel(section)
            val eventsInSection = continuationRows.filter { it.guid.length >= 20 && it.gameSection == section }
            val sectionWhite = eventsInSection.count { it.numberWhite.isNotBlank() }
            val sectionBlue = eventsInSection.count { it.numberBlue.isNotBlank() }
            continuationSummary.add(Triple(label, sectionWhite, sectionBlue))
        }

        val first = continuationSummary.getOrNull(0)
        continuationSectionLabel.postValue(first?.first ?: "")
        continuationGoalsWhite.postValue((first?.second ?: 0).toString())
        continuationGoalsBlue.postValue((first?.third ?: 0).toString())

        val second = continuationSummary.getOrNull(1)
        continuationSectionLabel2.postValue(second?.first ?: "")
        continuationGoalsWhite2.postValue((second?.second ?: 0).toString())
        continuationGoalsBlue2.postValue((second?.third ?: 0).toString())

        continuationSectionResults.postValue(
            continuationSummary.map {
                ContinuationSectionResult(
                    label = it.first,
                    whiteGoals = it.second.toString(),
                    blueGoals = it.third.toString()
                )
            }
        )

        return regularRows + continuationRows
    }

    private fun toRegularSectionHeader(section: Int): String {
        return when (section) {
            1 -> "I."
            2 -> "II."
            3 -> "III."
            4 -> "IV."
            else -> "$section."
        }
    }

    private fun getContinuationSectionLabel(section: Int): String {
        knownContinuationLabels[section]?.let { return it }

        val fromControl = GameControl.getSectionLabel(section)
        if (fromControl == "PSO" || fromControl.startsWith("OT-")) {
            knownContinuationLabels[section] = fromControl
            return fromControl
        }

        val continuationIndex = (section - GameControl.numberOfGameSection).coerceAtLeast(1)
        val fallback = if (GameControl.getContinuationModeLabel() == "PSO" || GameControl.gameEndMode == "PSO") {
            "PSO"
        } else {
            "OT-$continuationIndex"
        }
        knownContinuationLabels[section] = fallback
        return fallback
    }

    private fun resolveContinuationSections(eventSections: Set<Int>): Set<Int> {
        if (knownGameGuid != GameControl.currentGameGuid) {
            knownGameGuid = GameControl.currentGameGuid
            knownContinuationSections.clear()
            knownContinuationLabels.clear()
            continuationSectionLabel.postValue("")
            continuationGoalsWhite.postValue("0")
            continuationGoalsBlue.postValue("0")
            continuationSectionLabel2.postValue("")
            continuationGoalsWhite2.postValue("0")
            continuationGoalsBlue2.postValue("0")
            continuationSectionResults.postValue(emptyList())
        }

        val controlSections = GameControl.getContinuationSectionNumbers().toSet()
        val eventContinuationSections = eventSections.filter { it > GameControl.numberOfGameSection }.toSet()
        val continuationContextActive = controlSections.isNotEmpty() ||
            eventContinuationSections.isNotEmpty() ||
            GameControl.getContinuationModeLabel().isNotBlank() ||
            GameControl.gameEndMode.isNotBlank()
        val currentSectionFallback = if (continuationContextActive && GameControl.getCurrentGameSection() > GameControl.numberOfGameSection) {
            (GameControl.numberOfGameSection + 1..GameControl.getCurrentGameSection()).toSet()
        } else {
            emptySet()
        }

        knownContinuationSections.addAll(controlSections)
        knownContinuationSections.addAll(eventContinuationSections)
        knownContinuationSections.addAll(currentSectionFallback)

        controlSections.forEach { section ->
            val label = GameControl.getSectionLabel(section)
            if (label == "PSO" || label.startsWith("OT-")) {
                knownContinuationLabels[section] = label
            }
        }

        currentSectionFallback.forEach { section ->
            if (knownContinuationLabels.containsKey(section)) {
                return@forEach
            }
            val label = GameControl.getSectionLabel(section)
            if (label == "PSO" || label.startsWith("OT-")) {
                knownContinuationLabels[section] = label
            }
        }

        return knownContinuationSections
    }

    private fun normalizeSectionHeader(header: String): String {
        return header
            .replace("Viertel", "Abschnitt", ignoreCase = true)
            .replace("VIERTEL", "ABSCHNITT", ignoreCase = false)
    }
}
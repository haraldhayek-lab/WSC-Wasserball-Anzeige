package com.example.waterpolo3000

import android.app.AlertDialog
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.util.Log
import android.view.MotionEvent
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.waterpolo3000.adapters.GAME_PAGE_INDEX
import com.example.waterpolo3000.adapters.PROTOCOL_PAGE_INDEX
import com.example.waterpolo3000.adapters.GameEventAdapter
import com.example.waterpolo3000.data.AppDatabase
import com.example.waterpolo3000.data.ExclResult
import com.example.waterpolo3000.data.GameEventView
import com.example.waterpolo3000.databinding.FragmentGameBinding
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.*
import com.example.waterpolo3000.viewmodels.GameViewModel
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class GameFragment : Fragment() {

    private data class SnapshotFileMeta(val competition: String, val gameNumber: String)

    private data class EditPlayerOption(val label: String, val cap: String, val number: Int?)
    private data class EditEventOption(val type: Int, val label: String)
    private data class PendingGameEventEditState(
        val event: GameEventView,
        var minutes: Int,
        var seconds: Int,
        var tenths: Int,
        var cap: String,
        var number: Int?,
        var eventType: Int
    )

    private lateinit var binding: FragmentGameBinding
    private val viewModel: GameViewModel by viewModels()
    val checkNetworkConnection = CheckForInternet()

    private var playerBtnPressed: Button? = null
    private var eventBtnPressed: Button? = null

    lateinit var database: AppDatabase

    private var tempCurrentCountdown: Long = 0

    private val myArray = arrayOf("A", "O")
    private val exclusionResultWhite = (Array(PLAYER_COUNT) { ExclResult("", "", "") }).toMutableList()
    private val exclusionResultBlue = (Array(PLAYER_COUNT) { ExclResult("", "", "") }).toMutableList()

    lateinit var bindingButtonsBlue: Map<Int, Button>
    lateinit var bindingButtonsWhite: Map<Int, Button>

    var mainBoardMenuItem: MenuItem? = null
    var LedBoardMenuItem: MenuItem? = null
    val shotclockMenuItems = mutableListOf<MenuItem?>(
        null,
        null,
        null,
        null
    )
    var mainBoardConnectItem: MenuItem? = null
    var mainBoardBrightnessItem: MenuItem? = null
    val shotclockConnectItems = mutableListOf<MenuItem?>(
        null,
        null,
        null,
        null
    )
    var brightnessAllItem: MenuItem? = null
    var brightnessShotclocks = mutableListOf<MenuItem?>(
        null,
        null,
        null,
        null
    )
    var liveMenuItem: MenuItem? = null
    var hornMenuItem: MenuItem? = null
    var pauseShortenMenuItem: MenuItem? = null
    var overflowMenuItem: MenuItem? = null

    private enum class TeamImportTarget { WHITE, BLUE }

    private var pendingImportTarget: TeamImportTarget? = null
    private var importResultWhite: TeamPlayerImportResult? = null
    private var importResultBlue: TeamPlayerImportResult? = null
    private var importStatusWhiteView: TextView? = null
    private var importStatusBlueView: TextView? = null
    private var teamWhiteNameView: EditText? = null
    private var teamBlueNameView: EditText? = null
    private var timeControlsEnabledByViewModel = true
    private var pendingGameEventEdit: PendingGameEventEditState? = null
    private val rosterEnabledWhite = mutableMapOf<Int, Boolean>()
    private val rosterEnabledBlue = mutableMapOf<Int, Boolean>()
    private val exclusionBlockedWhite = mutableMapOf<Int, Boolean>()
    private val exclusionBlockedBlue = mutableMapOf<Int, Boolean>()
    private var delayedRosterRefreshJob: Job? = null
    private var startupRosterRetryJob: Job? = null

    private val saveGameLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(requireContext(), getString(R.string.save_game_cancelled), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            val exportResult = withContext(Dispatchers.IO) {
                viewModel.exportCurrentGameSnapshotJson()
            }

            val message = exportResult.fold(
                onSuccess = { json ->
                    val saved = runCatching {
                        requireContext().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            writer.write(json)
                        } ?: throw IllegalStateException("Output stream nicht verfuegbar")
                    }.isSuccess
                    if (saved) getString(R.string.save_game_success) else getString(R.string.save_game_failed)
                },
                onFailure = {
                    getString(R.string.save_game_failed)
                }
            )

            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private val loadGameLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            Toast.makeText(requireContext(), getString(R.string.load_game_cancelled), Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        lifecycleScope.launch {
            viewModel.newGame()
            resetPlayerAvailabilityCaches()
            resetPlayerExclusionUiState()
            refreshSelectablePlayerButtonsFromRoster()
            scheduleDelayedRosterRefresh()
            updateGameMetaHeader()

            val fileMeta = extractSnapshotMetaFromUri(uri)
            val importResult = withContext(Dispatchers.IO) {
                runCatching {
                    val json = requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    } ?: throw IllegalArgumentException("Datei konnte nicht gelesen werden")
                    viewModel.importGameSnapshotJson(
                        json = json,
                        overrideCompetition = fileMeta?.competition,
                        overrideGameNumber = fileMeta?.gameNumber
                    ).getOrThrow()
                }
            }

            val message = importResult.getOrElse {
                getString(R.string.load_game_failed)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            applyTimeControlModeUi()
            updateTimeControlButtonsState()
            updateGameMetaHeader()
            refreshSelectablePlayerButtonsFromRoster()
            scheduleDelayedRosterRefresh()
        }
    }

    private fun extractSnapshotMetaFromUri(uri: Uri): SnapshotFileMeta? {
        val fileName = resolveDisplayName(uri) ?: return null
        return parseSnapshotMetaFromFileName(fileName)
    }

    private fun resolveDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun parseSnapshotMetaFromFileName(fileName: String): SnapshotFileMeta? {
        val baseName = fileName.removeSuffix(".json")
        if (!baseName.startsWith(GAME_SNAPSHOT_FILENAME_PREFIX)) {
            return null
        }

        val tail = baseName.removePrefix(GAME_SNAPSHOT_FILENAME_PREFIX)
        val firstSeparator = tail.indexOf('_')
        if (firstSeparator < 0) {
            return null
        }
        val secondSeparator = tail.indexOf('_', firstSeparator + 1)
        if (secondSeparator < 0) {
            return null
        }

        val competition = tail.substring(0, firstSeparator).replace('_', ' ').trim()
        val gameNumber = tail.substring(firstSeparator + 1, secondSeparator).replace('_', ' ').trim()
        if (competition.isBlank() || gameNumber.isBlank()) {
            return null
        }

        return SnapshotFileMeta(competition = competition, gameNumber = gameNumber)
    }

    private val teamImportLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = pendingImportTarget ?: return@registerForActivityResult
        val statusView = if (target == TeamImportTarget.WHITE) importStatusWhiteView else importStatusBlueView

        if (uri == null) {
            statusView?.text = getString(R.string.team_import_no_file)
            return@registerForActivityResult
        }
        requireContext().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        statusView?.text = getString(R.string.team_import_loading)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        TeamPlayerImportParser.parse(reader.readText())
                    } ?: TeamPlayerImportResult(emptyList(), listOf(getString(R.string.team_import_open_failed)))
                }.getOrElse {
                    TeamPlayerImportResult(emptyList(), listOf(getString(R.string.team_import_parse_failed)))
                }
            }

            if (target == TeamImportTarget.WHITE) {
                importResultWhite = result
            } else {
                importResultBlue = result
            }

            val importedTeamName = result.rows.firstOrNull { !it.teamName.isNullOrBlank() }?.teamName?.trim()
            if (!importedTeamName.isNullOrEmpty()) {
                if (target == TeamImportTarget.WHITE) {
                    teamWhiteNameView?.setText(importedTeamName)
                } else {
                    teamBlueNameView?.setText(importedTeamName)
                }
            }

            val warningsText = if (result.warnings.isEmpty()) "" else " ${result.warnings.take(2).joinToString(" | ")}" 
            val teamLabel = if (target == TeamImportTarget.WHITE) getString(R.string.team_label_white) else getString(R.string.team_label_blue)
            statusView?.text = getString(R.string.team_import_loaded_for_team, teamLabel, result.rows.size) + warningsText
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentGameBinding.inflate(inflater, container, false)
        val tempListBtnBlue = mutableMapOf<Int, Button>()
        val tempListBtnWhite = mutableMapOf<Int, Button>()
        val regexBlue = "btn_B_\\d+".toRegex()
        val regexWhite = "btn_W_\\d+".toRegex()
        binding.root.allViews.asIterable().forEach {
            if (it.id > 0 &&
                regexBlue.matches(resources.getResourceName(it.id).split("/")[1]) &&
                it is Button
            ) {
                tempListBtnBlue[resources.getResourceName(it.id).split("_")[2].toInt()] = it
            } else if (it.id > 0 &&
                regexWhite.matches(resources.getResourceName(it.id).split("/")[1]) &&
                it is Button
            ) {
                tempListBtnWhite[resources.getResourceName(it.id).split("_")[2].toInt()] = it
            }
        }
        bindingButtonsBlue = tempListBtnBlue
        bindingButtonsWhite = tempListBtnWhite

        val adapter = GameEventAdapter()
        adapter.viewModelOut = viewModel
        // scroll always to the top item(last inserted) in recyclerview
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.gameEventRecyclerview.smoothScrollToPosition(0)
            }
        })
        binding.gameEventRecyclerview.adapter = adapter
        binding.setClickListener { processBtnClickEvent(it) }

        initObservers()
        subscribeUi(adapter, binding)
        setUi()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenuProvider()
        resetPlayerAvailabilityCaches()
        viewModel.btSearchExecuted = true
        applyTimeControlModeUi()
        updateGameMetaHeader()
        binding.currentGameSection.setOnClickListener {
            if (GameControl.isPauseTimerRunning()) {
                showPauseTimeEditDialog()
            } else if (isContinuationSectionDisplayed()) {
                showContinuationChoiceAfterDrawDialog()
            } else {
                showGameSectionSelectionDialog()
            }
        }
        updatePauseSectionEditState()
        updateTimeControlButtonsState()
        refreshSelectablePlayerButtonsFromRoster()
        scheduleDelayedRosterRefresh()
        if (!GameControl.gameStarted) {
            scheduleStartupRosterRefreshRetries()
        }
    }

    override fun onResume() {
        super.onResume()
        updateGameMetaHeader()
        (activity as? WaterpoloActivity)?.setHeaderSaveAction(false, null)
        refreshSelectablePlayerButtonsFromRoster()
        scheduleDelayedRosterRefresh()
        if (!GameControl.gameStarted) {
            scheduleStartupRosterRefreshRetries()
        }
    }

    private fun processBtnClickEvent(it: View) {
        val btnId = resources.getResourceName(it.id).split("/")[1]
        myVibrate()
        // handle game event buttons
        when (btnId.split("_")[1]) {
            "event" -> {
                if (playerBtnPressed == null && eventBtnPressed == null) {
                    eventBtnPressed = it as Button
                    eventBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.buttonActive
                        )
                    )
                } else if (playerBtnPressed != null && eventBtnPressed == null) {
                    playerBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (resources.getResourceName(playerBtnPressed!!.id)
                                    .split("_")[1].contains("B")
                            ) R.color.blue else R.color.white
                        )
                    )
                    viewModel.processGameEvent(
                        btnId.split("_")[3],
                        resources.getResourceName(playerBtnPressed!!.id).split("/")[1]
                    )
                    eventBtnPressed = null
                    playerBtnPressed = null
                } else if (playerBtnPressed == null && eventBtnPressed != null) {
                    eventBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (resources.getResourceName(eventBtnPressed!!.id)
                                    .split("_")[2].contains("goal")
                            ) R.color.green else R.color.red
                        )
                    )
                    eventBtnPressed = if (eventBtnPressed != it) {
                        it.setBackgroundColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.buttonActive
                            )
                        )
                        it as Button
                    } else {
                        null
                    }
                }
            }

            "time" -> {
                tempCurrentCountdown = GameControl.currentCountdown
                if (btnId.split("_")[2] == "timeout") {
                    viewModel.pauseClocksForTimeoutSelection()
                    val dialog = Dialog(requireContext())
                    dialog.setContentView(R.layout.dialog_timeout)

                    val dialogButtonWhite = dialog.findViewById<Button>(R.id.btn_white)
                    val dialogButtonCancel = dialog.findViewById<Button>(R.id.btn_cancel)
                    val dialogButtonBlue = dialog.findViewById<Button>(R.id.btn_blue)

                    dialogButtonWhite.setOnClickListener {
                        val timeoutWhite = binding.timeoutWhiteValue?.toIntOrNull() ?: 0
                        if (timeoutWhite >= DEFAULT_MAX_TIMEOUT) {
                            viewModel.cancelTimeoutSelectionResumeIfNeeded()
                            showTimeoutLimitWarningDialog("WHITE", GameControl.teamWhite.teamName)
                            return@setOnClickListener
                        }
                        viewModel.processTime("timeout")
                        viewModel.storeTimeout(WHITE, tempCurrentCountdown)
                        tempCurrentCountdown = 0
                        dialog.dismiss()
                    }
                    dialogButtonBlue.setOnClickListener {
                        val timeoutBlue = binding.timeoutBlueValue?.toIntOrNull() ?: 0
                        if (timeoutBlue >= DEFAULT_MAX_TIMEOUT) {
                            viewModel.cancelTimeoutSelectionResumeIfNeeded()
                            showTimeoutLimitWarningDialog("BLUE", GameControl.teamBlue.teamName)
                            return@setOnClickListener
                        }
                        viewModel.processTime("timeout")
                        viewModel.storeTimeout(BLUE, tempCurrentCountdown)
                        tempCurrentCountdown = 0
                        dialog.dismiss()
                    }
                    dialogButtonCancel.setOnClickListener {
                        viewModel.cancelTimeoutSelectionResumeIfNeeded()
                        dialog.dismiss()
                    }
                    dialog.show()
                } else {
                    val wasGameStarted = GameControl.gameStarted
                    viewModel.processTime(btnId.split("_")[2])
                    updateTimeControlButtonsState()
                    val isFirstStartClick = btnId.split("_")[2] == "StartStop" && !wasGameStarted && GameControl.gameStarted
                    if (isFirstStartClick) {
                        scheduleDelayedRosterRefresh(250L)
                    }
                }
            }

            else -> {
                if (playerBtnPressed == null && eventBtnPressed == null) {
                    playerBtnPressed = it as Button
                    playerBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.buttonActive
                        )
                    )

                } else if (playerBtnPressed == null && eventBtnPressed != null) {
                    eventBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (resources.getResourceName(eventBtnPressed!!.id)
                                    .split("_")[2].contains("goal")
                            ) R.color.green else R.color.red
                        )
                    )
                    viewModel.processGameEvent(
                        resources.getResourceName(eventBtnPressed!!.id)
                            .split("/")[1].split("_")[3], btnId
                    )
                    playerBtnPressed = it as Button
                    eventBtnPressed = null
                    playerBtnPressed = null

                } else if (playerBtnPressed != null && eventBtnPressed == null) {
                    playerBtnPressed!!.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            if (resources.getResourceName(playerBtnPressed!!.id)
                                    .split("_")[1].contains("B")
                            ) R.color.blue else R.color.white
                        )
                    )
                    playerBtnPressed = if (playerBtnPressed != it) {
                        it.setBackgroundColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.buttonActive
                            )
                        )
                        it as Button
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun startGameEventCorrectionDialog(event: GameEventView) {
        val state = PendingGameEventEditState(
            event = event,
            minutes = MyTimeConverter.getMinutesFromLong(event.time),
            seconds = MyTimeConverter.getSecondsFromLong(event.time),
            tenths = MyTimeConverter.getSecondsSmallFromLong(event.time),
            cap = if (event.cap.equals(WHITE, ignoreCase = true)) WHITE else BLUE,
            number = event.number.toIntOrNull(),
            eventType = event.gameEventType
        )
        pendingGameEventEdit = state
        showGameEventCorrectionDialog(state)
    }

    private fun showGameEventCorrectionDialog(state: PendingGameEventEditState) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_select_game_event_time)

        val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
        val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)
        val numPickerMinutes = dialog.findViewById<NumberPicker>(R.id.numberpicker_minutes)
        val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds)
        val numPickerSecondsSmall = dialog.findViewById<NumberPicker>(R.id.numberpicker_secondsSmall)
        val numPickerPlayer = dialog.findViewById<NumberPicker>(R.id.numberpicker_player)
        val numPickerEventType = dialog.findViewById<NumberPicker>(R.id.numberpicker_event_type)

        val playerOptions = buildEditPlayerOptions()
        val eventOptions = buildEditEventOptions()

        numPickerMinutes.maxValue = maxOf((GameControl.gameSectionLength / 60), state.minutes, 20)
        numPickerMinutes.minValue = 0
        numPickerMinutes.value = state.minutes
        numPickerSeconds.maxValue = 59
        numPickerSeconds.minValue = 0
        numPickerSeconds.value = state.seconds
        numPickerSecondsSmall.maxValue = 9
        numPickerSecondsSmall.minValue = 0
        numPickerSecondsSmall.value = state.tenths

        numPickerPlayer.displayedValues = null
        numPickerPlayer.minValue = 0
        numPickerPlayer.maxValue = playerOptions.lastIndex
        numPickerPlayer.displayedValues = playerOptions.map { it.label }.toTypedArray()
        numPickerPlayer.value = playerOptions.indexOfFirst {
            it.cap.equals(state.cap, ignoreCase = true) && it.number == state.number
        }.takeIf { it >= 0 } ?: playerOptions.indexOfFirst {
            it.cap.equals(state.cap, ignoreCase = true) && it.number == null
        }.takeIf { it >= 0 } ?: 0

        numPickerEventType.displayedValues = null
        numPickerEventType.minValue = 0
        numPickerEventType.maxValue = eventOptions.lastIndex
        numPickerEventType.displayedValues = eventOptions.map { it.label }.toTypedArray()
        numPickerEventType.value = eventOptions.indexOfFirst { it.type == state.eventType }.takeIf { it >= 0 } ?: 0

        dialogButtonOk.setOnClickListener {
            val selectedPlayer = playerOptions[numPickerPlayer.value]
            val selectedEvent = eventOptions[numPickerEventType.value]
            val participantGuid = GameControl.getParticipantByCapNumber(selectedPlayer.cap, selectedPlayer.number ?: 0)

            val newTime =
                (numPickerMinutes.value * 60 * 1000L) +
                    (numPickerSeconds.value * 1000L) +
                    (numPickerSecondsSmall.value * 100L)

            val oldSection = state.event.gameSection.toIntOrNull() ?: GameControl.getCurrentGameSection()

            viewModel.updateGameEvent(
                gameEvent = state.event,
                newTime = newTime,
                participantGuid = participantGuid,
                eventType = selectedEvent.type,
                oldCap = state.cap,
                oldNumber = state.number,
                oldSection = oldSection,
                oldTime = state.event.time,
                newCap = selectedPlayer.cap,
                newNumber = selectedPlayer.number,
                newSection = oldSection
            )
            pendingGameEventEdit = null
            dialog.dismiss()
        }

        dialogButtonCancel.setOnClickListener {
            pendingGameEventEdit = null
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.50f).toInt(),
            (resources.displayMetrics.heightPixels * 0.85f).toInt()
        )
    }

    private fun buildEditPlayerOptions(): List<EditPlayerOption> {
        val options = mutableListOf<EditPlayerOption>()
        for (number in 1..PLAYER_COUNT) {
            options.add(EditPlayerOption("W$number", WHITE, number))
        }
        for (number in 1..PLAYER_COUNT) {
            options.add(EditPlayerOption("B$number", BLUE, number))
        }
        return options
    }

    private fun buildEditEventOptions(): List<EditEventOption> {
        return listOf(
            EditEventOption(100, "Tor (Spiel)"),
            EditEventOption(101, "Tor (Freiw.)"),
            EditEventOption(102, "Tor (Mann+)"),
            EditEventOption(103, "Tor (Mann-)"),
            EditEventOption(104, "Tor (Penalty)"),
            EditEventOption(200, "Ausschluss"),
            EditEventOption(201, "Penalty"),
            EditEventOption(202, "Rolle"),
            EditEventOption(203, "Rot (4 min.)")
        )
    }

    private fun setUi() {
        viewModel.setAll()
    }

    private fun myVibrate() {
        val appContext = context ?: return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            ContextCompat.getSystemService(appContext, Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun setupMenuProvider() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                createOptionsMenu(menu, menuInflater)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return handleOptionsItemSelected(menuItem)
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun createOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_game, menu)
        LedBoardMenuItem = menu.findItem(R.id.led_boards_menu)
        overflowMenuItem = menu.findItem(R.id.overflowMenu)
        mainBoardMenuItem = menu.findItem(R.id.mainBoard_item)
        shotclockMenuItems[0] = menu.findItem(R.id.shotclock_1_item)
        shotclockMenuItems[1] = menu.findItem(R.id.shotclock_2_item)
        shotclockMenuItems[2] = menu.findItem(R.id.shotclock_3_item)
        shotclockMenuItems[3] = menu.findItem(R.id.shotclock_4_item)
        mainBoardConnectItem = menu.findItem(R.id.mainboard_connect_item)
        shotclockConnectItems[0] = menu.findItem(R.id.shotclock1_connect_item)
        shotclockConnectItems[1] = menu.findItem(R.id.shotclock2_connect_item)
        shotclockConnectItems[2] = menu.findItem(R.id.shotclock3_connect_item)
        shotclockConnectItems[3] = menu.findItem(R.id.shotclock4_connect_item)
        mainBoardBrightnessItem = menu.findItem(R.id.mainboard_brigthness_item)
        brightnessAllItem = menu.findItem(R.id.brigthness_all_item)
        brightnessShotclocks[0] = menu.findItem(R.id.brigthness_shotclock_1_item)
        brightnessShotclocks[1] = menu.findItem(R.id.brigthness_shotclock_2_item)
        brightnessShotclocks[2] = menu.findItem(R.id.brigthness_shotclock_3_item)
        brightnessShotclocks[3] = menu.findItem(R.id.brigthness_shotclock_4_item)
        liveMenuItem = menu.findItem(R.id.item_live)
        hornMenuItem = menu.findItem(R.id.item_horn)
        pauseShortenMenuItem = menu.findItem(R.id.item_edit_pause_time)
        liveMenuItem?.icon = getActionMenuSpacedIcon(R.drawable.ic_live_inactive)
        overflowMenuItem?.icon = getActionMenuSpacedIcon(R.drawable.ic_3_menu)
        hornMenuItem?.actionView = createHornActionView()
        setOptionsMenu()
    }

    private fun handleOptionsItemSelected(item: MenuItem): Boolean {
        return (when (item.itemId) {
            R.id.item_new_game -> {
                showNewGameConfirmDialog()
                true
            }
            R.id.item_save_game -> {
                saveGameLauncher.launch(viewModel.createGameSnapshotFileName())
                true
            }
            R.id.item_load_game -> {
                showLoadGameConfirmDialog()
                true
            }
            R.id.item_end_game -> {
                showEndGameDialog()
                true
            }
            R.id.item_live -> {
                if (viewModel.liveGame) {
                    viewModel.liveGame = false
                    liveMenuItem?.icon = activity?.let {
                        getActionMenuSpacedIcon(R.drawable.ic_live_inactive)
                    }

                    return true
                }
                if (context?.let { checkNetworkConnection.isOnline(it) } == true) {
                    viewModel.liveGame = true
                    viewModel.createFirstFirebaseEntry()
                    liveMenuItem?.icon = activity?.let { getActionMenuSpacedIcon(R.drawable.ic_live_active) }
                    Toast.makeText(requireContext(), if (viewModel.liveGame) "Live Übertragung aktiviert" else "Live Übertragung deaktiviert", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Internet nicht vebunden", Toast.LENGTH_LONG)
                        .show()
                }
                true
            }
            R.id.item_edit_main_time -> {
                val dialog = Dialog(requireContext())
                dialog.setContentView(R.layout.dialog_edit_time)

                val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
                val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)

                val numPickerMinutes = dialog.findViewById<NumberPicker>(R.id.numberpicker_minutes)
                val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds)
                val numPickerSecondsSmall =
                    dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds_small)
                val currentMainCountdown = GameControl.currentCountdown
                numPickerMinutes.maxValue = 20
                numPickerMinutes.minValue = 0
                numPickerMinutes.value = MyTimeConverter.getMinutesFromLong(currentMainCountdown)
                numPickerSeconds.maxValue = 59
                numPickerSeconds.minValue = 0
                numPickerSeconds.value = MyTimeConverter.getSecondsFromLong(currentMainCountdown)
                numPickerSecondsSmall.maxValue = 9
                numPickerSecondsSmall.minValue = 0
                numPickerSecondsSmall.value = MyTimeConverter.getSecondsSmallFromLong(currentMainCountdown)

                dialogButtonOk.setOnClickListener {
                    Toast.makeText(requireContext(), "test me", Toast.LENGTH_SHORT).show()
                    GameControl.currentCountdown =
                        ((numPickerMinutes.value * 60 * 1000) + (numPickerSeconds.value * 1000) + (numPickerSecondsSmall.value * 100)).toLong()
                    GameControl.setGameTimeEdit()
                    dialog.dismiss()
                }

                dialogButtonCancel.setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
                true
            }
            R.id.item_edit_pause_time -> {
                showPauseTimeEditDialog()
                true
            }
            R.id.item_edit_shotclock -> {
                val dialog = Dialog(requireContext())
                dialog.setContentView(R.layout.dialog_edit_shotclock)

                val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
                val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)

                val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds)
                val numPickerSecondsSmall =
                    dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds_small)
                val currentShotclockCountdown = GameControl.currentCountdownShotclock
                numPickerSeconds.maxValue = 30
                numPickerSeconds.minValue = 0
                numPickerSeconds.value = MyTimeConverter.getSecondsFromLong(currentShotclockCountdown)
                numPickerSecondsSmall.maxValue = 9
                numPickerSecondsSmall.minValue = 0
                numPickerSecondsSmall.value = MyTimeConverter.getSecondsSmallFromLong(currentShotclockCountdown)

                dialogButtonOk.setOnClickListener {
                    Toast.makeText(requireContext(), "test me", Toast.LENGTH_SHORT).show()
                    GameControl.currentCountdownShotclock =
                        ((numPickerSeconds.value * 1000) + (numPickerSecondsSmall.value * 100)).toLong()
                    GameControl.setShotclockEdit()
                    dialog.dismiss()
                }

                dialogButtonCancel.setOnClickListener {
                    dialog.dismiss()
                }
                dialog.show()
                true
            }
            R.id.item_game_settings -> {
                showGameSettingsDialog()
                true
            }
            R.id.item_draw_mode -> {
                if (GameControl.isGameFinished()) {
                    GameControl.reopenEndedGameForCorrection()
                }
                showContinuationChoiceAfterDrawDialog()
                true
            }
            R.id.item_edit_teams -> {
                showTeamSettingsDialog()
                true
            }
            R.id.mainboard_brigthness_item -> {
                val dialog = Dialog(requireContext())
                dialog.setContentView(R.layout.dialog_brightness)
                val brightnessText = "brightness%"
                val slider = dialog.findViewById<Slider>(R.id.slider_brightness)
                slider.value = viewModel.mainboardBrightness.toFloat()
                slider.addOnChangeListener() { _, value, _ ->
                    val output = "$brightnessText${value.toInt()}"
                    Log.d(TAG, "output: $value")
                    ProcessBT.sendMessageToMainBoard(output)
                    viewModel.mainboardBrightness = value.toInt()
                }
                dialog.show()
                true
            }
            R.id.brigthness_all_item -> {
                val dialog = Dialog(requireContext())
                dialog.setContentView(R.layout.dialog_brightness)
                val brightnessText = "brightness%"
                val slider = dialog.findViewById<Slider>(R.id.slider_brightness)
                slider.value = viewModel.allBrightness.toFloat()
                slider.addOnChangeListener() { _, value, _ ->
                    val output = "$brightnessText${value.toInt()}"
                    Log.d(TAG, "output: $value")
                    ProcessBT.sendMessageToMainBoard(output)
                    ProcessBT.sendMessageToAllShotClock(output)
                    viewModel.allBrightness = value.toInt()
                }
                dialog.show()
                true
            }
            R.id.brigthness_shotclock_1_item -> {
                showShotclockBrightnessDialog(0)
                true
            }
            R.id.brigthness_shotclock_2_item -> {
                showShotclockBrightnessDialog(1)
                true
            }
            R.id.brigthness_shotclock_3_item -> {
                showShotclockBrightnessDialog(2)
                true
            }
            R.id.brigthness_shotclock_4_item -> {
                showShotclockBrightnessDialog(3)
                true
            }
            R.id.connect_all_item -> {
                binding.bluetoothConnectionProgressBar.visibility = View.VISIBLE
                binding.bluetoothConnectionTextview.visibility = View.VISIBLE
                viewModel.bluetoothConnectAll()
                true
            }
            R.id.mainboard_connect_item -> {
                viewModel.connectMainBoard(": Haupt Tafel")
                true
            }
            R.id.shotclock1_connect_item -> {
                viewModel.connectShotclock(1, ": Shotclock 1")
                true
            }
            R.id.shotclock2_connect_item -> {
                viewModel.connectShotclock(2, ": Shotclock 2")
                true
            }
            R.id.shotclock3_connect_item -> {
                viewModel.connectShotclock(3, ": Shotclock 3")
                true
            }
            R.id.shotclock4_connect_item -> {
                viewModel.connectShotclock(4, ": Shotclock 4")
                true
            }
            else -> false
        })
    }

    private fun showGameSettingsDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_settings)

        val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
        val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)

        val numPickerMinutes = dialog.findViewById<NumberPicker>(R.id.numberpicker_min)
        val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_sec)
        val numPickerBreakBig = dialog.findViewById<NumberPicker>(R.id.numberpicker_break_big)
        val numPickerBreakSmall = dialog.findViewById<NumberPicker>(R.id.numberpicker_break_small)
        val numPickerBreakOtPso = dialog.findViewById<NumberPicker>(R.id.numberpicker_break_ot_pso)
        val numPickerTimeoutLength = dialog.findViewById<NumberPicker>(R.id.numberpicker_timeout_length)
        val numPickerTimeoutAmount = dialog.findViewById<NumberPicker>(R.id.numberpicker_timeout_amount)
        val numPickerShotclockLong = dialog.findViewById<NumberPicker>(R.id.numberpicker_shotclock_long)
        val numPickerShotclockShort = dialog.findViewById<NumberPicker>(R.id.numberpicker_shotclock_short)
        val numPickerPeriods = dialog.findViewById<NumberPicker>(R.id.numberpicker_periods)
        val numPickerOt = dialog.findViewById<NumberPicker>(R.id.numberpicker_ot)
        val numPickerPso = dialog.findViewById<NumberPicker>(R.id.numberpicker_pso)
        val numPickerTimeType = dialog.findViewById<NumberPicker>(R.id.numberpicker_time_type)

        dialogButtonOk.text = getString(R.string.new_game_meta_ok)
        dialogButtonCancel.text = getString(R.string.new_game_meta_cancel)
        dialogButtonOk.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonOk.setTextColor(Color.WHITE)
        dialogButtonCancel.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        dialogButtonCancel.setTextColor(Color.WHITE)

        configureNumberPicker(numPickerMinutes, 0, 20, GameControl.gameSectionLength / 60)
        configureNumberPicker(numPickerSeconds, 0, 59, GameControl.gameSectionLength % 60)
        configureNumberPicker(numPickerBreakBig, 0, 20, DEFAULT_PAUSE_LONG_LENGTH / 60)
        configureNumberPicker(numPickerBreakSmall, 0, 20, DEFAULT_PAUSE_SHORT_LENGTH / 60)
        configureNumberPicker(numPickerBreakOtPso, 0, 20, DEFAULT_PAUSE_OT_PSO_LENGTH / 60)
        configureNumberPicker(numPickerTimeoutLength, 0, 5, DEFAULT_TIMEOUT_LENGTH / 60)
        configureNumberPicker(numPickerTimeoutAmount, 0, 10, DEFAULT_MAX_TIMEOUT)
        configureNumberPicker(numPickerShotclockLong, 0, 60, DEFAULT_SHOTCLOCK_BIG_LENGTH)
        configureNumberPicker(numPickerShotclockShort, 0, 60, DEFAULT_SHOTCLOCK_SMALL_LENGTH)
        configureNumberPicker(numPickerPeriods, 1, 4, GameControl.numberOfGameSection)
        configureBooleanPicker(numPickerOt, GameControl.isOvertimeEnabled())
        configureBooleanPicker(numPickerPso, GameControl.isPsoEnabled())
        numPickerTimeType.displayedValues = null
        numPickerTimeType.minValue = 0
        numPickerTimeType.maxValue = 1
        numPickerTimeType.displayedValues = arrayOf("netto", "brutto")
        numPickerTimeType.value = if (DEFAULT_TIME_IS_BRUTTO) 1 else 0

        dialogButtonOk.setOnClickListener {
            viewModel.applyAndPersistGameStandards(
                GameStandards(
                    numberOfGameSection = numPickerPeriods.value,
                    overtimeEnabled = numPickerOt.value == 1,
                    psoEnabled = numPickerPso.value == 1,
                    gameSectionLength = (numPickerMinutes.value * 60) + numPickerSeconds.value,
                    shotclockLongLength = numPickerShotclockLong.value,
                    shotclockShortLength = numPickerShotclockShort.value,
                    pauseLongLength = numPickerBreakBig.value * 60,
                    pauseShortLength = numPickerBreakSmall.value * 60,
                    pauseOtPsoLength = numPickerBreakOtPso.value * 60,
                    timeoutLength = numPickerTimeoutLength.value * 60,
                    maxTimeout = numPickerTimeoutAmount.value,
                    timeIsBrutto = numPickerTimeType.value == 1
                )
            )

            applyTimeControlModeUi()
            updateTimeControlButtonsState()

            dialog.dismiss()
        }

        dialogButtonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.60f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showTeamSettingsDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_teams)

        val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
        val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)
        val dialogButtonImportWhite = dialog.findViewById<Button>(R.id.dialogBtnImportWhite)
        val dialogButtonImportBlue = dialog.findViewById<Button>(R.id.dialogBtnImportBlue)
        val importStatusWhite = dialog.findViewById<TextView>(R.id.textview_import_status_white)
        val importStatusBlue = dialog.findViewById<TextView>(R.id.textview_import_status_blue)
        val editTextTeamWhite = dialog.findViewById<EditText>(R.id.edittext_team_white)
        val editTextTeamBlue = dialog.findViewById<EditText>(R.id.edittext_team_blue)

        dialogButtonImportWhite.setBackgroundResource(R.drawable.bg_button_white_black_border)
        dialogButtonImportWhite.setTextColor(Color.BLACK)
        dialogButtonImportBlue.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.blue))
        dialogButtonImportBlue.setTextColor(Color.WHITE)

        dialogButtonOk.text = getString(R.string.new_game_meta_ok)
        dialogButtonCancel.text = getString(R.string.new_game_meta_cancel)
        dialogButtonOk.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonOk.setTextColor(Color.WHITE)
        dialogButtonCancel.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        dialogButtonCancel.setTextColor(Color.WHITE)

        importResultWhite = null
        importResultBlue = null
        importStatusWhiteView = importStatusWhite
        importStatusBlueView = importStatusBlue
        teamWhiteNameView = editTextTeamWhite
        teamBlueNameView = editTextTeamBlue
        importStatusWhite.text = getString(R.string.team_import_status_idle)
        importStatusBlue.text = getString(R.string.team_import_status_idle)

        editTextTeamWhite.setText(GameControl.teamWhite.teamName)
        editTextTeamBlue.setText(GameControl.teamBlue.teamName)

        dialogButtonImportWhite.setOnClickListener {
            pendingImportTarget = TeamImportTarget.WHITE
            teamImportLauncher.launch(arrayOf("text/plain", "text/csv", "application/json", "application/*+json", "text/*"))
        }
        dialogButtonImportBlue.setOnClickListener {
            pendingImportTarget = TeamImportTarget.BLUE
            teamImportLauncher.launch(arrayOf("text/plain", "text/csv", "application/json", "application/*+json", "text/*"))
        }

        dialogButtonOk.setOnClickListener {
            val textTeamWhiteInput = editTextTeamWhite.text.toString().trim()
            val textTeamBlueInput = editTextTeamBlue.text.toString().trim()

            val textTeamWhite = if (textTeamWhiteInput.isNotEmpty()) {
                textTeamWhiteInput
            } else {
                GameControl.teamWhite.teamName.ifBlank { "WHITE" }
            }
            val textTeamBlue = if (textTeamBlueInput.isNotEmpty()) {
                textTeamBlueInput
            } else {
                GameControl.teamBlue.teamName.ifBlank { "BLUE" }
            }

            GameControl.teamWhite.teamName = textTeamWhite
            GameControl.teamBlue.teamName = textTeamBlue
            viewModel.addTeam(GameControl.teamWhite)
            viewModel.addTeam(GameControl.teamBlue)

            importResultWhite?.let { result ->
                applyImportedPlayersForTeam(result, WHITE)
            }
            importResultBlue?.let { result ->
                applyImportedPlayersForTeam(result, BLUE)
            }

            ProcessBT.sendTeamNamesToMainBoardReliable()

            importStatusWhiteView = null
            importStatusBlueView = null
            teamWhiteNameView = null
            teamBlueNameView = null
            pendingImportTarget = null
            dialog.dismiss()
        }

        dialogButtonCancel.setOnClickListener {
            importStatusWhiteView = null
            importStatusBlueView = null
            teamWhiteNameView = null
            teamBlueNameView = null
            pendingImportTarget = null
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun applyImportedPlayersForTeam(result: TeamPlayerImportResult, cap: String) {
        val rows = result.rows.filter { it.number != null && it.number in 1..PLAYER_COUNT }
        if (rows.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.team_import_no_valid_rows), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var applied = 0
            val importedNumbers = rows.mapNotNull { it.number }.toSet()

            rows.forEach { row ->
                val number = row.number ?: return@forEach
                val playerGuid = if (cap == WHITE) {
                    GameControl.participantListWhite.getOrNull(number)?.player
                } else {
                    GameControl.participantListBlue.getOrNull(number)?.player
                } ?: return@forEach

                val player = com.example.waterpolo3000.data.Player(playerGuid).apply {
                    playerFirstName = row.firstName.ifBlank { "Vorname" }
                    val baseLastName = row.lastName.ifBlank { "Nachname" }
                    val yearSuffix = row.yearText?.trim()?.takeIf { it.isNotEmpty() && it.any(Char::isDigit) }
                    playerLastName = listOfNotNull(baseLastName, yearSuffix).joinToString(" ")
                    playerLicense = row.externalId?.toIntOrNull() ?: 0
                }
                viewModel.db.playerDao().insert(player)
                applied++
                return@forEach
            }

            // Fill missing jersey numbers with blanks so protocol rows remain visually present.
            (1..PLAYER_COUNT).forEach { number ->
                if (number in importedNumbers) {
                    return@forEach
                }
                val playerGuid = if (cap == WHITE) {
                    GameControl.participantListWhite.getOrNull(number)?.player
                } else {
                    GameControl.participantListBlue.getOrNull(number)?.player
                } ?: return@forEach

                val player = com.example.waterpolo3000.data.Player(playerGuid).apply {
                    playerFirstName = " "
                    playerLastName = " "
                }
                viewModel.db.playerDao().insert(player)
            }

            val skipped = rows.size - applied

            withContext(Dispatchers.Main) {
                val teamLabel = if (cap == WHITE) getString(R.string.team_label_white) else getString(R.string.team_label_blue)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.team_import_applied_for_team, teamLabel, applied, skipped),
                    Toast.LENGTH_LONG
                ).show()
                refreshSelectablePlayerButtonsFromRoster()
                scheduleDelayedRosterRefresh()
            }
        }
    }

    private fun resetPlayerAvailabilityCaches() {
        rosterEnabledWhite.clear()
        rosterEnabledBlue.clear()
        exclusionBlockedWhite.clear()
        exclusionBlockedBlue.clear()
        for (number in 1..PLAYER_COUNT) {
            rosterEnabledWhite[number] = true
            rosterEnabledBlue[number] = true
            exclusionBlockedWhite[number] = false
            exclusionBlockedBlue[number] = false
        }
    }

    private fun scheduleDelayedRosterRefresh(delayMs: Long = 450L) {
        delayedRosterRefreshJob?.cancel()
        delayedRosterRefreshJob = lifecycleScope.launch {
            delay(delayMs)
            refreshSelectablePlayerButtonsFromRoster()
        }
    }

    private fun scheduleStartupRosterRefreshRetries() {
        startupRosterRetryJob?.cancel()
        startupRosterRetryJob = lifecycleScope.launch {
            listOf(250L, 800L, 1600L).forEach { delayMs ->
                delay(delayMs)
                refreshSelectablePlayerButtonsFromRoster()
            }
        }
    }

    private fun refreshSelectablePlayerButtonsFromRoster() {
        lifecycleScope.launch {
            val whiteState = withContext(Dispatchers.IO) { loadRosterEnabledState(WHITE) }
            val blueState = withContext(Dispatchers.IO) { loadRosterEnabledState(BLUE) }

            whiteState.forEach { (number, enabled) ->
                rosterEnabledWhite[number] = enabled
                applyPlayerButtonAvailability(WHITE, number)
            }
            blueState.forEach { (number, enabled) ->
                rosterEnabledBlue[number] = enabled
                applyPlayerButtonAvailability(BLUE, number)
            }
        }
    }

    private suspend fun loadRosterEnabledState(cap: String): Map<Int, Boolean> {
        val result = mutableMapOf<Int, Boolean>()
        for (number in 1..PLAYER_COUNT) {
            val playerGuid = if (cap == WHITE) {
                GameControl.participantListWhite.getOrNull(number)?.player
            } else {
                GameControl.participantListBlue.getOrNull(number)?.player
            }

            val enabled = if (playerGuid.isNullOrBlank()) {
                true
            } else {
                val playerLookup = runCatching {
                    viewModel.db.playerDao().getPlayer(playerGuid).first()
                }
                val player = playerLookup.getOrNull()
                if (player == null && playerLookup.isFailure) {
                    true
                } else {
                    player != null && hasConfiguredPlayerName(player.playerFirstName, player.playerLastName)
                }
            }
            result[number] = enabled
        }
        return result
    }

    private fun hasConfiguredPlayerName(firstName: String?, lastName: String?): Boolean {
        return !firstName.isNullOrBlank() || !lastName.isNullOrBlank()
    }

    private fun applyPlayerButtonAvailability(cap: String, number: Int) {
        val isRosterEnabled = if (cap == WHITE) {
            rosterEnabledWhite[number] ?: true
        } else {
            rosterEnabledBlue[number] ?: true
        }
        val isExclusionBlocked = if (cap == WHITE) {
            exclusionBlockedWhite[number] ?: false
        } else {
            exclusionBlockedBlue[number] ?: false
        }

        // Before game start, keep player selection controlled only by roster usage.
        // This avoids stale exclusion states right after app launch.
        val effectiveExclusionBlocked = if (GameControl.gameStarted) isExclusionBlocked else false

        val enabled = isRosterEnabled && !effectiveExclusionBlocked
        val button = if (cap == WHITE) bindingButtonsWhite[number] else bindingButtonsBlue[number]
        button?.isEnabled = enabled
        button?.isClickable = enabled
        button?.alpha = if (enabled) 1.0f else 0.55f
        button?.setBackgroundColor(
            ContextCompat.getColor(
                requireContext(),
                if (enabled) {
                    if (cap == WHITE) R.color.white else R.color.blue
                } else {
                    R.color.gray_50_a600
                }
            )
        )

        if (!enabled && playerBtnPressed == button) {
            playerBtnPressed = null
        }
    }

    private fun showShotclockBrightnessDialog(index: Int) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_brightness)
        val slider = dialog.findViewById<Slider>(R.id.slider_brightness)
        slider.value = viewModel.shotclockBrightness[index].toFloat()
        slider.addOnChangeListener { _, value, _ ->
            val output = "brightness%${value.toInt()}"
            Log.d(TAG, "shotclock brightness[$index]: $value")
            ProcessBT.sendMessageToShotClock(output, index)
            viewModel.shotclockBrightness[index] = value.toInt()
        }
        dialog.show()
    }

    private fun showNewGameConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirm_load_game)

        val titleView = dialog.findViewById<TextView>(R.id.textview_load_game_title)
        val messageView = dialog.findViewById<TextView>(R.id.textview_load_game_message)
        val dialogButtonYes = dialog.findViewById<Button>(R.id.dialogBtnYes)
        val dialogButtonNo = dialog.findViewById<Button>(R.id.dialogBtnNo)

        titleView.text = getString(R.string.new_game_confirm_title)
        messageView.text = getString(R.string.load_game_confirm_message)

        dialogButtonYes.setTextColor(Color.WHITE)
        dialogButtonYes.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonNo.setTextColor(Color.WHITE)
        dialogButtonNo.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))

        dialogButtonYes.setOnClickListener {
            dialog.dismiss()
            showNewGameMetaDialog()
        }
        dialogButtonNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showNewGameMetaDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirm_new_game)

        val dialogButtonYes = dialog.findViewById<Button>(R.id.dialogBtnYes)
        val dialogButtonNo = dialog.findViewById<Button>(R.id.dialogBtnNo)
        val competitionEditText = dialog.findViewById<EditText>(R.id.edittext_competition)
        val gameNumberEditText = dialog.findViewById<EditText>(R.id.edittext_game_number)
        val deleteCurrentLabel = dialog.findViewById<TextView>(R.id.textview_delete_current)

        deleteCurrentLabel.visibility = View.GONE
        dialogButtonYes.text = getString(R.string.new_game_meta_ok)
        dialogButtonNo.text = getString(R.string.new_game_meta_cancel)
        dialogButtonYes.setTextColor(Color.WHITE)
        dialogButtonYes.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonNo.setTextColor(Color.WHITE)
        dialogButtonNo.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))

        competitionEditText.setText(GameControl.competitionName)
        gameNumberEditText.setText(GameControl.gameNumberLabel)

        dialogButtonYes.setOnClickListener {
            GameControl.setGameMeta(
                competitionEditText.text.toString(),
                gameNumberEditText.text.toString()
            )
            GameMetaCache.save(
                requireContext(),
                GameMeta(GameControl.competitionName, GameControl.gameNumberLabel)
            )
            viewModel.newGame()
            resetPlayerAvailabilityCaches()
            resetPlayerExclusionUiState()
            refreshSelectablePlayerButtonsFromRoster()
            scheduleDelayedRosterRefresh()
            updateGameMetaHeader()
            dialog.dismiss()
        }

        dialogButtonNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEndGameDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirm_end_game)

        val dialogButtonYes = dialog.findViewById<Button>(R.id.dialogBtnEndGameYes)
        val dialogButtonNo = dialog.findViewById<Button>(R.id.dialogBtnEndGameNo)

        dialogButtonYes.setTextColor(Color.WHITE)
        dialogButtonYes.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonNo.setTextColor(Color.WHITE)
        dialogButtonNo.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))

        dialogButtonNo.setOnClickListener {
            dialog.dismiss()
        }
        dialogButtonYes.setOnClickListener {
            dialog.dismiss()
            if (GameControl.isDrawByDisplayedResult() && (GameControl.isPsoEnabled() || GameControl.isOvertimeEnabled())) {
                showEndGameModeDialog()
            } else {
                showEndGameNotesDialog("")
            }
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showLoadGameConfirmDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_confirm_load_game)

        val dialogButtonYes = dialog.findViewById<Button>(R.id.dialogBtnYes)
        val dialogButtonNo = dialog.findViewById<Button>(R.id.dialogBtnNo)

        dialogButtonYes.setTextColor(Color.WHITE)
        dialogButtonYes.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        dialogButtonNo.setTextColor(Color.WHITE)
        dialogButtonNo.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))

        dialogButtonYes.setOnClickListener {
            loadGameLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            dialog.dismiss()
        }
        dialogButtonNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showEndGameModeDialog() {
        val options = mutableListOf<String>()
        if (GameControl.isPsoEnabled()) {
            options.add("PSO")
        }
        if (GameControl.isOvertimeEnabled()) {
            options.add("OT")
        }

        if (options.isEmpty()) {
            showEndGameNotesDialog("")
            return
        }

        var selected = options.first()
        var dialog: AlertDialog? = null
        dialog = AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.end_game_draw_title))
            .setSingleChoiceItems(options.toTypedArray(), 0) { _, which ->
                selected = options[which]
                dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.text =
                    getString(R.string.end_game_continue_with_selected_text, selected)
            }
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton(R.string.skip_text) { _, _ ->
                showEndGameNotesDialog("")
            }
            .setPositiveButton(getString(R.string.end_game_continue_with_selected_text, selected)) { _, _ ->
                showEndGameNotesDialog(selected)
            }
            .show()

        styleEndGameModeDialogButtons(dialog)
    }

    private fun styleEndGameModeDialogButtons(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(Color.BLACK)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.yellow_500))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        }
    }

    private fun showEndGameNotesDialog(endMode: String) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_end_game_notes)

        val notesInput = dialog.findViewById<EditText>(R.id.edittext_end_game_notes)
        val finishButton = dialog.findViewById<Button>(R.id.dialogBtnEndGame)
        val cancelButton = dialog.findViewById<Button>(R.id.dialogBtnCancel)

        finishButton.setTextColor(Color.WHITE)
        finishButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        cancelButton.setTextColor(Color.WHITE)
        cancelButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))

        finishButton.setOnClickListener {
            GameControl.markGameEnded(endMode, notesInput.text.toString())
            requireActivity().findViewById<ViewPager2>(R.id.view_pager).currentItem = PROTOCOL_PAGE_INDEX
            dialog.dismiss()
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.4f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateGameMetaHeader() {
        (activity as? WaterpoloActivity)?.updateGameMetaHeader(GameControl.getGameMetaDisplayText())
    }

    private fun configureNumberPicker(numberPicker: NumberPicker, minValue: Int, maxValue: Int, value: Int) {
        numberPicker.minValue = minValue
        numberPicker.maxValue = maxValue
        numberPicker.value = value.coerceIn(minValue, maxValue)
    }

    private fun configureBooleanPicker(numberPicker: NumberPicker, value: Boolean) {
        numberPicker.displayedValues = null
        numberPicker.minValue = 0
        numberPicker.maxValue = 1
        numberPicker.displayedValues = arrayOf("Nein", "Ja")
        numberPicker.value = if (value) 1 else 0
    }

    private fun createHornActionView(): View {
        return AppCompatImageButton(requireContext()).apply {
            setImageResource(R.drawable.ic_horn)
            setColorFilter(ContextCompat.getColor(requireContext(), R.color.red))
            contentDescription = getString(R.string.horn)
            background = null
            val verticalPaddingPx = dpToPx(8)
            val horizontalPaddingPx = dpToPx(24)
            setPadding(horizontalPaddingPx, verticalPaddingPx, horizontalPaddingPx, verticalPaddingPx)
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        GameControl.startManualHorn()
                        ProcessBT.startConnectedDisplaysHorn()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        GameControl.stopManualHorn()
                        ProcessBT.stopConnectedDisplaysHorn()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun setOptionsMenu() {
        val connectedDevicesCounter =
            (if (ProcessBT.mainBoardConnected) 1 else 0) + (if (ProcessBT.shotClocksConnected[0]) 1 else 0) + (if (ProcessBT.shotClocksConnected[1]) 1 else 0) + (if (ProcessBT.shotClocksConnected[2]) 1 else 0) + (if (ProcessBT.shotClocksConnected[3]) 1 else 0)
        if (LedBoardMenuItem != null) {
            when (connectedDevicesCounter) {
                0 -> {
                    LedBoardMenuItem!!.icon = activity?.let { getActionMenuSpacedIcon(R.drawable.ic_no_shotclock_connected) }
                }
                1 -> {
                    LedBoardMenuItem!!.icon = activity?.let { getActionMenuSpacedIcon(R.drawable.ic_one_device_connected) }
                }
                2 -> {
                    LedBoardMenuItem!!.icon = activity?.let { getActionMenuSpacedIcon(R.drawable.ic_two_devices_connected) }
                }
                3 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        getActionMenuSpacedIcon(R.drawable.ic_three_devices_connected)
                    }
                }
                4 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        getActionMenuSpacedIcon(R.drawable.ic_four_devices_connected)
                    }
                }
                5 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        getActionMenuSpacedIcon(R.drawable.ic_all_devices_connected)
                    }
                }
            }
            if (brightnessAllItem != null) {
                brightnessAllItem!!.isEnabled = connectedDevicesCounter > 0
            }
        }
        if (mainBoardMenuItem != null) {
            mainBoardMenuItem!!.icon = activity?.let {
                ContextCompat.getDrawable(
                    it,
                    if (ProcessBT.mainBoardConnected) R.drawable.ic_mainboard_connected else R.drawable.ic_mainboard_disconnected
                )
            }
            mainBoardConnectItem?.isEnabled = !ProcessBT.mainBoardConnected
            mainBoardBrightnessItem?.isEnabled = ProcessBT.mainBoardConnected
        }
        shotclockMenuItems.forEachIndexed { index, menuItem ->
            Log.d(TAG, "MenuItem-$index: ${menuItem != null}")
            if (menuItem != null) {
                menuItem.icon = activity?.let {
                    ContextCompat.getDrawable(
                        it,
                        if (ProcessBT.shotClocksConnected[index]) R.drawable.ic_shotclock_connected else R.drawable.ic_shotclock_disconnected
                    )
                }
                shotclockConnectItems[index]?.isEnabled = !ProcessBT.shotClocksConnected[index]
                brightnessShotclocks[index]?.isEnabled = ProcessBT.shotClocksConnected[index]
            }
        }

        val pauseTimerRunning = GameControl.isPauseTimerRunning()
        pauseShortenMenuItem?.isEnabled = pauseTimerRunning
        pauseShortenMenuItem?.isVisible = pauseTimerRunning
        updatePauseSectionEditState()
    }

    private fun initObservers() {
        // my livedata
        // Create the observer which updates the UI.
        val mainMinutesObserver = Observer<String> { newValue ->
            binding.mainMinutesView.text = newValue
        }
        val mainSecondsObserver = Observer<String> { newValue ->
            binding.mainSecondsView.text = newValue
        }
        val mainSecondsSmallObserver = Observer<String> { newValue ->
            binding.mainSecondsSmallView.text = newValue
        }
        val shotclockSecondsObserver = Observer<String> { newValue ->
            binding.shotclockSecondsView.text = newValue
        }
        val shotclockSecondsSmallObserver = Observer<String> { newValue ->
            binding.shotclockSecondsSmallView.text = newValue
        }
        val shotclockBigButtonLabelObserver = Observer<String> { newValue ->
            binding.btnTimeShotclockBig.text = newValue
        }
        val shotclockSmallButtonLabelObserver = Observer<String> { newValue ->
            binding.btnTimeShotclockSmall.text = newValue
        }
        val currentGameSectionObserver = Observer<String> { newValue ->
            binding.currentGameSection.text = newValue
            updatePauseSectionEditState()
            updateTimeControlButtonsState()
            if (newValue.isDigitsOnly()) {
                ProcessBT.sendMessageToMainBoard("gameSection%$newValue")
            }
        }
        val connectTextSetTextObserver = Observer<String> { newValue ->
            binding.bluetoothConnectionTextview.text = newValue

        }
        val connectViewsVisibilityObserver = Observer<Boolean> { newValue ->
            binding.bluetoothConnectionTextview.visibility =
                if (newValue) View.VISIBLE else View.GONE
            binding.bluetoothConnectionProgressBar.visibility =
                if (newValue) View.VISIBLE else View.GONE
            if (!newValue) {
                setOptionsMenu()
            }
        }
        val continuationChoiceRequestObserver = Observer<Int> { newValue ->
            if (newValue > 0) {
                showContinuationChoiceAfterDrawDialog()
            }
        }
        val gameEventEditRequestObserver = Observer<GameEventView?> { event ->
            if (event != null) {
                startGameEventCorrectionDialog(event)
                viewModel.clearGameEventEditRequest()
            }
        }

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.mainMinutes.observe(viewLifecycleOwner, mainMinutesObserver)
        viewModel.mainSeconds.observe(viewLifecycleOwner, mainSecondsObserver)
        viewModel.mainSecondsSmall.observe(viewLifecycleOwner, mainSecondsSmallObserver)
        viewModel.shotclockSeconds.observe(viewLifecycleOwner, shotclockSecondsObserver)
        viewModel.shotclockSecondsSmall.observe(viewLifecycleOwner, shotclockSecondsSmallObserver)
        viewModel.shotclockBigButtonLabel.observe(viewLifecycleOwner, shotclockBigButtonLabelObserver)
        viewModel.shotclockSmallButtonLabel.observe(viewLifecycleOwner, shotclockSmallButtonLabelObserver)
        viewModel.currentGameSection.observe(viewLifecycleOwner, currentGameSectionObserver)
        viewModel.continuationChoiceRequest.observe(viewLifecycleOwner, continuationChoiceRequestObserver)
        viewModel.gameEventEditRequest.observe(viewLifecycleOwner, gameEventEditRequestObserver)
        viewModel.connectTextview.observe(viewLifecycleOwner, connectTextSetTextObserver)
        viewModel.theConnectViewsVisibility.observe(
            viewLifecycleOwner, connectViewsVisibilityObserver
        )

        val timeClickableObserver = Observer<Boolean> { newValue ->
            timeControlsEnabledByViewModel = newValue
            updateTimeControlButtonsState()
            setOptionsMenu()
        }
        viewModel.timeClickable.observe(viewLifecycleOwner, timeClickableObserver)

        // set exclusion timer on player buttons
        val exclusionTimeObserver = Observer<String> { newValue ->
            val player = newValue.split(":")[0]
            val value1 = newValue.split(":")[1].split(".")[0].toInt()
            val value2 = newValue.split(":")[1].split(".")[1].toInt()
            val cap = player.split("_")[1]
            val index = player.split("_")[2].toInt()
            val btn =
                if (cap == "W") bindingButtonsWhite[index] else bindingButtonsBlue[index]
            val textColor =
                if (value1 < 1 && value2 < 1) (if (cap == "W") "#FF000000"/*black*/ else "#FFFFFFFF"/*white*/) else "#E91E63"/*red*/
            // btn_W_1:20
            btn?.text =
                if (value1 < 1 && value2 < 1)
                    (if (cap == "W")
                        "W${
                            bindingButtonsWhite[index]?.let {
                                resources.getResourceName(it.id).split("/")
                            }?.get(1)?.split("_")?.get(2)
                        }"
                    else
                        "B${
                            bindingButtonsBlue[index]?.let {
                                resources.getResourceName(it.id).split("/")
                            }?.get(1)?.split("_")?.get(2)
                        }"
                            )
                else ((
                        if (value1 < 1)
                            "$value1.$value2"
                        else
                            value1).toString())
            btn?.setTextColor(Color.parseColor(textColor))

        }
        viewModel.exclusionTime.observe(viewLifecycleOwner, exclusionTimeObserver)
    }

    private fun showPauseTimeEditDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_edit_time)

        val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
        val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)

        val numPickerMinutes = dialog.findViewById<NumberPicker>(R.id.numberpicker_minutes)
        val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds)
        val numPickerSecondsSmall = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds_small)
        numPickerMinutes.maxValue = 20
        numPickerMinutes.minValue = 0
        numPickerMinutes.value = 0
        numPickerSeconds.maxValue = 59
        numPickerSeconds.minValue = 0
        numPickerSeconds.value = 16
        numPickerSecondsSmall.maxValue = 9
        numPickerSecondsSmall.minValue = 0
        numPickerSecondsSmall.value = 0

        dialogButtonOk.setOnClickListener {
            val newPauseCountdown =
                ((numPickerMinutes.value * 60 * 1000) + (numPickerSeconds.value * 1000) + (numPickerSecondsSmall.value * 100)).toLong()
            val pauseWasEdited = GameControl.setPauseTimeEdit(newPauseCountdown)
            if (!pauseWasEdited) {
                Toast.makeText(requireContext(), "Keine aktive Pause", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialogButtonCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updatePauseSectionEditState() {
        val sectionClickable = true
        binding.currentGameSection.isClickable = sectionClickable
        binding.currentGameSection.alpha = if (sectionClickable) 1.0f else 0.85f
    }

    private fun applyTimeControlModeUi() {
        val shotclockStartStopButton = binding.btnTimeStartStopShotclock
        if (DEFAULT_TIME_IS_BRUTTO) {
            binding.btnTimeStartStop.text = getString(R.string.start_stop_main)
            shotclockStartStopButton.visibility = View.VISIBLE
            binding.btnTimeShotclockSmall.text = viewModel.shotclockSmallButtonLabel.value ?: getString(R.string.shotclock_small)
            binding.btnTimeShotclockBig.text = viewModel.shotclockBigButtonLabel.value ?: getString(R.string.shotclock_big)
        } else {
            binding.btnTimeStartStop.text = getString(R.string.start_stop)
            shotclockStartStopButton.visibility = View.GONE
            binding.btnTimeShotclockSmall.text = viewModel.shotclockSmallButtonLabel.value ?: getString(R.string.shotclock_small)
            binding.btnTimeShotclockBig.text = viewModel.shotclockBigButtonLabel.value ?: getString(R.string.shotclock_big)
        }
    }

    private fun showGameSectionSelectionDialog() {
        val maxSections = GameControl.numberOfGameSection.coerceAtLeast(1)
        val currentSection = GameControl.getCurrentGameSection().coerceIn(1, maxSections)
        val options = (1..maxSections).map { it.toString() }
        var selectedIndex = currentSection - 1

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Spielabschnitt waehlen")
            .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("OK") { _, _ ->
                val selectedSection = selectedIndex + 1
                val changed = GameControl.setCurrentGameSectionManually(selectedSection)
                if (!changed) {
                    Toast.makeText(requireContext(), "Abschnitt konnte nicht gesetzt werden", Toast.LENGTH_SHORT).show()
                }
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        }
    }

    private fun getActionMenuSpacedIcon(iconResId: Int): InsetDrawable? {
        val baseDrawable = ContextCompat.getDrawable(requireContext(), iconResId) ?: return null
        return InsetDrawable(baseDrawable, dpToPx(6), 0, dpToPx(6), 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun updateTimeControlButtonsState() {
        val pauseTimerRunning = GameControl.isPauseTimerRunning()
        val mainTimeRunning = GameControl.isMainTimeRunning()
        val shotclockRunning = GameControl.isShotclockRunning()
        val enabled = timeControlsEnabledByViewModel && !isPsoSectionDisplayed() && !pauseTimerRunning
        val timeoutEnabled = timeControlsEnabledByViewModel && !pauseTimerRunning && !isPsoSectionDisplayed()
        val shotclockStartStopButton = binding.btnTimeStartStopShotclock
        binding.btnTimeStartStop.isEnabled = enabled
        binding.btnTimeStartStop.isClickable = enabled
        binding.btnTimeShotclockBig.isEnabled = enabled
        binding.btnTimeShotclockBig.isClickable = enabled
        binding.btnTimeShotclockSmall.isEnabled = enabled
        binding.btnTimeShotclockSmall.isClickable = enabled
        val shotclockStartStopEnabled = enabled && DEFAULT_TIME_IS_BRUTTO && mainTimeRunning
        shotclockStartStopButton.isEnabled = shotclockStartStopEnabled
        shotclockStartStopButton.isClickable = shotclockStartStopEnabled
        binding.btnTimeTimeout.isEnabled = timeoutEnabled
        binding.btnTimeTimeout.isClickable = timeoutEnabled
        val alpha = if (enabled) 1.0f else 0.55f
        val timeoutAlpha = if (timeoutEnabled) 1.0f else 0.55f
        binding.btnTimeStartStop.alpha = alpha
        binding.btnTimeShotclockBig.alpha = alpha
        binding.btnTimeShotclockSmall.alpha = alpha
        shotclockStartStopButton.alpha = if (DEFAULT_TIME_IS_BRUTTO && mainTimeRunning) alpha else if (DEFAULT_TIME_IS_BRUTTO) 0.55f else 0.0f
        binding.btnTimeTimeout.alpha = timeoutAlpha

        val runningColor = ContextCompat.getColor(requireContext(), R.color.green_700)
        val stoppedColor = ContextCompat.getColor(requireContext(), R.color.red)
        binding.btnTimeStartStop.backgroundTintList = ColorStateList.valueOf(if (mainTimeRunning) runningColor else stoppedColor)
        shotclockStartStopButton.backgroundTintList = ColorStateList.valueOf(if (shotclockRunning) runningColor else stoppedColor)
    }

    private fun isPsoSectionDisplayed(): Boolean {
        return binding.currentGameSection.text?.toString()?.trim()?.equals("PSO", ignoreCase = true) == true
    }

    private fun isContinuationSectionDisplayed(): Boolean {
        val sectionLabel = binding.currentGameSection.text?.toString()?.trim() ?: return false
        return sectionLabel.equals("PSO", ignoreCase = true) || sectionLabel.uppercase().startsWith("OT-")
    }

    private fun resetPlayerExclusionUiState() {
        (1..PLAYER_COUNT).forEach { number ->
            bindingButtonsWhite[number]?.apply {
                text = "W$number"
                setTextColor(Color.parseColor("#000000"))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                isClickable = true
            }
            bindingButtonsBlue[number]?.apply {
                text = "B$number"
                setTextColor(Color.parseColor("#FFFFFF"))
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.blue))
                isClickable = true
            }
        }

        val exclusionRegex = "exclusion_[WB]_\\d+".toRegex()
        binding.root.allViews.asIterable().forEach { view ->
            if (view is TextView && view.id > 0) {
                val idName = resources.getResourceName(view.id).substringAfter('/')
                if (exclusionRegex.matches(idName)) {
                    view.text = ""
                }
            }
        }
    }

    private fun showContinuationChoiceAfterDrawDialog() {
        val options = GameControl.getAvailableContinuationModes().toMutableList()
        if (options.isEmpty()) {
            return
        }

        var selected = options.first()
        val continuationLabel = binding.currentGameSection.text?.toString()?.trim().orEmpty()
        val continuationSectionDisplayed = isContinuationSectionDisplayed()
        var dialog: AlertDialog? = null
        val gameCompleted = isGameCompletedForContinuationChoice()

        val dialogTitle = if (gameCompleted) {
            getString(R.string.end_game_draw_title)
        } else {
            getString(
                R.string.end_game_draw_title_with_warning,
                GameControl.getCurrentGameSection(),
                GameControl.numberOfGameSection
            )
        }

        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle(dialogTitle)
            .setSingleChoiceItems(options.toTypedArray(), 0) { _, which ->
                selected = options[which]
                dialog?.let { shownDialog ->
                    updateDrawModeDialogActionLabels(shownDialog, selected, continuationSectionDisplayed)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton(getString(R.string.draw_mode_continue_with_selected_text, selected)) { _, _ ->
                viewModel.applyContinuationChoice(selected)
            }

        if (continuationSectionDisplayed) {
            dialogBuilder.setNeutralButton("$continuationLabel loeschen") { _, _ ->
                lifecycleScope.launch {
                    val deleteResult = viewModel.deleteCurrentContinuationSectionIfEmpty()
                    if (!deleteResult.removedLabel.isNullOrBlank()) {
                        Toast.makeText(requireContext(), "${deleteResult.removedLabel} entfernt", Toast.LENGTH_SHORT).show()
                        val showDrawModeDialogAgain = GameControl.getCurrentGameSection() >= GameControl.getMaxGameSection() &&
                            GameControl.isDrawByDisplayedResult() &&
                            GameControl.getAvailableContinuationModes().isNotEmpty()
                        if (showDrawModeDialogAgain) {
                            view?.post {
                                showContinuationChoiceAfterDrawDialog()
                            }
                        }
                    } else if (!deleteResult.blockedLabel.isNullOrBlank() && deleteResult.blockedLogLineCount > 0) {
                        showContinuationDeleteBlockedDialog(deleteResult.blockedLabel, deleteResult.blockedLogLineCount)
                    }
                }
            }
        } else {
            dialogBuilder.setNeutralButton(getString(R.string.draw_mode_continue_without_selected_text, selected)) { _, _ ->
                viewModel.applyContinuationChoice("")
            }
        }

        dialog = dialogBuilder.show()
        updateDrawModeDialogActionLabels(dialog, selected, continuationSectionDisplayed)
        styleDrawModeDialogButtons(dialog)
    }

    private fun isGameCompletedForContinuationChoice(): Boolean {
        val currentSection = GameControl.getCurrentGameSection()
        val configuredSections = GameControl.numberOfGameSection

        if (currentSection > configuredSections) {
            return true
        }
        if (currentSection < configuredSections) {
            return false
        }

        return isDisplayedMainTimeZero() || GameControl.currentCountdown <= 0L
    }

    private fun isDisplayedMainTimeZero(): Boolean {
        val minutes = binding.mainMinutesView.text?.toString()?.trim()?.toIntOrNull() ?: return false
        val seconds = binding.mainSecondsView.text?.toString()?.trim()?.toIntOrNull() ?: return false
        val tenths = binding.mainSecondsSmallView.text?.toString()?.trim()?.toIntOrNull() ?: return false
        return minutes == 0 && seconds == 0 && tenths == 0
    }

    private fun updateDrawModeDialogActionLabels(
        dialog: AlertDialog,
        selectedMode: String,
        continuationSectionDisplayed: Boolean
    ) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.text =
            getString(R.string.draw_mode_continue_with_selected_text, selectedMode)

        if (!continuationSectionDisplayed) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.text =
                getString(R.string.draw_mode_continue_without_selected_text, selectedMode)
        }
    }

    private fun styleDrawModeDialogButtons(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green_700))
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            setTextColor(Color.BLACK)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.yellow_500))
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
        }
    }

    private fun showContinuationDeleteBlockedDialog(label: String, logLineCount: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.warning_title))
            .setMessage(getString(R.string.continuation_delete_blocked_message, label, logLineCount))
            .setPositiveButton(R.string.close_text) { _, _ ->
                requireActivity().findViewById<ViewPager2>(R.id.view_pager).currentItem = GAME_PAGE_INDEX
            }
            .show()
    }

    private fun showTimeoutLimitWarningDialog(color: String, teamName: String) {
        GameControl.playWarningSignal()
        val normalizedTeamName = if (teamName.isBlank()) "-" else teamName
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.warning_title))
            .setMessage(getString(R.string.timeout_limit_reached_message, color, normalizedTeamName))
            .setPositiveButton(R.string.close_text, null)
            .show()
    }

    private fun subscribeUi(adapter: GameEventAdapter, binding: FragmentGameBinding) {
        Log.d(TAG, "GameFragment.subscribeUi enter")
        viewModel.gameEvents.observe(viewLifecycleOwner) { result ->
            adapter.submitList(result)
        }
        viewModel.goals.observe(viewLifecycleOwner) { result ->
            binding.goalsWhite = result.white
            binding.goalsBlue = result.blue
            GameControl.setDisplayedResult(result.white, result.blue)
            val mainBoardString = "result%${result.white}:${result.blue}"
            ProcessBT.sendMessageToMainBoard(mainBoardString)
        }

        viewModel.timeoutForWhite.observe(viewLifecycleOwner) { result ->
            binding.timeoutWhiteValue = result.count
        }
        viewModel.timeoutForBlue.observe(viewLifecycleOwner) { result ->
            binding.timeoutBlueValue = result.count
        }

        val tempListTxtViewBlue = mutableMapOf<Int, TextView>()
        val tempListTxtViewWhite = mutableMapOf<Int, TextView>()
        val regexBlue = "exclusion_B_\\d+".toRegex()
        val regexWhite = "exclusion_W_\\d+".toRegex()
        binding.root.allViews.asIterable().forEach {
            if (it.id > 0 &&
                regexBlue.matches(resources.getResourceName(it.id).split("/")[1]) &&
                it is TextView
            ) {
                tempListTxtViewBlue[resources.getResourceName(it.id).split("_")[2].toInt()] = it
            } else if (it.id > 0 &&
                regexWhite.matches(resources.getResourceName(it.id).split("/")[1]) &&
                it is TextView
            ) {
                tempListTxtViewWhite[resources.getResourceName(it.id).split("_")[2].toInt()] = it
            }
        }
        val bindingExclusionsBlue: Map<Int, TextView> = tempListTxtViewBlue
        val bindingExclusionsWhite: Map<Int, TextView> = tempListTxtViewWhite

        viewModel.exclusionsBlue.forEachIndexed { index, liveData ->
            liveData.observe(viewLifecycleOwner) { result ->
                // check for change in exclusion list and send info to mainBoard
                if (result != null) {
                    sendExclusionToMainBoard(exclusionResultBlue, result, index, BLUE)
                }
                bindingExclusionsBlue[index + 1]?.text =
                    if (result != null) "${result.e1} ${result.e2} ${result.e3}" else ""
                val isBlocked = result != null && !(result.e1.isEmpty() || (!myArray.contains(result.e1) && !myArray.contains(result.e2) && result.e3.isEmpty()))
                exclusionBlockedBlue[index + 1] = isBlocked
                applyPlayerButtonAvailability(BLUE, index + 1)
            }
        }

        viewModel.exclusionsWhite.forEachIndexed { index, liveData ->
            liveData.observe(viewLifecycleOwner) { result ->
                // check for change in exclusion list and send info to mainBoard
                if (result != null) {
                    sendExclusionToMainBoard(exclusionResultWhite, result, index, WHITE)
                }
                bindingExclusionsWhite[index + 1]?.text =
                    if (result != null) "${result.e1} ${result.e2} ${result.e3}" else ""
                val isBlocked = result != null && !(!myArray.contains(result.e1) && !myArray.contains(result.e2) && result.e3.isEmpty())
                exclusionBlockedWhite[index + 1] = isBlocked
                applyPlayerButtonAvailability(WHITE, index + 1)
            }
        }
    }

    private fun sendExclusionToMainBoard(
        exclusionResult: MutableList<ExclResult>,
        result: ExclResult,
        index: Int,
        cap: String
    ) {
        if (
            exclusionResult[index].e1 != result.e1 ||
            exclusionResult[index].e2 != result.e2 ||
            exclusionResult[index].e3 != result.e3
        ) {
            exclusionResult[index] = result
            val numberOfExclusions = if (
                myArray.contains(result.e1) ||
                myArray.contains(result.e2)
            ) {
                3
            } else {
                result.e1.length + result.e2.length + result.e3.length
            }
            GameControl.setDisplayedExclusionCount(cap, index + 1, numberOfExclusions)
            ProcessBT.sendMessageToMainBoard("player%$cap%${index + 1}%$numberOfExclusions")
        }
    }

}
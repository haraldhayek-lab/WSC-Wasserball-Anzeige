package com.example.waterpolo3000

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.*
import android.widget.Button
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.example.waterpolo3000.adapters.GameEventAdapter
import com.example.waterpolo3000.data.AppDatabase
import com.example.waterpolo3000.data.ExclResult
import com.example.waterpolo3000.databinding.FragmentGameBinding
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.*
import com.example.waterpolo3000.viewmodels.GameViewModel
import com.google.android.material.slider.Slider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GameFragment : Fragment() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        viewModel.btSearchExecuted = true
    }

    private fun processBtnClickEvent(it: View) {
        myVibrate()
        val btnId = resources.getResourceName(it.id).split("/")[1]
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
                viewModel.processTime(btnId.split("_")[2])
                if (btnId.split("_")[2] == "timeout") {
                    val dialog = Dialog(requireContext())
                    dialog.setContentView(R.layout.dialog_timeout)

                    val dialogButtonWhite = dialog.findViewById<Button>(R.id.btn_white)
                    val dialogButtonCancel = dialog.findViewById<Button>(R.id.btn_cancel)
                    val dialogButtonBlue = dialog.findViewById<Button>(R.id.btn_blue)

                    dialogButtonWhite.setOnClickListener {
                        Toast.makeText(requireContext(), "timeout white", Toast.LENGTH_SHORT)
                            .show()
                        viewModel.storeTimeout(WHITE, tempCurrentCountdown)
                        tempCurrentCountdown = 0
                        dialog.dismiss()
                    }
                    dialogButtonBlue.setOnClickListener {
                        Toast.makeText(requireContext(), "timeout blue", Toast.LENGTH_SHORT)
                            .show()
                        viewModel.storeTimeout(BLUE, tempCurrentCountdown)
                        tempCurrentCountdown = 0
                        dialog.dismiss()
                    }
                    dialogButtonCancel.setOnClickListener {
                        dialog.dismiss()
                    }
                    dialog.show()
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

    private fun setUi() {
        viewModel.setAll()
    }

    private fun myVibrate() {
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_game, menu)
        super.onCreateOptionsMenu(menu, inflater)
        LedBoardMenuItem = menu.findItem(R.id.led_boards_menu)
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
        hornMenuItem?.actionView = createHornActionView()
        setOptionsMenu()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return (when (item.itemId) {
            R.id.item_new_game -> {
                showNewGameConfirmDialog()
                true
            }
            R.id.item_live -> {
                if (viewModel.liveGame) {
                    viewModel.liveGame = false
                    liveMenuItem?.icon = activity?.let {
                        ContextCompat.getDrawable(
                            it,
                            R.drawable.ic_live_inactive
                        )
                    }

                    return true
                }
                if (context?.let { checkNetworkConnection.isOnline(it) } == true) {
                    viewModel.liveGame = true
                    viewModel.createFirstFirebaseEntry()
                    liveMenuItem?.icon = activity?.let {ContextCompat.getDrawable(it, R.drawable.ic_live_active)}
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
                numPickerMinutes.maxValue = 20
                numPickerMinutes.minValue = 0
                numPickerMinutes.value = 0
                numPickerSeconds.maxValue = 59
                numPickerSeconds.minValue = 0
                numPickerSeconds.value = 0
                numPickerSecondsSmall.maxValue = 9
                numPickerSecondsSmall.minValue = 0
                numPickerSecondsSmall.value = 0

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
                val dialog = Dialog(requireContext())
                dialog.setContentView(R.layout.dialog_edit_time)

                val dialogButtonOk = dialog.findViewById<Button>(R.id.dialogBtnOK)
                val dialogButtonCancel = dialog.findViewById<Button>(R.id.dialogBtnCancel)

                val numPickerMinutes = dialog.findViewById<NumberPicker>(R.id.numberpicker_minutes)
                val numPickerSeconds = dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds)
                val numPickerSecondsSmall =
                    dialog.findViewById<NumberPicker>(R.id.numberpicker_seconds_small)
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
                numPickerSeconds.maxValue = 30
                numPickerSeconds.minValue = 0
                numPickerSeconds.value = 0
                numPickerSecondsSmall.maxValue = 9
                numPickerSecondsSmall.minValue = 0
                numPickerSecondsSmall.value = 0

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
            else ->
                super.onOptionsItemSelected(item)
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
        val numPickerTimeoutLength = dialog.findViewById<NumberPicker>(R.id.numberpicker_timeout_length)
        val numPickerTimeoutAmount = dialog.findViewById<NumberPicker>(R.id.numberpicker_timeout_amount)
        val numPickerShotclockLong = dialog.findViewById<NumberPicker>(R.id.numberpicker_shotclock_long)
        val numPickerShotclockShort = dialog.findViewById<NumberPicker>(R.id.numberpicker_shotclock_short)
        val numPickerPeriods = dialog.findViewById<NumberPicker>(R.id.numberpicker_periods)
        val numPickerOt = dialog.findViewById<NumberPicker>(R.id.numberpicker_ot)
        val numPickerPso = dialog.findViewById<NumberPicker>(R.id.numberpicker_pso)

        configureNumberPicker(numPickerMinutes, 0, 20, GameControl.gameSectionLength / 60)
        configureNumberPicker(numPickerSeconds, 0, 59, GameControl.gameSectionLength % 60)
        configureNumberPicker(numPickerBreakBig, 0, 20, DEFAULT_PAUSE_LONG_LENGTH / 60)
        configureNumberPicker(numPickerBreakSmall, 0, 20, DEFAULT_PAUSE_SHORT_LENGTH / 60)
        configureNumberPicker(numPickerTimeoutLength, 0, 5, DEFAULT_TIMEOUT_LENGTH / 60)
        configureNumberPicker(numPickerTimeoutAmount, 0, 10, DEFAULT_MAX_TIMEOUT)
        configureNumberPicker(numPickerShotclockLong, 0, 60, DEFAULT_SHOTCLOCK_BIG_LENGTH)
        configureNumberPicker(numPickerShotclockShort, 0, 60, DEFAULT_SHOTCLOCK_SMALL_LENGTH)
        configureNumberPicker(numPickerPeriods, 1, 4, GameControl.numberOfGameSection)
        configureBooleanPicker(numPickerOt, GameControl.isOvertimeEnabled())
        configureBooleanPicker(numPickerPso, GameControl.isPsoEnabled())

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
                    timeoutLength = numPickerTimeoutLength.value * 60,
                    maxTimeout = numPickerTimeoutAmount.value
                )
            )

            dialog.dismiss()
        }

        dialogButtonCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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
        dialog.setContentView(R.layout.dialog_confirm_new_game)

        val dialogButtonYes = dialog.findViewById<Button>(R.id.dialogBtnYes)
        val dialogButtonNo = dialog.findViewById<Button>(R.id.dialogBtnNo)

        dialogButtonYes.setOnClickListener {
            viewModel.newGame()
            dialog.dismiss()
        }

        dialogButtonNo.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.3f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
            contentDescription = getString(R.string.horn)
            background = null
            setPadding(16, 16, 16, 16)
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
                    LedBoardMenuItem!!.icon = activity?.let {ContextCompat.getDrawable(it, R.drawable.ic_no_shotclock_connected)}
                }
                1 -> {
                    LedBoardMenuItem!!.icon = activity?.let {ContextCompat.getDrawable(it, R.drawable.ic_one_device_connected)}
                }
                2 -> {
                    LedBoardMenuItem!!.icon = activity?.let {ContextCompat.getDrawable(it, R.drawable.ic_two_devices_connected)}
                }
                3 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        ContextCompat.getDrawable(
                            it,
                            R.drawable.ic_three_devices_connected
                        )
                    }
                }
                4 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        ContextCompat.getDrawable(
                            it,
                            R.drawable.ic_four_devices_connected
                        )
                    }
                }
                5 -> {
                    LedBoardMenuItem!!.icon = activity?.let {
                        ContextCompat.getDrawable(
                            it,
                            R.drawable.ic_all_devices_connected
                        )
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

        // Observe the LiveData, passing in this activity as the LifecycleOwner and the observer.
        viewModel.mainMinutes.observe(viewLifecycleOwner, mainMinutesObserver)
        viewModel.mainSeconds.observe(viewLifecycleOwner, mainSecondsObserver)
        viewModel.mainSecondsSmall.observe(viewLifecycleOwner, mainSecondsSmallObserver)
        viewModel.shotclockSeconds.observe(viewLifecycleOwner, shotclockSecondsObserver)
        viewModel.shotclockSecondsSmall.observe(viewLifecycleOwner, shotclockSecondsSmallObserver)
        viewModel.shotclockBigButtonLabel.observe(viewLifecycleOwner, shotclockBigButtonLabelObserver)
        viewModel.shotclockSmallButtonLabel.observe(viewLifecycleOwner, shotclockSmallButtonLabelObserver)
        viewModel.currentGameSection.observe(viewLifecycleOwner, currentGameSectionObserver)
        viewModel.connectTextview.observe(viewLifecycleOwner, connectTextSetTextObserver)
        viewModel.theConnectViewsVisibility.observe(
            viewLifecycleOwner, connectViewsVisibilityObserver
        )

        val timeClickableObserver = Observer<Boolean> { newValue ->
            binding.btnTimeStartStop.isClickable = newValue
            binding.btnTimeShotclockBig.isClickable = newValue
            binding.btnTimeShotclockSmall.isClickable = newValue
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
                if (result.e1.isEmpty() || (!myArray.contains(result.e1) && !myArray.contains(result.e2) && result.e3.isEmpty())) {
                    bindingButtonsBlue[index + 1]?.isClickable = true
                    bindingButtonsBlue[index + 1]?.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.blue
                        )
                    )
                } else {
                    bindingButtonsBlue[index + 1]?.isClickable = false
                    bindingButtonsBlue[index + 1]?.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.gray_50_a600
                        )
                    )
                }
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
                if (!myArray.contains(result.e1) && !myArray.contains(result.e2) && result.e3.isEmpty()) {
                    bindingButtonsWhite[index + 1]?.isClickable = true
                    bindingButtonsWhite[index + 1]?.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.white
                        )
                    )
                } else {
                    bindingButtonsWhite[index + 1]?.isClickable = false
                    bindingButtonsWhite[index + 1]?.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.gray_50_a600
                        )
                    )
                }
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
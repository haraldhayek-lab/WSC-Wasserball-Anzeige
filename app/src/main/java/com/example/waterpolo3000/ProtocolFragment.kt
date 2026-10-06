package com.example.waterpolo3000

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import com.example.waterpolo3000.adapters.ProtocolGoalTypeAdapter
import com.example.waterpolo3000.adapters.ProtocolPersonalFoulAdapter
import com.example.waterpolo3000.adapters.ProtocolTeamAdapter
import com.example.waterpolo3000.adapters.ProtocolTeamEditAdapter
import com.example.waterpolo3000.data.AppDatabase
import com.example.waterpolo3000.databinding.FragmentProtocolBinding
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.viewmodels.ContinuationSectionResult
import com.example.waterpolo3000.viewmodels.ProtocolViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProtocolFragment : Fragment() {

    private lateinit var binding: FragmentProtocolBinding
    private val viewModel: ProtocolViewModel by activityViewModels()
    private var pendingProtocolImage: Bitmap? = null
    private var lastContinuationRows: List<ContinuationSectionResult> = emptyList()
    private var continuationRenderVersion: Int = 0

    private val saveProtocolLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri: Uri? ->
        if (uri == null) {
            pendingProtocolImage = null
            return@registerForActivityResult
        }

        val bitmap = pendingProtocolImage
        if (bitmap == null) {
            Toast.makeText(requireContext(), getString(R.string.save_protocol_failed), Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }

        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            Toast.makeText(requireContext(), getString(R.string.save_protocol_success), Toast.LENGTH_LONG).show()
            openSavedFile(uri)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.save_protocol_failed), Toast.LENGTH_LONG).show()
        } finally {
            pendingProtocolImage = null
        }
    }

    lateinit var database: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProtocolBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.db = AppDatabase.getInstance(requireContext())
        applyTeamNames()
        applyProtocolModeLabels()
        applySectionResultRows()
        viewModel.refreshProtocolRowsForCurrentSettings()

        val adapterBlue = ProtocolTeamAdapter()
        val adapterWhite = ProtocolTeamAdapter()
        val adapterPersonalFoul = ProtocolPersonalFoulAdapter()
        val adapterGoalType = ProtocolGoalTypeAdapter()
        val adapterEditTeamWhite = ProtocolTeamEditAdapter()
        adapterEditTeamWhite.viewModelOut = viewModel
        val adapterEditTeamBlue = ProtocolTeamEditAdapter()
        adapterEditTeamBlue.viewModelOut = viewModel
        binding.teamBlueRecyclerview.adapter = adapterBlue
        binding.teamWhiteRecyclerview.adapter = adapterWhite
        binding.personalFoulRecyclerview.adapter = adapterPersonalFoul
        binding.goalsRecyclerview.adapter = adapterGoalType
        binding.editWhiteRecyclerview.adapter = adapterEditTeamWhite
        binding.editBlueRecyclerview.adapter = adapterEditTeamBlue

        subscribeUiTeams(adapterBlue, adapterWhite)
        subscribeUiPersonalFouls(adapterPersonalFoul)
        subscribeUiGoals(adapterGoalType, binding)
        subscribeUiEditTeams(adapterEditTeamWhite, adapterEditTeamBlue, binding)

        binding.setClickListener {
            val btnId = resources.getResourceName(it.id).split("/")[1]
            binding.edit = listOf("btn_save", "btn_cancel").contains(btnId)
            when(btnId){
                "btn_save" -> viewModel.storePlayerUpdated()
                "btn_cancel" -> {
                    viewModel.clearPlayerUpdate()
                    // needed to not to the have the changed names after a cancel
                    adapterEditTeamWhite.notifyDataSetChanged()
                    adapterEditTeamBlue.notifyDataSetChanged()
                }
            }
        }

        applySpecialNotes()
        updateProtocolSaveInHeader()
    }

    override fun onResume() {
        super.onResume()
        applyTeamNames()
        applySpecialNotes()
        (activity as? WaterpoloActivity)?.updateGameMetaHeader(GameControl.getGameMetaDisplayText())
        updateProtocolSaveInHeader()
        applyProtocolModeLabels()
        applySectionResultRows()
        viewModel.refreshProtocolRowsForCurrentSettings()
    }

    override fun onPause() {
        super.onPause()
        (activity as? WaterpoloActivity)?.setHeaderSaveAction(false, null)
    }

    private fun subscribeUiTeams(adapterBlue: ProtocolTeamAdapter, adapterWhite: ProtocolTeamAdapter) {
        viewModel.protocolForTeamBlue.observe(viewLifecycleOwner) { result ->
            adapterBlue.submitList(result)
        }
        viewModel.protocolForTeamWhite.observe(viewLifecycleOwner) { result ->
            adapterWhite.submitList(result)
        }
    }

    private fun subscribeUiPersonalFouls(adapter: ProtocolPersonalFoulAdapter) {
        viewModel.protocolForPersonalFoul.observe(viewLifecycleOwner) { result ->
            adapter.submitList(result)
        }
    }

    private fun subscribeUiGoals(adapter: ProtocolGoalTypeAdapter, binding: FragmentProtocolBinding) {
        viewModel.protocolGoalType.observe(viewLifecycleOwner) { result ->
            adapter.submitList(result)
        }
        viewModel.totalGoalsWhite.observe(viewLifecycleOwner) { result ->
            binding.goalsWhite = result
        }
        viewModel.totalGoalsBlue.observe(viewLifecycleOwner) { result ->
            binding.goalsBlue = result
        }
        viewModel.goalsFirstQuarter.observe(viewLifecycleOwner) { result ->
            binding.goalsWhiteFirst = result.white
            binding.goalsBlueFirst = result.blue
        }
        viewModel.goalsSecondQuarter.observe(viewLifecycleOwner) { result ->
            binding.goalsWhiteSecond = result.white
            binding.goalsBlueSecond = result.blue
        }
        viewModel.goalsThirdQuarter.observe(viewLifecycleOwner) { result ->
            binding.goalsWhiteThird = result.white
            binding.goalsBlueThird = result.blue
        }
        viewModel.goalsFourthQuarter.observe(viewLifecycleOwner) { result ->
            binding.goalsWhiteFourth = result.white
            binding.goalsBlueFourth = result.blue
        }
        viewModel.continuationSectionLabel.observe(viewLifecycleOwner) { result ->
            binding.continuationLabel = result
        }
        viewModel.continuationGoalsWhite.observe(viewLifecycleOwner) { result ->
            binding.continuationGoalsWhite = result
        }
        viewModel.continuationGoalsBlue.observe(viewLifecycleOwner) { result ->
            binding.continuationGoalsBlue = result
        }
        viewModel.continuationSectionLabel2.observe(viewLifecycleOwner) { result ->
            binding.continuationLabel2 = result
        }
        viewModel.continuationGoalsWhite2.observe(viewLifecycleOwner) { result ->
            binding.continuationGoalsWhite2 = result
        }
        viewModel.continuationGoalsBlue2.observe(viewLifecycleOwner) { result ->
            binding.continuationGoalsBlue2 = result
        }
        viewModel.continuationSectionResults.observe(viewLifecycleOwner) { rows ->
            val shouldKeepFallback = hasContinuationContext()
            val rowsToRender = when {
                rows.isNotEmpty() -> {
                    lastContinuationRows = rows
                    rows
                }
                shouldKeepFallback && lastContinuationRows.isNotEmpty() -> lastContinuationRows
                else -> {
                    lastContinuationRows = emptyList()
                    emptyList()
                }
            }
            renderContinuationSectionRows(rowsToRender)
        }
    }

    private fun hasContinuationContext(): Boolean {
        return GameControl.getContinuationSectionNumbers().isNotEmpty() ||
            GameControl.getContinuationModeLabel().isNotBlank() ||
            GameControl.gameEndMode.isNotBlank()
    }

    private fun subscribeUiEditTeams(adapterWhite: ProtocolTeamEditAdapter, adapterBlue: ProtocolTeamEditAdapter, binding: FragmentProtocolBinding) {
        binding.edit = true
        viewModel.editTeamWhite.observe(viewLifecycleOwner) { result ->
            adapterWhite.submitList(result)
        }
        viewModel.editTeamBlue.observe(viewLifecycleOwner) { result ->
            adapterBlue.submitList(result)
        }
    }

    private fun applyTeamNames() {
        binding.teamWhiteName.text = GameControl.teamWhite.teamName
        binding.teamBlueName.text = GameControl.teamBlue.teamName
    }

    private fun updateProtocolSaveInHeader() {
        val host = activity as? WaterpoloActivity
        if (GameControl.isProtocolSaveEnabled()) {
            host?.setHeaderSaveAction(true) { startSaveProtocolFlow() }
        } else {
            host?.setHeaderSaveAction(false, null)
        }
    }

    private fun applySpecialNotes() {
        binding.specialNotesValue.text = if (GameControl.gameSpecialNotes.isBlank()) {
            getString(R.string.special_notes_empty)
        } else {
            GameControl.gameSpecialNotes
        }
    }

    private fun applyProtocolModeLabels() {
        binding.personalFoulTitle.text = getString(R.string.personal_foul)
        binding.goalsOrderTitle.text = getString(R.string.goals_order)
    }

    private fun applySectionResultRows() {
        val sectionCount = GameControl.numberOfGameSection
        applySectionRow(1, sectionCount >= 1, "I")
        applySectionRow(2, sectionCount >= 2, "II")
        applySectionRow(3, sectionCount >= 3, "III")
        applySectionRow(4, sectionCount >= 4, "IV")
    }

    private fun applySectionRow(index: Int, visible: Boolean, label: String) {
        val sectionTitleId = when (index) {
            1 -> R.id.first_quarter_result_title
            2 -> R.id.second_quarter_result_title
            3 -> R.id.third_quarter_result_title
            else -> R.id.fourth_quarter_result_title
        }
        val whiteValueId = when (index) {
            1 -> R.id.first_quarter_result_white
            2 -> R.id.second_quarter_result_white
            3 -> R.id.third_quarter_result_white
            else -> R.id.fourth_quarter_result_white
        }
        val blueValueId = when (index) {
            1 -> R.id.first_quarter_result_blue
            2 -> R.id.second_quarter_result_blue
            3 -> R.id.third_quarter_result_blue
            else -> R.id.fourth_quarter_result_blue
        }
        val delimiterId = when (index) {
            1 -> R.id.delimiter_1
            2 -> R.id.delimiter_2
            3 -> R.id.delimiter_3
            else -> R.id.delimiter_4
        }

        val titleView = binding.root.findViewById<TextView>(sectionTitleId)
        val whiteView = binding.root.findViewById<TextView>(whiteValueId)
        val blueView = binding.root.findViewById<TextView>(blueValueId)
        val delimiterView = binding.root.findViewById<TextView>(delimiterId)

        titleView.text = label
        val visibility = if (visible) View.VISIBLE else View.GONE
        titleView.visibility = visibility
        whiteView.visibility = visibility
        blueView.visibility = visibility
        delimiterView.visibility = visibility
    }

    private fun renderContinuationSectionRows(rows: List<ContinuationSectionResult>) {
        continuationRenderVersion += 1
        val requestedVersion = continuationRenderVersion
        val container = binding.continuationResultsContainer
        container.removeAllViews()
        if (rows.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        binding.root.post {
            if (!isAdded || requestedVersion != continuationRenderVersion) {
                return@post
            }
            container.removeAllViews()

            val textSizePx = resources.getDimension(R.dimen.resultTextSize)
            val titleWidth = maxOf(binding.firstQuarterResultTitle.width, binding.firstQuarterResultTitle.measuredWidth, 1)
            val whiteWidth = maxOf(binding.firstQuarterResultWhite.width, binding.firstQuarterResultWhite.measuredWidth, 1)
            val delimiterWidth = maxOf(binding.delimiter1.width, binding.delimiter1.measuredWidth, 1)
            val blueWidth = maxOf(binding.firstQuarterResultBlue.width, binding.firstQuarterResultBlue.measuredWidth, 1)

            rows.forEach { row ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val labelView = TextView(requireContext()).apply {
                    text = row.label
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    width = titleWidth
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }

                val whiteView = TextView(requireContext()).apply {
                    text = row.whiteGoals
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    width = whiteWidth
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.white))
                }

                val delimiterView = TextView(requireContext()).apply {
                    text = ":"
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    width = delimiterWidth
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                }

                val blueView = TextView(requireContext()).apply {
                    text = row.blueGoals
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
                    width = blueWidth
                    setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.blue))
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }

                rowLayout.addView(labelView)
                rowLayout.addView(whiteView)
                rowLayout.addView(delimiterView)
                rowLayout.addView(blueView)
                container.addView(rowLayout)
            }
        }
    }

    private fun startSaveProtocolFlow() {
        pendingProtocolImage = captureFullDisplayBitmap()
        saveProtocolLauncher.launch(buildProtocolFilename())
    }

    private fun captureFullDisplayBitmap(): Bitmap {
        val fullView = activity?.window?.decorView?.rootView
        return if (fullView != null) {
            createBitmapFromView(fullView)
        } else {
            createBitmapFromView(binding.root)
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        val width = if (view.width > 0) view.width else resources.displayMetrics.widthPixels
        val height = if (view.height > 0) view.height else resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val background = view.background ?: activity?.window?.decorView?.background
        if (background != null) {
            background.setBounds(0, 0, width, height)
            background.draw(canvas)
        } else {
            val typedValue = TypedValue()
            val hasThemeBackground = requireContext().theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            canvas.drawColor(if (hasThemeBackground) typedValue.data else Color.WHITE)
        }

        view.draw(canvas)
        return bitmap
    }

    private fun buildProtocolFilename(): String {
        val competition = sanitizeFilenamePart(GameControl.competitionName.ifBlank { "bewerb" })
        val gameNumber = sanitizeFilenamePart(GameControl.gameNumberLabel.ifBlank { "spiel" })
        val teamWhite = sanitizeFilenamePart(GameControl.teamWhite.teamName.ifBlank { "white" })
        val teamBlue = sanitizeFilenamePart(GameControl.teamBlue.teamName.ifBlank { "blue" })
        val timestamp = java.text.SimpleDateFormat("yy-MM-dd-HH-mm", java.util.Locale.GERMANY)
            .format(java.util.Date())
        return "${competition}_${gameNumber}_${teamWhite}-${teamBlue}_${timestamp}.png"
    }

    private fun sanitizeFilenamePart(input: String): String {
        return input.trim()
            .replace("\\s+".toRegex(), "-")
            .replace("[^a-zA-Z0-9\\-_]".toRegex(), "-")
            .ifBlank { "na" }
    }

    private fun openSavedFile(uri: Uri) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (viewIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(viewIntent)
        }
    }

    // TODO: convert to data binding if applicable
//    private fun processPlayer() {
//        requireActivity().findViewById<ViewPager2>(R.id.view_pager).currentItem =
//            PLANT_LIST_PAGE_INDEX

//    }
}

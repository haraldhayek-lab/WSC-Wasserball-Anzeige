package com.example.waterpolo3000

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil.setContentView
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.databinding.ActivityGardenBinding
import com.example.waterpolo3000.utilities.CheckForInternet
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WaterpoloActivity : AppCompatActivity() {

    private var actionbarGameMetaTextView: TextView? = null
    private var actionbarSaveContainer: View? = null
    private var actionbarSaveLabel: TextView? = null
    private var actionbarSaveButton: ImageButton? = null
    private var onHeaderSaveClick: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView<ActivityGardenBinding>(this, R.layout.activity_garden)
        setupActionBarHeader()
        updateGameMetaHeader(GameControl.getGameMetaDisplayText())
    }

    fun updateGameMetaHeader(value: String) {
        actionbarGameMetaTextView?.text = value
    }

    fun setHeaderSaveAction(visible: Boolean, onClick: (() -> Unit)? = null) {
        onHeaderSaveClick = onClick
        actionbarSaveContainer?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun setupActionBarHeader() {
        supportActionBar?.let { actionBar ->
            actionBar.setDisplayShowTitleEnabled(false)
            actionBar.setDisplayShowCustomEnabled(true)
            val customView = LayoutInflater.from(this).inflate(R.layout.actionbar_title_meta, null)
            actionbarGameMetaTextView = customView.findViewById(R.id.actionbar_game_meta)
            actionbarSaveContainer = customView.findViewById(R.id.actionbar_save_container)
            actionbarSaveLabel = customView.findViewById(R.id.actionbar_save_label)
            actionbarSaveButton = customView.findViewById(R.id.actionbar_save_button)

            val headerClickListener = View.OnClickListener {
                onHeaderSaveClick?.invoke()
            }
            actionbarSaveLabel?.setOnClickListener(headerClickListener)
            actionbarSaveButton?.setOnClickListener(headerClickListener)

            actionBar.setCustomView(
                customView,
                ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT,
                    ActionBar.LayoutParams.WRAP_CONTENT
                )
            )
            setHeaderSaveAction(false, null)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
//        Toast.makeText(this, "hey: $keyCode, $event", Toast.LENGTH_SHORT).show()
        return when (keyCode) {
            KeyEvent.KEYCODE_PAGE_UP -> {
                GameControl.startStopCounter()
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                GameControl.newShotclockBig()
                true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                GameControl.newShotclockSmall()
                true
            }
            KeyEvent.KEYCODE_B -> {
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

}
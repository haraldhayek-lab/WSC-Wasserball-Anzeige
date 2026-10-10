package com.example.waterpolo3000.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.fragment.app.FragmentActivity
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.viewmodels.GameViewModel
import java.io.IOException
import java.util.*

//class ProcessBT(val myActivity: FragmentActivity?) {
class ProcessBT(private val appContext: Context? = null) {
    companion object {
        lateinit var gameViewModel: GameViewModel
        var btSocketMainBoard: BluetoothSocket? = null
        val btSocketsShotClocks = mutableListOf<BluetoothSocket?>(
            null,
            null,
            null,
            null
        )

        var mainBoardConnected = false
//        val shotClocksConnected = mutableListOf(
//            false,
//            false,
//            false,
//            false
//        )
        val shotClocksConnected = Array(4) {false}.toMutableList()
        private const val TEAM_SYNC_RETRY_ROUNDS = 2
        private const val TEAM_SYNC_DELAY_MS = 120L

        const val REQUEST_ENABLE_BT = 1

        fun sendMessageToMainBoard(text: String): Boolean {
            if (!mainBoardConnected) {
                if (text.startsWith("player%") || text.startsWith("team%") || text.startsWith("teamW") || text.startsWith("teamB")) {
                    Log.d(TAG, "mainboard command skipped, board not connected -> $text")
                }
                return false
            }

            val socket = btSocketMainBoard
            if (socket == null) {
                mainBoardConnected = false
                Log.d(TAG, "mainboard socket missing -> $text")
                return false
            }

            if (text.startsWith("player%") || text.startsWith("team%") || text.startsWith("teamW") || text.startsWith("teamB")) {
                Log.d(TAG, "mainboard command -> $text")
            }

            val sent = sendMessage("$text%", socket)
            if (!sent) {
                mainBoardConnected = false
                Log.d(TAG, "mainboard command failed -> $text")
            }
            return sent
        }

        fun sendTeamNamesToMainBoardReliable() {
            val commands = GameControl.getTeamNameCommands()
            repeat(TEAM_SYNC_RETRY_ROUNDS) {
                commands.forEach { command ->
                    sendMessageToMainBoard(command)
                    Thread.sleep(TEAM_SYNC_DELAY_MS)
                }
            }
        }

        fun triggerMainBoardHorn() {
            sendMessageToMainBoard("horn")
        }

        fun startConnectedDisplaysHorn() {
            sendMessageToMainBoard("horn%start")
            sendMessageToAllShotClock("horn%start")
        }

        fun stopConnectedDisplaysHorn() {
            sendMessageToMainBoard("horn%stop")
            sendMessageToAllShotClock("horn%stop")
        }

        fun sendMessageToShotClock(text: String, index: Int) {
            if (shotClocksConnected[index]) {
                Log.d(TAG, "Index: $index")
                Log.d(TAG, "Index: $index")
                val textTemp = "$text%"
                val check = btSocketsShotClocks[index]?.let { sendMessage(textTemp, it) }
                if (check == null || !check) {
                    shotClocksConnected[index] = false
                }
            }
        }

        fun sendMessageToAllShotClock(text: String) {
            shotClocksConnected.forEachIndexed { index, _ ->
                sendMessageToShotClock(text, index)
            }
        }

        private fun sendMessage(text: String, socket: BluetoothSocket): Boolean {
            return try {
                socket.outputStream?.write(text.toByteArray())
                true
            } catch (e: IOException) {
                false
            }
        }

        fun readBluetoothData(){
//            Log.d(TAG, Thread.currentThread().name)
//            val bluetoothSocketInputStream = bluetoothSocket.inputStream
//            val buffer = ByteArray(1024)
//            var bytes: Int
//            //Loop to listen for received bluetooth messages
//            while (true) {
//                try {
////                    bytes = bluetoothSocketInputStream.read(buffer)
////                    val readMessage = String(buffer, 0, bytes)
////                    liveData.postValue(readMessage)
//                } catch (e: IOException) {
//                    e.printStackTrace()
//                    break
//                }
//            }
        }
    }

    private var mainBoardMyUUID: UUID? = null
    lateinit var myMainBoard: BluetoothDevice
    var remoteMainBoard: BluetoothDevice? = null

    private val localShotClocks = mutableListOf<BluetoothDevice?>(
        null,
        null,
        null,
        null
    )
    val remoteShotClocks = mutableListOf<BluetoothDevice?>(
        null,
        null,
        null,
        null
    )
    private val shotClockUUIDs = mutableListOf<UUID?>(
        null,
        null,
        null,
        null
    )

    val bluetoothAdapter: BluetoothAdapter? =
        appContext?.getSystemService(BluetoothManager::class.java)?.adapter

    fun connectMainBoard(): Boolean {
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled) {
                requestBTPermisson()
            }
            if (bluetoothAdapter.isEnabled) {
                if (remoteMainBoard != null) {
                    mainBoardMyUUID?.let { connectToDevice(remoteMainBoard!!, it) }
                    if (mainBoardConnected) {
                        Log.d(TAG, "connected")
                        syncMainBoardAfterConnection()
                        return true
                    } else {
                        Log.d(TAG, "mainboard not connected")
                    }
                }
            }
        }
        return false
    }

    fun connectShotClock(index: Int): Boolean {
        Log.d(TAG, "yo0: $index")
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled) {
                requestBTPermisson()
            }
            Log.d(TAG, "yo1: $index")
            Log.d(TAG, remoteShotClocks[index].toString())
            if (bluetoothAdapter.isEnabled && remoteShotClocks[index] != null) {
                Log.d(TAG, "yo2: $index")
                shotClockUUIDs[index]?.let { connectToDevice(remoteShotClocks[index]!!, it) }
                Log.d(TAG, "yo3: $index")
                if (shotClocksConnected[index]) {
                    Log.d(TAG, "connected")
                    syncShotClockAfterConnection(index)
                    return true
                } else {
                    Log.d(TAG, "shotclock1 not connected")
                }
            }
        }
        return false
    }

    private fun initMainBoardAfterConnection(): String {
        val currCountdown = GameControl.currentCountdown
        val minutes = MyTimeConverter.getMinutesFromLong(currCountdown)
        val seconds = MyTimeConverter.getSecondsFromLong(currCountdown)
        val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
        val colorMainTime = if (minutes < 1) "red" else "default"
        Log.d(TAG, "send main")
        return "timeGame%$minutes:$secondsString%$colorMainTime"
    }

    private fun syncMainBoardAfterConnection() {
        sendMessageToMainBoard(initMainBoardAfterConnection())
        Thread.sleep(200)
        sendMessageToMainBoard("gameSection%${GameControl.getCurrentGameSection()}")
        Thread.sleep(200)
        sendTeamNamesToMainBoardReliable()
        sendMessageToMainBoard(GameControl.getDisplayedResultCommand())
        GameControl.getPlayerExclusionCommands().forEach { command ->
            Thread.sleep(80)
            sendMessageToMainBoard(command)
        }
    }

    private fun syncShotClockAfterConnection(index: Int) {
        val textMain = initShotClocksAfterConnection("main")
        val textShot = initShotClocksAfterConnection("shotclock")
        sendMessageToShotClock(textMain, index)
        Thread.sleep(200)
        sendMessageToShotClock(textShot, index)
    }

    private fun initShotClocksAfterConnection(mode: String): String {
        if (mode == "main") {
            val currCountdown = GameControl.currentCountdown
            val minutes = MyTimeConverter.getMinutesFromLong(currCountdown)
            val seconds = MyTimeConverter.getSecondsFromLong(currCountdown)
            val secondsString = if (seconds < 10) "0$seconds" else "$seconds"
            val colorMainTime = if (minutes < 1) "red" else "default"
            Log.d(TAG, "send first")
            return "time%$minutes:$secondsString%$colorMainTime"
        } else {
            val currShotCountdown = GameControl.getcountdownShotclock()
            Log.d(TAG, "currShotCountdown: $currShotCountdown")
            val secondsShot = MyTimeConverter.getSecondsFromLong(currShotCountdown)
            val secondsShotString = if (secondsShot < 10) "0$secondsShot" else "$secondsShot"
            val secondsShotSmall = MyTimeConverter.getSecondsSmallFromLong(currShotCountdown)
            val colorShotclock = if (secondsShot < 6) "red" else "default"
            Log.d(TAG, "send second")
            return "shotclock%$secondsShotString%$colorShotclock%$secondsShotSmall"
        }
    }

    fun manageMyConnectedSocket(socket: BluetoothSocket, mode: String) {
        when (mode) {
            MAIN_BOARD_BT_NAME -> btSocketMainBoard = socket
            SHOTCLOCK_1_BT_NAME -> btSocketsShotClocks[0] = socket
            SHOTCLOCK_2_BT_NAME -> btSocketsShotClocks[1] = socket
            SHOTCLOCK_3_BT_NAME -> btSocketsShotClocks[2] = socket
            SHOTCLOCK_4_BT_NAME -> btSocketsShotClocks[3] = socket
        }
    }

    private fun connectToDevice(device: BluetoothDevice, uuid: UUID) {
        val temp = ConnectThread(device, uuid)
        temp.start()
        temp.join()
        temp.cancel()
    }

    private inner class ConnectThread(val device: BluetoothDevice, uuid: UUID) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(uuid)
        }
        override fun run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()
//            var mode = ""
            Log.d(TAG, "Try to connect: ${device.name}")
            try {
                mmSocket?.let { socket ->
                    // Connect to the remote device through the socket. This call blocks
                    // until it succeeds or throws an exception.
                    socket.connect()
                    Log.d(TAG, "Socket is connected: ${socket.isConnected}")
                    when (device.name) {
                        MAIN_BOARD_BT_NAME -> mainBoardConnected = true
                        SHOTCLOCK_1_BT_NAME -> shotClocksConnected[0] = true
                        SHOTCLOCK_2_BT_NAME -> shotClocksConnected[1] = true
                        SHOTCLOCK_3_BT_NAME -> shotClocksConnected[2] = true
                        SHOTCLOCK_4_BT_NAME -> shotClocksConnected[3] = true
                    }
                    manageMyConnectedSocket(socket, device.name)
                }
            } catch (ex: Exception) {
                Log.d(TAG, ex.toString())
                try {
                    Log.d(TAG, "try again: ${device.name}")
                    val newSocket: BluetoothSocket =
                        device.javaClass.getMethod(
                            "createRfcommSocket",
                            Int::class.javaPrimitiveType
                        )
                            .invoke(device, 1) as BluetoothSocket
                    newSocket.let { socket ->
                        socket.connect()
                        if (!mainBoardConnected && (device == remoteMainBoard)) {
                            mainBoardConnected = true
                            manageMyConnectedSocket(newSocket, device.name)
                        } else if (!shotClocksConnected[0] && (device == remoteShotClocks[0])) {
                            shotClocksConnected[0] = true
                            manageMyConnectedSocket(newSocket, device.name)
                        } else if (!shotClocksConnected[1] && (device == remoteShotClocks[1])) {
                            shotClocksConnected[1] = true
                            manageMyConnectedSocket(newSocket, device.name)
                        } else if (!shotClocksConnected[2] && (device == remoteShotClocks[2])) {
                            shotClocksConnected[2] = true
                            manageMyConnectedSocket(newSocket, device.name)
                        } else if (!shotClocksConnected[3] && (device == remoteShotClocks[3])) {
                            shotClocksConnected[3] = true
                            manageMyConnectedSocket(newSocket, device.name)
                        }
                    }
                } catch (e1: IOException) {
                    Log.d(TAG, "Fallback failed. Cancelling: ", e1)
                    when (device.name) {
                        MAIN_BOARD_BT_NAME -> mainBoardConnected = false
                        SHOTCLOCK_1_BT_NAME -> shotClocksConnected[0] = false
                        SHOTCLOCK_2_BT_NAME -> shotClocksConnected[1] = false
                        SHOTCLOCK_3_BT_NAME -> shotClocksConnected[2] = false
                        SHOTCLOCK_4_BT_NAME -> shotClocksConnected[3] = false
                    }
                }
            }
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
//            try {
////                mmSocket?.close()
//            } catch (e: IOException) {
//                Log.e(TAG, "Could not close the client socket", e)
//            }
        }
    }

    fun searchAllDevice() {
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled) {
                requestBTPermisson()
            }

            if (bluetoothAdapter.isEnabled) {
                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices

                if (pairedDevices != null && pairedDevices.isNotEmpty()) {
                    pairedDevices.forEach { device ->
                        if (device.name == MAIN_BOARD_BT_NAME) {
                            Log.d(TAG, "paired device: mainboard")
                            myMainBoard = device
                            remoteMainBoard = bluetoothAdapter.getRemoteDevice(myMainBoard.address)
                            mainBoardMyUUID = myMainBoard.uuids[0].uuid
                        }
                        val nameArray = arrayOf(SHOTCLOCK_1_BT_NAME,SHOTCLOCK_2_BT_NAME,SHOTCLOCK_3_BT_NAME,SHOTCLOCK_4_BT_NAME)
                        nameArray.forEachIndexed { index, s ->
                            if (device.name == s) {
                                Log.d(TAG, "paired device: shotclock ${index+1}")
                                localShotClocks[index] = device
                                remoteShotClocks[index] = bluetoothAdapter.getRemoteDevice(
                                    localShotClocks[index]?.address
                                )
                                shotClockUUIDs[index] = localShotClocks[index]?.uuids?.get(0)?.uuid
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestBTPermisson() {
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled) {
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//                if (myActivity != null) {
//                    startActivityForResult(myActivity, enableBtIntent, REQUEST_ENABLE_BT, null)
//                }
            }
        }
//        else {
//            Toast.makeText(myActivity, "This device does not support bluetooth", Toast.LENGTH_LONG).show()
//        }
    }
}
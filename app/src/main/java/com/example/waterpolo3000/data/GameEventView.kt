package com.example.waterpolo3000.data

import androidx.room.Ignore
import com.example.waterpolo3000.game.GameControl
import com.example.waterpolo3000.utilities.MyTimeConverter

data class GameEventView(
    val guid               : String,
    val gameSection        : String,
    val time               : Long,
    val cap                : String,
    val number             : String,
    val gameEventType      : Int,
    val gameEventTypeString: String,
    val firstName          : String,
    val lastName           : String
){
    @Ignore
    var minutes = MyTimeConverter.getMinutesFromLong(time)
    @Ignore
    var seconds = MyTimeConverter.getSecondsFromLong(time)
    @Ignore
    var secondsSmall = MyTimeConverter.getSecondsSmallFromLong(time)

    @Ignore
    var sectionLabel = gameSection.toIntOrNull()?.let { GameControl.getSectionLabel(it) } ?: gameSection

    @Ignore
    var timeString =  "$minutes:"+if(seconds>9) "$seconds.$secondsSmall" else "0$seconds.$secondsSmall"
}

package com.example.waterpolo3000.data

import androidx.room.Ignore
import com.example.waterpolo3000.utilities.MyTimeConverter

data class ProtocolGameEventType (
    val gameSection: Int,
    val guid       : String,
    var time       : Long,
    var numberWhite: String,
    var numberBlue : String,
    var type       : String
){
    @Ignore
    var minutes = MyTimeConverter.getMinutesFromLong(time)
    @Ignore
    var seconds = MyTimeConverter.getSecondsFromLong(time)
    @Ignore
    var secondsSmall = MyTimeConverter.getSecondsSmallFromLong(time)

    @Ignore
    var timeString = if(guid.length<20) guid else "$minutes:"+if(seconds>9) "$seconds.$secondsSmall" else "0$seconds.$secondsSmall"
}
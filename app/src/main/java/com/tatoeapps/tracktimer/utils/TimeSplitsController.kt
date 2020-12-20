package com.tatoeapps.tracktimer.utils

class TimeSplitsController {
    //array of positions user marks in exoplayer
    private var timePointsArray = arrayListOf<Long>()
    private var textToDisplay = ""
    var isActive = false
    var isCleared = true

    fun startTiming(initialPosition: Long){
        isActive=true
        isCleared=false
        timePointsArray.clear()
        timePointsArray.add(initialPosition)
        textToDisplay = Utils.floatToStartString()
    }

    fun doLap(lapPosition: Long) {
        if (isActive){
            val timeSinceStart = lapPosition-timePointsArray[0]
            val timeLapSplit = lapPosition - timePointsArray[timePointsArray.size-1]
            timePointsArray.add(lapPosition)
            val pair = Pair(timeSinceStart/1000.toFloat(),timeLapSplit/1000.toFloat())
            textToDisplay+= Utils.pairFloatToLapString(pair)
        }
    }

    fun stopTiming(stopPosition:Long) {
        if (isActive){
            doLap(stopPosition)
            isActive=false
        }
    }

    fun clearTiming() {
        textToDisplay=""
        isActive=false
        isCleared=true
    }

    fun getTimingDisplayText():String {
        return textToDisplay
    }
}
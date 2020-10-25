package com.tatoeapps.tracktimer.utils

class TimeSplitsController {
    //array of positions user marks in exoplayer
    private var timePointsArray = arrayListOf<Long>()
    var isActive = false

    fun startTiming(initialPosition: Long):Float{
        isActive=true
        timePointsArray.clear()
        timePointsArray.add(initialPosition)
        return 0F
    }

    fun doLap(lapPosition: Long):Pair<Float,Float> {
        val timeSinceStart = lapPosition-timePointsArray[0]
        val timeLapSplit = lapPosition - timePointsArray[timePointsArray.size-1]
        timePointsArray.add(lapPosition)
        return Pair(timeSinceStart/1000.toFloat(),timeLapSplit/1000.toFloat())
    }

    fun stopTiming(stopPosition:Long):Pair<Float,Float> {
        isActive=false
        return doLap(stopPosition)
    }
}
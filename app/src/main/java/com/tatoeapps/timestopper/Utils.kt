package com.tatoeapps.timestopper

import timber.log.Timber

object Utils {

    fun getRealTimeFromMatrixInSeconds(
        pairArrayList: ArrayList<Pair<Long, Float>>,
        startingTime: Long
    ): Double {
        var total = 0L
        var iTime = startingTime
        for (pair in pairArrayList) {
            val fTime = pair.first
            val speedFactor = pair.second
            total += ((fTime - iTime)).toLong()
            iTime = pair.first
        }
        Timber.d("CONTROL - getting real time from matrix - total:$total")

        return total.toDouble() / 1000
    }

}
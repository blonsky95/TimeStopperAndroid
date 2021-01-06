package com.tatoeapps.tracktimer.utils

import android.app.Application
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
import android.transition.Slide
import android.transition.Transition
import android.view.Gravity
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Renderer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioRendererEventListener
import com.google.android.exoplayer2.audio.AudioSink
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.tatoeapps.tracktimer.R
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import java.text.DecimalFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

object Utils {

    /**
     * NORMAL UTILS
     */
    private val df = DecimalFormat("0.000")

    fun floatToStartString(startTiming: Float = 0F): String {
        return "${df.format(startTiming)} (START)"
    }

    fun pairFloatToLapString(pair: Pair<Float, Float>): String {
        return "\n${df.format(pair.first)} (${df.format(pair.second)})"
    }

    val VISIBILITY_DO_NOTHING = 0
    val VISIBILITY_TOGGLE = 1
    val VISIBILITY_SHOW = 2
    val VISIBILITY_HIDE = 3


    /**
     * Timing feature & Trial mode stuff
     */


//    const val numberVideosTimingFree = 1
//
//    fun canStartTimingTrial(context: Context): Boolean {
//        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
//        if (dayOfYear == getPrefDayOfYearTrial(context)) {
//            //so the user has done at least one video if the dates are matching
//            if (getCountOfFreeDailyTiming(context) >= numberVideosTimingFree) {
//                //too many videos for free trial, has expired
//                //expired
//                return false
//            }
//        } else {
//            //its a different day from the last use, so reset count to 0, and return true later
//            addOneToCountOfFreeDailyTiming(
//                context,
//                getCountOfFreeDailyTiming(context),
//                true
//            )
//        }
//        return true
//    }


    /**
     * SHARED PREFS STUFF
     */

//    private fun hasUserReviewedApp(context: Context): Boolean {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_has_reviewed), Context.MODE_PRIVATE
//        )
//        val defaultValue = false
//        return sharedPref.getBoolean(
//            context.getString(R.string.preference_has_reviewed),
//            defaultValue
//        )
//    }
//
//    fun updateHasUserReviewedApp(context: Context, isSubscribed: Boolean) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_has_reviewed), Context.MODE_PRIVATE
//        )
//        with(sharedPref.edit()) {
//            putBoolean(context.getString(R.string.preference_has_reviewed), isSubscribed)
//            apply()
//        }
//    }

//    private const val daysBetweenPrompt = 14
//    private const val MILLIS_IN_ONE_DAY = 86400000
//
//    fun shouldShowRatingPrompt(context: Context, currentSystemTimeInMillis: Long): Boolean {
//        if (hasUserReviewedApp(context)) {
//            return false
//        }
//
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_date_rating_prompt), Context.MODE_PRIVATE
//        )
//        val lastPromptTimeInMillis =
//            sharedPref.getLong(context.getString(R.string.preference_date_rating_prompt), -1L)
//        return if (lastPromptTimeInMillis > 0 && currentSystemTimeInMillis >= lastPromptTimeInMillis + (daysBetweenPrompt * MILLIS_IN_ONE_DAY)) {
//            //14 or more days have passed since update or first install
//            updateTimeOfLastPrompt(context, currentSystemTimeInMillis)
//            true
//        } else {
//            if (lastPromptTimeInMillis <= 0) {
//                updateTimeOfLastPrompt(context, currentSystemTimeInMillis)
//            }
//            false
//        }
//
//    }

//    private fun updateTimeOfLastPrompt(context: Context, systemTimeInMillis: Long) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_date_rating_prompt), Context.MODE_PRIVATE
//        )
//        with(sharedPref.edit()) {
//            putLong(context.getString(R.string.preference_date_rating_prompt), systemTimeInMillis)
//            apply()
//        }
//    }


//    fun isUserSubscribed(context: Context): Boolean {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_is_subscribed), Context.MODE_PRIVATE
//        )
//        val defaultValue = false
//        return sharedPref.getBoolean(
//            context.getString(R.string.preference_is_subscribed),
//            defaultValue
//        )
//    }
//
//    fun updateIsUserSubscribed(context: Context, isSubscribed: Boolean) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_is_subscribed), Context.MODE_PRIVATE
//        )
//        with(sharedPref.edit()) {
//            putBoolean(context.getString(R.string.preference_is_subscribed), isSubscribed)
//            apply()
//        }
//    }

//    fun getIsTimingFreeActive(context: Context): Boolean {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_is_trial_on), Context.MODE_PRIVATE
//        )
//        val defaultValue = false
//        return sharedPref.getBoolean(
//            context.getString(R.string.preference_is_trial_on),
//            defaultValue
//        )
//    }
//
//    fun updateIsTimingFreeActive(context: Context, isActive: Boolean) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_is_trial_on), Context.MODE_PRIVATE
//        )
//        with(sharedPref.edit()) {
//            putBoolean(context.getString(R.string.preference_is_trial_on), isActive)
//            apply()
//        }
//    }
//
//    private fun getPrefDayOfYearTrial(context: Context): Int {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_day_of_year_last_trial), Context.MODE_PRIVATE
//        )
//        val defaultValue = -1
//        return sharedPref.getInt(
//            context.getString(R.string.preference_day_of_year_last_trial),
//            defaultValue
//        )
//    }
//
//    fun updatePrefDayOfYearLastTiming(context: Context, int: Int) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_day_of_year_last_trial), Context.MODE_PRIVATE
//        )
//        with(sharedPref.edit()) {
//            putInt(context.getString(R.string.preference_day_of_year_last_trial), int)
//            apply()
//        }
//    }
//
//    fun getCountOfFreeDailyTiming(context: Context): Int {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_count_video_timing_trial),
//            Context.MODE_PRIVATE
//        )
//        val defaultValue = 0
//        return sharedPref.getInt(
//            context.getString(R.string.preference_count_video_timing_trial),
//            defaultValue
//        )
//    }
//
//    fun addOneToCountOfFreeDailyTiming(
//        context: Context,
//        countBeforeUpdate: Int,
//        resetCounter: Boolean = false
//    ) {
//        val sharedPref = context.getSharedPreferences(
//            context.getString(R.string.preference_count_video_timing_trial),
//            Context.MODE_PRIVATE
//        )
//        var newCount = countBeforeUpdate + 1
//        if (resetCounter) {
//            newCount = 0
//        }
//        with(sharedPref.edit()) {
//            putInt(context.getString(R.string.preference_count_video_timing_trial), newCount)
//            apply()
//        }
//    }
//



    /**
     * EXOPLAYER STUFF
     */

    fun getExoPlayerInstance(context: Context): SimpleExoPlayer {
        val myDefaultRenderersFactory =
            MyDefaultRendererFactory(
                context
            ).setEnableAudioTrackPlaybackParams(true)
        return SimpleExoPlayer.Builder(context, myDefaultRenderersFactory).build()
    }

    fun getDataSourceFactoryInstance(
        context: Context,
        application: Application
    ): DataSource.Factory {
        return DefaultDataSourceFactory(
            context,
            Util.getUserAgent(context, application.packageName)
        )
    }

    fun getVideoSource(
        mediaItem: MediaItem,
        dataSourceFactory: DataSource.Factory
    ): MediaSource {
        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)
    }

    fun getFrameRateOfVideo(context: Context, uri: Uri): Float {
        val mediaExtractor = MediaExtractor()
        var videoFrameRate = 30F
        try {
            mediaExtractor.setDataSource(context, uri, null)
            val numTracks = mediaExtractor.trackCount
            for (i in 0 until numTracks) {
                val mediaFormat = mediaExtractor.getTrackFormat(i)
                val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
                if (mime!!.startsWith("video/")) {
                    if (mediaFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        videoFrameRate =
                            mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.d("Exception getting frame rate: $e")
        } finally {
            mediaExtractor.release()
        }
        Timber.d("Video frame rate: $videoFrameRate")

        return videoFrameRate
    }


    /**
     * document this function hehe
     */
    fun getPositionOfNextFrame(
        currentExoPlayerPosition: Long,
        videoFrameRate: Float,
        needsCorrection: Boolean
    ): Long {
        val correctionNextFrameForward = floor(videoFrameRate / 15).toLong()
        val frameJumpInMs = round(1000 / videoFrameRate).toLong()

        return if (needsCorrection) {
            currentExoPlayerPosition + frameJumpInMs * correctionNextFrameForward
        } else {
            currentExoPlayerPosition + frameJumpInMs
        }
    }

    fun getPositionOfPreviousFrame(
        currentExoPlayerPosition: Long,
        videoFrameRate: Float
    ): Long {
        val frameJumpInMs = ceil(1000 / videoFrameRate).toLong()
        return currentExoPlayerPosition - frameJumpInMs
    }

    fun getNoAnimationTransition(supportFragmentManager: FragmentManager):FragmentTransaction {
        return supportFragmentManager.beginTransaction()
    }

    fun getVerticalFragmentTransition(supportFragmentManager: FragmentManager): FragmentTransaction {
        return supportFragmentManager.beginTransaction().setCustomAnimations(
            R.anim.slide_down_to_up,
            R.anim.slide_up_to_down,
            R.anim.slide_down_to_up,
            R.anim.slide_up_to_down
        )
    }

    fun getHorizontalFragmentTransition(supportFragmentManager: FragmentManager): FragmentTransaction {
        return supportFragmentManager.beginTransaction().setCustomAnimations(
            R.anim.slide_right_to_left,
            R.anim.slide_left_to_right,
            R.anim.slide_right_to_left,
            R.anim.slide_left_to_right
        )
    }

    fun getSlideTransition(infoContainer: LinearLayout): Transition {
        val transition = Slide(Gravity.START)
        transition.duration = 200
        transition.addTarget(infoContainer)
        return transition
    }


    private class MyDefaultRendererFactory(context: Context) :
        DefaultRenderersFactory(context) {
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: java.util.ArrayList<Renderer>
        ) {
//            super.buildAudioRenderers(
//                context,
//                extensionRendererMode,
//                mediaCodecSelector,
//                enableDecoderFallback,
//                audioSink,
//                eventHandler,
//                eventListener,
//                out
//            )
        }
    }


}
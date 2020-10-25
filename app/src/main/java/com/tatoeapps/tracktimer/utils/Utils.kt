package com.tatoeapps.tracktimer.utils

import android.app.Application
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Handler
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
import timber.log.Timber
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor

object Utils {

    /**
     * NORMAL UTILS
     */
    private val df = DecimalFormat("0.000")

    fun floatToStartString(startTiming: Float): String {
        return df.format(startTiming)
    }

    fun pairFloatToLapString(pair: Pair<Float,Float>) :String{
        return "\n${df.format(pair.first)} (${df.format(pair.second)})"
    }

    /**
     * SHARED PREFS STUFF
     */

    fun isUserFirstTimer(context: Context): Boolean {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.preference_first_time_key), Context.MODE_PRIVATE
        )
        val defaultValue = true
        return sharedPref.getBoolean(context.getString(R.string.preference_first_time_key), defaultValue)
//        return true
    }

    fun updateUserFirstTimer(context: Context, isFirstTime:Boolean) {
        val sharedPref = context.getSharedPreferences(
            context.getString(R.string.preference_first_time_key), Context.MODE_PRIVATE
        )
        with(sharedPref.edit()) {
            putBoolean(context.getString(R.string.preference_first_time_key), isFirstTime)
            apply()
        }
    }


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

    fun getVideoSource(mediaItem: MediaItem, dataSourceFactory: DataSource.Factory): MediaSource {
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
        val frameJumpInMs = ceil(1000 / videoFrameRate).toLong()

        return if (needsCorrection) {
            currentExoPlayerPosition + frameJumpInMs*correctionNextFrameForward
        } else {
            currentExoPlayerPosition + frameJumpInMs
        }
    }

    fun getPositionOfPreviousFrame(currentExoPlayerPosition: Long,
                                   videoFrameRate: Float): Long {
        val frameJumpInMs = ceil(1000 / videoFrameRate).toLong()
        return currentExoPlayerPosition - frameJumpInMs
    }

    private class MyDefaultRendererFactory(context: Context) : DefaultRenderersFactory(context) {
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
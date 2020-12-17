package com.tatoeapps.tracktimer.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.upstream.DataSource
import com.tatoeapps.tracktimer.fragments.SpeedSliderFragment
import com.tatoeapps.tracktimer.interfaces.MediaPlayerCustomActions
import com.tatoeapps.tracktimer.interfaces.TestInterface

class VideoPlayerController {

    /**
     * Bit of explaining, this class requires an exoplayer instance, a dataSourceFactory and a TestInterface
     *
     * It will handle
     * - preparingvideosource
     * - getCurrentPosition
     * - configure the custom actions of the player controls e.g. forward, rewind, next/previous frame
     * - stop/play video
     * - set speed of playback
     * - listener for player state - READY, END, BUFFERING, IDLE...
     */

    val isPlaying: Boolean = false
    private lateinit var mExoPlayer: SimpleExoPlayer
    private lateinit var mDataSourceFactory: DataSource.Factory

    private var firstNextFrameSkip = true
    private var videoFrameRate: Float = 0F
    private var speedFactor = SpeedSliderFragment.defaultSpeedFactor
//    private var currentPosition = 0L

    lateinit var mediaPlayerCustomActions: MediaPlayerCustomActions
    lateinit var testInterface: TestInterface

    private var hasVideo = false
    private var isPlayingVideo = false

    val videoSkipDefaultMs = 5000

    fun initialize(
        exoPlayer: SimpleExoPlayer,
        dataSourceFactory: DataSource.Factory,
        mInterface: TestInterface
    ) {
        mExoPlayer = exoPlayer
        mDataSourceFactory = dataSourceFactory
        testInterface = mInterface
        exoPlayer.addListener(playerStateListener)
    }

    fun prepareVideoSource(mediaItem: MediaItem) {
        val videoSource = Utils.getVideoSource(mediaItem, mDataSourceFactory)

        mExoPlayer.setMediaSource(videoSource)
        mExoPlayer.prepare()
        hasVideo = true
    }

    fun getCurrentPosition(): Long {
        return mExoPlayer.currentPosition
    }

    fun configureExoPlayerButtonsActions(context: Context, mediaUri: Uri) {
        videoFrameRate = Utils.getFrameRateOfVideo(context, mediaUri)

        mediaPlayerCustomActions = object : MediaPlayerCustomActions {
            override fun goForward() {
                mExoPlayer.seekTo(mExoPlayer.currentPosition + (videoSkipDefaultMs * speedFactor).toLong())
            }

            override fun goRewind() {
                val rewindPosition =
                    if (mExoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor) < 0) {
                        0L
                    } else {
                        mExoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor).toLong()
                    }
                mExoPlayer.seekTo(rewindPosition)
            }

            override fun goNextFrame() {
                if (!isPlayingVideo) {
                    //if first next frame skip is true, it needs frame correcting - see issue #18
                    val newPosition = Utils.getPositionOfNextFrame(
                        mExoPlayer.currentPosition,
                        videoFrameRate,
                        firstNextFrameSkip
                    )
                    firstNextFrameSkip = false
                    mExoPlayer.seekTo(newPosition)
                }
            }

            override fun goPreviousFrame() {
                if (!isPlayingVideo) {
                    mExoPlayer.seekTo(
                        Utils.getPositionOfPreviousFrame(
                            mExoPlayer.currentPosition,
                            videoFrameRate
                        )
                    )
                }
            }
        }
    }

    fun stopPlaying() {
        if (isPlaying) {
            mExoPlayer.playWhenReady = false
        }
    }

    fun stopAndRelease() {
        mExoPlayer.stop()
        mExoPlayer.release()
    }

    fun setSpeed(newSpeed: Float) {
        speedFactor = newSpeed
        val playbackParameters = PlaybackParameters(speedFactor)
        mExoPlayer.setPlaybackParameters(playbackParameters)
    }

    private val playerStateListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    Runnable {
                        mExoPlayer.playWhenReady = true
                        firstNextFrameSkip = true
                    }
                }
                Player.STATE_ENDED -> {
                    testInterface.mediaFinished()
                }
                Player.STATE_BUFFERING -> {
                }
                Player.STATE_IDLE -> {
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingVideo = isPlaying
            if (isPlaying) {
                firstNextFrameSkip = true
            }
        }
    }


}
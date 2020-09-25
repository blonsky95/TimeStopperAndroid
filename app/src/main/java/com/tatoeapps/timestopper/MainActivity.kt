package com.tatoeapps.timestopper

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.transition.Slide
import android.transition.Transition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.Util
import com.tatoeapps.timestopper.R.id
import com.tatoeapps.timestopper.R.layout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor


class MainActivity : AppCompatActivity(), ActionButtonsInterface, SpeedSliderInterface {

    lateinit var exoPlayer: SimpleExoPlayer
    lateinit var playerView: PlayerView
    lateinit var dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    private var hasVideo = false
    private var isPlayingVideo = false
    private var videoFrameRate = 30f

    private var task: Runnable? = null
    private var speedFactor = 1.0f

    private val df = DecimalFormat("0.000")

    private var realTimeInSecs = 0f
    private var initialPositionInMillis: Long = 0
    private var isTiming = false
    private var lapsTimeStringDisplay = ""
    private var firstTimeAfterPlay = true


    private var theTimeSpeedMatrixLog = arrayListOf<Pair<Long, Float>>()
    private var timeIntervalMatrix = arrayListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        Timber.plant(Timber.DebugTree())

        checkPermissions()

        setUpFullScreen()

        if (savedInstanceState == null) {
            getStartFragment()
        }

        setUpPlayer()

        hideBuffering()

        setTouchListener()
    }

    private fun getStartFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.start_fragment_container, StartFragment()).commit()
    }

    override fun onPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.stop()
            exoPlayer.release()
        }
        super.onPause()
    }

    private fun setTouchListener() {
        playerView.setOnClickListener {
            val show =
                (supportFragmentManager.findFragmentById(R.id.actionBtns_frag) as ActionButtonsFragment).isHidden

            toggleFragmentsVisibility(
                show,
                supportFragmentManager.findFragmentById(R.id.actionBtns_frag) as ActionButtonsFragment
            )
            toggleFragmentsVisibility(
                show,
                supportFragmentManager.findFragmentById(R.id.speedSlider_frag) as SpeedSliderFragment
            )
            toggleInfoDisplay(info_container, show)
        }
    }

    private fun toggleInfoDisplay(view: View, show: Boolean) {
        val transition: Transition = Slide(Gravity.START)
        transition.duration = 400
        transition.addTarget(view)
        TransitionManager.beginDelayedTransition(parent_container, transition)
        view.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toggleFragmentsVisibility(show: Boolean, fragment: Fragment) {

        val fragTransaction = supportFragmentManager.beginTransaction()
        if (fragment is ActionButtonsFragment) {
            fragTransaction.setCustomAnimations(
                R.anim.slide_right_to_left,
                R.anim.slide_left_to_right,
                R.anim.slide_right_to_left,
                R.anim.slide_left_to_right
            )
        } else {
            fragTransaction.setCustomAnimations(
                R.anim.slide_down_to_up,
                R.anim.slide_up_to_down,
                R.anim.slide_down_to_up,
                R.anim.slide_up_to_down
            )
        }

        if (show) {
            fragTransaction.show(fragment).commit()
        } else {
            fragTransaction.hide(fragment).commit()
        }
    }

    private fun updateInfoDisplay(isVisible: Boolean) {
        if (isVisible) {
            timePointsDisplay.visibility = View.VISIBLE
        } else {
            timePointsDisplay.visibility = View.GONE
        }
    }

    private fun updateLapsText(resetCounter: Boolean = false) {
        if (resetCounter) {
            realTimeInSecs = 0f
            lapsTimeStringDisplay = df.format(realTimeInSecs)
        } else {
            val realElapsedTime =
                Utils.getRealTimeFromMatrixInSeconds(theTimeSpeedMatrixLog, initialPositionInMillis)
            lapsTimeStringDisplay += "\n${df.format(realElapsedTime)} (${df.format(
                (timeIntervalMatrix[timeIntervalMatrix.size - 1] - timeIntervalMatrix[timeIntervalMatrix.size - 2]).toDouble() / 1000
            )})"
        }
        timePointsDisplay.text = lapsTimeStringDisplay
    }

    private fun hideBuffering() {
        val timeBar: DefaultTimeBar =
            playerView.findViewById<View>(id.exo_progress) as DefaultTimeBar
        timeBar.setBufferedColor(0x33FFFFFF)
    }

    private fun setUpPlayer() {

        val defaultRenderersFactory = DefaultRenderersFactory(this).setEnableAudioTrackPlaybackParams(true)
        exoPlayer = SimpleExoPlayer.Builder(this, defaultRenderersFactory).build()

//        /* Instantiate a DefaultLoadControl.Builder. */
//        val builder = DefaultLoadControl.Builder()
//
//        /* Maximum amount of media data to buffer (in milliseconds). */
//        val loadControlMaxBufferMs = 100
//        val loadControlMinBufferMs = 100
//
//        /*Configure the DefaultLoadControl to use our setting for how many
//        Milliseconds of media data to buffer. */
//        builder.setBufferDurationsMs(
////            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
//            loadControlMinBufferMs,
//            loadControlMaxBufferMs,
//            /* To reduce the startup time, also change the line below */
////            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
//            loadControlMinBufferMs,
//            loadControlMinBufferMs
//        )
//
//        /* Build the actual DefaultLoadControl instance */
//        val loadControl = builder.build();
//
///* Instantiate ExoPlayer with our configured DefaultLoadControl */
//        exoPlayer = SimpleExoPlayer.Builder(
//            this, DefaultRenderersFactory(this), DefaultTrackSelector(this),
//            DefaultMediaSourceFactory(this), loadControl,
//            DefaultBandwidthMeter.getSingletonInstance(this),
//            AnalyticsCollector(Clock.DEFAULT)
//        ).build()

        exoPlayer.addListener(playerStateListener)

        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        playerView = findViewById(id.playerView)
        playerView.player = exoPlayer

        dataSourceFactory =
            DefaultDataSourceFactory(this, Util.getUserAgent(this, application.packageName))
    }

    private fun setUpFullScreen() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            // Note that system bars will only be "visible" if none of the
            // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                Timber.d("System bars are visible")
                // adjustments to your UI, such as showing the action bar or
                // other navigational controls.
            } else {
                Timber.d("System bars are visible")
                // adjustments to your UI, such as hiding the action bar or
                // other navigational controls.
            }
        }

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    override fun setSpeed(newValue: Float) {
        changeSpeed(newValue / 100)
    }

    override fun importVideo() {
        intentPickMedia()
    }

    override fun startTiming() {
        isTiming = true
        updateInfoDisplay(isTiming)
        initialPositionInMillis = exoPlayer.currentPosition
        updateTimeIntervalMatrix(true)
        updateTheTimeSpeedMatrix(true)
        updateLapsText(true)
    }

    override fun lapTiming() {
        Timber.d("CONTROL - lap pressed - isTiming: $isTiming")
        Timber.d("TEST - player current position: ${exoPlayer.currentPosition}")
        if (isTiming) {
            updateTimeIntervalMatrix()
            updateTheTimeSpeedMatrix()
            updateLapsText()
        }
    }

    override fun stopTiming() {
        if (isTiming) {
            updateTheTimeSpeedMatrix()
            updateLapsText()
            isTiming = false
        }
    }

    override fun clearTiming() {
        updateInfoDisplay(false)
    }


    private fun updateTheTimeSpeedMatrix(resetMatrix: Boolean = false) {
        if (resetMatrix) {
            theTimeSpeedMatrixLog.clear()
        } else {
            val pair = Pair(exoPlayer.currentPosition, speedFactor)
            theTimeSpeedMatrixLog.add(pair)
        }
    }

    private fun updateTimeIntervalMatrix(resetMatrix: Boolean = false) {
        if (resetMatrix) {
            timeIntervalMatrix.clear()
            timeIntervalMatrix.add(0L)
        } else {
            timeIntervalMatrix.add(exoPlayer.currentPosition - initialPositionInMillis)
        }
    }

    private fun changeSpeed(newSpeed: Float) {
        if (isTiming) {
            updateTheTimeSpeedMatrix()
        }
        speedFactor = newSpeed
        val playbackParameters = PlaybackParameters(speedFactor)
//            exoPlayer.clearVideoDecoderOutputBufferRenderer()
//            exoPlayer.stop()
//            exoPlayer.playWhenReady=true
        exoPlayer.setPlaybackParameters(playbackParameters)
//        exoPlayer.seekTo(exoPlayer.currentPosition)
//            exoPlayer.clearVideoDecoderOutputBufferRenderer()

    }

    private fun intentPickMedia() {

        val intent = Intent(
            Intent.ACTION_OPEN_DOCUMENT,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        intent.type = "video/*"
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            hideStartFragment()
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            prepareVideoSource(data!!.data!!)
            getVideoFrameRate(data.data!!)
            configurePlayerButtons()
            updateInfoDisplay(false)

        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun hideStartFragment() {
        supportFragmentManager.beginTransaction()
            .hide(supportFragmentManager.findFragmentById(R.id.start_fragment_container) as StartFragment)
            .commitAllowingStateLoss()
    }

    private fun getVideoFrameRate(uri: Uri) {
        val mediaExtractor = MediaExtractor()
        try {
            mediaExtractor.setDataSource(this, uri, null)
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
    }

    private fun configurePlayerButtons() {
        val videoSkipDefaultMs = 5000
        custom_forward.setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition + (videoSkipDefaultMs * speedFactor).toLong())
        }
        custom_rewind.setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor).toLong())
        }

        val frameJumpInMs = ceil(1000 / videoFrameRate).toLong()


        next_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {

                val newPosition: Long
                if (firstTimeAfterPlay) {
                    //todo test with 30 and 60 and modify the 3 below to something more reasonable
                    val correctionNextFrameForward = floor(videoFrameRate/15).toLong()
                    newPosition = exoPlayer.currentPosition + frameJumpInMs * correctionNextFrameForward
                    firstTimeAfterPlay = false
                } else {
                    newPosition = exoPlayer.currentPosition + frameJumpInMs
                }
                firstTimeAfterPlay = false
                Timber.d("Current position before NF: ${exoPlayer.currentPosition}")
                exoPlayer.seekTo(newPosition)
                Timber.d("Current position after NF: ${exoPlayer.currentPosition}")
            }
        }
        previous_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                Timber.d("Current position before PF: ${exoPlayer.currentPosition}")
//                Timber.d("Buffered position PF: ${exoPlayer.bufferedPosition}")
//                exoPlayer.clearVideoDecoderOutputBufferRenderer()
                exoPlayer.seekTo(exoPlayer.currentPosition - frameJumpInMs)
                Timber.d("Current position after PF: ${exoPlayer.currentPosition}")
            }
        }

    }

    private fun prepareVideoSource(selectedMediaUri: Uri) {
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(selectedMediaUri)

        exoPlayer.prepare(videoSource)
        hasVideo = true
    }

    private val playerStateListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    task = Runnable {
                        exoPlayer.playWhenReady = true
                        firstTimeAfterPlay = true
                    }
                }

                Player.STATE_ENDED -> {

                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isPlayingVideo = isPlaying
            if (isPlaying) {
                firstTimeAfterPlay = true
            }
        }
    }

    override fun onBackPressed() {
        val dialogBuilder = AlertDialog.Builder(this)
            .setMessage("Are you sure you want to quit?")
            .setPositiveButton(
                "Yes"
            ) { _, _ -> super.onBackPressed() }
            .setNegativeButton("No", null)
            .setCancelable(true)

        dialogBuilder.show()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.WAKE_LOCK,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        } else {
            hasPermissions = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                hasPermissions =
                    (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }
}
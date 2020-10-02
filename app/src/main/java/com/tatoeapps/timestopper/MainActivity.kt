package com.tatoeapps.timestopper

import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.tatoeapps.timestopper.R.id
import com.tatoeapps.timestopper.R.layout
import com.tatoeapps.timestopper.SpeedSliderFragment.Companion.defaultSpeedFactor
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber
import java.lang.Runnable
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
    private var speedFactor = defaultSpeedFactor

    private val df = DecimalFormat("0.000")

    private var realTimeInSecs = 0f
    private var initialPositionInMillis: Long = 0
    private var isTiming = false
    private var lapsTimeStringDisplay = ""
    private var firstTimeAfterPlay = true

    private var isFullScreenActive = false

    private var theTimeSpeedMatrixLog = arrayListOf<Pair<Long, Float>>()
    private var timeIntervalMatrix = arrayListOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        Timber.plant(Timber.DebugTree())

        checkPermissions()
//        setUpFullScreen()
        setUpSystemUiListener()

        if (savedInstanceState == null) {
            getStartFragment()
        }

        setUpPlayer()
        hideBuffering()
        setUserScreenTapListener()
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
                    showActionFragments(true)
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
        exoPlayer.setPlaybackParameters(playbackParameters)
    }

    private fun intentPickMedia() {
        if (exoPlayer.isPlaying) {
            exoPlayer.playWhenReady = false
        }
        val intent = Intent(
            Intent.ACTION_OPEN_DOCUMENT,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        intent.type = "video/*"
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            isTiming = false

            //UI
            hideStartFragment()
            updateInfoDisplay(false)
            //check this
//            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
//                    View.SYSTEM_UI_FLAG_FULLSCREEN or
//                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

            //buttons
            prepareVideoSource(MediaItem.fromUri(data!!.data!!))
            getVideoFrameRate(data.data!!)
            adjustPlayerControls()

        }
        super.onActivityResult(requestCode, resultCode, data)
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

    private fun adjustPlayerControls() {
        val videoSkipDefaultMs = 5000

        //reset Speed
        (supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment).resetSpeed()

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
                    val correctionNextFrameForward = floor(videoFrameRate / 15).toLong()
                    newPosition =
                        exoPlayer.currentPosition + frameJumpInMs * correctionNextFrameForward
                    firstTimeAfterPlay = false
                } else {
                    newPosition = exoPlayer.currentPosition + frameJumpInMs
                }
                firstTimeAfterPlay = false
                exoPlayer.seekTo(newPosition)
            }
        }
        previous_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                exoPlayer.seekTo(exoPlayer.currentPosition - frameJumpInMs)
            }
        }

    }

    private fun prepareVideoSource(mediaItem: MediaItem) {
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(videoSource)
        exoPlayer.prepare()
        hasVideo = true
    }

    /**
     * LIFECYCLE STUFF
     */

    private var isAppInForeground = true

    override fun onPause() {
        Timber.d("System - on Pause")
        isAppInForeground = false
        if (exoPlayer.isPlaying) {
            exoPlayer.stop()
            exoPlayer.release()
        }
        super.onPause()
    }

    override fun onResume() {
        Timber.d("System - on Resume")
        isAppInForeground = true
        if (!isFullScreenActive) {
            setUpFullScreen()
        }
        super.onResume()
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

    /**
     * EVERYTHING UNDER HERE IS RELATED TO APP **UI** (OR SHOULD BE)
     */
    private fun hideStartFragment() {
        supportFragmentManager.beginTransaction()
            .hide(supportFragmentManager.findFragmentById(id.start_fragment_container) as StartFragment)
            .commitAllowingStateLoss()
    }

    private fun updateInfoDisplay(isVisible: Boolean) {
        if (isVisible) {
            timePointsDisplay.visibility = View.VISIBLE
        } else {
            timePointsDisplay.visibility = View.GONE
        }
    }

    private fun getStartFragment() {
        supportFragmentManager.beginTransaction()
            .add(id.start_fragment_container, StartFragment()).commit()
    }

    private fun setUserScreenTapListener() {
        playerView.setOnClickListener {
            val show =
                (supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment).isHidden
            showActionFragments(show)
            if (!isFullScreenActive) {
                setUpFullScreen()
            }
        }
    }

    private fun showActionFragments(show: Boolean) {
        toggleFragmentsVisibility(
            show,
            supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment
        )
        toggleFragmentsVisibility(
            show,
            supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment
        )
        toggleInfoDisplay(info_container, show)
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

    private fun hideBuffering() {
        val timeBar: DefaultTimeBar =
            playerView.findViewById<View>(id.exo_progress) as DefaultTimeBar
        timeBar.setBufferedColor(0x33FFFFFF)
    }


    private fun setUpPlayer() {
        exoPlayer = Utils.getExoPlayerInstance(this)
        exoPlayer.addListener(playerStateListener)
        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        playerView = findViewById(id.playerView)
        playerView.player = exoPlayer

        dataSourceFactory = Utils.getDataSourceFactoryInstance(this, application)
    }


    private fun setUpFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        isFullScreenActive = true
        Timber.d("System bars should hide")
    }


    private fun setUpSystemUiListener() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            //went to file provider
            if (!isAppInForeground && visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                Timber.d("System bars - in file provider - no need to change anything because onresume will catch")
                isFullScreenActive = false
                return@setOnSystemUiVisibilityChangeListener
            }

            //user drags status bar
            if (isAppInForeground && visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                Timber.d("System bars - are visible because user dragged - when user taps they will go ")
                isFullScreenActive = false
            } else
            //everything invisible - setUpFullScreen() has been called
            {
                isFullScreenActive = true
                Timber.d("System bars are invisible - no need to do shit")
            }
        }
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
                hasPermissions =
                    (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                return
            }

            else -> {
            }
        }
    }
}
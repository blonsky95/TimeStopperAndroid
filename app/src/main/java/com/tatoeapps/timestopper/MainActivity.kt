package com.tatoeapps.timestopper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.transition.Slide
import android.transition.Transition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.exoplayer2.PlaybackParameters
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.tatoeapps.timestopper.R.id
import com.tatoeapps.timestopper.R.layout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber
import java.text.DecimalFormat
import kotlin.math.ceil


class MainActivity : AppCompatActivity(), ActionButtonsInterface, SpeedSliderInterface {

    lateinit var exoPlayer: SimpleExoPlayer
    lateinit var playerView: PlayerView
    lateinit var dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    private var hasVideo = false

    private var task: Runnable? = null
    private var speedFactor = 1.0f

    private val df = DecimalFormat("0.000")

    private var realTimeInSecs = 0f
    private var initialPositionInMillis: Long = 0
    private var isTiming = false
    private var lapsTimeStringDisplay = ""

    private var theTimeSpeedMatrixLog = arrayListOf<Pair<Long, Float>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        Timber.plant(Timber.DebugTree())

        checkPermissions()

        setUpFullScreen()

        setUpPlayer()

        setTouchListener()
    }

    override fun onPause() {
        if (exoPlayer != null && exoPlayer.isPlaying) {
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
        Timber.d("CONTROL - lapText being updated - reset:$resetCounter")

        if (resetCounter) {
            realTimeInSecs = 0f
            lapsTimeStringDisplay = df.format(realTimeInSecs)
        } else {
            val realElapsedTime =
                Utils.getRealTimeFromMatrixInSeconds(theTimeSpeedMatrixLog, initialPositionInMillis)
            lapsTimeStringDisplay += "\n${df.format(realElapsedTime)}"
            Timber.d("CONTROL - lapText updated - new time:$realElapsedTime")

        }
        timePointsDisplay.text = lapsTimeStringDisplay
    }

    private fun setUpPlayer() {
        exoPlayer = SimpleExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerStateListener)

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
        changeSpeed(newValue/100)
    }

    override fun importVideo() {
        intentPickMedia()
    }

    override fun startTiming() {
        isTiming = true
        updateInfoDisplay(isTiming)
        initialPositionInMillis = exoPlayer.currentPosition
        updateTheTimeSpeedMatrix(true)
        updateLapsText(true)
    }

    override fun lapTiming() {
        Timber.d("CONTROL - lap pressed - isTiming: $isTiming")
        Timber.d("TEST - player current position: ${exoPlayer.currentPosition}")
        if (isTiming) {
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


    private fun updateTheTimeSpeedMatrix(resetMatrix: Boolean = false) {
        Timber.d("CONTROL - matrix being updated - reset:$resetMatrix")

        if (resetMatrix) {
            theTimeSpeedMatrixLog.clear()
        } else {
            val pair = Pair(exoPlayer.currentPosition, speedFactor)
            theTimeSpeedMatrixLog.add(pair)
            Timber.d("CONTROL - matrix updated - time added: ${pair.first} - speed added: ${pair.second}")
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
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            val selectedMediaUri = data!!.data

            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(selectedMediaUri)
            exoPlayer.prepare(videoSource)

            val videoSkipDefaultMs = 5000
            custom_forward.setOnClickListener {
                exoPlayer.seekTo(exoPlayer.currentPosition+(videoSkipDefaultMs*speedFactor).toLong())
            }
            custom_rewind.setOnClickListener {
                exoPlayer.seekTo(exoPlayer.currentPosition-(videoSkipDefaultMs*speedFactor).toLong())
            }

            hasVideo = true
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private val playerStateListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    task = Runnable {
                        exoPlayer.playWhenReady = true
                    }
                }

                Player.STATE_ENDED -> {

                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {

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
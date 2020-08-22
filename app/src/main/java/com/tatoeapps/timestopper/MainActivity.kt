package com.tatoeapps.timestopper

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import timber.log.Timber
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    lateinit var exoPlayer: SimpleExoPlayer
    lateinit var playerView: PlayerView
    lateinit var upSpeed: Button
    lateinit var downSpeed: Button

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

        Timber.plant(Timber.DebugTree())

        checkPermissions()

        exoPlayer = SimpleExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerStateListener)


        playerView = findViewById(id.playerView)
        playerView.player = exoPlayer

        dataSourceFactory =
            DefaultDataSourceFactory(this, Util.getUserAgent(this, application.packageName))

        speedDisplay.text = speedFactor.toString()

        val selectVideo = findViewById<Button>(id.button1)
        upSpeed = findViewById<Button>(id.button2)
        downSpeed = findViewById<Button>(id.button3)

        selectVideo.setOnClickListener {
            intentPickMedia()
        }

        upSpeed.setOnClickListener {
            changeSpeed(+0.1F)
        }

        downSpeed.setOnClickListener {
            changeSpeed(-0.1F)
        }

        startTiming.setOnClickListener {
            //start timing
            Timber.d("CONTROL - start pressed")

            initialPositionInMillis = exoPlayer.currentPosition
            updateTheTimeSpeedMatrix(true)
            updateLapsText(true)
            isTiming = true
        }

        lapTiming.setOnClickListener {
            //lap
            Timber.d("CONTROL - lap pressed - isTiming: $isTiming")
            Timber.d("TEST - player current position: ${exoPlayer.currentPosition}")
            if (isTiming) {
                updateTheTimeSpeedMatrix()
                updateLapsText()
            }

        }

        stopTiming.setOnClickListener {
            //stop timing
            Timber.d("CONTROL - stop pressed - isTiming: $isTiming")

            if (isTiming) {
                updateTheTimeSpeedMatrix()
                updateLapsText()
                isTiming = false
            }
        }
    }

    private fun updateTheTimeSpeedMatrix(resetMatrix: Boolean = false) {
        Timber.d("CONTROL - matrix being updated - reset:$resetMatrix")

        //todo use current position in bar

        if (resetMatrix) {
            theTimeSpeedMatrixLog.clear()
        } else {
//            var timeSinceLastUpdate = initialTimeInMillis
//            if (theTimeSpeedMatrixLog.isNotEmpty()) {
//                timeSinceLastUpdate=theTimeSpeedMatrixLog[theTimeSpeedMatrixLog.size-1].first.toLong()
//            }
//            val currentSystemTime = System.currentTimeMillis()
            val pair = Pair(exoPlayer.currentPosition, speedFactor)
            theTimeSpeedMatrixLog.add(pair)
            Timber.d("CONTROL - matrix updated - time added: ${pair.first} - speed added: ${pair.second}")
        }
    }

    private fun updateLapsText(resetCounter: Boolean = false) {
        Timber.d("CONTROL - lapText being updated - reset:$resetCounter")

        if (resetCounter) {
            realTimeInSecs = 0f
            lapsTimeStringDisplay = df.format(realTimeInSecs)
        } else {
            val realElapsedTime = getRealTimeFromMatrixInSeconds()
            lapsTimeStringDisplay += "\n${df.format(realElapsedTime)}"
            Timber.d("CONTROL - lapText updated - new time:$realElapsedTime")

        }
        timePointsDisplay.text = lapsTimeStringDisplay
    }

    private fun getRealTimeFromMatrixInSeconds(): Double {
        var total = 0L
        var iTime = initialPositionInMillis
        for (pair in theTimeSpeedMatrixLog) {
            val fTime = pair.first
            val speedFactor = pair.second
            total += ((fTime - iTime)).toLong()
            iTime = pair.first
        }
        Timber.d("CONTROL - getting real time from matrix - total:$total")

        return total.toDouble() / 1000
    }

    private fun changeSpeed(deltaSpeed: Float) {
        if (isTiming) {
            updateTheTimeSpeedMatrix()
        }
        speedFactor += deltaSpeed
        val playbackParameters = PlaybackParameters(speedFactor)
        exoPlayer.setPlaybackParameters(playbackParameters)

        speedDisplay.text = df.format(speedFactor)
        //todo do slider for speed
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
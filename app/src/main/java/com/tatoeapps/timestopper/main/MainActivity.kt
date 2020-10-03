package com.tatoeapps.timestopper.main

import android.content.Intent
import android.content.pm.PackageManager
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
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.ui.PlayerView
import com.tatoeapps.timestopper.R
import com.tatoeapps.timestopper.R.id
import com.tatoeapps.timestopper.R.layout
import com.tatoeapps.timestopper.fragments.SpeedSliderFragment.Companion.defaultSpeedFactor
import com.tatoeapps.timestopper.fragments.ActionButtonsFragment
import com.tatoeapps.timestopper.fragments.SpeedSliderFragment
import com.tatoeapps.timestopper.fragments.StartFragment
import com.tatoeapps.timestopper.interfaces.ActionButtonsInterface
import com.tatoeapps.timestopper.interfaces.SpeedSliderInterface
import com.tatoeapps.timestopper.utils.TimeSplitsController
import com.tatoeapps.timestopper.utils.Utils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber
import java.lang.Runnable


class MainActivity : AppCompatActivity(),
    ActionButtonsInterface,
    SpeedSliderInterface {

    lateinit var exoPlayer: SimpleExoPlayer
    lateinit var playerView: PlayerView
    lateinit var dataSourceFactory: com.google.android.exoplayer2.upstream.DataSource.Factory

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    private var hasVideo = false
    private var isPlayingVideo = false

    private var task: Runnable? = null
    private var speedFactor = defaultSpeedFactor

    private var firstNextFrameSkip = true

    private var isFullScreenActive = false

    private var timeSplitsController =
        TimeSplitsController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        Timber.plant(Timber.DebugTree())

        checkPermissions()
        setUpSystemUiListener()

        if (savedInstanceState == null) {
            getStartFragment()
        }

        setUpPlayer()
        hideBuffering()
        setUserScreenTapListener()
    }

    /**
     * Speed slider interface
     */
    override fun setSpeed(newValue: Float) {
        changeSpeed(newValue / 100)
    }

    /**
     * Action buttons interface
     */

    override fun importVideo() {
        intentPickMedia()
    }

    override fun startTiming() {
        updateTimeInfoVisibility(true)
        updateLapsText(Utils.floatToStartString(timeSplitsController.startTiming(exoPlayer.currentPosition)),true)
    }

    override fun lapTiming() {
        if (timeSplitsController.isActive) {
            updateLapsText(Utils.pairFloatToLapString(timeSplitsController.doLap(exoPlayer.currentPosition)), false)
        }
    }

    override fun stopTiming() {
        if (timeSplitsController.isActive) {
            updateLapsText(Utils.pairFloatToLapString(timeSplitsController.stopTiming(exoPlayer.currentPosition)), false)
        }
    }

    override fun clearTiming() {
        updateTimeInfoVisibility(false)
        timeSplitsController =
            TimeSplitsController()
    }

    /**
     * BUTTON ACTIONS
     */

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
            //UI
            hideStartFragment()
            updateTimeInfoVisibility(false)

            prepareVideoSource(MediaItem.fromUri(data!!.data!!))
            configureExoPlayerButtons(data.data!!)

        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun changeSpeed(newSpeed: Float) {
        speedFactor = newSpeed
        val playbackParameters = PlaybackParameters(speedFactor)
        exoPlayer.setPlaybackParameters(playbackParameters)
    }

    /**
     * EXOPLAYER STUFF
     */

    private val playerStateListener = object : Player.EventListener {
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    task = Runnable {
                        exoPlayer.playWhenReady = true
                        firstNextFrameSkip = true
                    }
                }
                Player.STATE_ENDED -> {
                    showActionFragments(true)
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

    private fun setUpPlayer() {
        exoPlayer = Utils.getExoPlayerInstance(this)
        exoPlayer.addListener(playerStateListener)
        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        playerView = findViewById(id.playerView)
        playerView.player = exoPlayer

        dataSourceFactory = Utils.getDataSourceFactoryInstance(this, application)
    }

    private fun configureExoPlayerButtons(mediaUri: Uri) {
        val videoSkipDefaultMs = 5000
        val videoFrameRate = Utils.getFrameRateOfVideo(this, mediaUri)

        //reset Speed -> Speed slider frag
        (supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment).resetSpeed()

        custom_forward.setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition + (videoSkipDefaultMs * speedFactor).toLong())
        }
        custom_rewind.setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor).toLong())
        }

        next_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                //if first next frame skip is true, it needs frame correcting - see issue #18
                val newPosition= Utils.getPositionOfNextFrame(exoPlayer.currentPosition,videoFrameRate,firstNextFrameSkip)
                firstNextFrameSkip = false
                exoPlayer.seekTo(newPosition)
            }
        }
        previous_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                exoPlayer.seekTo(Utils.getPositionOfPreviousFrame(exoPlayer.currentPosition, videoFrameRate))
            }
        }

    }

    private fun prepareVideoSource(mediaItem: MediaItem) {
        val videoSource = Utils.getVideoSource(mediaItem, dataSourceFactory)

        exoPlayer.setMediaSource(videoSource)
        exoPlayer.prepare()
        hasVideo = true
    }

    /**
     * LIFECYCLE STUFF
     */

    override fun onPause() {

        if (exoPlayer.isPlaying) {
            exoPlayer.stop()
            exoPlayer.release()
        }
        super.onPause()
    }

    override fun onResume() {
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

    private fun updateLapsText(newText:String, isReset:Boolean) {
        if (isReset) {
            timePointsDisplay.text = newText
            return
        } else {
            var timePointsDisplayText = timePointsDisplay.text.toString()
            timePointsDisplayText+=newText
            timePointsDisplay.text=timePointsDisplayText
        }
    }

    private fun hideStartFragment() {
        supportFragmentManager.beginTransaction()
            .hide(supportFragmentManager.findFragmentById(id.start_fragment_container) as StartFragment)
            .commitAllowingStateLoss()
    }

    private fun updateTimeInfoVisibility(isVisible: Boolean) {
        if (isVisible) {
            timePointsDisplay.visibility = View.VISIBLE
        } else {
            timePointsDisplay.visibility = View.GONE
        }
    }

    private fun getStartFragment() {
        supportFragmentManager.beginTransaction()
            .add(id.start_fragment_container,
                StartFragment()
            ).commit()
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

    private fun setUpFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        isFullScreenActive = true
    }

    private fun setUpSystemUiListener() {
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            //if triggered because in file provider - no need to change anything because onresume will catch the change in the variable
            //user has dragged status bar making it visible, or setUpFullscreen has been called and its about to go invisible
            isFullScreenActive =
                visibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
        }
    }

    /**
     * PERMISSIONS
     */

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
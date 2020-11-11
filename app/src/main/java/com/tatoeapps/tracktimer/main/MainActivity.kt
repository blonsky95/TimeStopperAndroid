package com.tatoeapps.tracktimer.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.transition.Slide
import android.transition.Transition
import android.transition.TransitionManager
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.otaliastudios.zoom.ZoomSurfaceView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.R.id
import com.tatoeapps.tracktimer.R.layout
import com.tatoeapps.tracktimer.fragments.ActionButtonsFragment
import com.tatoeapps.tracktimer.fragments.GuideFragment
import com.tatoeapps.tracktimer.fragments.SpeedSliderFragment
import com.tatoeapps.tracktimer.fragments.SpeedSliderFragment.Companion.defaultSpeedFactor
import com.tatoeapps.tracktimer.fragments.StartFragment
import com.tatoeapps.tracktimer.interfaces.ActionButtonsInterface
import com.tatoeapps.tracktimer.interfaces.GuideInterface
import com.tatoeapps.tracktimer.interfaces.SpeedSliderInterface
import com.tatoeapps.tracktimer.utils.BillingClientLifecycle
import com.tatoeapps.tracktimer.utils.DialogsCreatorObject
import com.tatoeapps.tracktimer.utils.TimeSplitsController
import com.tatoeapps.tracktimer.utils.Utils
import com.tatoeapps.tracktimer.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import kotlinx.android.synthetic.main.fragment_guide.*
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity(),
    LifecycleOwner,
    ActionButtonsInterface,
    SpeedSliderInterface,
    GuideInterface {

    private lateinit var mDetector: GestureDetectorCompat

    lateinit var exoPlayer: SimpleExoPlayer
    private lateinit var dataSourceFactory: DataSource.Factory
    lateinit var mainViewModel: MainViewModel

    //class that does all the billing - google pay subscriptions and related
    private lateinit var billingClientLifecycle: BillingClientLifecycle

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    private var hasVideo = false
    private var isPlayingVideo = false

    private var task: Runnable? = null
    private var speedFactor = defaultSpeedFactor

    private var firstNextFrameSkip = true
    private var videoFrameRate:Float = 0F

    private var isFullScreenActive = false
    private var isOnboardingOn = false

    private var timeSplitsController: TimeSplitsController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billingClientLifecycle = BillingClientLifecycle.getInstance(application, this, lifecycle)
        billingClientLifecycle.create(true)

        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        if (Utils.isUserFirstTimer(this)) {
            isOnboardingOn = true
            startActivity(Intent(this, OnBoardingActivity::class.java))
        } else {
            isOnboardingOn = false
            setContentView(layout.activity_main)
            Timber.plant(Timber.DebugTree())

            checkPermissions()
            setUpSystemUiVisibilityListener()

            supportFragmentManager.beginTransaction()
                .hide(supportFragmentManager.findFragmentById(id.guide_frag) as GuideFragment)
                .commit()

            if (savedInstanceState == null) {
                getStartFragment()
            }
            setUpPlayer()
            hideBuffering()
            setUserScreenTapListener()
            addObservers()

//            video_info_btn.setOnClickListener {
//                toggleVideoInfoVisibility(video_info.visibility==View.GONE)
//            }
        }
    }


    /**
     * Purchase subscription
     */

    //dialog window which pops up while waiting for a connection to google pay, its
    // not local because to dismiss its called from functions in other scopes
    private var loadingAlertDialog: AlertDialog? = null
    private var subscribedAlertDialog: android.app.AlertDialog? = null
    private var unsubscribedAlertDialog: android.app.AlertDialog? = null

    private fun addObservers() {

        //when this value is triggered - a subscription query has been made and results are brought back
        billingClientLifecycle.skusWithSkuDetails.observe(
            this,
            androidx.lifecycle.Observer<Map<String, SkuDetails>> { list: Map<String, SkuDetails> ->
                //first, gets rid of the loading dialog window
                mainViewModel.isConnectingToGooglePlay.postValue(false)
                val skuDetails = list[BillingClientLifecycle.subscriptionSku]

                val dialogWindowInterface =
                    object : DialogsCreatorObject.DialogWindowInterface {
                        override fun onSubscribeClicked() {
                            if (skuDetails != null) {
                                val flowParams =
                                    BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build()
                                billingClientLifecycle.startLaunchBillingFlow(flowParams)
                            }
                        }
                    }

                unsubscribedAlertDialog =
                    DialogsCreatorObject.getUnsubscribedDialog(this, list, dialogWindowInterface)
                unsubscribedAlertDialog!!.show()
                subscribedAlertDialog?.dismiss()
            })

        billingClientLifecycle.subscriptionActive.observe(
            this,
            androidx.lifecycle.Observer<Boolean> { subscriptionActive ->

                //BUG FIX - avoid on resume resets from using the singleton
                //if variable is false, user has prompted dialog, else, just a background check so dont interact with UI
                if (!billingClientLifecycle.mCheckingSubscriptionState) {
                    mainViewModel.isConnectingToGooglePlay.postValue(false)

                    //subscription active is always true as of 03/11/2020
                    subscribedAlertDialog =
                        DialogsCreatorObject.getSubscribedDialog(this)
                    subscribedAlertDialog!!.show()
                    unsubscribedAlertDialog?.dismiss()
                }

            })

        billingClientLifecycle.billingClientConnectionState.observe(
            this,
            androidx.lifecycle.Observer<Int> { _ ->
                if (loadingAlertDialog != null && loadingAlertDialog!!.isShowing) {
                    loadingAlertDialog!!.findViewById<TextView>(id.loading_dialog_text)?.text =
                        getString(
                            R.string.internet_problem_text
                        )
                    loadingAlertDialog!!.findViewById<ProgressBar>(id.indeterminateBar)?.visibility =
                        View.GONE
                }
            }
        )

        //when this value is triggered - if true, it displays the loading (connecting to google pay) dialog, if false, it dismisses it
        //this dialog window is not cancelable, but has a CANCEL button, which will kill the google pay connection and remove the loading screen
        mainViewModel.isConnectingToGooglePlay.observe(
            this,
            androidx.lifecycle.Observer<Boolean> { isConnecting ->
                if (!isConnecting) {
                    loadingAlertDialog?.dismiss()
                } else {
                    val dialogWindowInterface =
                        object : DialogsCreatorObject.DialogWindowInterface {
                            override fun onCancelButton() {
                                billingClientLifecycle.destroy()
                                mainViewModel.isConnectingToGooglePlay.postValue(false)
                            }
                        }

                    loadingAlertDialog =
                        DialogsCreatorObject.getLoadingDialog(this, dialogWindowInterface)
                    loadingAlertDialog!!.show()
                }

            }
        )


    }

    private fun getSubscriptionDialog() {
        //trigger the loading window as
        mainViewModel.isConnectingToGooglePlay.postValue(true)
        //it starts the query for purchases, which when retrieved will hide the window and show the available products
        billingClientLifecycle.create()
    }


    /**
     * Trial stuff
     *
     * The trial atm works with a 1 time per day timing feature use. To make it work there is 3 shared prefs values
     * 1. The date of last time timing was used
     * 2. Count of videos used (in one day)
     * 3. If the trial is active (in case user closes app while using timing and reopens the app, the pref would store this and it wouldnt reset)
     *
     * When users picks media it triggers the check - if pref 3 isnt active then nothing, else, user quit the app while it was open,
     * in which case pref 2 is increased 1, and pref 1 too, lastly pref 3 is reset to non active
     */

    private fun updateFreeTrialInfo() {
        //if trial was being used update count and date in which it happened, and set the trial isActive variable to false
        if (Utils.getIsTimingTrialActive(this)) {
            Utils.updatePrefCountOfFreeTimingVideosInTrial(
                this,
                Utils.getPrefCountOfFreeTimingVideosInTrial(this)
            )
            Utils.updatePrefDayOfYearTrial(this, Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
            Utils.updateIsTimingTrialActive(this, false)
        }
    }

    /**
     * Guide interface
     */

    override fun hideGuideFragment() {
        //hide the fragment
        toggleFragmentsVisibility(
            false,
            supportFragmentManager.findFragmentById(id.guide_frag) as GuideFragment
        )
    }


    /**
     * Speed slider interface
     */
    override fun setSpeed(newValue: Float) {
        changeSpeed(newValue / 100)
    }

    /**
     * Action buttons interface && trial control logic
     *
     * In the startTimingFeature the logic to check the state of the free timing feature trial is shown
     */

    override fun importVideo() {
        if (hasPermissions) {
            intentPickMedia()
        } else {
            Toast.makeText(
                this,
                this.resources.getString(R.string.permission_toast),
                Toast.LENGTH_SHORT
            ).show()
            checkPermissions()
        }
    }


    override fun startTimingFeature() {
        if (Utils.isUserSubscribed(this) || Utils.getIsTimingTrialActive(this)) {
            //user has already started the trial, so its in the same video as the one he started it with or has not exceeded count
            startTiming()
        } else {
            if (Utils.canStartTimingTrial(this)) {

                //starts the trial - put dialog saying you are starting trial
                val dialogPositiveNegativeInterface =
                    object : DialogsCreatorObject.DialogWindowInterface {
                        override fun onPositiveButton() {
                            //its ok to use application context here because its a shared prefs operation - doesnt care much about activity lifecycle
                            Utils.updateIsTimingTrialActive(applicationContext, true)
                            startTiming()
                        }

                        override fun onNegativeButton() {
                            getSubscriptionDialog()
                        }
                    }

                val alertDialog: AlertDialog =
                    DialogsCreatorObject.getTrialStartDialog(this, dialogPositiveNegativeInterface)
                alertDialog.show()

            } else {

                //expired - put dialog saying you are expired - get premium or wait one day
                val dialogPositiveNegativeInterface =
                    object : DialogsCreatorObject.DialogWindowInterface {
                        override fun onPositiveButton() {
                            getSubscriptionDialog()
                        }

                        override fun onNegativeButton() {
                        }
                    }

                val alertDialog: AlertDialog = DialogsCreatorObject.getTrialExpiredDialog(
                    this,
                    dialogPositiveNegativeInterface
                )
                alertDialog.show()
            }

        }
    }

    fun startTiming() {
        toggleTimingContainerVisibility(true)
        updateLapsText(
            Utils.floatToStartString(timeSplitsController!!.startTiming(exoPlayer.currentPosition)),
            true
        )
//        updateVideoInfo()
    }

    override fun lapTiming() {
        if (timeSplitsController!=null && timeSplitsController!!.isActive) {
            updateLapsText(
                Utils.pairFloatToLapString(timeSplitsController!!.doLap(exoPlayer.currentPosition)),
                false
            )
        }
    }

    override fun stopTiming() {
        if (timeSplitsController!=null && timeSplitsController!!.isActive) {
            updateLapsText(
                Utils.pairFloatToLapString(timeSplitsController!!.stopTiming(exoPlayer.currentPosition)),
                false
            )
        }
    }

    override fun clearTiming() {
        toggleTimingContainerVisibility(false)
        timeSplitsController?.clearTiming()
//        timeSplitsController = TimeSplitsController()
    }

    override fun helpButtonPressed() {
        showGuideWindow()
    }

    override fun subPressed() {
        getSubscriptionDialog()
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

        //if user has succesfully changed clip, check if the trial was active to count that video as used
        updateFreeTrialInfo()
        //initialise the timesplits controller to reset data from previous video
        timeSplitsController = TimeSplitsController()

        if (resultCode == RESULT_OK) {
            //UI
            hideStartFragment()
            toggleTimingContainerVisibility(false)

            prepareVideoSource(MediaItem.fromUri(data!!.data!!))
            configureExoPlayerButtons(data.data!!)
//            updateVideoInfo()
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun changeSpeed(newSpeed: Float) {
        speedFactor = newSpeed
        val playbackParameters = PlaybackParameters(speedFactor)
        exoPlayer.setPlaybackParameters(playbackParameters)
    }

    private fun showGuideWindow() {
        toggleFragmentsVisibility(
            true,
            supportFragmentManager.findFragmentById(id.guide_frag) as GuideFragment,
            true
        )
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
        val playerControls = player_controls
        val surface = surface_view


        exoPlayer.addVideoListener(object : VideoListener {
            override fun onVideoSizeChanged(
                width: Int,
                height: Int,
                unappliedRotationDegrees: Int,
                pixelWidthHeightRatio: Float
            ) {
                surface.setContentSize(width.toFloat(), height.toFloat())
            }
        })
        surface.setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent))
        surface.addCallback(object : ZoomSurfaceView.Callback {
            override fun onZoomSurfaceCreated(view: ZoomSurfaceView) {
                exoPlayer.setVideoSurface(view.surface)
            }

            override fun onZoomSurfaceDestroyed(view: ZoomSurfaceView) {
            }
        })

        playerControls.player = exoPlayer
        playerControls.show()


        exoPlayer.addListener(playerStateListener)
        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        dataSourceFactory = Utils.getDataSourceFactoryInstance(this, application)
    }

    private fun configureExoPlayerButtons(mediaUri: Uri) {
        val videoSkipDefaultMs = 5000
        videoFrameRate = Utils.getFrameRateOfVideo(this, mediaUri)

        //reset Speed -> Speed slider frag
        (supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment).resetSpeed()

        custom_forward.setOnClickListener {
            exoPlayer.seekTo(exoPlayer.currentPosition + (videoSkipDefaultMs * speedFactor).toLong())
        }
        custom_rewind.setOnClickListener {
            val rewindPosition =
                if (exoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor) < 0) {
                    0L
                } else {
                    exoPlayer.currentPosition - (videoSkipDefaultMs * speedFactor).toLong()
                }
            exoPlayer.seekTo(rewindPosition)
        }

        next_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                //if first next frame skip is true, it needs frame correcting - see issue #18
                val newPosition = Utils.getPositionOfNextFrame(
                    exoPlayer.currentPosition,
                    videoFrameRate,
                    firstNextFrameSkip
                )
                firstNextFrameSkip = false
                exoPlayer.seekTo(newPosition)
            }
        }
        previous_frame_btn.setOnClickListener {
            if (!isPlayingVideo) {
                exoPlayer.seekTo(
                    Utils.getPositionOfPreviousFrame(
                        exoPlayer.currentPosition,
                        videoFrameRate
                    )
                )
            }
        }

    }

    private fun prepareVideoSource(mediaItem: MediaItem) {
        val videoSource = Utils.getVideoSource(mediaItem, dataSourceFactory)

        exoPlayer.setMediaSource(videoSource)
        exoPlayer.prepare()
        hasVideo = true
    }

    override fun onPause() {
        exoPlayer.playWhenReady = false
        super.onPause()
    }

    override fun onDestroy() {
        exoPlayer.stop()
        exoPlayer.release()
        super.onDestroy()
    }


    override fun onResume() {
        if (!isFullScreenActive) {
            setUpFullScreen()
        }
        super.onResume()
    }

    override fun onBackPressed() {
        if (areFragmentsInBackstack() || supportFragmentManager.findFragmentById(id.start_fragment_container)!!.isVisible) {
            super.onBackPressed()
        } else {
            val dialogBuilder = AlertDialog.Builder(this)
                .setMessage(this.resources.getString(R.string.dialog_quit))
                .setPositiveButton(
                    "Yes"
                ) { _, _ ->
                    exoPlayer.playWhenReady = false
                    toggleFragmentsVisibility(
                        true,
                        supportFragmentManager.findFragmentById(id.start_fragment_container) as StartFragment
                    )
                }
                .setNegativeButton("No", null)
                .setCancelable(true)
            dialogBuilder.show()
        }
    }

    /**
     * EVERYTHING UNDER HERE IS RELATED TO APP **UI** (OR SHOULD BE)
     */

    private fun areFragmentsInBackstack(): Boolean {
        return supportFragmentManager.backStackEntryCount > 0
    }

    private fun updateLapsText(newText: String, isReset: Boolean) {
        if (isReset) {
            timing_display.text = newText
            return
        } else {
            var timePointsDisplayText = timing_display.text.toString()
            timePointsDisplayText += newText
            timing_display.text = timePointsDisplayText
        }
    }

//    private fun updateVideoInfo() {
//        video_info.text=Utils.getVideoInfo(videoFrameRate)
//    }

    private fun hideStartFragment() {
        supportFragmentManager.beginTransaction()
            .hide(supportFragmentManager.findFragmentById(id.start_fragment_container) as StartFragment)
            .commitAllowingStateLoss()
    }

//    private fun toggleVideoInfoVisibility(isVisible: Boolean) {
//        if (isVisible) {
//            video_info.visibility = View.VISIBLE
//        } else {
//            video_info.visibility = View.GONE
//        }
//    }

    private fun toggleTimingContainerVisibility(isVisible: Boolean) {
        if (isVisible) {
            info_container.visibility = View.VISIBLE
        } else {
            info_container.visibility = View.GONE
        }
    }

    private fun getStartFragment() {
        supportFragmentManager.beginTransaction()
            .add(
                id.start_fragment_container,
                StartFragment()
            ).commit()
    }

    private fun setUserScreenTapListener() {
        mDetector = GestureDetectorCompat(this, MyGestureListener())
        surface_view.setOnTouchListener { _, p1 -> mDetector.onTouchEvent(p1) }
        surface_view.setMaxZoom(5f)
        Timber.d("GESTURE - max zoom: ${surface_view.getMaxZoom()}")

    }

    private fun showActionFragments(show: Boolean) {
        if (show) {
            player_controls.show()
        } else {
            player_controls.hide()
        }
        toggleFragmentsVisibility(
            show,
            supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment
        )
        toggleFragmentsVisibility(
            show,
            supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment
        )

        if (timeSplitsController!=null && !timeSplitsController!!.isCleared) {
            toggleInfoDisplay(info_container, show)
        }
    }

    private fun toggleInfoDisplay(view: View, show: Boolean) {
        val transition: Transition = Slide(Gravity.START)
        transition.duration = 200
        transition.addTarget(view)
        TransitionManager.beginDelayedTransition(parent_container, transition)
        view.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toggleFragmentsVisibility(
        show: Boolean,
        fragment: Fragment,
        addToBackstack: Boolean = false
    ) {

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
            if (addToBackstack) {
                fragTransaction.show(fragment).addToBackStack("guide_frag").commit()
            } else {
                fragTransaction.show(fragment).commit()
            }
        } else {
            fragTransaction.hide(fragment).commit()
        }
    }

    private fun hideBuffering() {
        val timeBar: DefaultTimeBar =
            player_controls.findViewById<View>(id.exo_progress) as DefaultTimeBar
        timeBar.setBufferedColor(0x33FFFFFF)
    }

    private fun setUpFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        isFullScreenActive = true
    }

    private fun setUpSystemUiVisibilityListener() {
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
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.READ_EXTERNAL_STORAGE
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


    inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            Timber.d("GESTURE - se ha tocado una vez: ")
            val show =
                (supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment).isHidden
            showActionFragments(show)

            if (!isFullScreenActive) {
                setUpFullScreen()
            }
            return true
        }
    }

}
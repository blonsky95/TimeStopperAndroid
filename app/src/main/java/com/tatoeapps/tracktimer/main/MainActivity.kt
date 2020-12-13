package com.tatoeapps.tracktimer.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.play.core.review.ReviewManagerFactory
import com.tatoeapps.tracktimer.BuildConfig
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
import com.tatoeapps.tracktimer.interfaces.TestInterface
import com.tatoeapps.tracktimer.utils.*
import com.tatoeapps.tracktimer.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity(),
    LifecycleOwner,
    ActionButtonsInterface,
    SpeedSliderInterface,
    GuideInterface {

    private lateinit var mDetector: GestureDetectorCompat

    private var exoPlayer: SimpleExoPlayer? = null
    private lateinit var dataSourceFactory: DataSource.Factory
    lateinit var mainViewModel: MainViewModel

    //class that does all the billing - google pay subscriptions and related
    private lateinit var billingClientLifecycle: BillingClientLifecycle

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    private var videoPlayerController = VideoPlayerController()

    private var hasMediaLoaded = false

    private var timeSplitsController: TimeSplitsController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        billingClientLifecycle = BillingClientLifecycle.getInstance(application, this, lifecycle)
        billingClientLifecycle.create(true)

        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        if (Utils.isUserFirstTimer(this)) {
            startActivity(Intent(this, OnBoardingActivity::class.java))
        } else {
            setContentView(layout.activity_main)

            checkPermissions()
            setUpSystemUiVisibilityListener()

            setUpPlayer()
            hideBuffering()
            setUserScreenTapListener()
            addObservers()

            if (savedInstanceState == null) {
                if (intent?.action != Intent.ACTION_VIEW) {
                    getStartFragment()
                } else {
                    loadVideoFromImplicitIntent(intent.data)
                }
            }

            promptAppRatingToUser()
        }
    }

    override fun onResume() {
        setUpFullScreen()
        //check if screen is black so first check if start fragment isn't visible + exoplayer is instanced (not onboarding) + exo has a media item (not on start fragment)
//        if (hasMediaLoaded && exoPlayer?.currentMediaItem == null) {
//            //todo see if this works to detect a bug - so far no
//            Toast.makeText(this, "IS THERE BLACK SCREEN?", Toast.LENGTH_SHORT).show()
//        }
        super.onResume()
    }

    /**
     * Prompting user to rate app
     */

    private fun promptAppRatingToUser() {
        if (Utils.shouldShowRatingPrompt(this, System.currentTimeMillis())) {

            val dialogWindowInterface =
                object : DialogsCreatorObject.DialogWindowInterface {
                    override fun onPositiveButton() {
                        showAppReviewToUser()
                        super.onPositiveButton()
                    }
                }

            val suggestRateAppDialog =
                DialogsCreatorObject.getRatingPromptDialog(this, dialogWindowInterface)
            suggestRateAppDialog.setCancelable(true)
            suggestRateAppDialog.show()
        }
    }

    private fun showAppReviewToUser() {
        val manager = ReviewManagerFactory.create(this)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { reviewRequest ->
            if (reviewRequest.isSuccessful) {
                val reviewInfo = reviewRequest.result
                val flow = manager.launchReviewFlow(this, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    Utils.updateHasUserReviewedApp(this, true)
                }
            }
        }
    }

    /**
     * From implicit intent
     */

    private fun loadVideoFromImplicitIntent(data: Uri?) {
        updateFreeTrialInfo()
        timeSplitsController = TimeSplitsController()
        hasMediaLoaded = true

        toggleTimingContainerVisibility(false)
        preparePlayerForNewVideo(data)
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
     * Speed slider interface
     */
    override fun setSpeed(newValue: Float) {
        videoPlayerController.setSpeed(newValue / 100)
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
            Utils.floatToStartString(timeSplitsController!!.startTiming(videoPlayerController.getCurrentPosition())),
            true
        )
    }

    override fun lapTiming() {
        if (timeSplitsController != null && timeSplitsController!!.isActive) {
            updateLapsText(
                Utils.pairFloatToLapString(timeSplitsController!!.doLap(videoPlayerController.getCurrentPosition())),
                false
            )
        }
    }

    override fun stopTiming() {
        if (timeSplitsController != null && timeSplitsController!!.isActive) {
            updateLapsText(
                Utils.pairFloatToLapString(timeSplitsController!!.stopTiming(videoPlayerController.getCurrentPosition())),
                false
            )
        }
    }

    override fun clearTiming() {
        toggleTimingContainerVisibility(false)
        timeSplitsController?.clearTiming()
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
        if (videoPlayerController.isPlaying) {
            videoPlayerController.stopPlaying()
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

            //if user has succesfully changed clip, check if the trial was active to count that video as used
            updateFreeTrialInfo()
            //initialise the timesplits controller to reset data from previous video
            timeSplitsController = TimeSplitsController()

            //UI
            hideStartFragment()
            toggleTimingContainerVisibility(false)

            preparePlayerForNewVideo(data!!.data!!)
            hasMediaLoaded = true
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showGuideWindow() {
        getGuideFragment()
    }

    /**
     * EXOPLAYER STUFF
     */

    private fun preparePlayerForNewVideo(data: Uri?) {
        prepareVideoSource(MediaItem.fromUri(data!!))
        configureExoPlayerButtons(data)
        resetPlaybackSpeed()
    }

    private fun setUpPlayer() {
        exoPlayer = mainViewModel.getExoPlayerInstance(this)
        dataSourceFactory = mainViewModel.getDataSourceFactoryInstance(this, application)

        mainViewModel.configurePlayerVideoListener(exoPlayer!!, surface_view)
        mainViewModel.configureVideoSurface(exoPlayer!!, surface_view, this)
        mainViewModel.configurePlayerControls(exoPlayer!!, player_controls)

        videoPlayerController.initialize(exoPlayer!!, dataSourceFactory,
            object : TestInterface {
                override fun mediaFinished() {
                    showActionFragments(true)
                }
            })
    }

    private fun resetPlaybackSpeed() {
        //not the best way of communicating, it shouldnt be through the view but through the viewmodel
        (supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment).resetSpeed()
    }

    private fun configureExoPlayerButtons(mediaUri: Uri) {
        videoPlayerController.configureExoPlayerButtonsActions(this, mediaUri)

        custom_forward.setOnClickListener {
            videoPlayerController.mediaPlayerCustomActions.goForward()
        }
        custom_rewind.setOnClickListener {
            videoPlayerController.mediaPlayerCustomActions.goRewind()
        }
        next_frame_btn.setOnClickListener {
            videoPlayerController.mediaPlayerCustomActions.goNextFrame()
        }
        previous_frame_btn.setOnClickListener {
            videoPlayerController.mediaPlayerCustomActions.goPreviousFrame()
        }
    }

    private fun prepareVideoSource(mediaItem: MediaItem) {
        videoPlayerController.prepareVideoSource(mediaItem)
    }

    override fun onPause() {
        videoPlayerController.stopPlaying()
        super.onPause()
    }

    override fun onDestroy() {
        videoPlayerController.stopAndRelease()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (intent?.action == Intent.ACTION_VIEW || areFragmentsInBackstack() || supportFragmentManager.findFragmentById(
                id.full_screen_container
            )!!.isVisible
        ) {
            super.onBackPressed()
        } else {
            val dialogBuilder = AlertDialog.Builder(this)
                .setMessage(this.resources.getString(R.string.dialog_quit))
                .setPositiveButton(
                    "Yes"
                ) { _, _ ->
                    videoPlayerController.stopPlaying()
                    hasMediaLoaded = false
                    toggleFragmentsVisibility(
                        true,
                        supportFragmentManager.findFragmentById(id.full_screen_container) as StartFragment
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

    private fun hideStartFragment() {
        if (intent?.action != Intent.ACTION_VIEW) {
            supportFragmentManager.beginTransaction()
                .hide(supportFragmentManager.findFragmentById(id.full_screen_container) as StartFragment)
                .commitAllowingStateLoss()
        }
    }

    private fun getStartFragment() {
        supportFragmentManager.beginTransaction()
            .add(
                id.full_screen_container,
                StartFragment()
            ).commit()
    }

    private fun getGuideFragment() {
        val fragTransaction = supportFragmentManager.beginTransaction()

        fragTransaction.setCustomAnimations(
            R.anim.slide_right_to_left,
            R.anim.slide_left_to_right,
            R.anim.slide_right_to_left,
            R.anim.slide_left_to_right
        )

        fragTransaction.add(
            id.full_screen_container,
            GuideFragment()
        ).addToBackStack("guide_frag").commit()
    }


    //  Guide Fragment interface
    override fun hideGuideFragment() {
        super.onBackPressed()
    }

    private fun toggleTimingContainerVisibility(isVisible: Boolean) {
        if (isVisible) {
            info_container.visibility = View.VISIBLE
        } else {
            info_container.visibility = View.GONE
        }
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

        if (timeSplitsController != null && !timeSplitsController!!.isCleared) {
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
        mainViewModel.setUpFullScreen(window.decorView)
    }

    private fun setUpSystemUiVisibilityListener() {
        mainViewModel.setUpSystemUIVisibilityListener(window.decorView)
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
            val show =
                (supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment).isHidden
            showActionFragments(show)
            return true
        }
    }

}
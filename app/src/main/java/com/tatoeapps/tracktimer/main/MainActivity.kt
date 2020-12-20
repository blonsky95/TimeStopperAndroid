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
import android.view.Gravity
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.google.android.exoplayer2.ui.DefaultTimeBar
import com.tatoeapps.tracktimer.BuildConfig
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.R.id
import com.tatoeapps.tracktimer.R.layout
import com.tatoeapps.tracktimer.fragments.ActionButtonsFragment
import com.tatoeapps.tracktimer.fragments.GuideFragment
import com.tatoeapps.tracktimer.fragments.SpeedSliderFragment
import com.tatoeapps.tracktimer.fragments.StartFragment
import com.tatoeapps.tracktimer.interfaces.ActionButtonsInterface
import com.tatoeapps.tracktimer.interfaces.GuideInterface
import com.tatoeapps.tracktimer.interfaces.MediaPlayerCustomActions
import com.tatoeapps.tracktimer.interfaces.SpeedSliderInterface
import com.tatoeapps.tracktimer.utils.*
import com.tatoeapps.tracktimer.viewmodel.MainViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.exo_player_control_view.*
import timber.log.Timber


class MainActivity : AppCompatActivity(),
    LifecycleOwner,
    ActionButtonsInterface,
    SpeedSliderInterface,
    GuideInterface {

    lateinit var mainViewModel: MainViewModel

    //class that does all the billing - google pay subscriptions and related
    private lateinit var billingClientLifecycle: BillingClientLifecycle

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        //todo check this out too
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

            setUserGestureListener()
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
        mainViewModel.promptAppRatingToUser(this)
    }

    private fun showAppReviewToUser() {
        mainViewModel.showAppReviewToUser(this)
    }

    /**
     * From implicit intent
     */

    private fun loadVideoFromImplicitIntent(data: Uri?) {
        mainViewModel.prepareForNewVideo(this, data)
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
        mainViewModel.fragmentsVisibilityAction.observe(
            this,
            androidx.lifecycle.Observer { action ->
                when (action) {
                    Utils.VISIBILITY_TOGGLE -> showActionFragments(!areFragmentPanelsVisible())
                    Utils.VISIBILITY_SHOW -> showActionFragments(true)
                    Utils.VISIBILITY_HIDE -> showActionFragments(false)
                }
            })

        mainViewModel.timingVisibilityAction.observe(
            this,
            androidx.lifecycle.Observer { action ->
                when (action) {
                    Utils.VISIBILITY_TOGGLE -> changeVisibilityTimingContainer(!areFragmentPanelsVisible())
                    Utils.VISIBILITY_SHOW -> changeVisibilityTimingContainer(true)
                    Utils.VISIBILITY_HIDE -> changeVisibilityTimingContainer(false)
                }
            })

        mainViewModel.promptAppRatingToUser.observe(
            this,
            androidx.lifecycle.Observer { promptRating ->
                if (promptRating) {
                    showAppReviewToUser()
                }
            })

        mainViewModel.timingDisplayText.observe(
            this,
            androidx.lifecycle.Observer { newTimingDisplayText ->
                if (newTimingDisplayText.isNotEmpty()) {
                    timing_display.text = newTimingDisplayText
                }
            })

        mainViewModel.customPlayerControlActions.observe(
            this,
            androidx.lifecycle.Observer { playerControlsInterface ->
                if (playerControlsInterface != null) {
                    setPlayerControlsInterface(playerControlsInterface)
                }
            })

        mainViewModel.showSubscriptionDialog.observe(
            this,
            androidx.lifecycle.Observer { showSubscriptionDialog ->
                if (showSubscriptionDialog) {
                    getSubscriptionDialog()
                }
            })

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


    }


    private fun getSubscriptionDialog() {
        //trigger the loading window as
        mainViewModel.isConnectingToGooglePlay.postValue(true)
        //it starts the query for purchases, which when retrieved will hide the window and show the available products
        billingClientLifecycle.create()
    }

    /**
     * Speed slider interface
     */
    override fun setSpeed(newValue: Float) {
        mainViewModel.setVideoSpeed(newValue)
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

    override fun startTiming() {
        //checks if timing feature is available, and then also calls startTiming()
        mainViewModel.checkIfCanStartTiming(this)
    }

    override fun lapTiming() {
        mainViewModel.lapTiming()
    }

    override fun stopTiming() {
        mainViewModel.stopTiming()
    }

    override fun clearTiming() {
        mainViewModel.clearTiming()
    }

    override fun helpButtonPressed() {
        showGuideWindow()
    }

    override fun subscriptionButtonPressed() {
        getSubscriptionDialog()
    }

    /**
     * BUTTON ACTIONS
     */

    private fun intentPickMedia() {
        mainViewModel.stopPlayingVideo()

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
            mainViewModel.prepareForNewVideo(this, data!!.data!!)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun showGuideWindow() {
        getGuideFragment()
    }

    /**
     * EXOPLAYER STUFF
     */

    private fun setUpPlayer() {
        mainViewModel.setUpPlayer(this, surface_view, player_controls)
    }

    private fun setPlayerControlsInterface(playerControlsInterface: MediaPlayerCustomActions) {
        custom_forward.setOnClickListener {
            playerControlsInterface.goForward()
        }
        custom_rewind.setOnClickListener {
            playerControlsInterface.goRewind()
        }
        next_frame_btn.setOnClickListener {
            playerControlsInterface.goNextFrame()
        }
        previous_frame_btn.setOnClickListener {
            playerControlsInterface.goPreviousFrame()
        }

    }

    override fun onPause() {
        mainViewModel.stopPlayingVideo()
        super.onPause()
    }

    override fun onDestroy() {
        mainViewModel.stopPlayingAndRelease()
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
                    mainViewModel.stopPlayingVideo()
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

    private fun areFragmentPanelsVisible(): Boolean {
        return (supportFragmentManager.findFragmentById(R.id.actionBtns_frag) as ActionButtonsFragment).isVisible
    }

    private fun areFragmentsInBackstack(): Boolean {
        return supportFragmentManager.backStackEntryCount > 0
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

    private fun setUserGestureListener() {
        mainViewModel.setUserGestureListener(surface_view, this)
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
    }

    private fun changeVisibilityTimingContainer(show: Boolean) {
        val transition: Transition = Slide(Gravity.START)
        transition.duration = 200
        transition.addTarget(info_container)
        TransitionManager.beginDelayedTransition(parent_container, transition)
        info_container.visibility = if (show) View.VISIBLE else View.GONE
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
}
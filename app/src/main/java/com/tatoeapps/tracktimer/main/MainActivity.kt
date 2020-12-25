package com.tatoeapps.tracktimer.main

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.transition.Transition
import android.transition.TransitionManager
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
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
import com.tatoeapps.tracktimer.utils.Utils
import com.tatoeapps.tracktimer.utils.Utils.getHorizontalFragmentTransition
import com.tatoeapps.tracktimer.utils.Utils.getNoAnimationTransition
import com.tatoeapps.tracktimer.utils.Utils.getVerticalFragmentTransition
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

    private var alertDialog: AlertDialog? = null

    private var hasPermissions = false
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        mainViewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        mainViewModel.startBillingClientLifecycle(application, this, lifecycle)

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

            askUserIfWantToRateApp()
        }
    }

    override fun onResume() {
        setUpFullScreen()
        super.onResume()
    }

    /**
     * Prompting user to rate app
     */

    private fun askUserIfWantToRateApp() {
        mainViewModel.askUserIfWantToRateApp(this)
    }

    private fun letUserRateApp() {
        mainViewModel.letUserRateApp(this)
    }

    /**
     * From implicit intent
     */

    private fun loadVideoFromImplicitIntent(data: Uri?) {
        mainViewModel.prepareForNewVideo(this, data)
    }

    /**
     * Observers
     */
    private fun addObservers() {
        mainViewModel.fragmentsVisibilityAction.observe(
            this,
            androidx.lifecycle.Observer { action ->
                when (action) {
                    Utils.VISIBILITY_TOGGLE -> changeVisibilityOfActionFragments(!areFragmentPanelsVisible())
                    Utils.VISIBILITY_SHOW -> changeVisibilityOfActionFragments(true)
                    Utils.VISIBILITY_HIDE -> changeVisibilityOfActionFragments(false)
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

        mainViewModel.getStartFragment.observe(
            this,
            Observer { getStartFragment ->
                if (getStartFragment) {
                    getStartFragment(true)
                }
            }
        )

        mainViewModel.getGuideFragment.observe(
            this,
            Observer { getGuideFragment ->
                if (getGuideFragment) {
                    getGuideFragment()
                }
            }
        )

        mainViewModel.userWantsToRate.observe(
            this,
            androidx.lifecycle.Observer { userWantsToRate ->
                if (userWantsToRate) {
                    letUserRateApp()
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

        /**
         *  BILLING CLIENT LIFECYCLE - related stuff
         */

        //triggered when an alert dialog is to be displayed, only ONE can be displayed at any time:
        // 1 - dialog with loading animation - connecting to google play
        // 2 - dialog that shows subscription plan + subscribe button
        // 3 - dialog that says "you are already subscribed"
        // 4 - dialog that says "there is an error connecting"
        mainViewModel.alertDialog.observe(this, Observer { alertDialog ->
            //clear existent dialogs
            this.alertDialog?.dismiss()
            if (alertDialog != null) {
                this.alertDialog = alertDialog
                this.alertDialog?.show()
            }
        })

        /**
         * the following live data is not in VIEW MODEL but in BILLING CLIENT LIFECYCLE class
         */

        //dialog 1 appears when BCL is working on the background

        //BCL is saying the subscription info is ready to be presented - dialog 2
        mainViewModel.billingClientLifecycle.availableSubscriptions.observe(
            this,
            androidx.lifecycle.Observer<Map<String, SkuDetails>> { list: Map<String, SkuDetails> ->
                mainViewModel.showAvailableSubscriptions(list, this)
            })

        //BCL is saying the user just or is already subscribed - dialog 3
        mainViewModel.billingClientLifecycle.subscriptionActive.observe(
            this,
            androidx.lifecycle.Observer<Boolean> { subscriptionActive ->
                if (subscriptionActive) {
                    mainViewModel.showSubscribedDialog(this)
                }
            })

        //BCL is saying there has been an error connecting or elsewhere - dialog 4
        mainViewModel.billingClientLifecycle.billingClientConnectionState.observe(
            this,
            androidx.lifecycle.Observer<Int> { _ ->
                mainViewModel.showErrorAlertDialog(this)
            }
        )


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
        mainViewModel.getGuideFragment()
    }

    override fun subscriptionButtonPressed() {
        //this starts the code to get the subscription dialogs
        mainViewModel.checkForSubscriptions(this)
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

    /**
     * ACTIVITY LIFECYCLE STUFF
     */

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
        )
        //if app was open through an intent, or there is fragment on backstack, or startfragment (full screen container) then do normal back
        {
            super.onBackPressed()
        } else
        //trigger confirm dialog for user to quit a video
        {
            mainViewModel.getConfirmQuitVideoDialog(this)

        }
    }

    /**
     * EVERYTHING UNDER HERE IS RELATED TO APP **UI** (OR SHOULD BE)
     */

    //user tapping screen toggles the visibility of the button panels, so function used to know visibility state
    private fun areFragmentPanelsVisible(): Boolean {
        return (supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment).isVisible
    }

    //When fragments are in backstack, back button works normally, else triggers dialog to confirm quit
    private fun areFragmentsInBackstack(): Boolean {
        return supportFragmentManager.backStackEntryCount > 0
    }

    //next are all fragment hiding and showing logic - do paper scheme first - then code for it

    private fun hideStartFragment() {
        if (intent?.action != Intent.ACTION_VIEW) {
            supportFragmentManager.beginTransaction()
                .hide(supportFragmentManager.findFragmentById(id.full_screen_container) as StartFragment)
                .commitAllowingStateLoss()
        }
    }

    private fun getStartFragment(addAnimation: Boolean = false) {
        val fragmentTransaction =
            if (addAnimation) {
                getVerticalFragmentTransition(supportFragmentManager)
            } else {
                getNoAnimationTransition(supportFragmentManager)
            }

        fragmentTransaction.add(
            id.full_screen_container,
            StartFragment()
        ).commit()
    }


    private fun getGuideFragment() {
        val fragmentTransaction = getHorizontalFragmentTransition(supportFragmentManager)

        //this transaction adds to backstack because pressing back makes the entry animation inverse to exit
        fragmentTransaction.add(
            id.full_screen_container,
            GuideFragment()
        ).addToBackStack("guide_frag").commit()
    }


    //  Guide Fragment interface
    override fun hideGuideFragment() {
        super.onBackPressed()
    }

    private fun changeVisibilityOfActionFragments(show: Boolean) {
        if (show) {
            player_controls.show()
        } else {
            player_controls.hide()
        }

        val verticalFragTransition = getVerticalFragmentTransition(supportFragmentManager)
        val horizontalFragTransition = getHorizontalFragmentTransition(supportFragmentManager)

        performFragmentVisibilityAction(
            supportFragmentManager.findFragmentById(id.actionBtns_frag) as ActionButtonsFragment,
            horizontalFragTransition,
            show
        )
        performFragmentVisibilityAction(
            supportFragmentManager.findFragmentById(id.speedSlider_frag) as SpeedSliderFragment,
            verticalFragTransition,
            show
        )
    }

    private fun performFragmentVisibilityAction(
        fragment: Fragment,
        fragmentTransaction: FragmentTransaction,
        show: Boolean
    ) {
        if (show) {
            fragmentTransaction.show(fragment).commit()
        } else {
            fragmentTransaction.hide(fragment).commit()
        }
    }


    private fun changeVisibilityTimingContainer(show: Boolean) {
        val transition: Transition = Utils.getSlideTransition(info_container)
        TransitionManager.beginDelayedTransition(parent_container, transition)
        info_container.visibility = if (show) View.VISIBLE else View.GONE
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

    private fun setUserGestureListener() {
        mainViewModel.setUserGestureListener(surface_view, this)
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
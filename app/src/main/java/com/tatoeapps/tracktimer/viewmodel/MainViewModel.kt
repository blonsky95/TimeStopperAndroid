package com.tatoeapps.tracktimer.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.google.android.play.core.review.ReviewManagerFactory
import com.otaliastudios.zoom.ZoomSurfaceView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.interfaces.MediaPlayerCustomActions
import com.tatoeapps.tracktimer.interfaces.TestInterface
import com.tatoeapps.tracktimer.main.MainActivity
import com.tatoeapps.tracktimer.utils.DialogsCreatorObject
import com.tatoeapps.tracktimer.utils.TimeSplitsController
import com.tatoeapps.tracktimer.utils.Utils
import com.tatoeapps.tracktimer.utils.VideoPlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {


    /**
     * This is the job for all coroutines started by this ViewModel.
     * Cancelling this job will cancel all coroutines started by this ViewModel.
     */
    private val viewModelJob = SupervisorJob()

    /**
     * This is the main scope for all coroutines launched by MainViewModel.
     * Since we pass viewModelJob, you can cancel all coroutines
     * launched by uiScope by calling viewModelJob.cancel()
     */
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    /**
     * Cancel all coroutines when the ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }


    val isConnectingToGooglePlay = MutableLiveData<Boolean>(false)
    val fragmentsVisibilityAction = MutableLiveData(Utils.VISIBILITY_DO_NOTHING)
    val timingVisibilityAction = MutableLiveData(Utils.VISIBILITY_DO_NOTHING)
    val timingDisplayText = MutableLiveData<String>("")
    val promptAppRatingToUser = MutableLiveData<Boolean>(false)
    val customPlayerControlActions = MutableLiveData<MediaPlayerCustomActions?>(null)
    val speedReset = MutableLiveData<Boolean>(false)
    val showSubscriptionDialog = MutableLiveData<Boolean>(false)

    private var videoPlayerController = VideoPlayerController()
    private var timeSplitsController: TimeSplitsController? = null

    /**
    Register a listener in case there is a system UI visibility change like a notification or user tapping the support/action bar
     */
    fun setUpSystemUIVisibilityListener(decorView: View) {
        decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            //basically, if a system component becomes visible, it will restore the immersive sticky state

            //this condition checks the visibility, if ==0 then something is visible - from developer docs
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                setUpFullScreen(decorView)
            }
        }
    }

    /**
    Sets up fullscreen to a given View - window.decorView is always passed as a parameter
     */
    fun setUpFullScreen(decorView: View) {
        decorView.systemUiVisibility =
                //this one does immersive mode
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    //These hide nav and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }


    /**
    Configure exoplayer - video listener
     */


    private fun getExoPlayerInstance(context: Context): SimpleExoPlayer? {
        val exoPlayer = Utils.getExoPlayerInstance(context)
        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        return exoPlayer
    }

    private fun getDataSourceFactoryInstance(
        context: Context,
        application: Application
    ): DataSource.Factory {
        return Utils.getDataSourceFactoryInstance(context, application)
    }

    private fun configurePlayerVideoListener(exoPlayer: SimpleExoPlayer, surface: ZoomSurfaceView) {
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

    }

    /**
    Configure exoplayer surface for the zoomable surface component
     */
    private fun configureVideoSurface(
        exoPlayer: SimpleExoPlayer,
        surface: ZoomSurfaceView,
        context: Context
    ) {

        surface.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent))
        surface.setMaxZoom(5f)

        surface.addCallback(object : ZoomSurfaceView.Callback {
            override fun onZoomSurfaceCreated(view: ZoomSurfaceView) {
                exoPlayer.setVideoSurface(view.surface)
            }

            override fun onZoomSurfaceDestroyed(view: ZoomSurfaceView) {
            }
        })
    }

    private fun configurePlayerControls(
        exoPlayer: SimpleExoPlayer,
        playerControls: PlayerControlView
    ) {
        playerControls.player = exoPlayer
        playerControls.show()
    }

    /**
     * USER GESTURE DETECTOR STUFF
     */

    fun setUserGestureListener(surfaceView: ZoomSurfaceView, context: Context) {
        val mDetector = GestureDetectorCompat(context, MyGestureListener())
        surfaceView.setOnTouchListener { _, p1 -> mDetector.onTouchEvent(p1) }
    }


    inner class MyGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent?): Boolean {
            fragmentsVisibilityAction.postValue(Utils.VISIBILITY_TOGGLE)
            if (timeSplitsController != null && !timeSplitsController!!.isCleared) {
                timingVisibilityAction.postValue(Utils.VISIBILITY_TOGGLE)
            }
            return true
        }
    }

    /**
     * PROMPT USER APP RATING
     */
    fun promptAppRatingToUser(mainActivity: MainActivity) {
        if (Utils.shouldShowRatingPrompt(mainActivity, System.currentTimeMillis())) {
            val dialogWindowInterface =
                object : DialogsCreatorObject.DialogWindowInterface {
                    override fun onPositiveButton() {
                        promptAppRatingToUser.postValue(true)
                        super.onPositiveButton()
                    }
                }

            val suggestRateAppDialog =
                DialogsCreatorObject.getRatingPromptDialog(mainActivity, dialogWindowInterface)
            suggestRateAppDialog.setCancelable(true)
            suggestRateAppDialog.show()
        }
    }

    fun showAppReviewToUser(mainActivity: MainActivity) {
        val manager = ReviewManagerFactory.create(mainActivity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { reviewRequest ->
            if (reviewRequest.isSuccessful) {
                val reviewInfo = reviewRequest.result
                val flow = manager.launchReviewFlow(mainActivity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    Utils.updateHasUserReviewedApp(mainActivity, true)
                }
            }
        }
    }

    /**
     * VIDEO PLAYER
     */
    fun setUpPlayer(
        context: Context,
        surfaceView: ZoomSurfaceView?,
        playerControls: PlayerControlView?
    ) {
        val exoPlayer = getExoPlayerInstance(context)
        val dataSourceFactory = getDataSourceFactoryInstance(context, getApplication())

        configurePlayerVideoListener(exoPlayer!!, surfaceView!!)
        configureVideoSurface(exoPlayer, surfaceView, context)
        configurePlayerControls(exoPlayer, playerControls!!)

        videoPlayerController.initialize(
            exoPlayer, dataSourceFactory,
            object : TestInterface {
                override fun mediaFinished() {
                    fragmentsVisibilityAction.postValue(Utils.VISIBILITY_SHOW)
                }
            })

    }

    fun setVideoSpeed(newValue: Float) {
        videoPlayerController.setSpeed(newValue / 100)
    }

    fun stopPlayingVideo() {
        if (videoPlayerController.isPlaying) {
            videoPlayerController.stopPlaying()
        }
    }

    fun stopPlayingAndRelease() {
        videoPlayerController.stopAndRelease()
    }

    fun prepareForNewVideo(context: Context, data: Uri?) {
        videoPlayerController.prepareVideoSource(MediaItem.fromUri(data!!))
        videoPlayerController.configureExoPlayerButtonsActions(context, data)
        updateFreeDailyTiming(context)

        customPlayerControlActions.postValue(videoPlayerController.mediaPlayerCustomActions)
        timingVisibilityAction.postValue(Utils.VISIBILITY_HIDE)
        speedReset.postValue(true)
        clearTiming()
    }

    /**
     * TIMING THINGS
     */
    fun startTiming() {
        timingVisibilityAction.postValue(Utils.VISIBILITY_SHOW)
        timeSplitsController = TimeSplitsController()
        timeSplitsController?.startTiming(videoPlayerController.getCurrentPosition())
        timingDisplayText.postValue(timeSplitsController?.getTimingDisplayText())
    }

    fun lapTiming() {
        if (timeSplitsController != null && timeSplitsController!!.isActive) {
            timeSplitsController?.doLap(videoPlayerController.getCurrentPosition())
            timingDisplayText.postValue(timeSplitsController?.getTimingDisplayText())
        }
    }

    fun stopTiming() {
        if (timeSplitsController != null && timeSplitsController!!.isActive) {
            timeSplitsController?.stopTiming(videoPlayerController.getCurrentPosition())
            timingDisplayText.postValue(timeSplitsController?.getTimingDisplayText())
        }
    }

    fun clearTiming() {
        timingVisibilityAction.postValue(Utils.VISIBILITY_HIDE)
        timeSplitsController?.clearTiming()
    }

    /**
     * TIMING THINGS - TRIAL - 1 PER DAY
     *
     * The trial atm works with a 1 time per day timing feature use. To make it work there is 3 shared prefs values
     * 1. The date of last time timing was used
     * 2. Count of videos used (in one day)
     * 3. If the trial is active (in case user closes app while using timing and reopens the app, the pref would store this and it wouldnt reset)
     *
     * When users picks media it triggers the check - if pref 3 isnt active then nothing, else, user quit the app while it was open,
     * in which case pref 2 is increased 1, and pref 1 too, lastly pref 3 is reset to non active
     */

    private fun updateFreeDailyTiming(context: Context) {
        if (Utils.getIsTimingFreeActive(context)) {
            Utils.addOneToCountOfFreeDailyTiming(
                context,
                Utils.getCountOfFreeDailyTiming(context)
            )
            Utils.updatePrefDayOfYearLastTiming(
                context,
                Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            )
            Utils.updateIsTimingFreeActive(context, false)
        }
    }

    fun checkIfCanStartTiming(context: Context) {
        if (Utils.isUserSubscribed(context) || Utils.getIsTimingFreeActive(context)) {
            //user has already started the trial, so its in the same video as the one he started it with or has not exceeded count
            startTiming()
        } else {
            if (Utils.canStartTimingTrial(context)) {

                //starts the trial - put dialog saying you are starting trial
                val dialogPositiveNegativeInterface =
                    object : DialogsCreatorObject.DialogWindowInterface {
                        override fun onPositiveButton() {
                            //its ok to use application context here because its a shared prefs operation - doesnt care much about activity lifecycle
                            Utils.updateIsTimingFreeActive(context, true)
                            startTiming()
                        }

                        override fun onNegativeButton() {
                            showSubscriptionDialog.postValue(true)
                        }
                    }

                val alertDialog: AlertDialog =
                    DialogsCreatorObject.getTrialStartDialog(context, dialogPositiveNegativeInterface)
                alertDialog.show()

            } else {

                //expired - put dialog saying you are expired - get premium or wait one day
                val dialogPositiveNegativeInterface =
                    object : DialogsCreatorObject.DialogWindowInterface {
                        override fun onPositiveButton() {
                            showSubscriptionDialog.postValue(true)
                        }

                        override fun onNegativeButton() {
                        }
                    }

                val alertDialog: AlertDialog = DialogsCreatorObject.getTrialExpiredDialog(
                    context,
                    dialogPositiveNegativeInterface
                )
                alertDialog.show()
            }

        }
    }


}
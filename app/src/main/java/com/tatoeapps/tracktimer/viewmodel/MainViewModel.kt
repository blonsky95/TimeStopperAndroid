package com.tatoeapps.tracktimer.viewmodel

import android.app.Application
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.google.android.play.core.review.ReviewManagerFactory
import com.otaliastudios.zoom.ZoomSurfaceView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.main.MainActivity
import com.tatoeapps.tracktimer.utils.DialogsCreatorObject
import com.tatoeapps.tracktimer.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainViewModel() : ViewModel() {

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
    val toggleFragmentsVisibility = MutableLiveData<Boolean>(false)
    val promptAppRatingToUser = MutableLiveData<Boolean>(false)

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
     ********************************************************************************************************************************
     * TO BE EXPORTED TO A VIDEO PLAYER CONFIGER
     ********************************************************************************************************************************
     *  */
    /**
    Configure exoplayer - video listener
     */


    fun getExoPlayerInstance(context: Context): SimpleExoPlayer? {
        val exoPlayer = Utils.getExoPlayerInstance(context)
        exoPlayer.setSeekParameters(SeekParameters.EXACT) //this is the default anyway

        return exoPlayer
    }

    fun getDataSourceFactoryInstance(
        context: Context,
        application: Application
    ): DataSource.Factory {
        return Utils.getDataSourceFactoryInstance(context, application)
    }

    fun configurePlayerVideoListener(exoPlayer: SimpleExoPlayer, surface: ZoomSurfaceView) {
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
    fun configureVideoSurface(
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

    fun configurePlayerControls(exoPlayer: SimpleExoPlayer, playerControls: PlayerControlView) {
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
            toggleFragmentsVisibility.postValue(true)
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


}
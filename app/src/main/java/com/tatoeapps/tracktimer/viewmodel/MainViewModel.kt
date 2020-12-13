package com.tatoeapps.tracktimer.viewmodel

import android.app.Application
import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SeekParameters
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.video.VideoListener
import com.otaliastudios.zoom.ZoomSurfaceView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.fragments.SpeedSliderFragment
import com.tatoeapps.tracktimer.utils.Utils
import com.tatoeapps.tracktimer.utils.VideoPlayerController
import kotlinx.android.synthetic.main.activity_main.*
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

    fun getDataSourceFactoryInstance(context: Context, application: Application): DataSource.Factory {
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
    fun configureVideoSurface(exoPlayer: SimpleExoPlayer, surface: ZoomSurfaceView, context: Context) {

        surface.setBackgroundColor(ContextCompat.getColor(context, R.color.colorAccent))

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




}
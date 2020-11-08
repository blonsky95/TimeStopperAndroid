package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerFullScreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.interfaces.GuideInterface
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_guide.*
import kotlinx.android.synthetic.main.fragment_guide.view.*

class GuideFragment:Fragment() {

    private lateinit var guideInterface: GuideInterface
    private lateinit var youtubePlayerViewOne:YouTubePlayerView
    private lateinit var youtubePlayerViewTwo:YouTubePlayerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return  inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.return_button.setOnClickListener {
            guideInterface.hideGuideFragment()
        }

        youtubePlayerViewOne=youtube_player_view_1
        lifecycle.addObserver(youtubePlayerViewOne)
        youtubePlayerViewOne.addFullScreenListener(object : YouTubePlayerFullScreenListener {
            val previousLayoutParams = youtubePlayerViewOne.layoutParams

            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT)
            override fun onYouTubePlayerEnterFullScreen() {
                activity?.requestedOrientation= ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

//                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
//                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
//                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
//                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                youtubePlayerViewOne.layoutParams=layoutParams
            }

            override fun onYouTubePlayerExitFullScreen() {
                activity?.requestedOrientation= ActivityInfo.SCREEN_ORIENTATION_SENSOR
                youtubePlayerViewOne.layoutParams=previousLayoutParams
//                youtubePlayerViewOne.exitFullScreen()
            }

        })

        youtubePlayerViewTwo=youtube_player_view_2
        lifecycle.addObserver(youtubePlayerViewTwo)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is GuideInterface) {
            guideInterface = context
        }
    }
}
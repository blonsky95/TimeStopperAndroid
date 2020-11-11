package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.interfaces.GuideInterface
import kotlinx.android.synthetic.main.fragment_guide.*
import kotlinx.android.synthetic.main.fragment_guide.view.*

class GuideFragment : Fragment() {

    private lateinit var guideInterface: GuideInterface
    private lateinit var youtubePlayerViewOne: YouTubePlayerView
    private lateinit var youtubePlayerViewTwo: YouTubePlayerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.return_button.setOnClickListener {
            guideInterface.hideGuideFragment()
        }

        youtubePlayerViewOne = youtube_player_view_1
        lifecycle.addObserver(youtubePlayerViewOne)

        youtubePlayerViewTwo = youtube_player_view_2
        lifecycle.addObserver(youtubePlayerViewTwo)

    }

    override fun onResume() {
        super.onResume()
        updateLayoutIfOrientation(resources.configuration.orientation)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is GuideInterface) {
            guideInterface = context
        }
    }

    /**
     * ORIENTATION CHANGES
     */

    private fun updateLayoutIfOrientation(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            title_guide.visibility = View.GONE
            return_button.visibility = View.GONE
            youtube_player_view_1.setMargins(left = 96F, right = 96F)
            youtube_player_view_2.setMargins(left = 96F, right = 96F)
        } else {
            title_guide.visibility = View.VISIBLE
            return_button.visibility = View.VISIBLE
//            val params = youtube_player_view_1.layoutParams as ViewGroup.MarginLayoutParams
//            params.setMargins(youtube_player_view_1.dpToPx(48F),youtube_player_view_1.dpToPx(48F),youtube_player_view_1.dpToPx(48F),youtube_player_view_1.dpToPx(48F))
            youtube_player_view_1.setMargins(left = 12F, right = 12F)
            youtube_player_view_2.setMargins(left = 12F, right = 12F)
        }
    }

    //next 4(3) functions are examples of extension functions

    private fun View.setMargins(left: Float? = null, top: Float? = null, right: Float? = null, bottom: Float? = null) {
        layoutParams<ViewGroup.MarginLayoutParams> {
            left?.run { leftMargin = dpToPx(this) }
            top?.run { topMargin = dpToPx(this) }
            right?.run { rightMargin = dpToPx(this) }
            bottom?.run { bottomMargin = dpToPx(this) }
        }
    }

    //this one is an inline function
    private inline fun <reified T : ViewGroup.LayoutParams> View.layoutParams(block: T.() -> Unit) {
        if (layoutParams is T) block(layoutParams as T)
    }

    private fun View.dpToPx(dp: Float): Int = context.dpToPx(dp)
    private fun Context.dpToPx(dp: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLayoutIfOrientation(newConfig.orientation)
    }
}
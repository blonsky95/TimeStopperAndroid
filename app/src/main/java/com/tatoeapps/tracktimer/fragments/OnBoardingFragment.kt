package com.tatoeapps.tracktimer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.R
import kotlinx.android.synthetic.main.fragment_onboarding_page_1.view.*

class OnBoardingFragment: Fragment() {

    interface OnBoardingListener {
        fun onNextClick()
    }

    companion object {
        const val NAME = "onboarding_screen_id"

        fun newInstance(name : String, listener : OnBoardingListener) : Fragment {
            val fragment = OnBoardingFragment()
            val bundle = Bundle()
            bundle.putString(NAME, name)
            fragment.arguments = bundle
            fragment.onBoardingListener = listener
            return fragment
        }
    }

    private lateinit var onBoardingListener : OnBoardingListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return when (arguments?.getString(NAME)!!.toInt()) {
            0 -> {
                inflater.inflate(R.layout.fragment_onboarding_page_1, container, false)
            }

            else -> {
                inflater.inflate(R.layout.fragment_onboarding_page_2, container, false)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.guide_next_button.setOnClickListener {
            onBoardingListener.onNextClick()
        }
    }

}

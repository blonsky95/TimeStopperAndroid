package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.interfaces.GuideInterface
import kotlinx.android.synthetic.main.fragment_guide.view.*

class GuideFragment:Fragment() {

    private lateinit var guideInterface: GuideInterface

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return  inflater.inflate(R.layout.fragment_guide, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        view.trial_subscription_text.movementMethod=LinkMovementMethod.getInstance()
        view.return_button.setOnClickListener {
            guideInterface.hideGuideFragment()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is GuideInterface) {
            guideInterface = context
        }
    }
}
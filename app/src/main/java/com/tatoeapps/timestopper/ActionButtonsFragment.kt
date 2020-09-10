package com.tatoeapps.timestopper

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import kotlinx.android.synthetic.main.fragment_action_btns.view.*

class ActionButtonsFragment : Fragment() {

    private lateinit var actionButtonsInterface: ActionButtonsInterface
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return  inflater.inflate(R.layout.fragment_action_btns, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.addVideo_btn.setOnClickListener {
            actionButtonsInterface.importVideo()
        }
//        view.speedUp_btn.setOnClickListener {
//            actionButtonsInterface.addSpeed()
//        }
//        view.speedDown_btn.setOnClickListener {
//            actionButtonsInterface.minusSpeed()
//        }
        view.start_btn.setOnClickListener {
            actionButtonsInterface.startTiming()
        }
        view.lap_btn.setOnClickListener {
            actionButtonsInterface.lapTiming()
        }
        view.stop_btn.setOnClickListener {
            actionButtonsInterface.stopTiming()
        }
        view.clear_btn.setOnClickListener {
            actionButtonsInterface.clearTiming()
        }


    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ActionButtonsInterface) {
            actionButtonsInterface = context
        }

    }
}
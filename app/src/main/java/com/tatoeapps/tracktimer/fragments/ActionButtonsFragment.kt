package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.interfaces.ActionButtonsInterface
import com.tatoeapps.tracktimer.R
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
        view.get_help_btn.setOnClickListener {
            actionButtonsInterface.helpButtonPressed()
        }
        view.sub_btn.setOnClickListener {
            actionButtonsInterface.subscriptionButtonPressed()
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ActionButtonsInterface) {
            actionButtonsInterface = context
        }

    }
}
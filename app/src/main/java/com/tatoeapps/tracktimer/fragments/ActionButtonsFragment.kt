package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.databinding.FragmentActionBtnsBinding
import com.tatoeapps.tracktimer.interfaces.ActionButtonsInterface

class ActionButtonsFragment : Fragment() {

    private lateinit var actionButtonsInterface: ActionButtonsInterface

    private var _binding: FragmentActionBtnsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentActionBtnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.addVideoBtn.setOnClickListener {
            actionButtonsInterface.importVideo()
        }
        binding.startBtn.setOnClickListener {
            actionButtonsInterface.startTimingFeature()
        }
        binding.lapBtn.setOnClickListener {
            actionButtonsInterface.lapTiming()
        }
        binding.stopBtn.setOnClickListener {
            actionButtonsInterface.stopTiming()
        }
        binding.clearBtn.setOnClickListener {
            actionButtonsInterface.clearTiming()
        }
        binding.getHelpBtn.setOnClickListener {
            actionButtonsInterface.helpButtonPressed()
        }
        binding.subBtn.setOnClickListener {
            actionButtonsInterface.subPressed()
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ActionButtonsInterface) {
            actionButtonsInterface = context
        }

    }
}
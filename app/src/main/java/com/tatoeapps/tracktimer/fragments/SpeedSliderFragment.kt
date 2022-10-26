package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.tatoeapps.tracktimer.databinding.FragmentSpeedBtnsBinding
import com.tatoeapps.tracktimer.interfaces.SpeedSliderInterface

class SpeedSliderFragment : Fragment() {

    companion object {
        const val maxSpeedValue = 250.0f
        const val minSpeedValue = 10.0f
        const val intervalValue = 10.0f
        const val defaultSpeedFactor = 1.0f
    }

    private var _binding: FragmentSpeedBtnsBinding? = null
    private val binding get() = _binding!!

    private lateinit var speedSliderInterface: SpeedSliderInterface
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedBtnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.speedSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                speedSliderInterface.setSpeed(slider.value)
                updateSpeedDisplay((slider.value / 100).toString())
            }
        })

        binding.speedSlider.addOnChangeListener { slider, _, _ -> updateSpeedDisplay((slider.value / 100).toString()) }

        binding.addSpeedBtn.setOnClickListener {
            if (binding.speedSlider.value < maxSpeedValue) {
                val newSpeed = binding.speedSlider.value + intervalValue
                binding.speedSlider.value = newSpeed
                val speedString = (newSpeed / 100).toString()
                binding.speedValueDisplay.text = speedString
                speedSliderInterface.setSpeed(newSpeed)
            }
        }
        binding.minusSpeedBtn.setOnClickListener {
            if (binding.speedSlider.value > minSpeedValue) {
                val newSpeed = binding.speedSlider.value - intervalValue
                binding.speedSlider.value = newSpeed
                val speedString = (newSpeed / 100).toString()
                binding.speedValueDisplay.text = speedString
                speedSliderInterface.setSpeed(newSpeed)
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SpeedSliderInterface) {
            speedSliderInterface = context
        }
    }

    fun updateSpeedDisplay(newSpeed: String) {
        binding.speedValueDisplay.text = newSpeed
    }

    fun resetSpeed() {
        speedSliderInterface.setSpeed(defaultSpeedFactor * 100)
        binding.speedSlider.value = defaultSpeedFactor * 100
        binding.speedValueDisplay.text = defaultSpeedFactor.toString()
    }
}
package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.slider.Slider
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.databinding.FragmentActionBtnsBinding
import com.tatoeapps.tracktimer.databinding.FragmentSpeedBtnsBinding
import com.tatoeapps.tracktimer.interfaces.SpeedSliderInterface
import com.tatoeapps.tracktimer.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_speed_btns.*
import kotlinx.android.synthetic.main.fragment_speed_btns.view.*
import timber.log.Timber

@AndroidEntryPoint
class SpeedSliderFragment : Fragment() {

    companion object {
        const val maxSpeedValue = 250.0f
        const val minSpeedValue = 10.0f
        const val intervalValue = 10.0f
        const val defaultSpeedFactor = 1.0f
    }

    private var fragmentSpeedBtnsBinding: FragmentSpeedBtnsBinding? = null

    private lateinit var speedSliderInterface: SpeedSliderInterface

    private val mainViewModel: MainViewModel by activityViewModels()

    var currentSpeedText = MutableLiveData(defaultSpeedFactor.toString())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSpeedBtnsBinding.inflate(inflater, container, false)
        fragmentSpeedBtnsBinding = binding
        binding.lifecycleOwner=viewLifecycleOwner
        binding.speedSliderFragment = this
        return binding.root
    }

    override fun onDestroyView() {
        fragmentSpeedBtnsBinding=null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.speed_slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                speedSliderInterface.setSpeed(slider.value)
                currentSpeedText.postValue((slider.value / 100).toString())
            }
        })

        view.speed_slider.addOnChangeListener { slider, _, _ -> currentSpeedText.postValue((slider.value / 100).toString()) }

        mainViewModel.speedReset.observe(viewLifecycleOwner, Observer { speedReset ->
            if (speedReset) {
                resetSpeed()
            }
        })

    }

    fun addSpeed() {
        if (speed_slider.value < maxSpeedValue) {
            val newSpeed = speed_slider.value + intervalValue
            speed_slider.value = newSpeed
            speedSliderInterface.setSpeed(newSpeed)

            currentSpeedText.postValue((newSpeed / 100).toString())
        }
    }

    fun minusSpeed() {
        if (speed_slider.value > minSpeedValue) {
            val newSpeed = speed_slider.value - intervalValue
            speed_slider.value = newSpeed
            speedSliderInterface.setSpeed(newSpeed)

            currentSpeedText.postValue ((newSpeed / 100).toString())
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SpeedSliderInterface) {
            speedSliderInterface = context
        }
    }

    private fun resetSpeed() {
        speedSliderInterface.setSpeed(defaultSpeedFactor * 100)
        speed_slider.value = defaultSpeedFactor * 100
        speed_value_display.text = defaultSpeedFactor.toString()
    }
}
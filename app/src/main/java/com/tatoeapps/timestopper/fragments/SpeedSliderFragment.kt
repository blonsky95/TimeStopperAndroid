package com.tatoeapps.timestopper.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.tatoeapps.timestopper.R
import com.tatoeapps.timestopper.interfaces.SpeedSliderInterface
import kotlinx.android.synthetic.main.fragment_speed_btns.*
import kotlinx.android.synthetic.main.fragment_speed_btns.view.*
import java.text.NumberFormat

class SpeedSliderFragment : Fragment() {

    companion object {
        const val maxSpeedValue = 250.0f
        const val minSpeedValue = 10.0f
        const val intervalValue = 10.0f
        const val defaultSpeedFactor = 1.0f
    }

    private lateinit var speedSliderInterface: SpeedSliderInterface
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_speed_btns, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        view.speed_slider.setLabelFormatter { value: Float ->
//            val format = NumberFormat.getInstance()
//            format.format((value / 100).toDouble())
//        }

        view.speed_slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                speedSliderInterface.setSpeed(slider.value)
                updateSpeedDisplay((slider.value / 100).toString())
            }
        })

        view.speed_slider.addOnChangeListener { slider, _, _ -> updateSpeedDisplay((slider.value / 100).toString()) }

        view.add_speed_btn.setOnClickListener {
            if (speed_slider.value < maxSpeedValue) {
                val newSpeed = speed_slider.value + intervalValue
                speed_slider.value = newSpeed
                val speedString = (newSpeed / 100).toString()
                speed_value_display.text = speedString
                speedSliderInterface.setSpeed(newSpeed)
            }
        }
        view.minus_speed_btn.setOnClickListener {
            if (speed_slider.value > minSpeedValue) {
                val newSpeed = speed_slider.value - intervalValue
                speed_slider.value = newSpeed
                val speedString = (newSpeed / 100).toString()
                speed_value_display.text = speedString
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
        speed_value_display.text = newSpeed
    }

    fun resetSpeed() {
        speedSliderInterface.setSpeed(defaultSpeedFactor * 100)
        speed_slider.value = defaultSpeedFactor * 100
        speed_value_display.text = defaultSpeedFactor.toString()
    }
}
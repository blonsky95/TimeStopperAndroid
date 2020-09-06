package com.tatoeapps.timestopper

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.slider.Slider
import kotlinx.android.synthetic.main.fragment_action_btns.view.*
import kotlinx.android.synthetic.main.fragment_speed_btns.view.*
import java.text.NumberFormat

class SpeedSliderFragment : Fragment() {

    private lateinit var speedSliderInterface: SpeedSliderInterface
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return  inflater.inflate(R.layout.fragment_speed_btns, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.speed_slider.setLabelFormatter { value: Float ->
            val format = NumberFormat.getInstance()
//            format.maximumFractionDigits = 0
            format.format((value/100).toDouble())
        }

        view.speed_slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                // Responds to when slider's touch event is being started
            }

            override fun onStopTrackingTouch(slider: Slider) {
                speedSliderInterface.setSpeed(slider.value)
            }
        })
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SpeedSliderInterface) {
            speedSliderInterface = context
        }
    }
}
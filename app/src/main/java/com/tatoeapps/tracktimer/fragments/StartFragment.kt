package com.tatoeapps.tracktimer.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.interfaces.ActionButtonsInterface
import com.tatoeapps.tracktimer.R
import kotlinx.android.synthetic.main.fragment_start.view.*

class StartFragment : Fragment() {

    private lateinit var importVideoInterface: ActionButtonsInterface

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_start, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.button_container.setOnClickListener {
            importVideoInterface.importVideo()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ActionButtonsInterface) {
            importVideoInterface = context
        }
    }
}
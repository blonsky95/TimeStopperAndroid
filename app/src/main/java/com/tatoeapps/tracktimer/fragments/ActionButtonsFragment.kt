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

    private lateinit var mActionButtonsInterface: ActionButtonsInterface
    private var fragmentActionBtnsBinding:FragmentActionBtnsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val binding = FragmentActionBtnsBinding.inflate(inflater, container, false)
        fragmentActionBtnsBinding = binding
        binding.actionButtonsInterface=mActionButtonsInterface

        return binding.root
    }

    override fun onDestroyView() {
        fragmentActionBtnsBinding=null
        super.onDestroyView()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        if (context is ActionButtonsInterface) {
            mActionButtonsInterface = context
        }

    }
}
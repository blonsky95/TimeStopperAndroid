package com.tatoeapps.tracktimer.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tatoeapps.tracktimer.databinding.FragmentOnboardingPage1Binding
import com.tatoeapps.tracktimer.databinding.FragmentOnboardingPage2Binding

class OnBoardingFragment : Fragment() {

    interface OnBoardingListener {
        fun onNextClick()
    }

    private var _binding1: FragmentOnboardingPage1Binding? = null
    private var _binding2: FragmentOnboardingPage2Binding? = null
    private val binding get() = _binding1 ?: _binding2
    private val nextBtn = _binding1?.guideNextButton ?: _binding2?.guideNextButton

    companion object {
        const val NAME = "onboarding_screen_id"

        fun newInstance(name: String, listener: OnBoardingListener): Fragment {
            val fragment = OnBoardingFragment()
            val bundle = Bundle()
            bundle.putString(NAME, name)
            fragment.arguments = bundle
            fragment.onBoardingListener = listener
            return fragment
        }
    }

    private lateinit var onBoardingListener: OnBoardingListener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return when (arguments?.getString(NAME)!!.toInt()) {
            0 -> {
                FragmentOnboardingPage1Binding.inflate(inflater, container, false).apply {
                    _binding1 = this
                }.root
            }

            else -> {
                FragmentOnboardingPage2Binding.inflate(inflater, container, false).apply {
                    _binding2 = this
                }.root
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding1 = null
        _binding2 = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        nextBtn?.setOnClickListener {
            onBoardingListener.onNextClick()
        }
    }

}

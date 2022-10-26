package com.tatoeapps.tracktimer.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.tatoeapps.tracktimer.databinding.ActivityOnboardingBinding
import com.tatoeapps.tracktimer.fragments.OnBoardingFragment
import com.tatoeapps.tracktimer.utils.OnBoardingViewPagerAdapter
import com.tatoeapps.tracktimer.utils.Utils

class OnBoardingActivity : AppCompatActivity(), OnBoardingFragment.OnBoardingListener {

    private var currentPage = 0
    private val fragments = ArrayList<Fragment>()
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = OnBoardingViewPagerAdapter(this)
        for (i in 0..1) {
            fragments.add(OnBoardingFragment.newInstance("$i", this))
        }
        adapter.addFragments(fragments)
        binding.viewPager.adapter = adapter

        // keep track of the current screen
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
            }
        })
    }

    // open the main screen when next is tapped on the last screen
    override fun onNextClick() {
        if (currentPage == fragments.size - 1) {
            startActivity(Intent(this, MainActivity::class.java))
            Utils.updateUserFirstTimer(this, false)
            finish()
        } else {
            binding.viewPager.setCurrentItem(currentPage + 1, true)
        }
    }
}
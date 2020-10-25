package com.tatoeapps.tracktimer.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.fragments.OnBoardingFragment
import com.tatoeapps.tracktimer.utils.OnBoardingViewPagerAdapter
import com.tatoeapps.tracktimer.utils.Utils
import kotlinx.android.synthetic.main.activity_onboarding.*

class OnBoardingActivity : AppCompatActivity(), OnBoardingFragment.OnBoardingListener {

    private var currentPage = 0
    private val fragments = ArrayList<Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        val adapter = OnBoardingViewPagerAdapter(this)
        for (i in 0..1) {
            fragments.add(OnBoardingFragment.newInstance("$i", this))
        }
        adapter.addFragments(fragments)
        view_pager.adapter = adapter

        // keep track of the current screen
        view_pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
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
            view_pager.setCurrentItem(currentPage + 1, true)
        }
    }
}
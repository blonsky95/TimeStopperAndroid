package com.tatoeapps.tracktimer.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.fragments.OnBoardingFragment
import com.tatoeapps.tracktimer.utils.OnBoardingViewPagerAdapter
import com.tatoeapps.tracktimer.utils.PreferencesDataStore
import com.tatoeapps.tracktimer.utils.Utils
import kotlinx.android.synthetic.main.activity_onboarding.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class OnBoardingActivity : AppCompatActivity(), OnBoardingFragment.OnBoardingListener {

    private var currentPage = 0
    private val fragments = ArrayList<Fragment>()
    lateinit var preferencesDataStore: PreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        preferencesDataStore=PreferencesDataStore.getInstance(this)

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
            val context = this
            GlobalScope.launch {
                //dont want to block ui, even if super short simple task, it should be done in bg
                PreferencesDataStore.updateUserFirstTimer(context, false)
            }
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        } else {
            view_pager.setCurrentItem(currentPage + 1, true)
        }

    }
}

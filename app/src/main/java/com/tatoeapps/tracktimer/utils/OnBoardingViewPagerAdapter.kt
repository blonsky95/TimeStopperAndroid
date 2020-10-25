package com.tatoeapps.tracktimer.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnBoardingViewPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

    var fragments = ArrayList<Fragment>()

    fun addFragments(fragments : ArrayList<Fragment>) {
        this.fragments = fragments
    }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

}
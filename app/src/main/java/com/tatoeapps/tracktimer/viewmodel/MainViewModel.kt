package com.tatoeapps.tracktimer.viewmodel

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.billingclient.api.SkuDetails

class MainViewModel() : ViewModel() {

    val isConnectingToGooglePlay = MutableLiveData<Boolean>(false)

}
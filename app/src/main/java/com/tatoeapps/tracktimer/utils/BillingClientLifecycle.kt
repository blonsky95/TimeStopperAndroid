package com.tatoeapps.tracktimer.utils

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import com.android.billingclient.api.*
import timber.log.Timber

class BillingClientLifecycle(
    val app: Application,
    lifecycle: Lifecycle

) : LifecycleObserver, PurchasesUpdatedListener, BillingClientStateListener,
    SkuDetailsResponseListener {


    //is a singleton - but we dont want it to be an object because it has lifecycle related behaviour
    companion object {

        const val subscriptionSku = "track_timer_timing_feature"

        @Volatile
        private var INSTANCE: BillingClientLifecycle? = null

        fun getInstance(app: Application, lifecycle: Lifecycle): BillingClientLifecycle =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingClientLifecycle(app, lifecycle).also { INSTANCE = it }
            }
    }

    init {
        lifecycle.addObserver(this)
    }

    private lateinit var billingClient: BillingClient
    val skusWithSkuDetails = MutableLiveData<Map<String, SkuDetails>>()

    override fun onPurchasesUpdated(p0: BillingResult, p1: MutableList<Purchase>?) {
        TODO("Not yet implemented")
    }

    override fun onBillingServiceDisconnected() {
        TODO("Not yet implemented")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Timber.d("onBillingSetupFinished: $responseCode $debugMessage")
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            // The billing client is ready. You can query purchases here.
            querySkuDetails()
            queryPurchases()
        }
    }

    override fun onSkuDetailsResponse(
        billingResult: BillingResult,
        skuDetailsList: MutableList<SkuDetails>?
    ) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        when (responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                Timber.d("onSkuDetailsResponse: $responseCode $debugMessage")
                if (skuDetailsList == null) {
                    Timber.d("onSkuDetailsResponse: null SkuDetails list")
                    skusWithSkuDetails.postValue(emptyMap())
                } else
                    skusWithSkuDetails.postValue(HashMap<String, SkuDetails>().apply {
                        for (details in skuDetailsList) {
                            Timber.d("onSkuDetailsResponse: details ${details.description}")
                            put(details.sku, details)
                        }
                    }.also { postedValue ->
                        Timber.d("onSkuDetailsResponse: count ${postedValue.size}")
                    })
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            BillingClient.BillingResponseCode.ERROR -> {
                Timber.d("onSkuDetailsResponse: $responseCode $debugMessage")
            }
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                // These response codes are not expected.
                Timber.d("onSkuDetailsResponse: $responseCode $debugMessage")
            }
        }
    }

//    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun create() {
        billingClient = BillingClient.newBuilder(app.applicationContext)
            .setListener(this)
            .enablePendingPurchases()
            .build()

        if (!billingClient.isReady) {
            billingClient.startConnection(this)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun destroy() {
        if (billingClient.isReady) {
            Timber.d("BillingClient can only be used once -- closing connection")
            // BillingClient can only be used once.
            // After calling endConnection(), we must create a new BillingClient.
            billingClient.endConnection()
        }
    }

    fun querySkuDetails() {
        Timber.d("querySkuDetails")
        val params = SkuDetailsParams.newBuilder()
            .setType(BillingClient.SkuType.SUBS)
            .setSkusList(
                listOf(
                    "track_timer_timing_feature"
                )
            )
            .build()
        params.let { skuDetailsParams ->
            Timber.d("querySkuDetailsAsync")
            billingClient.querySkuDetailsAsync(skuDetailsParams, this)
        }
    }

    fun queryPurchases() {
//        if (!billingClient.isReady) {
//            Timber.d( "queryPurchases: BillingClient is not ready")
//        }
//        Timber.d( "queryPurchases: SUBS")
//        val result = billingClient.queryPurchases(BillingClient.SkuType.SUBS)
//        if (result == null) {
//            Timber.d( "queryPurchases: null purchase result")
//            processPurchases(null)
//        } else {
//            if (result.purchasesList == null) {
//                Timber.d( "queryPurchases: null purchase list")
//                processPurchases(null)
//            } else {
//                processPurchases(result.purchasesList)
//            }
//        }
    }

//    private val purchaseUpdateListener =
//        PurchasesUpdatedListener { billingResult, purchases ->
//            // To be implemented in a later section.
//        }


//    fun registerBillingConnection(context: Context, attemptCount: Int) {
//        billingClient.startConnection(object : BillingClientStateListener {
//            override fun onBillingSetupFinished(billingResult: BillingResult) {
//                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
//                    // The BillingClient is ready. You can query purchases here.
//                    querySkuDetailsForSubscription(context)
//                }
//            }
//
//            override fun onBillingServiceDisconnected() {
//                // Try to restart the connection on the next request to
//                // Google Play by calling the startConnection() method.
//
//                //retry connection 3 times
//                if (attemptCount < 3) {
//                    if (!billingClient.isReady) {
//                        registerBillingConnection(context, attemptCount)
//                    }
//                } else {
//                    Toast.makeText(context, "Connection to google play failing", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            }
//        })
//    }

//    fun querySkuDetailsForSubscription(context: Context) {
//        val skuList = ArrayList<String>()
//        skuList.add("track_timer_timing_feature")
//        val params = SkuDetailsParams.newBuilder()
//        params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS).build()
//        billingClient.querySkuDetailsAsync(params, context)
////        val skuDetailsResult = withContext(Dispatchers.IO) {
////            querySkuDetailsSuspend(params)
////        }
//
//        // Process the result.
//    }
}
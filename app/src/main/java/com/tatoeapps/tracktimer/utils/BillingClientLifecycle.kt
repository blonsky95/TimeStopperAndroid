package com.tatoeapps.tracktimer.utils

import android.app.Application
import androidx.lifecycle.*
import com.android.billingclient.api.*
import com.tatoeapps.tracktimer.main.MainActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * This class handles all the billing stuff, most of it is standard.
 * The flow of events is:
 * - class is created, connection is made
 * - queries existing purchases (aka is user subscribed)
 * - if there is a purchase (user subscribed), there is no need to query sku details (available subscriptions)
 * - if no purchases, then query skudetails and display to user the subscription
 * - launch billing flow if user buys
 * - receive purchase object, grant subscription, aknowledge the purchase
 *
 * However, the class triggers UI changes by posting livedata values. This responds to users attempting
 * to subscribe or pressing the subscribe button
 * Nevertheless, we need the class to check if users have cancelled subscription, to cancel the subscription after the
 * period, in this case, the existing purchases are queried, and if nothing is found then subscription pref
 * is updated to false.
 *
 * Here comes variable mCheckingSubscriptionState - if true - its just a check - and no live data will
 * be updated - but the value of subscribed in shared prefs will
 **/
class BillingClientLifecycle(
    val app: Application,
    private val mainActivity: MainActivity,
    lifecycle: Lifecycle

) : LifecycleObserver, PurchasesUpdatedListener, BillingClientStateListener,
    SkuDetailsResponseListener {

    var preferencesDataStore: PreferencesDataStore = PreferencesDataStore.getInstance(mainActivity)

    //is a singleton - but we dont want it to be an object because it has lifecycle related behaviour
    companion object {

        const val subscriptionSku = "track_timer_timing_feature"

        @Volatile
        private var INSTANCE: BillingClientLifecycle? = null

        fun getInstance(
            app: Application,
            mainActivity: MainActivity,
            lifecycle: Lifecycle
        ): BillingClientLifecycle =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingClientLifecycle(app, mainActivity, lifecycle).also {
                    INSTANCE = it
                }
            }
    }

    init {
        lifecycle.addObserver(this)
    }

    private lateinit var billingClient: BillingClient
    val availableSubscriptions = MutableLiveData<Map<String, SkuDetails>>()
    val subscriptionActive = MutableLiveData<Boolean>()
    val billingClientConnectionState = MutableLiveData<Int>()

    var userInterfaceRequireUpdating = false

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handleSubscription(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
//            Toast.makeText(mainActivity, "User canceled buy", Toast.LENGTH_SHORT).show()
        } else {
            // Handle any other error codes.
//            Toast.makeText(mainActivity, "Something canceled buy", Toast.LENGTH_SHORT).show()

        }
    }

    private fun handleSubscription(purchase: Purchase) {
        @Suppress("DEPRECATED_IDENTITY_EQUALS")
        //verifies the purchase
        if (purchase.purchaseState === Purchase.PurchaseState.PURCHASED) {
            //grant entitlement of purchase here
            GlobalScope.launch {
                preferencesDataStore.updateIsUserSubscribed( true)
            }
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)

                billingClient.acknowledgePurchase(acknowledgePurchaseParams.build()) { billingResult ->
                    val responseCode = billingResult.responseCode
                    val debugMessage = billingResult.debugMessage
                    Timber.d("acknowledgePurchase: $responseCode $debugMessage")
                }
            }
            if (userInterfaceRequireUpdating){
                subscriptionActive.postValue(true)
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        Timber.d("Billing service disconnected")
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        val responseCode = billingResult.responseCode
        val debugMessage = billingResult.debugMessage
        Timber.d("onBillingSetupFinished: $responseCode $debugMessage")
        if (responseCode == BillingClient.BillingResponseCode.OK) {
            // The billing client is ready. You can query purchases here.

            //first check if you are already subscribed, if you are, dialog will say something different
            queryExistingPurchases()
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
                    availableSubscriptions.postValue(emptyMap())
                } else
                    availableSubscriptions.postValue(HashMap<String, SkuDetails>().apply {
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
                billingClientConnectionState.postValue(responseCode)
                Timber.d("onSkuDetailsResponse: $responseCode $debugMessage")
            }
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.ITEM_NOT_OWNED -> {
                // These response codes are not expected.
                billingClientConnectionState.postValue(responseCode)
                Timber.d("onSkuDetailsResponse: $responseCode $debugMessage")
            }
        }
    }

    //    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun buildNewClient(doesUserInterfaceNeedUpdating: Boolean) {
        userInterfaceRequireUpdating=doesUserInterfaceNeedUpdating
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

    private fun querySkuDetails() {
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

    fun startLaunchBillingFlow(flowParams: BillingFlowParams): Any {
        return billingClient.launchBillingFlow(mainActivity, flowParams).responseCode
    }

    private fun queryExistingPurchases() {
        if (!billingClient.isReady) {
            Timber.d("queryPurchases: BillingClient is not ready")
        }
        Timber.d("queryPurchases: SUBS")
        val result = billingClient.queryPurchases(BillingClient.SkuType.SUBS)
        if (result.purchasesList != null && result.purchasesList!!.isNotEmpty()) {
            //user has a subscription! - update pref so app has subscription active pref
            for (subscription in result.purchasesList!!) {
                handleSubscription(subscription)
            }
        } else {
            //user doesnt have a subscription
            if (!userInterfaceRequireUpdating) {
                //This would be run at the start, and no windows need to appear, it just updates the state of the sub pref
                GlobalScope.launch {
                    preferencesDataStore.updateIsUserSubscribed(false)
                }
            } else {
                //In this case, user wants to see the subscriptions
                querySkuDetails()
            }

        }
    }
}
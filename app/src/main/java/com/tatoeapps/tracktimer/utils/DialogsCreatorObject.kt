package com.tatoeapps.tracktimer.utils

import android.app.AlertDialog
import android.content.Context
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.util.Linkify
import android.view.View
import com.android.billingclient.api.SkuDetails
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.main.MainActivity
import kotlinx.android.synthetic.main.dialog_buy_subscription.view.*
import kotlinx.android.synthetic.main.dialog_loading.view.*

object DialogsCreatorObject {

    interface DialogWindowInterface {

        fun onPositiveButton() {
        }

        fun onNegativeButton() {
        }

        fun onSubscribeClicked() {
        }

        fun onCancelButton() {
        }
    }

    fun getUnsubscribedDialog(
        mainActivity: MainActivity,
        list: Map<String, SkuDetails>,
        dialogWindowInterface: DialogWindowInterface
    ): AlertDialog {
        val builder = AlertDialog.Builder(mainActivity)
        // Get the layout inflater
        val dialogCustomView =
            mainActivity.layoutInflater.inflate(R.layout.dialog_buy_subscription, null)
        if (list.isEmpty()) {
            dialogCustomView.subscription_container.visibility = View.GONE
            dialogCustomView.available_subscriptions.text =
                mainActivity.resources.getString(R.string.no_subscriptions_available)
        } else {
            val skuDetails = list[BillingClientLifecycle.subscriptionSku]
            skuDetails?.let {
                dialogCustomView.product_title.text = it.title
                dialogCustomView.product_description.text = it.description
                dialogCustomView.product_price.text = it.price
                val spannableString = SpannableString(mainActivity.getString(R.string.subscription_explanation))
                Linkify.addLinks(spannableString, Linkify.WEB_URLS)
                dialogCustomView.trial_subscription_text.text = spannableString
                dialogCustomView.trial_subscription_text.movementMethod=LinkMovementMethod.getInstance()
                dialogCustomView.subscribe_button.setOnClickListener {
                    dialogWindowInterface.onSubscribeClicked()
                }

            }
        }
        builder.setView(dialogCustomView)
        return builder.create()
    }

    fun getSubscribedDialog(
        mainActivity: MainActivity): AlertDialog {
        val builder = AlertDialog.Builder(mainActivity)
        // Get the layout inflater
        val dialogCustomView =
            mainActivity.layoutInflater.inflate(R.layout.dialog_buy_subscription, null)

        dialogCustomView.available_subscriptions.visibility=View.GONE
        dialogCustomView.product_title.text = mainActivity.getString(R.string.already_subscribed)
        val spannableString = SpannableString(mainActivity.getString(R.string.cancel_subscription_info))
        Linkify.addLinks(spannableString, Linkify.WEB_URLS)
        dialogCustomView.product_description.text = spannableString
        dialogCustomView.product_description.movementMethod=LinkMovementMethod.getInstance()
        dialogCustomView.product_price.visibility=View.GONE
//        dialogCustomView.subscribe_button.text=mainActivity.getString(R.string.subscribed_text)
        dialogCustomView.trial_subscription_text.visibility=View.GONE
        dialogCustomView.subscribe_button.visibility=View.GONE

        builder.setView(dialogCustomView)
        return builder.create()
    }

    fun getTrialStartDialog(
        context: Context,
        dialogWindowInterface: DialogWindowInterface
    ): androidx.appcompat.app.AlertDialog {

        val alertDialogBuilder: androidx.appcompat.app.AlertDialog.Builder =
            androidx.appcompat.app.AlertDialog.Builder(context)
        alertDialogBuilder.setMessage(
            "${context.resources.getString(R.string.trial_prompt_segment_1)} ${Utils.numberVideosTimingFree} ${context.resources.getString(
                R.string.trial_prompt_segment_2
            )}" +
                    " ${context.resources.getString(R.string.trial_prompt_segment_3)} ${Utils.getPrefCountOfFreeTimingVideosInTrial(
                        context
                    )}${context.resources.getString(R.string.trial_prompt_segment_4)}"
        )
        alertDialogBuilder.setCancelable(true)

        alertDialogBuilder.setNeutralButton(
            context.resources.getString(R.string.go_unlimited)
        ) { _, _ ->
            dialogWindowInterface.onNegativeButton()
        }.setPositiveButton(
            context.resources.getString(R.string.yes_string)
        ) { _, _ ->
            dialogWindowInterface.onPositiveButton()
        }
        return alertDialogBuilder.create()
    }

    fun getTrialExpiredDialog(
        context: Context,
        dialogWindowInterface: DialogWindowInterface
    ): androidx.appcompat.app.AlertDialog {
        val alertDialogBuilder: androidx.appcompat.app.AlertDialog.Builder =
            androidx.appcompat.app.AlertDialog.Builder(context)
        alertDialogBuilder.setMessage(
            context.resources.getString(R.string.get_subscription_prompt)
        )
        alertDialogBuilder.setCancelable(true)

        alertDialogBuilder.setNeutralButton(
            context.resources.getString(R.string.next_time)
        ) { _, _ ->
            dialogWindowInterface.onNegativeButton()
        }.setPositiveButton(
            context.resources.getString(R.string.yes_string_exclamation)
        ) { _, _ ->
            dialogWindowInterface.onPositiveButton()
        }
        return alertDialogBuilder.create()
    }

    fun getLoadingDialog(
        mainActivity: MainActivity,
        dialogWindowInterface: DialogWindowInterface
    ): androidx.appcompat.app.AlertDialog {
        val alertDialogBuilder: androidx.appcompat.app.AlertDialog.Builder =
            androidx.appcompat.app.AlertDialog.Builder(mainActivity)
        val dialogCustomView = mainActivity.layoutInflater.inflate(R.layout.dialog_loading, null)
        dialogCustomView.cancel_button.setOnClickListener {
            dialogWindowInterface.onCancelButton()
        }
        alertDialogBuilder.setView(dialogCustomView).setCancelable(false)
        return alertDialogBuilder.create()
    }
}
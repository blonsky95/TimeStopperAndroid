package com.tatoeapps.tracktimer.utils

import android.app.AlertDialog
import android.content.Context
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import com.android.billingclient.api.SkuDetails
import com.tatoeapps.tracktimer.R
import com.tatoeapps.tracktimer.databinding.DialogBuySubscriptionBinding
import com.tatoeapps.tracktimer.databinding.DialogLoadingBinding
import com.tatoeapps.tracktimer.main.MainActivity

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

    fun getRatingPromptDialog(mainActivity: MainActivity, dialogWindowInterface: DialogWindowInterface) :AlertDialog{
        val builder = AlertDialog.Builder(mainActivity)
        builder.setMessage(mainActivity.getString(R.string.rating_prompt))
            .setPositiveButton(R.string.yes_string
            ) { _, _ ->
                dialogWindowInterface.onPositiveButton()
            }
            .setNegativeButton(mainActivity.getString(R.string.not_now_string)) {
                    _, _ ->
            }

        return builder.create()
    }

    fun getUnsubscribedDialog(
        mainActivity: MainActivity,
        list: Map<String, SkuDetails>,
        dialogWindowInterface: DialogWindowInterface
    ): AlertDialog {
        val builder = AlertDialog.Builder(mainActivity)
        val binding = DialogBuySubscriptionBinding.inflate(mainActivity.layoutInflater)

        if (list.isEmpty()) {
            binding.subscriptionContainer.visibility = View.GONE
            binding.availableSubscriptions.text =
                mainActivity.resources.getString(R.string.no_subscriptions_available)
        } else {
            val skuDetails = list[BillingClientLifecycle.subscriptionSku]
            skuDetails?.let {
                binding.productTitle.text = it.title
                binding.productDescription.text = it.description
                binding.productPrice.text = it.price
                val spannableString = SpannableString(mainActivity.getString(R.string.subscription_explanation))
                Linkify.addLinks(spannableString, Linkify.WEB_URLS)
                binding.trialSubscriptionText.text = spannableString
                binding.trialSubscriptionText.movementMethod=LinkMovementMethod.getInstance()
                binding.subscribeButton.setOnClickListener {
                    dialogWindowInterface.onSubscribeClicked()
                }

            }
        }
        builder.setView(binding.root)
        return builder.create()
    }

    fun getSubscribedDialog(
        mainActivity: MainActivity): AlertDialog {
        val builder = AlertDialog.Builder(mainActivity)

        val binding = DialogBuySubscriptionBinding.inflate(mainActivity.layoutInflater)

        binding.availableSubscriptions.visibility=View.GONE
        binding.productTitle.text = mainActivity.getString(R.string.already_subscribed)
        val spannableString = SpannableString(mainActivity.getString(R.string.cancel_subscription_info))
        Linkify.addLinks(spannableString, Linkify.WEB_URLS)
        binding.productDescription.text = spannableString
        binding.productDescription.movementMethod=LinkMovementMethod.getInstance()
        binding.productPrice.visibility=View.GONE
//        dialogCustomView.subscribe_button.text=mainActivity.getString(R.string.subscribed_text)
        binding.trialSubscriptionText.visibility=View.GONE
        binding.subscribeButton.visibility=View.GONE

        builder.setView(binding.root)
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
        val binding = DialogLoadingBinding.inflate(mainActivity.layoutInflater)

        binding.cancelButton.setOnClickListener {
            dialogWindowInterface.onCancelButton()
        }
        alertDialogBuilder.setView(binding.root).setCancelable(false)
        return alertDialogBuilder.create()
    }
}
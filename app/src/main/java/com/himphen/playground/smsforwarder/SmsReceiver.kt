package com.himphen.playground.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val from = messages.firstOrNull()?.displayOriginatingAddress
            ?: context.getString(R.string.unknown_sender)
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val statusStore = ForwardStatusStore(context)

        try {
            val inputData = Data.Builder()
                .putString(ForwardWorker.KEY_FROM, from)
                .putString(ForwardWorker.KEY_BODY, body)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(inputData)
                .addTag(ForwardWorker.TAG_FORWARD)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            statusStore.update(
                ForwardState.QUEUED,
                context.getString(R.string.sms_queued_status)
            )
        } catch (_: Exception) {
            statusStore.update(
                ForwardState.FAILED,
                context.getString(R.string.sms_queue_error)
            )
        }
    }
}

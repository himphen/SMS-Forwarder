package com.himphen.playground.smsforwarder

import android.content.Context
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

class ForwardWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    private val settingsStore = SecureSettingsStore(applicationContext)
    private val statusStore = ForwardStatusStore(applicationContext)
    private val telegramClient = TelegramClient()

    override suspend fun doWork(): Result {
        val settings = try {
            settingsStore.load()
        } catch (_: SettingsStorageException) {
            statusStore.update(
                ForwardState.FAILED,
                applicationContext.getString(R.string.secure_settings_unavailable)
            )
            return Result.failure(outputData(ERROR_STORAGE))
        }

        if (settings.botToken.isBlank() || settings.chatId.isBlank()) {
            statusStore.update(
                ForwardState.NOT_CONFIGURED,
                applicationContext.getString(R.string.not_configured_status)
            )
            return Result.success()
        }

        val templateError = MessageFormatter.validate(settings.template)
        if (templateError != null) {
            statusStore.update(ForwardState.CONFIGURATION_ERROR, templateError)
            return Result.failure(outputData(ERROR_TEMPLATE))
        }

        val from = inputData.getString(KEY_FROM).orEmpty().ifBlank {
            applicationContext.getString(R.string.unknown_sender)
        }
        val body = inputData.getString(KEY_BODY).orEmpty()
        val isTestMessage = inputData.getBoolean(KEY_IS_TEST, false)
        val message = try {
            MessageFormatter.format(settings.template, from, body)
        } catch (_: InvalidTemplateException) {
            statusStore.update(
                ForwardState.CONFIGURATION_ERROR,
                applicationContext.getString(R.string.saved_template_invalid)
            )
            return Result.failure(outputData(ERROR_TEMPLATE))
        }

        statusStore.update(
            ForwardState.SENDING,
            applicationContext.getString(R.string.sending_status_message)
        )

        return try {
            telegramClient.sendMessage(settings.botToken, settings.chatId, message)
            statusStore.update(
                ForwardState.SENT,
                if (isTestMessage) {
                    applicationContext.getString(R.string.test_delivered_status)
                } else {
                    applicationContext.getString(R.string.sms_forwarded_status)
                }
            )
            Result.success()
        } catch (error: TelegramApiException) {
            handleTelegramError(error)
        } catch (_: IOException) {
            handleRetryableFailure(
                applicationContext.getString(R.string.network_retry_status)
            )
        } catch (_: Exception) {
            statusStore.update(
                ForwardState.FAILED,
                applicationContext.getString(R.string.generic_failed_status)
            )
            Result.failure(outputData(ERROR_UNKNOWN))
        }
    }

    private fun handleTelegramError(error: TelegramApiException): Result {
        return when (error.kind) {
            TelegramFailureKind.RATE_LIMIT,
            TelegramFailureKind.SERVER -> {
                handleRetryableFailure(
                    if (error.kind == TelegramFailureKind.RATE_LIMIT) {
                        applicationContext.getString(R.string.rate_limit_retry_status)
                    } else {
                        applicationContext.getString(R.string.server_retry_status)
                    }
                )
            }

            TelegramFailureKind.INVALID_TOKEN -> {
                statusStore.update(
                    ForwardState.CONFIGURATION_ERROR,
                    applicationContext.getString(R.string.invalid_token_status)
                )
                Result.failure(outputData(ERROR_TOKEN))
            }

            TelegramFailureKind.FORBIDDEN -> {
                statusStore.update(
                    ForwardState.CONFIGURATION_ERROR,
                    applicationContext.getString(R.string.forbidden_status)
                )
                Result.failure(outputData(ERROR_CHAT))
            }

            TelegramFailureKind.INVALID_REQUEST -> {
                statusStore.update(
                    ForwardState.CONFIGURATION_ERROR,
                    applicationContext.getString(R.string.invalid_request_status)
                )
                Result.failure(outputData(ERROR_REQUEST))
            }

            TelegramFailureKind.UNKNOWN -> {
                statusStore.update(
                    ForwardState.FAILED,
                    applicationContext.getString(R.string.telegram_rejected_status)
                )
                Result.failure(outputData(ERROR_TELEGRAM))
            }
        }
    }

    private fun handleRetryableFailure(message: String): Result {
        return if (runAttemptCount < MAX_RETRIES) {
            statusStore.update(
                ForwardState.RETRYING,
                "$message (${runAttemptCount + 1}/$MAX_RETRIES)"
            )
            Result.retry()
        } else {
            statusStore.update(
                ForwardState.FAILED,
                applicationContext.getString(R.string.network_failed_status)
            )
            Result.failure(outputData(ERROR_NETWORK))
        }
    }

    private fun outputData(errorCode: String): Data {
        return Data.Builder()
            .putString(KEY_ERROR, errorCode)
            .build()
    }

    companion object {
        const val KEY_FROM = "from"
        const val KEY_BODY = "body"
        const val KEY_IS_TEST = "is_test"
        const val KEY_ERROR = "error"
        const val TAG_FORWARD = "sms-forward"
        const val TAG_TEST = "sms-forward-test"

        private const val MAX_RETRIES = 4
        private const val ERROR_STORAGE = "storage"
        private const val ERROR_TEMPLATE = "template"
        private const val ERROR_TOKEN = "token"
        private const val ERROR_CHAT = "chat"
        private const val ERROR_REQUEST = "request"
        private const val ERROR_TELEGRAM = "telegram"
        private const val ERROR_NETWORK = "network"
        private const val ERROR_UNKNOWN = "unknown"
    }
}

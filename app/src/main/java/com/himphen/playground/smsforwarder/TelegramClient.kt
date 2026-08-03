package com.himphen.playground.smsforwarder

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

enum class TelegramFailureKind {
    INVALID_TOKEN,
    FORBIDDEN,
    INVALID_REQUEST,
    RATE_LIMIT,
    SERVER,
    UNKNOWN
}

class TelegramApiException(
    val kind: TelegramFailureKind
) : Exception()

class TelegramClient(
    private val httpClient: OkHttpClient = DEFAULT_HTTP_CLIENT
) {
    @Throws(IOException::class, TelegramApiException::class)
    fun sendMessage(token: String, chatId: String, text: String) {
        val request = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(
                FormBody.Builder()
                    .add("chat_id", chatId)
                    .add("text", text)
                    .build()
            )
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw TelegramApiException(
                    kind = classifyFailure(response.code)
                )
            }
        }
    }

    private fun classifyFailure(statusCode: Int): TelegramFailureKind {
        return when (statusCode) {
            400 -> TelegramFailureKind.INVALID_REQUEST
            401 -> TelegramFailureKind.INVALID_TOKEN
            403 -> TelegramFailureKind.FORBIDDEN
            429 -> TelegramFailureKind.RATE_LIMIT
            in 500..599 -> TelegramFailureKind.SERVER
            else -> TelegramFailureKind.UNKNOWN
        }
    }

    companion object {
        private val DEFAULT_HTTP_CLIENT = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

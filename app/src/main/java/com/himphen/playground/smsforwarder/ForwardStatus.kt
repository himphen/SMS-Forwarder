package com.himphen.playground.smsforwarder

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit

enum class ForwardState {
    IDLE,
    QUEUED,
    SENDING,
    SENT,
    RETRYING,
    NOT_CONFIGURED,
    CONFIGURATION_ERROR,
    FAILED
}

data class ForwardStatus(
    val state: ForwardState = ForwardState.IDLE,
    val message: String = "",
    val updatedAt: Long = 0L
)

class ForwardStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<(ForwardStatus) -> Unit>()

    fun read(): ForwardStatus {
        val state = runCatching {
            ForwardState.valueOf(
                preferences.getString(KEY_STATE, ForwardState.IDLE.name)
                    ?: ForwardState.IDLE.name
            )
        }.getOrDefault(ForwardState.IDLE)

        return ForwardStatus(
            state = state,
            message = preferences.getString(KEY_MESSAGE, "").orEmpty(),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun update(state: ForwardState, message: String) {
        val status = ForwardStatus(
            state = state,
            message = message,
            updatedAt = System.currentTimeMillis()
        )
        preferences.edit {
            putString(KEY_STATE, status.state.name)
            putString(KEY_MESSAGE, status.message)
            putLong(KEY_UPDATED_AT, status.updatedAt)
        }

        val currentListeners = synchronized(listeners) { listeners.toList() }
        if (currentListeners.isEmpty()) return
        mainHandler.post {
            currentListeners.forEach { listener ->
                listener(status)
            }
        }
    }

    fun observe(listener: (ForwardStatus) -> Unit): () -> Unit {
        synchronized(listeners) {
            listeners += listener
        }
        return {
            synchronized(listeners) {
                listeners -= listener
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "sms_forwarder_status"
        private const val KEY_STATE = "state"
        private const val KEY_MESSAGE = "message"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}

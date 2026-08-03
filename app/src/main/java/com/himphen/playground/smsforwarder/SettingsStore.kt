package com.himphen.playground.smsforwarder

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sms_forwarder_settings"
)

private const val DATA_BOT_TOKEN_NAME = "bot_token"
private const val DATA_CHAT_ID_NAME = "chat_id"
private const val DATA_TEMPLATE_NAME = "message_template"

private val DATA_BOT_TOKEN = stringPreferencesKey(DATA_BOT_TOKEN_NAME)
private val DATA_CHAT_ID = stringPreferencesKey(DATA_CHAT_ID_NAME)
private val DATA_TEMPLATE = stringPreferencesKey(DATA_TEMPLATE_NAME)

data class ForwardSettings(
    val botToken: String = "",
    val chatId: String = "",
    val template: String = MessageFormatter.DEFAULT_TEMPLATE
) {
    fun isConfigured(): Boolean {
        return botToken.isNotBlank() &&
            chatId.isNotBlank() &&
            MessageFormatter.validate(template) == null
    }
}

class SettingsStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SecureSettingsStore(context: Context) {
    private val applicationContext = context.applicationContext
    private val dataStore = applicationContext.settingsDataStore

    private val aead: Aead by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createAead()
    }

    suspend fun load(): ForwardSettings = withContext(Dispatchers.IO) {
        try {
            val preferences = readPreferences()
            ForwardSettings(
                botToken = readEncrypted(preferences, DATA_BOT_TOKEN),
                chatId = readEncrypted(preferences, DATA_CHAT_ID),
                template = readEncrypted(preferences, DATA_TEMPLATE)
                    .ifBlank { MessageFormatter.DEFAULT_TEMPLATE }
            )
        } catch (error: SettingsStorageException) {
            throw error
        } catch (error: Exception) {
            throw SettingsStorageException("Unable to read secure settings.", error)
        }
    }

    suspend fun save(settings: ForwardSettings) = withContext(Dispatchers.IO) {
        try {
            writeSettings(settings)
        } catch (error: SettingsStorageException) {
            throw error
        } catch (error: Exception) {
            throw SettingsStorageException("Unable to save secure settings.", error)
        }
    }

    private suspend fun writeSettings(settings: ForwardSettings) {
        dataStore.edit { preferences ->
            preferences[DATA_BOT_TOKEN] = encrypt(settings.botToken, DATA_BOT_TOKEN_NAME)
            preferences[DATA_CHAT_ID] = encrypt(settings.chatId, DATA_CHAT_ID_NAME)
            preferences[DATA_TEMPLATE] = encrypt(settings.template, DATA_TEMPLATE_NAME)
        }
    }

    private suspend fun readPreferences(): Preferences {
        return dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .first()
    }

    private fun readEncrypted(
        preferences: Preferences,
        key: Preferences.Key<String>
    ): String {
        val encodedValue = preferences[key].orEmpty()
        if (encodedValue.isBlank()) return ""

        return try {
            decrypt(
                encodedValue = encodedValue,
                associatedData = key.name
            )
        } catch (error: GeneralSecurityException) {
            // Stale or corrupted ciphertext (e.g. from a prior encrypt bug); treat as unset.
            ""
        }
    }

    private fun encrypt(value: String, associatedData: String): String {
        val encrypted = aead.encrypt(
            value.toByteArray(StandardCharsets.UTF_8),
            associatedData.toByteArray(StandardCharsets.UTF_8)
        )
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(encodedValue: String, associatedData: String): String {
        val encrypted = Base64.decode(encodedValue, Base64.NO_WRAP)
        return String(
            aead.decrypt(
                encrypted,
                associatedData.toByteArray(StandardCharsets.UTF_8)
            ),
            StandardCharsets.UTF_8
        )
    }

    private fun createAead(): Aead {
        return try {
            AeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(
                    applicationContext,
                    TINK_KEYSET_NAME,
                    TINK_KEYSET_PREFS_NAME
                )
                .withKeyTemplate(KeyTemplates.get(TINK_KEY_TEMPLATE))
                .withMasterKeyUri(TINK_MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        } catch (error: Exception) {
            throw SettingsStorageException("Unable to initialize secure settings.", error)
        }
    }

    companion object {
        private const val TINK_KEYSET_NAME = "sms_forwarder_settings_keyset"
        private const val TINK_KEYSET_PREFS_NAME = "sms_forwarder_tink_keyset"
        private const val TINK_MASTER_KEY_URI =
            "android-keystore://sms_forwarder_tink_master_key"
        private const val TINK_KEY_TEMPLATE = "AES256_GCM"
    }
}

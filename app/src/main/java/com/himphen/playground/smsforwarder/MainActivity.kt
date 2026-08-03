package com.himphen.playground.smsforwarder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmsForwarderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmsForwarderScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsForwarderScreen() {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val secureSettingsLoadError = stringResource(R.string.secure_settings_load_error)
    val previewSampleSender = stringResource(R.string.preview_sample_sender)
    val previewSampleBody = stringResource(R.string.preview_sample_body)
    val smsPermissionRequired = stringResource(R.string.sms_permission_required)
    val smsPermissionGrantedMessage = stringResource(R.string.sms_permission_granted)
    val botTokenRequired = stringResource(R.string.bot_token_required)
    val chatIdRequired = stringResource(R.string.chat_id_required)
    val chatIdValidation = stringResource(R.string.chat_id_validation)
    val settingsReady = stringResource(R.string.settings_ready)
    val settingsSaved = stringResource(R.string.settings_saved)
    val secureSettingsSaveError = stringResource(R.string.secure_settings_save_error)
    val testSettingsRequired = stringResource(R.string.test_settings_required)
    val testMessageBody = stringResource(R.string.test_message_body)
    val testQueued = stringResource(R.string.test_queued)
    val testQueueError = stringResource(R.string.test_queue_error)
    val batteryAlreadyDisabled = stringResource(R.string.battery_already_disabled)
    val batteryScreenMissing = stringResource(R.string.battery_screen_missing)
    val batteryRequestBlocked = stringResource(R.string.battery_request_blocked)
    val settingsStore = remember(applicationContext) {
        SecureSettingsStore(applicationContext)
    }
    val settingsScope = rememberCoroutineScope()
    val statusStore = remember(applicationContext) {
        ForwardStatusStore(applicationContext)
    }

    var botToken by remember(settingsStore) {
        mutableStateOf("")
    }
    var chatId by remember(settingsStore) {
        mutableStateOf("")
    }
    var template by remember(settingsStore) {
        mutableStateOf(MessageFormatter.DEFAULT_TEMPLATE)
    }
    var savedSettings by remember(settingsStore) {
        mutableStateOf(ForwardSettings())
    }
    var tokenVisible by remember { mutableStateOf(false) }
    var smsPermissionGranted by remember(context) {
        mutableStateOf(hasSmsPermission(context))
    }
    var storageError by remember(settingsStore) {
        mutableStateOf<String?>(null)
    }
    var uiMessage by remember { mutableStateOf<String?>(null) }
    var status by remember(statusStore) { mutableStateOf(statusStore.read()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = context as? LifecycleOwner
    val currentContext by rememberUpdatedState(context)

    LaunchedEffect(settingsStore) {
        try {
            val initialSettings = settingsStore.load()
            botToken = initialSettings.botToken
            chatId = initialSettings.chatId
            template = initialSettings.template
            savedSettings = initialSettings
            storageError = null
        } catch (e: SettingsStorageException) {
            storageError = secureSettingsLoadError
        }
    }

    DisposableEffect(statusStore) {
        val removeListener = statusStore.observe { newStatus ->
            status = newStatus
        }
        onDispose(removeListener)
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    smsPermissionGranted = hasSmsPermission(currentContext)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            uiMessage = null
        }
    }

    val templateError = MessageFormatter.validate(template)
    val previewText = if (templateError == null) {
        MessageFormatter.format(
            template = template,
            from = previewSampleSender,
            body = previewSampleBody
        )
    } else {
        ""
    }
    val savedSettingsUsable = savedSettings.isConfigured() &&
            CHAT_ID_PATTERN.matches(savedSettings.chatId)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsPermissionGranted = granted
        if (!granted) {
            val message = smsPermissionRequired
            statusStore.update(ForwardState.FAILED, message)
            uiMessage = message
        } else {
            uiMessage = smsPermissionGrantedMessage
        }
    }

    fun saveSettings() {
        val trimmedToken = botToken.trim()
        val trimmedChatId = chatId.trim()
        val chatIdError = when {
            trimmedToken.isBlank() -> botTokenRequired
            trimmedChatId.isBlank() -> chatIdRequired
            !CHAT_ID_PATTERN.matches(trimmedChatId) ->
                chatIdValidation

            else -> null
        }

        if (chatIdError != null) {
            storageError = chatIdError
            uiMessage = chatIdError
            return
        }
        if (templateError != null) {
            storageError = templateError
            uiMessage = templateError
            return
        }

        val newSettings = ForwardSettings(
            botToken = trimmedToken,
            chatId = trimmedChatId,
            template = template
        )
        settingsScope.launch {
            try {
                settingsStore.save(newSettings)
                savedSettings = newSettings
                botToken = newSettings.botToken
                chatId = newSettings.chatId
                storageError = null
                statusStore.update(
                    ForwardState.IDLE,
                    settingsReady
                )
                uiMessage = settingsSaved
            } catch (_: SettingsStorageException) {
                storageError = secureSettingsSaveError
                uiMessage = secureSettingsSaveError
            }
        }
    }

    fun showTestResultToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    fun sendTestMessage() {
        if (!savedSettingsUsable) {
            val message = testSettingsRequired
            statusStore.update(ForwardState.CONFIGURATION_ERROR, message)
            showTestResultToast(message)
            return
        }

        val request = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    ForwardWorker.KEY_FROM to "Test",
                    ForwardWorker.KEY_BODY to testMessageBody,
                    ForwardWorker.KEY_IS_TEST to true
                )
            )
            .addTag(ForwardWorker.TAG_FORWARD)
            .addTag(ForwardWorker.TAG_TEST)
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

        try {
            statusStore.update(
                ForwardState.QUEUED,
                testQueued
            )
            WorkManager.getInstance(applicationContext).enqueue(request)
            settingsScope.launch {
                WorkManager.getInstance(applicationContext)
                    .getWorkInfoByIdFlow(request.id)
                    .first { workInfo -> workInfo?.state?.isFinished == true }
                val resultMessage = statusStore.read().message
                if (resultMessage.isNotBlank()) {
                    showTestResultToast(resultMessage)
                }
            }
        } catch (_: Exception) {
            val message = testQueueError
            statusStore.update(ForwardState.FAILED, message)
            showTestResultToast(message)
        }
    }

    fun requestBatteryOptimizationExemption() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        if (powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true) {
            uiMessage = batteryAlreadyDisabled
            return
        }

        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        } catch (_: ActivityNotFoundException) {
            uiMessage = batteryScreenMissing
        } catch (_: SecurityException) {
            uiMessage = batteryRequestBlocked
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (storageError != null) {
                ErrorCard(message = storageError!!)
            }

            StatusCard(
                status = status,
                permissionGranted = smsPermissionGranted,
                configured = savedSettingsUsable,
                onGrantClick = {
                    permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
            )

            ConfigurationCard(
                botToken = botToken,
                onBotTokenChange = {
                    botToken = it
                    storageError = null
                },
                tokenVisible = tokenVisible,
                onToggleTokenVisibility = { tokenVisible = !tokenVisible },
                chatId = chatId,
                onChatIdChange = {
                    chatId = it
                    storageError = null
                },
                template = template,
                onTemplateChange = {
                    template = it
                    storageError = null
                },
                templateError = templateError,
                previewText = previewText,
                onSave = ::saveSettings
            )

            ActionsCard(
                onSendTest = ::sendTestMessage,
                onDisableBattery = ::requestBatteryOptimizationExemption
            )

            HelpCard()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.action_required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: ForwardStatus,
    permissionGranted: Boolean,
    configured: Boolean,
    onGrantClick: () -> Unit
) {
    val containerColor = when {
        !permissionGranted -> MaterialTheme.colorScheme.errorContainer
        !configured -> MaterialTheme.colorScheme.tertiaryContainer
        status.state == ForwardState.FAILED ||
                status.state == ForwardState.CONFIGURATION_ERROR ->
            MaterialTheme.colorScheme.errorContainer

        status.state == ForwardState.SENT -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !permissionGranted -> MaterialTheme.colorScheme.onErrorContainer
        !configured -> MaterialTheme.colorScheme.onTertiaryContainer
        status.state == ForwardState.FAILED ||
                status.state == ForwardState.CONFIGURATION_ERROR ->
            MaterialTheme.colorScheme.onErrorContainer

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val title = when {
        !permissionGranted -> stringResource(R.string.permission_status_title)
        !configured -> stringResource(R.string.configuration_status_title)
        else -> statusTitle(status.state)
    }
    val description = when {
        !permissionGranted -> stringResource(R.string.permission_status_description)
        !configured -> stringResource(R.string.configuration_status_description)
        status.message.isNotBlank() -> status.message
        else -> stringResource(R.string.ready_description)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.forwarding_status),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (!permissionGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onGrantClick) {
                    Text(stringResource(R.string.grant_sms_permission))
                }
            }
        }
    }
}

@Composable
private fun ConfigurationCard(
    botToken: String,
    onBotTokenChange: (String) -> Unit,
    tokenVisible: Boolean,
    onToggleTokenVisibility: () -> Unit,
    chatId: String,
    onChatIdChange: (String) -> Unit,
    template: String,
    onTemplateChange: (String) -> Unit,
    templateError: String?,
    previewText: String,
    onSave: () -> Unit
) {
    val chatIdError = when {
        chatId.isBlank() -> stringResource(R.string.chat_id_required)
        !CHAT_ID_PATTERN.matches(chatId.trim()) ->
            stringResource(R.string.chat_id_invalid)

        else -> null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.telegram_configuration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = botToken,
                onValueChange = onBotTokenChange,
                label = { Text(stringResource(R.string.bot_token)) },
                supportingText = { Text(stringResource(R.string.bot_token_support)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (tokenVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                ),
                trailingIcon = {
                    TextButton(onClick = onToggleTokenVisibility) {
                        Text(
                            stringResource(
                                if (tokenVisible) R.string.hide else R.string.show
                            )
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = chatId,
                onValueChange = onChatIdChange,
                label = { Text(stringResource(R.string.chat_id)) },
                isError = chatIdError != null,
                supportingText = {
                    Text(
                        chatIdError
                            ?: stringResource(R.string.chat_id_support)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = template,
                onValueChange = onTemplateChange,
                label = { Text(stringResource(R.string.message_template)) },
                isError = templateError != null,
                supportingText = {
                    Text(templateError ?: stringResource(R.string.template_support))
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                keyboardOptions = KeyboardOptions.Default
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = previewText.ifBlank {
                            stringResource(R.string.preview_empty)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_settings))
            }
        }
    }
}

@Composable
private fun ActionsCard(
    onSendTest: () -> Unit,
    onDisableBattery: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.actions),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSendTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.send_test_message))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onDisableBattery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.review_battery_optimization))
            }
        }
    }
}

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = stringResource(R.string.how_to_find_chat_id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.chat_id_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun statusTitle(state: ForwardState): String {
    return when (state) {
        ForwardState.IDLE -> stringResource(R.string.ready_status)
        ForwardState.QUEUED -> stringResource(R.string.queued_status)
        ForwardState.SENDING -> stringResource(R.string.sending_status)
        ForwardState.SENT -> stringResource(R.string.delivered_status)
        ForwardState.RETRYING -> stringResource(R.string.retrying_status)
        ForwardState.NOT_CONFIGURED -> stringResource(R.string.configuration_required_status)
        ForwardState.CONFIGURATION_ERROR -> stringResource(R.string.configuration_error_status)
        ForwardState.FAILED -> stringResource(R.string.failed_status)
    }
}

private fun hasSmsPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
}

private val CHAT_ID_PATTERN = Regex("-?\\d+")

@Composable
fun SmsForwarderTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF9AD9FF),
        onPrimary = Color(0xFF00344A),
        primaryContainer = Color(0xFF164D66),
        onPrimaryContainer = Color(0xFFC8EAFF),
        secondary = Color(0xFFB7E8D2),
        onSecondary = Color(0xFF003829),
        secondaryContainer = Color(0xFF1C4F40),
        onSecondaryContainer = Color(0xFFD1FBE7),
        tertiary = Color(0xFFFFB5C5),
        onTertiary = Color(0xFF5B1128),
        tertiaryContainer = Color(0xFF762A43),
        onTertiaryContainer = Color(0xFFFFD9E1),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0B1218),
        onBackground = Color(0xFFE0E8EF),
        surface = Color(0xFF0B1218),
        onSurface = Color(0xFFE0E8EF),
        surfaceVariant = Color(0xFF3F484F),
        onSurfaceVariant = Color(0xFFBFC8D0),
        outline = Color(0xFF89929A)
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun SmsForwarderScreenPreview() {
    SmsForwarderTheme {
        Surface {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

# SMS Forwarder

<img src="assets/playstore-icon.png" width="100" height="100" alt="App Icon">

SMS Forwarder is an Android app that forwards incoming SMS messages to a Telegram chat through the Telegram Bot API. It is designed for private, self-hosted use and is distributed as an APK through GitHub Releases.

## Features

- Joins multipart SMS fragments before forwarding.
- Queues work with WorkManager when the device is offline.
- Retries temporary network, rate-limit, and Telegram server failures with a finite retry limit.
- Shows configuration and delivery errors in the app UI instead of relying on Logcat.
- Supports a custom plain-text message template with `{from}` and `{body}` placeholders.
- Stores the Bot Token, Chat ID, and template in Preferences DataStore with Tink AEAD; the Tink keyset is protected by Android Keystore.
- Uses a fixed dark Material 3 interface.
- Does not include application data in Android backups or device transfers.

## How it works

```text
Android SMS broadcast
  └── SmsReceiver
        └── WorkManager job
              └── ForwardWorker
                    └── Telegram Bot API
```

The app does not require a server. SMS content is placed into a local WorkManager job and sent directly from the Android device to Telegram.

## Requirements

- Android device running Android 8.0 / API 26 or newer.
- A working SIM and the `RECEIVE_SMS` permission.
- JDK 17.
- Android SDK Platform 37 and Android Build Tools installed.
- Android Studio or a shell environment that can run the included Gradle Wrapper.
- A Telegram bot and the destination chat ID.

This application uses a sensitive SMS permission. Google Play distribution may be restricted by current Play policy, so this repository targets GitHub Releases and APK side-loading.

## Telegram setup

1. Open Telegram and search for **@BotFather**.
2. Send `/newbot` and follow the prompts.
3. Copy the Bot Token. Treat it like a password.
4. Send a message to the new bot.
5. Open the following URL in a browser, replacing the placeholder:

   ```text
   https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
   ```

6. Find the numeric `id` inside the `chat` object. That is the Chat ID.

For groups or channels, add the bot to the destination and grant the access it needs. Group Chat IDs commonly start with `-100`; the app accepts negative numeric IDs.

Do not commit or share the Bot Token. If it is exposed, revoke it with BotFather and create a replacement.

## Build and install locally

Clone the repository, connect an Android device with USB debugging enabled, and run:

```bash
./gradlew clean assembleDebug
./gradlew installDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can also open the project in Android Studio and run the `app` configuration.

The published application ID is `com.himphen.playground.smsforwarder`. If you are migrating from an older build with a different application ID, Android treats it as a separate app and you will need to configure it again.

## First-launch setup

1. Install and open SMS Forwarder.
2. Enter the Bot Token and Chat ID.
3. Set the message template. The default is:

   ```text
   SMS from {from}:
   {body}
   ```

4. Tap **Save Settings Securely**.
5. Grant SMS permission.
6. Review battery optimization settings if the device manufacturer limits background work.
7. Send a test message and wait for the UI to report delivery.

The app does not claim that every Android manufacturer will preserve background behavior indefinitely. Battery policy, force-stop, revoked permissions, and vendor-specific restrictions can affect delivery.

## Message templates

Templates are plain text. The supported placeholders are:

- `{from}` — originating phone number or sender ID.
- `{body}` — the complete joined SMS body.

Unknown placeholders and empty templates are rejected. Telegram messages longer than 4096 characters are truncated with a visible `[message truncated]` marker.

## Error handling

- Network failures, HTTP 429 responses, and HTTP 5xx responses are retried with WorkManager backoff.
- Retries stop after a finite number of attempts.
- Invalid tokens, inaccessible chats, invalid Chat IDs, and invalid requests are shown as configuration errors and are not retried forever.
- An SMS received before configuration is complete is not retained for later delivery.
- The UI reports queued, sending, delivered, retrying, and failed states. A queued test message is not reported as delivered until the worker succeeds.

## Privacy and security

This app forwards the content of every received SMS to the configured Telegram chat. SMS messages may contain passwords, one-time codes, banking alerts, health information, or other sensitive data. Do not use this app if that data flow is not appropriate for your threat model.

- Bot Token, Chat ID, and the template are encrypted with Tink AEAD before being stored in Preferences DataStore; the Tink keyset is protected by Android Keystore.
- Application data is excluded from Android cloud backup and device transfer.
- The app intentionally does not write SMS content, phone numbers, Bot Tokens, or Telegram response bodies to Logcat.
- Telegram and the Android device remain separate services with their own security and retention policies.
- Changing or uninstalling the app can remove access to the locally stored settings. Keep a secure recovery process for your Bot Token.

## GitHub Releases

Pull requests and pushes to the main development branch run the GitHub Actions build and lint workflow. A GitHub Release is created only when a tag matching `vMAJOR.MINOR.PATCH`, such as `v1.0.0`, is pushed.

The release workflow removes the optional `v` prefix, validates the release version, and sets the Android version name to:

```text
Hong Kong Style French Toast 1.0.0
```

Set the repository variable `ANDROID_VERSION_CODE_BASE` to a non-negative integer. Each release derives `ANDROID_VERSION_CODE` by adding the workflow run number to this base value.

Repository maintainers must configure these GitHub Actions Secrets before creating a release:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow decodes the keystore only in the temporary GitHub-hosted runner, builds a signed APK, generates a SHA-256 checksum, and uploads both files to the GitHub Release. It only publishes GitHub Release assets; it does not upload to Google Play or build an AAB.

To publish a release:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Verify a downloaded artifact locally:

```bash
shasum -a 256 SMS-Forwarder-1.0.0.apk
```

The signing keystore is required for future updates. Store a secure offline backup; losing it means existing installations cannot receive normal signed updates.

## Permissions

- `RECEIVE_SMS`: receive incoming SMS broadcasts.
- `INTERNET`: send messages to the Telegram Bot API.
- `RECEIVE_BOOT_COMPLETED`: allow WorkManager to restore scheduled work after a reboot.

## Limitations

- The app forwards all received SMS; there is no sender allow-list or content filter.
- SMS delivery can be delayed or lost when permission is revoked, the app is force-stopped, the device is offline beyond the retry window, or the manufacturer restricts background execution.
- Telegram rate limits, chat permissions, outages, and API changes can prevent delivery.
- Telegram's message length limit applies to the final formatted message.
- Duplicate delivery is possible if a network response is lost after Telegram accepts a message and WorkManager retries the job.
- This project does not promise Google Play eligibility for its SMS permission usage.

## Contributing and support

Before opening an issue, include the Android version, device manufacturer, app version, and the safe UI error state. Never include SMS contents, Bot Tokens, Chat IDs, or screenshots containing secrets.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

# Google Play submission notes — TG Media Library 1.0.0

## Binary

- Application ID: `pt.nunosid.tgmedialibrary`
- Version name: `1.0.0`
- Version code: `11`
- Minimum SDK: 26
- Target SDK: 36 (Android 16)
- Distribution format: Android App Bundle (`.aab`)
- App name: **TG Media Library**
- Independent application; not affiliated with or endorsed by Telegram.

## App access for Google Play review

The app requires access to a Telegram account to demonstrate its core media-library functionality. In Play Console > Policy > App content > App access, select that some or all functionality is restricted and provide a dedicated reusable test Telegram account plus all information needed by the reviewer.

The reviewer flow is:

1. Launch **TG Media Library**.
2. On first launch, choose any 6–12 digit privacy PIN and confirm it.
3. Enter the supplied Telegram API ID and API Hash.
4. Enter the supplied test phone number.
5. Complete Telegram authentication using the reusable review credentials/instructions supplied in Play Console.
6. The media library loads the test account's media. Filters, playback, fullscreen, download and sharing can then be reviewed.

Do not submit personal or production Telegram credentials to Play Console. Use a dedicated review/test account and make sure the access method remains valid for the duration of review.

## Privacy policy URL

Use the public privacy policy committed to this repository, for example:

`https://github.com/NunoSid/TG-MediaDonwloader/blob/telegram-media-library/PRIVACY_POLICY.md`

## Data safety draft

Review the final Play Console wording before submission. Based on the current codebase:

- No advertising SDK.
- No developer-operated analytics SDK.
- No developer backend receives Telegram media or credentials.
- Authentication and Telegram content are exchanged directly with Telegram/TDLib as required for core functionality.
- API credentials and the TDLib database encryption key are stored locally using Android Keystore-backed encryption.
- The privacy PIN is not stored as plaintext.
- Media may be temporarily cached locally for playback/download/share and is purged by privacy controls.
- Media deliberately exported to Downloads remains until the user deletes it.

## Store listing positioning

Describe the application accurately as a private local media browser/library for the user's own Telegram account. Do not describe or visually present it as a calculator, a hidden vault, or an official Telegram application.

Suggested short description:

> Browse, filter, play and save media from your own Telegram account, with a local privacy lock.

Suggested disclosure:

> TG Media Library is an independent app and is not affiliated with or endorsed by Telegram.

# Telegram Media Library — Android

Android client focused on browsing and viewing videos already available in your Telegram account. It is deliberately **not a downloader**.

## What the MVP does

- Login to a normal Telegram user account through TDLib.
- Global video catalogue across Telegram chats.
- Metadata-only library index: conversation, date, duration, size, resolution, filename and caption.
- Search by filename, caption or chat.
- Sort by newest/oldest, largest/smallest and longest/shortest.
- Filters by conversation, size and duration.
- In-app video player.
- No export/download button and no user-visible media files.
- Player asks TDLib only for the byte range currently needed and deletes TDLib's temporary local file when playback closes.

## Important technical meaning of “no downloads”

A remote video cannot be displayed without receiving bytes. The app therefore buffers temporary chunks for playback. It does **not** create a permanent download, does not expose a downloaded file, and deletes the TDLib local media file after playback.

## Setup

1. Install Android Studio / JDK 17 and use Gradle 8.9.
2. Run `scripts/fetch-tdlib.ps1` on Windows (or `scripts/fetch-tdlib.sh` on macOS/Linux).
3. Copy `local.properties.example` to `local.properties`.
4. Set your Android SDK path.
5. Create your own Telegram `api_id` and `api_hash` at `https://my.telegram.org` -> **API development tools** and place them in `local.properties`.
6. Open the `android` folder in Android Studio and run the `app` configuration.

Do not commit `local.properties` or `app/libs/core-release.aar`; both are gitignored.

## Architecture

- Kotlin + Jetpack Compose
- TDLib user client (MTProto)
- Media3 / ExoPlayer
- `SearchMessagesFilterVideo` for the global catalogue
- On-demand `DownloadFile(offset, limit)` ranges used as a private streaming buffer
- `DeleteFile` when the player closes

## Production hardening still recommended

- Build TDLib from official Telegram source in CI instead of depending on a community prebuilt AAR.
- Add an encrypted credential/session backup strategy if the app is distributed beyond one personal device.
- Add date-range and chat multi-select filters.
- Add paging prefetch and a persistent metadata index for very large accounts.
- Add tests around sparse partial-file reads and seeking.

This project is an independent Telegram API client and is not affiliated with Telegram.

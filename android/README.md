# Telegram Media Library — Android

Android client focused on browsing and viewing videos already available in your Telegram account. It is deliberately **not a downloader**.

## What it does

- Login to a normal Telegram user account through TDLib.
- API ID/API Hash are entered on first launch and kept only in app-private storage on the device.
- Global video catalogue across Telegram chats.
- Metadata: conversation, date, duration, size, resolution, filename and caption.
- Search by filename, caption or chat.
- Sort by newest/oldest, largest/smallest and longest/shortest.
- Filters by conversation, size and duration.
- In-app video player.
- No download/export button and no user-visible media files.
- Player requests only the byte ranges it needs and deletes TDLib's temporary local media file when playback closes.

## What “no downloads” means technically

A remote video cannot be displayed without transferring bytes. Playback therefore uses temporary TDLib cache chunks. The app does not create a permanent media download, does not expose a downloaded file, and removes the TDLib cached file after playback.

## Install / use

1. Install the APK.
2. Create your own Telegram `api_id` and `api_hash` at `https://my.telegram.org` → **API development tools**.
3. Enter them in the first screen.
4. Enter your Telegram phone number, login code and 2FA password if applicable.
5. Browse, filter and play videos.

## Build from source

1. JDK 17 + Android SDK 35 + Gradle 8.9.
2. Run `scripts/fetch-tdlib.ps1` (Windows) or `scripts/fetch-tdlib.sh` (macOS/Linux).
3. Open the `android` folder in Android Studio and build `app`.

The GitHub Actions workflow on branch `telegram-media-library` performs the same clean build and publishes a debug APK artifact.

## Architecture

- Kotlin + Jetpack Compose
- TDLib user client (MTProto)
- Media3 / ExoPlayer
- `SearchMessagesFilterVideo` for the global catalogue
- On-demand `DownloadFile(offset, limit)` ranges used only as streaming cache
- `DeleteFile` when playback closes

For a production release, TDLib should preferably be built from official Telegram source in CI instead of relying on the convenience prebuilt AAR used by this MVP.

This project is an independent Telegram API client and is not affiliated with Telegram.

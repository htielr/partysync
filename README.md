# PartySync

A native Android client for syncing files between your phone and a self-hosted [copyparty](https://github.com/9001/copyparty) file server.

PartySync lets you mirror folders on your phone to your own server automatically, share files to it straight from the Android share sheet, and browse the server's folder tree from within the app — all without relying on a third-party cloud provider.

## Features

- **One-way and two-way folder sync** — pick a folder on your phone (via SAF) and map it to a path on the server. Two-way sync does a 3-way merge (baseline vs. local vs. remote); conflicts are kept as both copies rather than silently overwritten. Deletes never propagate automatically — a delete on one side gets restored from the other side on the next sync, by design.
- **Multi-server support** — configure multiple named copyparty servers, each folder mapping targets one of them.
- **Share-to-copyparty** — share any file(s) from the Android share sheet directly to a folder on your server.
- **Chunked resumable uploads (up2k)** — share-uploads use copyparty's real `up2k` protocol: hashed chunks, true resume after interruption, and a progress notification with pause/resume/cancel actions.
- **Interactive server folder browser** — browse the server's actual folder tree live (instead of typing a path by hand) when choosing where to upload or sync, on both the share screen and the add/edit folder-mapping screen.

## Requirements

- A running copyparty server (Docker or otherwise), reachable from your phone.
- Android 8.0 (API 26) or newer.
- JDK 17 and the Android SDK to build from source.

## Building

```bash
./gradlew assembleDebug      # build a debug APK
./gradlew testDebugUnitTest  # run unit tests
./gradlew installDebug       # install to a connected/adb-paired device
```

`local.properties` (with `sdk.dir` pointing at your Android SDK) is required and gitignored — Android Studio creates it automatically on first open, or create it by hand.

## Architecture

- Kotlin + Jetpack Compose, Hilt for DI, Room for local persistence, WorkManager for background sync/uploads.
- Server credentials are stored in `EncryptedSharedPreferences`, not Room, to keep them encrypted at rest.
- `data/remote/Up2kClient.kt` and `ChunkBatching.kt` implement copyparty's up2k chunked-upload wire protocol (chunk-size algorithm, SHA-512 chunk hashing, handshake/upload requests) ported from copyparty's own source.
- `sync/SyncEngine.kt` + `domain/usecase/SyncPlanner.kt` implement the two-way 3-way-merge sync logic; `sync/RemoteFolderScanner.kt` lists remote state via copyparty's `?ls` JSON API.

## Status

Regular folder sync (`SyncEngine`) still uses plain HTTP PUT uploads. Share-to-copyparty already has the full up2k chunked/resumable treatment; porting that same treatment onto regular folder sync is the next planned piece of work.

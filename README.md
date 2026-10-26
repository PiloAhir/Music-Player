# Sur Player — Android project

Sur Player is an offline-first local audio player starter project for Android.

## Included features
- Import multiple audio files through Android's file picker (MP3, M4A and other formats supported by Android/Media3)
- Background playback using Media3 `MediaSessionService`
- Media notification and lock-screen controls provided by Android Media3
- Play/pause, previous/next, repeat-all and repeat-one
- Favorites, search, reorder tracks up/down, remove tracks from the library
- Custom cover image per track
- Import plain text lyrics or timestamped `.lrc` lyrics
- LRC timed-line highlighting while audio plays
- Saves library order, favorites, cover URI and lyrics locally
- GitHub Actions workflow for building a debug APK

## Uploading to GitHub
Upload the **contents** of this project ZIP to the root of your repository, keeping all folders and paths. Do not upload only the ZIP itself. The `.github/workflows/android.yml` file must be in the `.github/workflows/` folder.

## Build APK
1. Push/commit the project files to the `main` branch.
2. Open **Actions** → **Build Sur Player APK**.
3. Wait for the workflow to finish successfully.
4. Open the completed workflow run and download the `sur-player-debug-apk` artifact.
5. Extract the downloaded artifact ZIP to find `app-debug.apk`.

## Notes
- Imported audio uses Android's document picker and persisted URI permission where supported. If the original audio file is deleted/moved or its access is revoked, that track may no longer play.
- Lyrics with `[mm:ss.xx]` timestamps (`.lrc`) can be highlighted approximately in sync. Plain `.txt` lyrics are displayed as static text.
- This project requires internet access for Gradle dependencies during the GitHub Actions build.
- This is a debug APK, not a Play Store signed release build.

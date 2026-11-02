# World TV

Independent Android directory for live TV, online radio, and webcams. It runs on phones, Android TV, and Google TV. World TV does not host streams and is not an official client of the channels or of Famelack.

## Download

<!-- release:start -->
**APK 1.0.3** (2026-11-03): [WorldTV-1.0.3.apk](https://github.com/Valijonzoda/World-TV/releases/download/v1.0.3/WorldTV-1.0.3.apk)
<!-- release:end -->

A push to `master` publishes a GitHub Release. The patch version increases by `0.0.1` unless the commit message contains `version: 1.2.0` or the workflow is started manually with a version.

## Dataset

Listings are loaded from the public repository [Valijonzoda/famelack-data](https://github.com/Valijonzoda/famelack-data) (MIT, Copyright 2026 Famelack LLC). The app does not ship the catalog inside the APK. It downloads only the files it needs and keeps a copy on the device so listings still open offline.

Verified layout:

```text
tv|radio|webcams/
  raw/
    countries_metadata.json
    countries/{code}.json
    categories/{name}.json
  compressed/
    same paths, gzip bytes with a .json name
```

`countries_metadata.json` is an object keyed by uppercase code:

```json
{
  "AD": {
    "country": "Andorra",
    "capital": "Andorra la Vella",
    "timeZone": "Europe/Andorra",
    "hasChannels": true,
    "channelCount": 2
  }
}
```

Each country file is a JSON array. Every item uses the same fields:

```json
{
  "nanoid": "mG0AeH9doLAPIb",
  "name": "KK TV",
  "sources": {
    "streams": ["https://example.com/playlist.m3u8"],
    "youtube": ["https://www.youtube-nocookie.com/embed/VIDEO_ID"]
  },
  "languages": ["por"],
  "country": "ao",
  "isGeoBlocked": false
}
```

`sources.streams` and `sources.youtube` are optional. There is no logo field, so cards use generated artwork. TV mixes HLS or progressive streams with some YouTube embeds. Radio entries are direct audio streams. Webcam entries in the current dataset are YouTube embed URLs only.

The United Kingdom is filed as `uk`, not `gb`. Channel counts on the country list come from `channelCount`.

World TV requests:

- `{kind}/raw/countries_metadata.json`
- `{kind}/raw/countries/{lowercase-code}.json`

Base URL: `https://raw.githubusercontent.com/Valijonzoda/famelack-data/main/`

To point the app at your own fork, set `datasetBase` in `gradle.properties`:

```properties
datasetBase=https://raw.githubusercontent.com/YOUR_USER/famelack-data/main/
```

## Architecture

- `domain` models, filtering, playback planning, repository interfaces
- `data` JSON parsing, OkHttp downloads, file cache, DataStore settings, favorites file
- `ui` Material 3 phone layout and a separate D-pad layout for television
- `playback` Media3 ExoPlayer for HLS and progressive audio/video, plus a foreground media session for radio

Direct streams play in ExoPlayer. YouTube embed URLs open in a WebView. If every address fails, the player explains that the source may be offline, region locked, or unsupported. `isGeoBlocked` is shown before playback; the app still tries the stream.

Favorites, theme, playback preferences, and the last country selection stay on the device. There is no account.

## Build

Install JDK 17 and Android SDK 36, then:

```bash
./gradlew test assembleDebug assembleRelease bundleRelease
```

Release builds are unsigned unless `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` are set in the environment. Do not commit a keystore.

GitHub Actions builds the debug APK, release APK, and release AAB, runs unit tests, and uploads the artifacts. A tag named `v*` also creates a GitHub Release.

## Rights

The dataset license allows reuse of the listing data. That license does not make the third-party streams official or owned by World TV. Some streams are region locked. See `play/privacy-policy.md` before publishing to Google Play.

Outfit is included under the SIL Open Font License. See `third_party/outfit/OFL.txt`.

## Screenshots

![TV Channels](03-tv-channels.png)
![TV Player](04-tv-player.png)
![Radio Player](05-radio-player.png)
![Webcams](06-webcams.png)
![Webcam Player](07-webcam-player.png)
![Favorites](08-favorites.png)
![Settings](09-settings.png)

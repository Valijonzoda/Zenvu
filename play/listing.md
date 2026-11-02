# Store listing

## Title

World TV

## Short description

Browse live TV, radio, and webcams from public listings.

## Full description

World TV is an independent directory for phones, Android TV, and Google TV. Switch between TV, radio, and webcams, search by name, and filter by country and language.

The catalog is downloaded from the public Famelack dataset. World TV does not host the video or audio, does not require an account, and does not claim that third-party channels or streams are officially licensed. Some listings only work in certain regions, and some webcam sources are embedded players that may refuse to play inside an app.

Favorites and settings stay on the device.

## Data safety notes for the Play Console

- Data collected by the developer: none
- Data stored on device: favorites, settings, cached listings
- Data sent to third parties when the user plays or refreshes: IP address and normal player requests go to GitHub, stream hosts, and YouTube embeds. The app does not add its own analytics.

## Graphics to capture before upload

Phone, portrait, at least 1080 px on the short side:

1. TV catalog with country selected
2. Country search
3. Radio player
4. Full-screen TV playback
5. Settings

Android TV, 1920x1080:

1. Left rail and channel grid with a focused card
2. Country list with focus
3. Full-screen playback controls

Do not submit a screenshot that implies the streams are official broadcaster apps.

## Release

Upload the release AAB from `app/build/outputs/bundle/release/`. Sign it with an upload key that is not stored in this repository. Play App Signing should hold the app signing key.

Content rating and declarations must say the app links to third-party live streams and does not guarantee broadcast rights.

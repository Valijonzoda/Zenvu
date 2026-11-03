# Data safety

Use these answers in the Play Console Data safety form. Zenvu does not use ads, analytics, or billing.

Data is sent to Firebase (Google) only after the user creates an account or signs in. Watching without an account does not create a Firebase user.

## Collected

| Play data type | Collected | Shared | Required | Purpose | Retention |
| --- | --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | No | Required for an account, optional to use the app | Account management | Until the user deletes the account |
| Personal info → User IDs | Yes. Firebase Authentication uid | No | Required for an account | Account management | Until the user deletes the account |
| App activity → App interactions | Yes. Saved favorites: kind (`tv`, `radio`, or `webcams`), channel id, name, country, and the time they were saved | No | Optional. Only if the user saves a channel | App functionality | Until the user removes the favorite or deletes the account |

Password: the user types it in the app and Zenvu sends it to Firebase Authentication over TLS. Zenvu does not write the password to Firestore, does not keep it on a Zenvu server, and does not log it. Do not add a separate stored-password row.

Display name: only if Google Sign-In provides one. Same purpose and retention as the email.

## Not collected

Location, contacts, photos, audio, files, calendar, financial info, web browsing history, health, messages, installed apps, and advertising ID.

Zenvu does not ship the Firebase Analytics or Ads SDK. Do not declare ads.

## Processing

- Encrypted in transit: yes (HTTPS to Firebase, GitHub, and stream hosts).
- Users can request deletion: yes. In the app: Settings → Account → Delete account. Web: `https://valijonzoda.github.io/Zenvu/delete-account/`
- Data sold: no.
- Shared with third parties: no. Firebase processes the account and favorites for Zenvu. Play treats a service provider as not “shared” when it only processes data on your behalf. Ephemeral: no.
- Device or other IDs: do not declare an advertising ID. Firebase Authentication may process an app-instance identifier as part of signing in. If the Play form asks about that SDK, follow Google’s current Firebase Authentication declaration and do not add an advertising ID.

## Still disclosed elsewhere

Refreshing the catalog sends the device IP address to GitHub. Playback sends the device IP address to the stream host or YouTube. Those are user-initiated connections to the service the user asked to open, not data Zenvu stores in an account.

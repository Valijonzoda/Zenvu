# Account deletion

Public page: https://valijonzoda.github.io/Zenvu/delete-account/

Zenvu stores an optional account in Firebase. You can watch channels without an account. Favorites are stored only after you sign in.

## Delete the account in the app

1. Open Zenvu.
2. Go to Settings.
3. In Account, choose Delete account.
4. Confirm. If Android asks, enter your password or sign in with Google again.
5. Zenvu deletes the Firebase Authentication user and the Firestore data under `users/{uid}`, including saved favorites.

## If you cannot open the app

Send an email to valijonzodafirdavs@gmail.com from the address on the account, with the subject "Delete my account". The account and its favorites are deleted from Firebase Authentication and Cloud Firestore. Confirmation is sent by email, normally within 30 days.

This page does not require a sign-in.

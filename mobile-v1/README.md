# SmartCRM field app (Flutter, Android & iOS)

The mobile app from SRS §6.11 for salespeople in the field. It uses the same SmartCRM backend
and sign-in as the web app.

| Requirement | In the app |
|---|---|
| FR-M.1 Log a visit | **Today → Log visit**: customer (optional), date and time, purpose, outcome, notes, and location if allowed |
| FR-M.2 Assigned leads and opportunities | **Leads** and **Deals** tabs list what is assigned to you. Tap one to change its stage (skipping lead stages asks first; a lost deal asks why) |
| FR-M.3 Meeting report | **Meetings** shows the last two weeks and upcoming meetings. Tap a meeting that has started to file its outcome, notes and next steps |
| FR-M.4 Push notifications | New assignments, follow-ups that fall due, and manager messages are pushed to the phone and kept in the **Inbox** |
| FR-M.5 Attendance | **Today → Check in / Check out**, with location if allowed. This feeds Module 8 attendance |
| FR-M.6 Offline with automatic sync | Lists are cached on the phone. Changes are saved locally and synced in order when the connection returns, when the app reopens, every 2 minutes, or on pull-to-refresh. The cloud icon shows anything waiting, and any change the server refused can be retried or discarded |

## Run

```sh
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080      # Android emulator → local backend
flutter run --dart-define=API_BASE_URL=https://api.your-domain   # real devices / release
```

Debug builds allow plain `http` so they can reach a local backend. Release builds need an
`https` API URL.

## Push notifications (Firebase)

Push uses Firebase Cloud Messaging, which also delivers to iOS through APNs. Until this setup is
done, the app works normally and notifications only appear in the Inbox.

1. Create a Firebase project. Add an Android app (`com.cubeage.smartcrm.smartcrm_mobile`) and an
   iOS app with the same bundle id.
2. Put `google-services.json` in `android/app/` and `GoogleService-Info.plist` in `ios/Runner/`.
   Both are git-ignored, so add them locally or in CI.
3. iOS only: in Xcode, enable the **Push Notifications** and **Background Modes → Remote
   notifications** capabilities. Then upload your APNs key in Firebase → Project settings →
   Cloud Messaging.
4. Backend: create a service-account key (Firebase → Project settings → Service accounts). Store
   the JSON file on the server, outside the repository, and set `FCM_CREDENTIALS_FILE` to its path.

## Tests

```sh
flutter analyze
flutter test
```

`test/sync_test.dart` covers the offline queue and the sync rules. `test/widget_test.dart`
covers sign-in.

## Notes

- The token is kept in the phone's secure storage (Keychain/Keystore). Cached CRM data is kept in
  the app's private folder and is wiped on sign-out.
- Times are sent as the phone's local time with no time zone, the same as the backend stores
  them. The phone and the server should use the same time zone.
- A check-in or check-out recorded offline must sync within 48 hours.

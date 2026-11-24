# Journey-change notifications (FCM)

This Android implementation works with the backend's standalone
`journey-notifier` daemon (see
`catenary-backend/docs/journey-notifier.md`).

Saving a journey bookmarks its full local itinerary, then schedules a
WorkManager reconciliation with the backend. Removing the bookmark removes
that journey from the next full-device subscription sync. FCM wakes the app
for foreground messages, and Google Play services shows notification
messages in the background. Saved-trip disruption alerts do not require
a foreground service. **When the rider explicitly starts a journey**, a separate
foreground service displays an ongoing leg-progress notification and refreshes
it while the app is backgrounded. This service never requests background GPS
or starts a second WebSocket; its progress is estimated from scheduled times
and uses the latest Ramonda prediction when already available to the app.
Android 14+ declares the `specialUse` foreground service type for this
user-initiated guidance, which requires declaration and review in Play Console.

## Firebase project and Android setup

1. Use the same Firebase project for the Android application and the
   backend FCM HTTP v1 sender. The checked-in Firebase client config
   currently identifies project `catenary-android` and application ID
   `com.catenarymaps.catenary`.
2. Enable Firebase Cloud Messaging in the Firebase/Google Cloud project.
   Google Play services should be present for delivery.
3. Keep `app/google-services.json` synced to the Firebase project's
   Android application; it contains public client identifiers, not the
   service-account private key. The **service-account JSON belongs only
   on the backend**.
4. Deploy the notifier behind a TLS reverse proxy. Set the Android build's
   public endpoint in `~/.gradle/gradle.properties`, the project's
   `local.properties`, or via the Gradle command line:

   ```properties
   journeyNotifierBaseUrl=https://journey-notifier.example.org
   ```

   ```sh
   ./gradlew :app:assembleDebug -PjourneyNotifierBaseUrl=https://journey-notifier.example.org
   ```

   The default is **empty** so unconfigured builds do not attempt to
   register device tokens with an undeployed server. This value is a
   public URL, not a secret.
5. Install the updated app. Open it once to create the high-importance
   `journey_changes` notification channel and schedule a reconciliation.
6. Save a journey. On Android 13+, allow the notification permission
   when prompted. You can change permissions in Android Settings later.

## Save, remove and token lifecycle

* `SavedJourneyStore.save` and `remove` enqueue a unique, network-
  constrained `JourneyNotifierSyncWorker` job. WorkManager also schedules
  periodic reconciliation (12h) to recover from outages and token changes.
* First registration sends the Firebase installation token to
  `POST /v1/devices`. The server returns a random per-installation
  `deviceId` and `deviceSecret`.
* The Android app stores that credential in preferences excluded from
  Android cloud backup and device transfer. On FCM `onNewToken`,
  it refreshes the backend token before sending subscriptions.
* Reconciliation uploads the **complete set** of locally saved journeys
  via `POST /v1/journeys/sync`, including the empty set when all bookmarks
  have been removed.
* Each itinerary's notification identity is a stable SHA-256 digest of its
  local `identity()`; transit legs are uploaded without map geometry.
* Trips disappear from the saved-trips *UI* 12 hours after scheduled arrival,
  but their local identity is held longer (30 days) so massive realtime
  delays are not silently unregistered; the **backend** controls cleanup.
* Once the backend expires a subscription, it persists a tombstone so the
  periodic Android full sync cannot recreate it.

## User-visible notification types

* Departure changed: "IC 5 at Genève now departs at 08:27."
* Platform changed: "Change of platform in Genève. Departure at 08:24 now from platform 4."
* Cancellation: "IC 5 during your journey has been cancelled. Tap for more information."
* Missed connection: "Connection to RE 33 no longer possible due to delay. Please find an alternate
  connection."

The notification's content intent opens the app's Saved Journeys screen.
When the app is foregrounded, its `FirebaseMessagingService` posts the
notification and remembers recent `eventId` values to suppress duplicates.
When the app is backgrounded, FCM displays notification+data messages
through Android's system delivery path.

## Operational checks

1. Save a future trip while the phone has network connectivity; confirm
   the backend PostgreSQL journey row is inserted.
2. Turn on Airplane Mode, remove a saved trip, then restore connectivity.
   WorkManager must delete the remote subscription after connectivity returns.
3. Inject an Aspen platform change, then a departure delay and a cancellation;
   confirm only new changes produce notifications.
4. Turn off Android notification permission: the app still runs, but the
   notification is not shown; no GPS or foreground service is started.
5. Rotate the FCM token and confirm the existing device credential is reused.
6. Test cold-start notification taps and opening Saved Journeys on tablets.

## Security

The app never contains Google service-account credentials. The device bearer
secret must not appear in analytics, logs or exported backups. Backend
registration and full reconciliation must be served over HTTPS, with
registration rate limits, device-auth checks and a request-size ceiling.

## Started-trip subscription and leg progress

* Pressing **Start** persists one active trip, registers or refreshes the Firebase
  token through the existing WorkManager job, and reconciles the backend set of **bookmarked
  journeys plus the one active journey**. An active journey does not
  need to be bookmarked to receive disruption alerts.
* Pressing **End** removes only the active-only subscription on the next sync.
  If it is also bookmarked, it stays subscribed. Switching active journeys atomically
  replaces the active-only subscription without removing either bookmark.
* The low-importance `active_journey_progress` channel carries a silent, ongoing
  foreground-service notification with the current leg's time-based progress (0–100%).
  During a walking transfer, it switches to the **next transit leg** and shows 0%
  until that service departs. The app updates it roughly every 20 seconds.
* Tapping the notification opens standalone active-journey details; it does not
  overwrite a planner search. Ending guidance stops and removes this notification,
  while the regular high-importance `journey_changes` FCM alert channel remains.
* Android 13+ asks for notification permission on the first Start or successful
  bookmark. If denied, trip tracking and remote subscription still work, but
  Android may suppress the notification from the notification drawer.
* The foreground service only starts in response to foreground app interaction,
  resumes when the app itself is brought to the foreground, and uses Android's
  `specialUse` type for continuous, user-initiated trip guidance; declare this
  type in Play Console before publishing and verify it is accepted.
* Progress is an **estimate** based on departure/arrival times, not proof that the
  rider is physically on board. Background realtime predictions are not actively
  polled by this service, and the last known predictions can become stale.

## Switch-trip confirmation

If the user presses Start on a different journey while one is active, the
existing trip remains active behind a modal dialog. Dismiss leaves it untouched; **Start trip**
replaces the active session. Bookmarks are unaffected.

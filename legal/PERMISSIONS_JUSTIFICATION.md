# Red Rocket - Permissions Justification

Reference document for Google Play's Permissions Declaration Form and for user
transparency.

**Source of truth is `app/src/main/AndroidManifest.xml`.** Every permission below
is declared there, and every permission declared there is listed below. If the two
ever disagree, the manifest is right and this file is a bug. An earlier revision of
this document claimed the app requested READ_SMS, VIBRATE, SCHEDULE_EXACT_ALARM and
FOREGROUND_SERVICE_SPECIAL_USE (it does not) and claimed it did not request
READ_CONTACTS or ACCESS_COARSE_LOCATION (it does). Both directions of that error are
worse than having no document at all: one over-declares to the reviewer, the other
tells the user the app cannot do something it can.

Eleven permissions are declared. Two of them are optional at runtime and the app is
fully functional without either.

---

## Sensitive permissions

### SEND_SMS
- **Why needed**: Core functionality. Red Rocket sends pre-configured emergency
  messages to the user's chosen contacts when an alert is detected.
- **Alternative considered**: None. There is no substitute for SMS when reaching
  contacts who may have no internet access during an emergency.
- **Declaration Form category**: Emergency/safety app where SMS sending is core
  functionality.

### RECEIVE_SMS
- **Why needed**: Tracks replies from emergency contacts after an alert is sent.
  Contacts reply with a single digit (1 safe, 2 safe and wants updates,
  3 emergency) and the Response Dashboard shows who has answered.
- **Alternative considered**: Without it, the user cannot know whether anyone
  received the message.
- **Note**: READ_SMS is *not* requested. Response tracking uses the incoming
  broadcast only; the app never reads the SMS database.

### READ_CONTACTS
- **Why needed**: Optional. Lets the user pick recipients from the address book
  instead of typing numbers by hand. Requested at runtime from the recipient
  picker (`ui/RecipientsInput.kt`) and offered once during first-launch setup
  (`ui/FirstLaunchScreen.kt`).
- **If denied**: Every other feature still works. Numbers are entered manually.
- **Data handling**: Contacts stay on the device. Nothing is uploaded.

### ACCESS_COARSE_LOCATION
- **Why needed**: Optional, and **off by default**. Powers alert enrichment: when
  the user enables it, `util/AlertEnricher.kt` reads the last known coarse location
  and asks the Event Horizon Web API what is happening within 15 km, so a detected
  alert can be shown with nearby context.
- **Default state**: `locationEnrichmentEnabled` defaults to `false`
  (`utils/AppSettings.kt`). Until the user turns it on, no location is read and no
  request is made.
- **If denied or left off**: Detection and sending are unaffected. Alerts are shown
  without the nearby-events context.
- **Data handling**: When enabled, latitude and longitude are sent to the Event
  Horizon Web API as query parameters on a read-only request. Nothing else about
  the user is sent, and no identifier accompanies the request.

### BIND_NOTIFICATION_LISTENER_SERVICE
- **Why needed**: Declared on `EmergencyNotificationListener`, not requested from
  the user as a runtime permission (the user grants notification access in system
  settings). Detects emergency alerts surfaced as notifications, because some OEMs
  deliver WEA/CMAS alerts that way rather than as cell broadcasts.
- **Alternative considered**: The cell broadcast receiver alone misses alerts on
  several Android OEMs.
- **Data handling**: Notification contents are evaluated on-device against the
  scenario filters and are not stored or transmitted.

---

## Standard permissions

### RECEIVE_BOOT_COMPLETED
- **Why needed**: Restarts monitoring after a reboot, so protection does not
  silently lapse until the user next opens the app.

### POST_NOTIFICATIONS
- **Why needed**: The foreground-service notification Android requires, plus the
  alert, sending-status and contact-reply notifications.

### FOREGROUND_SERVICE and FOREGROUND_SERVICE_DATA_SYNC
- **Why needed**: Keeps the monitoring service alive; Android kills background
  services aggressively. The declared `foregroundServiceType` is `dataSync`.
  FOREGROUND_SERVICE_SPECIAL_USE is *not* requested.

### WAKE_LOCK
- **Why needed**: The PARTIAL_WAKE_LOCK acquisitions in
  `EmergencyNotificationListener` and `EmergencyBroadcastReceiver` throw
  SecurityException without it, which silently disabled notification detection
  from v2.1.0 until it was caught on-device. Also keeps the device awake long
  enough to finish sending.

### REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
- **Why needed**: Asks the user once, during setup, to exempt Red Rocket from doze.
  Without the exemption, alerts arriving overnight can be delayed past the point of
  usefulness.
- **If denied**: The app still runs; delivery is less reliable while the device is
  dozing.

### INTERNET
- **Why needed**: Two uses, both listed here in full.
  1. A read-only version check on launch. Nothing about the user is sent.
  2. Alert enrichment, only when the user has enabled it. See
     ACCESS_COARSE_LOCATION above for exactly what is sent.
- There is no analytics, telemetry, crash reporting or account system.

---

## Permissions NOT requested

| Permission | Why not |
|---|---|
| ACCESS_FINE_LOCATION | Coarse location is enough for a 15 km radius, and only when enrichment is on |
| ACCESS_BACKGROUND_LOCATION | Location is read only in response to an alert, never in the background |
| READ_SMS | Replies are handled from the incoming broadcast; the SMS database is never read |
| SCHEDULE_EXACT_ALARM | Retry scheduling does not need exact alarms |
| VIBRATE | Not used |
| CAMERA | Not needed |
| RECORD_AUDIO | Not needed |
| READ_CALL_LOG | Not needed |
| READ_PHONE_STATE | Not needed |
| WRITE_EXTERNAL_STORAGE | Backups are written through the system file picker |

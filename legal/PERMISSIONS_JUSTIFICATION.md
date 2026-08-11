# Red Rocket - Permissions Justification

Reference document for Google Play's Permissions Declaration Form and user transparency.

---

## Sensitive Permissions

### SEND_SMS
- **Why needed**: Core functionality. Red Rocket sends pre-configured emergency messages to the user's chosen contacts when an emergency alert is detected.
- **Alternative considered**: There is no alternative to SMS for reaching contacts who may not have internet access during an emergency.
- **Declaration Form category**: Emergency/safety app where SMS sending is core functionality.

### RECEIVE_SMS
- **Why needed**: Tracks replies from emergency contacts after an alert is sent. The Response Dashboard shows who has responded and their status.
- **Alternative considered**: Without this, users cannot know if their contacts received and acknowledged the emergency message.

### READ_SMS
- **Why needed**: Reads incoming SMS responses from emergency contacts to populate the Response Dashboard.
- **Alternative considered**: None - response tracking requires reading incoming messages.

### BIND_NOTIFICATION_LISTENER_SERVICE
- **Why needed**: Detects emergency alerts from system notification channels and other emergency apps. Some devices surface WEA/CMAS alerts as notifications rather than cell broadcasts.
- **Alternative considered**: Cell Broadcast receiver alone does not catch all alert delivery methods across all Android OEMs.

---

## Standard Permissions

### RECEIVE_BOOT_COMPLETED
- **Why needed**: Auto-starts the emergency monitoring service after device reboot so the user remains protected without manual intervention.

### POST_NOTIFICATIONS
- **Why needed**: Shows persistent service notification (required by Android for foreground services) and alert notifications when emergencies are detected.

### FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE
- **Why needed**: Keeps the emergency monitoring service running reliably. Android kills background services aggressively - a foreground service ensures alerts are never missed.

### WAKE_LOCK
- **Why needed**: Keeps the device awake long enough to process an incoming emergency alert and send all SMS messages before the device returns to sleep.

### VIBRATE
- **Why needed**: Vibration feedback for emergency alert notifications.

### INTERNET
- **Why needed**: Update checker to notify users of new Red Rocket versions. No user data is transmitted.

### SCHEDULE_EXACT_ALARM
- **Why needed**: Schedules retry attempts for failed SMS deliveries via the LazarusRetrySystem.

---

## Permissions NOT Requested

| Permission | Why not |
|---|---|
| ACCESS_FINE_LOCATION | App does not use location |
| ACCESS_COARSE_LOCATION | App does not use location |
| READ_CONTACTS | Contacts are entered manually by user |
| CAMERA | Not needed |
| RECORD_AUDIO | Not needed |
| READ_CALL_LOG | Not needed |
| READ_PHONE_STATE | Not needed |

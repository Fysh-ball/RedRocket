# Google Play Data Safety Form - Red Rocket

Reference document for completing the Data Safety section in Google Play Console.

---

## Overview Questions

**Does your app collect or share any of the required user data types?**
Yes - the app handles messages (SMS) and contacts.

**Is all of the user data collected by your app encrypted in transit?**
Yes for everything that travels over the internet: the version check and the optional
alert-enrichment request are both HTTPS. SMS is sent via the device's native SMS stack
and is carrier-handled, not app-encrypted.

**Do you provide a way for users to request that their data is deleted?**
Yes - users can clear all data via Android Settings > Apps > Red Rocket > Clear Data, or by uninstalling the app.

---

## Data Types Declaration

### Messages (SMS or MMS)
- **Collected**: Yes
- **Shared**: No
- **Ephemeral**: No (stored in local Room database)
- **Required**: Yes (core functionality)
- **Purpose**: App functionality - sending emergency SMS to user-configured contacts and tracking delivery/responses

### Contacts (name, phone number)
- **Collected**: Yes (user-entered emergency contacts only)
- **Shared**: No
- **Ephemeral**: No (stored in local Room database)
- **Required**: Yes (core functionality)
- **Purpose**: App functionality - identifying who to send emergency messages to

### App activity (app interactions, in-app search history)
- **Collected**: No

### Web browsing history
- **Collected**: No

### Location
- **Collected**: Yes, approximate location only, and only if the user enables alert
  enrichment. The setting is off by default and the app is fully functional without it.
- **Shared**: Yes, with the Event Horizon Web API, which is operated by the same
  developer. Coordinates only; no identifier accompanies the request.
- **Ephemeral**: Yes. The coordinates are used for the one lookup and are not stored.
- **Required**: No
- **Purpose**: App functionality - showing what is happening near the user at the
  moment an alert fires.
- **Precise location**: No. ACCESS_COARSE_LOCATION only.

### Photos and videos
- **Collected**: No

### Audio files
- **Collected**: No

### Files and docs
- **Collected**: No

### Calendar
- **Collected**: No

### Device or other identifiers
- **Collected**: No

### Financial info
- **Collected**: No

### Health and fitness
- **Collected**: No

### Personal info (name, email, etc.)
- **Collected**: No (contact names are user-entered labels, not account data)

---

## Data Handling

**Is data transferred to third parties?** No

**Does the app use advertising SDKs?** No

**Does the app use analytics SDKs?** No

**Does the app contain any tracking code?** No

**Is user data processed ephemerally?** No - alert history and contacts persist until user clears them

---

## Notes for Form Completion

- The "Messages" category covers both outbound emergency SMS and inbound response tracking
- Contact data is either typed by the user or chosen from the device address book via
  the READ_CONTACTS picker, which is optional and requested at runtime. Either way it
  is stored locally and never uploaded.
- All user data stays on-device in a Room database
- The app does make network requests: an always-on read-only version check that sends
  nothing about the user, and an optional alert-enrichment lookup (off by default)
  that sends approximate coordinates. Both are covered above. An earlier revision of
  this document stated the app had no network layer, which was wrong.

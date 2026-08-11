# Google Play Data Safety Form - Red Rocket

Reference document for completing the Data Safety section in Google Play Console.

---

## Overview Questions

**Does your app collect or share any of the required user data types?**
Yes - the app handles messages (SMS) and contacts.

**Is all of the user data collected by your app encrypted in transit?**
SMS messages are sent via the device's native SMS stack. The app does not transmit data over the internet.

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
- **Collected**: No

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
- Contact data is entered manually by the user, not read from the device contacts (unless READ_CONTACTS permission is added later)
- All data stays on-device in a Room database
- The app has no network layer beyond the device SMS stack

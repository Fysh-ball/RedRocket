# Red Rocket Privacy Policy

**Effective Date:** August 12, 2026
**Developer:** Fysh-ball
**Contact:** redrocket@fysh.site

---

## Overview

Red Rocket is a personal emergency broadcast app that detects emergency alerts on your device and automatically sends SMS messages to your chosen contacts. Your privacy matters - this policy explains exactly what data the app handles and how.

## Data Collection and Storage

Red Rocket stores everything **on your device**. There is no account, no server holding
your data, no analytics and no tracking. Two network requests do leave the device and
both are described under "Network requests" below: a version check, and an optional
alert-enrichment lookup that is off until you turn it on.

### Data stored locally on your device:

| Data | Purpose | Storage |
|---|---|---|
| Emergency contact names and phone numbers | Send SMS alerts to your contacts | Room database |
| Emergency alert content | Detection, false alarm filtering, alert history | Room database |
| Scenario configurations | Your custom alert rules and message templates | Room database |
| Block phrases | Filter out non-emergency alerts | Room database |
| SMS send history | Track delivery status and prevent duplicates | Room database |
| Response records | Track replies from your contacts | Room database |
| App settings and preferences | Remember your configuration | DataStore/SharedPreferences |
| App logs | Debugging and troubleshooting | Room database |

### Data NOT collected:

- No location data, unless you enable alert enrichment (off by default), which sends
  your approximate coordinates at the moment an alert fires. See "Network requests".
- No device identifiers or fingerprints
- No usage analytics or telemetry
- No advertising identifiers
- No browsing or search history
- No biometric data
- No financial or payment data

## Data Sharing

Red Rocket does **not** sell, rent or trade your data, and contains no advertising or
analytics SDKs.

- No data is sold, rented, or traded
- No advertising or analytics SDKs are included
- Your contacts, scenarios, messages, alert history and logs never leave the device

## Network requests

This section exists because an earlier version of this policy said no data was ever
transmitted over the network, which was not accurate. Every request the app can make
is listed here.

**1. Version check (always on).** On launch the app makes a read-only request to
GitHub to see whether a newer release exists. It sends nothing about you: no
identifier, no contacts, no settings.

**2. Alert enrichment (optional, off by default).** If you switch this on in settings,
then when an alert is detected the app reads your approximate (coarse) location and
sends the latitude and longitude to the Event Horizon Web API, asking what events are
happening within 15 km so the alert can be shown with context. The request carries the
coordinates and nothing else: no identifier, no contact data, no alert text. The
result is displayed and not stored. If you leave this setting off, or deny the
location permission, no location is ever read and no such request is ever made.

**3. SMS.** The messages you configure are sent through your device's native SMS
stack to the contacts you chose. Your carrier handles them as it does any other text.

There is nothing else. No telemetry, no crash reporting, no account sync.

## How the App Works

1. **Alert detection**: The app listens for emergency broadcasts (WEA/CMAS/ETWS via Cell Broadcast) and emergency notifications from other apps
2. **False alarm filtering**: Alerts are processed locally by the FalseAlarmDetector to reduce false positives
3. **SMS sending**: When a real emergency is detected, the app sends your pre-configured SMS messages to your chosen contacts via your device's native SMS capability
4. **Response tracking**: The app monitors incoming SMS replies from your emergency contacts

Detection, filtering, sending and response tracking all happen on-device, and none of
them need an internet connection. Only the two requests under "Network requests"
above use the network, and neither is required for the app to protect you.

## Permissions Used

See [PERMISSIONS_JUSTIFICATION.md](PERMISSIONS_JUSTIFICATION.md) for a detailed breakdown of each permission and why it is needed.

## Data Retention

- Alert history and logs are stored indefinitely on your device until you clear them
- You can delete all app data at any time through Android Settings > Apps > Red Rocket > Clear Data
- Uninstalling the app removes all stored data

## Your Rights

You have full control over your data:

- **Access**: All your data is visible within the app (alert history, logs, contacts, scenarios)
- **Deletion**: Clear data through app settings or Android system settings
- **Portability**: Scenario backup/restore is available within the app
- **Correction**: Edit your contacts, scenarios, and settings at any time

## Children's Privacy

Red Rocket is not directed at children under 13. We do not knowingly collect data from children. The app is designed for adults managing their own emergency preparedness.

## Security

All data is stored in your device's private app storage, which is sandboxed by Android
and not accessible to other apps. The only things that leave the device are the SMS
messages you explicitly configure and the requests listed under "Network requests".

## Changes to This Policy

If this policy changes, the updated version will be published in this repository with a new effective date. For significant changes, the app's release notes will highlight the update.

## Contact

For privacy questions or concerns:
- Email: redrocket@fysh.site
- GitHub Issues: [Fysh-ball/RedRocket](https://github.com/Fysh-ball/RedRocket/issues)

## Applicable Law

This app is developed in Canada. For Canadian users, this policy is designed to align with the Personal Information Protection and Electronic Documents Act (PIPEDA). For US users, this policy complies with FTC guidelines on truthful disclosure of data practices.

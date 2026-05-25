# Red Rocket Privacy Policy

**Effective Date:** May 25, 2026
**Developer:** Fysh-ball
**Contact:** redrocket@fysh.site

---

## Overview

Red Rocket is a personal emergency broadcast app that detects emergency alerts on your device and automatically sends SMS messages to your chosen contacts. Your privacy matters - this policy explains exactly what data the app handles and how.

## Data Collection and Storage

Red Rocket operates **entirely on your device**. There are no servers, no cloud services, no analytics, and no tracking.

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

- No location data
- No device identifiers or fingerprints
- No usage analytics or telemetry
- No advertising identifiers
- No browsing or search history
- No biometric data
- No financial or payment data

## Data Sharing

Red Rocket does **not** share any data with third parties. Period.

- No data is sent to external servers
- No data is sold, rented, or traded
- No advertising or analytics SDKs are included
- No third-party services receive your data

The only data that leaves your device is the SMS messages you configure the app to send to your chosen emergency contacts.

## How the App Works

1. **Alert detection**: The app listens for emergency broadcasts (WEA/CMAS/ETWS via Cell Broadcast) and emergency notifications from other apps
2. **False alarm filtering**: Alerts are processed locally by the FalseAlarmDetector to reduce false positives
3. **SMS sending**: When a real emergency is detected, the app sends your pre-configured SMS messages to your chosen contacts via your device's native SMS capability
4. **Response tracking**: The app monitors incoming SMS replies from your emergency contacts

All of this happens on-device. No internet connection is required for core functionality.

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

All data is stored in your device's private app storage, which is sandboxed by Android and not accessible to other apps. No data is transmitted over the network (except the SMS messages you explicitly configure).

## Changes to This Policy

If this policy changes, the updated version will be published in this repository with a new effective date. For significant changes, the app's release notes will highlight the update.

## Contact

For privacy questions or concerns:
- Email: redrocket@fysh.site
- GitHub Issues: [Fysh-ball/RedRocket](https://github.com/Fysh-ball/RedRocket/issues)

## Applicable Law

This app is developed in Canada. For Canadian users, this policy is designed to align with the Personal Information Protection and Electronic Documents Act (PIPEDA). For US users, this policy complies with FTC guidelines on truthful disclosure of data practices.

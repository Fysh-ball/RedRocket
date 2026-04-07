# Red Rocket

Red Rocket is an automated emergency response app that keeps your contacts informed when it matters most.

When an emergency alert hits your device, Red Rocket detects it, matches it against your custom scenarios, and automatically sends your pre-written message to the people you chose. No fumbling with your phone. No hoping you remember who to call.

---

## What it does

**Automated alert detection**
Red Rocket listens for emergency broadcasts through Android's cell broadcast system and notification listener. When an alert comes in, it checks the content against your keywords and decides whether to send.

**Works worldwide**
Alert filters come with presets covering 14 disaster categories and detection keywords in 22 languages across 35 countries. The app auto-detects your region and dial code. If you travel or your SIM doesn't match, you can set it manually.

**Scenarios and groups**
You set up scenarios in advance. Each scenario has keywords that trigger it, and one or more groups of contacts with their own message. A "family" group might get one message, a "coworkers" group gets another. When the scenario fires, all groups send at once.

**Alert filters**
Each scenario can have Activation Keywords (words that must match for it to fire) and Block Phrases (words that cancel a trigger even if the keywords match, like "this is a test"). Tap a preset to add a whole category at once. Tap it again to remove it.

**Adaptive delivery**
Messages go out in parallel by default. If the network is struggling and sends start failing, it drops to sequential mode and eventually enters Lazarus retry mode, which keeps retrying until everything goes through. You can set it to keep trying indefinitely.

**Two-way responses**
After sending, Red Rocket listens for replies. Contacts reply with 1 (safe), 2 (safe but wants updates), or 3 (emergency). The dashboard tracks who responded, what they said, and who still hasn't replied.

**False alarm protection**
The detection engine runs 8 steps before firing, including hard blocks for test alerts, multilingual phrase matching, and a trust-level check based on which app the alert came from. It does not fire on test messages. AMBER alerts pass through like any other alert — add "amber alert" as an activation keyword if you want your scenario to trigger on them.

**Force send**
Need to send without waiting for an alert? There's a manual send option with a captcha and countdown so you don't fire it by accident.

**Home screen widget**
A widget shows Red Rocket's current status without opening the app.

**Backup and restore**
Export your scenarios to a file. Restore them on a new device in seconds.

---

## Privacy

No accounts. No servers. Your contacts, scenarios, and messages never leave your device.

The only outbound request is a read-only version check to GitHub on launch, to notify you when an update is available. No personal data is sent.

No data collection. No telemetry. No analytics. Full stop.

---

## Setup

On first launch, you'll be walked through the permissions the app needs. The two most important are notification access (to read alerts from the system) and battery optimization exemption (so Android does not kill the app in the background).

After setup, create a scenario, add contacts, write a message, and optionally set keywords or pick from the regional presets. The app handles the rest.

There's a test send option in Settings if you want to verify everything is working before you need it.

---

## A note

This app was designed and quality tested by me. While I wish I could have written it all myself, current world events makes me believe that this app needed to exist ASAP. While most of the issues have been ironed out, I am only human. I probably did make mistakes so please if there are any issues report it and I'll fix it right away. I use this app too lol.

If you did find this app useful please consider donating me a cup of rice!

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C71L2ELD)

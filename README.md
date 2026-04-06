# Red Rocket

Red Rocket is an automated emergency response app that keeps your contacts informed when it matters most.

When an emergency alert hits your device, Red Rocket detects it, matches it against your custom scenarios, and automatically sends your pre-written message to the people you chose. No fumbling with your phone. No hoping you remember who to call.

---

## What it does

**Automated alert detection**
Red Rocket listens for emergency broadcasts through Android's cell broadcast system and notification listener. When an alert comes in, it checks the content against your keywords and decides whether to send.

**Scenarios and groups**
You set up scenarios in advance. Each scenario has keywords that trigger it, and one or more groups of contacts with their own message. A "family" group might get one message, a "coworkers" group gets another. When the scenario fires, all groups send at once.

**Alert Filters**
Each scenario can have Activation Keywords (words that must match for it to fire) and Block Phrases (words that cancel a trigger even if the keywords match, like "this is a test"). Both support regional presets covering 22 languages and 35 countries.

**Adaptive delivery**
Messages go out in parallel by default. If the network is struggling and sends start failing, it drops to sequential mode and eventually enters Lazarus retry mode, which keeps retrying until everything goes through. You can set it to keep trying indefinitely.

**Two-way responses**
After sending, Red Rocket listens for replies. Contacts reply with 1 (safe), 2 (safe but wants updates), or 3 (emergency). The dashboard tracks who responded and what they said.

**False alarm protection**
The detection engine runs 8 steps before firing, including hard blocks for test alerts and AMBER alerts, multilingual phrase matching across 22 languages, and a trust-level check based on which app the alert came from. It does not fire on test messages.

---

## Privacy

No accounts. No servers. No internet required. Your contacts, scenarios, and messages never leave your device.

No data collection. No telemetry. No analytics. Full stop.

---

## Setup

On first launch, you'll be walked through the permissions the app needs. The two most important are notification access (to read alerts from the system) and battery optimization exemption (so Android does not kill the app in the background).

After setup, create a scenario, add contacts, write a message, and optionally set keywords. The app handles the rest.

---

## A note

This app was designed and quality-checked by one person. I built it because I wanted something like it to exist, and current events pushed me to get it done quickly. I use it myself.

It has been tested carefully, but no software is perfect. If you find a bug that affects reliability or sends messages incorrectly, please report it so I can fix it.

If this was useful to you, consider buying me a cup of rice.

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C71L2ELD)

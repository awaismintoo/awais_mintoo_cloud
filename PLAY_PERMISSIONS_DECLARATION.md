# Permissions: what the app declares and why (build 2026-11-13.521; use these answers in Play Console)
| Permission | Used for | When asked |
|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | prices, charts, exchanges, weather, listings | always |
| POST_NOTIFICATIONS | price and signal alerts | Android 13+ prompt |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE | user-switched background run with a visible notification: live price monitoring, price alerts and user-authorised automated trading on the user's own exchange account | when the user switches it on |
| WAKE_LOCK | keeps that switched-on background run awake | only while on |
| RECEIVE_BOOT_COMPLETED | restarts alerts / background run after a restart if the user left them on | - |
| VIBRATE | alert vibration; Azan and Namaz notifications vibrate for deaf and hard-of-hearing users | - |
| SCHEDULE_EXACT_ALARM | Azan / Namaz reminder at the exact prayer time (the user chooses Azan; the app checks canScheduleExactAlarms and asks the user to allow it in Settings) | when the user turns on Azan |
| RECORD_AUDIO | voice questions to Baba AI and 🎤 voice search | when the user taps the mic |
| CAMERA | attach a photo, scan a wallet QR code | when the user taps it |
| ACCESS_COARSE/FINE_LOCATION | optional: weather, prayer times, places near the user and live turn-by-turn navigation while the navigation screen is open (no background location) | when the user taps it |
| (Play version: no VPN) | The Google Play AAB is built without the WireGuard library, so it has no VpnService. Only the full APK from GitHub has MintooSuiteVPN. | - |

Foreground-service "special use" description to type in Play (must match the manifest): "User-switched background run with a visible notification: live price monitoring, price alerts and user-authorised automated trading on the user's own exchange account." Record a short video: open Auto Bot > switch on background run > notification appears > switch off.

No contacts, SMS, call log, all-files storage, overlay or battery-optimisation-exemption permissions are declared. Photos are picked through the system picker (no media permission).


## Build 525 additions
- **Location (foreground only)**: also used by the Compass & Qibla window (Qibla bearing, altimeter, "My location") and Explore around me / Where I parked. No background location is requested; nothing is uploaded.
- **Sensors**: magnetic/orientation sensor (compass) and air-pressure sensor (barometric altimeter) are read only while the Compass window is open. These sensors need no Android permission.
- **Home-screen widgets**: two more widgets (Qibla, Location & altitude) showing values saved by the app; no new permission.

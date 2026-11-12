# Permissions: what the app declares and why (build 2026-11-12.495; use these answers in Play Console)
| Permission | Used for | When asked |
|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | prices, charts, exchanges, weather, listings | always |
| POST_NOTIFICATIONS | price and signal alerts | Android 13+ prompt |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_SPECIAL_USE | user-switched background run with a visible notification: live price monitoring, price alerts and user-authorised automated trading on the user's own exchange account | when the user switches it on |
| WAKE_LOCK | keeps that switched-on background run awake | only while on |
| RECEIVE_BOOT_COMPLETED | restarts alerts / background run after a restart if the user left them on | - |
| VIBRATE | alert vibration | - |
| RECORD_AUDIO | voice questions to Baba AI | when the user taps the mic |
| CAMERA | attach a photo, scan a wallet QR code | when the user taps it |
| ACCESS_COARSE/FINE_LOCATION | optional: weather, prayer times and places near the user ("Use my location") | when the user taps it |
| (Play version: no VPN) | The Google Play AAB is built without the WireGuard library, so it has no VpnService. Only the full APK from GitHub has MintooSuiteVPN. | - |

Foreground-service "special use" description to type in Play (must match the manifest): "User-switched background run with a visible notification: live price monitoring, price alerts and user-authorised automated trading on the user's own exchange account." Record a short video: open Auto Bot > switch on background run > notification appears > switch off.

No contacts, SMS, call log, all-files storage, overlay or battery-optimisation-exemption permissions are declared. Photos are picked through the system picker (no media permission).

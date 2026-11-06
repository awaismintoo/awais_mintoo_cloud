# Permissions: what the app declares and why (use these exact answers in Play Console)
| Permission | Used for | When asked |
|---|---|---|
| INTERNET, ACCESS_NETWORK_STATE | prices, charts, exchanges | always |
| POST_NOTIFICATIONS | signal / price alerts | Android 13+ prompt |
| FOREGROUND_SERVICE + _SPECIAL_USE | optional "keep bot running" notification (user switches it on) | on switch-on |
| WAKE_LOCK | keeps the optional running bot awake while it is switched on | only while on |
| RECEIVE_BOOT_COMPLETED | restarts the optional bot/alerts after a phone restart if the user left them on | - |
| VIBRATE | alert vibration | - |
| RECORD_AUDIO | optional voice commands / voice to text | when user taps the mic |
| CAMERA | optional: scan a wallet QR code, profile picture | when user taps it |
| ACCESS_COARSE/FINE_LOCATION | optional "Around me" local news/weather, only when the user taps it; location is not stored on a server | when user taps it |
Camera, microphone and location are declared `required=false` (the app works without them). No contacts, SMS, call log, storage-wide or overlay permissions.
Data safety answers must match: location (approximate, optional, not stored by us), audio (voice-to-text on device/phone service, optional), no financial data collected by us, keys stay on the phone.
Foreground-service "special use" justification to type in Play: "User-started trading-alert helper that keeps price checks running with a visible notification."

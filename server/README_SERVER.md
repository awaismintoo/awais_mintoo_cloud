# MintooSuite always-on alert server (plain-language guide)

Why: the free GitHub alerts run only about every 15 minutes, can start late, and have no memory, so a signal can be missed or sent twice.
This small server wakes at **every 15-minute candle close (+10 seconds)**, checks the top 60 coins, sends **one** push per signal and
**remembers** what it sent (`sent.json`), so nothing repeats, even after a restart.

Honest limits: it uses the same rules as the GitHub version (UT Bot flip + Baba early breakout). The app's live Baba state machine (flip memory, v2 hour filter) is richer, so server alerts are an early heads-up, not a copy of every in-app signal. Open the app to check any alert. No profit promise.

## Steps
1. Rent the cheapest always-on Linux server (Ubuntu 22.04 or 24.04; about 4-6 USD a month, or a free-tier one). Pick a region where OKX/Bybit/Gate data works (Europe or Asia is fine; GitHub's USA servers are the ones blocked by Binance).
2. Copy the `server` folder to it and run as root: `bash install_server.sh`
3. In Firebase console > Project settings > Service accounts > Generate new private key. Put that file on the server as `/opt/mintoo-alerts/fcm_service_account.json` (NEVER in chat, never in the app). Then `systemctl restart mintoo-alerts`.
4. Check: `journalctl -u mintoo-alerts -f` (you will see a line every 15 minutes) and `cat /opt/mintoo-alerts/health.json`.
5. When it works, disable the GitHub schedule (Actions > MintooSuite push > Disable) so phones do not get two alerts.

Test without sending anything: `python3 mintoo_server.py --dry --once`
Settings (optional environment variables): `MW_COINS` (default 60), `MW_MAX_PUSH` (default 3 per bot per candle).
Phones that switched on "Alerts when the app is closed" receive it through the Firebase topic `picks`, same as before.

## Optional: check Google Play payments on your own server (stronger than trusting the phone)
`verify_purchase.py` asks Google directly whether a subscription is really active. Needs a Google Cloud service account added in Play Console (Users and permissions), its key file saved as `/opt/mintoo-alerts/play_service_account.json`, run behind https, and its URL stored in the app under `mintoosuite_verifyurl`. Without it the app still re-checks Google Play on the phone every time it opens, so a cancelled or refunded plan stops within 3 days at most.

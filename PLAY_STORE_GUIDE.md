# MintooSuite: signing and publishing guide (updated for build 2026-11-12.495)

Read the audit document first (MintooSuite A-to-Z Audit and Store Readiness). It lists what still blocks approval.

## What the Android app is
A standalone app: the whole MintooSuite web app is packed inside it (no website needed to open it). Live prices, news and trading still need internet. Package ID `com.mintoosuite.terminal`, targets Android 16 (API 36), which Google Play has required for new apps and updates since 31 Aug 2026.

Your old APK (from the website wrapper) was signed with a key named "MintooSuite Admin, US". The new key is made in your name (Muhammad Awais, Islamabad, PK).

## Steps (in Termux, in this order)
1. `bash mintoo_install.sh` : publishes the web app v9.3 and the new privacy policy.
2. `termux-setup-storage` (once, allow access), then `gh auth login` (once, choose GitHub.com, HTTPS, login with a token that has repo and workflow permission).
3. `bash mintoo_play_setup.sh` : adds the Android project, creates your signing key, stores it as GitHub secrets, builds in the cloud, and downloads `app-release.apk` (for testing on your phone) and `app-release.aab` (for Google Play).
4. Install the APK on your S20 Plus and test everything (prices, Trade window in Test mode, notifications).

## Protect the key
`~/mintoosuite_keys/mintoosuite-release.jks` plus its password are the only proof the app is yours. Copy it off the phone (email to yourself, USB). Never put it in the GitHub repo (the script ignores it). If you enrol in Play App Signing (recommended, default), Google can reset the upload key if you lose it.

## Google Play Console
1. Developer account: one-time US$25, identity verification. A personal account created after 13 Nov 2023 must run a closed test with at least 12 testers opted in for 14 continuous days before you can apply for production. An organisation account is exempt but needs business verification.
2. Create app: name MintooSuite, App, Free (see "Selling" below), declare it follows policies.
3. Test and release > Closed testing: upload `app-release.aab`, add 12+ tester Gmail addresses, share the opt-in link. After 14 days apply for production.
4. App content:
   - Privacy policy URL: https://awaismintoo.github.io/awais_mintoo_cloud/privacy.html
   - Ads: YES (AdMob banner in the free version, 18+ only). Target audience: 18+. Content rating questionnaire: finance/trading information, no gambling.
   - Financial features declaration: the app can place orders on a crypto exchange with the user's own API keys, so do NOT choose "no financial features". Choose the trading and funds options that describe it, and read Google's Cryptocurrency Exchanges and Software Wallets policy page. Apps that facilitate trading may need licences in some countries; choose target countries where you are sure you comply, or ask Play support. This is not legal advice.
   - Data safety (answer exactly): data is encrypted in transit; users can request deletion (in-app and delete-account page).
     Collected (optional): Personal info (email: account management; name and phone in listings: app functionality, public); Location (approximate and precise, only when the user taps Use my location or shares a price note: app functionality); Photos (listing photos: app functionality); App activity (user-generated content: listings, reviews, reports, price notes; app interactions only if analytics is switched on); App info and performance (crash logs, opt-in); Device or other IDs (device list for account security; advertising ID for ads); Financial info (purchase history through Google Play).
     Shared: advertising ID with Google AdMob; location with weather, prayer-time and map services (Open-Meteo, AlAdhan, OpenStreetMap); photos and AI text with OpenAI / Gemini only when the user runs that feature with their own key.
     Not collected: exchange API keys and wallet words (they stay on the phone and go only to the chosen exchange).
   - Financial features: declare the non-custodial software wallet and the crypto order tools (user's own exchange accounts); leave out countries where you do not hold the licences Google lists (for example US, UK, EU, Japan, South Korea). Pakistan: see the audit (Virtual Assets Act 2026 / PVARA).
   - AI-generated content: the app has in-app reporting (Baba AI answers and Photo studio pictures).
   - VPN: from build 497 the Play version (app-release.aab, flavour "play") is built without the VPN library, so it declares no VpnService and needs no VPN declaration. The full APK from GitHub keeps MintooSuiteVPN.
5. Store listing:
   - Title: MintooSuite
   - Short description (80): Markets, business and daily life in one app: signals, gold, shopping, weather.
   - Full description (start with): MintooSuite is an independent app, not affiliated with any exchange or shop. Crypto market data from major exchanges (700+ coins), ranked trade ideas with entry, stop-loss, target and a validity window, a Master Plan for the day, an Exness gold plan with lot sizes, a virtual paper-trading bot, news and alerts. Optional Binance order tickets using your own API keys (test mode first). Not financial advice; trading can lose money.
   - Do not write "guaranteed", "risk-free", "make $X per day". Google forbids promoting or glamorising trading earnings.
   - Icon: icon-512.png. Feature graphic: feature_graphic_1024x500.png. Screenshots: 2 to 8 phone screenshots from your S20 Plus.

## Selling the app
Paid apps need a Google merchant (payments) profile, and the countries where you can receive payments are limited. Check the list under Play Console > Payments profile for Pakistan before planning a price. If it is not supported, options are a free app with a paid website subscription (outside Play) or a partner/company account in a supported country.

## Updating later
Run the web installer (it publishes index.html), then GitHub > Actions > Build signed Android app > Run workflow, upload the new AAB. The version code rises automatically with each run.

## If the build fails
Open GitHub > Actions > the red run, copy the red error lines and send them to me. The Android project could not be compiled where it was written, so the first cloud build is its first real test.

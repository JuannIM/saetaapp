# Google Play Console — Data Safety Form Specification

**Application Name:** SAETA Saldo (com.saetasaldo.app)  
**Developer:** Juan Ignacio Mercado  
**Form Target:** Google Play Console > App Content > Data Safety Questionnaire  
**Reference Policy:** [Google Play User Data Policy](https://support.google.com/googleplay/android-developer/answer/10787469)

---

## 1. Overview Summary for Reviewers

SAETA Saldo is an open-source, local-first utility for querying public bus card balances in Salta, Argentina.
- **Data Collected for App Functionality:** None stored on external developer servers.
- **Data Shared with Third Parties:** None shared with advertising, analytics, or unrelated parties. The app sends direct HTTPS requests to the public transit provider `salta.miredbus.com.ar` to fetch balance, identical to a browser query.
- **Optional Account Connection:** Users may optionally log in on the official RedBus site inside a hardened in-app WebView. Credentials are entered directly into the provider's page and are never received, read, or stored by the app or the developer. Portal session cookies are processed on-device, held only in the app-private `CookieManager`, and sent only to `salta.miredbus.com.ar`; they are deleted when the user disconnects. Anonymous captcha mode remains fully available without an account.
- **Wear OS Companion:** The phone sends the favorite card's alias, formatted balance, trip estimate and last-update time to the user's own paired watch via the Google Play services Wearable Data Layer. This is a user-initiated transfer between the user's own devices; nothing reaches developer servers.
- **Cloudflare Turnstile:** Anonymous queries may load the portal's Cloudflare Turnstile challenge in an offscreen WebView. As on any website that uses it, Cloudflare processes technical connection data (e.g., IP address, browser signals) for bot detection; the app itself does not collect it.
- **Live Bus Map:** Reads publicly available routes, stops and real-time bus positions from the official portal, anonymously. Approximate location is used on-device only — when the user taps "Mi ubicación" it centers the map and shows a dot tracking the user while the map screen is open; the position is never stored, shared, or transmitted, and location reading stops when the screen is left or paused.
- **Data Tracking / Advertising:** Zero advertising, analytics, or behavioral tracking SDKs.
- **Data Encryption in Transit:** 100% of network traffic uses TLS 1.2+ HTTPS. Cleartext HTTP is disabled.
- **User Data Deletion:** Supported locally inside the app. Deleting a card wipes all associated history; "Desconectar" removes the portal session cookies.

> **Action required when this feature ships:** the Data Safety questionnaire must be re-submitted in Play Console. The answers below were updated for the optional RedBus account flow on 2026-10-05, for account wallets, pending loads and the Wear OS companion on 2026-10-07, and for the live bus map (on-device approximate location) on 2026-10-08.

---

## 2. Google Play Data Safety Responses (Line-by-Line Guide)

### Section: Data collection and security

| Question in Play Console | Response | Rationale / Explanation |
| :--- | :--- | :--- |
| **Does your app collect or share any of the required user data types?** | **No** (or **Yes - Ephemeral Transit Only** depending on Google Play interpretation of direct transit balance queries) | See breakdown below. If Google considers transit card numbers as financial info, declare as non-stored ephemeral. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All network traffic uses HTTPS (TLS 1.2+). Android network security config blocks unencrypted HTTP. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Users can delete cards and balance histories at any time from the app UI, and uninstalling the app deletes all SQLite database files. |

---

### Section: Data types breakdown

#### 1. Location
- **Approximate location:** Yes — used on-device only.
  - *Collected?* No (processed on-device; never sent to any server, including the transit provider's).
  - *Shared?* No.
  - *Ephemeral processing?* Yes — a single last-known/single-update read held in memory only; nothing is persisted.
  - *Required or optional?* Optional — requested in context only when the user taps "Mi ubicación" on the bus map; the map works fully without it.
  - *Purpose:* App functionality (center the live bus map on the user's position).
- **Precise location:** No — the app never requests `ACCESS_FINE_LOCATION`.

#### 2. Personal info
- **Name:** No
- **Email address:** See interpretation note below.
- **User IDs:** See interpretation note below.
- **Address:** No
- **Phone number:** No
- **Race and ethnicity:** No
- **Political or religious beliefs:** No
- **Sexual orientation:** No
- **Other personal info:** No

> **Interpretation note — optional RedBus login (verify against the live form before submitting):**
> When a user optionally connects their RedBus account, they type their credentials (email/username and password) directly into the official provider page rendered inside an isolated WebView. SAETA Saldo's own code never receives, reads, or stores those credentials — there is no JavaScript bridge and no DOM access — and they are transmitted only to `salta.miredbus.com.ar`. The only artifact the app retains is the provider's session cookies, held in the app-private `CookieManager`, sent exclusively back to that host, and deleted on "Desconectar".
>
> **Chosen answer:** declare Email address / User IDs as **shared with the provider, not collected by the developer**: *Collected?* No. *Shared?* Yes — with the service provider the user is logging into, at the user's explicit request (the credentials travel only inside the provider's own HTTPS page). *Ephemeral?* Yes for the credentials themselves — they never touch app code or storage; the derived session cookies persist on-device only until disconnect or provider expiry. *Required or optional?* Optional — the anonymous captcha flow works without an account. *Purpose:* App functionality.
>
> **Alternative reading:** if the Play form treats a login typed into the provider's own WebView page like an ordinary browser visit — i.e., the user hands data to the provider itself and the app "shares" nothing — answer **No** for Email address / User IDs instead. The app's actual behavior is exactly as described above either way; pick whichever answer the current form wording demands and keep this note accurate.

#### 3. Financial info
- **User payment info (Credit / Debit card):** No
- **Purchase history:** No
- **Credit score:** No
- **Other financial info (Transit card balance / number):**
  - *If declared in Play Console:*
    - **Collected?** Only locally processed and ephemerally transmitted to the transit query endpoint. With the optional account connection, the linked card number, the `Principal (Dinero)` balance and any other wallet balances, card type, card state, internal card number (`nroInterno`) and the account's card description are additionally read through the authenticated provider session and stored only in the on-device database; pending loads (`cargaspendientes`) are fetched on demand and kept in memory only.
    - **Shared?** No.
    - **Ephemeral processing?** Yes for the anonymous query (executed in real-time, not logged to developer servers). Account-synced values are retained only in the local database under the user's control.
    - **Required or optional?** Required for the core feature (checking card balance); the account connection itself is optional.
    - **Purpose:** App functionality.

#### 4. Health and fitness
- **Health info:** No
- **Fitness info:** No

#### 5. Messages
- **Emails / SMS:** No

#### 6. Photos and videos
- **Photos / Videos:** No (Captcha image is received from the transit provider into RAM, processed on-device via ML Kit, and discarded. Never saved to gallery or media store).

#### 7. Audio files
- **Voice or sound recordings:** No

#### 8. Files and docs
- **Files and docs:** No

#### 9. Calendar
- **Calendar events:** No

#### 10. Contacts
- **Contacts:** No

#### 11. App activity
- **App interactions:** No
- **In-app search history:** No
- **Installed apps:** No
- **Other app performance / analytics:** No

#### 12. Web browsing
- **Web browsing history:** No

#### 13. App info and performance
- **Crash logs:** No (Crashlytics not included)
- **Diagnostics:** No
- **Other app performance data:** No

#### 14. Device or other identifiers
- **Device or other IDs (IMEI, MAC, Android ID, Advertising ID):** **No**. The app does not access `Settings.Secure.ANDROID_ID`, telephony managers, or advertising IDs.

---

## 3. Privacy Policy URL for Play Store Listing

When publishing to Google Play Store, provide the raw or rendered GitHub URL:
`https://github.com/JuannIM/saetaapp/blob/main/PRIVACY_POLICY.md`

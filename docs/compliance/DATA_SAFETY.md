# Google Play Console — Data Safety Form Specification

**Application Name:** SAETA Saldo (com.saetasaldo.app)  
**Developer:** Juan Ignacio Mercado  
**Form Target:** Google Play Console > App Content > Data Safety Questionnaire  
**Reference Policy:** [Google Play User Data Policy](https://support.google.com/googleplay/android-developer/answer/10787469)

---

## 1. Overview Summary for Reviewers

SAETA Saldo is an open-source, local-first utility for querying public bus card balances in Salta, Argentina.
- **Data Collected for App Functionality:** None stored on external developer servers.
- **Data Shared with Third Parties:** None. (The app sends direct HTTPS requests to the public transit provider `salta.miredbus.com.ar` to fetch balance, identical to a browser query).
- **Data Tracking / Advertising:** Zero advertising, analytics, or behavioral tracking SDKs.
- **Data Encryption in Transit:** 100% of network traffic uses TLS 1.2+ HTTPS. Cleartext HTTP is disabled.
- **User Data Deletion:** Supported locally inside the app. Deleting a card wipes all associated history.

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
- **Approximate location:** No
- **Precise location:** No

#### 2. Personal info
- **Name:** No
- **Email address:** No
- **User IDs:** No
- **Address:** No
- **Phone number:** No
- **Race and ethnicity:** No
- **Political or religious beliefs:** No
- **Sexual orientation:** No
- **Other personal info:** No

#### 3. Financial info
- **User payment info (Credit / Debit card):** No
- **Purchase history:** No
- **Credit score:** No
- **Other financial info (Transit card balance / number):**
  - *If declared in Play Console:*
    - **Collected?** Only locally processed and ephemerally transmitted to the transit query endpoint.
    - **Shared?** No.
    - **Ephemeral processing?** Yes (the query is executed in real-time and not logged to developer servers).
    - **Required or optional?** Required for the core feature (checking card balance).
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

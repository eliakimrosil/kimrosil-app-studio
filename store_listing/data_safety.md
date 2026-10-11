# Google Play Data Safety Declaration: GrainRun: Wood Planing Guide

**Application:** GrainRun: Wood Planing Guide  
**Package Name:** `com.kimrosil.grainrun`  
**Developer:** Eliakim Rosil (KimRosil App Studio)  
**Public Showcase:** https://apps.kimrosil.com/grain-run/  
**Privacy Policy:** https://apps.kimrosil.com/grain-run/privacy.html  
**Last Updated:** October 2026  

---

## 1. Google Play Console Data Safety Overview
Use these exact responses when completing the **Google Play Console > App content > Data safety** section:

| Questionnaire Prompt | Required Answer | Explanation / Rationale |
|---|---|---|
| **Does your app collect or share any of the required user data types?** | **Yes** | Required solely due to Google Mobile Ads (AdMob) SDK integration. The developer collects zero proprietary personal data. |
| **Is all of the user data collected by your app encrypted in transit?** | **Yes** | All network requests transmitted by Google Mobile Ads SDK use modern HTTPS/TLS protocol encryption. |
| **Do you provide a way for users to request that their data be deleted?** | **Yes** | Users can reset or delete their Google Advertising ID via Android Settings (`Settings > Google > Ads > Delete advertising ID`) or clear application local storage (`Settings > Apps > GrainRun: Wood Planing Guide > Storage > Clear Data`). |

---

## 2. Declared Data Types (Google Mobile Ads SDK)

### A. Device or other IDs
- **Data Type:** Device or other IDs (Google Advertising ID / GAID / AAID)
- **Collected?** Yes (Collected automatically by Google Mobile Ads SDK)
- **Shared?** Yes (Shared with Google LLC for ad serving, frequency capping, and bot fraud prevention)
- **Processed ephemerally?** No
- **Is this data required or optional?** Optional (Users can opt out of personalized ads or delete advertising ID)
- **Declared Purposes:**
  - ☑ **Advertising or marketing**
  - ☑ **Analytics**
  - ☑ **Fraud prevention, security, and compliance**

### B. Location (Approximate Location)
- **Data Type:** Approximate location (Coarse location inferred by Google AdMob from device IP address)
- **Collected?** Yes
- **Shared?** Yes (Shared with Google LLC)
- **Processed ephemerally?** Yes
- **Is this data required or optional?** Optional
- **Declared Purposes:**
  - ☑ **Advertising or marketing** (Contextual and regional ad delivery)

### C. App Info and Performance
- **Data Type:** Crash logs, Diagnostics, Other app performance data
- **Collected?** Yes (Collected automatically by Google Mobile Ads SDK for SDK health)
- **Shared?** Yes (Shared with Google LLC)
- **Processed ephemerally?** No
- **Is this data required or optional?** Optional
- **Declared Purposes:**
  - ☑ **Analytics** (SDK stability monitoring)
  - ☑ **Fraud prevention, security, and compliance**

---

## 3. Categories NOT Collected (Zero-Collection Guarantee)
The Application explicitly **DOES NOT** collect, store, or share any of the following:
- ❌ **Personal info:** (Name, email address, user IDs, physical address, phone number, political/religious beliefs) - **NONE**
- ❌ **Financial info:** (Credit/debit cards, bank accounts, purchase history, salary, credit info) - **NONE**
- ❌ **Health & Fitness:** (Health records, fitness tracking, biometric data) - **NONE**
- ❌ **Messages:** (Emails, SMS, MMS, instant messages) - **NONE**
- ❌ **Photos and videos:** (User gallery, photos, video files) - **NONE**
- ❌ **Audio files:** (Voice recordings, music files) - **NONE**
- ❌ **Files and docs:** (PDFs, spreadsheets, device documents) - **NONE**
- ❌ **Calendar:** (Calendar events, meeting notes) - **NONE**
- ❌ **Contacts:** (Address book, contact lists) - **NONE**
- ❌ **App activity:** (Page views, in-app search queries, installed apps) - **NONE**
- ❌ **Web browsing history:** - **NONE**

---

## 4. Google Play Console Form Submission Steps
1. Navigate to **Google Play Console** → Select `GrainRun: Wood Planing Guide` (`com.kimrosil.grainrun`).
2. Go to **Policy and programs** → **App content** → **Data safety** → Click **Manage** (or **Start**).
3. Under **Data collection and security**:
   - Check **Yes** for data collection and sharing.
   - Check **Yes** for encrypted in transit.
   - Check **Yes** for data deletion mechanism.
4. Under **Data types**:
   - Expand **Location** → check **Approximate location**.
   - Expand **App info and performance** → check **Crash logs**, **Diagnostics**.
   - Expand **Device or other IDs** → check **Device or other IDs**.
   - Leave all remaining categories unchecked.
5. Under **Data usage and handling**:
   - For each checked item, set collection to **Collected** and **Shared**, mark as **Optional**, and select purposes: **Advertising or marketing**, **Analytics**, **Fraud prevention, security, and compliance**.
6. Review the summary and click **Save** / **Submit**.

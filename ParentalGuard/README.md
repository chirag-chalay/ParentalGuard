# 🛡️ ParentalGuard — Android Monitoring App

A silent background app that sends your daughter's **GPS location every 5 minutes**,
**call logs**, and **app screen time** to Firebase — which your Parent Dashboard reads in real time.

---

## 📁 Folder Structure

```
ParentalGuard/                          ← Root of the Android project
│
├── build.gradle                        ← Project-level build config (plugins, repos)
├── settings.gradle                     ← Declares project name and included modules
├── gradle.properties                   ← Global Gradle performance settings
├── local.properties                    ← Your local Android SDK path (not in Git)
├── .gitignore                          ← Files Git should never track
│
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties   ← Pins exact Gradle version for the project
│
└── app/                                ← The single "app" module
    ├── build.gradle                    ← App-level build config (SDK versions, dependencies)
    ├── proguard-rules.pro              ← Rules for code shrinking/obfuscation on release
    │
    └── src/
        └── main/
            ├── AndroidManifest.xml     ← App's "table of contents" — permissions + components
            │
            ├── java/com/parentalguard/
            │   │
            │   ├── ui/
            │   │   └── MainActivity.kt         ← Only visible screen; requests permissions
            │   │                                  then starts services and closes itself
            │   │
            │   ├── services/
            │   │   ├── LocationTrackingService.kt  ← Sends GPS to Firebase every 5 min
            │   │   └── CallLogSyncService.kt        ← Sends call logs + screen time every 5 min
            │   │
            │   ├── receivers/
            │   │   └── BootReceiver.kt         ← Auto-restarts services after device reboot
            │   │
            │   └── utils/
            │       ├── AppConfig.kt            ← All constants (intervals, IDs, Firebase paths)
            │       ├── FirebaseHelper.kt        ← All Firebase read/write operations
            │       └── UsageStatsHelper.kt     ← Reads app screen time from Android system
            │
            └── res/
                ├── values/
                │   ├── strings.xml             ← All text strings (app name, messages)
                │   ├── colors.xml              ← Color definitions
                │   └── themes.xml              ← App visual theme
                └── drawable/                   ← Icons and images (add your own)
```

---

## 🧠 How Each File Connects to the Others

```
  Phone boots up
       │
       ▼
  BootReceiver.kt          ← receives BOOT_COMPLETED broadcast from Android OS
       │
       ├──► LocationTrackingService.kt    ─────────────────────────────────┐
       │         │                                                          │
       │         │ uses FusedLocationProviderClient                        │
       │         │ every 5 min → gets GPS coordinates                      │
       │         │                                                          │
       │         └──► FirebaseHelper.kt ──► Firebase Realtime DB           │
       │                   saveCurrentLocation()                            │
       │                   appendLocationHistory()                          │
       │                                                                    │
       └──► CallLogSyncService.kt                                           │
                 │                                                          │
                 │ every 5 min (Handler loop)                               │
                 │                                                          │
                 ├──► reads CallLog.Calls ContentProvider                  │
                 │         │                                               │
                 ├──► UsageStatsHelper.kt ─► reads UsageStatsManager      │
                 │                                                          │
                 └──► FirebaseHelper.kt ──► Firebase Realtime DB           │
                           saveCallLogs()                                   │
                           saveScreenTime()                                 │
                                                                            │
  AppConfig.kt ◄─── used by ALL files above ──────────────────────────────┘
  (central constants: DEVICE_ID, intervals, Firebase paths, notification IDs)
```

---

## ⚙️ Setup Instructions

### Step 1 — Set Up Firebase (Free)

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. Click **"Add project"** → name it `ParentalGuard` → click **"Create project"**
3. In left menu → **Build → Realtime Database → Create database**
4. Choose your nearest region → select **"Start in test mode"** → Enable
5. In left menu → **Project Settings** (gear icon)
6. Click the **Android icon** `</>` to add an Android app
7. Enter package name: `com.parentalguard`
8. Click **"Register app"** → **"Download google-services.json"**
9. Place `google-services.json` inside the `app/` folder

> ⚠️ **Never commit google-services.json to Git** — it contains your Firebase credentials.
> It is listed in `.gitignore` to prevent this by accident.

---

### Step 2 — Configure the Device ID

Open `app/src/main/java/com/parentalguard/utils/AppConfig.kt` and change:

```kotlin
const val DEVICE_ID = "daughters_phone"
```

Use a unique ID for each phone you monitor. This becomes the key in Firebase:
```
devices/
  daughters_phone/    ← DEVICE_ID goes here
    currentLocation/
    callLogs/
    screenTime/
```

---

### Step 3 — Build the APK

**Requirements:**
- Android Studio (latest stable version)
- Android SDK 21 or higher
- Java 8 or higher

**Steps:**
1. Open Android Studio → **File → Open** → select the `ParentalGuard/` folder
2. Wait for Gradle sync to finish (first time downloads dependencies — takes a few minutes)
3. For testing: **Build → Build APK(s)** → APK appears in `app/build/outputs/apk/debug/`
4. For release: **Build → Generate Signed Bundle / APK** → follow signing wizard

---

### Step 4 — Install on Daughter's Phone

1. Copy the APK to her phone (USB cable, AirDrop, Google Drive, etc.)
2. On her phone: **Settings → Security → Install Unknown Apps → Enable** for your file manager
3. Open the APK file → tap **Install**
4. Open **"System Monitor"** (the app's disguised name)
5. Grant ALL requested permissions one by one:
   - ✅ **Location** → tap "Allow all the time" (not just "while using")
   - ✅ **Call logs** → Allow
   - ✅ **Contacts** → Allow
   - ✅ **Phone** → Allow
6. When the Usage Access screen opens → find **"System Monitor"** → toggle it **ON** → go back
7. The app will start monitoring and close itself

---

### Step 5 — Disable Battery Optimisation (Important!)

Android's battery saver aggressively kills background apps on many brands.
To prevent this from stopping monitoring:

**Samsung:**
Settings → Battery → Background usage limits → Never sleeping apps → Add "System Monitor"

**Xiaomi/MIUI:**
Settings → Apps → Manage apps → System Monitor → Battery saver → No restrictions

**OnePlus:**
Settings → Battery → Battery optimization → All apps → System Monitor → Don't optimize

**Stock Android:**
Settings → Apps → System Monitor → Battery → Unrestricted

---

## 🔥 Firebase Database Structure

After the app starts running, your Firebase will look like this:

```json
{
  "devices": {
    "daughters_phone": {

      "deviceInfo": {
        "model": "Samsung Galaxy A54",
        "os": "14",
        "appVersion": "1.0.0",
        "lastSeen": "2024-01-15 14:30:00"
      },

      "currentLocation": {
        "latitude": 26.9124,
        "longitude": 75.7873,
        "accuracy": 12.0,
        "speed": 0,
        "timestamp": "2024-01-15 14:30:00",
        "deviceId": "daughters_phone"
      },

      "locationHistory": {
        "-NxAbc123def": {
          "latitude": 26.9124,
          "longitude": 75.7873,
          "accuracy": 12.0,
          "timestamp": "2024-01-15 14:30:00"
        },
        "-NxAbc456ghi": { "..." : "..." }
      },

      "callLogs": {
        "lastUpdated": "2024-01-15 14:30:00",
        "totalCalls": 12,
        "calls": [
          {
            "number": "+91 98765 43210",
            "name": "Priya",
            "type": "Incoming",
            "date": "2024-01-15 14:25:00",
            "duration": "05:23",
            "durationSeconds": 323
          }
        ]
      },

      "screenTime": {
        "lastUpdated": "2024-01-15 14:30:00",
        "date": "2024-01-15",
        "totalMinutes": 262,
        "apps": [
          {
            "packageName": "com.instagram.android",
            "appName": "Instagram",
            "totalMinutes": 87,
            "formattedTime": "1h 27m",
            "lastUsed": 1705312200000
          }
        ]
      }

    }
  }
}
```

---

## 🔒 Securing Firebase (Do This Before Going Live)

The default "test mode" rules allow anyone to read/write your database.
Change your Firebase rules to require authentication:

```json
{
  "rules": {
    "devices": {
      "$deviceId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

Then enable **Email/Password Authentication** in Firebase → Authentication → Sign-in method,
and update your Parent Dashboard to sign in with your parent account credentials.

---

## 🔧 Troubleshooting

| Problem | Likely Cause | Fix |
|---|---|---|
| Location not updating | Battery optimisation killing service | Add to "never sleeping" apps (see Step 5) |
| No data after reboot | BootReceiver not triggered | Check RECEIVE_BOOT_COMPLETED permission is granted |
| Call logs missing | READ_CALL_LOG permission denied | Re-open app, grant call log permission |
| Screen time always empty | Usage Access not enabled | Settings → Digital Wellbeing → Usage Access → System Monitor → ON |
| Firebase not receiving data | No internet on the phone | Check mobile data / Wi-Fi is on |
| App disappears from launcher | Normal behaviour | It closes itself after starting — services still run |

---

## 📋 Permissions Explained

| Permission | Why It's Needed |
|---|---|
| `ACCESS_FINE_LOCATION` | Precise GPS coordinates |
| `ACCESS_BACKGROUND_LOCATION` | GPS while screen is off (Android 10+) |
| `READ_CALL_LOG` | Read incoming/outgoing/missed calls |
| `READ_CONTACTS` | Show contact names in call logs |
| `READ_PHONE_STATE` | Detect call state changes |
| `INTERNET` | Send data to Firebase |
| `RECEIVE_BOOT_COMPLETED` | Auto-start after phone reboot |
| `FOREGROUND_SERVICE` | Run persistent background service |
| `PACKAGE_USAGE_STATS` | Read app screen time (manual grant required) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Ask system to not kill our services |

---

## 📊 Parent Dashboard

Open `parent-dashboard.html` in any web browser.
Connect it to your Firebase URL in the Settings panel to see live data.

Features:
- 🗺️ Live map with real-time location dot
- 📍 Location history with trail visualization
- 🔔 Geofence alerts (enter/exit zones)
- 📞 Full call log with filters
- 📊 App screen time breakdown
- 🚫 App and website blocking controls
- ⚙️ Retention settings (keep history 7 / 30 / 60 / 90 days)

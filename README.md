# Progress Pal for Android

A native Android version (Kotlin and Jetpack Compose) of the Progress Pal web app built on Lovable, with the same screens and rules.

**Sign in with Google** to use the same account and data as the website (https://progress-pal-02.lovable.app). The app opens the website's `/app-login` page in the phone's browser; after "Continue with Google" the browser hands the login back to the app through a `progresspal://auth` link. This works on Huawei phones without Google Play Services. Changes made in the app or on the website show up on both, and edits made offline are sent when the phone is back online. You can also tap "Use without an account" to keep data only on the phone.

## Features

| Screen | What it does |
| --- | --- |
| **Home** | Overall task count and % done. A Saturday-first week calendar. A Daily Tasks card with day-progress and task-progress bars. Project cards with a live deadline countdown and an 🔥 URGENT stamp when less than 24h is left. Links to Challenge, Analytics and Completed. |
| **Project** | Name, target and deadline. A live "time remaining" bar, a circular progress ring and Total/Done/Pending stats. Add tasks with a deadline date/time and a repeat setting (none, once, daily, weekly, monthly). Edit and delete tasks. |
| **Daily Tasks** | Tasks for any date. Recurring tasks (daily, weekly, monthly, or custom days) are ticked off per date. A one-off task is added when you pick a future date. Edit or delete a repeating task for *only this day* or for *this day and all future days*. |
| **Completed** | Finished projects. You can change the completion date and time. |
| **Time vs Task Challenge** | Pick a duration from 30 min to 12 h, add timed tasks or import today's tasks, and see a difficulty score. Start now or schedule for later. Pause and resume, use per-task timers, abandon, view the result card, and see a "time without challenge" counter. |
| **Challenge History** | Won/lost, win rate, best win streak, tasks done, focused time, and past challenges. |
| **Analytics** | Bar chart by hour, day, week, month or year. Current and best streak, best day, average per day, today/week/month/year counts, most productive day, donut charts by weekday and time of day, a 4-week heatmap and a 6-month trend. |
| **Time Tracker** | Works together with the **Simple Time Tracker** app from the Play Store (version 1.61 or newer). Shows its activities as tiles (tap to start or stop the timer *inside* Simple Time Tracker; with Switch mode on, starting one stops the running one), running timers with a live clock, a day timeline of its records (pinch with two fingers to zoom), a **Planner** column on the right of the actual timeline (double-tap a plan to start its activity; tap an actual block to jump to Timers; hold any block to delete it — actual records can only be hidden in Progress Pal, since Simple Time Tracker does not let other apps delete them; plan tasks, projects or activities ahead of time with quick durations 5m…4h; a plan turns bright with ✓ when at least 80% of its time was tracked on its activity, and fades with ✗ when missed; switch the Planner off any time), and totals for today, 7 days, 30 days and everything synced. It syncs every time Progress Pal opens and keeps every synced day on the phone. **Sync Daily Tasks** (on by default) keeps one Daily Task for every Simple Time Tracker activity: names are matched ignoring case, spaces and a leading number like "17.", so nothing is duplicated; renamed activities rename their task, and a paired task you delete is not added again. Daily Tasks that have no activity yet are listed so you can add them in Simple Time Tracker (it does not let other apps create activities). One-time setup: in Simple Time Tracker open Settings → Automated tracking and turn on **Answer queries**. |
| **Account & backup** | Sign in or out, sync now, and save all data to a `.json` file and restore it, for example on a new phone. Open it from the ⋮ menu on Home. |

Recurring tasks reset automatically when a new day, week (Saturday) or month starts.

## Get the APK

Every push runs **Build APK** under GitHub **Actions**. Open the latest run and download the `ProgressPal-apk` artifact. It contains:

- `app-release.apk`: the small, optimized build. Install this one.
- `app-debug.apk`

Copy the APK to the phone and open it. Android will ask you to allow installs from that source. On Huawei: *Settings → Security → Install apps from external sources*.

> The release APK is signed with a debug key so it installs directly. Before publishing to AppGallery or Google Play, create your own keystore and set `signingConfig` in `app/build.gradle.kts`.

## Build it yourself

Requirements: Android Studio (Ladybug or newer) or JDK 17 plus the Android SDK (platform 35).

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

## Project layout

```
app/src/main/java/com/progresspal/app/
├── MainActivity.kt            navigation + backup screen
├── data/
│   ├── Models.kt              Project, Task, Challenge, ...
│   ├── AppStore.kt            on-device JSON storage and all actions
│   └── Logic.kt               scheduling rules, progress, difficulty score
└── ui/
    ├── theme/Theme.kt         colours (same palette as the web app), light/dark
    ├── components/            cards, bars, rings, week strip, task row, dialogs
    └── screens/               Home, Project, Daily Tasks, Completed, Challenge, Analytics
```

---

# TimeTally (time tracker)

A second, separate app in this repo (`timetracker/` module): a simple time tracker that keeps all data on the phone.

| Screen | What it does |
| --- | --- |
| **Timers** | A grid of coloured activity tiles. Tap a tile to start its timer, tap again to stop and save a record. Running timers are shown at the top with a live clock. Hold a tile to edit its name, colour and emoji, change its position, or delete it. Hold a running timer to change its start time, add a comment, or discard it. |
| **Records** | A day timeline from 00:00 to 24:00 with an hour scale: every activity is a coloured block as tall as its duration, showing its name, exact duration (1h 1m 48s) and start–end time. Overlapping timers sit side by side, a red line marks the current time, and empty gaps can be tapped to fill them. Tap a block to edit it. Swipe left or right, or use the day strip at the bottom, to change day. Pinch with two fingers to zoom in as far as single minutes (labels change from hours to minutes), drag to scroll, tap the zoom badge to reset. The ⋯ menu has Go to today, Pick a date and a list view. |
| **Statistics** | Day, week, month, year or all time. A donut chart, stacked bar chart (by hour, day or month), and time and percentage per activity, including untracked time. |
| **Settings** | Allow several timers at once, show untracked time, notification with a Stop button, week start day. Save and restore a `.json` backup, export records to `.csv`, delete all records. |

Download: `TimeTally.apk` from the same **latest-apk** release as Progress Pal, or build it with `./gradlew :timetracker:assembleRelease`.

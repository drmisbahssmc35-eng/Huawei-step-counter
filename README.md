# Progress Pal for Android

A native Android version (Kotlin and Jetpack Compose) of the Progress Pal web app built on Lovable, with the same screens and rules.

**Sign in with Google** to use the same account and data as the website (https://progress-pal-02.lovable.app). The app opens the website's `/app-login` page in the phone's browser; after "Continue with Google" the browser hands the login back to the app through a `progresspal://auth` link. This works on Huawei phones without Google Play Services. Changes made in the app or on the website show up on both, and edits made offline are sent when the phone is back online. You can also tap "Use without an account" to keep data only on the phone.

## Features

| Screen | What it does |
| --- | --- |
| **Home** | Overall task count and % done. A Saturday-first week calendar. A Daily Tasks card with day-progress and task-progress bars. Project cards with a live deadline countdown and an 🔥 URGENT stamp when less than 24h is left. Links to Challenge, Analytics and Completed. |
| **Project** | Name, target and deadline. A live "time remaining" bar, a circular progress ring and Total/Done/Pending stats. Add tasks with a deadline date/time and a repeat setting (none, once, daily, weekly, monthly). Edit and delete tasks. |
| **Daily Tasks** | Tap **+** with an empty box to open the Activity List (same as Simple Time Tracker), pick an activity, set a daily goal (5m…2h, or none) and the repeat; the task ticks itself once that much time is tracked on the activity that day, drawn like Simple Time Tracker's list in the activity's colour: each bar starts empty and fills as time is tracked (here, on the timeline or in Simple Time Tracker), showing "6m 20s / 10m · 63%". Tap the bar to start/stop the timer, tap the circle to tick it by hand (finish early), hold it to change the goal or delete. Every task has an edit (pencil) button, and a ≡ handle: drag it up or down to reorder the list (the order is remembered). Typing a name and tapping + adds a plain task. | Tasks for any date. Recurring tasks (daily, weekly, monthly, or custom days) are ticked off per date. A one-off task is added when you pick a future date. Edit or delete a repeating task for *only this day* or for *this day and all future days*. |
| **Completed** | Finished projects. You can change the completion date and time. |
| **Time vs Task Challenge** | Pick a duration from 30 min to 12 h, add timed tasks or import today's tasks, and see a difficulty score. Start now or schedule for later. Pause and resume, use per-task timers, abandon, view the result card, and see a "time without challenge" counter. |
| **Challenge History** | Won/lost, win rate, best win streak, tasks done, focused time, and past challenges. |
| **Project Portfolio** | A Gantt-style page (Home → Project Portfolio): each ongoing project with a Health ring (its progress) and an arrow bar from start to end on a calendar timeline (month names, and day dates or week-start dates) with a dashed Today line. Each project arrow fills up to the project's progress (tasks and their subtasks) and holds two bright bars: time ("1d 18h left · 24% passed") and tasks ("4/10 tasks · 40%"). Tap an arrow to open its tasks: each task has a tick box and progress bar and opens its subtasks, which open their own subtasks to any depth; add, rename (hold) and delete at every level, and add / rename / delete projects. Tap a project's name (or hold its arrow) to set its start and end, colour, phases (e.g. Planning → Operations → Sign off, each its own arrow), order, or hide it; switch the scale between hours, days (default), weeks, months and quarters, and show completed projects. |
| **Vinay Watch Tracker** | The Vinay Watch Tracker web app, built in (Home → Vinay Watch Tracker): load Dr. Vinay Kumar Singh's 146 videos (or add your own), play them inside the app (the YouTube page opens in an in-app viewer, full screen works) with the watch position saved every few seconds (age-restricted ones open in YouTube with a timer to log), mark complete, rewatch, delete and sort. Daily target bar, total and library watch time, videos done, current and highest streak, 30-day average, a 14-day chart and 23 career-rank milestones from Beginner to Professor with the date each was reached. While a video plays, the "Dr. Vinay Sing Video" activity runs in Simple Time Tracker (started when the player opens, stopped when it closes or the video ends, with the video title as comment), so watching shows on the timeline, Daily Tasks and scores. Kept on the phone. |
| **Orthopaedic Genius** | The Orthopaedic Genius (OrthoPrep / Ortho Exam Master) web app, built in (Home → Orthopaedic Genius): generate 10-question AI MCQ exams at FRCS / MS / FCPS standard from Apley & Solomon's and Campbell's (same question generator as the web app, needs internet), mixed with up to 3 earlier wrong answers. 10-minute timer with time and progress bars, instant right/wrong with explanation plus Google Images and YouTube links, resume an open exam, exam history with score, review wrong answers, delete. Daily question goal ring with streak and best streak, total / unique / per-day stats, 12 career ranks from Orthopaedic Intern (1,000) to Professor (20,000) with celebrations, chapter selection, and analytics (daily/weekly/monthly/yearly questions, score trend, time spent, milestones, weak topics). While an exam runs, an STT activity named with "Ortho", "MCQ", "Exam" or "Question" is recorded. Kept on the phone. |
| **Analytics** | **Productivity Level** at the top: each prayer done = 10, each completed task = 1, each plan done = 1 (e.g. 3 prayers + 8 tasks = 38). Shows today vs yesterday, this week / month / year, lifetime, best day and daily average, bright column charts by hour, day, week, month, year and lifetime growth (with % vs the previous period), and the level of any day you pick. Then the **Plan Score**: how many of the day's 24 hours you planned, in hours and % (e.g. 6.5h · 27%; overlapping plans count once), for today, tomorrow, 7/30-day and lifetime averages and a 14-day chart. Then the **Implement Score** (%): of all the time you planned, how much was spent on the planned activity at the planned time (counted up to now for today), shown for today with a bar, yesterday, 7 and 30 days, lifetime and a 14-day chart. Below that: | Bar chart by hour, day, week, month or year. Current and best streak, best day, average per day, today/week/month/year counts, most productive day, donut charts by weekday and time of day, a 4-week heatmap and a 6-month trend. |
| **Time Tracker** | Works together with the **Simple Time Tracker** app from the Play Store (version 1.61 or newer). Shows its activities as tiles (tap to start or stop the timer *inside* Simple Time Tracker; with Switch mode on, starting one stops the running one), running timers with a live clock, a day timeline of its records (pinch with two fingers to zoom), a **Planner** column on the right of the actual timeline (tap a running block to switch it to another activity, keeping its start time, like Simple Time Tracker; hold any actual or planned block (or tap a finished one) and choose ✓ Done when you finished before the planned time — the plan then counts as 100% done in the scores; tap the Planner column or + Plan → pick from the Activity List → set From (now, or right after the previous plan), a quick length 5m…3h and Repeat: once, daily, weekly, monthly, yearly or custom days; double-tap a plan to start its activity; tap an actual block to jump to Timers; hold any block to delete it — actual records can only be hidden in Progress Pal, since Simple Time Tracker does not let other apps delete them; plan tasks, projects or activities ahead of time with quick durations 5m…4h; a plan turns bright with ✓ when at least 80% of its time was tracked on its activity, and fades with ✗ when missed; switch the Planner off any time), and totals for today, 7 days, 30 days and everything synced. It syncs every time Progress Pal opens and keeps every synced day on the phone. Tasks are paired with activities by name (ignoring case, spaces and a leading number like "17."), so nothing is duplicated. When an activity is tracked for at least 1, 5, 15 or 30 minutes in a day (your choice, or Off), its Daily Task is ticked for that day automatically; un-ticking it by hand is respected. Daily Tasks that have no activity yet are listed so you can add them in Simple Time Tracker (it does not let other apps create activities). **Goals** come from Simple Time Tracker itself: it only shares them through its backup file, so turn on Settings → Backup → Automatic backup there, then in Time Tracker → Activity List tap "Choose backup file" and pick `stt_automatic.backup`. On every sync Progress Pal asks Simple Time Tracker to refresh that backup and reads the daily goals, which then drive the Daily Task bars, auto-ticks and the Activity List. One-time setup: in Simple Time Tracker open Settings → Automated tracking and turn on **Answer queries**. |
| **Day widget** | A second home-screen widget, "Progress Pal · Day", that can be resized up to the whole screen: today's full timeline (Actual on the left, Planner on the right, with done/missed marks and the red now-line), today's Productivity Level with its salat/task/plan breakdown today's Plan Score (hours and %) and Implement Score, and the salat streak beads. It has zoom buttons (home-screen widgets cannot be pinched): ＋ halves the visible time down to a single minute, − doubles it back to the whole day, ▲ / ▼ move half a window earlier or later (into other days too), and Now returns to the current time. It redraws when data changes and about every 15 minutes; double-tap it to open Time Tracker, where the timeline can be pinch-zoomed. |
| **Focus widget** | "Progress Pal · Focus": three pages picked with tabs (home-screen widgets cannot be swiped sideways). **Timeline**: today in eight 3-hour parts (12–3am … 9pm–12am), Actual and Planner side by side; scroll up/down between parts, it opens on the current one. Each part starts with a 3-hour timer bar (time passed in those 3 hours) and a bar of how much of the time planned in those 3 hours was actually spent on the planned activity at the planned time; double-tap a part to open the timeline. **Tasks**: today's Daily Tasks in your order, with goal progress bars; tap one to tick it. **Projects**: ongoing projects with progress bars. ↗ opens the matching screen in the app. |
| **Score widget** | "Progress Pal · Score": today's Productivity Level in a big bright number with ▲/▼ versus yesterday and the last 7-day average; Plan Score (hours and %) and Implement Score bars; the salat bead streak with how many prayers were missed since the last one prayed (only prayers whose time has passed count); day and task progress bars; and a live timer of the running activity. Tap the timer to open Simple Time Tracker, anywhere else to open the Progress Pal timeline. |
| **Azan at Fajr** | Plays the Azan at the planned Fajr time (the Planner's Fajr plan, else the Fajr task's time, else 5:00), even with the app closed, with a notification that has a Stop button. It stops by itself as soon as you start Fajr — its activity running in Simple Time Tracker or the Fajr task ticked. Switch it on/off in Time Tracker → Activity List. |
| **Live widgets** | A quiet background service (on by default, switch in Time Tracker → Activity List) keeps the widgets live: they update the moment a timer starts or stops in Simple Time Tracker (turn on its Settings → Automated tracking → Send events) and every minute while the screen is on. |
| **Simple Time Tracker button** | A small round button with Simple Time Tracker's own icon floats on every page and opens that app. Drag it anywhere if it covers something; it stays where you leave it. |
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

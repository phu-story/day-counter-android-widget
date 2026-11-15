# Day Counter

An Android app and home screen widget that counts down to (or up from) your important dates.

## Features

**App**
- Add, edit and delete important dates, each with an optional time.
- Each date has a "progress starts" date that sets where its progress bar begins.
- The Widgets tab lists every widget on your home screen so you can customize each one.

**Widget**
- Each widget has its own settings. Several widgets can show different dates in different styles.
- Show all dates (including ones added later) or pick specific dates for each widget.
- Swipe up or down to move between dates.
- Show only the title, or the title with a countdown.
- Count in years, months, weeks, days, hours, minutes or seconds, with 0–3 decimal places.
- Optional progress bar from the start date to the target date.
- Font (sans, serif, mono, cursive), bold, text size and text colour.
- Background colour and opacity, or fully transparent.

## Requirements

- Android 8.0 (API 26) or newer on the device
- JDK 17 or newer
- Gradle 9.x
- Android SDK with `platforms;android-36` and `build-tools;36.0.0`

## Setup

1. Install the Android SDK. With Homebrew:

   ```sh
   brew install --cask android-commandlinetools
   export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
   export PATH="$ANDROID_HOME/platform-tools:$PATH"
   sdkmanager --licenses
   sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
   ```

2. Tell Gradle where the SDK is, either through `ANDROID_HOME` or a `local.properties` file in the project root:

   ```properties
   sdk.dir=/opt/homebrew/share/android-commandlinetools
   ```

   `local.properties` is machine-specific; don't commit it.

## Build and run

Turn on **USB debugging** on your phone (Settings → About phone → tap Build number 7 times → Developer options → USB debugging), plug it in, and accept the prompt. Check it is connected:

```sh
adb devices
```

Then:

```sh
gradle assembleDebug        # build app/build/outputs/apk/debug/app-debug.apk
gradle installDebug         # build and install on the connected device
gradle testDebugUnitTest    # run unit tests
gradle lintDebug            # run Android lint
```

To add the widget, use **Add widget to home screen** in the app's Widgets tab, or long-press the home screen → Widgets → Day Counter.

## Project structure

```
app/src/main/java/com/example/widgetapp/
├── MainActivity.kt          App: Dates tab, Widgets tab, add/edit date dialog
├── WidgetConfigActivity.kt  Customize screen the launcher opens for one widget
├── WidgetEditor.kt          Shared widget editor UI and live preview
├── HomeWidget.kt            The Glance widget and its receiver
├── WidgetRefresh.kt         Alarm that keeps countdowns current
├── RefreshPlan.kt           Works out when the widgets next need redrawing
├── Countdown.kt             Countdown, progress and sorting logic
├── Models.kt                ImportantDate, WidgetSettings, JSON (de)serialization
└── Store.kt                 DataStore persistence for dates and per-widget settings
app/src/test/                Unit tests for the countdown maths and settings
```

## How it works

- Dates and widget settings are stored in a Preferences DataStore. Each widget's settings are saved under `widget_<id>`, and removed when the widget is removed.
- The widget is built with Jetpack Glance.
- Whole-day countdowns compare calendar dates, so an all-day event shows "Today" for the whole day. Year and month countdowns follow the real calendar, including leap years.

## Battery use

The widgets are redrawn only when something they show changes:

- One alarm is set for the exact moment a countdown number next changes, a date arrives, or the day rolls over (`RefreshPlan.kt`). A widget counting whole days is redrawn once a day; one counting minutes, once a minute.
- The alarm is non-wakeup, so it never wakes a sleeping phone. It fires as soon as the phone is next awake.
- If the alarm fires with the screen off and another is due within two minutes, the redraw is skipped.
- Android's own periodic widget refresh, which does wake the phone, is set to once a day as a safety net.
- The app's on-screen clock stops when the app is in the background.

The exception is a **seconds** countdown. It redraws the widget every second whenever the screen is on, in any app, and uses a small but constant amount of CPU. Every other unit costs nothing between refreshes.

## Known limitations

- Swiping between dates is vertical. Android launchers keep horizontal swipes for switching home screen pages.
- The seconds countdown only ticks while the screen is on and may take up to a minute to resume after unlocking.
- Colours are chosen from a preset palette; there is no custom colour picker.
- There is no app icon yet.
- Compose is pinned to BOM `2026.06.01` because newer releases require compile SDK 37.

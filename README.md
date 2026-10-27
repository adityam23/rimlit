# Rimlit

Rimlit lights up the edges of your screen when a notification arrives — Samsung-style edge lighting for any Android phone. It is free, open source, has no ads, and never touches the network.

| Home | Style | On screen | Screen off |
|---|---|---|---|
| ![Home screen](screenshots/home.png) | ![Style screen with live preview](screenshots/style.png) | ![Glow over the launcher](screenshots/overlay.png) | ![Always-on-display style glow](screenshots/lock-screen.png) |

## Features

- **Works everywhere:** while you use the phone the glow is drawn over whatever is on screen; when the screen is off it wakes into a black, always-on-display style glow. With the optional lock screen glow it also frames the lock screen itself.
- **Three styles:** a crisp breathing edge, a soft bloom with a bright core, or a blade of light racing around the screen.
- **Your colors:** one to three colors picked automatically from the notifying app's icon (or from the notification's picture, such as the sender's photo), a single custom color, a gradient of up to five colors, or a flowing rainbow. Any app can get its own palette.
- **Tune it:** thickness, glow width, speed, brightness and duration, with a live preview that updates as you drag.
- **Fits your screen:** follows your display's real rounded corners, and can ring the camera cutout instead of the whole edge.
- **Respectful:** honors Do Not Disturb, skips silent and low-priority notifications, and never covers incoming-call or alarm screens. Any app can be excluded.

## Install

Rimlit needs Android 10 or newer. Download the latest APK from [Releases](https://github.com/adityam23/rimlit/releases) and open it on your phone (you may need to allow installs from your browser or file manager).

Open Rimlit, turn on **Edge lighting**, and grant the permissions listed on the home screen:

- **Notification access** — to notice when notifications arrive. Rimlit only looks at which app posted it and its colors; nothing is stored or sent anywhere.
- **Display over other apps** — to draw the glow while you use the phone.
- **Notifications** — Rimlit wakes the screen with a silent notification of its own, so keep its notifications and its "Edge lighting" category on.
- **Full-screen notifications** — to wake the screen and show the glow when it is off.
- **Lock screen glow** (optional) — an accessibility service that lets Rimlit draw the glow above the lock screen while it is showing. It only owns the glow window: it receives no accessibility events and cannot read or control anything on screen. On Android 13 and newer you may first need to tap **Allow restricted settings** in Rimlit's App info menu.
- **Unrestricted battery** (recommended) — so the system doesn't delay the glow. Some phones (Xiaomi, Huawei, OnePlus) also need autostart enabled in their own battery settings.

## Privacy

Rimlit has no internet permission at all, so it cannot send anything off your device. There are no analytics, ads or accounts. Settings stay on the phone and are excluded from cloud backups.

## Build from source

You need JDK 25, Gradle 9.8 and the Android SDK (platform 36). The project intentionally has no Gradle wrapper, so use your installed `gradle`:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:detektMain
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Releases

Grab signed APKs from [GitHub Releases](https://github.com/adityam23/rimlit/releases).

## License

Rimlit is licensed under the [GNU General Public License v3.0](LICENSE).

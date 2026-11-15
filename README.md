# Math Stars ⭐

A math practice game for kids on Android: addition, subtraction and multiplication for school grades 1 to 6.

## Download

Get the **MathStars APK** from the [latest release](../../releases/latest), open it on an Android phone or tablet (Android 8.0 or newer) and tap Install. Android asks once to allow installing apps from that source; if Play Protect warns that the app isn't from the Play Store, tap *Install anyway*.

## How it plays

- **Who's playing?** Each child has their own player (a name, an animal and a grade) with their own stars, best scores and settings. Press and hold a player to change or delete them.
- **Pick a grade:**

  | Grade | Addition / subtraction | Multiplication |
  |---|---|---|
  | 1 | Numbers up to 20 | Doubling small numbers |
  | 2 | Up to 100 | Tables of 2, 3, 4, 5 and 10 |
  | 3 | Up to 1,000 | All tables up to 10 × 10 |
  | 4 | Up to 10,000 | 2-digit × 1-digit |
  | 5 | Up to 100,000 | 3-digit × 1-digit, 2-digit × 2-digit |
  | 6 | Negative numbers | Negative numbers up to 12 × 12 |

- **Pick how to answer:** tap one of four answers (the default for grades 1 and 2), or type the answer on a number pad (the default from grade 3 on).
- **Choose a game:** Addition, Subtraction, Multiplication or Mix it up.
- **Learn the times tables (1 to 12):** each table has a page with the whole table, a tip for remembering it, and a "hide the answers" mode to test yourself. *Practice* asks ×1 to ×10 of that table once each, in a random order, and gives stars for each table.
- A round is **10 questions** with a timer each and **3 hearts**. A wrong answer or running out of time costs a heart and shows the right answer.
- **Points:** 10 for each right answer, more for answering fast, and a bonus for 3 or more right in a row.
- **Stars:** ★★★ for 10 out of 10, ★★ for 8 or more, ★ for 5 or more.

No internet, no ads, no accounts: everything stays on the device.

## Build

Needs a full JDK (with `javac`) and the Android SDK (compile SDK 36, build tools 37.0.0).

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
JAVA_HOME=/path/to/jdk ./gradlew assembleDebug testDebugUnitTest
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`. To install it on a phone or tablet connected over USB (with USB debugging on):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Code

Plain Android framework views in Kotlin, with no libraries at runtime. Android 8.0 (API 26) or newer.

- `MathModel.kt`: grades, question generator, answer choices, and the game round (score, hearts, stars). Unit tests in `app/src/test`.
- `Profiles.kt`: players and the "Who's playing?" screen.
- `MainActivity.kt`: choose grade, answer mode and game.
- `TablesActivity.kt`: the times tables grid and each table's learn page.
- `GameActivity.kt`: the round (timer, answer buttons or number pad).
- `ResultActivity.kt`: stars, score and confetti.
- `Support.kt`: each player's saved progress, UI helpers, sounds.

# HorusGram for Android

HorusGram is an unofficial Telegram client. This is its Android version, built on [Nekogram](https://github.com/Nekogram/Nekogram), which is built on [Telegram for Android](https://github.com/DrKLO/Telegram). The desktop version is [HorusGram Desktop](https://github.com/plshours/HoursGram-Desktop).

What HorusGram adds, and where everything comes from, is in [NOTICE.md](NOTICE.md). Nekogram's own readme is kept as [NEKOGRAM_README.md](NEKOGRAM_README.md).

## Getting an APK

Every push to `main` or to a `feature/...` branch builds a debug APK on GitHub Actions: open the **Actions** tab, the latest run of *HorusGram Android Debug Build*, and download **HorusGram-Android-debug** at the bottom. A manual run (*Run workflow*) can build for more architectures than `arm64-v8a`.

Repository secrets the build reads:

| Secret | What it is |
|---|---|
| `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` | your own credentials from <https://my.telegram.org>. Without them the build uses public development values. |
| `GOOGLE_SERVICES_JSON` | optional: `google-services.json` of your Firebase project for the package names `com.horusgram.android` and `com.horusgram.android.beta`, base64-encoded. Without it there are no Firebase push notifications. |

## Building on your computer

1. `git clone --recursive https://github.com/plshours/HorusGram---Android.git`
2. Write `apiId=...` and `apiHash=...` into `local.properties`.
3. Copy `Tools/horusgram/google-services.placeholder.json` to `TMessagesProj_App/google-services.json`, or put your own file there.
4. `./gradlew :TMessagesProj_App:assembleDebug -PhorusAbis=arm64-v8a`

## The theme

`TMessagesProj/src/main/assets/horusgram.attheme` is generated from Telegram's Night theme:

    python Tools/horusgram/make_horusgram_theme.py TMessagesProj/src/main/assets/night.attheme TMessagesProj/src/main/assets/horusgram.attheme

Change the colors in the script, not in the generated file.

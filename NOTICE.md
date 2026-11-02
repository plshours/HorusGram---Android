# Notice

HorusGram for Android is a modified version of other people's free software. This file says whose, what was changed, and how to reach us.

## Where this code comes from

1. **Telegram for Android** — https://github.com/DrKLO/Telegram
   Copyright (c) Nikolai Kudashov and the Telegram authors. GNU GPL version 2 or later, as the headers of its files say. See [LICENSE](LICENSE).
2. **Nekogram** — https://github.com/Nekogram/Nekogram
   Copyright (c) the Nekogram authors. Distributed under the GNU GPL version 2, text in [LICENSE](LICENSE). Its sources are in `TMessagesProj/src/main/java/tw/nekomimi/nekogram/` and keep their original form. This repository carries the full history of Nekogram; HorusGram starts from its commit `199abde0f` of 28 October 2026.
3. **HorusGram Desktop** — https://github.com/plshours/HoursGram-Desktop
   The HorusGram palette and the way its colors are customized come from the desktop client (`Telegram/Resources/hoursgram.tdesktop-palette`, `Telegram/SourceFiles/hoursgram/customization/`). Only colour values and the method were carried over; the Android code is written anew.

Third-party libraries built with the client keep their own licences: the native ones are in `TMessagesProj/jni/` and its submodules, the Java ones are listed in the Gradle files.

## What HorusGram changed

Dates are those of the commits in the git history, from November 2026.

- the HorusGram theme (`TMessagesProj/src/main/assets/horusgram.attheme`, built from Telegram's Night theme by `Tools/horusgram/make_horusgram_theme.py`), used by default on a new install;
- a HorusGram section in the settings (`TMessagesProj/src/main/java/com/horusgram/`): five base colors whose shades follow them, every palette color by group, tails of incoming and outgoing bubbles separately, bubble corner radius, chat background;
- tail-less bubbles in `org/telegram/ui/ActionBar/MessageDrawable.java`;
- the name, icon and application id (`com.horusgram.android`), so that HorusGram installs next to Telegram and Nekogram;
- analytics and crash reports off unless the user turns them on;
- the build on GitHub Actions (`.github/workflows/android-debug.yml`); Nekogram's own release workflows were removed.

Every file changed keeps the header of its origin. Files that are entirely new carry the HorusGram header.

## Licence of this work

Telegram for Android is licensed under the GNU GPL version 2 or later, and Nekogram ships the text of version 2. To stay compatible with both, the new files of HorusGram for Android are licensed under the **GNU General Public License version 2 or (at your option) any later version**. You may use, study, change and share it under those terms. The complete corresponding source of every APK is this repository, at the commit named by the build.

This program comes with **no warranty**, to the extent permitted by law.

## Not Telegram

HorusGram is an independent, unofficial client. It is not made, approved or supported by Telegram FZ-LLC or Telegram Messenger Inc, nor by the Nekogram authors. Telegram is a trademark of its owner. The Telegram logo is not used as the logo of this project.

## Telegram API credentials

Telegram asks every client to use its own `api_id` and `api_hash`, issued at <https://my.telegram.org>. The workflow reads them from the repository secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH`. Until those are set, it falls back to the public development values from Telegram Desktop's build documentation, which are meant for building and testing only and should not be used for a build you hand to other people.

## If you are an author and something is missing

If you wrote part of this code and think your credit, your licence or your terms are not respected here, open an issue on this repository or write to the maintainer through the profile page. We will correct it quickly, and remove the code if you ask us to.

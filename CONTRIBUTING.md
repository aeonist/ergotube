# Contributing to ErgoTube

Thanks for wanting to help. ErgoTube is a small project with one strong opinion, so a few minutes with this page saves everyone time.

## The one rule

**Everything you tap often sits under the thumb of the hand that holds the phone.** Fixed controls live in the bottom 55% of the screen, on the hand's side, and mirror for the left hand. `ReachRuleTest` walks every screen for both hands and fails if a control drifts above the line. A feature that needs a button at the top of the screen needs a very good reason.

The product principles are in [`PRODUCT.md`](PRODUCT.md), and the history of every decision in [`docs/DECISIONS.md`](docs/DECISIONS.md). Read the relevant part before changing behaviour that was decided on purpose.

## Reporting bugs

Use the [bug report form](https://github.com/aeonist/ergotube/issues/new/choose). Include the build, the phone and Android version, and whether you were signed in. A screen recording is worth a thousand words.

**Never paste cookies, tokens, `adb` dumps of app storage or anything from your Google account.** Security problems go through [private advisories](https://github.com/aeonist/ergotube/security/advisories/new), see [`SECURITY.md`](SECURITY.md).

## Setting up

You need JDK 21 and the Android SDK with platform `android-37.0` and build-tools 37.

```bash
git clone https://github.com/aeonist/ergotube.git
cd ergotube
./gradlew assembleDebug testDebugUnitTest
```

Open the folder in Android Studio if you like; it is a single `app` module. Debug builds install as `app.ergotube.debug` next to a release build.

## Making a change

1. Branch from `main`.
2. Keep the change focused: one fix or one feature per pull request.
3. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before you push. CI runs the same.
4. If you touched screens, look at them on a phone or an emulator and attach before/after screenshots.
5. Open a pull request and fill in the template.

### Code conventions

- Kotlin, Jetpack Compose, Material 3. Code and comments in English.
- UI strings go in `res/values/strings.xml` **and** `res/values-ru/strings.xml`.
- InnerTube parsers live in `innertube/parse` and are tested against saved responses in `app/src/test/resources/innertube`. When YouTube changes a format, save the new response there and add a test.
- Show and hide things through the blur helpers in `ui/motion/Motion.kt` (`blurIn`, `blurOut`, `BlurVisibility`); nothing slides.
- Check library APIs and YouTube formats against their sources (NewPipeExtractor, yt-dlp, YouTube.js, Metrolist) rather than guessing.

### Account safety

- Account cookies may only be sent to `youtube.com` and `googlevideo.com`. Never log cookies or tokens.
- Every action on the user's account (like, subscribe, save, comment) goes through `ActionGuard`.
- Automated tests never sign in to a real account; use the structural fixtures.

## License

By contributing you agree that your work is released under the [GPL-3.0](LICENSE) with the attribution term in [`NOTICE`](NOTICE).

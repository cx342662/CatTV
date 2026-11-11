# CatTV TV source preview
Build: `./gradlew :app:assembleDebug :app:testDebugUnitTest -PCATTV_TV_BUILD=true`.
This independent source preview includes the native TV home, source management and QR pairing, TMDB artwork/manual matching, live guide, and TV-only release notifications. It does not contain the reference APK, its decoded code, signing keys, or private TMDB credentials.
The preview does not yet reproduce all settings or the System playback engine. CI debug artifacts are not in-place updates for locally signed APKs. Publish only a verified APK with the matching package, signer and versionCode to a `tv-arm32-vN` release with asset `CatTV-TV-ARM32-vN.apk`. Do not designate TV releases as repository Latest.

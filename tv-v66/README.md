# CatTV TV v66

This is the patch source corresponding to the v66 reference-based APK, not a standalone Gradle source tree. v67 is a separate CatTV source preview and does not reproduce v66.

The patch includes CatTV identity, logos, site navigation, senior mode and the TV update hook. The reference APK and its original code, libraries, and signing keys are not included. Supply your own copy of the exact reference APK (SHA-256 d835dc6c3b1e8833a774e7deeeca90f6b4399a25036ec704828957b9b14d5a36). Respect its original license. Repository-authored Java is AGPL-3.0-only.

Rebuild from this branch on Linux with Bash, JDK 21, Apktool 3.0.3, Android SDK platform 37, Android build tools 37.0.0, Git and zip. Set ANDROID_HOME and run `bash tv-v66/build.sh /path/to/reference.apk /path/to/empty-build-dir`. This produces an aligned unsigned APK. Sign it separately with your authorized keystore; only the same certificate as the release can update an existing installation.

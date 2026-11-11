# CatTV TV update client

This directory contains the CatTV-authored Android update checker under the repository's AGPLv3 license. It does not contain the reference APK, reverse-engineered third-party code, signing keys or private API credentials. It is an integration component, not the full source of the reference-based APK.

Call `TvUpdate.check(Activity)` when the TV home resumes. The client checks public GitHub Releases without a token, selects a higher `tv-arm32-vN` tag only if it carries the matching `CatTV-TV-ARM32-vN.apk` asset, and opens the release page after confirmation. The Android installer handles installation. It never silently installs updates.

Publish TV versions as a separate prerelease and do not make them the repository's Latest release. Preserve the application ID and signing certificate, and keep the APK versionCode equal to N. Install a version containing this checker once before expecting update prompts. The reference-derived v66 APK is not published by this branch.

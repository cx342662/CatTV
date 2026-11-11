# Add a feature to the v66 line

Edit this branch, not main or tv-source-preview. The build uses the public, checksum-pinned v66 APK as its base. It does not require the original reference file for incremental changes.

1. Decode the published v66 APK with Apktool 3.0.3. Make the change in the decoded resources or smali, then save a unified diff in tv-v66/dev/patches/ (paths relative to the decoded directory). Patches apply in filename order. For example, a 010-home.patch can change a TV home layout.
2. Commit the patch to tv-v66. GitHub Actions runs “Build CatTV TV from v66” and provides an unsigned APK in Artifacts. The version is read from tv-v66/dev/version.txt; increase it before distributing a new version.
3. Align and sign the APK locally with the same certificate as the installed CatTV TV version, then test it on a TV. Only then publish a TV prerelease with tag tv-arm32-vN and file CatTV-TV-ARM32-vN.apk. Never mark it as repository Latest.

Unsigned artifacts cannot update an installed app. Do not commit signing keys or TMDB credentials. This development path keeps v66 behavior, but modifying the reference player internals still requires editing smali or implementing a replacement; this is not an original source project.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isCatTvTvBuild = providers.gradleProperty("CATTV_TV_BUILD").map(String::toBoolean).getOrElse(false)

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.avbox.osc"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = if (isCatTvTvBuild) "com.github.avbox.osc.tv" else "com.github.avbox.osc"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Release workflow supplies version values; local debug keeps current defaults.
        versionCode = (project.findProperty("CATTV_VERSION_CODE") as String?)?.toIntOrNull() ?: if (isCatTvTvBuild) 67 else 37
        versionName = (project.findProperty("CATTV_VERSION_NAME") as String?) ?: if (isCatTvTvBuild) "1.3.2-TV-SourcePreview" else "1.3.3"
        buildConfigField("Boolean", "TV_MODE", isCatTvTvBuild.toString())
        val tmdbKey = if (isCatTvTvBuild) providers.environmentVariable("CATTV_BUILTIN_TMDB_KEY").orElse("").get().trim() else ""
        require(tmdbKey.isEmpty() || tmdbKey.matches(Regex("[a-fA-F0-9]{32}"))) { "Invalid TMDB v3 key format" }
        buildConfigField("String", "TV_TMDB_KEY", "\"$tmdbKey\"")
        manifestPlaceholders["supportsPip"] = (!isCatTvTvBuild).toString()
        manifestPlaceholders["tvOnly"] = isCatTvTvBuild.toString()
        manifestPlaceholders["screenOrientation"] = if (isCatTvTvBuild) "landscape" else "unspecified"
        manifestPlaceholders["launcherActivity"] = if (isCatTvTvBuild) ".ui.tv.TvHomeActivity" else ".ui.MainActivity"
        manifestPlaceholders["launcherTheme"] = if (isCatTvTvBuild) "@style/TvTheme" else "@style/AppTheme.Splash"
        manifestPlaceholders["appBanner"] = if (isCatTvTvBuild) "@drawable/cattv_tv_banner" else "@mipmap/ic_launcher"
        multiDexEnabled = true
        ndk {
            abiFilters += if (isCatTvTvBuild) setOf("armeabi-v7a") else setOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/beans.xml")
        }
    }

    androidResources {
        localeFilters += listOf("en", "zh", "zh-rCN", "b+zh+Hant", "zh-rTW", "zh-rHK")
    }

    sourceSets {
        if (isCatTvTvBuild) getByName("test").java.directories += "src/tvTest/java"
        getByName("main") {
            java.directories += "src/python/java"
            if (isCatTvTvBuild) java.directories += "src/tv/java"
            if (isCatTvTvBuild) res.directories += "src/tv/res"
        }
    }

    signingConfigs {
        // Only create the release signing config when the complete private signing
        // material is supplied. Debug builds must never try to read a legacy JKS
        // from the repository or cast missing passwords to non-null strings.
        val releaseStorePath = project.findProperty("RELEASE_STORE_FILE") as String?
        val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
        val releaseKeyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String?
        val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?
        val releaseStoreFile = releaseStorePath?.let { rootProject.file(it) }

        if (releaseStoreFile?.isFile == true &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-python.pro")
        }
    }
    splits {
        abi {
            isEnable = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("AVBox_${variant.buildType}.apk")
        }
    }
}

dependencies {
    if (isCatTvTvBuild) implementation(libs.androidx.leanback)
    api(fileTree("libs") { include("*.jar", "*.aar") })

    implementation(libs.nanohttpd)
    implementation(libs.cling.core)
    implementation(libs.cling.support)
    compileOnly(libs.cdi.api)
    compileOnly(libs.javax.inject)
    compileOnly(libs.javax.annotation.api)
    compileOnly(libs.javax.servlet.api)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.media)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.okio)
    implementation(libs.gson)
    implementation(libs.autosize)
    implementation(libs.xstream) {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "xpp3", module = "xpp3_min")
    }
    // MMKV 2.x intentionally drops 32-bit support. Keep phone builds on 2.x;
    // the dedicated TV armeabi-v7a build must use the 1.3.x LTS line.
    if (isCatTvTvBuild) {
        implementation("com.tencent:mmkv:1.3.14")
    } else {
        implementation(libs.mmkv)
    }
    implementation(libs.danmaku.flame.master)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.rtmp)
    implementation(libs.media3.database)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.media3.effect)
    implementation(project(":quickjs"))
    implementation(project(":pyramid"))

    implementation(libs.xx.permissions)
    implementation(libs.jsoup)
    implementation(libs.commons.io)
    implementation(libs.juniversalchardet)
    implementation(libs.zxing.core)
    implementation(libs.sardine) {
        exclude(group = "xpp3", module = "xpp3")
    }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.materialkolor)
    implementation(project(":libs:backdrop"))
    implementation(libs.capsule)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

configurations.configureEach {
    exclude(group = "io.antmedia", module = "rtmp-client")
}



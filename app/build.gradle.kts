import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.rimlit"
    //noinspection GradleDependency -- staying on 36 until the android-37 platform is adopted
    compileSdk = 36

    defaultConfig {
        applicationId = "app.rimlit"
        minSdk = 29
        //noinspection OldTargetApi -- staying on 36 until the android-37 platform is adopted
        targetSdk = 36
        // Both are literals: F-Droid reads them from this file with a regex and never runs Gradle. versionCode is
        // major × 10000 + minor × 100 + patch of versionName, so every release installs as an update.
        versionCode = 109
        versionName = "0.1.9"
        val (major, minor, patch) = versionName!!.split('.').map(String::toInt)
        check(versionCode == major * 10_000 + minor * 100 + patch) {
            "versionCode $versionCode doesn't match versionName $versionName: bump both together"
        }
    }

    // AGP otherwise adds a list of the dependencies, encrypted for Google Play, to the APK's signing block. Nobody
    // else can read it, so F-Droid's scanner rejects an APK that carries it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    // Release signing comes only from environment variables (GitHub secrets in CI); nothing secret lives
    // in the repo. Without them, release builds are simply unsigned.
    val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // AGP otherwise writes the git revision into the APK, for Play's crash tooling. It depends on how the
            // source was checked out (a worktree has none), which would break the build's reproducibility.
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // Never published to Play (F-Droid and GitHub only) — its battery-optimization policy doesn't apply.
        disable += "BatteryLife"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            // The state machine's model check holds every reachable state as a packed code, and its parallel search
            // keeps a level's temporaries alive (57 M states fit in 3 GB, not in 2 GB); its inputs must never be
            // trimmed to fit.
            it.maxHeapSize = providers.gradleProperty("testMaxHeap").getOrElse("3g")
        }
    }
}

// Static analysis on top of Android Lint: code smells, Compose misuse and official-style formatting.
// Runs with `gradle check` and in CI; the project's overrides live in config/detekt/detekt.yml.
detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel.set(true)
    // `gradle :app:detektMain -PdetektAutoCorrect` rewrites fixable formatting findings in place.
    autoCorrect.set(providers.gradleProperty("detektAutoCorrect").isPresent)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.palette.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    // The real org.json for JVM tests of the settings' JSON; the app uses the platform's.
    testImplementation(libs.org.json)

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.compose.rules.detekt)
}

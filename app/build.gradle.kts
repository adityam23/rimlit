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
        // CI passes its run number so every release has a higher versionCode and installs as an update.
        versionCode = providers.environmentVariable("RELEASE_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "0.1.0"
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        // Sideloaded only, never published to Play — its battery-optimization policy doesn't apply.
        disable += "BatteryLife"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            // The state machine's model check holds every reachable state as a packed code, and its parallel search
            // keeps a level's temporaries alive (it needs about 768 MB); 1 GB leaves headroom for a growing state
            // space, and its inputs must never be trimmed to fit.
            it.maxHeapSize = providers.gradleProperty("testMaxHeap").getOrElse("1g")
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

    detektPlugins(libs.detekt.rules.ktlint.wrapper)
    detektPlugins(libs.compose.rules.detekt)
}

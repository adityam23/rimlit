// AGP 9.0+ ships built-in Kotlin with a runtime dependency on KGP 2.2.10.
// JDK 25 support requires KGP 2.3+, so bump it explicitly (documented path).
// Pinned to the catalog's `kotlin` version so it stays in lockstep with the Compose compiler plugin.
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt) apply false
}

import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.justnoise.uiprototype"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnoise.uiprototype"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-ui-prototype"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        explainIssues = true
    }

    sourceSets {
        getByName("main").assets.directories.add(
            rootProject.projectDir.resolve(
                "../../justnoiseradio/Assets.xcassets/zap_button.imageset",
            ).path,
        )
    }
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "justnoise.repositoryRoot",
        rootProject.projectDir.resolve("../..").canonicalPath,
    )
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

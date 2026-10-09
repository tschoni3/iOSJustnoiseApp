import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // The disposable shell shares the Gate 0 resource namespace so the reviewed
    // capability sources compile unchanged. Its install identity remains separate.
    namespace = "com.justnoise.gate0"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnoise.androiddev"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-integrated-dev"

        // Cleanup belongs to the instrumentation test, after its emulator guard.
        // Do not add clearPackageData: a runner would clear state before that guard.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
        }
        create("cleanupCheck") {
            // A separate disposable sandbox for owner-authorized cleanup checks.
            // Reuse main/release sources, not the debug probe or tooling source set.
            initWith(getByName("release"))
            applicationIdSuffix = ".cleanupcheck"
            versionNameSuffix = "-cleanup-check"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
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
        getByName("main") {
            // Reuse the reviewed disposable capability implementation. No source is
            // copied, forked, or promoted into a production Android application.
            kotlin.directories.add(
                rootProject.projectDir.resolve("../android-gate0/app/src/main/java").path,
            )
            kotlin.directories.add(
                rootProject.projectDir.resolve("../android-shared/src/main/kotlin").path,
            )
            res.directories.add(
                rootProject.projectDir.resolve("../android-gate0/app/src/main/res").path,
            )
            res.directories.add(
                layout.buildDirectory.dir("generated/integratedResources").get().asFile.path,
            )
        }
    }
}

val syncIntegratedResources = tasks.register<Sync>("syncIntegratedResources") {
    into(layout.buildDirectory.dir("generated/integratedResources"))
    from(
        rootProject.projectDir.resolve(
            "../../justnoiseradio/Assets.xcassets/zap_button.imageset/zap_button.png",
        ),
    ) {
        into("drawable-nodpi")
    }
    from(
        rootProject.projectDir.resolve(
            "../../justnoiseradio/Assets.xcassets/Icon.imageset/Instagram post - 4.png",
        ),
    ) {
        into("drawable-nodpi")
        rename { "justnoise_icon.png" }
    }
    from(rootProject.projectDir.resolve("../../justnoiseradio/Assets.xcassets/Justnoise_logo_nav.imageset/Subtract.png")) {
        into("drawable-nodpi")
        rename { "justnoise_logo_nav.png" }
    }
    from(rootProject.projectDir.resolve("../../JN_video1.mp4")) {
        into("raw")
        rename { "jn_video1.mp4" }
    }
    from(rootProject.projectDir.resolve("../../JN_video2.mp4")) {
        into("raw")
        rename { "jn_video2.mp4" }
    }
}

tasks.named("preBuild").configure {
    dependsOn(syncIntegratedResources)
}

tasks.withType<Test>().configureEach {
    inputs.files(
        layout.projectDirectory.file("build.gradle.kts"),
        layout.projectDirectory.file("src/main/AndroidManifest.xml"),
        fileTree("src/cleanupCheck"),
    ).withPropertyName("cleanupBuildBoundary").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.18.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

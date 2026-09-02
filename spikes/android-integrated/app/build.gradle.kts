import org.gradle.api.tasks.Sync

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
    }

    buildTypes {
        release {
            isDebuggable = false
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
}

tasks.named("preBuild").configure {
    dependsOn(syncIntegratedResources)
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
    implementation("androidx.core:core:1.16.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

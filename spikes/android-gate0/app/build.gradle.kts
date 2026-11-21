import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
}

val generatedZapResources = layout.buildDirectory.dir("generated/zapArtwork/res")
val copyZapArtwork by tasks.registering(Sync::class) {
    from(rootProject.projectDir.resolve("../../justnoiseradio/Assets.xcassets/zap_button.imageset/zap_button.png")) {
        into("drawable-nodpi")
    }
    into(generatedZapResources)
}

android {
    namespace = "com.justnoise.gate0"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.justnoise.gate0"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-gate0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        unitTests.isIncludeAndroidResources = false
    }

    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
        explainIssues = true
        htmlReport = true
        sarifReport = true
        textReport = true
    }

    sourceSets {
        getByName("main").res.directories.add(generatedZapResources.get().asFile.path)
        getByName("test").resources.srcDir(
            rootProject.projectDir.resolve("../../product/behavior"),
        )
    }
}

tasks.named("preBuild").configure { dependsOn(copyZapArtwork) }

tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.projectDir.resolve("app/src/main/AndroidManifest.xml"),
        rootProject.projectDir.resolve("app/src/debug/AndroidManifest.xml"),
        rootProject.projectDir.resolve("app/src/release/AndroidManifest.xml"),
        rootProject.projectDir.resolve("../android-integrated/app/src/main/AndroidManifest.xml"),
        rootProject.projectDir.resolve("../android-integrated/app/src/debug/AndroidManifest.xml"),
        rootProject.projectDir.resolve("../android-integrated/app/src/release/AndroidManifest.xml"),
    ).withPropertyName("policyManifests").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        rootProject.projectDir.resolve("../android-integrated/app/src/main/res/values/privacy_draft.xml"),
        rootProject.projectDir.resolve("../android-integrated/app/src/main/res/values/settings.xml"),
        rootProject.projectDir.resolve("../android-integrated/app/src/main/res/values/strings.xml"),
        rootProject.projectDir.resolve("evidence/PLAY_DECLARATION_DRAFT.md"),
    ).withPropertyName("privacyDraftMaterials").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty(
        "justnoise.repositoryRoot",
        rootProject.projectDir.resolve("../..").canonicalPath,
    )
}

dependencies {
    implementation("androidx.core:core:1.18.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}

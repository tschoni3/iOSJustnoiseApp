import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
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
        getByName("test").resources.srcDir(
            rootProject.projectDir.resolve("../../product/behavior"),
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
    implementation("androidx.core:core:1.16.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestUtil("androidx.test:orchestrator:1.5.1")
}

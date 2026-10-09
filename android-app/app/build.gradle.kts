import java.util.Properties
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val repository = rootProject.projectDir.parentFile
val integrated = repository.resolve("spikes/android-integrated/app")
val capability = repository.resolve("spikes/android-gate0/app")
val credentialsPath = providers.environmentVariable("JUSTNOISE_SIGNING_PROPERTIES").orNull
    ?: error("Set JUSTNOISE_SIGNING_PROPERTIES to the protected external signing file; unsigned builds are forbidden.")
val credentialsFile = file(credentialsPath).canonicalFile
require(!credentialsFile.toPath().startsWith(repository.canonicalFile.toPath())) {
    "Signing credentials must be outside the repository."
}
require(credentialsFile.isFile && Files.getPosixFilePermissions(credentialsFile.toPath()).all {
    it in setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
}) { "Signing credentials must be an owner-only file." }
val credentials = Properties().apply { credentialsFile.inputStream().use { load(it) } }
fun credential(key: String) = credentials.getProperty(key)?.takeIf { it.isNotBlank() }
    ?: error("Required signing field is missing: $key")
val uploadStore = file(credential("storeFile")).canonicalFile
require(uploadStore.isFile && !uploadStore.toPath().startsWith(repository.canonicalFile.toPath())) {
    "An existing external upload keystore is required."
}
require(Files.getPosixFilePermissions(uploadStore.toPath()).all {
    it in setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
}) { "Upload keystore must be owner-only." }

android {
    // Keep source/resource namespaces stable; installed identity is independent.
    namespace = "com.justnoise.gate0"
    compileSdk = 36
    defaultConfig {
        applicationId = "shop.justnoise.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("upload") {
            storeFile = uploadStore
            storePassword = credential("storePassword")
            keyAlias = credential("keyAlias")
            keyPassword = credential("keyPassword")
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint {
        abortOnError = true
        checkDependencies = true
        checkReleaseBuilds = true
    }
    sourceSets {
        getByName("main") {
            manifest.srcFile(integrated.resolve("src/main/AndroidManifest.xml"))
            kotlin.directories.addAll(listOf(
                integrated.resolve("src/main/java").path,
                capability.resolve("src/main/java").path,
                repository.resolve("spikes/android-shared/src/main/kotlin").path,
            ))
            res.directories.addAll(listOf(
                integrated.resolve("src/main/res").path,
                capability.resolve("src/main/res").path,
                layout.buildDirectory.dir("generated/artwork").get().asFile.path,
            ))
        }
        getByName("test") {
            kotlin.directories.addAll(listOf(
                integrated.resolve("src/test/java").path,
                capability.resolve("src/test/java").path,
            ))
            resources.directories.add(repository.resolve("product/behavior").path)
        }
    }
}

androidComponents.beforeVariants { variant ->
    // No debug/cleanup variant or owner-phone instrumentation in this root.
    variant.enable = variant.buildType == "release"
    variant.hostTests.values.filter { it.type == "UnitTest" }.forEach {
        it.enable = variant.buildType == "release"
    }
    variant.enableAndroidTest = false
}

val artwork = tasks.register<Sync>("syncReleaseArtwork") {
    into(layout.buildDirectory.dir("generated/artwork"))
    from(repository.resolve("justnoiseradio/Assets.xcassets/zap_button.imageset/zap_button.png")) {
        into("drawable-nodpi")
    }
    from(repository.resolve("justnoiseradio/Assets.xcassets/Icon.imageset/Instagram post - 4.png")) {
        into("drawable-nodpi"); rename { "justnoise_icon.png" }
    }
    from(repository.resolve("justnoiseradio/Assets.xcassets/Justnoise_logo_nav.imageset/Subtract.png")) {
        into("drawable-nodpi"); rename { "justnoise_logo_nav.png" }
    }
    from(repository.resolve("JN_video1.mp4")) { into("raw"); rename { "jn_video1.mp4" } }
    from(repository.resolve("JN_video2.mp4")) { into("raw"); rename { "jn_video2.mp4" } }
}
tasks.named("preBuild").configure { dependsOn(artwork) }
tasks.withType<Test>().configureEach {
    systemProperty("justnoise.repositoryRoot", repository.canonicalPath)
}
// Build-only authorization: accidental Gradle install tasks must not touch a phone.
tasks.configureEach {
    if (name.startsWith("install")) {
        doFirst { error("Installation is not authorized for this local release candidate.") }
    }
}

// Host-only verifier dependency. It never enters the application's runtime graph.
val verifierTools = configurations.create("verifierTools")
tasks.register("writeVerifierClasspath") {
    val output = layout.buildDirectory.file("verifier-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(verifierTools.asPath) }
}

dependencies {
    // Production account adapter only; keep capability/integrated development roots offline.
    // 3.6.0 matches this project's Kotlin 2.3.21 toolchain (upstream 3.8 uses Kotlin 2.4).
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.4.3")
    testImplementation("io.ktor:ktor-client-mock:3.4.3")
    add(verifierTools.name, "com.android.tools.build:bundletool:1.18.3")
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core:1.18.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

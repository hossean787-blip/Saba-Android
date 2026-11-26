import java.io.File
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Standard release stays lightweight; the optional full-bank variant has a higher update code.
val suraEmbeddedFull = providers.gradleProperty("suraEmbeddedFull").orNull == "true"

android {
    namespace = "ir.saba.invoice"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.saba.invoice"
        minSdk = 23
        targetSdk = 35
        // Keep upgrade order above BOTH previously shipped variants (25 standard, 26 full).
        versionCode = if (suraEmbeddedFull) 128 else 127
        versionName = if (suraEmbeddedFull) "0.128.0" else "0.127.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Release signing is opt-in and never uses the ephemeral Android debug key.
    // Values are supplied by the trusted build environment, never source control.
    val releaseStorePath = System.getenv("SURA_RELEASE_STORE_FILE")
    val releaseStorePassword = System.getenv("SURA_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = System.getenv("SURA_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("SURA_RELEASE_KEY_PASSWORD")
    val releaseSigningReady = listOf(
        releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
    ).all { !it.isNullOrBlank() }
    if (releaseSigningReady) {
        signingConfigs {
            create("suraRelease") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            // Harden only the distribution variant; debug diagnostics remain available.
            isDebuggable = false
            isMinifyEnabled = true
            // Catalog/assets and dynamic resource lookups must remain intact.
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // An unsigned release remains possible locally for development,
            // but the trusted release workflow refuses to publish it.
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("suraRelease")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    // Install-time catalog chunks are gzip already; do not compress them again in APK.
    androidResources { noCompress.add("gz") }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

// Robolectric 4.14.1 runtime SDK coordinates (DefaultSdkProvider, i7).
// Resolve SDK jars with Gradle's normal dependency cache BEFORE any test starts.
// Tests must never perform their own Maven downloads.
val robolectricSdkVersions = mapOf(
    21 to "5.0.2_r3-robolectric-r0-i7",
    22 to "5.1.1_r9-robolectric-r2-i7",
    23 to "6.0.1_r3-robolectric-r1-i7",
    24 to "7.0.0_r1-robolectric-r1-i7",
    25 to "7.1.0_r7-robolectric-r1-i7",
    26 to "8.0.0_r4-robolectric-r1-i7",
    27 to "8.1.0-robolectric-4611349-i7",
    28 to "9-robolectric-4913185-2-i7",
    29 to "10-robolectric-5803371-i7",
    30 to "11-robolectric-6757853-i7",
    31 to "12-robolectric-7732740-i7",
    32 to "12.1-robolectric-8229987-i7",
    33 to "13-robolectric-9030017-i7",
    34 to "14-robolectric-10818077-i7",
    35 to "15-robolectric-12650502-i7"
)
val declaredTestSdks = mutableSetOf(28, 35) // existing default test API and latest resource SDK
val sdkAnnotation = Regex("""@Config\s*\([^)]*\)""")
val sdkArray = Regex("""\bsdk\s*=\s*(?:\[([^]]+)\]|\{([^}]+)\}|(\d+))""")
fileTree("src/test") { include("**/*.kt", "**/*.java") }.files.forEach { source ->
    sdkAnnotation.findAll(source.readText()).forEach { annotation ->
        sdkArray.find(annotation.value)?.let { match ->
            Regex("""\d+""").findAll(match.value.substringAfter('=')).forEach {
                declaredTestSdks.add(it.value.toInt())
            }
        }
    }
}
val sdkJars = declaredTestSdks.sorted().map { api ->
    val version = requireNotNull(robolectricSdkVersions[api]) {
        "No pinned Robolectric SDK for API $api; update the SDK map."
    }
    // Separate configurations prevent Gradle from choosing only the highest version.
    configurations.detachedConfiguration(
        dependencies.create("org.robolectric:android-all-instrumented:$version@jar")
    ).apply { isTransitive = false }
}
val robolectricSdkDir = layout.buildDirectory.dir("robolectric-sdk-jars")
val prepareRobolectricSdks = tasks.register("prepareRobolectricSdks") {
    group = "verification"
    description = "Resolve and stage all pinned Robolectric Android SDK jars."
    // Resolve lazily only when a local jar is absent or structurally invalid.
    // A successful preparation from any approved mirror can be reused by tests.
    doLast {
        val directory = robolectricSdkDir.get().asFile
        directory.mkdirs()
        fun valid(jar: File): Boolean = jar.isFile && jar.length() > 0 &&
            runCatching { ZipFile(jar).use { it.size() > 0 } }.getOrDefault(false)
        declaredTestSdks.sorted().forEachIndexed { index, api ->
            val name = "android-all-instrumented-${robolectricSdkVersions.getValue(api)}.jar"
            val jar = directory.resolve(name)
            if (!valid(jar)) {
                val source = sdkJars[index].singleFile
                check(source.name == name && valid(source)) { "Invalid Robolectric SDK: $name" }
                val temporary = directory.resolve("$name.tmp")
                source.copyTo(temporary, overwrite = true)
                check(valid(temporary)) { "Invalid staged Robolectric SDK: $name" }
                temporary.copyTo(jar, overwrite = true)
                temporary.delete()
            }
            check(valid(jar)) { "Missing Robolectric SDK: $name" }
        }
        logger.lifecycle("ROBOLECTRIC_SDK_READY APIs=${declaredTestSdks.sorted()}")
    }
}

tasks.withType<Test>().configureEach {
    dependsOn(prepareRobolectricSdks)
    systemProperty("robolectric.dependency.dir", robolectricSdkDir.get().asFile.absolutePath)
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.usePreinstrumentedJars", "true")
    maxHeapSize = "1536m"
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}


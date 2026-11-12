plugins { id("com.android.application") }
android {
    namespace = "ir.sabagostar.suramanager"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.sabagostar.suramanager"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.0-rc2"
    }
    // Optional production signing: only through existing trusted CI secrets, never in source or APK.
    val storePath = System.getenv("SURA_RELEASE_STORE_FILE")
    val storePass = System.getenv("SURA_RELEASE_STORE_PASSWORD")
    val keyAlias = System.getenv("SURA_RELEASE_KEY_ALIAS")
    val keyPass = System.getenv("SURA_RELEASE_KEY_PASSWORD")
    val canSign = listOf(storePath,storePass,keyAlias,keyPass).all { !it.isNullOrBlank() }
    if (canSign) {
        signingConfigs {
            create("suraManagerRelease") {
                storeFile = file(requireNotNull(storePath))
                storePassword = storePass
                this.keyAlias = keyAlias
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (canSign) signingConfig = signingConfigs.getByName("suraManagerRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

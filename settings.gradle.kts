pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // Use a documented Maven Central mirror ONLY for the pinned test SDK jars.
    // All other dependencies continue using the original repositories.
    val sdkRepository = when (providers.gradleProperty("suraRobolectricRepository").orNull) {
        "central" -> "https://repo.maven.apache.org/maven2/"
        "eu" -> "https://maven-central-eu.storage-download.googleapis.com/maven2/"
        null, "mirror" -> "https://maven-central.storage-download.googleapis.com/maven2/"
        else -> error("Unknown Robolectric SDK repository")
    }
    val cipherRepository = when (providers.gradleProperty("suraSqlCipherRepository").orNull) {
        "central" -> "https://repo.maven.apache.org/maven2/"
        "mirror" -> "https://maven-central.storage-download.googleapis.com/maven2/"
        null, "eu" -> "https://maven-central-eu.storage-download.googleapis.com/maven2/"
        else -> error("Unknown SQLCipher repository")
    }
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "SuraRobolectricSdk"
                    url = uri(sdkRepository)
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("org.robolectric", "android-all-instrumented") }
        }
        // SQLCipher was verified in the isolated lab; scope the HTTPS Central mirror
        // to exactly this pinned dependency to avoid Central rate limiting in CI.
        exclusiveContent {
            forRepository {
                maven {
                    name = "SuraSqlCipherCentralMirror"
                    url = uri(cipherRepository)
                    metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
                }
            }
            filter { includeModule("net.zetetic", "sqlcipher-android") }
        }
        google { content { excludeGroup("org.robolectric") } }
        mavenCentral()
    }
}
rootProject.name = "Saba"
include(":app")

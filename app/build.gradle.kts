plugins { id("com.android.application") }

val releaseKeystore = System.getenv("JB_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("JB_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("JB_KEY_ALIAS")
val releaseKeyPassword = System.getenv("JB_KEY_PASSWORD")
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val signedReleaseReady = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "pk.jb.idcardprinter"
    compileSdk = 35
    defaultConfig {
        applicationId = "pk.jb.idcardprinter"
        minSdk = 24
        targetSdk = 28
        // Monotonically increases for GitHub Actions builds. Keep the same signing key for updates.
        versionCode = if (ciBuildNumber != null) 10000 + ciBuildNumber else 2
        versionName = if (ciBuildNumber != null) "1.2.$ciBuildNumber" else "1.2"
    }
    signingConfigs {
        if (signedReleaseReady) {
            create("jbRelease") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (signedReleaseReady) signingConfig = signingConfigs.getByName("jbRelease")
        }
    }
}

dependencies {
    implementation("org.opencv:opencv:4.10.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
}

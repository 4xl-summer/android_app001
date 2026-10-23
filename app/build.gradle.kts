plugins { id("com.android.application") }

android {
    namespace = "com.example.blankapp"
    compileSdk = 35
    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.example.blankapp"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + versionCode
    }

    signingConfigs {
        create("releaseKey") {
            val keyFile = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!keyFile.isNullOrBlank()) {
                storeFile = file(keyFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseKey")
        }
    }
}

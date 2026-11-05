import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Register this Android app in the shared HireMyDriver Firebase project.
val customerFirebaseConfig = file("google-services.json")
if (customerFirebaseConfig.isFile) {
    apply(plugin = "com.google.gms.google-services")
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst { check(customerFirebaseConfig.isFile) { "Add hiremydriver-e88f7's Android app/google-services.json before building a release." } }
}

// local.properties is already excluded from version control. Never log the key.
val localMapsProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val mapsApiKey = providers.environmentVariable("MAPS_API_KEY")
    .orElse(localMapsProperties.getProperty("MAPS_API_KEY", "")).get().trim()
val routesApiKey = providers.environmentVariable("ROUTES_API_KEY")
    .orElse(localMapsProperties.getProperty("ROUTES_API_KEY", "")).get().trim()

android {
    buildFeatures { resValues = true }
    namespace = "com.example.hiremydriver"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.hiremydriver"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        manifestPlaceholders["ROUTES_API_KEY"] = routesApiKey
        resValue("string", "emergency_api_url", localMapsProperties.getProperty("EMERGENCY_API_URL", "").trim())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res-icons",
                "src/main/res-img",
                "src/main/res-bg"
            )
        }
    }

}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

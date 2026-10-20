plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.libraryonline"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.libraryonline"
        minSdk = 23
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Demo SMTP credentials are packaged in the APK; do not use in production.
        val smtpEmail = providers.gradleProperty("SMTP_EMAIL").orElse("").get()
                .replace("\\", "\\\\").replace("\"", "\\\"")
        val smtpAppPassword = providers.gradleProperty("SMTP_APP_PASSWORD").orElse("").get()
                .replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "SMTP_EMAIL", "\"$smtpEmail\"")
        buildConfigField("String", "SMTP_APP_PASSWORD", "\"$smtpAppPassword\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.fragment)
    implementation(libs.material)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.functions)
    implementation(libs.android.mail)
    implementation(libs.android.activation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
plugins {
    id("com.android.library")
}

android {
    namespace = "dev.pdfcrate.enginelab"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = true
    }
}

dependencies {
    // Experimental candidate, confined to instrumentation; not a product dependency.
    androidTestImplementation("com.tom-roush:pdfbox-android:2.0.27.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

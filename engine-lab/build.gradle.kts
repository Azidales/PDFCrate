plugins {
    alias(libs.plugins.android.library)
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
    androidTestImplementation(libs.pdfbox.android)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    alias(gravitySdk.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "ai.gravityfield.gravity_sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

publishing {
    publications {
        create<MavenPublication>("release")
        {
            groupId = "ai.gravityfield"
            artifactId = "gravity-sdk"
            version = "1.0.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

dependencies {

    implementation(gravitySdk.androidx.activity.compose)
    implementation(gravitySdk.androidx.core.ktx)
    implementation(gravitySdk.androidx.appcompat)
    implementation(gravitySdk.material)
    implementation(platform(gravitySdk.androidx.compose.bom))
    implementation(gravitySdk.androidx.ui)
    implementation(gravitySdk.androidx.ui.graphics)
    implementation(gravitySdk.androidx.ui.tooling.preview)
    implementation(gravitySdk.androidx.material3)
    implementation(gravitySdk.ktor.client.core)
    implementation(gravitySdk.ktor.client.cio)
    implementation(gravitySdk.ktor.client.logging)
    implementation(gravitySdk.ktor.client.content.negotiation)
    implementation(gravitySdk.ktor.client.serialization.kotlinx)
    implementation(gravitySdk.kotlinx.serialization.json)
    implementation(gravitySdk.multiplatform.settings)
    implementation(gravitySdk.multiplatform.settings.no.arg)
    implementation(gravitySdk.coil.compose)
    implementation(gravitySdk.coil.network)
    testImplementation(gravitySdk.junit)
    androidTestImplementation(gravitySdk.androidx.junit)
    androidTestImplementation(gravitySdk.androidx.espresso.core)
}
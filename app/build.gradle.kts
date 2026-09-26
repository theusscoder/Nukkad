plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "in.nukkad"
    compileSdk = 35
    flavorDimensions += "audience"
    productFlavors {
        create("customer") {
            dimension = "audience"
            applicationIdSuffix = ".customer"
            versionNameSuffix = "-customer"
            buildConfigField("String", "NUKKAD_ROLE", "\"customer\"")
        }
        create("merchant") {
            dimension = "audience"
            applicationIdSuffix = ".merchant"
            versionNameSuffix = "-merchant"
            buildConfigField("String", "NUKKAD_ROLE", "\"merchant\"")
        }
        create("dev") {
            dimension = "audience"
            buildConfigField("String", "NUKKAD_ROLE", "\"dev\"")
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("nukkad-debug.keystore")
            storePassword = "android"
            keyAlias = "nukkad"
            keyPassword = "android"
        }
    }
    defaultConfig {
        applicationId = "in.nukkad"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.4-m3-routing"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties") }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

// Opt-in network smoke test; ordinary unit tests remain offline.
tasks.withType<Test>().configureEach {
    systemProperty("nukkad.mqttSmoke", providers.gradleProperty("mqttSmoke").orElse("false").get())
}








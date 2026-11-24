import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.catenarymaps.catenary.feature.journeyplanner"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 27 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
}
kotlin { jvmToolchain(25) }
dependencies {
    api(project(":core:journey"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
}

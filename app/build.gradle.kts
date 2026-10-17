import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}
// Signing material is local-only. No file path or secret is stored in this build script.
val releaseSigningProperties = Properties()
val releaseSigningPath = providers.environmentVariable("ZHANGYI_SIGNING_PROPERTIES").orNull
if (!releaseSigningPath.isNullOrBlank()) {
    val signingPropertiesFile = file(releaseSigningPath).canonicalFile
    require(!signingPropertiesFile.toPath().startsWith(rootProject.rootDir.canonicalFile.toPath())) {
        "Release signing properties must be stored outside the repository."
    }
    require(signingPropertiesFile.isFile) { "Release signing properties file is unavailable." }
    signingPropertiesFile.inputStream().use(releaseSigningProperties::load)
}
fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull ?: releaseSigningProperties.getProperty(propertyName)
val releaseStorePath = releaseSigningValue("ZHANGYI_KEYSTORE_PATH", "storeFile")
val releaseStorePassword = releaseSigningValue("ZHANGYI_STORE_PASSWORD", "storePassword")
val releaseAlias = releaseSigningValue("ZHANGYI_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSigningValue("ZHANGYI_KEY_PASSWORD", "keyPassword")
val releaseSigningValues = listOf(releaseStorePath, releaseStorePassword, releaseAlias, releaseKeyPassword)
val hasReleaseSigning = !releaseSigningPath.isNullOrBlank() || releaseSigningValues.any { it != null }
if (hasReleaseSigning) {
    require(!gradle.startParameter.isConfigurationCacheRequested) {
        "Signed release builds must disable the configuration cache to protect credentials."
    }
    require(releaseSigningValues.all { !it.isNullOrBlank() }) {
        "Release signing requires storeFile, storePassword, keyAlias and keyPassword."
    }
    val keyStoreFile = file(requireNotNull(releaseStorePath)).canonicalFile
    require(!keyStoreFile.toPath().startsWith(rootProject.rootDir.canonicalFile.toPath())) {
        "The release keystore must be stored outside the repository."
    }
    require(keyStoreFile.isFile) { "The release keystore file is unavailable." }
    // AGP can write signing configuration intermediates: keep the entire signed build outside Git.
    val privateBuildDirectory = providers.environmentVariable("ZHANGYI_SIGNED_BUILD_DIR").orNull
        ?.let { file(it).canonicalFile } ?: keyStoreFile.parentFile.resolve("build").canonicalFile
    require(!privateBuildDirectory.toPath().startsWith(rootProject.rootDir.canonicalFile.toPath())) {
        "Signed build intermediates must be stored outside the repository."
    }
    layout.buildDirectory.set(privateBuildDirectory)
}

android {
    namespace = "cn.zhangyi"
    compileSdk = 35
    defaultConfig {
        applicationId = "cn.zhangyi"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
                storeType = releaseSigningProperties.getProperty("storeType", "JKS")
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.7.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

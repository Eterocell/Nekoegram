plugins {
    id("com.android.library")
    kotlin("android") version "1.8.10"
}

dependencies {
    implementation(project(":TMessagesProj"))

    implementation("androidx.core:core:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
}

android {
    namespace = "com.eterocell.nekoegram"
}

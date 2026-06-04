plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":model"))

    testImplementation(libs.kotlin.test)
}
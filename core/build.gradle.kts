plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":exporter"))
    implementation(project(":model"))
}
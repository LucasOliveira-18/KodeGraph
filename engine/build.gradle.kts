plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":model"))

    implementation(libs.kotlin.compiler.embeddable)

    testImplementation(libs.kotlin.test)
}
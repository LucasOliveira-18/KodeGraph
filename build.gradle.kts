plugins {
    alias(libs.plugins.kotlin.jvm)
}

allprojects {
    group = "io.kodegraph"
    version = "alpha-0.1.0"

    repositories {
        mavenCentral()
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}
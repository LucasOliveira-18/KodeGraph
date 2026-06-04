import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    application
}

application {
    mainClass.set("kodegraph.cli.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":engine"))
    implementation(project(":exporter"))
    implementation(project(":model"))
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("kodeGraph-cli")
    archiveClassifier.set("")
    mergeServiceFiles()
}

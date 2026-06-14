import com.github.gradle.node.npm.task.NpmTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.node)
}

dependencies {
    implementation(project(":model"))

    testImplementation(libs.kotlin.test)
}

node {
    download.set(true)
    version.set("20.11.0")
}

val compileTypeScript = tasks.register<NpmTask>("compileTypeScript") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "build"))

    inputs.dir("src/main/resources/kodegraph/exporter/html")
    inputs.file("package.json")
    inputs.file("tsconfig.json")

    outputs.dir("build/typescript-out")
}

sourceSets {
    main {
        resources {
            srcDir("build/typescript-out")
        }
    }
}

tasks.processResources {
    dependsOn(compileTypeScript)
}
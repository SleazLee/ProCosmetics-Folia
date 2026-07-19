plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API
    paperweight.paperDevBundle(rootProject.extra["paperApiVersion"].toString())

    // Project dependencies
    implementation(project(":api"))
    compileOnly("org.jetbrains:annotations:26.1.0")
}

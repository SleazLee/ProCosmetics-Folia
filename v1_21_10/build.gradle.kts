dependencies {
    compileOnly("io.papermc.paper:paper-api:${rootProject.extra["paperApiVersion_1_21_10"]}")

    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")

    implementation(project(":api"))
    implementation(project(":core"))
}
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("net.kyori:adventure-api:4.26.1")
    compileOnly("net.kyori:adventure-platform-bukkit:4.4.1")

    implementation(project(":api"))
    implementation(project(":core"))
}

plugins {
    java
    id("com.gradleup.shadow") version "8.3.5"
}

group = "dev.rui"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("org.mongodb:mongodb-driver-sync:5.2.1")
    implementation("redis.clients:jedis:5.1.5")

}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("Prueba")
    archiveClassifier.set("")
    archiveVersion.set("")
    relocate("com.mongodb", "dev.rui.prueba.libs.mongodb")
    relocate("org.bson", "dev.rui.prueba.libs.bson")
    relocate("redis.clients.jedis", "dev.rui.prueba.libs.jedis")
    relocate("org.apache.commons.pool2", "dev.rui.prueba.libs.pool2")
    relocate("org.json", "dev.rui.prueba.libs.json")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

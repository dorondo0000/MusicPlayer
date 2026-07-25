plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    java
}

group = "kr.dorondo"
version = "1.1.2"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-buffer:4.1.97.Final")
    implementation("io.javalin:javalin:5.6.3")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(kotlin("stdlib"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        relocate("io.javalin", "kr.dorondo.cinematomusic.libs.javalin")
        relocate("org.eclipse.jetty", "kr.dorondo.cinematomusic.libs.jetty")
        relocate("kotlin", "kr.dorondo.cinematomusic.libs.kotlin")
        relocate("com.google.gson", "kr.dorondo.cinematomusic.libs.gson")
        
        doLast {
            val pluginsDir = file("C:/단타/1.21.8연구/plugins")
            if (pluginsDir.exists()) {
                copy {
                    from(archiveFile)
                    into(pluginsDir)
                }
                println("Copied to plugins folder: ${pluginsDir.absolutePath}")
            }
        }
    }
    
    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}

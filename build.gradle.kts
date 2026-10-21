plugins {
    java
}

group = "com.voidsmp"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.200-stable")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
    }
    jar {
        archiveFileName.set("VoidWeapon-${project.version}.jar")
    }
}
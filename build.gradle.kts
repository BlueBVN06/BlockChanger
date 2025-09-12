plugins {
  `java-library`
  id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
  id("maven-publish") // Enable publishing to Maven
}

group = "dev.lrxh"
version = "2.0-SNAPSHOT"
description = "BlockChanger library built with Paperweight"

java {
  // Automatically provision JDK 21 if missing
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
  // Development bundle for Paper 1.21.8
  paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
}

tasks {
  compileJava {
    options.release.set(21) // Compile for Java 21 bytecode
  }
  javadoc {
    options.encoding = Charsets.UTF_8.name()
  }
}

/**
 * Configure publishing to Maven.
 * - Publishes the deobfuscated dev jar (main artifact).
 * - Publishes the reobfuscated runtime jar as a secondary artifact with classifier "reobf".
 */
publishing {
  publications {
    create<MavenPublication>("mavenJava") {
      groupId = "dev.lrxh"
      artifactId = "BlockChanger"
      version = "2.0-SNAPSHOT"

      from(components["java"])

      // Include the runtime jar as an additional artifact
      artifact(tasks.named("reobfJar")) {
        classifier = "reobf"
      }
    }
  }
  repositories {
    // Publish to local Maven (~/.m2/repository)
    mavenLocal()
  }
}

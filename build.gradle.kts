import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.tolgee"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        // Build against IntelliJ IDEA Ultimate so JavaScript/TypeScript APIs are available.
        // The plugin still runs on Community (and other IntelliJ IDEs) at runtime because the
        // language plugins are declared as optional <depends> in plugin.xml.
        //
        // 2025.1 is the minimum that handles Java 25 in the bundled Gradle plugin's
        // JVM compatibility matrix; older builds crash on `JavaVersion.parse("25")`.
        intellijIdeaUltimate("2025.1")

        // Bundled language plugins we depend on for language-specific completion.
        bundledPlugins(
            "JavaScript",                   // TS/JS/JSX/TSX  (Ultimate only)
            "com.intellij.java",            // Java
            "org.jetbrains.kotlin",         // Kotlin
        )

        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

// Extra run task: `./gradlew runIdeAndroidStudio` launches the plugin sandbox
// against a local Android Studio install. Default `runIde` still uses the
// IntelliJ IDEA Ultimate defined in the `dependencies { intellijPlatform { ... } }`
// block so JS/TS completion keeps compiling.
intellijPlatformTesting {
    runIde.register("runIdeAndroidStudio") {
        val studioContents = "/Users/bdshadow/Applications/Android Studio.app/Contents"
        localPath.set(file(studioContents))
        task {
            // Android Studio's studio.vmoptions sets
            //   -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
            // and the launcher normally prepends nio-fs.jar to the boot classpath so the
            // JVM can resolve that SPI at boot. Gradle's runIde doesn't replicate that for
            // a localPath IDE, so JFR init crashes with ClassNotFoundException. Boot with
            // Studio's own JBR and add nio-fs.jar to the boot classpath.
            executable = "$studioContents/jbr/Contents/Home/bin/java"
            jvmArgs("-Xbootclasspath/a:$studioContents/lib/nio-fs.jar")
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.tolgee.intellij"
        name = "Tolgee"
        version = project.version.toString()
        // description and <change-notes> live in src/main/resources/META-INF/plugin.xml
        // so the CDATA HTML stays authored in one place.
        vendor {
            name = "Dmitrii Bocharov"
            email = "bdshadow@gmail.com"
            url = "https://github.com/bdshadow/tolgee-intellij-plugin"
        }
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    publishing {
        // token via env: ORG_GRADLE_PROJECT_intellijPlatformPublishingToken
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }

    runIde {
        autoReload = false
    }

    // We have no .form files and no @NotNull form bindings, so bytecode
    // instrumentation is unnecessary. Disabling it sidesteps a JBR-vs-OpenJDK
    // path issue (`<JAVA_HOME>/Packages does not exist`) when running on a
    // non-JBR JDK.
    instrumentCode {
        enabled = false
    }
    instrumentTestCode {
        enabled = false
    }

    // buildSearchableOptions launches an embedded IDE to index settings for the
    // Search Everywhere → Settings popup. It conflicts with any already-running
    // IDE on the machine ("Only one instance of IDEA can be run at a time.") and
    // it's purely an optimization for the settings dialog. Skipping it.
    buildSearchableOptions {
        enabled = false
    }
}

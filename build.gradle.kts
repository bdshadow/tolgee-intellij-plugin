import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.dbocharov.tolgee"
version = "0.0.1"

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

// Extra run task: `./gradlew -PandroidStudioPath=… runIdeAndroidStudio` launches
// the plugin sandbox against a local Android Studio install. Only registered when
// the property is provided so a fresh clone doesn't fail on someone else's absent
// path. Set `androidStudioPath` in `~/.gradle/gradle.properties` or on the command
// line — e.g. `-PandroidStudioPath=/Users/you/Applications/Android Studio.app/Contents`.
val androidStudioPath: String? = findProperty("androidStudioPath") as String?
if (androidStudioPath != null) {
    intellijPlatformTesting {
        runIde.register("runIdeAndroidStudio") {
            localPath.set(file(androidStudioPath))
            task {
                // Android Studio's studio.vmoptions sets
                //   -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
                // and the launcher normally prepends nio-fs.jar to the boot classpath so the
                // JVM can resolve that SPI at boot. Gradle's runIde doesn't replicate that for
                // a localPath IDE, so JFR init crashes with ClassNotFoundException. Boot with
                // Studio's own JBR and add nio-fs.jar to the boot classpath.
                executable = "$androidStudioPath/jbr/Contents/Home/bin/java"
                jvmArgs("-Xbootclasspath/a:$androidStudioPath/lib/nio-fs.jar")
            }
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.dbocharov.tolgee"
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

    // Apache-2.0 §4(a) requires the plugin ZIP itself carry the license text and
    // per-dependency attribution — the GitHub-side LICENSE doesn't reach users
    // downloading the compiled bundle from Marketplace. Land both files at the top
    // of the plugin directory inside the ZIP.
    prepareSandbox {
        from(rootDir.resolve("LICENSE")) { into(intellijPlatform.projectName) }
        from(rootDir.resolve("THIRD-PARTY-NOTICES.md")) { into(intellijPlatform.projectName) }
    }
}

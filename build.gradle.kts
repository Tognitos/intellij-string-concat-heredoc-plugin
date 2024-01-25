import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * Difference between .kts and .kt files?
 * @see https://stackoverflow.com/a/61834182
 *
 * .kt — normal source files, .kts — script files
 * You don't need the main function in a .kts file, It will be executed line by line just like a bash/python script.
 * The .kts files don't need separate compilation. You run them using the following command:
 * `kotlinc -script <filename>.kts`
 */

/**
 * Helps find the properties of the project, which are defined in the gradle.properties file
 */
fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support -> make sure it's the same version as the kotlin plugin in IntelliJ
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.15.0"
    // Gradle IntelliJ Changelog Plugin
    id("org.jetbrains.changelog") version "2.2.0"
    // Gradle IntelliJ Qodana Plugin @see https://www.jetbrains.com/qodana/
    // "The code quality platform for your favorite CI tool" (any CI/CD System, GitHub Actions, Gitlab, Jenkins, CircleCI, ...)
    id("org.jetbrains.qodana") version "0.1.13"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}


// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {

    // by default result path is $projectPath/build/results
    // resultsPath.set("some/output/path")

    // see defaults: https://www.jetbrains.com/help/qodana/qodana-gradle-plugin.html#qodana+%7B+%7D+extension+configuration

    cachePath.set(projectDir.resolve(".qodana").canonicalPath)
    reportPath.set(projectDir.resolve("build/reports/inspections").canonicalPath)
    saveReport.set(true)
    // will change based on run config, see "Run Qodana" configuration in Gradle View
    showReport.set(System.getenv("QODANA_SHOW_REPORT")?.toBoolean() ?: false)
}

tasks {
    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> { // task compileJava under "other" in Gradle View?
            sourceCompatibility = it
            targetCompatibility = it

            // add arg that will list usages of deprecated features when compiling
            options.compilerArgs.add("-Xlint:deprecation")
        }
        withType<KotlinCompile> {// task compileKotlin under "other" in Gradle View?
            kotlinOptions.jvmTarget = it
        }
    }

    // I don't think the following is necessary, as it should be configured in ./gradle/wrapper/gradle-wrapper.properties
    // It just risks to run into inconsistencies between the wrapper properties and this run config definition
//    wrapper {
//        gradleVersion = properties("gradleVersion")
//    }

    patchPluginXml {
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        version.set(properties("pluginVersion"))

        println(version.getOrElse("bla"))

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription.set(
            projectDir.resolve("README.md").readText().lines().run {
                val start = "<!-- Plugin description -->"
                val end = "<!-- Plugin description end -->"

                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end))
            }.joinToString("\n").run { markdownToHTML(this) }
        )

        // Get the latest available change notes from the changelog file
        changeNotes.set(provider {
            changelog.renderItem(changelog.run {
                getOrNull(properties("pluginVersion")) ?: getLatest()
            }, Changelog.OutputType.HTML)
            .also { println(it) } // tap in the result above, print it and return it, see https://kotlinlang.org/docs/scope-functions.html#function-selection
        })
    }

    runIde {
        // specify to use local PhpStorm installation if available
        file("/Applications/PhpStorm.app/Contents").let {
            // TODO: find a way to understand whether you're in a ci job, then you would not do any of this
            if (it.isFile) {
                ideDir.set(it)
            } else {
                println("Could not find file at path ${it.path} : Are you inside a CI job?")
            }
        }

        // enable auto-reload when `runIde` is running, and `buildPlugin` is executed
        autoReloadPlugins.set(true)

    }

    // explicitly disable to allow for autoreload
    // @see https://plugins.jetbrains.com/docs/intellij/ide-development-instance.html#enabling-auto-reload
    buildSearchableOptions {
        enabled = false
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token.set(System.getenv("PUBLISH_TOKEN"))

        // pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels.set(listOf(properties("pluginVersion").split('-').getOrElse(1) { "default" }.split('.').first()))
    }

    jar {
        // jar() gets executed at some point when the gradle "build" task is executed
        // it complains if this duplicatesStrategy is not defined. WARN behaves as INCLUDE, which basically overrides
        // the original file with the duplicate
        duplicatesStrategy = DuplicatesStrategy.WARN
    }
}

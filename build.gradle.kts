plugins {
    id("java")
    id("com.diffplug.spotless") version "7.0.2"
}

group = "dev.dromzeh"

// resolve git metadata at configuration time so the update checker can compare against the
// github repo by commit (source builds) or tag (releases), and so the project version is
// derived from git tags instead of being hand-bumped. returns "" when git is unavailable
// (e.g. building from a source tarball) so callers can fall back.
fun git(vararg args: String): String =
    try {
        providers.exec {
            commandLine(listOf("git") + args)
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
    } catch (e: Exception) {
        ""
    }

val gitCommit = git("rev-parse", "HEAD").ifEmpty { "unknown" }
val gitCommitShort = git("rev-parse", "--short", "HEAD").ifEmpty { "unknown" }
val gitBranch = git("rev-parse", "--abbrev-ref", "HEAD").ifEmpty { "unknown" }

// version comes from git, no hand-bumping: exactly on a tag -> the tag (v1.2.0 -> 1.2.0),
// otherwise nearest tag + commits-ahead (1.2.0-5-gabc1234), or 0.0.0-dev before the first tag.
// requires tags to be present locally (CI uses fetch-depth: 0).
version =
    run {
        val exactTag = git("describe", "--tags", "--exact-match")
        if (exactTag.isNotEmpty()) return@run exactTag.removePrefix("v")
        val described = git("describe", "--tags", "--always")
        if (described.contains("-")) described.removePrefix("v") else "0.0.0-dev"
    }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("net.luckperms:api:5.4")
    // gson is provided by the server at runtime; only needed to compile the update checker
    compileOnly("com.google.code.gson:gson:2.10.1")
}

val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    val versionString = version.toString()
    // declare git values as inputs so the task re-runs (and re-embeds) when HEAD moves
    inputs.property("version", versionString)
    inputs.property("commit", gitCommit)
    inputs.property("branch", gitBranch)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("immortail-build.properties").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("version=$versionString")
                appendLine("commit=$gitCommit")
                appendLine("commit.short=$gitCommitShort")
                appendLine("branch=$gitBranch")
            },
        )
    }
}

tasks.jar {
    archiveFileName.set("immortail-v${version}.jar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    from(generateBuildInfo)
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

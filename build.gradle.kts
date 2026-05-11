import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.BaseExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.nav.safeargs.kotlin) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val out = providers.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
    }
    return out.standardOutput.asText.get().trim()
}

val ciBuild = providers.environmentVariable("CI").isPresent
val officialBuild by extra(providers.environmentVariable("OFFICIAL_BUILD").orElse("false").get() == "true")

@Suppress("unused")
val crowdinProjectId: String by extra(providers.environmentVariable("CROWDIN_PROJECT_ID").orElse("").get())

@Suppress("unused")
val crowdinApiKey: String by extra(providers.environmentVariable("CROWDIN_API_KEY").orElse("").get())

fun getUncommittedSuffix(): String {
    if (officialBuild) return ""

    if (ciBuild) {
        val headRefVal = providers.environmentVariable("GITHUB_HEAD_REF").orElse("HEAD").get()
        return "-$headRefVal"
    }

    var returnedVal = ""

    try {
        val branch = "git rev-parse --abbrev-ref HEAD".execute().split("/").last()
        if (branch != "master") {
            returnedVal += "-$branch"
        }
    } catch (_: Throwable) {}

    val result = "git status -s".execute()
    if (result.isEmpty()) {
        return returnedVal
    }

    return "$returnedVal-dirty+${result.count { it == '\n' } + 1}"
}

val gitHasUncommittedSuffix = getUncommittedSuffix()
val gitCommitCount = "git rev-list refs/remotes/origin/master --count".execute().toInt()

// 432 is the count of commits before license changed
val gitCommitCountAfterOss = gitCommitCount - 432

val minSdkVer by extra(29)
val targetSdkVer by extra(36)

val appVerCode by extra(gitCommitCount + 0x6f7373) // commit count + 0xOSS
val appVerName by extra("oss-${gitCommitCountAfterOss}${gitHasUncommittedSuffix}")

/*
 * configVerCode, serviceVerCode and minBackupVerCode is used by other build.gradle.kts files
 *
 * DO NOT REMOVE THESE LINES
*/

@Suppress("unused")
val configVerCode by extra(93)

@Suppress("unused")
val serviceVerCode by extra(102)

@Suppress("unused")
val minBackupVerCode by extra(65)

@Suppress("unused")
val appPackageName by extra("org.frknkrc44.hma_oss")

@Suppress("unused")
val localBuild by extra(providers.environmentVariable("LOCAL_BUILD").orElse("false").get() == "true")

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType<BaseExtension>()?.run {
        compileSdkVersion(targetSdkVer)

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = appVerCode
            versionName = appVerName

            consumerProguardFiles("proguard-rules.pro")
        }

        val storeFilePath = providers.environmentVariable("SIGNING_STORE_FILE").orNull
        val config = storeFilePath?.let {
            signingConfigs.create("config") {
                storeFile = file(it)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull
            }
        }

        buildTypes {
            all {
                signingConfig = config ?: signingConfigs["debug"]
            }
            named("release") {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        buildTypes {
            named("release") {
                isShrinkResources = true
            }
        }

        dependenciesInfo {
            // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
            includeInApk = false
            // Disables dependency metadata when building Android App Bundles (for Google Play)
            includeInBundle = false
        }
    }
}

subprojects {
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
}

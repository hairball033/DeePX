import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val protectedDistribution = providers.gradleProperty("protectedBuild")
    .orElse(providers.environmentVariable("PROTECTED_BUILD"))
    .orElse("false")
    .map { it.equals("true", ignoreCase = true) }
    .get()
// R8 whole-program shrink/optimize switch. Mirrors POLARIS' `ext.ENABLE_R8`: flip it in one place
// to turn R8 on or off for both build types. Never obfuscates (see proguard-rules-no-obfuscate.pro)
// so the module stays readable and Xposed-addressable.
val ENABLE_R8 = false
val localApiIncluded = providers.gradleProperty("localApiIncluded")
    .orElse("true")
    .map { it.equals("true", ignoreCase = true) }
    .get()
val shiV5V241 = providers.gradleProperty("shiV5V241")
    .orElse(providers.environmentVariable("SHI_V5_V241"))
    .orElse("false")
    .map { it.equals("true", ignoreCase = true) }
    .get()
check(!shiV5V241 || protectedDistribution) {
    "shiV5V241 is Closed-only and must never enter an Open build"
}
val moduleSourceRoot = providers.gradleProperty("moduleSourceRoot")
    .orElse("../../module/src")
    .get()
val modulePayloadSourceRoot = providers.gradleProperty("modulePayloadSourceRoot")
    .orElse("../../module-universal/protected-payload-src")
    .get()
val moduleResourceRoot = providers.gradleProperty("moduleResourceRoot")
    .orElse("../../module-universal/res")
    .get()
val moduleAssetsRoot = providers.gradleProperty("moduleAssetsRoot")
    .orElse("../../module-universal/assets")
    .get()
val moduleCompatRoot = providers.gradleProperty("moduleCompatRoot")
    .orElse("../../module-legacy/compat")
    .get()
val googleV241Basic = providers.gradleProperty("googleV241Basic")
    .orElse("false")
    .map { it.equals("true", ignoreCase = true) }
    .get()
val shiV5HostVersionCode = providers.gradleProperty("shiV5HostVersionCode")
    .orElse(providers.environmentVariable("SHI_V5_HOST_VERSION_CODE"))
    .orElse("257")
    .get()
val exactV241HostBuild = shiV5HostVersionCode == "257"
val shiV5HostCertSha256Alt = providers.gradleProperty("shiV5HostCertSha256Alt")
    .orElse(providers.environmentVariable("SHI_V5_HOST_CERT_SHA256_ALT"))
    .orElse("")
    .get()
val moduleManifestFile = providers.gradleProperty("moduleManifestFile")
    .orElse("src/main/AndroidManifest.xml")
    .get()

val materialIconsExtendedSource = configurations.create("materialIconsExtendedSource") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
val trimmedMaterialIconsJar = layout.buildDirectory.file(
    "generated/trimmedMaterialIcons/material-icons-extended-used.jar"
)
val trimMaterialIcons = tasks.register("trimMaterialIcons") {
    inputs.files(materialIconsExtendedSource)
    outputs.file(trimmedMaterialIconsJar)
    doLast {
        val wanted = setOf(
            "androidx/compose/material/icons/outlined/ArrowDownwardKt.class",
            "androidx/compose/material/icons/outlined/ArrowUpwardKt.class",
            "androidx/compose/material/icons/outlined/CodeKt.class",
            "androidx/compose/material/icons/outlined/DevicesKt.class",
            "androidx/compose/material/icons/outlined/ForumKt.class",
            "androidx/compose/material/icons/outlined/GavelKt.class",
            "androidx/compose/material/icons/rounded/CheckCircleOutlineKt.class",
            "androidx/compose/material/icons/rounded/ErrorOutlineKt.class",
            "androidx/compose/material/icons/rounded/BlurOnKt.class",
            "androidx/compose/material/icons/rounded/CallToActionKt.class",
            "androidx/compose/material/icons/rounded/ColorizeKt.class",
            "androidx/compose/material/icons/rounded/DesignServicesKt.class",
            "androidx/compose/material/icons/rounded/StyleKt.class",
            "androidx/compose/material/icons/rounded/WallpaperKt.class",
            "androidx/compose/material/icons/rounded/WaterDropKt.class",
            "androidx/compose/material/icons/rounded/PaletteKt.class",
            "androidx/compose/material/icons/rounded/HelpOutlineKt.class",
            "androidx/compose/material/icons/rounded/GavelKt.class",
            "androidx/compose/material/icons/rounded/GroupsKt.class",
            "META-INF/material-icons-extended_release.kotlin_module",
            "META-INF/androidx.compose.material_material-icons-extended.version",
        )
        val sourceAar = materialIconsExtendedSource.singleFile
        val classesJar = ZipFile(sourceAar).use { aar ->
            val entry = aar.getEntry("classes.jar")
                ?: error("Official Material extended-icons AAR has no classes.jar")
            aar.getInputStream(entry).use { it.readBytes() }
        }
        val output = trimmedMaterialIconsJar.get().asFile
        output.parentFile.mkdirs()
        val copied = linkedSetOf<String>()
        ZipInputStream(ByteArrayInputStream(classesJar)).use { input ->
            ZipOutputStream(output.outputStream().buffered()).use { jar ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in wanted) {
                        val exactEntry = ZipEntry(entry.name).apply { time = 0L }
                        jar.putNextEntry(exactEntry)
                        input.copyTo(jar)
                        jar.closeEntry()
                        copied += entry.name
                    }
                    input.closeEntry()
                }
            }
        }
        check(copied == wanted) {
            "Material icon trim mismatch: missing=${wanted - copied}, unexpected=${copied - wanted}"
        }
    }
}

val stagedModuleSources = layout.buildDirectory.dir("generated/moduleSources")
val stageModuleSources = tasks.register<Sync>("stageModuleSources") {
    from(moduleSourceRoot) {
        exclude("com/smallhairball/deepx/SettingsActivity.java")
        exclude("com/smallhairball/deepx/BuildInfo.java")
        exclude("com/smallhairball/deepx/internal/**")
        if (!protectedDistribution) {
            exclude("com/smallhairball/deepx/CloudPromptClient.java")
            exclude("com/smallhairball/deepx/CommunityUi.java")
            exclude("com/smallhairball/deepx/LoaderSecurityGuard.java")
            exclude("com/smallhairball/deepx/z16.java")
            exclude("com/smallhairball/deepx/RuntimeProofPartA.java")
            exclude("com/smallhairball/deepx/RuntimeProofPartB.java")
        }
    }
    into(stagedModuleSources)
}
val protectedInputs = layout.buildDirectory.dir("generated/protectedInputs")
val prepareVariantInputs = tasks.register<Exec>("prepareVariantInputs") {
    val script = if (protectedDistribution) {
        "../prepare-protected-inputs.sh"
    } else {
        "../prepare-development-inputs.sh"
    }
    commandLine("bash", script)
    environment("SHI_V5_V241", shiV5V241.toString())
    environment("LOCAL_API_INCLUDED", localApiIncluded.toString())
    environment("GOOGLE_V241_BASIC", googleV241Basic.toString())
    environment("SHI_V5_HOST_VERSION_CODE", shiV5HostVersionCode)
    environment("SHI_V5_HOST_CERT_SHA256_ALT", shiV5HostCertSha256Alt)
    environment("MODULE_PROTECTED_PAYLOAD_SOURCE", file(modulePayloadSourceRoot).absolutePath)
    inputs.file(file(script))
    inputs.files(fileTree(modulePayloadSourceRoot))
    if (shiV5V241) {
        inputs.file(file("../../editions/closed/v241-protection/runtime-proof-compat.env"))
    }
    outputs.dir(protectedInputs)
    outputs.upToDateWhen { false }
}
val stagedModuleResources = layout.buildDirectory.dir("generated/moduleResources")
val stageModuleResources = tasks.register<Sync>("stageModuleResources") {
    from(moduleResourceRoot) {
        exclude("raw/gpl_3_0.txt")
    }
    into(stagedModuleResources)
}
val compileProtectedPayload = tasks.register<Exec>("compileProtectedPayload") {
    val classesDir = layout.buildDirectory.dir(
        "intermediates/javac/release/compileReleaseJavaWithJavac/classes")
    dependsOn("compileReleaseJavaWithJavac")
    commandLine("bash", "../compile-protected-payload.sh",
        classesDir.get().asFile.absolutePath)
    environment("SHI_V5_V241", shiV5V241.toString())
    environment("GOOGLE_V241_BASIC", googleV241Basic.toString())
    environment("MODULE_PROTECTED_PAYLOAD_SOURCE", file(modulePayloadSourceRoot).absolutePath)
    inputs.file(file("../compile-protected-payload.sh"))
    inputs.files(fileTree(modulePayloadSourceRoot))
    inputs.dir(classesDir)
    outputs.dir(protectedInputs.map { it.dir("resources") })
    outputs.upToDateWhen { false }
}
val stagedRuntimeResources = layout.buildDirectory.dir("generated/runtimeResources")
val stageRuntimeResources = tasks.register<Sync>("stageRuntimeResources") {
    from("$moduleResourceRoot/drawable-nodpi") {
        include("ic_category_*.png")
        into("META-INF/com.smallhairball.deepx.icons")
    }
    from("../../third_party/shizuku/rish_shizuku.dex") {
        into("META-INF/com.smallhairball.deepx.agent")
        rename { "rish_shizuku_rt.dat" }
    }
    into(stagedRuntimeResources)
}

android {
    namespace = "com.smallhairball.deepx"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smallhairball.deepx"
        minSdk = 24
        targetSdk = 37
        versionCode = 20260926
        versionName = "1.0.0 Beta"
        manifestPlaceholders["runtimeProofEnabled"] = shiV5V241.toString()
        manifestPlaceholders["closedActivationEnabled"] = protectedDistribution.toString()
        manifestPlaceholders["localApiComponentEnabled"] =
            (protectedDistribution && localApiIncluded).toString()
        manifestPlaceholders["applicationClassName"] = "android.app.Application"
        if (shiV5V241) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    sourceSets["main"].apply {
        manifest.srcFile(moduleManifestFile)
        java.directories += setOf(
            stagedModuleSources.get().asFile.path,
            protectedInputs.get().dir("src").asFile.path,
            file(moduleCompatRoot).path,
            file("src/main/java").path,
            file("../../editions/closed/full-backup-src").path,
        )
        res.directories += stagedModuleResources.get().asFile.path
        assets.directories += file(moduleAssetsRoot).path
        if (protectedDistribution) {
            assets.directories += protectedInputs.get().dir("assets").asFile.path
        }
        resources.directories += stagedRuntimeResources.get().asFile.path
        resources.directories += protectedInputs.get().dir("resources").asFile.path
        jniLibs.directories += file("src/main/jniLibs").path
        if (localApiIncluded && !shiV5V241) {
            jniLibs.directories += file("../../third_party/shi-native/android").path
        }
        if (shiV5V241) {
            jniLibs.directories += file("../../third_party/shi-v5-v241/android").path
        }
        if (protectedDistribution) {
            jniLibs.directories += protectedInputs.get().dir("native").asFile.path
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }

    androidResources {
        if (exactV241HostBuild) ignoreAssetsPattern = "rickroll.mp4"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = ENABLE_R8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules-no-obfuscate.pro",
            )
        }
        getByName("release") {
            isMinifyEnabled = ENABLE_R8
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules-no-obfuscate.pro",
                if (protectedDistribution) "proguard-protected-rules.pro"
                else "proguard-rules.pro",
            )
        }
    }
}

tasks.configureEach {
    if (name != "trimMaterialIcons" && name != "clean" && !name.startsWith("stage")) {
        dependsOn(trimMaterialIcons)
    }
    if (name.startsWith("compile") || name.startsWith("ksp") || name.startsWith("kapt")) {
        dependsOn(stageModuleSources)
        dependsOn(prepareVariantInputs)
    }
    if (name.contains("JavaRes") || name.startsWith("merge") && name.contains("Resource")) {
        dependsOn(stageRuntimeResources)
        dependsOn(prepareVariantInputs)
        if (protectedDistribution) dependsOn(compileProtectedPayload)
    }
    if (name.contains("NativeLib") || name.contains("JniLib")) {
        if (protectedDistribution) dependsOn(prepareVariantInputs)
        if (shiV5V241) dependsOn(compileProtectedPayload)
    }
    if (name.startsWith("merge") && name.contains("Assets")) {
        if (protectedDistribution) dependsOn(prepareVariantInputs)
    }
    if (name != "stageModuleResources" && name != "trimMaterialIcons" && name != "clean") {
        dependsOn(stageModuleResources)
    }
    if (name == "stageModuleResources") mustRunAfter("clean")
    if (name == "prepareVariantInputs") mustRunAfter("clean")
}

dependencies {
    materialIconsExtendedSource(
        "androidx.compose.material:material-icons-extended-android:1.7.8@aar"
    )
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core:1.18.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha19")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4")
    implementation("com.github.rikkahub:hugeicons-compose:1.3")
    implementation(files(trimmedMaterialIconsJar))
    compileOnly("de.robv.android.xposed:api:82")
}
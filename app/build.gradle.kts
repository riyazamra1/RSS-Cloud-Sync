import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application") version "8.11.0"
    kotlin("android") version "1.9.22"
}

val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) keystoreProps.load(FileInputStream(keystorePropsFile))
val hasValidSigningProps = keystorePropsFile.exists().also { exists -> if (exists) FileInputStream(keystorePropsFile).use { keystoreProps.load(it) } }.let { listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { key -> keystoreProps[key] != null } }

android {
    namespace = "com.riyaz.rsscloudsync"
    compileSdk = 36
    buildFeatures { buildConfig = true; viewBinding = true }
    lint { checkReleaseBuilds = false }
    signingConfigs { if (hasValidSigningProps) { create("release") { storeFile=rootProject.file(keystoreProps["storeFile"] as String); storePassword=keystoreProps["storePassword"] as String; keyAlias=keystoreProps["keyAlias"] as String; keyPassword=keystoreProps["keyPassword"] as String } } }
    defaultConfig { applicationId="com.riyaz.rsscloudsync"; minSdk=21; targetSdk=36; versionCode=8; versionName="1.8"; vectorDrawables { useSupportLibrary=true } }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    buildTypes { release { if (hasValidSigningProps) signingConfig=signingConfigs.getByName("release"); isMinifyEnabled=true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
    composeOptions { kotlinCompilerExtensionVersion="1.5.10" }
    packaging { resources { resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}"); resources.excludes.add("META-INF/kotlinx_coroutines_core.version"); resources.pickFirsts.add("nonJvmMain/default/linkdata/package_androidx/0_androidx.knm"); resources.pickFirsts.add("nonJvmMain/default/linkdata/root_package/0_androidx.knm"); resources.pickFirsts.add("nonJvmMain/default/linkdata/module"); resources.pickFirsts.add("nativeMain/default/linkdata/root_package/0_.knm"); resources.pickFirsts.add("nativeMain/default/linkdata/package_androidx/0_androidx.knm"); resources.pickFirsts.add("commonMain/default/linkdata/root_package/0_.knm"); resources.pickFirsts.add("commonMain/default/linkdata/module"); resources.pickFirsts.add("commonMain/default/linkdata/package_androidx/0_androidx.knm"); resources.merges.add("commonMain/default/manifest"); resources.merges.add("nonJvmMain/default/manifest"); resources.merges.add("nativeMain/default/manifest") } }
    configurations.all { resolutionStrategy { force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22"); force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22"); force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22"); force("androidx.collection:collection:1.4.2"); force("androidx.annotation:annotation:1.8.1"); force("androidx.core:core-ktx:1.8.0"); force("androidx.lifecycle:lifecycle-runtime-ktx:2.3.1"); force("androidx.collection:collection-ktx:1.4.2") } }
}
tasks.withType<JavaCompile> { options.compilerArgs.add("-Xlint:deprecation") }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions.jvmTarget="17" }

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.interpolator:interpolator:1.0.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime:2.11.2")
    implementation("com.google.android.gms:play-services-auth:21.4.0")
}

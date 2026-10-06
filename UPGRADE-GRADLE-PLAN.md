# WSC-Anzeige Gradle Upgrade Plan

## Current Baseline (verified)
- Build works with Java 16 helper script and Gradle Wrapper 7.0.2.
- Android Gradle Plugin (AGP): 7.0.2
- Kotlin Gradle Plugin: 1.5.31
- compileSdk / targetSdk: 30 / 30
- Hilt: 2.38.1

## Why the build failed before
- Wrapper was set to Gradle 8.9 while AGP/Kotlin stack is still on older versions.
- Result: Kapt task API incompatibility during configuration.

## Target
- Reach a modern Gradle 8.x stack without breaking the project in one big jump.

## Safety Rules
1. Change only one compatibility layer per step.
2. Build after every step.
3. If step fails, revert only that step and fix before continuing.

## Step-by-step route

### Step 0 (done)
- Keep wrapper on 7.0.2 to stay compatible with AGP 7.0.2.
- Validation command:
  - ./gradlew-java16 assembleDebug

### Step 1
- Upgrade to AGP 7.4.x line. (done: 7.4.2)
- Upgrade wrapper to the Gradle version required by chosen AGP 7.4.x release. (done: 7.6.4)
- Upgrade Kotlin plugin to a version compatible with that AGP/Gradle pair. (done: 1.7.20)
- Compatibility adjustments required by this step:
  - compileSdkVersion / targetSdkVersion: 31 / 31
  - lifecycleVersion: 2.4.1
  - roomVersion: 2.4.3
  - hiltVersion: 2.44
- Validation commands:
  - ./gradlew-java16 --version
  - ./gradlew-java16 assembleDebug

### Step 2
- Introduce namespace in app/build.gradle if still missing. (done)
- Keep applicationId unchanged. (done)
- Remove deprecated package attribute from AndroidManifest.xml. (done)
- Validation command:
  - ./gradlew-java16 assembleDebug

### Step 3
- Move toolchain to JDK 17. (done: local Temurin 17 under ~/.local/jdks/temurin-17)
- Switch from gradlew-java16 helper to JDK 17 build invocation. (done)
- Updated files:
  - gradlew-java17 helper added
  - .vscode/tasks.json build task uses gradlew-java17
  - scripts/install-debug-on-emulator.sh uses gradlew-java17
- Validation command:
  - ./gradlew assembleDebug

### Step 4
- Move to AGP 8.x baseline (first stable point, not latest yet). (done: AGP 8.0.2)
- Upgrade wrapper to matching Gradle 8.x required by that AGP release. (done: Gradle 8.0.2)
- Upgrade Kotlin plugin to a matching version. (done: Kotlin 1.8.10)
- Compatibility updates completed for this step:
  - Navigation Safe Args plugin: 2.5.3 (AGP 8 minimum satisfied)
  - Google Services Gradle plugin: 4.4.2
  - Java/Kotlin compile targets: 17 / 17
  - buildFeatures.buildConfig enabled explicitly
  - MainApplication BuildConfig import corrected to app package
- Validation commands:
  - ./gradlew assembleDebug
  - ./gradlew test

### Step 5
- Optional: move from AGP 8.x baseline to newer AGP 8.x and matching Gradle (for example Gradle 8.9) only after Step 4 is stable. (done)
- Implemented versions in this workspace:
  - AGP: 8.7.3
  - Gradle Wrapper: 8.9
  - Kotlin plugin: 1.9.24
  - Navigation Safe Args plugin: 2.5.3
  - Google Services Gradle plugin: 4.4.2
  - Hilt: 2.51.1
  - Room: 2.6.1
  - compileSdk / targetSdk: 34 / 34
- Validation commands:
  - ./gradlew assembleDebug
  - ./gradlew connectedAndroidTest

## Files to touch during migration
- gradle/wrapper/gradle-wrapper.properties
- build.gradle (project)
- app/build.gradle
- gradle.properties
- app/src/main/AndroidManifest.xml (only if namespace/package cleanup is needed)

## Quick rollback policy
- If a step fails, revert changed files for that step only.
- Re-run baseline build command before trying an alternative version pair.

/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.facebook.react.model.ModelPackageJson
import com.facebook.react.tasks.BuildCodegenCLITask
import com.facebook.react.tasks.GenerateCodegenArtifactsTask
import com.facebook.react.tasks.GenerateCodegenSchemaTask
import com.facebook.react.utils.JsonUtils
import com.facebook.react.utils.findPackageJsonFile
import java.io.File
import kotlin.system.exitProcess
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.internal.jvm.Jvm

class ReactPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    checkJvmVersion(project)
    val extension = project.extensions.create("react", ReactExtension::class.java, project)
    applyAppPlugin(project, extension)
    applyCodegenPlugin(project, extension)
  }

  private fun checkJvmVersion(project: Project) {
    val jvmVersion = Jvm.current()?.javaVersion?.majorVersion
    if ((jvmVersion?.toIntOrNull() ?: 0) <= 8) {
      project.logger.error(
          """

      ********************************************************************************

      ERROR: requires JDK11 or higher.
      Incompatible major version detected: '$jvmVersion'

      ********************************************************************************

      """.trimIndent())
      exitProcess(1)
    }
  }

  private fun applyAppPlugin(project: Project, config: ReactExtension) {
    project.afterEvaluate {
      if (config.applyAppPlugin.getOrElse(false)) {
        val androidConfiguration = project.extensions.getByType(BaseExtension::class.java)
        project.configureDevPorts(androidConfiguration)

        val isAndroidLibrary = project.plugins.hasPlugin("com.android.library")
        val variants =
            if (isAndroidLibrary) {
              project.extensions.getByType(LibraryExtension::class.java).libraryVariants
            } else {
              project.extensions.getByType(AppExtension::class.java).applicationVariants
            }
        variants.all { project.configureReactTasks(variant = it, config = config) }
      }
    }
  }

  /**
   * A plugin to enable react-native-codegen in Gradle environment. See the Gradle API docs for more
   * information: https://docs.gradle.org/current/javadoc/org/gradle/api/Project.html
   */
  private fun applyCodegenPlugin(project: Project, extension: ReactExtension) {
    // First, we set up the output dir for the codegen.
    val generatedSrcDir = File(project.buildDir, "generated/source/codegen")

    val buildCodegenTask =
        project.tasks.register("buildCodegenCLI", BuildCodegenCLITask::class.java) {
          it.codegenDir.set(project.getCodegenDir(extension))
          val bashWindowsHome = project.findProperty("REACT_WINDOWS_BASH") as String?
          it.bashWindowsHome.set(bashWindowsHome)
        }

    // We create the task to produce schema from JS files.
    val generateCodegenSchemaTask =
        project.tasks.register(
            "generateCodegenSchemaFromJavaScript", GenerateCodegenSchemaTask::class.java) { it ->
          it.dependsOn(buildCodegenTask)
          it.nodeExecutableAndArgs.set(extension.nodeExecutableAndArgs)
          it.codegenDir.set(project.getCodegenDir(extension))
          it.generatedSrcDir.set(generatedSrcDir)

          // We're reading the package.json at configuration time to properly feed
          // the `jsRootDir` @Input property of this task. Therefore, the
          // parsePackageJson should be invoked inside this lambda.
          val packageJson = findPackageJsonFile(project, extension)
          val parsedPackageJson = packageJson?.let { JsonUtils.fromCodegenJson(it) }

          val jsSrcsDirInPackageJson = parsedPackageJson?.codegenConfig?.jsSrcsDir
          if (jsSrcsDirInPackageJson != null) {
            it.jsRootDir.set(File(packageJson.parentFile, jsSrcsDirInPackageJson))
          } else {
            it.jsRootDir.set(extension.jsRootDir)
          }

          it.onlyIf { project.needsCodegenFromPackageJson(parsedPackageJson) }
        }

    // We create the task to generate Java code from schema.
    val generateCodegenArtifactsTask =
        project.tasks.register(
            "generateCodegenArtifactsFromSchema", GenerateCodegenArtifactsTask::class.java) {
          it.dependsOn(generateCodegenSchemaTask)
          it.reactNativeDir.set(project.getReactNativeDir(extension))
          it.deprecatedReactRoot.set(extension.reactRoot)
          it.nodeExecutableAndArgs.set(extension.nodeExecutableAndArgs)
          it.codegenDir.set(project.getCodegenDir(extension))
          it.generatedSrcDir.set(generatedSrcDir)
          it.packageJsonFile.set(findPackageJsonFile(project, extension))
          it.codegenJavaPackageName.set(extension.codegenJavaPackageName)
          it.libraryName.set(extension.libraryName)

          // We're reading the package.json at configuration time to properly feed
          // the `jsRootDir` @Input property of this task. Therefore, the
          // parsePackageJson should be invoked inside this lambda.
          val packageJson = findPackageJsonFile(project, extension)
          val parsedPackageJson = packageJson?.let { JsonUtils.fromCodegenJson(it) }

          it.onlyIf { project.needsCodegenFromPackageJson(parsedPackageJson) }
        }

    // We add dependencies & generated sources to the project.
    // Note: This last step needs to happen after the project has been evaluated.
    project.afterEvaluate {
      // `preBuild` is one of the base tasks automatically registered by Gradle.
      // This will invoke the codegen before compiling the entire project.
      project.tasks.named("preBuild", Task::class.java).dependsOn(generateCodegenArtifactsTask)

      /**
       * Finally, update the android configuration to include the generated sources. This equivalent
       * to this DSL:
       *
       * android { sourceSets { main { java { srcDirs += "$generatedSrcDir/java" } } } }
       *
       * See documentation at
       * https://google.github.io/android-gradle-dsl/current/com.android.build.gradle.BaseExtension.html.
       */
      val android = project.extensions.getByName("android") as BaseExtension

      android.sourceSets.getByName("main").java.srcDir(File(generatedSrcDir, "java"))
    }
  }

  /**
  * Function to look for the `package.json` and parse it. It returns a [ModelPackageJson] if found or
  * null others.
  *
  * Please note that this function access the [ReactExtension] field properties and calls .get() on
  * them, so calling this during apply() of the ReactPlugin is not recommended. It should be invoked
  * inside lazy lambdas or at execution time.
  */
  internal fun readPackageJsonFile(project: Project, extension: ReactExtension): ModelPackageJson? {
    val packageJson = findPackageJsonFile(project, extension)
    return packageJson?.let { JsonUtils.fromCodegenJson(it) }
  }

  internal fun Project.needsCodegenFromPackageJson(extension: ReactExtension): Boolean {
    val parsedPackageJson = readPackageJsonFile(this, extension)
    return needsCodegenFromPackageJson(parsedPackageJson)
  }

  internal fun Project.needsCodegenFromPackageJson(model: ModelPackageJson?): Boolean {
    /**
    This flag allows us to codegen TurboModule bindings for only Discord modules. We need this differentiation because
    React Native tooling only allows us to run TurboModule codegen if newArchEnabled=true, but the newArchEnabled flag
    assumes a complete migration to the New Architecture. Third party libraries make codegen assumptions that
    then break the build if we codegen, then build with newArchEnabled=false (things like codegen-ing TurboModule
    classes that then share the same name as legacy module classes used when newArchEnabled=false).

    The goal of this is to get us in a state where we can consume both TurboModules and legacy modules without having
    to fully migrate to the new architecture.
    */
    var discordApproved = true
    if (onlyDiscordTurboModuleCodegen()) {
      discordApproved = model?.codegenConfig?.android?.javaPackageName?.startsWith("com.discord") == true
    }

    // Adding a log to see what packages are getting codegen'd
    val willCodegen = discordApproved && model?.codegenConfig != null
    if (willCodegen) {
      println("Running codegen for package ${model?.codegenConfig?.android?.javaPackageName?.toString()}")
    }
    return willCodegen
  }

  /**
  NOTE(flewp): The react-native repository has two "react-native-codegen" projects, one in packages/, and one provided
  as a node module by yarn (eventually existing in node_modules). When we build React Native from source, we do not yarn
  the react-native project, so we're forcing the codegenDir to point to the packages version if we're in our
  Discord-only-TurboModule-codegen build state.
   */
  internal fun Project.getCodegenDir(extension: ReactExtension) = if(project.onlyDiscordTurboModuleCodegen())
    project.objects.directoryProperty().convention(
      project.rootProject.layout.projectDirectory.dir("../../discord_app/node_modules/react-native/packages/react-native-codegen")
    )
    else extension.codegenDir

  /**
  NOTE(flewp): The `reactNativeDir` is part of a newer way of configuring the React Native build via a `react {}` gradle
  object placed at the app/build.gradle level (vs the old `project.ext.react = []` way). Its default value points to
  "../node_modules/react-native", which is incorrect because of our monorepo.

  Currently, setting the `react { reactNativeDir = "" }` in our Android app is, for an unknown reason, not affecting the
  `reactNativeDir` property here in this plugin file, so this forces the property to be set in the correct place if
  we're trying to codegen Discord TurboModules.
   */
  internal fun Project.getReactNativeDir(extension: ReactExtension) = if(project.onlyDiscordTurboModuleCodegen())
    project.objects.directoryProperty().convention(
      project.rootProject.layout.projectDirectory.dir("../../discord_app/node_modules/react-native")
    )
    else extension.reactNativeDir

  internal fun Project.onlyDiscordTurboModuleCodegen() = project.hasProperty("onlyDiscordTurboModulesEnabled")
}

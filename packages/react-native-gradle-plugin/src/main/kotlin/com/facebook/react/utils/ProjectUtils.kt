/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.utils

import com.facebook.react.model.ModelPackageJson
import com.facebook.react.utils.KotlinStdlibCompatUtils.lowercaseCompat
import com.facebook.react.utils.KotlinStdlibCompatUtils.toBooleanStrictOrNullCompat
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty

internal object ProjectUtils {
  internal val Project.isNewArchEnabled: Boolean
    get() =
        project.hasProperty("newArchEnabled") &&
            project.property("newArchEnabled").toString().toBoolean()

  const val HERMES_FALLBACK = true

  internal val Project.isHermesEnabled: Boolean
    get() =
        if (project.hasProperty("hermesEnabled")) {
          project
              .property("hermesEnabled")
              .toString()
              .lowercaseCompat()
              .toBooleanStrictOrNullCompat()
              ?: true
        } else if (project.extensions.extraProperties.has("react")) {
          @Suppress("UNCHECKED_CAST")
          val reactMap = project.extensions.extraProperties.get("react") as? Map<String, Any?>
          when (val enableHermesKey = reactMap?.get("enableHermes")) {
            is Boolean -> enableHermesKey
            is String -> enableHermesKey.lowercaseCompat().toBooleanStrictOrNullCompat() ?: true
            else -> HERMES_FALLBACK
          }
        } else {
          HERMES_FALLBACK
        }

  internal fun Project.needsCodegenFromPackageJson(rootProperty: DirectoryProperty): Boolean {
    val parsedPackageJson = readPackageJsonFile(this, rootProperty)
    return needsCodegenFromPackageJson(parsedPackageJson)
  }

  internal fun Project.needsCodegenFromPackageJson(model: ModelPackageJson?): Boolean {
    /**
    This flag allows us to codegen TurboModule bindings for only Discord modules and core React Native modules.
    We need this differentiation because React Native tooling only allows us to run TurboModule codegen for non-core
    modules if newArchEnabled=true, but the newArchEnabled flag assumes a complete migration to the New Architecture.
    Other non-core third party libraries make codegen assumptions that then break the build if we codegen, then build
    with newArchEnabled=false (things like codegen-ing TurboModule classes that then share the same name as legacy
    module classes used when newArchEnabled=false).
    The goal of this is to get us in a state where we can consume both TurboModules and legacy modules without having
    to fully migrate to the new architecture.
    */
    var discordApproved = true
    if (project.hasProperty("onlyDiscordTurboModulesEnabled") && (project.property("onlyDiscordTurboModulesEnabled").toString().lowercaseCompat().toBooleanStrictOrNullCompat() ?: false)) {
      // "ReactAndroid" tasks generate core React Native modules, and we can't build without these. Otherwise look for
      // modules that begin with "com.discord", those are Discord's TurboModules.
      discordApproved = this.name == "ReactNativeSource" ||
        model?.codegenConfig?.android?.javaPackageName?.startsWith("com.discord") == true
    }

    // Adding a log to see what packages are getting codegen'd
    val willCodegen = discordApproved && model?.codegenConfig != null
    if (willCodegen) {
      println("Running codegen for package ${model?.codegenConfig?.android?.javaPackageName?.toString()}")
    }
    return willCodegen
  }

  internal fun Project.getReactNativeArchitectures(): List<String> {
    val architectures = mutableListOf<String>()
    if (project.hasProperty("reactNativeArchitectures")) {
      val architecturesString = project.property("reactNativeArchitectures").toString()
      architectures.addAll(architecturesString.split(",").filter { it.isNotBlank() })
    }
    return architectures
  }
}

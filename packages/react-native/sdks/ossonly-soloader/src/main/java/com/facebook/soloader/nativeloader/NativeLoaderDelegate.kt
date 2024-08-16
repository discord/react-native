/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.soloader.nativeloader

/**
 * This class is a stub of NativeLoaderDelegate used ONLY by React Native OSS.
 *
 * Fresco in OSS depends on NativeLoader, but we don't want to include the real
 * NativeLoader/SoLoader in React Native OSS. This stub is used to make Fresco work properly for us.
 */
public interface NativeLoaderDelegate {

  public fun loadLibrary(shortName: String?, flags: Int): Boolean

  public fun getLibraryPath(libName: String?): String

  public fun getSoSourcesVersion(): Int
}
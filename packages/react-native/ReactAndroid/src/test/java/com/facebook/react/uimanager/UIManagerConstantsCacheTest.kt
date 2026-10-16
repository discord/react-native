/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.uimanager

import com.tencent.mmkv.MMKV
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class UIManagerConstantsCacheTest {

  @Test(timeout = 1_000)
  fun disabledCacheSkipsAllWorkAndCannotBeReenabled() {
    val cache = newCache()
    val application = RuntimeEnvironment.getApplication()

    mockStatic(MMKV::class.java).use { mmkv ->
      cache.init(application, false)
      cache.init(application, true)

      assertThat(cache.getCachedConstants()).isNull()
      assertThat(cache.getCachedBubblingEventsTypes()).isNull()
      assertThat(cache.getUIManagerConstantsAsWritableMap()).isNull()

      cache.saveConstantsAndBubblingEventsTypes(mapOf("key" to "value"), null)
      mmkv.verifyNoInteractions()
    }
  }

  private fun newCache(): UIManagerConstantsCache {
    val constructor = UIManagerConstantsCache::class.java.getDeclaredConstructor()
    constructor.isAccessible = true
    return constructor.newInstance()
  }
}

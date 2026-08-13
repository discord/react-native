/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.fabric.mounting

import android.view.View
import android.widget.FrameLayout
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.testutils.shadows.ShadowSoLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSoLoader::class])
class ViewTransitionCoordinatorTest {
  private lateinit var coordinator: ViewTransitionCoordinator
  private lateinit var reactContext: ReactApplicationContext

  @Before
  fun setUp() {
    ReactNativeFeatureFlagsForTests.setUp()
    reactContext = BridgeReactContext(RuntimeEnvironment.getApplication())
    coordinator = ViewTransitionCoordinator()
  }

  @Test
  fun viewInTransition_shouldEnqueueOperations() {
    val childTag = 100
    val parentTag = 200

    assertThat(coordinator.shouldEnqueueOperation(childTag, parentTag)).isFalse()

    coordinator.markViewInTransition(childTag, true, null) {}

    assertThat(coordinator.shouldEnqueueOperation(childTag, parentTag)).isTrue()
  }

  @Test
  fun enqueueAndDrainAddOperation() {
    val childTag = 100
    val parentTag = 200
    val parentView = FrameLayout(reactContext)
    val childView = View(reactContext)

    coordinator.markViewInTransition(childTag, true, null) {}
    val operation = AddViewOperation(childTag, parentTag, 0, parentView, childView)
    coordinator.enqueueOperation(operation)

    assertThat(coordinator.shouldEnqueueOperation(childTag, parentTag)).isTrue()

    coordinator.markViewInTransition(childTag, false, null) {}
  }

  @Test
  fun queueMaintainsOrderForParent() {
    val parent1Tag = 200
    val parent2Tag = 300
    val childTag = 100
    val parentView1 = FrameLayout(reactContext)
    val parentView2 = FrameLayout(reactContext)
    val childView = View(reactContext)

    coordinator.markViewInTransition(childTag, true, null) {}

    val operation1 = AddViewOperation(childTag, parent1Tag, 0, parentView1, childView)
    val operation2 = AddViewOperation(childTag, parent2Tag, 0, parentView2, childView)
    coordinator.enqueueOperation(operation1)
    coordinator.enqueueOperation(operation2)

    assertThat(coordinator.shouldEnqueueOperation(999, parent1Tag)).isTrue()
    assertThat(coordinator.shouldEnqueueOperation(999, parent2Tag)).isTrue()
    assertThat(coordinator.isFirstInLineForChild(childTag, parent1Tag)).isTrue()
    assertThat(coordinator.isFirstInLineForChild(childTag, parent2Tag)).isFalse()
  }

  @Test
  fun clearAllPending() {
    val childTag = 100
    val parentTag = 200
    val parentView = FrameLayout(reactContext)
    val childView = View(reactContext)

    coordinator.markViewInTransition(childTag, true, null) {}
    val operation = AddViewOperation(childTag, parentTag, 0, parentView, childView)
    coordinator.enqueueOperation(operation)
    assertThat(coordinator.shouldEnqueueOperation(childTag, parentTag)).isTrue()

    coordinator.clearAllPending()

    assertThat(coordinator.shouldEnqueueOperation(childTag, parentTag)).isFalse()
  }

  @Test
  fun deleteQueueShouldNotImpactUnrelatedViews() {
    val childATag = 100
    val childBTag = 101

    coordinator.markViewInTransition(childATag, true, null) {}
    val deleteOperation = DeleteViewOperation(childATag)
    coordinator.enqueueOperation(deleteOperation)

    val shouldEnqueue =
        coordinator.shouldEnqueueOperation(
            childBTag,
            DELETE_VIEW_PARENT_TAG,
            false,
        )
    assertThat(shouldEnqueue).isFalse()
  }
}

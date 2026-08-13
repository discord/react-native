/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.fabric.mounting

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnDetach
import com.facebook.common.logging.FLog
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BridgeReactContext
import com.facebook.react.fabric.mounting.MountingManager.MountItemExecutor
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlags
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsDefaults
import com.facebook.react.internal.featureflags.ReactNativeFeatureFlagsForTests
import com.facebook.react.touch.JSResponderHandler
import com.facebook.react.uimanager.RootViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.uimanager.ViewManagerRegistry
import com.facebook.react.views.view.ReactViewManager
import com.facebook.testutils.shadows.ShadowArguments
import com.facebook.testutils.shadows.ShadowNativeArray
import com.facebook.testutils.shadows.ShadowNativeLoader
import com.facebook.testutils.shadows.ShadowNativeMap
import com.facebook.testutils.shadows.ShadowReadableNativeArray
import com.facebook.testutils.shadows.ShadowReadableNativeMap
import com.facebook.testutils.shadows.ShadowSoLoader
import com.facebook.testutils.shadows.ShadowWritableNativeArray
import com.facebook.testutils.shadows.ShadowWritableNativeMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
          ShadowArguments::class,
          ShadowSoLoader::class,
          ShadowNativeLoader::class,
          ShadowNativeArray::class,
          ShadowNativeMap::class,
          ShadowWritableNativeMap::class,
          ShadowWritableNativeArray::class,
          ShadowReadableNativeMap::class,
          ShadowReadableNativeArray::class,
        ]
)
class SurfaceMountingManagerIntegrationTest {
  private lateinit var surfaceMountingManager: SurfaceMountingManager
  private lateinit var reactContext: BridgeReactContext
  private lateinit var themedReactContext: ThemedReactContext
  private lateinit var activity: Activity

  @Before
  fun setUp() {
    ReactNativeFeatureFlagsForTests.setUp()
    val featureFlags =
        object : ReactNativeFeatureFlagsDefaults() {
          override fun enableFabricLogs(): Boolean = true
        }
    ReactNativeFeatureFlags.override(featureFlags)
    FLog.setMinimumLoggingLevel(android.util.Log.DEBUG)

    reactContext = BridgeReactContext(RuntimeEnvironment.getApplication())
    themedReactContext = ThemedReactContext(reactContext, reactContext, null, -1)

    val viewManagers = listOf<ViewManager<*, *>>(ReactViewManager())
    val viewManagerRegistry = ViewManagerRegistry(viewManagers)
    val rootViewManager = RootViewManager()
    val mountItemExecutor = MountItemExecutor {}
    surfaceMountingManager =
        SurfaceMountingManager(
            SURFACE_ID,
            JSResponderHandler(),
            viewManagerRegistry,
            rootViewManager,
            mountItemExecutor,
            themedReactContext,
        )

    val controller = Robolectric.buildActivity(Activity::class.java)
    controller.setup()
    activity = controller.get()
    ShadowLog.stream = System.out
  }

  @Test
  fun addViewAt_executesImmediatelyWithoutTransition() {
    val parentView = createView(100)
    val childView = createView(200)

    surfaceMountingManager.addViewAt(100, 200, 0)

    assertThat(parentView.childCount).isEqualTo(1)
    assertThat(parentView.getChildAt(0)).isEqualTo(childView)
  }

  @Test
  fun removeViewAt_queuesOperationWhenParentHasQueuedOperations() {
    val parentTag = 100
    val childTag = 200
    val parentView = createView(parentTag)
    val childView = createView(childTag)
    activity.setContentView(parentView)
    surfaceMountingManager.addViewAt(parentTag, childTag, 0)

    startTransition(parentView, childView, childTag)
    val latch = detachLatch(childView)
    surfaceMountingManager.removeViewAt(childTag, parentTag, 0)

    assertThat(parentView.childCount).isEqualTo(0)
    assertThat(childView.parent).isEqualTo(parentView)

    finishTransition(parentView, childView, childTag, latch)

    assertThat(parentView.childCount).isEqualTo(0)
    assertThat(childView.parent).isNull()
  }

  @Test
  fun addViewAt_maintainsParentOrderForSameChild() {
    val rootTag = 10
    val parent1Tag = 100
    val parent2Tag = 200
    val childTag = 300
    val rootView = createView(rootTag)
    val parent1View = createView(parent1Tag)
    val parent2View = createView(parent2Tag)
    val childView = createView(childTag)
    activity.setContentView(rootView)
    surfaceMountingManager.addViewAt(rootTag, parent1Tag, 0)
    surfaceMountingManager.addViewAt(rootTag, parent2Tag, 1)
    surfaceMountingManager.addViewAt(parent1Tag, childTag, 0)

    startTransition(parent1View, childView, childTag)
    val latch = detachLatch(childView)
    surfaceMountingManager.removeViewAt(childTag, parent1Tag, 0)
    surfaceMountingManager.addViewAt(parent2Tag, childTag, 0)

    finishTransition(parent1View, childView, childTag, latch)

    assertThat(parent1View.childCount).isEqualTo(0)
    assertThat(parent2View.childCount).isEqualTo(1)
    assertThat(parent2View.getChildAt(0)).isEqualTo(childView)
    assertThat(childView.parent).isEqualTo(parent2View)
  }

  @Test
  fun queuedAddToDeletedParent_doesNotCrashWhenTransitionDrains() {
    val rootTag = 100
    val originalParentTag = 200
    val destinationParentTag = 300
    val childTag = 400
    val rootView = createView(rootTag)
    val originalParentView = createView(originalParentTag)
    createView(destinationParentTag)
    val childView = createView(childTag)
    activity.setContentView(rootView)
    surfaceMountingManager.addViewAt(rootTag, originalParentTag, 0)
    surfaceMountingManager.addViewAt(rootTag, destinationParentTag, 1)
    surfaceMountingManager.addViewAt(originalParentTag, childTag, 0)

    startTransition(originalParentView, childView, childTag)
    val latch = detachLatch(childView)
    surfaceMountingManager.removeViewAt(childTag, originalParentTag, 0)
    surfaceMountingManager.addViewAt(destinationParentTag, childTag, 0)

    // The queued add retains the destination ViewGroup, but the coordinator does not prevent its
    // ViewState from being deleted before the child transition finishes.
    surfaceMountingManager.removeViewAt(destinationParentTag, rootTag, 1)
    surfaceMountingManager.deleteView(destinationParentTag)

    finishTransition(originalParentView, childView, childTag, latch)
  }

  @Test
  fun removeAndDelete_fullyDrains() {
    val parentTag = 300
    val child1Tag = 100
    val child2Tag = 200
    val parentView = createView(parentTag)
    val childView1 = createView(child1Tag)
    val childView2 = createView(child2Tag)
    activity.setContentView(parentView)
    surfaceMountingManager.addViewAt(parentTag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentTag, child2Tag, 1)

    startTransition(parentView, childView1, child1Tag)
    startTransition(parentView, childView2, child2Tag)
    val latch = detachLatch(childView1, childView2)
    surfaceMountingManager.removeViewAt(child2Tag, parentTag, 1)
    surfaceMountingManager.removeViewAt(child1Tag, parentTag, 0)
    surfaceMountingManager.deleteView(child1Tag)
    surfaceMountingManager.deleteView(child2Tag)

    surfaceMountingManager.markViewInTransition(child1Tag, false)
    surfaceMountingManager.markViewInTransition(child2Tag, false)
    parentView.endViewTransition(childView1)
    parentView.endViewTransition(childView2)
    awaitDetach(latch)

    assertThat(parentView.childCount).isEqualTo(0)
    assertThat(childView1.parent).isNull()
    assertThat(childView2.parent).isNull()
    assertThatThrownBy { surfaceMountingManager.getView(child1Tag) }
    assertThatThrownBy { surfaceMountingManager.getView(child2Tag) }
    assertThat(getCoordinator().isEmpty()).isTrue()
  }

  @Test
  fun removeAndReparent_doesNotDeadlock() {
    val parentATag = 100
    val parentBTag = 200
    val child1Tag = 300
    val child2Tag = 400
    val parentAView = createView(parentATag)
    val parentBView = createView(parentBTag)
    val childView1 = createView(child1Tag)
    val childView2 = createView(child2Tag)
    activity.setContentView(parentAView)
    surfaceMountingManager.addViewAt(parentATag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentATag, child2Tag, 1)

    startTransition(parentAView, childView1, child1Tag)
    startTransition(parentAView, childView2, child2Tag)
    val latch = detachLatch(childView1, childView2)
    surfaceMountingManager.removeViewAt(child2Tag, parentATag, 1)
    surfaceMountingManager.removeViewAt(child1Tag, parentATag, 0)
    surfaceMountingManager.addViewAt(parentBTag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentBTag, child2Tag, 1)

    surfaceMountingManager.markViewInTransition(child1Tag, false)
    surfaceMountingManager.markViewInTransition(child2Tag, false)
    parentAView.endViewTransition(childView1)
    parentAView.endViewTransition(childView2)
    awaitDetach(latch)

    assertThat(parentAView.childCount).isEqualTo(0)
    assertThat(parentBView.childCount).isEqualTo(2)
    assertThat(parentBView.getChildAt(0)).isEqualTo(childView1)
    assertThat(parentBView.getChildAt(1)).isEqualTo(childView2)
    assertThat(getCoordinator().isEmpty()).isTrue()
  }

  @Test
  fun multipleReparenting_doesNotDeadlock() {
    val parentATag = 100
    val parentBTag = 200
    val parentCTag = 300
    val child1Tag = 400
    val child2Tag = 500
    val parentAView = createView(parentATag)
    val parentBView = createView(parentBTag)
    val parentCView = createView(parentCTag)
    val childView1 = createView(child1Tag)
    val childView2 = createView(child2Tag)
    activity.setContentView(parentAView)
    surfaceMountingManager.addViewAt(parentATag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentATag, child2Tag, 1)

    startTransition(parentAView, childView1, child1Tag)
    startTransition(parentAView, childView2, child2Tag)
    val latch = detachLatch(childView1, childView2)
    surfaceMountingManager.removeViewAt(child2Tag, parentATag, 1)
    surfaceMountingManager.removeViewAt(child1Tag, parentATag, 0)
    surfaceMountingManager.addViewAt(parentBTag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentBTag, child2Tag, 1)
    surfaceMountingManager.removeViewAt(child2Tag, parentBTag, 1)
    surfaceMountingManager.removeViewAt(child1Tag, parentBTag, 0)
    surfaceMountingManager.addViewAt(parentCTag, child1Tag, 0)
    surfaceMountingManager.addViewAt(parentCTag, child2Tag, 1)

    surfaceMountingManager.markViewInTransition(child1Tag, false)
    surfaceMountingManager.markViewInTransition(child2Tag, false)
    parentAView.endViewTransition(childView1)
    parentAView.endViewTransition(childView2)
    awaitDetach(latch)

    assertThat(parentAView.childCount).isEqualTo(0)
    assertThat(parentBView.childCount).isEqualTo(0)
    assertThat(parentCView.childCount).isEqualTo(2)
    assertThat(parentCView.getChildAt(0)).isEqualTo(childView1)
    assertThat(parentCView.getChildAt(1)).isEqualTo(childView2)
    assertThat(getCoordinator().isEmpty()).isTrue()
  }

  @Test
  fun partialDrainThenFullDrain_cleansUpAllChildren() {
    val parentATag = 100
    val parentBTag = 200
    val childTags = intArrayOf(300, 400, 500, 600)
    val parentAView = createView(parentATag)
    val parentBView = createView(parentBTag)
    val childViews = childTags.map(::createView)
    activity.setContentView(parentAView)
    for (index in childTags.indices) {
      surfaceMountingManager.addViewAt(parentATag, childTags[index], index)
      startTransition(parentAView, childViews[index], childTags[index])
    }

    val firstLatch = detachLatch(childViews[2], childViews[3])
    val secondLatch = detachLatch(childViews[0], childViews[1])
    for (index in childTags.indices.reversed()) {
      surfaceMountingManager.removeViewAt(childTags[index], parentATag, index)
    }
    for (index in childTags.indices) {
      surfaceMountingManager.addViewAt(parentBTag, childTags[index], index)
    }

    surfaceMountingManager.markViewInTransition(childTags[3], false)
    surfaceMountingManager.markViewInTransition(childTags[2], false)
    parentAView.endViewTransition(childViews[3])
    parentAView.endViewTransition(childViews[2])
    awaitDetach(firstLatch)

    surfaceMountingManager.markViewInTransition(childTags[1], false)
    surfaceMountingManager.markViewInTransition(childTags[0], false)
    parentAView.endViewTransition(childViews[1])
    parentAView.endViewTransition(childViews[0])
    awaitDetach(secondLatch)

    assertThat(parentAView.childCount).isEqualTo(0)
    assertThat(parentBView.childCount).isEqualTo(4)
    for (index in childViews.indices) {
      assertThat(parentBView.getChildAt(index)).isEqualTo(childViews[index])
    }
    assertThat(getCoordinator().isEmpty()).isTrue()
  }

  @Test
  fun viewNotMarkedButWithParent_isQueued() {
    val parent1Tag = 100
    val parent2Tag = 200
    val childTag = 300
    val parent1View = createView(parent1Tag)
    val parent2View = createView(parent2Tag)
    val childView = createView(childTag)
    activity.setContentView(parent1View)
    surfaceMountingManager.addViewAt(parent1Tag, childTag, 0)

    parent1View.startViewTransition(childView)
    parent1View.removeView(childView)
    assertThat(childView.parent).isNotNull()
    surfaceMountingManager.addViewAt(parent2Tag, childTag, 0)
    val latch = detachLatch(childView)

    parent1View.endViewTransition(childView)
    awaitDetach(latch)

    assertThat(parent1View.childCount).isEqualTo(0)
    assertThat(parent2View.childCount).isEqualTo(1)
    assertThat(parent2View.getChildAt(0)).isEqualTo(childView)
  }

  @Test
  fun queuedDeleteForUnrelatedView_doesNotAffectRecreatedTag() {
    val windowTag = 10
    val parentTag = 20
    val intermediateTag = 16
    val childTag = 14
    val unrelatedTag = 999
    val windowView = createView(windowTag)
    val parentView = createView(parentTag)
    val oldIntermediateView = createView(intermediateTag)
    val childView = createView(childTag)
    val unrelatedView = createView(unrelatedTag)
    activity.setContentView(windowView)
    surfaceMountingManager.addViewAt(windowTag, parentTag, 0)
    surfaceMountingManager.addViewAt(parentTag, intermediateTag, 0)
    surfaceMountingManager.addViewAt(intermediateTag, childTag, 0)
    surfaceMountingManager.addViewAt(windowTag, unrelatedTag, 1)

    startTransition(windowView, unrelatedView, unrelatedTag)
    val latch = detachLatch(unrelatedView)
    surfaceMountingManager.removeViewAt(unrelatedTag, windowTag, 1)
    surfaceMountingManager.deleteView(unrelatedTag)

    surfaceMountingManager.removeViewAt(childTag, intermediateTag, 0)
    surfaceMountingManager.removeViewAt(intermediateTag, parentTag, 0)
    surfaceMountingManager.deleteView(intermediateTag)
    surfaceMountingManager.addViewAt(parentTag, childTag, 0)

    surfaceMountingManager.removeViewAt(childTag, parentTag, 0)
    val newIntermediateView = createView(intermediateTag)
    surfaceMountingManager.addViewAt(parentTag, intermediateTag, 0)
    surfaceMountingManager.addViewAt(intermediateTag, childTag, 0)

    finishTransition(windowView, unrelatedView, unrelatedTag, latch)

    val mountedIntermediateView = surfaceMountingManager.getView(intermediateTag)
    assertThat(mountedIntermediateView).isEqualTo(newIntermediateView)
    assertThat(mountedIntermediateView).isNotEqualTo(oldIntermediateView)

    surfaceMountingManager.removeViewAt(childTag, intermediateTag, 0)
    surfaceMountingManager.removeViewAt(intermediateTag, parentTag, 0)
    surfaceMountingManager.deleteView(intermediateTag)
    assertThat(childView.parent).isNull()
    surfaceMountingManager.addViewAt(parentTag, childTag, 0)
    assertThat(childView.parent).isEqualTo(parentView)
  }

  @Test
  fun queuedDelete_isCancelledWhenTagIsRecreated() {
    val windowTag = 10
    val parentTag = 20
    val intermediateTag = 16
    val childTag = 14
    val windowView = createView(windowTag)
    val parentView = createView(parentTag)
    val oldIntermediateView = createView(intermediateTag)
    val childView = createView(childTag)
    activity.setContentView(windowView)
    surfaceMountingManager.addViewAt(windowTag, parentTag, 0)
    surfaceMountingManager.addViewAt(parentTag, intermediateTag, 0)
    surfaceMountingManager.addViewAt(intermediateTag, childTag, 0)

    startTransition(parentView, oldIntermediateView, intermediateTag)
    val latch = detachLatch(oldIntermediateView)
    surfaceMountingManager.removeViewAt(childTag, intermediateTag, 0)
    surfaceMountingManager.removeViewAt(intermediateTag, parentTag, 0)
    surfaceMountingManager.deleteView(intermediateTag)
    surfaceMountingManager.addViewAt(parentTag, childTag, 0)

    surfaceMountingManager.removeViewAt(childTag, parentTag, 0)
    val recreatedIntermediateView = createView(intermediateTag)
    surfaceMountingManager.addViewAt(parentTag, intermediateTag, 0)
    surfaceMountingManager.addViewAt(intermediateTag, childTag, 0)

    finishTransition(parentView, oldIntermediateView, intermediateTag, latch)

    val mountedIntermediateView = surfaceMountingManager.getView(intermediateTag)
    assertThat(mountedIntermediateView).isEqualTo(recreatedIntermediateView)
    assertThat(mountedIntermediateView).isEqualTo(oldIntermediateView)

    surfaceMountingManager.removeViewAt(childTag, intermediateTag, 0)
    surfaceMountingManager.removeViewAt(intermediateTag, parentTag, 0)
    surfaceMountingManager.deleteView(intermediateTag)
    assertThat(childView.parent).isNull()
    surfaceMountingManager.addViewAt(parentTag, childTag, 0)
    assertThat(childView.parent).isEqualTo(parentView)
  }

  private fun createView(tag: Int): ViewGroup {
    val props = Arguments.createMap()
    surfaceMountingManager.createView("RCTView", tag, props, null, null, true)
    return surfaceMountingManager.getView(tag) as ViewGroup
  }

  private fun startTransition(parent: ViewGroup, child: View, childTag: Int) {
    parent.startViewTransition(child)
    surfaceMountingManager.markViewInTransition(childTag, true)
  }

  private fun finishTransition(
      parent: ViewGroup,
      child: View,
      childTag: Int,
      latch: CountDownLatch,
  ) {
    surfaceMountingManager.markViewInTransition(childTag, false)
    parent.endViewTransition(child)
    awaitDetach(latch)
  }

  private fun detachLatch(vararg views: View): CountDownLatch {
    val latch = CountDownLatch(views.size)
    for (view in views) {
      view.doOnDetach { latch.countDown() }
    }
    return latch
  }

  private fun awaitDetach(latch: CountDownLatch) {
    assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
    val mainLooper = Looper.getMainLooper()
    Shadows.shadowOf(mainLooper).idle()
  }

  private fun getCoordinator(): ViewTransitionCoordinator {
    val field = SurfaceMountingManager::class.java.getDeclaredField("viewTransitionCoordinator")
    field.isAccessible = true
    return field.get(surfaceMountingManager) as ViewTransitionCoordinator
  }

  private companion object {
    private const val SURFACE_ID = 1
  }
}

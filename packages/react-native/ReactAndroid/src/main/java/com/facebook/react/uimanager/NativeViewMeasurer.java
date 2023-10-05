/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */
package com.facebook.react.uimanager;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewParent;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.UiThreadUtil;

public class NativeViewMeasurer {
  public static final String TAG = "NativeViewMeasurer";
  private final ViewProvider viewProvider;
  public NativeViewMeasurer(ViewProvider viewProvider) {
    this.viewProvider = viewProvider;
  }

  /**
   * Returns true on success, false on failure. If successful, after calling, output buffer will be
   * {x, y, width, height}.
   */
  public int[] measure(int tag) {
    UiThreadUtil.assertOnUiThread();

    int[] outputBuffer = {0, 0, 0, 0, 0, 0};
    View v = viewProvider.provideView(tag);
    if (v == null) {
      FLog.w(TAG, "measure: No native view for " + tag + " currently exists");
      return outputBuffer;
    }

    View rootView = (View) RootViewUtil.getRootView(v);
    // It is possible that the RootView can't be found because this view is no longer on the screen
    // and has been removed by clipping
    if (rootView == null) {
      FLog.w(TAG, "measure: Native view " + tag + " is no longer on screen");
      return outputBuffer;
    }

    computeBoundingBox(rootView, outputBuffer);
    int rootX = outputBuffer[0];
    int rootY = outputBuffer[1];
    computeBoundingBox(v, outputBuffer);
    outputBuffer[0] -= rootX;
    outputBuffer[1] -= rootY;
    return outputBuffer;
  }

  /**
   * Returns the coordinates of a view relative to the window (not just the RootView which is what
   * measure will return)
   *
   * @param tag - the tag for the view
   */
  public int[] measureInWindow(int tag) {
    UiThreadUtil.assertOnUiThread();
    View v = viewProvider.provideView(tag);
    int[] outputBuffer = {0, 0, 0, 0};
    if (v == null) {
      FLog.w(TAG, "measureInWindow: No native view for " + tag + " currently exists");
      return outputBuffer;
    }

    int[] locationOutputBuffer = new int[2];
    v.getLocationOnScreen(locationOutputBuffer);

    // we need to subtract visibleWindowCoords - to subtract possible window insets, split screen or
    // multi window
    Rect visibleWindowFrame = new Rect();
    v.getWindowVisibleDisplayFrame(visibleWindowFrame);
    outputBuffer[0] = locationOutputBuffer[0] - visibleWindowFrame.left;
    outputBuffer[1] = locationOutputBuffer[1] - visibleWindowFrame.top;

    // outputBuffer[0,1] already contain what we want
    outputBuffer[2] = v.getWidth();
    outputBuffer[3] = v.getHeight();
    return outputBuffer;
  }

  private void computeBoundingBox(View view, int[] outputBuffer) {
    RectF boundingBox = new RectF(0, 0, view.getWidth(), view.getHeight());
    boundingBox.set(0, 0, view.getWidth(), view.getHeight());
    mapRectFromViewToWindowCoords(view, boundingBox);

    outputBuffer[0] = Math.round(boundingBox.left);
    outputBuffer[1] = Math.round(boundingBox.top);
    outputBuffer[2] = Math.round(boundingBox.right - boundingBox.left);
    outputBuffer[3] = Math.round(boundingBox.bottom - boundingBox.top);
    outputBuffer[4] = Math.round(view.getLeft());
    outputBuffer[5] = Math.round(view.getTop());
  }

  private void mapRectFromViewToWindowCoords(View view, RectF rect) {
    Matrix matrix = view.getMatrix();
    if (!matrix.isIdentity()) {
      matrix.mapRect(rect);
    }

    rect.offset(view.getLeft(), view.getTop());

    ViewParent parent = view.getParent();
    while (parent instanceof View) {
      View parentView = (View) parent;

      rect.offset(-parentView.getScrollX(), -parentView.getScrollY());

      matrix = parentView.getMatrix();
      if (!matrix.isIdentity()) {
        matrix.mapRect(rect);
      }

      rect.offset(parentView.getLeft(), parentView.getTop());

      parent = parentView.getParent();
    }
  }


  public interface ViewProvider {
    View provideView(int tag);
  }
}


/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.imagehelper;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import com.facebook.infer.annotation.Assertions;

import java.util.Objects;

/** Class describing an image source (network URI or resource) and size. */
public class ImageSource {

  private @Nullable Uri mUri;
  private String mSource;
  private double mSize;
  private boolean isResource;
  private boolean isForceCached;

  public ImageSource(Context context, String source, double width, double height, boolean forceCached) {
    mSource = source;
    mSize = width * height;
    isForceCached = forceCached;

    // Important: we compute the URI here so that we don't need to hold a reference to the context,
    // potentially causing leaks.
    mUri = computeUri(context);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImageSource that = (ImageSource) o;
    return Double.compare(that.mSize, mSize) == 0
      && isResource == that.isResource
      && Objects.equals(mUri, that.mUri)
      && Objects.equals(mSource, that.mSource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mUri, mSource, mSize, isResource);
  }

  public ImageSource(Context context, String source) {
    this(context, source, 0.0d, 0.0d, false);
  }

  /** Get the source of this image, as it was passed to the constructor. */
  public String getSource() {
    return mSource;
  }

  /** Get the URI for this image - can be either a parsed network URI or a resource URI. */
  public Uri getUri() {
    return Assertions.assertNotNull(mUri);
  }

  /** Get the area of this image. */
  public double getSize() {
    return mSize;
  }

  public boolean isForceCached() {
    return isForceCached;
  }

  /** Get whether this image source represents an Android resource or a network URI. */
  public boolean isResource() {
    return isResource;
  }

  private Uri computeUri(Context context) {
    try {
      Uri uri = Uri.parse(mSource);
      // Verify scheme is set, so that relative uri (used by static resources) are not handled.
      return uri.getScheme() == null ? computeLocalUri(context) : uri;
    } catch (Exception e) {
      return computeLocalUri(context);
    }
  }

  private Uri computeLocalUri(Context context) {
    isResource = true;
    return ResourceDrawableIdHelper.getInstance().getResourceDrawableUri(context, mSource);
  }
}

package com.facebook.react.views.imagehelper;

import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

enum PhoneDensity {
  Medium(DisplayMetrics.DENSITY_MEDIUM, "mdpi"),
  High(DisplayMetrics.DENSITY_HIGH, "hdpi"),
  XHigh(DisplayMetrics.DENSITY_XHIGH, "xhdpi"),
  XXHigh(DisplayMetrics.DENSITY_XXHIGH, "xxhdpi"),
  XXXHigh(DisplayMetrics.DENSITY_XXXHIGH, "xxxhdpi");

  int density;

  @NonNull
  String fileParentSuffix;

  PhoneDensity(
    int density,
    @NonNull String fileParentSuffix
  ) {
    this.density = density;
    this.fileParentSuffix = fileParentSuffix;
  }
}

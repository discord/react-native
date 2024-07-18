package com.facebook.react.views.text;

import android.content.res.AssetManager;

public class CreateTypefaceObject {
  public String fontFamilyName;
  public int style;
  public AssetManager assetManager;

  public CreateTypefaceObject(
    String fontFamilyName, int style, AssetManager assetManager) {
    this.fontFamilyName = fontFamilyName;
    this.style = style;
    this.assetManager = assetManager;
  }
}

/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.views.text;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.res.ResourcesCompat;
import com.facebook.infer.annotation.Nullsafe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for loading and caching Typeface objects.
 *
 * <p>This will first try to load a typeface from the assets/fonts folder. If one is not found in
 * that folder, this will fallback to the best matching system typeface.
 *
 * <p>Custom fonts support the extensions `.ttf` and `.otf` and the variants `bold`, `italic`, and
 * `bold_italic`. For example, given a font named "ExampleFontFamily", the following are supported:
 *
 * <ul>
 *   <li>ExampleFontFamily.ttf (or .otf)
 *   <li>ExampleFontFamily_bold.ttf (or .otf)
 *   <li>ExampleFontFamily_italic.ttf (or .otf)
 *   <li>ExampleFontFamily_bold_italic.ttf (or .otf)
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
public class ReactFontManager {
  public static Callback chooseHeightOverride = null;

  // NOTE: Indices in `EXTENSIONS` correspond to the `TypeFace` style constants.
  private static final String[] EXTENSIONS = {"", "_bold", "_italic", "_bold_italic"};
  private static final String[] FILE_EXTENSIONS = {".ttf", ".otf"};
  private static final String FONTS_ASSET_PATH = "fonts/";

  private static ReactFontManager sReactFontManagerInstance;

  private final Map<String, AssetFontFamily> mFontCache;
  private final Map<String, Typeface> mCustomTypefaceCache;

  private ReactFontManager() {
    mFontCache = new HashMap<>();
    mCustomTypefaceCache = new HashMap<>();
  }

  public static ReactFontManager getInstance() {
    if (sReactFontManagerInstance == null) {
      sReactFontManagerInstance = new ReactFontManager();
    }
    return sReactFontManagerInstance;
  }

  public Typeface getTypeface(String fontFamilyName, int style, AssetManager assetManager) {
    return getTypeface(fontFamilyName, new TypefaceStyle(style), assetManager);
  }

  public Typeface getTypeface(
      String fontFamilyName, int weight, boolean italic, AssetManager assetManager) {
    return getTypeface(fontFamilyName, new TypefaceStyle(weight, italic), assetManager);
  }

  public Typeface getTypeface(
      String fontFamilyName, int style, int weight, AssetManager assetManager) {
    return getTypeface(fontFamilyName, new TypefaceStyle(style, weight), assetManager);
  }

  public Typeface getTypeface(
      String fontFamilyName, TypefaceStyle typefaceStyle, AssetManager assetManager) {
    if (mCustomTypefaceCache.containsKey(fontFamilyName)) {
      // Apply `typefaceStyle` because custom fonts configure variants using `app:fontStyle` and
      // `app:fontWeight` in their resource XML configuration file.
      return typefaceStyle.apply(mCustomTypefaceCache.get(fontFamilyName));
    }

    AssetFontFamily assetFontFamily = mFontCache.get(fontFamilyName);
    if (assetFontFamily == null) {
      assetFontFamily = new AssetFontFamily();
      mFontCache.put(fontFamilyName, assetFontFamily);
    }

    int style = typefaceStyle.getNearestStyle();

    Typeface assetTypeface = assetFontFamily.getTypefaceForStyle(style);
    if (assetTypeface == null) {
      assetTypeface = createAssetTypeface(fontFamilyName, style, assetManager);
      assetFontFamily.setTypefaceForStyle(style, assetTypeface);
    }
    // Do not apply `typefaceStyle` because asset font files already incorporate the style.
    return assetTypeface;
  }

  /*
   * This method allows you to load custom fonts from res/font folder as provided font family name.
   * Fonts may be one of .ttf, .otf or XML (https://developer.android.com/guide/topics/ui/look-and-feel/fonts-in-xml).
   * To support multiple font styles or weights, you must provide a font in XML format.
   *
   * ReactFontManager.getInstance().addCustomFont(this, "Srisakdi", R.font.srisakdi);
   */
  public void addCustomFont(Context context, String fontFamily, int fontId) {
    Typeface font = ResourcesCompat.getFont(context, fontId);
    if (font != null) {
      mCustomTypefaceCache.put(fontFamily, font);
    }
  }

  /**
   * Equivalent method to {@see addCustomFont(Context, String, int)} which accepts a Typeface
   * object.
   */
  public void addCustomFont(String fontFamily, @Nullable Typeface font) {
    if (font != null) {
      mCustomTypefaceCache.put(fontFamily, font);
    }
  }

  /**
   * Add additional font family, or replace the exist one in the font memory cache.
   *
   * @param style
   * @see {@link Typeface#DEFAULT}
   * @see {@link Typeface#BOLD}
   * @see {@link Typeface#ITALIC}
   * @see {@link Typeface#BOLD_ITALIC}
   */
  public void setTypeface(String fontFamilyName, int style, Typeface typeface) {
    if (typeface != null) {
      AssetFontFamily assetFontFamily = mFontCache.get(fontFamilyName);
      if (assetFontFamily == null) {
        assetFontFamily = new AssetFontFamily();
        mFontCache.put(fontFamilyName, assetFontFamily);
      }
      assetFontFamily.setTypefaceForStyle(style, typeface);
    }
  }

  private static Typeface createAssetTypeface(
      String fontFamilyName, int style, AssetManager assetManager) {

    if (createAssetTypefaceOverride != null) {
      return createAssetTypefaceOverride.invoke(fontFamilyName, style, assetManager);
    }

    String extension = EXTENSIONS[style];
    for (String fileExtension : FILE_EXTENSIONS) {
      String fileName =
          new StringBuilder()
              .append(FONTS_ASSET_PATH)
              .append(fontFamilyName)
              .append(extension)
              .append(fileExtension)
              .toString();
      try {
        return Typeface.createFromAsset(assetManager, fileName);
      } catch (RuntimeException e) {
        // If the typeface asset does not exist, try another extension.
        continue;
      }
    }
    return Typeface.create(fontFamilyName, style);
  }

  @RequiresApi(api = Build.VERSION_CODES.Q)
  private static Typeface createAssetTypefaceWithFallbacks(
    String[] fontFamilyNames, int style, AssetManager assetManager) {
    List<FontFamily> fontFamilies = new ArrayList<>();

    // Iterate over the list of fontFamilyNames, constructing new FontFamily objects
    // for use in the CustomFallbackBuilder below.
    for (String fontFamilyName : fontFamilyNames) {
      for (String fileExtension : FILE_EXTENSIONS) {
        String fileName =
          new StringBuilder()
            .append(FONTS_ASSET_PATH)
            .append(fontFamilyName)
            .append(fileExtension)
            .toString();
        try {
          Font font = new Font.Builder(assetManager, fileName).build();
          FontFamily family = new FontFamily.Builder(font).build();
          fontFamilies.add(family);
        } catch (RuntimeException e) {
          // If the typeface asset does not exist, try another extension.
          continue;
        } catch (IOException e) {
          // If the font asset does not exist, try another extension.
          continue;
        }
      }
    }

    // If there's some problem constructing fonts, fall back to the default behavior.
    if (fontFamilies.size() == 0) {
      return createAssetTypeface(fontFamilyNames[0], style, assetManager);
    }

    Typeface.CustomFallbackBuilder fallbackBuilder = new Typeface.CustomFallbackBuilder(fontFamilies.get(0));
    for (int i = 1; i < fontFamilies.size(); i++) {
      fallbackBuilder.addCustomFallback(fontFamilies.get(i));
    }
    return fallbackBuilder.build();
  }

  /** Responsible for caching typefaces for each custom font family. */
  private static class AssetFontFamily {

    private SparseArray<Typeface> mTypefaceSparseArray;

    private AssetFontFamily() {
      mTypefaceSparseArray = new SparseArray<>(4);
    }

    public @Nullable Typeface getTypefaceForStyle(int style) {
      return mTypefaceSparseArray.get(style);
    }

    public void setTypefaceForStyle(int style, Typeface typeface) {
      mTypefaceSparseArray.put(style, typeface);
    }
  }
}

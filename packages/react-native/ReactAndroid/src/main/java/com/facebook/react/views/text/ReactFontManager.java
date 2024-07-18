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
import androidx.arch.core.util.Function;
import com.facebook.react.common.ReactConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsible for loading and caching Typeface objects.
 *
 * @deprecated This class is deprecated and it will be deleted in the near future. Please use {@link
 *     com.facebook.react.common.assets.ReactFontManager} instead.
 */
@Nullsafe(Nullsafe.Mode.LOCAL)
@Deprecated
public class ReactFontManager {
  public static Function<CreateTypefaceObject, Typeface> createAssetTypefaceOverride = null;

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
      sReactFontManagerInstance =
        new ReactFontManager();
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
      return createAssetTypefaceOverride.apply(
        new CreateTypefaceObject(fontFamilyName, style, assetManager)
      );
    }

    // This is the original RN logic for getting the typeface.
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

  public static class TypefaceStyle {

    public static final int BOLD = 700;
    public static final int NORMAL = 400;
    private static final int MIN_WEIGHT = 1;
    private static final int MAX_WEIGHT = 1000;

    private final boolean mItalic;
    private final int mWeight;

    public TypefaceStyle(int weight, boolean italic) {
      mItalic = italic;
      mWeight = weight == ReactConstants.UNSET ? NORMAL : weight;
    }

    public TypefaceStyle(int style) {
      if (style == ReactConstants.UNSET) {
        style = Typeface.NORMAL;
      }

      mItalic = (style & Typeface.ITALIC) != 0;
      mWeight = (style & Typeface.BOLD) != 0 ? BOLD : NORMAL;
    }

    /**
     * If `weight` is supplied, it will be combined with the italic bit from `style`. Otherwise, any
     * existing weight bit in `style` will be used.
     */
    public TypefaceStyle(int style, int weight) {
      if (style == ReactConstants.UNSET) {
        style = Typeface.NORMAL;
      }

      mItalic = (style & Typeface.ITALIC) != 0;
      mWeight =
        weight == ReactConstants.UNSET ? (style & Typeface.BOLD) != 0 ? BOLD : NORMAL : weight;
    }

    public int getNearestStyle() {
      if (mWeight < BOLD) {
        return mItalic ? Typeface.ITALIC : Typeface.NORMAL;
      } else {
        return mItalic ? Typeface.BOLD_ITALIC : Typeface.BOLD;
      }
    }

    public Typeface apply(Typeface typeface) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        return Typeface.create(typeface, getNearestStyle());
      } else {
        return Typeface.create(typeface, mWeight, mItalic);
      }
    }
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

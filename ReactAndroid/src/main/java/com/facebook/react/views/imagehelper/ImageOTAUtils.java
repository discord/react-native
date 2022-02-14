package com.facebook.react.views.imagehelper;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

public class ImageOTAUtils {
  private static final ConcurrentHashMap<String, String> mResourceCacheMap = new ConcurrentHashMap<>();
  private static final String CACHE_DRAWABLE_DIRECTORY_SCHEME = "otas/app/src/main/res";
  private static final String[] RESOURCE_EXTENSIONS = {
    "xml",
    "png",
    "svg",
    "jpg"
  };

  private static int densityDpi;

  @Nullable
  static Drawable getResourceDrawable(Context context, @Nullable String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }

    name = sanitizeResourceDrawableId(name);

    // Checks to see if we have an ota version of the file, otherwise default to normal behavior.
    File otaFile = getDrawableFileFromCache(context, name);
    if (otaFile != null) {
      return Drawable.createFromPath(otaFile.getAbsolutePath());
    }

    return null;
  }

  @Nullable
  static Uri getResourceUri(Context context, @Nullable String name) {
    if (name == null || name.isEmpty()) {
      return Uri.EMPTY;
    }

    name = sanitizeResourceDrawableId(name);

    // Checks to see if we have an ota version of the file, otherwise default to normal behavior.
    File otaFile = ImageOTAUtils.getDrawableFileFromCache(context, name);
    if (otaFile != null) {
      return Uri.fromFile(otaFile);
    }

    return null;
  }

  /**
   * Checks the cache to see if there is a drawable file downloaded via OTA.
   */
  @Nullable
  private static File getDrawableFileFromCache(Context context, @Nullable String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return null;
    }

    String cacheMapFileName = mResourceCacheMap.get(fileName);

    // Check the cache to see if we've already looked up the file before.
    if (cacheMapFileName != null) {
      return new File(cacheMapFileName);
    }

    File file = null;
    int densityDpi = getDensityDpi(context);
    PhoneDensity[] phoneDensities = PhoneDensity.values();

    // We start from the medium dpi and go up.
    for (PhoneDensity phoneDensity : phoneDensities) {
      String drawableFileParent = String.format("drawable-%s", phoneDensity.fileParentSuffix);
      String mipMapFileParent = String.format("mipmap-%s", phoneDensity.fileParentSuffix);

      String[] parentFileNames = { drawableFileParent, mipMapFileParent };

      File resourceFile = checkFiles(context, parentFileNames, fileName);

      if (resourceFile != null) {
        file = resourceFile;
      }

      // If we've found a file at our current dpi level, return it.
      // Otherwise continue looking up the chain.
      if (densityDpi <= phoneDensity.density) {
        if (file != null) {
          mResourceCacheMap.put(fileName, file.getAbsolutePath());
          return file;
        }
      }
    }

    // As a last resort, check the drawable/raw folders.
    String[] parentFileNames = { "drawable", "raw" };
    file = checkFiles(context, parentFileNames, fileName);

    if (file != null) {
      mResourceCacheMap.put(fileName, file.getAbsolutePath());
    }

    return file;
  }

  /**
   * Given a list of files, check if any of them exist.
   * Checks multiple extension types.
   */
  private static File checkFiles(Context context, String[] parentFileNames, String fileName) {
    for(String parentFileName : parentFileNames) {
      for (String extension : RESOURCE_EXTENSIONS) {
        File file = getFile(context, parentFileName, fileName, extension);
        if (file.exists()) {
          return file;
        }
      }
    }

    return null;
  }

  /**
   * Returns a file object with the correct directory extensions.
   */
  private static File getFile(
    Context context,
    String parentFileName,
    String fileName,
    String extension
  ) {
    String fullDrawableFileName = String.format(
      "%s/%s/%s.%s",
      CACHE_DRAWABLE_DIRECTORY_SCHEME,
      parentFileName,
      fileName,
      extension
    );

    return new File(context.getCacheDir(), fullDrawableFileName);
  }

  /**
   * Returns the density dpi for the device.
   */
  private static int getDensityDpi(Context context) {
    // Cache this so we only have to do this once.
    if (densityDpi == 0) {
      DisplayMetrics metrics = new DisplayMetrics();

      WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      windowManager.getDefaultDisplay().getMetrics(metrics);

      densityDpi = metrics.densityDpi;
    }

    return densityDpi;
  }

  private static String sanitizeResourceDrawableId(@NonNull String name) {
    return name.toLowerCase().replace("-", "_");
  }
}

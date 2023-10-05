package com.facebook.react.animated;

import android.util.Log;
import android.view.View;

import com.facebook.fbreact.specs.NativeFabricMeasurerTurboModuleSpec;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.UIManager;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.uimanager.NativeViewMeasurer;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.UIManagerHelper;
import com.facebook.react.uimanager.common.UIManagerType;

@ReactModule(name = NativeFabricMeasurerTurboModuleSpec.NAME)
public class NativeFabricMeasurerModule extends NativeFabricMeasurerTurboModuleSpec implements NativeViewMeasurer.ViewProvider {
  private final NativeViewMeasurer measurer = new NativeViewMeasurer(this);

  public NativeFabricMeasurerModule(ReactApplicationContext reactContext) {
    super(reactContext);
  }

  @Override
  public void measureNatively(double viewTag, Callback callback) {
    getReactApplicationContext().runOnUiQueueThread(() -> {
      int[] output = measurer.measure((int) viewTag);
      float x = PixelUtil.toDIPFromPixel(output[0]);
      float y = PixelUtil.toDIPFromPixel(output[1]);
      float width = PixelUtil.toDIPFromPixel(output[2]);
      float height = PixelUtil.toDIPFromPixel(output[3]);
      callback.invoke(0, 0, width, height, x, y);
    });
  }

  @Override
  public void measureInWindowNatively(double viewTag, Callback callback) {
    getReactApplicationContext().runOnUiQueueThread(() -> {
      int[] output = measurer.measureInWindow((int) viewTag);
      float x = PixelUtil.toDIPFromPixel(output[0]);
      float y = PixelUtil.toDIPFromPixel(output[1]);
      float width = PixelUtil.toDIPFromPixel(output[2]);
      float height = PixelUtil.toDIPFromPixel(output[3]);
      callback.invoke(x, y, width, height);
    });
  }

  @Override
  public View provideView(int tag) {
    UIManager uiManager = UIManagerHelper.getUIManager(getReactApplicationContext(), UIManagerType.FABRIC);
    if (uiManager == null) {
      return null;
    }

    return uiManager.resolveView(tag);
  }
}

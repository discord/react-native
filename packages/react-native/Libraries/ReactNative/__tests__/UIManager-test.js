/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow strict-local
 * @format
 */

'use strict';

jest.mock('../../Utilities/Platform', () => ({
  OS: 'android',
  select: spec => spec.android ?? spec.native ?? spec.default,
}));
jest.unmock('../UIManager');

describe('UIManager', () => {
  const originalBridgelessValue = global.RN$Bridgeless;
  const originalUIManagerOptimization = global.RN$discordUiManagerOptimization;
  const originalGetConstants = global.RN$LegacyInterop_UIManager_getConstants;
  const originalGetConstantsForViewManager =
    global.RN$LegacyInterop_UIManager_getConstantsForViewManager;
  const originalGetDefaultEventTypes =
    global.RN$LegacyInterop_UIManager_getDefaultEventTypes;
  const viewManagerConfig = {Commands: {focus: 1}, NativeProps: {}};
  const getConstants = jest.fn(() => ({
    LazyViewManagersEnabled: true,
    ViewManagerNames: ['RCTTestView'],
  }));
  const getConstantsForViewManager = jest.fn(() => viewManagerConfig);
  const getDefaultEventTypes = jest.fn(() => ({}));

  beforeEach(() => {
    jest.resetModules();
    getConstants.mockClear();
    getConstantsForViewManager.mockClear();
    getDefaultEventTypes.mockClear();

    // $FlowExpectedError[cannot-write]
    global.RN$Bridgeless = true;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstants = getConstants;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstantsForViewManager =
      getConstantsForViewManager;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getDefaultEventTypes =
      getDefaultEventTypes;
  });

  afterEach(() => {
    // $FlowExpectedError[cannot-write]
    global.RN$Bridgeless = originalBridgelessValue;
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerOptimization = originalUIManagerOptimization;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstants = originalGetConstants;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstantsForViewManager =
      originalGetConstantsForViewManager;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getDefaultEventTypes =
      originalGetDefaultEventTypes;
    jest.resetModules();
  });

  it('preserves lazy ViewManager getters in optimization mode 1', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerOptimization = 1;

    const UIManager = require('../UIManager').default;

    expect(getConstants).toHaveBeenCalledTimes(1);
    expect(getConstantsForViewManager).not.toHaveBeenCalled();
    expect(
      Object.getOwnPropertyDescriptor(UIManager, 'RCTTestView'),
    ).toBeDefined();
    expect(Object.keys(UIManager)).toContain('RCTTestView');

    expect(Reflect.get(UIManager, 'RCTTestView')).toBe(viewManagerConfig);
    expect(Reflect.get(UIManager, 'RCTTestView')).toBe(viewManagerConfig);
    expect(getConstantsForViewManager).toHaveBeenCalledTimes(1);
  });

  it('eagerly reads ViewManager getters in optimization mode 0', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerOptimization = 0;

    const UIManager = require('../UIManager').default;

    expect(getConstants).toHaveBeenCalledTimes(1);
    expect(getConstantsForViewManager).toHaveBeenCalledTimes(1);
    expect(Reflect.get(UIManager, 'RCTTestView')).toBe(viewManagerConfig);
  });

  it('loads and caches individual ViewManager configs on demand in optimization mode 2', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerOptimization = 2;

    const UIManager = require('../UIManager').default;

    expect(getConstants).not.toHaveBeenCalled();
    expect(getConstantsForViewManager).not.toHaveBeenCalled();

    expect(UIManager.getViewManagerConfig('RCTTestView')).toBe(
      viewManagerConfig,
    );
    expect(UIManager.getViewManagerConfig('RCTTestView')).toBe(
      viewManagerConfig,
    );
    expect(getConstantsForViewManager).toHaveBeenCalledTimes(1);

    expect(UIManager.getConstants()).toEqual({
      LazyViewManagersEnabled: true,
      ViewManagerNames: ['RCTTestView'],
    });
    expect(getConstants).toHaveBeenCalledTimes(1);
  });

  it('does not load all constants when attaching default event types in optimization mode 2', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerOptimization = 2;

    const getNativeComponentAttributes =
      require('../getNativeComponentAttributes').default;
    getNativeComponentAttributes('RCTTestView');

    expect(getConstants).not.toHaveBeenCalled();
    expect(getConstantsForViewManager).toHaveBeenCalledTimes(1);
    expect(getDefaultEventTypes).toHaveBeenCalledTimes(1);
  });
});

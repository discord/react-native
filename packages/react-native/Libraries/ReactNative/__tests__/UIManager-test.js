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
  const originalLazyPropertiesOptimization =
    global.RN$discordUiManagerLazyPropertiesOptimization;
  const originalGetConstants = global.RN$LegacyInterop_UIManager_getConstants;
  const originalGetConstantsForViewManager =
    global.RN$LegacyInterop_UIManager_getConstantsForViewManager;
  const originalGetDefaultEventTypes =
    global.RN$LegacyInterop_UIManager_getDefaultEventTypes;
  const viewManagerConfig = {Commands: {focus: 1}};
  const getConstants = jest.fn(() => ({
    LazyViewManagersEnabled: true,
    ViewManagerNames: ['RCTTestView'],
  }));
  const getConstantsForViewManager = jest.fn(() => viewManagerConfig);

  beforeEach(() => {
    jest.resetModules();
    getConstants.mockClear();
    getConstantsForViewManager.mockClear();

    // $FlowExpectedError[cannot-write]
    global.RN$Bridgeless = true;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstants = getConstants;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getConstantsForViewManager =
      getConstantsForViewManager;
    // $FlowExpectedError[cannot-write]
    global.RN$LegacyInterop_UIManager_getDefaultEventTypes = jest.fn(
      () => ({}),
    );
  });

  afterEach(() => {
    // $FlowExpectedError[cannot-write]
    global.RN$Bridgeless = originalBridgelessValue;
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerLazyPropertiesOptimization =
      originalLazyPropertiesOptimization;
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

  it('preserves lazy ViewManager getters when composing the Bridgeless implementation', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerLazyPropertiesOptimization = true;

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

  it('eagerly reads ViewManager getters when the optimization is disabled', () => {
    // $FlowExpectedError[cannot-write]
    global.RN$discordUiManagerLazyPropertiesOptimization = false;

    const UIManager = require('../UIManager').default;

    expect(getConstants).toHaveBeenCalledTimes(1);
    expect(getConstantsForViewManager).toHaveBeenCalledTimes(1);
    expect(Reflect.get(UIManager, 'RCTTestView')).toBe(viewManagerConfig);
  });
});

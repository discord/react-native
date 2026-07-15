/**
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * @flow strict-local
 * @format
 */

import type {RNTesterModuleExample} from '../../types/RNTesterTypes';

import * as React from 'react';
import {useEffect, useRef, useState} from 'react';
import {
  Animated,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  enableIOSBorderBoxShadowBackdrop,
  enableIOSBorderBoxShadowPathByDefault,
} from 'react-native/src/private/featureflags/ReactNativeFeatureFlags';

type ShadowPathMode = 'auto' | 'border-box' | 'content-alpha';
type ShadowPathSelection = 'default' | 'box-shadow' | ShadowPathMode;
type AnimationMode = 'off' | 'scale' | 'bounds' | 'content';
type BackdropScenario =
  | 'eligible'
  | 'translucent-ancestor'
  | 'earlier-sibling'
  | 'unknown-ancestor'
  | 'rounded-visible'
  | 'rounded-clipped';

const SHADOW_PATH_OPTIONS: $ReadOnlyArray<{
  label: string,
  value: ShadowPathSelection,
}> = [
  {label: 'Default', value: 'default'},
  {label: 'Auto', value: 'auto'},
  {label: 'Border Box', value: 'border-box'},
  {label: 'Content Alpha', value: 'content-alpha'},
  {label: 'Box Shadow', value: 'box-shadow'},
];

const ANIMATION_OPTIONS: $ReadOnlyArray<{
  label: string,
  value: AnimationMode,
}> = [
  {label: 'Off', value: 'off'},
  {label: 'Scale', value: 'scale'},
  {label: 'Bounds', value: 'bounds'},
  {label: 'Content', value: 'content'},
];

const BACKDROP_SCENARIOS: $ReadOnlyArray<{
  label: string,
  value: BackdropScenario,
}> = [
  {label: 'Eligible', value: 'eligible'},
  {label: 'Translucent', value: 'translucent-ancestor'},
  {label: 'Sibling', value: 'earlier-sibling'},
  {label: 'Unknown', value: 'unknown-ancestor'},
  {label: 'Rounded', value: 'rounded-visible'},
  {label: 'Clipped', value: 'rounded-clipped'},
];

function ShadowPathPlayground(): React.Node {
  const [selectedMode, setSelectedMode] =
    useState<ShadowPathSelection>('box-shadow');
  const [animationMode, setAnimationMode] = useState<AnimationMode>('off');
  const [backdropScenario, setBackdropScenario] =
    useState<BackdropScenario>('eligible');
  // Keep layout and native-driven animations on separate values. Animated
  // values cannot safely switch between the JS and native drivers.
  const nativeAnimationProgressRef = useRef<?Animated.Value>(null);
  if (nativeAnimationProgressRef.current == null) {
    nativeAnimationProgressRef.current = new Animated.Value(0);
  }
  const nativeAnimationProgress = nativeAnimationProgressRef.current;
  const boundsAnimationProgressRef = useRef<?Animated.Value>(null);
  if (boundsAnimationProgressRef.current == null) {
    boundsAnimationProgressRef.current = new Animated.Value(0);
  }
  const boundsAnimationProgress = boundsAnimationProgressRef.current;

  useEffect(() => {
    nativeAnimationProgress.stopAnimation();
    nativeAnimationProgress.setValue(0);
    boundsAnimationProgress.stopAnimation();
    boundsAnimationProgress.setValue(0);

    if (animationMode === 'off') {
      return;
    }

    // Bounds cannot use the native driver because it intentionally runs
    // layout on every frame to invalidate the shadow host's silhouette.
    let animationProgress = nativeAnimationProgress;
    let useNativeDriver = true;
    if (animationMode === 'bounds') {
      animationProgress = boundsAnimationProgress;
      useNativeDriver = false;
    }

    const growAnimation = Animated.timing(animationProgress, {
      duration: 700,
      toValue: 1,
      useNativeDriver,
    });
    const shrinkAnimation = Animated.timing(animationProgress, {
      duration: 700,
      toValue: 0,
      useNativeDriver,
    });
    const animationSequence = Animated.sequence([
      growAnimation,
      shrinkAnimation,
    ]);
    const animation = Animated.loop(animationSequence);
    animation.start();

    return () => {
      animation.stop();
    };
  }, [animationMode, boundsAnimationProgress, nativeAnimationProgress]);

  const scale = nativeAnimationProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.12],
  });
  const animatedWidth = boundsAnimationProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [160, 220],
  });
  const contentTranslation = nativeAnimationProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [0, 24],
  });
  // Keep the two shadow APIs mutually exclusive so profiling the Box Shadow
  // mode cannot also measure legacy shadow work.
  const usesBoxShadow = selectedMode === 'box-shadow';
  let shadowPathProps: {shadowPathIOS?: ShadowPathMode} = {};
  if (selectedMode !== 'default' && selectedMode !== 'box-shadow') {
    shadowPathProps = {shadowPathIOS: selectedMode};
  }
  const flagEnabled = enableIOSBorderBoxShadowPathByDefault();
  const flagDefault = flagEnabled ? 'Border Box' : 'Auto';
  const backdropEnabled = enableIOSBorderBoxShadowBackdrop();
  const activeMode = SHADOW_PATH_OPTIONS.find(
    option => option.value === selectedMode,
  );
  const activeAnimation = ANIMATION_OPTIONS.find(
    option => option.value === animationMode,
  );
  const activeBackdropScenario = BACKDROP_SCENARIOS.find(
    option => option.value === backdropScenario,
  );

  if (activeMode == null) {
    throw new Error('Selected shadow path mode must have a label.');
  }
  if (activeAnimation == null) {
    throw new Error('Selected animation mode must have a label.');
  }
  if (activeBackdropScenario == null) {
    throw new Error('Selected backdrop scenario must have a label.');
  }

  const renderButtonShadowHost = (): React.Node => (
    <Animated.View
      {...shadowPathProps}
      style={[
        styles.buttonShadowHost,
        usesBoxShadow ? styles.buttonBoxShadow : styles.buttonLegacyShadow,
        animationMode === 'scale' ? {transform: [{scale}]} : null,
        animationMode === 'bounds' ? {width: animatedWidth} : null,
      ]}>
      <View style={styles.translucentButton}>
        <Text style={styles.buttonLabel}>Translucent button</Text>
      </View>
    </Animated.View>
  );

  return (
    <View style={styles.container}>
      <View accessibilityRole="radiogroup" style={styles.segmentedControl}>
        {SHADOW_PATH_OPTIONS.map(option => {
          const selected = option.value === selectedMode;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{checked: selected}}
              key={option.value}
              onPress={() => setSelectedMode(option.value)}
              style={[
                styles.segment,
                selected ? styles.selectedSegment : null,
              ]}>
              <Text
                style={[
                  styles.segmentLabel,
                  selected ? styles.selectedSegmentLabel : null,
                ]}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <Text style={styles.label}>Animation workload</Text>
      <View accessibilityRole="radiogroup" style={styles.segmentedControl}>
        {ANIMATION_OPTIONS.map(option => {
          const selected = option.value === animationMode;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{checked: selected}}
              key={option.value}
              onPress={() => setAnimationMode(option.value)}
              style={[
                styles.segment,
                selected ? styles.selectedSegment : null,
              ]}>
              <Text
                style={[
                  styles.segmentLabel,
                  selected ? styles.selectedSegmentLabel : null,
                ]}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <Text style={styles.status}>Active mode: {activeMode.label}</Text>
      <Text style={styles.status}>Feature-flag default: {flagDefault}</Text>
      <Text style={styles.status}>Animation: {activeAnimation.label}</Text>
      <Text style={styles.status}>
        Backplate: {backdropEnabled ? 'Enabled' : 'Disabled'}
      </Text>
      <Text style={styles.explanation}>
        In Instruments, inspect the ShadowBackdrop::propagate trace for
        visited-node and eligibility counts.
      </Text>
      <Text style={styles.explanation}>
        Scale exercises cached compositing. Bounds repeatedly changes the
        button's silhouette. Content moves a child within the irregular shadow
        host.
      </Text>

      <Text style={styles.heading}>Backdrop proof scenario</Text>
      <View accessibilityRole="radiogroup" style={styles.segmentedControl}>
        {BACKDROP_SCENARIOS.map(option => {
          const selected = option.value === backdropScenario;
          return (
            <Pressable
              accessibilityRole="radio"
              accessibilityState={{checked: selected}}
              key={option.value}
              onPress={() => setBackdropScenario(option.value)}
              style={[
                styles.segment,
                selected ? styles.selectedSegment : null,
              ]}>
              <Text
                style={[
                  styles.segmentLabel,
                  selected ? styles.selectedSegmentLabel : null,
                ]}>
                {option.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <Text style={styles.status}>
        Scenario: {activeBackdropScenario.label}
      </Text>
      <View style={styles.sampleStage}>
        {backdropScenario === 'eligible' ? (
          <View style={styles.opaqueBackdropProvider}>
            {renderButtonShadowHost()}
          </View>
        ) : null}
        {backdropScenario === 'translucent-ancestor' ? (
          <View style={styles.opaqueBackdropProvider}>
            <View style={styles.translucentAncestorBarrier}>
              {renderButtonShadowHost()}
            </View>
          </View>
        ) : null}
        {backdropScenario === 'earlier-sibling' ? (
          <View style={styles.opaqueBackdropProvider}>
            <View style={styles.earlierSiblingBarrier} />
            {renderButtonShadowHost()}
          </View>
        ) : null}
        {backdropScenario === 'unknown-ancestor' ? (
          <ScrollView
            contentContainerStyle={styles.unknownAncestorContent}
            scrollEnabled={false}
            style={styles.unknownAncestorBarrier}>
            {renderButtonShadowHost()}
          </ScrollView>
        ) : null}
        {backdropScenario === 'rounded-visible' ? (
          <View style={styles.roundedVisibleBarrier}>
            {renderButtonShadowHost()}
          </View>
        ) : null}
        {backdropScenario === 'rounded-clipped' ? (
          <View style={styles.roundedClippedProvider}>
            {renderButtonShadowHost()}
          </View>
        ) : null}
      </View>

      <Text style={styles.heading}>Irregular composited content</Text>
      <Text style={styles.explanation}>
        Content Alpha follows the circles. Border Box intentionally shadows the
        host bounds instead.
      </Text>
      <View style={styles.sampleStage}>
        <Animated.View
          {...shadowPathProps}
          style={[
            styles.irregularShadowHost,
            usesBoxShadow
              ? styles.irregularBoxShadow
              : styles.irregularLegacyShadow,
            animationMode === 'scale' ? {transform: [{scale}]} : null,
          ]}>
          <View style={[styles.circle, styles.leftCircle]} />
          <View style={[styles.circle, styles.centerCircle]} />
          <Animated.View
            style={[
              styles.circle,
              styles.rightCircle,
              animationMode === 'content'
                ? {transform: [{translateX: contentTranslation}]}
                : null,
            ]}
          />
        </Animated.View>
      </View>

      <Text style={styles.heading}>Structural stress cases</Text>
      <Text style={styles.explanation}>
        The left column has deep transparent wrappers. The right column has a
        dense list where every item is a shadow host.
      </Text>
      <View style={styles.stressStage}>
        <View style={styles.deepWrapperOne}>
          <View style={styles.deepWrapperTwo}>
            <View style={styles.deepWrapperThree}>
              <View
                {...shadowPathProps}
                style={[
                  styles.stressShadowHost,
                  usesBoxShadow
                    ? styles.stressBoxShadow
                    : styles.stressLegacyShadow,
                ]}>
                <View style={styles.stressTranslucentContent}>
                  <Text style={styles.stressLabel}>Deep wrapper</Text>
                </View>
              </View>
            </View>
          </View>
        </View>
        <View style={styles.denseList}>
          {[0, 1, 2, 3].map(index => (
            <View
              {...shadowPathProps}
              key={index}
              style={[
                styles.stressShadowHost,
                usesBoxShadow
                  ? styles.stressBoxShadow
                  : styles.stressLegacyShadow,
              ]}>
              <View style={styles.stressTranslucentContent}>
                <Text style={styles.stressLabel}>List item {index + 1}</Text>
              </View>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 12,
    padding: 16,
  },
  segmentedControl: {
    borderColor: '#8e8e93',
    borderRadius: 8,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
  },
  segment: {
    alignItems: 'center',
    borderColor: '#8e8e93',
    borderRightWidth: StyleSheet.hairlineWidth,
    flex: 1,
    justifyContent: 'center',
    minHeight: 38,
    paddingHorizontal: 4,
  },
  selectedSegment: {
    backgroundColor: '#0a84ff',
  },
  segmentLabel: {
    color: '#3c3c43',
    fontSize: 12,
    textAlign: 'center',
  },
  selectedSegmentLabel: {
    color: 'white',
    fontWeight: '600',
  },
  label: {
    fontSize: 16,
  },
  status: {
    color: '#636366',
    fontVariant: ['tabular-nums'],
  },
  heading: {
    fontSize: 17,
    fontWeight: '600',
    marginTop: 8,
  },
  explanation: {
    color: '#636366',
    lineHeight: 19,
  },
  sampleStage: {
    alignItems: 'center',
    backgroundColor: '#d1d1d6',
    justifyContent: 'center',
    minHeight: 140,
    overflow: 'visible',
  },
  opaqueBackdropProvider: {
    alignItems: 'center',
    backgroundColor: '#6750a4',
    justifyContent: 'center',
    minHeight: 100,
    minWidth: 260,
  },
  translucentAncestorBarrier: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    justifyContent: 'center',
    minHeight: 100,
    minWidth: 260,
  },
  earlierSiblingBarrier: {
    backgroundColor: '#ff453a',
    height: 64,
    position: 'absolute',
    width: 190,
  },
  unknownAncestorBarrier: {
    backgroundColor: '#6750a4',
    maxHeight: 100,
    minWidth: 260,
  },
  unknownAncestorContent: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 100,
  },
  roundedVisibleBarrier: {
    alignItems: 'center',
    backgroundColor: '#6750a4',
    borderRadius: 24,
    justifyContent: 'center',
    minHeight: 100,
    minWidth: 260,
  },
  roundedClippedProvider: {
    alignItems: 'center',
    backgroundColor: '#6750a4',
    borderRadius: 24,
    justifyContent: 'center',
    minHeight: 100,
    minWidth: 260,
    overflow: 'hidden',
  },
  buttonShadowHost: {
    borderRadius: 8,
    width: 160,
  },
  buttonLegacyShadow: {
    shadowColor: 'black',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.14,
    shadowRadius: 4,
  },
  buttonBoxShadow: {
    boxShadow: '0px 1px 8px rgba(0, 0, 0, 0.14)',
  },
  translucentButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 40,
    minWidth: 160,
    overflow: 'hidden',
    width: '100%',
  },
  buttonLabel: {
    color: '#1c1c1e',
    fontWeight: '600',
  },
  irregularShadowHost: {
    height: 72,
    position: 'relative',
    width: 160,
  },
  irregularLegacyShadow: {
    shadowColor: 'black',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.25,
    shadowRadius: 4,
  },
  irregularBoxShadow: {
    boxShadow: '0px 1px 8px rgba(0, 0, 0, 0.25)',
  },
  circle: {
    backgroundColor: '#ff9f0a',
    borderRadius: 28,
    height: 56,
    position: 'absolute',
    top: 8,
    width: 56,
  },
  leftCircle: {
    left: 8,
  },
  centerCircle: {
    backgroundColor: '#ff375f',
    left: 52,
  },
  rightCircle: {
    backgroundColor: '#bf5af2',
    left: 96,
  },
  stressStage: {
    backgroundColor: '#6750a4',
    gap: 12,
    padding: 16,
  },
  deepWrapperOne: {
    padding: 2,
  },
  deepWrapperTwo: {
    padding: 2,
  },
  deepWrapperThree: {
    padding: 2,
  },
  denseList: {
    gap: 8,
  },
  stressShadowHost: {
    borderRadius: 8,
    width: 160,
  },
  stressLegacyShadow: {
    shadowColor: 'black',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.14,
    shadowRadius: 4,
  },
  stressBoxShadow: {
    boxShadow: '0px 1px 8px rgba(0, 0, 0, 0.14)',
  },
  stressTranslucentContent: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 32,
  },
  stressLabel: {
    color: '#1c1c1e',
    fontSize: 12,
  },
});

export default ({
  title: 'iOS Shadow Path',
  name: 'ios-shadow-path',
  description:
    'Compares legacy iOS shadow path modes and their dynamic-shadow behavior.',
  platform: 'ios',
  render: (): React.Node => <ShadowPathPlayground />,
}: RNTesterModuleExample);

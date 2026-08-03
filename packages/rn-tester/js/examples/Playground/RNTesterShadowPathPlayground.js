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
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

type ShadowPathMode = 'auto' | 'border-box' | 'content-alpha';
type ShadowPathSelection = 'default' | ShadowPathMode;

const SHADOW_PATH_OPTIONS: $ReadOnlyArray<{
  label: string,
  value: ShadowPathSelection,
}> = [
  {label: 'Default', value: 'default'},
  {label: 'Auto', value: 'auto'},
  {label: 'Border Box', value: 'border-box'},
  {label: 'Content Alpha', value: 'content-alpha'},
];

function ShadowPathPlayground(): React.Node {
  const [selectedMode, setSelectedMode] =
    useState<ShadowPathSelection>('default');
  const [animationEnabled, setAnimationEnabled] = useState(false);
  const animationProgressRef = useRef<?Animated.Value>(null);
  if (animationProgressRef.current == null) {
    animationProgressRef.current = new Animated.Value(0);
  }
  const animationProgress = animationProgressRef.current;

  useEffect(() => {
    if (!animationEnabled) {
      animationProgress.stopAnimation();
      animationProgress.setValue(0);
      return;
    }

    const growAnimation = Animated.timing(animationProgress, {
      duration: 700,
      toValue: 1,
      useNativeDriver: true,
    });
    const shrinkAnimation = Animated.timing(animationProgress, {
      duration: 700,
      toValue: 0,
      useNativeDriver: true,
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
  }, [animationEnabled, animationProgress]);

  const scale = animationProgress.interpolate({
    inputRange: [0, 1],
    outputRange: [1, 1.12],
  });
  const animatedStyle = {transform: [{scale}]};
  const shadowPathProps: {shadowPathIOS?: ShadowPathMode} =
    selectedMode === 'default' ? {} : {shadowPathIOS: selectedMode};
  const activeMode = SHADOW_PATH_OPTIONS.find(
    option => option.value === selectedMode,
  );

  if (activeMode == null) {
    throw new Error('Selected shadow path mode must have a label.');
  }

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

      <View style={styles.animationRow}>
        <Text style={styles.label}>Scale animation</Text>
        <Switch onValueChange={setAnimationEnabled} value={animationEnabled} />
      </View>

      <Text style={styles.status}>Active mode: {activeMode.label}</Text>
      <Text style={styles.status}>Omitted default: Auto</Text>

      <Text style={styles.heading}>Translucent button hierarchy</Text>
      <View style={styles.sampleStage}>
        <Animated.View
          {...shadowPathProps}
          style={[styles.buttonShadowHost, animatedStyle]}>
          <View style={styles.translucentButton}>
            <Text style={styles.buttonLabel}>Translucent button</Text>
          </View>
        </Animated.View>
      </View>

      <Text style={styles.heading}>Irregular composited content</Text>
      <Text style={styles.explanation}>
        Content Alpha follows the circles. Border Box intentionally shadows the
        host bounds instead.
      </Text>
      <View style={styles.sampleStage}>
        <Animated.View
          {...shadowPathProps}
          style={[styles.irregularShadowHost, animatedStyle]}>
          <View style={[styles.circle, styles.leftCircle]} />
          <View style={[styles.circle, styles.centerCircle]} />
          <View style={[styles.circle, styles.rightCircle]} />
        </Animated.View>
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
    overflow: 'hidden',
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
  animationRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
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
  buttonShadowHost: {
    borderRadius: 8,
    shadowColor: 'black',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.14,
    shadowRadius: 4,
  },
  translucentButton: {
    alignItems: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.12)',
    borderRadius: 8,
    justifyContent: 'center',
    minHeight: 40,
    minWidth: 160,
    overflow: 'hidden',
  },
  buttonLabel: {
    color: '#1c1c1e',
    fontWeight: '600',
  },
  irregularShadowHost: {
    height: 72,
    position: 'relative',
    shadowColor: 'black',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.25,
    shadowRadius: 4,
    width: 160,
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
});

export default ({
  title: 'iOS Shadow Path',
  name: 'ios-shadow-path',
  description:
    'Compares legacy iOS shadow path modes and their dynamic-shadow behavior.',
  platform: 'ios',
  render: (): React.Node => <ShadowPathPlayground />,
}: RNTesterModuleExample);

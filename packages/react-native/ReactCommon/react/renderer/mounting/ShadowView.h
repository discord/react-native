/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#pragma once

#include <react/renderer/components/view/ViewProps.h>
#include <react/renderer/core/EventEmitter.h>
#include <react/renderer/core/LayoutMetrics.h>
#include <react/renderer/core/Props.h>
#include <react/renderer/core/ReactPrimitives.h>
#include <react/renderer/core/ShadowNode.h>
#include <react/renderer/debug/flags.h>
#include <react/utils/hash_combine.h>

namespace facebook::react {

enum class ShadowBackdropHostKind : uint8_t {
  Root,
  View,
  Unknown,
};

enum class ShadowBackdropKind : uint8_t {
  None,
  Provider,
  Barrier,
};

enum class ShadowBackdropReason : uint8_t {
  None,
  EarlierSibling,
  TranslucentAncestor,
  UnknownAncestor,
  VisualEffect,
  RoundedAncestor,
  OutsideProvider,
  DynamicColor,
};

struct ShadowBackdrop final {
  ShadowBackdropKind kind{ShadowBackdropKind::None};
  ShadowBackdropReason reason{ShadowBackdropReason::None};
  SharedColor color{};
  Rect coverage{};
  Tag providerTag{-1};
  Tag barrierTag{-1};

  bool operator==(const ShadowBackdrop& other) const = default;
};

/*
 * Rendering context derived from the complete, laid-out Fabric tree before
 * diffing. Component views do not receive it as a prop because it depends on
 * their mounted surroundings.
 */
struct ShadowViewEnvironment final {
  ShadowBackdrop shadowBackdrop{};

  bool operator==(const ShadowViewEnvironment& other) const = default;
};

inline bool shadowBackdropContainsRect(const Rect& outer, const Rect& inner) {
  return inner.getMinX() >= outer.getMinX() &&
      inner.getMaxX() <= outer.getMaxX() &&
      inner.getMinY() >= outer.getMinY() &&
      inner.getMaxY() <= outer.getMaxY();
}

inline bool shadowBackdropHasRoundedCorners(const BorderMetrics& borderMetrics) {
  const auto& radii = borderMetrics.borderRadii;
  return radii.topLeft.vertical != 0 || radii.topLeft.horizontal != 0 ||
      radii.topRight.vertical != 0 || radii.topRight.horizontal != 0 ||
      radii.bottomLeft.vertical != 0 || radii.bottomLeft.horizontal != 0 ||
      radii.bottomRight.vertical != 0 || radii.bottomRight.horizontal != 0;
}

inline bool shadowBackdropHasVisualEffect(const ViewProps& props) {
  return !props.boxShadow.empty() || !props.filter.empty() ||
      !props.backgroundImage.empty() || props.mixBlendMode != BlendMode::Normal ||
      props.outlineWidth != 0 || isColorMeaningful(props.shadowColor);
}

inline ShadowBackdrop shadowBackdropBarrier(ShadowBackdropReason reason) {
  return {
      .kind = ShadowBackdropKind::Barrier,
      .reason = reason,
  };
}

inline ShadowBackdrop shadowBackdropForChild(
    const ShadowBackdrop& backdrop,
    const LayoutMetrics& childLayoutMetrics) {
  if (backdrop.kind != ShadowBackdropKind::Provider) {
    return backdrop;
  }

  const auto& childFrame = childLayoutMetrics.frame;
  if (!shadowBackdropContainsRect(backdrop.coverage, childFrame)) {
    return shadowBackdropBarrier(ShadowBackdropReason::OutsideProvider);
  }

  ShadowBackdrop childBackdrop = backdrop;
  childBackdrop.coverage.origin.x -= childFrame.origin.x;
  childBackdrop.coverage.origin.y -= childFrame.origin.y;
  return childBackdrop;
}

inline ShadowBackdrop resolveShadowBackdropForChild(
    const ShadowBackdrop& inheritedBackdrop,
    ShadowBackdropHostKind parentHostKind,
    const ViewProps& parentProps,
    const LayoutMetrics& parentLayoutMetrics,
    const LayoutMetrics& childLayoutMetrics,
    bool hasEarlierSiblingBarrier) {
  if (parentHostKind == ShadowBackdropHostKind::Unknown) {
    return shadowBackdropBarrier(ShadowBackdropReason::UnknownAncestor);
  }

  if (hasEarlierSiblingBarrier) {
    return shadowBackdropBarrier(ShadowBackdropReason::EarlierSibling);
  }

  if (parentProps.opacity < 0.999 || shadowBackdropHasVisualEffect(parentProps)) {
    return shadowBackdropBarrier(ShadowBackdropReason::VisualEffect);
  }

  const auto borderMetrics = parentProps.resolveBorderMetrics(parentLayoutMetrics);
  const bool hasRoundedCorners = shadowBackdropHasRoundedCorners(borderMetrics);
  if (hasRoundedCorners && !parentProps.getClipsContentToBounds()) {
    return shadowBackdropBarrier(ShadowBackdropReason::RoundedAncestor);
  }

  if (parentProps.backgroundColor) {
    const auto backgroundAlpha = alphaFromColor(parentProps.backgroundColor);
    if (backgroundAlpha == 255) {
      const auto& parentSize = parentLayoutMetrics.frame.size;
      ShadowBackdrop parentBackdrop{
          .kind = ShadowBackdropKind::Provider,
          .color = parentProps.backgroundColor,
          .coverage = {{0, 0}, parentSize},
      };
      return shadowBackdropForChild(parentBackdrop, childLayoutMetrics);
    }

    if (backgroundAlpha > 0) {
      return shadowBackdropBarrier(ShadowBackdropReason::TranslucentAncestor);
    }
  }

  return shadowBackdropForChild(inheritedBackdrop, childLayoutMetrics);
}

/*
 * Describes a view that can be mounted.
 * This is exposed to the mounting layer.
 */
struct ShadowView final {
  ShadowView() = default;
  ShadowView(const ShadowView& shadowView) = default;
  ShadowView(ShadowView&& shadowView) noexcept = default;

  /*
   * Constructs a `ShadowView` from given `ShadowNode`.
   */
  explicit ShadowView(const ShadowNode& shadowNode);

  ShadowView& operator=(const ShadowView& other) = default;
  ShadowView& operator=(ShadowView&& other) = default;

  bool operator==(const ShadowView& rhs) const;
  bool operator!=(const ShadowView& rhs) const;

  ComponentName componentName{};
  ComponentHandle componentHandle{};
  SurfaceId surfaceId{};
  Tag tag{};
  ShadowNodeTraits traits{};
  Props::Shared props{};
  EventEmitter::Shared eventEmitter{};
  LayoutMetrics layoutMetrics{EmptyLayoutMetrics};
  State::Shared state{};
  ShadowViewEnvironment environment{};
};

#if RN_DEBUG_STRING_CONVERTIBLE

std::string getDebugName(const ShadowView& object);
std::vector<DebugStringConvertibleObject> getDebugProps(
    const ShadowView& object,
    DebugStringConvertibleOptions options);

#endif

} // namespace facebook::react

namespace std {

template <>
struct hash<facebook::react::ShadowBackdrop> {
  size_t operator()(const facebook::react::ShadowBackdrop& backdrop) const {
    return facebook::react::hash_combine(
        0,
        backdrop.kind,
        backdrop.reason,
        backdrop.color,
        backdrop.coverage,
        backdrop.providerTag,
        backdrop.barrierTag);
  }
};

template <>
struct hash<facebook::react::ShadowViewEnvironment> {
  size_t operator()(const facebook::react::ShadowViewEnvironment& environment) const {
    return facebook::react::hash_combine(0, environment.shadowBackdrop);
  }
};

template <>
struct hash<facebook::react::ShadowView> {
  size_t operator()(const facebook::react::ShadowView& shadowView) const {
    return facebook::react::hash_combine(
        0,
        shadowView.surfaceId,
        shadowView.componentHandle,
        shadowView.tag,
        shadowView.props,
        shadowView.eventEmitter,
        shadowView.layoutMetrics,
        shadowView.state,
        shadowView.environment);
  }
};

} // namespace std

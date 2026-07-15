/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "sliceChildShadowNodeViewPairs.h"
#include <cxxreact/TraceSection.h>
#include <react/featureflags/ReactNativeFeatureFlags.h>
#include <react/renderer/components/root/RootShadowNode.h>
#include <react/renderer/components/view/ViewShadowNode.h>
#include <react/renderer/core/LayoutableShadowNode.h>

#include "ShadowViewNodePair.h"

namespace facebook::react {

#ifndef NDEBUG
struct ShadowBackdropPropagationStats {
  size_t nodesVisited{};
  size_t environmentsComputed{};
  size_t providers{};
  size_t barriers{};

  void didVisitNode() {
    nodesVisited++;
  }

  void didComputeEnvironment(const ShadowBackdrop& shadowBackdrop) {
    environmentsComputed++;
    if (shadowBackdrop.kind == ShadowBackdropKind::Provider) {
      providers++;
    } else if (shadowBackdrop.kind == ShadowBackdropKind::Barrier) {
      barriers++;
    }
  }
};
#else
struct ShadowBackdropPropagationStats {
  void didVisitNode() {}
  void didComputeEnvironment(const ShadowBackdrop&) {}
};
#endif

static ShadowBackdropHostKind shadowBackdropHostKind(
    const ShadowNode& shadowNode) {
  const auto componentName = shadowNode.getComponentName();
  if (componentName == RootComponentName) {
    return ShadowBackdropHostKind::Root;
  }

  if (componentName == ViewComponentName) {
    return ShadowBackdropHostKind::View;
  }

  return ShadowBackdropHostKind::Unknown;
}

static const ViewProps& shadowBackdropViewProps(
    const ShadowNode& shadowNode,
    ShadowBackdropHostKind hostKind) {
  static const ViewProps emptyViewProps{};
  if (hostKind == ShadowBackdropHostKind::Unknown) {
    return emptyViewProps;
  }

  const auto& props = *shadowNode.getProps();
  return static_cast<const ViewProps&>(props);
}

static bool shadowBackdropSiblingHasUnboundedEffect(
    const ShadowNode& shadowNode) {
  const auto hostKind = shadowBackdropHostKind(shadowNode);
  if (hostKind == ShadowBackdropHostKind::Unknown) {
    return true;
  }

  const auto& props = shadowBackdropViewProps(shadowNode, hostKind);
  const Transform identityTransform{};
  return shadowBackdropHasVisualEffect(props) || props.transform != identityTransform;
}

static bool shadowBackdropRectsIntersect(const Rect& first, const Rect& second) {
  const auto intersection = Rect::intersect(first, second);
  return intersection.size.width > 0 && intersection.size.height > 0;
}

static void collectShadowBackdropsForChildren(
    const ShadowNode& parentShadowNode,
    const ShadowBackdrop& inheritedBackdrop,
    ShadowViewEnvironmentMap& environments,
    ShadowBackdropPropagationStats& stats) {
  const auto parentHostKind = shadowBackdropHostKind(parentShadowNode);
  const auto& parentProps =
      shadowBackdropViewProps(parentShadowNode, parentHostKind);
  const auto parentShadowView = ShadowView(parentShadowNode);
  const auto parentLayoutMetrics = parentShadowView.layoutMetrics;

  std::vector<const ShadowNode*> childShadowNodes;
  for (const auto& childShadowNode : parentShadowNode.getChildren()) {
    const auto childShadowNodePointer = childShadowNode.get();
    childShadowNodes.push_back(childShadowNodePointer);
  }

  std::stable_sort(
      childShadowNodes.begin(),
      childShadowNodes.end(),
      [](const ShadowNode* first, const ShadowNode* second) {
        return first->getOrderIndex() < second->getOrderIndex();
      });

  Rect earlierSiblingBounds{};
  bool hasEarlierSiblingBounds = false;
  bool hasEarlierSiblingUnboundedEffect = false;

  for (const auto* childShadowNode : childShadowNodes) {
    stats.didVisitNode();
    const auto childShadowView = ShadowView(*childShadowNode);
    const auto childLayoutMetrics = childShadowView.layoutMetrics;
    const auto childBounds = childLayoutMetrics.getOverflowInsetFrame();
    bool hasEarlierSiblingBarrier = hasEarlierSiblingUnboundedEffect;
    if (hasEarlierSiblingBounds &&
        shadowBackdropRectsIntersect(earlierSiblingBounds, childBounds)) {
      hasEarlierSiblingBarrier = true;
    }

    auto childBackdrop = resolveShadowBackdropForChild(
        inheritedBackdrop,
        parentHostKind,
        parentProps,
        parentLayoutMetrics,
        childLayoutMetrics,
        hasEarlierSiblingBarrier);
    if (childBackdrop.kind == ShadowBackdropKind::Provider &&
        childBackdrop.providerTag == -1) {
      childBackdrop.providerTag = parentShadowNode.getTag();
    }
    if (childBackdrop.kind == ShadowBackdropKind::Barrier &&
        childBackdrop.barrierTag == -1) {
      childBackdrop.barrierTag = parentShadowNode.getTag();
    }
    const auto childTag = childShadowNode->getTag();
    ShadowViewEnvironment childEnvironment{};
    childEnvironment.shadowBackdrop = childBackdrop;
    const auto insertion = environments.emplace(childTag, childEnvironment);
    react_native_assert(insertion.second);
    stats.didComputeEnvironment(childBackdrop);

    collectShadowBackdropsForChildren(
        *childShadowNode, childBackdrop, environments, stats);

    if (childLayoutMetrics != EmptyLayoutMetrics) {
      if (hasEarlierSiblingBounds) {
        earlierSiblingBounds.unionInPlace(childBounds);
      } else {
        earlierSiblingBounds = childBounds;
        hasEarlierSiblingBounds = true;
      }
    }

    if (shadowBackdropSiblingHasUnboundedEffect(*childShadowNode)) {
      hasEarlierSiblingUnboundedEffect = true;
    }
  }
}

ShadowViewEnvironmentMap collectShadowViewEnvironments(
    const ShadowNode& rootShadowNode) {
  ShadowViewEnvironmentMap environments{};
  ShadowBackdropPropagationStats stats{};
#ifndef NDEBUG
  TraceSection propagationSection("ShadowBackdrop::propagate");
#endif
  collectShadowBackdropsForChildren(rootShadowNode, {}, environments, stats);
#ifndef NDEBUG
  TraceSection statsSection(
      "ShadowBackdrop::propagateStats",
      "nodes",
      stats.nodesVisited,
      "environments",
      stats.environmentsComputed,
      "providers",
      stats.providers,
      "barriers",
      stats.barriers);
#endif
  return environments;
}

/*
 * Sorting comparator for `reorderInPlaceIfNeeded`.
 */
static bool shouldFirstPairComesBeforeSecondOne(
    const ShadowViewNodePair* lhs,
    const ShadowViewNodePair* rhs) noexcept {
  return lhs->shadowNode->getOrderIndex() < rhs->shadowNode->getOrderIndex();
}

/*
 * Reorders pairs in-place based on `orderIndex` using a stable sort algorithm.
 */
static void reorderInPlaceIfNeeded(
    std::vector<ShadowViewNodePair*>& pairs) noexcept {
  if (pairs.size() < 2) {
    return;
  }

  auto isReorderNeeded = false;
  for (const auto& pair : pairs) {
    if (pair->shadowNode->getOrderIndex() != 0) {
      isReorderNeeded = true;
      break;
    }
  }

  if (!isReorderNeeded) {
    return;
  }

  std::stable_sort(
      pairs.begin(), pairs.end(), &shouldFirstPairComesBeforeSecondOne);
}

static void sliceChildShadowNodeViewPairsRecursively(
    std::vector<ShadowViewNodePair*>& pairList,
    size_t& startOfStaticIndex,
    ViewNodePairScope& scope,
    Point layoutOffset,
    const ShadowNode& shadowNode,
    const CullingContext& cullingContext,
    const ShadowViewEnvironmentMap* environments) {
  for (const auto& sharedChildShadowNode : shadowNode.getChildren()) {
    auto& childShadowNode = *sharedChildShadowNode;
#ifndef ANDROID
    // T153547836: Disabled on Android because the mounting infrastructure
    // is not fully ready yet.
    // On iOS, gated by useTraitHiddenOnIOS. When false, the view stays in
    // the slice and is hidden via UIView.hidden = YES in
    // updateLayoutMetrics: instead of being removed.
    if (ReactNativeFeatureFlags::useTraitHiddenOnIOS() &&
        childShadowNode.getTraits().check(ShadowNodeTraits::Trait::Hidden)) {
      continue;
    }
#endif
    auto shadowView = ShadowView(childShadowNode);
    if (environments) {
      const auto childTag = childShadowNode.getTag();
      const auto environment = environments->find(childTag);
      react_native_assert(environment != environments->end());
      shadowView.environment = environment->second;
    }

    if (ReactNativeFeatureFlags::enableViewCulling()) {
      auto isViewCullable =
          !shadowView.traits.check(
              ShadowNodeTraits::Trait::Unstable_uncullableView) &&
          !shadowView.traits.check(
              ShadowNodeTraits::Trait::Unstable_uncullableTrace);
      if (cullingContext.shouldConsiderCulling() && isViewCullable) {
        auto overflowInsetFrame =
            shadowView.layoutMetrics.getOverflowInsetFrame() *
            cullingContext.transform;
        if (auto layoutableShadowNode =
                dynamic_cast<const LayoutableShadowNode*>(&childShadowNode)) {
          overflowInsetFrame =
              overflowInsetFrame * layoutableShadowNode->getTransform();
        }
        auto doesIntersect =
            Rect::intersect(cullingContext.frame, overflowInsetFrame) != Rect{};
        if (!doesIntersect) {
          continue; // Culling.
        }
      }
    }

    auto origin = layoutOffset;
    auto cullingContextCopy = cullingContext.adjustCullingContextIfNeeded(
        {.shadowView = shadowView, .shadowNode = &childShadowNode});

    if (shadowView.layoutMetrics != EmptyLayoutMetrics) {
      origin += shadowView.layoutMetrics.frame.origin;
      shadowView.layoutMetrics.frame.origin += layoutOffset;
    }

    // This might not be a FormsView, or a FormsStackingContext. We let the
    // differ handle removal of flattened views from the Mounting layer and
    // shuffling their children around.
    bool childrenFormStackingContexts = shadowNode.getTraits().check(
        ShadowNodeTraits::Trait::ChildrenFormStackingContext);
    bool isConcreteView = (childShadowNode.getTraits().check(
                               ShadowNodeTraits::Trait::FormsView) ||
                           childrenFormStackingContexts) &&
        !childShadowNode.getTraits().check(
            ShadowNodeTraits::Trait::ForceFlattenView);
    bool areChildrenFlattened =
        (!childShadowNode.getTraits().check(
             ShadowNodeTraits::Trait::FormsStackingContext) &&
         !childrenFormStackingContexts) ||
        childShadowNode.getTraits().check(
            ShadowNodeTraits::Trait::ForceFlattenView);

    Point storedOrigin = {};
    if (areChildrenFlattened) {
      storedOrigin = origin;
    }
    scope.push_back(
        {shadowView,
         &childShadowNode,
         areChildrenFlattened,
         isConcreteView,
         storedOrigin,
         environments});

    if (shadowView.layoutMetrics.positionType == PositionType::Static) {
      auto it = pairList.begin();
      std::advance(it, startOfStaticIndex);
      pairList.insert(it, &scope.back());
      startOfStaticIndex++;
      if (areChildrenFlattened) {
        sliceChildShadowNodeViewPairsRecursively(
            pairList,
            startOfStaticIndex,
            scope,
            origin,
            childShadowNode,
            cullingContextCopy,
            environments);
      }
    } else {
      pairList.push_back(&scope.back());
      if (areChildrenFlattened) {
        size_t pairListSize = pairList.size();
        sliceChildShadowNodeViewPairsRecursively(
            pairList,
            pairListSize,
            scope,
            origin,
            childShadowNode,
            cullingContextCopy,
            environments);
      }
    }
  }
}

std::vector<ShadowViewNodePair*> sliceChildShadowNodeViewPairs(
    const ShadowViewNodePair& shadowNodePair,
    ViewNodePairScope& scope,
    bool allowFlattened,
    Point layoutOffset,
    const CullingContext& cullingContext) {
  const auto& shadowNode = *shadowNodePair.shadowNode;
  auto pairList = std::vector<ShadowViewNodePair*>{};

  if (shadowNodePair.flattened && shadowNodePair.isConcreteView &&
      !allowFlattened) {
    return pairList;
  }

  size_t startOfStaticIndex = 0;

  sliceChildShadowNodeViewPairsRecursively(
      pairList,
      startOfStaticIndex,
      scope,
      layoutOffset,
      shadowNode,
      cullingContext,
      shadowNodePair.environments);

  // Sorting pairs based on `orderIndex` if needed.
  reorderInPlaceIfNeeded(pairList);

  // Set list and mountIndex for each after reordering
  size_t mountIndex = 0;
  for (auto child : pairList) {
    child->mountIndex =
        (child->isConcreteView ? mountIndex++ : static_cast<unsigned long>(-1));
  }

  return pairList;
}

} // namespace facebook::react

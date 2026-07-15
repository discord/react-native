/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include <gtest/gtest.h>

#include <memory>
#include <utility>

#include <react/featureflags/ReactNativeFeatureFlags.h>
#include <react/featureflags/ReactNativeFeatureFlagsDefaults.h>
#include <react/renderer/components/root/RootShadowNode.h>
#include <react/renderer/components/view/ViewShadowNode.h>
#include <react/renderer/core/ShadowNodeFragment.h>
#include <react/renderer/element/ComponentBuilder.h>
#include <react/renderer/element/Element.h>
#include <react/renderer/element/testUtils.h>
#include <react/renderer/mounting/Differentiator.h>
#include <react/renderer/mounting/ShadowView.h>

namespace facebook::react {

namespace {

LayoutMetrics layoutMetrics(Size size, Point origin = {}) {
  LayoutMetrics result{};
  result.frame = {origin, size};
  return result;
}

} // namespace

class ShadowBackdropTestFeatureFlags : public ReactNativeFeatureFlagsDefaults {
 public:
  bool enableIOSBorderBoxShadowBackdrop() override {
    return true;
  }
};

class ShadowBackdropDifferentiatorTest : public ::testing::Test {
 protected:
  void SetUp() override {
    ReactNativeFeatureFlags::dangerouslyReset();
    auto featureFlags = std::make_unique<ShadowBackdropTestFeatureFlags>();
    ReactNativeFeatureFlags::override(std::move(featureFlags));
  }

  void TearDown() override {
    ReactNativeFeatureFlags::dangerouslyReset();
  }
};

TEST(ShadowBackdropTest, opaque_view_provides_backdrop_to_child) {
  ViewProps parentProps{};
  parentProps.backgroundColor = blackColor();

  auto parentLayout = layoutMetrics({100, 100});
  auto childLayout = layoutMetrics({40, 40}, {20, 20});
  auto backdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      parentProps,
      parentLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Provider);
  EXPECT_EQ(backdrop.color, blackColor());
}

TEST(ShadowBackdropTest, transparent_wrapper_relays_opaque_backdrop) {
  ViewProps providerProps{};
  providerProps.backgroundColor = blackColor();
  auto providerLayout = layoutMetrics({100, 100});
  auto wrapperLayout = layoutMetrics({60, 60}, {20, 20});
  auto inheritedBackdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      providerProps,
      providerLayout,
      wrapperLayout,
      false);

  ViewProps wrapperProps{};
  auto childLayout = layoutMetrics({40, 40}, {10, 10});
  auto backdrop = resolveShadowBackdropForChild(
      inheritedBackdrop,
      ShadowBackdropHostKind::View,
      wrapperProps,
      wrapperLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Provider);
  EXPECT_EQ(backdrop.color, blackColor());
}

TEST(ShadowBackdropTest, transparent_wrapper_does_not_extend_provider_coverage) {
  ViewProps providerProps{};
  providerProps.backgroundColor = blackColor();
  auto providerLayout = layoutMetrics({100, 100});
  auto wrapperLayout = layoutMetrics({40, 40}, {60, 0});
  auto inheritedBackdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      providerProps,
      providerLayout,
      wrapperLayout,
      false);

  ViewProps wrapperProps{};
  auto childLayout = layoutMetrics({40, 40}, {20, 0});
  auto backdrop = resolveShadowBackdropForChild(
      inheritedBackdrop,
      ShadowBackdropHostKind::View,
      wrapperProps,
      wrapperLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Barrier);
  EXPECT_EQ(backdrop.reason, ShadowBackdropReason::OutsideProvider);
}

TEST(ShadowBackdropTest, translucent_ancestor_is_a_barrier) {
  ViewProps parentProps{};
  parentProps.backgroundColor = colorFromRGBA(255, 255, 255, 31);

  auto parentLayout = layoutMetrics({100, 100});
  auto childLayout = layoutMetrics({40, 40}, {20, 20});
  auto backdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      parentProps,
      parentLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Barrier);
  EXPECT_EQ(backdrop.reason, ShadowBackdropReason::TranslucentAncestor);
}

TEST(ShadowBackdropTest, overlapping_preceding_sibling_is_a_barrier) {
  ViewProps parentProps{};
  parentProps.backgroundColor = blackColor();

  auto parentLayout = layoutMetrics({100, 100});
  auto childLayout = layoutMetrics({40, 40}, {20, 20});
  auto backdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      parentProps,
      parentLayout,
      childLayout,
      true);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Barrier);
  EXPECT_EQ(backdrop.reason, ShadowBackdropReason::EarlierSibling);
}

TEST(ShadowBackdropTest, unknown_ancestor_is_a_barrier) {
  ViewProps parentProps{};
  auto parentLayout = layoutMetrics({100, 100});
  auto childLayout = layoutMetrics({40, 40}, {20, 20});
  auto backdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::Unknown,
      parentProps,
      parentLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Barrier);
  EXPECT_EQ(backdrop.reason, ShadowBackdropReason::UnknownAncestor);
}

TEST(ShadowBackdropTest, provider_does_not_escape_its_own_bounds) {
  ViewProps parentProps{};
  parentProps.backgroundColor = blackColor();

  auto parentLayout = layoutMetrics({100, 100});
  auto childLayout = layoutMetrics({40, 40}, {80, 80});
  auto backdrop = resolveShadowBackdropForChild(
      {},
      ShadowBackdropHostKind::View,
      parentProps,
      parentLayout,
      childLayout,
      false);

  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Barrier);
  EXPECT_EQ(backdrop.reason, ShadowBackdropReason::OutsideProvider);
}

TEST_F(
    ShadowBackdropDifferentiatorTest,
    root_environment_pass_reaches_nested_created_view) {
  std::shared_ptr<RootShadowNode> rootShadowNode;

  auto providerProps = std::make_shared<ViewShadowNodeProps>();
  providerProps->backgroundColor = blackColor();

  auto shadowHostProps = std::make_shared<ViewShadowNodeProps>();
  shadowHostProps->shadowPathIOS = ShadowPathMode::BorderBox;

  // clang-format off
  auto element =
      Element<RootShadowNode>()
        .reference(rootShadowNode)
        .tag(1)
        .children({
          Element<ViewShadowNode>()
            .tag(2)
            .props(providerProps)
            .children({
              Element<ViewShadowNode>()
                .tag(3)
                .props(shadowHostProps)
            })
        });
  // clang-format on

  auto builder = simpleComponentBuilder();
  builder.build(element);
  rootShadowNode->layoutIfNeeded();

  auto emptyRootFragment = ShadowNodeFragment{
      ShadowNodeFragment::propsPlaceholder(),
      ShadowNode::emptySharedShadowNodeSharedList(),
  };
  auto emptyRootShadowNode = rootShadowNode->clone(emptyRootFragment);
  auto mutations =
      calculateShadowViewMutations(*emptyRootShadowNode, *rootShadowNode);

  const ShadowView* createdShadowHost = nullptr;
  for (const auto& mutation : mutations) {
    if (mutation.type == ShadowViewMutation::Create &&
        mutation.newChildShadowView.tag == 3) {
      createdShadowHost = &mutation.newChildShadowView;
      break;
    }
  }

  ASSERT_NE(createdShadowHost, nullptr);
  const auto& backdrop = createdShadowHost->environment.shadowBackdrop;
  EXPECT_EQ(backdrop.kind, ShadowBackdropKind::Provider);
  EXPECT_EQ(backdrop.color, blackColor());
  EXPECT_EQ(backdrop.providerTag, 2);
}

} // namespace facebook::react

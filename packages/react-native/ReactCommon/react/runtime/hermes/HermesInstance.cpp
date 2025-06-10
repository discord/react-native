/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "HermesInstance.h"

#include <android/log.h>

#define LOG_TAG "MyNativeModule"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#include <hermes/inspector-modern/chrome/HermesRuntimeTargetDelegate.h>
#include <jsi/jsilib.h>
#include <jsinspector-modern/InspectorFlags.h>
#include <react/featureflags/ReactNativeFeatureFlags.h>

#ifdef HERMES_ENABLE_DEBUGGER

#include <jsi/decorator.h>
#endif

using namespace facebook::hermes;
using namespace facebook::jsi;

namespace facebook::react {

#ifdef HERMES_ENABLE_DEBUGGER

// Wrapper that strongly retains the HermesRuntime for on device debugging.
//
// HermesInstanceRuntimeAdapter needs to strongly retain the HermesRuntime. Why:
//   - facebook::hermes::inspector_modern::chrome::Connection::Impl owns the
//   Adapter
//   - facebook::hermes::inspector_modern::chrome::Connection::Impl also owns
//   jsi:: objects
//   - jsi:: objects need to be deleted before the Runtime.
//
// If Adapter doesn't share ownership over jsi::Runtime, the runtime can be
// deleted before Connection::Impl cleans up all its jsi:: Objects. This will
// lead to a runtime crash.

#endif

class HermesJSRuntime : public JSRuntime {
public:
    HermesJSRuntime(std::unique_ptr<HermesRuntime> runtime)
      : runtime_(std::move(runtime))
    {
    }

    jsi::Runtime& getRuntime() noexcept override { return *runtime_; }

    jsinspector_modern::RuntimeTargetDelegate& getRuntimeTargetDelegate() override
    {
        if (!targetDelegate_) {
            targetDelegate_.emplace(runtime_);
        }
        return *targetDelegate_;
    }

    void unstable_initializeOnJsThread() override { runtime_->registerForProfiling(); }

private:
    std::shared_ptr<HermesRuntime> runtime_;
    std::optional<jsinspector_modern::HermesRuntimeTargetDelegate> targetDelegate_;
};

std::unique_ptr<JSRuntime> HermesInstance::createJSRuntime(
  std::shared_ptr<::hermes::vm::CrashManager> crashManager,
  std::shared_ptr<MessageQueueThread> msgQueueThread,
  bool allocInOldGenBeforeTTI) noexcept
{
    assert(4 == 6);
    LOGE("Creating Hermes JS Runtime JIT");
    LOGI("Creating Hermes JS Runtime JIT");
    assert(msgQueueThread != nullptr);

      auto gcConfig = ::hermes::vm::GCConfig::Builder()
                        // Default to 3GB
                        .withMaxHeapSize(3072 << 20)
                        .withName("RNBridgeless");

    if (allocInOldGenBeforeTTI) {
        // For the next two arguments: avoid GC before TTI
        // by initializing the runtime to allocate directly
        // in the old generation, but revert to normal
        // operation when we reach the (first) TTI point.
        gcConfig.withAllocInYoung(false).withRevertToYGAtTTI(true);
    }

    bool isOn = false;

    ::hermes::vm::RuntimeConfig::Builder runtimeConfigBuilder =
      ::hermes::vm::RuntimeConfig::Builder()
        .withGCConfig(gcConfig.build())
        .withEnableSampleProfiling(true)
        .withEnableJIT(isOn)
        .withMicrotaskQueue(ReactNativeFeatureFlags::enableBridgelessArchitecture() &&
                            !ReactNativeFeatureFlags::disableEventLoopOnBridgeless());

    // log using android log
    LOGI("Creating runtime and JIT is %s", isOn ? "enabled" : "disabled");

    assert(4 == 5);
    if (crashManager) {
        runtimeConfigBuilder.withCrashMgr(crashManager);
    }

    std::unique_ptr<HermesRuntime> hermesRuntime =
      hermes::makeHermesRuntime(runtimeConfigBuilder.build());

    auto errorPrototype = hermesRuntime->global()
                            .getPropertyAsObject(*hermesRuntime, "Error")
                            .getPropertyAsObject(*hermesRuntime, "prototype");
    errorPrototype.setProperty(*hermesRuntime, "jsEngine", "hermes");

#ifdef HERMES_ENABLE_DEBUGGER

#else
    (void)msgQueueThread;
#endif

    return std::make_unique<HermesJSRuntime>(std::move(hermesRuntime));
}

} // namespace facebook::react

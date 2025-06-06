/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#pragma once

#include <mutex>
#include <thread>

#include <jsi/jsi.h>

namespace facebook::react {

/*
 * Takes a function and calls it with a reference to a Runtime. The function
 * will be called when it is safe to do so (i.e. it ensures non-concurrent
 * access) and may be invoked asynchronously, depending on the implementation.
 * If you need to access a Runtime, it's encouraged to use a RuntimeExecutor
 * instead of storing a pointer to the Runtime itself, which makes it more
 * difficult to ensure that the Runtime is being accessed safely.
 */
using RuntimeExecutor =
    std::function<void(std::function<void(jsi::Runtime& runtime)>&& callback)>;

/*
 * Executes a `callback` in a *synchronous* manner on the same thread using
 * given `RuntimeExecutor`.
 * Use this method when the caller needs to *be blocked* by executing the
 * `callback` and requires that the callback will be executed on the same
 * thread.
 * Example order of events (when not a sync call in runtimeExecutor callback):
 * - [UI thread] Lock all mutexes at start
 * - [UI thread] runtimeCaptured.lock before callback
 * - [JS thread] Set runtimePtr in runtimeExecutor callback
 * - [JS thread] runtimeCaptured.unlock in runtimeExecutor callback
 * - [UI thread] Call callback
 * - [JS thread] callbackExecuted.lock in runtimeExecutor callback
 * - [UI thread] callbackExecuted.unlock after callback
 * - [UI thread] jsBlockExecuted.lock after callback
 * - [JS thread] jsBlockExecuted.unlock in runtimeExecutor callback
 */
inline static void executeSynchronouslyOnSameThread_CAN_DEADLOCK(
    const RuntimeExecutor& runtimeExecutor,
    std::function<void(jsi::Runtime& runtime)>&& callback) noexcept {
  // Note: We need the third mutex to get back to the main thread before
  // the lambda is finished (because all mutexes are allocated on the stack).

  std::mutex runtimeCaptured;
  std::mutex callbackExecuted;
  std::mutex jsBlockExecuted;

  runtimeCaptured.lock();
  callbackExecuted.lock();
  jsBlockExecuted.lock();

  jsi::Runtime* runtimePtr;

  auto threadId = std::this_thread::get_id();

  runtimeExecutor([&](jsi::Runtime& runtime) {
    runtimePtr = &runtime;

    if (threadId == std::this_thread::get_id()) {
      // In case of a synchronous call, we should unlock mutexes and return.
      runtimeCaptured.unlock();
      jsBlockExecuted.unlock();
      return;
    }

    runtimeCaptured.unlock();
    // `callback` is called somewhere here.
    callbackExecuted.lock();
    jsBlockExecuted.unlock();
  });

  runtimeCaptured.lock();
  callback(*runtimePtr);
  callbackExecuted.unlock();
  jsBlockExecuted.lock();
}

template <typename DataT>
inline static DataT executeSynchronouslyOnSameThread_CAN_DEADLOCK(
    const RuntimeExecutor& runtimeExecutor,
    std::function<DataT(jsi::Runtime& runtime)>&& callback) noexcept {
  DataT data;

  executeSynchronouslyOnSameThread_CAN_DEADLOCK(
      runtimeExecutor,
      [&](jsi::Runtime& runtime) { data = callback(runtime); });

  return data;
}
} // namespace facebook::react

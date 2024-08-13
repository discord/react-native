/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#import "RCTCxxUtils.h"

#import <React/RCTFollyConvert.h>
#import <React/RCTModuleData.h>
#import <React/RCTUtils.h>
#import <cxxreact/CxxNativeModule.h>
#import <jsi/jsi.h>

#import "DispatchMessageQueueThread.h"
#import "RCTCxxModule.h"
#import "RCTNativeModule.h"

namespace facebook::react {

using facebook::jsi::JSError;

std::vector<std::unique_ptr<NativeModule>>
createNativeModules(NSArray<RCTModuleData *> *modules, RCTBridge *bridge, const std::shared_ptr<Instance> &instance)
{
  std::vector<std::unique_ptr<NativeModule>> nativeModules;
  for (RCTModuleData *moduleData in modules) {
    if ([moduleData.moduleClass isSubclassOfClass:[RCTCxxModule class]]) {
      nativeModules.emplace_back(std::make_unique<CxxNativeModule>(
          instance,
          [moduleData.name UTF8String],
          [moduleData] { return [(RCTCxxModule *)(moduleData.instance) createModule]; },
          std::make_shared<DispatchMessageQueueThread>(moduleData)));
    } else {
      nativeModules.emplace_back(std::make_unique<RCTNativeModule>(bridge, moduleData));
    }
  }
  return nativeModules;
}

static NSError *errorWithException(const std::exception &e)
{
  NSString *msg = @(e.what());
  NSMutableDictionary *errorInfo = [NSMutableDictionary dictionary];

  const auto *jsError = dynamic_cast<const JSError *>(&e);
  if (jsError) {
    errorInfo[RCTJSRawStackTraceKey] = @(jsError->getStack().c_str());
    msg = [@"Unhandled JS Exception: " stringByAppendingString:msg];
  }

  NSError *nestedError;
  try {
    std::rethrow_if_nested(e);
  } catch (const std::exception &e) {
    nestedError = errorWithException(e);
  } catch (...) {
  }

  if (nestedError) {
    msg = [NSString stringWithFormat:@"%@\n\n%@", msg, [nestedError localizedDescription]];
  }

  errorInfo[NSLocalizedDescriptionKey] = msg;
  return [NSError errorWithDomain:RCTErrorDomain code:1 userInfo:errorInfo];
}

NSError *tryAndReturnError(const std::function<void()> &func)
{
  try {
    @try {
      func();
      return nil;
    } @catch (NSException *exception) {
      return RCTErrorWithNSException(exception);
    } @catch (id exception) {
      // This will catch any other ObjC exception, but no C++ exceptions
      return RCTErrorWithMessage(@"non-std ObjC Exception");
    }
  } catch (const std::exception &ex) {
    return errorWithException(ex);
  } catch (id ex) {
    NSLog(@"Caught an exception: %@", ex);
    NSLog(@"is at an objc exception: %d", [ex isKindOfClass:[NSException class]]);
    NSLog(@"description: %@", [ex name]);
    NSLog(@"user info %@", [ex userInfo]);
    return RCTErrorWithMessage(@"non-std ObjC Exception");
  }
}

NSString *deriveSourceURL(NSURL *url)
{
  NSString *sourceUrl;
  if (url.isFileURL) {
    // Url will contain only path to resource (i.g. file:// will be removed)
    sourceUrl = url.path;
  } else {
    // Url will include protocol (e.g. http://)
    sourceUrl = url.absoluteString;
  }
  return sourceUrl ?: @"";
}

} // namespace facebook::react

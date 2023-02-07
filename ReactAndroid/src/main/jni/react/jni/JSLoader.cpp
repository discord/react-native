/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include "JSLoader.h"

#include <android/asset_manager_jni.h>
#include <android/trace.h>
#include <cxxreact/JSBigString.h>
#include <cxxreact/JSBundleType.h>
#include <dlfcn.h>
#include <fbjni/fbjni.h>
#include <folly/Conv.h>

#ifdef WITH_FBSYSTRACE
#include <fbsystrace.h>
using fbsystrace::FbSystraceSection;
#endif

#ifdef WITH_CHONKER
#include "chonker.h"
#endif

using namespace facebook::jni;

namespace facebook {
namespace react {

class AssetManagerString : public JSBigString {
 public:
  AssetManagerString(AAsset *asset) : asset_(asset) {
#ifdef WITH_CHONKER
    if (discord::chonker::ChonkyFile::IsChonkyBuffer(
            AAsset_getBuffer(asset), AAsset_getLength(asset))) {
      chonky_ = discord::chonker::ChonkyFile::OpenBuffer(
          AAsset_getBuffer(asset), AAsset_getLength(asset));

      if (!chonky_) {
        throw std::runtime_error(
            "Unable to chonker bundle due to an internal error.");
      }
    }
#endif
  }

  virtual ~AssetManagerString() {
    AAsset_close(asset_);
  }

  bool isAscii() const override {
    return false;
  }

  const char *c_str() const override {
#ifdef WITH_CHONKER
    if (chonky_) {
      return reinterpret_cast<const char *>(chonky_->GetBuffer());
    }
#endif
    return (const char *)AAsset_getBuffer(asset_);
  }

  // Length of the c_str without the NULL byte.
  size_t size() const override {
#ifdef WITH_CHONKER
    if (chonky_) {
      return chonky_->GetUncompressedSize();
    }
#endif
    return AAsset_getLength(asset_);
  }

 private:
#ifdef WITH_CHONKER
  std::unique_ptr<discord::chonker::ChonkyFile> chonky_;
#endif
  AAsset *asset_;
};

__attribute__((visibility("default"))) AAssetManager *extractAssetManager(
    alias_ref<JAssetManager::javaobject> assetManager) {
  auto env = Environment::current();
  return AAssetManager_fromJava(env, assetManager.get());
}

__attribute__((visibility("default"))) std::unique_ptr<const JSBigString>
loadScriptFromAssets(AAssetManager *manager, const std::string &assetName) {
#ifdef WITH_FBSYSTRACE
  FbSystraceSection s(
      TRACE_TAG_REACT_CXX_BRIDGE,
      "reactbridge_jni_loadScriptFromAssets",
      "assetName",
      assetName);
#endif
  if (manager) {
    auto asset = AAssetManager_open(
        manager,
        assetName.c_str(),
        AASSET_MODE_STREAMING); // Optimized for sequential read: see
                                // AssetManager.java for docs
    if (asset) {
      auto script = std::make_unique<AssetManagerString>(asset);
      if (script->size() >= sizeof(BundleHeader)) {
        // When using bytecode, it's safe for the underlying buffer to not be \0
        // terminated. In all other scenarios, we will force a copy of the
        // script to ensure we have a terminator.
        const BundleHeader *header =
            reinterpret_cast<const BundleHeader *>(script->c_str());
        if (isHermesBytecodeBundle(*header)) {
          return script;
        }
      }

      auto buf = std::make_unique<JSBigBufferString>(script->size());
      memcpy(buf->data(), script->c_str(), script->size());
      return buf;
    }
  }

  throw std::runtime_error(folly::to<std::string>(
      "Unable to load script. Make sure you're "
      "either running Metro (run 'npx react-native start') or that your bundle '",
      assetName,
      "' is packaged correctly for release."));
}

} // namespace react
} // namespace facebook

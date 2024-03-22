# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

require "json"
require_relative "./hermes-utils.rb"

react_native_path = File.join(__dir__, "..", "..")

# Whether Hermes is built for Release or Debug is determined by the PRODUCTION envvar.
build_type = ENV['PRODUCTION'] == "1" ? :release : :debug

# package.json
package = JSON.parse(File.read(File.join(react_native_path, "package.json")))
version = package['version']

# sdks/.hermesversion
hermestag_file = File.join(react_native_path, "sdks", ".hermesversion")
build_from_source = ENV['BUILD_FROM_SOURCE'] === 'true'

git = "https://github.com/facebook/hermes.git"

abort_if_invalid_tarball_provided!

source = compute_hermes_source(build_from_source, hermestag_file, git, version, build_type, react_native_path)

Pod::Spec.new do |spec|
  spec.name        = "hermes-engine"
  spec.version     = version
  spec.summary     = "Hermes is a small and lightweight JavaScript engine optimized for running React Native."
  spec.description = "Hermes is a JavaScript engine optimized for fast start-up of React Native apps. It features ahead-of-time static optimization and compact bytecode."
  spec.homepage    = "https://hermesengine.dev"
  spec.license     = package['license']
  spec.author      = "Facebook"
  spec.source      = source
  spec.platforms   = { :osx => "10.13", :ios => "12.4" }

  spec.preserve_paths      = '**/*.*'
  spec.source_files        = ''

  spec.pod_target_xcconfig = {
                    "CLANG_CXX_LANGUAGE_STANDARD" => "c++17",
                    "CLANG_CXX_LIBRARY" => "compiler-default"
                  }.merge!(build_type == :debug ? { "GCC_PREPROCESSOR_DEFINITIONS" => "HERMES_ENABLE_DEBUGGER=1" } : {})

  spec.ios.vendored_frameworks = "destroot/Library/Frameworks/ios/hermes.framework"
  spec.osx.vendored_frameworks = "destroot/Library/Frameworks/macosx/hermes.framework"

  spec.subspec 'Pre-built' do |ss|
    ss.preserve_paths = ["destroot/bin/*"].concat(build_type == :debug ? ["**/*.{h,c,cpp}"] : [])
    ss.source_files = "destroot/include/**/*.h"
    ss.exclude_files = ["destroot/include/jsi/jsi/JSIDynamic.{h,cpp}", "destroot/include/jsi/jsi/jsilib-*.{h,cpp}"]
    ss.header_mappings_dir = "destroot/include"
    ss.ios.vendored_frameworks = "destroot/Library/Frameworks/universal/hermes.xcframework"
    ss.osx.vendored_frameworks = "destroot/Library/Frameworks/macosx/hermes.framework"
  end
end

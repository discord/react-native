# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

boost_config = get_boost_config()
boost_git_url = boost_config[:git]

Pod::Spec.new do |spec|
  spec.name = 'boost'
  spec.version = '1.84.0'
  spec.license = { :type => 'Boost Software License', :file => "LICENSE_1_0.txt" }
  spec.homepage = 'http://www.boost.org'
  spec.summary = 'Boost provides free peer-reviewed portable C++ source libraries.'
  spec.authors = 'Rene Rivera'
  spec.source = { :http => 'https://archives.boost.io/release/1.84.0/source/boost_1_84_0.tar.gz',
                  :sha256 => 'a5800f405508f5df8114558ca9855d2640a2de8f0445f051fa1c7c3383045724' }

  # Pinning to the same version as React.podspec.
  spec.platforms = min_supported_versions
  spec.requires_arc = false
  spec.source_files = 'boost/**/*.{hpp,cpp}'

  spec.module_name = 'boost'
  spec.header_dir = 'boost'
  spec.preserve_path = 'boost'
  spec.header_mappings_dir = 'boost/boost'

  spec.resource_bundles = {'boost_privacy' => 'boost/PrivacyInfo.xcprivacy'}
end

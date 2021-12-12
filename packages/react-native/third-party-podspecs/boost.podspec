# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

Pod::Spec.new do |spec|
  spec.name = 'boost'
  spec.version = '1.83.0'
  spec.license = { :type => 'Boost Software License', :file => "LICENSE_1_0.txt" }
  spec.homepage = 'http://www.boost.org'
  spec.summary = 'Boost provides free peer-reviewed portable C++ source libraries.'
  spec.authors = 'Rene Rivera'
  spec.source = { :http => 'https://archives.boost.io/release/1.83.0/source/boost_1_83_0.tar.gz',
                  :sha256 => 'c0685b68dd44cc46574cce86c4e17c0f611b15e195be9848dfd0769a0a207628' }

  # Pinning to the same version as React.podspec.
  spec.platforms = min_supported_versions
  spec.requires_arc = false

  spec.module_name = 'boost'
  spec.header_dir = 'boost'
  spec.preserve_path = 'boost'
end

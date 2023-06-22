# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

Pod::Spec.new do |spec|
  spec.name = 'boost'
  spec.version = '1.76.0'
  spec.license = { :type => 'Boost Software License', :file => "LICENSE_1_0.txt" }
  spec.homepage = 'http://www.boost.org'
  spec.summary = 'Boost provides free peer-reviewed portable C++ source libraries.'
  spec.authors = 'Rene Rivera'
  # PodToBuild does not support tar bz2 compression so we use tar.gz instead.
  spec.source = { :http => 'https://boostorg.jfrog.io/artifactory/main/release/1.76.0/source/boost_1_76_0.tar.gz',
                  :sha256 => '7bd7ddceec1a1dfdcbdb3e609b60d01739c38390a5f956385a12f3122049f0ca' } # jhgg was here

  # Pinning to the same version as React.podspec.
  spec.platforms = { :ios => '11.0' }
  spec.requires_arc = false

  spec.module_name = 'boost'
  spec.header_dir = 'boost'
  spec.preserve_path = 'boost'
end

# Copyright (c) Meta Platforms, Inc. and affiliates.
#
# This source code is licensed under the MIT license found in the
# LICENSE file in the root directory of this source tree.

cmake_minimum_required(VERSION 3.13)
set(CMAKE_VERBOSE_MAKEFILE on)

add_compile_options(
        -fexceptions
        -frtti
        -std=c++20
        -Wno-unused-lambda-capture
        -DWITH_PERFETTO=1
        -DLOG_TAG=\"ReactNative\")

file(GLOB react_cxxreact_SRC CONFIGURE_DEPENDS *.cpp
${REACT_COMMON_DIR}/reactperflogger/reactperflogger/ReactPerfettoCategories.cpp
        /Users/szymonkapala/work/perfetto/perfetto/sdk/perfetto.cc
)
add_library(react_cxxreact OBJECT ${react_cxxreact_SRC})

target_include_directories(react_cxxreact PUBLIC ${REACT_COMMON_DIR}
        /Users/szymonkapala/work/perfetto/perfetto/sdk
        ${REACT_COMMON_DIR}/reactperflogger
)

target_link_libraries(react_cxxreact
        boost
        callinvoker
        folly_runtime
        glog
        jsi
        jsinspector
        logger
        reactperflogger
        runtimeexecutor
        react_debug)

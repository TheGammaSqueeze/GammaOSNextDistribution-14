# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

inc_android_compat = include_directories('include')

files_lib_android_compat = files(
  'port.cc',
)

lib_android_compat = static_library(
   'android_compat',
   files_lib_android_compat,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_android_compat]
)

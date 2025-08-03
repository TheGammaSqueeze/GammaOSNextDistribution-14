# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

inc_codec_common = include_directories('include')

files_lib_codec_common = files(
  'ChecksumCalculator.cpp',
  'glUtils.cpp',
)

lib_codec_common = static_library(
   'codec_common',
   files_lib_codec_common,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_android_compat, inc_codec_common, inc_qemu_pipe,
                         inc_qemu_pipe_types, inc_guest_iostream,
                         inc_system, inc_opengl_headers],
)

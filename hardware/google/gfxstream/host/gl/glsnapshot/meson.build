# Copyright 2023 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_gl_snapshot = files(
  'GLSnapshot.cpp',
)

lib_gl_snapshot = static_library(
  'gl_snapshot',
  files_lib_gl_snapshot,
  cpp_args: gfxstream_host_args,
  include_directories: [inc_gfxstream_include, inc_include]
)

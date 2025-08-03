# Copyright 2023 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_compressed_textures = files(
  'AstcCpuDecompressorNoOp.cpp',
)

lib_compressed_textures = static_library(
  'compressed_textures',
  files_lib_compressed_textures,
  cpp_args: gfxstream_host_args,
  include_directories: [inc_etc],
  link_with: [lib_etc],
)

# Copyright 2023 Android Open Source Project
# SPDX-License-Identifier: MIT

subdir('magma_dec')

files_lib_magma_server = files(
  'Connection.cpp',
  'Decoder.cpp',
  'DrmBuffer.cpp',
  'DrmContext.cpp',
  'DrmDevice.cpp',
  'IntelDrmDecoder.cpp',
)

magma_server_cpp_args = [
  '-DGFXSTREAM_MAGMA_USE_INTEL_DRM=1',
]

lib_magma_server = static_library(
  'magma_server',
  files_lib_magma_server,
  cpp_args: gfxstream_host_args + magma_server_cpp_args,
  include_directories: [inc_root, inc_gfxstream_include, inc_include, inc_magma_external,
                        inc_magma_external_lib, inc_apigen_codec, inc_magma_dec,
                        inc_stream_servers],
  dependencies: [drm_dep, aemu_base_dep, aemu_logging_dep],
  link_with: [lib_magma_dec, lib_apigen_codec],
)

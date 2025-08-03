# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_platform = files(
  '../VirtGpu.cpp',
  'RutabagaLayer.cpp',
  'RutabagaVirtGpuBlob.cpp',
  'RutabagaVirtGpuBlobMapping.cpp',
  'RutabagaVirtGpuSyncHelper.cpp',
  'RutabagaVirtGpuDevice.cpp',
)

lib_platform = static_library(
   'platform',
   files_lib_platform,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_platform, inc_android_compat],
   link_with: [lib_android_compat],
   dependencies: [drm_dep, rutabaga_gfx_ffi_dep],
)

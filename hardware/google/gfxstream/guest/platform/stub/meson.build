# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_platform = files(
  '../VirtGpu.cpp',
  'StubVirtGpuDevice.cpp',
  'StubVirtGpuBlobMapping.cpp',
  'StubVirtGpuBlob.cpp',
  'StubSync.cpp',
)

lib_platform_stub = static_library(
   'stub_platform',
   files_lib_platform,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_platform, inc_android_compat],
   dependencies: drm_dep,
)

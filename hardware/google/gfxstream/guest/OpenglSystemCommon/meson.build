# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_stream = files(
  'HostConnection.cpp',
  'ProcessPipe.cpp',
  'QemuPipeStream.cpp',
  'ThreadInfo.cpp',
  'VirtioGpuPipeStream.cpp',
)

lib_stream = static_library(
   'stream',
   files_lib_stream,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_vulkan_headers, inc_opengl_headers,
                         inc_guest_iostream, inc_opengl_codec,
                         inc_android_emu, inc_render_enc, inc_android_compat,
                         inc_qemu_pipe, inc_qemu_pipe_types, inc_gralloc,
                         inc_vulkan_enc, inc_goldfish_address_space,
                         inc_system, inc_platform, inc_codec_common],
   link_with: [lib_codec_common, lib_goldfish_address_space, lib_qemu_pipe,
               lib_render_control_enc, lib_platform],
   dependencies: drm_dep
)

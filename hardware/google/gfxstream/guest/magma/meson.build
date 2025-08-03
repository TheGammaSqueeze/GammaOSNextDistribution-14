# Copyright 2023 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_magma_enc = files(
    'magma.cpp',
    'magma_enc_util.cpp',
    'magma_enc/magma_enc.cpp',
    'magma_enc/magma_entry.cpp',
    'magma_enc/magma_stubs.cpp',
    'magma_enc/magma_client_context.cpp',
  )

inc_magma_enc = include_directories('magma_enc')

lib_magma_enc = static_library(
   'magma',
   files_lib_magma_enc,
   cpp_args: gfxstream_guest_args + [
      '-Wno-misleading-indentation',
      '-Wno-unused-function',
   ],
   include_directories: [inc_fuchsia_magma, inc_fuchsia_magma_nolib, inc_magma, inc_magma_enc,
                         inc_android_emu, inc_guest_iostream, inc_android_compat,
                         inc_opengl_codec, inc_opengl_system, inc_render_enc, inc_system,
                         inc_goldfish_address_space, inc_platform, inc_codec_common],
   link_with: [
      lib_platform,
      lib_stream, # for ASG
      lib_codec_common, # for checksums
      lib_emu_android_base, # for tracing
      lib_android_compat # for logging
   ],
   dependencies: [
    dependency('libdrm')
   ],
   install: true,
)

install_subdir('../../third-party/fuchsia/magma',
               install_dir: 'include')

pkg = import('pkgconfig')
pkg.generate(lib_magma_enc,
             description: 'magma',
             subdirs: ['magma/include', 'magma/include/lib/'],
)

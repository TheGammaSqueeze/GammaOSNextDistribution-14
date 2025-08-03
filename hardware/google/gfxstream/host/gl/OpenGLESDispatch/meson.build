# Copyright 2023 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_gl_dispatch = files(
  'EGLDispatch.cpp',
  'gles1_stubbed_in_translator_namespace.cpp',
  'gles2_stubbed_in_translator_namespace.cpp',
  'GLESv1Dispatch.cpp',
  'GLESv2Dispatch.cpp',
  'OpenGLDispatchLoader.cpp',
  'StaticDispatch.cpp',
)

lib_gl_dispatch = static_library(
  'gl_dispatch',
  files_lib_gl_dispatch,
  include_directories: [inc_gfxstream_include, inc_include],
  cpp_args: gfxstream_host_args,
  link_with: [lib_egl_translator, lib_glescm_translator, lib_glesv2_translator]
)

# Copyright 2022 Android Open Source Project
# SPDX-License-Identifier: MIT

files_lib_linux_platform = files(
  'AlignedBuf.cpp',
  'AndroidSubAllocator.cpp',
  'AndroidSubAllocator.h',
  'Pool.cpp',
  'Process.cpp',
  'AndroidHealthMonitor.cpp',
  'AndroidHealthMonitorConsumerBasic.cpp',
  'Tracing.cpp',
  'ring_buffer.c',
  'files/MemStream.cpp',
  'files/Stream.cpp',
  'files/StreamSerializing.cpp',
  'synchronization/AndroidMessageChannel.cpp',
  'threads/AndroidFunctorThread.cpp',
  'threads/AndroidThread_pthread.cpp',
  'threads/AndroidWorkPool.cpp',
)

lib_emu_android_base = static_library(
   'emu_android_base',
   files_lib_linux_platform,
   cpp_args: gfxstream_guest_args,
   include_directories: [inc_android_emu, inc_android_compat],
   dependencies: thread_dep
)

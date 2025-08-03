/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <cstdint>

constexpr int kBufferMaxPlanes = 4;

enum class BufferColorSpace : int32_t {
  kUndefined,
  kItuRec601,
  kItuRec709,
  kItuRec2020,
};

enum class BufferSampleRange : int32_t {
  kUndefined,
  kFullRange,
  kLimitedRange,
};

enum class BufferBlendMode : int32_t {
  kUndefined,
  kNone,
  kPreMult,
  kCoverage,
};

struct BufferInfo {
  uint32_t width;
  uint32_t height;
  uint32_t format; /* DRM_FORMAT_* from drm_fourcc.h */
  uint32_t pitches[kBufferMaxPlanes];
  uint32_t offsets[kBufferMaxPlanes];
  /* sizes[] is used only by mapper@4 metadata getter for internal purposes */
  uint32_t sizes[kBufferMaxPlanes];
  int prime_fds[kBufferMaxPlanes];
  uint64_t modifiers[kBufferMaxPlanes];

  BufferColorSpace color_space;
  BufferSampleRange sample_range;
  BufferBlendMode blend_mode;
};

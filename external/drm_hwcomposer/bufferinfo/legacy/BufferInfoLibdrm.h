/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <hardware/gralloc.h>

#include "bufferinfo/BufferInfoGetter.h"

namespace android {

class BufferInfoLibdrm : public LegacyBufferInfoGetter {
 public:
  using LegacyBufferInfoGetter::LegacyBufferInfoGetter;
  auto GetBoInfo(buffer_handle_t handle) -> std::optional<BufferInfo> override;
  int ValidateGralloc() override;

 private:
  bool GetYuvPlaneInfo(uint32_t hal_format, int num_fds, buffer_handle_t handle,
                       BufferInfo *bo);
};

}  // namespace android

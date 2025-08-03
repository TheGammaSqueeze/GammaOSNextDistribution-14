// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <stddef.h>
#include <stdint.h>

namespace audio_proxy::service {

// Copy data from ring buffer "src" to ring buffer "dst". "dst" is guaranteed to
// have more space than "src".
void copyRingBuffer(int8_t* dstBuf1, size_t dstLen1, int8_t* dstBuf2,
                    size_t dstLen2, const int8_t* srcBuf1, size_t srcLen1,
                    const int8_t* srcBuf2, size_t srcLen2);

}  // namespace audio_proxy::service
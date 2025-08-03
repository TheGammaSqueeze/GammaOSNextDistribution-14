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

#include "RingBufferUtil.h"

#include <android-base/logging.h>

namespace audio_proxy::service {
namespace {
struct CopyDesc {
  int8_t* dst = nullptr;
  const int8_t* src = nullptr;
  size_t len = 0;
};
}  // namespace

void copyRingBuffer(int8_t* dstBuf1, size_t dstLen1, int8_t* dstBuf2,
                    size_t dstLen2, const int8_t* srcBuf1, size_t srcLen1,
                    const int8_t* srcBuf2, size_t srcLen2) {
  // Caller should make sure the dst buffer has more space.
  DCHECK_GE(dstLen1 + dstLen2, srcLen1 + srcLen2);

  CopyDesc cp1 = {dstBuf1, srcBuf1, 0};
  CopyDesc cp2;
  CopyDesc cp3;

  if (srcLen1 == dstLen1) {
    cp1 = {dstBuf1, srcBuf1, srcLen1};

    DCHECK_LE(srcLen2, dstLen2);
    cp2 = {dstBuf2, srcBuf2, srcLen2};

    // No need to copy more data, thus no need to update cp3.
  } else if (srcLen1 < dstLen1) {
    cp1 = {dstBuf1, srcBuf1, srcLen1};

    if (dstLen1 <= srcLen1 + srcLen2) {
      // Copy data into both dstBuf1 and dstBuf2.
      cp2 = {cp1.dst + cp1.len, srcBuf2, dstLen1 - srcLen1};
      cp3 = {dstBuf2, cp2.src + cp2.len, srcLen1 + srcLen2 - dstLen1};
    } else {
      // dstBuf1 is bigger enough to hold all the data from src.
      cp2 = {cp1.dst + cp1.len, srcBuf2, srcLen2};

      // No need to copy more data, thus no need to update cp3.
    }
  } else {  // srcLen1 > dstLen1
    cp1 = {dstBuf1, srcBuf1, dstLen1};
    cp2 = {dstBuf2, cp1.src + cp1.len, srcLen1 - dstLen1};
    cp3 = {cp2.dst + cp2.len, srcBuf2, srcLen2};
  }

  if (cp1.len > 0) {
    DCHECK(cp1.dst);
    DCHECK(cp1.src);
    std::memcpy(cp1.dst, cp1.src, cp1.len);
  }

  if (cp2.len > 0) {
    DCHECK(cp2.dst);
    DCHECK(cp2.src);
    std::memcpy(cp2.dst, cp2.src, cp2.len);
  }

  if (cp3.len > 0) {
    DCHECK(cp3.dst);
    DCHECK(cp3.src);
    std::memcpy(cp3.dst, cp3.src, cp3.len);
  }
}

}  // namespace audio_proxy::service
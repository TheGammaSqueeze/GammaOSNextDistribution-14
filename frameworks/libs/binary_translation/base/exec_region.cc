/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "berberis/base/exec_region.h"

#include <sys/mman.h>

#include "berberis/base/mmap.h"

namespace berberis {

void ExecRegion::Write(const uint8_t* dst, const void* src, size_t size) {
  CHECK_LE(begin(), dst);
  CHECK_GE(end(), dst + size);
  CHECK_NE(write_, nullptr);
  size_t offset = dst - begin();
  memcpy(write_ + offset, src, size);
}

void ExecRegion::Detach() {
  if (write_ != nullptr) {
    MunmapOrDie(write_, size_);
  }
  write_ = nullptr;
}

void ExecRegion::Free() {
  Detach();
  MunmapOrDie(exec_, size_);
}

}  // namespace berberis

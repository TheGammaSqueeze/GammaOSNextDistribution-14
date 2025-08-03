/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "berberis/base/mmap.h"

#include <stdint.h>
#include <sys/mman.h>

#include "berberis/base/checks.h"

namespace berberis {

void* MmapImpl(MmapImplArgs args) {
  return mmap(args.addr, args.size, args.prot, args.flags, args.fd, args.offset);
}

void* MmapImplOrDie(MmapImplArgs args) {
  void* ptr = MmapImpl(args);
  CHECK_NE(ptr, MAP_FAILED);
  return ptr;
}

void MunmapOrDie(void* ptr, size_t size) {
  int res = munmap(ptr, size);
  CHECK_EQ(res, 0);
}

void MprotectOrDie(void* ptr, size_t size, int prot) {
  int res = mprotect(ptr, size, prot);
  CHECK_EQ(res, 0);
}

}  // namespace berberis

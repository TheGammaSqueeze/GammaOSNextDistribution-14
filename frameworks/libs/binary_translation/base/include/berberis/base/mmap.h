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

#ifndef BERBERIS_BASE_MMAP_H_
#define BERBERIS_BASE_MMAP_H_

#include <sys/mman.h>

#include <cstddef>

#include "berberis/base/bit_util.h"
#include "berberis/base/macros.h"
#include "berberis/base/page_size.h"

namespace berberis {

template <typename T>
constexpr T AlignDownPageSize(T x) {
  return AlignDown(x, kPageSize);
}

template <typename T>
constexpr T AlignUpPageSize(T x) {
  return AlignUp(x, kPageSize);
}

template <typename T>
constexpr bool IsAlignedPageSize(T x) {
  return IsAligned(x, kPageSize);
}

struct MmapImplArgs {
  void* addr = nullptr;
  size_t size = 0;
  int prot = PROT_READ | PROT_WRITE;
  int flags = MAP_PRIVATE | MAP_ANONYMOUS;
  int fd = -1;
  off_t offset = 0;
};

void* MmapImpl(MmapImplArgs args);
void* MmapImplOrDie(MmapImplArgs args);

inline void* Mmap(size_t size) {
  return MmapImpl({.size = size});
}

inline void* MmapOrDie(size_t size) {
  return MmapImplOrDie({.size = size});
}

void MunmapOrDie(void* ptr, size_t size);

void MprotectOrDie(void* ptr, size_t size, int prot);

class ScopedMmap {
 public:
  ScopedMmap() : data_(nullptr), size_(0) {}
  explicit ScopedMmap(size_t size) { Init(size); }

  ~ScopedMmap() {
    if (size_) {
      MunmapOrDie(data_, size_);
    }
  }

  void Init(size_t size) {
    size_ = AlignUpPageSize(size);
    data_ = MmapOrDie(size_);
  }

  void* data() const { return data_; }
  size_t size() const { return size_; }

 private:
  void* data_;
  size_t size_;

  DISALLOW_COPY_AND_ASSIGN(ScopedMmap);
};

}  // namespace berberis

#endif  // BERBERIS_BASE_MMAP_H_

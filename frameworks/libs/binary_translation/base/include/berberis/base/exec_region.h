/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef BERBERIS_BASE_EXEC_REGION_H_
#define BERBERIS_BASE_EXEC_REGION_H_

#include <cstddef>
#include <cstdint>

namespace berberis {

// ExecRegion manages a range of writable executable memory.
// This implementation works with 2 mappings of memfd file,
// one is executable and is read only another one is writable
// and not executable.
//
// Because both mappings are backed by memfd file msync is not
// needed in order to keep them up to date. It is backed by shmem
// and always consistent. shmem implementation of fsync is no-op:
// https://github.com/torvalds/linux/blob/3de0c269adc6c2fac0bb1fb11965f0de699dc32b/mm/shmem.c#L3931
//
//
// Move-only!
class ExecRegion {
 public:
  ExecRegion() = default;
  explicit ExecRegion(uint8_t* exec, uint8_t* write, size_t size)
      : exec_{exec}, write_{write}, size_{size} {}

  ExecRegion(const ExecRegion& other) = delete;
  ExecRegion& operator=(const ExecRegion& other) = delete;

  ExecRegion(ExecRegion&& other) noexcept {
    exec_ = other.exec_;
    write_ = other.write_;
    size_ = other.size_;
    other.exec_ = nullptr;
    other.write_ = nullptr;
    other.size_ = 0;
  }

  ExecRegion& operator=(ExecRegion&& other) noexcept {
    if (this == &other) {
      return *this;
    }
    exec_ = other.exec_;
    write_ = other.write_;
    size_ = other.size_;
    other.exec_ = nullptr;
    other.write_ = nullptr;
    other.size_ = 0;
    return *this;
  }

  [[nodiscard]] const uint8_t* begin() const { return exec_; }
  [[nodiscard]] const uint8_t* end() const { return exec_ + size_; }

  void Write(const uint8_t* dst, const void* src, size_t size);

  void Detach();
  void Free();

 private:
  uint8_t* exec_ = nullptr;
  uint8_t* write_ = nullptr;
  size_t size_ = 0;
};

}  // namespace berberis

#endif  // BERBERIS_BASE_EXEC_REGION_H_

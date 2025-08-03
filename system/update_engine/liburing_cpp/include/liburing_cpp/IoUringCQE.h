//
// Copyright (C) 2022 The Android Open Source Project
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
//

#ifndef __IO_URING_CQE_CPP_H
#define __IO_URING_CQE_CPP_H

#include <stdint.h>

#include <type_traits>

namespace io_uring_cpp {

struct IoUringCQE {
  int32_t res;
  uint32_t flags;
  template <typename T>
  T GetData() const {
    static_assert(
        std::is_trivially_copy_constructible_v<T>,
        "Only trivially copiable types can be passed for io_uring data");
    static_assert(sizeof(T) <= 8,
                  "io_uring SQE's data field has size of 8 bytes, can't pass "
                  "data larger than that.");
    return *reinterpret_cast<const T*>(&userdata);
  }

  constexpr IoUringCQE(int32_t res, uint32_t flags, uint64_t userdata)
      : res(res), flags(flags), userdata(userdata) {}

 private:
  uint64_t userdata;
};

}  // namespace io_uring_cpp

#endif

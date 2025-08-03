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
#include <errno.h>
#include <string.h>

#include <algorithm>
#include <iostream>
#include <numeric>
#include <type_traits>
#include <vector>

#ifndef __IO_URING_SQE_CPP_H
#define __IO_URING_SQE_CPP_H

namespace io_uring_cpp {

struct [[nodiscard]] IoUringSubmitResult {
  constexpr bool IsOk() const { return ret > 0; }
  const char *ErrMsg() const {
    if (IsOk()) {
      return nullptr;
    }
    return strerror(-ret);
  }
  constexpr auto ErrCode() const { return std::min(ret, 0); }
  constexpr auto EntriesSubmitted() const { return std::max(ret, 0); }

  int ret;
};

struct [[nodiscard]] Errno {
  constexpr Errno(int ret) : error_code(ret) {
    error_code = std::abs(error_code);
  }
  int error_code;
  constexpr int ErrCode() { return error_code; }
  const char *ErrMsg();
  constexpr bool IsOk() const { return error_code == 0; }
};

std::ostream &operator<<(std::ostream &, Errno err);

struct [[nodiscard]] IoUringSQE {
  constexpr IoUringSQE(void *p) : sqe(p) {}
  IoUringSQE &SetFlags(unsigned int flags);
  template <typename T>
  IoUringSQE &SetData(const T &data) {
    static_assert(
        std::is_trivially_copy_constructible_v<T>,
        "Only trivially copiable types can be passed for io_uring data");
    static_assert(sizeof(T) <= 8,
                  "io_uring SQE's data field has size of 8 bytes, can't pass "
                  "data larger than that.");
    return SetData(*reinterpret_cast<const uint64_t*>(&data));
  }
  IoUringSQE& SetData(uint64_t data);

  constexpr bool IsOk() const { return sqe != nullptr; }

 private:
  void *sqe;
};

}  // namespace io_uring_cpp

#endif

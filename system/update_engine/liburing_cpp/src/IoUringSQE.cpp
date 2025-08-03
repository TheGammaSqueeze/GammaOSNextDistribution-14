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

#include <liburing_cpp/IoUringSQE.h>

#include <cstdint>

#include "liburing.h"

namespace io_uring_cpp {

IoUringSQE &IoUringSQE::SetFlags(unsigned int flags) {
  if (IsOk()) {
    ::io_uring_sqe_set_flags(static_cast<struct io_uring_sqe *>(sqe), flags);
  }
  return *this;
}

IoUringSQE &IoUringSQE::SetData(uint64_t data) {
  if (IsOk()) {
    ::io_uring_sqe_set_data(static_cast<struct io_uring_sqe *>(sqe),
                            reinterpret_cast<void *>(data));
  }
  return *this;
}

}  // namespace io_uring_cpp

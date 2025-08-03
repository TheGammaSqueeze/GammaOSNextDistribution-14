/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef BERBERIS_BASE_PAGESIZE_H_
#define BERBERIS_BASE_PAGESIZE_H_

#include "berberis/base/checks.h"

#include <unistd.h>

namespace berberis {

// Accessor for page size constant.
//
// Only allows access to the page size constant if
// it has been initialized.
//
// For cases where page size value is needed before kPageSize
// has been initialized, getpagesize() is returned.
struct PageSize {
  PageSize() : value_(getpagesize()) {}

  operator size_t() const {
    if (value_ == 0) {
      return getpagesize();
    }
    CHECK((value_ & (value_ - 1)) == 0);  // Power of 2
    return value_;
  }

 private:
  const size_t value_;
};

namespace {

inline constexpr size_t kMaxPageSize = 0x10000;  // 64KB
inline const PageSize kPageSize;

}  // namespace

}  // namespace berberis

#endif  // BERBERIS_BASE_PAGESIZE_H_

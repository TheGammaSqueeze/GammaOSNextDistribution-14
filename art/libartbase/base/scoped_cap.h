/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_LIBARTBASE_BASE_SCOPED_CAP_H_
#define ART_LIBARTBASE_BASE_SCOPED_CAP_H_

#include <sys/capability.h>

#include <utility>

#include "android-base/logging.h"

namespace art {

// A wrapper of `cap_t` that automatically calls `cap_free`.
class ScopedCap {
 public:
  explicit ScopedCap(cap_t cap) : cap_(cap) {}

  ScopedCap(const ScopedCap&) = delete;
  ScopedCap& operator=(const ScopedCap&) = delete;
  ScopedCap(ScopedCap&& other) noexcept : cap_(std::exchange(other.cap_, nullptr)) {}

  ~ScopedCap() {
    if (cap_ != nullptr) {
      if (cap_free(cap_) != 0) {
        PLOG(ERROR) << "Failed to call cap_free";
      }
    }
  }

  cap_t Get() const { return cap_; }

 private:
  cap_t cap_;
};

}  // namespace art

#endif  // ART_LIBARTBASE_BASE_SCOPED_CAP_H_

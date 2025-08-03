/*
 * Copyright (C) 2018 The Android Open Source Project
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

#ifndef NOGROD_STRING_TABLE_
#define NOGROD_STRING_TABLE_

#include <cstdint>
#include <string>
#include <vector>

#include <berberis/base/checks.h>
#include <berberis/base/macros.h>

#include "buffer.h"

namespace nogrod {

class StringTable {
 public:
  StringTable() = default;

  StringTable(Buffer<char> strtab) : strtab_(std::move(strtab)) {
    // string table should be \0 terminated.
    CHECK_EQ(strtab_.data()[strtab_.size() - 1], 0);
  }

  StringTable(const StringTable&) = delete;
  StringTable& operator=(const StringTable&) = delete;
  StringTable(StringTable&&) = default;
  StringTable& operator=(StringTable&&) = default;

  [[nodiscard]] const char* GetString(size_t index) const {
    CHECK_LT(index, strtab_.size());
    return strtab_.data() + index;
  }

 private:
  Buffer<char> strtab_;
};

}  // namespace nogrod
#endif  // NOGROD_STRING_TABLE_

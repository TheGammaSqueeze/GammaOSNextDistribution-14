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

#ifndef BERBERIS_LITE_TRANSLATOR_RISCV64_REGISTER_MAINTAINER_H_
#define BERBERIS_LITE_TRANSLATOR_RISCV64_REGISTER_MAINTAINER_H_

#include <optional>

#include "berberis/base/checks.h"

namespace berberis {

template <typename RegType>
class RegMaintainer {
 public:
  RegMaintainer() : reg_(std::nullopt), modified_(false) {}

  RegType GetMapped() const { return reg_.value(); }
  void Map(RegType reg) { reg_ = std::optional<RegType>(reg); }
  bool IsMapped() const { return reg_.has_value(); }
  void NoticeModified() { modified_ = true; }
  bool IsModified() const { return modified_; }

 private:
  std::optional<RegType> reg_;
  bool modified_;
};

template <typename RegType, unsigned size>
class RegisterFileMaintainer {
 public:
  RegType GetMapped(unsigned i) const {
    CHECK_LT(i, size);
    return arr_[i].GetMapped();
  }

  void Map(unsigned i, RegType reg) {
    CHECK_LT(i, size);
    arr_[i].Map(reg);
  }

  bool IsMapped(unsigned i) const {
    CHECK_LT(i, size);
    return arr_[i].IsMapped();
  }

  void NoticeModified(unsigned i) {
    CHECK_LT(i, size);
    arr_[i].NoticeModified();
  }

  bool IsModified(unsigned i) const {
    CHECK_LT(i, size);
    return arr_[i].IsModified();
  }

 private:
  RegMaintainer<RegType> arr_[size];
};

}  // namespace berberis

#endif  // BERBERIS_LITE_TRANSLATOR_RISCV64_REGISTER_MAINTAINER_H_

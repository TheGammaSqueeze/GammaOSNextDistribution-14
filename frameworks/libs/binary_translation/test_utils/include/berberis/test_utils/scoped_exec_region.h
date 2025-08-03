/*
 * Copyright (C) 2021 The Android Open Source Project
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


#ifndef BERBERIS_TEST_UTILS_SCOPED_EXEC_REGION_H_
#define BERBERIS_TEST_UTILS_SCOPED_EXEC_REGION_H_

#include "berberis/assembler/machine_code.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/exec_region_anonymous.h"

namespace berberis {

class ScopedExecRegion {
 public:
  ScopedExecRegion() = default;

  explicit ScopedExecRegion(MachineCode* code) { Init(code); }

  ScopedExecRegion(const ScopedExecRegion&) = delete;
  ScopedExecRegion& operator=(const ScopedExecRegion&) = delete;
  ScopedExecRegion(const ScopedExecRegion&&) = delete;
  ScopedExecRegion& operator=(const ScopedExecRegion&&) = delete;

  ~ScopedExecRegion() { exec_.Free(); }

  void Init(MachineCode* code) {
    exec_ = ExecRegionAnonymousFactory::Create(code->install_size());
    code->Install(&exec_, exec_.begin(), &recovery_map_);
    exec_.Detach();
  }

  template <typename T = uint8_t>
  const T* get() const {
    return bit_cast<const T*>(exec_.begin());
  }

  [[nodiscard]] const RecoveryMap& recovery_map() const { return recovery_map_; }

 private:
  ExecRegion exec_;
  RecoveryMap recovery_map_;
};

}  // namespace berberis

#endif  // BERBERIS_TEST_UTILS_SCOPED_EXEC_REGION_H_

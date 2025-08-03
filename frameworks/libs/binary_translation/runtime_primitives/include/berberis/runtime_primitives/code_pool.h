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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_CODE_POOL_H_
#define BERBERIS_RUNTIME_PRIMITIVES_CODE_POOL_H_

#include <cstdint>
#include <mutex>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/exec_region.h"
#include "berberis/base/exec_region_anonymous.h"
#include "berberis/runtime_primitives/host_code.h"

#if defined(__BIONIC__)
#include "berberis/base/exec_region_elf_backed.h"
#endif

namespace berberis {

// Code pool is an arena used to store fragments of generated code.
// TODO(b/232598137): Consider freeing allocated regions.
template <typename ExecRegionFactory>
class CodePool {
 public:
  CodePool() = default;

  // Not copyable or movable
  CodePool(const CodePool&) = delete;
  CodePool& operator=(const CodePool&) = delete;
  CodePool(CodePool&&) = delete;
  CodePool& operator=(CodePool&&) = delete;

  [[nodiscard]] HostCode Add(MachineCode* code) {
    std::lock_guard<std::mutex> lock(mutex_);

    uint32_t size = code->install_size();

    // This is the start of a generated code region which is always a branch
    // target. Align on 16-bytes as recommended by Intel.
    // TODO(b/232598137) Extract this into host specified behavior.
    current_address_ = AlignUp(current_address_, 16);

    if (exec_.end() < current_address_ + size) {
      ResetExecRegion(size);
    }

    const uint8_t* result = current_address_;
    current_address_ += size;

    code->Install(&exec_, result, &recovery_map_);
    return result;
  }

  [[nodiscard]] uintptr_t FindRecoveryCode(uintptr_t fault_addr) const {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = recovery_map_.find(fault_addr);
    if (it != recovery_map_.end()) {
      return it->second;
    }
    return 0;
  }

  void ResetExecRegion(uint32_t size = ExecRegionFactory::kExecRegionSize) {
    exec_.Detach();
    exec_ = ExecRegionFactory::Create(std::max(size, ExecRegionFactory::kExecRegionSize));
    current_address_ = exec_.begin();
  }

 private:
  ExecRegion exec_;
  const uint8_t* current_address_ = nullptr;
  // TODO(b/232598137): have recovery map for each region instead!
  RecoveryMap recovery_map_;
  mutable std::mutex mutex_;
};

// Stored data for generated code.
class DataPool {
 public:
  // Returns default data pool.
  static DataPool* GetInstance();

  DataPool() = default;

  template <typename T>
  T* Add(const T& v) {
    return reinterpret_cast<T*>(AddRaw(&v, sizeof(T)));
  }

  void* AddRaw(const void* ptr, uint32_t size);

 private:
  Arena arena_;
  std::mutex mutex_;
};

// Resets exec regions for all CodePools
void ResetAllExecRegions();

// Returns default code pool.
[[nodiscard]] CodePool<ExecRegionAnonymousFactory>* GetDefaultCodePoolInstance();

#if defined(__BIONIC__)
[[nodiscard]] CodePool<ExecRegionElfBackedFactory>* GetFunctionWrapperCodePoolInstance();
#else
[[nodiscard]] CodePool<ExecRegionAnonymousFactory>* GetFunctionWrapperCodePoolInstance();
#endif

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_CODE_POOL_H_

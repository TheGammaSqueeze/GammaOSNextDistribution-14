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

#pragma once

#include <sys/types.h>

#include <cstdint>
#include <functional>
#include <ostream>
#include <string>
#include <vector>

#include <meminfo/meminfo.h>
#include <meminfo/procmeminfo.h>

namespace android {
namespace smapinfo {

class ProcessRecord final {
  public:
    ProcessRecord(pid_t pid, bool get_wss, uint64_t pgflags, uint64_t pgflags_mask,
                  bool get_cmdline, bool get_oomadj, std::ostream& err);

    bool valid() const;
    void CalculateSwap(const std::vector<uint16_t>& swap_offset_array,
                       float zram_compression_ratio);

    // Getters
    pid_t pid() const { return pid_; }
    const std::string& cmdline() const { return cmdline_; }
    int32_t oomadj() const { return oomadj_; }
    uint64_t proportional_swap() const { return proportional_swap_; }
    uint64_t unique_swap() const { return unique_swap_; }
    uint64_t zswap() const { return zswap_; }

    // Wrappers to ProcMemInfo
    const std::vector<uint64_t>& SwapOffsets() const { return swap_offsets_; }
    // show_wss may be used to return differentiated output in the future.
    const ::android::meminfo::MemUsage& Usage([[maybe_unused]] bool show_wss) const {
        return usage_or_wss_;
    }
    const std::vector<::android::meminfo::Vma>& Smaps() { return procmem_.Smaps(); }
    bool ForEachExistingVma(const ::android::meminfo::VmaCallback& callback) {
        return procmem_.ForEachExistingVma(callback);
    }

  private:
    ::android::meminfo::ProcMemInfo procmem_;
    pid_t pid_;
    std::string cmdline_;
    int32_t oomadj_;
    uint64_t proportional_swap_;
    uint64_t unique_swap_;
    uint64_t zswap_;
    ::android::meminfo::MemUsage usage_or_wss_;
    std::vector<uint64_t> swap_offsets_;
};

}  // namespace smapinfo
}  // namespace android

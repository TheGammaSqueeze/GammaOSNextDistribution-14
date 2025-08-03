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

#pragma once

#include <sys/types.h>

#include <functional>
#include <string>
#include <string_view>
#include <vector>

#include "meminfo.h"

namespace android {
namespace meminfo {

using VmaCallback = std::function<void(const Vma&)>;

class ProcMemInfo final {
    // Per-process memory accounting
  public:
    // Reset the working set accounting of the process via /proc/<pid>/clear_refs
    static bool ResetWorkingSet(pid_t pid);

    ProcMemInfo(pid_t pid, bool get_wss = false, uint64_t pgflags = 0, uint64_t pgflags_mask = 0);

    const std::vector<Vma>& Maps();
    const MemUsage& Usage();
    const MemUsage& Wss();

    // Same as Maps() except, only valid for reading working set using CONFIG_IDLE_PAGE_TRACKING
    // support in kernel. If the kernel support doesn't exist, the function will return an empty
    // vector.
    const std::vector<Vma>& MapsWithPageIdle();

    // Same as Maps() except, do not read the usage stats for each map.
    const std::vector<Vma>& MapsWithoutUsageStats();

    // If MapsWithoutUsageStats was called, this function will fill in
    // usage stats for this single vma. If 'use_kb' is true, the vma's
    // usage will be populated in kilobytes instead of bytes.
    bool FillInVmaStats(Vma& vma, bool use_kb = false);

    // If ReadMaps (with get_usage_stats == false) or MapsWithoutUsageStats was
    // called, this function will fill in usage stats for all vmas in 'maps_'.
    bool GetUsageStats(bool get_wss, bool use_pageidle = false, bool swap_only = false);

    // Collect all 'vma' or 'maps' from /proc/<pid>/smaps and store them in 'maps_'. Only
    // information available from smaps fields will be read, so 'swap_offsets_' will not
    // be populated. If 'collect_usage' is 'true', this method will populate 'usage_' as
    // vmas are being collected. Returns a constant reference to the vma vector after the
    // collection is done.
    //
    // Each 'struct Vma' is *fully* populated by this method (unlike SmapsOrRollup).
    const std::vector<Vma>& Smaps(const std::string& path = "", bool collect_usage = false);

    // If 'use_smaps' is 'true' this method reads /proc/<pid>/smaps and calls the callback()
    // for each vma or map that it finds, else if 'use_smaps' is false /proc/<pid>/maps is
    // used instead. Each vma or map found, is converted to 'struct Vma' object which is then
    // passed to the callback.
    // Returns 'false' if the file is malformed.
    bool ForEachVma(const VmaCallback& callback, bool use_smaps = true);

    // Reads all VMAs from /proc/<pid>/maps and calls the callback() for each one of them.
    // Returns false in case of failure during parsing.
    bool ForEachVmaFromMaps(const VmaCallback& callback);

    // Similar to other VMA reading methods, except this one allows passing a reusable buffer
    // to store the /proc/<pid>/maps content
    bool ForEachVmaFromMaps(const VmaCallback& callback, std::string& mapsBuffer);

    // Takes the existing VMAs in 'maps_' and calls the callback() for each one
    // of them. This is intended to avoid parsing /proc/<pid>/maps or
    // /proc/<pid>/smaps twice.
    // Returns false if 'maps_' is empty.
    bool ForEachExistingVma(const VmaCallback& callback);

    // Used to parse either of /proc/<pid>/{smaps, smaps_rollup} and record the process's
    // Pss and Private memory usage in 'stats'.  In particular, the method only populates the fields
    // of the MemUsage structure that are intended to be used by Android's periodic Pss collection.
    //
    // The method populates the following statistics in order to be fast an efficient.
    //   Pss
    //   Rss
    //   Uss
    //   private_clean
    //   private_dirty
    //   SwapPss
    // All other fields of MemUsage are zeroed.
    bool SmapsOrRollup(MemUsage* stats) const;

    // Used to parse either of /proc/<pid>/{smaps, smaps_rollup} and record the process's
    // Pss.
    // Returns 'true' on success and the value of Pss in the out parameter.
    bool SmapsOrRollupPss(uint64_t* pss) const;

    // Used to parse /proc/<pid>/status and record the process's RSS memory as
    // reported by VmRSS. This is cheaper than using smaps or maps. VmRSS as
    // reported by the kernel is not accurate; one of the maps or smaps methods
    // should be used if an estimate is not sufficient.
    //
    // Returns 'true' on success and the value of VmRSS in the out parameter.
    bool StatusVmRSS(uint64_t* rss) const;

    const std::vector<uint64_t>& SwapOffsets();

    // Reads /proc/<pid>/pagemap for this process for each page within
    // the 'vma' and stores that in 'pagemap'. It is assumed that the 'vma'
    // is obtained by calling Maps() or 'ForEachVma' for the same object. No special checks
    // are made to see if 'vma' is *valid*.
    // Returns false if anything goes wrong, 'true' otherwise.
    bool PageMap(const Vma& vma, std::vector<uint64_t>* pagemap);

    ~ProcMemInfo() = default;

  private:
    bool ReadMaps(bool get_wss, bool use_pageidle = false, bool get_usage_stats = true,
                  bool swap_only = false);
    bool ReadVmaStats(int pagemap_fd, Vma& vma, bool get_wss, bool use_pageidle, bool swap_only);

    pid_t pid_;
    bool get_wss_;
    uint64_t pgflags_;
    uint64_t pgflags_mask_;

    std::vector<Vma> maps_;

    MemUsage usage_;
    std::vector<uint64_t> swap_offsets_;
};

// Makes callback for each 'vma' or 'map' found in file provided.
// If 'read_smaps_fields' is 'true', the file is expected to be in the
// same format as /proc/<pid>/smaps, else the file is expected to be
// formatted as /proc/<pid>/maps.
// Returns 'false' if the file is malformed.
bool ForEachVmaFromFile(const std::string& path, const VmaCallback& callback,
                        bool read_smaps_fields = true);

// Returns if the kernel supports /proc/<pid>/smaps_rollup. Assumes that the
// calling process has access to the /proc/<pid>/smaps_rollup.
// Returns 'false' if the file doesn't exist.
bool IsSmapsRollupSupported();

// Same as ProcMemInfo::SmapsOrRollup but reads the statistics directly
// from a file. The file MUST be in the same format as /proc/<pid>/smaps
// or /proc/<pid>/smaps_rollup
bool SmapsOrRollupFromFile(const std::string& path, MemUsage* stats);

// Same as ProcMemInfo::SmapsOrRollupPss but reads the statistics directly
// from a file and returns total Pss in kB. The file MUST be in the same format
// as /proc/<pid>/smaps or /proc/<pid>/smaps_rollup
bool SmapsOrRollupPssFromFile(const std::string& path, uint64_t* pss);

// Same as ProcMemInfo::StatusVmRSS but reads the statistics directly from a file.
// The file MUST be in the same format as /proc/<pid>/status.
bool StatusVmRSSFromFile(const std::string& path, uint64_t* rss);

// The output format that can be specified by user.
enum class Format { INVALID = 0, RAW, JSON, CSV };

Format GetFormat(std::string_view arg);

std::string EscapeCsvString(const std::string& raw);

std::string EscapeJsonString(const std::string& raw);

}  // namespace meminfo
}  // namespace android

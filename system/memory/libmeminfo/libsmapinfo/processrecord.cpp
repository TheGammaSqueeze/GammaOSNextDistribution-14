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

#include <inttypes.h>
#include <linux/oom.h>
#include <stdlib.h>

#include <iostream>
#include <string>
#include <vector>

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <meminfo/procmeminfo.h>

#include <processrecord.h>

namespace android {
namespace smapinfo {

using ::android::base::StringPrintf;
using ::android::meminfo::MemUsage;
using ::android::meminfo::ProcMemInfo;
using ::android::meminfo::Vma;
using ::android::meminfo::VmaCallback;

ProcessRecord::ProcessRecord(pid_t pid, bool get_wss, uint64_t pgflags, uint64_t pgflags_mask,
                             bool get_cmdline, bool get_oomadj, std::ostream& err)
    : procmem_(pid, get_wss, pgflags, pgflags_mask),
      pid_(-1),
      oomadj_(OOM_SCORE_ADJ_MAX + 1),
      proportional_swap_(0),
      unique_swap_(0),
      zswap_(0) {
    // cmdline_ only needs to be populated if this record will be used by procrank/librank.
    if (get_cmdline) {
        std::string fname = StringPrintf("/proc/%d/cmdline", pid);
        if (!::android::base::ReadFileToString(fname, &cmdline_)) {
            err << "Failed to read cmdline from: " << fname << "\n";
            cmdline_ = "<unknown>";
        }
        // We deliberately don't read the /proc/<pid>/cmdline file directly into 'cmdline_'
        // because some processes have cmdlines that end with "0x00 0x0A 0x00",
        // e.g. xtra-daemon, lowi-server.
        // The .c_str() assignment takes care of trimming the cmdline at the first 0x00. This is
        // how the original procrank worked (luckily).
        cmdline_.resize(strlen(cmdline_.c_str()));

        // If there is no cmdline (empty, not <unknown>), a kernel thread will have comm. This only
        // matters for bug reports, which output 'SHOW MAP <pid>: <cmdline>' as section titles.
        if (cmdline_.empty()) {
            fname = StringPrintf("/proc/%d/comm", pid);
            if (!::android::base::ReadFileToString(fname, &cmdline_)) {
                err << "Failed to read comm from: " << fname << "\n";
            }
            // comm seems to contain a trailing '\n' that isn't present in cmdline. dumpstate
            // surrounds kernel thread names with brackets; this behavior is maintained here.
            if (auto pos = cmdline_.find_last_of('\n'); pos != std::string::npos) {
                cmdline_.erase(pos);
            }
            cmdline_ = StringPrintf("[%s]", cmdline_.c_str());
        }
    }

    // oomadj_ only needs to be populated if this record will be used by procrank/librank.
    if (get_oomadj) {
        std::string fname = StringPrintf("/proc/%d/oom_score_adj", pid);
        std::string oom_score;
        if (!::android::base::ReadFileToString(fname, &oom_score)) {
            err << "Failed to read oom_score_adj file: " << fname << "\n";
            return;
        }
        if (!::android::base::ParseInt(::android::base::Trim(oom_score), &oomadj_)) {
            err << "Failed to parse oomadj from: " << fname << "\n";
            return;
        }
    }

    // We want to use Smaps() to populate procmem_'s maps before calling Wss() or Usage(), as
    // these will fall back on the slower ReadMaps().
    //
    // This Smaps() call is temporarily disabled because it results in
    // procmem_'s swap_offsets_ not being populated, causing procrank to not
    // report PSwap/USwap/ZSwap. Wss() and Usage() both fall back to ReadMaps(),
    // which will populate swap_offsets_.
    //
    // procmem_.Smaps("", true);
    usage_or_wss_ = get_wss ? procmem_.Wss() : procmem_.Usage();
    swap_offsets_ = procmem_.SwapOffsets();
    pid_ = pid;
}

bool ProcessRecord::valid() const {
    return pid_ != -1;
}

void ProcessRecord::CalculateSwap(const std::vector<uint16_t>& swap_offset_array,
                                  float zram_compression_ratio) {
    for (auto& off : swap_offsets_) {
        proportional_swap_ += getpagesize() / swap_offset_array[off];
        unique_swap_ += swap_offset_array[off] == 1 ? getpagesize() : 0;
        zswap_ = proportional_swap_ * zram_compression_ratio;
    }
    // This is divided by 1024 to convert to KB.
    proportional_swap_ /= 1024;
    unique_swap_ /= 1024;
    zswap_ /= 1024;
}

}  // namespace smapinfo
}  // namespace android

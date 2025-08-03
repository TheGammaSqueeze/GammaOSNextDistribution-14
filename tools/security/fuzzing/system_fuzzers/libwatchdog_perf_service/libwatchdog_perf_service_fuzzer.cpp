/*
 * Copyright 2020 The Android Open Source Project
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

#include <android-base/file.h>
#include <fuzzer/FuzzedDataProvider.h>

#include <iostream>

#include "PerformanceProfiler.h"
#include "ProcStatCollector.h"
#include "UidIoStatsCollector.h"

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::WriteStringToFile;

struct statsInfo {
    std::string instr[13];
    int len[13];
};

statsInfo info = {{"cpu", "procs_running", "procs_blocked", "cpu0", "cpu1", "cpu2", "cpu3", "intr",
                   "ctxt", "btime", "process", "procs_sleeping", "softirq"},
                  {10, 1, 1, 10, 10, 10, 10, 31, 1, 1, 1, 1, 11}};

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, std::size_t size) {
    FuzzedDataProvider fdp(data, size);
    std::string uidIoStatsSnapshot;
    std::string procStatsSnapshot;

    // Prepare for UidIoStatsCollector data
    int count = 0;
    while (fdp.remaining_bytes() > (size / 2)) {
        uint64_t val = fdp.ConsumeIntegral<uint64_t>();
        uidIoStatsSnapshot += (((count % 11) == 0 ? "" : " ") + std::to_string(val));
        if (count > 0 && count % 11 == 10) {
            uidIoStatsSnapshot += "\n";
        }
        count++;
    }

    // Prepare for ProcStats data
    while (fdp.remaining_bytes() > 5) {
        uint32_t choose = fdp.ConsumeIntegralInRange<uint32_t>(0, 3);
        uint32_t idx = choose;
        switch (choose) {
            case 0:
            case 1:
            case 2:
                break;
            case 3:
                idx = fdp.ConsumeIntegralInRange<uint32_t>(3, 12);
                break;
            default:
                break;
        }
        procStatsSnapshot += info.instr[idx];
        for (int i = 0; i < info.len[idx]; i++) {
            uint64_t val = fdp.ConsumeIntegral<uint64_t>();
            procStatsSnapshot += " " + std::to_string(val);
        }
        procStatsSnapshot += "\n";
    }

    if (uidIoStatsSnapshot.size() > 0 && procStatsSnapshot.size() > 0) {
      // Test UidIoStatsCollector
      TemporaryFile tf1;
      WriteStringToFile(uidIoStatsSnapshot, tf1.path);
      sp<UidIoStatsCollector> uidIoStatsCollector =
          sp<UidIoStatsCollector>::make(tf1.path);
      uidIoStatsCollector->collect();
      // Test procStatCollector
      TemporaryFile tf2;
      WriteStringToFile(procStatsSnapshot, tf2.path);
      sp<ProcStatCollector> procStatCollector =
          sp<ProcStatCollector>::make(tf2.path);
      procStatCollector->collect();
    }
    return 0;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

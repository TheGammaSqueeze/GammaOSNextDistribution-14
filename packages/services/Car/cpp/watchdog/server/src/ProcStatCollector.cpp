/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "carwatchdogd"

#include "ProcStatCollector.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <log/log.h>

#include <string>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::Error;
using ::android::base::ParseInt;
using ::android::base::ParseUint;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::Split;
using ::android::base::StartsWith;

namespace {

bool parseCpuStats(const std::string& data, int32_t millisPerClockTick, CpuStats* cpuStats) {
    std::vector<std::string> fields = Split(data, " ");
    if (fields.size() == 12 && fields[1].empty()) {
        /* The first cpu line will have an extra space after the first word. This will generate an
         * empty element when the line is split on " ". Erase the extra element.
         */
        fields.erase(fields.begin() + 1);
    }
    if (fields.size() != 11 || fields[0] != "cpu" ||
        !ParseInt(fields[1], &cpuStats->userTimeMillis) ||
        !ParseInt(fields[2], &cpuStats->niceTimeMillis) ||
        !ParseInt(fields[3], &cpuStats->sysTimeMillis) ||
        !ParseInt(fields[4], &cpuStats->idleTimeMillis) ||
        !ParseInt(fields[5], &cpuStats->ioWaitTimeMillis) ||
        !ParseInt(fields[6], &cpuStats->irqTimeMillis) ||
        !ParseInt(fields[7], &cpuStats->softIrqTimeMillis) ||
        !ParseInt(fields[8], &cpuStats->stealTimeMillis) ||
        !ParseInt(fields[9], &cpuStats->guestTimeMillis) ||
        !ParseInt(fields[10], &cpuStats->guestNiceTimeMillis)) {
        ALOGW("Invalid cpu line: \"%s\"", data.c_str());
        return false;
    }
    // Convert clock ticks to millis
    cpuStats->userTimeMillis *= millisPerClockTick;
    cpuStats->niceTimeMillis *= millisPerClockTick;
    cpuStats->sysTimeMillis *= millisPerClockTick;
    cpuStats->idleTimeMillis *= millisPerClockTick;
    cpuStats->ioWaitTimeMillis *= millisPerClockTick;
    cpuStats->irqTimeMillis *= millisPerClockTick;
    cpuStats->softIrqTimeMillis *= millisPerClockTick;
    cpuStats->stealTimeMillis *= millisPerClockTick;
    cpuStats->guestTimeMillis *= millisPerClockTick;
    cpuStats->guestNiceTimeMillis *= millisPerClockTick;
    return true;
}

bool parseContextSwitches(const std::string& data, uint64_t* out) {
    std::vector<std::string> fields = Split(data, " ");
    if (fields.size() != 2 || !StartsWith(fields[0], "ctxt") || !ParseUint(fields[1], out)) {
        ALOGW("Invalid ctxt line: \"%s\"", data.c_str());
        return false;
    }
    return true;
}

bool parseProcsCount(const std::string& data, uint32_t* out) {
    std::vector<std::string> fields = Split(data, " ");
    if (fields.size() != 2 || !StartsWith(fields[0], "procs_") || !ParseUint(fields[1], out)) {
        ALOGW("Invalid procs_ line: \"%s\"", data.c_str());
        return false;
    }
    return true;
}

}  // namespace

Result<void> ProcStatCollector::collect() {
    if (!mEnabled) {
        return Error() << "Cannot access " << kPath;
    }

    Mutex::Autolock lock(mMutex);
    const auto& info = getProcStatLocked();
    if (!info.ok()) {
        return Error() << "Failed to get proc stat contents: " << info.error();
    }

    mDeltaStats = *info;
    mDeltaStats -= mLatestStats;
    mLatestStats = *info;

    return {};
}

Result<ProcStatInfo> ProcStatCollector::getProcStatLocked() const {
    std::string buffer;
    if (!ReadFileToString(kPath, &buffer)) {
        return Error() << "ReadFileToString failed for " << kPath;
    }

    std::vector<std::string> lines = Split(std::move(buffer), "\n");
    ProcStatInfo info;
    bool didReadContextSwitches = false;
    bool didReadProcsRunning = false;
    bool didReadProcsBlocked = false;
    for (size_t i = 0; i < lines.size(); i++) {
        if (lines[i].empty()) {
            continue;
        }
        if (!lines[i].compare(0, 4, "cpu ")) {
            if (info.totalCpuTimeMillis() != 0) {
                return Error() << "Duplicate `cpu .*` line in " << kPath;
            }
            if (!parseCpuStats(lines[i], mMillisPerClockTick, &info.cpuStats)) {
                return Error() << "Failed to parse `cpu .*` line in " << kPath;
            }
        } else if (!lines[i].compare(0, 4, "ctxt")) {
            if (didReadContextSwitches) {
                return Error() << "Duplicate `ctxt .*` line in " << kPath;
            }
            if (!parseContextSwitches(std::move(lines[i]), &info.contextSwitchesCount)) {
                return Error() << "Failed to parse `ctxt .*` line in " << kPath;
            }
            didReadContextSwitches = true;
        } else if (!lines[i].compare(0, 6, "procs_")) {
            if (!lines[i].compare(0, 13, "procs_running")) {
                if (didReadProcsRunning) {
                    return Error() << "Duplicate `procs_running .*` line in " << kPath;
                }
                if (!parseProcsCount(std::move(lines[i]), &info.runnableProcessCount)) {
                    return Error() << "Failed to parse `procs_running .*` line in " << kPath;
                }
                didReadProcsRunning = true;
                continue;
            } else if (!lines[i].compare(0, 13, "procs_blocked")) {
                if (didReadProcsBlocked) {
                    return Error() << "Duplicate `procs_blocked .*` line in " << kPath;
                }
                if (!parseProcsCount(std::move(lines[i]), &info.ioBlockedProcessCount)) {
                    return Error() << "Failed to parse `procs_blocked .*` line in " << kPath;
                }
                didReadProcsBlocked = true;
                continue;
            }
            return Error() << "Unknown procs_ line `" << lines[i] << "` in " << kPath;
        }
    }
    if (info.totalCpuTimeMillis() == 0 || !didReadContextSwitches || !didReadProcsRunning ||
        !didReadProcsBlocked) {
        return Error() << kPath << " is incomplete";
    }
    return info;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

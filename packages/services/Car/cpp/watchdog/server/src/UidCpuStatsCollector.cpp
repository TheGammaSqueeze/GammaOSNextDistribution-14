/*
 * Copyright (c) 2022, The Android Open Source Project
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

#include "UidCpuStatsCollector.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>

#include <inttypes.h>

#include <string>
#include <unordered_map>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::android::base::EndsWith;
using ::android::base::Error;
using ::android::base::ParseInt;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::Split;

enum class ReadError : int {
    ERR_INVALID_FILE,
    ERR_FILE_OPEN_READ,
    NUM_ERRORS,
};

/**
 * Returns a map of CPU time in milliseconds spent by each UID since system boot up.
 *
 * /proc/uid_cputime/show_uid_stat file format:
 * <uid>: <user_time_micro_seconds> <system_time_micro_seconds>
 */
Result<std::unordered_map<uid_t, int64_t>> readUidCpuTimeFile(const std::string& path) {
    std::string buffer;
    if (!ReadFileToString(path, &buffer)) {
        return Error(static_cast<int>(ReadError::ERR_FILE_OPEN_READ))
                << "ReadFileToString failed for " << path;
    }
    std::unordered_map<uid_t, int64_t> cpuTimeMillisByUid;
    std::vector<std::string> lines = Split(buffer, "\n");
    for (size_t i = 0; i < lines.size(); i++) {
        if (lines[i].empty()) {
            continue;
        }
        const std::string delimiter = " ";
        std::vector<std::string> elements = Split(lines[i], delimiter);
        if (elements.size() < 3) {
            return Error(static_cast<int>(ReadError::ERR_INVALID_FILE))
                    << "Line \"" << lines[i] << "\" doesn't contain the delimiter \"" << delimiter
                    << "\" in file " << path;
        }
        if (EndsWith(elements[0], ":")) {
            elements[0].pop_back();
        }
        int64_t uid = -1;
        int64_t userCpuTimeUs = 0;
        int64_t systemCpuTimeUs = 0;
        if (!ParseInt(elements[0], &uid) || !ParseInt(elements[1], &userCpuTimeUs) ||
            !ParseInt(elements[2], &systemCpuTimeUs)) {
            return Error(static_cast<int>(ReadError::ERR_INVALID_FILE))
                    << "Failed to parse line from file: " << path << ", error: line " << lines[i]
                    << " has invalid format";
        }
        if (cpuTimeMillisByUid.find(uid) != cpuTimeMillisByUid.end()) {
            return Error(static_cast<int>(ReadError::ERR_INVALID_FILE))
                    << "Duplicate " << uid << " line: \"" << lines[i] << "\" in file " << path;
        }
        // Store CPU time as milliseconds
        cpuTimeMillisByUid[uid] = userCpuTimeUs / 1000 + systemCpuTimeUs / 1000;
    }
    if (cpuTimeMillisByUid.empty()) {
        return Error(static_cast<int>(ReadError::ERR_INVALID_FILE)) << "Empty file: " << path;
    }
    return cpuTimeMillisByUid;
}

}  // namespace

Result<void> UidCpuStatsCollector::collect() {
    Mutex::Autolock lock(mMutex);
    if (!mEnabled) {
        return Error() << "Can not access: " << mPath;
    }
    auto cpuTimeMillisByUid = readUidCpuTimeFile(mPath);
    if (!cpuTimeMillisByUid.ok()) {
        return Error(cpuTimeMillisByUid.error().code())
                << "Failed to read top-level per UID CPU time file " << mPath << ": "
                << cpuTimeMillisByUid.error().message();
    }

    mDeltaStats.clear();
    for (const auto& [uid, cpuTime] : *cpuTimeMillisByUid) {
        if (cpuTime == 0) {
            continue;
        }
        int64_t deltaCpuTime = cpuTime;
        if (const auto& it = mLatestStats.find(uid);
            it != mLatestStats.end() && it->second <= deltaCpuTime) {
            deltaCpuTime -= it->second;
            if (deltaCpuTime == 0) {
                continue;
            }
        }
        mDeltaStats[uid] = deltaCpuTime;
    }
    mLatestStats = std::move(*cpuTimeMillisByUid);
    return {};
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

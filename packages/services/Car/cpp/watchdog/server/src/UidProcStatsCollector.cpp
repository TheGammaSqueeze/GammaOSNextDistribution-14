/*
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
#define DEBUG false  // STOPSHIP if true.

#include "UidProcStatsCollector.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <log/log.h>

#include <dirent.h>

#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::EndsWith;
using ::android::base::Error;
using ::android::base::ParseInt;
using ::android::base::ParseUint;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::Split;
using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::Trim;

namespace {

constexpr uint64_t kMaxUint64 = std::numeric_limits<uint64_t>::max();

constexpr const char* kProcPidStatFileFormat = "/proc/%" PRIu32 "/stat";
constexpr const char* kProcPidStatusFileFormat = "/proc/%" PRIu32 "/status";

enum ReadStatus {
    // Default value is an error for backwards compatibility with the Result::ErrorCode.
    READ_ERROR = 0,
    // PIDs may disappear between scanning and reading directory/files. Use |READ_WARNING| in these
    // instances to return the missing directory/file for logging purposes.
    READ_WARNING = 1,
    NUM_READ_STATUS = 2,
};

uint64_t addUint64(const uint64_t& l, const uint64_t& r) {
    return (kMaxUint64 - l) > r ? (l + r) : kMaxUint64;
}

/**
 * /proc/PID/stat or /proc/PID/task/TID/stat format:
 * <pid> <comm> <state> <ppid> <pgrp ID> <session ID> <tty_nr> <tpgid> <flags> <minor faults>
 * <children minor faults> <major faults> <children major faults> <user mode time>
 * <system mode time> <children user mode time> <children kernel mode time> <priority> <nice value>
 * <num threads> <start time since boot> <virtual memory size> <resident set size> <rss soft limit>
 * <start code addr> <end code addr> <start stack addr> <ESP value> <EIP> <bitmap of pending sigs>
 * <bitmap of blocked sigs> <bitmap of ignored sigs> <waiting channel> <num pages swapped>
 * <cumulative pages swapped> <exit signal> <processor #> <real-time prio> <agg block I/O delays>
 * <guest time> <children guest time> <start data addr> <end data addr> <start break addr>
 * <cmd line args start addr> <amd line args end addr> <env start addr> <env end addr> <exit code>
 * Example line: 1 (init) S 0 0 0 0 0 0 0 0 220 0 0 0 0 0 0 0 2 0 0 ...etc...
 */
bool parsePidStatLine(const std::string& line, PidStat* pidStat) {
    std::vector<std::string> fields = Split(line, " ");

    /* Note: Regex parsing for the below logic increased the time taken to run the
     * UidProcStatsCollectorTest#TestProcPidStatContentsFromDevice from 151.7ms to 1.3 seconds.
     *
     * Comm string is enclosed with ( ) brackets and may contain space(s). Thus calculate the
     * commEndOffset based on the field that contains the closing bracket.
     */
    size_t commEndOffset = 0;
    for (size_t i = 1; i < fields.size(); ++i) {
        pidStat->comm += fields[i];
        if (EndsWith(fields[i], ")")) {
            commEndOffset = i - 1;
            break;
        }
        pidStat->comm += " ";
    }

    if (pidStat->comm.front() != '(' || pidStat->comm.back() != ')') {
        ALOGD("Comm string `%s` not enclosed in brackets", pidStat->comm.c_str());
        return false;
    }
    pidStat->comm.erase(pidStat->comm.begin());
    pidStat->comm.erase(pidStat->comm.end() - 1);

    int64_t systemCpuTime = 0;
    if (fields.size() < 22 + commEndOffset ||
        !ParseUint(fields[11 + commEndOffset], &pidStat->majorFaults) ||
        !ParseInt(fields[13 + commEndOffset], &pidStat->cpuTimeMillis) ||
        !ParseInt(fields[14 + commEndOffset], &systemCpuTime) ||
        !ParseInt(fields[21 + commEndOffset], &pidStat->startTimeMillis)) {
        ALOGD("Invalid proc pid stat contents: \"%s\"", line.c_str());
        return false;
    }
    pidStat->cpuTimeMillis += systemCpuTime;
    pidStat->state = fields[2 + commEndOffset];
    return true;
}

Result<PidStat> readPidStatFile(const std::string& path, int32_t millisPerClockTick) {
    std::string buffer;
    if (!ReadFileToString(path, &buffer)) {
        return Error(READ_WARNING) << "ReadFileToString failed for " << path;
    }
    std::vector<std::string> lines = Split(std::move(buffer), "\n");
    if (lines.size() != 1 && (lines.size() != 2 || !lines[1].empty())) {
        return Error(READ_ERROR) << path << " contains " << lines.size() << " lines != 1";
    }
    PidStat pidStat;
    if (!parsePidStatLine(std::move(lines[0]), &pidStat)) {
        return Error(READ_ERROR) << "Failed to parse the contents of " << path;
    }
    pidStat.startTimeMillis = pidStat.startTimeMillis * millisPerClockTick;
    pidStat.cpuTimeMillis = pidStat.cpuTimeMillis * millisPerClockTick;
    return pidStat;
}

std::vector<std::string> getLinesWithTags(std::string_view buffer,
                                          const std::vector<std::string>& tags) {
    std::vector<std::string> result;
    std::vector<std::string> notFoundTags(tags);
    size_t base = 0;
    std::string_view sub;
    for (size_t found = 0; !notFoundTags.empty() && found != buffer.npos;) {
        found = buffer.find_first_of('\n', base);
        sub = buffer.substr(base, found - base);
        bool hasTag = false;
        for (auto it = notFoundTags.begin(); it != notFoundTags.end();) {
            if (sub.find(*it) != sub.npos) {
                notFoundTags.erase(it);
                hasTag = true;
            } else {
                ++it;
            }
        }
        if (hasTag) {
            result.push_back(std::string{sub});
        }
        base = found + 1;
    }
    return result;
}

Result<std::unordered_map<std::string, std::string>> readKeyValueFile(
        const std::string& path, const std::string& delimiter,
        const std::vector<std::string>& tags) {
    std::string buffer;
    if (!ReadFileToString(path, &buffer)) {
        return Error(READ_WARNING) << "ReadFileToString failed for " << path;
    }
    std::vector<std::string> lines = getLinesWithTags(buffer, tags);
    std::unordered_map<std::string, std::string> contents;
    for (size_t i = 0; i < lines.size(); ++i) {
        if (lines[i].empty()) {
            continue;
        }
        std::vector<std::string> elements = Split(lines[i], delimiter);
        if (elements.size() < 2) {
            return Error(READ_ERROR)
                    << "Line \"" << lines[i] << "\" doesn't contain the delimiter \"" << delimiter
                    << "\" in file " << path;
        }
        std::string key = elements[0];
        std::string value = Trim(lines[i].substr(key.length() + delimiter.length()));
        if (contents.find(key) != contents.end()) {
            return Error(READ_ERROR)
                    << "Duplicate " << key << " line: \"" << lines[i] << "\" in file " << path;
        }
        contents[key] = value;
    }
    return contents;
}

/**
 * Returns UID and TGID from the given pid status file.
 *
 * /proc/PID/status file format:
 * Tgid:    <Thread group ID of the process>
 * Uid:     <Read UID>   <Effective UID>   <Saved set UID>   <Filesystem UID>
 *
 * Note: Included only the fields that are parsed from the file.
 */
Result<std::tuple<uid_t, pid_t>> readPidStatusFile(const std::string& path) {
    auto result = readKeyValueFile(path, ":\t", {"Uid", "Tgid"});
    if (!result.ok()) {
        return Error(result.error().code()) << result.error();
    }
    auto contents = result.value();
    if (contents.empty()) {
        return Error(READ_ERROR) << "Empty file " << path;
    }
    int64_t uid = 0;
    int64_t tgid = 0;
    if (contents.find("Uid") == contents.end() ||
        !ParseInt(Split(contents["Uid"], "\t")[0], &uid)) {
        return Error(READ_ERROR) << "Failed to read 'UID' from file " << path;
    }
    if (contents.find("Tgid") == contents.end() || !ParseInt(contents["Tgid"], &tgid)) {
        return Error(READ_ERROR) << "Failed to read 'Tgid' from file " << path;
    }
    return std::make_tuple(uid, tgid);
}

/**
 * Returns the total CPU cycles from the given time_in_state file.
 *
 * /proc/PID/task/TID/time_in_state file format:
 * cpuX
 * <CPU freq (kHz)> <time spent at freq (clock ticks)>
 * <CPU freq (kHz)> <time spent at freq (clock ticks)>
 * ...
 * cpuY
 * <CPU freq (kHz)> <time spent at freq (clock ticks)>
 * <CPU freq (kHz)> <time spent at freq (clock ticks)>
 * ...
 *
 * Note: Each 'cpuX' header refers to a particular CPU freq policy. A policy can contain multiple
 * cores. Since we gather the time spent at a frequency at the thread level, their is no need
 * aggregate the time across cores because threads only run in one core at a time.
 */
Result<uint64_t> readTimeInStateFile(const std::string& path) {
    const auto mul = [](const uint64_t& l, const uint64_t& r) -> uint64_t {
        if (l == 0 || r == 0) {
            return 0;
        }
        return (kMaxUint64 / r) > l ? (l * r) : kMaxUint64;
    };

    std::string buffer;
    if (!ReadFileToString(path, &buffer)) {
        return Error(READ_WARNING) << "ReadFileToString failed for " << path;
    }
    std::string delimiter = " ";
    uint64_t oneTenthCpuCycles = 0;
    const uint64_t cyclesPerKHzClockTicks = 1000 / sysconf(_SC_CLK_TCK);
    std::vector<std::string> lines = Split(buffer, "\n");
    for (size_t i = 0; i < lines.size(); ++i) {
        if (lines[i].empty() || StartsWith(lines[i], "cpu")) {
            continue;
        }
        std::vector<std::string> elements = Split(lines[i], delimiter);
        if (elements.size() < 2) {
            return Error(READ_ERROR)
                    << "Line \"" << lines[i] << "\" doesn't contain the delimiter \"" << delimiter
                    << "\" in file " << path;
        }
        uint64_t freqKHz;
        uint64_t clockTicks;
        if (!ParseUint(elements[0], &freqKHz) || !ParseUint(Trim(elements[1]), &clockTicks)) {
            return Error(READ_ERROR)
                    << "Line \"" << lines[i] << "\" has invalid format in file " << path;
        }
        oneTenthCpuCycles = addUint64(oneTenthCpuCycles, mul(freqKHz, clockTicks));
    }
    // The frequency is in kHz and the time is in clock ticks (10ms). In order to obtain cycles,
    // one has to scale the frequency by 1000 to obtain Hz and the time by 1/sysconf(_SC_CLK_TCK)
    // to obtain seconds which results in scaling the result by |cyclesPerKHzClockTicks|.
    return mul(oneTenthCpuCycles, cyclesPerKHzClockTicks);
}

}  // namespace

std::string ProcessStats::toString() const {
    std::string buffer;
    StringAppendF(&buffer,
                  "{comm: %s, startTimeMillis: %" PRIu64 ", cpuTimeMillis: %" PRIu64
                  ", totalCpuCycles: %" PRIu64 ", totalMajorFaults: %" PRIu64
                  ", totalTasksCount: %d, ioBlockedTasksCount: %d, cpuCyclesByTid: {",
                  comm.c_str(), startTimeMillis, cpuTimeMillis, totalCpuCycles, totalMajorFaults,
                  totalTasksCount, ioBlockedTasksCount);
    for (const auto& [tid, cpuCycles] : cpuCyclesByTid) {
        StringAppendF(&buffer, "{tid: %d, cpuCycles: %" PRIu64 "},", tid, cpuCycles);
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

std::string UidProcStats::toString() const {
    std::string buffer;
    StringAppendF(&buffer,
                  "UidProcStats{cpuTimeMillis: %" PRIu64 ", cpuCycles: %" PRIu64
                  ", totalMajorFaults: %" PRIu64 ", totalTasksCount: %d, ioBlockedTasksCount: %d"
                  ", processStatsByPid: {",
                  cpuTimeMillis, cpuCycles, totalMajorFaults, totalTasksCount, ioBlockedTasksCount);
    for (const auto& [pid, processStats] : processStatsByPid) {
        StringAppendF(&buffer, "{pid: %" PRIi32 ", processStats: %s},", pid,
                      processStats.toString().c_str());
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

void UidProcStatsCollector::init() {
    // Note: Verify proc file access outside the constructor. Otherwise, the unittests of
    // dependent classes would call the constructor before mocking and get killed due to
    // sepolicy violation.
    std::string pidStatPath = StringPrintf((mPath + kStatFileFormat).c_str(), PID_FOR_INIT);
    std::string tidStatPath = StringPrintf((mPath + kTaskDirFormat + kStatFileFormat).c_str(),
                                           PID_FOR_INIT, PID_FOR_INIT);
    std::string pidStatusPath = StringPrintf((mPath + kStatusFileFormat).c_str(), PID_FOR_INIT);
    std::string tidTimeInStatePath =
            StringPrintf((mPath + kTaskDirFormat + kTimeInStateFormat).c_str(), PID_FOR_INIT,
                         PID_FOR_INIT);

    Mutex::Autolock lock(mMutex);
    mEnabled = access(pidStatPath.c_str(), R_OK) == 0 && access(tidStatPath.c_str(), R_OK) == 0 &&
            access(pidStatusPath.c_str(), R_OK) == 0;

    mTimeInStateEnabled = access(tidTimeInStatePath.c_str(), R_OK) == 0;
    if (mTimeInStateEnabled) {
        auto tidCpuCycles = readTimeInStateFile(tidTimeInStatePath);
        mTimeInStateEnabled = tidCpuCycles.ok() && *tidCpuCycles > 0;
    }
    if (!mTimeInStateEnabled) {
        ALOGW("Time in state files are not enabled. Missing time in state file at path: %s",
              tidTimeInStatePath.c_str());
    }
}

Result<void> UidProcStatsCollector::collect() {
    if (!mEnabled) {
        return Error() << "Can not access PID stat files under " << kProcDirPath;
    }

    Mutex::Autolock lock(mMutex);
    auto uidProcStatsByUid = readUidProcStatsLocked();
    if (!uidProcStatsByUid.ok()) {
        return Error() << uidProcStatsByUid.error();
    }

    mDeltaStats.clear();
    for (const auto& [uid, currUidStats] : *uidProcStatsByUid) {
        if (const auto& it = mLatestStats.find(uid); it == mLatestStats.end()) {
            mDeltaStats[uid] = currUidStats;
            continue;
        }
        const auto& prevUidStats = mLatestStats[uid];
        UidProcStats deltaUidStats = {
                .totalTasksCount = currUidStats.totalTasksCount,
                .ioBlockedTasksCount = currUidStats.ioBlockedTasksCount,
        };
        // Generate the delta stats since the previous collection. Delta stats are generated by
        // calculating the difference between the |prevUidStats| and the |currUidStats|.
        for (const auto& [pid, processStats] : currUidStats.processStatsByPid) {
            ProcessStats deltaProcessStats = processStats;
            if (const auto& it = prevUidStats.processStatsByPid.find(pid);
                it != prevUidStats.processStatsByPid.end() &&
                it->second.startTimeMillis == deltaProcessStats.startTimeMillis) {
                auto prevProcessStats = it->second;
                if (prevProcessStats.cpuTimeMillis <= deltaProcessStats.cpuTimeMillis) {
                    deltaProcessStats.cpuTimeMillis -= prevProcessStats.cpuTimeMillis;
                }
                if (prevProcessStats.totalMajorFaults <= deltaProcessStats.totalMajorFaults) {
                    deltaProcessStats.totalMajorFaults -= prevProcessStats.totalMajorFaults;
                }
                // Generate the process delta CPU cycles by iterating through the thread-level CPU
                // cycles and calculating the sum of the deltas of each thread.
                deltaProcessStats.totalCpuCycles = 0;
                for (const auto& [tid, threadCpuCycles] : processStats.cpuCyclesByTid) {
                    uint64_t deltaThreadCpuCycles = threadCpuCycles;
                    if (const auto& cIt = prevProcessStats.cpuCyclesByTid.find(tid);
                        cIt != prevProcessStats.cpuCyclesByTid.end() &&
                        cIt->second <= deltaThreadCpuCycles) {
                        deltaThreadCpuCycles -= cIt->second;
                    }
                    deltaProcessStats.cpuCyclesByTid[tid] = deltaThreadCpuCycles;
                    deltaProcessStats.totalCpuCycles =
                            addUint64(deltaProcessStats.totalCpuCycles, deltaThreadCpuCycles);
                }
            }
            deltaUidStats.cpuTimeMillis += deltaProcessStats.cpuTimeMillis;
            deltaUidStats.cpuCycles =
                    addUint64(deltaUidStats.cpuCycles, deltaProcessStats.totalCpuCycles);
            deltaUidStats.totalMajorFaults += deltaProcessStats.totalMajorFaults;
            deltaUidStats.processStatsByPid[pid] = deltaProcessStats;
        }
        mDeltaStats[uid] = std::move(deltaUidStats);
    }
    mLatestStats = std::move(*uidProcStatsByUid);
    return {};
}

Result<std::unordered_map<uid_t, UidProcStats>> UidProcStatsCollector::readUidProcStatsLocked()
        const {
    std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid;
    auto procDirp = std::unique_ptr<DIR, int (*)(DIR*)>(opendir(mPath.c_str()), closedir);
    if (!procDirp) {
        return Error() << "Failed to open " << mPath << " directory";
    }
    for (dirent* pidDir = nullptr; (pidDir = readdir(procDirp.get())) != nullptr;) {
        pid_t pid = 0;
        if (pidDir->d_type != DT_DIR || !ParseInt(pidDir->d_name, &pid)) {
            continue;
        }
        auto result = readProcessStatsLocked(pid);
        if (!result.ok()) {
            if (result.error().code() != READ_WARNING) {
                return Error() << result.error();
            }
            if (DEBUG) {
                ALOGD("%s", result.error().message().c_str());
            }
            continue;
        }
        uid_t uid = std::get<0>(*result);
        ProcessStats processStats = std::get<ProcessStats>(*result);
        if (uidProcStatsByUid.find(uid) == uidProcStatsByUid.end()) {
            uidProcStatsByUid[uid] = {};
        }
        UidProcStats* uidProcStats = &uidProcStatsByUid[uid];
        uidProcStats->cpuTimeMillis += processStats.cpuTimeMillis;
        uidProcStats->cpuCycles = addUint64(uidProcStats->cpuCycles, processStats.totalCpuCycles);
        uidProcStats->totalMajorFaults += processStats.totalMajorFaults;
        uidProcStats->totalTasksCount += processStats.totalTasksCount;
        uidProcStats->ioBlockedTasksCount += processStats.ioBlockedTasksCount;
        uidProcStats->processStatsByPid[pid] = std::move(processStats);
    }
    return uidProcStatsByUid;
}

Result<std::tuple<uid_t, ProcessStats>> UidProcStatsCollector::readProcessStatsLocked(
        pid_t pid) const {
    // 1. Read top-level pid stats.
    std::string path = StringPrintf((mPath + kStatFileFormat).c_str(), pid);
    auto pidStat = readPidStatFile(path, mMillisPerClockTick);
    if (!pidStat.ok()) {
        return Error(pidStat.error().code())
                << "Failed to read top-level per-process stat file '%s': %s"
                << pidStat.error().message().c_str();
    }

    // 2. Read aggregated process status.
    pid_t tgid = -1;
    uid_t uid = -1;
    path = StringPrintf((mPath + kStatusFileFormat).c_str(), pid);
    if (auto result = readPidStatusFile(path); !result.ok()) {
        if (result.error().code() != READ_WARNING) {
            return Error() << "Failed to read pid status for pid " << pid << ": "
                           << result.error().message().c_str();
        }
        for (const auto& [curUid, uidProcStats] : mLatestStats) {
            if (const auto it = uidProcStats.processStatsByPid.find(pid);
                it != uidProcStats.processStatsByPid.end() &&
                it->second.startTimeMillis == pidStat->startTimeMillis) {
                tgid = pid;
                uid = curUid;
                break;
            }
        }
    } else {
        uid = std::get<0>(*result);
        tgid = std::get<1>(*result);
    }

    if (uid == static_cast<uid_t>(-1) || tgid != pid) {
        return Error(READ_WARNING)
                << "Skipping PID '" << pid << "' because either Tgid != PID or invalid UID";
    }

    ProcessStats processStats = {
            .comm = std::move(pidStat->comm),
            .startTimeMillis = pidStat->startTimeMillis,
            .cpuTimeMillis = pidStat->cpuTimeMillis,
            .totalCpuCycles = 0,
            .totalTasksCount = 1,
            /* Top-level process stats has the aggregated major page faults count and this should be
             * persistent across thread creation/termination. Thus use the value from this field.
             */
            .totalMajorFaults = pidStat->majorFaults,
            .ioBlockedTasksCount = pidStat->state == "D" ? 1 : 0,
            .cpuCyclesByTid = {},
    };

    // 3. Read per-thread stats.
    std::string taskDir = StringPrintf((mPath + kTaskDirFormat).c_str(), pid);
    bool didReadMainThread = false;
    auto taskDirp = std::unique_ptr<DIR, int (*)(DIR*)>(opendir(taskDir.c_str()), closedir);
    for (dirent* tidDir = nullptr;
         taskDirp != nullptr && (tidDir = readdir(taskDirp.get())) != nullptr;) {
        pid_t tid = 0;
        if (tidDir->d_type != DT_DIR || !ParseInt(tidDir->d_name, &tid)) {
            continue;
        }

        if (tid != pid) {
            path = StringPrintf((taskDir + kStatFileFormat).c_str(), tid);
            auto tidStat = readPidStatFile(path, mMillisPerClockTick);
            if (!tidStat.ok()) {
                if (tidStat.error().code() != READ_WARNING) {
                    return Error() << "Failed to read per-thread stat file: "
                                   << tidStat.error().message().c_str();
                }
                /* Maybe the thread terminated before reading the file so skip this thread and
                 * continue with scanning the next thread's stat.
                 */
                continue;
            }

            processStats.ioBlockedTasksCount += tidStat->state == "D" ? 1 : 0;
            processStats.totalTasksCount += 1;
        }

        if (!mTimeInStateEnabled) {
            continue;
        }

        path = StringPrintf((taskDir + kTimeInStateFormat).c_str(), tid);
        auto tidCpuCycles = readTimeInStateFile(path);
        if (!tidCpuCycles.ok() || *tidCpuCycles <= 0) {
            if (!tidCpuCycles.ok() && tidCpuCycles.error().code() != READ_WARNING) {
                return Error() << "Failed to read per-thread time_in_state file: "
                               << tidCpuCycles.error().message().c_str();
            }
            // time_in_state file might not be supported by the Kernel (when the Kernel configs
            // CPU_FREQ_STAT or CPU_FREQ_TIMES are not be enabled or the governor doesn't report the
            // CPU transition states to the Kernel CPU frequency node). Or non-positive CPU cycles
            // calculated. Or maybe the thread terminated before reading the file so skip this
            // thread and continue with scanning the next thread's stat.
            continue;
        }

        processStats.totalCpuCycles = addUint64(processStats.totalCpuCycles, *tidCpuCycles);
        processStats.cpuCyclesByTid[tid] = *tidCpuCycles;
    }
    return std::make_tuple(uid, processStats);
}

Result<PidStat> UidProcStatsCollector::readStatFileForPid(pid_t pid) {
    std::string path = StringPrintf(kProcPidStatFileFormat, pid);
    return readPidStatFile(path, 1000 / sysconf(_SC_CLK_TCK));
}

Result<std::tuple<uid_t, pid_t>> UidProcStatsCollector::readPidStatusFileForPid(pid_t pid) {
    std::string path = StringPrintf(kProcPidStatusFileFormat, pid);
    return readPidStatusFile(path);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

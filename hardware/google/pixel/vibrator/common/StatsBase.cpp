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

#include "StatsBase.h"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <log/log.h>
#include <utils/Trace.h>

#include <chrono>
#include <sstream>

using ::aidl::android::frameworks::stats::IStats;
using ::aidl::android::frameworks::stats::VendorAtom;
using ::aidl::android::frameworks::stats::VendorAtomValue;

namespace PixelAtoms = ::android::hardware::google::pixel::PixelAtoms;

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (sizeof((x)) / sizeof((x)[0]))
#endif

#ifdef TRACE_STATS
static const char *kAtomLookup[] = {"HAPTICS_PLAYCOUNTS", "HAPTICS_LATENCIES", "HAPTICS_ERRORS",
                                    "INVALID"};

const char *atomToString(uint32_t atomId) {
    switch (atomId) {
        case PixelAtoms::Atom::kVibratorPlaycountReported:
            return kAtomLookup[0];
            break;
        case PixelAtoms::Atom::kVibratorLatencyReported:
            return kAtomLookup[1];
            break;
        case PixelAtoms::Atom::kVibratorErrorsReported:
            return kAtomLookup[2];
            break;
        default:
            return kAtomLookup[ARRAY_SIZE(kAtomLookup) - 1];
            break;
    }
}

#define STATS_TRACE(...)   \
    ATRACE_NAME(__func__); \
    ALOGD(__VA_ARGS__)
#else
#define STATS_TRACE(...) ATRACE_NAME(__func__)
#define atomToString(x)
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

#ifdef FAST_LOG
static constexpr auto UPLOAD_INTERVAL = std::chrono::minutes(1);
#else
static constexpr auto UPLOAD_INTERVAL = std::chrono::hours(24);
#endif

static void reportVendorAtom(const std::shared_ptr<IStats> &statsClient, const VendorAtom &atom) {
    STATS_TRACE("   reportVendorAtom(statsClient, atom: %s)", atomToString(atom.atomId));
    const ndk::ScopedAStatus status = statsClient->reportVendorAtom(atom);
    if (status.isOk()) {
        ALOGI("Vendor atom [id = %d] reported.", atom.atomId);
    } else {
        ALOGE("Failed to report atom [id = %d].", atom.atomId);
    }
}

static std::string dumpData(const std::vector<int32_t> &data) {
    std::stringstream stream;
    for (auto datum : data) {
        stream << " " << datum;
    }
    return stream.str();
}

StatsBase::StatsBase(const std::string &instance)
    : mReporterThread([this]() { runReporterThread(); }),
      kStatsInstanceName(std::string() + IStats::descriptor + "/" + instance) {}

StatsBase::~StatsBase() {}

void StatsBase::debug(int fd) {
    STATS_TRACE("debug(fd: %d)", fd);

    dprintf(fd, "Stats:\n");
    {
        std::scoped_lock<std::mutex> lock(mDataAccess);
        dprintf(fd, "  Waveform Counts:%s\n", dumpData(mWaveformCounts).c_str());
        dprintf(fd, "  Duration Counts:%s\n", dumpData(mDurationCounts).c_str());
        dprintf(fd, "  Min Latencies:%s\n", dumpData(mMinLatencies).c_str());
        dprintf(fd, "  Max Latencies:%s\n", dumpData(mMaxLatencies).c_str());
        dprintf(fd, "  Latency Totals:%s\n", dumpData(mLatencyTotals).c_str());
        dprintf(fd, "  Latency Counts:%s\n", dumpData(mLatencyCounts).c_str());
        dprintf(fd, "  Error Counts: %s\n", dumpData(mErrorCounts).c_str());
    }
}

void StatsBase::reportVendorAtomAsync(const VendorAtom &atom) {
    STATS_TRACE("reportVendorAtomAsync(atom: %s)", atomToString(atom.atomId));
    std::scoped_lock<std::mutex> lock(mAtomQueueAccess);
    mAtomQueue.push_back(atom);
    mAtomQueueUpdated.notify_all();
}

void StatsBase::uploadDiagnostics() {
    STATS_TRACE("uploadDiagnostics()");
    uploadPlaycountAtoms();
    uploadLatencyAtoms();
    uploadErrorAtoms();
}

std::shared_ptr<IStats> StatsBase::waitForStatsService() const {
    STATS_TRACE("waitForStatsService()");
    if (!AServiceManager_isDeclared(kStatsInstanceName.c_str())) {
        ALOGE("IStats service '%s' is not registered.", kStatsInstanceName.c_str());
        return nullptr;
    }

    ALOGI("Waiting for IStats service '%s' to come up.", kStatsInstanceName.c_str());
    std::shared_ptr<IStats> client = IStats::fromBinder(
            ndk::SpAIBinder(AServiceManager_waitForService(kStatsInstanceName.c_str())));
    if (!client) {
        ALOGE("Failed to get IStats service '%s'.", kStatsInstanceName.c_str());
        return nullptr;
    }
    ALOGI("IStats service online.");
    return client;
}

void StatsBase::runReporterThread() {
    STATS_TRACE("runReporterThread()");
    using clock = std::chrono::steady_clock;
    auto nextUpload = clock::now() + UPLOAD_INTERVAL;
    auto status = std::cv_status::no_timeout;

    while (!mTerminateReporterThread) {
        drainAtomQueue();
        {
            std::unique_lock<std::mutex> lock(mAtomQueueAccess);
            if (!mAtomQueue.empty())
                continue;
            status = mAtomQueueUpdated.wait_until(lock, nextUpload);
        }

        if (status == std::cv_status::timeout) {
            nextUpload = clock::now() + UPLOAD_INTERVAL;
            uploadDiagnostics();
        }
    }
}

void StatsBase::drainAtomQueue() {
    STATS_TRACE("drainAtomQueue()");
    std::vector<VendorAtom> tempQueue;
    {
        std::unique_lock<std::mutex> lock(mAtomQueueAccess);
        std::swap(mAtomQueue, tempQueue);
    }

    std::shared_ptr<IStats> client = waitForStatsService();
    if (!client) {
        ALOGE("Failed to get IStats service. Atoms are dropped.");
        return;
    }

    for (const VendorAtom &atom : tempQueue) {
        reportVendorAtom(client, atom);
    }
}

void StatsBase::uploadPlaycountAtoms() {
    STATS_TRACE("uploadPlaycountAtoms()");
    VendorAtom playcountAtom = vibratorPlaycountAtom();
    reportVendorAtomAsync(playcountAtom);
    clearData(&mWaveformCounts);
    clearData(&mDurationCounts);
}

void StatsBase::uploadLatencyAtoms() {
    STATS_TRACE("uploadLatencyAtoms()");
    VendorAtom latencyAtom = vibratorLatencyAtom();
    reportVendorAtomAsync(latencyAtom);
    clearData(&mMinLatencies);
    clearData(&mMaxLatencies);
    clearData(&mLatencyTotals);
    clearData(&mLatencyCounts);
}

void StatsBase::uploadErrorAtoms() {
    STATS_TRACE("uploadErrorAtoms()");
    VendorAtom errorAtom = vibratorErrorAtom();
    reportVendorAtomAsync(errorAtom);
    clearData(&mErrorCounts);
}

void StatsBase::clearData(std::vector<int32_t> *data) {
    STATS_TRACE("clearData(data)");
    if (data) {
        std::scoped_lock<std::mutex> lock(mDataAccess);
        std::fill((*data).begin(), (*data).end(), 0);
    }
}

VendorAtom StatsBase::vibratorPlaycountAtom() {
    STATS_TRACE("vibratorPlaycountAtom()");
    std::vector<VendorAtomValue> values(2);

    {
        std::scoped_lock<std::mutex> lock(mDataAccess);
        values[0].set<VendorAtomValue::repeatedIntValue>(mWaveformCounts);
        values[1].set<VendorAtomValue::repeatedIntValue>(mDurationCounts);
    }

    return VendorAtom{
            .reverseDomainName = "",
            .atomId = PixelAtoms::Atom::kVibratorPlaycountReported,
            .values = std::move(values),
    };
}

VendorAtom StatsBase::vibratorLatencyAtom() {
    STATS_TRACE("vibratorLatencyAtom()");
    std::vector<VendorAtomValue> values(3);
    std::vector<int32_t> avgLatencies;

    {
        std::scoped_lock<std::mutex> lock(mDataAccess);
        for (uint32_t i = 0; i < mLatencyCounts.size(); i++) {
            int32_t avg = 0;
            if (mLatencyCounts[0] > 0) {
                avg = mLatencyTotals[i] / mLatencyCounts[i];
            }
            avgLatencies.push_back(avg);
        }

        values[0].set<VendorAtomValue::repeatedIntValue>(mMinLatencies);
        values[1].set<VendorAtomValue::repeatedIntValue>(mMaxLatencies);
    }
    values[2].set<VendorAtomValue::repeatedIntValue>(avgLatencies);

    return VendorAtom{
            .reverseDomainName = "",
            .atomId = PixelAtoms::Atom::kVibratorLatencyReported,
            .values = std::move(values),
    };
}

VendorAtom StatsBase::vibratorErrorAtom() {
    STATS_TRACE("vibratorErrorAtom()");
    std::vector<VendorAtomValue> values(1);

    {
        std::scoped_lock<std::mutex> lock(mDataAccess);
        values[0].set<VendorAtomValue::repeatedIntValue>(mErrorCounts);
    }

    return VendorAtom{
            .reverseDomainName = "",
            .atomId = PixelAtoms::Atom::kVibratorErrorsReported,
            .values = std::move(values),
    };
}

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl

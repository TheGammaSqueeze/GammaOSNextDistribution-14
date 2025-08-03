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
#pragma once

#include <utils/SystemClock.h>

#include <cinttypes>
#include <mutex>
#include <thread>
#include <vector>

/* Forward declaration to speed-up build and avoid build errors. Clients of this
 * library force to use C++11 std, when AIDL auto-generated code uses features
 * from more recent C++ version. */
namespace aidl {
namespace android {
namespace frameworks {
namespace stats {

class VendorAtom;
class IStats;

}  // namespace stats
}  // namespace frameworks
}  // namespace android
}  // namespace aidl

namespace aidl {
namespace android {
namespace hardware {
namespace vibrator {

class StatsBase {
  public:
    using VendorAtom = ::aidl::android::frameworks::stats::VendorAtom;
    using IStats = ::aidl::android::frameworks::stats::IStats;

    StatsBase(const std::string &instance);
    ~StatsBase();

    void debug(int fd);

  protected:
    std::vector<int32_t> mWaveformCounts;
    std::vector<int32_t> mDurationCounts;
    std::vector<int32_t> mMinLatencies;
    std::vector<int32_t> mMaxLatencies;
    std::vector<int32_t> mLatencyTotals;
    std::vector<int32_t> mLatencyCounts;
    std::vector<int32_t> mErrorCounts;
    std::mutex mDataAccess;

  private:
    void runReporterThread();
    void reportVendorAtomAsync(const VendorAtom &atom);
    void uploadDiagnostics();
    std::shared_ptr<IStats> waitForStatsService() const;
    void drainAtomQueue();

    void uploadPlaycountAtoms();
    void uploadLatencyAtoms();
    void uploadErrorAtoms();

    void clearData(std::vector<int32_t> *data);

    VendorAtom vibratorPlaycountAtom();
    VendorAtom vibratorLatencyAtom();
    VendorAtom vibratorErrorAtom();

    std::thread mReporterThread;
    std::vector<VendorAtom> mAtomQueue;
    std::mutex mAtomQueueAccess;
    std::condition_variable mAtomQueueUpdated;
    bool mTerminateReporterThread = false;

    const std::string kStatsInstanceName;
};

}  // namespace vibrator
}  // namespace hardware
}  // namespace android
}  // namespace aidl

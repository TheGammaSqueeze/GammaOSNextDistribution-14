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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MMMETRICSREPORTER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MMMETRICSREPORTER_H

#include <map>
#include <string>

#include <aidl/android/frameworks/stats/IStats.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtomValue;

/**
 * A class to upload Pixel MM health metrics
 */
class MmMetricsReporter {
  public:
    MmMetricsReporter();
    void aggregatePixelMmMetricsPer5Min();
    void logPixelMmMetricsPerHour(const std::shared_ptr<IStats> &stats_client);
    void logPixelMmMetricsPerDay(const std::shared_ptr<IStats> &stats_client);
    void logCmaStatus(const std::shared_ptr<IStats> &stats_client);

  private:
    struct MmMetricsInfo {
        std::string name;
        int atom_key;
        bool update_diff;
    };

    enum CmaType {
        FARAWIMG = 0,
        FAIMG = 1,
        FATPU = 2,
        FAPREV = 3,
        VFRAME = 4,
        VSTREAM = 5,
    };

    static const std::vector<MmMetricsInfo> kMmMetricsPerHourInfo;
    static const std::vector<MmMetricsInfo> kMmMetricsPerDayInfo;
    static const std::vector<MmMetricsInfo> kCmaStatusInfo;
    static const std::vector<MmMetricsInfo> kCmaStatusExtInfo;

    // raw PSI
    static constexpr const char *kPsiBasePath = "/proc/pressure";
    static constexpr const char *kPsiTypes[3] = {"cpu", "io", "memory"};
    static constexpr const char *kPsiCategories[2] = {"full", "some"};
    static constexpr const char *kPsiMetricNames[4] = {"avg10", "avg60", "avg300", "total"};
    static constexpr int kPsiNumFiles = sizeof(kPsiTypes) / sizeof(kPsiTypes[0]);
    static constexpr int kPsiNumCategories = sizeof(kPsiCategories) / sizeof(kPsiCategories[0]);
    // number of statistics metric names (one total and several timed averages, per category)
    static constexpr int kPsiNumNames = sizeof(kPsiMetricNames) / sizeof(kPsiMetricNames[0]);

    // Though cpu has no 'full' category, here we assume it has
    // So, all file will contain 2 lines x 4 metrics per line = 8 metrics total.
    static constexpr int kPsiMetricsPerFile = kPsiNumCategories * kPsiNumNames;

    // we have 1 'total' and all others 'averages' per category
    // "total" metrics are already accumulative and thus no aggregation is needed.
    //  raw values are used.
    static constexpr int kPsiNumTotals = 1;
    static constexpr int kPsiNumAvgs = kPsiNumNames - kPsiNumTotals;

    // -1 since "cpu" type has no "full" category
    static constexpr int kPsiNumAllCategories = kPsiNumFiles * kPsiNumCategories - 1;

    // number of raw metrics: total and avgs, and the combined all: added together.
    static constexpr int kPsiNumAllTotals = kPsiNumAllCategories * kPsiNumTotals;
    static constexpr int kPsiNumAllAvgs = kPsiNumAllCategories * kPsiNumAvgs;
    static constexpr int kPsiNumAllMetrics = kPsiNumAllTotals + kPsiNumAllAvgs;

    // aggregated into (1) min, (2) max, (3) average (internally the sum is kept than the average)
    static constexpr int kPsiNumOfAggregatedType = 3;

    // # of upload metrics will have a aggregation factor on all 'average' type raw metrics.
    static constexpr int kPsiNumAllUploadAvgMetrics = kPsiNumAllAvgs * kPsiNumOfAggregatedType;
    static constexpr int kPsiNumAllUploadTotalMetrics = kPsiNumAllTotals;
    static constexpr int kPsiNumAllUploadMetrics =
            kPsiNumAllUploadTotalMetrics + kPsiNumAllUploadAvgMetrics;

    bool checkKernelMMMetricSupport();

    bool MmMetricsSupported() {
        // Currently, we collect these metrics and report this atom only for userdebug_or_eng
        // We only grant permissions to access sysfs for userdebug_or_eng.
        // Add a check to avoid unnecessary access.
        // In addition, we need to check the kernel MM metrics support.
        return !is_user_build_ && ker_mm_metrics_support_;
    }

    bool CmaMetricsSupported() {
        // For CMA metric
        return ker_mm_metrics_support_;
    }

    bool ReadFileToUint(const char *const path, uint64_t *val);
    bool reportVendorAtom(const std::shared_ptr<IStats> &stats_client, int atom_id,
                          const std::vector<VendorAtomValue> &values, const std::string &atom_name);
    void readCompactionDurationStat(std::vector<long> *store);
    void fillCompactionDurationStatAtom(const std::vector<long> &store,
                                        std::vector<VendorAtomValue> *values);
    void readDirectReclaimStat(std::vector<long> *store);
    void fillDirectReclaimStatAtom(const std::vector<long> &store,
                                   std::vector<VendorAtomValue> *values);
    void readPressureStall(const char *basePath, std::vector<long> *store);
    bool parsePressureStallFileContent(bool is_cpu, std::string lines, std::vector<long> *store,
                                       int file_save_idx);
    bool parsePressureStallWords(std::vector<std::string> words, std::vector<long> *store,
                                 int line_save_idx);
    bool savePressureMetrics(std::string name, std::string value, std::vector<long> *store,
                             int base_save_idx);
    void fillPressureStallAtom(std::vector<VendorAtomValue> *values);
    void aggregatePressureStall();
    std::map<std::string, uint64_t> readVmStat(const char *path);
    uint64_t getIonTotalPools();
    uint64_t getGpuMemory();
    void fillAtomValues(const std::vector<MmMetricsInfo> &metrics_info,
                        const std::map<std::string, uint64_t> &mm_metrics,
                        std::map<std::string, uint64_t> *prev_mm_metrics,
                        std::vector<VendorAtomValue> *atom_values);
    bool isValidPid(int pid, const char *name);
    int findPidByProcessName(const char *name);
    uint64_t getStimeByPid(int pid);
    void fillProcessStime(int atom_key, const char *name, int *pid, uint64_t *prev_stime,
                          std::vector<VendorAtomValue> *atom_values);
    std::map<std::string, uint64_t> readCmaStat(const std::string &cma_type,
                                                const std::vector<MmMetricsInfo> &metrics_info);
    void reportCmaStatusAtom(
            const std::shared_ptr<IStats> &stats_client, int atom_id, const std::string &cma_type,
            int cma_name_offset, const std::vector<MmMetricsInfo> &metrics_info,
            std::map<std::string, std::map<std::string, uint64_t>> *all_prev_cma_stat);

    const char *const kVmstatPath;
    const char *const kIonTotalPoolsPath;
    const char *const kIonTotalPoolsPathForLegacy;
    const char *const kGpuTotalPages;
    const char *const kCompactDuration;
    const char *const kDirectReclaimBasePath;
    const char *const kPixelStatMm;
    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    static constexpr int kVendorAtomOffset = 2;
    static constexpr int kNumCompactionDurationPrevMetrics = 6;
    static constexpr int kNumDirectReclaimPrevMetrics = 20;

    std::vector<long> prev_compaction_duration_;
    std::vector<long> prev_direct_reclaim_;
    long prev_psi_total_[kPsiNumAllTotals];
    long psi_total_[kPsiNumAllTotals];
    long psi_aggregated_[kPsiNumAllUploadAvgMetrics];  // min, max and avg of original avgXXX
    int psi_data_set_count_ = 0;
    std::map<std::string, uint64_t> prev_hour_vmstat_;
    std::map<std::string, uint64_t> prev_day_vmstat_;
    std::map<std::string, uint64_t> prev_day_pixel_vmstat_;
    std::map<std::string, std::map<std::string, uint64_t>> prev_cma_stat_;
    std::map<std::string, std::map<std::string, uint64_t>> prev_cma_stat_ext_;
    int kswapd_pid_ = -1;
    int kcompactd_pid_ = -1;
    uint64_t prev_kswapd_stime_ = 0;
    uint64_t prev_kcompactd_stime_ = 0;
    bool is_user_build_;
    bool ker_mm_metrics_support_;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_MMMETRICSREPORTER_H

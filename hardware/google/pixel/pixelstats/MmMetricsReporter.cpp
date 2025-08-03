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

#define LOG_TAG "pixelstats: MmMetrics"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/parsedouble.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/MmMetricsReporter.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/Log.h>

#define SZ_4K 0x00001000
#define SZ_2M 0x00200000

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::base::StartsWith;
using android::hardware::google::pixel::PixelAtoms::CmaStatus;
using android::hardware::google::pixel::PixelAtoms::CmaStatusExt;
using android::hardware::google::pixel::PixelAtoms::PixelMmMetricsPerDay;
using android::hardware::google::pixel::PixelAtoms::PixelMmMetricsPerHour;

const std::vector<MmMetricsReporter::MmMetricsInfo> MmMetricsReporter::kMmMetricsPerHourInfo = {
        {"nr_free_pages", PixelMmMetricsPerHour::kFreePagesFieldNumber, false},
        {"nr_anon_pages", PixelMmMetricsPerHour::kAnonPagesFieldNumber, false},
        {"nr_file_pages", PixelMmMetricsPerHour::kFilePagesFieldNumber, false},
        {"nr_slab_reclaimable", PixelMmMetricsPerHour::kSlabReclaimableFieldNumber, false},
        {"nr_slab_unreclaimable", PixelMmMetricsPerHour::kSlabUnreclaimableFieldNumber, false},
        {"nr_zspages", PixelMmMetricsPerHour::kZspagesFieldNumber, false},
        {"nr_unevictable", PixelMmMetricsPerHour::kUnevictableFieldNumber, false},
};

const std::vector<MmMetricsReporter::MmMetricsInfo> MmMetricsReporter::kMmMetricsPerDayInfo = {
        {"workingset_refault", PixelMmMetricsPerDay::kWorkingsetRefaultFieldNumber, true},
        {"pswpin", PixelMmMetricsPerDay::kPswpinFieldNumber, true},
        {"pswpout", PixelMmMetricsPerDay::kPswpoutFieldNumber, true},
        {"allocstall_dma", PixelMmMetricsPerDay::kAllocstallDmaFieldNumber, true},
        {"allocstall_dma32", PixelMmMetricsPerDay::kAllocstallDma32FieldNumber, true},
        {"allocstall_normal", PixelMmMetricsPerDay::kAllocstallNormalFieldNumber, true},
        {"allocstall_movable", PixelMmMetricsPerDay::kAllocstallMovableFieldNumber, true},
        {"pgalloc_dma", PixelMmMetricsPerDay::kPgallocDmaFieldNumber, true},
        {"pgalloc_dma32", PixelMmMetricsPerDay::kPgallocDma32FieldNumber, true},
        {"pgalloc_normal", PixelMmMetricsPerDay::kPgallocNormalFieldNumber, true},
        {"pgalloc_movable", PixelMmMetricsPerDay::kPgallocMovableFieldNumber, true},
        {"pgsteal_kswapd", PixelMmMetricsPerDay::kPgstealKswapdFieldNumber, true},
        {"pgsteal_direct", PixelMmMetricsPerDay::kPgstealDirectFieldNumber, true},
        {"pgscan_kswapd", PixelMmMetricsPerDay::kPgscanKswapdFieldNumber, true},
        {"pgscan_direct", PixelMmMetricsPerDay::kPgscanDirectFieldNumber, true},
        {"oom_kill", PixelMmMetricsPerDay::kOomKillFieldNumber, true},
        {"pgalloc_costly_order", PixelMmMetricsPerDay::kPgallocHighFieldNumber, true},
        {"pgcache_hit", PixelMmMetricsPerDay::kPgcacheHitFieldNumber, true},
        {"pgcache_miss", PixelMmMetricsPerDay::kPgcacheMissFieldNumber, true},
        {"workingset_refault_file", PixelMmMetricsPerDay::kWorkingsetRefaultFileFieldNumber, true},
        {"workingset_refault_anon", PixelMmMetricsPerDay::kWorkingsetRefaultAnonFieldNumber, true},
        {"compact_success", PixelMmMetricsPerDay::kCompactSuccessFieldNumber, true},
        {"compact_fail", PixelMmMetricsPerDay::kCompactFailFieldNumber, true},
        {"kswapd_low_wmark_hit_quickly", PixelMmMetricsPerDay::kKswapdLowWmarkHqFieldNumber, true},
        {"kswapd_high_wmark_hit_quickly", PixelMmMetricsPerDay::kKswapdHighWmarkHqFieldNumber,
         true},
        {"thp_file_alloc", PixelMmMetricsPerDay::kThpFileAllocFieldNumber, true},
        {"thp_zero_page_alloc", PixelMmMetricsPerDay::kThpZeroPageAllocFieldNumber, true},
        {"thp_split_page", PixelMmMetricsPerDay::kThpSplitPageFieldNumber, true},
        {"thp_migration_split", PixelMmMetricsPerDay::kThpMigrationSplitFieldNumber, true},
        {"thp_deferred_split_page", PixelMmMetricsPerDay::kThpDeferredSplitPageFieldNumber, true},
};

const std::vector<MmMetricsReporter::MmMetricsInfo> MmMetricsReporter::kCmaStatusInfo = {
        {"alloc_pages_attempts", CmaStatus::kCmaAllocPagesAttemptsFieldNumber, true},
        {"alloc_pages_failfast_attempts", CmaStatus::kCmaAllocPagesSoftAttemptsFieldNumber, true},
        {"fail_pages", CmaStatus::kCmaFailPagesFieldNumber, true},
        {"fail_failfast_pages", CmaStatus::kCmaFailSoftPagesFieldNumber, true},
        {"migrated_pages", CmaStatus::kMigratedPagesFieldNumber, true},
};

const std::vector<MmMetricsReporter::MmMetricsInfo> MmMetricsReporter::kCmaStatusExtInfo = {
        {"latency_low", CmaStatusExt::kCmaAllocLatencyLowFieldNumber, false},
        {"latency_mid", CmaStatusExt::kCmaAllocLatencyMidFieldNumber, false},
        {"latency_high", CmaStatusExt::kCmaAllocLatencyHighFieldNumber, false},
};

static bool file_exists(const char *path) {
    struct stat sbuf;

    return (stat(path, &sbuf) == 0);
}

bool MmMetricsReporter::checkKernelMMMetricSupport() {
    const char *const require_all[] = {
            kVmstatPath,
            kGpuTotalPages,
            kPixelStatMm,
    };
    const char *const require_one[] = {
            kIonTotalPoolsPath,
            kIonTotalPoolsPathForLegacy,
    };

    for (auto &path : require_all) {
        if (!file_exists(path)) {
            ALOGI("MM Metrics not supported - no %s.", path);
            return false;
        }
    }

    std::string err_msg;
    for (auto &path : require_one) {
        if (file_exists(path)) {
            err_msg.clear();
            break;
        }
        err_msg += path;
        err_msg += ", ";
    }

    if (!err_msg.empty()) {
        err_msg.pop_back();  // remove last space
        err_msg.pop_back();  // remove last comma
        ALOGI("MM Metrics not supported - no IonTotalPools path.");
        return false;
    }
    return true;
}

static bool checkUserBuild() {
    return android::base::GetProperty("ro.build.type", "") == "user";
}

MmMetricsReporter::MmMetricsReporter()
    : kVmstatPath("/proc/vmstat"),
      kIonTotalPoolsPath("/sys/kernel/dma_heap/total_pools_kb"),
      kIonTotalPoolsPathForLegacy("/sys/kernel/ion/total_pools_kb"),
      kGpuTotalPages("/sys/kernel/pixel_stat/gpu/mem/total_page_count"),
      kCompactDuration("/sys/kernel/pixel_stat/mm/compaction/mm_compaction_duration"),
      kDirectReclaimBasePath("/sys/kernel/pixel_stat/mm/vmscan/direct_reclaim"),
      kPixelStatMm("/sys/kernel/pixel_stat/mm"),
      prev_compaction_duration_(kNumCompactionDurationPrevMetrics, 0),
      prev_direct_reclaim_(kNumDirectReclaimPrevMetrics, 0) {
    is_user_build_ = checkUserBuild();
    ker_mm_metrics_support_ = checkKernelMMMetricSupport();
}

bool MmMetricsReporter::ReadFileToUint(const char *const path, uint64_t *val) {
    std::string file_contents;

    if (!ReadFileToString(path, &file_contents)) {
        // Don't print this log if the file doesn't exist, since logs will be printed repeatedly.
        if (errno != ENOENT) {
            ALOGI("Unable to read %s - %s", path, strerror(errno));
        }
        return false;
    } else {
        file_contents = android::base::Trim(file_contents);
        if (!android::base::ParseUint(file_contents, val)) {
            ALOGI("Unable to convert %s to uint - %s", path, strerror(errno));
            return false;
        }
    }
    return true;
}

/*
 * This function reads whole file and parses tokens separated by <delim> into
 * long integers.  Useful for direct reclaim & compaction duration sysfs nodes.
 * Data write is using all or none policy: It will not write partial data unless
 * all data values are good.
 *
 * path: file to open/read
 * data: where to store the results
 * start_idx: index into data[] where to start saving the results
 * delim: delimiters separating different longs
 * skip: how many resulting longs to skip before saving
 * nonnegtive: set to true to validate positive numbers
 *
 * Return value: number of longs actually stored on success.  negative
 *               error codes on errors.
 */
static int ReadFileToLongs(const std::string &path, std::vector<long> *data, int start_idx,
                           const char *delim, int skip, bool nonnegative = false) {
    std::vector<long> out;
    enum { err_read_file = -1, err_parse = -2 };
    std::string file_contents;

    if (!ReadFileToString(path, &file_contents)) {
        // Don't print this log if the file doesn't exist, since logs will be printed repeatedly.
        if (errno != ENOENT) {
            ALOGI("Unable to read %s - %s", path.c_str(), strerror(errno));
        }
        return err_read_file;
    }

    file_contents = android::base::Trim(file_contents);
    std::vector<std::string> words = android::base::Tokenize(file_contents, delim);
    if (words.size() == 0)
        return 0;

    for (auto &w : words) {
        if (skip) {
            skip--;
            continue;
        }
        long tmp;
        if (!android::base::ParseInt(w, &tmp) || (nonnegative && tmp < 0))
            return err_parse;
        out.push_back(tmp);
    }

    int min_size = std::max(static_cast<int>(out.size()) + start_idx, 0);
    if (min_size > data->size())
        data->resize(min_size);
    std::copy(out.begin(), out.end(), data->begin() + start_idx);

    return out.size();
}

/*
 * This function calls ReadFileToLongs, and checks the expected number
 * of long integers read.  Useful for direct reclaim & compaction duration
 * sysfs nodes.
 *
 *  path: file to open/read
 *  data: where to store the results
 *  start_idx: index into data[] where to start saving the results
 *  delim: delimiters separating different longs
 *  skip: how many resulting longs to skip before saving
 *  expected_num: number of expected longs to be read.
 *  nonnegtive: set to true to validate positive numbers
 *
 *  Return value: true if successfully get expected number of long values.
 *                otherwise false.
 */
static inline bool ReadFileToLongsCheck(const std::string &path, std::vector<long> *store,
                                        int start_idx, const char *delim, int skip,
                                        int expected_num, bool nonnegative = false) {
    int num = ReadFileToLongs(path, store, start_idx, delim, skip, nonnegative);

    if (num == expected_num)
        return true;

    int last_idx = std::min(start_idx + expected_num, static_cast<int>(store->size()));
    std::fill(store->begin() + start_idx, store->begin() + last_idx, -1);

    return false;
}

bool MmMetricsReporter::reportVendorAtom(const std::shared_ptr<IStats> &stats_client, int atom_id,
                                         const std::vector<VendorAtomValue> &values,
                                         const std::string &atom_name) {
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = atom_id,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report %s to Stats service", atom_name.c_str());
        return false;
    }
    return true;
}

/**
 * Parse the output of /proc/vmstat or the sysfs having the same output format.
 * The map containing pairs of {field_string, data} will be returned.
 */
std::map<std::string, uint64_t> MmMetricsReporter::readVmStat(const char *path) {
    std::string file_contents;
    std::map<std::string, uint64_t> vmstat_data;

    if (path == nullptr) {
        ALOGI("vmstat path is not specified");
        return vmstat_data;
    }

    if (!ReadFileToString(path, &file_contents)) {
        ALOGE("Unable to read vmstat from %s, err: %s", path, strerror(errno));
        return vmstat_data;
    }

    std::istringstream data(file_contents);
    std::string line;
    while (std::getline(data, line)) {
        std::vector<std::string> words = android::base::Split(line, " ");
        if (words.size() != 2)
            continue;

        uint64_t i;
        if (!android::base::ParseUint(words[1], &i))
            continue;

        vmstat_data[words[0]] = i;
    }
    return vmstat_data;
}

uint64_t MmMetricsReporter::getIonTotalPools() {
    uint64_t res;

    if (!ReadFileToUint(kIonTotalPoolsPathForLegacy, &res) || (res == 0)) {
        if (!ReadFileToUint(kIonTotalPoolsPath, &res)) {
            return 0;
        }
    }

    return res;
}

/**
 * Collect GPU memory from kGpuTotalPages and return the total number of 4K page.
 */
uint64_t MmMetricsReporter::getGpuMemory() {
    uint64_t gpu_size = 0;

    if (!ReadFileToUint(kGpuTotalPages, &gpu_size)) {
        return 0;
    }
    return gpu_size;
}

/**
 * fillAtomValues() is used to copy Mm metrics to values
 * metrics_info: This is a vector of MmMetricsInfo {field_string, atom_key, update_diff}
 *               field_string is used to get the data from mm_metrics.
 *               atom_key is the position where the data should be put into values.
 *               update_diff will be true if this is an accumulated data.
 *               metrics_info may have multiple entries with the same atom_key,
 *               e.g. workingset_refault and workingset_refault_file.
 * mm_metrics: This map contains pairs of {field_string, cur_value} collected
 *             from /proc/vmstat or the sysfs for the pixel specific metrics.
 *             e.g. {"nr_free_pages", 200000}
 *             Some data in mm_metrics are accumulated, e.g. pswpin.
 *             We upload the difference instead of the accumulated value
 *             when update_diff of the field is true.
 * prev_mm_metrics: The pointer to the metrics we collected last time.
 * atom_values: The atom values that will be reported later.
 */
void MmMetricsReporter::fillAtomValues(const std::vector<MmMetricsInfo> &metrics_info,
                                       const std::map<std::string, uint64_t> &mm_metrics,
                                       std::map<std::string, uint64_t> *prev_mm_metrics,
                                       std::vector<VendorAtomValue> *atom_values) {
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(0);
    // resize atom_values to add all fields defined in metrics_info
    int max_idx = 0;
    for (auto &entry : metrics_info) {
        if (max_idx < entry.atom_key)
            max_idx = entry.atom_key;
    }
    unsigned int size = max_idx - kVendorAtomOffset + 1;
    if (atom_values->size() < size)
        atom_values->resize(size, tmp);

    for (auto &entry : metrics_info) {
        int atom_idx = entry.atom_key - kVendorAtomOffset;

        auto data = mm_metrics.find(entry.name);
        if (data == mm_metrics.end())
            continue;

        uint64_t cur_value = data->second;
        uint64_t prev_value = 0;
        if (prev_mm_metrics->size() != 0) {
            auto prev_data = prev_mm_metrics->find(entry.name);
            if (prev_data != prev_mm_metrics->end())
                prev_value = prev_data->second;
        }

        if (entry.update_diff) {
            tmp.set<VendorAtomValue::longValue>(cur_value - prev_value);
        } else {
            tmp.set<VendorAtomValue::longValue>(cur_value);
        }
        (*atom_values)[atom_idx] = tmp;
    }
    (*prev_mm_metrics) = mm_metrics;
}

void MmMetricsReporter::aggregatePixelMmMetricsPer5Min() {
    aggregatePressureStall();
}

void MmMetricsReporter::logPixelMmMetricsPerHour(const std::shared_ptr<IStats> &stats_client) {
    if (!MmMetricsSupported())
        return;

    std::map<std::string, uint64_t> vmstat = readVmStat(kVmstatPath);
    if (vmstat.size() == 0)
        return;

    uint64_t ion_total_pools = getIonTotalPools();
    uint64_t gpu_memory = getGpuMemory();

    // allocate enough values[] entries for the metrics.
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(0);
    int last_value_index =
            PixelMmMetricsPerHour::kPsiMemSomeAvg300AvgFieldNumber - kVendorAtomOffset;
    std::vector<VendorAtomValue> values(last_value_index + 1, tmp);

    fillAtomValues(kMmMetricsPerHourInfo, vmstat, &prev_hour_vmstat_, &values);
    tmp.set<VendorAtomValue::longValue>(ion_total_pools);
    values[PixelMmMetricsPerHour::kIonTotalPoolsFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::longValue>(gpu_memory);
    values[PixelMmMetricsPerHour::kGpuMemoryFieldNumber - kVendorAtomOffset] = tmp;
    fillPressureStallAtom(&values);

    // Send vendor atom to IStats HAL
    reportVendorAtom(stats_client, PixelAtoms::Atom::kPixelMmMetricsPerHour, values,
                     "PixelMmMetricsPerHour");
}

void MmMetricsReporter::logPixelMmMetricsPerDay(const std::shared_ptr<IStats> &stats_client) {
    if (!MmMetricsSupported())
        return;

    std::map<std::string, uint64_t> vmstat = readVmStat(kVmstatPath);
    if (vmstat.size() == 0)
        return;

    std::vector<long> direct_reclaim;
    readDirectReclaimStat(&direct_reclaim);

    std::vector<long> compaction_duration;
    readCompactionDurationStat(&compaction_duration);

    bool is_first_atom = (prev_day_vmstat_.size() == 0) ? true : false;

    // allocate enough values[] entries for the metrics.
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(0);
    int last_value_index =
            PixelMmMetricsPerDay::kThpDeferredSplitPageFieldNumber - kVendorAtomOffset;
    std::vector<VendorAtomValue> values(last_value_index + 1, tmp);

    fillAtomValues(kMmMetricsPerDayInfo, vmstat, &prev_day_vmstat_, &values);

    std::map<std::string, uint64_t> pixel_vmstat =
            readVmStat(android::base::StringPrintf("%s/vmstat", kPixelStatMm).c_str());
    fillAtomValues(kMmMetricsPerDayInfo, pixel_vmstat, &prev_day_pixel_vmstat_, &values);
    fillProcessStime(PixelMmMetricsPerDay::kKswapdStimeClksFieldNumber, "kswapd0", &kswapd_pid_,
                     &prev_kswapd_stime_, &values);
    fillProcessStime(PixelMmMetricsPerDay::kKcompactdStimeClksFieldNumber, "kcompactd0",
                     &kcompactd_pid_, &prev_kcompactd_stime_, &values);
    fillDirectReclaimStatAtom(direct_reclaim, &values);
    fillCompactionDurationStatAtom(direct_reclaim, &values);

    // Don't report the first atom to avoid big spike in accumulated values.
    if (!is_first_atom) {
        // Send vendor atom to IStats HAL
        reportVendorAtom(stats_client, PixelAtoms::Atom::kPixelMmMetricsPerDay, values,
                         "PixelMmMetricsPerDay");
    }
}

/**
 * Check if /proc/<pid>/comm is equal to name.
 */
bool MmMetricsReporter::isValidPid(int pid, const char *name) {
    if (pid <= 0)
        return false;

    std::string file_contents;
    std::string path = android::base::StringPrintf("/proc/%d/comm", pid);
    if (!ReadFileToString(path, &file_contents)) {
        ALOGI("Unable to read %s, err: %s", path.c_str(), strerror(errno));
        return false;
    }

    file_contents = android::base::Trim(file_contents);
    return !file_contents.compare(name);
}

/**
 * Return pid if /proc/<pid>/comm is equal to name, or -1 if not found.
 */
int MmMetricsReporter::findPidByProcessName(const char *name) {
    std::unique_ptr<DIR, int (*)(DIR *)> dir(opendir("/proc"), closedir);
    if (!dir)
        return -1;

    int pid;
    while (struct dirent *dp = readdir(dir.get())) {
        if (dp->d_type != DT_DIR)
            continue;

        if (!android::base::ParseInt(dp->d_name, &pid))
            continue;

        // Avoid avc denial since pixelstats-vendor doesn't have the permission to access /proc/1
        if (pid == 1)
            continue;

        std::string file_contents;
        std::string path = android::base::StringPrintf("/proc/%s/comm", dp->d_name);
        if (!ReadFileToString(path, &file_contents))
            continue;

        file_contents = android::base::Trim(file_contents);
        if (file_contents.compare(name))
            continue;

        return pid;
    }
    return -1;
}

/**
 * Get stime of a process from /proc/<pid>/stat
 * stime is the 15th field.
 */
uint64_t MmMetricsReporter::getStimeByPid(int pid) {
    const int stime_idx = 15;
    uint64_t stime;
    std::string file_contents;
    std::string path = android::base::StringPrintf("/proc/%d/stat", pid);
    if (!ReadFileToString(path, &file_contents)) {
        ALOGI("Unable to read %s, err: %s", path.c_str(), strerror(errno));
        return false;
    }

    std::vector<std::string> data = android::base::Split(file_contents, " ");
    if (data.size() < stime_idx) {
        ALOGI("Unable to find stime from %s. size: %zu", path.c_str(), data.size());
        return false;
    }

    if (android::base::ParseUint(data[stime_idx - 1], &stime))
        return stime;
    else
        return 0;
}

/**
 * Find stime of the process and copy it into atom_values
 * atom_key: Currently, it can only be kKswapdTimeFieldNumber or kKcompactdTimeFieldNumber
 * name: process name
 * pid: The pid of the process. It would be the pid we found last time,
 *      or -1 if not found.
 * prev_stime: The stime of the process collected last time.
 * atom_values: The atom we will report later.
 */
void MmMetricsReporter::fillProcessStime(int atom_key, const char *name, int *pid,
                                         uint64_t *prev_stime,
                                         std::vector<VendorAtomValue> *atom_values) {
    // resize atom_values if there is no space for this stime field.
    int atom_idx = atom_key - kVendorAtomOffset;
    int size = atom_idx + 1;
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(0);
    if (atom_values->size() < size)
        atom_values->resize(size, tmp);

    if (!isValidPid(*pid, name)) {
        (*pid) = findPidByProcessName(name);
        if ((*pid) <= 0) {
            ALOGI("Unable to find pid of %s, err: %s", name, strerror(errno));
            return;
        }
    }

    uint64_t stime = getStimeByPid(*pid);
    tmp.set<VendorAtomValue::longValue>(stime - *prev_stime);
    (*atom_values)[atom_idx] = tmp;
    (*prev_stime) = stime;
}

/**
 * Collect CMA metrics from kPixelStatMm/cma/<cma_type>/<metric>
 * cma_type: CMA heap name
 * metrics_info: This is a vector of MmMetricsInfo {metric, atom_key, update_diff}.
 *               Currently, we only collect CMA metrics defined in metrics_info
 */
std::map<std::string, uint64_t> MmMetricsReporter::readCmaStat(
        const std::string &cma_type,
        const std::vector<MmMetricsReporter::MmMetricsInfo> &metrics_info) {
    uint64_t file_contents;
    std::map<std::string, uint64_t> cma_stat;
    for (auto &entry : metrics_info) {
        std::string path = android::base::StringPrintf("%s/cma/%s/%s", kPixelStatMm,
                                                       cma_type.c_str(), entry.name.c_str());
        if (!ReadFileToUint(path.c_str(), &file_contents))
            continue;
        cma_stat[entry.name] = file_contents;
    }
    return cma_stat;
}

/**
 * This function reads compaction duration sysfs node
 * (/sys/kernel/pixel_stat/mm/compaction/mm_compaction_duration)
 *
 * store: vector to save compaction duration info
 */
void MmMetricsReporter::readCompactionDurationStat(std::vector<long> *store) {
    static const std::string path(kCompactDuration);
    constexpr int num_metrics = 6;

    store->resize(num_metrics);

    int start_idx = 0;
    int expected_num = num_metrics;

    if (!ReadFileToLongsCheck(path, store, start_idx, " ", 1, expected_num, true)) {
        ALOGI("Unable to read %s for the direct reclaim info.", path.c_str());
    }
}

/**
 * This function fills atom values (values) from acquired compaction duration
 * information from vector store
 *
 * store: the already collected (by readCompactionDurationStat()) compaction
 *        duration information
 * values: the atom value vector to be filled.
 */
void MmMetricsReporter::fillCompactionDurationStatAtom(const std::vector<long> &store,
                                                       std::vector<VendorAtomValue> *values) {
    // first metric index
    constexpr int start_idx = PixelMmMetricsPerDay::kCompactionTotalTimeFieldNumber;
    constexpr int num_metrics = 6;

    if (!MmMetricsSupported())
        return;

    int size = start_idx + num_metrics - kVendorAtomOffset;
    if (values->size() < size)
        values->resize(size);

    for (int i = 0; i < num_metrics; i++) {
        VendorAtomValue tmp;
        if (store[i] == -1) {
            tmp.set<VendorAtomValue::longValue>(0);
        } else {
            tmp.set<VendorAtomValue::longValue>(store[i] - prev_compaction_duration_[i]);
            prev_compaction_duration_[i] = store[i];
        }
        (*values)[start_idx + i] = tmp;
    }
    prev_compaction_duration_ = store;
}

/**
 * This function reads direct reclaim sysfs node (4 files:
 * /sys/kernel/pixel_stat/mm/vmscan/direct_reclaim/<level>/latency_stat,
 * where <level> = native, top, visible, other.), and save total time and
 * 4 latency information per file. Total (1+4) x 4 = 20 metrics will be
 * saved.
 *
 * store: vector to save direct reclaim info
 */
void MmMetricsReporter::readDirectReclaimStat(std::vector<long> *store) {
    static const std::string base_path(kDirectReclaimBasePath);
    static const std::vector<std::string> dr_levels{"native", "top", "visible", "other"};
    static const std::string sysfs_name = "latency_stat";
    constexpr int num_metrics_per_file = 5;
    int num_file = dr_levels.size();
    int num_metrics = num_metrics_per_file * num_file;

    store->resize(num_metrics);
    int pass = -1;
    for (auto level : dr_levels) {
        ++pass;
        std::string path = base_path + '/' + level + '/' + sysfs_name;
        int start_idx = pass * num_metrics_per_file;
        int expected_num = num_metrics_per_file;
        if (!ReadFileToLongsCheck(path, store, start_idx, " ", 1, expected_num, true)) {
            ALOGI("Unable to read %s for the direct reclaim info.", path.c_str());
        }
    }
}

/**
 * This function fills atom values (values) from acquired direct reclaim
 * information from vector store
 *
 * store: the already collected (by readDirectReclaimStat()) direct reclaim
 *        information
 * values: the atom value vector to be filled.
 */
void MmMetricsReporter::fillDirectReclaimStatAtom(const std::vector<long> &store,
                                                  std::vector<VendorAtomValue> *values) {
    // first metric index
    constexpr int start_idx = PixelMmMetricsPerDay::kDirectReclaimNativeLatencyTotalTimeFieldNumber;
    constexpr int num_metrics = 20; /* num_metrics_per_file * num_file */

    if (!MmMetricsSupported())
        return;

    int size = start_idx + num_metrics - kVendorAtomOffset;
    if (values->size() < size)
        values->resize(size);

    for (int i = 0; i < num_metrics; i++) {
        VendorAtomValue tmp;
        tmp.set<VendorAtomValue::longValue>(store[i] - prev_direct_reclaim_[i]);
        (*values)[start_idx + i] = tmp;
    }
    prev_direct_reclaim_ = store;
}

/**
 * This function reads pressure (PSI) files (loop thru all 3 files: cpu, io, and
 * memory) and calls the parser to parse and store the metric values.
 * Note that each file have two lines (except cpu has one line only): one with
 * a leading "full", and the other with a leading "some", showing the category
 * for that line.
 * A category has 4 metrics, avg10, avg60, avg300, and total.
 * i.e. the moving average % of PSI in 10s, 60s, 300s time window plus lastly
 * the total stalled time, except that 'cpu' has no 'full' category.
 * In total, we have 3 x 2 x 4 - 4 = 24 - 4  = 20 metrics, arranged in
 * the order of
 *
 *    cpu_some_avg<xyz>
 *    cpu_some_total
 *    io_full_avg<xyz>
 *    io_full_total
 *    io_some_avg<xyz>
 *    io_some_total
 *    mem_full_avg<xyz>
 *    mem_full_total
 *    mem_some_avg<xyz>
 *    mem_some_total
 *
 *    where <xyz>=10, 60, 300 in the order as they appear.
 *
 *    Note that for those avg values (i.e.  <abc>_<def>_avg<xyz>), they
 *    are in percentage with 2-decimal digit accuracy.  We will use an
 *    integer in 2-decimal fixed point format to represent the values.
 *    i.e. value x 100, or to cope with floating point errors,
 *         floor(value x 100 + 0.5)
 *
 *    In fact, in newer kernels, "cpu" PSI has no "full" category.  Some
 *    old kernel has them all zeros, to keep backward compatibility.  The
 *    parse function called by this function is able to detect and ignore
 *    the "cpu, full" category.
 *
 *    sample pressure stall files:
 *    /proc/pressure # cat cpu
 *    some avg10=2.93 avg60=3.17 avg300=3.15 total=94628150260
 *    /proc/pressure # cat io
 *    some avg10=1.06 avg60=1.15 avg300=1.18 total=37709873805
 *    full avg10=1.06 avg60=1.10 avg300=1.11 total=36592322936
 *    /proc/pressure # cat memory
 *    some avg10=0.00 avg60=0.00 avg300=0.00 total=29705314
 *    full avg10=0.00 avg60=0.00 avg300=0.00 total=17234456
 *
 *    PSI information definitions could be found at
 *    https://www.kernel.org/doc/html/latest/accounting/psi.html
 *
 * basePath: the base path to the pressure stall information
 * store: pointer to the vector to store the 20 metrics in the mentioned
 *        order
 */
void MmMetricsReporter::readPressureStall(const char *basePath, std::vector<long> *store) {
    constexpr int kTypeIdxCpu = 0;

    // Callers should have already prepared this, but we resize it here for safety
    store->resize(kPsiNumAllMetrics);
    std::fill(store->begin(), store->end(), -1);

    // To make the process unified, we prepend an imaginary "cpu + full"
    // type-category combination.  Now, each file (cpu, io, memnry) contains
    // two categories, i.e. "full" and "some".
    // Each category has <kPsiNumNames> merics and thus need that many entries
    // to store them, except that the first category (the imaginary one) do not
    // need any storage. So we set the save index for the 1st file ("cpu") to
    // -kPsiNumNames.
    int file_save_idx = -kPsiNumNames;

    // loop thru all pressure stall files: cpu, io, memory
    for (int type_idx = 0; type_idx < kPsiNumFiles;
         ++type_idx, file_save_idx += kPsiMetricsPerFile) {
        std::string file_contents;
        std::string path = std::string("") + basePath + '/' + kPsiTypes[type_idx];

        if (!ReadFileToString(path, &file_contents)) {
            // Don't print this log if the file doesn't exist, since logs will be printed
            // repeatedly.
            if (errno != ENOENT)
                ALOGI("Unable to read %s - %s", path.c_str(), strerror(errno));
            goto err_out;
        }
        if (!MmMetricsReporter::parsePressureStallFileContent(type_idx == kTypeIdxCpu,
                                                              file_contents, store, file_save_idx))
            goto err_out;
    }
    return;

err_out:
    std::fill(store->begin(), store->end(), -1);
}

/*
 * This function parses a pressure stall file, which contains two
 * lines, i.e. the "full", and "some" lines, except that the 'cpu' file
 * contains only one line ("some"). Refer to the function comments of
 * readPressureStall() for pressure stall file format.
 *
 * For old kernel, 'cpu' file might contain an extra line for "full", which
 * will be ignored.
 *
 * is_cpu: Is the data from the file 'cpu'
 * lines: the file content
 * store: the output vector to hold the parsed data.
 * file_save_idx: base index to start saving 'store' vector for this file.
 *
 * Return value: true on success, false otherwise.
 */
bool MmMetricsReporter::parsePressureStallFileContent(bool is_cpu, std::string lines,
                                                      std::vector<long> *store, int file_save_idx) {
    constexpr int kNumOfWords = 5;  // expected number of words separated by spaces.
    constexpr int kCategoryFull = 0;

    std::istringstream data(lines);
    std::string line;

    while (std::getline(data, line)) {
        int category_idx = 0;

        line = android::base::Trim(line);
        std::vector<std::string> words = android::base::Tokenize(line, " ");
        if (words.size() != kNumOfWords) {
            ALOGE("PSI parse fail: num of words = %d != expected %d",
                  static_cast<int>(words.size()), kNumOfWords);
            return false;
        }

        // words[0] should be either "full" or "some", the category name.
        for (auto &cat : kPsiCategories) {
            if (words[0].compare(cat) == 0)
                break;
            ++category_idx;
        }
        if (category_idx == kPsiNumCategories) {
            ALOGE("PSI parse fail: unknown category %s", words[0].c_str());
            return false;
        }

        // skip (cpu, full) combination.
        if (is_cpu && category_idx == kCategoryFull) {
            ALOGI("kernel: old PSI sysfs node.");
            continue;
        }

        // Now we have separated words in a vector, e.g.
        // ["some", "avg10=2.93", "avg60=3.17", "avg300=3.15",  total=94628150260"]
        // call parsePressureStallWords to parse them.
        int line_save_idx = file_save_idx + category_idx * kPsiNumNames;
        if (!parsePressureStallWords(words, store, line_save_idx))
            return false;
    }
    return true;
}

// This function parses the already split words, e.g.
// ["some", "avg10=0.00", "avg60=0.00", "avg300=0.00", "total=29705314"],
// from a line (category) in a pressure stall file.
//
// words: the split words in the form of "name=value"
// store: the output vector
// line_save_idx: the base start index to save in vector for this line (category)
//
// Return value: true on success, false otherwise.
bool MmMetricsReporter::parsePressureStallWords(std::vector<std::string> words,
                                                std::vector<long> *store, int line_save_idx) {
    // Skip the first word, which is already parsed by the caller.
    // All others are value pairs in "name=value" form.
    // e.g. ["some", "avg10=0.00", "avg60=0.00", "avg300=0.00", "total=29705314"]
    // "some" is skipped.
    for (int i = 1; i < words.size(); ++i) {
        std::vector<std::string> metric = android::base::Tokenize(words[i], "=");
        if (metric.size() != 2) {
            ALOGE("%s: parse error (name=value) @ idx %d", __FUNCTION__, i);
            return false;
        }
        if (!MmMetricsReporter::savePressureMetrics(metric[0], metric[1], store, line_save_idx))
            return false;
    }
    return true;
}

// This function parses one value pair in "name=value" format, and depending on
// the name, save to its proper location in the store vector.
// name = "avg10" -> save to index base_save_idx.
// name = "avg60" -> save to index base_save_idx + 1.
// name = "avg300" -> save to index base_save_idx + 2.
// name = "total" -> save to index base_save_idx + 3.
//
// name: the metrics name
// value: the metrics value
// store: the output vector
// base_save_idx: the base save index
//
// Return value: true on success, false otherwise.
//
bool MmMetricsReporter::savePressureMetrics(std::string name, std::string value,
                                            std::vector<long> *store, int base_save_idx) {
    int name_idx = 0;
    constexpr int kNameIdxTotal = 3;

    for (auto &mn : kPsiMetricNames) {
        if (name.compare(mn) == 0)
            break;
        ++name_idx;
    }
    if (name_idx == kPsiNumNames) {
        ALOGE("%s: parse error: unknown metric name.", __FUNCTION__);
        return false;
    }

    long out;
    if (name_idx == kNameIdxTotal) {
        // 'total' metrics
        unsigned long tmp;
        if (!android::base::ParseUint(value, &tmp))
            out = -1;
        else
            out = tmp;
    } else {
        // 'avg' metrics
        double d = -1.0;
        if (android::base::ParseDouble(value, &d))
            out = static_cast<long>(d * 100 + 0.5);
        else
            out = -1;
    }

    if (base_save_idx + name_idx >= store->size()) {
        // should never reach here
        ALOGE("out of bound access to store[] (src line %d) @ index %d", __LINE__,
              base_save_idx + name_idx);
        return false;
    } else {
        (*store)[base_save_idx + name_idx] = out;
    }
    return true;
}

/**
 * This function reads in the current pressure (PSI) information, and aggregates
 * it (except for the "total" information, which will overwrite
 * the previous value without aggregation.
 *
 * data are arranged in the following order, and must comply the order defined
 * in the proto:
 *
 *    // note: these 5 'total' metrics are not aggregated.
 *    cpu_some_total
 *    io_full_total
 *    io_some_total
 *    mem_full_total
 *    mem_some_total
 *
 *    //  9 aggregated metrics as above avg<xyz>_<aggregate>
 *    //  where <xyz> = 10, 60, 300; <aggregate> = min, max, sum
 *    cpu_some_avg10_min
 *    cpu_some_avg10_max
 *    cpu_some_avg10_sum
 *    cpu_some_avg60_min
 *    cpu_some_avg60_max
 *    cpu_some_avg60_sum
 *    cpu_some_avg300_min
 *    cpu_some_avg300_max
 *    cpu_some_avg300_sum
 *
 *    // similar 9 metrics as above avg<xyz>_<aggregate>
 *    io_full_avg<xyz>_<aggregate>
 *
 *    // similar 9 metrics as above avg<xyz>_<aggregate>
 *    io_some_avg<xyz>_<aggregate>
 *
 *    // similar 9 metrics as above avg<xyz>_<aggregate>
 *    mem_full_avg<xyz>_<aggregate>
 *
 *    // similar 9 metrics as above avg<xyz>_<aggregate>
 *    mem_some_avg<xyz>_<aggregate>
 *
 * In addition, it increases psi_data_set_count_ by 1 (in order to calculate
 * the average from the "_sum" aggregate.)
 */
void MmMetricsReporter::aggregatePressureStall() {
    constexpr int kFirstTotalOffset = kPsiNumAvgs;

    if (!MmMetricsSupported())
        return;

    std::vector<long> psi(kPsiNumAllMetrics, -1);
    readPressureStall(kPsiBasePath, &psi);

    // Pre-check for possible later out of bound error, if readPressureStall()
    // decreases the vector size.
    // It's for safety only.  The condition should never be true.
    if (psi.size() != kPsiNumAllMetrics) {
        ALOGE("Wrong psi[] size %d != expected %d after read.", static_cast<int>(psi.size()),
              kPsiNumAllMetrics);
        return;
    }

    // check raw metrics and preventively handle errors: Although we don't expect read sysfs
    // node could fail.  Discard all current readings on any error.
    for (int i = 0; i < kPsiNumAllMetrics; ++i) {
        if (psi[i] == -1) {
            ALOGE("Bad data @ psi[%ld] = -1", psi[i]);
            goto err_out;
        }
    }

    // "total" metrics are accumulative: just replace the previous accumulation.
    for (int i = 0; i < kPsiNumAllTotals; ++i) {
        int psi_idx;

        psi_idx = i * kPsiNumNames + kFirstTotalOffset;
        if (psi_idx >= psi.size()) {
            // should never reach here
            ALOGE("out of bound access to psi[] (src line %d) @ index %d", __LINE__, psi_idx);
            goto err_out;
        } else {
            psi_total_[i] = psi[psi_idx];
        }
    }

    // "avg" metrics will be aggregated to min, max and sum
    // later on, the sum will be divided by psi_data_set_count_ to get the average.
    int aggr_idx;
    aggr_idx = 0;
    for (int psi_idx = 0; psi_idx < kPsiNumAllMetrics; ++psi_idx) {
        if (psi_idx % kPsiNumNames == kFirstTotalOffset)
            continue;  // skip 'total' metrics, already processed.

        if (aggr_idx + 3 > kPsiNumAllUploadAvgMetrics) {
            // should never reach here
            ALOGE("out of bound access to psi_aggregated_[] (src line %d) @ index %d ~ %d",
                  __LINE__, aggr_idx, aggr_idx + 2);
            return;  // give up avgs, but keep totals (so don't go err_out
        }

        long value = psi[psi_idx];
        if (psi_data_set_count_ == 0) {
            psi_aggregated_[aggr_idx++] = value;
            psi_aggregated_[aggr_idx++] = value;
            psi_aggregated_[aggr_idx++] = value;
        } else {
            psi_aggregated_[aggr_idx++] = std::min(value, psi_aggregated_[aggr_idx]);
            psi_aggregated_[aggr_idx++] = std::max(value, psi_aggregated_[aggr_idx]);
            psi_aggregated_[aggr_idx++] += value;
        }
    }
    ++psi_data_set_count_;
    return;

err_out:
    for (int i = 0; i < kPsiNumAllTotals; ++i) psi_total_[i] = -1;
}

/**
 * This function fills atom values (values) from psi_aggregated_[]
 *
 * values: the atom value vector to be filled.
 */
void MmMetricsReporter::fillPressureStallAtom(std::vector<VendorAtomValue> *values) {
    constexpr int avg_of_avg_offset = 2;
    constexpr int total_start_idx =
            PixelMmMetricsPerHour::kPsiCpuSomeTotalFieldNumber - kVendorAtomOffset;
    constexpr int avg_start_idx = total_start_idx + kPsiNumAllTotals;

    if (!MmMetricsSupported())
        return;

    VendorAtomValue tmp;

    // The caller should have setup the correct total size,
    // but we check and extend the size when it's too small for safety.
    unsigned int min_value_size = total_start_idx + kPsiNumAllUploadMetrics;
    if (values->size() < min_value_size)
        values->resize(min_value_size);

    // "total" metric
    int metric_idx = total_start_idx;
    for (int save = 0; save < kPsiNumAllTotals; ++save, ++metric_idx) {
        if (psi_data_set_count_ == 0)
            psi_total_[save] = -1;  // no data: invalidate the current total

        // A good difference needs a good previous value and a good current value.
        if (psi_total_[save] != -1 && prev_psi_total_[save] != -1)
            tmp.set<VendorAtomValue::longValue>(psi_total_[save] - prev_psi_total_[save]);
        else
            tmp.set<VendorAtomValue::longValue>(-1);

        prev_psi_total_[save] = psi_total_[save];
        if (metric_idx >= values->size()) {
            // should never reach here
            ALOGE("out of bound access to value[] for psi-total @ index %d", metric_idx);
            goto cleanup;
        } else {
            (*values)[metric_idx] = tmp;
        }
    }

    // "avg" metrics -> aggregate to min,  max, and avg of the original avg
    metric_idx = avg_start_idx;
    for (int save = 0; save < kPsiNumAllUploadAvgMetrics; ++save, ++metric_idx) {
        if (psi_data_set_count_) {
            if (save % kPsiNumOfAggregatedType == avg_of_avg_offset) {
                // avg of avg
                tmp.set<VendorAtomValue::intValue>(psi_aggregated_[save] / psi_data_set_count_);
            } else {
                // min or max of avg
                tmp.set<VendorAtomValue::intValue>(psi_aggregated_[save]);
            }
        } else {
            tmp.set<VendorAtomValue::intValue>(-1);
        }
        if (metric_idx >= values->size()) {
            // should never reach here
            ALOGE("out of bound access to value[] for psi-avg @ index %d", metric_idx);
            goto cleanup;
        } else {
            (*values)[metric_idx] = tmp;
        }
    }

cleanup:
    psi_data_set_count_ = 0;
}

/**
 * This function is to collect CMA metrics and upload them.
 * The CMA metrics are collected by readCmaStat(), copied into atom values
 * by fillAtomValues(), and then uploaded by reportVendorAtom(). The collected
 * metrics will be stored in prev_cma_stat_ and prev_cma_stat_ext_ according
 * to its CmaType.
 *
 * stats_client: The Stats service
 * atom_id: The id of atom. It can be PixelAtoms::Atom::kCmaStatus or kCmaStatusExt
 * cma_type: The name of CMA heap.
 * cma_name_offset: The offset of the field cma_heap_name in CmaStatus or CmaStatusExt
 * type_idx: The id of the CMA heap. We add this id in atom values to identify
 *           the CMA status data.
 * metrics_info: This is a vector of MmMetricsInfo {metric, atom_key, update_diff}.
 *               We only collect metrics defined in metrics_info from CMA heap path.
 * all_prev_cma_stat: This is the CMA status collected last time.
 *                    It is a map containing pairs of {type_idx, cma_stat}, and cma_stat is
 *                    a map contains pairs of {metric, cur_value}.
 *                    e.g. {CmaType::FARAWIMG, {"alloc_pages_attempts", 100000}, {...}, ....}
 *                    is collected from kPixelStatMm/cma/farawimg/alloc_pages_attempts
 */
void MmMetricsReporter::reportCmaStatusAtom(
        const std::shared_ptr<IStats> &stats_client, int atom_id, const std::string &cma_type,
        int cma_name_offset, const std::vector<MmMetricsInfo> &metrics_info,
        std::map<std::string, std::map<std::string, uint64_t>> *all_prev_cma_stat) {
    std::map<std::string, uint64_t> cma_stat = readCmaStat(cma_type, metrics_info);
    if (!cma_stat.empty()) {
        std::vector<VendorAtomValue> values;
        VendorAtomValue tmp;
        // type is an enum value corresponding to the CMA heap name. Since CMA heap name
        // can be added/removed/modified, it would take effort to maintain the mapping table.
        // We would like to store CMA heap name directly, so just set type to 0.
        tmp.set<VendorAtomValue::intValue>(0);
        values.push_back(tmp);

        std::map<std::string, uint64_t> prev_cma_stat;
        auto entry = all_prev_cma_stat->find(cma_type);
        if (entry != all_prev_cma_stat->end())
            prev_cma_stat = entry->second;

        bool is_first_atom = (prev_cma_stat.size() == 0) ? true : false;
        fillAtomValues(metrics_info, cma_stat, &prev_cma_stat, &values);

        int size = cma_name_offset - kVendorAtomOffset + 1;
        if (values.size() < size) {
            values.resize(size, tmp);
        }
        tmp.set<VendorAtomValue::stringValue>(cma_type);
        values[cma_name_offset - kVendorAtomOffset] = tmp;

        (*all_prev_cma_stat)[cma_type] = prev_cma_stat;
        if (!is_first_atom)
            reportVendorAtom(stats_client, atom_id, values, "CmaStatus");
    }
}

/**
 * Find the CMA heap defined in kCmaTypeInfo, and then call reportCmaStatusAtom()
 * to collect the CMA metrics from kPixelStatMm/cma/<cma_type> and upload them.
 */
void MmMetricsReporter::logCmaStatus(const std::shared_ptr<IStats> &stats_client) {
    if (!CmaMetricsSupported())
        return;

    std::string cma_root = android::base::StringPrintf("%s/cma", kPixelStatMm);
    std::unique_ptr<DIR, int (*)(DIR *)> dir(opendir(cma_root.c_str()), closedir);
    if (!dir)
        return;

    while (struct dirent *dp = readdir(dir.get())) {
        if (dp->d_type != DT_DIR)
            continue;

        std::string cma_type(dp->d_name);

        reportCmaStatusAtom(stats_client, PixelAtoms::Atom::kCmaStatus, cma_type,
                            CmaStatus::kCmaHeapNameFieldNumber, kCmaStatusInfo, &prev_cma_stat_);
        reportCmaStatusAtom(stats_client, PixelAtoms::Atom::kCmaStatusExt, cma_type,
                            CmaStatusExt::kCmaHeapNameFieldNumber, kCmaStatusExtInfo,
                            &prev_cma_stat_ext_);
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

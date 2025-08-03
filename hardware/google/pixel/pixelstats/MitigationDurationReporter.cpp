/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "pixelstats: PowerMitigationDurationCounts"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/MitigationDurationReporter.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::hardware::google::pixel::PixelAtoms::PowerMitigationDurationCounts;

enum DurationOutputOrder {
    UVLO1,
    UVLO1_MMWAVE,
    UVLO1_RFFE,
    UVLO2,
    UVLO2_MMWAVE,
    UVLO2_RFFE,
    BATOILO,
    BATOILO_MMWAVE,
    BATOILO_RFFE,
    MAIN0,
    MAIN1,
    MAIN2,
    MAIN3,
    MAIN4,
    MAIN5,
    MAIN6,
    MAIN7,
    MAIN8,
    MAIN9,
    MAIN10,
    MAIN11,
    SUB0,
    SUB1,
    SUB2,
    SUB3,
    SUB4,
    SUB5,
    SUB6,
    SUB7,
    SUB8,
    SUB9,
    SUB10,
    SUB11,
};

MitigationDurationReporter::MitigationDurationReporter() {}

bool MitigationDurationReporter::getStatFromLine(const std::string *line, int *val) {
    std::vector<std::string> strs = android::base::Split(*line, ":");
    if (strs.size() != 2) {
        ALOGI("Unable to split %s", line->c_str());
        return false;
    }
    std::string str = strs[1];
    str = android::base::Trim(str);
    str.erase(std::remove(str.begin(), str.end(), '\n'), str.cend());
    if (!android::base::ParseInt(str, val)) {
        ALOGI("Unable to convert %s to int - %s", str.c_str(), strerror(errno));
        return false;
    }
    return true;
}

void MitigationDurationReporter::valueAssignmentHelper(std::vector<VendorAtomValue> *values,
                                                       int *val, int fieldNumber) {
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(*val);
    (*values)[fieldNumber - kVendorAtomOffset] = tmp;
}

void MitigationDurationReporter::logMitigationDuration(const std::shared_ptr<IStats> &stats_client,
                                                       const std::string &path) {
    struct IrqDurationCounts greater_than_thresh = {};

    if (!getIrqDurationCountHelper(path + kGreaterThanTenMsSysfsNode, &greater_than_thresh))
        return;

    VendorAtomValue tmp;
    std::vector<VendorAtomValue> values(33);

    valueAssignmentHelper(&values, &greater_than_thresh.uvlo1_none,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo1NoneFieldNumber);
    valueAssignmentHelper(&values, &greater_than_thresh.uvlo1_mmwave,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo1MmwaveFieldNumber);
    valueAssignmentHelper(&values, &greater_than_thresh.uvlo1_rffe,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo1RffeFieldNumber);

    valueAssignmentHelper(&values, &greater_than_thresh.uvlo2_none,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo2NoneFieldNumber);
    valueAssignmentHelper(&values, &greater_than_thresh.uvlo2_mmwave,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo2MmwaveFieldNumber);
    valueAssignmentHelper(&values, &greater_than_thresh.uvlo2_rffe,
                          PowerMitigationDurationCounts::kGreaterThanThreshUvlo2RffeFieldNumber);

    valueAssignmentHelper(&values, &greater_than_thresh.batoilo_none,
                          PowerMitigationDurationCounts::kGreaterThanThreshBatoiloNoneFieldNumber);
    valueAssignmentHelper(
            &values, &greater_than_thresh.batoilo_mmwave,
            PowerMitigationDurationCounts::kGreaterThanThreshBatoiloMmwaveFieldNumber);
    valueAssignmentHelper(&values, &greater_than_thresh.batoilo_rffe,
                          PowerMitigationDurationCounts::kGreaterThanThreshBatoiloRffeFieldNumber);

    int i;
    for (i = 0; i < MITIGATION_DURATION_MAIN_COUNT; i++) {
        valueAssignmentHelper(
                &values, &greater_than_thresh.main[i],
                PowerMitigationDurationCounts::kGreaterThanThreshMain0FieldNumber + i);
    }

    for (i = 0; i < MITIGATION_DURATION_SUB_COUNT; i++) {
        valueAssignmentHelper(&values, &greater_than_thresh.sub[i],
                              PowerMitigationDurationCounts::kGreaterThanThreshSub0FieldNumber + i);
    }

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kMitigationDuration,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report to Stats service");
}

int MitigationDurationReporter::updateStat(const std::string *line, int *val) {
    int stat_value;
    if (!getStatFromLine(line, &stat_value) || *val == stat_value) {
        return 0;
    }

    *val = stat_value;
    return 1;
}

bool MitigationDurationReporter::getIrqDurationCountHelper(
        const std::string kMitigationDurationFile, struct IrqDurationCounts *counts) {
    std::string file_contents;

    if (!ReadFileToString(kMitigationDurationFile, &file_contents)) {
        ALOGI("Unable to read %s - %s", kMitigationDurationFile.c_str(), strerror(errno));
        return false;
    }

    std::vector<std::string> lines = android::base::Split(file_contents, "\n");
    if (lines.size() < kExpectedNumberOfLines) {
        ALOGI("Readback size is invalid");
        return false;
    }

    int16_t i;
    int num_stats = 0;

    num_stats += updateStat(&lines[UVLO1], &counts->uvlo1_none);
    num_stats += updateStat(&lines[UVLO1_MMWAVE], &counts->uvlo1_mmwave);
    num_stats += updateStat(&lines[UVLO1_RFFE], &counts->uvlo1_rffe);
    num_stats += updateStat(&lines[UVLO2], &counts->uvlo2_none);
    num_stats += updateStat(&lines[UVLO2_MMWAVE], &counts->uvlo2_mmwave);
    num_stats += updateStat(&lines[UVLO2_RFFE], &counts->uvlo2_rffe);
    num_stats += updateStat(&lines[BATOILO], &counts->batoilo_none);
    num_stats += updateStat(&lines[BATOILO_MMWAVE], &counts->batoilo_mmwave);
    num_stats += updateStat(&lines[BATOILO_RFFE], &counts->batoilo_rffe);

    for (i = MAIN0; i <= MAIN11; i++) {
        num_stats += updateStat(&lines[i], &counts->main[i - MAIN0]);
    }

    for (i = SUB0; i <= SUB11; i++) {
        num_stats += updateStat(&lines[i], &counts->sub[i - SUB0]);
    }

    return num_stats > 0;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

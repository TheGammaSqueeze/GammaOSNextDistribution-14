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

#define LOG_TAG "pixelstats: PowerMitigationStats"

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <pixelstats/MitigationStatsReporter.h>
#include <utils/Log.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::hardware::google::pixel::PixelAtoms::PowerMitigationStats;

MitigationStatsReporter::MitigationStatsReporter() {}

bool MitigationStatsReporter::ReadFileToInt(const std::string &path, int *val) {
    std::string file_contents;

    if (!ReadFileToString(path.c_str(), &file_contents)) {
        ALOGI("Unable to read %s - %s", path.c_str(), strerror(errno));
        return false;
    } else {
        file_contents = android::base::Trim(file_contents);
        if (!android::base::ParseInt(file_contents, val)) {
            ALOGI("Unable to convert %s to int - %s", path.c_str(), strerror(errno));
            return false;
        }
    }
    return true;
}

void MitigationStatsReporter::logMitigationStatsPerHour(const std::shared_ptr<IStats> &stats_client,
                                                        const std::string &path) {
    struct MitigationCount last_count = {};
    struct MitigationCap last_cap = {};

    if (!logMitigationCount(path, &last_count))
        return;
    logMitigationCap(path, &last_cap);

    VendorAtomValue tmp;
    std::vector<VendorAtomValue> values(24);
    tmp.set<VendorAtomValue::intValue>(last_count.batoilo_count - prev_count.batoilo_count);
    values[PowerMitigationStats::kBatoiloCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.vdroop1_count - prev_count.vdroop1_count);
    values[PowerMitigationStats::kVdroop1CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.vdroop2_count - prev_count.vdroop2_count);
    values[PowerMitigationStats::kVdroop2CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.smpl_warn_count - prev_count.smpl_warn_count);
    values[PowerMitigationStats::kSmplWarnCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.ocp_cpu1_count - prev_count.ocp_cpu1_count);
    values[PowerMitigationStats::kOcpCpu1CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.ocp_cpu2_count - prev_count.ocp_cpu2_count);
    values[PowerMitigationStats::kOcpCpu2CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.ocp_gpu_count - prev_count.ocp_gpu_count);
    values[PowerMitigationStats::kOcpGpuCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.ocp_tpu_count - prev_count.ocp_tpu_count);
    values[PowerMitigationStats::kOcpTpuCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.soft_ocp_cpu1_count -
                                       prev_count.soft_ocp_cpu1_count);
    values[PowerMitigationStats::kSoftOcpCpu1CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.soft_ocp_cpu2_count -
                                       prev_count.soft_ocp_cpu2_count);
    values[PowerMitigationStats::kSoftOcpCpu2CountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.soft_ocp_gpu_count -
                                       prev_count.soft_ocp_gpu_count);
    values[PowerMitigationStats::kSoftOcpGpuCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_count.soft_ocp_tpu_count -
                                       prev_count.soft_ocp_tpu_count);
    values[PowerMitigationStats::kSoftOcpTpuCountFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.batoilo_cap);
    values[PowerMitigationStats::kBatoiloCapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.vdroop1_cap);
    values[PowerMitigationStats::kVdroop1CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.vdroop2_cap);
    values[PowerMitigationStats::kVdroop2CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.smpl_warn_cap);
    values[PowerMitigationStats::kSmplWarnCapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.ocp_cpu1_cap);
    values[PowerMitigationStats::kOcpCpu1CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.ocp_cpu2_cap);
    values[PowerMitigationStats::kOcpCpu2CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.ocp_gpu_cap);
    values[PowerMitigationStats::kOcpGpuCapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.ocp_tpu_cap);
    values[PowerMitigationStats::kOcpTpuCapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.soft_ocp_cpu1_cap);
    values[PowerMitigationStats::kSoftOcpCpu1CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.soft_ocp_cpu2_cap);
    values[PowerMitigationStats::kSoftOcpCpu2CapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.soft_ocp_gpu_cap);
    values[PowerMitigationStats::kSoftOcpGpuCapFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(last_cap.soft_ocp_tpu_cap);
    values[PowerMitigationStats::kSoftOcpTpuCapFieldNumber - kVendorAtomOffset] = tmp;

    prev_count = last_count;
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kMitigationStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report to Stats service");
}

void MitigationStatsReporter::logMitigationCap(const std::string kMitigationDir,
                                               struct MitigationCap *last_cap) {
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/batoilo_cap",
                  &(last_cap->batoilo_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/ocp_cpu1_cap",
                  &(last_cap->ocp_cpu1_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/ocp_cpu2_cap",
                  &(last_cap->ocp_cpu2_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/ocp_gpu_cap",
                  &(last_cap->ocp_gpu_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/ocp_tpu_cap",
                  &(last_cap->ocp_tpu_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/smpl_warn_cap",
                  &(last_cap->smpl_warn_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/soft_ocp_cpu1_cap",
                  &(last_cap->soft_ocp_cpu1_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/soft_ocp_cpu2_cap",
                  &(last_cap->soft_ocp_cpu2_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/soft_ocp_gpu_cap",
                  &(last_cap->soft_ocp_gpu_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/soft_ocp_tpu_cap",
                  &(last_cap->soft_ocp_tpu_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/vdroop1_cap",
                  &(last_cap->vdroop1_cap));
    ReadFileToInt(kMitigationDir + "/last_triggered_capacity/vdroop2_cap",
                  &(last_cap->vdroop2_cap));
}

bool MitigationStatsReporter::logMitigationCount(const std::string kMitigationDir,
                                                 struct MitigationCount *last_count) {
    bool send_stats = false;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/batoilo_count",
                       &(last_count->batoilo_count)))
        return false;
    send_stats |= (last_count->batoilo_count - prev_count.batoilo_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/ocp_cpu1_count",
                       &(last_count->ocp_cpu1_count)))
        return false;
    send_stats |= (last_count->ocp_cpu1_count - prev_count.ocp_cpu1_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/ocp_cpu2_count",
                       &(last_count->ocp_cpu2_count)))
        return false;
    send_stats |= (last_count->ocp_cpu2_count - prev_count.ocp_cpu2_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/ocp_gpu_count",
                       &(last_count->ocp_gpu_count)))
        return false;
    send_stats |= (last_count->ocp_gpu_count - prev_count.ocp_gpu_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/ocp_tpu_count",
                       &(last_count->ocp_tpu_count)))
        return false;
    send_stats |= (last_count->ocp_tpu_count - prev_count.ocp_tpu_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/smpl_warn_count",
                       &(last_count->smpl_warn_count)))
        return false;
    send_stats |= (last_count->smpl_warn_count - prev_count.smpl_warn_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/soft_ocp_cpu1_count",
                       &(last_count->soft_ocp_cpu1_count)))
        return false;
    send_stats |= (last_count->soft_ocp_cpu1_count - prev_count.soft_ocp_cpu1_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/soft_ocp_cpu2_count",
                       &(last_count->soft_ocp_cpu2_count)))
        return false;
    send_stats |= (last_count->soft_ocp_cpu2_count - prev_count.soft_ocp_cpu2_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/soft_ocp_gpu_count",
                       &(last_count->soft_ocp_gpu_count)))
        return false;
    send_stats |= (last_count->soft_ocp_gpu_count - prev_count.soft_ocp_gpu_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/soft_ocp_tpu_count",
                       &(last_count->soft_ocp_tpu_count)))
        return false;
    send_stats |= (last_count->soft_ocp_tpu_count - prev_count.soft_ocp_tpu_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/vdroop1_count",
                       &(last_count->vdroop1_count)))
        return false;
    send_stats |= (last_count->vdroop1_count - prev_count.vdroop1_count) > 0;
    if (!ReadFileToInt(kMitigationDir + "/last_triggered_count/vdroop2_count",
                       &(last_count->vdroop2_count)))
        return false;
    send_stats |= (last_count->vdroop2_count - prev_count.vdroop2_count) > 0;
    return send_stats;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

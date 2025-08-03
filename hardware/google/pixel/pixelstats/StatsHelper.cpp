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

#include <android/binder_manager.h>
#include <pixelstats/StatsHelper.h>

#define LOG_TAG "pixelstats-vendor"

#include <utils/Log.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;

// Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
// store everything in the values array at the index of the field number
// -2.
const int kVendorAtomOffset = 2;

std::shared_ptr<IStats> getStatsService() {
    const std::string instance = std::string() + IStats::descriptor + "/default";
    static bool isStatsDeclared = false;
    if (!isStatsDeclared) {
        // It is good to cache the result - it would not be changed
        isStatsDeclared = AServiceManager_isDeclared(instance.c_str());
        if (!isStatsDeclared) {
            ALOGE("Stats service is not registered.");
            return nullptr;
        }
    }
    return IStats::fromBinder(ndk::SpAIBinder(AServiceManager_waitForService(instance.c_str())));
}

void reportSpeakerImpedance(const std::shared_ptr<IStats> &stats_client,
                            const PixelAtoms::VendorSpeakerImpedance &speakerImpedance) {
    // Load values array
    std::vector<VendorAtomValue> values(2);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(speakerImpedance.speaker_location());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(speakerImpedance.impedance());
    values[1] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorSpeakerImpedance,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorSpeakerImpedance to Stats service");
}

void reportSpeakerHealthStat(const std::shared_ptr<IStats> &stats_client,
                             const PixelAtoms::VendorSpeakerStatsReported &speakerHealthStat) {
    // Load values array
    std::vector<VendorAtomValue> values(5);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(speakerHealthStat.speaker_location());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(speakerHealthStat.impedance());
    values[1] = tmp;
    tmp.set<VendorAtomValue::intValue>(speakerHealthStat.max_temperature());
    values[2] = tmp;
    tmp.set<VendorAtomValue::intValue>(speakerHealthStat.excursion());
    values[3] = tmp;
    tmp.set<VendorAtomValue::intValue>(speakerHealthStat.heartbeat());
    values[4] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorSpeakerStatsReported,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorSpeakerStatsReported to Stats service");
}

void reportSlowIo(const std::shared_ptr<IStats> &stats_client,
                  const PixelAtoms::VendorSlowIo &slowIo) {
    // Load values array
    std::vector<VendorAtomValue> values(2);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(slowIo.operation());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(slowIo.count());
    values[1] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorSlowIo,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorSlowIo to Stats service");
}

void reportChargeCycles(const std::shared_ptr<IStats> &stats_client,
                        const std::vector<int32_t> &chargeCycles) {
    // Load values array
    const int32_t kChargeCyclesBucketsCount =
            PixelAtoms::VendorChargeCycles::kCycleBucket10FieldNumber - kVendorAtomOffset + 1;
    std::vector<VendorAtomValue> values(kChargeCyclesBucketsCount);
    VendorAtomValue tmp;
    for (int32_t bucketIdx = 0; bucketIdx < kChargeCyclesBucketsCount; ++bucketIdx) {
        tmp.set<VendorAtomValue::intValue>(chargeCycles[bucketIdx]);
        values[bucketIdx] = tmp;
    }

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorChargeCycles,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorChargeCycles to Stats service");
}

void reportHardwareFailed(const std::shared_ptr<IStats> &stats_client,
                          const PixelAtoms::VendorHardwareFailed &failure) {
    // Load values array
    std::vector<VendorAtomValue> values(3);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(failure.hardware_type());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(failure.hardware_location());
    values[1] = tmp;
    tmp.set<VendorAtomValue::intValue>(failure.failure_code());
    values[2] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorHardwareFailed,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorHardwareFailed to Stats service");
}

void reportSpeechDspStat(const std::shared_ptr<IStats> &stats_client,
                         const PixelAtoms::VendorSpeechDspStat &dsp_stats) {
    // Load values array
    std::vector<VendorAtomValue> values(4);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(dsp_stats.total_uptime_millis());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(dsp_stats.total_downtime_millis());
    values[1] = tmp;
    tmp.set<VendorAtomValue::intValue>(dsp_stats.total_crash_count());
    values[2] = tmp;
    tmp.set<VendorAtomValue::intValue>(dsp_stats.total_recover_count());
    values[3] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorSpeechDspStat,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorSpeechDspStat to Stats service");
}

void reportUsbPortOverheat(const std::shared_ptr<IStats> &stats_client,
                           const PixelAtoms::VendorUsbPortOverheat &overheat_info) {
    // Load values array
    std::vector<VendorAtomValue> values(5);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(overheat_info.plug_temperature_deci_c());
    values[0] = tmp;
    tmp.set<VendorAtomValue::intValue>(overheat_info.max_temperature_deci_c());
    values[1] = tmp;
    tmp.set<VendorAtomValue::intValue>(overheat_info.time_to_overheat_secs());
    values[2] = tmp;
    tmp.set<VendorAtomValue::intValue>(overheat_info.time_to_hysteresis_secs());
    values[3] = tmp;
    tmp.set<VendorAtomValue::intValue>(overheat_info.time_to_inactive_secs());
    values[4] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorUsbPortOverheat,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorUsbPortOverheat to Stats service");
}

void reportUsbDataSessionEvent(const std::shared_ptr<IStats> &stats_client,
                               const PixelAtoms::VendorUsbDataSessionEvent &usb_data_event) {
    // Load values array
    std::vector<VendorAtomValue> values(4);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(usb_data_event.usb_role());
    values[0] = tmp;
    tmp.set<VendorAtomValue::repeatedIntValue>(std::vector<int32_t>(
            usb_data_event.usb_states().begin(), usb_data_event.usb_states().end()));
    values[1] = tmp;
    tmp.set<VendorAtomValue::repeatedLongValue>(std::vector<int64_t>(
            usb_data_event.elapsed_time_ms().begin(), usb_data_event.elapsed_time_ms().end()));
    values[2] = tmp;
    tmp.set<VendorAtomValue::longValue>(usb_data_event.duration_ms());
    values[3] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorUsbDataSessionEvent,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorUsbDataSessionEvent to Stats service");
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

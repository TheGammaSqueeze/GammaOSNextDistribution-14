/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <pixelstats/StatsHelper.h>
#include <pixelstats/SysfsCollector.h>

#define LOG_TAG "pixelstats-vendor"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <utils/Log.h>
#include <utils/Timers.h>

#include <mntent.h>
#include <sys/timerfd.h>
#include <sys/vfs.h>
#include <cinttypes>
#include <string>

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))
#endif

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::base::ReadFileToString;
using android::base::StartsWith;
using android::base::WriteStringToFile;
using android::hardware::google::pixel::PixelAtoms::BatteryCapacity;
using android::hardware::google::pixel::PixelAtoms::BlockStatsReported;
using android::hardware::google::pixel::PixelAtoms::BootStatsInfo;
using android::hardware::google::pixel::PixelAtoms::DisplayPanelErrorStats;
using android::hardware::google::pixel::PixelAtoms::DisplayPortErrorStats;
using android::hardware::google::pixel::PixelAtoms::F2fsAtomicWriteInfo;
using android::hardware::google::pixel::PixelAtoms::F2fsCompressionInfo;
using android::hardware::google::pixel::PixelAtoms::F2fsGcSegmentInfo;
using android::hardware::google::pixel::PixelAtoms::F2fsSmartIdleMaintEnabledStateChanged;
using android::hardware::google::pixel::PixelAtoms::F2fsStatsInfo;
using android::hardware::google::pixel::PixelAtoms::HDCPAuthTypeStats;
using android::hardware::google::pixel::PixelAtoms::PartitionsUsedSpaceReported;
using android::hardware::google::pixel::PixelAtoms::PcieLinkStatsReported;
using android::hardware::google::pixel::PixelAtoms::StorageUfsHealth;
using android::hardware::google::pixel::PixelAtoms::StorageUfsResetCount;
using android::hardware::google::pixel::PixelAtoms::ThermalDfsStats;
using android::hardware::google::pixel::PixelAtoms::VendorAudioAdaptedInfoStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioBtMediaStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioHardwareStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioOffloadedEffectStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioPcmStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioPdmStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorAudioThirdPartyEffectStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorChargeCycles;
using android::hardware::google::pixel::PixelAtoms::VendorHardwareFailed;
using android::hardware::google::pixel::PixelAtoms::VendorLongIRQStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorResumeLatencyStats;
using android::hardware::google::pixel::PixelAtoms::VendorSlowIo;
using android::hardware::google::pixel::PixelAtoms::VendorSpeakerImpedance;
using android::hardware::google::pixel::PixelAtoms::VendorSpeakerStatsReported;
using android::hardware::google::pixel::PixelAtoms::VendorSpeechDspStat;
using android::hardware::google::pixel::PixelAtoms::VendorTempResidencyStats;
using android::hardware::google::pixel::PixelAtoms::ZramBdStat;
using android::hardware::google::pixel::PixelAtoms::ZramMmStat;

SysfsCollector::SysfsCollector(const struct SysfsPaths &sysfs_paths)
    : kSlowioReadCntPath(sysfs_paths.SlowioReadCntPath),
      kSlowioWriteCntPath(sysfs_paths.SlowioWriteCntPath),
      kSlowioUnmapCntPath(sysfs_paths.SlowioUnmapCntPath),
      kSlowioSyncCntPath(sysfs_paths.SlowioSyncCntPath),
      kCycleCountBinsPath(sysfs_paths.CycleCountBinsPath),
      kImpedancePath(sysfs_paths.ImpedancePath),
      kCodecPath(sysfs_paths.CodecPath),
      kCodec1Path(sysfs_paths.Codec1Path),
      kSpeechDspPath(sysfs_paths.SpeechDspPath),
      kBatteryCapacityCC(sysfs_paths.BatteryCapacityCC),
      kBatteryCapacityVFSOC(sysfs_paths.BatteryCapacityVFSOC),
      kUFSLifetimeA(sysfs_paths.UFSLifetimeA),
      kUFSLifetimeB(sysfs_paths.UFSLifetimeB),
      kUFSLifetimeC(sysfs_paths.UFSLifetimeC),
      kF2fsStatsPath(sysfs_paths.F2fsStatsPath),
      kZramMmStatPath("/sys/block/zram0/mm_stat"),
      kZramBdStatPath("/sys/block/zram0/bd_stat"),
      kEEPROMPath(sysfs_paths.EEPROMPath),
      kBrownoutCsvPath(sysfs_paths.BrownoutCsvPath),
      kBrownoutLogPath(sysfs_paths.BrownoutLogPath),
      kBrownoutReasonProp(sysfs_paths.BrownoutReasonProp),
      kPowerMitigationStatsPath(sysfs_paths.MitigationPath),
      kPowerMitigationDurationPath(sysfs_paths.MitigationDurationPath),
      kSpeakerTemperaturePath(sysfs_paths.SpeakerTemperaturePath),
      kSpeakerExcursionPath(sysfs_paths.SpeakerExcursionPath),
      kSpeakerHeartbeatPath(sysfs_paths.SpeakerHeartBeatPath),
      kUFSErrStatsPath(sysfs_paths.UFSErrStatsPath),
      kBlockStatsLength(sysfs_paths.BlockStatsLength),
      kAmsRatePath(sysfs_paths.AmsRatePath),
      kThermalStatsPaths(sysfs_paths.ThermalStatsPaths),
      kCCARatePath(sysfs_paths.CCARatePath),
      kTempResidencyAndResetPaths(sysfs_paths.TempResidencyAndResetPaths),
      kLongIRQMetricsPath(sysfs_paths.LongIRQMetricsPath),
      kStormIRQMetricsPath(sysfs_paths.StormIRQMetricsPath),
      kIRQStatsResetPath(sysfs_paths.IRQStatsResetPath),
      kResumeLatencyMetricsPath(sysfs_paths.ResumeLatencyMetricsPath),
      kModemPcieLinkStatsPath(sysfs_paths.ModemPcieLinkStatsPath),
      kWifiPcieLinkStatsPath(sysfs_paths.WifiPcieLinkStatsPath),
      kDisplayStatsPaths(sysfs_paths.DisplayStatsPaths),
      kDisplayPortStatsPaths(sysfs_paths.DisplayPortStatsPaths),
      kHDCPStatsPaths(sysfs_paths.HDCPStatsPaths),
      kPDMStatePath(sysfs_paths.PDMStatePath),
      kWavesPath(sysfs_paths.WavesPath),
      kAdaptedInfoCountPath(sysfs_paths.AdaptedInfoCountPath),
      kAdaptedInfoDurationPath(sysfs_paths.AdaptedInfoDurationPath),
      kPcmLatencyPath(sysfs_paths.PcmLatencyPath),
      kPcmCountPath(sysfs_paths.PcmCountPath),
      kTotalCallCountPath(sysfs_paths.TotalCallCountPath),
      kOffloadEffectsIdPath(sysfs_paths.OffloadEffectsIdPath),
      kOffloadEffectsDurationPath(sysfs_paths.OffloadEffectsDurationPath),
      kBluetoothAudioUsagePath(sysfs_paths.BluetoothAudioUsagePath),
      kGMSRPath(sysfs_paths.GMSRPath),
      kMaxfgHistoryPath("/dev/maxfg_history") {}

bool SysfsCollector::ReadFileToInt(const std::string &path, int *val) {
    return ReadFileToInt(path.c_str(), val);
}

bool SysfsCollector::ReadFileToInt(const char *const path, int *val) {
    std::string file_contents;

    if (!ReadFileToString(path, &file_contents)) {
        ALOGE("Unable to read %s - %s", path, strerror(errno));
        return false;
    } else if (StartsWith(file_contents, "0x")) {
        if (sscanf(file_contents.c_str(), "0x%x", val) != 1) {
            ALOGE("Unable to convert %s to hex - %s", path, strerror(errno));
            return false;
        }
    } else if (sscanf(file_contents.c_str(), "%d", val) != 1) {
        ALOGE("Unable to convert %s to int - %s", path, strerror(errno));
        return false;
    }
    return true;
}

/**
 * Read the contents of kCycleCountBinsPath and report them via IStats HAL.
 * The contents are expected to be N buckets total, the nth of which indicates the
 * number of times battery %-full has been increased with the n/N% full bucket.
 */
void SysfsCollector::logBatteryChargeCycles(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    int val;
    if (kCycleCountBinsPath == nullptr || strlen(kCycleCountBinsPath) == 0) {
        ALOGV("Battery charge cycle path not specified");
        return;
    }
    if (!ReadFileToString(kCycleCountBinsPath, &file_contents)) {
        ALOGE("Unable to read battery charge cycles %s - %s", kCycleCountBinsPath, strerror(errno));
        return;
    }

    const int32_t kChargeCyclesBucketsCount =
            VendorChargeCycles::kCycleBucket10FieldNumber - kVendorAtomOffset + 1;
    std::vector<int32_t> charge_cycles;
    std::stringstream stream(file_contents);
    while (stream >> val) {
        charge_cycles.push_back(val);
    }
    if (charge_cycles.size() > kChargeCyclesBucketsCount) {
        ALOGW("Got excessive battery charge cycles count %" PRIu64,
              static_cast<uint64_t>(charge_cycles.size()));
    } else {
        // Push 0 for buckets that do not exist.
        for (int bucketIdx = charge_cycles.size(); bucketIdx < kChargeCyclesBucketsCount;
             ++bucketIdx) {
            charge_cycles.push_back(0);
        }
    }

    std::replace(file_contents.begin(), file_contents.end(), ' ', ',');
    reportChargeCycles(stats_client, charge_cycles);
}

/**
 * Read the contents of kEEPROMPath and report them.
 */
void SysfsCollector::logBatteryEEPROM(const std::shared_ptr<IStats> &stats_client) {
    if (kEEPROMPath == nullptr || strlen(kEEPROMPath) == 0) {
        ALOGV("Battery EEPROM path not specified");
    } else {
        battery_EEPROM_reporter_.checkAndReport(stats_client, kEEPROMPath);
    }

    battery_EEPROM_reporter_.checkAndReportGMSR(stats_client, kGMSRPath);

    battery_EEPROM_reporter_.checkAndReportMaxfgHistory(stats_client, kMaxfgHistoryPath);
}

/**
 * Log battery health stats
 */
void SysfsCollector::logBatteryHealth(const std::shared_ptr<IStats> &stats_client) {
    battery_health_reporter_.checkAndReportStatus(stats_client);
}

/**
 * Check the codec for failures over the past 24hr.
 */
void SysfsCollector::logCodecFailed(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kCodecPath == nullptr || strlen(kCodecPath) == 0) {
        ALOGV("Audio codec path not specified");
        return;
    }
    if (!ReadFileToString(kCodecPath, &file_contents)) {
        ALOGE("Unable to read codec state %s - %s", kCodecPath, strerror(errno));
        return;
    }
    if (file_contents == "0") {
        return;
    } else {
        VendorHardwareFailed failure;
        failure.set_hardware_type(VendorHardwareFailed::HARDWARE_FAILED_CODEC);
        failure.set_hardware_location(0);
        failure.set_failure_code(VendorHardwareFailed::COMPLETE);
        reportHardwareFailed(stats_client, failure);
    }
}

/**
 * Check the codec1 for failures over the past 24hr.
 */
void SysfsCollector::logCodec1Failed(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kCodec1Path == nullptr || strlen(kCodec1Path) == 0) {
        ALOGV("Audio codec1 path not specified");
        return;
    }
    if (!ReadFileToString(kCodec1Path, &file_contents)) {
        ALOGE("Unable to read codec1 state %s - %s", kCodec1Path, strerror(errno));
        return;
    }
    if (file_contents == "0") {
        return;
    } else {
        ALOGE("%s report hardware fail", kCodec1Path);
        VendorHardwareFailed failure;
        failure.set_hardware_type(VendorHardwareFailed::HARDWARE_FAILED_CODEC);
        failure.set_hardware_location(1);
        failure.set_failure_code(VendorHardwareFailed::COMPLETE);
        reportHardwareFailed(stats_client, failure);
    }
}

void SysfsCollector::reportSlowIoFromFile(const std::shared_ptr<IStats> &stats_client,
                                          const char *path,
                                          const VendorSlowIo::IoOperation &operation_s) {
    std::string file_contents;
    if (path == nullptr || strlen(path) == 0) {
        ALOGV("slow_io path not specified");
        return;
    }
    if (!ReadFileToString(path, &file_contents)) {
        ALOGE("Unable to read slowio %s - %s", path, strerror(errno));
        return;
    } else {
        int32_t slow_io_count = 0;
        if (sscanf(file_contents.c_str(), "%d", &slow_io_count) != 1) {
            ALOGE("Unable to parse %s from file %s to int.", file_contents.c_str(), path);
        } else if (slow_io_count > 0) {
            VendorSlowIo slow_io;
            slow_io.set_operation(operation_s);
            slow_io.set_count(slow_io_count);
            reportSlowIo(stats_client, slow_io);
        }
        // Clear the stats
        if (!android::base::WriteStringToFile("0", path, true)) {
            ALOGE("Unable to clear SlowIO entry %s - %s", path, strerror(errno));
        }
    }
}

/**
 * Check for slow IO operations.
 */
void SysfsCollector::logSlowIO(const std::shared_ptr<IStats> &stats_client) {
    reportSlowIoFromFile(stats_client, kSlowioReadCntPath, VendorSlowIo::READ);
    reportSlowIoFromFile(stats_client, kSlowioWriteCntPath, VendorSlowIo::WRITE);
    reportSlowIoFromFile(stats_client, kSlowioUnmapCntPath, VendorSlowIo::UNMAP);
    reportSlowIoFromFile(stats_client, kSlowioSyncCntPath, VendorSlowIo::SYNC);
}

/**
 * Report the last-detected impedance of left & right speakers.
 */
void SysfsCollector::logSpeakerImpedance(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kImpedancePath == nullptr || strlen(kImpedancePath) == 0) {
        ALOGV("Audio impedance path not specified");
        return;
    }
    if (!ReadFileToString(kImpedancePath, &file_contents)) {
        ALOGE("Unable to read impedance path %s", kImpedancePath);
        return;
    }

    float left, right;
    if (sscanf(file_contents.c_str(), "%g,%g", &left, &right) != 2) {
        ALOGE("Unable to parse speaker impedance %s", file_contents.c_str());
        return;
    }
    VendorSpeakerImpedance left_obj;
    left_obj.set_speaker_location(0);
    left_obj.set_impedance(static_cast<int32_t>(left * 1000));

    VendorSpeakerImpedance right_obj;
    right_obj.set_speaker_location(1);
    right_obj.set_impedance(static_cast<int32_t>(right * 1000));

    reportSpeakerImpedance(stats_client, left_obj);
    reportSpeakerImpedance(stats_client, right_obj);
}

/**
 * Report the last-detected impedance, temperature and heartbeats of left & right speakers.
 */
void SysfsCollector::logSpeakerHealthStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents_impedance;
    std::string file_contents_temperature;
    std::string file_contents_excursion;
    std::string file_contents_heartbeat;
    int count, i;
    float impedance_ohm[4];
    float temperature_C[4];
    float excursion_mm[4];
    float heartbeat[4];

    if (kImpedancePath == nullptr || strlen(kImpedancePath) == 0) {
        ALOGD("Audio impedance path not specified");
        return;
    } else if (!ReadFileToString(kImpedancePath, &file_contents_impedance)) {
        ALOGD("Unable to read speaker impedance path %s", kImpedancePath);
        return;
    }

    if (kSpeakerTemperaturePath == nullptr || strlen(kSpeakerTemperaturePath) == 0) {
        ALOGD("Audio speaker temperature path not specified");
        return;
    } else if (!ReadFileToString(kSpeakerTemperaturePath, &file_contents_temperature)) {
        ALOGD("Unable to read speaker temperature path %s", kSpeakerTemperaturePath);
        return;
    }

    if (kSpeakerExcursionPath == nullptr || strlen(kSpeakerExcursionPath) == 0) {
        ALOGD("Audio speaker excursion path not specified");
        return;
    } else if (!ReadFileToString(kSpeakerExcursionPath, &file_contents_excursion)) {
        ALOGD("Unable to read speaker excursion path %s", kSpeakerExcursionPath);
        return;
    }

    if (kSpeakerHeartbeatPath == nullptr || strlen(kSpeakerHeartbeatPath) == 0) {
        ALOGD("Audio speaker heartbeat path not specified");
        return;
    } else if (!ReadFileToString(kSpeakerHeartbeatPath, &file_contents_heartbeat)) {
        ALOGD("Unable to read speaker heartbeat path %s", kSpeakerHeartbeatPath);
        return;
    }

    count = sscanf(file_contents_impedance.c_str(), "%g,%g,%g,%g", &impedance_ohm[0],
                   &impedance_ohm[1], &impedance_ohm[2], &impedance_ohm[3]);
    if (count <= 0)
        return;

    count = sscanf(file_contents_temperature.c_str(), "%g,%g,%g,%g", &temperature_C[0],
                   &temperature_C[1], &temperature_C[2], &temperature_C[3]);
    if (count <= 0)
        return;

    count = sscanf(file_contents_excursion.c_str(), "%g,%g,%g,%g", &excursion_mm[0],
                   &excursion_mm[1], &excursion_mm[2], &excursion_mm[3]);
    if (count <= 0)
        return;

    count = sscanf(file_contents_heartbeat.c_str(), "%g,%g,%g,%g", &heartbeat[0], &heartbeat[1],
                   &heartbeat[2], &heartbeat[3]);
    if (count <= 0)
        return;

    VendorSpeakerStatsReported obj[4];
    for (i = 0; i < count && i < 4; i++) {
        obj[i].set_speaker_location(i);
        obj[i].set_impedance(static_cast<int32_t>(impedance_ohm[i] * 1000));
        obj[i].set_max_temperature(static_cast<int32_t>(temperature_C[i] * 1000));
        obj[i].set_excursion(static_cast<int32_t>(excursion_mm[i] * 1000));
        obj[i].set_heartbeat(static_cast<int32_t>(heartbeat[i]));

        reportSpeakerHealthStat(stats_client, obj[i]);
    }
}

void SysfsCollector::logDisplayStats(const std::shared_ptr<IStats> &stats_client) {
    display_stats_reporter_.logDisplayStats(stats_client, kDisplayStatsPaths,
                                            DisplayStatsReporter::DISP_PANEL_STATE);
}

void SysfsCollector::logDisplayPortStats(const std::shared_ptr<IStats> &stats_client) {
    display_stats_reporter_.logDisplayStats(stats_client, kDisplayPortStatsPaths,
                                            DisplayStatsReporter::DISP_PORT_STATE);
}

void SysfsCollector::logHDCPStats(const std::shared_ptr<IStats> &stats_client) {
    display_stats_reporter_.logDisplayStats(stats_client, kHDCPStatsPaths,
                                            DisplayStatsReporter::HDCP_STATE);
}

void SysfsCollector::logThermalStats(const std::shared_ptr<IStats> &stats_client) {
    thermal_stats_reporter_.logThermalStats(stats_client, kThermalStatsPaths);
}

/**
 * Report the Speech DSP state.
 */
void SysfsCollector::logSpeechDspStat(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kSpeechDspPath == nullptr || strlen(kSpeechDspPath) == 0) {
        ALOGV("Speech DSP path not specified");
        return;
    }
    if (!ReadFileToString(kSpeechDspPath, &file_contents)) {
        ALOGE("Unable to read speech dsp path %s", kSpeechDspPath);
        return;
    }

    int32_t up_time = 0, down_time = 0, crash_count = 0, recover_count = 0;
    if (sscanf(file_contents.c_str(), "%d,%d,%d,%d", &up_time, &down_time, &crash_count,
               &recover_count) != 4) {
        ALOGE("Unable to parse speech dsp stat %s", file_contents.c_str());
        return;
    }

    ALOGD("SpeechDSP uptime %d downtime %d crashcount %d recovercount %d", up_time, down_time,
          crash_count, recover_count);
    VendorSpeechDspStat dsp_stat;
    dsp_stat.set_total_uptime_millis(up_time);
    dsp_stat.set_total_downtime_millis(down_time);
    dsp_stat.set_total_crash_count(crash_count);
    dsp_stat.set_total_recover_count(recover_count);

    reportSpeechDspStat(stats_client, dsp_stat);
}

void SysfsCollector::logBatteryCapacity(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kBatteryCapacityCC == nullptr || strlen(kBatteryCapacityCC) == 0) {
        ALOGV("Battery Capacity CC path not specified");
        return;
    }
    if (kBatteryCapacityVFSOC == nullptr || strlen(kBatteryCapacityVFSOC) == 0) {
        ALOGV("Battery Capacity VFSOC path not specified");
        return;
    }
    int delta_cc_sum, delta_vfsoc_sum;
    if (!ReadFileToInt(kBatteryCapacityCC, &delta_cc_sum) ||
            !ReadFileToInt(kBatteryCapacityVFSOC, &delta_vfsoc_sum))
        return;

    // Load values array
    std::vector<VendorAtomValue> values(2);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(delta_cc_sum);
    values[BatteryCapacity::kDeltaCcSumFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(delta_vfsoc_sum);
    values[BatteryCapacity::kDeltaVfsocSumFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kBatteryCapacity,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report ChargeStats to Stats service");
}

void SysfsCollector::logUFSLifetime(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (kUFSLifetimeA == nullptr || strlen(kUFSLifetimeA) == 0) {
        ALOGV("UFS lifetimeA path not specified");
        return;
    }
    if (kUFSLifetimeB == nullptr || strlen(kUFSLifetimeB) == 0) {
        ALOGV("UFS lifetimeB path not specified");
        return;
    }
    if (kUFSLifetimeC == nullptr || strlen(kUFSLifetimeC) == 0) {
        ALOGV("UFS lifetimeC path not specified");
        return;
    }

    int lifetimeA = 0, lifetimeB = 0, lifetimeC = 0;
    if (!ReadFileToInt(kUFSLifetimeA, &lifetimeA) ||
        !ReadFileToInt(kUFSLifetimeB, &lifetimeB) ||
        !ReadFileToInt(kUFSLifetimeC, &lifetimeC)) {
        ALOGE("Unable to read UFS lifetime : %s", strerror(errno));
        return;
    }

    // Load values array
    std::vector<VendorAtomValue> values(3);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(lifetimeA);
    values[StorageUfsHealth::kLifetimeAFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(lifetimeB);
    values[StorageUfsHealth::kLifetimeBFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(lifetimeC);
    values[StorageUfsHealth::kLifetimeCFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kStorageUfsHealth,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report UfsHealthStat to Stats service");
    }
}

void SysfsCollector::logUFSErrorStats(const std::shared_ptr<IStats> &stats_client) {
    int value, host_reset_count = 0;

    if (kUFSErrStatsPath.empty() || strlen(kUFSErrStatsPath.front().c_str()) == 0) {
        ALOGV("UFS host reset count path not specified");
        return;
    }

    for (int i = 0; i < kUFSErrStatsPath.size(); i++) {
        if (!ReadFileToInt(kUFSErrStatsPath[i], &value)) {
            ALOGE("Unable to read host reset count");
            return;
        }
        host_reset_count += value;
    }

    // Load values array
    std::vector<VendorAtomValue> values(1);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(host_reset_count);
    values[StorageUfsResetCount::kHostResetCountFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kUfsResetCount,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report UFS host reset count to Stats service");
    }
}

static std::string getUserDataBlock() {
    std::unique_ptr<std::FILE, int (*)(std::FILE*)> fp(setmntent("/proc/mounts", "re"), endmntent);
    if (fp == nullptr) {
        ALOGE("Error opening /proc/mounts");
        return "";
    }

    mntent* mentry;
    while ((mentry = getmntent(fp.get())) != nullptr) {
        if (strcmp(mentry->mnt_dir, "/data") == 0) {
            return std::string(basename(mentry->mnt_fsname));
        }
    }
    return "";
}

void SysfsCollector::logF2fsStats(const std::shared_ptr<IStats> &stats_client) {
    int dirty, free, cp_calls_fg, gc_calls_fg, moved_block_fg, vblocks;
    int cp_calls_bg, gc_calls_bg, moved_block_bg;

    if (kF2fsStatsPath == nullptr) {
        ALOGE("F2fs stats path not specified");
        return;
    }

    const std::string userdataBlock = getUserDataBlock();
    const std::string kF2fsStatsDir = kF2fsStatsPath + userdataBlock;

    if (!ReadFileToInt(kF2fsStatsDir + "/dirty_segments", &dirty)) {
        ALOGV("Unable to read dirty segments");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/free_segments", &free)) {
        ALOGV("Unable to read free segments");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/cp_foreground_calls", &cp_calls_fg)) {
        ALOGV("Unable to read cp_foreground_calls");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/cp_background_calls", &cp_calls_bg)) {
        ALOGV("Unable to read cp_background_calls");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/gc_foreground_calls", &gc_calls_fg)) {
        ALOGV("Unable to read gc_foreground_calls");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/gc_background_calls", &gc_calls_bg)) {
        ALOGV("Unable to read gc_background_calls");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/moved_blocks_foreground", &moved_block_fg)) {
        ALOGV("Unable to read moved_blocks_foreground");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/moved_blocks_background", &moved_block_bg)) {
        ALOGV("Unable to read moved_blocks_background");
    }

    if (!ReadFileToInt(kF2fsStatsDir + "/avg_vblocks", &vblocks)) {
        ALOGV("Unable to read avg_vblocks");
    }

    // Load values array
    std::vector<VendorAtomValue> values(9);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(dirty);
    values[F2fsStatsInfo::kDirtySegmentsFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(free);
    values[F2fsStatsInfo::kFreeSegmentsFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(cp_calls_fg);
    values[F2fsStatsInfo::kCpCallsFgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(cp_calls_bg);
    values[F2fsStatsInfo::kCpCallsBgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(gc_calls_fg);
    values[F2fsStatsInfo::kGcCallsFgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(gc_calls_bg);
    values[F2fsStatsInfo::kGcCallsBgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(moved_block_fg);
    values[F2fsStatsInfo::kMovedBlocksFgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(moved_block_bg);
    values[F2fsStatsInfo::kMovedBlocksBgFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(vblocks);
    values[F2fsStatsInfo::kValidBlocksFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kF2FsStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report F2fs stats to Stats service");
    }
}

void SysfsCollector::logF2fsAtomicWriteInfo(const std::shared_ptr<IStats> &stats_client) {
    int peak_atomic_write, committed_atomic_block, revoked_atomic_block;

    if (kF2fsStatsPath == nullptr) {
        ALOGV("F2fs stats path not specified");
        return;
    }

    std::string userdataBlock = getUserDataBlock();

    std::string path = kF2fsStatsPath + (userdataBlock + "/peak_atomic_write");
    if (!ReadFileToInt(path, &peak_atomic_write)) {
        ALOGE("Unable to read peak_atomic_write");
        return;
    } else {
        if (!WriteStringToFile(std::to_string(0), path)) {
            ALOGE("Failed to write to file %s", path.c_str());
            return;
        }
    }

    path = kF2fsStatsPath + (userdataBlock + "/committed_atomic_block");
    if (!ReadFileToInt(path, &committed_atomic_block)) {
        ALOGE("Unable to read committed_atomic_block");
        return;
    } else {
        if (!WriteStringToFile(std::to_string(0), path)) {
            ALOGE("Failed to write to file %s", path.c_str());
            return;
        }
    }

    path = kF2fsStatsPath + (userdataBlock + "/revoked_atomic_block");
    if (!ReadFileToInt(path, &revoked_atomic_block)) {
        ALOGE("Unable to read revoked_atomic_block");
        return;
    } else {
        if (!WriteStringToFile(std::to_string(0), path)) {
            ALOGE("Failed to write to file %s", path.c_str());
            return;
        }
    }

    // Load values array
    std::vector<VendorAtomValue> values(3);
    values[F2fsAtomicWriteInfo::kPeakAtomicWriteFieldNumber - kVendorAtomOffset] =
                    VendorAtomValue::make<VendorAtomValue::intValue>(peak_atomic_write);
    values[F2fsAtomicWriteInfo::kCommittedAtomicBlockFieldNumber - kVendorAtomOffset] =
                    VendorAtomValue::make<VendorAtomValue::intValue>(committed_atomic_block);
    values[F2fsAtomicWriteInfo::kRevokedAtomicBlockFieldNumber - kVendorAtomOffset] =
                    VendorAtomValue::make<VendorAtomValue::intValue>(revoked_atomic_block);

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kF2FsAtomicWriteInfo,
                        .values = values};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report F2fs Atomic Write info to Stats service");
    }
}

void SysfsCollector::logF2fsCompressionInfo(const std::shared_ptr<IStats> &stats_client) {
    int compr_written_blocks, compr_saved_blocks, compr_new_inodes;

    if (kF2fsStatsPath == nullptr) {
        ALOGV("F2fs stats path not specified");
        return;
    }

    std::string userdataBlock = getUserDataBlock();

    std::string path = kF2fsStatsPath + (userdataBlock + "/compr_written_block");
    if (!ReadFileToInt(path, &compr_written_blocks)) {
        ALOGE("Unable to read compression written blocks");
        return;
    }

    path = kF2fsStatsPath + (userdataBlock + "/compr_saved_block");
    if (!ReadFileToInt(path, &compr_saved_blocks)) {
        ALOGE("Unable to read compression saved blocks");
        return;
    } else {
        if (!WriteStringToFile(std::to_string(0), path)) {
            ALOGE("Failed to write to file %s", path.c_str());
            return;
        }
    }

    path = kF2fsStatsPath + (userdataBlock + "/compr_new_inode");
    if (!ReadFileToInt(path, &compr_new_inodes)) {
        ALOGE("Unable to read compression new inodes");
        return;
    } else {
        if (!WriteStringToFile(std::to_string(0), path)) {
            ALOGE("Failed to write to file %s", path.c_str());
            return;
        }
    }

    // Load values array
    std::vector<VendorAtomValue> values(3);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(compr_written_blocks);
    values[F2fsCompressionInfo::kComprWrittenBlocksFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(compr_saved_blocks);
    values[F2fsCompressionInfo::kComprSavedBlocksFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(compr_new_inodes);
    values[F2fsCompressionInfo::kComprNewInodesFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kF2FsCompressionInfo,
                        .values = values};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report F2fs compression info to Stats service");
    }
}

int SysfsCollector::getReclaimedSegments(const std::string &mode) {
    std::string userDataStatsPath = kF2fsStatsPath + getUserDataBlock();
    std::string gcSegmentModePath = userDataStatsPath + "/gc_segment_mode";
    std::string gcReclaimedSegmentsPath = userDataStatsPath + "/gc_reclaimed_segments";
    int reclaimed_segments;

    if (!WriteStringToFile(mode, gcSegmentModePath)) {
        ALOGE("Failed to change gc_segment_mode to %s", mode.c_str());
        return -1;
    }

    if (!ReadFileToInt(gcReclaimedSegmentsPath, &reclaimed_segments)) {
        ALOGE("GC mode(%s): Unable to read gc_reclaimed_segments", mode.c_str());
        return -1;
    }

    if (!WriteStringToFile(std::to_string(0), gcReclaimedSegmentsPath)) {
        ALOGE("GC mode(%s): Failed to reset gc_reclaimed_segments", mode.c_str());
        return -1;
    }

    return reclaimed_segments;
}

void SysfsCollector::logF2fsGcSegmentInfo(const std::shared_ptr<IStats> &stats_client) {
    int reclaimed_segments_normal, reclaimed_segments_urgent_high;
    int reclaimed_segments_urgent_mid, reclaimed_segments_urgent_low;
    std::string gc_normal_mode = std::to_string(0);         // GC normal mode
    std::string gc_urgent_high_mode = std::to_string(4);    // GC urgent high mode
    std::string gc_urgent_low_mode = std::to_string(5);     // GC urgent low mode
    std::string gc_urgent_mid_mode = std::to_string(6);     // GC urgent mid mode

    if (kF2fsStatsPath == nullptr) {
        ALOGV("F2fs stats path not specified");
        return;
    }

    reclaimed_segments_normal = getReclaimedSegments(gc_normal_mode);
    if (reclaimed_segments_normal == -1) return;
    reclaimed_segments_urgent_high = getReclaimedSegments(gc_urgent_high_mode);
    if (reclaimed_segments_urgent_high == -1) return;
    reclaimed_segments_urgent_low = getReclaimedSegments(gc_urgent_low_mode);
    if (reclaimed_segments_urgent_low == -1) return;
    reclaimed_segments_urgent_mid = getReclaimedSegments(gc_urgent_mid_mode);
    if (reclaimed_segments_urgent_mid == -1) return;

    // Load values array
    std::vector<VendorAtomValue> values(4);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(reclaimed_segments_normal);
    values[F2fsGcSegmentInfo::kReclaimedSegmentsNormalFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(reclaimed_segments_urgent_high);
    values[F2fsGcSegmentInfo::kReclaimedSegmentsUrgentHighFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(reclaimed_segments_urgent_low);
    values[F2fsGcSegmentInfo::kReclaimedSegmentsUrgentLowFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(reclaimed_segments_urgent_mid);
    values[F2fsGcSegmentInfo::kReclaimedSegmentsUrgentMidFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kF2FsGcSegmentInfo,
                        .values = values};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report F2fs GC Segment info to Stats service");
    }
}

void SysfsCollector::logF2fsSmartIdleMaintEnabled(const std::shared_ptr<IStats> &stats_client) {
    bool smart_idle_enabled = android::base::GetBoolProperty(
        "persist.device_config.storage_native_boot.smart_idle_maint_enabled", false);

    // Load values array
    VendorAtomValue tmp;
    std::vector<VendorAtomValue> values(1);
    tmp.set<VendorAtomValue::intValue>(smart_idle_enabled);
    values[F2fsSmartIdleMaintEnabledStateChanged::kEnabledFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = PixelAtoms::ReverseDomainNames().pixel(),
                        .atomId = PixelAtoms::Atom::kF2FsSmartIdleMaintEnabledStateChanged,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report F2fsSmartIdleMaintEnabled to Stats service");
    }
}

void SysfsCollector::logBlockStatsReported(const std::shared_ptr<IStats> &stats_client) {
    std::string sdaPath = "/sys/block/sda/stat";
    std::string file_contents;
    std::string stat;
    std::vector<std::string> stats;
    std::stringstream ss;

    // These index comes from kernel Document
    // Documentation/ABI/stable/sysfs-block
    const int READ_IO_IDX = 0, READ_SEC_IDX = 2, READ_TICK_IDX = 3;
    const int WRITE_IO_IDX = 4, WRITE_SEC_IDX = 6, WRITE_TICK_IDX = 7;
    uint64_t read_io, read_sectors, read_ticks;
    uint64_t write_io, write_sectors, write_ticks;

    if (!ReadFileToString(sdaPath.c_str(), &file_contents)) {
        ALOGE("Failed to read block layer stat %s", sdaPath.c_str());
        return;
    }

    ss.str(file_contents);
    while (ss >> stat) {
        stats.push_back(stat);
    }

    if (stats.size() < kBlockStatsLength) {
        ALOGE("block layer stat format is incorrect %s, length %zu/%d", file_contents.c_str(),
              stats.size(), kBlockStatsLength);
        return;
    }

    read_io = std::stoul(stats[READ_IO_IDX]);
    read_sectors = std::stoul(stats[READ_SEC_IDX]);
    read_ticks = std::stoul(stats[READ_TICK_IDX]);
    write_io = std::stoul(stats[WRITE_IO_IDX]);
    write_sectors = std::stoul(stats[WRITE_SEC_IDX]);
    write_ticks = std::stoul(stats[WRITE_TICK_IDX]);

    // Load values array
    std::vector<VendorAtomValue> values(6);
    values[BlockStatsReported::kReadIoFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(read_io);
    values[BlockStatsReported::kReadSectorsFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(read_sectors);
    values[BlockStatsReported::kReadTicksFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(read_ticks);
    values[BlockStatsReported::kWriteIoFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(write_io);
    values[BlockStatsReported::kWriteSectorsFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(write_sectors);
    values[BlockStatsReported::kWriteTicksFieldNumber - kVendorAtomOffset] =
                        VendorAtomValue::make<VendorAtomValue::longValue>(write_ticks);

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = PixelAtoms::ReverseDomainNames().pixel(),
                        .atomId = PixelAtoms::Atom::kBlockStatsReported,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report block layer stats to Stats service");
    }
}

void SysfsCollector::logTempResidencyStats(const std::shared_ptr<IStats> &stats_client) {
    for (const auto &temp_residency_and_reset_path : kTempResidencyAndResetPaths) {
        temp_residency_reporter_.logTempResidencyStats(stats_client,
                                                       temp_residency_and_reset_path.first,
                                                       temp_residency_and_reset_path.second);
    }
}

void SysfsCollector::reportZramMmStat(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (!kZramMmStatPath) {
        ALOGV("ZramMmStat path not specified");
        return;
    }

    if (!ReadFileToString(kZramMmStatPath, &file_contents)) {
        ALOGE("Unable to ZramMmStat %s - %s", kZramMmStatPath, strerror(errno));
        return;
    } else {
        int64_t orig_data_size = 0;
        int64_t compr_data_size = 0;
        int64_t mem_used_total = 0;
        int64_t mem_limit = 0;
        int64_t max_used_total = 0;
        int64_t same_pages = 0;
        int64_t pages_compacted = 0;
        int64_t huge_pages = 0;
        int64_t huge_pages_since_boot = 0;

        // huge_pages_since_boot may not exist according to kernel version.
        // only check if the number of collected data is equal or larger then 8
        if (sscanf(file_contents.c_str(),
                   "%" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64 " %" SCNd64
                   " %" SCNd64 " %" SCNd64 " %" SCNd64,
                   &orig_data_size, &compr_data_size, &mem_used_total, &mem_limit, &max_used_total,
                   &same_pages, &pages_compacted, &huge_pages, &huge_pages_since_boot) < 8) {
            ALOGE("Unable to parse ZramMmStat %s from file %s to int.",
                    file_contents.c_str(), kZramMmStatPath);
        }

        // Load values array.
        // The size should be the same as the number of fields in ZramMmStat
        std::vector<VendorAtomValue> values(6);
        VendorAtomValue tmp;
        tmp.set<VendorAtomValue::longValue>(orig_data_size);
        values[ZramMmStat::kOrigDataSizeFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(compr_data_size);
        values[ZramMmStat::kComprDataSizeFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(mem_used_total);
        values[ZramMmStat::kMemUsedTotalFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(same_pages);
        values[ZramMmStat::kSamePagesFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(huge_pages);
        values[ZramMmStat::kHugePagesFieldNumber - kVendorAtomOffset] = tmp;

        // Skip the first data to avoid a big spike in this accumulated value.
        if (prev_huge_pages_since_boot_ == -1)
            tmp.set<VendorAtomValue::longValue>(0);
        else
            tmp.set<VendorAtomValue::longValue>(huge_pages_since_boot -
                                                prev_huge_pages_since_boot_);

        values[ZramMmStat::kHugePagesSinceBootFieldNumber - kVendorAtomOffset] = tmp;
        prev_huge_pages_since_boot_ = huge_pages_since_boot;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kZramMmStat,
                            .values = std::move(values)};
        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Zram Unable to report ZramMmStat to Stats service");
    }
}

void SysfsCollector::reportZramBdStat(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    if (!kZramBdStatPath) {
        ALOGV("ZramBdStat path not specified");
        return;
    }

    if (!ReadFileToString(kZramBdStatPath, &file_contents)) {
        ALOGE("Unable to ZramBdStat %s - %s", kZramBdStatPath, strerror(errno));
        return;
    } else {
        int64_t bd_count = 0;
        int64_t bd_reads = 0;
        int64_t bd_writes = 0;

        if (sscanf(file_contents.c_str(), "%" SCNd64 " %" SCNd64 " %" SCNd64,
                                &bd_count, &bd_reads, &bd_writes) != 3) {
            ALOGE("Unable to parse ZramBdStat %s from file %s to int.",
                    file_contents.c_str(), kZramBdStatPath);
        }

        // Load values array
        std::vector<VendorAtomValue> values(3);
        VendorAtomValue tmp;
        tmp.set<VendorAtomValue::longValue>(bd_count);
        values[ZramBdStat::kBdCountFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(bd_reads);
        values[ZramBdStat::kBdReadsFieldNumber - kVendorAtomOffset] = tmp;
        tmp.set<VendorAtomValue::longValue>(bd_writes);
        values[ZramBdStat::kBdWritesFieldNumber - kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kZramBdStat,
                            .values = std::move(values)};
        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Zram Unable to report ZramBdStat to Stats service");
    }
}

void SysfsCollector::logZramStats(const std::shared_ptr<IStats> &stats_client) {
    reportZramMmStat(stats_client);
    reportZramBdStat(stats_client);
}

void SysfsCollector::logBootStats(const std::shared_ptr<IStats> &stats_client) {
    int mounted_time_sec = 0;

    if (kF2fsStatsPath == nullptr) {
        ALOGE("F2fs stats path not specified");
        return;
    }

    std::string userdataBlock = getUserDataBlock();

    if (!ReadFileToInt(kF2fsStatsPath + (userdataBlock + "/mounted_time_sec"), &mounted_time_sec)) {
        ALOGV("Unable to read mounted_time_sec");
        return;
    }

    int fsck_time_ms = android::base::GetIntProperty("ro.boottime.init.fsck.data", 0);
    int checkpoint_time_ms = android::base::GetIntProperty("ro.boottime.init.mount.data", 0);

    if (fsck_time_ms == 0 && checkpoint_time_ms == 0) {
        ALOGV("Not yet initialized");
        return;
    }

    // Load values array
    std::vector<VendorAtomValue> values(3);
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::intValue>(mounted_time_sec);
    values[BootStatsInfo::kMountedTimeSecFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(fsck_time_ms / 1000);
    values[BootStatsInfo::kFsckTimeSecFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(checkpoint_time_ms / 1000);
    values[BootStatsInfo::kCheckpointTimeSecFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kBootStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report Boot stats to Stats service");
    } else {
        log_once_reported = true;
    }
}

/**
 * Report the AMS & CCA rate.
 */
void SysfsCollector::logVendorAudioHardwareStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    uint32_t milli_ams_rate, c1, c2, c3, c4;
    uint32_t total_call_voice = 0, total_call_voip = 0;
    bool isAmsReady = false, isCCAReady = false;

    if (kAmsRatePath == nullptr) {
        ALOGD("Audio AMS Rate path not specified");
    } else {
        if (!ReadFileToString(kAmsRatePath, &file_contents)) {
            ALOGD("Unable to read ams_rate path %s", kAmsRatePath);
        } else {
            if (sscanf(file_contents.c_str(), "%u", &milli_ams_rate) != 1) {
                ALOGD("Unable to parse ams_rate %s", file_contents.c_str());
            } else {
                isAmsReady = true;
                ALOGD("milli_ams_rate = %u", milli_ams_rate);
            }
        }
    }

    if (kCCARatePath == nullptr) {
        ALOGD("Audio CCA Rate path not specified");
    } else {
        if (!ReadFileToString(kCCARatePath, &file_contents)) {
            ALOGD("Unable to read cca_rate path %s", kCCARatePath);
        } else {
            if (sscanf(file_contents.c_str(), "%u %u %u %u", &c1, &c2, &c3, &c4) != 4) {
                ALOGD("Unable to parse cca rates %s", file_contents.c_str());
            } else {
                isCCAReady = true;
            }
        }
    }

    if (kTotalCallCountPath == nullptr) {
        ALOGD("Total call count path not specified");
    } else {
        if (!ReadFileToString(kTotalCallCountPath, &file_contents)) {
            ALOGD("Unable to read total call path %s", kTotalCallCountPath);
        } else {
            if (sscanf(file_contents.c_str(), "%u %u", &total_call_voice, &total_call_voip) != 2) {
                ALOGD("Unable to parse total call %s", file_contents.c_str());
            }
        }
    }

    if (!(isAmsReady || isCCAReady)) {
        ALOGD("no ams or cca data to report");
        return;
    }

    // Sending ams_rate, total_call, c1 and c2
    {
        std::vector<VendorAtomValue> values(7);
        VendorAtomValue tmp;

        if (isAmsReady) {
            tmp.set<VendorAtomValue::intValue>(milli_ams_rate);
            values[VendorAudioHardwareStatsReported::kMilliRateOfAmsPerDayFieldNumber -
                   kVendorAtomOffset] = tmp;
        }

        tmp.set<VendorAtomValue::intValue>(1);
        values[VendorAudioHardwareStatsReported::kSourceFieldNumber - kVendorAtomOffset] = tmp;

        if (isCCAReady) {
            tmp.set<VendorAtomValue::intValue>(c1);
            values[VendorAudioHardwareStatsReported::kCcaActiveCountPerDayFieldNumber -
                   kVendorAtomOffset] = tmp;

            tmp.set<VendorAtomValue::intValue>(c2);
            values[VendorAudioHardwareStatsReported::kCcaEnableCountPerDayFieldNumber -
                   kVendorAtomOffset] = tmp;
        }

        tmp.set<VendorAtomValue::intValue>(total_call_voice);
        values[VendorAudioHardwareStatsReported::kTotalCallCountPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioHardwareStatsReported,
                            .values = std::move(values)};
        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioHardwareStatsReported to Stats service");
    }

    // Sending total_call, c3 and c4
    {
        std::vector<VendorAtomValue> values(7);
        VendorAtomValue tmp;

        tmp.set<VendorAtomValue::intValue>(0);
        values[VendorAudioHardwareStatsReported::kSourceFieldNumber - kVendorAtomOffset] = tmp;

        if (isCCAReady) {
            tmp.set<VendorAtomValue::intValue>(c3);
            values[VendorAudioHardwareStatsReported::kCcaActiveCountPerDayFieldNumber -
                   kVendorAtomOffset] = tmp;

            tmp.set<VendorAtomValue::intValue>(c4);
            values[VendorAudioHardwareStatsReported::kCcaEnableCountPerDayFieldNumber -
                   kVendorAtomOffset] = tmp;
        }

        tmp.set<VendorAtomValue::intValue>(total_call_voip);
        values[VendorAudioHardwareStatsReported::kTotalCallCountPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioHardwareStatsReported,
                            .values = std::move(values)};
        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioHardwareStatsReported to Stats service");
    }
}

/**
 * Report PDM States which indicates microphone background noise level.
 * This function will report at most 4 atoms showing different background noise type.
 */
void SysfsCollector::logVendorAudioPdmStatsReported(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<int> pdm_states;

    if (kPDMStatePath == nullptr) {
        ALOGD("Audio PDM State path not specified");
    } else {
        if (!ReadFileToString(kPDMStatePath, &file_contents)) {
            ALOGD("Unable to read PDM State path %s", kPDMStatePath);
        } else {
            std::stringstream file_content_stream(file_contents);
            while (file_content_stream.good()) {
                std::string substr;
                int state;
                getline(file_content_stream, substr, ',');
                if (sscanf(substr.c_str(), "%d", &state) != 1) {
                    ALOGD("Unable to parse PDM State %s", file_contents.c_str());
                } else {
                    pdm_states.push_back(state);
                    ALOGD("Parsed PDM State: %d", state);
                }
            }
        }
    }
    if (pdm_states.empty()) {
        ALOGD("Empty PDM State parsed.");
        return;
    }

    if (pdm_states.size() > 4) {
        ALOGD("Too many values parsed.");
        return;
    }

    for (int index = 0; index < pdm_states.size(); index++) {
        std::vector<VendorAtomValue> values(2);
        VendorAtomValue tmp;

        if (pdm_states[index] == 0) {
            continue;
        }

        tmp.set<VendorAtomValue::intValue>(index);
        values[VendorAudioPdmStatsReported::kPdmIndexFieldNumber - kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(pdm_states[index]);
        values[VendorAudioPdmStatsReported::kStateFieldNumber - kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioPdmStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioPdmStatsReported at index %d", index);
    }
}

/**
 * Report Third party audio effects stats.
 * This function will report at most 5 atoms showing different instance stats.
 */
void SysfsCollector::logWavesStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<std::vector<int>> volume_duration_per_instance;

    constexpr int num_instances = 5;
    constexpr int num_volume = 10;

    if (kWavesPath == nullptr) {
        ALOGD("Audio Waves stats path not specified");
        return;
    }

    if (!ReadFileToString(kWavesPath, &file_contents)) {
        ALOGD("Unable to read Wave stats path %s", kWavesPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int duration;
        std::vector<int> volume_duration;
        while (file_content_stream.good() && file_content_stream >> duration) {
            volume_duration.push_back(duration);
            if (volume_duration.size() >= num_volume) {
                volume_duration_per_instance.push_back(volume_duration);
                volume_duration.clear();
            }
        }
    }

    if (volume_duration_per_instance.size() != num_instances) {
        ALOGE("Number of instances %zu doesn't match the correct number %d",
              volume_duration_per_instance.size(), num_instances);
        return;
    }
    for (int i = 0; i < volume_duration_per_instance.size(); i++) {
        if (volume_duration_per_instance[i].size() != num_volume) {
            ALOGE("Number of volume %zu doesn't match the correct number %d",
                  volume_duration_per_instance[i].size(), num_volume);
            return;
        }
    }

    std::vector<int> volume_range_field_numbers = {
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange0ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange1ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange2ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange3ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange4ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange5ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange6ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange7ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange8ActiveMsPerDayFieldNumber,
            VendorAudioThirdPartyEffectStatsReported::kVolumeRange9ActiveMsPerDayFieldNumber};

    for (int index = 0; index < volume_duration_per_instance.size(); index++) {
        std::vector<VendorAtomValue> values(11);
        VendorAtomValue tmp;

        bool has_value = false;
        for (int volume_index = 0; volume_index < num_volume; volume_index++) {
            if (volume_duration_per_instance[index][volume_index] > 0) {
                has_value = true;
            }
        }
        if (!has_value) {
            continue;
        }

        tmp.set<VendorAtomValue::intValue>(index);
        values[VendorAudioThirdPartyEffectStatsReported::kInstanceFieldNumber - kVendorAtomOffset] =
                tmp;

        for (int volume_index = 0; volume_index < num_volume; volume_index++) {
            tmp.set<VendorAtomValue::intValue>(volume_duration_per_instance[index][volume_index]);
            values[volume_range_field_numbers[volume_index] - kVendorAtomOffset] = tmp;
        }
        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioThirdPartyEffectStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioThirdPartyEffectStatsReported at index %d", index);
    }
}

/**
 * Report Audio Adapted Information stats such as thermal throttling.
 * This function will report at most 6 atoms showing different instance stats.
 */
void SysfsCollector::logAdaptedInfoStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<int> count_per_feature;
    std::vector<int> duration_per_feature;

    constexpr int num_features = 6;

    if (kAdaptedInfoCountPath == nullptr) {
        ALOGD("Audio Adapted Info Count stats path not specified");
        return;
    }

    if (kAdaptedInfoDurationPath == nullptr) {
        ALOGD("Audio Adapted Info Duration stats path not specified");
        return;
    }

    if (!ReadFileToString(kAdaptedInfoCountPath, &file_contents)) {
        ALOGD("Unable to read Adapted Info Count stats path %s", kAdaptedInfoCountPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int count;
        while (file_content_stream.good() && file_content_stream >> count) {
            count_per_feature.push_back(count);
        }
    }

    if (count_per_feature.size() != num_features) {
        ALOGD("Audio Adapted Info Count doesn't match the number of features. %zu / %d",
              count_per_feature.size(), num_features);
        return;
    }

    if (!ReadFileToString(kAdaptedInfoDurationPath, &file_contents)) {
        ALOGD("Unable to read Adapted Info Duration stats path %s", kAdaptedInfoDurationPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int duration;
        while (file_content_stream.good() && file_content_stream >> duration) {
            duration_per_feature.push_back(duration);
        }
    }

    if (duration_per_feature.size() != num_features) {
        ALOGD("Audio Adapted Info Duration doesn't match the number of features. %zu / %d",
              duration_per_feature.size(), num_features);
        return;
    }

    for (int index = 0; index < num_features; index++) {
        std::vector<VendorAtomValue> values(3);
        VendorAtomValue tmp;

        if (count_per_feature[index] == 0 && duration_per_feature[index] == 0) {
            continue;
        }

        tmp.set<VendorAtomValue::intValue>(index);
        values[VendorAudioAdaptedInfoStatsReported::kFeatureIdFieldNumber - kVendorAtomOffset] =
                tmp;

        tmp.set<VendorAtomValue::intValue>(count_per_feature[index]);
        values[VendorAudioAdaptedInfoStatsReported::kActiveCountsPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(duration_per_feature[index]);
        values[VendorAudioAdaptedInfoStatsReported::kActiveDurationMsPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioAdaptedInfoStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioAdaptedInfoStatsReported at index %d", index);
    }
}

/**
 * Report audio PCM usage stats such as latency and active count.
 * This function will report at most 19 atoms showing different PCM type.
 */
void SysfsCollector::logPcmUsageStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<int> count_per_type;
    std::vector<int> latency_per_type;

    constexpr int num_type = 19;

    if (kPcmLatencyPath == nullptr) {
        ALOGD("PCM Latency path not specified");
        return;
    }

    if (kPcmCountPath == nullptr) {
        ALOGD("PCM Count path not specified");
        return;
    }

    if (!ReadFileToString(kPcmCountPath, &file_contents)) {
        ALOGD("Unable to read PCM Count path %s", kPcmCountPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int count;
        while (file_content_stream.good() && file_content_stream >> count) {
            count_per_type.push_back(count);
        }
    }

    if (count_per_type.size() != num_type) {
        ALOGD("Audio PCM Count path doesn't match the number of features. %zu / %d",
              count_per_type.size(), num_type);
        return;
    }

    if (!ReadFileToString(kPcmLatencyPath, &file_contents)) {
        ALOGD("Unable to read PCM Latency path %s", kPcmLatencyPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int duration;
        while (file_content_stream.good() && file_content_stream >> duration) {
            latency_per_type.push_back(duration);
        }
    }

    if (latency_per_type.size() != num_type) {
        ALOGD("Audio PCM Latency path doesn't match the number of features. %zu / %d",
              latency_per_type.size(), num_type);
        return;
    }

    for (int index = 0; index < num_type; index++) {
        std::vector<VendorAtomValue> values(3);
        VendorAtomValue tmp;

        if (latency_per_type[index] == 0 && count_per_type[index] == 0) {
            continue;
        }

        tmp.set<VendorAtomValue::intValue>(index);
        values[VendorAudioPcmStatsReported::kTypeFieldNumber - kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(latency_per_type[index]);
        values[VendorAudioPcmStatsReported::kPcmOpenLatencyAvgMsPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(count_per_type[index]);
        values[VendorAudioPcmStatsReported::kPcmActiveCountsPerDayFieldNumber - kVendorAtomOffset] =
                tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioPcmStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioPcmStatsReported at index %d", index);
    }
}

/**
 * Report audio Offload Effects usage stats duration per day.
 */
void SysfsCollector::logOffloadEffectsStats(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<int> uuids;
    std::vector<int> durations;

    if (kOffloadEffectsIdPath == nullptr) {
        ALOGD("Offload Effects ID Path is not specified");
        return;
    }

    if (kOffloadEffectsDurationPath == nullptr) {
        ALOGD("Offload Effects Duration Path is not specified");
        return;
    }

    if (!ReadFileToString(kOffloadEffectsIdPath, &file_contents)) {
        ALOGD("Unable to read Offload Effect ID path %s", kOffloadEffectsIdPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int uuid;
        while (file_content_stream.good() && file_content_stream >> uuid) {
            uuids.push_back(uuid);
        }
    }

    if (!ReadFileToString(kOffloadEffectsDurationPath, &file_contents)) {
        ALOGD("Unable to read Offload Effect duration path %s", kOffloadEffectsDurationPath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int duration;
        while (file_content_stream.good() && file_content_stream >> duration) {
            durations.push_back(duration);
        }
    }

    if (durations.size() * 4 != uuids.size()) {
        ALOGD("ID and duration data does not match: %zu and %zu", durations.size(), uuids.size());
        return;
    }

    for (int index = 0; index < durations.size(); index++) {
        std::vector<VendorAtomValue> values(3);
        VendorAtomValue tmp;
        int64_t uuid_msb = ((int64_t)uuids[index * 4] << 32 | uuids[index * 4 + 1]);
        int64_t uuid_lsb = ((int64_t)uuids[index * 4 + 2] << 32 | uuids[index * 4 + 3]);

        if (uuid_msb == 0 && uuid_lsb == 0) {
            continue;
        }

        tmp.set<VendorAtomValue::VendorAtomValue::longValue>(uuid_msb);
        values[VendorAudioOffloadedEffectStatsReported::kEffectUuidMsbFieldNumber -
               kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::VendorAtomValue::longValue>(uuid_lsb);
        values[VendorAudioOffloadedEffectStatsReported::kEffectUuidLsbFieldNumber -
               kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(durations[index]);
        values[VendorAudioOffloadedEffectStatsReported::kEffectActiveSecondsPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioOffloadedEffectStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk()) {
            ALOGE("Unable to report VendorAudioOffloadedEffectStatsReported at index %d", index);
        } else {
            ALOGD("Reported VendorAudioOffloadedEffectStatsReported successfully at index %d",
                  index);
        }
    }
}

/**
 * Report bluetooth audio usage stats.
 * This function will report at most 5 atoms showing different instance stats.
 */
void SysfsCollector::logBluetoothAudioUsage(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents;
    std::vector<int> duration_per_codec;

    constexpr int num_codec = 5;

    if (kBluetoothAudioUsagePath == nullptr) {
        ALOGD("Bluetooth Audio stats path not specified");
        return;
    }

    if (!ReadFileToString(kBluetoothAudioUsagePath, &file_contents)) {
        ALOGD("Unable to read Bluetooth Audio stats path %s", kBluetoothAudioUsagePath);
    } else {
        std::stringstream file_content_stream(file_contents);
        int duration;
        while (file_content_stream.good() && file_content_stream >> duration) {
            duration_per_codec.push_back(duration);
        }
    }

    if (duration_per_codec.size() != num_codec) {
        ALOGD("Bluetooth Audio num codec != number of codec. %zu / %d", duration_per_codec.size(),
              num_codec);
        return;
    }

    for (int index = 0; index < num_codec; index++) {
        std::vector<VendorAtomValue> values(2);
        VendorAtomValue tmp;

        if (duration_per_codec[index] == 0) {
            ALOGD("Skipped VendorAudioBtMediaStatsReported at codec:%d", index);
            continue;
        }

        tmp.set<VendorAtomValue::intValue>(index);
        values[VendorAudioBtMediaStatsReported::kBtCodecTypeFieldNumber - kVendorAtomOffset] = tmp;

        tmp.set<VendorAtomValue::intValue>(duration_per_codec[index]);
        values[VendorAudioBtMediaStatsReported::kActiveSecondsPerDayFieldNumber -
               kVendorAtomOffset] = tmp;

        // Send vendor atom to IStats HAL
        VendorAtom event = {.reverseDomainName = "",
                            .atomId = PixelAtoms::Atom::kVendorAudioBtMediaStatsReported,
                            .values = std::move(values)};

        const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
        if (!ret.isOk())
            ALOGE("Unable to report VendorAudioBtMediaStatsReported at index %d", index);
        else
            ALOGD("Reporting VendorAudioBtMediaStatsReported: codec:%d, duration:%d", index,
                  duration_per_codec[index]);
    }
}

/**
 * Logs the Resume Latency stats.
 */
void SysfsCollector::logVendorResumeLatencyStats(const std::shared_ptr<IStats> &stats_client) {
    std::string uart_enabled = android::base::GetProperty("init.svc.console", "");
    if (uart_enabled == "running") {
        return;
    }
    std::string file_contents;
    if (!kResumeLatencyMetricsPath) {
        ALOGE("ResumeLatencyMetrics path not specified");
        return;
    }
    if (!ReadFileToString(kResumeLatencyMetricsPath, &file_contents)) {
        ALOGE("Unable to ResumeLatencyMetric %s - %s", kResumeLatencyMetricsPath, strerror(errno));
        return;
    }

    int offset = 0;
    int bytes_read;
    const char *data = file_contents.c_str();
    int data_len = file_contents.length();

    int curr_bucket_cnt;
    if (!sscanf(data + offset, "Resume Latency Bucket Count: %d\n%n", &curr_bucket_cnt,
                &bytes_read))
        return;
    offset += bytes_read;
    if (offset >= data_len)
        return;

    int64_t max_latency;
    if (!sscanf(data + offset, "Max Resume Latency: %" PRId64 "\n%n", &max_latency, &bytes_read))
        return;
    offset += bytes_read;
    if (offset >= data_len)
        return;

    uint64_t sum_latency;
    if (!sscanf(data + offset, "Sum Resume Latency: %" PRIu64 "\n%n", &sum_latency, &bytes_read))
        return;
    offset += bytes_read;
    if (offset >= data_len)
        return;

    if (curr_bucket_cnt > kMaxResumeLatencyBuckets)
        return;
    if (curr_bucket_cnt != prev_data.bucket_cnt) {
        prev_data.resume_latency_buckets.clear();
    }

    int64_t total_latency_cnt = 0;
    int64_t count;
    int index = 2;
    std::vector<VendorAtomValue> values(curr_bucket_cnt + 2);
    VendorAtomValue tmp;
    // Iterate over resume latency buckets to get latency count within some latency thresholds
    while (sscanf(data + offset, "%*ld - %*ldms ====> %" PRId64 "\n%n", &count, &bytes_read) == 1 ||
           sscanf(data + offset, "%*ld - infms ====> %" PRId64 "\n%n", &count, &bytes_read) == 1) {
        offset += bytes_read;
        if (offset >= data_len && (index + 1 < curr_bucket_cnt + 2))
            return;
        if (curr_bucket_cnt == prev_data.bucket_cnt) {
            tmp.set<VendorAtomValue::longValue>(count -
                                                prev_data.resume_latency_buckets[index - 2]);
            prev_data.resume_latency_buckets[index - 2] = count;
        } else {
            tmp.set<VendorAtomValue::longValue>(count);
            prev_data.resume_latency_buckets.push_back(count);
        }
        if (index >= curr_bucket_cnt + 2)
            return;
        values[index] = tmp;
        index += 1;
        total_latency_cnt += count;
    }
    tmp.set<VendorAtomValue::longValue>(max_latency);
    values[0] = tmp;
    if ((sum_latency - prev_data.resume_latency_sum_ms < 0) ||
        (total_latency_cnt - prev_data.resume_count <= 0)) {
        tmp.set<VendorAtomValue::longValue>(-1);
        ALOGI("average resume latency get overflow");
    } else {
        tmp.set<VendorAtomValue::longValue>(
                (int64_t)(sum_latency - prev_data.resume_latency_sum_ms) /
                (total_latency_cnt - prev_data.resume_count));
    }
    values[1] = tmp;

    prev_data.resume_latency_sum_ms = sum_latency;
    prev_data.resume_count = total_latency_cnt;
    prev_data.bucket_cnt = curr_bucket_cnt;
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorResumeLatencyStats,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report VendorResumeLatencyStats to Stats service");
}

/**
 * Read and store top 5 irq stats.
 */
void process_irqatom_values(std::string file_contents, int *offset,
                            std::vector<VendorAtomValue> *values) {
    const char *data = file_contents.c_str();
    int bytes_read;
    int64_t irq_data;
    int irq_num;

    std::vector<std::pair<int, int64_t>> irq_pair;

    while (sscanf(data + *offset, "%d %" PRId64 "\n%n", &irq_num, &irq_data, &bytes_read) == 2) {
        irq_pair.push_back(std::make_pair(irq_num, irq_data));
        *offset += bytes_read;
    }
    VendorAtomValue tmp;
    int irq_stats_size = irq_pair.size();
    for (int i = 0; i < 5; i++) {
        if (irq_stats_size < 5 && i >= irq_stats_size) {
            tmp.set<VendorAtomValue::longValue>(-1);
            values->push_back(tmp);
            tmp.set<VendorAtomValue::longValue>(0);
            values->push_back(tmp);
        } else {
            tmp.set<VendorAtomValue::longValue>(irq_pair[i].first);
            values->push_back(tmp);
            tmp.set<VendorAtomValue::longValue>(irq_pair[i].second);
            values->push_back(tmp);
        }
    }
}

/**
 * Logs the Long irq stats.
 */
void SysfsCollector::logVendorLongIRQStatsReported(const std::shared_ptr<IStats> &stats_client) {
    std::string uart_enabled = android::base::GetProperty("init.svc.console", "");
    if (uart_enabled == "running") {
        return;
    }
    std::string irq_file_contents, storm_file_contents;
    if (kLongIRQMetricsPath == nullptr || strlen(kLongIRQMetricsPath) == 0) {
        ALOGV("LongIRQ path not specified");
        return;
    }
    if (!ReadFileToString(kLongIRQMetricsPath, &irq_file_contents)) {
        ALOGE("Unable to read LongIRQ %s - %s", kLongIRQMetricsPath, strerror(errno));
        return;
    }
    if (kStormIRQMetricsPath == nullptr || strlen(kStormIRQMetricsPath) == 0) {
        ALOGV("StormIRQ path not specified");
        return;
    }
    if (!ReadFileToString(kStormIRQMetricsPath, &storm_file_contents)) {
        ALOGE("Unable to read StormIRQ %s - %s", kStormIRQMetricsPath, strerror(errno));
        return;
    }
    if (kIRQStatsResetPath == nullptr || strlen(kIRQStatsResetPath) == 0) {
        ALOGV("IRQStatsReset path not specified");
        return;
    }
    int offset = 0;
    int bytes_read;
    const char *data = irq_file_contents.c_str();

    // Get, process softirq stats
    int64_t irq_count;
    if (sscanf(data + offset, "long SOFTIRQ count: %" PRId64 "\n%n", &irq_count, &bytes_read) != 1)
        return;
    offset += bytes_read;
    std::vector<VendorAtomValue> values;
    VendorAtomValue tmp;
    tmp.set<VendorAtomValue::longValue>(irq_count);
    values.push_back(tmp);

    if (sscanf(data + offset, "long SOFTIRQ detail (num, latency):\n%n", &bytes_read) != 0)
        return;
    offset += bytes_read;
    process_irqatom_values(data, &offset, &values);

    // Get, process irq stats
    if (sscanf(data + offset, "long IRQ count: %" PRId64 "\n%n", &irq_count, &bytes_read) != 1)
        return;
    offset += bytes_read;
    tmp.set<VendorAtomValue::longValue>(irq_count);
    values.push_back(tmp);

    if (sscanf(data + offset, "long IRQ detail (num, latency):\n%n", &bytes_read) != 0)
        return;
    offset += bytes_read;
    process_irqatom_values(data, &offset, &values);

    // Get, process storm irq stats
    offset = 0;
    data = storm_file_contents.c_str();
    if (sscanf(data + offset, "storm IRQ detail (num, storm_count):\n%n", &bytes_read) != 0)
        return;
    offset += bytes_read;
    process_irqatom_values(data, &offset, &values);

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kVendorLongIrqStatsReported,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report kVendorLongIRQStatsReported to Stats service");

    // Reset irq stats
    if (!WriteStringToFile(std::to_string(1), kIRQStatsResetPath)) {
        ALOGE("Failed to write to stats_reset");
        return;
    }
}

void SysfsCollector::logPartitionUsedSpace(const std::shared_ptr<IStats> &stats_client) {
    struct statfs fs_info;
    char path[] = "/mnt/vendor/persist";

    if (statfs(path, &fs_info) == -1) {
        ALOGE("statfs: %s", strerror(errno));
        return;
    }

    // Load values array
    std::vector<VendorAtomValue> values(3);
    values[PartitionsUsedSpaceReported::kDirectoryFieldNumber - kVendorAtomOffset] =
                    VendorAtomValue::make<VendorAtomValue::intValue>
                        (PixelAtoms::PartitionsUsedSpaceReported::PERSIST);
    values[PartitionsUsedSpaceReported::kFreeBytesFieldNumber - kVendorAtomOffset] =
            VendorAtomValue::make<VendorAtomValue::longValue>(fs_info.f_bsize * fs_info.f_bfree);
    values[PartitionsUsedSpaceReported::kTotalBytesFieldNumber - kVendorAtomOffset] =
            VendorAtomValue::make<VendorAtomValue::longValue>(fs_info.f_bsize * fs_info.f_blocks);
    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kPartitionUsedSpaceReported,
                        .values = std::move(values)};

    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report Partitions Used Space Reported to stats service");
    }
}

void SysfsCollector::logPcieLinkStats(const std::shared_ptr<IStats> &stats_client) {
    struct sysfs_map {
        const char *sysfs_path;
        bool is_counter;
        int modem_val;
        int wifi_val;
        int modem_msg_field_number;
        int wifi_msg_field_number;
    };

    int i;
    bool reportPcieLinkStats = false;

    /* Map sysfs data to PcieLinkStatsReported message elements */
    struct sysfs_map datamap[] = {
        {"link_down_irqs", true, 0, 0,
         PcieLinkStatsReported::kModemPcieLinkdownsFieldNumber,
         PcieLinkStatsReported::kWifiPcieLinkdownsFieldNumber},

        {"complete_timeout_irqs", true, 0, 0,
         PcieLinkStatsReported::kModemPcieCompletionTimeoutsFieldNumber,
         PcieLinkStatsReported::kWifiPcieCompletionTimeoutsFieldNumber},

        {"link_up_failures", true, 0, 0,
         PcieLinkStatsReported::kModemPcieLinkupFailuresFieldNumber,
         PcieLinkStatsReported::kWifiPcieLinkupFailuresFieldNumber},

        {"link_recovery_failures", true, 0, 0,
         PcieLinkStatsReported::kModemPcieLinkRecoveryFailuresFieldNumber,
         PcieLinkStatsReported::kWifiPcieLinkRecoveryFailuresFieldNumber},

        {"pll_lock_average", false, 0, 0,
         PcieLinkStatsReported::kModemPciePllLockAvgFieldNumber,
         PcieLinkStatsReported::kWifiPciePllLockAvgFieldNumber},

        {"link_up_average", false, 0, 0,
         PcieLinkStatsReported::kModemPcieLinkUpAvgFieldNumber,
         PcieLinkStatsReported::kWifiPcieLinkUpAvgFieldNumber },
    };


    if (kModemPcieLinkStatsPath == nullptr) {
        ALOGD("Modem PCIe stats path not specified");
    } else {
        for (i=0; i < ARRAY_SIZE(datamap); i++) {
            std::string modempath =
                    std::string(kModemPcieLinkStatsPath) + "/" + datamap[i].sysfs_path;

            if (ReadFileToInt(modempath, &(datamap[i].modem_val))) {
                reportPcieLinkStats = true;
                ALOGD("Modem %s = %d", datamap[i].sysfs_path,
                      datamap[i].modem_val);
                if (datamap[i].is_counter) {
                    std::string value = std::to_string(datamap[i].modem_val);
                    /* Writing the value back clears the counter */
                    if (!WriteStringToFile(value, modempath)) {
                        ALOGE("Unable to clear modem PCIe statistics file: %s - %s",
                              modempath.c_str(), strerror(errno));
                    }
                }
            }
        }
    }

    if (kWifiPcieLinkStatsPath == nullptr) {
        ALOGD("Wifi PCIe stats path not specified");
    } else {
        for (i=0; i < ARRAY_SIZE(datamap); i++) {
            std::string wifipath =
                    std::string(kWifiPcieLinkStatsPath) + "/" + datamap[i].sysfs_path;

            if (ReadFileToInt(wifipath, &(datamap[i].wifi_val))) {
                reportPcieLinkStats = true;
                ALOGD("Wifi %s = %d", datamap[i].sysfs_path,
                      datamap[i].wifi_val);
                if (datamap[i].is_counter) {
                    std::string value = std::to_string(datamap[i].wifi_val);
                    /* Writing the value back clears the counter */
                    if (!WriteStringToFile(value, wifipath)) {
                        ALOGE("Unable to clear wifi PCIe statistics file: %s - %s",
                              wifipath.c_str(), strerror(errno));
                    }
                }
            }
        }
    }

    if (!reportPcieLinkStats) {
        ALOGD("No PCIe link stats to report");
        return;
    }

    // Load values array
    std::vector<VendorAtomValue> values(2 * ARRAY_SIZE(datamap));
    VendorAtomValue tmp;
    for (i=0; i < ARRAY_SIZE(datamap); i++) {
        if (datamap[i].modem_val > 0) {
            tmp.set<VendorAtomValue::intValue>(datamap[i].modem_val);
            values[datamap[i].modem_msg_field_number - kVendorAtomOffset] = tmp;
        }
        if (datamap[i].wifi_val > 0) {
            tmp.set<VendorAtomValue::intValue>(datamap[i].wifi_val);
            values[datamap[i].wifi_msg_field_number - kVendorAtomOffset] = tmp;
        }
    }

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kPcieLinkStats,
                        .values = std::move(values)};

    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report PCIe link statistics to stats service");
    }
}

/**
 * Read the contents of kPowerMitigationDurationPath and report them.
 */
void SysfsCollector::logMitigationDurationCounts(const std::shared_ptr<IStats> &stats_client) {
    if (kPowerMitigationDurationPath == nullptr || strlen(kPowerMitigationDurationPath) == 0) {
        ALOGE("Mitigation Duration path is invalid!");
        return;
    }
    mitigation_duration_reporter_.logMitigationDuration(stats_client, kPowerMitigationDurationPath);
}

void SysfsCollector::logPerDay() {
    const std::shared_ptr<IStats> stats_client = getStatsService();
    if (!stats_client) {
        ALOGE("Unable to get AIDL Stats service");
        return;
    }
    // Collect once per service init; can be multiple due to service reinit
    if (!log_once_reported) {
        logBootStats(stats_client);
    }
    logBatteryCapacity(stats_client);
    logBatteryChargeCycles(stats_client);
    logBatteryEEPROM(stats_client);
    logBatteryHealth(stats_client);
    logBlockStatsReported(stats_client);
    logCodec1Failed(stats_client);
    logCodecFailed(stats_client);
    logDisplayStats(stats_client);
    logDisplayPortStats(stats_client);
    logHDCPStats(stats_client);
    logF2fsStats(stats_client);
    logF2fsAtomicWriteInfo(stats_client);
    logF2fsCompressionInfo(stats_client);
    logF2fsGcSegmentInfo(stats_client);
    logF2fsSmartIdleMaintEnabled(stats_client);
    logSlowIO(stats_client);
    logSpeakerImpedance(stats_client);
    logSpeechDspStat(stats_client);
    logUFSLifetime(stats_client);
    logUFSErrorStats(stats_client);
    logSpeakerHealthStats(stats_client);
    mm_metrics_reporter_.logCmaStatus(stats_client);
    mm_metrics_reporter_.logPixelMmMetricsPerDay(stats_client);
    logVendorAudioHardwareStats(stats_client);
    logThermalStats(stats_client);
    logTempResidencyStats(stats_client);
    logVendorLongIRQStatsReported(stats_client);
    logVendorResumeLatencyStats(stats_client);
    logPartitionUsedSpace(stats_client);
    logPcieLinkStats(stats_client);
    logMitigationDurationCounts(stats_client);
    logVendorAudioPdmStatsReported(stats_client);
    logWavesStats(stats_client);
    logAdaptedInfoStats(stats_client);
    logPcmUsageStats(stats_client);
    logOffloadEffectsStats(stats_client);
    logBluetoothAudioUsage(stats_client);
}

void SysfsCollector::aggregatePer5Min() {
    mm_metrics_reporter_.aggregatePixelMmMetricsPer5Min();
}

void SysfsCollector::logBrownout() {
    const std::shared_ptr<IStats> stats_client = getStatsService();
    if (!stats_client) {
        ALOGE("Unable to get AIDL Stats service");
        return;
    }
    if (kBrownoutCsvPath != nullptr && strlen(kBrownoutCsvPath) > 0)
        brownout_detected_reporter_.logBrownoutCsv(stats_client, kBrownoutCsvPath,
                                                   kBrownoutReasonProp);
    else if (kBrownoutLogPath != nullptr && strlen(kBrownoutLogPath) > 0)
        brownout_detected_reporter_.logBrownout(stats_client, kBrownoutLogPath,
                                                kBrownoutReasonProp);
}

void SysfsCollector::logOnce() {
    logBrownout();
}

void SysfsCollector::logPerHour() {
    const std::shared_ptr<IStats> stats_client = getStatsService();
    if (!stats_client) {
        ALOGE("Unable to get AIDL Stats service");
        return;
    }
    mm_metrics_reporter_.logPixelMmMetricsPerHour(stats_client);
    logZramStats(stats_client);
    if (kPowerMitigationStatsPath != nullptr && strlen(kPowerMitigationStatsPath) > 0)
        mitigation_stats_reporter_.logMitigationStatsPerHour(stats_client,
                                                             kPowerMitigationStatsPath);
}

/**
 * Loop forever collecting stats from sysfs nodes and reporting them via
 * IStats.
 */
void SysfsCollector::collect(void) {
    int timerfd = timerfd_create(CLOCK_BOOTTIME, 0);
    if (timerfd < 0) {
        ALOGE("Unable to create timerfd - %s", strerror(errno));
        return;
    }

    // Sleep for 30 seconds on launch to allow codec driver to load.
    sleep(30);

    // sample & aggregate for the first time.
    aggregatePer5Min();

    // Collect first set of stats on boot.
    logOnce();
    logPerHour();
    logPerDay();

    struct itimerspec period;

    // gcd (greatest common divisor) of all the following timings
    constexpr int kSecondsPerWake = 5 * 60;

    constexpr int kWakesPer5Min = 5 * 60 / kSecondsPerWake;
    constexpr int kWakesPerHour = 60 * 60 / kSecondsPerWake;
    constexpr int kWakesPerDay = 24 * 60 * 60 / kSecondsPerWake;

    int wake_5min = 0;
    int wake_hours = 0;
    int wake_days = 0;

    period.it_interval.tv_sec = kSecondsPerWake;
    period.it_interval.tv_nsec = 0;
    period.it_value.tv_sec = kSecondsPerWake;
    period.it_value.tv_nsec = 0;

    if (timerfd_settime(timerfd, 0, &period, NULL)) {
        ALOGE("Unable to set one hour timer - %s", strerror(errno));
        return;
    }

    while (1) {
        int readval;
        union {
            char buf[8];
            uint64_t count;
        } expire;

        do {
            errno = 0;
            readval = read(timerfd, expire.buf, sizeof(expire.buf));
        } while (readval < 0 && errno == EINTR);
        if (readval < 0) {
            ALOGE("Timerfd error - %s\n", strerror(errno));
            return;
        }

        wake_5min += expire.count;
        wake_hours += expire.count;
        wake_days += expire.count;

        if (wake_5min >= kWakesPer5Min) {
            wake_5min %= kWakesPer5Min;
            aggregatePer5Min();
        }

        if (wake_hours >= kWakesPerHour) {
            if (wake_hours >= 2 * kWakesPerHour)
                ALOGW("Hourly wake: sleep too much: expire.count=%" PRId64, expire.count);
            wake_hours %= kWakesPerHour;
            logPerHour();
        }

        if (wake_days >= kWakesPerDay) {
            if (wake_hours >= 2 * kWakesPerDay)
                ALOGW("Daily wake: sleep too much: expire.count=%" PRId64, expire.count);
            wake_days %= kWakesPerDay;
            logPerDay();
        }
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

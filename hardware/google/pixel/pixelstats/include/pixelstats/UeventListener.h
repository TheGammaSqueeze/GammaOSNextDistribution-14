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

#ifndef HARDWARE_GOOGLE_PIXEL_PIXELSTATS_UEVENTLISTENER_H
#define HARDWARE_GOOGLE_PIXEL_PIXELSTATS_UEVENTLISTENER_H

#include <aidl/android/frameworks/stats/IStats.h>
#include <android-base/chrono_utils.h>
#include <pixelstats/BatteryCapacityReporter.h>
#include <pixelstats/ChargeStatsReporter.h>
#include <pixelstats/BatteryFGReporter.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::IStats;
/**
 * A class to listen for uevents and report reliability events to
 * the PixelStats HAL.
 * Runs in a background thread if created with ListenForeverInNewThread().
 * Alternatively, process one message at a time with ProcessUevent().
 */
class UeventListener {
  public:
    struct UeventPaths {
        const char *const AudioUevent;
        const char *const SsocDetailsPath;
        const char *const OverheatPath;
        const char *const ChargeMetricsPath;
        const char *const TypeCPartnerUevent;
        const char *const TypeCPartnerVidPath;
        const char *const TypeCPartnerPidPath;
        const char *const WirelessChargerPtmcUevent;  // Deprecated.
        const char *const WirelessChargerPtmcPath;    // Deprecated.
        const std::vector<std::string> FGLearningPath;
        const char *const FwUpdatePath;
        const std::vector<std::string> FGModelLoadingPath;
    };
    constexpr static const char *const ssoc_details_path =
            "/sys/class/power_supply/battery/ssoc_details";
    constexpr static const char *const overheat_path_default =
            "/sys/devices/platform/soc/soc:google,overheat_mitigation";
    constexpr static const char *const charge_metrics_path_default =
            "/sys/class/power_supply/battery/charge_stats";
    constexpr static const char *const typec_partner_vid_path_default =
            "/sys/class/typec/port0-partner/identity/id_header";
    constexpr static const char *const typec_partner_pid_path_default =
            "/sys/class/typec/port0-partner/identity/product";
    constexpr static const char *const typec_partner_uevent_default = "DEVTYPE=typec_partner";

    UeventListener(const std::string audio_uevent, const std::string ssoc_details_path = "",
                   const std::string overheat_path = overheat_path_default,
                   const std::string charge_metrics_path = charge_metrics_path_default,
                   const std::string typec_partner_vid_path = typec_partner_vid_path_default,
                   const std::string typec_partner_pid_path = typec_partner_pid_path_default,
                   const std::vector<std::string> fg_learning_path = { "" },
                   const std::string fw_update_path = "",
                   const std::vector<std::string> fg_modelloading_path = { "" });
    UeventListener(const struct UeventPaths &paths);

    bool ProcessUevent();  // Process a single Uevent.
    void ListenForever();  // Process Uevents forever

  private:
    bool ReadFileToInt(const std::string &path, int *val);
    bool ReadFileToInt(const char *path, int *val);
    void ReportMicStatusUevents(const std::shared_ptr<IStats> &stats_client, const char *devpath,
                                const char *mic_status);
    void ReportMicBrokenOrDegraded(const std::shared_ptr<IStats> &stats_client, const int mic,
                                   const bool isBroken);
    void ReportUsbPortOverheatEvent(const std::shared_ptr<IStats> &stats_client,
                                    const char *driver);
    void ReportChargeStats(const std::shared_ptr<IStats> &stats_client, const std::string line,
                           const std::string wline_at, const std::string wline_ac,
                           const std::string pca_line);
    void ReportVoltageTierStats(const std::shared_ptr<IStats> &stats_client, const char *line,
                                const bool has_wireless, const std::string wfile_contents);
    void ReportChargeMetricsEvent(const std::shared_ptr<IStats> &stats_client, const char *driver);
    void ReportBatteryCapacityFGEvent(const std::shared_ptr<IStats> &stats_client,
                                      const char *subsystem);
    void ReportTypeCPartnerId(const std::shared_ptr<IStats> &stats_client);
    void ReportGpuEvent(const std::shared_ptr<IStats> &stats_client, const char *driver,
                        const char *gpu_event_type, const char *gpu_event_info);
    void ReportThermalAbnormalEvent(const std::shared_ptr<IStats> &stats_client,
                                    const char *devpath, const char *thermal_abnormal_event_type,
                                    const char *thermal_abnormal_event_info);
    void ReportFGMetricsEvent(const std::shared_ptr<IStats> &stats_client, const char *driver);

    const std::string kAudioUevent;
    const std::string kBatterySSOCPath;
    const std::string kUsbPortOverheatPath;
    const std::string kChargeMetricsPath;
    const std::string kTypeCPartnerUevent;
    const std::string kTypeCPartnerVidPath;
    const std::string kTypeCPartnerPidPath;
    const std::vector<std::string> kFGLearningPath;
    const std::string kFwUpdatePath;
    const std::vector<std::string> kFGModelLoadingPath;


    const std::unordered_map<std::string, PixelAtoms::GpuEvent::GpuEventType>
            kGpuEventTypeStrToEnum{
                    {"KMD_ERROR",
                     PixelAtoms::GpuEvent::GpuEventType::GpuEvent_GpuEventType_MALI_KMD_ERROR},
                    {"GPU_RESET",
                     PixelAtoms::GpuEvent::GpuEventType::GpuEvent_GpuEventType_MALI_GPU_RESET}};

    const std::unordered_map<std::string, PixelAtoms::GpuEvent::GpuEventInfo>
            kGpuEventInfoStrToEnum{
                    {"CSG_REQ_STATUS_UPDATE",
                     PixelAtoms::GpuEvent::GpuEventInfo::
                             GpuEvent_GpuEventInfo_MALI_CSG_REQ_STATUS_UPDATE},
                    {"CSG_SUSPEND",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_CSG_SUSPEND},
                    {"CSG_SLOTS_SUSPEND", PixelAtoms::GpuEvent::GpuEventInfo::
                                                  GpuEvent_GpuEventInfo_MALI_CSG_SLOTS_SUSPEND},
                    {"CSG_GROUP_SUSPEND", PixelAtoms::GpuEvent::GpuEventInfo::
                                                  GpuEvent_GpuEventInfo_MALI_CSG_GROUP_SUSPEND},
                    {"CSG_EP_CFG",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_CSG_EP_CFG},
                    {"CSG_SLOTS_START", PixelAtoms::GpuEvent::GpuEventInfo::
                                                GpuEvent_GpuEventInfo_MALI_CSG_SLOTS_START},
                    {"GROUP_TERM",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_GROUP_TERM},
                    {"QUEUE_START",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_QUEUE_START},
                    {"QUEUE_STOP",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_QUEUE_STOP},
                    {"QUEUE_STOP_ACK",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_QUEUE_STOP_ACK},
                    {"CSG_SLOT_READY",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_CSG_SLOT_READY},
                    {"L2_PM_TIMEOUT",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_L2_PM_TIMEOUT},
                    {"PM_TIMEOUT",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_PM_TIMEOUT},
                    {"CSF_RESET_OK",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_CSF_RESET_OK},
                    {"CSF_RESET_FAILED", PixelAtoms::GpuEvent::GpuEventInfo::
                                                 GpuEvent_GpuEventInfo_MALI_CSF_RESET_FAILED},
                    {"TILER_OOM",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_TILER_OOM},
                    {"PROGRESS_TIMER",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_PROGRESS_TIMER},
                    {"CS_ERROR",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_CS_ERROR},
                    {"FW_ERROR",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_FW_ERROR},
                    {"PMODE_EXIT_TIMEOUT", PixelAtoms::GpuEvent::GpuEventInfo::
                                                   GpuEvent_GpuEventInfo_MALI_PMODE_EXIT_TIMEOUT},
                    {"PMODE_ENTRY_FAILURE", PixelAtoms::GpuEvent::GpuEventInfo::
                                                    GpuEvent_GpuEventInfo_MALI_PMODE_ENTRY_FAILURE},
                    {"GPU_PAGE_FAULT",
                     PixelAtoms::GpuEvent::GpuEventInfo::GpuEvent_GpuEventInfo_MALI_GPU_PAGE_FAULT},
                    {"MMU_AS_ACTIVE_STUCK",
                     PixelAtoms::GpuEvent::GpuEventInfo::
                             GpuEvent_GpuEventInfo_MALI_MMU_AS_ACTIVE_STUCK}};

    const std::unordered_map<std::string,
                             PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType>
            kThermalAbnormalityTypeStrToEnum{
                    {"UNKNOWN", PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                                        ThermalSensorAbnormalityDetected_AbnormalityType_UNKNOWN},
                    {"SENSOR_STUCK",
                     PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                             ThermalSensorAbnormalityDetected_AbnormalityType_SENSOR_STUCK},
                    {"EXTREME_HIGH_TEMP",
                     PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                             ThermalSensorAbnormalityDetected_AbnormalityType_EXTREME_HIGH_TEMP},
                    {"EXTREME_LOW_TEMP",
                     PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                             ThermalSensorAbnormalityDetected_AbnormalityType_EXTREME_LOW_TEMP},
                    {"HIGH_RISING_SPEED",
                     PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                             ThermalSensorAbnormalityDetected_AbnormalityType_HIGH_RISING_SPEED},
                    {"TEMP_READ_FAIL",
                     PixelAtoms::ThermalSensorAbnormalityDetected::AbnormalityType::
                             ThermalSensorAbnormalityDetected_AbnormalityType_TEMP_READ_FAIL},
            };

    BatteryCapacityReporter battery_capacity_reporter_;
    ChargeStatsReporter charge_stats_reporter_;
    BatteryFGReporter battery_fg_reporter_;

    // Proto messages are 1-indexed and VendorAtom field numbers start at 2, so
    // store everything in the values array at the index of the field number
    // -2.
    const int kVendorAtomOffset = 2;

    int uevent_fd_;
    int log_fd_;
};

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_PIXELSTATS_UEVENTLISTENER_H

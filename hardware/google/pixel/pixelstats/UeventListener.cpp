/*
 * Copyright (C) 2017 The Android Open Source Project
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

/* If you are watching for a new uevent, uncomment the following define.
 * After flashing your test build, run:
 *    adb root && adb shell
 *    stop vendor.pixelstats_vendor
 *    touch /data/local/tmp/uevents
 *    /vendor/bin/pixelstats-vendor &
 *
 *    then trigger any events.
 *    If you leave adb connected, you can watch them with
 *    tail -f /data/local/tmp/uevents
 *
 *    Once you are done,
 *
 *    adb pull /data/local/tmp/uevents
 *    adb rm /data/local/tmp/uevents
 *    adb reboot
 *
 *    provide this log in the bug as support for your feature.
 */
// #define LOG_UEVENTS_TO_FILE_ONLY_FOR_DEVEL "/data/local/tmp/uevents"

#define LOG_TAG "pixelstats-uevent"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <cutils/uevent.h>
#include <fcntl.h>
#include <hardware/google/pixel/pixelstats/pixelatoms.pb.h>
#include <linux/thermal.h>
#include <log/log.h>
#include <pixelstats/StatsHelper.h>
#include <pixelstats/UeventListener.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <utils/StrongPointer.h>

#include <string>
#include <thread>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using aidl::android::frameworks::stats::VendorAtom;
using aidl::android::frameworks::stats::VendorAtomValue;
using android::sp;
using android::base::ReadFileToString;
using android::base::WriteStringToFile;
using android::hardware::google::pixel::PixelAtoms::GpuEvent;
using android::hardware::google::pixel::PixelAtoms::PdVidPid;
using android::hardware::google::pixel::PixelAtoms::ThermalSensorAbnormalityDetected;
using android::hardware::google::pixel::PixelAtoms::VendorHardwareFailed;
using android::hardware::google::pixel::PixelAtoms::VendorUsbPortOverheat;

constexpr int32_t UEVENT_MSG_LEN = 2048;  // it's 2048 in all other users.
constexpr int32_t PRODUCT_TYPE_OFFSET = 23;
constexpr int32_t PRODUCT_TYPE_MASK = 7;
constexpr int32_t PRODUCT_TYPE_CHARGER = 3;
constexpr int32_t VID_MASK = 0xffff;
constexpr int32_t VID_GOOGLE = 0x18d1;
constexpr int32_t PID_OFFSET = 2;
constexpr int32_t PID_LENGTH = 4;
constexpr uint32_t PID_P30 = 0x4f05;
constexpr const char *THERMAL_ABNORMAL_INFO_EQ = "THERMAL_ABNORMAL_INFO=";
constexpr const char *THERMAL_ABNORMAL_TYPE_EQ = "THERMAL_ABNORMAL_TYPE=";

bool UeventListener::ReadFileToInt(const std::string &path, int *val) {
    return ReadFileToInt(path.c_str(), val);
}

bool UeventListener::ReadFileToInt(const char *const path, int *val) {
    std::string file_contents;

    if (!ReadFileToString(path, &file_contents)) {
        ALOGE("Unable to read %s - %s", path, strerror(errno));
        return false;
    } else if (sscanf(file_contents.c_str(), "%d", val) != 1) {
        ALOGE("Unable to convert %s to int - %s", path, strerror(errno));
        return false;
    }
    return true;
}

void UeventListener::ReportMicBrokenOrDegraded(const std::shared_ptr<IStats> &stats_client,
                                               const int mic, const bool isbroken) {
    VendorHardwareFailed failure;
    failure.set_hardware_type(VendorHardwareFailed::HARDWARE_FAILED_MICROPHONE);
    failure.set_hardware_location(mic);
    failure.set_failure_code(isbroken ? VendorHardwareFailed::COMPLETE
                                      : VendorHardwareFailed::DEGRADE);
    reportHardwareFailed(stats_client, failure);
}

void UeventListener::ReportMicStatusUevents(const std::shared_ptr<IStats> &stats_client,
                                            const char *devpath, const char *mic_status) {
    if (!devpath || !mic_status)
        return;
    if (!strcmp(devpath, ("DEVPATH=" + kAudioUevent).c_str())) {
        std::vector<std::string> value = android::base::Split(mic_status, "=");
        bool isbroken;

        if (value.size() == 2) {
            if (!value[0].compare("MIC_BREAK_STATUS"))
                isbroken = true;
            else if (!value[0].compare("MIC_DEGRADE_STATUS"))
                isbroken = false;
            else
                return;

            if (!value[1].compare("true")) {
                ReportMicBrokenOrDegraded(stats_client, 0, isbroken);
            } else {
                int mic_status = atoi(value[1].c_str());

                if (mic_status > 0 && mic_status <= 7) {
                    for (int mic_bit = 0; mic_bit < 3; mic_bit++)
                        if (mic_status & (0x1 << mic_bit))
                            ReportMicBrokenOrDegraded(stats_client, mic_bit, isbroken);
                } else if (mic_status == 0) {
                    // mic is ok
                    return;
                } else {
                    // should not enter here
                    ALOGE("invalid mic status");
                    return;
                }
            }
        }
    }
}

void UeventListener::ReportUsbPortOverheatEvent(const std::shared_ptr<IStats> &stats_client,
                                                const char *driver) {
    if (!driver || strcmp(driver, "DRIVER=google,overheat_mitigation")) {
        return;
    }

    int32_t plug_temperature_deci_c = 0;
    int32_t max_temperature_deci_c = 0;
    int32_t time_to_overheat_secs = 0;
    int32_t time_to_hysteresis_secs = 0;
    int32_t time_to_inactive_secs = 0;

    // TODO(achant b/182941868): test return value and skip reporting in case of an error
    ReadFileToInt((kUsbPortOverheatPath + "/plug_temp"), &plug_temperature_deci_c);
    ReadFileToInt((kUsbPortOverheatPath + "/max_temp"), &max_temperature_deci_c);
    ReadFileToInt((kUsbPortOverheatPath + "/trip_time"), &time_to_overheat_secs);
    ReadFileToInt((kUsbPortOverheatPath + "/hysteresis_time"), &time_to_hysteresis_secs);
    ReadFileToInt((kUsbPortOverheatPath + "/cleared_time"), &time_to_inactive_secs);

    VendorUsbPortOverheat overheat_info;
    overheat_info.set_plug_temperature_deci_c(plug_temperature_deci_c);
    overheat_info.set_max_temperature_deci_c(max_temperature_deci_c);
    overheat_info.set_time_to_overheat_secs(time_to_overheat_secs);
    overheat_info.set_time_to_hysteresis_secs(time_to_hysteresis_secs);
    overheat_info.set_time_to_inactive_secs(time_to_inactive_secs);

    reportUsbPortOverheat(stats_client, overheat_info);
}

void UeventListener::ReportChargeMetricsEvent(const std::shared_ptr<IStats> &stats_client,
                                              const char *driver) {
    if (!driver || strcmp(driver, "DRIVER=google,battery")) {
        return;
    }

    charge_stats_reporter_.checkAndReport(stats_client, kChargeMetricsPath);
}

void UeventListener::ReportFGMetricsEvent(const std::shared_ptr<IStats> &stats_client,
                                              const char *driver) {
    if (!driver || (strcmp(driver, "DRIVER=max77779-fg") && strcmp(driver, "DRIVER=maxfg") &&
        strcmp(driver, "DRIVER=max1720x")))
        return;

    battery_fg_reporter_.checkAndReportFGLearning(stats_client, kFGLearningPath);
    battery_fg_reporter_.checkAndReportFwUpdate(stats_client, kFwUpdatePath);
    battery_fg_reporter_.checkAndReportFGModelLoading(stats_client, kFGModelLoadingPath);
}

/**
 * Report raw battery capacity, system battery capacity and associated
 * battery capacity curves. This data is collected to verify the filter
 * applied on the battery capacity. This will allow debugging of issues
 * ranging from incorrect fuel gauge hardware calculations to issues
 * with the software reported battery capacity.
 *
 * The data is retrieved by parsing the battery power supply's ssoc_details.
 *
 * This atom logs data in 5 potential events:
 *      1. When a device is connected
 *      2. When a device is disconnected
 *      3. When a device has reached a full charge (from the UI's perspective)
 *      4. When there is a >= 2 percent skip in the UI reported SOC
 *      5. When there is a difference of >= 4 percent between the raw hardware
 *          battery capacity and the system reported battery capacity.
 */
void UeventListener::ReportBatteryCapacityFGEvent(const std::shared_ptr<IStats> &stats_client,
                                                  const char *subsystem) {
    if (!subsystem || strcmp(subsystem, "SUBSYSTEM=power_supply")) {
        return;
    }

    // Indicates an implicit disable of the battery capacity reporting
    if (kBatterySSOCPath.empty()) {
        return;
    }

    battery_capacity_reporter_.checkAndReport(stats_client, kBatterySSOCPath);
}

void UeventListener::ReportTypeCPartnerId(const std::shared_ptr<IStats> &stats_client) {
    std::string file_contents_vid, file_contents_pid;
    uint32_t pid, vid;

    if (!ReadFileToString(kTypeCPartnerVidPath.c_str(), &file_contents_vid)) {
        ALOGE("Unable to read %s - %s", kTypeCPartnerVidPath.c_str(), strerror(errno));
        return;
    }

    if (sscanf(file_contents_vid.c_str(), "%x", &vid) != 1) {
        ALOGE("Unable to parse vid %s from file %s to int.", file_contents_vid.c_str(),
              kTypeCPartnerVidPath.c_str());
        return;
    }

    if (!ReadFileToString(kTypeCPartnerPidPath.c_str(), &file_contents_pid)) {
        ALOGE("Unable to read %s - %s", kTypeCPartnerPidPath.c_str(), strerror(errno));
        return;
    }

    if (sscanf(file_contents_pid.substr(PID_OFFSET, PID_LENGTH).c_str(), "%x", &pid) != 1) {
        ALOGE("Unable to parse pid %s from file %s to int.",
              file_contents_pid.substr(PID_OFFSET, PID_LENGTH).c_str(),
              kTypeCPartnerPidPath.c_str());
        return;
    }

    // Upload data only for Google VID
    if ((VID_MASK & vid) != VID_GOOGLE) {
        return;
    }

    // Upload data only for chargers unless for P30 PID where the product type
    // isn't set to charger.
    if ((((vid >> PRODUCT_TYPE_OFFSET) & PRODUCT_TYPE_MASK) != PRODUCT_TYPE_CHARGER) &&
        (pid != PID_P30)) {
        return;
    }

    std::vector<VendorAtomValue> values(2);
    VendorAtomValue tmp;

    tmp.set<VendorAtomValue::intValue>(vid & VID_MASK);
    values[PdVidPid::kVidFieldNumber - kVendorAtomOffset] = tmp;
    tmp.set<VendorAtomValue::intValue>(pid);
    values[PdVidPid::kPidFieldNumber - kVendorAtomOffset] = tmp;

    // Send vendor atom to IStats HAL
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kPdVidPid,
                        .values = std::move(values)};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk()) {
        ALOGE("Unable to report PD VID/PID to Stats service");
    }
}

void UeventListener::ReportGpuEvent(const std::shared_ptr<IStats> &stats_client, const char *driver,
                                    const char *gpu_event_type, const char *gpu_event_info) {
    if (!stats_client || !driver || strncmp(driver, "DRIVER=mali", strlen("DRIVER=mali")) ||
        !gpu_event_type || !gpu_event_info)
        return;

    std::vector<std::string> type = android::base::Split(gpu_event_type, "=");
    std::vector<std::string> info = android::base::Split(gpu_event_info, "=");

    if (type.size() != 2 || info.size() != 2)
        return;

    if (type[0] != "GPU_UEVENT_TYPE" || info[0] != "GPU_UEVENT_INFO")
        return;

    auto event_type = kGpuEventTypeStrToEnum.find(type[1]);
    auto event_info = kGpuEventInfoStrToEnum.find(info[1]);
    if (event_type == kGpuEventTypeStrToEnum.end() || event_info == kGpuEventInfoStrToEnum.end())
        return;

    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kGpuEvent,
                        .values = {event_type->second, event_info->second}};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report GPU event.");
}

/**
 * Report thermal abnormal event.
 * The data is sent as uevent environment parameters:
 *      1. THERMAL_ABNORMAL_TYPE={type}
 *      2. THERMAL_ABNORMAL_INFO=Name:{name},Val:{val}
 * This atom logs data in 3 potential events:
 *      1. thermistor or tj temperature reading stuck
 *      2. thermistor or tj showing very high temperature reading
 *      3. thermistor or tj showing very low temperature reading
 */
void UeventListener::ReportThermalAbnormalEvent(const std::shared_ptr<IStats> &stats_client,
                                                const char *devpath,
                                                const char *thermal_abnormal_event_type,
                                                const char *thermal_abnormal_event_info) {
    if (!stats_client || !devpath ||
        strncmp(devpath, "DEVPATH=/module/pixel_metrics",
                strlen("DEVPATH=/module/pixel_metrics")) ||
        !thermal_abnormal_event_type || !thermal_abnormal_event_info)
        return;
    ALOGD("Thermal Abnormal Type: %s, Thermal Abnormal Info: %s", thermal_abnormal_event_type,
          thermal_abnormal_event_info);
    std::vector<std::string> type_msg = android::base::Split(thermal_abnormal_event_type, "=");
    std::vector<std::string> info_msg = android::base::Split(thermal_abnormal_event_info, "=");
    if (type_msg.size() != 2 || info_msg.size() != 2) {
        ALOGE("Invalid msg size for thermal abnormal with type(%zu) and info(%zu)", type_msg.size(),
              info_msg.size());
        return;
    }

    if (type_msg[0] != "THERMAL_ABNORMAL_TYPE" || info_msg[0] != "THERMAL_ABNORMAL_INFO") {
        ALOGE("Invalid msg prefix for thermal abnormal with type(%s) and info(%s)",
              type_msg[0].c_str(), info_msg[0].c_str());
        return;
    }

    auto abnormality_type = kThermalAbnormalityTypeStrToEnum.find(type_msg[1]);
    if (abnormality_type == kThermalAbnormalityTypeStrToEnum.end()) {
        ALOGE("Unknown thermal abnormal event type %s", type_msg[1].c_str());
        return;
    }

    std::vector<std::string> info_list = android::base::Split(info_msg[1], ",");
    if (info_list.size() != 2) {
        ALOGE("Thermal abnormal info(%s) split size %zu != 2", info_msg[1].c_str(),
              info_list.size());
        return;
    }

    const auto &name_msg = info_list[0], val_msg = info_list[1];
    if (!android::base::StartsWith(name_msg, "name:") ||
        !android::base::StartsWith(val_msg, "val:")) {
        ALOGE("Invalid prefix for thermal abnormal info name(%s), val(%s)", name_msg.c_str(),
              val_msg.c_str());
        return;
    }

    auto name_start_pos = std::strlen("name:");
    auto name = name_msg.substr(name_start_pos);
    if (name.length() > THERMAL_NAME_LENGTH) {
        ALOGE("Invalid sensor name %s with length %zu > %d", name.c_str(), name.length(),
              THERMAL_NAME_LENGTH);
        return;
    }

    auto val_start_pos = std::strlen("val:");
    auto val_str = val_msg.substr(val_start_pos);
    int val;
    if (sscanf(val_str.c_str(), "%d", &val) != 1) {
        ALOGE("Invalid value for thermal abnormal info: %s", val_str.c_str());
        return;
    }
    ALOGI("Reporting Thermal Abnormal event of type: %s(%d) for %s with val: %d",
          abnormality_type->first.c_str(), abnormality_type->second, name.c_str(), val);
    VendorAtom event = {.reverseDomainName = "",
                        .atomId = PixelAtoms::Atom::kThermalSensorAbnormalityDetected,
                        .values = {abnormality_type->second, name, val}};
    const ndk::ScopedAStatus ret = stats_client->reportVendorAtom(event);
    if (!ret.isOk())
        ALOGE("Unable to report Thermal Abnormal event.");
}

bool UeventListener::ProcessUevent() {
    char msg[UEVENT_MSG_LEN + 2];
    char *cp;
    const char *driver, *product, *subsystem;
    const char *mic_break_status, *mic_degrade_status;
    const char *devpath;
    bool collect_partner_id = false;
    const char *gpu_event_type = nullptr, *gpu_event_info = nullptr;
    const char *thermal_abnormal_event_type = nullptr, *thermal_abnormal_event_info = nullptr;
    int n;

    if (uevent_fd_ < 0) {
        uevent_fd_ = uevent_open_socket(64 * 1024, true);
        if (uevent_fd_ < 0) {
            ALOGE("uevent_init: uevent_open_socket failed\n");
            return false;
        }
    }

#ifdef LOG_UEVENTS_TO_FILE_ONLY_FOR_DEVEL
    if (log_fd_ < 0) {
        /* Intentionally no O_CREAT so no logging will happen
         * unless the user intentionally 'touch's the file.
         */
        log_fd_ = open(LOG_UEVENTS_TO_FILE_ONLY_FOR_DEVEL, O_WRONLY);
    }
#endif

    n = uevent_kernel_multicast_recv(uevent_fd_, msg, UEVENT_MSG_LEN);
    if (n <= 0 || n >= UEVENT_MSG_LEN)
        return false;

    // Ensure double-null termination of msg.
    msg[n] = '\0';
    msg[n + 1] = '\0';

    driver = product = subsystem = NULL;
    mic_break_status = mic_degrade_status = devpath = NULL;

    /**
     * msg is a sequence of null-terminated strings.
     * Iterate through and record positions of string/value pairs of interest.
     * Double null indicates end of the message. (enforced above).
     */
    cp = msg;
    while (*cp) {
        if (log_fd_ > 0) {
            write(log_fd_, cp, strlen(cp));
            write(log_fd_, "\n", 1);
        }
        if (!strncmp(cp, "DRIVER=", strlen("DRIVER="))) {
            driver = cp;
        } else if (!strncmp(cp, "PRODUCT=", strlen("PRODUCT="))) {
            product = cp;
        } else if (!strncmp(cp, "MIC_BREAK_STATUS=", strlen("MIC_BREAK_STATUS="))) {
            mic_break_status = cp;
        } else if (!strncmp(cp, "MIC_DEGRADE_STATUS=", strlen("MIC_DEGRADE_STATUS="))) {
            mic_degrade_status = cp;
        } else if (!strncmp(cp, "DEVPATH=", strlen("DEVPATH="))) {
            devpath = cp;
        } else if (!strncmp(cp, "SUBSYSTEM=", strlen("SUBSYSTEM="))) {
            subsystem = cp;
        } else if (!strncmp(cp, kTypeCPartnerUevent.c_str(), kTypeCPartnerUevent.size())) {
            collect_partner_id = true;
        } else if (!strncmp(cp, "GPU_UEVENT_TYPE=", strlen("GPU_UEVENT_TYPE="))) {
            gpu_event_type = cp;
        } else if (!strncmp(cp, "GPU_UEVENT_INFO=", strlen("GPU_UEVENT_INFO="))) {
            gpu_event_info = cp;
        } else if (!strncmp(cp, THERMAL_ABNORMAL_TYPE_EQ, strlen(THERMAL_ABNORMAL_TYPE_EQ))) {
            thermal_abnormal_event_type = cp;
        } else if (!strncmp(cp, THERMAL_ABNORMAL_INFO_EQ, strlen(THERMAL_ABNORMAL_INFO_EQ))) {
            thermal_abnormal_event_info = cp;
        }
        /* advance to after the next \0 */
        while (*cp++) {
        }
    }

    std::shared_ptr<IStats> stats_client = getStatsService();
    if (!stats_client) {
        ALOGE("Unable to get Stats service instance.");
    } else {
        /* Process the strings recorded. */
        ReportMicStatusUevents(stats_client, devpath, mic_break_status);
        ReportMicStatusUevents(stats_client, devpath, mic_degrade_status);
        ReportUsbPortOverheatEvent(stats_client, driver);
        ReportChargeMetricsEvent(stats_client, driver);
        ReportBatteryCapacityFGEvent(stats_client, subsystem);
        if (collect_partner_id) {
            ReportTypeCPartnerId(stats_client);
        }
        ReportGpuEvent(stats_client, driver, gpu_event_type, gpu_event_info);
        ReportThermalAbnormalEvent(stats_client, devpath, thermal_abnormal_event_type,
                                   thermal_abnormal_event_info);
        ReportFGMetricsEvent(stats_client, driver);
    }

    if (log_fd_ > 0) {
        write(log_fd_, "\n", 1);
    }
    return true;
}

UeventListener::UeventListener(const std::string audio_uevent, const std::string ssoc_details_path,
                               const std::string overheat_path,
                               const std::string charge_metrics_path,
                               const std::string typec_partner_vid_path,
                               const std::string typec_partner_pid_path,
                               const std::vector<std::string> fg_learning_path,
                               const std::string fw_update_path,
                               const std::vector<std::string> fg_modelloading_path)
    : kAudioUevent(audio_uevent),
      kBatterySSOCPath(ssoc_details_path),
      kUsbPortOverheatPath(overheat_path),
      kChargeMetricsPath(charge_metrics_path),
      kTypeCPartnerUevent(typec_partner_uevent_default),
      kTypeCPartnerVidPath(typec_partner_vid_path),
      kTypeCPartnerPidPath(typec_partner_pid_path),
      kFGLearningPath(fg_learning_path),
      kFwUpdatePath(fw_update_path),
      kFGModelLoadingPath(fg_modelloading_path),
      uevent_fd_(-1),
      log_fd_(-1) {}

UeventListener::UeventListener(const struct UeventPaths &uevents_paths)
    : kAudioUevent((uevents_paths.AudioUevent == nullptr) ? "" : uevents_paths.AudioUevent),
      kBatterySSOCPath((uevents_paths.SsocDetailsPath == nullptr) ? ssoc_details_path
                                                                  : uevents_paths.SsocDetailsPath),
      kUsbPortOverheatPath((uevents_paths.OverheatPath == nullptr) ? overheat_path_default
                                                                   : uevents_paths.OverheatPath),
      kChargeMetricsPath((uevents_paths.ChargeMetricsPath == nullptr)
                                 ? charge_metrics_path_default
                                 : uevents_paths.ChargeMetricsPath),
      kTypeCPartnerUevent((uevents_paths.TypeCPartnerUevent == nullptr)
                                  ? typec_partner_uevent_default
                                  : uevents_paths.TypeCPartnerUevent),
      kTypeCPartnerVidPath((uevents_paths.TypeCPartnerVidPath == nullptr)
                                   ? typec_partner_vid_path_default
                                   : uevents_paths.TypeCPartnerVidPath),
      kTypeCPartnerPidPath((uevents_paths.TypeCPartnerPidPath == nullptr)
                                   ? typec_partner_pid_path_default
                                   : uevents_paths.TypeCPartnerPidPath),
      kFGLearningPath(uevents_paths.FGLearningPath),
      kFwUpdatePath((uevents_paths.FwUpdatePath == nullptr)
                                   ? "" : uevents_paths.FwUpdatePath),
      kFGModelLoadingPath(uevents_paths.FGModelLoadingPath),
      uevent_fd_(-1),
      log_fd_(-1) {}

/* Thread function to continuously monitor uevents.
 * Exit after kMaxConsecutiveErrors to prevent spinning. */
void UeventListener::ListenForever() {
    constexpr int kMaxConsecutiveErrors = 10;
    int consecutive_errors = 0;

    while (1) {
        if (ProcessUevent()) {
            consecutive_errors = 0;
        } else {
            if (++consecutive_errors >= kMaxConsecutiveErrors) {
                ALOGE("Too many ProcessUevent errors; exiting UeventListener.");
                return;
            }
        }
    }
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

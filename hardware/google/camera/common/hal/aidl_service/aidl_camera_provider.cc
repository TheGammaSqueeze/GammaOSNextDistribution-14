/*
 * Copyright (C) 2022 The Android Open Source Project
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

#define LOG_TAG "GCH_AidlCameraProvider"
//#define LOG_NDEBUG 0
#include "aidl_camera_provider.h"

#include <log/log.h>

#include <regex>

#include "aidl_camera_device.h"
#include "aidl_utils.h"
#include "camera_device.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

namespace aidl_utils = ::android::hardware::camera::implementation::aidl_utils;

using aidl::android::hardware::camera::common::CameraDeviceStatus;
using aidl::android::hardware::camera::common::Status;
using aidl::android::hardware::camera::common::TorchModeStatus;
using aidl::android::hardware::camera::common::VendorTagSection;
using ::android::google_camera_hal::CameraDevice;

const std::string AidlCameraProvider::kProviderName = "internal";
// "device@<version>/internal/<id>"
const std::regex AidlCameraProvider::kDeviceNameRegex(
    "device@([0-9]+\\.[0-9]+)/internal/(.+)");

std::shared_ptr<AidlCameraProvider> AidlCameraProvider::Create() {
  std::shared_ptr<AidlCameraProvider> provider =
      ndk::SharedRefBase::make<AidlCameraProvider>();

  status_t res = provider->Initialize();
  if (res != OK) {
    ALOGE("%s: Initializing AidlCameraProvider failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return nullptr;
  }

  return provider;
}

status_t AidlCameraProvider::Initialize() {
  google_camera_provider_ = CameraProvider::Create();
  if (google_camera_provider_ == nullptr) {
    ALOGE("%s: Creating CameraProvider failed.", __FUNCTION__);
    return NO_INIT;
  }

  camera_provider_callback_ = {
      .camera_device_status_change = google_camera_hal::CameraDeviceStatusChangeFunc(
          [this](std::string camera_id,
                 google_camera_hal::CameraDeviceStatus new_status) {
            if (callbacks_ == nullptr) {
              ALOGE("%s: callbacks_ is null", __FUNCTION__);
              return;
            }
            CameraDeviceStatus aidl_camera_device_status;
            status_t res = aidl_utils::ConvertToAidlCameraDeviceStatus(
                new_status, &aidl_camera_device_status);
            if (res != OK) {
              ALOGE(
                  "%s: Converting to aidl camera device status failed: %s(%d)",
                  __FUNCTION__, strerror(-res), res);
              return;
            }

            std::unique_lock<std::mutex> lock(callbacks_lock_);
            auto aidl_res = callbacks_->cameraDeviceStatusChange(
                "device@" +
                    device::implementation::AidlCameraDevice::kDeviceVersion +
                    "/" + kProviderName + "/" + camera_id,
                aidl_camera_device_status);
            if (!aidl_res.isOk()) {
              ALOGE("%s: device status change transaction error: %s",
                    __FUNCTION__, aidl_res.getMessage());
              return;
            }
          }),
      .physical_camera_device_status_change =
          google_camera_hal::PhysicalCameraDeviceStatusChangeFunc(
              [this](std::string camera_id, std::string physical_camera_id,
                     google_camera_hal::CameraDeviceStatus new_status) {
                if (callbacks_ == nullptr) {
                  ALOGE("%s: callbacks_ is null", __FUNCTION__);
                  return;
                }
                /*auto castResult =
                    provider::V2_6::ICameraProviderCallback::castFrom(callbacks_);
                if (!castResult.isOk()) {
                  ALOGE("%s: callbacks_ cannot be casted to version 2.6",
                        __FUNCTION__);
                  return;
                }
                sp<provider::V2_6::ICameraProviderCallback> callbacks_2_6_ =
                    castResult;
                if (callbacks_2_6_ == nullptr) {
                  ALOGE("%s: callbacks_2_6_ is null", __FUNCTION__);
                  return;
                }*/

                CameraDeviceStatus aidl_camera_device_status;
                status_t res = aidl_utils::ConvertToAidlCameraDeviceStatus(
                    new_status, &aidl_camera_device_status);
                if (res != OK) {
                  ALOGE(
                      "%s: Converting to aidl camera device status failed: "
                      "%s(%d)",
                      __FUNCTION__, strerror(-res), res);
                  return;
                }

                std::unique_lock<std::mutex> lock(callbacks_lock_);
                auto aidl_res = callbacks_->physicalCameraDeviceStatusChange(
                    "device@" +
                        device::implementation::AidlCameraDevice::kDeviceVersion +
                        "/" + kProviderName + "/" + camera_id,
                    physical_camera_id, aidl_camera_device_status);
                if (!aidl_res.isOk()) {
                  ALOGE(
                      "%s: physical camera status change transaction error: %s",
                      __FUNCTION__, aidl_res.getMessage());
                  return;
                }
              }),
      .torch_mode_status_change = google_camera_hal::TorchModeStatusChangeFunc(
          [this](std::string camera_id,
                 google_camera_hal::TorchModeStatus new_status) {
            if (callbacks_ == nullptr) {
              ALOGE("%s: callbacks_ is null", __FUNCTION__);
              return;
            }

            TorchModeStatus aidl_torch_status;
            status_t res = aidl_utils::ConvertToAidlTorchModeStatus(
                new_status, &aidl_torch_status);
            if (res != OK) {
              ALOGE("%s: Converting to aidl torch status failed: %s(%d)",
                    __FUNCTION__, strerror(-res), res);
              return;
            }

            std::unique_lock<std::mutex> lock(callbacks_lock_);
            auto aidl_res = callbacks_->torchModeStatusChange(
                "device@" +
                    device::implementation::AidlCameraDevice::kDeviceVersion +
                    "/" + kProviderName + "/" + camera_id,
                aidl_torch_status);
            if (!aidl_res.isOk()) {
              ALOGE("%s: torch status change transaction error: %s",
                    __FUNCTION__, aidl_res.getMessage());
              return;
            }
          }),
  };

  google_camera_provider_->SetCallback(&camera_provider_callback_);
  // purge pending malloc pages after initialization
  mallopt(M_PURGE, 0);
  return OK;
}

ScopedAStatus AidlCameraProvider::setCallback(
    const std::shared_ptr<ICameraProviderCallback>& callback) {
  if (callback == nullptr) {
    ALOGE("AidlCameraProvider::setCallback() called with nullptr");
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  bool first_time = false;
  {
    std::unique_lock<std::mutex> lock(callbacks_lock_);
    first_time = callbacks_ == nullptr;
    callbacks_ = callback;
  }
  google_camera_provider_->TriggerDeferredCallbacks();
#ifdef __ANDROID_APEX__
  if (first_time) {
    std::string ready_property_name = "vendor.camera.hal.ready.count";
    int ready_count = property_get_int32(ready_property_name.c_str(), 0);
    property_set(ready_property_name.c_str(),
                 std::to_string(++ready_count).c_str());
    ALOGI("AidlCameraProvider::setCallback() first time ready count: %d ",
          ready_count);
  }
#endif
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraProvider::getVendorTags(
    std::vector<VendorTagSection>* vts) {
  if (vts == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  vts->clear();
  std::vector<google_camera_hal::VendorTagSection> hal_vendor_tag_sections;

  status_t res =
      google_camera_provider_->GetVendorTags(&hal_vendor_tag_sections);
  if (res != OK) {
    ALOGE("%s: Getting vendor tags failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  res = aidl_utils::ConvertToAidlVendorTagSections(hal_vendor_tag_sections, vts);
  if (res != OK) {
    ALOGE("%s: Converting to aidl vendor tags failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraProvider::getCameraIdList(
    std::vector<std::string>* camera_ids_ret) {
  if (camera_ids_ret == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  camera_ids_ret->clear();
  std::vector<uint32_t> camera_ids;
  status_t res = google_camera_provider_->GetCameraIdList(&camera_ids);
  if (res != OK) {
    ALOGE("%s: Getting camera ID list failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }

  camera_ids_ret->resize(camera_ids.size());
  for (uint32_t i = 0; i < camera_ids_ret->size(); i++) {
    // camera ID is in the form of "device@<major>.<minor>/<type>/<id>"
    (*camera_ids_ret)[i] =
        "device@" + device::implementation::AidlCameraDevice::kDeviceVersion +
        "/" + kProviderName + "/" + std::to_string(camera_ids[i]);
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraProvider::getConcurrentCameraIds(
    std::vector<ConcurrentCameraIdCombination>* aidl_camera_id_combinations) {
  if (aidl_camera_id_combinations == nullptr) {
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }
  aidl_camera_id_combinations->clear();
  std::vector<std::unordered_set<uint32_t>> camera_id_combinations;
  status_t res = google_camera_provider_->GetConcurrentStreamingCameraIds(
      &camera_id_combinations);
  if (res != OK) {
    ALOGE(
        "%s: Getting the combinations of concurrent streaming camera ids "
        "failed: %s(%d)",
        __FUNCTION__, strerror(-res), res);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  aidl_camera_id_combinations->resize(camera_id_combinations.size());
  int i = 0;
  for (auto& combination : camera_id_combinations) {
    std::vector<std::string> aidl_combination(combination.size());
    int c = 0;
    for (auto& camera_id : combination) {
      aidl_combination[c] = std::to_string(camera_id);
      c++;
    }
    (*aidl_camera_id_combinations)[i].combination = aidl_combination;
    i++;
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraProvider::isConcurrentStreamCombinationSupported(
    const std::vector<CameraIdAndStreamCombination>& configs, bool* supported) {
  *supported = false;
  std::vector<google_camera_hal::CameraIdAndStreamConfiguration>
      devices_stream_configs(configs.size());
  status_t res = OK;
  size_t c = 0;
  std::vector<CameraIdAndStreamCombination> configsWithOverriddenSensorPixelModes =
      configs;
  for (auto& config : configsWithOverriddenSensorPixelModes) {
    aidl_utils::FixSensorPixelModesInStreamConfig(&config.streamConfiguration);
  }

  for (auto& config : configsWithOverriddenSensorPixelModes) {
    res = aidl_utils::ConvertToHalStreamConfig(
        config.streamConfiguration,
        &devices_stream_configs[c].stream_configuration);
    if (res != OK) {
      ALOGE("%s: ConverToHalStreamConfig failed", __FUNCTION__);
      return ScopedAStatus::fromServiceSpecificError(
          static_cast<int32_t>(Status::INTERNAL_ERROR));
    }
    uint32_t camera_id = atoi(config.cameraId.c_str());
    devices_stream_configs[c].camera_id = camera_id;
    c++;
  }
  res = google_camera_provider_->IsConcurrentStreamCombinationSupported(
      devices_stream_configs, supported);
  if (res != OK) {
    ALOGE("%s: IsConcurrentStreamCombinationSupported failed", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  return ScopedAStatus::ok();
}

bool AidlCameraProvider::ParseDeviceName(const std::string& device_name,
                                         std::string* device_version,
                                         std::string* camera_id) {
  std::string device_name_std(device_name.c_str());
  std::smatch sm;

  if (std::regex_match(device_name_std, sm,
                       AidlCameraProvider::kDeviceNameRegex)) {
    if (device_version != nullptr) {
      *device_version = sm[1];
    }
    if (camera_id != nullptr) {
      *camera_id = sm[2];
    }
    return true;
  }
  return false;
}

ScopedAStatus AidlCameraProvider::getCameraDeviceInterface(
    const std::string& camera_device_name,
    std::shared_ptr<ICameraDevice>* device) {
  std::unique_ptr<CameraDevice> google_camera_device;
  if (device == nullptr) {
    ALOGE("%s: device is nullptr. ", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  // Parse camera_device_name.
  std::string camera_id, device_version;

  bool match = ParseDeviceName(camera_device_name, &device_version, &camera_id);
  if (!match) {
    ALOGE("%s: Device name parse fail. ", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::ILLEGAL_ARGUMENT));
  }

  status_t res = google_camera_provider_->CreateCameraDevice(
      atoi(camera_id.c_str()), &google_camera_device);
  if (res != OK) {
    ALOGE("%s: Creating CameraDevice failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return aidl_utils::ConvertToAidlReturn(res);
  }

  *device = device::implementation::AidlCameraDevice::Create(
      std::move(google_camera_device));
  if (*device == nullptr) {
    ALOGE("%s: Creating AidlCameraDevice failed", __FUNCTION__);
    return ScopedAStatus::fromServiceSpecificError(
        static_cast<int32_t>(Status::INTERNAL_ERROR));
  }
  return ScopedAStatus::ok();
}

ScopedAStatus AidlCameraProvider::notifyDeviceStateChange(int64_t new_state) {
  google_camera_hal::DeviceState device_state =
      google_camera_hal::DeviceState::kNormal;
  ::android::hardware::camera::implementation::aidl_utils::ConvertToHalDeviceState(
      new_state, device_state);
  google_camera_provider_->NotifyDeviceStateChange(device_state);
  return ScopedAStatus::ok();
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android

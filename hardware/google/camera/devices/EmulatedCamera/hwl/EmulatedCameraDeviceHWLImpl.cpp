/*
 * Copyright (C) 2019 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCameraDeviceHwlImpl"
#include "EmulatedCameraDeviceHWLImpl.h"

#include <hardware/camera_common.h>
#include <log/log.h>

#include "EmulatedCameraDeviceSessionHWLImpl.h"
#include "utils/HWLUtils.h"

namespace android {

std::unique_ptr<CameraDeviceHwl> EmulatedCameraDeviceHwlImpl::Create(
    uint32_t camera_id, std::unique_ptr<HalCameraMetadata> static_meta,
    PhysicalDeviceMapPtr physical_devices,
    std::shared_ptr<EmulatedTorchState> torch_state) {
  auto device = std::unique_ptr<EmulatedCameraDeviceHwlImpl>(
      new EmulatedCameraDeviceHwlImpl(camera_id, std::move(static_meta),
                                      std::move(physical_devices),
                                      torch_state));

  if (device == nullptr) {
    ALOGE("%s: Creating EmulatedCameraDeviceHwlImpl failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = device->Initialize();
  if (res != OK) {
    ALOGE("%s: Initializing EmulatedCameraDeviceHwlImpl failed: %s (%d).",
          __FUNCTION__, strerror(-res), res);
    return nullptr;
  }

  ALOGI("%s: Created EmulatedCameraDeviceHwlImpl for camera %u", __FUNCTION__,
        device->camera_id_);

  return device;
}

EmulatedCameraDeviceHwlImpl::EmulatedCameraDeviceHwlImpl(
    uint32_t camera_id, std::unique_ptr<HalCameraMetadata> static_meta,
    PhysicalDeviceMapPtr physical_devices,
    std::shared_ptr<EmulatedTorchState> torch_state)
    : camera_id_(camera_id),
      static_metadata_(std::move(static_meta)),
      physical_device_map_(std::move(physical_devices)),
      torch_state_(torch_state) {}

uint32_t EmulatedCameraDeviceHwlImpl::GetCameraId() const {
  return camera_id_;
}

status_t EmulatedCameraDeviceHwlImpl::Initialize() {
  auto ret = GetSensorCharacteristics(static_metadata_.get(),
                                      &sensor_chars_[camera_id_]);
  if (ret != OK) {
    ALOGE("%s: Unable to extract sensor characteristics %s (%d)", __FUNCTION__,
          strerror(-ret), ret);
    return ret;
  }

  stream_configuration_map_ =
      std::make_unique<StreamConfigurationMap>(*static_metadata_);
  stream_configuration_map_max_resolution_ =
      std::make_unique<StreamConfigurationMap>(*static_metadata_,
                                               /*maxResolution*/ true);

  for (const auto& it : *physical_device_map_) {
    uint32_t physical_id = it.first;
    HalCameraMetadata* physical_hal_metadata = it.second.second.get();
    physical_stream_configuration_map_.emplace(
        physical_id,
        std::make_unique<StreamConfigurationMap>(*physical_hal_metadata));
    physical_stream_configuration_map_max_resolution_.emplace(
        physical_id, std::make_unique<StreamConfigurationMap>(
                         *physical_hal_metadata, /*maxResolution*/ true));

    ret = GetSensorCharacteristics(physical_hal_metadata,
                                   &sensor_chars_[physical_id]);
    if (ret != OK) {
      ALOGE("%s: Unable to extract camera %d sensor characteristics %s (%d)",
            __FUNCTION__, physical_id, strerror(-ret), ret);
      return ret;
    }
  }

  default_torch_strength_level_ = GetDefaultTorchStrengthLevel();
  maximum_torch_strength_level_ = GetMaximumTorchStrengthLevel();

  device_info_ = EmulatedCameraDeviceInfo::Create(
      HalCameraMetadata::Clone(static_metadata_.get()));
  if (device_info_ == nullptr) {
    ALOGE("%s: Unable to create device info for camera %d", __FUNCTION__,
          camera_id_);
    return NO_INIT;
  }

  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::GetResourceCost(
    CameraResourceCost* cost) const {
  // TODO: remove hardcode
  cost->resource_cost = 100;

  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::GetCameraCharacteristics(
    std::unique_ptr<HalCameraMetadata>* characteristics) const {
  if (characteristics == nullptr) {
    return BAD_VALUE;
  }

  *characteristics = HalCameraMetadata::Clone(static_metadata_.get());

  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::GetPhysicalCameraCharacteristics(
    uint32_t physical_camera_id,
    std::unique_ptr<HalCameraMetadata>* characteristics) const {
  if (characteristics == nullptr) {
    return BAD_VALUE;
  }

  if (physical_device_map_.get() == nullptr) {
    ALOGE("%s: Camera %d is not a logical device!", __func__, camera_id_);
    return NO_INIT;
  }

  if (physical_device_map_->find(physical_camera_id) ==
      physical_device_map_->end()) {
    ALOGE("%s: Physical camera id %d is not part of logical camera %d!",
          __func__, physical_camera_id, camera_id_);
    return BAD_VALUE;
  }

  *characteristics = HalCameraMetadata::Clone(
      physical_device_map_->at(physical_camera_id).second.get());

  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::SetTorchMode(TorchMode mode) {
  if (torch_state_.get() == nullptr) {
    return INVALID_OPERATION;
  }

  // If torch strength control is supported, reset the torch strength level to
  // default level whenever the torch is turned OFF.
  if (maximum_torch_strength_level_ > 1) {
    torch_state_->InitializeTorchDefaultLevel(default_torch_strength_level_);
    torch_state_->InitializeSupportTorchStrengthLevel(true);
  }

  return torch_state_->SetTorchMode(mode);
}

status_t EmulatedCameraDeviceHwlImpl::TurnOnTorchWithStrengthLevel(int32_t torch_strength) {
  if (torch_state_.get() == nullptr) {
    return UNKNOWN_TRANSACTION;
  }

  // This API is supported if the maximum level is set to greater than 1.
  if (maximum_torch_strength_level_ <= 1) {
    ALOGE("Torch strength control feature is not supported.");
    return UNKNOWN_TRANSACTION;
  }
  // Validate that the torch_strength is within the range.
  if (torch_strength > maximum_torch_strength_level_ || torch_strength < 1) {
    ALOGE("Torch strength value should be within the range.");
    return BAD_VALUE;
  }

  return torch_state_->TurnOnTorchWithStrengthLevel(torch_strength);
}

status_t EmulatedCameraDeviceHwlImpl::GetTorchStrengthLevel(int32_t& torch_strength) const {
  if (default_torch_strength_level_ < 1 && maximum_torch_strength_level_ <= 1) {
    ALOGE("Torch strength control feature is not supported.");
    return UNKNOWN_TRANSACTION;
  }

  torch_strength = torch_state_->GetTorchStrengthLevel();
  ALOGV("Current torch strength level is: %d", torch_strength);
  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::ConstructDefaultRequestSettings(
    RequestTemplate type, std::unique_ptr<HalCameraMetadata>* request_settings) {
  if (request_settings == nullptr) {
    ALOGE("%s requestSettings is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  auto idx = static_cast<size_t>(type);
  if (idx >= kTemplateCount) {
    ALOGE("%s: Unexpected request type: %d", __FUNCTION__, type);
    return BAD_VALUE;
  }

  if (device_info_->default_requests_[idx].get() == nullptr) {
    ALOGE("%s: Unsupported request type: %d", __FUNCTION__, type);
    return BAD_VALUE;
  }

  *request_settings = HalCameraMetadata::Clone(
      device_info_->default_requests_[idx]->GetRawCameraMetadata());
  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::DumpState(int /*fd*/) {
  return OK;
}

status_t EmulatedCameraDeviceHwlImpl::CreateCameraDeviceSessionHwl(
    CameraBufferAllocatorHwl* /*camera_allocator_hwl*/,
    std::unique_ptr<CameraDeviceSessionHwl>* session) {
  if (session == nullptr) {
    ALOGE("%s: session is nullptr.", __FUNCTION__);
    return BAD_VALUE;
  }

  std::unique_ptr<EmulatedCameraDeviceInfo> deviceInfo =
      EmulatedCameraDeviceInfo::Clone(*device_info_);
  *session = EmulatedCameraDeviceSessionHwlImpl::Create(
      camera_id_, std::move(deviceInfo),
      ClonePhysicalDeviceMap(physical_device_map_), torch_state_);
  if (*session == nullptr) {
    ALOGE("%s: Cannot create EmulatedCameraDeviceSessionHWlImpl.", __FUNCTION__);
    return BAD_VALUE;
  }

  if (torch_state_.get() != nullptr) {
    torch_state_->AcquireFlashHw();
  }

  return OK;
}

bool EmulatedCameraDeviceHwlImpl::IsStreamCombinationSupported(
    const StreamConfiguration& stream_config) {
  return EmulatedSensor::IsStreamCombinationSupported(
      camera_id_, stream_config, *stream_configuration_map_,
      *stream_configuration_map_max_resolution_,
      physical_stream_configuration_map_,
      physical_stream_configuration_map_max_resolution_, sensor_chars_);
}

int32_t EmulatedCameraDeviceHwlImpl::GetDefaultTorchStrengthLevel() const {
  camera_metadata_ro_entry entry;
  int32_t default_level = 0;
  auto ret = static_metadata_->Get(ANDROID_FLASH_INFO_STRENGTH_DEFAULT_LEVEL, &entry);
  if (ret == OK && (entry.count == 1)) {
     default_level = *entry.data.i32;
     ALOGV("Default torch strength level is %d", default_level);
  }
  return default_level;
}

int32_t EmulatedCameraDeviceHwlImpl::GetMaximumTorchStrengthLevel() const {
  camera_metadata_ro_entry entry;
  int32_t max_level = 0;
  auto ret = static_metadata_->Get(ANDROID_FLASH_INFO_STRENGTH_MAXIMUM_LEVEL, &entry);
  if (ret == OK && (entry.count == 1)) {
     max_level = *entry.data.i32;
     ALOGV("Maximum torch strength level is %d", max_level);
  }
  return max_level;
}

}  // namespace android

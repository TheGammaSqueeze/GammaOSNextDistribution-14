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
#define LOG_TAG "GCH_CameraProvider"
#define ATRACE_TAG ATRACE_TAG_CAMERA
#include "camera_provider.h"

#include <dlfcn.h>
#include <log/log.h>
#include <utils/Trace.h>

#if !GCH_HWL_USE_DLOPEN
#include "lyric_hwl/madvise_library_list.h"
#endif
#include "vendor_tag_defs.h"
#include "vendor_tag_utils.h"

#if GCH_HWL_USE_DLOPEN
// HWL layer implementation path
constexpr std::string_view kCameraHwlLib = "libgooglecamerahwl_impl.so";
#endif

namespace android {
namespace google_camera_hal {

CameraProvider::~CameraProvider() {
  VendorTagManager::GetInstance().Reset();
  if (hwl_lib_handle_ != nullptr) {
    dlclose(hwl_lib_handle_);
    hwl_lib_handle_ = nullptr;
  }
}

std::unique_ptr<CameraProvider> CameraProvider::Create(
    std::unique_ptr<CameraProviderHwl> camera_provider_hwl) {
  ATRACE_CALL();
  auto provider = std::unique_ptr<CameraProvider>(new CameraProvider());
  if (provider == nullptr) {
    ALOGE("%s: Creating CameraProvider failed.", __FUNCTION__);
    return nullptr;
  }

  status_t res = provider->Initialize(std::move(camera_provider_hwl));
  if (res != OK) {
    ALOGE("%s: Initializing CameraProvider failed: %s (%d).", __FUNCTION__,
          strerror(-res), res);
    return nullptr;
  }

  return provider;
}

status_t CameraProvider::Initialize(
    std::unique_ptr<CameraProviderHwl> camera_provider_hwl) {
  ATRACE_CALL();
  // Advertise the HAL vendor tags to the camera metadata framework before
  // creating a HWL provider.
  status_t res = VendorTagManager::GetInstance().AddTags(kHalVendorTagSections);
  if (res != OK) {
    ALOGE("%s: Initializing VendorTagManager failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  if (camera_provider_hwl == nullptr) {
    status_t res = CreateHwl(&camera_provider_hwl);
    if (res != OK) {
      ALOGE("%s: Creating CameraProviderHwlImpl failed.", __FUNCTION__);
      return NO_INIT;
    }
  }

  res = camera_provider_hwl->CreateBufferAllocatorHwl(&camera_allocator_hwl_);
  if (res == INVALID_OPERATION) {
    camera_allocator_hwl_ = nullptr;
    ALOGE("%s: HWL doesn't support vendor buffer allocation %s(%d)",
          __FUNCTION__, strerror(-res), res);
  } else if (res != OK) {
    camera_allocator_hwl_ = nullptr;
    ALOGE("%s: Creating CameraBufferAllocatorHwl failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return NO_INIT;
  }

  camera_provider_hwl_ = std::move(camera_provider_hwl);
  res = InitializeVendorTags();
  if (res != OK) {
    ALOGE("%s InitailizeVendorTags() failed: %s (%d).", __FUNCTION__,
          strerror(-res), res);
    camera_provider_hwl_ = nullptr;
    return res;
  }

  return OK;
}

status_t CameraProvider::InitializeVendorTags() {
  std::vector<VendorTagSection> hwl_tag_sections;
  status_t res = camera_provider_hwl_->GetVendorTags(&hwl_tag_sections);
  if (res != OK) {
    ALOGE("%s: GetVendorTags() for HWL tags failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  // Combine HAL and HWL vendor tag sections
  res = vendor_tag_utils::CombineVendorTags(
      kHalVendorTagSections, hwl_tag_sections, &vendor_tag_sections_);
  if (res != OK) {
    ALOGE("%s: CombineVendorTags() failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  return OK;
}

status_t CameraProvider::SetCallback(const CameraProviderCallback* callback) {
  ATRACE_CALL();
  if (callback == nullptr) {
    return BAD_VALUE;
  }

  provider_callback_ = callback;
  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized to set callback.",
          __FUNCTION__);
    return NO_INIT;
  }

  hwl_provider_callback_.camera_device_status_change =
      HwlCameraDeviceStatusChangeFunc(
          [this](uint32_t camera_id, CameraDeviceStatus new_status) {
            provider_callback_->camera_device_status_change(
                std::to_string(camera_id), new_status);
          });

  hwl_provider_callback_.physical_camera_device_status_change =
      HwlPhysicalCameraDeviceStatusChangeFunc(
          [this](uint32_t camera_id, uint32_t physical_camera_id,
                 CameraDeviceStatus new_status) {
            provider_callback_->physical_camera_device_status_change(
                std::to_string(camera_id), std::to_string(physical_camera_id),
                new_status);
          });

  hwl_provider_callback_.torch_mode_status_change = HwlTorchModeStatusChangeFunc(
      [this](uint32_t camera_id, TorchModeStatus new_status) {
        provider_callback_->torch_mode_status_change(std::to_string(camera_id),
                                                     new_status);
      });

  camera_provider_hwl_->SetCallback(hwl_provider_callback_);
  return OK;
}

status_t CameraProvider::TriggerDeferredCallbacks() {
  ATRACE_CALL();
  return camera_provider_hwl_->TriggerDeferredCallbacks();
}

status_t CameraProvider::GetVendorTags(
    std::vector<VendorTagSection>* vendor_tag_sections) const {
  ATRACE_CALL();
  if (vendor_tag_sections == nullptr) {
    return BAD_VALUE;
  }

  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized.", __FUNCTION__);
    return NO_INIT;
  }

  *vendor_tag_sections = vendor_tag_sections_;
  return OK;
}

status_t CameraProvider::GetCameraIdList(std::vector<uint32_t>* camera_ids) const {
  ATRACE_CALL();
  if (camera_ids == nullptr) {
    ALOGE("%s: camera_ids is nullptr", __FUNCTION__);
    return BAD_VALUE;
  }

  status_t res = camera_provider_hwl_->GetVisibleCameraIds(camera_ids);
  if (res != OK) {
    ALOGE("%s: failed to get visible camera IDs from the camera provider",
          __FUNCTION__);
    return res;
  }
  return OK;
}

bool CameraProvider::IsSetTorchModeSupported() const {
  ATRACE_CALL();
  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized.", __FUNCTION__);
    return NO_INIT;
  }

  return camera_provider_hwl_->IsSetTorchModeSupported();
}

status_t CameraProvider::IsConcurrentStreamCombinationSupported(
    const std::vector<CameraIdAndStreamConfiguration>& configs,
    bool* is_supported) {
  ATRACE_CALL();
  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized.", __FUNCTION__);
    return NO_INIT;
  }
  return camera_provider_hwl_->IsConcurrentStreamCombinationSupported(
      configs, is_supported);
}

// Get the combinations of camera ids which support concurrent streaming
status_t CameraProvider::GetConcurrentStreamingCameraIds(
    std::vector<std::unordered_set<uint32_t>>* camera_id_combinations) {
  if (camera_id_combinations == nullptr) {
    return BAD_VALUE;
  }

  ATRACE_CALL();
  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized.", __FUNCTION__);
    return NO_INIT;
  }

  return camera_provider_hwl_->GetConcurrentStreamingCameraIds(
      camera_id_combinations);
}

status_t CameraProvider::CreateCameraDevice(
    uint32_t camera_id, std::unique_ptr<CameraDevice>* device) {
  ATRACE_CALL();
  std::vector<uint32_t> camera_ids;
  if (device == nullptr) {
    return BAD_VALUE;
  }

  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: Camera provider HWL was not initialized.", __FUNCTION__);
    return NO_INIT;
  }

  // Check camera_id is valid.
  status_t res = camera_provider_hwl_->GetVisibleCameraIds(&camera_ids);
  if (res != OK) {
    ALOGE("%s: failed to get visible camera IDs from the camera provider",
          __FUNCTION__);
    return res;
  }

  if (std::find(camera_ids.begin(), camera_ids.end(), camera_id) ==
      camera_ids.end()) {
    ALOGE("%s: camera_id: %u  invalid.", __FUNCTION__, camera_id);
    return BAD_VALUE;
  }

  std::unique_ptr<CameraDeviceHwl> camera_device_hwl;
  res = camera_provider_hwl_->CreateCameraDeviceHwl(camera_id,
                                                    &camera_device_hwl);
  if (res != OK) {
    ALOGE("%s: Creating CameraProviderHwl failed: %s(%d)", __FUNCTION__,
          strerror(-res), res);
    return res;
  }

  const std::vector<std::string>* configure_streams_libs = nullptr;

#if GCH_HWL_USE_DLOPEN
  configure_streams_libs = reinterpret_cast<decltype(configure_streams_libs)>(
      dlsym(hwl_lib_handle_, "configure_streams_libraries"));
#else
  configure_streams_libs = &configure_streams_libraries;
#endif
  *device =
      CameraDevice::Create(std::move(camera_device_hwl),
                           camera_allocator_hwl_.get(), configure_streams_libs);
  if (*device == nullptr) {
    return NO_INIT;
  }

  return OK;
}

status_t CameraProvider::CreateHwl(
    std::unique_ptr<CameraProviderHwl>* camera_provider_hwl) {
  ATRACE_CALL();
#if GCH_HWL_USE_DLOPEN
  CreateCameraProviderHwl_t create_hwl;

  ALOGI("%s:Loading %s library", __FUNCTION__, kCameraHwlLib.data());
  hwl_lib_handle_ = dlopen(kCameraHwlLib.data(), RTLD_NOW);

  if (hwl_lib_handle_ == nullptr) {
    ALOGE("HWL loading %s failed due to error: %s", kCameraHwlLib.data(),
          dlerror());
    return NO_INIT;
  }

  create_hwl = (CreateCameraProviderHwl_t)dlsym(hwl_lib_handle_,
                                                "CreateCameraProviderHwl");
  if (create_hwl == nullptr) {
    ALOGE("%s: dlsym failed (%s).", __FUNCTION__, kCameraHwlLib.data());
    dlclose(hwl_lib_handle_);
    hwl_lib_handle_ = nullptr;
    return NO_INIT;
  }

  // Create the HWL camera provider
  camera_provider_hwl->reset(create_hwl());
#else
  camera_provider_hwl->reset(CreateCameraProviderHwl());
#endif

  if (*camera_provider_hwl == nullptr) {
    ALOGE("Error! Creating CameraProviderHwl failed");
    return UNKNOWN_ERROR;
  }

  return OK;
}

status_t CameraProvider::NotifyDeviceStateChange(
    google_camera_hal::DeviceState device_state) {
  if (camera_provider_hwl_ == nullptr) {
    ALOGE("%s: null provider hwl", __FUNCTION__);
    return NO_INIT;
  }
  camera_provider_hwl_->NotifyDeviceStateChange(device_state);
  return OK;
}
}  // namespace google_camera_hal
}  // namespace android

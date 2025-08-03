/*
 * Copyright (C) 2015 The Android Open Source Project
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

#pragma once

#include <cstdint>
#include <map>
#include <tuple>

#include "DrmConnector.h"
#include "DrmCrtc.h"
#include "DrmEncoder.h"
#include "utils/fd.h"

namespace android {

class DrmFbImporter;
class DrmPlane;
class ResourceManager;

class DrmDevice {
 public:
  ~DrmDevice() = default;

  static auto CreateInstance(std::string const &path, ResourceManager *res_man)
      -> std::unique_ptr<DrmDevice>;

  auto &GetFd() const {
    return fd_;
  }

  auto &GetResMan() {
    return *res_man_;
  }

  auto GetConnectors() -> const std::vector<std::unique_ptr<DrmConnector>> &;
  auto GetPlanes() -> const std::vector<std::unique_ptr<DrmPlane>> &;
  auto GetCrtcs() -> const std::vector<std::unique_ptr<DrmCrtc>> &;
  auto GetEncoders() -> const std::vector<std::unique_ptr<DrmEncoder>> &;

  auto GetMinResolution() const {
    return min_resolution_;
  }

  auto GetMaxResolution() const {
    return max_resolution_;
  }

  std::string GetName() const;

  auto RegisterUserPropertyBlob(void *data, size_t length) const
      -> DrmModeUserPropertyBlobUnique;

  auto HasAddFb2ModifiersSupport() const {
    return HasAddFb2ModifiersSupport_;
  }

  auto &GetDrmFbImporter() {
    return *drm_fb_importer_;
  }

  auto FindCrtcById(uint32_t id) const -> DrmCrtc * {
    for (const auto &crtc : crtcs_) {
      if (crtc->GetId() == id) {
        return crtc.get();
      }
    };

    return nullptr;
  }

  auto FindEncoderById(uint32_t id) const -> DrmEncoder * {
    for (const auto &enc : encoders_) {
      if (enc->GetId() == id) {
        return enc.get();
      }
    };

    return nullptr;
  }

  int GetProperty(uint32_t obj_id, uint32_t obj_type, const char *prop_name,
                  DrmProperty *property) const;

 private:
  explicit DrmDevice(ResourceManager *res_man);
  auto Init(const char *path) -> int;

  static auto IsKMSDev(const char *path) -> bool;

  SharedFd fd_;

  std::vector<std::unique_ptr<DrmConnector>> connectors_;
  std::vector<std::unique_ptr<DrmConnector>> writeback_connectors_;
  std::vector<std::unique_ptr<DrmEncoder>> encoders_;
  std::vector<std::unique_ptr<DrmCrtc>> crtcs_;
  std::vector<std::unique_ptr<DrmPlane>> planes_;

  std::pair<uint32_t, uint32_t> min_resolution_;
  std::pair<uint32_t, uint32_t> max_resolution_;

  bool HasAddFb2ModifiersSupport_{};

  std::unique_ptr<DrmFbImporter> drm_fb_importer_;

  ResourceManager *const res_man_;
};
}  // namespace android

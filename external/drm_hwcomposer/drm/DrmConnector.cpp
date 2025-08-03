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

#define LOG_TAG "hwc-drm-connector"

#include "DrmConnector.h"

#include <xf86drmMode.h>

#include <array>
#include <cerrno>
#include <cstdint>
#include <sstream>

#include "DrmDevice.h"
#include "utils/log.h"

#ifndef DRM_MODE_CONNECTOR_SPI
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define DRM_MODE_CONNECTOR_SPI 19
#endif

#ifndef DRM_MODE_CONNECTOR_USB
// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
#define DRM_MODE_CONNECTOR_USB 20
#endif

namespace android {

constexpr size_t kTypesCount = 21;

static bool GetOptionalConnectorProperty(const DrmDevice &dev,
                                         const DrmConnector &connector,
                                         const char *prop_name,
                                         DrmProperty *property) {
  return dev.GetProperty(connector.GetId(), DRM_MODE_OBJECT_CONNECTOR,
                         prop_name, property) == 0;
}

static bool GetConnectorProperty(const DrmDevice &dev,
                                 const DrmConnector &connector,
                                 const char *prop_name, DrmProperty *property) {
  if (!GetOptionalConnectorProperty(dev, connector, prop_name, property)) {
    ALOGE("Could not get %s property\n", prop_name);
    return false;
  }
  return true;
}

auto DrmConnector::CreateInstance(DrmDevice &dev, uint32_t connector_id,
                                  uint32_t index)
    -> std::unique_ptr<DrmConnector> {
  auto conn = MakeDrmModeConnectorUnique(*dev.GetFd(), connector_id);
  if (!conn) {
    ALOGE("Failed to get connector %d", connector_id);
    return {};
  }

  auto c = std::unique_ptr<DrmConnector>(
      new DrmConnector(std::move(conn), &dev, index));

  if (!GetConnectorProperty(dev, *c, "DPMS", &c->dpms_property_) ||
      !GetConnectorProperty(dev, *c, "CRTC_ID", &c->crtc_id_property_)) {
    return {};
  }

  c->UpdateEdidProperty();

  if (c->IsWriteback() &&
      (!GetConnectorProperty(dev, *c, "WRITEBACK_PIXEL_FORMATS",
                             &c->writeback_pixel_formats_) ||
       !GetConnectorProperty(dev, *c, "WRITEBACK_FB_ID",
                             &c->writeback_fb_id_) ||
       !GetConnectorProperty(dev, *c, "WRITEBACK_OUT_FENCE_PTR",
                             &c->writeback_out_fence_))) {
    return {};
  }

  return c;
}

int DrmConnector::UpdateEdidProperty() {
  return GetOptionalConnectorProperty(*drm_, *this, "EDID", &edid_property_)
             ? 0
             : -EINVAL;
}

auto DrmConnector::GetEdidBlob() -> DrmModePropertyBlobUnique {
  auto ret = UpdateEdidProperty();
  if (ret != 0) {
    return {};
  }

  auto blob_id = GetEdidProperty().GetValue();
  if (!blob_id) {
    return {};
  }

  return MakeDrmModePropertyBlobUnique(*drm_->GetFd(), *blob_id);
}

bool DrmConnector::IsInternal() const {
  auto type = connector_->connector_type;
  return type == DRM_MODE_CONNECTOR_LVDS || type == DRM_MODE_CONNECTOR_eDP ||
         type == DRM_MODE_CONNECTOR_DSI || type == DRM_MODE_CONNECTOR_VIRTUAL ||
         type == DRM_MODE_CONNECTOR_DPI || type == DRM_MODE_CONNECTOR_SPI;
}

bool DrmConnector::IsExternal() const {
  auto type = connector_->connector_type;
  return type == DRM_MODE_CONNECTOR_HDMIA ||
         type == DRM_MODE_CONNECTOR_DisplayPort ||
         type == DRM_MODE_CONNECTOR_DVID || type == DRM_MODE_CONNECTOR_DVII ||
         type == DRM_MODE_CONNECTOR_VGA || type == DRM_MODE_CONNECTOR_USB;
}

bool DrmConnector::IsWriteback() const {
#ifdef DRM_MODE_CONNECTOR_WRITEBACK
  return connector_->connector_type == DRM_MODE_CONNECTOR_WRITEBACK;
#else
  return false;
#endif
}

bool DrmConnector::IsValid() const {
  return IsInternal() || IsExternal() || IsWriteback();
}

std::string DrmConnector::GetName() const {
  constexpr std::array<const char *, kTypesCount> kNames =
      {"None",      "VGA",  "DVI-I",     "DVI-D",   "DVI-A", "Composite",
       "SVIDEO",    "LVDS", "Component", "DIN",     "DP",    "HDMI-A",
       "HDMI-B",    "TV",   "eDP",       "Virtual", "DSI",   "DPI",
       "Writeback", "SPI",  "USB"};

  if (connector_->connector_type < kTypesCount) {
    std::ostringstream name_buf;
    name_buf << kNames[connector_->connector_type] << "-"
             << connector_->connector_type_id;
    return name_buf.str();
  }

  ALOGE("Unknown type in connector %d, could not make his name", GetId());
  return "None";
}

int DrmConnector::UpdateModes() {
  auto conn = MakeDrmModeConnectorUnique(*drm_->GetFd(), GetId());
  if (!conn) {
    ALOGE("Failed to get connector %d", GetId());
    return -ENODEV;
  }
  connector_ = std::move(conn);

  modes_.clear();
  for (int i = 0; i < connector_->count_modes; ++i) {
    bool exists = false;
    for (const DrmMode &mode : modes_) {
      if (mode == connector_->modes[i]) {
        exists = true;
        break;
      }
    }

    if (!exists) {
      modes_.emplace_back(&connector_->modes[i]);
    }
  }

  return 0;
}

}  // namespace android

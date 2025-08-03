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

#include "drmconnector.h"

#include <cutils/properties.h>
#include <errno.h>
#include <inttypes.h>
#include <log/log.h>
#include <stdint.h>
#include <xf86drmMode.h>

#include <array>
#include <sstream>

#include "drmdevice.h"

#ifndef DRM_MODE_CONNECTOR_WRITEBACK
#define DRM_MODE_CONNECTOR_WRITEBACK 18
#endif

namespace android {

constexpr size_t TYPES_COUNT = 18;

DrmConnector::DrmConnector(DrmDevice *drm, drmModeConnectorPtr c,
                           DrmEncoder *current_encoder,
                           std::vector<DrmEncoder *> &possible_encoders)
    : drm_(drm),
      id_(c->connector_id),
      encoder_(current_encoder),
      display_(-1),
      type_(c->connector_type),
      type_id_(c->connector_type_id),
      state_(c->connection),
      mm_width_(c->mmWidth),
      mm_height_(c->mmHeight),
      possible_encoders_(possible_encoders) {
}

int DrmConnector::Init() {
  int ret = drm_->GetConnectorProperty(*this, "DPMS", &dpms_property_);
  if (ret) {
    ALOGE("Could not get DPMS property\n");
    return ret;
  }
  ret = drm_->GetConnectorProperty(*this, "CRTC_ID", &crtc_id_property_);
  if (ret) {
    ALOGE("Could not get CRTC_ID property\n");
    return ret;
  }
  ret = drm_->GetConnectorProperty(*this, "EDID", &edid_property_);
  if (ret) {
    ALOGW("Could not get EDID property\n");
  }
  if (writeback()) {
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_PIXEL_FORMATS",
                                     &writeback_pixel_formats_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_PIXEL_FORMATS connector_id = %d\n", id_);
      return ret;
    }
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_FB_ID",
                                     &writeback_fb_id_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_FB_ID connector_id = %d\n", id_);
      return ret;
    }
    ret = drm_->GetConnectorProperty(*this, "WRITEBACK_OUT_FENCE_PTR",
                                     &writeback_out_fence_);
    if (ret) {
      ALOGE("Could not get WRITEBACK_OUT_FENCE_PTR connector_id = %d\n", id_);
      return ret;
    }
  }

  ret = drm_->GetConnectorProperty(*this, "max_luminance", &max_luminance_);
  if (ret) {
    ALOGE("Could not get max_luminance property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "max_avg_luminance", &max_avg_luminance_);
  if (ret) {
    ALOGE("Could not get max_avg_luminance property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "min_luminance", &min_luminance_);
  if (ret) {
    ALOGE("Could not get min_luminance property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "hdr_formats", &hdr_formats_);
  if (ret) {
    ALOGE("Could not get hdr_formats property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "panel orientation", &orientation_);
  if (ret) {
    ALOGE("Could not get orientation property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "lp_mode", &lp_mode_property_);
  if (!ret) {
      UpdateLpMode();
  } else {
      ALOGE("Could not get lp_mode property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "brightness_capability", &brightness_cap_);
  if (ret) {
      ALOGE("Could not get brightness_capability property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "brightness_level", &brightness_level_);
  if (ret) {
      ALOGE("Could not get brightness_level property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "hbm_mode", &hbm_mode_);
  if (ret) {
      ALOGE("Could not get hbm_mode property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "dimming_on", &dimming_on_);
  if (ret) {
      ALOGE("Could not get dimming_on property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "local_hbm_mode", &lhbm_on_);
  if (ret) {
      ALOGE("Could not get local_hbm_mode property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "mipi_sync", &mipi_sync_);
  if (ret) {
      ALOGE("Could not get mipi_sync property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "panel_idle_support", &panel_idle_support_);
  if (ret) {
      ALOGE("Could not get panel_idle_support property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "rr_switch_duration", &rr_switch_duration_);
  if (ret) {
      ALOGE("Could not get rr_switch_duration property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "operation_rate", &operation_rate_);
  if (ret) {
    ALOGE("Could not get operation_rate property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "refresh_on_lp", &refresh_on_lp_);
  if (ret) {
    ALOGE("Could not get refresh_on_lp property\n");
  }

  ret = drm_->GetConnectorProperty(*this, "Content Protection", &content_protection_);
  if (ret) {
    ALOGE("Could not get Content Protection property\n");
  }

  properties_.push_back(&dpms_property_);
  properties_.push_back(&crtc_id_property_);
  properties_.push_back(&edid_property_);
  if (writeback()) {
      properties_.push_back(&writeback_pixel_formats_);
      properties_.push_back(&writeback_fb_id_);
      properties_.push_back(&writeback_out_fence_);
  }
  properties_.push_back(&max_luminance_);
  properties_.push_back(&max_avg_luminance_);
  properties_.push_back(&min_luminance_);
  properties_.push_back(&hdr_formats_);
  properties_.push_back(&orientation_);
  properties_.push_back(&lp_mode_property_);
  properties_.push_back(&brightness_cap_);
  properties_.push_back(&brightness_level_);
  properties_.push_back(&hbm_mode_);
  properties_.push_back(&dimming_on_);
  properties_.push_back(&lhbm_on_);
  properties_.push_back(&mipi_sync_);
  properties_.push_back(&panel_idle_support_);
  properties_.push_back(&rr_switch_duration_);
  properties_.push_back(&operation_rate_);
  properties_.push_back(&refresh_on_lp_);
  properties_.push_back(&content_protection_);

  return 0;
}

uint32_t DrmConnector::id() const {
  return id_;
}

int DrmConnector::display() const {
  return display_;
}

void DrmConnector::set_display(int display) {
  display_ = display;
}

bool DrmConnector::internal() const {
  return type_ == DRM_MODE_CONNECTOR_LVDS || type_ == DRM_MODE_CONNECTOR_eDP ||
         type_ == DRM_MODE_CONNECTOR_DSI ||
         type_ == DRM_MODE_CONNECTOR_VIRTUAL || type_ == DRM_MODE_CONNECTOR_DPI;
}

bool DrmConnector::external() const {
  return type_ == DRM_MODE_CONNECTOR_HDMIA ||
         type_ == DRM_MODE_CONNECTOR_DisplayPort ||
         type_ == DRM_MODE_CONNECTOR_DVID || type_ == DRM_MODE_CONNECTOR_DVII ||
         type_ == DRM_MODE_CONNECTOR_VGA;
}

bool DrmConnector::writeback() const {
#ifdef DRM_MODE_CONNECTOR_WRITEBACK
  return type_ == DRM_MODE_CONNECTOR_WRITEBACK;
#else
  return false;
#endif
}

bool DrmConnector::valid_type() const {
  return internal() || external() || writeback();
}

std::string DrmConnector::name() const {
  constexpr std::array<const char *, TYPES_COUNT> names =
      {"None",   "VGA",  "DVI-I",     "DVI-D",   "DVI-A", "Composite",
       "SVIDEO", "LVDS", "Component", "DIN",     "DP",    "HDMI-A",
       "HDMI-B", "TV",   "eDP",       "Virtual", "DSI",   "DPI"};

  if (type_ < TYPES_COUNT) {
    std::ostringstream name_buf;
    name_buf << names[type_] << "-" << type_id_;
    return name_buf.str();
  } else {
    ALOGE("Unknown type in connector %d, could not make his name", id_);
    return "None";
  }
}

int DrmConnector::UpdateModes(bool is_vrr_mode) {
  std::lock_guard<std::recursive_mutex> lock(modes_lock_);

  int fd = drm_->fd();

  drmModeConnectorPtr c = drmModeGetConnector(fd, id_);
  if (!c) {
    ALOGE("Failed to get connector %d", id_);
    return -ENODEV;
  }

  if (state_ == DRM_MODE_CONNECTED &&
      c->connection == DRM_MODE_CONNECTED && modes_.size() > 0) {
    // no need to update modes
    return 0;
  }

  if (state_ == DRM_MODE_DISCONNECTED &&
      c->connection == DRM_MODE_DISCONNECTED && modes_.size() == 0) {
    // no need to update modes
    return 0;
  }

  state_ = c->connection;

  // Update mm_width_ and mm_height_ for xdpi/ydpi calculations
  mm_width_ = c->mmWidth;
  mm_height_ = c->mmHeight;

  bool preferred_mode_found = false;
  std::vector<DrmMode> new_modes;
  for (int i = 0; i < c->count_modes; ++i) {
    bool exists = false;
    for (const DrmMode &mode : modes_) {
      if (mode == c->modes[i]) {
        new_modes.push_back(mode);
        exists = true;
        break;
      }
    }
    if (!exists) {
      // Remove modes that mismatch with the VRR setting..
      if (is_vrr_mode != ((c->modes[i].type & DRM_MODE_TYPE_VRR) != 0)) {
        continue;
      }
      DrmMode m(&c->modes[i]);
      m.set_id(drm_->next_mode_id());
      new_modes.push_back(m);
    }
    // Use only the first DRM_MODE_TYPE_PREFERRED mode found
    if (!preferred_mode_found &&
        (new_modes.back().type() & DRM_MODE_TYPE_PREFERRED)) {
      preferred_mode_id_ = new_modes.back().id();
      preferred_mode_found = true;
    }
  }
  modes_.swap(new_modes);
  if (!preferred_mode_found && modes_.size() != 0) {
    preferred_mode_id_ = modes_[0].id();
  }
  return 1;
}

int DrmConnector::UpdateEdidProperty() {
  return drm_->UpdateConnectorProperty(*this, &edid_property_);
}

int DrmConnector::UpdateLuminanceAndHdrProperties() {
  int res = 0;

  res = drm_->UpdateConnectorProperty(*this, &max_luminance_);
  if (res)
    return res;
  res = drm_->UpdateConnectorProperty(*this, &max_avg_luminance_);
  if (res)
    return res;
  res = drm_->UpdateConnectorProperty(*this, &min_luminance_);
  if (res)
    return res;
  res = drm_->UpdateConnectorProperty(*this, &hdr_formats_);
  if (res)
    return res;
  return 0;
}

const DrmMode &DrmConnector::active_mode() const {
  return active_mode_;
}

void DrmConnector::set_active_mode(const DrmMode &mode) {
  active_mode_ = mode;
}

const DrmProperty &DrmConnector::dpms_property() const {
  return dpms_property_;
}

const DrmProperty &DrmConnector::crtc_id_property() const {
  return crtc_id_property_;
}

const DrmProperty &DrmConnector::edid_property() const {
  return edid_property_;
}

const DrmProperty &DrmConnector::writeback_pixel_formats() const {
  return writeback_pixel_formats_;
}

const DrmProperty &DrmConnector::writeback_fb_id() const {
  return writeback_fb_id_;
}

const DrmProperty &DrmConnector::writeback_out_fence() const {
  return writeback_out_fence_;
}

const DrmProperty &DrmConnector::max_luminance() const {
  return max_luminance_;
}

const DrmProperty &DrmConnector::max_avg_luminance() const {
  return max_avg_luminance_;
}

const DrmProperty &DrmConnector::min_luminance() const {
  return min_luminance_;
}

const DrmProperty &DrmConnector::brightness_cap() const {
    return brightness_cap_;
}

const DrmProperty &DrmConnector::brightness_level() const {
    return brightness_level_;
}

const DrmProperty &DrmConnector::hbm_mode() const {
    return hbm_mode_;
}

const DrmProperty &DrmConnector::dimming_on() const {
    return dimming_on_;
}

const DrmProperty &DrmConnector::lhbm_on() const {
    return lhbm_on_;
}

const DrmProperty &DrmConnector::mipi_sync() const {
    return mipi_sync_;
}

const DrmProperty &DrmConnector::hdr_formats() const {
  return hdr_formats_;
}

const DrmProperty &DrmConnector::orientation() const {
  return orientation_;
}

const DrmMode &DrmConnector::lp_mode() const {
    return lp_mode_;
}

const DrmProperty &DrmConnector::operation_rate() const {
    return operation_rate_;
}

const DrmProperty &DrmConnector::refresh_on_lp() const {
    return refresh_on_lp_;
}

int DrmConnector::UpdateLpMode() {
    auto [ret, blobId] = lp_mode_property_.value();
    if (ret) {
        ALOGE("Fail to get blob id for lp mode");
        return ret;
    }
    drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(drm_->fd(), blobId);
    if (!blob) {
        ALOGE("Fail to get blob for lp mode(%" PRId64 ")", blobId);
        return -ENOENT;
    }

    auto modeInfoPtr = static_cast<drmModeModeInfoPtr>(blob->data);
    lp_mode_ = DrmMode(modeInfoPtr);
    drmModeFreePropertyBlob(blob);

    ALOGD("Updating LP mode to: %s", lp_mode_.name().c_str());

    return 0;
}

int DrmConnector::ResetLpMode() {
    int ret = drm_->UpdateConnectorProperty(*this, &lp_mode_property_);

    if (ret) {
        return ret;
    }

    UpdateLpMode();

    return 0;
}

const DrmProperty &DrmConnector::panel_idle_support() const {
    return panel_idle_support_;
}

const DrmProperty &DrmConnector::rr_switch_duration() const {
    return rr_switch_duration_;
}

const DrmProperty &DrmConnector::content_protection() const {
    return content_protection_;
}

DrmEncoder *DrmConnector::encoder() const {
  return encoder_;
}

void DrmConnector::set_encoder(DrmEncoder *encoder) {
  encoder_ = encoder;
}

drmModeConnection DrmConnector::state() const {
  return state_;
}

uint32_t DrmConnector::mm_width() const {
  return mm_width_;
}

uint32_t DrmConnector::mm_height() const {
  return mm_height_;
}
}  // namespace android

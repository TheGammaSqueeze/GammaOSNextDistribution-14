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

#define LOG_TAG "hwc-drm-crtc"

#include "drmcrtc.h"
#include "drmdevice.h"

#include <stdint.h>
#include <xf86drmMode.h>

#include <log/log.h>

namespace android {

DrmCrtc::DrmCrtc(DrmDevice *drm, drmModeCrtcPtr c, unsigned pipe)
    : drm_(drm), id_(c->crtc_id), pipe_(pipe), mode_(&c->mode) {
        displays_.clear();
}

int DrmCrtc::Init() {
  int ret = drm_->GetCrtcProperty(*this, "ACTIVE", &active_property_);
  if (ret) {
    ALOGE("Failed to get ACTIVE property");
    return ret;
  }

  ret = drm_->GetCrtcProperty(*this, "MODE_ID", &mode_property_);
  if (ret) {
    ALOGE("Failed to get MODE_ID property");
    return ret;
  }

  ret = drm_->GetCrtcProperty(*this, "OUT_FENCE_PTR", &out_fence_ptr_property_);
  if (ret) {
    ALOGE("Failed to get OUT_FENCE_PTR property");
    return ret;
  }

  if (drm_->GetCrtcProperty(*this, "partial_region", &partial_region_property_))
    ALOGI("Failed to get &partial_region_property");

  if (drm_->GetCrtcProperty(*this, "cgc_lut", &cgc_lut_property_))
    ALOGI("Failed to get cgc_lut property");
  if (drm_->GetCrtcProperty(*this, "cgc_lut_fd", &cgc_lut_fd_property_))
    ALOGI("Failed to get cgc_lut_fd property");
  if (drm_->GetCrtcProperty(*this, "DEGAMMA_LUT", &degamma_lut_property_))
    ALOGI("Failed to get &degamma_lut property");
  if (drm_->GetCrtcProperty(*this, "DEGAMMA_LUT_SIZE", &degamma_lut_size_property_))
    ALOGI("Failed to get &degamma_lut_size property");
  if (drm_->GetCrtcProperty(*this, "GAMMA_LUT", &gamma_lut_property_))
    ALOGI("Failed to get &gamma_lut property");
  if (drm_->GetCrtcProperty(*this, "GAMMA_LUT_SIZE", &gamma_lut_size_property_))
    ALOGI("Failed to get &gamma_lut_size property");
  if (drm_->GetCrtcProperty(*this, "linear_matrix", &linear_matrix_property_))
    ALOGI("Failed to get &linear_matrix property");
  if (drm_->GetCrtcProperty(*this, "gamma_matrix", &gamma_matrix_property_))
    ALOGI("Failed to get &gamma_matrix property");
  if (drm_->GetCrtcProperty(*this, "force_bpc", &force_bpc_property_))
    ALOGI("Failed to get &force_bpc_property_");
  if (drm_->GetCrtcProperty(*this, "disp_dither", &disp_dither_property_))
    ALOGI("Failed to get &disp_dither property");
  if (drm_->GetCrtcProperty(*this, "cgc_dither", &cgc_dither_property_))
    ALOGI("Failed to get &cgc_dither property");
  if (drm_->GetCrtcProperty(*this, "adjusted_vblank", &adjusted_vblank_property_))
    ALOGI("Failed to get &adjusted_vblank property");
  if (drm_->GetCrtcProperty(*this, "ppc", &ppc_property_))
    ALOGI("Failed to get &ppc property");
  if (drm_->GetCrtcProperty(*this, "max_disp_freq", &max_disp_freq_property_))
    ALOGI("Failed to get &max_disp_freq property");
  if (drm_->GetCrtcProperty(*this, "dqe_enabled", &dqe_enabled_property_))
    ALOGI("Failed to get &dqe_enabled_property property");
  if (drm_->GetCrtcProperty(*this, "color mode", &color_mode_property_))
    ALOGI("Failed to get color mode property");
  if (drm_->GetCrtcProperty(*this, "expected_present_time", &expected_present_time_property_))
      ALOGI("Failed to get expected_present_time property");
  if (drm_->GetCrtcProperty(*this, "rcd_plane_id", &rcd_plane_id_property_))
      ALOGI("Failed to get &rcd_plane_id property");

  /* Histogram Properties */
  if (drm_->GetCrtcProperty(*this, "histogram_roi", &histogram_roi_property_))
      ALOGI("Failed to get &histogram_roi property");
  if (drm_->GetCrtcProperty(*this, "histogram_weights", &histogram_weights_property_))
      ALOGI("Failed to get &histogram_weights property");
  if (drm_->GetCrtcProperty(*this, "histogram_threshold", &histogram_threshold_property_))
      ALOGI("Failed to get &histogram_threshold property");
  if (drm_->GetCrtcProperty(*this, "histogram_pos", &histogram_position_property_))
      ALOGI("Failed to get &histogram_position property");

  properties_.push_back(&active_property_);
  properties_.push_back(&mode_property_);
  properties_.push_back(&out_fence_ptr_property_);
  properties_.push_back(&cgc_lut_property_);
  properties_.push_back(&cgc_lut_fd_property_);
  properties_.push_back(&degamma_lut_property_);
  properties_.push_back(&degamma_lut_size_property_);
  properties_.push_back(&gamma_lut_property_);
  properties_.push_back(&gamma_lut_size_property_);
  properties_.push_back(&linear_matrix_property_);
  properties_.push_back(&gamma_matrix_property_);
  properties_.push_back(&partial_region_property_);
  properties_.push_back(&force_bpc_property_);
  properties_.push_back(&disp_dither_property_);
  properties_.push_back(&cgc_dither_property_);
  properties_.push_back(&adjusted_vblank_property_);
  properties_.push_back(&ppc_property_);
  properties_.push_back(&max_disp_freq_property_);
  properties_.push_back(&dqe_enabled_property_);
  properties_.push_back(&color_mode_property_);
  properties_.push_back(&expected_present_time_property_);
  properties_.push_back(&rcd_plane_id_property_);

  /* Histogram Properties */
  properties_.push_back(&histogram_roi_property_);
  properties_.push_back(&histogram_weights_property_);
  properties_.push_back(&histogram_threshold_property_);
  properties_.push_back(&histogram_position_property_);

  /* Histogram Properties :: multichannel */
  // TODO: b/295786065 - Get available channels from crtc property.
  for (int i = 0; i < histogram_channels_max; i++) {
      char pname[64];

      sprintf(pname, "histogram_%d", i);
      if (!drm_->GetCrtcProperty(*this, pname, &histogram_channel_property_[i])) {
        ALOGD("histogram_channel #%d property found", i);
        properties_.push_back(&histogram_channel_property_[i]);
      } else {
        ALOGD("histogram_channel #%d property not found, break", i);
        break;
      }
  }
  return 0;
}

uint32_t DrmCrtc::id() const {
  return id_;
}

unsigned DrmCrtc::pipe() const {
  return pipe_;
}

const std::vector<int>& DrmCrtc::displays() const {
  return displays_;
}

bool DrmCrtc::has_display(int display) const {
  auto it = find_if(displays_.begin(), displays_.end(),
      [&display](auto disp) {
      return (disp == display);
      });
  if (it != displays_.end())
    return true;
  return false;
}

void DrmCrtc::set_display(int display) {
  displays_.push_back(display);
}

bool DrmCrtc::can_bind(int display) const {
  if (displays_.size() == 0)
    return true;
  return has_display(display);
}

const DrmProperty &DrmCrtc::active_property() const {
  return active_property_;
}

const DrmProperty &DrmCrtc::mode_property() const {
  return mode_property_;
}

const DrmProperty &DrmCrtc::out_fence_ptr_property() const {
  return out_fence_ptr_property_;
}

const DrmProperty &DrmCrtc::cgc_lut_property() const {
    return cgc_lut_property_;
}

const DrmProperty &DrmCrtc::cgc_lut_fd_property() const {
    return cgc_lut_fd_property_;
}

const DrmProperty &DrmCrtc::degamma_lut_property() const {
    return degamma_lut_property_;
}

const DrmProperty &DrmCrtc::degamma_lut_size_property() const {
    return degamma_lut_size_property_;
}

const DrmProperty &DrmCrtc::gamma_lut_property() const {
    return gamma_lut_property_;
}

const DrmProperty &DrmCrtc::gamma_lut_size_property() const {
    return gamma_lut_size_property_;
}

const DrmProperty &DrmCrtc::linear_matrix_property() const {
    return linear_matrix_property_;
}

const DrmProperty &DrmCrtc::gamma_matrix_property() const {
    return gamma_matrix_property_;
}

const DrmProperty &DrmCrtc::partial_region_property() const {
    return partial_region_property_;
}

const DrmProperty &DrmCrtc::force_bpc_property() const {
    return force_bpc_property_;
}

const DrmProperty &DrmCrtc::disp_dither_property() const {
    return disp_dither_property_;
}

const DrmProperty &DrmCrtc::cgc_dither_property() const {
    return cgc_dither_property_;
}

DrmProperty &DrmCrtc::adjusted_vblank_property() {
    return adjusted_vblank_property_;
}

const DrmProperty &DrmCrtc::ppc_property() const {
    return ppc_property_;
}

const DrmProperty &DrmCrtc::max_disp_freq_property() const {
    return max_disp_freq_property_;
}

const DrmProperty &DrmCrtc::dqe_enabled_property() const {
    return dqe_enabled_property_;
}

const DrmProperty &DrmCrtc::color_mode_property() const {
    return color_mode_property_;
}

const DrmProperty &DrmCrtc::expected_present_time_property() const {
    return expected_present_time_property_;
}

/* Histogram Properties */
const DrmProperty &DrmCrtc::histogram_roi_property() const {
    return histogram_roi_property_;
}

const DrmProperty &DrmCrtc::histogram_weights_property() const {
    return histogram_weights_property_;
}

const DrmProperty &DrmCrtc::histogram_threshold_property() const {
    return histogram_threshold_property_;
}

const DrmProperty &DrmCrtc::histogram_position_property() const {
    return histogram_position_property_;
}

const DrmProperty &DrmCrtc::rcd_plane_id_property() const {
    return rcd_plane_id_property_;
}

/* Histogram Properties */
const DrmProperty &DrmCrtc::histogram_channel_property(uint8_t channelId) const {
    if (histogram_channels_max <= channelId) {
        ALOGE("Invalid histogram channel number %u\n", channelId);
        return histogram_channel_property_[0];
    }
    return histogram_channel_property_[channelId];
}

}  // namespace android

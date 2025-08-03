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

#define LOG_TAG "hwc-drm-plane"

#include "drmplane.h"

#include <errno.h>
#include <log/log.h>
#include <stdint.h>
#include <xf86drmMode.h>

#include <cinttypes>

#include "drmdevice.h"

namespace android {

DrmPlane::DrmPlane(DrmDevice *drm, drmModePlanePtr p)
    : drm_(drm), id_(p->plane_id), possible_crtc_mask_(p->possible_crtcs) {
  for (uint32_t i = 0; i < p->count_formats; i++) {
    formats_.push_back(p->formats[i]);
  }
}

int DrmPlane::Init() {
  DrmProperty p;

  int ret = drm_->GetPlaneProperty(*this, "type", &p);
  if (ret) {
    ALOGE("Could not get plane type property");
    return ret;
  }

  uint64_t type;
  std::tie(ret, type) = p.value();
  if (ret) {
    ALOGE("Failed to get plane type property value");
    return ret;
  }
  switch (type) {
    case DRM_PLANE_TYPE_OVERLAY:
    case DRM_PLANE_TYPE_PRIMARY:
    case DRM_PLANE_TYPE_CURSOR:
      type_ = (uint32_t)type;
      break;
    default:
      ALOGE("Invalid plane type %" PRIu64, type);
      return -EINVAL;
  }

  ret = drm_->GetPlaneProperty(*this, "CRTC_ID", &crtc_property_);
  if (ret) {
    ALOGE("Could not get CRTC_ID property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "FB_ID", &fb_property_);
  if (ret) {
    ALOGE("Could not get FB_ID property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "CRTC_X", &crtc_x_property_);
  if (ret) {
    ALOGE("Could not get CRTC_X property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "CRTC_Y", &crtc_y_property_);
  if (ret) {
    ALOGE("Could not get CRTC_Y property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "CRTC_W", &crtc_w_property_);
  if (ret) {
    ALOGE("Could not get CRTC_W property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "CRTC_H", &crtc_h_property_);
  if (ret) {
    ALOGE("Could not get CRTC_H property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "SRC_X", &src_x_property_);
  if (ret) {
    ALOGE("Could not get SRC_X property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "SRC_Y", &src_y_property_);
  if (ret) {
    ALOGE("Could not get SRC_Y property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "SRC_W", &src_w_property_);
  if (ret) {
    ALOGE("Could not get SRC_W property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "SRC_H", &src_h_property_);
  if (ret) {
    ALOGE("Could not get SRC_H property");
    return ret;
  }

  ret = drm_->GetPlaneProperty(*this, "zpos", &zpos_property_);
  if (ret)
    ALOGE("Could not get zpos property for plane %u", id());

  ret = drm_->GetPlaneProperty(*this, "rotation", &rotation_property_);
  if (ret)
    ALOGE("Could not get rotation property");

  ret = drm_->GetPlaneProperty(*this, "alpha", &alpha_property_);
  if (ret)
    ALOGI("Could not get alpha property");

  ret = drm_->GetPlaneProperty(*this, "pixel blend mode", &blend_property_);
  if (ret)
    ALOGI("Could not get pixel blend mode property");

  ret = drm_->GetPlaneProperty(*this, "IN_FENCE_FD", &in_fence_fd_property_);
  if (ret)
    ALOGI("Could not get IN_FENCE_FD property");

  ret = drm_->GetPlaneProperty(*this, "standard", &standard_);
  if (ret)
    ALOGI("Could not get standard property");

  ret = drm_->GetPlaneProperty(*this, "transfer", &transfer_);
  if (ret)
    ALOGI("Could not get transfer property");

  ret = drm_->GetPlaneProperty(*this, "range", &range_);
  if (ret)
    ALOGI("Could not get range property");

  ret = drm_->GetPlaneProperty(*this, "max_luminance", &max_luminance_);
  if (ret)
    ALOGI("Could not get max_luminance property");

  ret = drm_->GetPlaneProperty(*this, "min_luminance", &min_luminance_);
  if (ret)
    ALOGI("Could not get min_luminance property");

  ret = drm_->GetPlaneProperty(*this, "hw restrictions", &hw_restrictions_);
  if (ret)
    ALOGI("Could not get hw restrictions property");

  if (drm_->GetPlaneProperty(*this, "eotf_lut", &eotf_lut_))
    ALOGI("Could not get eotf_lut property");
  if (drm_->GetPlaneProperty(*this, "oetf_lut", &oetf_lut_))
    ALOGI("Could not get oetf_lut property");
  if (drm_->GetPlaneProperty(*this, "gammut_matrix", &gammut_matrix_))
    ALOGI("Could not get gammut_matrix property");
  if (drm_->GetPlaneProperty(*this, "tone_mapping", &tone_mapping_))
    ALOGI("Could not get tone_mapping property");
  if (drm_->GetPlaneProperty(*this, "colormap", &colormap_))
    ALOGI("Could not get colormap property");
  if (drm_->GetPlaneProperty(*this, "block", &block_))
    ALOGI("Could not get block property");

  properties_.push_back(&crtc_property_);
  properties_.push_back(&fb_property_);
  properties_.push_back(&crtc_x_property_);
  properties_.push_back(&crtc_y_property_);
  properties_.push_back(&crtc_w_property_);
  properties_.push_back(&crtc_h_property_);
  properties_.push_back(&src_x_property_);
  properties_.push_back(&src_y_property_);
  properties_.push_back(&src_w_property_);
  properties_.push_back(&src_h_property_);
  properties_.push_back(&zpos_property_);
  properties_.push_back(&rotation_property_);
  properties_.push_back(&alpha_property_);
  properties_.push_back(&blend_property_);
  properties_.push_back(&in_fence_fd_property_);
  properties_.push_back(&standard_);
  properties_.push_back(&transfer_);
  properties_.push_back(&range_);
  properties_.push_back(&max_luminance_);
  properties_.push_back(&min_luminance_);
  properties_.push_back(&hw_restrictions_);
  properties_.push_back(&eotf_lut_);
  properties_.push_back(&oetf_lut_);
  properties_.push_back(&gammut_matrix_);
  properties_.push_back(&tone_mapping_);
  properties_.push_back(&colormap_);
  properties_.push_back(&block_);

  return 0;
}

uint32_t DrmPlane::id() const {
  return id_;
}

bool DrmPlane::GetCrtcSupported(const DrmCrtc &crtc) const {
  return !!((1 << crtc.pipe()) & possible_crtc_mask_);
}

bool DrmPlane::isFormatSupported(const uint32_t format) const {
  return std::find(formats_.begin(), formats_.end(), format) != formats_.end();
}

uint32_t DrmPlane::getNumFormatSupported() const {
  return formats_.size();
}

uint32_t DrmPlane::type() const {
  return type_;
}

const DrmProperty &DrmPlane::crtc_property() const {
  return crtc_property_;
}

const DrmProperty &DrmPlane::fb_property() const {
  return fb_property_;
}

const DrmProperty &DrmPlane::crtc_x_property() const {
  return crtc_x_property_;
}

const DrmProperty &DrmPlane::crtc_y_property() const {
  return crtc_y_property_;
}

const DrmProperty &DrmPlane::crtc_w_property() const {
  return crtc_w_property_;
}

const DrmProperty &DrmPlane::crtc_h_property() const {
  return crtc_h_property_;
}

const DrmProperty &DrmPlane::src_x_property() const {
  return src_x_property_;
}

const DrmProperty &DrmPlane::src_y_property() const {
  return src_y_property_;
}

const DrmProperty &DrmPlane::src_w_property() const {
  return src_w_property_;
}

const DrmProperty &DrmPlane::src_h_property() const {
  return src_h_property_;
}

const DrmProperty &DrmPlane::zpos_property() const {
  return zpos_property_;
}

const DrmProperty &DrmPlane::rotation_property() const {
  return rotation_property_;
}

const DrmProperty &DrmPlane::alpha_property() const {
  return alpha_property_;
}

const DrmProperty &DrmPlane::blend_property() const {
  return blend_property_;
}

const DrmProperty &DrmPlane::in_fence_fd_property() const {
  return in_fence_fd_property_;
}

const DrmProperty &DrmPlane::standard_property() const {
  return standard_;
}

const DrmProperty &DrmPlane::transfer_property() const {
  return transfer_;
}

const DrmProperty &DrmPlane::range_property() const {
  return range_;
}

const DrmProperty &DrmPlane::max_luminance_property() const {
  return max_luminance_;
}

const DrmProperty &DrmPlane::min_luminance_property() const {
  return min_luminance_;
}

const DrmProperty &DrmPlane::hw_restrictions_property() const {
  return hw_restrictions_;
}

const DrmProperty &DrmPlane::eotf_lut_property() const {
  return eotf_lut_;
}

const DrmProperty &DrmPlane::oetf_lut_property() const {
  return oetf_lut_;
}

const DrmProperty &DrmPlane::gammut_matrix_property() const {
  return gammut_matrix_;
}

const DrmProperty &DrmPlane::tone_mapping_property() const {
  return tone_mapping_;
}

const DrmProperty &DrmPlane::colormap_property() const {
  return colormap_;
}

const DrmProperty &DrmPlane::block_property() const {
  return block_;
}

} // namespace android

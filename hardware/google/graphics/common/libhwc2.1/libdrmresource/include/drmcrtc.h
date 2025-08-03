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

#ifndef ANDROID_DRM_CRTC_H_
#define ANDROID_DRM_CRTC_H_

#include <stdint.h>
#include <xf86drmMode.h>

#include <array>

#include "drmmode.h"
#include "drmproperty.h"

namespace android {

class DrmDevice;

class DrmCrtc {
 public:
  DrmCrtc(DrmDevice *drm, drmModeCrtcPtr c, unsigned pipe);
  DrmCrtc(const DrmCrtc &) = delete;
  DrmCrtc &operator=(const DrmCrtc &) = delete;

  int Init();

  uint32_t id() const;
  unsigned pipe() const;

  const std::vector<int>& displays() const;
  bool has_display(const int display) const;
  void set_display(int display);

  bool can_bind(int display) const;

  const DrmProperty &active_property() const;
  const DrmProperty &mode_property() const;
  const DrmProperty &out_fence_ptr_property() const;
  const DrmProperty &cgc_lut_property() const;
  const DrmProperty &cgc_lut_fd_property() const;
  const DrmProperty &degamma_lut_property() const;
  const DrmProperty &degamma_lut_size_property() const;
  const DrmProperty &gamma_lut_property() const;
  const DrmProperty &gamma_lut_size_property() const;
  const DrmProperty &linear_matrix_property() const;
  const DrmProperty &gamma_matrix_property() const;
  const DrmProperty &partial_region_property() const;
  const DrmProperty &force_bpc_property() const;
  const DrmProperty &disp_dither_property() const;
  const DrmProperty &cgc_dither_property() const;
  DrmProperty &adjusted_vblank_property();
  const DrmProperty &ppc_property() const;
  const DrmProperty &max_disp_freq_property() const;
  const DrmProperty &dqe_enabled_property() const;
  const DrmProperty &color_mode_property() const;
  const DrmProperty &expected_present_time_property() const;
  const DrmProperty &rcd_plane_id_property() const;

  /* Histogram Properties */
  const DrmProperty &histogram_roi_property() const;
  const DrmProperty &histogram_weights_property() const;
  const DrmProperty &histogram_threshold_property() const;
  const DrmProperty &histogram_position_property() const;

  /* Histogram Properties:: multi channel */
  const DrmProperty &histogram_channel_property(uint8_t channelId) const;

  const std::vector<DrmProperty *> &properties() const {
      return properties_;
  }

 private:
  DrmDevice *drm_;

  uint32_t id_;
  unsigned pipe_;
  std::vector<int> displays_;

  DrmMode mode_;

  DrmProperty active_property_;
  DrmProperty mode_property_;
  DrmProperty out_fence_ptr_property_;
  DrmProperty cgc_lut_property_;
  DrmProperty cgc_lut_fd_property_;
  DrmProperty degamma_lut_property_;
  DrmProperty degamma_lut_size_property_;
  DrmProperty gamma_lut_property_;
  DrmProperty gamma_lut_size_property_;
  DrmProperty linear_matrix_property_;
  DrmProperty gamma_matrix_property_;
  DrmProperty partial_region_property_;
  DrmProperty force_bpc_property_;
  DrmProperty disp_dither_property_;
  DrmProperty cgc_dither_property_;
  DrmProperty adjusted_vblank_property_;
  DrmProperty ppc_property_;
  DrmProperty max_disp_freq_property_;
  DrmProperty dqe_enabled_property_;
  DrmProperty color_mode_property_;
  DrmProperty expected_present_time_property_;
  DrmProperty rcd_plane_id_property_;

  /* Histogram Properties */
  DrmProperty histogram_roi_property_;
  DrmProperty histogram_weights_property_;
  DrmProperty histogram_threshold_property_;
  DrmProperty histogram_position_property_;

  /* Histogram Properties :: multichannel support */
  // TODO: b/295786065 - Get available channels from crtc property.
  static const uint8_t histogram_channels_max = 4;
  std::array<DrmProperty, histogram_channels_max> histogram_channel_property_;

  /* all properties */
  std::vector<DrmProperty *> properties_;
};

}  // namespace android

#endif  // ANDROID_DRM_CRTC_H_

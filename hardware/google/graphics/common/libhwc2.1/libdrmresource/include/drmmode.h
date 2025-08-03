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

#ifndef ANDROID_DRM_MODE_H_
#define ANDROID_DRM_MODE_H_

#include <stdint.h>
#include <xf86drmMode.h>

#include <ratio>
#include <string>

// Alternative definitions(alias) of DRM modes and flags for VRR.
// The kernel contains corresponding defines that MUST align with those specified here..
#define DRM_MODE_TYPE_VRR DRM_MODE_TYPE_USERDEF
#define DRM_MODE_FLAG_NS DRM_MODE_FLAG_CLKDIV2
#define DRM_MODE_FLAG_TE_FREQ_X1 DRM_MODE_FLAG_PHSYNC
#define DRM_MODE_FLAG_TE_FREQ_X2 DRM_MODE_FLAG_NHSYNC
#define DRM_MODE_FLAG_TE_FREQ_X4 DRM_MODE_FLAG_PVSYNC

// BTS needs to take operation rate into account
#define DRM_MODE_FLAG_BTS_OP_RATE DRM_MODE_FLAG_NVSYNC

#define PANEL_REFRESH_CTRL_FI (1 << 0)
#define PANEL_REFRESH_CTRL_IDLE (1 << 1)

namespace android {

class DrmMode {
 public:
  DrmMode() = default;
  DrmMode(drmModeModeInfoPtr m);

  bool operator==(const drmModeModeInfo &m) const;
  void ToDrmModeModeInfo(drm_mode_modeinfo *m) const;

  inline bool is_vrr_mode() const { return (type_ & DRM_MODE_TYPE_VRR); };
  inline bool is_ns_mode() const { return (flags_ & DRM_MODE_FLAG_NS); };

  uint32_t id() const;
  void set_id(uint32_t id);

  uint32_t clock() const;

  uint32_t h_display() const;
  uint32_t h_sync_start() const;
  uint32_t h_sync_end() const;
  uint32_t h_total() const;
  uint32_t h_skew() const;

  uint32_t v_display() const;
  uint32_t v_sync_start() const;
  uint32_t v_sync_end() const;
  uint32_t v_total() const;
  uint32_t v_scan() const;
  float v_refresh() const;
  float te_frequency() const;
  // Convert frequency to period, with the default unit being nanoseconds.
  float v_period(int64_t unit = std::nano::den) const;
  float te_period(int64_t unit = std::nano::den) const;

  bool is_operation_rate_to_bts() const;
  uint32_t flags() const;
  uint32_t type() const;

  std::string name() const;

 private:
  uint32_t id_ = 0;

  uint32_t clock_ = 0;

  uint32_t h_display_ = 0;
  uint32_t h_sync_start_ = 0;
  uint32_t h_sync_end_ = 0;
  uint32_t h_total_ = 0;
  uint32_t h_skew_ = 0;

  uint32_t v_display_ = 0;
  uint32_t v_sync_start_ = 0;
  uint32_t v_sync_end_ = 0;
  uint32_t v_total_ = 0;
  uint32_t v_scan_ = 0;
  uint32_t v_refresh_ = 0;

  uint32_t flags_ = 0;
  uint32_t type_ = 0;

  std::string name_;
};
}  // namespace android

#endif  // ANDROID_DRM_MODE_H_

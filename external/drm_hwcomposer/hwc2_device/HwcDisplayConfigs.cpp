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

#define LOG_TAG "hwc-display-configs"

#include "HwcDisplayConfigs.h"

#include <cmath>

#include "drm/DrmConnector.h"
#include "utils/log.h"

constexpr uint32_t kHeadlessModeDisplayWidthMm = 163;
constexpr uint32_t kHeadlessModeDisplayHeightMm = 122;
constexpr uint32_t kHeadlessModeDisplayWidthPx = 1024;
constexpr uint32_t kHeadlessModeDisplayHeightPx = 768;
constexpr uint32_t kHeadlessModeDisplayVRefresh = 60;

namespace android {

// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables)
uint32_t HwcDisplayConfigs::last_config_id = 1;

void HwcDisplayConfigs::FillHeadless() {
  hwc_configs.clear();

  last_config_id++;
  preferred_config_id = active_config_id = last_config_id;
  auto headless_drm_mode_info = (drmModeModeInfo){
      .hdisplay = kHeadlessModeDisplayWidthPx,
      .vdisplay = kHeadlessModeDisplayHeightPx,
      .vrefresh = kHeadlessModeDisplayVRefresh,
      .name = "HEADLESS-MODE",
  };
  hwc_configs[active_config_id] = (HwcDisplayConfig){
      .id = active_config_id,
      .group_id = 1,
      .mode = DrmMode(&headless_drm_mode_info),
  };

  mm_width = kHeadlessModeDisplayWidthMm;
  mm_height = kHeadlessModeDisplayHeightMm;
}

// NOLINTNEXTLINE (readability-function-cognitive-complexity): Fixme
HWC2::Error HwcDisplayConfigs::Update(DrmConnector &connector) {
  /* In case UpdateModes will fail we will still have one mode for headless
   * mode*/
  FillHeadless();
  /* Read real configs */
  auto ret = connector.UpdateModes();
  if (ret != 0) {
    ALOGE("Failed to update display modes %d", ret);
    return HWC2::Error::BadDisplay;
  }

  if (connector.GetModes().empty()) {
    ALOGE("No modes reported by KMS");
    return HWC2::Error::BadDisplay;
  }

  hwc_configs.clear();
  mm_width = connector.GetMmWidth();
  mm_height = connector.GetMmHeight();

  preferred_config_id = 0;
  uint32_t preferred_config_group_id = 0;

  auto first_config_id = last_config_id;
  uint32_t last_group_id = 1;

  /* Group modes */
  for (const auto &mode : connector.GetModes()) {
    /* Find group for the new mode or create new group */
    uint32_t group_found = 0;
    for (auto &hwc_config : hwc_configs) {
      if (mode.GetRawMode().hdisplay ==
              hwc_config.second.mode.GetRawMode().hdisplay &&
          mode.GetRawMode().vdisplay ==
              hwc_config.second.mode.GetRawMode().vdisplay) {
        group_found = hwc_config.second.group_id;
      }
    }
    if (group_found == 0) {
      group_found = last_group_id++;
    }

    bool disabled = false;
    if ((mode.GetRawMode().flags & DRM_MODE_FLAG_3D_MASK) != 0) {
      ALOGI("Disabling display mode %s (Modes with 3D flag aren't supported)",
            mode.GetName().c_str());
      disabled = true;
    }

    /* Add config */
    hwc_configs[last_config_id] = {
        .id = last_config_id,
        .group_id = group_found,
        .mode = mode,
        .disabled = disabled,
    };

    /* Chwck if the mode is preferred */
    if ((mode.GetRawMode().type & DRM_MODE_TYPE_PREFERRED) != 0 &&
        preferred_config_id == 0) {
      preferred_config_id = last_config_id;
      preferred_config_group_id = group_found;
    }

    last_config_id++;
  }

  /* We must have preferred mode. Set first mode as preferred
   * in case KMS haven't reported anything. */
  if (preferred_config_id == 0) {
    preferred_config_id = first_config_id;
    preferred_config_group_id = 1;
  }

  for (uint32_t group = 1; group < last_group_id; group++) {
    bool has_interlaced = false;
    bool has_progressive = false;
    for (auto &hwc_config : hwc_configs) {
      if (hwc_config.second.group_id != group || hwc_config.second.disabled) {
        continue;
      }

      if (hwc_config.second.IsInterlaced()) {
        has_interlaced = true;
      } else {
        has_progressive = true;
      }
    }

    auto has_both = has_interlaced && has_progressive;
    if (!has_both) {
      continue;
    }

    bool group_contains_preferred_interlaced = false;
    if (group == preferred_config_group_id &&
        hwc_configs[preferred_config_id].IsInterlaced()) {
      group_contains_preferred_interlaced = true;
    }

    for (auto &hwc_config : hwc_configs) {
      if (hwc_config.second.group_id != group || hwc_config.second.disabled) {
        continue;
      }

      auto disable = group_contains_preferred_interlaced
                         ? !hwc_config.second.IsInterlaced()
                         : hwc_config.second.IsInterlaced();

      if (disable) {
        ALOGI(
            "Group %i: Disabling display mode %s (This group should consist "
            "of %s modes)",
            group, hwc_config.second.mode.GetName().c_str(),
            group_contains_preferred_interlaced ? "interlaced" : "progressive");

        hwc_config.second.disabled = true;
      }
    }
  }

  /* Group should not contain 2 modes with FPS delta less than ~1HZ
   * otherwise android.graphics.cts.SetFrameRateTest CTS will fail
   */
  constexpr float kMinFpsDelta = 1.0;  // FPS
  for (uint32_t m1 = first_config_id; m1 < last_config_id; m1++) {
    for (uint32_t m2 = first_config_id; m2 < last_config_id; m2++) {
      if (m1 != m2 && hwc_configs[m1].group_id == hwc_configs[m2].group_id &&
          !hwc_configs[m1].disabled && !hwc_configs[m2].disabled &&
          fabsf(hwc_configs[m1].mode.GetVRefresh() -
                hwc_configs[m2].mode.GetVRefresh()) < kMinFpsDelta) {
        ALOGI(
            "Group %i: Disabling display mode %s (Refresh rate value is "
            "too close to existing mode %s)",
            hwc_configs[m2].group_id, hwc_configs[m2].mode.GetName().c_str(),
            hwc_configs[m1].mode.GetName().c_str());

        hwc_configs[m2].disabled = true;
      }
    }
  }

  return HWC2::Error::None;
}

}  // namespace android

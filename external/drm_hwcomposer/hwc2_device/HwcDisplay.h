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

#pragma once

#include <hardware/hwcomposer2.h>

#include <atomic>
#include <optional>
#include <sstream>

#include "HwcDisplayConfigs.h"
#include "compositor/FlatteningController.h"
#include "compositor/LayerData.h"
#include "drm/DrmAtomicStateManager.h"
#include "drm/ResourceManager.h"
#include "drm/VSyncWorker.h"
#include "hwc2_device/HwcLayer.h"

namespace android {

class Backend;
class DrmHwcTwo;

inline constexpr uint32_t kPrimaryDisplay = 0;

class HwcDisplay {
 public:
  HwcDisplay(hwc2_display_t handle, HWC2::DisplayType type, DrmHwcTwo *hwc2);
  HwcDisplay(const HwcDisplay &) = delete;
  ~HwcDisplay();

  /* SetPipeline should be carefully used only by DrmHwcTwo hotplug handlers */
  void SetPipeline(DrmDisplayPipeline *pipeline);

  HWC2::Error CreateComposition(AtomicCommitArgs &a_args);
  std::vector<HwcLayer *> GetOrderLayersByZPos();

  void ClearDisplay();

  std::string Dump();

  // HWC Hooks
  HWC2::Error AcceptDisplayChanges();
  HWC2::Error CreateLayer(hwc2_layer_t *layer);
  HWC2::Error DestroyLayer(hwc2_layer_t layer);
  HWC2::Error GetActiveConfig(hwc2_config_t *config) const;
  HWC2::Error GetChangedCompositionTypes(uint32_t *num_elements,
                                         hwc2_layer_t *layers, int32_t *types);
  HWC2::Error GetClientTargetSupport(uint32_t width, uint32_t height,
                                     int32_t format, int32_t dataspace);
  HWC2::Error GetColorModes(uint32_t *num_modes, int32_t *modes);
  HWC2::Error GetDisplayAttribute(hwc2_config_t config, int32_t attribute,
                                  int32_t *value);
  HWC2::Error GetDisplayConfigs(uint32_t *num_configs, hwc2_config_t *configs);
  HWC2::Error GetDisplayName(uint32_t *size, char *name);
  HWC2::Error GetDisplayRequests(int32_t *display_requests,
                                 uint32_t *num_elements, hwc2_layer_t *layers,
                                 int32_t *layer_requests);
  HWC2::Error GetDisplayType(int32_t *type);
#if __ANDROID_API__ > 27
  HWC2::Error GetRenderIntents(int32_t mode, uint32_t *outNumIntents,
                               int32_t *outIntents);
  HWC2::Error SetColorModeWithIntent(int32_t mode, int32_t intent);
#endif
#if __ANDROID_API__ > 28
  HWC2::Error GetDisplayIdentificationData(uint8_t *outPort,
                                           uint32_t *outDataSize,
                                           uint8_t *outData);
  HWC2::Error GetDisplayCapabilities(uint32_t *outNumCapabilities,
                                     uint32_t *outCapabilities);
  HWC2::Error GetDisplayBrightnessSupport(bool *supported);
  HWC2::Error SetDisplayBrightness(float);
#endif
#if __ANDROID_API__ > 29
  HWC2::Error GetDisplayConnectionType(uint32_t *outType);

  HWC2::Error SetActiveConfigWithConstraints(
      hwc2_config_t config,
      hwc_vsync_period_change_constraints_t *vsyncPeriodChangeConstraints,
      hwc_vsync_period_change_timeline_t *outTimeline);
  HWC2::Error SetAutoLowLatencyMode(bool on);
  HWC2::Error GetSupportedContentTypes(
      uint32_t *outNumSupportedContentTypes,
      const uint32_t *outSupportedContentTypes);

  HWC2::Error SetContentType(int32_t contentType);
#endif
  HWC2::Error GetDisplayVsyncPeriod(uint32_t *outVsyncPeriod);

  HWC2::Error GetDozeSupport(int32_t *support);
  HWC2::Error GetHdrCapabilities(uint32_t *num_types, int32_t *types,
                                 float *max_luminance,
                                 float *max_average_luminance,
                                 float *min_luminance);
  HWC2::Error GetReleaseFences(uint32_t *num_elements, hwc2_layer_t *layers,
                               int32_t *fences);
  HWC2::Error PresentDisplay(int32_t *out_present_fence);
  HWC2::Error SetActiveConfig(hwc2_config_t config);
  HWC2::Error ChosePreferredConfig();
  HWC2::Error SetClientTarget(buffer_handle_t target, int32_t acquire_fence,
                              int32_t dataspace, hwc_region_t damage);
  HWC2::Error SetColorMode(int32_t mode);
  HWC2::Error SetColorTransform(const float *matrix, int32_t hint);
  HWC2::Error SetOutputBuffer(buffer_handle_t buffer, int32_t release_fence);
  HWC2::Error SetPowerMode(int32_t mode);
  HWC2::Error SetVsyncEnabled(int32_t enabled);
  HWC2::Error ValidateDisplay(uint32_t *num_types, uint32_t *num_requests);
  HwcLayer *get_layer(hwc2_layer_t layer) {
    auto it = layers_.find(layer);
    if (it == layers_.end())
      return nullptr;
    return &it->second;
  }

  /* Statistics */
  struct Stats {
    Stats minus(Stats b) const {
      return {total_frames_ - b.total_frames_,
              total_pixops_ - b.total_pixops_,
              gpu_pixops_ - b.gpu_pixops_,
              failed_kms_validate_ - b.failed_kms_validate_,
              failed_kms_present_ - b.failed_kms_present_,
              frames_flattened_ - b.frames_flattened_};
    }

    uint32_t total_frames_ = 0;
    uint64_t total_pixops_ = 0;
    uint64_t gpu_pixops_ = 0;
    uint32_t failed_kms_validate_ = 0;
    uint32_t failed_kms_present_ = 0;
    uint32_t frames_flattened_ = 0;
  };

  const Backend *backend() const;
  void set_backend(std::unique_ptr<Backend> backend);

  auto GetHwc2() {
    return hwc2_;
  }

  std::map<hwc2_layer_t, HwcLayer> &layers() {
    return layers_;
  }

  auto &GetPipe() {
    return *pipeline_;
  }

  bool CtmByGpu();

  Stats &total_stats() {
    return total_stats_;
  }

  /* Headless mode required to keep SurfaceFlinger alive when all display are
   * disconnected, Without headless mode Android will continuously crash.
   * Only single internal (primary) display is required to be in HEADLESS mode
   * to prevent the crash. See:
   * https://source.android.com/devices/graphics/hotplug#handling-common-scenarios
   */
  bool IsInHeadlessMode() {
    return !pipeline_;
  }

  void Deinit();

  auto GetFlatCon() {
    return flatcon_;
  }

 private:
  HwcDisplayConfigs configs_;

  DrmHwcTwo *const hwc2_;

  SharedFd present_fence_;

  std::optional<DrmMode> staged_mode_;
  int64_t staged_mode_change_time_{};
  uint32_t staged_mode_config_id_{};

  DrmDisplayPipeline *pipeline_{};

  std::unique_ptr<Backend> backend_;
  std::shared_ptr<FlatteningController> flatcon_;

  std::shared_ptr<VSyncWorker> vsync_worker_;
  bool vsync_event_en_{};
  bool vsync_tracking_en_{};
  int64_t last_vsync_ts_{};

  const hwc2_display_t handle_;
  HWC2::DisplayType type_;

  uint32_t layer_idx_{};

  std::map<hwc2_layer_t, HwcLayer> layers_;
  HwcLayer client_layer_;
  int32_t color_mode_{};
  static constexpr int kCtmRows = 3;
  static constexpr int kCtmCols = 3;
  std::shared_ptr<drm_color_ctm> color_matrix_;
  android_color_transform_t color_transform_hint_{};

  std::shared_ptr<DrmKmsPlan> current_plan_;

  uint32_t frame_no_ = 0;
  Stats total_stats_;
  Stats prev_stats_;
  std::string DumpDelta(HwcDisplay::Stats delta);

  void SetColorMarixToIdentity();

  HWC2::Error Init();

  HWC2::Error SetActiveConfigInternal(uint32_t config, int64_t change_time);
};

}  // namespace android

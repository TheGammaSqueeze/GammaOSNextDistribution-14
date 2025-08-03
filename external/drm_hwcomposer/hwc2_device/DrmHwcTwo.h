/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "drm/ResourceManager.h"
#include "hwc2_device/HwcDisplay.h"

namespace android {

class DrmHwcTwo : public PipelineToFrontendBindingInterface {
 public:
  DrmHwcTwo();
  ~DrmHwcTwo() override = default;

  std::pair<HWC2_PFN_HOTPLUG, hwc2_callback_data_t> hotplug_callback_{};
  std::pair<HWC2_PFN_VSYNC, hwc2_callback_data_t> vsync_callback_{};
#if __ANDROID_API__ > 29
  std::pair<HWC2_PFN_VSYNC_2_4, hwc2_callback_data_t> vsync_2_4_callback_{};
  std::pair<HWC2_PFN_VSYNC_PERIOD_TIMING_CHANGED, hwc2_callback_data_t>
      period_timing_changed_callback_{};
#endif
  std::pair<HWC2_PFN_REFRESH, hwc2_callback_data_t> refresh_callback_{};

  // Device functions
  HWC2::Error CreateVirtualDisplay(uint32_t width, uint32_t height,
                                   int32_t *format, hwc2_display_t *display);
  HWC2::Error DestroyVirtualDisplay(hwc2_display_t display);
  void Dump(uint32_t *outSize, char *outBuffer);
  uint32_t GetMaxVirtualDisplayCount();
  HWC2::Error RegisterCallback(int32_t descriptor, hwc2_callback_data_t data,
                               hwc2_function_pointer_t function);

  auto GetDisplay(hwc2_display_t display_handle) {
    return displays_.count(display_handle) != 0
               ? displays_[display_handle].get()
               : nullptr;
  }

  auto &GetResMan() {
    return resource_manager_;
  }

  void ScheduleHotplugEvent(hwc2_display_t displayid, bool connected) {
    deferred_hotplug_events_[displayid] = connected;
  }

  // PipelineToFrontendBindingInterface
  bool BindDisplay(DrmDisplayPipeline *pipeline) override;
  bool UnbindDisplay(DrmDisplayPipeline *pipeline) override;
  void FinalizeDisplayBinding() override;

  void SendVsyncEventToClient(hwc2_display_t displayid, int64_t timestamp,
                              uint32_t vsync_period) const;
  void SendVsyncPeriodTimingChangedEventToClient(hwc2_display_t displayid,
                                                 int64_t timestamp) const;

 private:
  void SendHotplugEventToClient(hwc2_display_t displayid, bool connected) const;

  ResourceManager resource_manager_;
  std::map<hwc2_display_t, std::unique_ptr<HwcDisplay>> displays_;
  std::map<DrmDisplayPipeline *, hwc2_display_t> display_handles_;

  std::string mDumpString;

  std::map<hwc2_display_t, bool> deferred_hotplug_events_;
  std::vector<hwc2_display_t> displays_for_removal_list_;

  uint32_t last_display_handle_ = kPrimaryDisplay;
};
}  // namespace android

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

#define LOG_TAG "hwc-drm-two"

#include "DrmHwcTwo.h"

#include <cinttypes>

#include "backend/Backend.h"
#include "utils/log.h"

namespace android {

DrmHwcTwo::DrmHwcTwo() : resource_manager_(this){};

/* Must be called after every display attach/detach cycle */
void DrmHwcTwo::FinalizeDisplayBinding() {
  if (displays_.count(kPrimaryDisplay) == 0) {
    /* Primary display MUST always exist */
    ALOGI("No pipelines available. Creating null-display for headless mode");
    displays_[kPrimaryDisplay] = std::make_unique<
        HwcDisplay>(kPrimaryDisplay, HWC2::DisplayType::Physical, this);
    /* Initializes null-display */
    displays_[kPrimaryDisplay]->SetPipeline(nullptr);
  }

  if (displays_[kPrimaryDisplay]->IsInHeadlessMode() &&
      !display_handles_.empty()) {
    /* Reattach first secondary display to take place of the primary */
    auto *pipe = display_handles_.begin()->first;
    ALOGI("Primary display was disconnected, reattaching '%s' as new primary",
          pipe->connector->Get()->GetName().c_str());
    UnbindDisplay(pipe);
    BindDisplay(pipe);
  }

  // Finally, send hotplug events to the client
  for (auto &dhe : deferred_hotplug_events_) {
    SendHotplugEventToClient(dhe.first, dhe.second);
  }
  deferred_hotplug_events_.clear();

  /* Wait 0.2s before removing the displays to flush pending HWC2 transactions
   */
  auto &mutex = GetResMan().GetMainLock();
  mutex.unlock();
  const int kTimeForSFToDisposeDisplayUs = 200000;
  usleep(kTimeForSFToDisposeDisplayUs);
  mutex.lock();
  for (auto handle : displays_for_removal_list_) {
    displays_.erase(handle);
  }
}

bool DrmHwcTwo::BindDisplay(DrmDisplayPipeline *pipeline) {
  if (display_handles_.count(pipeline) != 0) {
    ALOGE("%s, pipeline is already used by another display, FIXME!!!: %p",
          __func__, pipeline);
    return false;
  }

  uint32_t disp_handle = kPrimaryDisplay;

  if (displays_.count(kPrimaryDisplay) != 0 &&
      !displays_[kPrimaryDisplay]->IsInHeadlessMode()) {
    disp_handle = ++last_display_handle_;
  }

  if (displays_.count(disp_handle) == 0) {
    auto disp = std::make_unique<HwcDisplay>(disp_handle,
                                             HWC2::DisplayType::Physical, this);
    displays_[disp_handle] = std::move(disp);
  }

  ALOGI("Attaching pipeline '%s' to the display #%d%s",
        pipeline->connector->Get()->GetName().c_str(), (int)disp_handle,
        disp_handle == kPrimaryDisplay ? " (Primary)" : "");

  displays_[disp_handle]->SetPipeline(pipeline);
  display_handles_[pipeline] = disp_handle;

  return true;
}

bool DrmHwcTwo::UnbindDisplay(DrmDisplayPipeline *pipeline) {
  if (display_handles_.count(pipeline) == 0) {
    ALOGE("%s, can't find the display, pipeline: %p", __func__, pipeline);
    return false;
  }
  auto handle = display_handles_[pipeline];
  display_handles_.erase(pipeline);

  ALOGI("Detaching the pipeline '%s' from the display #%i%s",
        pipeline->connector->Get()->GetName().c_str(), (int)handle,
        handle == kPrimaryDisplay ? " (Primary)" : "");

  if (displays_.count(handle) == 0) {
    ALOGE("%s, can't find the display, handle: %" PRIu64, __func__, handle);
    return false;
  }
  displays_[handle]->SetPipeline(nullptr);

  /* We must defer display disposal and removal, since it may still have pending
   * HWC_API calls scheduled and waiting until ueventlistener thread releases
   * main lock, otherwise transaction may fail and SF may crash
   */
  if (handle != kPrimaryDisplay) {
    displays_for_removal_list_.emplace_back(handle);
  }
  return true;
}

HWC2::Error DrmHwcTwo::CreateVirtualDisplay(uint32_t /*width*/,
                                            uint32_t /*height*/,
                                            int32_t * /*format*/,
                                            hwc2_display_t * /*display*/) {
  // TODO(nobody): Implement virtual display
  return HWC2::Error::Unsupported;
}

HWC2::Error DrmHwcTwo::DestroyVirtualDisplay(hwc2_display_t /*display*/) {
  // TODO(nobody): Implement virtual display
  return HWC2::Error::Unsupported;
}

void DrmHwcTwo::Dump(uint32_t *outSize, char *outBuffer) {
  if (outBuffer != nullptr) {
    auto copied_bytes = mDumpString.copy(outBuffer, *outSize);
    *outSize = static_cast<uint32_t>(copied_bytes);
    return;
  }

  std::stringstream output;

  output << "-- drm_hwcomposer --\n\n";

  for (auto &disp : displays_)
    output << disp.second->Dump();

  mDumpString = output.str();
  *outSize = static_cast<uint32_t>(mDumpString.size());
}

uint32_t DrmHwcTwo::GetMaxVirtualDisplayCount() {
  // TODO(nobody): Implement virtual display
  return 0;
}

HWC2::Error DrmHwcTwo::RegisterCallback(int32_t descriptor,
                                        hwc2_callback_data_t data,
                                        hwc2_function_pointer_t function) {
  switch (static_cast<HWC2::Callback>(descriptor)) {
    case HWC2::Callback::Hotplug: {
      hotplug_callback_ = std::make_pair(HWC2_PFN_HOTPLUG(function), data);
      if (function != nullptr) {
        resource_manager_.Init();
      } else {
        resource_manager_.DeInit();
        /* Headless display may still be here. Remove it! */
        if (displays_.count(kPrimaryDisplay) != 0) {
          displays_[kPrimaryDisplay]->Deinit();
          displays_.erase(kPrimaryDisplay);
        }
      }
      break;
    }
    case HWC2::Callback::Refresh: {
      refresh_callback_ = std::make_pair(HWC2_PFN_REFRESH(function), data);
      break;
    }
    case HWC2::Callback::Vsync: {
      vsync_callback_ = std::make_pair(HWC2_PFN_VSYNC(function), data);
      break;
    }
#if __ANDROID_API__ > 29
    case HWC2::Callback::Vsync_2_4: {
      vsync_2_4_callback_ = std::make_pair(HWC2_PFN_VSYNC_2_4(function), data);
      break;
    }
    case HWC2::Callback::VsyncPeriodTimingChanged: {
      period_timing_changed_callback_ = std::
          make_pair(HWC2_PFN_VSYNC_PERIOD_TIMING_CHANGED(function), data);
      break;
    }
#endif
    default:
      break;
  }
  return HWC2::Error::None;
}

void DrmHwcTwo::SendHotplugEventToClient(hwc2_display_t displayid,
                                         bool connected) const {
  auto hc = hotplug_callback_;
  if (hc.first != nullptr && hc.second != nullptr) {
    /* For some reason HWC Service will call HWC2 API in hotplug callback
     * handler. This is the reason we're using recursive mutex.
     */
    hc.first(hc.second, displayid,
             connected == DRM_MODE_CONNECTED ? HWC2_CONNECTION_CONNECTED
                                             : HWC2_CONNECTION_DISCONNECTED);
  }
}

void DrmHwcTwo::SendVsyncEventToClient(
    hwc2_display_t displayid, int64_t timestamp,
    [[maybe_unused]] uint32_t vsync_period) const {
  /* vsync callback */
#if __ANDROID_API__ > 29
  if (vsync_2_4_callback_.first != nullptr &&
      vsync_2_4_callback_.second != nullptr) {
    vsync_2_4_callback_.first(vsync_2_4_callback_.second, displayid, timestamp,
                              vsync_period);
  } else
#endif
      if (vsync_callback_.first != nullptr &&
          vsync_callback_.second != nullptr) {
    vsync_callback_.first(vsync_callback_.second, displayid, timestamp);
  }
}

void DrmHwcTwo::SendVsyncPeriodTimingChangedEventToClient(
    [[maybe_unused]] hwc2_display_t displayid,
    [[maybe_unused]] int64_t timestamp) const {
#if __ANDROID_API__ > 29
  hwc_vsync_period_change_timeline_t timeline = {
      .newVsyncAppliedTimeNanos = timestamp,
      .refreshRequired = false,
      .refreshTimeNanos = 0,
  };
  if (period_timing_changed_callback_.first != nullptr &&
      period_timing_changed_callback_.second != nullptr) {
    period_timing_changed_callback_
        .first(period_timing_changed_callback_.second, displayid, &timeline);
  }
#endif
}

}  // namespace android

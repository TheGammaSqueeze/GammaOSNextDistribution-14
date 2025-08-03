/*
* Copyright (c) 2014 - 2018, The Linux Foundation. All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*     * Redistributions of source code must retain the above copyright
*       notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
*       copyright notice, this list of conditions and the following
*       disclaimer in the documentation and/or other materials provided
*       with the distribution.
*     * Neither the name of The Linux Foundation nor the names of its
*       contributors may be used to endorse or promote products derived
*       from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
* MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
* ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
* BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
* WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
* OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
* IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

#include <cutils/properties.h>
#include <sync/sync.h>
#include <utils/constants.h>
#include <utils/debug.h>
#include <utils/utils.h>
#include <stdarg.h>
#include <sys/mman.h>

#include <map>
#include <string>
#include <vector>

#include "hwc_display_primary.h"
#include "hwc_debugger.h"

#define __CLASS__ "HWCDisplayPrimary"

namespace sdm {

DisplayError HWCDisplayPrimary::PMICInterface::Init() {
  std::string str_lcd_bias("/sys/class/lcd_bias/secure_mode");
  fd_lcd_bias_ = ::open(str_lcd_bias.c_str(), O_WRONLY);
  if (fd_lcd_bias_ < 0) {
    DLOGE("File '%s' could not be opened. errno = %d, desc = %s", str_lcd_bias.c_str(), errno,
          strerror(errno));
    return kErrorHardware;
  }

  std::string str_leds_wled("/sys/class/leds/wled/secure_mode");
  fd_wled_ = ::open(str_leds_wled.c_str(), O_WRONLY);
  if (fd_wled_ < 0) {
    DLOGE("File '%s' could not be opened. errno = %d, desc = %s", str_leds_wled.c_str(), errno,
          strerror(errno));
    return kErrorHardware;
  }

  return kErrorNone;
}

void HWCDisplayPrimary::PMICInterface::Deinit() {
  ::close(fd_lcd_bias_);
  ::close(fd_wled_);
}

DisplayError HWCDisplayPrimary::PMICInterface::Notify(bool secure_display_start) {
  std::string str_sd_start = secure_display_start ? std::to_string(1) : std::to_string(0);
  ssize_t err = ::pwrite(fd_lcd_bias_, str_sd_start.c_str(), str_sd_start.length(), 0);
  if (err <= 0) {
    DLOGE("Write failed for lcd_bias, Error = %s", strerror(errno));
    return kErrorHardware;
  }

  err = ::pwrite(fd_wled_, str_sd_start.c_str(), str_sd_start.length(), 0);
  if (err <= 0) {
    DLOGE("Write failed for wled, Error = %s", strerror(errno));
    return kErrorHardware;
  }

  DLOGI("Successfully notifed about secure display %s to PMIC driver",
        secure_display_start ? "start": "end");
  return kErrorNone;
}

int HWCDisplayPrimary::Create(CoreInterface *core_intf, BufferAllocator *buffer_allocator,
                              HWCCallbacks *callbacks, qService::QService *qservice,
                              HWCDisplay **hwc_display) {
  int status = 0;
  uint32_t primary_width = 0;
  uint32_t primary_height = 0;

  HWCDisplay *hwc_display_primary =
      new HWCDisplayPrimary(core_intf, buffer_allocator, callbacks, qservice);
  status = hwc_display_primary->Init();
  if (status) {
    delete hwc_display_primary;
    return status;
  }

  hwc_display_primary->GetMixerResolution(&primary_width, &primary_height);
  int width = 0, height = 0;
  HWCDebugHandler::Get()->GetProperty(FB_WIDTH_PROP, &width);
  HWCDebugHandler::Get()->GetProperty(FB_HEIGHT_PROP, &height);
  if (width > 0 && height > 0) {
    primary_width = UINT32(width);
    primary_height = UINT32(height);
  }

  status = hwc_display_primary->SetFrameBufferResolution(primary_width, primary_height);
  if (status) {
    Destroy(hwc_display_primary);
    return status;
  }

  *hwc_display = hwc_display_primary;

  return status;
}

void HWCDisplayPrimary::Destroy(HWCDisplay *hwc_display) {
  hwc_display->Deinit();
  delete hwc_display;
}

HWCDisplayPrimary::HWCDisplayPrimary(CoreInterface *core_intf, BufferAllocator *buffer_allocator,
                                     HWCCallbacks *callbacks, qService::QService *qservice)
    : HWCDisplay(core_intf, callbacks, kPrimary, HWC_DISPLAY_PRIMARY, true, qservice,
                 DISPLAY_CLASS_PRIMARY, buffer_allocator),
      buffer_allocator_(buffer_allocator),
      cpu_hint_(NULL) {
}

int HWCDisplayPrimary::Init() {
  cpu_hint_ = new CPUHint();
  if (cpu_hint_->Init(static_cast<HWCDebugHandler *>(HWCDebugHandler::Get())) != kErrorNone) {
    delete cpu_hint_;
    cpu_hint_ = NULL;
  }

  use_metadata_refresh_rate_ = true;
  int disable_metadata_dynfps = 0;
  HWCDebugHandler::Get()->GetProperty(DISABLE_METADATA_DYNAMIC_FPS_PROP, &disable_metadata_dynfps);
  if (disable_metadata_dynfps) {
    use_metadata_refresh_rate_ = false;
  }

  int status = HWCDisplay::Init();
  if (status) {
    return status;
  }
  color_mode_ = new HWCColorMode(display_intf_);
  color_mode_->Init();
  HWCDebugHandler::Get()->GetProperty(ENABLE_DEFAULT_COLOR_MODE, &default_mode_status_);

  pmic_intf_ = new PMICInterface();
  pmic_intf_->Init();

  return status;
}

int HWCDisplayPrimary::Deinit() {
  histogram.stop();

  int status = HWCDisplay::Deinit();
  if (status) {
    return status;
  }
  pmic_intf_->Deinit();
  delete pmic_intf_;

  return 0;
}

std::string HWCDisplayPrimary::Dump() {
  return HWCDisplay::Dump() + histogram.Dump();
}

void HWCDisplayPrimary::ProcessBootAnimCompleted() {
  uint32_t numBootUpLayers = 0;
  // TODO(user): Remove this hack

  numBootUpLayers = static_cast<uint32_t>(Debug::GetBootAnimLayerCount());

  if (numBootUpLayers == 0) {
    numBootUpLayers = 2;
  }
  /* All other checks namely "init.svc.bootanim" or
  * HWC_GEOMETRY_CHANGED fail in correctly identifying the
  * exact bootup transition to homescreen
  */
  char property[PROPERTY_VALUE_MAX];
  bool isEncrypted = false;
  bool main_class_services_started = false;
  property_get("ro.crypto.state", property, "unencrypted");
  if (!strcmp(property, "encrypted")) {
    property_get("ro.crypto.type", property, "block");
    if (!strcmp(property, "block")) {
      isEncrypted = true;
      property_get("vold.decrypt", property, "");
      if (!strcmp(property, "trigger_restart_framework")) {
        main_class_services_started = true;
      }
    }
  }

  if ((!isEncrypted || (isEncrypted && main_class_services_started)) &&
      (layer_set_.size() > numBootUpLayers)) {
    DLOGI("Applying default mode");
    boot_animation_completed_ = true;
    // Applying default mode after bootanimation is finished And
    // If Data is Encrypted, it is ready for access.
    if (display_intf_) {
      display_intf_->ApplyDefaultDisplayMode();
      RestoreColorTransform();
    }
  }
}

HWC2::Error HWCDisplayPrimary::Validate(uint32_t *out_num_types, uint32_t *out_num_requests) {
  auto status = HWC2::Error::None;
  DisplayError error = kErrorNone;

  if (default_mode_status_ && !boot_animation_completed_) {
    ProcessBootAnimCompleted();
  }

  if (display_paused_) {
    MarkLayersForGPUBypass();
    return status;
  }

  if (color_tranform_failed_) {
    // Must fall back to client composition
    MarkLayersForClientComposition();
  }

  if (config_pending_) {
    if (display_intf_->SetActiveConfig(display_config_) != kErrorNone) {
      DLOGW("Invalid display config %d", display_config_);
      // Reset the display config with active config
      display_intf_->GetActiveConfig(&display_config_);
    }
  }
  // Fill in the remaining blanks in the layers and add them to the SDM layerstack
  BuildLayerStack();
  // Checks and replaces layer stack for solid fill
  SolidFillPrepare();

  bool pending_output_dump = dump_frame_count_ && dump_output_to_file_;

  if (readback_buffer_queued_ || pending_output_dump) {
    CloseFd(&output_buffer_.release_fence_fd);
    // RHS values were set in FrameCaptureAsync() called from a binder thread. They are picked up
    // here in a subsequent draw round. Readback is not allowed for any secure use case.
    readback_configured_ = !layer_stack_.flags.secure_present;
    if (readback_configured_) {
      DisablePartialUpdateOneFrame();
      layer_stack_.output_buffer = &output_buffer_;
      layer_stack_.flags.post_processed_output = post_processed_output_;
    }
  }

  uint32_t num_updating_layers = GetUpdatingLayersCount();
  bool one_updating_layer = (num_updating_layers == 1);
  if (num_updating_layers != 0) {
    ToggleCPUHint(one_updating_layer);
  }

  uint32_t refresh_rate = GetOptimalRefreshRate(one_updating_layer);
  bool final_rate = force_refresh_rate_ ? true : false;
  error = display_intf_->SetRefreshRate(refresh_rate, final_rate);
  if (error == kErrorNone) {
    // On success, set current refresh rate to new refresh rate
    current_refresh_rate_ = refresh_rate;
  }

  if (layer_set_.empty()) {
    // Avoid flush for Command mode panel.
    DisplayConfigFixedInfo display_config;
    display_intf_->GetConfig(&display_config);
    flush_ = !(display_config.is_cmdmode && secure_display_active_);
    validated_ = true;
    return status;
  }

  status = PrepareLayerStack(out_num_types, out_num_requests);
  pending_commit_ = true;
  return status;
}

HWC2::Error HWCDisplayPrimary::Present(int32_t *out_retire_fence) {
  auto status = HWC2::Error::None;
  if (display_paused_) {
    // TODO(user): From old HWC implementation
    // If we do not handle the frame set retireFenceFd to outbufAcquireFenceFd
    // Revisit this when validating display_paused
    DisplayError error = display_intf_->Flush();
    validated_ = false;
    if (error != kErrorNone) {
      DLOGE("Flush failed. Error = %d", error);
    }
  } else {
    status = HWCDisplay::CommitLayerStack();
    if (status == HWC2::Error::None) {
      HandleFrameOutput();
      SolidFillCommit();
      status = PostCommitLayerStack(out_retire_fence);
    }
  }

  if (CC_UNLIKELY(!has_init_light_server_)) {
    using ILight = ::hardware::google::light::V1_0::ILight;
    vendor_ILight_ = ILight::getService();
    if (vendor_ILight_ != nullptr) {
      vendor_ILight_->setHbm(false);
    } else {
      DLOGE("failed to get vendor light service");
    }

    uint32_t panel_x, panel_y;
    GetPanelResolution(&panel_x, &panel_y);
    hbm_threshold_px_ = float(panel_x * panel_y) * hbm_threshold_pct_;
    DLOGI("Configure hbm_threshold_px_ to %f", hbm_threshold_px_);

    has_init_light_server_ = true;
  }

  const bool enable_hbm(hdr_largest_layer_px_ > hbm_threshold_px_);
  if (high_brightness_mode_ != enable_hbm && vendor_ILight_ != nullptr) {
    using ::android::hardware::light::V2_0::Status;
    if (Status::SUCCESS == vendor_ILight_->setHbm(enable_hbm)) {
      high_brightness_mode_ = enable_hbm;
    } else {
      DLOGE("failed to setHbm to %d", enable_hbm);
    }
  }

  CloseFd(&output_buffer_.acquire_fence_fd);
  pending_commit_ = false;
  return status;
}

HWC2::Error HWCDisplayPrimary::GetColorModes(uint32_t *out_num_modes, ColorMode *out_modes) {
  if (out_modes == nullptr) {
    *out_num_modes = color_mode_->GetColorModeCount();
  } else {
    color_mode_->GetColorModes(out_num_modes, out_modes);
  }

  return HWC2::Error::None;
}

HWC2::Error HWCDisplayPrimary::GetRenderIntents(ColorMode mode, uint32_t *out_num_intents,
                                                RenderIntent *out_intents) {
  if (out_intents == nullptr) {
    *out_num_intents = color_mode_->GetRenderIntentCount(mode);
  } else {
    color_mode_->GetRenderIntents(mode, out_num_intents, out_intents);
  }
  return HWC2::Error::None;
}

HWC2::Error HWCDisplayPrimary::SetColorMode(ColorMode mode) {
  return SetColorModeWithRenderIntent(mode, RenderIntent::COLORIMETRIC);
}

HWC2::Error HWCDisplayPrimary::SetWhiteCompensation(bool enabled) {
  auto status = color_mode_->SetWhiteCompensation(enabled);
  if (status != HWC2::Error::None) {
    DLOGE("failed for SetWhiteCompensation to %d", enabled);
    return status;
  }

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);

  return status;
}

HWC2::Error HWCDisplayPrimary::SetColorModeWithRenderIntent(ColorMode mode, RenderIntent intent) {
  auto status = color_mode_->SetColorModeWithRenderIntent(mode, intent);
  if (status != HWC2::Error::None) {
    DLOGE("failed for mode = %d intent = %d", mode, intent);
    return status;
  }
  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);
  validated_ = false;
  auto working_color_space = color_mode_->GetWorkingColorSpace();
  working_primaries_ = working_color_space.first;
  working_transfer_ = working_color_space.second;
  current_color_mode_ = color_mode_->GetCurrentColorMode();
  return status;
}

HWC2::Error HWCDisplayPrimary::SetColorModeById(int32_t color_mode_id) {
  auto status = color_mode_->SetColorModeById(color_mode_id);
  if (status != HWC2::Error::None) {
    DLOGE("failed for mode = %d", color_mode_id);
    return status;
  }

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);
  validated_ = false;

  return status;
}

HWC2::Error HWCDisplayPrimary::RestoreColorTransform() {
  auto status = color_mode_->RestoreColorTransform();
  if (status != HWC2::Error::None) {
    DLOGE("failed to RestoreColorTransform");
    return status;
  }

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);

  return status;
}

HWC2::Error HWCDisplayPrimary::SetColorTransform(const float *matrix,
                                                 android_color_transform_t hint) {
  if (!matrix) {
    return HWC2::Error::BadParameter;
  }

  auto status = color_mode_->SetColorTransform(matrix, hint);
  if (status != HWC2::Error::None) {
    DLOGE("failed for hint = %d", hint);
    color_tranform_failed_ = true;
    return status;
  }

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);
  color_tranform_failed_ = false;
  validated_ = false;

  return status;
}

HWC2::Error HWCDisplayPrimary::SetReadbackBuffer(const native_handle_t *buffer,
                                                 int32_t acquire_fence,
                                                 bool post_processed_output) {
  const private_handle_t *handle = reinterpret_cast<const private_handle_t *>(buffer);
  if (!handle || (handle->fd < 0)) {
    return HWC2::Error::BadParameter;
  }

  // Configure the output buffer as Readback buffer
  output_buffer_.width = UINT32(handle->width);
  output_buffer_.height = UINT32(handle->height);
  output_buffer_.unaligned_width = UINT32(handle->unaligned_width);
  output_buffer_.unaligned_height = UINT32(handle->unaligned_height);
  output_buffer_.format = GetSDMFormat(handle->format, handle->flags);
  output_buffer_.planes[0].fd = handle->fd;
  output_buffer_.planes[0].stride = UINT32(handle->width);
  output_buffer_.acquire_fence_fd = dup(acquire_fence);
  output_buffer_.release_fence_fd = -1;

  post_processed_output_ = post_processed_output;
  readback_buffer_queued_ = true;
  readback_configured_ = false;
  validated_ = false;

  return HWC2::Error::None;
}

HWC2::Error HWCDisplayPrimary::GetReadbackBufferFence(int32_t *release_fence) {
  auto status = HWC2::Error::None;

  if (readback_configured_ && (output_buffer_.release_fence_fd >= 0)) {
    *release_fence = output_buffer_.release_fence_fd;
  } else {
    status = HWC2::Error::Unsupported;
    *release_fence = -1;
  }

  post_processed_output_ = false;
  readback_buffer_queued_ = false;
  readback_configured_ = false;
  output_buffer_ = {};

  return status;
}

HWC2::Error HWCDisplayPrimary::PostCommitLayerStack(int32_t *out_retire_fence) {
  auto status = HWCDisplay::PostCommitLayerStack(out_retire_fence);
  if (status != HWC2::Error::None) {
    return status;
  }

  if (pmic_notification_pending_) {
    // Wait for current commit to complete
    if (*out_retire_fence >= 0) {
      int ret = sync_wait(*out_retire_fence, 1000);
      if (ret < 0) {
        DLOGE("sync_wait error errno = %d, desc = %s", errno, strerror(errno));
      }
    }
    pmic_intf_->Notify(false /* secure_display_start */);
    pmic_notification_pending_ = false;
  }
  return HWC2::Error::None;
}

DisplayError HWCDisplayPrimary::TeardownConcurrentWriteback(void) {
  DisplayError error = kErrorNotSupported;

  if (output_buffer_.release_fence_fd >= 0) {
    int32_t release_fence_fd = dup(output_buffer_.release_fence_fd);
    int ret = sync_wait(output_buffer_.release_fence_fd, 1000);
    if (ret < 0) {
      DLOGE("sync_wait error errno = %d, desc = %s", errno, strerror(errno));
    }

    ::close(release_fence_fd);
    if (ret)
      return kErrorResources;
  }

  if (display_intf_) {
    error = display_intf_->TeardownConcurrentWriteback();
  }

  return error;
}

int HWCDisplayPrimary::Perform(uint32_t operation, ...) {
  va_list args;
  va_start(args, operation);
  int val = 0;
  LayerSolidFill *solid_fill_color;
  LayerRect *rect = NULL;

  switch (operation) {
    case SET_METADATA_DYN_REFRESH_RATE:
      val = va_arg(args, int32_t);
      SetMetaDataRefreshRateFlag(val);
      break;
    case SET_BINDER_DYN_REFRESH_RATE:
      val = va_arg(args, int32_t);
      ForceRefreshRate(UINT32(val));
      break;
    case SET_DISPLAY_MODE:
      val = va_arg(args, int32_t);
      SetDisplayMode(UINT32(val));
      break;
    case SET_QDCM_SOLID_FILL_INFO:
      solid_fill_color = va_arg(args, LayerSolidFill*);
      SetQDCMSolidFillInfo(true, *solid_fill_color);
      break;
    case UNSET_QDCM_SOLID_FILL_INFO:
      solid_fill_color = va_arg(args, LayerSolidFill*);
      SetQDCMSolidFillInfo(false, *solid_fill_color);
      break;
    case SET_QDCM_SOLID_FILL_RECT:
      rect = va_arg(args, LayerRect*);
      solid_fill_rect_ = *rect;
      break;
    default:
      DLOGW("Invalid operation %d", operation);
      va_end(args);
      return -EINVAL;
  }
  va_end(args);
  validated_ = false;

  return 0;
}

DisplayError HWCDisplayPrimary::SetDisplayMode(uint32_t mode) {
  DisplayError error = kErrorNone;

  if (display_intf_) {
    error = display_intf_->SetDisplayMode(mode);
  }

  return error;
}

void HWCDisplayPrimary::SetMetaDataRefreshRateFlag(bool enable) {
  int disable_metadata_dynfps = 0;

  HWCDebugHandler::Get()->GetProperty(DISABLE_METADATA_DYNAMIC_FPS_PROP, &disable_metadata_dynfps);
  if (disable_metadata_dynfps) {
    return;
  }
  use_metadata_refresh_rate_ = enable;
}

void HWCDisplayPrimary::SetQDCMSolidFillInfo(bool enable, const LayerSolidFill &color) {
  solid_fill_enable_ = enable;
  solid_fill_color_ = color;
}

void HWCDisplayPrimary::ToggleCPUHint(bool set) {
  if (!cpu_hint_) {
    return;
  }

  if (set) {
    cpu_hint_->Set();
  } else {
    cpu_hint_->Reset();
  }
}

void HWCDisplayPrimary::SetSecureDisplay(bool secure_display_active) {
  if (secure_display_active_ != secure_display_active) {
    // Skip Prepare and call Flush for null commit
    DLOGI("SecureDisplay state changed from %d to %d Needs Flush!!", secure_display_active_,
          secure_display_active);
    secure_display_active_ = secure_display_active;
    if (secure_display_active_) {
      pmic_intf_->Notify(true /* secure_display_start */);
    } else {
      pmic_notification_pending_ = true;
    }

    // Avoid flush for Command mode panel.
    DisplayConfigFixedInfo display_config;
    display_intf_->GetConfig(&display_config);
    skip_prepare_ = !display_config.is_cmdmode;
  }
}

void HWCDisplayPrimary::ForceRefreshRate(uint32_t refresh_rate) {
  if ((refresh_rate && (refresh_rate < min_refresh_rate_ || refresh_rate > max_refresh_rate_)) ||
      force_refresh_rate_ == refresh_rate) {
    // Cannot honor force refresh rate, as its beyond the range or new request is same
    return;
  }

  force_refresh_rate_ = refresh_rate;

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);

  return;
}

uint32_t HWCDisplayPrimary::GetOptimalRefreshRate(bool one_updating_layer) {
  if (force_refresh_rate_) {
    return force_refresh_rate_;
  } else if (use_metadata_refresh_rate_ && one_updating_layer && metadata_refresh_rate_) {
    return metadata_refresh_rate_;
  }

  return max_refresh_rate_;
}

DisplayError HWCDisplayPrimary::Refresh() {
  DisplayError error = kErrorNone;

  callbacks_->Refresh(HWC_DISPLAY_PRIMARY);

  return error;
}

void HWCDisplayPrimary::SetIdleTimeoutMs(uint32_t timeout_ms) {
  display_intf_->SetIdleTimeoutMs(timeout_ms);
  validated_ = false;
}

void HWCDisplayPrimary::HandleFrameOutput() {
  if (readback_buffer_queued_) {
    validated_ = false;
  }

  if (dump_output_to_file_) {
    HandleFrameDump();
  }
}

void HWCDisplayPrimary::HandleFrameDump() {
  if (!readback_configured_) {
    dump_frame_count_ = 0;
  }

  if (dump_frame_count_ && output_buffer_.release_fence_fd >= 0) {
    int ret = sync_wait(output_buffer_.release_fence_fd, 1000);
    ::close(output_buffer_.release_fence_fd);
    output_buffer_.release_fence_fd = -1;
    if (ret < 0) {
      DLOGE("sync_wait error errno = %d, desc = %s", errno, strerror(errno));
    } else {
      DumpOutputBuffer(output_buffer_info_, output_buffer_base_, layer_stack_.retire_fence_fd);
      readback_buffer_queued_ = false;
      validated_ = false;
    }
  }

  if (0 == dump_frame_count_) {
    dump_output_to_file_ = false;
    // Unmap and Free buffer
    if (munmap(output_buffer_base_, output_buffer_info_.alloc_buffer_info.size) != 0) {
      DLOGE("unmap failed with err %d", errno);
    }
    if (buffer_allocator_->FreeBuffer(&output_buffer_info_) != 0) {
      DLOGE("FreeBuffer failed");
    }

    readback_buffer_queued_ = false;
    post_processed_output_ = false;
    readback_configured_ = false;

    output_buffer_ = {};
    output_buffer_info_ = {};
    output_buffer_base_ = nullptr;
  }
}

HWC2::Error HWCDisplayPrimary::SetFrameDumpConfig(uint32_t count, uint32_t bit_mask_layer_type,
                                                  int32_t format, bool post_processed) {
  HWCDisplay::SetFrameDumpConfig(count, bit_mask_layer_type, format, post_processed);
  dump_output_to_file_ = bit_mask_layer_type & (1 << OUTPUT_LAYER_DUMP);
  DLOGI("output_layer_dump_enable %d", dump_output_to_file_);

  if (!count || !dump_output_to_file_) {
    return HWC2::Error::None;
  }

  // Allocate and map output buffer
  output_buffer_info_ = {};

  if (post_processed) {
    // To dump post-processed (DSPP) output, use Panel resolution.
    GetPanelResolution(&output_buffer_info_.buffer_config.width,
                       &output_buffer_info_.buffer_config.height);
  } else {
    // To dump Layer Mixer output, use FrameBuffer resolution.
    GetFrameBufferResolution(&output_buffer_info_.buffer_config.width,
                             &output_buffer_info_.buffer_config.height);
  }

  output_buffer_info_.buffer_config.format = GetSDMFormat(format, 0);
  output_buffer_info_.buffer_config.buffer_count = 1;
  if (buffer_allocator_->AllocateBuffer(&output_buffer_info_) != 0) {
    DLOGE("Buffer allocation failed");
    output_buffer_info_ = {};
    return HWC2::Error::NoResources;
  }

  void *buffer = mmap(NULL, output_buffer_info_.alloc_buffer_info.size, PROT_READ | PROT_WRITE,
                      MAP_SHARED, output_buffer_info_.alloc_buffer_info.fd, 0);

  if (buffer == MAP_FAILED) {
    DLOGE("mmap failed with err %d", errno);
    buffer_allocator_->FreeBuffer(&output_buffer_info_);
    output_buffer_info_ = {};
    return HWC2::Error::NoResources;
  }

  output_buffer_base_ = buffer;
  const native_handle_t *handle = static_cast<native_handle_t *>(output_buffer_info_.private_data);
  SetReadbackBuffer(handle, -1, post_processed);

  return HWC2::Error::None;
}

int HWCDisplayPrimary::FrameCaptureAsync(const BufferInfo &output_buffer_info,
                                         bool post_processed_output) {
  // Note: This function is called in context of a binder thread and a lock is already held
  if (output_buffer_info.alloc_buffer_info.fd < 0) {
    DLOGE("Invalid fd %d", output_buffer_info.alloc_buffer_info.fd);
    return -1;
  }

  auto panel_width = 0u;
  auto panel_height = 0u;
  auto fb_width = 0u;
  auto fb_height = 0u;

  GetPanelResolution(&panel_width, &panel_height);
  GetFrameBufferResolution(&fb_width, &fb_height);

  if (post_processed_output && (output_buffer_info.buffer_config.width < panel_width ||
                                output_buffer_info.buffer_config.height < panel_height)) {
    DLOGE("Buffer dimensions should not be less than panel resolution");
    return -1;
  } else if (!post_processed_output && (output_buffer_info.buffer_config.width < fb_width ||
                                        output_buffer_info.buffer_config.height < fb_height)) {
    DLOGE("Buffer dimensions should not be less than FB resolution");
    return -1;
  }

  const native_handle_t *buffer = static_cast<native_handle_t *>(output_buffer_info.private_data);
  SetReadbackBuffer(buffer, -1, post_processed_output);

  return 0;
}

bool HWCDisplayPrimary::GetFrameCaptureFence(int32_t *release_fence) {
  return (GetReadbackBufferFence(release_fence) == HWC2::Error::None);
}

DisplayError HWCDisplayPrimary::SetDetailEnhancerConfig
                                   (const DisplayDetailEnhancerData &de_data) {
  DisplayError error = kErrorNotSupported;

  if (display_intf_) {
    error = display_intf_->SetDetailEnhancerData(de_data);
    validated_ = false;
  }
  return error;
}

DisplayError HWCDisplayPrimary::ControlPartialUpdate(bool enable, uint32_t *pending) {
  DisplayError error = kErrorNone;

  if (display_intf_) {
    error = display_intf_->ControlPartialUpdate(enable, pending);
    validated_ = false;
  }

  return error;
}

DisplayError HWCDisplayPrimary::DisablePartialUpdateOneFrame() {
  DisplayError error = kErrorNone;

  if (display_intf_) {
    error = display_intf_->DisablePartialUpdateOneFrame();
    validated_ = false;
  }

  return error;
}

HWC2::Error HWCDisplayPrimary::SetDisplayedContentSamplingEnabledVndService(bool enabled) {
  std::unique_lock<decltype(sampling_mutex)> lk(sampling_mutex);
  vndservice_sampling_vote = enabled;
  if (api_sampling_vote || vndservice_sampling_vote) {
    histogram.start();
  } else {
    histogram.stop();
  }
  return HWC2::Error::None;
}

HWC2::Error HWCDisplayPrimary::SetDisplayedContentSamplingEnabled(int32_t enabled, uint8_t component_mask, uint64_t max_frames) {
    if ((enabled != HWC2_DISPLAYED_CONTENT_SAMPLING_ENABLE) &&
        (enabled != HWC2_DISPLAYED_CONTENT_SAMPLING_DISABLE))
      return HWC2::Error::BadParameter;

    std::unique_lock<decltype(sampling_mutex)> lk(sampling_mutex);
    if (enabled == HWC2_DISPLAYED_CONTENT_SAMPLING_ENABLE) {
      api_sampling_vote = true;
    } else {
      api_sampling_vote = false;
    }

    auto start = api_sampling_vote || vndservice_sampling_vote;
    if (start && max_frames == 0) {
        histogram.start();
    } else if (start) {
        histogram.start(max_frames);
    } else {
        histogram.stop();
    }
    return HWC2::Error::None;
}

HWC2::Error HWCDisplayPrimary::GetDisplayedContentSamplingAttributes(int32_t* format,
                                                                     int32_t* dataspace,
                                                                     uint8_t* supported_components) {
    return histogram.getAttributes(format, dataspace, supported_components);
}

HWC2::Error HWCDisplayPrimary::GetDisplayedContentSample(uint64_t max_frames,
                                                         uint64_t timestamp,
                                                         uint64_t* numFrames,
                                                         int32_t samples_size[NUM_HISTOGRAM_COLOR_COMPONENTS],
                                                         uint64_t* samples[NUM_HISTOGRAM_COLOR_COMPONENTS])
{
    histogram.collect(max_frames, timestamp, samples_size, samples, numFrames);
    return HWC2::Error::None;
}

DisplayError HWCDisplayPrimary::SetMixerResolution(uint32_t width, uint32_t height) {
  DisplayError error = display_intf_->SetMixerResolution(width, height);
  validated_ = false;
  return error;
}

DisplayError HWCDisplayPrimary::GetMixerResolution(uint32_t *width, uint32_t *height) {
  return display_intf_->GetMixerResolution(width, height);
}

HWC2::Error HWCDisplayPrimary::ControlIdlePowerCollapse(bool enable, bool synchronous) {
  DisplayError error = kErrorNone;

  if (display_intf_) {
    error = display_intf_->ControlIdlePowerCollapse(enable, synchronous);
    validated_ = false;
  }

  return (error != kErrorNone) ?  HWC2::Error::Unsupported : HWC2::Error::None;
}

}  // namespace sdm

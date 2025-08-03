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

#define LOG_TAG "hwc-display"
#define ATRACE_TAG ATRACE_TAG_GRAPHICS

#include "HwcDisplay.h"

#include "DrmHwcTwo.h"
#include "backend/Backend.h"
#include "backend/BackendManager.h"
#include "bufferinfo/BufferInfoGetter.h"
#include "utils/log.h"
#include "utils/properties.h"

namespace android {

std::string HwcDisplay::DumpDelta(HwcDisplay::Stats delta) {
  if (delta.total_pixops_ == 0)
    return "No stats yet";
  auto ratio = 1.0 - double(delta.gpu_pixops_) / double(delta.total_pixops_);

  std::stringstream ss;
  ss << " Total frames count: " << delta.total_frames_ << "\n"
     << " Failed to test commit frames: " << delta.failed_kms_validate_ << "\n"
     << " Failed to commit frames: " << delta.failed_kms_present_ << "\n"
     << ((delta.failed_kms_present_ > 0)
             ? " !!! Internal failure, FIX it please\n"
             : "")
     << " Flattened frames: " << delta.frames_flattened_ << "\n"
     << " Pixel operations (free units)"
     << " : [TOTAL: " << delta.total_pixops_ << " / GPU: " << delta.gpu_pixops_
     << "]\n"
     << " Composition efficiency: " << ratio;

  return ss.str();
}

std::string HwcDisplay::Dump() {
  auto connector_name = IsInHeadlessMode()
                            ? std::string("NULL-DISPLAY")
                            : GetPipe().connector->Get()->GetName();

  std::stringstream ss;
  ss << "- Display on: " << connector_name << "\n"
     << "Statistics since system boot:\n"
     << DumpDelta(total_stats_) << "\n\n"
     << "Statistics since last dumpsys request:\n"
     << DumpDelta(total_stats_.minus(prev_stats_)) << "\n\n";

  memcpy(&prev_stats_, &total_stats_, sizeof(Stats));
  return ss.str();
}

HwcDisplay::HwcDisplay(hwc2_display_t handle, HWC2::DisplayType type,
                       DrmHwcTwo *hwc2)
    : hwc2_(hwc2), handle_(handle), type_(type), client_layer_(this){};

void HwcDisplay::SetColorMarixToIdentity() {
  color_matrix_ = std::make_shared<drm_color_ctm>();
  for (int i = 0; i < kCtmCols; i++) {
    for (int j = 0; j < kCtmRows; j++) {
      constexpr uint64_t kOne = (1ULL << 32); /* 1.0 in s31.32 format */
      color_matrix_->matrix[i * kCtmRows + j] = (i == j) ? kOne : 0;
    }
  }

  color_transform_hint_ = HAL_COLOR_TRANSFORM_IDENTITY;
}

HwcDisplay::~HwcDisplay() = default;

void HwcDisplay::SetPipeline(DrmDisplayPipeline *pipeline) {
  Deinit();

  pipeline_ = pipeline;

  if (pipeline != nullptr || handle_ == kPrimaryDisplay) {
    Init();
    hwc2_->ScheduleHotplugEvent(handle_, /*connected = */ true);
  } else {
    hwc2_->ScheduleHotplugEvent(handle_, /*connected = */ false);
  }
}

void HwcDisplay::Deinit() {
  if (pipeline_ != nullptr) {
    AtomicCommitArgs a_args{};
    a_args.composition = std::make_shared<DrmKmsPlan>();
    GetPipe().atomic_state_manager->ExecuteAtomicCommit(a_args);
/*
 *  TODO:
 *  Unfortunately the following causes regressions on db845c
 *  with VtsHalGraphicsComposerV2_3TargetTest due to the display
 *  never coming back. Patches to avoiding that issue on the
 *  the kernel side unfortunately causes further crashes in
 *  drm_hwcomposer, because the client detach takes longer then the
 *  1 second max VTS expects. So for now as a workaround, lets skip
 *  deactivating the display on deinit, which matches previous
 *  behavior prior to commit d0494d9b8097
 */
#if 0
    a_args.composition = {};
    a_args.active = false;
    GetPipe().atomic_state_manager->ExecuteAtomicCommit(a_args);
#endif

    current_plan_.reset();
    backend_.reset();
    if (flatcon_) {
      flatcon_->StopThread();
      flatcon_.reset();
    }
  }

  if (vsync_worker_) {
    vsync_worker_->StopThread();
    vsync_worker_ = {};
  }

  SetClientTarget(nullptr, -1, 0, {});
}

HWC2::Error HwcDisplay::Init() {
  ChosePreferredConfig();

  auto vsw_callbacks = (VSyncWorkerCallbacks){
      .out_event =
          [this](int64_t timestamp) {
            const std::unique_lock lock(hwc2_->GetResMan().GetMainLock());
            if (vsync_event_en_) {
              uint32_t period_ns{};
              GetDisplayVsyncPeriod(&period_ns);
              hwc2_->SendVsyncEventToClient(handle_, timestamp, period_ns);
            }
            if (vsync_tracking_en_) {
              last_vsync_ts_ = timestamp;
            }
            if (!vsync_event_en_ && !vsync_tracking_en_) {
              vsync_worker_->VSyncControl(false);
            }
          },
      .get_vperiod_ns = [this]() -> uint32_t {
        uint32_t outVsyncPeriod = 0;
        GetDisplayVsyncPeriod(&outVsyncPeriod);
        return outVsyncPeriod;
      },
  };

  vsync_worker_ = VSyncWorker::CreateInstance(pipeline_, vsw_callbacks);
  if (!vsync_worker_) {
    ALOGE("Failed to create event worker for d=%d\n", int(handle_));
    return HWC2::Error::BadDisplay;
  }

  if (!IsInHeadlessMode()) {
    auto ret = BackendManager::GetInstance().SetBackendForDisplay(this);
    if (ret) {
      ALOGE("Failed to set backend for d=%d %d\n", int(handle_), ret);
      return HWC2::Error::BadDisplay;
    }
    auto flatcbk = (struct FlatConCallbacks){.trigger = [this]() {
      if (hwc2_->refresh_callback_.first != nullptr &&
          hwc2_->refresh_callback_.second != nullptr)
        hwc2_->refresh_callback_.first(hwc2_->refresh_callback_.second,
                                       handle_);
    }};
    flatcon_ = FlatteningController::CreateInstance(flatcbk);
  }

  client_layer_.SetLayerBlendMode(HWC2_BLEND_MODE_PREMULTIPLIED);

  SetColorMarixToIdentity();

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::ChosePreferredConfig() {
  HWC2::Error err{};
  if (!IsInHeadlessMode()) {
    err = configs_.Update(*pipeline_->connector->Get());
  } else {
    configs_.FillHeadless();
  }
  if (!IsInHeadlessMode() && err != HWC2::Error::None) {
    return HWC2::Error::BadDisplay;
  }

  return SetActiveConfig(configs_.preferred_config_id);
}

HWC2::Error HwcDisplay::AcceptDisplayChanges() {
  for (std::pair<const hwc2_layer_t, HwcLayer> &l : layers_)
    l.second.AcceptTypeChange();
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::CreateLayer(hwc2_layer_t *layer) {
  layers_.emplace(static_cast<hwc2_layer_t>(layer_idx_), HwcLayer(this));
  *layer = static_cast<hwc2_layer_t>(layer_idx_);
  ++layer_idx_;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::DestroyLayer(hwc2_layer_t layer) {
  if (!get_layer(layer)) {
    return HWC2::Error::BadLayer;
  }

  layers_.erase(layer);
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetActiveConfig(hwc2_config_t *config) const {
  if (configs_.hwc_configs.count(staged_mode_config_id_) == 0)
    return HWC2::Error::BadConfig;

  *config = staged_mode_config_id_;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetChangedCompositionTypes(uint32_t *num_elements,
                                                   hwc2_layer_t *layers,
                                                   int32_t *types) {
  if (IsInHeadlessMode()) {
    *num_elements = 0;
    return HWC2::Error::None;
  }

  uint32_t num_changes = 0;
  for (auto &l : layers_) {
    if (l.second.IsTypeChanged()) {
      if (layers && num_changes < *num_elements)
        layers[num_changes] = l.first;
      if (types && num_changes < *num_elements)
        types[num_changes] = static_cast<int32_t>(l.second.GetValidatedType());
      ++num_changes;
    }
  }
  if (!layers && !types)
    *num_elements = num_changes;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetClientTargetSupport(uint32_t width, uint32_t height,
                                               int32_t /*format*/,
                                               int32_t dataspace) {
  if (IsInHeadlessMode()) {
    return HWC2::Error::None;
  }

  auto min = pipeline_->device->GetMinResolution();
  auto max = pipeline_->device->GetMaxResolution();

  if (width < min.first || height < min.second)
    return HWC2::Error::Unsupported;

  if (width > max.first || height > max.second)
    return HWC2::Error::Unsupported;

  if (dataspace != HAL_DATASPACE_UNKNOWN)
    return HWC2::Error::Unsupported;

  // TODO(nobody): Validate format can be handled by either GL or planes
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetColorModes(uint32_t *num_modes, int32_t *modes) {
  if (!modes)
    *num_modes = 1;

  if (modes)
    *modes = HAL_COLOR_MODE_NATIVE;

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayAttribute(hwc2_config_t config,
                                            int32_t attribute_in,
                                            int32_t *value) {
  int conf = static_cast<int>(config);

  if (configs_.hwc_configs.count(conf) == 0) {
    ALOGE("Could not find mode #%d", conf);
    return HWC2::Error::BadConfig;
  }

  auto &hwc_config = configs_.hwc_configs[conf];

  static const int32_t kUmPerInch = 25400;
  auto mm_width = configs_.mm_width;
  auto mm_height = configs_.mm_height;
  auto attribute = static_cast<HWC2::Attribute>(attribute_in);
  switch (attribute) {
    case HWC2::Attribute::Width:
      *value = static_cast<int>(hwc_config.mode.GetRawMode().hdisplay);
      break;
    case HWC2::Attribute::Height:
      *value = static_cast<int>(hwc_config.mode.GetRawMode().vdisplay);
      break;
    case HWC2::Attribute::VsyncPeriod:
      // in nanoseconds
      *value = static_cast<int>(1E9 / hwc_config.mode.GetVRefresh());
      break;
    case HWC2::Attribute::DpiX:
      // Dots per 1000 inches
      *value = mm_width ? int(hwc_config.mode.GetRawMode().hdisplay *
                              kUmPerInch / mm_width)
                        : -1;
      break;
    case HWC2::Attribute::DpiY:
      // Dots per 1000 inches
      *value = mm_height ? int(hwc_config.mode.GetRawMode().vdisplay *
                               kUmPerInch / mm_height)
                         : -1;
      break;
#if __ANDROID_API__ > 29
    case HWC2::Attribute::ConfigGroup:
      /* Dispite ConfigGroup is a part of HWC2.4 API, framework
       * able to request it even if service @2.1 is used */
      *value = int(hwc_config.group_id);
      break;
#endif
    default:
      *value = -1;
      return HWC2::Error::BadConfig;
  }
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayConfigs(uint32_t *num_configs,
                                          hwc2_config_t *configs) {
  uint32_t idx = 0;
  for (auto &hwc_config : configs_.hwc_configs) {
    if (hwc_config.second.disabled) {
      continue;
    }

    if (configs != nullptr) {
      if (idx >= *num_configs) {
        break;
      }
      configs[idx] = hwc_config.second.id;
    }

    idx++;
  }
  *num_configs = idx;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayName(uint32_t *size, char *name) {
  std::ostringstream stream;
  if (IsInHeadlessMode()) {
    stream << "null-display";
  } else {
    stream << "display-" << GetPipe().connector->Get()->GetId();
  }
  auto string = stream.str();
  auto length = string.length();
  if (!name) {
    *size = length;
    return HWC2::Error::None;
  }

  *size = std::min<uint32_t>(static_cast<uint32_t>(length - 1), *size);
  strncpy(name, string.c_str(), *size);
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayRequests(int32_t * /*display_requests*/,
                                           uint32_t *num_elements,
                                           hwc2_layer_t * /*layers*/,
                                           int32_t * /*layer_requests*/) {
  // TODO(nobody): I think virtual display should request
  //      HWC2_DISPLAY_REQUEST_WRITE_CLIENT_TARGET_TO_OUTPUT here
  *num_elements = 0;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayType(int32_t *type) {
  *type = static_cast<int32_t>(type_);
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDozeSupport(int32_t *support) {
  *support = 0;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetHdrCapabilities(uint32_t *num_types,
                                           int32_t * /*types*/,
                                           float * /*max_luminance*/,
                                           float * /*max_average_luminance*/,
                                           float * /*min_luminance*/) {
  *num_types = 0;
  return HWC2::Error::None;
}

/* Find API details at:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:hardware/libhardware/include/hardware/hwcomposer2.h;l=1767
 *
 * Called after PresentDisplay(), CLIENT is expecting release fence for the
 * prior buffer (not the one assigned to the layer at the moment).
 */
HWC2::Error HwcDisplay::GetReleaseFences(uint32_t *num_elements,
                                         hwc2_layer_t *layers,
                                         int32_t *fences) {
  if (IsInHeadlessMode()) {
    *num_elements = 0;
    return HWC2::Error::None;
  }

  uint32_t num_layers = 0;

  for (auto &l : layers_) {
    if (!l.second.GetPriorBufferScanOutFlag() || !present_fence_) {
      continue;
    }

    ++num_layers;

    if (layers == nullptr || fences == nullptr)
      continue;

    if (num_layers > *num_elements) {
      ALOGW("Overflow num_elements %d/%d", num_layers, *num_elements);
      return HWC2::Error::None;
    }

    layers[num_layers - 1] = l.first;
    fences[num_layers - 1] = DupFd(present_fence_);
  }
  *num_elements = num_layers;

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::CreateComposition(AtomicCommitArgs &a_args) {
  if (IsInHeadlessMode()) {
    ALOGE("%s: Display is in headless mode, should never reach here", __func__);
    return HWC2::Error::None;
  }

  a_args.color_matrix = color_matrix_;

  uint32_t prev_vperiod_ns = 0;
  GetDisplayVsyncPeriod(&prev_vperiod_ns);

  auto mode_update_commited_ = false;
  if (staged_mode_ &&
      staged_mode_change_time_ <= ResourceManager::GetTimeMonotonicNs()) {
    client_layer_.SetLayerDisplayFrame(
        (hwc_rect_t){.left = 0,
                     .top = 0,
                     .right = int(staged_mode_->GetRawMode().hdisplay),
                     .bottom = int(staged_mode_->GetRawMode().vdisplay)});

    configs_.active_config_id = staged_mode_config_id_;

    a_args.display_mode = *staged_mode_;
    if (!a_args.test_only) {
      mode_update_commited_ = true;
    }
  }

  // order the layers by z-order
  bool use_client_layer = false;
  uint32_t client_z_order = UINT32_MAX;
  std::map<uint32_t, HwcLayer *> z_map;
  for (std::pair<const hwc2_layer_t, HwcLayer> &l : layers_) {
    switch (l.second.GetValidatedType()) {
      case HWC2::Composition::Device:
        z_map.emplace(l.second.GetZOrder(), &l.second);
        break;
      case HWC2::Composition::Client:
        // Place it at the z_order of the lowest client layer
        use_client_layer = true;
        client_z_order = std::min(client_z_order, l.second.GetZOrder());
        break;
      default:
        continue;
    }
  }
  if (use_client_layer)
    z_map.emplace(client_z_order, &client_layer_);

  if (z_map.empty())
    return HWC2::Error::BadLayer;

  std::vector<LayerData> composition_layers;

  /* Import & populate */
  for (std::pair<const uint32_t, HwcLayer *> &l : z_map) {
    l.second->PopulateLayerData();
  }

  // now that they're ordered by z, add them to the composition
  for (std::pair<const uint32_t, HwcLayer *> &l : z_map) {
    if (!l.second->IsLayerUsableAsDevice()) {
      /* This will be normally triggered on validation of the first frame
       * containing CLIENT layer. At this moment client buffer is not yet
       * provided by the CLIENT.
       * This may be triggered once in HwcLayer lifecycle in case FB can't be
       * imported. For example when non-contiguous buffer is imported into
       * contiguous-only DRM/KMS driver.
       */
      return HWC2::Error::BadLayer;
    }
    composition_layers.emplace_back(l.second->GetLayerData());
  }

  /* Store plan to ensure shared planes won't be stolen by other display
   * in between of ValidateDisplay() and PresentDisplay() calls
   */
  current_plan_ = DrmKmsPlan::CreateDrmKmsPlan(GetPipe(),
                                               std::move(composition_layers));
  if (!current_plan_) {
    if (!a_args.test_only) {
      ALOGE("Failed to create DrmKmsPlan");
    }
    return HWC2::Error::BadConfig;
  }

  a_args.composition = current_plan_;

  auto ret = GetPipe().atomic_state_manager->ExecuteAtomicCommit(a_args);

  if (ret) {
    if (!a_args.test_only)
      ALOGE("Failed to apply the frame composition ret=%d", ret);
    return HWC2::Error::BadParameter;
  }

  if (mode_update_commited_) {
    staged_mode_.reset();
    vsync_tracking_en_ = false;
    if (last_vsync_ts_ != 0) {
      hwc2_->SendVsyncPeriodTimingChangedEventToClient(handle_,
                                                       last_vsync_ts_ +
                                                           prev_vperiod_ns);
    }
  }

  return HWC2::Error::None;
}

/* Find API details at:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:hardware/libhardware/include/hardware/hwcomposer2.h;l=1805
 */
HWC2::Error HwcDisplay::PresentDisplay(int32_t *out_present_fence) {
  if (IsInHeadlessMode()) {
    *out_present_fence = -1;
    return HWC2::Error::None;
  }
  HWC2::Error ret{};

  ++total_stats_.total_frames_;

  AtomicCommitArgs a_args{};
  ret = CreateComposition(a_args);

  if (ret != HWC2::Error::None)
    ++total_stats_.failed_kms_present_;

  if (ret == HWC2::Error::BadLayer) {
    // Can we really have no client or device layers?
    *out_present_fence = -1;
    return HWC2::Error::None;
  }
  if (ret != HWC2::Error::None)
    return ret;

  this->present_fence_ = a_args.out_fence;
  *out_present_fence = DupFd(a_args.out_fence);

  // Reset the color matrix so we don't apply it over and over again.
  color_matrix_ = {};

  ++frame_no_;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetActiveConfigInternal(uint32_t config,
                                                int64_t change_time) {
  if (configs_.hwc_configs.count(config) == 0) {
    ALOGE("Could not find active mode for %u", config);
    return HWC2::Error::BadConfig;
  }

  staged_mode_ = configs_.hwc_configs[config].mode;
  staged_mode_change_time_ = change_time;
  staged_mode_config_id_ = config;

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetActiveConfig(hwc2_config_t config) {
  return SetActiveConfigInternal(config, ResourceManager::GetTimeMonotonicNs());
}

/* Find API details at:
 * https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:hardware/libhardware/include/hardware/hwcomposer2.h;l=1861
 */
HWC2::Error HwcDisplay::SetClientTarget(buffer_handle_t target,
                                        int32_t acquire_fence,
                                        int32_t dataspace,
                                        hwc_region_t /*damage*/) {
  client_layer_.SetLayerBuffer(target, acquire_fence);
  client_layer_.SetLayerDataspace(dataspace);

  /*
   * target can be nullptr, this does mean the Composer Service is calling
   * cleanDisplayResources() on after receiving HOTPLUG event. See more at:
   * https://cs.android.com/android/platform/superproject/+/master:hardware/interfaces/graphics/composer/2.1/utils/hal/include/composer-hal/2.1/ComposerClient.h;l=350;drc=944b68180b008456ed2eb4d4d329e33b19bd5166
   */
  if (target == nullptr) {
    client_layer_.SwChainClearCache();
    return HWC2::Error::None;
  }

  if (IsInHeadlessMode()) {
    return HWC2::Error::None;
  }

  client_layer_.PopulateLayerData();
  if (!client_layer_.IsLayerUsableAsDevice()) {
    ALOGE("Client layer must be always usable by DRM/KMS");
    return HWC2::Error::BadLayer;
  }

  auto &bi = client_layer_.GetLayerData().bi;
  if (!bi) {
    ALOGE("%s: Invalid state", __func__);
    return HWC2::Error::BadLayer;
  }

  auto source_crop = (hwc_frect_t){.left = 0.0F,
                                   .top = 0.0F,
                                   .right = static_cast<float>(bi->width),
                                   .bottom = static_cast<float>(bi->height)};
  client_layer_.SetLayerSourceCrop(source_crop);

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetColorMode(int32_t mode) {
  if (mode < HAL_COLOR_MODE_NATIVE || mode > HAL_COLOR_MODE_BT2100_HLG)
    return HWC2::Error::BadParameter;

  if (mode != HAL_COLOR_MODE_NATIVE)
    return HWC2::Error::Unsupported;

  color_mode_ = mode;
  return HWC2::Error::None;
}

#include <xf86drmMode.h>

HWC2::Error HwcDisplay::SetColorTransform(const float *matrix, int32_t hint) {
  if (hint < HAL_COLOR_TRANSFORM_IDENTITY ||
      hint > HAL_COLOR_TRANSFORM_CORRECT_TRITANOPIA)
    return HWC2::Error::BadParameter;

  if (!matrix && hint == HAL_COLOR_TRANSFORM_ARBITRARY_MATRIX)
    return HWC2::Error::BadParameter;

  color_transform_hint_ = static_cast<android_color_transform_t>(hint);

  if (IsInHeadlessMode())
    return HWC2::Error::None;

  if (!GetPipe().crtc->Get()->GetCtmProperty())
    return HWC2::Error::None;

  switch (color_transform_hint_) {
    case HAL_COLOR_TRANSFORM_IDENTITY:
      SetColorMarixToIdentity();
      break;
    case HAL_COLOR_TRANSFORM_ARBITRARY_MATRIX:
      color_matrix_ = std::make_shared<drm_color_ctm>();
      /* DRM expects a 3x3 matrix, but the HAL provides a 4x4 matrix. */
      for (int i = 0; i < kCtmCols; i++) {
        for (int j = 0; j < kCtmRows; j++) {
          constexpr int kInCtmRows = 4;
          /* HAL matrix type is float, but DRM expects a s31.32 fix point */
          auto value = uint64_t(matrix[i * kInCtmRows + j] * float(1ULL << 32));
          color_matrix_->matrix[i * kCtmRows + j] = value;
        }
      }
      break;
    default:
      return HWC2::Error::Unsupported;
  }

  return HWC2::Error::None;
}

bool HwcDisplay::CtmByGpu() {
  if (color_transform_hint_ == HAL_COLOR_TRANSFORM_IDENTITY)
    return false;

  if (GetPipe().crtc->Get()->GetCtmProperty())
    return false;

  if (GetHwc2()->GetResMan().GetCtmHandling() == CtmHandling::kDrmOrIgnore)
    return false;

  return true;
}

HWC2::Error HwcDisplay::SetOutputBuffer(buffer_handle_t /*buffer*/,
                                        int32_t /*release_fence*/) {
  // TODO(nobody): Need virtual display support
  return HWC2::Error::Unsupported;
}

HWC2::Error HwcDisplay::SetPowerMode(int32_t mode_in) {
  auto mode = static_cast<HWC2::PowerMode>(mode_in);

  AtomicCommitArgs a_args{};

  switch (mode) {
    case HWC2::PowerMode::Off:
      a_args.active = false;
      break;
    case HWC2::PowerMode::On:
      a_args.active = true;
      break;
    case HWC2::PowerMode::Doze:
    case HWC2::PowerMode::DozeSuspend:
      return HWC2::Error::Unsupported;
    default:
      ALOGE("Incorrect power mode value (%d)\n", mode);
      return HWC2::Error::BadParameter;
  }

  if (IsInHeadlessMode()) {
    return HWC2::Error::None;
  }

  if (a_args.active && *a_args.active) {
    /*
     * Setting the display to active before we have a composition
     * can break some drivers, so skip setting a_args.active to
     * true, as the next composition frame will implicitly activate
     * the display
     */
    return GetPipe().atomic_state_manager->ActivateDisplayUsingDPMS() == 0
               ? HWC2::Error::None
               : HWC2::Error::BadParameter;
  };

  auto err = GetPipe().atomic_state_manager->ExecuteAtomicCommit(a_args);
  if (err) {
    ALOGE("Failed to apply the dpms composition err=%d", err);
    return HWC2::Error::BadParameter;
  }
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetVsyncEnabled(int32_t enabled) {
  vsync_event_en_ = HWC2_VSYNC_ENABLE == enabled;
  if (vsync_event_en_) {
    vsync_worker_->VSyncControl(true);
  }
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::ValidateDisplay(uint32_t *num_types,
                                        uint32_t *num_requests) {
  if (IsInHeadlessMode()) {
    *num_types = *num_requests = 0;
    return HWC2::Error::None;
  }

  /* In current drm_hwc design in case previous frame layer was not validated as
   * a CLIENT, it is used by display controller (Front buffer). We have to store
   * this state to provide the CLIENT with the release fences for such buffers.
   */
  for (auto &l : layers_) {
    l.second.SetPriorBufferScanOutFlag(l.second.GetValidatedType() !=
                                       HWC2::Composition::Client);
  }

  return backend_->ValidateDisplay(this, num_types, num_requests);
}

std::vector<HwcLayer *> HwcDisplay::GetOrderLayersByZPos() {
  std::vector<HwcLayer *> ordered_layers;
  ordered_layers.reserve(layers_.size());

  for (auto &[handle, layer] : layers_) {
    ordered_layers.emplace_back(&layer);
  }

  std::sort(std::begin(ordered_layers), std::end(ordered_layers),
            [](const HwcLayer *lhs, const HwcLayer *rhs) {
              return lhs->GetZOrder() < rhs->GetZOrder();
            });

  return ordered_layers;
}

HWC2::Error HwcDisplay::GetDisplayVsyncPeriod(
    uint32_t *outVsyncPeriod /* ns */) {
  return GetDisplayAttribute(configs_.active_config_id,
                             HWC2_ATTRIBUTE_VSYNC_PERIOD,
                             (int32_t *)(outVsyncPeriod));
}

#if __ANDROID_API__ > 29
HWC2::Error HwcDisplay::GetDisplayConnectionType(uint32_t *outType) {
  if (IsInHeadlessMode()) {
    *outType = static_cast<uint32_t>(HWC2::DisplayConnectionType::Internal);
    return HWC2::Error::None;
  }
  /* Primary display should be always internal,
   * otherwise SF will be unhappy and will crash
   */
  if (GetPipe().connector->Get()->IsInternal() || handle_ == kPrimaryDisplay)
    *outType = static_cast<uint32_t>(HWC2::DisplayConnectionType::Internal);
  else if (GetPipe().connector->Get()->IsExternal())
    *outType = static_cast<uint32_t>(HWC2::DisplayConnectionType::External);
  else
    return HWC2::Error::BadConfig;

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetActiveConfigWithConstraints(
    hwc2_config_t config,
    hwc_vsync_period_change_constraints_t *vsyncPeriodChangeConstraints,
    hwc_vsync_period_change_timeline_t *outTimeline) {
  if (vsyncPeriodChangeConstraints == nullptr || outTimeline == nullptr) {
    return HWC2::Error::BadParameter;
  }

  uint32_t current_vsync_period{};
  GetDisplayVsyncPeriod(&current_vsync_period);

  if (vsyncPeriodChangeConstraints->seamlessRequired) {
    return HWC2::Error::SeamlessNotAllowed;
  }

  outTimeline->refreshTimeNanos = vsyncPeriodChangeConstraints
                                      ->desiredTimeNanos -
                                  current_vsync_period;
  auto ret = SetActiveConfigInternal(config, outTimeline->refreshTimeNanos);
  if (ret != HWC2::Error::None) {
    return ret;
  }

  outTimeline->refreshRequired = true;
  outTimeline->newVsyncAppliedTimeNanos = vsyncPeriodChangeConstraints
                                              ->desiredTimeNanos;

  last_vsync_ts_ = 0;
  vsync_tracking_en_ = true;
  vsync_worker_->VSyncControl(true);

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetAutoLowLatencyMode(bool /*on*/) {
  return HWC2::Error::Unsupported;
}

HWC2::Error HwcDisplay::GetSupportedContentTypes(
    uint32_t *outNumSupportedContentTypes,
    const uint32_t *outSupportedContentTypes) {
  if (outSupportedContentTypes == nullptr)
    *outNumSupportedContentTypes = 0;

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetContentType(int32_t contentType) {
  if (contentType != HWC2_CONTENT_TYPE_NONE)
    return HWC2::Error::Unsupported;

  /* TODO: Map to the DRM Connector property:
   * https://elixir.bootlin.com/linux/v5.4-rc5/source/drivers/gpu/drm/drm_connector.c#L809
   */

  return HWC2::Error::None;
}
#endif

#if __ANDROID_API__ > 28
HWC2::Error HwcDisplay::GetDisplayIdentificationData(uint8_t *outPort,
                                                     uint32_t *outDataSize,
                                                     uint8_t *outData) {
  if (IsInHeadlessMode()) {
    return HWC2::Error::Unsupported;
  }

  auto blob = GetPipe().connector->Get()->GetEdidBlob();
  if (!blob) {
    return HWC2::Error::Unsupported;
  }

  *outPort = handle_; /* TDOD(nobody): What should be here? */

  if (outData) {
    *outDataSize = std::min(*outDataSize, blob->length);
    memcpy(outData, blob->data, *outDataSize);
  } else {
    *outDataSize = blob->length;
  }

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayCapabilities(uint32_t *outNumCapabilities,
                                               uint32_t *outCapabilities) {
  if (outNumCapabilities == nullptr) {
    return HWC2::Error::BadParameter;
  }

  bool skip_ctm = false;

  // Skip client CTM if user requested DRM_OR_IGNORE
  if (GetHwc2()->GetResMan().GetCtmHandling() == CtmHandling::kDrmOrIgnore)
    skip_ctm = true;

  // Skip client CTM if DRM can handle it
  if (!skip_ctm && !IsInHeadlessMode() &&
      GetPipe().crtc->Get()->GetCtmProperty())
    skip_ctm = true;

  if (!skip_ctm) {
    *outNumCapabilities = 0;
    return HWC2::Error::None;
  }

  *outNumCapabilities = 1;
  if (outCapabilities) {
    outCapabilities[0] = HWC2_DISPLAY_CAPABILITY_SKIP_CLIENT_COLOR_TRANSFORM;
  }

  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::GetDisplayBrightnessSupport(bool *supported) {
  *supported = false;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetDisplayBrightness(float /* brightness */) {
  return HWC2::Error::Unsupported;
}

#endif /* __ANDROID_API__ > 28 */

#if __ANDROID_API__ > 27

HWC2::Error HwcDisplay::GetRenderIntents(
    int32_t mode, uint32_t *outNumIntents,
    int32_t * /*android_render_intent_v1_1_t*/ outIntents) {
  if (mode != HAL_COLOR_MODE_NATIVE) {
    return HWC2::Error::BadParameter;
  }

  if (outIntents == nullptr) {
    *outNumIntents = 1;
    return HWC2::Error::None;
  }
  *outNumIntents = 1;
  outIntents[0] = HAL_RENDER_INTENT_COLORIMETRIC;
  return HWC2::Error::None;
}

HWC2::Error HwcDisplay::SetColorModeWithIntent(int32_t mode, int32_t intent) {
  if (intent < HAL_RENDER_INTENT_COLORIMETRIC ||
      intent > HAL_RENDER_INTENT_TONE_MAP_ENHANCE)
    return HWC2::Error::BadParameter;

  if (mode < HAL_COLOR_MODE_NATIVE || mode > HAL_COLOR_MODE_BT2100_HLG)
    return HWC2::Error::BadParameter;

  if (mode != HAL_COLOR_MODE_NATIVE)
    return HWC2::Error::Unsupported;

  if (intent != HAL_RENDER_INTENT_COLORIMETRIC)
    return HWC2::Error::Unsupported;

  color_mode_ = mode;
  return HWC2::Error::None;
}

#endif /* __ANDROID_API__ > 27 */

const Backend *HwcDisplay::backend() const {
  return backend_.get();
}

void HwcDisplay::set_backend(std::unique_ptr<Backend> backend) {
  backend_ = std::move(backend);
}

}  // namespace android

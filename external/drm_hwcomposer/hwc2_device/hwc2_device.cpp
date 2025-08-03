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

// NOLINTNEXTLINE(cppcoreguidelines-macro-usage)
// #define LOG_NDEBUG 0 // Uncomment to see HWC2 API calls in logcat

#define LOG_TAG "hwc2-device"

#include <cinttypes>

#include "DrmHwcTwo.h"
#include "backend/Backend.h"
#include "utils/log.h"

namespace android {

/* Converts long __PRETTY_FUNCTION__ result, e.g.:
 * "int32_t android::LayerHook(hwc2_device_t *, hwc2_display_t, hwc2_layer_t,"
 * "Args...) [HookType = HWC2::Error (android::HwcLayer::*)(const native_handle"
 * "*,int), func = &android::HwcLayer::SetLayerBuffer, Args = <const
 * "native_handle, int>"
 * to the short "android::HwcLayer::SetLayerBuffer" for better logs readability
 */
static std::string GetFuncName(const char *pretty_function) {
  const std::string str(pretty_function);
  const char *start = "func = &";
  auto p1 = str.find(start);
  p1 += strlen(start);
  auto p2 = str.find(',', p1);
  return str.substr(p1, p2 - p1);
}

struct Drmhwc2Device : hwc2_device {
  DrmHwcTwo drmhwctwo;
};

static DrmHwcTwo *ToDrmHwcTwo(hwc2_device_t *dev) {
  // NOLINTNEXTLINE(cppcoreguidelines-pro-type-static-cast-downcast):
  return &static_cast<Drmhwc2Device *>(dev)->drmhwctwo;
}

template <typename PFN, typename T>
static hwc2_function_pointer_t ToHook(T function) {
  static_assert(std::is_same<PFN, T>::value, "Incompatible fn pointer");
  // NOLINTNEXTLINE(cppcoreguidelines-pro-type-reinterpret-cast):
  return reinterpret_cast<hwc2_function_pointer_t>(function);
}

template <typename T, typename HookType, HookType func, typename... Args>
static T DeviceHook(hwc2_device_t *dev, Args... args) {
  ALOGV("Device hook: %s", GetFuncName(__PRETTY_FUNCTION__).c_str());
  DrmHwcTwo *hwc = ToDrmHwcTwo(dev);
  const std::unique_lock lock(hwc->GetResMan().GetMainLock());
  return static_cast<T>(((*hwc).*func)(std::forward<Args>(args)...));
}

template <typename HookType, HookType func, typename... Args>
static int32_t DisplayHook(hwc2_device_t *dev, hwc2_display_t display_handle,
                           Args... args) {
  ALOGV("Display #%" PRIu64 " hook: %s", display_handle,
        GetFuncName(__PRETTY_FUNCTION__).c_str());
  DrmHwcTwo *hwc = ToDrmHwcTwo(dev);
  const std::unique_lock lock(hwc->GetResMan().GetMainLock());
  auto *display = hwc->GetDisplay(display_handle);
  if (display == nullptr)
    return static_cast<int32_t>(HWC2::Error::BadDisplay);

  return static_cast<int32_t>((display->*func)(std::forward<Args>(args)...));
}

template <typename HookType, HookType func, typename... Args>
static int32_t LayerHook(hwc2_device_t *dev, hwc2_display_t display_handle,
                         hwc2_layer_t layer_handle, Args... args) {
  ALOGV("Display #%" PRIu64 " Layer: #%" PRIu64 " hook: %s", display_handle,
        layer_handle, GetFuncName(__PRETTY_FUNCTION__).c_str());
  DrmHwcTwo *hwc = ToDrmHwcTwo(dev);
  const std::unique_lock lock(hwc->GetResMan().GetMainLock());
  auto *display = hwc->GetDisplay(display_handle);
  if (display == nullptr)
    return static_cast<int32_t>(HWC2::Error::BadDisplay);

  HwcLayer *layer = display->get_layer(layer_handle);
  if (!layer)
    return static_cast<int32_t>(HWC2::Error::BadLayer);

  return static_cast<int32_t>((layer->*func)(std::forward<Args>(args)...));
}

static int HookDevClose(hw_device_t *dev) {
  // NOLINTNEXTLINE (cppcoreguidelines-pro-type-reinterpret-cast): Safe
  auto *hwc2_dev = reinterpret_cast<hwc2_device_t *>(dev);
  const std::unique_ptr<DrmHwcTwo> ctx(ToDrmHwcTwo(hwc2_dev));
  return 0;
}

static void HookDevGetCapabilities(hwc2_device_t * /*dev*/, uint32_t *out_count,
                                   int32_t * /*out_capabilities*/) {
  *out_count = 0;
}

static hwc2_function_pointer_t HookDevGetFunction(struct hwc2_device * /*dev*/,
                                                  int32_t descriptor) {
  auto func = static_cast<HWC2::FunctionDescriptor>(descriptor);
  switch (func) {
    // Device functions
    case HWC2::FunctionDescriptor::CreateVirtualDisplay:
      return ToHook<HWC2_PFN_CREATE_VIRTUAL_DISPLAY>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::CreateVirtualDisplay),
                     &DrmHwcTwo::CreateVirtualDisplay, uint32_t, uint32_t,
                     int32_t *, hwc2_display_t *>);
    case HWC2::FunctionDescriptor::DestroyVirtualDisplay:
      return ToHook<HWC2_PFN_DESTROY_VIRTUAL_DISPLAY>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::DestroyVirtualDisplay),
                     &DrmHwcTwo::DestroyVirtualDisplay, hwc2_display_t>);
    case HWC2::FunctionDescriptor::Dump:
      return ToHook<HWC2_PFN_DUMP>(
          DeviceHook<void, decltype(&DrmHwcTwo::Dump), &DrmHwcTwo::Dump,
                     uint32_t *, char *>);
    case HWC2::FunctionDescriptor::GetMaxVirtualDisplayCount:
      return ToHook<HWC2_PFN_GET_MAX_VIRTUAL_DISPLAY_COUNT>(
          DeviceHook<uint32_t, decltype(&DrmHwcTwo::GetMaxVirtualDisplayCount),
                     &DrmHwcTwo::GetMaxVirtualDisplayCount>);
    case HWC2::FunctionDescriptor::RegisterCallback:
      return ToHook<HWC2_PFN_REGISTER_CALLBACK>(
          DeviceHook<int32_t, decltype(&DrmHwcTwo::RegisterCallback),
                     &DrmHwcTwo::RegisterCallback, int32_t,
                     hwc2_callback_data_t, hwc2_function_pointer_t>);

    // Display functions
    case HWC2::FunctionDescriptor::AcceptDisplayChanges:
      return ToHook<HWC2_PFN_ACCEPT_DISPLAY_CHANGES>(
          DisplayHook<decltype(&HwcDisplay::AcceptDisplayChanges),
                      &HwcDisplay::AcceptDisplayChanges>);
    case HWC2::FunctionDescriptor::CreateLayer:
      return ToHook<HWC2_PFN_CREATE_LAYER>(
          DisplayHook<decltype(&HwcDisplay::CreateLayer),
                      &HwcDisplay::CreateLayer, hwc2_layer_t *>);
    case HWC2::FunctionDescriptor::DestroyLayer:
      return ToHook<HWC2_PFN_DESTROY_LAYER>(
          DisplayHook<decltype(&HwcDisplay::DestroyLayer),
                      &HwcDisplay::DestroyLayer, hwc2_layer_t>);
    case HWC2::FunctionDescriptor::GetActiveConfig:
      return ToHook<HWC2_PFN_GET_ACTIVE_CONFIG>(
          DisplayHook<decltype(&HwcDisplay::GetActiveConfig),
                      &HwcDisplay::GetActiveConfig, hwc2_config_t *>);
    case HWC2::FunctionDescriptor::GetChangedCompositionTypes:
      return ToHook<HWC2_PFN_GET_CHANGED_COMPOSITION_TYPES>(
          DisplayHook<decltype(&HwcDisplay::GetChangedCompositionTypes),
                      &HwcDisplay::GetChangedCompositionTypes, uint32_t *,
                      hwc2_layer_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetClientTargetSupport:
      return ToHook<HWC2_PFN_GET_CLIENT_TARGET_SUPPORT>(
          DisplayHook<decltype(&HwcDisplay::GetClientTargetSupport),
                      &HwcDisplay::GetClientTargetSupport, uint32_t, uint32_t,
                      int32_t, int32_t>);
    case HWC2::FunctionDescriptor::GetColorModes:
      return ToHook<HWC2_PFN_GET_COLOR_MODES>(
          DisplayHook<decltype(&HwcDisplay::GetColorModes),
                      &HwcDisplay::GetColorModes, uint32_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayAttribute:
      return ToHook<HWC2_PFN_GET_DISPLAY_ATTRIBUTE>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayAttribute),
                      &HwcDisplay::GetDisplayAttribute, hwc2_config_t, int32_t,
                      int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayConfigs:
      return ToHook<HWC2_PFN_GET_DISPLAY_CONFIGS>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayConfigs),
                      &HwcDisplay::GetDisplayConfigs, uint32_t *,
                      hwc2_config_t *>);
    case HWC2::FunctionDescriptor::GetDisplayName:
      return ToHook<HWC2_PFN_GET_DISPLAY_NAME>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayName),
                      &HwcDisplay::GetDisplayName, uint32_t *, char *>);
    case HWC2::FunctionDescriptor::GetDisplayRequests:
      return ToHook<HWC2_PFN_GET_DISPLAY_REQUESTS>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayRequests),
                      &HwcDisplay::GetDisplayRequests, int32_t *, uint32_t *,
                      hwc2_layer_t *, int32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayType:
      return ToHook<HWC2_PFN_GET_DISPLAY_TYPE>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayType),
                      &HwcDisplay::GetDisplayType, int32_t *>);
    case HWC2::FunctionDescriptor::GetDozeSupport:
      return ToHook<HWC2_PFN_GET_DOZE_SUPPORT>(
          DisplayHook<decltype(&HwcDisplay::GetDozeSupport),
                      &HwcDisplay::GetDozeSupport, int32_t *>);
    case HWC2::FunctionDescriptor::GetHdrCapabilities:
      return ToHook<HWC2_PFN_GET_HDR_CAPABILITIES>(
          DisplayHook<decltype(&HwcDisplay::GetHdrCapabilities),
                      &HwcDisplay::GetHdrCapabilities, uint32_t *, int32_t *,
                      float *, float *, float *>);
    case HWC2::FunctionDescriptor::GetReleaseFences:
      return ToHook<HWC2_PFN_GET_RELEASE_FENCES>(
          DisplayHook<decltype(&HwcDisplay::GetReleaseFences),
                      &HwcDisplay::GetReleaseFences, uint32_t *, hwc2_layer_t *,
                      int32_t *>);
    case HWC2::FunctionDescriptor::PresentDisplay:
      return ToHook<HWC2_PFN_PRESENT_DISPLAY>(
          DisplayHook<decltype(&HwcDisplay::PresentDisplay),
                      &HwcDisplay::PresentDisplay, int32_t *>);
    case HWC2::FunctionDescriptor::SetActiveConfig:
      return ToHook<HWC2_PFN_SET_ACTIVE_CONFIG>(
          DisplayHook<decltype(&HwcDisplay::SetActiveConfig),
                      &HwcDisplay::SetActiveConfig, hwc2_config_t>);
    case HWC2::FunctionDescriptor::SetClientTarget:
      return ToHook<HWC2_PFN_SET_CLIENT_TARGET>(
          DisplayHook<decltype(&HwcDisplay::SetClientTarget),
                      &HwcDisplay::SetClientTarget, buffer_handle_t, int32_t,
                      int32_t, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetColorMode:
      return ToHook<HWC2_PFN_SET_COLOR_MODE>(
          DisplayHook<decltype(&HwcDisplay::SetColorMode),
                      &HwcDisplay::SetColorMode, int32_t>);
    case HWC2::FunctionDescriptor::SetColorTransform:
      return ToHook<HWC2_PFN_SET_COLOR_TRANSFORM>(
          DisplayHook<decltype(&HwcDisplay::SetColorTransform),
                      &HwcDisplay::SetColorTransform, const float *, int32_t>);
    case HWC2::FunctionDescriptor::SetOutputBuffer:
      return ToHook<HWC2_PFN_SET_OUTPUT_BUFFER>(
          DisplayHook<decltype(&HwcDisplay::SetOutputBuffer),
                      &HwcDisplay::SetOutputBuffer, buffer_handle_t, int32_t>);
    case HWC2::FunctionDescriptor::SetPowerMode:
      return ToHook<HWC2_PFN_SET_POWER_MODE>(
          DisplayHook<decltype(&HwcDisplay::SetPowerMode),
                      &HwcDisplay::SetPowerMode, int32_t>);
    case HWC2::FunctionDescriptor::SetVsyncEnabled:
      return ToHook<HWC2_PFN_SET_VSYNC_ENABLED>(
          DisplayHook<decltype(&HwcDisplay::SetVsyncEnabled),
                      &HwcDisplay::SetVsyncEnabled, int32_t>);
    case HWC2::FunctionDescriptor::ValidateDisplay:
      return ToHook<HWC2_PFN_VALIDATE_DISPLAY>(
          DisplayHook<decltype(&HwcDisplay::ValidateDisplay),
                      &HwcDisplay::ValidateDisplay, uint32_t *, uint32_t *>);
#if __ANDROID_API__ > 27
    case HWC2::FunctionDescriptor::GetRenderIntents:
      return ToHook<HWC2_PFN_GET_RENDER_INTENTS>(
          DisplayHook<decltype(&HwcDisplay::GetRenderIntents),
                      &HwcDisplay::GetRenderIntents, int32_t, uint32_t *,
                      int32_t *>);
    case HWC2::FunctionDescriptor::SetColorModeWithRenderIntent:
      return ToHook<HWC2_PFN_SET_COLOR_MODE_WITH_RENDER_INTENT>(
          DisplayHook<decltype(&HwcDisplay::SetColorModeWithIntent),
                      &HwcDisplay::SetColorModeWithIntent, int32_t, int32_t>);
#endif
#if __ANDROID_API__ > 28
    case HWC2::FunctionDescriptor::GetDisplayIdentificationData:
      return ToHook<HWC2_PFN_GET_DISPLAY_IDENTIFICATION_DATA>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayIdentificationData),
                      &HwcDisplay::GetDisplayIdentificationData, uint8_t *,
                      uint32_t *, uint8_t *>);
    case HWC2::FunctionDescriptor::GetDisplayCapabilities:
      return ToHook<HWC2_PFN_GET_DISPLAY_CAPABILITIES>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayCapabilities),
                      &HwcDisplay::GetDisplayCapabilities, uint32_t *,
                      uint32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayBrightnessSupport:
      return ToHook<HWC2_PFN_GET_DISPLAY_BRIGHTNESS_SUPPORT>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayBrightnessSupport),
                      &HwcDisplay::GetDisplayBrightnessSupport, bool *>);
    case HWC2::FunctionDescriptor::SetDisplayBrightness:
      return ToHook<HWC2_PFN_SET_DISPLAY_BRIGHTNESS>(
          DisplayHook<decltype(&HwcDisplay::SetDisplayBrightness),
                      &HwcDisplay::SetDisplayBrightness, float>);
#endif /* __ANDROID_API__ > 28 */
#if __ANDROID_API__ > 29
    case HWC2::FunctionDescriptor::GetDisplayConnectionType:
      return ToHook<HWC2_PFN_GET_DISPLAY_CONNECTION_TYPE>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayConnectionType),
                      &HwcDisplay::GetDisplayConnectionType, uint32_t *>);
    case HWC2::FunctionDescriptor::GetDisplayVsyncPeriod:
      return ToHook<HWC2_PFN_GET_DISPLAY_VSYNC_PERIOD>(
          DisplayHook<decltype(&HwcDisplay::GetDisplayVsyncPeriod),
                      &HwcDisplay::GetDisplayVsyncPeriod,
                      hwc2_vsync_period_t *>);
    case HWC2::FunctionDescriptor::SetActiveConfigWithConstraints:
      return ToHook<HWC2_PFN_SET_ACTIVE_CONFIG_WITH_CONSTRAINTS>(
          DisplayHook<decltype(&HwcDisplay::SetActiveConfigWithConstraints),
                      &HwcDisplay::SetActiveConfigWithConstraints,
                      hwc2_config_t, hwc_vsync_period_change_constraints_t *,
                      hwc_vsync_period_change_timeline_t *>);
    case HWC2::FunctionDescriptor::SetAutoLowLatencyMode:
      return ToHook<HWC2_PFN_SET_AUTO_LOW_LATENCY_MODE>(
          DisplayHook<decltype(&HwcDisplay::SetAutoLowLatencyMode),
                      &HwcDisplay::SetAutoLowLatencyMode, bool>);
    case HWC2::FunctionDescriptor::GetSupportedContentTypes:
      return ToHook<HWC2_PFN_GET_SUPPORTED_CONTENT_TYPES>(
          DisplayHook<decltype(&HwcDisplay::GetSupportedContentTypes),
                      &HwcDisplay::GetSupportedContentTypes, uint32_t *,
                      uint32_t *>);
    case HWC2::FunctionDescriptor::SetContentType:
      return ToHook<HWC2_PFN_SET_CONTENT_TYPE>(
          DisplayHook<decltype(&HwcDisplay::SetContentType),
                      &HwcDisplay::SetContentType, int32_t>);
#endif
    // Layer functions
    case HWC2::FunctionDescriptor::SetCursorPosition:
      return ToHook<HWC2_PFN_SET_CURSOR_POSITION>(
          LayerHook<decltype(&HwcLayer::SetCursorPosition),
                    &HwcLayer::SetCursorPosition, int32_t, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerBlendMode:
      return ToHook<HWC2_PFN_SET_LAYER_BLEND_MODE>(
          LayerHook<decltype(&HwcLayer::SetLayerBlendMode),
                    &HwcLayer::SetLayerBlendMode, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerBuffer:
      return ToHook<HWC2_PFN_SET_LAYER_BUFFER>(
          LayerHook<decltype(&HwcLayer::SetLayerBuffer),
                    &HwcLayer::SetLayerBuffer, buffer_handle_t, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerColor:
      return ToHook<HWC2_PFN_SET_LAYER_COLOR>(
          LayerHook<decltype(&HwcLayer::SetLayerColor),
                    &HwcLayer::SetLayerColor, hwc_color_t>);
    case HWC2::FunctionDescriptor::SetLayerCompositionType:
      return ToHook<HWC2_PFN_SET_LAYER_COMPOSITION_TYPE>(
          LayerHook<decltype(&HwcLayer::SetLayerCompositionType),
                    &HwcLayer::SetLayerCompositionType, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerDataspace:
      return ToHook<HWC2_PFN_SET_LAYER_DATASPACE>(
          LayerHook<decltype(&HwcLayer::SetLayerDataspace),
                    &HwcLayer::SetLayerDataspace, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerDisplayFrame:
      return ToHook<HWC2_PFN_SET_LAYER_DISPLAY_FRAME>(
          LayerHook<decltype(&HwcLayer::SetLayerDisplayFrame),
                    &HwcLayer::SetLayerDisplayFrame, hwc_rect_t>);
    case HWC2::FunctionDescriptor::SetLayerPlaneAlpha:
      return ToHook<HWC2_PFN_SET_LAYER_PLANE_ALPHA>(
          LayerHook<decltype(&HwcLayer::SetLayerPlaneAlpha),
                    &HwcLayer::SetLayerPlaneAlpha, float>);
    case HWC2::FunctionDescriptor::SetLayerSidebandStream:
      return ToHook<HWC2_PFN_SET_LAYER_SIDEBAND_STREAM>(
          LayerHook<decltype(&HwcLayer::SetLayerSidebandStream),
                    &HwcLayer::SetLayerSidebandStream,
                    const native_handle_t *>);
    case HWC2::FunctionDescriptor::SetLayerSourceCrop:
      return ToHook<HWC2_PFN_SET_LAYER_SOURCE_CROP>(
          LayerHook<decltype(&HwcLayer::SetLayerSourceCrop),
                    &HwcLayer::SetLayerSourceCrop, hwc_frect_t>);
    case HWC2::FunctionDescriptor::SetLayerSurfaceDamage:
      return ToHook<HWC2_PFN_SET_LAYER_SURFACE_DAMAGE>(
          LayerHook<decltype(&HwcLayer::SetLayerSurfaceDamage),
                    &HwcLayer::SetLayerSurfaceDamage, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetLayerTransform:
      return ToHook<HWC2_PFN_SET_LAYER_TRANSFORM>(
          LayerHook<decltype(&HwcLayer::SetLayerTransform),
                    &HwcLayer::SetLayerTransform, int32_t>);
    case HWC2::FunctionDescriptor::SetLayerVisibleRegion:
      return ToHook<HWC2_PFN_SET_LAYER_VISIBLE_REGION>(
          LayerHook<decltype(&HwcLayer::SetLayerVisibleRegion),
                    &HwcLayer::SetLayerVisibleRegion, hwc_region_t>);
    case HWC2::FunctionDescriptor::SetLayerZOrder:
      return ToHook<HWC2_PFN_SET_LAYER_Z_ORDER>(
          LayerHook<decltype(&HwcLayer::SetLayerZOrder),
                    &HwcLayer::SetLayerZOrder, uint32_t>);
    case HWC2::FunctionDescriptor::Invalid:
    default:
      return nullptr;
  }
}

static int HookDevOpen(const struct hw_module_t *module, const char *name,
                       struct hw_device_t **dev) {
  if (strcmp(name, HWC_HARDWARE_COMPOSER) != 0) {
    ALOGE("Invalid module name- %s", name);
    return -EINVAL;
  }

  auto ctx = std::make_unique<Drmhwc2Device>();
  if (!ctx) {
    ALOGE("Failed to allocate DrmHwcTwo");
    return -ENOMEM;
  }

  ctx->common.tag = HARDWARE_DEVICE_TAG;
  ctx->common.version = HWC_DEVICE_API_VERSION_2_0;
  ctx->common.close = HookDevClose;
  // NOLINTNEXTLINE(cppcoreguidelines-pro-type-cstyle-cast)
  ctx->common.module = (hw_module_t *)module;
  ctx->getCapabilities = HookDevGetCapabilities;
  ctx->getFunction = HookDevGetFunction;

  *dev = &ctx.release()->common;

  return 0;
}

}  // namespace android

// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables)
static struct hw_module_methods_t hwc2_module_methods = {
    .open = android::HookDevOpen,
};

// NOLINTNEXTLINE(cppcoreguidelines-avoid-non-const-global-variables)
hw_module_t HAL_MODULE_INFO_SYM = {
    .tag = HARDWARE_MODULE_TAG,
    .module_api_version = HARDWARE_MODULE_API_VERSION(2, 0),
    .id = HWC_HARDWARE_MODULE_ID,
    .name = "DrmHwcTwo module",
    .author = "The Android Open Source Project",
    .methods = &hwc2_module_methods,
    .dso = nullptr,
    .reserved = {0},
};

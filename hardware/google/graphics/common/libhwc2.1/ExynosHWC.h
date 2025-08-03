/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ANDROID_EXYNOS_HWC_H_
#define ANDROID_EXYNOS_HWC_H_
#include <hardware/hwcomposer2.h>
#include <cutils/atomic.h>
#include "ExynosMPPType.h"
#include "ExynosHWCModule.h"
#include "ExynosDevice.h"

//#define DISABLE_FENCE

enum {
    HWC_CTL_MAX_OVLY_CNT = 100,
    HWC_CTL_VIDEO_OVLY_CNT = 101,
    HWC_CTL_DYNAMIC_RECOMP = 102,
    HWC_CTL_SKIP_STATIC = 103,
    /* HWC_CTL_DMA_BW_BAL = 104, */
    HWC_CTL_SECURE_DMA = 105,
    HWC_CTL_WINDOW_UPDATE = 106,
    HWC_CTL_FORCE_PANIC = 107,
    HWC_CTL_FORCE_GPU = 108,
    HWC_CTL_SKIP_M2M_PROCESSING = 109,
    HWC_CTL_DISPLAY_MODE = 110,
    HWC_CTL_SKIP_RESOURCE_ASSIGN = 111,
    HWC_CTL_SKIP_VALIDATE = 112,
    HWC_CTL_DUMP_MID_BUF = 200,
    HWC_CTL_CAPTURE_READBACK = 201,
    HWC_CTL_ENABLE_COMPOSITION_CROP = 300,
    HWC_CTL_ENABLE_EXYNOSCOMPOSITION_OPT = 301,
    HWC_CTL_ENABLE_CLIENTCOMPOSITION_OPT = 302,
    HWC_CTL_USE_MAX_G2D_SRC = 303,
    HWC_CTL_ENABLE_HANDLE_LOW_FPS = 304,
    HWC_CTL_ENABLE_EARLY_START_MPP = 305,
    HWC_CTL_DDI_RESOLUTION_CHANGE = 306,
    HWC_CTL_ENABLE_FENCE_TRACER = 307,
    HWC_CTL_DO_FENCE_FILE_DUMP = 308,
    HWC_CTL_SYS_FENCE_LOGGING = 309,
};

class ExynosDevice;

hwc2_function_pointer_t exynos_getFunction(struct hwc2_device* device, int32_t descriptor);
void exynos_getCapabilities(struct hwc2_device* device, uint32_t* outcount, int32_t* outcapabilities);
void exynos_dump(hwc2_device_t* device, uint32_t* outSize, char* outBuffer);
int32_t exynos_acceptDisplayChanges(hwc2_device_t* device, hwc2_display_t display);
int32_t exynos_createLayer(hwc2_device_t* device, hwc2_display_t display, hwc2_layer_t* outLayer);
int32_t exynos_createVirtualDisplay(hwc2_device_t* device, uint32_t width, uint32_t height,
        int32_t* format, hwc2_display_t* outDisplay);
int32_t exynos_destroyLayer(hwc2_device_t* device, hwc2_display_t display, hwc2_layer_t layer);
int32_t exynos_destroyVirtualDisplay(hwc2_device_t* device, hwc2_display_t display);
int32_t exynos_getActiveConfig(hwc2_device_t *device, hwc2_display_t display, hwc2_config_t* outConfig);
int32_t exynos_getChangedCompositionTypes(hwc2_device_t *device, hwc2_display_t display,
        uint32_t* outNumElements, hwc2_layer_t* outLayers, int32_t* outTypes);
int32_t exynos_getClientTargetSupport(hwc2_device_t *device, hwc2_display_t display, uint32_t width,
        uint32_t height, int32_t format, int32_t dataSpace);
int32_t exynos_getColorModes(hwc2_device_t *device, hwc2_display_t display, uint32_t* outNumModes,
        int32_t* outModes);
int32_t exynos_getDisplayAttribute(hwc2_device_t *device, hwc2_display_t display, hwc2_config_t config,
        int32_t attribute, int32_t *outValue);
int32_t exynos_getDisplayConfigs(hwc2_device_t *device, hwc2_display_t display, uint32_t* outNumConfigs,
        hwc2_config_t* outConfigs);
int32_t exynos_getDisplayName(hwc2_device_t *device, hwc2_display_t display, uint32_t* outSize, char* outName);
int32_t exynos_getDisplayRequests(hwc2_device_t *device, hwc2_display_t display, int32_t* outDisplayRequests,
        uint32_t* outNumElements, hwc2_layer_t* outLayers, int32_t* outLayerRequests);
int32_t exynos_getDisplayType(hwc2_device_t *device, hwc2_display_t display, int32_t* outType);
int32_t exynos_getDozeSupport(hwc2_device_t *device, hwc2_display_t display, int32_t* outSupport);
int32_t exynos_getHdrCapabilities(hwc2_device_t *device, hwc2_display_t display, uint32_t* outNumTypes,
        int32_t* outTypes, float* outMaxLuminance, float* outMaxAverageLuminance,
        float* outMinLuminance);
int32_t exynos_getMaxVirtualDisplayCount(hwc2_device_t* device);
int32_t exynos_getReleaseFences(hwc2_device_t *device, hwc2_display_t display, uint32_t* outNumElements,
        hwc2_layer_t* outLayers, int32_t* outFences);
int32_t exynos_presentDisplay(hwc2_device_t *device, hwc2_display_t display, int32_t* outRetireFence);
int32_t exynos_registerCallback(hwc2_device_t* device, int32_t descriptor,
        hwc2_callback_data_t callbackData, hwc2_function_pointer_t pointer);
int32_t exynos_setActiveConfig(hwc2_device_t *device, hwc2_display_t display, hwc2_config_t config);
int32_t exynos_setClientTarget(hwc2_device_t *device, hwc2_display_t display, buffer_handle_t target,
        int32_t acquireFence, int32_t dataspace, hwc_region_t damage);
int32_t exynos_setColorMode(hwc2_device_t *device, hwc2_display_t display, int32_t mode);
int32_t exynos_setColorTransform(hwc2_device_t *device, hwc2_display_t display, const float* matrix, int32_t hint);
int32_t exynos_setCursorPosition(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer,
        int32_t x, int32_t y);
int32_t exynos_setLayerBlendMode(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, int32_t mode);
int32_t exynos_setLayerBuffer(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer,
        buffer_handle_t buffer, int32_t acquireFence);
int32_t exynos_setLayerColor(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, hwc_color_t color);
int32_t exynos_setLayerCompositionType(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, int32_t type);
int32_t exynos_setLayerDataspace(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, int32_t dataspace);
int32_t exynos_setLayerDisplayFrame(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer,
        hwc_rect_t frame);
int32_t exynos_setLayerPlaneAlpha(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, float alpha);
int32_t exynos_setLayerSidebandStream(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer,
        const native_handle_t* stream);
int32_t exynos_setLayerSourceCrop(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, hwc_frect_t crop);
int32_t exynos_setLayerSurfaceDamage(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, hwc_region_t damage);
int32_t exynos_setLayerTransform(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, int32_t transform);
int32_t exynos_setLayerVisibleRegion(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, hwc_region_t visible);
int32_t exynos_setLayerZOrder(hwc2_device_t *device, hwc2_display_t display, hwc2_layer_t layer, uint32_t z);
int32_t exynos_setOutputBuffer(hwc2_device_t *device, hwc2_display_t display, buffer_handle_t buffer, int32_t releaseFence);
int32_t exynos_setPowerMode(hwc2_device_t *device, hwc2_display_t display, int32_t mode);
int32_t exynos_setVsyncEnabled(hwc2_device_t *device, hwc2_display_t display, int32_t enabled);
int32_t exynos_validateDisplay(hwc2_device_t *device, hwc2_display_t display,
        uint32_t* outNumTypes, uint32_t* outNumRequests);
int32_t exynos_setLayerPerFrameMetadata(hwc2_device_t* device, hwc2_display_t display,
        hwc2_layer_t layer, uint32_t numElements,
        const int32_t* /*hw2_per_frame_metadata_key_t*/ keys,
        const float* metadata);
int32_t exynos_getPerFrameMetadataKeys(hwc2_device_t* device, hwc2_display_t display,
        uint32_t* outNumKeys, int32_t* /*hwc2_per_frame_metadata_key_t*/ outKeys);
int32_t exynos_getRenderIntents(hwc2_device_t* device, hwc2_display_t display, int32_t mode,
        uint32_t* outNumIntents, int32_t* /*android_render_intent_v1_1_t*/ outIntents);
int32_t exynos_setColorModeWithRenderIntent(hwc2_device_t* device, hwc2_display_t display,
        int32_t /*android_color_mode_t*/ mode,
        int32_t /*android_render_intent_v1_1_t */ intent);
int32_t exynos_getReadbackBufferAttributes(hwc2_device_t *dev, hwc2_display_t display,
        int32_t* /*android_pixel_format_t*/ outFormat,
        int32_t* /*android_dataspace_t*/ outDataspace);
int32_t exynos_setReadbackBuffer(hwc2_device_t *dev, hwc2_display_t display,
        buffer_handle_t buffer, int32_t releaseFence);
int32_t exynos_getReadbackBufferFence(hwc2_device_t *dev, hwc2_display_t display,
        int32_t* outFence);

int32_t exynos_getDisplayIdentificationData(hwc2_device_t* device, hwc2_display_t display, uint8_t* outPort,
        uint32_t* outDataSize, uint8_t* outData);
int32_t exynos_getDisplayCapabilities(hwc2_device_t* device, hwc2_display_t display, uint32_t* outNumCapabilities,
        uint32_t* outCapabilities);
int32_t exynos_setLayerColorTransform(hwc2_device_t* device, hwc2_display_t display, hwc2_layer_t layer,
        const float* matrix);
int32_t exynos_getDisplayedContentSamplingAttributes(hwc2_device_t* device, hwc2_display_t display,
        int32_t* /* andrmid_pixel_format_t */ format,
        int32_t* /* android_dataspace_t */ dataspace,
        uint8_t* /* mask of android_component_t */ supported_components);
int32_t exynos_setDisplayedContentSamplingEnabled(hwc2_device_t* device, hwc2_display_t display,
        int32_t /*hwc2_displayed_content_sampling_t*/ enabled,
        uint8_t /* mask of android_component_t */ component_mask,
        uint64_t max_frames);
int32_t exynos_getDisplayedContentSample(hwc2_device_t* device, hwc2_display_t display,
        uint64_t max_frames, uint64_t timestamp,
        uint64_t* frame_count, int32_t samples_size[4], uint64_t* samples[4]);
int32_t exynos_setLayerPerFrameMetadataBlobs(hwc2_device_t* device, hwc2_display_t display,
        hwc2_layer_t layer, uint32_t numElements, const int32_t* keys, const uint32_t* sizes,
        const uint8_t* metadata);
int32_t exynos_getDisplayBrightnessSupport(hwc2_device_t* device, hwc2_display_t display, bool* outSupport);
int32_t exynos_setDisplayBrightness(hwc2_device_t* device, hwc2_display_t display, float brightness);

int32_t exynos_GetDisplayConnectionType(hwc2_device_t* device, hwc2_display_t display,
        uint32_t* /*hwc2_display_connection_type_t*/ outType);
int32_t exynos_GetDisplayVsyncPeriod(hwc2_device_t* device, hwc2_display_t display,
        hwc2_vsync_period_t* outVsyncPeriod);
int32_t exynos_SetActiveConfigWithConstraints(hwc2_device_t* device, hwc2_display_t display,
        hwc2_config_t config, hwc_vsync_period_change_constraints_t* vsyncPeriodChangeConstraints,
        hwc_vsync_period_change_timeline_t* outTimeline);
int32_t exynos_SetAutoLowLatencyMode(hwc2_device_t* device, hwc2_display_t display, bool on);
int32_t exynos_GetSupportedContentTypes(hwc2_device_t* device, hwc2_display_t display,
        uint32_t* outNumSupportedContentTypes, uint32_t* outSupportedContentTypes);
int32_t exynos_SetContentType(hwc2_device_t* device, hwc2_display_t display,
        int32_t /* hwc2_content_type_t */ contentType);
int32_t exynos_GetClientTargetProperty(hwc2_device_t* device, hwc2_display_t display,
        hwc_client_target_property_t* outClientTargetProperty);
int32_t exynos_SetLayerGenericMetadata(hwc2_device_t* device, hwc2_display_t display,
        hwc2_layer_t layer, uint32_t keyLength, const char* key,
        bool mandatory, uint32_t valueLength, const uint8_t* value);
void exynos_GetLayerGenericMetadataKey(hwc2_device_t* device, uint32_t keyIndex,
        uint32_t* outKeyLength, char* outKey, bool* outMandatory);

enum {
    NO_DRM = 0,
    NORMAL_DRM,
    SECURE_DRM,
};

struct exynos_hwc2_device_t {
    hwc2_device_t base;
	ExynosDevice *device;
};
#endif

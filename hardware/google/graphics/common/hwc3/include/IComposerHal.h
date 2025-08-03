/*
 * Copyright (C) 2021 The Android Open Source Project
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
/**
 * Idealy IComposerHal should use aidl NativeHandle not native_handle_t/buffer_handle_t.
 * However current patten is the HWC lib does not own buffer resources (HWC lib
 * does own the fences).
 */
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/graphics/common/BlendMode.h>
#include <aidl/android/hardware/graphics/common/ColorTransform.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/DisplayHotplugEvent.h>
#include <aidl/android/hardware/graphics/common/FRect.h>
#include <aidl/android/hardware/graphics/common/Hdr.h>
#include <aidl/android/hardware/graphics/common/HdrConversionCapability.h>
#include <aidl/android/hardware/graphics/common/HdrConversionStrategy.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#include <aidl/android/hardware/graphics/common/Point.h>
#include <aidl/android/hardware/graphics/common/Rect.h>
#include <aidl/android/hardware/graphics/common/Transform.h>
#include <aidl/android/hardware/graphics/composer3/Buffer.h>
#include <aidl/android/hardware/graphics/composer3/Capability.h>
#include <aidl/android/hardware/graphics/composer3/ChangedCompositionTypes.h>
#include <aidl/android/hardware/graphics/composer3/ClientTarget.h>
#include <aidl/android/hardware/graphics/composer3/ClientTargetProperty.h>
#include <aidl/android/hardware/graphics/composer3/ClientTargetPropertyWithBrightness.h>
#include <aidl/android/hardware/graphics/composer3/Color.h>
#include <aidl/android/hardware/graphics/composer3/ColorMode.h>
#include <aidl/android/hardware/graphics/composer3/CommandError.h>
#include <aidl/android/hardware/graphics/composer3/CommandResultPayload.h>
#include <aidl/android/hardware/graphics/composer3/Composition.h>
#include <aidl/android/hardware/graphics/composer3/ContentType.h>
#include <aidl/android/hardware/graphics/composer3/DimmingStage.h>
#include <aidl/android/hardware/graphics/composer3/DisplayAttribute.h>
#include <aidl/android/hardware/graphics/composer3/DisplayBrightness.h>
#include <aidl/android/hardware/graphics/composer3/DisplayCapability.h>
#include <aidl/android/hardware/graphics/composer3/DisplayCommand.h>
#include <aidl/android/hardware/graphics/composer3/DisplayConfiguration.h>
#include <aidl/android/hardware/graphics/composer3/DisplayConnectionType.h>
#include <aidl/android/hardware/graphics/composer3/DisplayContentSample.h>
#include <aidl/android/hardware/graphics/composer3/DisplayContentSamplingAttributes.h>
#include <aidl/android/hardware/graphics/composer3/DisplayIdentification.h>
#include <aidl/android/hardware/graphics/composer3/DisplayRequest.h>
#include <aidl/android/hardware/graphics/composer3/FormatColorComponent.h>
#include <aidl/android/hardware/graphics/composer3/HdrCapabilities.h>
#include <aidl/android/hardware/graphics/composer3/LayerBrightness.h>
#include <aidl/android/hardware/graphics/composer3/LayerCommand.h>
#include <aidl/android/hardware/graphics/composer3/OverlayProperties.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableBlendMode.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableComposition.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableDataspace.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableTransform.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadata.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadataBlob.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadataKey.h>
#include <aidl/android/hardware/graphics/composer3/PlaneAlpha.h>
#include <aidl/android/hardware/graphics/composer3/PowerMode.h>
#include <aidl/android/hardware/graphics/composer3/PresentFence.h>
#include <aidl/android/hardware/graphics/composer3/PresentOrValidate.h>
#include <aidl/android/hardware/graphics/composer3/ReadbackBufferAttributes.h>
#include <aidl/android/hardware/graphics/composer3/RefreshRateChangedDebugData.h>
#include <aidl/android/hardware/graphics/composer3/ReleaseFences.h>
#include <aidl/android/hardware/graphics/composer3/RenderIntent.h>
#include <aidl/android/hardware/graphics/composer3/VirtualDisplay.h>
#include <aidl/android/hardware/graphics/composer3/VsyncPeriodChangeConstraints.h>
#include <aidl/android/hardware/graphics/composer3/VsyncPeriodChangeTimeline.h>
#include <aidl/android/hardware/graphics/composer3/ZOrder.h>
#include <cutils/native_handle.h>

// avoid naming conflict
using AidlPixelFormat = aidl::android::hardware::graphics::common::PixelFormat;
using AidlNativeHandle = aidl::android::hardware::common::NativeHandle;
using DisplayConfiguration = aidl::android::hardware::graphics::composer3::DisplayConfiguration;

namespace aidl::android::hardware::graphics::composer3::impl {

// Abstraction of ComposerHal. Returned error code is compatible with AIDL
// IComposerClient interface.
class IComposerHal {
 public:
     static std::unique_ptr<IComposerHal> create(int32_t composerInterfaceVersion);
     virtual ~IComposerHal() = default;

     virtual void getCapabilities(std::vector<Capability>* caps) = 0;
     virtual void dumpDebugInfo(std::string* output) = 0;
     virtual bool hasCapability(Capability cap) = 0;

     class EventCallback {
     public:
         virtual ~EventCallback() = default;
         virtual void onHotplug(int64_t display, bool connected) = 0;
         virtual void onRefresh(int64_t display) = 0;
         virtual void onVsync(int64_t display, int64_t timestamp, int32_t vsyncPeriodNanos) = 0;
         virtual void onVsyncPeriodTimingChanged(int64_t display,
                                                 const VsyncPeriodChangeTimeline& timeline) = 0;
         virtual void onVsyncIdle(int64_t display) = 0;
         virtual void onSeamlessPossible(int64_t display) = 0;
         virtual void onRefreshRateChangedDebug(const RefreshRateChangedDebugData& data) = 0;
         virtual void onHotplugEvent(int64_t display, common::DisplayHotplugEvent event) = 0;
     };
    virtual void registerEventCallback(EventCallback* callback) = 0;
    virtual void unregisterEventCallback() = 0;

    virtual int32_t acceptDisplayChanges(int64_t display) = 0;
    virtual int32_t createLayer(int64_t display, int64_t* outLayer) = 0;
    virtual int32_t createVirtualDisplay(uint32_t width, uint32_t height, AidlPixelFormat format,
                                         VirtualDisplay* outDisplay) = 0;
    virtual int32_t destroyLayer(int64_t display, int64_t layer) = 0;
    virtual int32_t destroyVirtualDisplay(int64_t display) = 0;
    virtual int32_t flushDisplayBrightnessChange(int64_t display) = 0;
    virtual int32_t getActiveConfig(int64_t display, int32_t* outConfig) = 0;
    virtual int32_t getColorModes(int64_t display, std::vector<ColorMode>* outModes) = 0;
    virtual int32_t getDataspaceSaturationMatrix(common::Dataspace dataspace,
                                                 std::vector<float>* matrix) = 0;
    virtual int32_t getDisplayAttribute(int64_t display, int32_t config,
                                      DisplayAttribute attribute, int32_t* outValue) = 0;
    virtual int32_t getDisplayBrightnessSupport(int64_t display, bool& outSupport) = 0;
    virtual int32_t getDisplayIdleTimerSupport(int64_t display, bool& outSupport) = 0;
    virtual int32_t getDisplayMultiThreadedPresentSupport(const int64_t& display,
                                                          bool& outSupport) = 0;
    virtual int32_t getDisplayCapabilities(int64_t display,
                                           std::vector<DisplayCapability>* caps) = 0;
    virtual int32_t getDisplayConfigs(int64_t display, std::vector<int32_t>* configs) = 0;
    virtual int32_t getDisplayConfigurations(int64_t display, int32_t maxFrameIntervalNs,
                                             std::vector<DisplayConfiguration>* configs) = 0;
    virtual int32_t notifyExpectedPresent(int64_t display,
                                          const ClockMonotonicTimestamp& expectedPresentTime,
                                          int32_t frameIntervalNs) = 0;
    virtual int32_t getDisplayConnectionType(int64_t display, DisplayConnectionType* outType) = 0;
    virtual int32_t getDisplayIdentificationData(int64_t display, DisplayIdentification *id) = 0;
    virtual int32_t getDisplayName(int64_t display, std::string* outName) = 0;
    virtual int32_t getDisplayVsyncPeriod(int64_t display, int32_t* outVsyncPeriod) = 0;
    virtual int32_t getDisplayedContentSample(int64_t display, int64_t maxFrames,
                                            int64_t timestamp, DisplayContentSample* samples) = 0;
    virtual int32_t getDisplayedContentSamplingAttributes(
            int64_t display, DisplayContentSamplingAttributes* attrs) = 0;
    virtual int32_t getDisplayPhysicalOrientation(int64_t display,
                                                  common::Transform* orientation) = 0;
    virtual int32_t getDozeSupport(int64_t display, bool& outSupport) = 0;
    virtual int32_t getHdrCapabilities(int64_t display, HdrCapabilities* caps) = 0;
    virtual int32_t getOverlaySupport(OverlayProperties* caps) = 0;
    virtual int32_t getMaxVirtualDisplayCount(int32_t* count) = 0;
    virtual int32_t getPerFrameMetadataKeys(int64_t display,
                                            std::vector<PerFrameMetadataKey>* keys) = 0;
    virtual int32_t getReadbackBufferAttributes(int64_t display,
                                                ReadbackBufferAttributes* attrs) = 0;
    virtual int32_t getReadbackBufferFence(int64_t display,
                                           ndk::ScopedFileDescriptor* aqcuireFence) = 0;
    virtual int32_t getRenderIntents(int64_t display, ColorMode mode,
                                     std::vector<RenderIntent>* intents) = 0;
    virtual int32_t getSupportedContentTypes(int64_t display, std::vector<ContentType>* types) = 0;
    virtual int32_t presentDisplay(int64_t display, ndk::ScopedFileDescriptor& fence,
                                   std::vector<int64_t>* outLayers,
                                   std::vector<ndk::ScopedFileDescriptor>* outReleaseFences) = 0;
    virtual int32_t setActiveConfig(int64_t display, int32_t config) = 0;
    virtual int32_t setActiveConfigWithConstraints(
            int64_t display, int32_t config,
            const VsyncPeriodChangeConstraints& vsyncPeriodChangeConstraints,
            VsyncPeriodChangeTimeline* timeline) = 0;
    virtual int32_t setBootDisplayConfig(int64_t display, int32_t config) = 0;
    virtual int32_t clearBootDisplayConfig(int64_t display) = 0;
    virtual int32_t getPreferredBootDisplayConfig(int64_t display, int32_t* config) = 0;
    virtual int32_t getHdrConversionCapabilities(std::vector<common::HdrConversionCapability>*) = 0;
    virtual int32_t setHdrConversionStrategy(const common::HdrConversionStrategy&,
                                             common::Hdr*) = 0;
    virtual int32_t setAutoLowLatencyMode(int64_t display, bool on) = 0;
    virtual int32_t setClientTarget(int64_t display, buffer_handle_t target,
                                    const ndk::ScopedFileDescriptor& fence,
                                    common::Dataspace dataspace,
                                    const std::vector<common::Rect>& damage) = 0; // cmd
    virtual int32_t getHasClientComposition(int64_t display, bool& outHasClientComp) = 0;
    virtual int32_t setColorMode(int64_t display, ColorMode mode, RenderIntent intent) = 0;
    virtual int32_t setColorTransform(int64_t display, const std::vector<float>& matrix) = 0; // cmd
    virtual int32_t setContentType(int64_t display, ContentType contentType) = 0;
    virtual int32_t setDisplayBrightness(int64_t display, float brightness) = 0;
    virtual int32_t setDisplayedContentSamplingEnabled(int64_t display, bool enable,
                                                       FormatColorComponent componentMask,
                                                       int64_t maxFrames) = 0;
    virtual int32_t setLayerBlendMode(int64_t display, int64_t layer, common::BlendMode mode) = 0;
    virtual int32_t setLayerBuffer(int64_t display, int64_t layer, buffer_handle_t buffer,
                                   const ndk::ScopedFileDescriptor& acquireFence) = 0;
    virtual int32_t setLayerColor(int64_t display, int64_t layer, Color color) = 0;
    virtual int32_t setLayerColorTransform(int64_t display, int64_t layer,
                                           const std::vector<float>& matrix) = 0;
    virtual int32_t setLayerCompositionType(int64_t display, int64_t layer, Composition type) = 0;
    virtual int32_t setLayerCursorPosition(int64_t display, int64_t layer, int32_t x,
                                           int32_t y) = 0;
    virtual int32_t setLayerDataspace(int64_t display, int64_t layer,
                                      common::Dataspace dataspace) = 0;
    virtual int32_t setLayerDisplayFrame(int64_t display, int64_t layer,
                                         const common::Rect& frame) = 0;
    virtual int32_t setLayerPerFrameMetadata(int64_t display, int64_t layer,
                            const std::vector<std::optional<PerFrameMetadata>>& metadata) = 0;
    virtual int32_t setLayerPerFrameMetadataBlobs(int64_t display, int64_t layer,
                            const std::vector<std::optional<PerFrameMetadataBlob>>& blobs) = 0;
    virtual int32_t setLayerPlaneAlpha(int64_t display, int64_t layer, float alpha) = 0;
    virtual int32_t setLayerSidebandStream(int64_t display, int64_t layer,
                                           buffer_handle_t stream) = 0;
    virtual int32_t setLayerSourceCrop(int64_t display, int64_t layer,
                                       const common::FRect& crop) = 0;
    virtual int32_t setLayerSurfaceDamage(int64_t display, int64_t layer,
                        const std::vector<std::optional<common::Rect>>& damage) = 0;
    virtual int32_t setLayerTransform(int64_t display, int64_t layer,
                                      common::Transform transform) = 0;
    virtual int32_t setLayerVisibleRegion(int64_t display, int64_t layer,
                                 const std::vector<std::optional<common::Rect>>& visible) = 0;
    virtual int32_t setLayerBrightness(int64_t display, int64_t layer, float brightness) = 0;
    virtual int32_t setLayerZOrder(int64_t display, int64_t layer, uint32_t z) = 0;
    virtual int32_t setOutputBuffer(int64_t display, buffer_handle_t buffer,
                                    const ndk::ScopedFileDescriptor& releaseFence) = 0;
    virtual int32_t setPowerMode(int64_t display, PowerMode mode) = 0;
    virtual int32_t setReadbackBuffer(int64_t display, buffer_handle_t buffer,
                                      const ndk::ScopedFileDescriptor& releaseFence) = 0;
    virtual int32_t setVsyncEnabled(int64_t display, bool enabled) = 0;
    virtual int32_t validateDisplay(int64_t display, std::vector<int64_t>* outChangedLayers,
                                    std::vector<Composition>* outCompositionTypes,
                                    uint32_t* outDisplayRequestMask,
                                    std::vector<int64_t>* outRequestedLayers,
                                    std::vector<int32_t>* outRequestMasks,
                                    ClientTargetProperty* outClientTargetProperty,
                                    DimmingStage* outDimmingStage) = 0;
    virtual int32_t setExpectedPresentTime(
            int64_t display, const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
            int frameIntervalNs) = 0;
    virtual int32_t setIdleTimerEnabled(int64_t display, int32_t timeout) = 0;
    virtual int32_t getRCDLayerSupport(int64_t display, bool& outSupport) = 0;
    virtual int32_t setLayerBlockingRegion(
            int64_t display, int64_t layer,
            const std::vector<std::optional<common::Rect>>& blockingRegion) = 0;
    virtual int32_t setRefreshRateChangedCallbackDebugEnabled(int64_t display, bool enabled) = 0;
};

} // namespace aidl::android::hardware::graphics::composer3::detail

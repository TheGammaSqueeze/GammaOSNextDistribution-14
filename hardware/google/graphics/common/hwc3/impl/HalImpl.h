/*
 * Copyright 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <memory>
#include <unordered_set>

#include "include/IComposerHal.h"

class ExynosDevice;
class ExynosDisplay;
class ExynosLayer;

struct exynos_hwc2_device_t;
typedef struct exynos_hwc2_device_t ExynosHWCCtx;

namespace aidl::android::hardware::graphics::composer3::impl {

// Forward aidl call to Exynos HWC
class HalImpl : public IComposerHal {
  public:
    HalImpl(std::unique_ptr<ExynosDevice> device);
    virtual ~HalImpl() = default;

    void getCapabilities(std::vector<Capability>* caps) override;
    void dumpDebugInfo(std::string* output) override;
    bool hasCapability(Capability cap) override;

    void registerEventCallback(EventCallback* callback) override;
    void unregisterEventCallback() override;

    int32_t acceptDisplayChanges(int64_t display) override;
    int32_t createLayer(int64_t display, int64_t* outLayer) override;
    int32_t createVirtualDisplay(uint32_t width, uint32_t height, AidlPixelFormat format,
                                 VirtualDisplay* outDisplay) override;
    int32_t destroyLayer(int64_t display, int64_t layer) override;
    int32_t destroyVirtualDisplay(int64_t display) override;
    int32_t flushDisplayBrightnessChange(int64_t display) override;
    int32_t getActiveConfig(int64_t display, int32_t* outConfig) override;
    int32_t getColorModes(int64_t display, std::vector<ColorMode>* outModes) override;

    int32_t getDataspaceSaturationMatrix(common::Dataspace dataspace,
                                         std::vector<float>* matrix) override;
    int32_t getDisplayAttribute(int64_t display, int32_t config, DisplayAttribute attribute,
                                int32_t* outValue) override;
    int32_t getDisplayBrightnessSupport(int64_t display, bool& outSupport) override;
    int32_t getDisplayCapabilities(int64_t display, std::vector<DisplayCapability>* caps) override;
    int32_t getDisplayConfigs(int64_t display, std::vector<int32_t>* configs) override;
    int32_t getDisplayConfigurations(int64_t display, int32_t maxFrameIntervalNs,
                                     std::vector<DisplayConfiguration>* outConfigs) override;
    int32_t notifyExpectedPresent(int64_t display,
                                  const ClockMonotonicTimestamp& expectedPresentTime,
                                  int32_t frameIntervalNs) override;
    int32_t getDisplayConnectionType(int64_t display, DisplayConnectionType* outType) override;
    int32_t getDisplayIdentificationData(int64_t display, DisplayIdentification* id) override;
    int32_t getDisplayName(int64_t display, std::string* outName) override;
    int32_t getDisplayVsyncPeriod(int64_t display, int32_t* outVsyncPeriod) override;
    int32_t getDisplayedContentSample(int64_t display, int64_t maxFrames, int64_t timestamp,
                                      DisplayContentSample* samples) override;
    int32_t getDisplayedContentSamplingAttributes(int64_t display,
                                                  DisplayContentSamplingAttributes* attrs) override;
    int32_t getDisplayPhysicalOrientation(int64_t display, common::Transform* orientation) override;
    int32_t getDozeSupport(int64_t display, bool& outSupport) override;
    int32_t getHdrCapabilities(int64_t display, HdrCapabilities* caps) override;
    int32_t getOverlaySupport(OverlayProperties* caps) override;
    int32_t getMaxVirtualDisplayCount(int32_t* count) override;
    int32_t getPerFrameMetadataKeys(int64_t display,
                                    std::vector<PerFrameMetadataKey>* keys) override;

    int32_t getReadbackBufferAttributes(int64_t display, ReadbackBufferAttributes* attrs) override;
    int32_t getReadbackBufferFence(int64_t display,
                                   ndk::ScopedFileDescriptor* acquireFence) override;
    int32_t getRenderIntents(int64_t display, ColorMode mode,
                             std::vector<RenderIntent>* intents) override;
    int32_t getSupportedContentTypes(int64_t display, std::vector<ContentType>* types) override;
    int32_t presentDisplay(int64_t display, ndk::ScopedFileDescriptor& fence,
                           std::vector<int64_t>* outLayers,
                           std::vector<ndk::ScopedFileDescriptor>* outReleaseFences) override;
    int32_t setActiveConfig(int64_t display, int32_t config) override;
    int32_t setActiveConfigWithConstraints(
            int64_t display, int32_t config,
            const VsyncPeriodChangeConstraints& vsyncPeriodChangeConstraints,
            VsyncPeriodChangeTimeline* timeline) override;
    int32_t setBootDisplayConfig(int64_t display, int32_t config) override;
    int32_t clearBootDisplayConfig(int64_t display) override;
    int32_t getPreferredBootDisplayConfig(int64_t display, int32_t* config) override;
    int32_t getHdrConversionCapabilities(std::vector<common::HdrConversionCapability>*) override;
    int32_t setHdrConversionStrategy(const common::HdrConversionStrategy&, common::Hdr*) override;
    int32_t setAutoLowLatencyMode(int64_t display, bool on) override;
    int32_t setClientTarget(int64_t display, buffer_handle_t target,
                            const ndk::ScopedFileDescriptor& fence, common::Dataspace dataspace,
                            const std::vector<common::Rect>& damage) override;
    int32_t getHasClientComposition(int64_t display, bool& outHasClientComp) override;
    int32_t setColorMode(int64_t display, ColorMode mode, RenderIntent intent) override;
    int32_t setColorTransform(int64_t display, const std::vector<float>& matrix) override;
    int32_t setContentType(int64_t display, ContentType contentType) override;
    int32_t setDisplayBrightness(int64_t display, float brightness) override;
    int32_t setDisplayedContentSamplingEnabled(int64_t display, bool enable,
                                               FormatColorComponent componentMask,
                                               int64_t maxFrames) override;
    int32_t setLayerBlendMode(int64_t display, int64_t layer, common::BlendMode mode) override;
    int32_t setLayerBuffer(int64_t display, int64_t layer, buffer_handle_t buffer,
                           const ndk::ScopedFileDescriptor& acquireFence) override;
    int32_t setLayerColor(int64_t display, int64_t layer, Color color) override;
    int32_t setLayerColorTransform(int64_t display, int64_t layer,
                                   const std::vector<float>& matrix) override;
    int32_t setLayerCompositionType(int64_t display, int64_t layer, Composition type) override;
    int32_t setLayerCursorPosition(int64_t display, int64_t layer, int32_t x, int32_t y) override;
    int32_t setLayerDataspace(int64_t display, int64_t layer, common::Dataspace dataspace) override;
    int32_t setLayerDisplayFrame(int64_t display, int64_t layer,
                                 const common::Rect& frame) override;
    int32_t setLayerPerFrameMetadata(int64_t display, int64_t layer,
                            const std::vector<std::optional<PerFrameMetadata>>& metadata) override;
    int32_t setLayerPerFrameMetadataBlobs(int64_t display, int64_t layer,
                            const std::vector<std::optional<PerFrameMetadataBlob>>& blobs) override;
    int32_t setLayerPlaneAlpha(int64_t display, int64_t layer, float alpha) override;
    int32_t setLayerSidebandStream(int64_t display, int64_t layer,
                                   buffer_handle_t stream) override;
    int32_t setLayerSourceCrop(int64_t display, int64_t layer, const common::FRect& crop) override;
    int32_t setLayerSurfaceDamage(int64_t display, int64_t layer,
                                  const std::vector<std::optional<common::Rect>>& damage) override;
    int32_t setLayerTransform(int64_t display, int64_t layer, common::Transform transform) override;
    int32_t setLayerVisibleRegion(int64_t display, int64_t layer,
                          const std::vector<std::optional<common::Rect>>& visible) override;
    int32_t setLayerBrightness(int64_t display, int64_t layer, float brightness) override;
    int32_t setLayerZOrder(int64_t display, int64_t layer, uint32_t z) override;
    int32_t setOutputBuffer(int64_t display, buffer_handle_t buffer,
                            const ndk::ScopedFileDescriptor& releaseFence) override;
    int32_t setPowerMode(int64_t display, PowerMode mode) override;
    int32_t setReadbackBuffer(int64_t display, buffer_handle_t buffer,
                              const ndk::ScopedFileDescriptor& releaseFence) override;
    int32_t setVsyncEnabled(int64_t display, bool enabled) override;
    int32_t getDisplayIdleTimerSupport(int64_t display, bool& outSupport) override;
    int32_t getDisplayMultiThreadedPresentSupport(const int64_t& display,
                                                  bool& outSupport) override;
    int32_t setIdleTimerEnabled(int64_t display, int32_t timeout) override;
    int32_t getRCDLayerSupport(int64_t display, bool& outSupport) override;
    int32_t setLayerBlockingRegion(
            int64_t display, int64_t layer,
            const std::vector<std::optional<common::Rect>>& blockingRegion) override;
    int32_t validateDisplay(int64_t display, std::vector<int64_t>* outChangedLayers,
                            std::vector<Composition>* outCompositionTypes,
                            uint32_t* outDisplayRequestMask,
                            std::vector<int64_t>* outRequestedLayers,
                            std::vector<int32_t>* outRequestMasks,
                            ClientTargetProperty* outClientTargetProperty,
                            DimmingStage* outDimmingStage) override;
    int32_t setExpectedPresentTime(int64_t display,
                                   const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
                                   int frameIntervalNs) override;

    EventCallback* getEventCallback() { return mEventCallback; }
    int32_t setRefreshRateChangedCallbackDebugEnabled(int64_t display, bool enabled) override;

private:
    void initCaps();
    int32_t getHalDisplay(int64_t display, ExynosDisplay*& halDisplay);
    int32_t getHalLayer(int64_t display, int64_t layer, ExynosLayer*& halLayer);

    std::unique_ptr<ExynosDevice> mDevice;
    EventCallback* mEventCallback;
#ifdef USES_HWC_SERVICES
    std::unique_ptr<ExynosHWCCtx> mHwcCtx;
#endif
    std::unordered_set<Capability> mCaps;
};

} // namespace aidl::android::hardware::graphics::composer3::impl

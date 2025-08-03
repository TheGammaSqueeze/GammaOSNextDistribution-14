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

#include <aidl/android/hardware/graphics/common/DisplayDecorationSupport.h>
#include <aidl/android/hardware/graphics/composer3/BnComposerClient.h>
#include <utils/Mutex.h>

#include <memory>

#include "ComposerCommandEngine.h"
#include "include/IComposerHal.h"
#include "include/IResourceManager.h"

namespace aidl::android::hardware::graphics::composer3::impl {

class ComposerClient : public BnComposerClient {
public:
    ComposerClient(IComposerHal* hal) : mHal(hal) {}
    virtual ~ComposerClient();
    bool init();
    void setOnClientDestroyed(std::function<void()> onClientDestroyed) {
        mOnClientDestroyed = onClientDestroyed;
    }

    class HalEventCallback : public IComposerHal::EventCallback {
      public:
          HalEventCallback(IComposerHal* hal, IResourceManager* resources,
                           const std::shared_ptr<IComposerCallback>& callback)
                : mHal(hal), mResources(resources), mCallback(callback) {}
          void onHotplug(int64_t display, bool connected) override;
          void onRefresh(int64_t display) override;
          void onVsync(int64_t display, int64_t timestamp, int32_t vsyncPeriodNanos) override;
          void onVsyncPeriodTimingChanged(int64_t display,
                                          const VsyncPeriodChangeTimeline& timeline) override;
          void onVsyncIdle(int64_t display) override;
          void onSeamlessPossible(int64_t display) override;
          void onRefreshRateChangedDebug(const RefreshRateChangedDebugData& data) override;
          void onHotplugEvent(int64_t display, common::DisplayHotplugEvent event) override;

      private:
        void processDisplayResources(int64_t display, bool connected);
        void cleanDisplayResources(int64_t display);

        IComposerHal* mHal;
        IResourceManager* mResources;
        const std::shared_ptr<IComposerCallback> mCallback;
    };

    // composer3 interface
    ndk::ScopedAStatus createLayer(int64_t display, int32_t bufferSlotCount,
                                   int64_t* layer) override;
    ndk::ScopedAStatus createVirtualDisplay(int32_t width, int32_t height,
                                            AidlPixelFormat formatHint,
                                            int32_t outputBufferSlotCount,
                                            VirtualDisplay* display) override;
    ndk::ScopedAStatus destroyLayer(int64_t display, int64_t layer) override;
    ndk::ScopedAStatus destroyVirtualDisplay(int64_t display) override;
    ndk::ScopedAStatus executeCommands(const std::vector<DisplayCommand>& commands,
                                       std::vector<CommandResultPayload>* results) override;
    ndk::ScopedAStatus getActiveConfig(int64_t display, int32_t* config) override;
    ndk::ScopedAStatus getColorModes(int64_t display, std::vector<ColorMode>* colorModes) override;
    ndk::ScopedAStatus getDataspaceSaturationMatrix(common::Dataspace dataspace,
                                                    std::vector<float>* matrix) override;
    ndk::ScopedAStatus getDisplayAttribute(int64_t display, int32_t config,
                                           DisplayAttribute attribute, int32_t* value) override;
    ndk::ScopedAStatus getDisplayCapabilities(int64_t display,
                                              std::vector<DisplayCapability>* caps) override;
    ndk::ScopedAStatus getDisplayConfigs(int64_t display, std::vector<int32_t>* configs) override;
    ndk::ScopedAStatus getDisplayConnectionType(int64_t display,
                                                DisplayConnectionType* type) override;
    ndk::ScopedAStatus getDisplayIdentificationData(int64_t display,
                                                    DisplayIdentification* id) override;
    ndk::ScopedAStatus getDisplayName(int64_t display, std::string* name) override;
    ndk::ScopedAStatus getDisplayVsyncPeriod(int64_t display, int32_t* vsyncPeriod) override;
    ndk::ScopedAStatus getDisplayedContentSample(int64_t display, int64_t maxFrames,
                                                 int64_t timestamp,
                                                 DisplayContentSample* samples) override;
    ndk::ScopedAStatus getDisplayedContentSamplingAttributes(
            int64_t display, DisplayContentSamplingAttributes* attrs) override;
    ndk::ScopedAStatus getDisplayPhysicalOrientation(int64_t display,
                                                     common::Transform* orientation) override;
    ndk::ScopedAStatus getHdrCapabilities(int64_t display, HdrCapabilities* caps) override;
    ndk::ScopedAStatus getOverlaySupport(OverlayProperties* caps) override;
    ndk::ScopedAStatus getMaxVirtualDisplayCount(int32_t* count) override;
    ndk::ScopedAStatus getPerFrameMetadataKeys(int64_t display,
                                               std::vector<PerFrameMetadataKey>* keys) override;
    ndk::ScopedAStatus getReadbackBufferAttributes(int64_t display,
                                                   ReadbackBufferAttributes* attrs) override;
    ndk::ScopedAStatus getReadbackBufferFence(int64_t display,
                                              ndk::ScopedFileDescriptor* acquireFence) override;
    ndk::ScopedAStatus getRenderIntents(int64_t display, ColorMode mode,
                                        std::vector<RenderIntent>* intents) override;
    ndk::ScopedAStatus getSupportedContentTypes(int64_t display,
                                                std::vector<ContentType>* types) override;
    ndk::ScopedAStatus getDisplayDecorationSupport(
            int64_t display, std::optional<common::DisplayDecorationSupport>* support) override;
    ndk::ScopedAStatus registerCallback(
            const std::shared_ptr<IComposerCallback>& callback) override;
    ndk::ScopedAStatus setActiveConfig(int64_t display, int32_t config) override;
    ndk::ScopedAStatus setActiveConfigWithConstraints(
            int64_t display, int32_t config, const VsyncPeriodChangeConstraints& constraints,
            VsyncPeriodChangeTimeline* timeline) override;
    ndk::ScopedAStatus setBootDisplayConfig(int64_t display, int32_t config) override;
    ndk::ScopedAStatus clearBootDisplayConfig(int64_t display) override;
    ndk::ScopedAStatus getPreferredBootDisplayConfig(int64_t display, int32_t* config) override;
    ndk::ScopedAStatus getHdrConversionCapabilities(
            std::vector<common::HdrConversionCapability>*) override;
    ndk::ScopedAStatus setHdrConversionStrategy(const common::HdrConversionStrategy&,
                                                common::Hdr* preferredHdrOutputType) override;
    ndk::ScopedAStatus setAutoLowLatencyMode(int64_t display, bool on) override;
    ndk::ScopedAStatus setClientTargetSlotCount(int64_t display, int32_t count) override;
    ndk::ScopedAStatus setColorMode(int64_t display, ColorMode mode, RenderIntent intent) override;
    ndk::ScopedAStatus setContentType(int64_t display, ContentType type) override;
    ndk::ScopedAStatus setDisplayedContentSamplingEnabled(int64_t display, bool enable,
                                                          FormatColorComponent componentMask,
                                                          int64_t maxFrames) override;
    ndk::ScopedAStatus setPowerMode(int64_t display, PowerMode mode) override;
    ndk::ScopedAStatus setReadbackBuffer(int64_t display, const AidlNativeHandle& buffer,
                                         const ndk::ScopedFileDescriptor& releaseFence) override;
    ndk::ScopedAStatus setVsyncEnabled(int64_t display, bool enabled) override;
    ndk::ScopedAStatus setIdleTimerEnabled(int64_t display, int32_t timeout) override;
    ndk::ScopedAStatus setRefreshRateChangedCallbackDebugEnabled(int64_t /* display */,
                                                                 bool /* enabled */) override;
    ndk::ScopedAStatus getDisplayConfigurations(
            int64_t display, int32_t maxFrameIntervalNs,
            std::vector<DisplayConfiguration>* configs) override;
    ndk::ScopedAStatus notifyExpectedPresent(int64_t display,
                                             const ClockMonotonicTimestamp& expectedPresentTime,
                                             int32_t frameIntervalNs) override;

protected:
    ::ndk::SpAIBinder createBinder() override;

private:
    void destroyResources();

    IComposerHal* mHal;
    std::unique_ptr<IResourceManager> mResources;
    std::function<void()> mOnClientDestroyed;
    std::unique_ptr<HalEventCallback> mHalEventCallback;
};

} // namespace aidl::android::hardware::graphics::composer3::impl


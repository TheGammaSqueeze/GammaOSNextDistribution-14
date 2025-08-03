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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "ComposerClient.h"

#include <android-base/logging.h>
#include <android/binder_ibinder_platform.h>
#include <hardware/hwcomposer2.h>

#include "Util.h"

namespace aidl::android::hardware::graphics::composer3::impl {

bool ComposerClient::init() {
    DEBUG_FUNC();
    mResources = IResourceManager::create();
    if (!mResources) {
        LOG(ERROR) << "failed to create composer resources";
        return false;
    }

    return true;
}

ComposerClient::~ComposerClient() {
    DEBUG_FUNC();
    LOG(DEBUG) << "destroying composer client";

    mHal->unregisterEventCallback();
    destroyResources();

    if (mOnClientDestroyed) {
        mOnClientDestroyed();
    }

    LOG(DEBUG) << "removed composer client";
}

// no need to check nullptr for output parameter, the aidl stub code won't pass nullptr
ndk::ScopedAStatus ComposerClient::createLayer(int64_t display, int32_t bufferSlotCount,
                                               int64_t* layer) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->createLayer(display, layer);
    if (!err) {
        err = mResources->addLayer(display, *layer, bufferSlotCount);
        if (err) {
            layer = 0;
        }
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::createVirtualDisplay(int32_t width, int32_t height,
                                                        AidlPixelFormat formatHint,
                                                        int32_t outputBufferSlotCount,
                                                        VirtualDisplay* display) {
    DEBUG_FUNC();
    auto err = mHal->createVirtualDisplay(width, height, formatHint, display);
    if (!err) {
        err = mResources->addVirtualDisplay(display->display, outputBufferSlotCount);
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayConfigurations(
        int64_t display, int32_t maxFrameIntervalNs, std::vector<DisplayConfiguration>* configs) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayConfigurations(display, maxFrameIntervalNs, configs);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::notifyExpectedPresent(
        int64_t display, const ClockMonotonicTimestamp& expectedPresentTime,
        int32_t frameIntervalNs) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->notifyExpectedPresent(display, expectedPresentTime, frameIntervalNs);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::destroyLayer(int64_t display, int64_t layer) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->destroyLayer(display, layer);
    if (!err) {
        err = mResources->removeLayer(display, layer);
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::destroyVirtualDisplay(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->destroyVirtualDisplay(display);
    if (!err) {
        err = mResources->removeDisplay(display);
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::executeCommands(const std::vector<DisplayCommand>& commands,
                                                   std::vector<CommandResultPayload>* results) {
    int64_t display = commands.empty() ? -1 : commands[0].display;
    DEBUG_DISPLAY_FUNC(display);
    ComposerCommandEngine engine(mHal, mResources.get());

    auto err = engine.init();
    if (err != ::android::NO_ERROR) {
        LOG(ERROR) << "executeCommands(): init ComposerCommandEngine failed " << err;
        return TO_BINDER_STATUS(err);
    }

    err = engine.execute(commands, results);
    if (err != ::android::NO_ERROR) {
        LOG(ERROR) << "executeCommands(): execute failed " << err;
        return TO_BINDER_STATUS(err);
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getActiveConfig(int64_t display, int32_t* config) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getActiveConfig(display, config);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getColorModes(int64_t display,
                                                 std::vector<ColorMode>* colorModes) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getColorModes(display, colorModes);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDataspaceSaturationMatrix(common::Dataspace dataspace,
                                                                std::vector<float>* matrix) {
    DEBUG_FUNC();
    if (dataspace != common::Dataspace::SRGB_LINEAR) {
        return TO_BINDER_STATUS(EX_BAD_PARAMETER);
    }

    auto err = mHal->getDataspaceSaturationMatrix(dataspace, matrix);
    if (err) {
        constexpr std::array<float, 16> unit {
                1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        };
        matrix->clear();
        matrix->insert(matrix->begin(), unit.begin(), unit.end());
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayAttribute(int64_t display, int32_t config,
                                                       DisplayAttribute attribute, int32_t* value) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayAttribute(display, config, attribute, value);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayCapabilities(int64_t display,
                                                          std::vector<DisplayCapability>* caps) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayCapabilities(display, caps);
    if (err) {
        return TO_BINDER_STATUS(err);
    }

    bool support = false;
    err = mHal->getDisplayIdleTimerSupport(display, support);
    if (err != ::android::OK) {
        LOG(ERROR) << "failed to getDisplayIdleTimerSupport: " << err;
    }

    if (support) {
        caps->push_back(DisplayCapability::DISPLAY_IDLE_TIMER);
    }

    err = mHal->getDisplayMultiThreadedPresentSupport(display, support);
    if (err != ::android::OK) {
        LOG(ERROR) << "failed to getDisplayMultiThreadedPresentSupport: " << err;
        return TO_BINDER_STATUS(err);
    }

    if (support) {
        caps->push_back(DisplayCapability::MULTI_THREADED_PRESENT);
    }

    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayConfigs(int64_t display,
                                                     std::vector<int32_t>* configs) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayConfigs(display, configs);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayConnectionType(int64_t display,
                                                            DisplayConnectionType* type) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayConnectionType(display, type);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayIdentificationData(int64_t display,
                                                                DisplayIdentification* id) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayIdentificationData(display, id);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayName(int64_t display, std::string* name) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayName(display, name);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayVsyncPeriod(int64_t display, int32_t* vsyncPeriod) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayVsyncPeriod(display, vsyncPeriod);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayedContentSample(int64_t display, int64_t maxFrames,
                                                             int64_t timestamp,
                                                             DisplayContentSample* samples) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayedContentSample(display, maxFrames, timestamp, samples);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayedContentSamplingAttributes(
        int64_t display, DisplayContentSamplingAttributes* attrs) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayedContentSamplingAttributes(display, attrs);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayPhysicalOrientation(int64_t display,
                                                                 common::Transform* orientation) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getDisplayPhysicalOrientation(display, orientation);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getHdrCapabilities(int64_t display, HdrCapabilities* caps) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getHdrCapabilities(display, caps);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getOverlaySupport(OverlayProperties* caps) {
    DEBUG_FUNC();
    auto err = mHal->getOverlaySupport(caps);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getMaxVirtualDisplayCount(int32_t* count) {
    DEBUG_FUNC();
    auto err = mHal->getMaxVirtualDisplayCount(count);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getPerFrameMetadataKeys(int64_t display,
                                                           std::vector<PerFrameMetadataKey>* keys) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getPerFrameMetadataKeys(display, keys);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getReadbackBufferAttributes(int64_t display,
                                                               ReadbackBufferAttributes* attrs) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getReadbackBufferAttributes(display, attrs);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getReadbackBufferFence(int64_t display,
                                                          ndk::ScopedFileDescriptor* acquireFence) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getReadbackBufferFence(display, acquireFence);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getRenderIntents(int64_t display, ColorMode mode,
                                                    std::vector<RenderIntent>* intents) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getRenderIntents(display, mode, intents);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getSupportedContentTypes(int64_t display,
                                                            std::vector<ContentType>* types) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getSupportedContentTypes(display, types);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getDisplayDecorationSupport(
        int64_t display, std::optional<common::DisplayDecorationSupport>* supportStruct) {
    DEBUG_DISPLAY_FUNC(display);
    bool support = false;
    auto err = mHal->getRCDLayerSupport(display, support);
    if (err != ::android::OK) {
        LOG(ERROR) << "failed to getRCDLayerSupport: " << err;
    }
    if (support) {
        // TODO (b/218499393): determine from mHal instead of hard coding.
        auto& s = supportStruct->emplace();
        s.format = common::PixelFormat::R_8;
        s.alphaInterpretation = common::AlphaInterpretation::COVERAGE;
    } else {
        supportStruct->reset();
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::registerCallback(
        const std::shared_ptr<IComposerCallback>& callback) {
    DEBUG_FUNC();
    // no locking as we require this function to be called only once
    mHalEventCallback = std::make_unique<HalEventCallback>(mHal, mResources.get(), callback);
    mHal->registerEventCallback(mHalEventCallback.get());
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ComposerClient::setActiveConfig(int64_t display, int32_t config) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setActiveConfig(display, config);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setActiveConfigWithConstraints(
        int64_t display, int32_t config, const VsyncPeriodChangeConstraints& constraints,
        VsyncPeriodChangeTimeline* timeline) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setActiveConfigWithConstraints(display, config, constraints, timeline);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setBootDisplayConfig(int64_t display, int32_t config) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setBootDisplayConfig(display, config);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::clearBootDisplayConfig(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->clearBootDisplayConfig(display);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getPreferredBootDisplayConfig(int64_t display, int32_t* config) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->getPreferredBootDisplayConfig(display, config);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::getHdrConversionCapabilities(
        std::vector<common::HdrConversionCapability>* hdrConversionCapabilities) {
    DEBUG_FUNC();
    auto err = mHal->getHdrConversionCapabilities(hdrConversionCapabilities);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setHdrConversionStrategy(
        const common::HdrConversionStrategy& hdrConversionStrategy,
        common::Hdr* preferredHdrOutputType) {
    DEBUG_FUNC();
    auto err = mHal->setHdrConversionStrategy(hdrConversionStrategy, preferredHdrOutputType);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setAutoLowLatencyMode(int64_t display, bool on) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setAutoLowLatencyMode(display, on);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setClientTargetSlotCount(int64_t display, int32_t count) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mResources->setDisplayClientTargetCacheSize(display, count);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setColorMode(int64_t display, ColorMode mode,
                                                RenderIntent intent) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setColorMode(display, mode, intent);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setContentType(int64_t display, ContentType type) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setContentType(display, type);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setDisplayedContentSamplingEnabled(
        int64_t display, bool enable, FormatColorComponent componentMask, int64_t maxFrames) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setDisplayedContentSamplingEnabled(display, enable, componentMask, maxFrames);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setPowerMode(int64_t display, PowerMode mode) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setPowerMode(display, mode);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setReadbackBuffer(
        int64_t display, const AidlNativeHandle& aidlBuffer,
        const ndk::ScopedFileDescriptor& releaseFence) {
    DEBUG_DISPLAY_FUNC(display);
    buffer_handle_t readbackBuffer;
    // Note ownership of the buffer is not passed to resource manager.
    buffer_handle_t buffer = ::android::makeFromAidl(aidlBuffer);
    auto bufReleaser = mResources->createReleaser(true /* isBuffer */);
    auto err = mResources->getDisplayReadbackBuffer(display, buffer,
                                                    readbackBuffer, bufReleaser.get());
    if (!err) {
        err = mHal->setReadbackBuffer(display, readbackBuffer, releaseFence);
    }
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setVsyncEnabled(int64_t display, bool enabled) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setVsyncEnabled(display, enabled);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setIdleTimerEnabled(int64_t display, int32_t timeout) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setIdleTimerEnabled(display, timeout);
    return TO_BINDER_STATUS(err);
}

ndk::ScopedAStatus ComposerClient::setRefreshRateChangedCallbackDebugEnabled(int64_t display,
                                                                             bool enabled) {
    DEBUG_DISPLAY_FUNC(display);
    auto err = mHal->setRefreshRateChangedCallbackDebugEnabled(display, enabled);
    return TO_BINDER_STATUS(err);
}

void ComposerClient::HalEventCallback::onRefreshRateChangedDebug(
        const RefreshRateChangedDebugData& data) {
    DEBUG_DISPLAY_FUNC(data.display);
    auto ret = mCallback->onRefreshRateChangedDebug(data);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onRefreshRateChangedDebug:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onHotplug(int64_t display, bool connected) {
    DEBUG_DISPLAY_FUNC(display);
    processDisplayResources(display, connected);
    auto ret = mCallback->onHotplug(display, connected);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onHotplug:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onRefresh(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    mResources->setDisplayMustValidateState(display, true);
    auto ret = mCallback->onRefresh(display);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onRefresh:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onVsync(int64_t display, int64_t timestamp,
                                               int32_t vsyncPeriodNanos) {
    DEBUG_DISPLAY_FUNC(display);
    auto ret = mCallback->onVsync(display, timestamp, vsyncPeriodNanos);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onVsync:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onVsyncPeriodTimingChanged(
        int64_t display, const VsyncPeriodChangeTimeline& timeline) {
    DEBUG_DISPLAY_FUNC(display);
    auto ret = mCallback->onVsyncPeriodTimingChanged(display, timeline);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onVsyncPeriodTimingChanged:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onVsyncIdle(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    auto ret = mCallback->onVsyncIdle(display);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onVsyncIdle:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onSeamlessPossible(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    auto ret = mCallback->onSeamlessPossible(display);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onSealmessPossible:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::onHotplugEvent(int64_t display,
                                                      common::DisplayHotplugEvent event) {
    DEBUG_DISPLAY_FUNC(display);
    processDisplayResources(display, event == common::DisplayHotplugEvent::CONNECTED);
    auto ret = mCallback->onHotplugEvent(display, event);
    if (!ret.isOk()) {
        LOG(ERROR) << "failed to send onHotplugEvent:" << ret.getDescription();
    }
}

void ComposerClient::HalEventCallback::processDisplayResources(int64_t display, bool connected) {
    if (connected) {
        if (mResources->hasDisplay(display)) {
            // This is a subsequent hotplug "connected" for a display. This signals a
            // display change and thus the framework may want to reallocate buffers. We
            // need to free all cached handles, since they are holding a strong reference
            // to the underlying buffers.
            cleanDisplayResources(display);
            mResources->removeDisplay(display);
        }
        mResources->addPhysicalDisplay(display);
    } else {
        mResources->removeDisplay(display);
    }
}

void ComposerClient::HalEventCallback::cleanDisplayResources(int64_t display) {
    DEBUG_DISPLAY_FUNC(display);
    size_t cacheSize;
    auto err = mResources->getDisplayClientTargetCacheSize(display, &cacheSize);
    if (!err) {
        for (int slot = 0; slot < cacheSize; slot++) {
            // Replace the buffer slots with NULLs. Keep the old handle until it is
            // replaced in ComposerHal, otherwise we risk leaving a dangling pointer.
            buffer_handle_t outHandle;
            auto bufReleaser = mResources->createReleaser(true /* isBuffer */);
            err = mResources->getDisplayClientTarget(display, slot, /*useCache*/ true,
                                                    /*rawHandle*/ nullptr, outHandle,
                                                    bufReleaser.get());
            if (err) {
                continue;
            }
            const std::vector<common::Rect> damage;
            ndk::ScopedFileDescriptor fence; // empty fence
            common::Dataspace dataspace = common::Dataspace::UNKNOWN;
            err = mHal->setClientTarget(display, outHandle, fence, dataspace, damage);
            if (err) {
                LOG(ERROR) << "Can't clean slot " << slot
                           << " of the client target buffer cache for display" << display;
            }
        }
    } else {
        LOG(ERROR) << "Can't clean client target cache for display " << display;
    }

    err = mResources->getDisplayOutputBufferCacheSize(display, &cacheSize);
    if (!err) {
        for (int slot = 0; slot < cacheSize; slot++) {
            // Replace the buffer slots with NULLs. Keep the old handle until it is
            // replaced in ComposerHal, otherwise we risk leaving a dangling pointer.
            buffer_handle_t outputBuffer;
            auto bufReleaser = mResources->createReleaser(true /* isBuffer */);
            err = mResources->getDisplayOutputBuffer(display, slot, /*useCache*/ true,
                                                    /*rawHandle*/ nullptr, outputBuffer,
                                                    bufReleaser.get());
            if (err) {
                continue;
            }
            ndk::ScopedFileDescriptor emptyFd;
            err = mHal->setOutputBuffer(display, outputBuffer, /*fence*/ emptyFd);
            if (err) {
                LOG(ERROR) << "Can't clean slot " << slot
                           << " of the output buffer cache for display " << display;
            }
        }
    } else {
        LOG(ERROR) << "Can't clean output buffer cache for display " << display;
    }
}

void ComposerClient::destroyResources() {
    DEBUG_FUNC();
    // We want to call hwc2_close here (and move hwc2_open to the
    // constructor), with the assumption that hwc2_close would
    //
    //  - clean up all resources owned by the client
    //  - make sure all displays are blank (since there is no layer)
    //
    // But since SF used to crash at this point, different hwcomposer2
    // implementations behave differently on hwc2_close.  Our only portable
    // choice really is to abort().  But that is not an option anymore
    // because we might also have VTS or VR as clients that can come and go.
    //
    // Below we manually clean all resources (layers and virtual
    // displays), and perform a presentDisplay afterwards.
    mResources->clear([this](int64_t display, bool isVirtual, const std::vector<int64_t> layers) {
        LOG(WARNING) << "destroying client resources for display " << display;
        for (auto layer : layers) {
            mHal->destroyLayer(display, layer);
        }

        if (isVirtual) {
            mHal->destroyVirtualDisplay(display);
        } else {
            LOG(WARNING) << "performing a final presentDisplay";
            std::vector<int64_t> changedLayers;
            std::vector<Composition> compositionTypes;
            uint32_t displayRequestMask = 0;
            std::vector<int64_t> requestedLayers;
            std::vector<int32_t> requestMasks;
            ClientTargetProperty clientTargetProperty;
            DimmingStage dimmingStage;
            mHal->validateDisplay(display, &changedLayers, &compositionTypes, &displayRequestMask,
                                  &requestedLayers, &requestMasks, &clientTargetProperty,
                                  &dimmingStage);
            mHal->acceptDisplayChanges(display);

            ndk::ScopedFileDescriptor presentFence;
            std::vector<int64_t> releasedLayers;
            std::vector<ndk::ScopedFileDescriptor> releaseFences;
            mHal->presentDisplay(display, presentFence, &releasedLayers, &releaseFences);
        }
    });
    mResources.reset();
}

::ndk::SpAIBinder ComposerClient::createBinder() {
    auto binder = BnComposerClient::createBinder();
    AIBinder_setInheritRt(binder.get(), true);
    return binder;
}

} // namespace aidl::android::hardware::graphics::composer3::impl

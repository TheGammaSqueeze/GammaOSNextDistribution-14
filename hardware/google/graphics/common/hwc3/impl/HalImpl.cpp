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

#include "HalImpl.h"

#include <aidl/android/hardware/graphics/composer3/IComposerCallback.h>
#include <android-base/logging.h>
#include <hardware/hwcomposer2.h>

#include "ExynosDevice.h"
#include "ExynosDeviceModule.h"
#include "ExynosDisplay.h"
#include "ExynosHWCService.h"
#include "ExynosLayer.h"
#include "TranslateHwcAidl.h"
#include "Util.h"

using namespace SOC_VERSION;

namespace {

static constexpr int32_t kMinComposerInterfaceVersionForVrrApi = 3;

};

namespace aidl::android::hardware::graphics::composer3::impl {

std::unique_ptr<IComposerHal> IComposerHal::create(int32_t composerInterfaceVersion) {
    bool vrrApiSupported = composerInterfaceVersion >= kMinComposerInterfaceVersionForVrrApi;
    auto device = std::make_unique<ExynosDeviceModule>(vrrApiSupported);
    if (!device) {
        return nullptr;
    }
    return std::make_unique<HalImpl>(std::move(device));
}

namespace hook {

void hotplug(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                        int32_t connected) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;

    h2a::translate(hwcDisplay, display);
    hal->getEventCallback()->onHotplug(display, connected == HWC2_CONNECTION_CONNECTED);
}

void refresh(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;

    h2a::translate(hwcDisplay, display);
    hal->getEventCallback()->onRefresh(display);
}

void vsync(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                           int64_t timestamp, hwc2_vsync_period_t hwcVsyncPeriodNanos) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;
    int32_t vsyncPeriodNanos;

    h2a::translate(hwcDisplay, display);
    h2a::translate(hwcVsyncPeriodNanos, vsyncPeriodNanos);
    hal->getEventCallback()->onVsync(display, timestamp, vsyncPeriodNanos);
}

void vsyncPeriodTimingChanged(hwc2_callback_data_t callbackData,
                                         hwc2_display_t hwcDisplay,
                                         hwc_vsync_period_change_timeline_t* hwcTimeline) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;
    VsyncPeriodChangeTimeline timeline;

    h2a::translate(hwcDisplay, display);
    h2a::translate(*hwcTimeline, timeline);
    hal->getEventCallback()->onVsyncPeriodTimingChanged(display, timeline);
}

void vsyncIdle(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;

    h2a::translate(hwcDisplay, display);
    hal->getEventCallback()->onVsyncIdle(display);
}

void seamlessPossible(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;

    h2a::translate(hwcDisplay, display);
    hal->getEventCallback()->onSeamlessPossible(display);
}

void refreshRateChangedDebug(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                             hwc2_vsync_period_t hwcVsyncPeriodNanos,
                             int32_t hwcRefreshPeriodNanos) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;
    int32_t vsyncPeriodNanos;

    h2a::translate(hwcDisplay, display);
    h2a::translate(hwcVsyncPeriodNanos, vsyncPeriodNanos);
    // TODO (b/314527560) Update refreshPeriodNanos for VRR display
    hal->getEventCallback()->onRefreshRateChangedDebug(RefreshRateChangedDebugData{
            .display = display,
            .vsyncPeriodNanos = vsyncPeriodNanos,
            .refreshPeriodNanos = hwcRefreshPeriodNanos,
    });
}

void hotplugEvent(hwc2_callback_data_t callbackData, hwc2_display_t hwcDisplay,
                  common::DisplayHotplugEvent hotplugEvent) {
    auto hal = static_cast<HalImpl*>(callbackData);
    int64_t display;

    h2a::translate(hwcDisplay, display);
    hal->getEventCallback()->onHotplugEvent(display, hotplugEvent);
}

} // nampesapce hook

HalImpl::HalImpl(std::unique_ptr<ExynosDevice> device) : mDevice(std::move(device)) {
    initCaps();
#ifdef USES_HWC_SERVICES
    LOG(DEBUG) << "Start HWCService";
    mHwcCtx = std::make_unique<ExynosHWCCtx>();
    memset(&mHwcCtx->base, 0, sizeof(mHwcCtx->base));
    mHwcCtx->device = mDevice.get();

    auto hwcService = ::android::ExynosHWCService::getExynosHWCService();
    hwcService->setExynosHWCCtx(mHwcCtx.get());
    // This callback is for DP hotplug event if connected
    // hwcService->setBootFinishedCallback(...);
#endif
}

void HalImpl::initCaps() {
    uint32_t count = 0;
    mDevice->getCapabilities(&count, nullptr);

    std::vector<int32_t> halCaps(count);
    mDevice->getCapabilities(&count, halCaps.data());

    for (auto hwcCap : halCaps) {
        Capability cap;
        h2a::translate(hwcCap, cap);
        mCaps.insert(cap);
    }

    mCaps.insert(Capability::BOOT_DISPLAY_CONFIG);
    mCaps.insert(Capability::REFRESH_RATE_CHANGED_CALLBACK_DEBUG);
}

int32_t HalImpl::getHalDisplay(int64_t display, ExynosDisplay*& halDisplay) {
    hwc2_display_t hwcDisplay;
    a2h::translate(display, hwcDisplay);
    halDisplay = mDevice->getDisplay(static_cast<uint32_t>(hwcDisplay));

    if (!halDisplay) { [[unlikely]]
        return HWC2_ERROR_BAD_DISPLAY;
    }
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getHalLayer(int64_t display, int64_t layer, ExynosLayer*& halLayer) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_layer_t hwcLayer;
    a2h::translate(layer, hwcLayer);
    halLayer = halDisplay->checkLayer(hwcLayer);
    if (!halLayer) { [[unlikely]]
        return HWC2_ERROR_BAD_LAYER;
    }

    return HWC2_ERROR_NONE;
}

bool HalImpl::hasCapability(Capability cap) {
    return mCaps.find(cap) != mCaps.end();
}

void HalImpl::getCapabilities(std::vector<Capability>* caps) {
    caps->clear();
    caps->insert(caps->begin(), mCaps.begin(), mCaps.end());
}

void HalImpl::dumpDebugInfo(std::string* output) {
    if (output == nullptr) return;

    String8 result;
    mDevice->dump(result);

    output->resize(result.size());
    output->assign(result.c_str());
}

void HalImpl::registerEventCallback(EventCallback* callback) {
    mEventCallback = callback;

    mDevice->registerCallback(HWC2_CALLBACK_HOTPLUG, this,
                              reinterpret_cast<hwc2_function_pointer_t>(hook::hotplug));
    mDevice->registerCallback(HWC2_CALLBACK_REFRESH, this,
                              reinterpret_cast<hwc2_function_pointer_t>(hook::refresh));
    mDevice->registerCallback(HWC2_CALLBACK_VSYNC_2_4, this,
                     reinterpret_cast<hwc2_function_pointer_t>(hook::vsync));
    mDevice->registerCallback(HWC2_CALLBACK_VSYNC_PERIOD_TIMING_CHANGED, this,
                     reinterpret_cast<hwc2_function_pointer_t>(hook::vsyncPeriodTimingChanged));
    mDevice->registerCallback(HWC2_CALLBACK_SEAMLESS_POSSIBLE, this,
                     reinterpret_cast<hwc2_function_pointer_t>(hook::seamlessPossible));

    // register HWC3 Callback
    mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onVsyncIdle, this,
                                  reinterpret_cast<hwc2_function_pointer_t>(hook::vsyncIdle));
    mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onRefreshRateChangedDebug, this,
                                  reinterpret_cast<hwc2_function_pointer_t>(
                                          hook::refreshRateChangedDebug));
    // Don't register onHotplugEvent until it's available in nextfood (b/323291596)
    // mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onHotplugEvent, this,
    //                             reinterpret_cast<hwc2_function_pointer_t>(hook::hotplugEvent));
}

void HalImpl::unregisterEventCallback() {
    mDevice->registerCallback(HWC2_CALLBACK_HOTPLUG, this, nullptr);
    mDevice->registerCallback(HWC2_CALLBACK_REFRESH, this, nullptr);
    mDevice->registerCallback(HWC2_CALLBACK_VSYNC_2_4, this, nullptr);
    mDevice->registerCallback(HWC2_CALLBACK_VSYNC_PERIOD_TIMING_CHANGED, this, nullptr);
    mDevice->registerCallback(HWC2_CALLBACK_SEAMLESS_POSSIBLE, this, nullptr);

    // unregister HWC3 Callback
    mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onVsyncIdle, this, nullptr);
    mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onRefreshRateChangedDebug, this,
                                  nullptr);
    // Don't register onHotplugEvent until it's available in nextfood (b/323291596)
    // mDevice->registerHwc3Callback(IComposerCallback::TRANSACTION_onHotplugEvent, this, nullptr);

    mEventCallback = nullptr;
}

int32_t HalImpl::acceptDisplayChanges(int64_t display) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->acceptDisplayChanges();
}

int32_t HalImpl::createLayer(int64_t display, int64_t* outLayer) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_layer_t hwcLayer = 0;
    RET_IF_ERR(halDisplay->createLayer(&hwcLayer));

    h2a::translate(hwcLayer, *outLayer);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::destroyLayer(int64_t display, int64_t layer) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halDisplay->destroyLayer(reinterpret_cast<hwc2_layer_t>(halLayer));
}

int32_t HalImpl::createVirtualDisplay(uint32_t width, uint32_t height, AidlPixelFormat format,
                                      VirtualDisplay* outDisplay) {
    int32_t hwcFormat;
    a2h::translate(format, hwcFormat);
    hwc2_display_t hwcDisplay = getDisplayId(HWC_DISPLAY_VIRTUAL, 0);
    auto halDisplay = mDevice->getDisplay(static_cast<uint32_t>(hwcDisplay));
    if (!halDisplay) {
        return HWC2_ERROR_BAD_PARAMETER;
    }

    RET_IF_ERR(mDevice->createVirtualDisplay(width, height, &hwcFormat, halDisplay));

    h2a::translate(hwcDisplay, outDisplay->display);
    h2a::translate(hwcFormat, outDisplay->format);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::destroyVirtualDisplay(int64_t display) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return mDevice->destroyVirtualDisplay(halDisplay);
}

int32_t HalImpl::getActiveConfig(int64_t display, int32_t* outConfig) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_config_t hwcConfig;
    RET_IF_ERR(halDisplay->getActiveConfig(&hwcConfig));

    h2a::translate(hwcConfig, *outConfig);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getColorModes(int64_t display, std::vector<ColorMode>* outModes) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getColorModes(&count, nullptr));

    std::vector<int32_t> hwcModes(count);
    RET_IF_ERR(halDisplay->getColorModes(&count, hwcModes.data()));

    h2a::translate(hwcModes, *outModes);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDataspaceSaturationMatrix([[maybe_unused]] common::Dataspace dataspace,
                                              std::vector<float>* matrix) {
    // Pixel HWC does not support dataspace saturation matrix, return unit matrix.
    std::vector<float> unitMatrix = {
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };

    *matrix = std::move(unitMatrix);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayAttribute(int64_t display, int32_t config,
                                     DisplayAttribute attribute, int32_t* outValue) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_config_t hwcConfig;
    int32_t hwcAttr;
    a2h::translate(config, hwcConfig);
    a2h::translate(attribute, hwcAttr);

    auto err = halDisplay->getDisplayAttribute(hwcConfig, hwcAttr, outValue);
    if (err != HWC2_ERROR_NONE && *outValue == -1) {
        return HWC2_ERROR_BAD_PARAMETER;
    }
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayBrightnessSupport(int64_t display, bool& outSupport) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->getDisplayBrightnessSupport(&outSupport);
}

int32_t HalImpl::getDisplayCapabilities(int64_t display,
                                        std::vector<DisplayCapability>* caps) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getDisplayCapabilities(&count, nullptr));

    std::vector<uint32_t> hwcCaps(count);
    RET_IF_ERR(halDisplay->getDisplayCapabilities(&count, hwcCaps.data()));

    h2a::translate(hwcCaps, *caps);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayConfigs(int64_t display, std::vector<int32_t>* configs) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getDisplayConfigs(&count, nullptr));

    std::vector<hwc2_config_t> hwcConfigs(count);
    RET_IF_ERR(halDisplay->getDisplayConfigs(&count, hwcConfigs.data()));

    h2a::translate(hwcConfigs, *configs);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayConfigurations(int64_t display, int32_t,
                                          std::vector<DisplayConfiguration>* outConfigs) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    std::vector<int32_t> configIds;
    RET_IF_ERR(getDisplayConfigs(display, &configIds));

    for (const auto configId : configIds) {
        DisplayConfiguration config;
        config.configId = configId;
        // Get required display attributes
        RET_IF_ERR(getDisplayAttribute(display, configId, DisplayAttribute::WIDTH, &config.width));
        RET_IF_ERR(
                getDisplayAttribute(display, configId, DisplayAttribute::HEIGHT, &config.height));
        RET_IF_ERR(getDisplayAttribute(display, configId, DisplayAttribute::VSYNC_PERIOD,
                                       &config.vsyncPeriod));
        RET_IF_ERR(getDisplayAttribute(display, configId, DisplayAttribute::CONFIG_GROUP,
                                       &config.configGroup));
        // Get optional display attributes
        int32_t dpiX, dpiY;
        auto statusDpiX = getDisplayAttribute(display, configId, DisplayAttribute::DPI_X, &dpiX);
        auto statusDpiY = getDisplayAttribute(display, configId, DisplayAttribute::DPI_Y, &dpiY);
        // TODO(b/294120341): getDisplayAttribute for DPI should return dots per inch
        if (statusDpiX == HWC2_ERROR_NONE && statusDpiY == HWC2_ERROR_NONE) {
            config.dpi = {dpiX / 1000.0f, dpiY / 1000.0f};
        }
        // Determine whether there is a need to configure VRR.
        hwc2_config_t hwcConfigId;
        a2h::translate(configId, hwcConfigId);
        std::optional<VrrConfig_t> vrrConfig = halDisplay->getVrrConfigs(hwcConfigId);
        if (vrrConfig.has_value() && vrrConfig->isFullySupported) {
            // TODO(b/290843234): complete the remaining values within vrrConfig.
            VrrConfig hwc3VrrConfig;
            VrrConfig::NotifyExpectedPresentConfig notifyExpectedPresentConfig;
            hwc3VrrConfig.minFrameIntervalNs = vrrConfig->minFrameIntervalNs;
            if (vrrConfig->notifyExpectedPresentConfig.has_value()) {
                notifyExpectedPresentConfig.headsUpNs =
                        vrrConfig->notifyExpectedPresentConfig->HeadsUpNs;
                notifyExpectedPresentConfig.timeoutNs =
                        vrrConfig->notifyExpectedPresentConfig->TimeoutNs;
                hwc3VrrConfig.notifyExpectedPresentConfig =
                        std::make_optional(notifyExpectedPresentConfig);
            }
            config.vrrConfig = std::make_optional(hwc3VrrConfig);
        }
        outConfigs->push_back(config);
    }

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::notifyExpectedPresent(int64_t display,
                                       const ClockMonotonicTimestamp& expectedPresentTime,
                                       int32_t frameIntervalNs) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    RET_IF_ERR(
            halDisplay->notifyExpectedPresent(expectedPresentTime.timestampNanos, frameIntervalNs));
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayConnectionType(int64_t display, DisplayConnectionType* outType) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t hwcType = HWC2_DISPLAY_CONNECTION_TYPE_INTERNAL;
    RET_IF_ERR(halDisplay->getDisplayConnectionType(&hwcType));
    h2a::translate(hwcType, *outType);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayIdentificationData(int64_t display, DisplayIdentification *id) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint8_t port;
    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getDisplayIdentificationData(&port, &count, nullptr));

    id->data.resize(count);
    RET_IF_ERR(halDisplay->getDisplayIdentificationData(&port, &count, id->data.data()));

    h2a::translate(port, id->port);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayName(int64_t display, std::string* outName) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getDisplayName(&count, nullptr));

    outName->resize(count);
    RET_IF_ERR(halDisplay->getDisplayName(&count, outName->data()));

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayVsyncPeriod(int64_t display, int32_t* outVsyncPeriod) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_vsync_period_t hwcVsyncPeriod;
    RET_IF_ERR(halDisplay->getDisplayVsyncPeriod(&hwcVsyncPeriod));

    h2a::translate(hwcVsyncPeriod, *outVsyncPeriod);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDisplayedContentSample([[maybe_unused]] int64_t display,
                                           [[maybe_unused]] int64_t maxFrames,
                                           [[maybe_unused]] int64_t timestamp,
                                           [[maybe_unused]] DisplayContentSample* samples) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::getDisplayedContentSamplingAttributes(
        [[maybe_unused]] int64_t display,
        [[maybe_unused]] DisplayContentSamplingAttributes* attrs) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::getDisplayPhysicalOrientation(int64_t display,
                                               common::Transform* orientation) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    HwcMountOrientation hwcOrientation;
    RET_IF_ERR(halDisplay->getMountOrientation(&hwcOrientation));
    h2a::translate(hwcOrientation, *orientation);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getDozeSupport(int64_t display, bool& support) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcSupport;
    RET_IF_ERR(halDisplay->getDozeSupport(&hwcSupport));

    h2a::translate(hwcSupport, support);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getHdrCapabilities(int64_t display, HdrCapabilities* caps) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getHdrCapabilities(&count, nullptr, &caps->maxLuminance,
                                              &caps->maxAverageLuminance,
                                              &caps->minLuminance));
    std::vector<int32_t> hwcHdrTypes(count);
    RET_IF_ERR(halDisplay->getHdrCapabilities(&count, hwcHdrTypes.data(),
                                              &caps->maxLuminance,
                                              &caps->maxAverageLuminance,
                                              &caps->minLuminance));

    h2a::translate(hwcHdrTypes, caps->types);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getOverlaySupport(OverlayProperties* caps) {
    return mDevice->getOverlaySupport(caps);
}

int32_t HalImpl::getMaxVirtualDisplayCount(int32_t* count) {
    uint32_t hwcCount = mDevice->getMaxVirtualDisplayCount();
    h2a::translate(hwcCount, *count);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getPerFrameMetadataKeys(int64_t display,
                                         std::vector<PerFrameMetadataKey>* keys) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t numKeys = 0;
    auto resManager = mDevice->mResourceManager;
    if (resManager->hasHDR10PlusMPP()) {
        numKeys = HWC2_HDR10_PLUS_SEI + 1;
    } else {
        numKeys = HWC2_MAX_FRAME_AVERAGE_LIGHT_LEVEL + 1;
    }
    for (uint32_t i = 0; i < numKeys; ++i) {
        PerFrameMetadataKey key;
        h2a::translate(i, key);
        keys->push_back(key);
    }

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getReadbackBufferAttributes(int64_t display,
                                             ReadbackBufferAttributes* attrs) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t format = -1;
    int32_t dataspace = -1;
    RET_IF_ERR(halDisplay->getReadbackBufferAttributes(&format, &dataspace));

    h2a::translate(format, attrs->format);
    h2a::translate(dataspace, attrs->dataspace);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getReadbackBufferFence(int64_t display,
                                        ndk::ScopedFileDescriptor* acquireFence) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t fd = -1;
    RET_IF_ERR(halDisplay->getReadbackBufferFence(&fd));

    h2a::translate(fd, *acquireFence);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getRenderIntents(int64_t display, ColorMode mode,
                                  std::vector<RenderIntent>* intents) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcMode;
    uint32_t count = 0;
    a2h::translate(mode, hwcMode);
    RET_IF_ERR(halDisplay->getRenderIntents(hwcMode, &count, nullptr));

    std::vector<int32_t> hwcIntents(count);
    RET_IF_ERR(halDisplay->getRenderIntents(hwcMode, &count, hwcIntents.data()));

    h2a::translate(hwcIntents, *intents);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getSupportedContentTypes(int64_t display, std::vector<ContentType>* types) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getSupportedContentTypes(&count, nullptr));

    std::vector<uint32_t> hwcTypes(count);
    RET_IF_ERR(halDisplay->getSupportedContentTypes(&count, hwcTypes.data()));

    h2a::translate(hwcTypes, *types);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::flushDisplayBrightnessChange(int64_t display) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->flushDisplayBrightnessChange();
}

int32_t HalImpl::presentDisplay(int64_t display, ndk::ScopedFileDescriptor& fence,
                       std::vector<int64_t>* outLayers,
                       std::vector<ndk::ScopedFileDescriptor>* outReleaseFences) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

   // TODO: not expect acceptDisplayChanges if there are no changes to accept
    if (halDisplay->mRenderingState == RENDERING_STATE_VALIDATED) {
        LOG(INFO) << halDisplay->mDisplayName.c_str()
                   << ": acceptDisplayChanges was not called";
        if (halDisplay->acceptDisplayChanges() != HWC2_ERROR_NONE) {
            LOG(ERROR) << halDisplay->mDisplayName.c_str()
            << ": acceptDisplayChanges is failed";
        }
    }

    int32_t hwcFence;
    RET_IF_ERR(halDisplay->presentDisplay(&hwcFence));
    h2a::translate(hwcFence, fence);

    uint32_t count = 0;
    RET_IF_ERR(halDisplay->getReleaseFences(&count, nullptr, nullptr));

    std::vector<hwc2_layer_t> hwcLayers(count);
    std::vector<int32_t> hwcFences(count);
    RET_IF_ERR(halDisplay->getReleaseFences(&count, hwcLayers.data(), hwcFences.data()));

    h2a::translate(hwcLayers, *outLayers);
    h2a::translate(hwcFences, *outReleaseFences);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::setActiveConfig(int64_t display, int32_t config) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_config_t hwcConfig;
    a2h::translate(config, hwcConfig);
    return halDisplay->setActiveConfig(hwcConfig);
}

int32_t HalImpl::setActiveConfigWithConstraints(
            int64_t display, int32_t config,
            const VsyncPeriodChangeConstraints& vsyncPeriodChangeConstraints,
            VsyncPeriodChangeTimeline* timeline) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_config_t hwcConfig;
    hwc_vsync_period_change_constraints_t hwcConstraints;
    hwc_vsync_period_change_timeline_t hwcTimeline;

    a2h::translate(config, hwcConfig);
    a2h::translate(vsyncPeriodChangeConstraints, hwcConstraints);
    RET_IF_ERR(halDisplay->setActiveConfigWithConstraints(hwcConfig, &hwcConstraints, &hwcTimeline));

    h2a::translate(hwcTimeline, *timeline);
    return HWC2_ERROR_NONE;
}

int32_t HalImpl::setBootDisplayConfig(int64_t display, int32_t config) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->setBootDisplayConfig(config);
}

int32_t HalImpl::clearBootDisplayConfig(int64_t display) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->clearBootDisplayConfig();
}

int32_t HalImpl::getPreferredBootDisplayConfig(int64_t display, int32_t* config) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->getPreferredBootDisplayConfig(config);
}

int32_t HalImpl::getHdrConversionCapabilities(std::vector<common::HdrConversionCapability>*) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::setHdrConversionStrategy(const common::HdrConversionStrategy&, common::Hdr*) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::setAutoLowLatencyMode(int64_t display, bool on) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->setAutoLowLatencyMode(on);
}

int32_t HalImpl::setClientTarget(int64_t display, buffer_handle_t target,
                                 const ndk::ScopedFileDescriptor& fence,
                                 common::Dataspace dataspace,
                                 const std::vector<common::Rect>& damage) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcFence;
    int32_t hwcDataspace;
    std::vector<hwc_rect_t> hwcDamage;

    a2h::translate(fence, hwcFence);
    a2h::translate(dataspace, hwcDataspace);
    a2h::translate(damage, hwcDamage);
    hwc_region_t region = { hwcDamage.size(), hwcDamage.data() };
    UNUSED(region);

    return halDisplay->setClientTarget(target, hwcFence, hwcDataspace);
}

int32_t HalImpl::getHasClientComposition(int64_t display, bool& outHasClientComp) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    outHasClientComp = halDisplay->hasClientComposition();

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::setColorMode(int64_t display, ColorMode mode, RenderIntent intent) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcMode;
    int32_t hwcIntent;

    a2h::translate(mode, hwcMode);
    a2h::translate(intent, hwcIntent);
    return halDisplay->setColorModeWithRenderIntent(hwcMode, hwcIntent);
}

int32_t HalImpl::setColorTransform(int64_t display, const std::vector<float>& matrix) {
    // clang-format off
    constexpr std::array<float, 16> kIdentity = {
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };
    // clang-format on
    const bool isIdentity = (std::equal(matrix.begin(), matrix.end(), kIdentity.begin()));
    const common::ColorTransform hint = isIdentity ? common::ColorTransform::IDENTITY
                                                   : common::ColorTransform::ARBITRARY_MATRIX;

    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcHint;
    a2h::translate(hint, hwcHint);
    return halDisplay->setColorTransform(matrix.data(), hwcHint);
}

int32_t HalImpl::setContentType(int64_t display, ContentType contentType) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t type;
    a2h::translate(contentType, type);
    return halDisplay->setContentType(type);
}

int32_t HalImpl::setDisplayBrightness(int64_t display, float brightness) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->setDisplayBrightness(brightness, true /* wait present */);
}

int32_t HalImpl::setDisplayedContentSamplingEnabled(
        [[maybe_unused]] int64_t display,
        [[maybe_unused]] bool enable,
        [[maybe_unused]] FormatColorComponent componentMask,
        [[maybe_unused]] int64_t maxFrames) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::setLayerBlendMode(int64_t display, int64_t layer, common::BlendMode mode) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    int32_t hwcMode;
    a2h::translate(mode, hwcMode);
    return halLayer->setLayerBlendMode(hwcMode);
}

int32_t HalImpl::setLayerBuffer(int64_t display, int64_t layer, buffer_handle_t buffer,
                                const ndk::ScopedFileDescriptor& acquireFence) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    int32_t hwcFd;
    a2h::translate(acquireFence, hwcFd);

    return halLayer->setLayerBuffer(buffer, hwcFd);
}

int32_t HalImpl::setLayerColor(int64_t display, int64_t layer, Color color) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    hwc_color_t hwcColor;
    a2h::translate(color, hwcColor);
    return halLayer->setLayerColor(hwcColor);
}

int32_t HalImpl::setLayerColorTransform(int64_t display, int64_t layer,
                                        const std::vector<float>& matrix) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halLayer->setLayerColorTransform(matrix.data());
}

int32_t HalImpl::setLayerCompositionType(int64_t display, int64_t layer, Composition type) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    int32_t hwcType;
    a2h::translate(type, hwcType);
    return halLayer->setLayerCompositionType(hwcType);
}

int32_t HalImpl::setLayerCursorPosition(int64_t display, int64_t layer, int32_t x, int32_t y) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halLayer->setCursorPosition(x, y);
}

int32_t HalImpl::setLayerDataspace(int64_t display, int64_t layer, common::Dataspace dataspace) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    int32_t hwcDataspace;
    a2h::translate(dataspace, hwcDataspace);
    return halLayer->setLayerDataspace(hwcDataspace);
}

int32_t HalImpl::setLayerDisplayFrame(int64_t display, int64_t layer, const common::Rect& frame) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    hwc_rect_t hwcFrame;
    a2h::translate(frame, hwcFrame);
    return halLayer->setLayerDisplayFrame(hwcFrame);
}

int32_t HalImpl::setLayerPerFrameMetadata(int64_t display, int64_t layer,
                           const std::vector<std::optional<PerFrameMetadata>>& metadata) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    uint32_t count = metadata.size();
    std::vector<int32_t> keys;
    std::vector<float> values;

    for (uint32_t ix = 0; ix < count; ++ix) {
        if (metadata[ix]) {
            int32_t key;
            a2h::translate(metadata[ix]->key, key);
            keys.push_back(key);
            values.push_back(metadata[ix]->value);
        }
    }

    return halLayer->setLayerPerFrameMetadata(count, keys.data(), values.data());
}

int32_t HalImpl::setLayerPerFrameMetadataBlobs(int64_t display, int64_t layer,
                           const std::vector<std::optional<PerFrameMetadataBlob>>& blobs) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    uint32_t count = blobs.size();
    std::vector<int32_t> keys;
    std::vector<uint32_t> sizes;
    std::vector<uint8_t> values;

    for (uint32_t ix = 0; ix < count; ++ix) {
        if (blobs[ix]) {
            int32_t key;
            a2h::translate(blobs[ix]->key, key);
            keys.push_back(key);
            sizes.push_back(blobs[ix]->blob.size());
            values.insert(values.end(), blobs[ix]->blob.begin(), blobs[ix]->blob.end());
        }
    }

    return halLayer->setLayerPerFrameMetadataBlobs(count, keys.data(), sizes.data(),
                                                   values.data());
}

int32_t HalImpl::setLayerPlaneAlpha(int64_t display, int64_t layer, float alpha) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halLayer->setLayerPlaneAlpha(alpha);
}

int32_t HalImpl::setLayerSidebandStream([[maybe_unused]] int64_t display,
                                        [[maybe_unused]] int64_t layer,
                                        [[maybe_unused]] buffer_handle_t stream) {
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t HalImpl::setLayerSourceCrop(int64_t display, int64_t layer, const common::FRect& crop) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    hwc_frect_t hwcCrop;
    a2h::translate(crop, hwcCrop);
    return halLayer->setLayerSourceCrop(hwcCrop);
}

int32_t HalImpl::setLayerSurfaceDamage(int64_t display, int64_t layer,
                                  const std::vector<std::optional<common::Rect>>& damage) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    std::vector<hwc_rect_t> hwcDamage;
    a2h::translate(damage, hwcDamage);
    hwc_region_t region = { hwcDamage.size(), hwcDamage.data() };

    return halLayer->setLayerSurfaceDamage(region);
}

int32_t HalImpl::setLayerTransform(int64_t display, int64_t layer, common::Transform transform) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    int32_t hwcTransform;
    a2h::translate(transform, hwcTransform);

    return halLayer->setLayerTransform(hwcTransform);
}

int32_t HalImpl::setLayerVisibleRegion(int64_t display, int64_t layer,
                               const std::vector<std::optional<common::Rect>>& visible) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    std::vector<hwc_rect_t> hwcVisible;
    a2h::translate(visible, hwcVisible);
    hwc_region_t region = { hwcVisible.size(), hwcVisible.data() };

    return halLayer->setLayerVisibleRegion(region);
}

int32_t HalImpl::setLayerBrightness(int64_t display, int64_t layer, float brightness) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halLayer->setLayerBrightness(brightness);
}

int32_t HalImpl::setLayerZOrder(int64_t display, int64_t layer, uint32_t z) {
    ExynosLayer *halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    return halLayer->setLayerZOrder(z);
}

int32_t HalImpl::setOutputBuffer(int64_t display, buffer_handle_t buffer,
                                 const ndk::ScopedFileDescriptor& releaseFence) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcFence;
    a2h::translate(releaseFence, hwcFence);

    auto err = halDisplay->setOutputBuffer(buffer, hwcFence);
    // unlike in setClientTarget, releaseFence is owned by us
    if (err == HWC2_ERROR_NONE && hwcFence >= 0) {
        close(hwcFence);
    }

    return err;
}

int32_t HalImpl::setPowerMode(int64_t display, PowerMode mode) {
    if (mode == PowerMode::ON_SUSPEND || mode == PowerMode::DOZE_SUSPEND) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcMode;
    a2h::translate(mode, hwcMode);
    return halDisplay->setPowerMode(hwcMode);
}

int32_t HalImpl::setReadbackBuffer(int64_t display, buffer_handle_t buffer,
                                   const ndk::ScopedFileDescriptor& releaseFence) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    int32_t hwcFence;
    a2h::translate(releaseFence, hwcFence);

    return halDisplay->setReadbackBuffer(buffer, hwcFence);
}

int32_t HalImpl::setVsyncEnabled(int64_t display, bool enabled) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    hwc2_vsync_t hwcEnable;
    a2h::translate(enabled, hwcEnable);
    return halDisplay->setVsyncEnabled(hwcEnable);
}

int32_t HalImpl::setIdleTimerEnabled(int64_t display, int32_t timeout) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->setDisplayIdleTimer(timeout);
}

int32_t HalImpl::validateDisplay(int64_t display, std::vector<int64_t>* outChangedLayers,
                                 std::vector<Composition>* outCompositionTypes,
                                 uint32_t* outDisplayRequestMask,
                                 std::vector<int64_t>* outRequestedLayers,
                                 std::vector<int32_t>* outRequestMasks,
                                 ClientTargetProperty* outClientTargetProperty,
                                 DimmingStage* outDimmingStage) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    uint32_t typesCount = 0;
    uint32_t reqsCount = 0;
    auto err = halDisplay->validateDisplay(&typesCount, &reqsCount);

    if (err != HWC2_ERROR_NONE && err != HWC2_ERROR_HAS_CHANGES) {
        return err;
    }

    std::vector<hwc2_layer_t> hwcChangedLayers(typesCount);
    std::vector<int32_t> hwcCompositionTypes(typesCount);
    RET_IF_ERR(halDisplay->getChangedCompositionTypes(&typesCount, hwcChangedLayers.data(),
                                                      hwcCompositionTypes.data()));

    int32_t displayReqs;
    std::vector<hwc2_layer_t> hwcRequestedLayers(reqsCount);
    outRequestMasks->resize(reqsCount);
    RET_IF_ERR(halDisplay->getDisplayRequests(&displayReqs, &reqsCount,
                                              hwcRequestedLayers.data(), outRequestMasks->data()));

    h2a::translate(hwcChangedLayers, *outChangedLayers);
    h2a::translate(hwcCompositionTypes, *outCompositionTypes);
    *outDisplayRequestMask = displayReqs;
    h2a::translate(hwcRequestedLayers, *outRequestedLayers);

    hwc_client_target_property hwcProperty;
    HwcDimmingStage hwcDimmingStage;
    if (!halDisplay->getClientTargetProperty(&hwcProperty, &hwcDimmingStage)) {
        h2a::translate(hwcDimmingStage, *outDimmingStage);
        h2a::translate(hwcProperty, *outClientTargetProperty);
    } // else ignore this error

    return err;
}

int HalImpl::setExpectedPresentTime(
        int64_t display, const std::optional<ClockMonotonicTimestamp> expectedPresentTime,
        int frameIntervalNs) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    if (!expectedPresentTime.has_value()) return HWC2_ERROR_NONE;

    if (halDisplay->getPendingExpectedPresentTime() != 0) {
        ALOGW("HalImpl: set expected present time multiple times in one frame");
    }

    halDisplay->setExpectedPresentTime(expectedPresentTime->timestampNanos, frameIntervalNs);

    return HWC2_ERROR_NONE;
}

int32_t HalImpl::getRCDLayerSupport(int64_t display, bool& outSupport) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->getRCDLayerSupport(outSupport);
}

int32_t HalImpl::setLayerBlockingRegion(
        int64_t display, int64_t layer,
        const std::vector<std::optional<common::Rect>>& blockingRegion) {
    ExynosLayer* halLayer;
    RET_IF_ERR(getHalLayer(display, layer, halLayer));

    std::vector<hwc_rect_t> halBlockingRegion;
    a2h::translate(blockingRegion, halBlockingRegion);

    return halLayer->setLayerBlockingRegion(halBlockingRegion);
}

int32_t HalImpl::getDisplayIdleTimerSupport(int64_t display, bool& outSupport) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->getDisplayIdleTimerSupport(outSupport);
}

int32_t HalImpl::getDisplayMultiThreadedPresentSupport(const int64_t& display, bool& outSupport) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->getDisplayMultiThreadedPresentSupport(outSupport);
}

int32_t HalImpl::setRefreshRateChangedCallbackDebugEnabled(int64_t display, bool enabled) {
    ExynosDisplay* halDisplay;
    RET_IF_ERR(getHalDisplay(display, halDisplay));

    return halDisplay->setRefreshRateChangedCallbackDebugEnabled(enabled);
}

} // namespace aidl::android::hardware::graphics::composer3::impl

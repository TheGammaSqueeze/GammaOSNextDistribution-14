/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "HistogramDevice.h"

#include <drm/samsung_drm.h>

#include <sstream>
#include <string>

#include "ExynosDisplayDrmInterface.h"
#include "ExynosHWCHelper.h"
#include "android-base/macros.h"

/**
 * histogramOnBinderDied
 *
 * The binderdied callback function which is registered in registerHistogram and would trigger
 * unregisterHistogram to cleanup the channel.
 *
 * @cookie pointer to the TokenInfo of the binder object.
 */
static void histogramOnBinderDied(void* cookie) {
    ATRACE_CALL();
    HistogramDevice::HistogramErrorCode histogramErrorCode;
    HistogramDevice::TokenInfo* tokenInfo = (HistogramDevice::TokenInfo*)cookie;
    ALOGI("%s: histogram channel #%u: client with token(%p) is died", __func__,
          tokenInfo->channelId, tokenInfo->token.get());

    /* release the histogram channel */
    tokenInfo->histogramDevice->unregisterHistogram(tokenInfo->token, &histogramErrorCode);

    if (histogramErrorCode != HistogramDevice::HistogramErrorCode::NONE) {
        ALOGE("%s: histogram channel #%u: failed to unregisterHistogram: %s", __func__,
              tokenInfo->channelId,
              aidl::com::google::hardware::pixel::display::toString(histogramErrorCode).c_str());
    }
}

HistogramDevice::ChannelInfo::ChannelInfo()
      : status(ChannelStatus_t::DISABLED),
        token(nullptr),
        pid(-1),
        requestedRoi(DISABLED_ROI),
        requestedBlockingRoi(DISABLED_ROI),
        workingConfig(),
        threshold(0),
        histDataCollecting(false) {}

HistogramDevice::ChannelInfo::ChannelInfo(const ChannelInfo& other) {
    std::scoped_lock lock(other.channelInfoMutex);
    status = other.status;
    token = other.token;
    pid = other.pid;
    requestedRoi = other.requestedRoi;
    requestedBlockingRoi = other.requestedBlockingRoi;
    workingConfig = other.workingConfig;
    threshold = other.threshold;
    histDataCollecting = other.histDataCollecting;
}

HistogramDevice::HistogramDevice(ExynosDisplay* display, uint8_t channelCount,
                                 std::vector<uint8_t> reservedChannels) {
    mDisplay = display;

    // TODO: b/295786065 - Get available channels from crtc property.
    initChannels(channelCount, reservedChannels);

    /* Create the death recipient which will be deleted in the destructor */
    mDeathRecipient = AIBinder_DeathRecipient_new(histogramOnBinderDied);
}

HistogramDevice::~HistogramDevice() {
    if (mDeathRecipient) {
        AIBinder_DeathRecipient_delete(mDeathRecipient);
    }
}

void HistogramDevice::initDrm(const DrmCrtc& crtc) {
    // TODO: b/295786065 - Get available channels from crtc property.

    // TODO: b/295786065 - Check if the multi channel property is supported.
    initHistogramCapability(crtc.histogram_channel_property(0).id() != 0);

    /* print the histogram capability */
    String8 logString;
    dumpHistogramCapability(logString);
    ALOGI("%s", logString.c_str());

    std::unique_lock<std::mutex> lock(mInitDrmDoneMutex);
    ::android::base::ScopedLockAssertion lock_assertion(mInitDrmDoneMutex);
    mInitDrmDone = true;
    mInitDrmDone_cv.notify_all();
}

bool HistogramDevice::waitInitDrmDone() {
    ATRACE_CALL();
    std::unique_lock<std::mutex> lock(mInitDrmDoneMutex);
    ::android::base::ScopedLockAssertion lock_assertion(mInitDrmDoneMutex);

    mInitDrmDone_cv.wait_for(lock, std::chrono::milliseconds(50), [this]() -> bool {
        ::android::base::ScopedLockAssertion lock_assertion(mInitDrmDoneMutex);
        return mInitDrmDone;
    });

    if (!mInitDrmDone) {
        ALOGW("%s: HistogramDevice::mInitDrmDone is still false", __func__);
    }

    return mInitDrmDone;
}

ndk::ScopedAStatus HistogramDevice::getHistogramCapability(
        HistogramCapability* histogramCapability) const {
    ATRACE_CALL();

    if (!histogramCapability) {
        ALOGE("%s: binder error, histogramCapability is nullptr", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
    }

    *histogramCapability = mHistogramCapability;

    return ndk::ScopedAStatus::ok();
}

#if defined(EXYNOS_HISTOGRAM_CHANNEL_REQUEST)
ndk::ScopedAStatus HistogramDevice::registerHistogram(const ndk::SpAIBinder& token,
                                                      const HistogramConfig& histogramConfig,
                                                      HistogramErrorCode* histogramErrorCode) {
    ATRACE_CALL();

    if (waitInitDrmDone() == false) {
        ALOGE("%s: histogram initDrm is not completed yet", __func__);
        // TODO: b/323158344 - add retry error in HistogramErrorCode and return here.
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    if (UNLIKELY(!mHistogramCapability.supportMultiChannel)) {
        ALOGE("%s: histogram interface is not supported", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return configHistogram(token, histogramConfig, histogramErrorCode, false);
}
#else
ndk::ScopedAStatus HistogramDevice::registerHistogram(const ndk::SpAIBinder& token,
                                                      const HistogramConfig& histogramConfig,
                                                      HistogramErrorCode* histogramErrorCode) {
    ATRACE_CALL();
    ALOGE("%s: histogram interface is not supported", __func__);
    return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
}
#endif

ndk::ScopedAStatus HistogramDevice::queryHistogram(const ndk::SpAIBinder& token,
                                                   std::vector<char16_t>* histogramBuffer,
                                                   HistogramErrorCode* histogramErrorCode) {
    ATRACE_CALL();

    if (UNLIKELY(!mHistogramCapability.supportMultiChannel)) {
        ALOGE("%s: histogram interface is not supported", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    /* No need to validate the argument (token), if the token is not correct it cannot be converted
     * to the channel id later. */

    /* validate the argument (histogramBuffer) */
    if (!histogramBuffer) {
        ALOGE("%s: binder error, histogramBuffer is nullptr", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
    }

    /* validate the argument (histogramErrorCode) */
    if (!histogramErrorCode) {
        ALOGE("%s: binder error, histogramErrorCode is nullptr", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
    }

    /* default histogramErrorCode: no error */
    *histogramErrorCode = HistogramErrorCode::NONE;

    if (mDisplay->isPowerModeOff()) {
        ALOGW("%s: DISPLAY_POWEROFF, histogram is not available when display is off", __func__);
        *histogramErrorCode = HistogramErrorCode::DISPLAY_POWEROFF;
        return ndk::ScopedAStatus::ok();
    }

    if (mDisplay->isSecureContentPresenting()) {
        ALOGV("%s: DRM_PLAYING, histogram is not available when secure content is presenting",
              __func__);
        *histogramErrorCode = HistogramErrorCode::DRM_PLAYING;
        return ndk::ScopedAStatus::ok();
    }

    uint8_t channelId;

    /* Hold the mAllocatorMutex for a short time just to convert the token to channel id. Prevent
     * holding the mAllocatorMutex when waiting for the histogram data back which may takes several
     * milliseconds */
    {
        ATRACE_NAME("getChannelId");
        std::scoped_lock lock(mAllocatorMutex);
        if ((*histogramErrorCode = getChannelIdByTokenLocked(token, channelId)) !=
            HistogramErrorCode::NONE) {
            return ndk::ScopedAStatus::ok();
        }
    }

    getHistogramData(channelId, histogramBuffer, histogramErrorCode);

    /* Clear the histogramBuffer when error occurs */
    if (*histogramErrorCode != HistogramErrorCode::NONE) {
        histogramBuffer->assign(HISTOGRAM_BIN_COUNT, 0);
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus HistogramDevice::reconfigHistogram(const ndk::SpAIBinder& token,
                                                      const HistogramConfig& histogramConfig,
                                                      HistogramErrorCode* histogramErrorCode) {
    ATRACE_CALL();

    if (UNLIKELY(!mHistogramCapability.supportMultiChannel)) {
        ALOGE("%s: histogram interface is not supported", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    return configHistogram(token, histogramConfig, histogramErrorCode, true);
}

ndk::ScopedAStatus HistogramDevice::unregisterHistogram(const ndk::SpAIBinder& token,
                                                        HistogramErrorCode* histogramErrorCode) {
    ATRACE_CALL();

    if (UNLIKELY(!mHistogramCapability.supportMultiChannel)) {
        ALOGE("%s: histogram interface is not supported", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }

    /* No need to validate the argument (token), if the token is not correct it cannot be converted
     * to the channel id later. */

    /* validate the argument (histogramErrorCode) */
    if (!histogramErrorCode) {
        ALOGE("%s: binder error, histogramErrorCode is nullptr", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
    }

    /* default histogramErrorCode: no error */
    *histogramErrorCode = HistogramErrorCode::NONE;

    ATRACE_BEGIN("getChannelId");
    uint8_t channelId;
    std::scoped_lock lock(mAllocatorMutex);

    if ((*histogramErrorCode = getChannelIdByTokenLocked(token, channelId)) !=
        HistogramErrorCode::NONE) {
        ATRACE_END();
        return ndk::ScopedAStatus::ok();
    }
    ATRACE_END();

    releaseChannelLocked(channelId);

    /*
     * If AIBinder is alive, the unregisterHistogram is triggered from the histogram client, and we
     * need to unlink the binder object from death notification.
     * If AIBinder is already dead, the unregisterHistogram is triggered from binderdied callback,
     * no need to unlink here.
     */
    if (LIKELY(AIBinder_isAlive(token.get()))) {
        binder_status_t status;
        if ((status = AIBinder_unlinkToDeath(token.get(), mDeathRecipient,
                                             &mTokenInfoMap[token.get()]))) {
            /* Not return error due to the AIBinder_unlinkToDeath */
            ALOGE("%s: histogram channel #%u: AIBinder_linkToDeath error %d", __func__, channelId,
                  status);
        }
    }

    /* Delete the corresponding TokenInfo after the binder object is already unlinked. */
    mTokenInfoMap.erase(token.get());

    return ndk::ScopedAStatus::ok();
}

void HistogramDevice::handleDrmEvent(void* event) {
    int ret = NO_ERROR;
    uint8_t channelId;
    char16_t* buffer;

    if ((ret = parseDrmEvent(event, channelId, buffer))) {
        ALOGE("%s: failed to parseDrmEvent, ret %d", __func__, ret);
        return;
    }

    ATRACE_NAME(String8::format("handleHistogramDrmEvent #%u", channelId).c_str());
    if (channelId >= mChannels.size()) {
        ALOGE("%s: histogram channel #%u: invalid channelId", __func__, channelId);
        return;
    }

    ChannelInfo& channel = mChannels[channelId];
    std::unique_lock<std::mutex> lock(channel.histDataCollectingMutex);

    /* Check if the histogram channel is collecting the histogram data */
    if (channel.histDataCollecting == true) {
        std::memcpy(channel.histData, buffer, HISTOGRAM_BIN_COUNT * sizeof(char16_t));
        channel.histDataCollecting = false;
    } else {
        ALOGW("%s: histogram channel #%u: ignore the histogram channel event", __func__, channelId);
    }

    channel.histDataCollecting_cv.notify_all();
}

void HistogramDevice::prepareAtomicCommit(ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq) {
    ATRACE_NAME("HistogramAtomicCommit");

    ExynosDisplayDrmInterface* moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface*>(mDisplay->mDisplayInterface.get());
    if (!moduleDisplayInterface) {
        HWC_LOGE(mDisplay, "%s: failed to get ExynosDisplayDrmInterface (nullptr)", __func__);
        return;
    }

    /* Get the current active region and check if the resolution is changed. */
    int32_t currDisplayActiveH = moduleDisplayInterface->getActiveModeHDisplay();
    int32_t currDisplayActiveV = moduleDisplayInterface->getActiveModeVDisplay();
    bool isResolutionChanged =
            (mDisplayActiveH != currDisplayActiveH) || (mDisplayActiveV != currDisplayActiveV);
    mDisplayActiveH = currDisplayActiveH;
    mDisplayActiveV = currDisplayActiveV;

    /* Loop through every channel and call prepareChannelCommit */
    for (uint8_t channelId = 0; channelId < mChannels.size(); ++channelId) {
        int channelRet = prepareChannelCommit(drmReq, channelId, moduleDisplayInterface,
                                              isResolutionChanged);

        /* Every channel is independent, no early return when the channel commit fails. */
        if (channelRet) {
            ALOGE("%s: histogram channel #%u: failed to prepare atomic commit: %d", __func__,
                  channelId, channelRet);
        }
    }
}

void HistogramDevice::postAtomicCommit() {
    /* Atomic commit is success, loop through every channel and update the channel status */
    for (uint8_t channelId = 0; channelId < mChannels.size(); ++channelId) {
        ChannelInfo& channel = mChannels[channelId];
        std::scoped_lock lock(channel.channelInfoMutex);

        switch (channel.status) {
            case ChannelStatus_t::CONFIG_BLOB_ADDED:
                channel.status = ChannelStatus_t::CONFIG_COMMITTED;
                break;
            case ChannelStatus_t::DISABLE_BLOB_ADDED:
                channel.status = ChannelStatus_t::DISABLED;
                break;
            default:
                break;
        }
    }
}

void HistogramDevice::dump(String8& result) const {
    /* Do not dump the Histogram Device if it is not supported. */
    if (!mHistogramCapability.supportMultiChannel) {
        return;
    }

    /* print the histogram capability */
    dumpHistogramCapability(result);

    result.appendFormat("\n");

    /* print the histogram channel info*/
    result.appendFormat("Histogram channel info:\n");
    for (uint8_t channelId = 0; channelId < mChannels.size(); ++channelId) {
        // TODO: b/294489887 - Use buildForMiniDump can eliminate the redundant rows.
        TableBuilder tb;
        const ChannelInfo& channel = mChannels[channelId];
        std::scoped_lock lock(channel.channelInfoMutex);
        tb.add("ID", (int)channelId);
        tb.add("status", toString(channel.status));
        tb.add("token", channel.token.get());
        tb.add("pid", channel.pid);
        tb.add("requestedRoi", toString(channel.requestedRoi));
        tb.add("workingRoi", toString(channel.workingConfig.roi));
        tb.add("requestedBlockRoi", toString(channel.requestedBlockingRoi));
        tb.add("workingBlockRoi",
               toString(channel.workingConfig.blockingRoi.value_or(DISABLED_ROI)));
        tb.add("threshold", channel.threshold);
        tb.add("weightRGB", toString(channel.workingConfig.weights));
        tb.add("samplePos",
               aidl::com::google::hardware::pixel::display::toString(
                       channel.workingConfig.samplePos));
        result.append(tb.build().c_str());
    }

    result.appendFormat("\n");
}

void HistogramDevice::initChannels(uint8_t channelCount,
                                   const std::vector<uint8_t>& reservedChannels) {
    mChannels.resize(channelCount);
    ALOGI("%s: init histogram with %d channels", __func__, channelCount);

    for (const uint8_t reservedChannelId : reservedChannels) {
        if (reservedChannelId < mChannels.size()) {
            std::scoped_lock channelLock(mChannels[reservedChannelId].channelInfoMutex);
            mChannels[reservedChannelId].status = ChannelStatus_t::RESERVED;
        }
    }

    std::scoped_lock lock(mAllocatorMutex);
    for (uint8_t channelId = 0; channelId < channelCount; ++channelId) {
        std::scoped_lock channelLock(mChannels[channelId].channelInfoMutex);

        if (mChannels[channelId].status == ChannelStatus_t::RESERVED) {
            ALOGI("%s: histogram channel #%u: reserved for driver", __func__, (int)channelId);
            continue;
        }

        mFreeChannels.push(channelId);
    }
}

void HistogramDevice::initHistogramCapability(bool supportMultiChannel) {
    ExynosDisplayDrmInterface* moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface*>(mDisplay->mDisplayInterface.get());

    if (!moduleDisplayInterface) {
        ALOGE("%s: failed to get ExynosDisplayDrmInterface (nullptr), cannot get panel full "
              "resolution",
              __func__);
        mHistogramCapability.fullResolutionWidth = 0;
        mHistogramCapability.fullResolutionHeight = 0;
    } else {
        mHistogramCapability.fullResolutionWidth =
                moduleDisplayInterface->getPanelFullResolutionHSize();
        mHistogramCapability.fullResolutionHeight =
                moduleDisplayInterface->getPanelFullResolutionVSize();
    }

    mHistogramCapability.channelCount = mChannels.size();
    mHistogramCapability.supportMultiChannel = supportMultiChannel;
    mHistogramCapability.supportSamplePosList.push_back(HistogramSamplePos::POST_POSTPROC);
    mHistogramCapability.supportBlockingRoi = false;

    initPlatformHistogramCapability();
}

ndk::ScopedAStatus HistogramDevice::configHistogram(const ndk::SpAIBinder& token,
                                                    const HistogramConfig& histogramConfig,
                                                    HistogramErrorCode* histogramErrorCode,
                                                    bool isReconfig) {
    /* validate the argument (histogramErrorCode) */
    if (!histogramErrorCode) {
        ALOGE("%s: binder error, histogramErrorCode is nullptr", __func__);
        return ndk::ScopedAStatus::fromExceptionCode(EX_NULL_POINTER);
    }

    /* default histogramErrorCode: no error */
    *histogramErrorCode = HistogramErrorCode::NONE;

    /* validate the argument (token) */
    if (token.get() == nullptr) {
        ALOGE("%s: BAD_TOKEN, token is nullptr", __func__);
        *histogramErrorCode = HistogramErrorCode::BAD_TOKEN;
        return ndk::ScopedAStatus::ok();
    }

    /* validate the argument (histogramConfig) */
    if ((*histogramErrorCode = validateHistogramConfig(histogramConfig)) !=
        HistogramErrorCode::NONE) {
        return ndk::ScopedAStatus::ok();
    }

    {
        ATRACE_BEGIN("getOrAcquireChannelId");
        uint8_t channelId;
        std::scoped_lock lock(mAllocatorMutex);

        /* isReconfig is false: registerHistogram, need to allcoate the histogram channel
         * isReconfig is true: reconfigHistogram, already registered, only need to get channel id
         */
        if (!isReconfig) {
            if ((*histogramErrorCode = acquireChannelLocked(token, channelId)) !=
                HistogramErrorCode::NONE) {
                ATRACE_END();
                return ndk::ScopedAStatus::ok();
            }
        } else {
            if ((*histogramErrorCode = getChannelIdByTokenLocked(token, channelId)) !=
                HistogramErrorCode::NONE) {
                ATRACE_END();
                return ndk::ScopedAStatus::ok();
            }
        }
        ATRACE_END();

        /* store the histogram information, and mark the channel ready for atomic commit by setting
         * the status to CONFIG_PENDING */
        fillupChannelInfo(channelId, token, histogramConfig);

        if (!isReconfig) {
            /* link the binder object (token) to the death recipient. When the binder object is
             * destructed, the callback function histogramOnBinderDied can release the histogram
             * resources automatically. */
            binder_status_t status;
            if ((status = AIBinder_linkToDeath(token.get(), mDeathRecipient,
                                               &mTokenInfoMap[token.get()]))) {
                /* Not return error due to the AIBinder_linkToDeath because histogram function can
                 * still work */
                ALOGE("%s: histogram channel #%u: AIBinder_linkToDeath error %d", __func__,
                      channelId, status);
            }
        }
    }

    if (!mDisplay->isPowerModeOff()) {
        mDisplay->mDevice->onRefresh(mDisplay->mDisplayId);
    }

    return ndk::ScopedAStatus::ok();
}

void HistogramDevice::getHistogramData(uint8_t channelId, std::vector<char16_t>* histogramBuffer,
                                       HistogramErrorCode* histogramErrorCode) {
    ATRACE_NAME(String8::format("%s #%u", __func__, channelId).c_str());
    int32_t ret;
    ExynosDisplayDrmInterface* moduleDisplayInterface =
            static_cast<ExynosDisplayDrmInterface*>(mDisplay->mDisplayInterface.get());
    if (!moduleDisplayInterface) {
        *histogramErrorCode = HistogramErrorCode::BAD_HIST_DATA;
        ALOGE("%s: histogram channel #%u: BAD_HIST_DATA, moduleDisplayInterface is nullptr",
              __func__, channelId);
        return;
    }

    ChannelInfo& channel = mChannels[channelId];

    std::unique_lock<std::mutex> lock(channel.histDataCollectingMutex);

    /* Check if the previous queryHistogram is finished */
    if (channel.histDataCollecting) {
        *histogramErrorCode = HistogramErrorCode::BAD_HIST_DATA;
        ALOGE("%s: histogram channel #%u: BAD_HIST_DATA, previous %s not finished", __func__,
              channelId, __func__);
        return;
    }

    /* Send the ioctl request (histogram_channel_request_ioctl) which allocate the drm event and
     * send back the drm event with data when available. */
    if ((ret = moduleDisplayInterface->sendHistogramChannelIoctl(HistogramChannelIoctl_t::REQUEST,
                                                                 channelId)) != NO_ERROR) {
        *histogramErrorCode = HistogramErrorCode::BAD_HIST_DATA;
        ALOGE("%s: histogram channel #%u: BAD_HIST_DATA, sendHistogramChannelIoctl (REQUEST) "
              "error "
              "(%d)",
              __func__, channelId, ret);
        return;
    }
    channel.histDataCollecting = true;

    {
        ATRACE_NAME(String8::format("waitDrmEvent #%u", channelId).c_str());
        /* Wait until the condition variable is notified or timeout. */
        channel.histDataCollecting_cv.wait_for(lock, std::chrono::milliseconds(50),
                                               [this, &channel]() {
                                                   return (!mDisplay->isPowerModeOff() &&
                                                           !channel.histDataCollecting);
                                               });
    }

    /* If the histDataCollecting is not cleared, check the reason and clear the histogramBuffer.
     */
    if (channel.histDataCollecting) {
        if (mDisplay->isPowerModeOff()) {
            *histogramErrorCode = HistogramErrorCode::DISPLAY_POWEROFF;
            ALOGW("%s: histogram channel #%u: DISPLAY_POWEROFF, histogram is not available "
                  "when "
                  "display is off",
                  __func__, channelId);
        } else {
            *histogramErrorCode = HistogramErrorCode::BAD_HIST_DATA;
            ALOGE("%s: histogram channel #%u: BAD_HIST_DATA, no histogram channel event is "
                  "handled",
                  __func__, channelId);
        }

        /* Cancel the histogram data request */
        ALOGI("%s: histogram channel #%u: cancel histogram data request", __func__, channelId);
        if ((ret = moduleDisplayInterface
                           ->sendHistogramChannelIoctl(HistogramChannelIoctl_t::CANCEL,
                                                       channelId)) != NO_ERROR) {
            ALOGE("%s: histogram channel #%u: sendHistogramChannelIoctl (CANCEL) error (%d)",
                  __func__, channelId, ret);
        }

        channel.histDataCollecting = false;
        return;
    }

    if (mDisplay->isSecureContentPresenting()) {
        ALOGV("%s: histogram channel #%u: DRM_PLAYING, histogram is not available when secure "
              "content is presenting",
              __func__, channelId);
        *histogramErrorCode = HistogramErrorCode::DRM_PLAYING;
        return;
    }

    /* Copy the histogram data from histogram info to histogramBuffer */
    histogramBuffer->assign(channel.histData, channel.histData + HISTOGRAM_BIN_COUNT);
}

// TODO: b/295990513 - Remove the if defined after kernel prebuilts are merged.
#if defined(EXYNOS_HISTOGRAM_CHANNEL_REQUEST)
int HistogramDevice::parseDrmEvent(void* event, uint8_t& channelId, char16_t*& buffer) const {
    struct exynos_drm_histogram_channel_event* histogram_channel_event =
            (struct exynos_drm_histogram_channel_event*)event;
    channelId = histogram_channel_event->hist_id;
    buffer = (char16_t*)&histogram_channel_event->bins;
    return NO_ERROR;
}
#else
int HistogramDevice::parseDrmEvent(void* event, uint8_t& channelId, char16_t*& buffer) const {
    channelId = 0;
    buffer = nullptr;
    return INVALID_OPERATION;
}
#endif

HistogramDevice::HistogramErrorCode HistogramDevice::acquireChannelLocked(
        const ndk::SpAIBinder& token, uint8_t& channelId) {
    ATRACE_CALL();
    if (mFreeChannels.size() == 0) {
        ALOGE("%s: NO_CHANNEL_AVAILABLE, there is no histogram channel available", __func__);
        return HistogramErrorCode::NO_CHANNEL_AVAILABLE;
    }

    if (mTokenInfoMap.find(token.get()) != mTokenInfoMap.end()) {
        ALOGE("%s: BAD_TOKEN, token (%p) is already registered", __func__, token.get());
        return HistogramErrorCode::BAD_TOKEN;
    }

    /* Acquire free channel id from the free list */
    channelId = mFreeChannels.front();
    mFreeChannels.pop();
    mTokenInfoMap[token.get()] = {.channelId = channelId, .histogramDevice = this, .token = token};

    return HistogramErrorCode::NONE;
}

void HistogramDevice::releaseChannelLocked(uint8_t channelId) {
    /* Add the channel id back to the free list and cleanup the channel info with status set to
     * DISABLE_PENDING */
    mFreeChannels.push(channelId);
    cleanupChannelInfo(channelId);
}

HistogramDevice::HistogramErrorCode HistogramDevice::getChannelIdByTokenLocked(
        const ndk::SpAIBinder& token, uint8_t& channelId) {
    if (mTokenInfoMap.find(token.get()) == mTokenInfoMap.end()) {
        ALOGE("%s: BAD_TOKEN, token (%p) is not registered", __func__, token.get());
        return HistogramErrorCode::BAD_TOKEN;
    }

    channelId = mTokenInfoMap[token.get()].channelId;

    return HistogramErrorCode::NONE;
}

void HistogramDevice::cleanupChannelInfo(uint8_t channelId) {
    ATRACE_NAME(String8::format("%s #%u", __func__, channelId).c_str());
    ChannelInfo& channel = mChannels[channelId];
    std::scoped_lock lock(channel.channelInfoMutex);
    channel.status = ChannelStatus_t::DISABLE_PENDING;
    channel.token = nullptr;
    channel.pid = -1;
    channel.requestedRoi = DISABLED_ROI;
    channel.requestedBlockingRoi = DISABLED_ROI;
    channel.workingConfig = {.roi = DISABLED_ROI,
                             .weights.weightR = 0,
                             .weights.weightG = 0,
                             .weights.weightB = 0,
                             .samplePos = HistogramSamplePos::POST_POSTPROC,
                             .blockingRoi = DISABLED_ROI};
    channel.threshold = 0;
}

void HistogramDevice::fillupChannelInfo(uint8_t channelId, const ndk::SpAIBinder& token,
                                        const HistogramConfig& histogramConfig) {
    ATRACE_NAME(String8::format("%s #%u", __func__, channelId).c_str());
    ChannelInfo& channel = mChannels[channelId];
    std::scoped_lock lock(channel.channelInfoMutex);
    channel.status = ChannelStatus_t::CONFIG_PENDING;
    channel.token = token;
    channel.pid = AIBinder_getCallingPid();
    channel.requestedRoi = histogramConfig.roi;
    channel.requestedBlockingRoi = histogramConfig.blockingRoi.value_or(DISABLED_ROI);
    channel.workingConfig = histogramConfig;
    channel.workingConfig.roi = DISABLED_ROI;
    channel.workingConfig.blockingRoi = DISABLED_ROI;
}

int HistogramDevice::prepareChannelCommit(ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq,
                                          uint8_t channelId,
                                          ExynosDisplayDrmInterface* moduleDisplayInterface,
                                          bool isResolutionChanged) {
    int ret = NO_ERROR;

    ChannelInfo& channel = mChannels[channelId];
    std::scoped_lock lock(channel.channelInfoMutex);

    if (channel.status == ChannelStatus_t::CONFIG_COMMITTED ||
        channel.status == ChannelStatus_t::CONFIG_PENDING) {
        if (mDisplayActiveH == 0 || mDisplayActiveV == 0) {
            /* mActiveModeState is not initialized, postpone histogram config to next atomic commit
             */
            ALOGW("%s: mActiveModeState is not initialized, active: (%dx%d), postpone histogram "
                  "config to next atomic commit",
                  __func__, mDisplayActiveH, mDisplayActiveV);
            /* postpone the histogram config to next atomic commit */
            ALOGD("%s: histogram channel #%u: set status (CONFIG_PENDING)", __func__, channelId);
            channel.status = ChannelStatus_t::CONFIG_PENDING;
            return NO_ERROR;
        }

        /* If the channel status is CONFIG_COMMITTED, check if the working roi needs to be
         * updated due to resolution changed. */
        if (channel.status == ChannelStatus_t::CONFIG_COMMITTED) {
            if (LIKELY(isResolutionChanged == false)) {
                return NO_ERROR;
            } else {
                ALOGI("%s: histogram channel #%u: detect resolution changed, update roi setting",
                      __func__, channelId);
            }
        }

        HistogramRoiRect convertedRoi;

        /* calculate the roi based on the current active resolution */
        ret = convertRoiLocked(moduleDisplayInterface, channel.requestedRoi, convertedRoi);
        if (ret) {
            ALOGE("%s: histogram channel #%u: failed to convert to workingRoi, ret: %d", __func__,
                  channelId, ret);
            channel.status = ChannelStatus_t::CONFIG_ERROR;
            return ret;
        }
        channel.workingConfig.roi = convertedRoi;

        /* calculate the blocking roi based on the current active resolution */
        ret = convertRoiLocked(moduleDisplayInterface, channel.requestedBlockingRoi, convertedRoi);
        if (ret) {
            ALOGE("%s: histogram channel #%u: failed to convert to workingBlockRoi, ret: %d",
                  __func__, channelId, ret);
            channel.status = ChannelStatus_t::CONFIG_ERROR;
            return ret;
        }
        channel.workingConfig.blockingRoi = convertedRoi;

        /* threshold is calculated based on the roi coordinates rather than configured by client */
        channel.threshold = calculateThreshold(channel.workingConfig.roi);

        /* Create histogram drm config struct (platform dependent) */
        std::shared_ptr<void> blobData;
        size_t blobLength = 0;
        if ((ret = createHistogramDrmConfigLocked(channel, blobData, blobLength))) {
            ALOGE("%s: histogram channel #%u: failed to createHistogramDrmConfig, ret: %d",
                  __func__, channelId, ret);
            channel.status = ChannelStatus_t::CONFIG_ERROR;
            return ret;
        }

        /* Add histogram blob to atomic commit */
        ret = moduleDisplayInterface->setDisplayHistogramChannelSetting(drmReq, channelId,
                                                                        blobData.get(), blobLength);
        if (ret == NO_ERROR) {
            channel.status = ChannelStatus_t::CONFIG_BLOB_ADDED;
        } else {
            ALOGE("%s: histogram channel #%u: failed to setDisplayHistogramChannelSetting, ret: %d",
                  __func__, channelId, ret);
            channel.status = ChannelStatus_t::CONFIG_ERROR;
            return ret;
        }
    } else if (channel.status == ChannelStatus_t::DISABLE_PENDING) {
        ret = moduleDisplayInterface->clearDisplayHistogramChannelSetting(drmReq, channelId);
        if (ret == NO_ERROR) {
            channel.status = ChannelStatus_t::DISABLE_BLOB_ADDED;
        } else {
            ALOGE("%s: histogram channel #%u: failed to clearDisplayHistogramChannelSetting, ret: "
                  "%d",
                  __func__, channelId, ret);
            channel.status = ChannelStatus_t::DISABLE_ERROR;
        }
    }

    return ret;
}

// TODO: b/295990513 - Remove the if defined after kernel prebuilts are merged.
#if defined(EXYNOS_HISTOGRAM_CHANNEL_REQUEST)
int HistogramDevice::createHistogramDrmConfigLocked(const ChannelInfo& channel,
                                                    std::shared_ptr<void>& configPtr,
                                                    size_t& length) const {
    configPtr = std::make_shared<struct histogram_channel_config>();
    struct histogram_channel_config* channelConfig =
            (struct histogram_channel_config*)configPtr.get();

    if (channelConfig == nullptr) {
        ALOGE("%s: histogram failed to allocate histogram_channel_config", __func__);
        return NO_MEMORY;
    }

    channelConfig->roi.start_x = channel.workingConfig.roi.left;
    channelConfig->roi.start_y = channel.workingConfig.roi.top;
    channelConfig->roi.hsize = channel.workingConfig.roi.right - channel.workingConfig.roi.left;
    channelConfig->roi.vsize = channel.workingConfig.roi.bottom - channel.workingConfig.roi.top;
    if (mHistogramCapability.supportBlockingRoi && channel.workingConfig.blockingRoi.has_value() &&
        channel.workingConfig.blockingRoi.value() != DISABLED_ROI) {
        const HistogramRoiRect& blockedRoi = channel.workingConfig.blockingRoi.value();
        channelConfig->flags |= HISTOGRAM_FLAGS_BLOCKED_ROI;
        channelConfig->blocked_roi.start_x = blockedRoi.left;
        channelConfig->blocked_roi.start_y = blockedRoi.top;
        channelConfig->blocked_roi.hsize = blockedRoi.right - blockedRoi.left;
        channelConfig->blocked_roi.vsize = blockedRoi.bottom - blockedRoi.top;
    } else {
        channelConfig->flags &= ~HISTOGRAM_FLAGS_BLOCKED_ROI;
    }
    channelConfig->weights.weight_r = channel.workingConfig.weights.weightR;
    channelConfig->weights.weight_g = channel.workingConfig.weights.weightG;
    channelConfig->weights.weight_b = channel.workingConfig.weights.weightB;
    channelConfig->pos = (channel.workingConfig.samplePos == HistogramSamplePos::POST_POSTPROC)
            ? POST_DQE
            : PRE_DQE;
    channelConfig->threshold = channel.threshold;

    length = sizeof(struct histogram_channel_config);

    return NO_ERROR;
}
#else
int HistogramDevice::createHistogramDrmConfigLocked(const ChannelInfo& channel,
                                                    std::shared_ptr<void>& configPtr,
                                                    size_t& length) const {
    /* Default implementation doesn't know the histogram channel config struct in the kernel.
     * Cannot allocate and initialize the channel config. */
    configPtr = nullptr;
    length = 0;
    return INVALID_OPERATION;
}
#endif

int HistogramDevice::convertRoiLocked(ExynosDisplayDrmInterface* moduleDisplayInterface,
                                      const HistogramRoiRect& requestedRoi,
                                      HistogramRoiRect& convertedRoi) const {
    const int32_t& panelH = mHistogramCapability.fullResolutionWidth;
    const int32_t& panelV = mHistogramCapability.fullResolutionHeight;

    ALOGV("%s: active: (%dx%d), panel: (%dx%d)", __func__, mDisplayActiveH, mDisplayActiveV, panelH,
          panelV);

    if (panelH < mDisplayActiveH || mDisplayActiveH < 0 || panelV < mDisplayActiveV ||
        mDisplayActiveV < 0) {
        ALOGE("%s: failed to convert roi, active: (%dx%d), panel: (%dx%d)", __func__,
              mDisplayActiveH, mDisplayActiveV, panelH, panelV);
        return -EINVAL;
    }

    /* Linear transform from full resolution to active resolution */
    convertedRoi.left = requestedRoi.left * mDisplayActiveH / panelH;
    convertedRoi.top = requestedRoi.top * mDisplayActiveV / panelV;
    convertedRoi.right = requestedRoi.right * mDisplayActiveH / panelH;
    convertedRoi.bottom = requestedRoi.bottom * mDisplayActiveV / panelV;

    ALOGV("%s: working roi: %s", __func__, toString(convertedRoi).c_str());

    return NO_ERROR;
}

void HistogramDevice::dumpHistogramCapability(String8& result) const {
    /* Append the histogram capability info to the dump string */
    result.appendFormat("Histogram capability:\n");
    result.appendFormat("\tsupportMultiChannel: %s\n",
                        mHistogramCapability.supportMultiChannel ? "true" : "false");
    result.appendFormat("\tsupportBlockingRoi: %s\n",
                        mHistogramCapability.supportBlockingRoi ? "true" : "false");
    result.appendFormat("\tchannelCount: %d\n", mHistogramCapability.channelCount);
    result.appendFormat("\tfullscreen roi: (0,0)x(%dx%d)\n",
                        mHistogramCapability.fullResolutionWidth,
                        mHistogramCapability.fullResolutionHeight);
    result.appendFormat("\tsupportSamplePosList:");
    for (HistogramSamplePos samplePos : mHistogramCapability.supportSamplePosList) {
        result.appendFormat(" %s",
                            aidl::com::google::hardware::pixel::display::toString(samplePos)
                                    .c_str());
    }
    result.appendFormat("\n");
}

HistogramDevice::HistogramErrorCode HistogramDevice::validateHistogramConfig(
        const HistogramConfig& histogramConfig) const {
    HistogramErrorCode ret;

    if ((ret = validateHistogramRoi(histogramConfig.roi, "")) != HistogramErrorCode::NONE ||
        (ret = validateHistogramWeights(histogramConfig.weights)) != HistogramErrorCode::NONE ||
        (ret = validateHistogramSamplePos(histogramConfig.samplePos)) != HistogramErrorCode::NONE ||
        (ret = validateHistogramBlockingRoi(histogramConfig.blockingRoi)) !=
                HistogramErrorCode::NONE) {
        return ret;
    }

    return HistogramErrorCode::NONE;
}

HistogramDevice::HistogramErrorCode HistogramDevice::validateHistogramRoi(
        const HistogramRoiRect& roi, const char* roiType) const {
    if (roi == DISABLED_ROI) return HistogramErrorCode::NONE;

    if ((roi.left < 0) || (roi.top < 0) || (roi.right - roi.left <= 0) ||
        (roi.bottom - roi.top <= 0) || (roi.right > mHistogramCapability.fullResolutionWidth) ||
        (roi.bottom > mHistogramCapability.fullResolutionHeight)) {
        ALOGE("%s: BAD_ROI, %sroi: %s, full screen roi: (0,0)x(%dx%d)", __func__, roiType,
              toString(roi).c_str(), mHistogramCapability.fullResolutionWidth,
              mHistogramCapability.fullResolutionHeight);
        return HistogramErrorCode::BAD_ROI;
    }

    return HistogramErrorCode::NONE;
}

HistogramDevice::HistogramErrorCode HistogramDevice::validateHistogramWeights(
        const HistogramWeights& weights) const {
    if ((weights.weightR + weights.weightG + weights.weightB) != WEIGHT_SUM) {
        ALOGE("%s: BAD_WEIGHT, weights(%d,%d,%d)\n", __func__, weights.weightR, weights.weightG,
              weights.weightB);
        return HistogramErrorCode::BAD_WEIGHT;
    }

    return HistogramErrorCode::NONE;
}

HistogramDevice::HistogramErrorCode HistogramDevice::validateHistogramSamplePos(
        const HistogramSamplePos& samplePos) const {
    for (HistogramSamplePos mSamplePos : mHistogramCapability.supportSamplePosList) {
        if (samplePos == mSamplePos) {
            return HistogramErrorCode::NONE;
        }
    }

    ALOGE("%s: BAD_POSITION, samplePos is %d", __func__, (int)samplePos);
    return HistogramErrorCode::BAD_POSITION;
}

HistogramDevice::HistogramErrorCode HistogramDevice::validateHistogramBlockingRoi(
        const std::optional<HistogramRoiRect>& blockingRoi) const {
    /* If the platform doesn't support blockingRoi, client should not enable blockingRoi  */
    if (mHistogramCapability.supportBlockingRoi == false) {
        if (blockingRoi.has_value() && blockingRoi.value() != DISABLED_ROI) {
            ALOGE("%s: BAD_ROI, platform doesn't support blockingRoi, requested: %s", __func__,
                  toString(blockingRoi.value()).c_str());
            return HistogramErrorCode::BAD_ROI;
        }
        return HistogramErrorCode::NONE;
    }

    /* For the platform that supports blockingRoi, use the same validate rule as roi */
    return validateHistogramRoi(blockingRoi.value_or(DISABLED_ROI), "blocking ");
}

int HistogramDevice::calculateThreshold(const HistogramRoiRect& roi) const {
    /* If roi is disabled, the targeted region is entire screen. */
    int32_t roiH = (roi != DISABLED_ROI) ? (roi.right - roi.left) : mDisplayActiveH;
    int32_t roiV = (roi != DISABLED_ROI) ? (roi.bottom - roi.top) : mDisplayActiveV;
    int threshold = (roiV * roiH) >> 16;
    // TODO: b/294491895 - Check if the threshold plus one really need it
    return threshold + 1;
}

std::string HistogramDevice::toString(const ChannelStatus_t& status) {
    switch (status) {
        case ChannelStatus_t::RESERVED:
            return "RESERVED";
        case ChannelStatus_t::DISABLED:
            return "DISABLED";
        case ChannelStatus_t::CONFIG_PENDING:
            return "CONFIG_PENDING";
        case ChannelStatus_t::CONFIG_BLOB_ADDED:
            return "CONFIG_BLOB_ADDED";
        case ChannelStatus_t::CONFIG_COMMITTED:
            return "CONFIG_COMMITTED";
        case ChannelStatus_t::CONFIG_ERROR:
            return "CONFIG_ERROR";
        case ChannelStatus_t::DISABLE_PENDING:
            return "DISABLE_PENDING";
        case ChannelStatus_t::DISABLE_BLOB_ADDED:
            return "DISABLE_BLOB_ADDED";
        case ChannelStatus_t::DISABLE_ERROR:
            return "DISABLE_ERROR";
    }

    return "UNDEFINED";
}

std::string HistogramDevice::toString(const HistogramRoiRect& roi) {
    if (roi == DISABLED_ROI) return "OFF";

    std::ostringstream os;
    os << "(" << roi.left << "," << roi.top << ")";
    os << "x";
    os << "(" << roi.right << "," << roi.bottom << ")";
    return os.str();
}

std::string HistogramDevice::toString(const HistogramWeights& weights) {
    std::ostringstream os;
    os << "(";
    os << (int)weights.weightR << ",";
    os << (int)weights.weightG << ",";
    os << (int)weights.weightB;
    os << ")";
    return os.str();
}

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

#pragma once

#include <aidl/android/hardware/graphics/common/Rect.h>
#include <aidl/com/google/hardware/pixel/display/HistogramCapability.h>
#include <aidl/com/google/hardware/pixel/display/HistogramConfig.h>
#include <aidl/com/google/hardware/pixel/display/HistogramErrorCode.h>
#include <aidl/com/google/hardware/pixel/display/HistogramSamplePos.h>
#include <aidl/com/google/hardware/pixel/display/Weight.h>
#include <android-base/thread_annotations.h>
#include <drm/samsung_drm.h>
#include <utils/String8.h>

#include <condition_variable>
#include <mutex>
#include <queue>
#include <unordered_map>

#include "ExynosDisplay.h"
#include "ExynosDisplayDrmInterface.h"
#include "drmcrtc.h"

using namespace android;

class HistogramDevice {
public:
    using HistogramCapability = aidl::com::google::hardware::pixel::display::HistogramCapability;
    using HistogramConfig = aidl::com::google::hardware::pixel::display::HistogramConfig;
    using HistogramErrorCode = aidl::com::google::hardware::pixel::display::HistogramErrorCode;
    using HistogramRoiRect = aidl::android::hardware::graphics::common::Rect;
    using HistogramSamplePos = aidl::com::google::hardware::pixel::display::HistogramSamplePos;
    using HistogramWeights = aidl::com::google::hardware::pixel::display::Weight;
    using HistogramChannelIoctl_t = ExynosDisplayDrmInterface::HistogramChannelIoctl_t;

    /* For blocking roi and roi, (0, 0, 0, 0) means disabled */
    static constexpr HistogramRoiRect DISABLED_ROI = {0, 0, 0, 0};

    /* Histogram weight constraint: weightR + weightG + weightB = WEIGHT_SUM */
    static constexpr size_t WEIGHT_SUM = 1024;

    /* Histogram channel status */
    enum class ChannelStatus_t : uint32_t {
        /* occupied by the driver for specific usage such as LHBM */
        RESERVED = 0,

        /* channel is off */
        DISABLED,

        /* channel config is ready and requires to be added into an atomic commit */
        CONFIG_PENDING,

        /* channel config (blob) is added to an atomic commit but not committed yet */
        CONFIG_BLOB_ADDED,

        /* channel config is committed to drm driver successfully */
        CONFIG_COMMITTED,

        /* channel config has error */
        CONFIG_ERROR,

        /* channel is released and requires an atomic commit to cleanup completely */
        DISABLE_PENDING,

        /* channel is released and the cleanup blob is added but not committed yet */
        DISABLE_BLOB_ADDED,

        /* channel disable has error */
        DISABLE_ERROR,
    };

    struct ChannelInfo {
        /* protect the channel info fields */
        mutable std::mutex channelInfoMutex;

        /* protect histDataCollecting variable */
        mutable std::mutex histDataCollectingMutex;

        /* track the channel status */
        ChannelStatus_t status GUARDED_BY(channelInfoMutex);

        /* token passed in by the histogram client */
        ndk::SpAIBinder token GUARDED_BY(channelInfoMutex);

        /* histogram client process id */
        pid_t pid GUARDED_BY(channelInfoMutex);

        /* requested roi from the client by registerHistogram or reconfigHistogram */
        HistogramRoiRect requestedRoi GUARDED_BY(channelInfoMutex);

        /* requested blocking roi from the client by registerHistogram or reconfigHistogram */
        HistogramRoiRect requestedBlockingRoi GUARDED_BY(channelInfoMutex);

        /* histogram config that would be applied to hardware, the requestedRoi may be different
         * from the roi described in workingConfig due to RRS (Runtime Resolution Switch) */
        HistogramConfig workingConfig GUARDED_BY(channelInfoMutex);

        /* histogram threshold that would be applied to the hardware which is used to prevent the
         * histogram data (16 bits) overflow */
        int threshold GUARDED_BY(channelInfoMutex);

        /* histogram data would be stored as part of the channel info */
        uint16_t histData[HISTOGRAM_BIN_COUNT];
        bool histDataCollecting; // GUARDED_BY(histDataCollectingMutex);
        std::condition_variable histDataCollecting_cv;

        ChannelInfo();
        ChannelInfo(const ChannelInfo& other);
    };

    /* TokenInfo is not only used to stored the corresponding channel id but also passed to the
     * binderdied callback */
    struct TokenInfo {
        /* corresponding channel id of the token */
        uint8_t channelId;

        /* pointer to the HistogramDevice, binderdied callback would use this pointer to cleanup the
         * channel in HistogramDevice by the member function unregisterHistogram */
        HistogramDevice* histogramDevice;

        /* binderdied callback would call unregisterHistogram with this token */
        ndk::SpAIBinder token;
    };

    /**
     * HistogramDevice
     *
     * Construct the HistogramDevice to mange histogram channel.
     *
     * @display display pointer which would be stored in mDisplay.
     * @channelCount number of the histogram channels in the system.
     * @reservedChannels a list of channel id that are reserved by the driver.
     */
    explicit HistogramDevice(ExynosDisplay* display, uint8_t channelCount,
                             std::vector<uint8_t> reservedChannels);

    /**
     * ~HistogramDevice
     *
     * Destruct the HistogramDevice.
     */
    virtual ~HistogramDevice();

    /**
     * initDrm
     *
     * Get histogram info from crtc property and initialize the mHistogramCapability.
     *     1. The available histogram channel bitmask.
     *     2. Determine kernel support multi channel property or not.
     *
     * @crtc drm crtc object which would contain histogram related information.
     */
    void initDrm(const DrmCrtc& crtc);

    /**
     * getHistogramCapability
     *
     * Return the histogram capability for the system.
     *
     * @histogramCapability: describe the histogram capability for the system.
     * @return ok() when the interface is supported and arguments are valid, else otherwise.
     */
    ndk::ScopedAStatus getHistogramCapability(HistogramCapability* histogramCapability) const;

    /**
     * registerHistogram
     *
     * Register the histogram sampling config, and allocate a histogram channel if available.
     * If the display is not turned on, just store the histogram config. Otherwise, trigger the
     * onRefresh call to force the config take effect, and then the DPU hardware will continuously
     * sample the histogram data.
     *
     * @token binder object created by the client whose lifetime should be equal to the client. When
     * the binder object is destructed, the unregisterHistogram would be called automatically. Token
     * serves as the handle in every histogram operation.
     * @histogramConfig histogram config from the client.
     * @histogramErrorCode NONE when no error, or else otherwise. Client should retry when failed.
     * @return ok() when the interface is supported, or EX_UNSUPPORTED_OPERATION when the interface
     * is not supported yet.
     */
    ndk::ScopedAStatus registerHistogram(const ndk::SpAIBinder& token,
                                         const HistogramConfig& histogramConfig,
                                         HistogramErrorCode* histogramErrorCode);

    /**
     * queryHistogram
     *
     * Query the histogram data from the corresponding channel of the token.
     *
     * @token is the handle registered via registerHistogram which would be used to identify the
     * channel.
     * @histogramBuffer 256 * 16 bits buffer to store the luma counts return by the histogram
     * hardware.
     * @histogramErrorCode NONE when no error, or else otherwise. Client should examine this
     * errorcode.
     * @return ok() when the interface is supported, or EX_UNSUPPORTED_OPERATION when the interface
     * is not supported yet.
     */
    ndk::ScopedAStatus queryHistogram(const ndk::SpAIBinder& token,
                                      std::vector<char16_t>* histogramBuffer,
                                      HistogramErrorCode* histogramErrorCode);

    /**
     * reconfigHistogram
     *
     * Change the histogram config for the corresponding channel of the token.
     *
     * @token is the handle registered via registerHistogram which would be used to identify the
     * channel.
     * @histogramConfig histogram config from the client.
     * @histogramErrorCode NONE when no error, or else otherwise. Client should examine this
     * errorcode.
     * @return ok() when the interface is supported, or EX_UNSUPPORTED_OPERATION when the interface
     * is not supported yet.
     */
    ndk::ScopedAStatus reconfigHistogram(const ndk::SpAIBinder& token,
                                         const HistogramConfig& histogramConfig,
                                         HistogramErrorCode* histogramErrorCode);

    /**
     * unregisterHistogram
     *
     * Release the corresponding channel of the token and add the channel id to free channel list.
     *
     * @token is the handle registered via registerHistogram which would be used to identify the
     * channel.
     * @histogramErrorCode NONE when no error, or else otherwise. Client should examine this
     * errorcode.
     * @return ok() when the interface is supported, or EX_UNSUPPORTED_OPERATION when the interface
     * is not supported yet.
     */
    ndk::ScopedAStatus unregisterHistogram(const ndk::SpAIBinder& token,
                                           HistogramErrorCode* histogramErrorCode);

    /**
     * handleDrmEvent
     *
     * Handle the histogram channel drm event (EXYNOS_DRM_HISTOGRAM_CHANNEL_EVENT) and copy the
     * histogram data from event struct to channel info.
     *
     * @event histogram channel drm event pointer (struct exynos_drm_histogram_channel_event *)
     */
    void handleDrmEvent(void* event);

    /**
     * prepareAtomicCommit
     *
     * Prepare the histogram atomic commit for each channel (see prepareChannelCommit).
     *
     * @drmReq drm atomic request object
     */
    void prepareAtomicCommit(ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq);

    /**
     * postAtomicCommit
     *
     * After the atomic commit is done, update the channel status as below.
     * Channel_Status:
     *     CONFIG_BLOB_ADDED  -> CONFIG_COMMITTED
     *     DISABLE_BLOB_ADDED -> DISABLED
     */
    void postAtomicCommit();

    /**
     * dump
     *
     * Dump every histogram channel information.
     *
     * @result histogram channel dump information would be appended to this string
     */
    void dump(String8& result) const;

protected:
    HistogramCapability mHistogramCapability;

private:
    mutable std::mutex mAllocatorMutex;
    std::queue<uint8_t> mFreeChannels GUARDED_BY(mAllocatorMutex); // free channel list
    std::unordered_map<AIBinder*, TokenInfo> mTokenInfoMap GUARDED_BY(mAllocatorMutex);
    std::vector<ChannelInfo> mChannels;
    int32_t mDisplayActiveH = 0;
    int32_t mDisplayActiveV = 0;
    ExynosDisplay* mDisplay = nullptr;

    mutable std::mutex mInitDrmDoneMutex;
    bool mInitDrmDone GUARDED_BY(mInitDrmDoneMutex) = false;
    std::condition_variable mInitDrmDone_cv;

    /* Death recipient for the binderdied callback, would be deleted in the destructor */
    AIBinder_DeathRecipient* mDeathRecipient = nullptr;

    /**
     * initChannels
     *
     * Allocate channelCount channels and initialize the channel status for every channel.
     *
     * @channelCount number of channels in the system including the reserved channels.
     * @reservedChannels a list of channel id that are reserved by the driver.
     */
    void initChannels(uint8_t channelCount, const std::vector<uint8_t>& reservedChannels);

    /**
     * initHistogramCapability
     *
     * Initialize the histogramCapability which would be queried by the client (see
     * getHistogramCapability).
     *
     * @supportMultiChannel true if the kernel support multi channel property, false otherwise.
     */
    void initHistogramCapability(bool supportMultiChannel);

    /**
     * initPlatformHistogramCapability
     *
     * Initialize platform specific histogram capability.
     */
    virtual void initPlatformHistogramCapability() {}

    /**
     * waitInitDrmDone
     *
     * Wait until the initDrm is finished, or when the timeout expires.
     *
     * @return true if initDrm is finished, or false when the timeout expires.
     */
    bool waitInitDrmDone();

    /**
     * configHistogram
     *
     * Implementation of the registerHistogram and reconfigHistogram.
     *
     * @token binder object created by the client.
     * @histogramConfig histogram config requested by the client.
     * @histogramErrorCode::NONE when success, or else otherwise.
     * @isReconfig is true if it is not the register request, only need to change the config.
     * @return ok() when the interface is supported, or else otherwise.
     */
    ndk::ScopedAStatus configHistogram(const ndk::SpAIBinder& token,
                                       const HistogramConfig& histogramConfig,
                                       HistogramErrorCode* histogramErrorCode, bool isReconfig);

    /**
     * getHistogramData
     *
     * Get the histogram data by sending ioctl request which will allocate the drm event for
     * histogram, and wait on the condition variable histDataCollecting_cv until the drm event is
     * handled or timeout. Copy the histogram data from channel info to histogramBuffer.
     *
     * @channelId histogram channel id.
     * @histogramBuffer AIDL created buffer which will be sent back to the client.
     * @histogramErrorCode::NONE when success, or else otherwise.
     */
    void getHistogramData(uint8_t channelId, std::vector<char16_t>* histogramBuffer,
                          HistogramErrorCode* histogramErrorCode);

    /**
     * parseDrmEvent
     *
     * Parse the histogram drm event. This function should get the histogram channel id
     * and the histogram buffer address from the event struct.
     *
     * @event histogram drm event struct.
     * @channelId stores the extracted channel id from the event.
     * @buffer stores the extracted buffer address from the event.
     * @return NO_ERROR on success, else otherwise.
     */
    int parseDrmEvent(void* event, uint8_t& channelId, char16_t*& buffer) const;

    /**
     * acquireChannelLocked
     *
     * Acquire an available channel from the mFreeChannels, and record the token to channel id
     * mapping info. Should be called with mAllocatorMutex held.
     *
     * @token binder object created by the client.
     * @channelId store the acquired channel id.
     * @return HistogramErrorCode::NONE when success, or else otherwise.
     */
    HistogramErrorCode acquireChannelLocked(const ndk::SpAIBinder& token, uint8_t& channelId)
            REQUIRES(mAllocatorMutex);

    /**
     * releaseChannelLocked
     *
     * Find the corresponding channel id of the token and release the channel. Add the channel id to
     * the mFreeChannels and cleanup the channel. Should be called with mAllocatorMutex held.
     *
     * @channelId the channel id to be cleanup.
     */
    void releaseChannelLocked(uint8_t channelId) REQUIRES(mAllocatorMutex);

    /**
     * getChannelIdByTokenLocked
     *
     * Convert the token to the channel id. Should be called with mAllocatorMutex held.
     *
     * @token binder object created by the client.
     * @return HistogramErrorCode::NONE when success, or else otherwise.
     */
    HistogramErrorCode getChannelIdByTokenLocked(const ndk::SpAIBinder& token, uint8_t& channelId)
            REQUIRES(mAllocatorMutex);

    /**
     * cleanupChannelInfo
     *
     * Cleanup the channel info and set status to DISABLE_PENDING which means need to wait
     * for the atomic commit to release the kernel and hardware channel resources.
     *
     * @channelId the channel id to be cleanup.
     */
    void cleanupChannelInfo(uint8_t channelId);

    /**
     * fillupChannelInfo
     *
     * Fillup the channel info with the histogramConfig from the client, and set status to
     * CONFIG_PENDING which means need to wait for the atomic commit to configure the
     * channel.
     *
     * @channelId the channel id to be configured.
     * @token binder object created by the client.
     * @histogramConfig histogram config requested by the client.
     */
    void fillupChannelInfo(uint8_t channelId, const ndk::SpAIBinder& token,
                           const HistogramConfig& histogramConfig);

    /**
     * prepareChannelCommit
     *
     * For the histogram channel needed to be configured, prepare the histogram channel config into
     * the struct histogram_channel_config which will be used to creating the drm blob in
     * setDisplayHistogramChannelSetting.
     * ChannelStatus_t:
     *     CONFIG_PENDING -> CONFIG_BLOB_ADDED
     *     CONFIG_DONE (detect roi needs update due to resolution change) -> CONFIG_BLOB_ADDED
     *
     * For the histogram channel needed to be disabled, call clearDisplayHistogramChannelSetting to
     * disable.
     * ChannelStatus_t:
     *     DISABLE_PENDING -> DISABLE_BLOB_ADDED
     *
     * @drmReq drm atomic request object
     * @channelId histogram channel id
     * @moduleDisplayInterface display drm interface pointer
     * @isResolutionChanged true if the resolution change is detected, false otherwise.
     * @return NO_ERROR on success, else otherwise
     */
    int prepareChannelCommit(ExynosDisplayDrmInterface::DrmModeAtomicReq& drmReq, uint8_t channelId,
                             ExynosDisplayDrmInterface* moduleDisplayInterface,
                             bool isResolutionChanged);

    /**
     * createHistogramDrmConfigLocked
     *
     * Allocate and initialize the histogram config for the drm driver. Composer would trigger
     * setDisplayHistogramChannelSetting and create the property blob with this config. The
     * allcoated config should be deleted via deleteHistogramDrmConfig after the property blob
     * is created. This function should be called with channelInfoMutex hold.
     *
     * @channel histogram channel.
     * @configPtr shared pointer to the allocated histogram config struct.
     * @length size of the histogram config.
     * @return NO_ERROR on success, else otherwise
     */
    int createHistogramDrmConfigLocked(const ChannelInfo& channel, std::shared_ptr<void>& configPtr,
                                       size_t& length) const REQUIRES(channel.channelInfoMutex);

    /**
     * convertRoiLocked
     *
     * Linear transform the requested roi (based on panel full resolution) into the working roi
     * (active resolution).
     *
     * @moduleDisplayInterface the displayInterface which contains the full resolution info
     * @requestedRoi requested roi
     * @workingRoi converted roi from the requested roi
     * @return NO_ERROR on success, else otherwise
     */
    int convertRoiLocked(ExynosDisplayDrmInterface* moduleDisplayInterface,
                         const HistogramRoiRect& requestedRoi, HistogramRoiRect& workingRoi) const;

    void dumpHistogramCapability(String8& result) const;

    HistogramErrorCode validateHistogramConfig(const HistogramConfig& histogramConfig) const;
    HistogramErrorCode validateHistogramRoi(const HistogramRoiRect& roi, const char* roiType) const;
    HistogramErrorCode validateHistogramWeights(const HistogramWeights& weights) const;
    HistogramErrorCode validateHistogramSamplePos(const HistogramSamplePos& samplePos) const;
    HistogramErrorCode validateHistogramBlockingRoi(
            const std::optional<HistogramRoiRect>& blockingRoi) const;

    int calculateThreshold(const HistogramRoiRect& roi) const;

    static std::string toString(const ChannelStatus_t& status);
    static std::string toString(const HistogramRoiRect& roi);
    static std::string toString(const HistogramWeights& weights);
};

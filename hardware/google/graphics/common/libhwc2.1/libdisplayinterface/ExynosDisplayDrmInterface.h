/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef _EXYNOSDISPLAYDRMINTERFACE_H
#define _EXYNOSDISPLAYDRMINTERFACE_H

#include <drm/samsung_drm.h>
#include <utils/Condition.h>
#include <utils/Mutex.h>
#include <xf86drmMode.h>

#include <list>
#include <unordered_map>

#include "ExynosDisplay.h"
#include "ExynosDisplayInterface.h"
#include "ExynosHWC.h"
#include "ExynosMPP.h"
#include "drmconnector.h"
#include "drmcrtc.h"
#include "histogram/histogram.h"
#include "vsyncworker.h"

/* Max plane number of buffer object */
#define HWC_DRM_BO_MAX_PLANES 4

/* Monitor Descriptor data is 13 bytes in VESA EDID Standard */
#define MONITOR_DESCRIPTOR_DATA_LENGTH 13

#ifndef HWC_FORCE_PANIC_PATH
#define HWC_FORCE_PANIC_PATH "/d/dpu/panic"
#endif

using namespace android;

class ExynosDevice;

template <typename T>
using DrmArray = std::array<T, HWC_DRM_BO_MAX_PLANES>;

class DisplayConfigGroupIdGenerator {
public:
    DisplayConfigGroupIdGenerator() = default;
    ~DisplayConfigGroupIdGenerator() = default;

    // Vrr will utilize the last two parameters. In the case of non-vrr, they are automatically set
    // to 0. Avoid using this class with a mix of Vrr and non-Vrr settings, as doing so may yield
    // unexpected results.
    int getGroupId(int width, int height, int minFrameInterval = 0, int vsyncPeriod = 0) {
        const auto &key = std::make_tuple(width, height, minFrameInterval, vsyncPeriod);
        if (groups_.count(key) > 0) {
            return groups_[key];
        }
        size_t last_id = groups_.size();
        groups_[key] = last_id;
        return last_id;
    }

private:
    std::map<std::tuple<int, int, int, int>, int> groups_;
};

class FramebufferManager {
    public:
        FramebufferManager(){};
        ~FramebufferManager();
        void init(int drmFd);

        // get buffer for provided config, if a buffer with same config is already cached it will be
        // reused otherwise one will be allocated. returns fbId that can be used to attach to the
        // plane, any buffers allocated/reused with this call will be bound to the corresponding
        // layer. Those fbIds will be cleaned up once the layer was destroyed.
        int32_t getBuffer(const exynos_win_config_data &config, uint32_t &fbId);

        void checkShrink();

        void cleanup(const ExynosLayer *layer);

        // The flip function is to help clean up the cached fbIds of destroyed
        // layers after the previous fdIds were update successfully on the
        // screen.
        // This should be called after the frame update.
        void flip(const bool hasSecureFrameBuffer, const bool hasM2mSecureLayerBuffer);

        // release all currently tracked buffers, this can be called for example when display is turned
        // off
        void releaseAll();

    private:
        // this struct should contain elements that can be used to identify framebuffer more easily
        struct Framebuffer {
            struct BufferDesc {
                uint64_t bufferId;
                int drmFormat;
                bool isSecure;
                bool operator==(const Framebuffer::BufferDesc &rhs) const {
                    return (bufferId == rhs.bufferId && drmFormat == rhs.drmFormat &&
                            isSecure == rhs.isSecure);
                }
            };
            struct SolidColorDesc {
                uint32_t width;
                uint32_t height;
                bool operator==(const Framebuffer::SolidColorDesc &rhs) const {
                    return (width == rhs.width && height == rhs.height);
                }
            };

            explicit Framebuffer(int fd, uint32_t fb, BufferDesc desc)
                  : drmFd(fd), fbId(fb), bufferDesc(desc){};
            explicit Framebuffer(int fd, uint32_t fb, SolidColorDesc desc)
                  : drmFd(fd), fbId(fb), colorDesc(desc){};
            ~Framebuffer() { drmModeRmFB(drmFd, fbId); };
            int drmFd;
            uint32_t fbId;
            union {
                BufferDesc bufferDesc;
                SolidColorDesc colorDesc;
            };
        };
        using FBList = std::list<std::unique_ptr<Framebuffer>>;

        template <class UnaryPredicate>
        uint32_t findCachedFbId(const ExynosLayer *layer, const bool isM2mSecureLayer,
                                UnaryPredicate predicate);
        int addFB2WithModifiers(uint32_t state, uint32_t width, uint32_t height, uint32_t drmFormat,
                                const DrmArray<uint32_t> &handles,
                                const DrmArray<uint32_t> &pitches,
                                const DrmArray<uint32_t> &offsets,
                                const DrmArray<uint64_t> &modifier, uint32_t *buf_id,
                                uint32_t flags);
        bool validateLayerInfo(uint32_t state, uint32_t pixel_format,
                               const DrmArray<uint32_t> &handles,
                               const DrmArray<uint64_t> &modifier);
        uint32_t getBufHandleFromFd(int fd);
        void freeBufHandle(uint32_t handle);
        void removeFBsThreadRoutine();

        void markInuseLayerLocked(const ExynosLayer *layer, const bool isM2mSecureLayer)
                REQUIRES(mMutex);
        void destroyUnusedLayersLocked() REQUIRES(mMutex);
        void destroySecureFramebufferLocked() REQUIRES(mMutex);
        void destroyM2mSecureLayerBufferLocked() REQUIRES(mMutex);

        int mDrmFd = -1;

        // mCachedLayerBuffers map keep the relationship between Layer and FBList.
        // mCachedM2mSecureLayerBuffers map keep the relationship between M2M secure
        // Layer and FBList. The map entry will be deleted once the layer is destroyed.
        std::map<const ExynosLayer *, FBList> mCachedLayerBuffers;
        std::map<const ExynosLayer *, FBList> mCachedM2mSecureLayerBuffers;

        // mCleanBuffers list keeps fbIds of destroyed layers. Those fbIds will
        // be destroyed in mRmFBThread thread.
        FBList mCleanBuffers;

        // mCacheShrinkPending is set when we want to clean up unused layers
        // in mCachedLayerBuffers. When the flag is set, mCachedLayersInuse will
        // keep in-use layers in this frame update. Those unused layers will be
        // freed at the end of the update. mCacheM2mSecureShrinkPending is same to
        // mCacheShrinkPending but for mCachedM2mSecureLayerBuffers.
        // TODO: have a better way to maintain inuse layers
        bool mCacheShrinkPending = false;
        bool mCacheM2mSecureShrinkPending = false;
        bool mHasSecureFramebuffer = false;
        bool mHasM2mSecureLayerBuffer = false;
        std::set<const ExynosLayer *> mCachedLayersInuse;
        std::set<const ExynosLayer *> mCachedM2mSecureLayersInuse;

        std::thread mRmFBThread;
        bool mRmFBThreadRunning = false;
        Condition mFlipDone;
        Mutex mMutex;

        static constexpr size_t MAX_CACHED_LAYERS = 16;
        static constexpr size_t MAX_CACHED_M2M_SECURE_LAYERS = 1;
        static constexpr size_t MAX_CACHED_BUFFERS_PER_LAYER = 32;
        static constexpr size_t MAX_CACHED_M2M_SECURE_BUFFERS_PER_LAYER = 3;
};

inline bool isFramebuffer(const ExynosLayer *layer) {
    return layer == nullptr;
}

template <class UnaryPredicate>
uint32_t FramebufferManager::findCachedFbId(const ExynosLayer *layer, const bool isM2mSecureLayer,
                                            UnaryPredicate predicate) {
    Mutex::Autolock lock(mMutex);
    markInuseLayerLocked(layer, isM2mSecureLayer);
    const auto &cachedBuffers =
            (!isM2mSecureLayer) ? mCachedLayerBuffers[layer] : mCachedM2mSecureLayerBuffers[layer];
    const auto it = std::find_if(cachedBuffers.begin(), cachedBuffers.end(), predicate);
    return (it != cachedBuffers.end()) ? (*it)->fbId : 0;
}

class ExynosDisplayDrmInterface :
    public ExynosDisplayInterface,
    public VsyncCallback
{
    public:
        class DrmModeAtomicReq {
            public:
                DrmModeAtomicReq(ExynosDisplayDrmInterface *displayInterface);
                ~DrmModeAtomicReq();

                DrmModeAtomicReq(const DrmModeAtomicReq&) = delete;
                DrmModeAtomicReq& operator=(const DrmModeAtomicReq&) = delete;

                drmModeAtomicReqPtr pset() { return mPset; };
                void savePset() {
                    if (mSavedPset) {
                        drmModeAtomicFree(mSavedPset);
                    }
                    mSavedPset = drmModeAtomicDuplicate(mPset);
                }
                void restorePset() {
                    if (mPset) {
                        drmModeAtomicFree(mPset);
                    }
                    mPset = mSavedPset;
                    mSavedPset = NULL;
                }

                void setError(int err) { mError = err; };
                int getError() { return mError; };
                int32_t atomicAddProperty(const uint32_t id,
                        const DrmProperty &property,
                        uint64_t value, bool optional = false);
                String8& dumpAtomicCommitInfo(String8 &result, bool debugPrint = false);
                int commit(uint32_t flags, bool loggingForDebug = false);
                void addOldBlob(uint32_t blob_id) {
                    mOldBlobs.push_back(blob_id);
                };
                int destroyOldBlobs() {
                    for (auto &blob : mOldBlobs) {
                        int ret = mDrmDisplayInterface->mDrmDevice->DestroyPropertyBlob(blob);
                        if (ret) {
                            HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                                    "Failed to destroy old blob after commit %d", ret);
                            return ret;
                        }
                    }
                    mOldBlobs.clear();
                    return NO_ERROR;
                };
                void dumpDrmAtomicCommitMessage(int err);

                void setAckCallback(std::function<void()> callback) {
                    mAckCallback = std::move(callback);
                };

            private:
                drmModeAtomicReqPtr mPset;
                drmModeAtomicReqPtr mSavedPset;
                int mError = 0;
                ExynosDisplayDrmInterface *mDrmDisplayInterface = NULL;
                /* Destroy old blobs after commit */
                std::vector<uint32_t> mOldBlobs;
                int drmFd() const { return mDrmDisplayInterface->mDrmDevice->fd(); }

                std::function<void()> mAckCallback;

                static constexpr uint32_t kAllowDumpDrmAtomicMessageTimeMs = 5000U;
                static constexpr const char* kDrmModuleParametersDebugNode =
                        "/sys/module/drm/parameters/debug";
                static constexpr const int kEnableDrmAtomicMessage = 16;
                static constexpr const int kDisableDrmDebugMessage = 0;

        };
        class ExynosVsyncCallback {
            public:
                void enableVSync(bool enable) {
                    mVsyncEnabled = enable;
                    resetVsyncTimeStamp();
                };
                bool getVSyncEnabled() { return mVsyncEnabled; };
                void setDesiredVsyncPeriod(uint64_t period) {
                    mDesiredVsyncPeriod = period;
                    resetVsyncTimeStamp();
                };
                uint64_t getDesiredVsyncPeriod() { return mDesiredVsyncPeriod;};
                uint64_t getVsyncTimeStamp() { return mVsyncTimeStamp; };
                uint64_t getVsyncPeriod() { return mVsyncPeriod; };
                bool Callback(int display, int64_t timestamp);
                void resetVsyncTimeStamp() { mVsyncTimeStamp = 0; };
                void resetDesiredVsyncPeriod() { mDesiredVsyncPeriod = 0;};
            private:
                bool mVsyncEnabled = false;
                uint64_t mVsyncTimeStamp = 0;
                uint64_t mVsyncPeriod = 0;
                uint64_t mDesiredVsyncPeriod = 0;
        };
        void Callback(int display, int64_t timestamp) override;

        ExynosDisplayDrmInterface(ExynosDisplay *exynosDisplay);
        ~ExynosDisplayDrmInterface();
        virtual void init(ExynosDisplay *exynosDisplay);
        virtual int32_t setPowerMode(int32_t mode);
        virtual int32_t setLowPowerMode() override;
        virtual bool isDozeModeAvailable() const {
            return mDozeDrmMode.h_display() > 0 && mDozeDrmMode.v_display() > 0;
        };
        virtual int32_t setVsyncEnabled(uint32_t enabled);
        virtual int32_t getDisplayConfigs(
                uint32_t* outNumConfigs,
                hwc2_config_t* outConfigs);
        virtual void dumpDisplayConfigs();
        virtual bool supportDataspace(int32_t dataspace);
        virtual int32_t getColorModes(uint32_t* outNumModes, int32_t* outModes);
        virtual int32_t setColorMode(int32_t mode);
        virtual int32_t setActiveConfig(hwc2_config_t config);
        virtual int32_t setCursorPositionAsync(uint32_t x_pos, uint32_t y_pos);
        virtual int32_t updateHdrCapabilities();
        virtual int32_t deliverWinConfigData();
        virtual int32_t clearDisplay(bool needModeClear = false);
        virtual int32_t disableSelfRefresh(uint32_t disable);
        virtual int32_t setForcePanic();
        virtual int getDisplayFd() { return mDrmDevice->fd(); };
        virtual int32_t initDrmDevice(DrmDevice *drmDevice);
        virtual int getDrmDisplayId(uint32_t type, uint32_t index);
        virtual uint32_t getMaxWindowNum() { return mMaxWindowNum; };
        virtual int32_t getReadbackBufferAttributes(int32_t* /*android_pixel_format_t*/ outFormat,
                int32_t* /*android_dataspace_t*/ outDataspace);
        virtual int32_t getDisplayIdentificationData(uint8_t* outPort,
                uint32_t* outDataSize, uint8_t* outData);
        virtual bool needRefreshOnLP();

        /* For HWC 2.4 APIs */
        virtual int32_t getDisplayVsyncPeriod(
                hwc2_vsync_period_t* outVsyncPeriod);
        virtual int32_t getConfigChangeDuration();
        virtual int32_t getVsyncAppliedTime(hwc2_config_t config,
                int64_t* actualChangeTime);
        virtual int32_t setActiveConfigWithConstraints(
                hwc2_config_t config, bool test = false);

        virtual int32_t setDisplayColorSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq __unused &drmReq) {
            return NO_ERROR;
        }
        virtual int32_t setPlaneColorSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq,
                const std::unique_ptr<DrmPlane> &plane,
                const exynos_win_config_data& config,
                uint32_t &solidColor)
        { return NO_ERROR;};
        virtual void destroyLayer(ExynosLayer *layer) override;

        /* For HWC 3.0 APIs */
        virtual int32_t getDisplayIdleTimerSupport(bool &outSupport);
        virtual int32_t getDefaultModeId(int32_t *modeId) override;

        virtual int32_t waitVBlank();
        float getDesiredRefreshRate() { return mDesiredModeState.mode.v_refresh(); }
        int32_t getOperationRate() {
            if (mExynosDisplay->mOperationRateManager) {
                    return mExynosDisplay->mOperationRateManager->getTargetOperationRate();
            }
            return 0;
        }

        /* For Histogram */
        virtual int32_t setDisplayHistogramSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) {
            return NO_ERROR;
        }

        /* For Histogram Multi Channel support */
        int32_t setDisplayHistogramChannelSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq, uint8_t channelId,
                void *blobData, size_t blobLength);
        int32_t clearDisplayHistogramChannelSetting(
                ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq, uint8_t channelId);
        enum class HistogramChannelIoctl_t {
            /* send the histogram data request by calling histogram_channel_request_ioctl */
            REQUEST = 0,

            /* cancel the histogram data request by calling histogram_channel_cancel_ioctl */
            CANCEL,
        };
        int32_t sendHistogramChannelIoctl(HistogramChannelIoctl_t control, uint8_t channelId) const;

        int32_t getFrameCount() { return mFrameCounter; }
        virtual void registerHistogramInfo(const std::shared_ptr<IDLHistogram> &info) { return; }
        virtual int32_t setHistogramControl(hidl_histogram_control_t enabled) { return NO_ERROR; }
        virtual int32_t setHistogramData(void *bin) { return NO_ERROR; }
        int32_t getActiveModeHDisplay() { return mActiveModeState.mode.h_display(); }
        int32_t getActiveModeVDisplay() { return mActiveModeState.mode.v_display(); }
        uint32_t getActiveModeId() { return mActiveModeState.mode.id(); }
        int32_t getPanelFullResolutionHSize() { return mPanelFullResolutionHSize; }
        int32_t getPanelFullResolutionVSize() { return mPanelFullResolutionVSize; }
        uint32_t getCrtcId() { return mDrmCrtc->id(); }
        int32_t triggerClearDisplayPlanes();

        virtual void setVrrSettings(const VrrSettings_t& vrrSettings) override;
        bool isFullVrrSupported() const {
            return (mIsVrrModeSupported && mExynosDisplay->mDevice->isVrrApiSupported());
        }
        bool isPseudoVrrSupported() const {
            return (mIsVrrModeSupported && !mExynosDisplay->mDevice->isVrrApiSupported());
        }

        void handleDrmPropertyUpdate(uint32_t connector_id, uint32_t prop_id);

        /* store the manufacturer info and product id from EDID
         * - Manufacturer ID is stored in EDID byte 8 and 9.
         * - Manufacturer product ID is stored in EDID byte 10 and 11.
         */
        virtual void setManufacturerInfo(uint8_t edid8, uint8_t edid9) override;
        virtual uint32_t getManufacturerInfo() override { return mManufacturerInfo; }
        virtual void setProductId(uint8_t edid10, uint8_t edid11) override;
        virtual uint32_t getProductId() override { return mProductId; }

    protected:
        enum class HalMipiSyncType : uint32_t {
            HAL_MIPI_CMD_SYNC_REFRESH_RATE = 0,
            HAL_MIPI_CMD_SYNC_LHBM,
            HAL_MIPI_CMD_SYNC_GHBM,
            HAL_MIPI_CMD_SYNC_BL,
            HAL_MIPI_CMD_SYNC_OP_RATE,
        };

        struct ModeState {
            enum ModeStateType {
                MODE_STATE_NONE = 0U,
                MODE_STATE_REFRESH_RATE = 1U << 0,
                MODE_STATE_RESOLUTION = 1U << 1,
                MODE_STATE_FORCE_MODE_SET = 1U << 2,
            };
            DrmMode mode;
            uint32_t blob_id = 0;
            uint32_t old_blob_id = 0;
            void setMode(const DrmMode newMode, const uint32_t modeBlob,
                    DrmModeAtomicReq &drmReq) {
                if (newMode.v_refresh() != mode.v_refresh()) {
                    mModeState |= ModeStateType::MODE_STATE_REFRESH_RATE;
                }
                if (isFullModeSwitch(newMode)) {
                    mModeState |= ModeStateType::MODE_STATE_RESOLUTION;
                }

                drmReq.addOldBlob(old_blob_id);
                mode = newMode;
                old_blob_id = blob_id;
                blob_id = modeBlob;
            };
            void reset() {
                *this = {};
            };
            void apply(ModeState &toModeState, DrmModeAtomicReq &drmReq) {
                toModeState.setMode(mode, blob_id, drmReq);
                drmReq.addOldBlob(old_blob_id);
                reset();
            };

            int32_t mModeState = ModeStateType::MODE_STATE_NONE;
            void forceModeSet() { mModeState |= ModeStateType::MODE_STATE_FORCE_MODE_SET; }
            void clearPendingModeState() { mModeState = ModeStateType::MODE_STATE_NONE; }
            bool needsModeSet() const { return mModeState != ModeStateType::MODE_STATE_NONE; }
            bool isSeamless() const { return !(mModeState & ModeStateType::MODE_STATE_RESOLUTION); }
            bool isFullModeSwitch(const DrmMode &newMode) {
                if ((mode.h_display() != newMode.h_display()) ||
                    (mode.v_display() != newMode.v_display()))
                    return true;
                return false;
            }
        };
        int32_t createModeBlob(const DrmMode &mode, uint32_t &modeBlob);
        int32_t setDisplayMode(DrmModeAtomicReq& drmReq, const uint32_t& modeBlob,
                               const uint32_t& modeId);
        int32_t clearDisplayMode(DrmModeAtomicReq &drmReq);
        int32_t clearDisplayPlanes(DrmModeAtomicReq &drmReq);
        int32_t choosePreferredConfig();
        int getDeconChannel(ExynosMPP *otfMPP);
        /*
         * This function adds FB and gets new fb id if fbId is 0,
         * if fbId is not 0, this reuses fbId.
         */
        int32_t setupCommitFromDisplayConfig(DrmModeAtomicReq &drmReq,
                const exynos_win_config_data &config,
                const uint32_t configIndex,
                const std::unique_ptr<DrmPlane> &plane,
                uint32_t &fbId);

        int32_t setupPartialRegion(DrmModeAtomicReq &drmReq);
        void parseBlendEnums(const DrmProperty &property);
        void parseStandardEnums(const DrmProperty &property);
        void parseTransferEnums(const DrmProperty &property);
        void parseRangeEnums(const DrmProperty &property);
        void parseColorModeEnums(const DrmProperty &property);
        void parseMipiSyncEnums(const DrmProperty &property);
        void updateMountOrientation();
        void parseRCDId(const DrmProperty &property);

        int32_t setupWritebackCommit(DrmModeAtomicReq &drmReq);
        int32_t clearWritebackCommit(DrmModeAtomicReq &drmReq);

    private:
        int32_t updateColorSettings(DrmModeAtomicReq &drmReq, uint64_t dqeEnabled);
        int32_t getLowPowerDrmModeModeInfo();
        int32_t setActiveDrmMode(DrmMode const &mode);
        void setMaxWindowNum(uint32_t num) { mMaxWindowNum = num; };
        int32_t getSpecialChannelId(uint32_t planeId);

    protected:
        struct PartialRegionState {
            struct drm_clip_rect partial_rect = {0, 0, 0, 0};
            uint32_t blob_id = 0;
            bool isUpdated(drm_clip_rect rect) {
                return ((partial_rect.x1 != rect.x1) ||
                        (partial_rect.y1 != rect.y1) ||
                        (partial_rect.x2 != rect.x2) ||
                        (partial_rect.y2 != rect.y2));
            };
        };

        struct BlockingRegionState {
            struct decon_win_rect mRegion;
            uint32_t mBlobId = 0;

            inline bool operator==(const decon_win_rect &rhs) const {
                return mRegion.x == rhs.x && mRegion.y == rhs.y && mRegion.w == rhs.w &&
                        mRegion.h == rhs.h;
            }
            inline bool operator!=(const decon_win_rect &rhs) const { return !(*this == rhs); }
        };

        class DrmReadbackInfo {
            public:
                void init(DrmDevice *drmDevice, uint32_t displayId);
                ~DrmReadbackInfo() {
                    if (mDrmDevice == NULL)
                        return;
                    if (mOldFbId > 0)
                        drmModeRmFB(mDrmDevice->fd(), mOldFbId);
                    if (mFbId > 0)
                        drmModeRmFB(mDrmDevice->fd(), mFbId);
                }
                DrmConnector* getWritebackConnector() { return mWritebackConnector; };
                void setFbId(uint32_t fbId) {
                    if ((mDrmDevice != NULL) && (mOldFbId > 0))
                        drmModeRmFB(mDrmDevice->fd(), mOldFbId);
                    mOldFbId = mFbId;
                    mFbId = fbId;
                }
                void pickFormatDataspace();
                static constexpr uint32_t PREFERRED_READBACK_FORMAT =
                    HAL_PIXEL_FORMAT_RGBA_8888;
                uint32_t mReadbackFormat = HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED;
                bool mNeedClearReadbackCommit = false;
            private:
                DrmDevice *mDrmDevice = NULL;
                DrmConnector *mWritebackConnector = NULL;
                uint32_t mFbId = 0;
                uint32_t mOldFbId = 0;
                std::vector<uint32_t> mSupportedFormats;
        };
        DrmDevice *mDrmDevice;
        DrmCrtc *mDrmCrtc;
        DrmConnector *mDrmConnector;
        VSyncWorker mDrmVSyncWorker;
        ExynosVsyncCallback mVsyncCallback;
        ModeState mActiveModeState;
        ModeState mDesiredModeState;
        PartialRegionState mPartialRegionState;
        BlockingRegionState mBlockState;
        /* Mapping plane id to ExynosMPP, key is plane id */
        std::unordered_map<uint32_t, ExynosMPP*> mExynosMPPsForPlane;

        DrmEnumParser::MapHal2DrmEnum mBlendEnums;
        DrmEnumParser::MapHal2DrmEnum mStandardEnums;
        DrmEnumParser::MapHal2DrmEnum mTransferEnums;
        DrmEnumParser::MapHal2DrmEnum mRangeEnums;
        DrmEnumParser::MapHal2DrmEnum mColorModeEnums;
        DrmEnumParser::MapHal2DrmEnum mMipiSyncEnums;

        DrmReadbackInfo mReadbackInfo;
        FramebufferManager mFBManager;
        std::array<uint8_t, MONITOR_DESCRIPTOR_DATA_LENGTH> mMonitorDescription;
        nsecs_t mLastDumpDrmAtomicMessageTime;
        bool mIsResolutionSwitchInProgress = false;

    private:
        int32_t getDisplayFakeEdid(uint8_t &outPort, uint32_t &outDataSize, uint8_t *outData);

        String8 mDisplayTraceName;
        DrmMode mDozeDrmMode;
        uint32_t mMaxWindowNum = 0;
        int32_t mFrameCounter = 0;
        int32_t mPanelFullResolutionHSize = 0;
        int32_t mPanelFullResolutionVSize = 0;

        // Vrr related settings.
        bool mIsVrrModeSupported = false;
        int32_t mNotifyExpectedPresentHeadsUpNs = 0;
        int32_t mNotifyExpectedPresentTimeoutNs = 0;
        std::function<void(int)> mConfigChangeCallback;

        /**
         * retrievePanelFullResolution
         *
         * Retrieve the panel full resolution by looking into the modes of the mDrmConnector
         * and store the full resolution info in mPanelFullResolutionHSize (x component) and
         * mPanelFullResolutionVSize (y component).
         *
         * Note: this function will be called only once in initDrmDevice()
         */
        void retrievePanelFullResolution();

        const uint8_t kEDIDManufacturerIDByte1 = 8;
        const uint8_t kEDIDManufacturerIDByte2 = 9;
        const uint8_t kEDIDProductIDByte1 = 10;
        const uint8_t kEDIDProductIDByte2 = 11;
        uint32_t mManufacturerInfo;
        uint32_t mProductId;

    public:
        virtual bool readHotplugStatus();
        virtual int readHotplugErrorCode();
        virtual void resetHotplugErrorCode();
};

#endif

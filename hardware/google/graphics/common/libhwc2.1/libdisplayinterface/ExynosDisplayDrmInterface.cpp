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

#define ATRACE_TAG (ATRACE_TAG_GRAPHICS | ATRACE_TAG_HAL)

#include "ExynosDisplayDrmInterface.h"

#include <aidl/android/hardware/drm/HdcpLevels.h>
#include <cutils/properties.h>
#include <drm.h>
#include <drm/drm_fourcc.h>
#include <sys/types.h>
#include <xf86drm.h>

#include <algorithm>
#include <numeric>

#include "BrightnessController.h"
#include "ExynosHWCDebug.h"
#include "ExynosHWCHelper.h"
#include "ExynosLayer.h"
#include "ExynosPrimaryDisplay.h"
#include "HistogramController.h"

using ::aidl::android::hardware::drm::HdcpLevel;
using ::aidl::android::hardware::drm::HdcpLevels;
using namespace std::chrono_literals;
using namespace SOC_VERSION;

constexpr uint32_t MAX_PLANE_NUM = 3;
constexpr uint32_t CBCR_INDEX = 1;
constexpr float DISPLAY_LUMINANCE_UNIT = 10000;
constexpr auto vsyncPeriodTag = "VsyncPeriod";

typedef struct _drmModeAtomicReqItem drmModeAtomicReqItem, *drmModeAtomicReqItemPtr;

struct _drmModeAtomicReqItem {
    uint32_t object_id;
    uint32_t property_id;
    uint64_t value;
};

struct _drmModeAtomicReq {
    uint32_t cursor;
    uint32_t size_items;
    drmModeAtomicReqItemPtr items;
};

using namespace vendor::graphics;

extern struct exynos_hwc_control exynosHWCControl;
static const int32_t kUmPerInch = 25400;

int writeIntToKernelFile(const char* path, const int value) {
    std::ofstream ofs(path);

    if (!ofs.is_open()) {
        ALOGW("%s(): unable to open %s (%s)", __func__, path, strerror(errno));
        return -1;
    }

    ofs << value << std::endl;

    return 0;
}

FramebufferManager::~FramebufferManager()
{
    {
        Mutex::Autolock lock(mMutex);
        mRmFBThreadRunning = false;
    }
    mFlipDone.signal();
    mRmFBThread.join();
}

void FramebufferManager::init(int drmFd)
{
    mDrmFd = drmFd;
    mRmFBThreadRunning = true;
    mRmFBThread = std::thread(&FramebufferManager::removeFBsThreadRoutine, this);
    pthread_setname_np(mRmFBThread.native_handle(), "RemoveFBsThread");
}

uint32_t FramebufferManager::getBufHandleFromFd(int fd)
{
    uint32_t gem_handle = 0;

    int ret = drmPrimeFDToHandle(mDrmFd, fd, &gem_handle);
    if (ret) {
        ALOGE("drmPrimeFDToHandle failed with fd %d error %d (%s)", fd, ret, strerror(errno));
    }
    return gem_handle;
}

int FramebufferManager::addFB2WithModifiers(uint32_t state, uint32_t width, uint32_t height,
                                            uint32_t drmFormat, const DrmArray<uint32_t> &handles,
                                            const DrmArray<uint32_t> &pitches,
                                            const DrmArray<uint32_t> &offsets,
                                            const DrmArray<uint64_t> &modifier, uint32_t *buf_id,
                                            uint32_t flags) {
    if (CC_UNLIKELY(!validateLayerInfo(state, drmFormat, handles, modifier))) {
        return -EINVAL;
    }

    int ret = drmModeAddFB2WithModifiers(mDrmFd, width, height, drmFormat, handles.data(),
                                         pitches.data(), offsets.data(), modifier.data(), buf_id,
                                         flags);
    if (ret) ALOGE("Failed to add fb error %d\n", ret);

    return ret;
}

bool FramebufferManager::validateLayerInfo(uint32_t state, uint32_t drmFormat,
                                           const DrmArray<uint32_t> &handles,
                                           const DrmArray<uint64_t> &modifier) {
    switch (state) {
        case exynos_win_config_data::WIN_STATE_RCD:
            return drmFormat == DRM_FORMAT_C8 && handles[0] != 0 && handles[1] == 0 &&
                    modifier[0] == 0;
    }

    return true;
}

void FramebufferManager::checkShrink() {
    Mutex::Autolock lock(mMutex);

    mCacheShrinkPending = mCachedLayerBuffers.size() > MAX_CACHED_LAYERS;
    mCacheM2mSecureShrinkPending =
            mCachedM2mSecureLayerBuffers.size() > MAX_CACHED_M2M_SECURE_LAYERS;
}

void FramebufferManager::cleanup(const ExynosLayer *layer) {
    ATRACE_CALL();

    Mutex::Autolock lock(mMutex);
    auto clean = [&](std::map<const ExynosLayer *, FBList> &layerBuffs) {
        if (auto it = layerBuffs.find(layer); it != layerBuffs.end()) {
            mCleanBuffers.splice(mCleanBuffers.end(), std::move(it->second));
            layerBuffs.erase(it);
        }
    };
    clean(mCachedLayerBuffers);
    clean(mCachedM2mSecureLayerBuffers);
}

void FramebufferManager::removeFBsThreadRoutine()
{
    FBList cleanupBuffers;
    while (true) {
        {
            Mutex::Autolock lock(mMutex);
            if (!mRmFBThreadRunning) {
                break;
            }
            mFlipDone.wait(mMutex);
            cleanupBuffers.splice(cleanupBuffers.end(), mCleanBuffers);
        }
        ATRACE_NAME("cleanup framebuffers");
        cleanupBuffers.clear();
    }
}

int32_t FramebufferManager::getBuffer(const exynos_win_config_data &config, uint32_t &fbId) {
    ATRACE_CALL();
    int ret = NO_ERROR;
    int drmFormat = DRM_FORMAT_UNDEFINED;
    uint32_t bpp = 0;
    uint32_t bufferNum, planeNum = 0;
    uint32_t bufWidth, bufHeight = 0;
    bool isM2mSecureLayer = (config.protection && config.layer && config.layer->mM2mMPP);
    DrmArray<uint32_t> pitches = {0};
    DrmArray<uint32_t> offsets = {0};
    DrmArray<uint64_t> modifiers = {0};
    DrmArray<uint32_t> handles = {0};

    if (config.protection) modifiers[0] |= DRM_FORMAT_MOD_PROTECTION;

    if (config.state == config.WIN_STATE_BUFFER || config.state == config.WIN_STATE_RCD) {
        bufWidth = config.src.f_w;
        bufHeight = config.src.f_h;

        auto exynosFormat = halFormatToExynosFormat(config.format, config.compressionInfo.type);
        if (exynosFormat == nullptr) {
            ALOGE("%s:: unknown HAL format (%d)", __func__, config.format);
            return -EINVAL;
        }

        drmFormat = exynosFormat->drmFormat;
        if (drmFormat == DRM_FORMAT_UNDEFINED) {
            ALOGE("%s:: unknown drm format (%d)", __func__, config.format);
            return -EINVAL;
        }

        bpp = getBytePerPixelOfPrimaryPlane(config.format);
        if ((bufferNum = exynosFormat->bufferNum) == 0) {
            ALOGE("%s:: getBufferNumOfFormat(%d) error", __func__, config.format);
            return -EINVAL;
        }
        if (((planeNum = exynosFormat->planeNum) == 0) || (planeNum > MAX_PLANE_NUM)) {
            ALOGE("%s:: getPlaneNumOfFormat(%d) error, planeNum(%d)", __func__, config.format,
                  planeNum);
            return -EINVAL;
        }

        fbId = findCachedFbId(config.layer, isM2mSecureLayer,
                              [bufferDesc = Framebuffer::BufferDesc{config.buffer_id, drmFormat,
                                                                    config.protection}](
                                      auto &buffer) { return buffer->bufferDesc == bufferDesc; });
        if (fbId != 0) {
            return NO_ERROR;
        }

        if (config.compressionInfo.type == COMP_TYPE_AFBC) {
            uint64_t compressed_modifier = config.compressionInfo.modifier;
            switch (config.comp_src) {
                case DPP_COMP_SRC_G2D:
                    compressed_modifier |= AFBC_FORMAT_MOD_SOURCE_G2D;
                    break;
                case DPP_COMP_SRC_GPU:
                    compressed_modifier |= AFBC_FORMAT_MOD_SOURCE_GPU;
                    break;
                default:
                    break;
            }
            modifiers[0] |= DRM_FORMAT_MOD_ARM_AFBC(compressed_modifier);
        } else if (config.compressionInfo.type == COMP_TYPE_SBWC) {
            modifiers[0] |= DRM_FORMAT_MOD_SAMSUNG_SBWC(config.compressionInfo.modifier);
        }

        for (uint32_t bufferIndex = 0; bufferIndex < bufferNum; bufferIndex++) {
            pitches[bufferIndex] = config.src.f_w * bpp;
            modifiers[bufferIndex] = modifiers[0];
            handles[bufferIndex] = getBufHandleFromFd(config.fd_idma[bufferIndex]);
            if (handles[bufferIndex] == 0) {
                return -ENOMEM;
            }
        }

        if ((bufferNum == 1) && (planeNum > bufferNum)) {
            /* offset for cbcr */
            offsets[CBCR_INDEX] =
                    getExynosBufferYLength(config.src.f_w, config.src.f_h, config.format);
            for (uint32_t planeIndex = 1; planeIndex < planeNum; planeIndex++) {
                handles[planeIndex] = handles[0];
                pitches[planeIndex] = pitches[0];
                modifiers[planeIndex] = modifiers[0];
            }
        }
    } else if (config.state == config.WIN_STATE_COLOR) {
        bufWidth = config.dst.w;
        bufHeight = config.dst.h;
        modifiers[0] |= DRM_FORMAT_MOD_SAMSUNG_COLORMAP;
        drmFormat = DRM_FORMAT_BGRA8888;
        bufferNum = 0;
        handles[0] = 0xff000000;
        bpp = getBytePerPixelOfPrimaryPlane(HAL_PIXEL_FORMAT_BGRA_8888);
        pitches[0] = config.dst.w * bpp;
        fbId = findCachedFbId(config.layer, isM2mSecureLayer,
                              [colorDesc = Framebuffer::SolidColorDesc{bufWidth, bufHeight}](
                                      auto &buffer) { return buffer->colorDesc == colorDesc; });
        if (fbId != 0) {
            return NO_ERROR;
        }
    } else {
        ALOGE("%s:: unknown config state(%d)", __func__, config.state);
        return -EINVAL;
    }

    ret = addFB2WithModifiers(config.state, bufWidth, bufHeight, drmFormat, handles, pitches,
                              offsets, modifiers, &fbId, modifiers[0] ? DRM_MODE_FB_MODIFIERS : 0);

    for (uint32_t bufferIndex = 0; bufferIndex < bufferNum; bufferIndex++) {
        freeBufHandle(handles[bufferIndex]);
    }

    if (ret) {
        ALOGE("%s:: Failed to add FB, fb_id(%d), ret(%d), f_w: %d, f_h: %d, dst.w: %d, dst.h: %d, "
              "format: %d %4.4s, buf_handles[%d, %d, %d, %d], "
              "pitches[%d, %d, %d, %d], offsets[%d, %d, %d, %d], modifiers[%#" PRIx64 ", %#" PRIx64
              ", %#" PRIx64 ", %#" PRIx64 "]",
              __func__, fbId, ret, config.src.f_w, config.src.f_h, config.dst.w, config.dst.h,
              drmFormat, (char *)&drmFormat, handles[0], handles[1], handles[2], handles[3],
              pitches[0], pitches[1], pitches[2], pitches[3], offsets[0], offsets[1], offsets[2],
              offsets[3], modifiers[0], modifiers[1], modifiers[2], modifiers[3]);
        return ret;
    }

    if (config.layer || config.buffer_id) {
        Mutex::Autolock lock(mMutex);
        auto &cachedBuffers = (!isM2mSecureLayer) ? mCachedLayerBuffers[config.layer]
                                                  : mCachedM2mSecureLayerBuffers[config.layer];
        auto maxCachedBufferSize = (!isM2mSecureLayer) ? MAX_CACHED_BUFFERS_PER_LAYER
                                                       : MAX_CACHED_M2M_SECURE_BUFFERS_PER_LAYER;

        if (cachedBuffers.size() > maxCachedBufferSize) {
            ALOGW("FBManager: cached buffers size %zu exceeds limitation(%zu) while adding fbId %d",
                  cachedBuffers.size(), maxCachedBufferSize, fbId);
            mCleanBuffers.splice(mCleanBuffers.end(), cachedBuffers);
        }

        if (config.state == config.WIN_STATE_COLOR) {
            cachedBuffers.emplace_front(
                    new Framebuffer(mDrmFd, fbId,
                                    Framebuffer::SolidColorDesc{bufWidth, bufHeight}));
        } else {
            cachedBuffers.emplace_front(
                    new Framebuffer(mDrmFd, fbId,
                                    Framebuffer::BufferDesc{config.buffer_id, drmFormat,
                                                            config.protection}));
            mHasSecureFramebuffer |= (isFramebuffer(config.layer) && config.protection);
            mHasM2mSecureLayerBuffer |= isM2mSecureLayer;
        }
    } else {
        ALOGW("FBManager: possible leakage fbId %d was created", fbId);
    }

    return 0;
}

void FramebufferManager::flip(const bool hasSecureFrameBuffer, const bool hasM2mSecureLayerBuffer) {
    bool needCleanup = false;
    {
        Mutex::Autolock lock(mMutex);
        destroyUnusedLayersLocked();
        if (!hasSecureFrameBuffer) {
            destroySecureFramebufferLocked();
        }

        if (!hasM2mSecureLayerBuffer) {
            destroyM2mSecureLayerBufferLocked();
        }
        needCleanup = mCleanBuffers.size() > 0;
    }

    if (needCleanup) {
        mFlipDone.signal();
    }
}

void FramebufferManager::releaseAll()
{
    Mutex::Autolock lock(mMutex);
    mCachedLayerBuffers.clear();
    mCachedM2mSecureLayerBuffers.clear();
    mCleanBuffers.clear();
}

void FramebufferManager::freeBufHandle(uint32_t handle) {
    if (handle == 0) {
        return;
    }

    struct drm_gem_close gem_close {
        .handle = handle
    };
    int ret = drmIoctl(mDrmFd, DRM_IOCTL_GEM_CLOSE, &gem_close);
    if (ret) {
        ALOGE("Failed to close gem handle 0x%x with error %d\n", handle, ret);
    }
}

void FramebufferManager::markInuseLayerLocked(const ExynosLayer *layer,
                                              const bool isM2mSecureLayer) {
    if (!isM2mSecureLayer && mCacheShrinkPending) {
        mCachedLayersInuse.insert(layer);
    }

    if (isM2mSecureLayer && mCacheM2mSecureShrinkPending) {
        mCachedM2mSecureLayersInuse.insert(layer);
    }
}

void FramebufferManager::destroyUnusedLayersLocked() {
    auto destroyUnusedLayers =
            [&](const bool &cacheShrinkPending, std::set<const ExynosLayer *> &cachedLayersInuse,
                std::map<const ExynosLayer *, FBList> &cachedLayerBuffers) -> bool {
        if (!cacheShrinkPending || cachedLayersInuse.size() == cachedLayerBuffers.size()) {
            cachedLayersInuse.clear();
            return false;
        }

        for (auto layer = cachedLayerBuffers.begin(); layer != cachedLayerBuffers.end();) {
            if (cachedLayersInuse.find(layer->first) == cachedLayersInuse.end()) {
                mCleanBuffers.splice(mCleanBuffers.end(), std::move(layer->second));
                layer = cachedLayerBuffers.erase(layer);
            } else {
                ++layer;
            }
        }
        cachedLayersInuse.clear();
        return true;
    };

    auto cachedLayerSize = mCachedLayerBuffers.size();
    if (destroyUnusedLayers(mCacheShrinkPending, mCachedLayersInuse, mCachedLayerBuffers)) {
        ALOGW("FBManager: shrink cached layers from %zu to %zu", cachedLayerSize,
              mCachedLayerBuffers.size());
    }

    cachedLayerSize = mCachedM2mSecureLayerBuffers.size();
    if (destroyUnusedLayers(mCacheM2mSecureShrinkPending, mCachedM2mSecureLayersInuse,
                            mCachedM2mSecureLayerBuffers)) {
        ALOGW("FBManager: shrink cached M2M secure layers from %zu to %zu", cachedLayerSize,
              mCachedM2mSecureLayerBuffers.size());
    }
}

void FramebufferManager::destroySecureFramebufferLocked() {
    if (!mHasSecureFramebuffer) {
        return;
    }

    mHasSecureFramebuffer = false;

    for (auto &layer : mCachedLayerBuffers) {
        if (isFramebuffer(layer.first)) {
            auto &bufferList = layer.second;
            for (auto it = bufferList.begin(); it != bufferList.end(); ++it) {
                auto &buffer = *it;
                if (buffer->bufferDesc.isSecure) {
                    // Assume the latest non-secure buffer in the front
                    // TODO: have a better way to keep in-used buffers
                    mCleanBuffers.splice(mCleanBuffers.end(), bufferList, it, bufferList.end());
                    return;
                }
            }
        }
    }
}

void FramebufferManager::destroyM2mSecureLayerBufferLocked() {
    if (!mHasM2mSecureLayerBuffer) {
        return;
    }

    mHasM2mSecureLayerBuffer = false;

    for (auto &layer : mCachedM2mSecureLayerBuffers) {
        auto &bufferList = layer.second;
        if (bufferList.size()) {
            mCleanBuffers.splice(mCleanBuffers.end(), bufferList, bufferList.begin(),
                                 bufferList.end());
        }
    }
}

void ExynosDisplayDrmInterface::destroyLayer(ExynosLayer *layer) {
    mFBManager.cleanup(layer);
}

int32_t ExynosDisplayDrmInterface::getDisplayIdleTimerSupport(bool &outSupport) {
    if (isFullVrrSupported()) {
        outSupport = false;
        return NO_ERROR;
    } else if (isPseudoVrrSupported()) {
        // Retuen true to avoid SF idle timer working. We insert frames manually
        // for pseudo VRR, so ideally panel idle should be disabled in the driver.
        outSupport = true;
        return NO_ERROR;
    }

    auto [ret, support] = mDrmConnector->panel_idle_support().value();
    if (ret) {
        ALOGI("no panel_idle_support drm property or invalid value (%d)", ret);
        outSupport = false;
    } else {
        outSupport = (support > 0);
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::getDefaultModeId(int32_t *modeId) {
    if (modeId == nullptr) return HWC2_ERROR_BAD_PARAMETER;

    *modeId = mDrmConnector->get_preferred_mode_id();
    return NO_ERROR;
}

ExynosDisplayDrmInterface::ExynosDisplayDrmInterface(ExynosDisplay *exynosDisplay):
    mMonitorDescription{0}
{
    mType = INTERFACE_TYPE_DRM;
    init(exynosDisplay);
}

ExynosDisplayDrmInterface::~ExynosDisplayDrmInterface()
{
    if (mActiveModeState.blob_id)
        mDrmDevice->DestroyPropertyBlob(mActiveModeState.blob_id);
    if (mActiveModeState.old_blob_id)
        mDrmDevice->DestroyPropertyBlob(mActiveModeState.old_blob_id);
    if (mDesiredModeState.blob_id)
        mDrmDevice->DestroyPropertyBlob(mDesiredModeState.blob_id);
    if (mDesiredModeState.old_blob_id)
        mDrmDevice->DestroyPropertyBlob(mDesiredModeState.old_blob_id);
    if (mPartialRegionState.blob_id)
        mDrmDevice->DestroyPropertyBlob(mPartialRegionState.blob_id);
}

void ExynosDisplayDrmInterface::init(ExynosDisplay *exynosDisplay)
{
    mExynosDisplay = exynosDisplay;
    mDisplayTraceName = mExynosDisplay->mDisplayTraceName;
    mDrmDevice = NULL;
    mDrmCrtc = NULL;
    mDrmConnector = NULL;
}

void ExynosDisplayDrmInterface::parseBlendEnums(const DrmProperty &property)
{
    const std::vector<std::pair<uint32_t, const char *>> blendEnums = {
        {HWC2_BLEND_MODE_NONE, "None"},
        {HWC2_BLEND_MODE_PREMULTIPLIED, "Pre-multiplied"},
        {HWC2_BLEND_MODE_COVERAGE, "Coverage"},
    };

    ALOGD("Init blend enums");
    DrmEnumParser::parseEnums(property, blendEnums, mBlendEnums);
    for (auto &e : mBlendEnums) {
        ALOGD("blend [hal: %d, drm: %" PRId64 "]", e.first, e.second);
    }
}

void ExynosDisplayDrmInterface::parseStandardEnums(const DrmProperty &property)
{
    const std::vector<std::pair<uint32_t, const char *>> standardEnums = {
        {HAL_DATASPACE_STANDARD_UNSPECIFIED, "Unspecified"},
        {HAL_DATASPACE_STANDARD_BT709, "BT709"},
        {HAL_DATASPACE_STANDARD_BT601_625, "BT601_625"},
        {HAL_DATASPACE_STANDARD_BT601_625_UNADJUSTED, "BT601_625_UNADJUSTED"},
        {HAL_DATASPACE_STANDARD_BT601_525, "BT601_525"},
        {HAL_DATASPACE_STANDARD_BT601_525_UNADJUSTED, "BT601_525_UNADJUSTED"},
        {HAL_DATASPACE_STANDARD_BT2020, "BT2020"},
        {HAL_DATASPACE_STANDARD_BT2020_CONSTANT_LUMINANCE, "BT2020_CONSTANT_LUMINANCE"},
        {HAL_DATASPACE_STANDARD_BT470M, "BT470M"},
        {HAL_DATASPACE_STANDARD_FILM, "FILM"},
        {HAL_DATASPACE_STANDARD_DCI_P3, "DCI-P3"},
        {HAL_DATASPACE_STANDARD_ADOBE_RGB, "Adobe RGB"},
    };

    ALOGD("Init standard enums");
    DrmEnumParser::parseEnums(property, standardEnums, mStandardEnums);
    for (auto &e : mStandardEnums) {
        ALOGD("standard [hal: %d, drm: %" PRId64 "]",
                e.first >> HAL_DATASPACE_STANDARD_SHIFT, e.second);
    }
}

void ExynosDisplayDrmInterface::parseTransferEnums(const DrmProperty &property)
{
    const std::vector<std::pair<uint32_t, const char *>> transferEnums = {
        {HAL_DATASPACE_TRANSFER_UNSPECIFIED, "Unspecified"},
        {HAL_DATASPACE_TRANSFER_LINEAR, "Linear"},
        {HAL_DATASPACE_TRANSFER_SRGB, "sRGB"},
        {HAL_DATASPACE_TRANSFER_SMPTE_170M, "SMPTE 170M"},
        {HAL_DATASPACE_TRANSFER_GAMMA2_2, "Gamma 2.2"},
        {HAL_DATASPACE_TRANSFER_GAMMA2_6, "Gamma 2.6"},
        {HAL_DATASPACE_TRANSFER_GAMMA2_8, "Gamma 2.8"},
        {HAL_DATASPACE_TRANSFER_ST2084, "ST2084"},
        {HAL_DATASPACE_TRANSFER_HLG, "HLG"},
    };

    ALOGD("Init transfer enums");
    DrmEnumParser::parseEnums(property, transferEnums, mTransferEnums);
    for (auto &e : mTransferEnums) {
        ALOGD("transfer [hal: %d, drm: %" PRId64 "]",
                e.first >> HAL_DATASPACE_TRANSFER_SHIFT, e.second);
    }
}

void ExynosDisplayDrmInterface::parseRangeEnums(const DrmProperty &property)
{
    const std::vector<std::pair<uint32_t, const char *>> rangeEnums = {
        {HAL_DATASPACE_RANGE_UNSPECIFIED, "Unspecified"},
        {HAL_DATASPACE_RANGE_FULL, "Full"},
        {HAL_DATASPACE_RANGE_LIMITED, "Limited"},
        {HAL_DATASPACE_RANGE_EXTENDED, "Extended"},
    };

    ALOGD("Init range enums");
    DrmEnumParser::parseEnums(property, rangeEnums, mRangeEnums);
    for (auto &e : mRangeEnums) {
        ALOGD("range [hal: %d, drm: %" PRId64 "]",
                e.first >> HAL_DATASPACE_RANGE_SHIFT, e.second);
    }
}

void ExynosDisplayDrmInterface::parseColorModeEnums(const DrmProperty &property)
{
    const std::vector<std::pair<uint32_t, const char *>> colorModeEnums = {
        {HAL_COLOR_MODE_NATIVE, "Native"},
        {HAL_COLOR_MODE_DCI_P3, "DCI-P3"},
        {HAL_COLOR_MODE_SRGB, "sRGB"},
    };

    ALOGD("Init color mode enums");
    DrmEnumParser::parseEnums(property, colorModeEnums, mColorModeEnums);
    for (auto &e : mColorModeEnums) {
        ALOGD("Colormode [hal: %d, drm: %" PRId64 "]", e.first, e.second);
    }
}

void ExynosDisplayDrmInterface::parseMipiSyncEnums(const DrmProperty &property) {
    const std::vector<std::pair<uint32_t, const char *>> modeEnums = {
            {toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_REFRESH_RATE), "sync_refresh_rate"},
            {toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_LHBM), "sync_lhbm"},
            {toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_GHBM), "sync_ghbm"},
            {toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_BL), "sync_bl"},
            {toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_OP_RATE), "sync_op_rate"},
    };
    DrmEnumParser::parseEnums(property, modeEnums, mMipiSyncEnums);
    for (auto &e : mMipiSyncEnums) {
        ALOGD("mipi sync [hal 0x%x, drm: %" PRId64 ", %s]", e.first, e.second,
              modeEnums[e.first].second);
    }
}

void ExynosDisplayDrmInterface::updateMountOrientation()
{
    const std::vector<std::pair<HwcMountOrientation, const char*>> orientationEnums = {
        { HwcMountOrientation::ROT_0, "Normal" },
        { HwcMountOrientation::ROT_90, "Left Side Up" },
        { HwcMountOrientation::ROT_180, "Upside Down" },
        { HwcMountOrientation::ROT_270, "Right Side Up" },
    };

    mExynosDisplay->mMountOrientation = HwcMountOrientation::ROT_0;
    const DrmProperty &orientation = mDrmConnector->orientation();
    if (orientation.id() == 0)
        return;

    auto [err, drmOrientation] = orientation.value();
    if (err) {
        ALOGW("%s failed to get drm prop value, err: %d", __func__, err);
        return;
    }

    for (auto &e : orientationEnums) {
        uint64_t enumValue;
        std::tie(enumValue, err) = orientation.getEnumValueWithName(e.second);
        if (!err && enumValue == drmOrientation) {
            mExynosDisplay->mMountOrientation = e.first;
            return;
        }
    }

    ALOGW("%s ignore unrecoganized orientation %" PRId64, __func__, drmOrientation);
}

void ExynosDisplayDrmInterface::parseRCDId(const DrmProperty &property) {
    if (mExynosDisplay->mType != HWC_DISPLAY_PRIMARY) {
        ALOGW("%s invalid display type: %d", __func__, mExynosDisplay->mType);
        return;
    }

    if (property.id() == 0) {
        static_cast<ExynosPrimaryDisplay *>(mExynosDisplay)->mRcdId = -1;
        return;
    }

    auto [err, rcd_id] = property.value();
    if (err < 0) {
        ALOGW("%s failed to get drm prop value", __func__);
        return;
    }

    if (getSpecialChannelId(rcd_id) >= 0)
        static_cast<ExynosPrimaryDisplay *>(mExynosDisplay)->mRcdId = rcd_id;
}

int ExynosDisplayDrmInterface::getDrmDisplayId(uint32_t type, uint32_t index)
{
    for (auto &conn: mDrmDevice->connectors()) {
        if ((((type == HWC_DISPLAY_PRIMARY) && conn->internal()) && (index == conn->display())) ||
             ((type == HWC_DISPLAY_EXTERNAL) && conn->external()))
            return conn->display();
    }

    return -1;
}

int32_t ExynosDisplayDrmInterface::initDrmDevice(DrmDevice *drmDevice)
{
    if (mExynosDisplay == NULL) {
        ALOGE("mExynosDisplay is not set");
        return -EINVAL;
    }
    if ((mDrmDevice = drmDevice) == NULL) {
        ALOGE("drmDevice is NULL");
        return -EINVAL;
    }

    mFBManager.init(mDrmDevice->fd());

    int drmDisplayId = getDrmDisplayId(mExynosDisplay->mType, mExynosDisplay->mIndex);
    if (drmDisplayId < 0) {
        ALOGE("getDrmDisplayId is failed");
        return -EINVAL;
    }

    if (mExynosDisplay->mType != HWC_DISPLAY_EXTERNAL)
        mReadbackInfo.init(mDrmDevice, drmDisplayId);

    if ((mDrmCrtc = mDrmDevice->GetCrtcForDisplay(drmDisplayId)) == NULL) {
        ALOGE("%s:: GetCrtcForDisplay is NULL (id: %d)",
                mExynosDisplay->mDisplayName.c_str(), drmDisplayId);
        return -EINVAL;
    }

    if ((mDrmConnector = mDrmDevice->GetConnectorForDisplay(drmDisplayId)) == NULL) {
        ALOGE("%s:: GetConnectorForDisplay is NULL (id: %d)",
                mExynosDisplay->mDisplayName.c_str(), drmDisplayId);
        return -EINVAL;
    }

    /* Check CRTC and Connector are matched with Display Type */
    if (((mExynosDisplay->mType == HWC_DISPLAY_PRIMARY) && mDrmConnector->external()) ||
         ((mExynosDisplay->mType == HWC_DISPLAY_EXTERNAL) && mDrmConnector->internal())) {
         ALOGE("%s:: Display(id: %u) is not matched with Connector(id: %u)",
                 mExynosDisplay->mDisplayName.c_str(), drmDisplayId, mDrmConnector->id());
         return -EINVAL;
    }

    ALOGD("%s:: display type: %d, index: %d, drmDisplayId: %d, "
            "crtc id: %d, connector id: %d",
            __func__, mExynosDisplay->mType, mExynosDisplay->mIndex,
            drmDisplayId, mDrmCrtc->id(), mDrmConnector->id());

    /* Mapping ExynosMPP resource with DPP Planes */
    uint32_t numWindow = 0;
    for (uint32_t i = 0; i < mDrmDevice->planes().size(); i++) {
        auto &plane = mDrmDevice->planes().at(i);
        uint32_t plane_id = plane->id();

        if (!plane->zpos_property().isImmutable()) {
            /* Plane can be used for composition */
            ExynosMPP *exynosMPP =
                mExynosDisplay->mResourceManager->getOtfMPPWithChannel(i);
            if (exynosMPP == NULL)
                HWC_LOGE(mExynosDisplay, "getOtfMPPWithChannel fail, ch(%d)", plane_id);
            mExynosMPPsForPlane[plane_id] = exynosMPP;
            numWindow++;
        } else {
            /*
             * Plane is special purpose plane which cannot be used for compositon.
             * It's zpos property is immutable.
             */
            mExynosMPPsForPlane[plane_id] = NULL;
        }
    }
    setMaxWindowNum(numWindow);

    if (mExynosDisplay->mMaxWindowNum != getMaxWindowNum()) {
        ALOGE("%s:: Invalid max window number (mMaxWindowNum: %d, getMaxWindowNum(): %d",
                __func__, mExynosDisplay->mMaxWindowNum, getMaxWindowNum());
        return -EINVAL;
    }

    getLowPowerDrmModeModeInfo();

    mDrmVSyncWorker.Init(mDrmDevice, drmDisplayId, mDisplayTraceName);
    mDrmVSyncWorker.RegisterCallback(std::shared_ptr<VsyncCallback>(this));

    if (!mDrmDevice->planes().empty()) {
        auto &plane = mDrmDevice->planes().front();
        parseBlendEnums(plane->blend_property());
        parseStandardEnums(plane->standard_property());
        parseTransferEnums(plane->transfer_property());
        parseRangeEnums(plane->range_property());
    }

    choosePreferredConfig();

    // After choosePreferredConfig, the mDrmConnector->modes array is initialized, get the panel
    // full resolution information here.
    if (mExynosDisplay->mType == HWC_DISPLAY_PRIMARY) {
        retrievePanelFullResolution();
    }

    parseColorModeEnums(mDrmCrtc->color_mode_property());
    parseMipiSyncEnums(mDrmConnector->mipi_sync());
    updateMountOrientation();

    if (mExynosDisplay->mType == HWC_DISPLAY_PRIMARY) parseRCDId(mDrmCrtc->rcd_plane_id_property());

    if (mExynosDisplay->mBrightnessController &&
            mExynosDisplay->mBrightnessController->initDrm(*mDrmDevice, *mDrmConnector)) {
        ALOGW("%s failed to init brightness controller", __func__);
    }

    if (mExynosDisplay->mHistogramController) {
        mExynosDisplay->mHistogramController->initDrm(*mDrmCrtc);
    }

    return NO_ERROR;
}


void ExynosDisplayDrmInterface::Callback(
        int display, int64_t timestamp)
{
    {
        Mutex::Autolock lock(mExynosDisplay->getDisplayMutex());
        bool configApplied = mVsyncCallback.Callback(display, timestamp);

        if (configApplied) {
            if (mVsyncCallback.getDesiredVsyncPeriod()) {
                mExynosDisplay->resetConfigRequestStateLocked(mActiveModeState.mode.id());
                mDrmConnector->set_active_mode(mActiveModeState.mode);
                mVsyncCallback.resetDesiredVsyncPeriod();
            }

            /*
             * Disable vsync if vsync config change is done
             */
            if (!mVsyncCallback.getVSyncEnabled()) {
                mDrmVSyncWorker.VSyncControl(false);
                mVsyncCallback.resetVsyncTimeStamp();
            }
        } else {
            mExynosDisplay->updateConfigRequestAppliedTime();
        }

        if (!mExynosDisplay->mPlugState || !mVsyncCallback.getVSyncEnabled()) {
            return;
        }

        // Refresh rate during enabling LHBM might be different from the one SF expects.
        // HWC just reports the SF expected Vsync to make UI smoothness consistent even if
        // HWC runs at different refresh rate temporarily.
        if (!mExynosDisplay->isConfigSettingEnabled()) {
            int64_t pendingPeriodNs =
                    mExynosDisplay->getVsyncPeriod(mExynosDisplay->mPendingConfig);
            int64_t activePeriodNs = mExynosDisplay->getVsyncPeriod(mExynosDisplay->mActiveConfig);
            if (pendingPeriodNs && mExynosDisplay->mLastVsyncTimestamp) {
                if (activePeriodNs > pendingPeriodNs) {
                    DISPLAY_DRM_LOGW("wrong vsync period: %" PRId64 "us (active), %" PRId64
                                     "us (pending)",
                                     activePeriodNs / 1000, pendingPeriodNs / 1000);
                } else if (activePeriodNs != pendingPeriodNs) {
                    int64_t deltaNs = timestamp - mExynosDisplay->mLastVsyncTimestamp;
                    if (deltaNs < (pendingPeriodNs - ms2ns(2))) {
                        DISPLAY_DRM_LOGI("skip mismatching Vsync callback, delta=%" PRId64 "us",
                                         deltaNs / 1000);
                        return;
                    }
                }
            }
        }
        mExynosDisplay->mLastVsyncTimestamp = timestamp;
    }

    mExynosDisplay->onVsync(timestamp);

    ExynosDevice *exynosDevice = mExynosDisplay->mDevice;

    if (exynosDevice->onVsync_2_4(mExynosDisplay->mDisplayId, timestamp,
                                  mExynosDisplay->mVsyncPeriod)) {
        DISPLAY_ATRACE_INT(vsyncPeriodTag, static_cast<int32_t>(mExynosDisplay->mVsyncPeriod));
        return;
    }

    exynosDevice->onVsync(mExynosDisplay->mDisplayId, timestamp);
}

bool ExynosDisplayDrmInterface::ExynosVsyncCallback::Callback(
        int display, int64_t timestamp)
{
    /*
     * keep vsync period if mVsyncTimeStamp
     * is not initialized since vsync is enabled
     */
    if (mVsyncTimeStamp > 0) {
        mVsyncPeriod = timestamp - mVsyncTimeStamp;
    }

    mVsyncTimeStamp = timestamp;

    /* There was no config chage request */
    if (!mDesiredVsyncPeriod)
        return true;

    /*
     * mDesiredVsyncPeriod is nanoseconds
     * Compare with 20% margin
     */
    if (abs(static_cast<int32_t>(mDesiredVsyncPeriod - mVsyncPeriod)) < (mDesiredVsyncPeriod / 5))
        return true;

    return false;
}

int32_t ExynosDisplayDrmInterface::getLowPowerDrmModeModeInfo() {
    auto mode = mDrmConnector->lp_mode();

    if (!mode.clock()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    mDozeDrmMode = mode;

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::setLowPowerMode() {
    if (!isDozeModeAvailable()) {
        return HWC2_ERROR_UNSUPPORTED;
    }

    uint32_t mm_width = mDrmConnector->mm_width();
    uint32_t mm_height = mDrmConnector->mm_height();

    mExynosDisplay->mXres = mDozeDrmMode.h_display();
    mExynosDisplay->mYres = mDozeDrmMode.v_display();
    // in nanoseconds
    mExynosDisplay->mVsyncPeriod = static_cast<uint32_t>(mDozeDrmMode.te_period());
    // Dots per 1000 inches
    mExynosDisplay->mXdpi = mm_width ? (mDozeDrmMode.h_display() * kUmPerInch) / mm_width : -1;
    // Dots per 1000 inches
    mExynosDisplay->mYdpi = mm_height ? (mDozeDrmMode.v_display() * kUmPerInch) / mm_height : -1;

    mExynosDisplay->mRefreshRate = static_cast<int32_t>(mDozeDrmMode.v_refresh());

    return setActiveDrmMode(mDozeDrmMode);
}

int32_t ExynosDisplayDrmInterface::setPowerMode(int32_t mode)
{
    int ret = 0;
    uint64_t dpms_value = 0;
    if (mode == HWC_POWER_MODE_OFF) {
        dpms_value = DRM_MODE_DPMS_OFF;
    } else {
        dpms_value = DRM_MODE_DPMS_ON;
    }

    const DrmProperty &prop = mDrmConnector->dpms_property();
    if ((ret = drmModeConnectorSetProperty(mDrmDevice->fd(), mDrmConnector->id(), prop.id(),
            dpms_value)) != NO_ERROR) {
        HWC_LOGE(mExynosDisplay, "setPower mode ret (%d)", ret);
    }

    return ret;
}

int32_t ExynosDisplayDrmInterface::setVsyncEnabled(uint32_t enabled)
{
    if (enabled == HWC2_VSYNC_ENABLE) {
        mDrmVSyncWorker.VSyncControl(true);
    } else {
        if (mVsyncCallback.getDesiredVsyncPeriod() == 0)
            mDrmVSyncWorker.VSyncControl(false);
    }

    mVsyncCallback.enableVSync(HWC2_VSYNC_ENABLE == enabled);

    ExynosDevice *exynosDevice = mExynosDisplay->mDevice;
    if (exynosDevice->isCallbackAvailable(HWC2_CALLBACK_VSYNC_2_4)) {
        DISPLAY_ATRACE_INT(vsyncPeriodTag, 0);
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::choosePreferredConfig() {
    uint32_t num_configs = 0;
    int32_t err = getDisplayConfigs(&num_configs, NULL);
    if (err != HWC2_ERROR_NONE || !num_configs)
        return err;

    int32_t id = -1, fps = 0, vsyncRate = 0, width = 0, height = 0;
    err = HWC2_ERROR_BAD_CONFIG;
    if ((mExynosDisplay->mType == HWC_DISPLAY_PRIMARY) && (mExynosDisplay->mIndex == 0)) {
        char modeStr[PROPERTY_VALUE_MAX];
        // kernel preferred mode should be aligned to bootloader setting, use this property
        // to specify default user space preferred mode to override kernel's setting.
        if (property_get("vendor.display.preferred_mode", modeStr, "") > 0 &&
            sscanf(modeStr, "%dx%d@%d", &width, &height, &fps) == 3) {
            err = mExynosDisplay->lookupDisplayConfigs(width, height, fps, fps, &id);
        } else if (property_get("ro.vendor.primarydisplay.preferred_mode", modeStr, "") > 0 &&
                   sscanf(modeStr, "%dx%d@%d:%d", &width, &height, &fps, &vsyncRate) == 4) {
            err = mExynosDisplay->lookupDisplayConfigs(width, height, fps, vsyncRate, &id);
        }
    }

    const int32_t drmPreferredId = mDrmConnector->get_preferred_mode_id();
    if (err != HWC2_ERROR_NONE) {
        id = drmPreferredId;
    }

    auto& configs = mExynosDisplay->mDisplayConfigs;
    auto& config = configs[id];
    width = config.width;
    height = config.height;
    fps = config.refreshRate;
    vsyncRate = nanoSec2Hz(config.vsyncPeriod);
    ALOGI("Preferred mode: configs[%d]=%dx%d@%d:%d, state: %d", id, width, height, fps, vsyncRate,
          mDrmConnector->state());
    if (id != drmPreferredId &&
        (width != configs[drmPreferredId].width || height != configs[drmPreferredId].height)) {
        // HWC cannot send a resolution change commit here until 1st frame update because of
        // some panels requirement. Therefore, it calls setActiveConfigWithConstraints() help
        // set mDesiredModeState correctly, and then trigger modeset in the 1s frame update.
        if ((err = setActiveConfigWithConstraints(id)) < 0) {
            ALOGE("failed to setActiveConfigWithConstraints(), err %d", err);
            return err;
        }
    } else {
        if ((err = setActiveConfig(id)) < 0) {
            ALOGE("failed to set default config, err %d", err);
            return err;
        }
    }

    return mExynosDisplay->updateInternalDisplayConfigVariables(id);
}

int32_t ExynosDisplayDrmInterface::getDisplayConfigs(
        uint32_t* outNumConfigs,
        hwc2_config_t* outConfigs)
{
    if (!mExynosDisplay || !(mExynosDisplay->mDevice)) {
        return HWC2_ERROR_BAD_DISPLAY;
    }

    std::lock_guard<std::recursive_mutex> lock(mDrmConnector->modesLock());

    if (!outConfigs) {
        bool useVrrConfigs = isFullVrrSupported();
        int ret = mDrmConnector->UpdateModes(useVrrConfigs);
        if (ret < 0) {
            ALOGE("%s: failed to update display modes (%d)",
                  mExynosDisplay->mDisplayName.c_str(), ret);
            *outNumConfigs = 0;
            return HWC2_ERROR_BAD_DISPLAY;
        }
        if (ret == 0) {
            // no need to update mExynosDisplay->mDisplayConfigs
            goto no_mode_changes;
        }
        ALOGI("Select Vrr Config for display %s: %s", mExynosDisplay->mDisplayName.c_str(),
              useVrrConfigs ? "full" : (isPseudoVrrSupported() ? "pseudo" : "non-Vrr"));

        if (mDrmConnector->state() == DRM_MODE_CONNECTED) {
            /*
             * EDID property for External Display is created during initialization,
             * but it is not complete. It will be completed after Hot Plug Detection
             * & DRM Mode update.
             */
            if (mExynosDisplay->mType == HWC_DISPLAY_EXTERNAL)
                mDrmConnector->UpdateEdidProperty();

            if (mDrmConnector->modes().size() == 0) {
                ALOGE("%s: DRM_MODE_CONNECTED, but no modes available",
                      mExynosDisplay->mDisplayName.c_str());
                mExynosDisplay->mDisplayConfigs.clear();
                mExynosDisplay->mPlugState = false;
                *outNumConfigs = 0;
                return HWC2_ERROR_BAD_DISPLAY;
            }

            mExynosDisplay->mPlugState = true;
        } else
            mExynosDisplay->mPlugState = false;

        dumpDisplayConfigs();

        mExynosDisplay->mDisplayConfigs.clear();

        uint32_t mm_width = mDrmConnector->mm_width();
        uint32_t mm_height = mDrmConnector->mm_height();
        ALOGD("%s: mm_width(%u) mm_height(%u)",
              mExynosDisplay->mDisplayName.c_str(), mm_width, mm_height);

        DisplayConfigGroupIdGenerator groupIdGenerator;
        float peakRr = -1;
        for (const DrmMode &mode : mDrmConnector->modes()) {
            displayConfigs_t configs;
            float rr = mode.v_refresh();
            configs.refreshRate = static_cast<int32_t>(rr);
            configs.vsyncPeriod = static_cast<int32_t>(mode.te_period());
            if (configs.vsyncPeriod <= 0.0f) {
                ALOGE("%s:: invalid vsync period", __func__);
                return HWC2_ERROR_BAD_DISPLAY;
            }
            configs.isOperationRateToBts = mode.is_operation_rate_to_bts();
            configs.width = mode.h_display();
            configs.height = mode.v_display();
            // Dots per 1000 inches
            configs.Xdpi = mm_width ? (mode.h_display() * kUmPerInch) / mm_width : -1;
            // Dots per 1000 inches
            configs.Ydpi = mm_height ? (mode.v_display() * kUmPerInch) / mm_height : -1;
            // find peak rr
            if (rr > peakRr) {
                peakRr = rr;
            }
            // Configure VRR if it's turned on.
            if (mIsVrrModeSupported) {
                VrrConfig_t vrrConfig;
                vrrConfig.minFrameIntervalNs = static_cast<int>(std::nano::den / rr);
                vrrConfig.isNsMode = mode.is_ns_mode();
                vrrConfig.vsyncPeriodNs = configs.vsyncPeriod;
                configs.vrrConfig = std::make_optional(vrrConfig);
                if (mode.is_vrr_mode()) {
                    if (!isFullVrrSupported()) {
                        return HWC2_ERROR_BAD_DISPLAY;
                    }
                    configs.vrrConfig->isFullySupported = true;
                    // TODO(b/290843234): FrameIntervalPowerHint is currently optional and omitted.
                    // Supply initial values for notifyExpectedPresentConfig; potential changes may
                    // come later.
                    NotifyExpectedPresentConfig_t notifyExpectedPresentConfig =
                            {.HeadsUpNs = mNotifyExpectedPresentHeadsUpNs,
                             .TimeoutNs = mNotifyExpectedPresentTimeoutNs};
                    configs.vrrConfig->notifyExpectedPresentConfig =
                            std::make_optional(notifyExpectedPresentConfig);
                    configs.groupId =
                            groupIdGenerator.getGroupId(configs.width, configs.height,
                                                        configs.vrrConfig->minFrameIntervalNs,
                                                        configs.vsyncPeriod);
                }
            }
            if (!mode.is_vrr_mode()) {
                configs.groupId = groupIdGenerator.getGroupId(configs.width, configs.height);
            }
            mExynosDisplay->mDisplayConfigs.insert(std::make_pair(mode.id(), configs));
            ALOGD("%s: config group(%d), id(%d), w(%d), h(%d), rr(%f), vsync(%d), "
                  "xdpi(%d), ydpi(%d), vrr(%s), ns(%s)",
                  mExynosDisplay->mDisplayName.c_str(), configs.groupId, mode.id(),
                  configs.width, configs.height, rr, configs.vsyncPeriod,
                  configs.Xdpi, configs.Ydpi, mode.is_vrr_mode() ? "true" : "false",
                  mode.is_ns_mode() ? "true" : "false");
        }
        mExynosDisplay->setPeakRefreshRate(peakRr);
    }

no_mode_changes:
    uint32_t num_modes = static_cast<uint32_t>(mDrmConnector->modes().size());
    if (!outConfigs) {
        *outNumConfigs = num_modes;
        return (*outNumConfigs > 0) ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_DISPLAY;
    }

    uint32_t idx = 0;

    for (const DrmMode &mode : mDrmConnector->modes()) {
        if (idx >= *outNumConfigs)
            break;
        outConfigs[idx++] = mode.id();
    }
    *outNumConfigs = idx;

    return (*outNumConfigs > 0) ? HWC2_ERROR_NONE : HWC2_ERROR_BAD_DISPLAY;
}

void ExynosDisplayDrmInterface::dumpDisplayConfigs()
{
    std::lock_guard<std::recursive_mutex> lock(mDrmConnector->modesLock());

    uint32_t num_modes = static_cast<uint32_t>(mDrmConnector->modes().size());
    for (uint32_t i = 0; i < num_modes; i++) {
        auto mode = mDrmConnector->modes().at(i);
        ALOGD("%s: config[%d] %s: id(%d), clock(%d), flags(0x%x), type(0x%x)",
              mExynosDisplay->mDisplayName.c_str(), i, mode.name().c_str(), mode.id(),
              mode.clock(), mode.flags(), mode.type());
        ALOGD("\th_display(%d), h_sync_start(%d), h_sync_end(%d), h_total(%d), h_skew(%d)",
              mode.h_display(), mode.h_sync_start(), mode.h_sync_end(), mode.h_total(),
              mode.h_skew());
        ALOGD("\tv_display(%d), v_sync_start(%d), v_sync_end(%d), v_total(%d), v_scan(%d), "
              "v_refresh(%f)",
              mode.v_display(), mode.v_sync_start(), mode.v_sync_end(), mode.v_total(),
              mode.v_scan(), mode.v_refresh());
    }
}

int32_t ExynosDisplayDrmInterface::getDisplayVsyncPeriod(hwc2_vsync_period_t* outVsyncPeriod)
{
    return HWC2_ERROR_UNSUPPORTED;
}

int32_t ExynosDisplayDrmInterface::getConfigChangeDuration()
{
    const auto [ret, duration] = mDrmConnector->rr_switch_duration().value();

    if (!ret && duration > 0) {
        return duration;
    }

    return 2;
};

bool ExynosDisplayDrmInterface::needRefreshOnLP() {
    const auto [ret, refresh_on_lp] = mDrmConnector->refresh_on_lp().value();

    if (!ret) {
        return refresh_on_lp;
    }

    return false;
};

int32_t ExynosDisplayDrmInterface::getVsyncAppliedTime(
        hwc2_config_t config, int64_t* actualChangeTime)
{
    if (mDrmCrtc->adjusted_vblank_property().id() == 0) {
        uint64_t currentTime = systemTime(SYSTEM_TIME_MONOTONIC);
        *actualChangeTime = currentTime +
            (mExynosDisplay->mVsyncPeriod) * getConfigChangeDuration();
        return HWC2_ERROR_NONE;
    }

    int ret = 0;
    if ((ret = mDrmDevice->UpdateCrtcProperty(*mDrmCrtc,
            &mDrmCrtc->adjusted_vblank_property())) != 0) {
        HWC_LOGE(mExynosDisplay, "Failed to update vblank property");
        return ret;
    }

    uint64_t timestamp;
    std::tie(ret, timestamp) = mDrmCrtc->adjusted_vblank_property().value();
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "Failed to get vblank property");
        return ret;
    }

    *actualChangeTime = static_cast<int64_t>(timestamp);
    return HWC2_ERROR_NONE;
}

bool ExynosDisplayDrmInterface::supportDataspace(int32_t dataspace)
{
    bool supportStandard = false;
    bool supportTransfer = false;
    bool supportRange = false;

    /* Check supported standard */
    for (auto &e : mStandardEnums) {
        if (e.first & dataspace)
            supportStandard = true;
    }

    /* Check supported transfer */
    for (auto &e : mTransferEnums) {
        if (e.first & dataspace)
            supportTransfer = true;
    }

    /* Check supported range */
    for (auto &e : mRangeEnums) {
        if (e.first & dataspace)
            supportRange = true;
    }

    return supportStandard && supportTransfer && supportRange;
}

int32_t ExynosDisplayDrmInterface::getColorModes(uint32_t *outNumModes, int32_t *outModes)
{
    if (mDrmCrtc->color_mode_property().id() == 0) {
        *outNumModes = 1;

        if (outModes != NULL) {
            outModes[0] = HAL_COLOR_MODE_NATIVE;
        }
        return HWC2_ERROR_NONE;
    }

    uint32_t colorNum = 0;
    for (auto &e : mColorModeEnums) {
        if (outModes != NULL) {
            outModes[colorNum] = e.first;
        }
        colorNum++;
        ALOGD("Colormode [hal: %d, drm: %" PRId64 "]", e.first, e.second);
    }
    *outNumModes = colorNum;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplayDrmInterface::setColorMode(int32_t mode)
{
    int ret = 0;

    if (mDrmCrtc->color_mode_property().id() == 0) {
        return HWC2_ERROR_NONE;
    }

    DrmModeAtomicReq drmReq(this);

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
                                        mDrmCrtc->color_mode_property(), mode)) < 0)
        return ret;

    if ((ret = drmReq.commit(0, true)) < 0)
        return ret;

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplayDrmInterface::setActiveConfigWithConstraints(
        hwc2_config_t config, bool test)
{
    std::lock_guard<std::recursive_mutex> lock(mDrmConnector->modesLock());

    ALOGD("%s:: %s config(%d) test(%d)", __func__, mExynosDisplay->mDisplayName.c_str(), config,
          test);

    auto mode = std::find_if(mDrmConnector->modes().begin(), mDrmConnector->modes().end(),
            [config](DrmMode const &m) { return m.id() == config;});
    if (mode == mDrmConnector->modes().end()) {
        HWC_LOGE(mExynosDisplay, "Could not find active mode for %d", config);
        return HWC2_ERROR_BAD_CONFIG;
    }

    if (mDesiredModeState.needsModeSet()) {
        ALOGI("Previous mode change %d request is not applied", mDesiredModeState.mode.id());
    } else if ((mActiveModeState.blob_id != 0) && (mActiveModeState.mode.id() == config)) {
        ALOGD("%s:: same mode %d", __func__, config);
        /* trigger resetConfigRequestStateLocked() */
        mVsyncCallback.setDesiredVsyncPeriod(mActiveModeState.mode.te_period());
        mDrmVSyncWorker.VSyncControl(true);
        return HWC2_ERROR_NONE;
    }

    int32_t ret = HWC2_ERROR_NONE;
    DrmModeAtomicReq drmReq(this);
    uint32_t modeBlob = 0;
    if (mDesiredModeState.mode.id() != config) {
        if ((ret = createModeBlob(*mode, modeBlob)) != NO_ERROR) {
            HWC_LOGE(mExynosDisplay, "%s: Fail to set mode state",
                    __func__);
            return HWC2_ERROR_BAD_CONFIG;
        }
    }
    const auto isResSwitch =
            (mActiveModeState.blob_id != 0) && mActiveModeState.isFullModeSwitch(*mode);

    if (!test) {
        if (modeBlob) { /* only replace desired mode if it has changed */
            if (mDesiredModeState.isFullModeSwitch(*mode)) {
                mIsResolutionSwitchInProgress = true;
                mExynosDisplay->mDevice->setVBlankOffDelay(0);
            }
            mDesiredModeState.setMode(*mode, modeBlob, drmReq);
            if (mExynosDisplay->mOperationRateManager) {
                mExynosDisplay->mOperationRateManager->onConfig(config);
                mExynosDisplay->handleTargetOperationRate();
            }
            DISPLAY_DRM_LOGI("%s: config(%d)", __func__, config);
        } else {
            ALOGD("%s:: same desired mode %d", __func__, config);
        }
    } else {
        if (!isResSwitch) {
            ret = setDisplayMode(drmReq, modeBlob ? modeBlob : mDesiredModeState.blob_id,
                                 modeBlob ? mode->id() : mDesiredModeState.mode.id());
            if (ret < 0) {
                HWC_LOGE(mExynosDisplay, "%s: Fail to apply display mode", __func__);
                return ret;
            }
            ret = drmReq.commit(DRM_MODE_ATOMIC_TEST_ONLY, true);
            if (ret) {
                drmReq.addOldBlob(modeBlob);
                HWC_LOGE(mExynosDisplay,
                         "%s:: Failed to commit pset ret=%d in applyDisplayMode()\n", __func__,
                         ret);
                return ret;
            }
        }

        if (modeBlob) {
            mDrmDevice->DestroyPropertyBlob(modeBlob);
        }
    }
    return HWC2_ERROR_NONE;
}
int32_t ExynosDisplayDrmInterface::setActiveDrmMode(DrmMode const &mode) {
    /* Don't skip when power was off */
    if (!(mExynosDisplay->mSkipFrame) && (mActiveModeState.blob_id != 0) &&
        (mActiveModeState.mode.id() == mode.id()) && !mActiveModeState.needsModeSet()) {
        ALOGD("%s:: same mode %d", __func__, mode.id());
        return HWC2_ERROR_NONE;
    }

    int32_t ret = HWC2_ERROR_NONE;
    uint32_t modeBlob;
    if ((ret = createModeBlob(mode, modeBlob)) != NO_ERROR) {
        HWC_LOGE(mExynosDisplay, "%s: Fail to set mode state",
                __func__);
        return HWC2_ERROR_BAD_CONFIG;
    }

    DrmModeAtomicReq drmReq(this);

    uint32_t flags = DRM_MODE_ATOMIC_ALLOW_MODESET;
    bool reconfig = false;

    if (mActiveModeState.isFullModeSwitch(mode)) {
        reconfig = true;
    }

    if ((ret = setDisplayMode(drmReq, modeBlob, mode.id())) != NO_ERROR) {
        drmReq.addOldBlob(modeBlob);
        HWC_LOGE(mExynosDisplay, "%s: Fail to apply display mode",
                __func__);
        return ret;
    }

    if ((ret = drmReq.commit(flags, true))) {
        drmReq.addOldBlob(modeBlob);
        HWC_LOGE(mExynosDisplay, "%s:: Failed to commit pset ret=%d in applyDisplayMode()\n",
                __func__, ret);
        return ret;
    }

    mDrmConnector->set_active_mode(mode);
    mActiveModeState.setMode(mode, modeBlob, drmReq);
    mActiveModeState.clearPendingModeState();

    if (reconfig) {
        mDrmConnector->ResetLpMode();
        getLowPowerDrmModeModeInfo();
    }

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplayDrmInterface::setActiveConfig(hwc2_config_t config) {
    std::lock_guard<std::recursive_mutex> lock(mDrmConnector->modesLock());

    auto mode = std::find_if(mDrmConnector->modes().begin(), mDrmConnector->modes().end(),
                             [config](DrmMode const &m) { return m.id() == config; });
    if (mode == mDrmConnector->modes().end()) {
        HWC_LOGE(mExynosDisplay, "Could not find active mode for %d", config);
        return HWC2_ERROR_BAD_CONFIG;
    }

    if (mExynosDisplay->mOperationRateManager) {
        mExynosDisplay->mOperationRateManager->onConfig(config);
        mExynosDisplay->handleTargetOperationRate();
    }

    mExynosDisplay->updateAppliedActiveConfig(config, systemTime(SYSTEM_TIME_MONOTONIC));
    if (!setActiveDrmMode(*mode)) {
        DISPLAY_DRM_LOGI("%s: config(%d)", __func__, config);
    } else {
        DISPLAY_DRM_LOGE("%s: config(%d) failed", __func__, config);
    }

    return 0;
}

int32_t ExynosDisplayDrmInterface::createModeBlob(const DrmMode &mode,
        uint32_t &modeBlob)
{
    struct drm_mode_modeinfo drm_mode;
    memset(&drm_mode, 0, sizeof(drm_mode));
    mode.ToDrmModeModeInfo(&drm_mode);

    modeBlob = 0;
    int ret = mDrmDevice->CreatePropertyBlob(&drm_mode, sizeof(drm_mode),
            &modeBlob);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create mode property blob %d", ret);
        return ret;
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::setDisplayMode(DrmModeAtomicReq& drmReq,
                                                  const uint32_t& modeBlob,
                                                  const uint32_t& modeId) {
    int ret = NO_ERROR;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
           mDrmCrtc->active_property(), 1)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
            mDrmCrtc->mode_property(), modeBlob)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmConnector->id(),
            mDrmConnector->crtc_id_property(), mDrmCrtc->id())) < 0)
        return ret;

    if (mConfigChangeCallback) {
        drmReq.setAckCallback(std::bind(mConfigChangeCallback, modeId));
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::setCursorPositionAsync(uint32_t x_pos, uint32_t y_pos)
{
    return 0;
}

int32_t ExynosDisplayDrmInterface::updateHdrCapabilities()
{
    /* Init member variables */
    mExynosDisplay->mHdrTypes.clear();
    mExynosDisplay->mMaxLuminance = 0;
    mExynosDisplay->mMaxAverageLuminance = 0;
    mExynosDisplay->mMinLuminance = 0;

    if (mExynosDisplay->mType == HWC_DISPLAY_EXTERNAL) {
        int upd_res = mDrmConnector->UpdateLuminanceAndHdrProperties();
        if (!upd_res) ALOGW("%s: UpdateLuminanceAndHdrProperties failed (%d)", __func__, upd_res);
    }
    const DrmProperty &prop_max_luminance = mDrmConnector->max_luminance();
    const DrmProperty &prop_max_avg_luminance = mDrmConnector->max_avg_luminance();
    const DrmProperty &prop_min_luminance = mDrmConnector->min_luminance();
    const DrmProperty &prop_hdr_formats = mDrmConnector->hdr_formats();

    int ret = 0;
    uint64_t max_luminance = 0;
    uint64_t max_avg_luminance = 0;
    uint64_t min_luminance = 0;
    uint64_t hdr_formats = 0;

    if ((prop_max_luminance.id() == 0) ||
        (prop_max_avg_luminance.id() == 0) ||
        (prop_min_luminance.id() == 0) ||
        (prop_hdr_formats.id() == 0)) {
        HWC_LOGE(mExynosDisplay,
                 "%s:: there is no property for hdrCapabilities (max_luminance: %d, "
                 "max_avg_luminance: %d, min_luminance: %d, hdr_formats: %d",
                 __func__, prop_max_luminance.id(), prop_max_avg_luminance.id(),
                 prop_min_luminance.id(), prop_hdr_formats.id());
        return -1;
    }

    std::tie(ret, max_luminance) = prop_max_luminance.value();
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "%s:: there is no max_luminance (ret = %d)",
                __func__, ret);
        return -1;
    }
    mExynosDisplay->mMaxLuminance = (float)max_luminance / DISPLAY_LUMINANCE_UNIT;

    std::tie(ret, max_avg_luminance) = prop_max_avg_luminance.value();
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "%s:: there is no max_avg_luminance (ret = %d)",
                __func__, ret);
        return -1;
    }
    mExynosDisplay->mMaxAverageLuminance = (float)max_avg_luminance / DISPLAY_LUMINANCE_UNIT;

    std::tie(ret, min_luminance) = prop_min_luminance.value();
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "%s:: there is no min_luminance (ret = %d)",
                __func__, ret);
        return -1;
    }
    mExynosDisplay->mMinLuminance = (float)min_luminance / DISPLAY_LUMINANCE_UNIT;

    std::tie(ret, hdr_formats) = prop_hdr_formats.value();
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "%s:: there is no hdr_formats (ret = %d)",
                __func__, ret);
        return -1;
    }

    uint32_t typeBit;
    std::tie(typeBit, ret) = prop_hdr_formats.getEnumValueWithName("Dolby Vision");
    if ((ret == 0) && (hdr_formats & (1 << typeBit))) {
        mExynosDisplay->mHdrTypes.push_back(HAL_HDR_DOLBY_VISION);
        HDEBUGLOGD(eDebugHWC, "%s: supported hdr types : %d",
                mExynosDisplay->mDisplayName.c_str(), HAL_HDR_DOLBY_VISION);
    }
    std::tie(typeBit, ret) = prop_hdr_formats.getEnumValueWithName("HDR10");
    if ((ret == 0) && (hdr_formats & (1 << typeBit))) {
        mExynosDisplay->mHdrTypes.push_back(HAL_HDR_HDR10);
        if (mExynosDisplay->mDevice->mResourceManager->hasHDR10PlusMPP()) {
            mExynosDisplay->mHdrTypes.push_back(HAL_HDR_HDR10_PLUS);
        }
        HDEBUGLOGD(eDebugHWC, "%s: supported hdr types : %d",
                mExynosDisplay->mDisplayName.c_str(), HAL_HDR_HDR10);
    }
    std::tie(typeBit, ret) = prop_hdr_formats.getEnumValueWithName("HLG");
    if ((ret == 0) && (hdr_formats & (1 << typeBit))) {
        mExynosDisplay->mHdrTypes.push_back(HAL_HDR_HLG);
        HDEBUGLOGD(eDebugHWC, "%s: supported hdr types : %d",
                mExynosDisplay->mDisplayName.c_str(), HAL_HDR_HLG);
    }

    ALOGI("%s: get hdrCapabilities info max_luminance(%" PRId64 "), "
            "max_avg_luminance(%" PRId64 "), min_luminance(%" PRId64 "), "
            "hdr_formats(0x%" PRIx64 ")",
            mExynosDisplay->mDisplayName.c_str(),
            max_luminance, max_avg_luminance, min_luminance, hdr_formats);

    ALOGI("%s: mHdrTypes size(%zu), maxLuminance(%f), maxAverageLuminance(%f), minLuminance(%f)",
            mExynosDisplay->mDisplayName.c_str(), mExynosDisplay->mHdrTypes.size(), mExynosDisplay->mMaxLuminance,
            mExynosDisplay->mMaxAverageLuminance, mExynosDisplay->mMinLuminance);

    return 0;
}

int ExynosDisplayDrmInterface::getDeconChannel(ExynosMPP *otfMPP)
{
    int32_t channelNum = sizeof(idma_channel_map)/sizeof(dpp_channel_map_t);
    for (int i = 0; i < channelNum; i++) {
        if((idma_channel_map[i].type == otfMPP->mPhysicalType) &&
           (idma_channel_map[i].index == otfMPP->mPhysicalIndex))
            return idma_channel_map[i].channel;
    }
    return -EINVAL;
}

int32_t ExynosDisplayDrmInterface::setupCommitFromDisplayConfig(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq,
        const exynos_win_config_data &config,
        const uint32_t configIndex,
        const std::unique_ptr<DrmPlane> &plane,
        uint32_t &fbId)
{
    ATRACE_CALL();
    int ret = NO_ERROR;

    if (fbId == 0) {
        if ((ret = mFBManager.getBuffer(config, fbId)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s:: Failed to get FB, fbId(%d), ret(%d)", __func__, fbId,
                     ret);
            return ret;
        }
    }

    if ((ret = drmReq.atomicAddProperty(plane->id(),
                plane->crtc_property(), mDrmCrtc->id())) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->fb_property(), fbId)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->crtc_x_property(), config.dst.x)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->crtc_y_property(), config.dst.y)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->crtc_w_property(), config.dst.w)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->crtc_h_property(), config.dst.h)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->src_x_property(), (int)(config.src.x) << 16)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->src_y_property(), (int)(config.src.y) << 16)) < 0)
        HWC_LOGE(mExynosDisplay, "%s:: Failed to add src_y property to plane",
                __func__);
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->src_w_property(), (int)(config.src.w) << 16)) < 0)
        return ret;
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->src_h_property(), (int)(config.src.h) << 16)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(plane->id(),
            plane->rotation_property(),
            halTransformToDrmRot(config.transform), true)) < 0)
        return ret;

    uint64_t drmEnum = 0;
    std::tie(drmEnum, ret) = DrmEnumParser::halToDrmEnum(config.blending, mBlendEnums);
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "Fail to convert blend(%d)", config.blending);
        return ret;
    }
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->blend_property(), drmEnum, true)) < 0)
        return ret;

    if (plane->zpos_property().id() && !plane->zpos_property().isImmutable()) {
        uint64_t min_zpos = 0;

        // Ignore ret and use min_zpos as 0 by default
        std::tie(std::ignore, min_zpos) = plane->zpos_property().rangeMin();

        if ((ret = drmReq.atomicAddProperty(plane->id(),
                plane->zpos_property(), configIndex + min_zpos)) < 0)
            return ret;
    }

    if (plane->alpha_property().id()) {
        uint64_t min_alpha = 0;
        uint64_t max_alpha = 0;
        std::tie(std::ignore, min_alpha) = plane->alpha_property().rangeMin();
        std::tie(std::ignore, max_alpha) = plane->alpha_property().rangeMax();
        if ((ret = drmReq.atomicAddProperty(plane->id(),
                plane->alpha_property(),
                (uint64_t)(((max_alpha - min_alpha) * config.plane_alpha) + 0.5) + min_alpha, true)) < 0)
            return ret;
    }

    if (config.acq_fence >= 0) {
        if ((ret = drmReq.atomicAddProperty(plane->id(),
                        plane->in_fence_fd_property(), config.acq_fence)) < 0)
            return ret;
    }

    if (config.state == config.WIN_STATE_COLOR)
    {
        if (plane->colormap_property().id()) {
            if ((ret = drmReq.atomicAddProperty(plane->id(),
                            plane->colormap_property(), config.color)) < 0)
                return ret;
        } else {
            HWC_LOGE(mExynosDisplay, "colormap property is not supported");
        }
    }

    std::tie(drmEnum, ret) = DrmEnumParser::halToDrmEnum(
                    config.dataspace & HAL_DATASPACE_STANDARD_MASK, mStandardEnums);
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "Fail to convert standard(%d)",
                config.dataspace & HAL_DATASPACE_STANDARD_MASK);
        return ret;
    }
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->standard_property(),
                    drmEnum, true)) < 0)
        return ret;

    std::tie(drmEnum, ret) = DrmEnumParser::halToDrmEnum(
                    config.dataspace & HAL_DATASPACE_TRANSFER_MASK, mTransferEnums);
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "Fail to convert transfer(%d)",
                config.dataspace & HAL_DATASPACE_TRANSFER_MASK);
        return ret;
    }
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->transfer_property(), drmEnum, true)) < 0)
        return ret;

    std::tie(drmEnum, ret) = DrmEnumParser::halToDrmEnum(
                     config.dataspace & HAL_DATASPACE_RANGE_MASK, mRangeEnums);
    if (ret < 0) {
        HWC_LOGE(mExynosDisplay, "Fail to convert range(%d)",
                config.dataspace & HAL_DATASPACE_RANGE_MASK);
        return ret;
    }
    if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->range_property(), drmEnum, true)) < 0)
        return ret;

    if (hasHdrInfo(config.dataspace)) {
        if ((ret = drmReq.atomicAddProperty(plane->id(),
                plane->min_luminance_property(), config.min_luminance)) < 0)
            return ret;
        if ((ret = drmReq.atomicAddProperty(plane->id(),
                       plane->max_luminance_property(), config.max_luminance)) < 0)
            return ret;
    }

    if (config.state == config.WIN_STATE_RCD) {
        if (plane->block_property().id()) {
            if (mBlockState != config.block_area) {
                uint32_t blobId = 0;
                ret = mDrmDevice->CreatePropertyBlob(&config.block_area, sizeof(config.block_area),
                                                     &blobId);
                if (ret || (blobId == 0)) {
                    HWC_LOGE(mExynosDisplay, "Failed to create blocking region blob id=%d, ret=%d",
                             blobId, ret);
                    return ret;
                }

                mBlockState.mRegion = config.block_area;
                if (mBlockState.mBlobId) {
                    drmReq.addOldBlob(mBlockState.mBlobId);
                }
                mBlockState.mBlobId = blobId;
            }

            if ((ret = drmReq.atomicAddProperty(plane->id(), plane->block_property(),
                                                mBlockState.mBlobId)) < 0) {
                HWC_LOGE(mExynosDisplay, "Failed to set blocking region property %d", ret);
                return ret;
            }
        }
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::setupPartialRegion(DrmModeAtomicReq &drmReq)
{
    if (!mDrmCrtc->partial_region_property().id())
        return NO_ERROR;

    int ret = NO_ERROR;

    struct decon_frame &update_region = mExynosDisplay->mDpuData.win_update_region;
    struct drm_clip_rect partial_rect = {
        static_cast<unsigned short>(update_region.x),
        static_cast<unsigned short>(update_region.y),
        static_cast<unsigned short>(update_region.x + update_region.w),
        static_cast<unsigned short>(update_region.y + update_region.h),
    };
    if ((mPartialRegionState.blob_id == 0) ||
         mPartialRegionState.isUpdated(partial_rect))
    {
        uint32_t blob_id = 0;
        ret = mDrmDevice->CreatePropertyBlob(&partial_rect,
                sizeof(partial_rect),&blob_id);
        if (ret || (blob_id == 0)) {
            HWC_LOGE(mExynosDisplay, "Failed to create partial region "
                    "blob id=%d, ret=%d", blob_id, ret);
            return ret;
        }

        HDEBUGLOGD(eDebugWindowUpdate,
                "%s: partial region updated [%d, %d, %d, %d] -> [%d, %d, %d, %d] blob(%d)",
                mExynosDisplay->mDisplayName.c_str(),
                mPartialRegionState.partial_rect.x1,
                mPartialRegionState.partial_rect.y1,
                mPartialRegionState.partial_rect.x2,
                mPartialRegionState.partial_rect.y2,
                partial_rect.x1,
                partial_rect.y1,
                partial_rect.x2,
                partial_rect.y2,
                blob_id);
        mPartialRegionState.partial_rect = partial_rect;

        if (mPartialRegionState.blob_id)
            drmReq.addOldBlob(mPartialRegionState.blob_id);
        mPartialRegionState.blob_id = blob_id;
    }
    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
                    mDrmCrtc->partial_region_property(),
                    mPartialRegionState.blob_id)) < 0) {
        HWC_LOGE(mExynosDisplay, "Failed to set partial region property %d", ret);
        return ret;
    }

    return ret;
}

int32_t ExynosDisplayDrmInterface::waitVBlank() {
    drmVBlank vblank;
    uint32_t high_crtc = (mDrmCrtc->pipe() << DRM_VBLANK_HIGH_CRTC_SHIFT);
    memset(&vblank, 0, sizeof(vblank));
    vblank.request.type = (drmVBlankSeqType)(
        DRM_VBLANK_RELATIVE | (high_crtc & DRM_VBLANK_HIGH_CRTC_MASK));
    vblank.request.sequence = 1;

    int ret = drmWaitVBlank(mDrmDevice->fd(), &vblank);
    return ret;
}

int32_t ExynosDisplayDrmInterface::updateColorSettings(DrmModeAtomicReq &drmReq, uint64_t dqeEnabled) {
    int ret = NO_ERROR;

    if (dqeEnabled) {
        if ((ret = setDisplayColorSetting(drmReq)) != 0) {
            HWC_LOGE(mExynosDisplay, "Failed to set display color setting");
            return ret;
        }
    }

    for (size_t i = 0; i < mExynosDisplay->mDpuData.configs.size(); i++) {
        exynos_win_config_data& config = mExynosDisplay->mDpuData.configs[i];
        if ((config.state == config.WIN_STATE_BUFFER) ||
            (config.state == config.WIN_STATE_COLOR)) {
            int channelId = 0;
            if ((channelId = getDeconChannel(config.assignedMPP)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s:: Failed to get channel id (%d)",
                        __func__, channelId);
                ret = -EINVAL;
                return ret;
            }

            auto &plane = mDrmDevice->planes().at(channelId);
            uint32_t solidColor = config.color;
            if ((ret = setPlaneColorSetting(drmReq, plane, config, solidColor)) != 0) {
                HWC_LOGE(mExynosDisplay, "Failed to set plane color setting, config[%zu]", i);
                return ret;
            }
            if (config.state == config.WIN_STATE_COLOR && solidColor != config.color) {
                config.color = solidColor;
            }
        }
    }

    return ret;
}

int32_t ExynosDisplayDrmInterface::deliverWinConfigData()
{
    int ret = NO_ERROR;
    DrmModeAtomicReq drmReq(this);
    std::unordered_map<uint32_t, uint32_t> planeEnableInfo;
    android::String8 result;
    bool hasSecureFrameBuffer = false;
    bool hasM2mSecureLayerBuffer = false;

    mFrameCounter++;

    funcReturnCallback retCallback([&]() {
        if ((ret == NO_ERROR) && !drmReq.getError()) {
            mFBManager.flip(hasSecureFrameBuffer, hasM2mSecureLayerBuffer);
        } else if (ret == -ENOMEM) {
            ALOGW("OOM, release all cached buffers by FBManager");
            mFBManager.releaseAll();
        }
    });

    mFBManager.checkShrink();

    bool needModesetForReadback = false;
    if (mExynosDisplay->mDpuData.enable_readback) {
        if ((ret = setupWritebackCommit(drmReq)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s:: Failed to setup writeback commit ret(%d)",
                    __func__, ret);
            return ret;
        }
        needModesetForReadback = true;
    } else {
        if (mReadbackInfo.mNeedClearReadbackCommit) {
            if ((ret = clearWritebackCommit(drmReq)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s: Failed to clear writeback commit ret(%d)",
                         __func__, ret);
                return ret;
            }
            needModesetForReadback = true;
        }
    }

    uint64_t mipi_sync_type = 0;
    if (mDesiredModeState.needsModeSet()) {
        if (mExynosDisplay->checkRrCompensationEnabled()) {
            mipi_sync_type |=
                1 << mMipiSyncEnums[toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_REFRESH_RATE)];
        }

        if ((ret = setDisplayMode(drmReq, mDesiredModeState.blob_id, mDesiredModeState.mode.id())) <
            0) {
            HWC_LOGE(mExynosDisplay, "%s: Fail to apply display mode",
                    __func__);
            return ret;
        }
    }

    if ((ret = setupPartialRegion(drmReq)) != NO_ERROR)
        return ret;

    uint64_t out_fences[mDrmDevice->crtcs().size()];
    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
                    mDrmCrtc->out_fence_ptr_property(),
                    (uint64_t)&out_fences[mDrmCrtc->pipe()], true)) < 0) {
        return ret;
    }

    for (auto &plane : mDrmDevice->planes()) {
        planeEnableInfo[plane->id()] = 0;
    }

    uint64_t dqeEnable = 1;
    if (mExynosDisplay->mDpuData.enable_readback &&
        !mExynosDisplay->mDpuData.readback_info.requested_from_service) {
        dqeEnable = 0;
    }

    if ((mDrmCrtc->dqe_enabled_property().id()) &&
        ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
                                         mDrmCrtc->dqe_enabled_property(), dqeEnable)) < 0)) {
            HWC_LOGE(mExynosDisplay, "%s: Fail to dqe_enable setting", __func__);
            return ret;
    }

    // Update of color settings could change layer's solid color. So it should
    // be called before use of layer's solid color.
    if ((ret = updateColorSettings(drmReq, dqeEnable)) != 0) {
        HWC_LOGE(mExynosDisplay, "failed to update color settings (%d)", ret);
        return ret;
    }

    for (size_t i = 0; i < mExynosDisplay->mDpuData.configs.size(); i++) {
        exynos_win_config_data& config = mExynosDisplay->mDpuData.configs[i];
        if ((config.state == config.WIN_STATE_BUFFER) ||
            (config.state == config.WIN_STATE_COLOR)) {
            int channelId = 0;
            if ((channelId = getDeconChannel(config.assignedMPP)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s:: Failed to get channel id (%d)",
                        __func__, channelId);
                ret = -EINVAL;
                return ret;
            }
            /* src size should be set even in dim layer */
            if (config.state == config.WIN_STATE_COLOR) {
                config.src.w = config.dst.w;
                config.src.h = config.dst.h;
            }
            auto &plane = mDrmDevice->planes().at(channelId);
            uint32_t fbId = 0;
            if ((ret = setupCommitFromDisplayConfig(drmReq, config, i, plane, fbId)) < 0) {
                HWC_LOGE(mExynosDisplay, "setupCommitFromDisplayConfig failed, config[%zu]", i);
                return ret;
            }
            hasSecureFrameBuffer |= (isFramebuffer(config.layer) && config.protection);
            hasM2mSecureLayerBuffer |= (config.protection && config.layer && config.layer->mM2mMPP);
            /* Set this plane is enabled */
            planeEnableInfo[plane->id()] = 1;
        }
    }

    for (size_t i = 0; i < mExynosDisplay->mDpuData.rcdConfigs.size(); ++i) {
        exynos_win_config_data &config = mExynosDisplay->mDpuData.rcdConfigs[i];
        if ((config.state == config.WIN_STATE_RCD) &&
            (mExynosDisplay->mType == HWC_DISPLAY_PRIMARY)) {
            const int32_t rcdId = static_cast<ExynosPrimaryDisplay *>(mExynosDisplay)->mRcdId;
            const int32_t channelId = getSpecialChannelId(rcdId);
            if (channelId >= 0) {
                auto &plane = mDrmDevice->planes().at(channelId);
                uint32_t fbId = 0;
                if ((ret = setupCommitFromDisplayConfig(drmReq, config, i, plane, fbId)) < 0) {
                    HWC_LOGE(mExynosDisplay, "setupCommitFromDisplayConfig failed, config[%zu]", i);
                }
                planeEnableInfo[plane->id()] = 1;
            }
        }
    }

    /* Disable unused plane */
    for (auto &plane : mDrmDevice->planes()) {
        if (planeEnableInfo[plane->id()] == 0) {
            /* Don't disable planes that are reserved to other display */
            ExynosMPP* exynosMPP = mExynosMPPsForPlane[plane->id()];
            if ((exynosMPP != NULL) && (mExynosDisplay != NULL) &&
                (exynosMPP->mAssignedState & MPP_ASSIGN_STATE_RESERVED) &&
                (exynosMPP->mReservedDisplay != (int32_t)mExynosDisplay->mDisplayId))
                continue;

            if ((exynosMPP == NULL) && (mExynosDisplay->mType == HWC_DISPLAY_PRIMARY) &&
                (plane->id() != static_cast<ExynosPrimaryDisplay *>(mExynosDisplay)->mRcdId))
                continue;

            /* If this plane is not supported by the CRTC binded with ExynosDisplay,
             * it should be disabled by this ExynosDisplay */
            if (!plane->GetCrtcSupported(*mDrmCrtc))
                continue;

            if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->crtc_property(), 0)) < 0)
                return ret;

            if ((ret = drmReq.atomicAddProperty(plane->id(),
                    plane->fb_property(), 0)) < 0)
                return ret;
        }
    }

    if (ATRACE_ENABLED()) {
        mExynosDisplay->traceLayerTypes();
    }

    if (mExynosDisplay->mBrightnessController) {
        bool ghbmSync, lhbmSync, blSync, opRateSync;
        bool mixedComposition = mExynosDisplay->isMixedComposition()
                                || mExynosDisplay->isPriorFrameMixedCompostion();
        ret = mExynosDisplay->mBrightnessController->prepareFrameCommit(*mExynosDisplay,
                                                                        *mDrmConnector, drmReq,
                                                                        mixedComposition, ghbmSync,
                                                                        lhbmSync, blSync,
                                                                        opRateSync);
        if (ret < 0) {
            HWC_LOGE(mExynosDisplay, "%s: Fail to config brightness", __func__);
        } else {
            if (ghbmSync) {
                mipi_sync_type |=
                    1 << mMipiSyncEnums[toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_GHBM)];
            }
            if (lhbmSync) {
                mipi_sync_type |=
                    1 << mMipiSyncEnums[toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_LHBM)];
            }
            if (blSync) {
                mipi_sync_type |=
                    1 << mMipiSyncEnums[toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_BL)];
            }
            if (opRateSync) {
                mipi_sync_type |= 1
                        << mMipiSyncEnums[toUnderlying(HalMipiSyncType::HAL_MIPI_CMD_SYNC_OP_RATE)];
            }
        }
    }

    uint32_t flags = DRM_MODE_ATOMIC_NONBLOCK;
    if (needModesetForReadback || !mDesiredModeState.isSeamless())
        flags |= DRM_MODE_ATOMIC_ALLOW_MODESET;

    /* For Histogram */
    // TODO: b/300026478 - Skip setDisplayHistogramSetting when multi channel is enabled
    if (dqeEnable && (ret = setDisplayHistogramSetting(drmReq)) != 0) {
        HWC_LOGE(mExynosDisplay, "Failed to set display histogram setting (%d)", ret);
    }

    /* For multichannel histogram */
    if (dqeEnable && mExynosDisplay->mHistogramController) {
        mExynosDisplay->mHistogramController->prepareAtomicCommit(drmReq);
    }

    if (mDrmConnector->mipi_sync().id() && (mipi_sync_type != 0)) {
        // skip mipi sync in Doze mode
        bool inDoze = isDozeModeAvailable() && mDozeDrmMode.id() == mActiveModeState.mode.id();
        if (!inDoze) {
            ATRACE_NAME("mipi_sync"); // mark this commit
            if ((ret = drmReq.atomicAddProperty(mDrmConnector->id(),
                                                mDrmConnector->mipi_sync(),
                                                mipi_sync_type)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s: Fail to set mipi_sync property (%d)", __func__, ret);
            }
        }
    }

    auto expectedPresentTime = mExynosDisplay->getPendingExpectedPresentTime();
    if (expectedPresentTime != 0) {
        /* TODO: don't pass expected present time before we can provide accurate time that desire
         * refresh rate take effect (b/202346402)
         */
        bool ignoreExpectedPresentTime = false;
        if (mVsyncCallback.getDesiredVsyncPeriod()) {
            ignoreExpectedPresentTime = true;

            /* limit the condition to avoid unexpected early present */
            auto desiredVsyncPeriod = mVsyncCallback.getDesiredVsyncPeriod();
            auto currentVsyncPeriod = mExynosDisplay->mVsyncPeriod;
            constexpr auto nsecsPerMs = std::chrono::nanoseconds(1ms).count();
            if (currentVsyncPeriod > desiredVsyncPeriod &&
                (((currentVsyncPeriod % desiredVsyncPeriod) < nsecsPerMs) ||
                 (desiredVsyncPeriod - (currentVsyncPeriod % desiredVsyncPeriod)) < nsecsPerMs)) {
                ignoreExpectedPresentTime = false;
            }
        }

        if (!ignoreExpectedPresentTime) {
            if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
                                                mDrmCrtc->expected_present_time_property(),
                                                expectedPresentTime)) < 0) {
                HWC_LOGE(mExynosDisplay, "%s: Fail to set expected_present_time property (%d)",
                         __func__, ret);
            }
        }
        mExynosDisplay->applyExpectedPresentTime();
    }

    if ((ret = drmReq.commit(flags, true)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s:: Failed to commit pset ret=%d in deliverWinConfigData()\n",
                __func__, ret);
        return ret;
    }

    mExynosDisplay->mDpuData.retire_fence = (int)out_fences[mDrmCrtc->pipe()];
    /*
     * [HACK] dup retire_fence for each layer's release fence
     * Do not use hwc_dup because hwc_dup increase usage count of fence treacer
     * Usage count of this fence is incresed by ExynosDisplay::deliverWinConfigData()
     */
    for (auto &display_config : mExynosDisplay->mDpuData.configs) {
        if ((display_config.state == display_config.WIN_STATE_BUFFER) ||
            (display_config.state == display_config.WIN_STATE_CURSOR)) {
            display_config.rel_fence =
                dup((int)out_fences[mDrmCrtc->pipe()]);
        }
    }

    if (mDesiredModeState.needsModeSet()) {
        mDesiredModeState.apply(mActiveModeState, drmReq);
        if (!mActiveModeState.isSeamless()) {
            mDrmConnector->ResetLpMode();
            getLowPowerDrmModeModeInfo();
        }
        mVsyncCallback.setDesiredVsyncPeriod(mActiveModeState.mode.te_period());
        /* Enable vsync to check vsync period */
        mDrmVSyncWorker.VSyncControl(true);
    }

    /* For multichannel histogram */
    if (dqeEnable && mExynosDisplay->mHistogramController) {
        mExynosDisplay->mHistogramController->postAtomicCommit();
    }

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::clearDisplayMode(DrmModeAtomicReq &drmReq)
{
    int ret = NO_ERROR;

    if ((ret = drmReq.atomicAddProperty(mDrmConnector->id(),
            mDrmConnector->crtc_id_property(), 0)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
            mDrmCrtc->mode_property(), 0)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(),
           mDrmCrtc->active_property(), 0)) < 0)
        return ret;

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::triggerClearDisplayPlanes()
{
    ATRACE_CALL();
    DrmModeAtomicReq drmReq(this);

    clearDisplayPlanes(drmReq);
    int ret = NO_ERROR;
    if ((ret = drmReq.commit(0, true))) {
        HWC_LOGE(mExynosDisplay, "%s:: Failed to commit pset ret=(%d)\n",
                __func__, ret);
        return ret;
    }
    return ret;
}

void ExynosDisplayDrmInterface::setVrrSettings(const VrrSettings_t& vrrSettings) {
    if (vrrSettings.enabled) {
        mIsVrrModeSupported = true;
        mNotifyExpectedPresentHeadsUpNs = vrrSettings.notifyExpectedPresentConfig.HeadsUpNs;
        mNotifyExpectedPresentTimeoutNs = vrrSettings.notifyExpectedPresentConfig.TimeoutNs;
        mConfigChangeCallback = vrrSettings.configChangeCallback;
    }
}

int32_t ExynosDisplayDrmInterface::clearDisplayPlanes(DrmModeAtomicReq &drmReq)
{
    int ret = NO_ERROR;

    /* Disable all planes */
    for (auto &plane : mDrmDevice->planes()) {
        /* Do not disable planes that are reserved to other dispaly */
        ExynosMPP* exynosMPP = mExynosMPPsForPlane[plane->id()];
        if ((exynosMPP != NULL) && (mExynosDisplay != NULL) &&
            (exynosMPP->mAssignedState & MPP_ASSIGN_STATE_RESERVED) &&
            (exynosMPP->mReservedDisplay != (int32_t)mExynosDisplay->mDisplayId))
            continue;

        /* If this plane is not supported by the CRTC binded with ExynosDisplay,
         * it should be disabled by this ExynosDisplay */
        if (!plane->GetCrtcSupported(*mDrmCrtc))
            continue;

        if ((ret = drmReq.atomicAddProperty(plane->id(),
                                            plane->crtc_property(), 0)) < 0) {
            break;
        }

        if ((ret = drmReq.atomicAddProperty(plane->id(),
                                            plane->fb_property(), 0)) < 0) {
            break;
        }
    }

    return ret;
}

int32_t ExynosDisplayDrmInterface::clearDisplay(bool needModeClear)
{
    ExynosDevice *exynosDevice = mExynosDisplay->mDevice;
    const bool isAsyncOff = needModeClear && exynosDevice->isDispOffAsyncSupported() &&
            !exynosDevice->hasOtherDisplayOn(mExynosDisplay);
    int ret = NO_ERROR;
    DrmModeAtomicReq drmReq(this);

    ret = clearDisplayPlanes(drmReq);
    if (ret != NO_ERROR) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to clear planes", __func__);

        return ret;
    }

    /* Disable readback connector if required */
    if (mReadbackInfo.mNeedClearReadbackCommit &&
        !mExynosDisplay->mDpuData.enable_readback) {
        if ((ret = clearWritebackCommit(drmReq)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s: Failed to apply writeback", __func__);
            return ret;
        }
    }

    /* Disable ModeSet */
    if (needModeClear && !isAsyncOff) {
        if ((ret = clearDisplayMode(drmReq)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s: Failed to apply display mode", __func__);
            return ret;
        }
    }

    ret = drmReq.commit(DRM_MODE_ATOMIC_ALLOW_MODESET, true);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "%s:: Failed to commit pset ret=%d in clearDisplay()\n",
                __func__, ret);
        return ret;
    }

    /* During async off we're clearing planes within a single refresh cycle
     * and then offloading display off asynchronously.
     */
    if (isAsyncOff) {
        if ((ret = clearDisplayMode(drmReq)) < 0) {
            HWC_LOGE(mExynosDisplay, "%s: Failed to apply display mode", __func__);
            return ret;
        }

        ret = drmReq.commit(DRM_MODE_ATOMIC_ALLOW_MODESET | DRM_MODE_ATOMIC_NONBLOCK, true);
        if (ret) {
            HWC_LOGE(mExynosDisplay, "%s:: Failed to commit pset ret=%d in clearDisplay()\n",
                     __func__, ret);
            return ret;
        }
    }

    if (needModeClear) mActiveModeState.forceModeSet();

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::disableSelfRefresh(uint32_t disable)
{
    return 0;
}

int32_t ExynosDisplayDrmInterface::setForcePanic()
{
    if (exynosHWCControl.forcePanic == 0)
        return NO_ERROR;

    usleep(20000000);

    FILE *forcePanicFd = fopen(HWC_FORCE_PANIC_PATH, "w");
    if (forcePanicFd == NULL) {
        ALOGW("%s:: Failed to open fd", __func__);
        return -1;
    }

    int val = 1;
    fwrite(&val, sizeof(int), 1, forcePanicFd);
    fclose(forcePanicFd);

    return 0;
}

ExynosDisplayDrmInterface::DrmModeAtomicReq::DrmModeAtomicReq(ExynosDisplayDrmInterface *displayInterface)
    : mDrmDisplayInterface(displayInterface)
{
    mPset = drmModeAtomicAlloc();
    mSavedPset = NULL;
}

ExynosDisplayDrmInterface::DrmModeAtomicReq::~DrmModeAtomicReq()
{
    if (mError != 0) {
        android::String8 result;
        result.appendFormat("atomic commit error\n");
        if (hwcCheckDebugMessages(eDebugDisplayInterfaceConfig) == false)
            dumpAtomicCommitInfo(result);
        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay, "%s", result.c_str());
    }

    if(mPset)
        drmModeAtomicFree(mPset);

    if (destroyOldBlobs() != NO_ERROR)
        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay, "destroy blob error");
}

int32_t ExynosDisplayDrmInterface::DrmModeAtomicReq::atomicAddProperty(
        const uint32_t id,
        const DrmProperty &property,
        uint64_t value, bool optional)
{
    if (!optional && !property.id()) {
        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay, "%s:: %s property id(%d) for id(%d) is not available",
                __func__, property.name().c_str(), property.id(), id);
        return -EINVAL;
    }

    if (property.id() && property.validateChange(value)) {
        int ret = drmModeAtomicAddProperty(mPset, id,
                property.id(), value);
        if (ret < 0) {
            HWC_LOGE(mDrmDisplayInterface->mExynosDisplay, "%s:: Failed to add property %d(%s) for id(%d), ret(%d)",
                    __func__, property.id(), property.name().c_str(), id, ret);
            return ret;
        }
    }

    return NO_ERROR;
}

String8& ExynosDisplayDrmInterface::DrmModeAtomicReq::dumpAtomicCommitInfo(
        String8 &result, bool debugPrint)
{
    /* print log only if eDebugDisplayInterfaceConfig flag is set when debugPrint is true */
    if (debugPrint &&
        (hwcCheckDebugMessages(eDebugDisplayInterfaceConfig) == false))
        return result;

    if (debugPrint)
        ALOGD("%s atomic config ++++++++++++", mDrmDisplayInterface->mExynosDisplay->mDisplayName.c_str());

    for (int i = 0; i < drmModeAtomicGetCursor(mPset); i++) {
        const DrmProperty *property = NULL;
        String8 objectName;
        /* Check crtc properties */
        if (mPset->items[i].object_id == mDrmDisplayInterface->mDrmCrtc->id()) {
            for (auto property_ptr : mDrmDisplayInterface->mDrmCrtc->properties()) {
                if (mPset->items[i].property_id == property_ptr->id()){
                    property = property_ptr;
                    objectName.appendFormat("Crtc");
                    break;
                }
            }
            if (property == NULL) {
                HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                        "%s:: object id is crtc but there is no matched property",
                        __func__);
            }
        } else if (mPset->items[i].object_id == mDrmDisplayInterface->mDrmConnector->id()) {
            for (auto property_ptr : mDrmDisplayInterface->mDrmConnector->properties()) {
                if (mPset->items[i].property_id == property_ptr->id()){
                    property = property_ptr;
                    objectName.appendFormat("Connector");
                    break;
                }
            }
            if (property == NULL) {
                HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                        "%s:: object id is connector but there is no matched property",
                        __func__);
            }
        } else {
            uint32_t channelId = 0;
            for (auto &plane : mDrmDisplayInterface->mDrmDevice->planes()) {
                if (mPset->items[i].object_id == plane->id()) {
                    for (auto property_ptr : plane->properties()) {
                        if (mPset->items[i].property_id == property_ptr->id()){
                            property = property_ptr;
                            objectName.appendFormat("Plane[%d]", channelId);
                            break;
                        }
                    }
                    if (property == NULL) {
                        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                                "%s:: object id is plane but there is no matched property",
                                __func__);
                    }
                }
                channelId++;
            }
        }
        if (property == NULL) {
            HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                    "%s:: Fail to get property[%d] (object_id: %d, property_id: %d, value: %" PRId64 ")",
                    __func__, i, mPset->items[i].object_id, mPset->items[i].property_id,
                    mPset->items[i].value);
            continue;
        }

        if (debugPrint)
            ALOGD("property[%d] %s object_id: %d, property_id: %d, name: %s,  value: %" PRId64 ")\n",
                    i, objectName.c_str(), mPset->items[i].object_id, mPset->items[i].property_id, property->name().c_str(), mPset->items[i].value);
        else
            result.appendFormat("property[%d] %s object_id: %d, property_id: %d, name: %s,  value: %" PRId64 ")\n",
                i,  objectName.c_str(), mPset->items[i].object_id, mPset->items[i].property_id, property->name().c_str(), mPset->items[i].value);
    }
    return result;
}

int ExynosDisplayDrmInterface::DrmModeAtomicReq::commit(uint32_t flags, bool loggingForDebug)
{
    ATRACE_NAME("drmModeAtomicCommit");
    android::String8 result;

    /*
     * During kernel is in TUI, all atomic commits should be returned with error EPERM(-1).
     * To avoid handling atomic commit as fail, it needs to check TUI status.
     */
    int ret = drmModeAtomicCommit(mDrmDisplayInterface->mDrmDevice->fd(),
            mPset, flags, mDrmDisplayInterface->mDrmDevice);
    if (loggingForDebug)
        dumpAtomicCommitInfo(result, true);
    if ((ret == -EPERM) && mDrmDisplayInterface->mDrmDevice->event_listener()->IsDrmInTUI()) {
        ALOGV("skip atomic commit error handling as kernel is in TUI");
        ret = NO_ERROR;
    } else if (ret < 0) {
        if (ret == -EINVAL) {
            dumpDrmAtomicCommitMessage(ret);
        }
        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay, "commit error: %d", ret);
        setError(ret);
    }

    if (ret == 0 && mAckCallback) {
        if (!(flags & DRM_MODE_ATOMIC_TEST_ONLY)) {
            mAckCallback();
        }
    }

    if (mDrmDisplayInterface->mIsResolutionSwitchInProgress &&
        !mDrmDisplayInterface->mDesiredModeState.needsModeSet()) {
        mDrmDisplayInterface->mIsResolutionSwitchInProgress = false;
        mDrmDisplayInterface->mExynosDisplay->mDevice->setVBlankOffDelay(1);
    }

    return ret;
}

void ExynosDisplayDrmInterface::DrmModeAtomicReq::dumpDrmAtomicCommitMessage(int err) {
    const nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    const nsecs_t diffMs = ns2ms(now - mDrmDisplayInterface->mLastDumpDrmAtomicMessageTime);
    if (diffMs < kAllowDumpDrmAtomicMessageTimeMs) {
        return;
    }

    if (writeIntToKernelFile(kDrmModuleParametersDebugNode, kEnableDrmAtomicMessage)) {
        return;
    }

    HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
             "commit error, enable atomic message and test again");
    int ret = drmModeAtomicCommit(mDrmDisplayInterface->mDrmDevice->fd(), mPset,
                                  DRM_MODE_ATOMIC_TEST_ONLY, mDrmDisplayInterface->mDrmDevice);
    if (ret != err) {
        HWC_LOGE(mDrmDisplayInterface->mExynosDisplay,
                 "re-try commit error(%d) is different from %d", ret, err);
    }

    writeIntToKernelFile(kDrmModuleParametersDebugNode, kDisableDrmDebugMessage);
    mDrmDisplayInterface->mLastDumpDrmAtomicMessageTime = systemTime(SYSTEM_TIME_MONOTONIC);
}

int32_t ExynosDisplayDrmInterface::getReadbackBufferAttributes(
        int32_t* /*android_pixel_format_t*/ outFormat,
        int32_t* /*android_dataspace_t*/ outDataspace)
{
    DrmConnector *writeback_conn = mReadbackInfo.getWritebackConnector();
    if (writeback_conn == NULL) {
        ALOGE("%s: There is no writeback connection", __func__);
        return -EINVAL;
    }
    mReadbackInfo.pickFormatDataspace();
    if (mReadbackInfo.mReadbackFormat ==
            HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
        ALOGE("readback format(%d) is not valid",
                mReadbackInfo.mReadbackFormat);
        return -EINVAL;
    }
    *outFormat = mReadbackInfo.mReadbackFormat;
    *outDataspace = HAL_DATASPACE_UNKNOWN;
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::setupWritebackCommit(DrmModeAtomicReq &drmReq)
{
    int ret = NO_ERROR;
    DrmConnector *writeback_conn = mReadbackInfo.getWritebackConnector();
    if (writeback_conn == NULL) {
        ALOGE("%s: There is no writeback connection", __func__);
        return -EINVAL;
    }
    if (writeback_conn->writeback_fb_id().id() == 0 ||
        writeback_conn->writeback_out_fence().id() == 0) {
        ALOGE("%s: Writeback properties don't exit", __func__);
        return -EINVAL;
    }

    uint32_t writeback_fb_id = 0;
    exynos_win_config_data writeback_config;
    VendorGraphicBufferMeta gmeta(mExynosDisplay->mDpuData.readback_info.handle);

    writeback_config.state = exynos_win_config_data::WIN_STATE_BUFFER;
    writeback_config.format = mReadbackInfo.mReadbackFormat;
    writeback_config.src = {0, 0, mExynosDisplay->mXres, mExynosDisplay->mYres,
                            gmeta.stride, gmeta.vstride};
    writeback_config.dst = {0, 0, mExynosDisplay->mXres, mExynosDisplay->mYres,
                            gmeta.stride, gmeta.vstride};
    writeback_config.fd_idma[0] = gmeta.fd;
    writeback_config.fd_idma[1] = gmeta.fd1;
    writeback_config.fd_idma[2] = gmeta.fd2;
    if ((ret = mFBManager.getBuffer(writeback_config, writeback_fb_id)) < 0) {
        ALOGE("%s: getBuffer() fail ret(%d)", __func__, ret);
        return ret;
    }

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->writeback_fb_id(),
            writeback_fb_id)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->writeback_out_fence(),
            (uint64_t)& mExynosDisplay->mDpuData.readback_info.acq_fence)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->crtc_id_property(),
            mDrmCrtc->id())) < 0)
        return ret;

    mReadbackInfo.setFbId(writeback_fb_id);
    mReadbackInfo.mNeedClearReadbackCommit = true;
    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterface::clearWritebackCommit(DrmModeAtomicReq &drmReq)
{
    int ret;

    DrmConnector *writeback_conn = mReadbackInfo.getWritebackConnector();
    if (writeback_conn == NULL) {
        ALOGE("%s: There is no writeback connection", __func__);
        return -EINVAL;
    }

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->writeback_fb_id(), 0)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->writeback_out_fence(), 0)) < 0)
        return ret;

    if ((ret = drmReq.atomicAddProperty(writeback_conn->id(),
            writeback_conn->crtc_id_property(), 0)) < 0)
        return ret;

    mReadbackInfo.mNeedClearReadbackCommit = false;
    return NO_ERROR;
}

void ExynosDisplayDrmInterface::DrmReadbackInfo::init(DrmDevice *drmDevice, uint32_t displayId)
{
    mDrmDevice = drmDevice;
    mWritebackConnector = mDrmDevice->AvailableWritebackConnector(displayId);
    if (mWritebackConnector == NULL) {
        ALOGI("writeback is not supported");
        return;
    }
    if (mWritebackConnector->writeback_fb_id().id() == 0 ||
        mWritebackConnector->writeback_out_fence().id() == 0) {
        ALOGE("%s: Writeback properties don't exit", __func__);
        mWritebackConnector = NULL;
        return;
    }

    if (mWritebackConnector->writeback_pixel_formats().id()) {
        int32_t ret = NO_ERROR;
        uint64_t blobId;
        std::tie(ret, blobId) = mWritebackConnector->writeback_pixel_formats().value();
        if (ret) {
            ALOGE("Fail to get blob id for writeback_pixel_formats");
            return;
        }
        drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(mDrmDevice->fd(), blobId);
        if (!blob) {
            ALOGE("Fail to get blob for writeback_pixel_formats(%" PRId64 ")", blobId);
            return;
        }
        uint32_t formatNum = (blob->length)/sizeof(uint32_t);
        uint32_t *formats = (uint32_t *)blob->data;
        for (uint32_t i = 0; i < formatNum; i++) {
            int halFormat = drmFormatToHalFormat(formats[i]);
            ALOGD("supported writeback format[%d] %4.4s, %d", i, (char *)&formats[i], halFormat);
            if (halFormat != HAL_PIXEL_FORMAT_EXYNOS_UNDEFINED)
                mSupportedFormats.push_back(halFormat);
        }
        drmModeFreePropertyBlob(blob);
    }
}

void ExynosDisplayDrmInterface::DrmReadbackInfo::pickFormatDataspace()
{
    if (!mSupportedFormats.empty())
        mReadbackFormat = mSupportedFormats[0];
    auto it = std::find(mSupportedFormats.begin(),
            mSupportedFormats.end(), PREFERRED_READBACK_FORMAT);
    if (it != mSupportedFormats.end())
        mReadbackFormat = *it;
}

int32_t ExynosDisplayDrmInterface::getDisplayFakeEdid(uint8_t &outPort, uint32_t &outDataSize,
                                                      uint8_t *outData) {
    uint32_t width = mExynosDisplay->mXres;
    uint32_t height = mExynosDisplay->mYres;
    uint32_t clock = (width * height * kDefaultRefreshRateFrequency) / 10000;
    std::array<uint8_t, 128> edid_buf{
            0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, /* header */
            0x1C, 0xEC,                                     /* manufacturer GGL */
            0x00, 0x00,                                     /* product */
            0x00, 0x00, 0x00, 0x00,                         /* serial number */
            0x01,                                           /* week of manufacture */
            0x00,                                           /* year of manufacture */
            0x01, 0x03,                                     /* EDID version */
            0x80,                                           /* capabilities - digital */
            0x00,                                           /* horizontal in cm */
            0x00,                                           /* vertical in cm */
            0x78,                                           /* gamma 2.2 */
            0xEE, 0xEE, 0x91, 0xA3, 0x54, 0x4C, 0x99, 0x26, 0x0F, 0x50, 0x54, /* chromaticity */
            0x00, 0x00, 0x00, /* no default timings */
            /* no standard timings */
            0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01,
            0x01, 0x01,
            /* descriptor block 1 */
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            /* descriptor block 2 */
            0x00, 0x00, 0x00, 0xFD, 0x00, 0x00, 0xC8, 0x00, 0xC8, 0x64, 0x00, 0x0A, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20,
            /* descriptor block 3 */
            0x00, 0x00, 0x00, 0xFC, 0x00, 'C', 'o', 'm', 'm', 'o', 'n', ' ', 'P', 'a', 'n', 'e',
            'l', '\n',
            /* descriptor block 4 */
            0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, /* number of extensions */
            0x00                          /* checksum */
    };
    edid_buf[55] = clock >> 8;
    edid_buf[56] = width & 0xff;
    edid_buf[58] = (width >> 4) & 0xf0;
    edid_buf[59] = height & 0xff;
    edid_buf[61] = (height >> 4) & 0xf0;

    if (mMonitorDescription[0] != 0) {
        /* Descriptor block 3 starts at address 90, data offset is 5 bytes */
        memcpy(&edid_buf[95], mMonitorDescription.data(), mMonitorDescription.size());
    }

    unsigned int sum = std::accumulate(edid_buf.begin(), edid_buf.end() - 1, 0);
    edid_buf[127] = (0x100 - (sum & 0xFF)) & 0xFF;
    if (outData) {
        outDataSize = std::min<uint32_t>(outDataSize, edid_buf.size());
        memcpy(outData, edid_buf.data(), outDataSize);
    } else {
        outDataSize = static_cast<uint32_t>(edid_buf.size());
    }

    outPort = mExynosDisplay->mDisplayId;
    ALOGD("using Display Fake Edid");
    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplayDrmInterface::getDisplayIdentificationData(
        uint8_t* outPort, uint32_t* outDataSize, uint8_t* outData)
{
    if ((mDrmDevice == nullptr) || (mDrmConnector == nullptr)) {
        ALOGE("%s: display(%s) mDrmDevice(%p), mDrmConnector(%p)",
                __func__, mExynosDisplay->mDisplayName.c_str(),
                mDrmDevice, mDrmConnector);
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (mDrmConnector->edid_property().id() == 0) {
        ALOGD("%s: edid_property is not supported",
                mExynosDisplay->mDisplayName.c_str());
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (outPort == nullptr || outDataSize == nullptr) return HWC2_ERROR_BAD_PARAMETER;

    drmModePropertyBlobPtr blob;
    int ret;
    uint64_t blobId;

    std::tie(ret, blobId) = mDrmConnector->edid_property().value();
    if (ret) {
        ALOGE("Failed to get edid property value.");
        return HWC2_ERROR_UNSUPPORTED;
    }
    if (blobId == 0) {
        ALOGD("%s: edid_property is supported but blob is not valid",
                mExynosDisplay->mDisplayName.c_str());
        return getDisplayFakeEdid(*outPort, *outDataSize, outData);
    }

    blob = drmModeGetPropertyBlob(mDrmDevice->fd(), blobId);
    if (blob == nullptr) {
        ALOGD("%s: Failed to get blob",
                mExynosDisplay->mDisplayName.c_str());
        return HWC2_ERROR_UNSUPPORTED;
    }

    if (outData) {
        *outDataSize = std::min(*outDataSize, blob->length);
        memcpy(outData, blob->data, *outDataSize);
        setManufacturerInfo(outData[kEDIDManufacturerIDByte1], outData[kEDIDManufacturerIDByte2]);
        setProductId(outData[kEDIDProductIDByte1], outData[kEDIDProductIDByte2]);
    } else {
        *outDataSize = blob->length;
    }
    drmModeFreePropertyBlob(blob);
    *outPort = mDrmConnector->id();

    return HWC2_ERROR_NONE;
}

int32_t ExynosDisplayDrmInterface::getSpecialChannelId(uint32_t planeId) {
    ExynosDevice *exynosDevice = mExynosDisplay->mDevice;
    for (int i = 0; i < exynosDevice->getSpecialPlaneNum(); i++) {
        const int32_t channelId = exynosDevice->getSpecialPlaneId(i);
        auto &plane = mDrmDevice->planes().at(channelId);
        if (plane->id() == planeId) return channelId;
    }

    ALOGE("%s: Failed to get RCD planeId.", __func__);

    return -EINVAL;
}

bool ExynosDisplayDrmInterface::readHotplugStatus() {
    if (mDrmConnector == nullptr) {
        return false;
    }

    uint32_t numConfigs = 0;
    int32_t err = getDisplayConfigs(&numConfigs, NULL);

    return (err == HWC2_ERROR_NONE && numConfigs > 0 && mExynosDisplay->mPlugState);
}

void ExynosDisplayDrmInterface::retrievePanelFullResolution() {
    std::lock_guard<std::recursive_mutex> lock(mDrmConnector->modesLock());

    // The largest resolution in the modes of mDrmConnector is the panel full resolution.
    for (auto it = mDrmConnector->modes().begin(); it != mDrmConnector->modes().end(); it++) {
        if (it->h_display() * it->v_display() >
            mPanelFullResolutionHSize * mPanelFullResolutionVSize) {
            mPanelFullResolutionHSize = it->h_display();
            mPanelFullResolutionVSize = it->v_display();
        }
    }

    if (mPanelFullResolutionHSize <= 0 || mPanelFullResolutionVSize <= 0) {
        ALOGE("%s: failed to get panel full resolution", __func__);
    } else {
        ALOGI("%s: panel full resolution: (%dx%d)", __func__, mPanelFullResolutionHSize,
              mPanelFullResolutionVSize);
    }
}

int32_t ExynosDisplayDrmInterface::setDisplayHistogramChannelSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq, uint8_t channelId, void *blobData,
        size_t blobLength) {
    int ret = NO_ERROR;
    uint32_t blobId = 0;

    ATRACE_NAME(String8::format("%s #%u", __func__, channelId).c_str());

    const DrmProperty &prop = mDrmCrtc->histogram_channel_property(channelId);
    if (!prop.id()) {
        ALOGE("Unsupported multi-channel histrogram for channel:%d", channelId);
        return -ENOTSUP;
    }

    ret = mDrmDevice->CreatePropertyBlob(blobData, blobLength, &blobId);
    if (ret) {
        HWC_LOGE(mExynosDisplay, "Failed to create histogram channel(%d) blob %d", channelId, ret);
        return ret;
    }

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop, blobId)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to add property", __func__);
        return ret;
    }

    // TODO: b/295794044 - Clear the old histogram channel blob

    return ret;
}

int32_t ExynosDisplayDrmInterface::clearDisplayHistogramChannelSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq, uint8_t channelId) {
    int ret = NO_ERROR;

    ATRACE_NAME(String8::format("%s #%u", __func__, channelId).c_str());

    const DrmProperty &prop = mDrmCrtc->histogram_channel_property(channelId);
    if (!prop.id()) {
        ALOGE("Unsupported multi-channel histrogram for channel:%d", channelId);
        return -ENOTSUP;
    }

    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop, 0)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Failed to add property", __func__);
        return ret;
    }

    // TODO: b/295794044 - Clear the old histogram channel blob

    return ret;
}

// TODO: b/295990513 - Remove the if defined after kernel prebuilts are merged.
#if defined(EXYNOS_HISTOGRAM_CHANNEL_REQUEST)
int32_t ExynosDisplayDrmInterface::sendHistogramChannelIoctl(HistogramChannelIoctl_t control,
                                                             uint8_t channelId) const {
    struct exynos_drm_histogram_channel_request histogramRequest;

    histogramRequest.crtc_id = mDrmCrtc->id();
    histogramRequest.hist_id = channelId;

    if (control == HistogramChannelIoctl_t::REQUEST) {
        ATRACE_NAME(String8::format("requestIoctl #%u", channelId).c_str());
        return mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_CHANNEL_REQUEST,
                                           (void*)&histogramRequest);
    } else if (control == HistogramChannelIoctl_t::CANCEL) {
        ATRACE_NAME(String8::format("cancelIoctl #%u", channelId).c_str());
        return mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_CHANNEL_CANCEL,
                                           (void*)&histogramRequest);
    } else {
        ALOGE("%s: unknown control %d", __func__, (int)control);
        return BAD_VALUE;
    }
}
#else
int32_t ExynosDisplayDrmInterface::sendHistogramChannelIoctl(HistogramChannelIoctl_t control,
                                                             uint8_t channelId) const {
    ALOGE("%s: kernel doesn't support multi channel histogram ioctl", __func__);
    return INVALID_OPERATION;
}
#endif

static constexpr auto kDpHotplugErrorCodeSysfsPath =
        "/sys/devices/platform/110f0000.drmdp/drm-displayport/dp_hotplug_error_code";

int ExynosDisplayDrmInterface::readHotplugErrorCode() {
    if (mExynosDisplay->mType != HWC_DISPLAY_EXTERNAL) return 0;
    int hotplug_error_code = 0;
    std::ifstream ifs(kDpHotplugErrorCodeSysfsPath);
    if (ifs.is_open()) ifs >> hotplug_error_code;
    return hotplug_error_code;
}

void ExynosDisplayDrmInterface::resetHotplugErrorCode() {
    if (mExynosDisplay->mType != HWC_DISPLAY_EXTERNAL) return;
    std::ofstream ofs(kDpHotplugErrorCodeSysfsPath);
    if (ofs.is_open()) ofs << "0";
}

void ExynosDisplayDrmInterface::handleDrmPropertyUpdate(uint32_t connector_id, uint32_t prop_id) {
    if (!mDrmConnector || mDrmConnector->id() != connector_id) return;
    auto& conn_props = mDrmConnector->properties();
    auto prop = std::find_if(conn_props.begin(), conn_props.end(),
                             [prop_id](const DrmProperty* prop) { return prop->id() == prop_id; });
    if (prop == conn_props.end()) {
        ALOGD("%s: Unknown property prop_id=%u", __func__, prop_id);
        return;
    }
    mDrmDevice->UpdateConnectorProperty(*mDrmConnector, *prop);
    if ((*prop)->id() == mDrmConnector->content_protection().id()) {
        auto [ret, content_protection_value] = mDrmConnector->content_protection().value();
        if (ret < 0) {
            ALOGW("%s: failed to get DRM content_protection property value ret=%d", __func__, ret);
            return;
        }
        bool protectionEnabled = (content_protection_value == DRM_MODE_CONTENT_PROTECTION_ENABLED);
        HdcpLevels hdcpLevels;
        hdcpLevels.connectedLevel = protectionEnabled ? HdcpLevel::HDCP_V1 : HdcpLevel::HDCP_NONE;
        hdcpLevels.maxLevel = HdcpLevel::HDCP_V1;
        mExynosDisplay->contentProtectionUpdated(hdcpLevels);
    }
}

void ExynosDisplayDrmInterface::setManufacturerInfo(uint8_t edid8, uint8_t edid9) {
    mManufacturerInfo = edid9 << 8 | edid8;
}

void ExynosDisplayDrmInterface::setProductId(uint8_t edid10, uint8_t edid11) {
    mProductId = edid11 << 8 | edid10;
}

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

#include "ExynosDeviceDrmInterface.h"

#include <drm/drm_mode.h>
#include <drm/samsung_drm.h>
#include <hardware/hwcomposer_defs.h>

#include "ExynosDevice.h"
#include "ExynosDisplay.h"
#include "ExynosDisplayDrmInterface.h"
#include "ExynosExternalDisplayModule.h"
#include "ExynosHWCDebug.h"
#include "HistogramController.h"

void set_hwc_dpp_size_range(hwc_dpp_size_range &hwc_dpp_range, dpp_size_range &dpp_range) {
    hwc_dpp_range.min = dpp_range.min;
    hwc_dpp_range.max = dpp_range.max;
    hwc_dpp_range.align = dpp_range.align;
}

static void set_dpp_ch_restriction(struct hwc_dpp_ch_restriction &hwc_dpp_restriction,
                                   struct dpp_ch_restriction &drm_restriction) {
    hwc_dpp_restriction.id = drm_restriction.id;
    hwc_dpp_restriction.attr = drm_restriction.attr;
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.src_f_w, drm_restriction.restriction.src_f_w);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.src_f_h, drm_restriction.restriction.src_f_h);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.src_w, drm_restriction.restriction.src_w);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.src_h, drm_restriction.restriction.src_h);
    hwc_dpp_restriction.restriction.src_x_align = drm_restriction.restriction.src_x_align;
    hwc_dpp_restriction.restriction.src_y_align = drm_restriction.restriction.src_y_align;
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.dst_f_w, drm_restriction.restriction.dst_f_w);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.dst_f_h, drm_restriction.restriction.dst_f_h);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.dst_w, drm_restriction.restriction.dst_w);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.dst_h, drm_restriction.restriction.dst_h);
    hwc_dpp_restriction.restriction.dst_x_align = drm_restriction.restriction.dst_x_align;
    hwc_dpp_restriction.restriction.dst_y_align = drm_restriction.restriction.dst_y_align;
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.blk_w, drm_restriction.restriction.blk_w);
    set_hwc_dpp_size_range(hwc_dpp_restriction.restriction.blk_h, drm_restriction.restriction.blk_h);
    hwc_dpp_restriction.restriction.blk_x_align = drm_restriction.restriction.blk_x_align;
    hwc_dpp_restriction.restriction.blk_y_align = drm_restriction.restriction.blk_y_align;
    hwc_dpp_restriction.restriction.src_h_rot_max = drm_restriction.restriction.src_h_rot_max;
    hwc_dpp_restriction.restriction.scale_down = drm_restriction.restriction.scale_down;
    hwc_dpp_restriction.restriction.scale_up = drm_restriction.restriction.scale_up;

    /* scale ratio can't be 0 */
    if (hwc_dpp_restriction.restriction.scale_down == 0)
        hwc_dpp_restriction.restriction.scale_down = 1;
    if (hwc_dpp_restriction.restriction.scale_up == 0)
        hwc_dpp_restriction.restriction.scale_up = 1;
}

using namespace SOC_VERSION;

ExynosDeviceDrmInterface::ExynosDeviceDrmInterface(ExynosDevice *exynosDevice) {
    mType = INTERFACE_TYPE_DRM;
}

ExynosDeviceDrmInterface::~ExynosDeviceDrmInterface() {
    mDrmDevice->event_listener()->UnRegisterPropertyUpdateHandler(
            static_cast<DrmPropertyUpdateHandler*>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->UnRegisterHotplugHandler(
            static_cast<DrmEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->UnRegisterHistogramHandler(
            static_cast<DrmHistogramEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->UnRegisterHistogramChannelHandler(
            static_cast<DrmHistogramChannelEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->UnRegisterTUIHandler(
            static_cast<DrmTUIEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->UnRegisterPanelIdleHandler(
            static_cast<DrmPanelIdleEventHandler *>(&mExynosDrmEventHandler));
}

void ExynosDeviceDrmInterface::init(ExynosDevice *exynosDevice) {
    mUseQuery = false;
    mExynosDevice = exynosDevice;
    mDrmResourceManager.Init();
    mDrmDevice = mDrmResourceManager.GetDrmDevice(HWC_DISPLAY_PRIMARY);
    assert(mDrmDevice != NULL);

    updateRestrictions();

    mExynosDrmEventHandler.init(mExynosDevice, mDrmDevice);
    mDrmDevice->event_listener()->RegisterHotplugHandler(
            static_cast<DrmEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->RegisterHistogramHandler(
            static_cast<DrmHistogramEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->RegisterHistogramChannelHandler(
            static_cast<DrmHistogramChannelEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->RegisterTUIHandler(
            static_cast<DrmTUIEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->RegisterPanelIdleHandler(
            static_cast<DrmPanelIdleEventHandler *>(&mExynosDrmEventHandler));
    mDrmDevice->event_listener()->RegisterPropertyUpdateHandler(
            static_cast<DrmPropertyUpdateHandler*>(&mExynosDrmEventHandler));

    if (mDrmDevice->event_listener()->IsDrmInTUI()) {
        mExynosDevice->enterToTUI();
        ALOGD("%s:: device is already in TUI", __func__);
    }
}

void ExynosDeviceDrmInterface::postInit() {
    mDrmDevice->event_listener()->InitWorker();
}

int32_t ExynosDeviceDrmInterface::initDisplayInterface(
        std::unique_ptr<ExynosDisplayInterface> &dispInterface) {
    ExynosDisplayDrmInterface *displayInterface =
        static_cast<ExynosDisplayDrmInterface*>(dispInterface.get());
    return displayInterface->initDrmDevice(mDrmDevice);
}

void ExynosDeviceDrmInterface::updateRestrictions() {
    int32_t ret = 0;
    uint32_t channelId = 0;

    for (auto &plane : mDrmDevice->planes()) {
        struct hwc_dpp_ch_restriction hwc_res;

        /* Set size restriction information */
        if (plane->hw_restrictions_property().id()) {
            uint64_t blobId;

            std::tie(ret, blobId) = plane->hw_restrictions_property().value();
            if (ret)
                break;

            struct dpp_ch_restriction *res;
            drmModePropertyBlobPtr blob = drmModeGetPropertyBlob(mDrmDevice->fd(), blobId);
            if (!blob) {
                ALOGE("Fail to get blob for hw_restrictions(%" PRId64 ")", blobId);
                ret = HWC2_ERROR_UNSUPPORTED;
                break;
            }
            res = (struct dpp_ch_restriction *)blob->data;
            set_dpp_ch_restriction(hwc_res, *res);
            drmModeFreePropertyBlob(blob);
        } else {
            ALOGI("plane[%d] There is no hw restriction information", channelId);
            ret = HWC2_ERROR_UNSUPPORTED;
            break;
        }

        /* Set supported format information */
        for (auto format : plane->formats()) {
            std::vector<uint32_t> halFormats;
            if (drmFormatToHalFormats(format, &halFormats) != NO_ERROR) {
                ALOGE("Fail to convert drm format(%d)", format);
                continue;
            }
            for (auto halFormat : halFormats) {
                hwc_res.restriction.formats.push_back(halFormat);
            }
        }

        if (hwcCheckDebugMessages(eDebugDefault))
            printDppRestriction(hwc_res);

        if (plane->isFormatSupported(DRM_FORMAT_C8) && plane->getNumFormatSupported() == 1)
            mDPUInfo.dpuInfo.spp_chs.push_back(hwc_res);
        else
            mDPUInfo.dpuInfo.dpp_chs.push_back(hwc_res);

        channelId++;
    }

    DrmCrtc *drmCrtc = mDrmDevice->GetCrtcForDisplay(0);
    if (drmCrtc != nullptr) {
        /*
         * Run makeDPURestrictions() even if there is error
         * in getting the value
         */
        if (drmCrtc->ppc_property().id()) {
            auto [ret_ppc, value] = drmCrtc->ppc_property().value();
            if (ret_ppc < 0) {
                ALOGE("Failed to get ppc property");
            } else {
                mDPUInfo.dpuInfo.ppc = static_cast<uint32_t>(value);
            }
        }
        if (drmCrtc->max_disp_freq_property().id()) {
            auto [ret_max_freq, value] = drmCrtc->max_disp_freq_property().value();
            if (ret_max_freq < 0) {
                ALOGE("Failed to get max_disp_freq property");
            } else {
                mDPUInfo.dpuInfo.max_disp_freq = static_cast<uint32_t>(value);
            }
        }
    } else {
        ALOGE("%s:: Fail to get DrmCrtc", __func__);
    }

    if (ret != NO_ERROR) {
        ALOGI("Fail to get restriction (ret: %d)", ret);
        mUseQuery = false;
        return;
    }

    if ((ret = makeDPURestrictions()) != NO_ERROR) {
        ALOGE("makeDPURestrictions fail");
    } else if ((ret = updateFeatureTable()) != NO_ERROR) {
        ALOGE("updateFeatureTable fail");
    }

    if (ret == NO_ERROR)
        mUseQuery = true;
    else {
        ALOGI("There is no hw restriction information, use default values");
        mUseQuery = false;
    }
}

void ExynosDeviceDrmInterface::ExynosDrmEventHandler::init(ExynosDevice *exynosDevice,
                                                           DrmDevice *drmDevice) {
    mExynosDevice = exynosDevice;
    mDrmDevice = drmDevice;
}

void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleEvent(uint64_t timestamp_us) {
    mExynosDevice->handleHotplug();
}

void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleHistogramEvent(uint32_t crtc_id,
                                                                           void *bin) {
    ExynosDisplayDrmInterface *displayInterface;
    DrmProperty crtc;
    uint32_t id;

    for (auto display : mExynosDevice->mDisplays) {
        displayInterface =
                static_cast<ExynosDisplayDrmInterface *>(display->mDisplayInterface.get());
        id = displayInterface->getCrtcId();
        if (id == crtc_id) {
            displayInterface->setHistogramData(bin);
        }
    }
}

#if defined(EXYNOS_DRM_HISTOGRAM_CHANNEL_EVENT)
void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleHistogramChannelEvent(void *event) {
    struct exynos_drm_histogram_channel_event *histogram_channel_event =
            (struct exynos_drm_histogram_channel_event *)event;

    for (auto display : mExynosDevice->mDisplays) {
        ExynosDisplayDrmInterface *displayInterface =
                static_cast<ExynosDisplayDrmInterface *>(display->mDisplayInterface.get());
        if (histogram_channel_event->crtc_id == displayInterface->getCrtcId()) {
            if (display->mHistogramController) {
                display->mHistogramController->handleDrmEvent(histogram_channel_event);
            } else {
                ALOGE("%s: no valid mHistogramController for crtc_id (%u)", __func__,
                      histogram_channel_event->crtc_id);
            }

            return;
        }
    }

    ALOGE("%s: no display with crtc_id (%u)", __func__, histogram_channel_event->crtc_id);
}
#else
void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleHistogramChannelEvent(void *event) {}
#endif

void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleTUIEvent() {
    if (mDrmDevice->event_listener()->IsDrmInTUI()) {
        /* Received TUI Enter event */
        if (!mExynosDevice->isInTUI()) {
            mExynosDevice->enterToTUI();
            ALOGV("%s:: DRM device in TUI", __func__);
        }
    } else {
        /* Received TUI Exit event */
        if (mExynosDevice->isInTUI()) {
            mExynosDevice->onRefreshDisplays();
            mExynosDevice->exitFromTUI();
            ALOGV("%s:: DRM device out TUI", __func__);
        }
    }
}

constexpr size_t IDLE_ENTER_EVENT_DATA_SIZE = 3;
void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleIdleEnterEvent(char const *event) {
    /* PANEL_IDLE_ENTER=<display index>,<vrefresh>,<idle te vrefresh> */
    std::string_view idle_event_str(event);
    auto prefix_shift_pos = idle_event_str.find("=");
    if (prefix_shift_pos == std::string::npos) {
        ALOGE("%s: idle enter event format is incorrect", __func__);
    }

    int count = 0;
    int value[IDLE_ENTER_EVENT_DATA_SIZE] = {0};
    const auto &[displayIndex, vrefresh, idleTeVrefresh] = value;

    auto start_pos = prefix_shift_pos + 1;
    auto end_pos = idle_event_str.find(",", start_pos);
    while (end_pos != std::string::npos && count < IDLE_ENTER_EVENT_DATA_SIZE) {
        auto info = idle_event_str.substr(start_pos, end_pos - start_pos);

        value[count++] = atoi(info.data());
        start_pos = end_pos + 1;
        end_pos = idle_event_str.find(",", start_pos);
        if (end_pos == std::string::npos) {
            info = idle_event_str.substr(start_pos, idle_event_str.size() - start_pos);
            value[count++] = atoi(info.data());
        }
    }

    if (count != IDLE_ENTER_EVENT_DATA_SIZE) {
        ALOGE("%s: idle enter event is incomplete", __func__);
        return;
    }

    ExynosDisplay *primaryDisplay =
            mExynosDevice->getDisplay(getDisplayId(HWC_DISPLAY_PRIMARY, displayIndex));
    if (primaryDisplay) {
        /* sending vsyncIdle callback */
        if (vrefresh != idleTeVrefresh) {
            mExynosDevice->onVsyncIdle(primaryDisplay->getId());
        }

        primaryDisplay->handleDisplayIdleEnter(idleTeVrefresh);
    }
}

void ExynosDeviceDrmInterface::ExynosDrmEventHandler::handleDrmPropertyUpdate(unsigned connector_id,
                                                                              unsigned prop_id) {
    ALOGD("%s: connector_id=%u prop_id=%u", __func__, connector_id, prop_id);
    for (auto display : mExynosDevice->mDisplays) {
        ExynosDisplayDrmInterface* displayInterface =
                static_cast<ExynosDisplayDrmInterface*>(display->mDisplayInterface.get());
        if (displayInterface) displayInterface->handleDrmPropertyUpdate(connector_id, prop_id);
    }
}

int32_t ExynosDeviceDrmInterface::registerSysfsEventHandler(
        std::shared_ptr<DrmSysfsEventHandler> handler) {
    return mDrmDevice->event_listener()->RegisterSysfsHandler(std::move(handler));
}

int32_t ExynosDeviceDrmInterface::unregisterSysfsEventHandler(int sysfsFd) {
    return mDrmDevice->event_listener()->UnRegisterSysfsHandler(sysfsFd);
}

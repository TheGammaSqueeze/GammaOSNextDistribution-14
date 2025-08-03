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

#include <hardware/exynos/ion.h>
#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosPrimaryDisplayModule.h"

using namespace gs201;

/////////////////////////////////////////////////// ExynosDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosDisplayDrmInterfaceModule::ExynosDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
  : gs101::ExynosDisplayDrmInterfaceModule(exynosDisplay)
{
}

ExynosDisplayDrmInterfaceModule::~ExynosDisplayDrmInterfaceModule()
{
    for (auto p: mCGCDataInfos) {
        if (p.second != nullptr)
            munmap(p.second, sizeCgcDmaLut);

        if (p.first > 0)
            close(p.first);
    }
}

int32_t ExynosDisplayDrmInterfaceModule::initDrmDevice(DrmDevice *drmDevice)
{
    int ret = gs101::ExynosDisplayDrmInterfaceModule::initDrmDevice(drmDevice);
    if (ret != NO_ERROR)
        return ret;

    /* create file descriptors for CGC DMA */
    int32_t fd;
    struct cgc_dma_lut *buf;

    int ionFd = exynos_ion_open();
    if (ionFd >= 0) {
        while (mCGCDataInfos.size() < sizeCgCDataInfo) {
            fd = exynos_ion_alloc(ionFd, sizeCgcDmaLut, EXYNOS_ION_HEAP_SYSTEM_MASK, 0);
            if (fd >= 0) {
                buf = (struct cgc_dma_lut *)mmap(0, sizeCgcDmaLut, PROT_READ | PROT_WRITE,
                                                 MAP_SHARED, fd, 0);
                if (buf == nullptr) {
                    ALOGE("Failed to map buffer for CGC_DMA LUT");
                    close(fd);
                    ret = -ENOMEM;
                    break;
                } else {
                    memset(buf, 0, sizeCgcDmaLut);
                    mCGCDataInfos.emplace_back(CGCDataInfo(fd, buf));
                }
            } else {
                ALOGE("Failed to allocate ION for CGC_DMA LUT");
                ret = -ENOMEM;
                break;
            }
        }

        exynos_ion_close(ionFd);
    } else
        ALOGE("Failed to open ION for CGC_DMA LUT");

    return ret;
}

int32_t ExynosDisplayDrmInterfaceModule::createCgcDMAFromIDqe(
        const GsInterfaceType::IDqe::CgcData &cgcData)
{
    if ((cgcData.config->r_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (cgcData.config->g_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT) ||
        (cgcData.config->b_values.size() != DRM_SAMSUNG_CGC_LUT_REG_CNT)) {
        ALOGE("CGC data size is not same (r: %zu, g: %zu: b: %zu)",
                cgcData.config->r_values.size(),
                cgcData.config->g_values.size(),
                cgcData.config->b_values.size());
        return -EINVAL;
    }

    if (iCGCDataInfo >= mCGCDataInfos.size()) {
        ALOGE("CGC Data Infos is empty");
        return -EINVAL;
    }

    struct cgc_dma_lut *buf = mCGCDataInfos.at(iCGCDataInfo).second;
    uint32_t i = 0;
    for (; i < (DRM_SAMSUNG_CGC_LUT_REG_CNT - 1); i++) {
        buf[i * 2].r_value = (uint16_t)(cgcData.config->r_values[i]);
        buf[i * 2].g_value = (uint16_t)(cgcData.config->g_values[i]);
        buf[i * 2].b_value = (uint16_t)(cgcData.config->b_values[i]);
        buf[i * 2 + 1].r_value = (uint16_t)(cgcData.config->r_values[i] >> 16);
        buf[i * 2 + 1].g_value = (uint16_t)(cgcData.config->g_values[i] >> 16);
        buf[i * 2 + 1].b_value = (uint16_t)(cgcData.config->b_values[i] >> 16);
    }
    buf[i * 2].r_value = (uint16_t)cgcData.config->r_values[i];
    buf[i * 2].g_value = (uint16_t)cgcData.config->g_values[i];
    buf[i * 2].b_value = (uint16_t)cgcData.config->b_values[i];

    return mCGCDataInfos.at(iCGCDataInfo).first;
}

int32_t ExynosDisplayDrmInterfaceModule::setCgcLutDmaProperty(
        const DrmProperty &prop,
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq)
{
    if (!prop.id())
        return NO_ERROR;

    ExynosDeviceModule* device = static_cast<ExynosDeviceModule*>(mExynosDisplay->mDevice);
	gs101::ColorManager* colorManager = device->getDisplayColorManager(mExynosDisplay);
	if (!colorManager) return NO_ERROR;
    const GsInterfaceType::IDqe &dqe = colorManager->getDqe();
    const GsInterfaceType::IDqe::CgcData &cgcData = dqe.Cgc();

    /* dirty bit is valid only if enable is true */
    if (!mForceDisplayColorSetting && cgcData.enable && !cgcData.dirty)
        return NO_ERROR;

    int32_t ret = 0;
    int32_t cgcLutFd = disabledCgc;

    if (cgcData.enable) {
        if (cgcData.config == nullptr) {
            ALOGE("no CGC config");
            return NO_ERROR;
        }

        cgcLutFd = createCgcDMAFromIDqe(cgcData);
        if (cgcLutFd < 0) {
            HWC_LOGE(mExynosDisplay, "%s: create CGC DMA FD fail", __func__);
            return cgcLutFd;
        }

        iCGCDataInfo = (iCGCDataInfo + 1) % sizeCgCDataInfo;
    }

    /* CGC Disabled information should not be delivered at every frame */
    if (cgcLutFd == disabledCgc && !mCgcEnabled)
        return NO_ERROR;

    /* CGC setting when cgc is enabled and dirty */
    if ((ret = drmReq.atomicAddProperty(mDrmCrtc->id(), prop, cgcLutFd, true)) < 0) {
        HWC_LOGE(mExynosDisplay, "%s: Fail to set cgc_dma_fd property", __func__);
        return ret;
    }
    dqe.Cgc().NotifyDataApplied();

    mCgcEnabled = (cgcLutFd != disabledCgc);

    return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::setDisplayColorSetting(
        ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq)
{
    if (!mForceDisplayColorSetting && !mColorSettingChanged)
        return NO_ERROR;

    int32_t ret = gs101::ExynosDisplayDrmInterfaceModule::setDisplayColorSetting(drmReq);
    if (ret != NO_ERROR)
        return ret;

    return setCgcLutDmaProperty(mDrmCrtc->cgc_lut_fd_property(), drmReq);
}

int32_t ExynosDisplayDrmInterfaceModule::setHistoPosProperty(
    const DrmProperty &prop,
    ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) {
  if (!prop.id()) return NO_ERROR;

  int32_t ret = 0;

  if ((ret = drmReq.atomicAddProperty(
           mDrmCrtc->id(), prop, (uint64_t)mHistogramInfo->getHistogramPos(),
           true)) < 0) {
    HWC_LOGE(mExynosDisplay, "%s: Fail to set histogram position property",
             __func__);
    return ret;
  }

  return NO_ERROR;
}

int32_t ExynosDisplayDrmInterfaceModule::setDisplayHistogramSetting(
    ExynosDisplayDrmInterface::DrmModeAtomicReq &drmReq) {
  if ((isHistogramInfoRegistered() == false) || (isPrimary() == false)) return NO_ERROR;

  int32_t ret =
      gs101::ExynosDisplayDrmInterfaceModule::setDisplayHistogramSetting(
          drmReq);
  if (ret != NO_ERROR) return ret;

  ret = setHistoPosProperty(mDrmCrtc->histogram_position_property(), drmReq);

  return ret;
}

void ExynosDisplayDrmInterfaceModule::registerHistogramInfo(
        const std::shared_ptr<IDLHistogram> &info) {
  gs101::ExynosDisplayDrmInterfaceModule::registerHistogramInfo(info);
  mHistogramInfo = info;
}

//////////////////////////////////////////////////// ExynosPrimaryDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosPrimaryDisplayDrmInterfaceModule::ExynosPrimaryDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
  : ExynosDisplayDrmInterfaceModule(exynosDisplay)
{
}

ExynosPrimaryDisplayDrmInterfaceModule::~ExynosPrimaryDisplayDrmInterfaceModule()
{
}

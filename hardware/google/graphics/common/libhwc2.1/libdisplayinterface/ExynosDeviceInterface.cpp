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

#include "ExynosDeviceInterface.h"

#include <cutils/properties.h>

#include <unordered_map>
#include <unordered_set>

#include "ExynosDevice.h"
#include "ExynosHWCDebug.h"
#include "ExynosMPP.h"
#include "ExynosResourceManager.h"
#include "ExynosResourceRestriction.h"

using namespace SOC_VERSION;

void ExynosDeviceInterface::printDppRestriction(struct hwc_dpp_ch_restriction res)
{
    ALOGD("=========================================================");
    ALOGD("id: %d", res.id);
    ALOGD("hwc_dpp_restriction");
    ALOGD("src_f_w (%d, %d, %d)", res.restriction.src_f_w.min, res.restriction.src_f_w.max, res.restriction.src_f_w.align);
    ALOGD("src_f_h (%d, %d, %d)", res.restriction.src_f_h.min, res.restriction.src_f_h.max, res.restriction.src_f_h.align);
    ALOGD("src_w (%d, %d, %d)", res.restriction.src_w.min, res.restriction.src_w.max, res.restriction.src_w.align);
    ALOGD("src_h (%d, %d, %d)", res.restriction.src_h.min, res.restriction.src_h.max, res.restriction.src_h.align);
    ALOGD("src_x_align(%d), src_y_align(%d)", res.restriction.src_x_align, res.restriction.src_y_align);

    ALOGD("dst_f_w (%d, %d, %d)", res.restriction.dst_f_w.min, res.restriction.dst_f_w.max, res.restriction.dst_f_w.align);
    ALOGD("dst_f_h (%d, %d, %d)", res.restriction.dst_f_h.min, res.restriction.dst_f_h.max, res.restriction.dst_f_h.align);
    ALOGD("dst_w (%d, %d, %d)", res.restriction.dst_w.min, res.restriction.dst_w.max, res.restriction.dst_w.align);
    ALOGD("dst_h (%d, %d, %d)", res.restriction.dst_h.min, res.restriction.dst_h.max, res.restriction.dst_h.align);
    ALOGD("dst_x_align(%d), dst_y_align(%d)", res.restriction.dst_x_align, res.restriction.dst_y_align);
    ALOGD("src_h_rot_max(%d)", res.restriction.src_h_rot_max);

    ALOGD("blk_w (%d, %d, %d)", res.restriction.blk_w.min, res.restriction.blk_w.max, res.restriction.blk_w.align);
    ALOGD("blk_h (%d, %d, %d)", res.restriction.blk_h.min, res.restriction.blk_h.max, res.restriction.blk_h.align);
    ALOGD("blk_x_align(%d), blk_y_align(%d)", res.restriction.blk_x_align, res.restriction.blk_y_align);

    ALOGD("format cnt: %zu", res.restriction.formats.size());
    for(int i = 0; i < res.restriction.formats.size(); i++) {
        ALOGD("[%d] format: %d", i, res.restriction.formats[i]);
    }

    ALOGD("scale down: %d, up: %d", res.restriction.scale_down, res.restriction.scale_up);
    ALOGD("attr: 0x%lx", res.attr);
}

int32_t ExynosDeviceInterface::makeDPURestrictions() {
#ifdef DISABLE_READ_RESTRICTIONS
    return -EINVAL;
#endif
    int32_t ret = 0;

    struct hwc_dpp_restrictions_info *dpuInfo = &mDPUInfo.dpuInfo;
    HDEBUGLOGD(eDebugAttrSetting, "DPP ver : %d, cnt : %zu", dpuInfo->ver, dpuInfo->dpp_chs.size());
    ExynosResourceManager *resourceManager = mExynosDevice->mResourceManager;

    /* format resctriction */
    for (int i = 0; i < dpuInfo->dpp_chs.size(); i++){
        hwc_dpp_restriction r = dpuInfo->dpp_chs[i].restriction;
        HDEBUGLOGD(eDebugAttrSetting, "id : %d, format count : %zu", i, r.formats.size());
    }

    /* Check attribute overlap */
    std::unordered_set<unsigned long> attrs;
    for (size_t i = 0; i < dpuInfo->dpp_chs.size(); ++i) {
        const hwc_dpp_ch_restriction &r = dpuInfo->dpp_chs[i];
        if (attrs.find(r.attr) != attrs.end())
            mDPUInfo.overlap[i] = true;
        else
            attrs.insert(r.attr);
        HDEBUGLOGD(eDebugAttrSetting, "Index : %zu, overlap %d", i, mDPUInfo.overlap[i]);
    }

    for (int i = 0; i < dpuInfo->dpp_chs.size(); i++){
        if (mDPUInfo.overlap[i]) continue;
        hwc_dpp_restriction r = dpuInfo->dpp_chs[i].restriction;
        mpp_phycal_type_t hwType = resourceManager->getPhysicalType(i);
        for (auto &format: r.formats) {
            restriction_key_t queried_format;
            queried_format.hwType = hwType;
            queried_format.nodeType = NODE_NONE;
            /* format is HAL format */
            queried_format.format = format;
            queried_format.reserved = 0;
            resourceManager->makeFormatRestrictions(queried_format);
            HDEBUGLOGD(eDebugAttrSetting, "%s : %d", getMPPStr(hwType).c_str(), format);
        }
    }

    for (int i = 0; i < dpuInfo->dpp_chs.size(); i++){
        if (mDPUInfo.overlap[i]) continue;
        const hwc_dpp_restriction &r = dpuInfo->dpp_chs[i].restriction;

        /* RGB size restrictions */
        restriction_size rSize;
        rSize.maxDownScale = r.scale_down;
        rSize.maxUpScale = r.scale_up;
        rSize.maxFullWidth = r.dst_f_w.max;
        rSize.maxFullHeight = r.dst_f_h.max;
        rSize.minFullWidth = r.dst_f_w.min;
        rSize.minFullHeight = r.dst_f_h.min;;
        rSize.fullWidthAlign = r.dst_x_align;
        rSize.fullHeightAlign = r.dst_y_align;;
        rSize.maxCropWidth = r.src_w.max;
        rSize.maxCropHeight = r.src_h.max;
        rSize.minCropWidth = r.src_w.min;
        rSize.minCropHeight = r.src_h.min;
        rSize.cropXAlign = r.src_x_align;
        rSize.cropYAlign = r.src_y_align;
        rSize.cropWidthAlign = r.blk_x_align;
        rSize.cropHeightAlign = r.blk_y_align;

        mpp_phycal_type_t hwType = resourceManager->getPhysicalType(i);
        if (hwType <= MPP_DPP_NUM) {
            auto newMaxDownscale =
                    static_cast<unsigned int>(property_get_int32("vendor.hwc.dpp.downscale", 0));
            if (newMaxDownscale != 0) {
                rSize.maxDownScale = min(rSize.maxDownScale, newMaxDownscale);
                ALOGI("%s:: Apply DPP downscale restriction on 1/%d", __func__, rSize.maxDownScale);
            }
        }
        resourceManager->makeSizeRestrictions(hwType, rSize, RESTRICTION_RGB);

        /* YUV size restrictions */
        rSize.minCropWidth = 32; //r.src_w.min;
        rSize.minCropHeight = 32; //r.src_h.min;
        rSize.fullWidthAlign = max(r.dst_x_align, YUV_CHROMA_H_SUBSAMPLE);
        rSize.fullHeightAlign = max(r.dst_y_align, YUV_CHROMA_V_SUBSAMPLE);
        rSize.cropXAlign = max(r.src_x_align, YUV_CHROMA_H_SUBSAMPLE);
        rSize.cropYAlign = max(r.src_y_align, YUV_CHROMA_V_SUBSAMPLE);
        rSize.cropWidthAlign = max(r.blk_x_align, YUV_CHROMA_H_SUBSAMPLE);
        rSize.cropHeightAlign = max(r.blk_y_align, YUV_CHROMA_V_SUBSAMPLE);

        resourceManager->makeSizeRestrictions(hwType, rSize, RESTRICTION_YUV);
    }

    for (auto it: resourceManager->getOtfMPPs()) {
        if (mDPUInfo.dpuInfo.ppc)
            it->setPPC(static_cast<float>(mDPUInfo.dpuInfo.ppc));
        if (mDPUInfo.dpuInfo.max_disp_freq)
            it->setClockKhz(mDPUInfo.dpuInfo.max_disp_freq);
    }

    return ret;
}

int32_t ExynosDeviceInterface::updateFeatureTable() {
    const struct hwc_dpp_restrictions_info &dpuInfo = mDPUInfo.dpuInfo;
    if (mExynosDevice->mResourceManager == NULL)
        return -1;

    const ExynosResourceManager &resourceManager = *(mExynosDevice->mResourceManager);
    const uint32_t featureTableCnt = resourceManager.getFeatureTableSize();

    const int attrMapCnt = sizeof(dpu_attr_map_table)/sizeof(dpu_attr_map_t);
    const int dpp_cnt = dpuInfo.dpp_chs.size();

    HDEBUGLOGD(eDebugAttrSetting, "Before");
    for (uint32_t j = 0; j < featureTableCnt; j++){
        HDEBUGLOGD(eDebugAttrSetting, "type : %d, feature : 0x%lx",
                feature_table[j].hwType,
                (unsigned long)feature_table[j].attr);
    }

    // dpp count
    for (int i = 0; i < dpp_cnt; i++){
        hwc_dpp_ch_restriction c_r = dpuInfo.dpp_chs[i];
        if (mDPUInfo.overlap[i]) continue;
        HDEBUGLOGD(eDebugAttrSetting, "DPU attr : (ch:%d), 0x%lx", i, (unsigned long)c_r.attr);
        mpp_phycal_type_t hwType = resourceManager.getPhysicalType(i);
        // feature table count
        for (uint32_t j = 0; j < featureTableCnt; j++){
            if (feature_table[j].hwType == hwType) {
                uint64_t attr = 0;
                // dpp attr count
                for (int k = 0; k < attrMapCnt; k++) {
                    if (c_r.attr & (1 << dpu_attr_map_table[k].dpp_attr)) {
                        attr |= dpu_attr_map_table[k].hwc_attr;
                    }
                }
                auto it = sw_feature_table.find(hwType);
                if (it != sw_feature_table.end())
                    attr |= it->second;
                feature_table[j].attr = attr;
            }
        }
    }

    HDEBUGLOGD(eDebugAttrSetting, "After");
    for (uint32_t j = 0; j < featureTableCnt; j++){
        HDEBUGLOGD(eDebugAttrSetting, "type : %d, feature : 0x%lx",
                feature_table[j].hwType,
                (unsigned long)feature_table[j].attr);
        mExynosDevice->mResourceManager->mMPPAttrs.insert(std::make_pair((uint32_t)feature_table[j].hwType,
                    (uint64_t)feature_table[j].attr));
    }
    return 0;
}


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
#include "DisplaySceneInfo.h"

#include "ExynosLayer.h"

LayerColorData& DisplaySceneInfo::getLayerColorDataInstance(uint32_t index) {
    size_t currentSize = displayScene.layer_data.size();
    if (index >= currentSize) {
        displayScene.layer_data.resize(currentSize + 1);
        colorSettingChanged = true;
    }
    return displayScene.layer_data[index];
}

int32_t DisplaySceneInfo::setLayerDataMappingInfo(ExynosMPPSource* layer, uint32_t index) {
    if (layerDataMappingInfo.count(layer) != 0) {
        ALOGE("layer mapping is already inserted (layer: %p, index:%d)", layer, index);
        return -EINVAL;
    }
    // if assigned displaycolor dppIdx changes, do not reuse it (force plane color update).
    uint32_t oldPlaneId = prev_layerDataMappingInfo.count(layer) != 0 &&
                    prev_layerDataMappingInfo[layer].dppIdx == index
            ? prev_layerDataMappingInfo[layer].planeId
            : LayerMappingInfo::kPlaneIdNone;
    layerDataMappingInfo.insert(std::make_pair(layer, LayerMappingInfo{index, oldPlaneId}));

    return NO_ERROR;
}

void DisplaySceneInfo::setLayerDataspace(LayerColorData& layerColorData, hwc::Dataspace dataspace) {
    if (layerColorData.dataspace != dataspace) {
        colorSettingChanged = true;
        layerColorData.dataspace = dataspace;
    }
}

void DisplaySceneInfo::disableLayerHdrStaticMetadata(LayerColorData& layerColorData) {
    if (layerColorData.static_metadata.is_valid) {
        colorSettingChanged = true;
        layerColorData.static_metadata.is_valid = false;
    }
}

void DisplaySceneInfo::setLayerHdrStaticMetadata(LayerColorData& layerColorData,
                                                 const ExynosHdrStaticInfo& exynosHdrStaticInfo) {
    if (layerColorData.static_metadata.is_valid == false) {
        colorSettingChanged = true;
        layerColorData.static_metadata.is_valid = true;
    }

    updateInfoSingleVal(layerColorData.static_metadata.display_red_primary_x,
                        exynosHdrStaticInfo.sType1.mR.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_red_primary_y,
                        exynosHdrStaticInfo.sType1.mR.y);
    updateInfoSingleVal(layerColorData.static_metadata.display_green_primary_x,
                        exynosHdrStaticInfo.sType1.mG.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_green_primary_y,
                        exynosHdrStaticInfo.sType1.mG.y);
    updateInfoSingleVal(layerColorData.static_metadata.display_blue_primary_x,
                        exynosHdrStaticInfo.sType1.mB.x);
    updateInfoSingleVal(layerColorData.static_metadata.display_blue_primary_y,
                        exynosHdrStaticInfo.sType1.mB.y);
    updateInfoSingleVal(layerColorData.static_metadata.white_point_x,
                        exynosHdrStaticInfo.sType1.mW.x);
    updateInfoSingleVal(layerColorData.static_metadata.white_point_y,
                        exynosHdrStaticInfo.sType1.mW.y);
    updateInfoSingleVal(layerColorData.static_metadata.max_luminance,
                        exynosHdrStaticInfo.sType1.mMaxDisplayLuminance);
    updateInfoSingleVal(layerColorData.static_metadata.min_luminance,
                        exynosHdrStaticInfo.sType1.mMinDisplayLuminance);
    updateInfoSingleVal(layerColorData.static_metadata.max_content_light_level,
                        exynosHdrStaticInfo.sType1.mMaxContentLightLevel);
    updateInfoSingleVal(layerColorData.static_metadata.max_frame_average_light_level,
                        exynosHdrStaticInfo.sType1.mMaxFrameAverageLightLevel);
}

void DisplaySceneInfo::setLayerColorTransform(LayerColorData& layerColorData,
                                              std::array<float, TRANSFORM_MAT_SIZE>& matrix) {
    updateInfoSingleVal(layerColorData.matrix, matrix);
}

void DisplaySceneInfo::disableLayerHdrDynamicMetadata(LayerColorData& layerColorData) {
    if (layerColorData.dynamic_metadata.is_valid) {
        colorSettingChanged = true;
        layerColorData.dynamic_metadata.is_valid = false;
    }
}

void DisplaySceneInfo::setLayerHdrDynamicMetadata(
        LayerColorData& layerColorData, const ExynosHdrDynamicInfo& exynosHdrDynamicInfo) {
    if (layerColorData.dynamic_metadata.is_valid == false) {
        colorSettingChanged = true;
        layerColorData.dynamic_metadata.is_valid = true;
    }
    updateInfoSingleVal(layerColorData.dynamic_metadata.display_maximum_luminance,
                        exynosHdrDynamicInfo.data.targeted_system_display_maximum_luminance);

    if (!std::equal(layerColorData.dynamic_metadata.maxscl.begin(),
                    layerColorData.dynamic_metadata.maxscl.end(),
                    exynosHdrDynamicInfo.data.maxscl[0])) {
        colorSettingChanged = true;
        for (uint32_t i = 0; i < layerColorData.dynamic_metadata.maxscl.size(); i++) {
            layerColorData.dynamic_metadata.maxscl[i] = exynosHdrDynamicInfo.data.maxscl[0][i];
        }
    }
    static constexpr uint32_t DYNAMIC_META_DAT_SIZE = 15;

    updateInfoVectorVal(layerColorData.dynamic_metadata.maxrgb_percentages,
                        exynosHdrDynamicInfo.data.maxrgb_percentages[0], DYNAMIC_META_DAT_SIZE);
    updateInfoVectorVal(layerColorData.dynamic_metadata.maxrgb_percentiles,
                        exynosHdrDynamicInfo.data.maxrgb_percentiles[0], DYNAMIC_META_DAT_SIZE);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_flag,
                        exynosHdrDynamicInfo.data.tone_mapping.tone_mapping_flag[0]);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_knee_x,
                        exynosHdrDynamicInfo.data.tone_mapping.knee_point_x[0]);
    updateInfoSingleVal(layerColorData.dynamic_metadata.tm_knee_y,
                        exynosHdrDynamicInfo.data.tone_mapping.knee_point_y[0]);
    updateInfoVectorVal(layerColorData.dynamic_metadata.bezier_curve_anchors,
                        exynosHdrDynamicInfo.data.tone_mapping.bezier_curve_anchors[0],
                        DYNAMIC_META_DAT_SIZE);
}

int32_t DisplaySceneInfo::setClientCompositionColorData(
        const ExynosCompositionInfo& clientCompositionInfo, LayerColorData& layerData,
        float dimSdrRatio) {
    layerData.dim_ratio = 1.0f;
    setLayerDataspace(layerData, static_cast<hwc::Dataspace>(clientCompositionInfo.mDataSpace));
    disableLayerHdrStaticMetadata(layerData);
    disableLayerHdrDynamicMetadata(layerData);

    if (dimSdrRatio != 1.0) {
        std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix = {
            dimSdrRatio, 0.0, 0.0, 0.0,
            0.0, dimSdrRatio, 0.0, 0.0,
            0.0, 0.0, dimSdrRatio, 0.0,
            0.0, 0.0, 0.0, 1.0
        };
        setLayerColorTransform(layerData, scaleMatrix);
    } else {
        static std::array<float, TRANSFORM_MAT_SIZE> defaultMatrix {
            1.0, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,
            0.0, 0.0, 0.0, 1.0
        };
        setLayerColorTransform(layerData, defaultMatrix);
    }

    return NO_ERROR;
}

int32_t DisplaySceneInfo::setLayerColorData(LayerColorData& layerData, ExynosLayer* layer,
                                            float dimSdrRatio) {
    layerData.is_solid_color_layer = layer->isDimLayer();
    layerData.solid_color.r = layer->mColor.r;
    layerData.solid_color.g = layer->mColor.g;
    layerData.solid_color.b = layer->mColor.b;
    layerData.solid_color.a = layer->mColor.a;
    layerData.dim_ratio = layer->mPreprocessedInfo.sdrDimRatio;
    setLayerDataspace(layerData, static_cast<hwc::Dataspace>(layer->mDataSpace));
    if (layer->mIsHdrLayer && layer->getMetaParcel() != nullptr) {
        if (layer->getMetaParcel()->eType & VIDEO_INFO_TYPE_HDR_STATIC)
            setLayerHdrStaticMetadata(layerData, layer->getMetaParcel()->sHdrStaticInfo);
        else
            disableLayerHdrStaticMetadata(layerData);

        if (layer->getMetaParcel()->eType & VIDEO_INFO_TYPE_HDR_DYNAMIC)
            setLayerHdrDynamicMetadata(layerData, layer->getMetaParcel()->sHdrDynamicInfo);
        else
            disableLayerHdrDynamicMetadata(layerData);
    } else {
        disableLayerHdrStaticMetadata(layerData);
        disableLayerHdrDynamicMetadata(layerData);
    }

    static std::array<float, TRANSFORM_MAT_SIZE> defaultMatrix {
        1.0, 0.0, 0.0, 0.0,
        0.0, 1.0, 0.0, 0.0,
        0.0, 0.0, 1.0, 0.0,
        0.0, 0.0, 0.0, 1.0
    };

    if (dimSdrRatio == 1.0 || layer->mIsHdrLayer) {
        if (layer->mLayerColorTransform.enable)
            setLayerColorTransform(layerData, layer->mLayerColorTransform.mat);
        else
            setLayerColorTransform(layerData, defaultMatrix);
    } else {
        if (layer->mLayerColorTransform.enable) {
            std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix = layer->mLayerColorTransform.mat;

            // scale coeffs
            scaleMatrix[0] *= dimSdrRatio;
            scaleMatrix[1] *= dimSdrRatio;
            scaleMatrix[2] *= dimSdrRatio;
            scaleMatrix[4] *= dimSdrRatio;
            scaleMatrix[5] *= dimSdrRatio;
            scaleMatrix[6] *= dimSdrRatio;
            scaleMatrix[8] *= dimSdrRatio;
            scaleMatrix[9] *= dimSdrRatio;
            scaleMatrix[10] *= dimSdrRatio;

            // scale offsets
            scaleMatrix[12] *= dimSdrRatio;
            scaleMatrix[13] *= dimSdrRatio;
            scaleMatrix[14] *= dimSdrRatio;

            setLayerColorTransform(layerData, scaleMatrix);
        } else {
            std::array<float, TRANSFORM_MAT_SIZE> scaleMatrix = {
                dimSdrRatio, 0.0, 0.0, 0.0,
                0.0, dimSdrRatio, 0.0, 0.0,
                0.0, 0.0, dimSdrRatio, 0.0,
                0.0, 0.0, 0.0, 1.0
            };

            setLayerColorTransform(layerData, scaleMatrix);
        }
    }

    return NO_ERROR;
}

bool DisplaySceneInfo::needDisplayColorSetting() {
    /* TODO: Check if we can skip color setting */
    /* For now, propage setting every frame */
    return true;

    if (colorSettingChanged) return true;
    if (prev_layerDataMappingInfo != layerDataMappingInfo) return true;

    return false;
}

void DisplaySceneInfo::printDisplayScene() {
    ALOGD("======================= DisplayScene info ========================");
    ALOGD("dpu_bit_depth: %d", static_cast<uint32_t>(displayScene.dpu_bit_depth));
    ALOGD("color_mode: %d", static_cast<uint32_t>(displayScene.color_mode));
    ALOGD("render_intent: %d", static_cast<uint32_t>(displayScene.render_intent));
    ALOGD("matrix");
    for (uint32_t i = 0; i < 16; (i += 4)) {
        ALOGD("%f, %f, %f, %f", displayScene.matrix[i], displayScene.matrix[i + 1],
              displayScene.matrix[i + 2], displayScene.matrix[i + 3]);
    }
    ALOGD("layer: %zu ++++++", displayScene.layer_data.size());
    for (uint32_t i = 0; i < displayScene.layer_data.size(); i++) {
        ALOGD("layer[%d] info", i);
        printLayerColorData(displayScene.layer_data[i]);
    }

    ALOGD("layerDataMappingInfo: %zu ++++++", layerDataMappingInfo.size());
    for (auto layer : layerDataMappingInfo) {
        ALOGD("[layer: %p] [%d, %d]", layer.first, layer.second.dppIdx, layer.second.planeId);
    }
}

void DisplaySceneInfo::printLayerColorData(const LayerColorData& layerData) {
    ALOGD("dataspace: 0x%8x", static_cast<uint32_t>(layerData.dataspace));
    ALOGD("matrix");
    for (uint32_t i = 0; i < 16; (i += 4)) {
        ALOGD("%f, %f, %f, %f", layerData.matrix[i], layerData.matrix[i + 1],
              layerData.matrix[i + 2], layerData.matrix[i + 3]);
    }
    ALOGD("static_metadata.is_valid(%d)", layerData.static_metadata.is_valid);
    if (layerData.static_metadata.is_valid) {
        ALOGD("\tdisplay_red_primary(%d, %d)", layerData.static_metadata.display_red_primary_x,
              layerData.static_metadata.display_red_primary_y);
        ALOGD("\tdisplay_green_primary(%d, %d)", layerData.static_metadata.display_green_primary_x,
              layerData.static_metadata.display_green_primary_y);
        ALOGD("\tdisplay_blue_primary(%d, %d)", layerData.static_metadata.display_blue_primary_x,
              layerData.static_metadata.display_blue_primary_y);
        ALOGD("\twhite_point(%d, %d)", layerData.static_metadata.white_point_x,
              layerData.static_metadata.white_point_y);
    }
    ALOGD("dynamic_metadata.is_valid(%d)", layerData.dynamic_metadata.is_valid);
    if (layerData.dynamic_metadata.is_valid) {
        ALOGD("\tdisplay_maximum_luminance: %d",
              layerData.dynamic_metadata.display_maximum_luminance);
        ALOGD("\tmaxscl(%d, %d, %d)", layerData.dynamic_metadata.maxscl[0],
              layerData.dynamic_metadata.maxscl[1], layerData.dynamic_metadata.maxscl[2]);
        ALOGD("\ttm_flag(%d)", layerData.dynamic_metadata.tm_flag);
        ALOGD("\ttm_knee_x(%d)", layerData.dynamic_metadata.tm_knee_x);
        ALOGD("\ttm_knee_y(%d)", layerData.dynamic_metadata.tm_knee_y);
    }
}

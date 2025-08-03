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
#ifndef __DISPLAY_SCENE_INFO_H__
#define __DISPLAY_SCENE_INFO_H__

#include <displaycolor/displaycolor.h>
#include "ExynosHWCHelper.h"
#include "VendorVideoAPI.h"

using namespace displaycolor;

class ExynosCompositionInfo;
class ExynosLayer;
class ExynosMPPSource;

class DisplaySceneInfo {
public:
    struct LayerMappingInfo {
        bool operator==(const LayerMappingInfo& rhs) const {
            return ((dppIdx == rhs.dppIdx) && (planeId == rhs.planeId));
        }

        // index in DisplayScene::layer_data
        uint32_t dppIdx;
        // assigned drm plane id in last color setting update
        uint32_t planeId;
        static constexpr uint32_t kPlaneIdNone = std::numeric_limits<uint32_t>::max();
    };
    bool colorSettingChanged = false;
    bool displaySettingDelivered = false;
    DisplayScene displayScene;

    /*
     * Index of LayerColorData in DisplayScene::layer_data
     * and assigned plane id in last color setting update.
     * for each layer, including client composition
     * key: ExynosMPPSource*
     * data: LayerMappingInfo
     */
    std::map<ExynosMPPSource*, LayerMappingInfo> layerDataMappingInfo;
    std::map<ExynosMPPSource*, LayerMappingInfo> prev_layerDataMappingInfo;

    void reset() {
        colorSettingChanged = false;
        prev_layerDataMappingInfo = layerDataMappingInfo;
        layerDataMappingInfo.clear();
    };

    template <typename T, typename M>
    void updateInfoSingleVal(T& dst, M& src) {
        if (src != dst) {
            colorSettingChanged = true;
            dst = src;
        }
    };

    template <typename T, typename M>
    void updateInfoVectorVal(std::vector<T>& dst, M* src, uint32_t size) {
        if ((dst.size() != size) || !std::equal(dst.begin(), dst.end(), src)) {
            colorSettingChanged = true;
            dst.resize(size);
            for (uint32_t i = 0; i < size; i++) {
                dst[i] = src[i];
            }
        }
    };

    void setColorMode(hwc::ColorMode mode) { updateInfoSingleVal(displayScene.color_mode, mode); };

    void setRenderIntent(hwc::RenderIntent intent) {
        updateInfoSingleVal(displayScene.render_intent, intent);
    };

    void setColorTransform(const float* matrix) {
        for (uint32_t i = 0; i < displayScene.matrix.size(); i++) {
            if (displayScene.matrix[i] != matrix[i]) {
                colorSettingChanged = true;
                displayScene.matrix[i] = matrix[i];
            }
        }
    }

    LayerColorData& getLayerColorDataInstance(uint32_t index);
    int32_t setLayerDataMappingInfo(ExynosMPPSource* layer, uint32_t index);
    void setLayerDataspace(LayerColorData& layerColorData, hwc::Dataspace dataspace);
    void disableLayerHdrStaticMetadata(LayerColorData& layerColorData);
    void setLayerHdrStaticMetadata(LayerColorData& layerColorData,
                                   const ExynosHdrStaticInfo& exynosHdrStaticInfo);
    void setLayerColorTransform(LayerColorData& layerColorData,
                                std::array<float, TRANSFORM_MAT_SIZE>& matrix);
    void disableLayerHdrDynamicMetadata(LayerColorData& layerColorData);
    void setLayerHdrDynamicMetadata(LayerColorData& layerColorData,
                                    const ExynosHdrDynamicInfo& exynosHdrDynamicInfo);
    int32_t setLayerColorData(LayerColorData& layerData, ExynosLayer* layer, float dimSdrRatio);
    int32_t setClientCompositionColorData(const ExynosCompositionInfo& clientCompositionInfo,
                                          LayerColorData& layerData, float dimSdrRatio);
    bool needDisplayColorSetting();
    void printDisplayScene();
    void printLayerColorData(const LayerColorData& layerData);
};

#endif // __DISPLAY_SCENE_INFO_H__

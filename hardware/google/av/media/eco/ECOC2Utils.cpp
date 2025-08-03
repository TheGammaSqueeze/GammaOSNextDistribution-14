/*
 * Copyright (C) 2020 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "ECOC2Utils"
#include "eco/ECOC2Utils.h"

namespace android {
namespace media {
namespace eco {

int C2MediaType2ECOCodecType(std::shared_ptr<C2PortMediaTypeSetting::output> mediatype) {
    std::string_view mime = mediatype->m.value;
    if (!mime.compare("video/avc")) {
        return CodecTypeAVC;
    } else if (!mime.compare("video/hevc")) {
        return CodecTypeHEVC;
    }
    return CodecTypeUnknown;
}

int C2Profile2ECOProfile(C2Config::profile_t profile) {
    switch (profile) {
        case PROFILE_AVC_BASELINE:
            return AVCProfileBaseline;
        case PROFILE_AVC_CONSTRAINED_BASELINE:
            return AVCProfileConstrainedBaseline;
        case PROFILE_AVC_MAIN:
            return AVCProfileMain;
        case PROFILE_AVC_HIGH:
            return AVCProfileHigh;
        case PROFILE_HEVC_MAIN:
            return HEVCProfileMain;
        default:
            return 0;
    }
}

int C2Level2ECOLevel(C2Config::level_t level) {
    switch (level) {
        case LEVEL_AVC_1:
            return AVCLevel1;
        case LEVEL_AVC_1B:
            return AVCLevel1b;
        case LEVEL_AVC_1_1:
            return AVCLevel11;
        case LEVEL_AVC_1_2:
            return AVCLevel12;
        case LEVEL_AVC_1_3:
            return AVCLevel13;
        case LEVEL_AVC_2:
            return AVCLevel2;
        case LEVEL_AVC_2_1:
            return AVCLevel21;
        case LEVEL_AVC_2_2:
            return AVCLevel22;
        case LEVEL_AVC_3:
            return AVCLevel3;
        case LEVEL_AVC_3_1:
            return AVCLevel31;
        case LEVEL_AVC_3_2:
            return AVCLevel32;
        case LEVEL_AVC_4 :
            return AVCLevel4;
        case LEVEL_AVC_4_1:
            return AVCLevel41;
        case LEVEL_AVC_5:
            return AVCLevel5;
        case LEVEL_AVC_5_1:
            return AVCLevel51;
        case LEVEL_AVC_5_2:
            return AVCLevel52;
        case LEVEL_AVC_6:
            return AVCLevel6;
        case LEVEL_AVC_6_1:
            return AVCLevel61;
        case LEVEL_AVC_6_2:
            return AVCLevel62;
        case LEVEL_HEVC_MAIN_1:
            return HEVCMainTierLevel1;
        case LEVEL_HEVC_MAIN_2:
            return HEVCMainTierLevel2;
        case LEVEL_HEVC_MAIN_2_1:
            return HEVCMainTierLevel21;
        case LEVEL_HEVC_MAIN_3:
            return HEVCMainTierLevel3;
        case LEVEL_HEVC_MAIN_3_1:
            return HEVCMainTierLevel31;
        case LEVEL_HEVC_MAIN_4:
            return HEVCMainTierLevel4;
        case LEVEL_HEVC_MAIN_4_1:
            return HEVCMainTierLevel41;
        case LEVEL_HEVC_MAIN_5:
            return HEVCMainTierLevel5;
        case LEVEL_HEVC_MAIN_5_1:
            return HEVCMainTierLevel51;
        case LEVEL_HEVC_MAIN_5_2:
            return HEVCMainTierLevel52;
        case LEVEL_HEVC_MAIN_6:
            return HEVCMainTierLevel6;
        case LEVEL_HEVC_MAIN_6_1:
            return HEVCMainTierLevel61;
        case LEVEL_HEVC_MAIN_6_2:
            return HEVCMainTierLevel62;
        case LEVEL_HEVC_HIGH_4:
            return HEVCHighTierLevel4;
        case LEVEL_HEVC_HIGH_4_1:
            return HEVCHighTierLevel41;
        case LEVEL_HEVC_HIGH_5 :
            return HEVCHighTierLevel5;
        case LEVEL_HEVC_HIGH_5_1:
            return HEVCHighTierLevel51;
        case LEVEL_HEVC_HIGH_5_2:
            return HEVCHighTierLevel52;
        case LEVEL_HEVC_HIGH_6:
            return HEVCHighTierLevel6;
        case LEVEL_HEVC_HIGH_6_1:
            return HEVCHighTierLevel61;
        case LEVEL_HEVC_HIGH_6_2:
            return HEVCHighTierLevel62;
        default:
            return 0;
    }
}

int C2PictureType2ECOFrameType(C2Config::picture_type_t frametype) {
    switch (frametype) {
        case I_FRAME:
            return FrameTypeI;
        case P_FRAME:
            return FrameTypeP;
        case B_FRAME:
            return FrameTypeB;
        default:
            return FrameTypeUnknown;
    }
}

}  // namespace eco
}  // namespace media
}  // namespace android

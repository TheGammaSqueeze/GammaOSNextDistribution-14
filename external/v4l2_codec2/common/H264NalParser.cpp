// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "NalParser"

#include <v4l2_codec2/common/H264NalParser.h>

#include <media/stagefright/foundation/ABitReader.h>
#include <utils/Log.h>

namespace android {

namespace {

enum H264ProfileIDC {
    kProfileIDCAVLC444 = 44,
    kProfileIDScalableBaseline = 83,
    kProfileIDScalableHigh = 86,
    kProfileIDCHigh = 100,
    kProfileIDHigh10 = 110,
    kProfileIDSMultiviewHigh = 118,
    kProfileIDHigh422 = 122,
    kProfileIDStereoHigh = 128,
    kProfileIDHigh444Predictive = 244,
};

constexpr uint32_t kYUV444Idc = 3;

// Skip a H.264 sequence scaling list in the specified bitstream.
bool skipScalingList(ABitReader* br, size_t scalingListSize) {
    size_t nextScale = 8;
    size_t lastScale = 8;
    for (size_t j = 0; j < scalingListSize; ++j) {
        if (nextScale != 0) {
            int32_t deltaScale;
            if (!NalParser::parseSE(br, &deltaScale)) return false;  // delta_sl
            if (deltaScale < -128) {
                ALOGW("delta scale (%d) is below range, capping to -128", deltaScale);
                deltaScale = -128;
            } else if (deltaScale > 127) {
                ALOGW("delta scale (%d) is above range, capping to 127", deltaScale);
                deltaScale = 127;
            }
            nextScale = (lastScale + (deltaScale + 256)) % 256;
        }
        lastScale = (nextScale == 0) ? lastScale : nextScale;
    }
    return true;
}

// Skip the H.264 sequence scaling matrix in the specified bitstream.
bool skipScalingMatrix(ABitReader* br, size_t numScalingLists) {
    for (size_t i = 0; i < numScalingLists; ++i) {
        uint32_t seq_scaling_list_present_flag;
        if (!br->getBitsGraceful(1, &seq_scaling_list_present_flag))
            return false;  // seq_scaling_list_present_flag
        if (seq_scaling_list_present_flag) {
            if (i < 6) {
                if (!skipScalingList(br, 16)) return false;
            } else {
                if (!skipScalingList(br, 64)) return false;
            }
        }
    }
    return true;
}

}  // namespace

H264NalParser::H264NalParser(const uint8_t* data, size_t length) : NalParser(data, length) {}

bool H264NalParser::locateSPS() {
    while (locateNextNal()) {
        if (length() == 0) continue;
        if (type() != kSPSType) continue;
        return true;
    }

    return false;
}

bool H264NalParser::locateIDR() {
    while (locateNextNal()) {
        if (length() == 0) continue;
        if (type() != kIDRType) continue;
        return true;
    }

    return false;
}

uint8_t H264NalParser::type() const {
    // First byte is forbidden_zero_bit (1) + nal_ref_idc (2) + nal_unit_type (5)
    constexpr uint8_t kNALTypeMask = 0x1f;
    return *mCurrNalDataPos & kNALTypeMask;
}

bool H264NalParser::findCodedColorAspects(ColorAspects* colorAspects) {
    ALOG_ASSERT(colorAspects);
    ALOG_ASSERT(type() == kSPSType);

    // Unfortunately we can't directly jump to the Video Usability Information (VUI) parameters that
    // contain the color aspects. We need to parse the entire SPS header up until the values we
    // need.

    // Skip first byte containing type.
    NALBitReader br(mCurrNalDataPos + 1, length() - 1);

    uint32_t unused;
    uint32_t profileIDC;
    if (!br.getBitsGraceful(8, &profileIDC)) return false;  // profile_idc
    br.skipBits(16);        // constraint flags + reserved bits + level_idc
    parseUE(&br, &unused);  // seq_parameter_set_id

    if (profileIDC == kProfileIDCHigh || profileIDC == kProfileIDHigh10 ||
        profileIDC == kProfileIDHigh422 || profileIDC == kProfileIDHigh444Predictive ||
        profileIDC == kProfileIDCAVLC444 || profileIDC == kProfileIDScalableBaseline ||
        profileIDC == kProfileIDScalableHigh || profileIDC == kProfileIDSMultiviewHigh ||
        profileIDC == kProfileIDStereoHigh) {
        uint32_t chromaFormatIDC;
        if (!parseUE(&br, &chromaFormatIDC)) return false;
        if (chromaFormatIDC == kYUV444Idc) {  // chroma_format_idc
            br.skipBits(1);                   // separate_colour_plane_flag
        }

        parseUE(&br, &unused);  // bit_depth_luma_minus8
        parseUE(&br, &unused);  // bit_depth_chroma_minus8
        br.skipBits(1);         // lossless_qpprime_y_zero_flag

        uint32_t seqScalingMatrixPresentFlag;
        if (!br.getBitsGraceful(1, &seqScalingMatrixPresentFlag))
            return false;  // seq_scaling_matrix_present_flag
        if (seqScalingMatrixPresentFlag) {
            const size_t numScalingLists = (chromaFormatIDC != kYUV444Idc) ? 8 : 12;
            if (!skipScalingMatrix(&br, numScalingLists)) return false;
        }
    }

    parseUE(&br, &unused);  // log2_max_frame_num_minus4
    uint32_t pictureOrderCountType;
    if (!parseUE(&br, &pictureOrderCountType)) return false;  // pic_order_cnt_type
    if (pictureOrderCountType == 0) {
        parseUE(&br, &unused);  // log2_max_pic_order_cnt_lsb_minus4
    } else if (pictureOrderCountType == 1) {
        br.skipBits(1);  // delta_pic_order_always_zero_flag
        int32_t unused_i;
        parseSE(&br, &unused_i);  // offset_for_non_ref_pic
        parseSE(&br, &unused_i);  // offset_for_top_to_bottom_field
        uint32_t numReferenceFrames;
        if (!parseUE(&br, &numReferenceFrames))
            return false;  // num_ref_frames_in_pic_order_cnt_cycle
        for (uint32_t i = 0; i < numReferenceFrames; ++i) {
            parseUE(&br, &unused);  // offset_for_ref_frame
        }
    }

    parseUE(&br, &unused);  // num_ref_frames
    br.skipBits(1);         // gaps_in_frame_num_value_allowed_flag
    parseUE(&br, &unused);  // pic_width_in_mbs_minus1
    parseUE(&br, &unused);  // pic_height_in_map_units_minus1
    uint32_t frameMbsOnlyFlag;
    if (!br.getBitsGraceful(1, &frameMbsOnlyFlag)) return false;  // frame_mbs_only_flag
    if (!frameMbsOnlyFlag) {
        br.skipBits(1);  // mb_adaptive_frame_field_flag
    }
    br.skipBits(1);  // direct_8x8_inference_flag

    uint32_t frameCroppingFlag;
    if (!br.getBitsGraceful(1, &frameCroppingFlag)) return false;  // frame_cropping_flag
    if (frameCroppingFlag) {
        parseUE(&br, &unused);  // frame_cropping_rect_left_offset
        parseUE(&br, &unused);  // frame_cropping_rect_right_offset
        parseUE(&br, &unused);  // frame_cropping_rect_top_offset
        parseUE(&br, &unused);  // frame_cropping_rect_bottom_offset
    }

    uint32_t vuiParametersPresentFlag;
    if (!br.getBitsGraceful(1, &vuiParametersPresentFlag))
        return false;  // vui_parameters_present_flag
    if (vuiParametersPresentFlag) {
        uint32_t aspectRatioInfoPresentFlag;
        if (!br.getBitsGraceful(1, &aspectRatioInfoPresentFlag))
            return false;  // VUI aspect_ratio_info_present_flag
        if (aspectRatioInfoPresentFlag) {
            uint32_t aspectRatioIdc;
            if (!br.getBitsGraceful(8, &aspectRatioIdc)) return false;  // VUI aspect_ratio_idc
            if (aspectRatioIdc == 255) {  // VUI aspect_ratio_idc == extended sample aspect ratio
                br.skipBits(32);          // VUI sar_width + sar_height
            }
        }

        uint32_t overscanInfoPresentFlag;
        if (!br.getBitsGraceful(1, &overscanInfoPresentFlag))
            return false;  // VUI overscan_info_present_flag
        if (overscanInfoPresentFlag) {
            br.skipBits(1);  // VUI overscan_appropriate_flag
        }
        uint32_t videoSignalTypePresentFlag;
        if (!br.getBitsGraceful(1, &videoSignalTypePresentFlag))
            return false;  // VUI video_signal_type_present_flag
        if (videoSignalTypePresentFlag) {
            br.skipBits(3);  // VUI video_format
            uint32_t videoFullRangeFlag;
            if (!br.getBitsGraceful(1, &videoFullRangeFlag))
                return false;  // VUI videoFullRangeFlag
            colorAspects->fullRange = videoFullRangeFlag;
            uint32_t color_description_present_flag;
            if (!br.getBitsGraceful(1, &color_description_present_flag))
                return false;  // VUI color_description_present_flag
            if (color_description_present_flag) {
                if (!br.getBitsGraceful(8, &colorAspects->primaries))
                    return false;  // VUI colour_primaries
                if (!br.getBitsGraceful(8, &colorAspects->transfer))
                    return false;  // VUI transfer_characteristics
                if (!br.getBitsGraceful(8, &colorAspects->coeffs))
                    return false;  // VUI matrix_coefficients
                return true;
            }
        }
    }

    return false;  // The NAL unit doesn't contain color aspects info.
}

}  // namespace android

/**
 * Copyright (C) 2022 The Android Open Source Project
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

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaBitReader.h>
#include <ImsMediaBinaryFormat.h>
#include <ImsMediaTrace.h>
#include <VideoConfig.h>
#include <memory>
#include <string.h>

#define START_CODE_PREFIX_LEN           4
#define MAX_OUTPUT_BUFFER_READ_ATTEMPTS 5
#define FRAME_TYPE_SPS                  7
#define FRAME_TYPE_PPS                  8

ImsMediaVideoUtil::ImsMediaVideoUtil() {}

ImsMediaVideoUtil::~ImsMediaVideoUtil() {}

int32_t ImsMediaVideoUtil::ConvertCodecType(int32_t type)
{
    switch (type)
    {
        default:
        case VideoConfig::CODEC_AVC:
            return kVideoCodecAvc;
        case VideoConfig::CODEC_HEVC:
            return kVideoCodecHevc;
    }
}

uint32_t ImsMediaVideoUtil::GetResolutionFromSize(uint32_t nWidth, uint32_t nHeight)
{
    if (nWidth == 128 && nHeight == 96)
        return kVideoResolutionSqcifLandscape;
    else if (nWidth == 96 && nHeight == 128)
        return kVideoResolutionSqcifPortrait;
    else if (nWidth == 480 && nHeight == 640)
        return kVideoResolutionVgaPortrait;
    else if (nWidth == 640 && nHeight == 480)
        return kVideoResolutionVgaLandscape;
    else if (nWidth == 240 && nHeight == 320)
        return kVideoResolutionQvgaPortrait;
    else if (nWidth == 320 && nHeight == 240)
        return kVideoResolutionQvgaLandscape;
    else if (nWidth == 144 && nHeight == 176)
        return kVideoResolutionQcifPortrait;
    else if (nWidth == 176 && nHeight == 144)
        return kVideoResolutionQcifLandscape;
    else if (nWidth == 352 && nHeight == 288)
        return kVideoResolutionCifLandscape;
    else if (nWidth == 288 && nHeight == 352)
        return kVideoResolutionCifPortrait;
    else if (nWidth == 240 && nHeight == 352)
        return kVideoResolutionSifPortrait;
    else if (nWidth == 352 && nHeight == 240)
        return kVideoResolutionSifLandscape;
    else if (nWidth == 1280 && nHeight == 720)
        return kVideoResolutionHdLandscape;
    else if (nWidth == 720 && nHeight == 1280)
        return kVideoResolutionHdPortrait;
    else
        return kVideoResolutionInvalid;
}

ImsMediaResult ImsMediaVideoUtil::ParseAvcSpropParam(const char* szSpropparam, tCodecConfig* pInfo)
{
    ImsMediaBitReader bitreader;
    bool ret = false;
    uint8_t pbSPSConfig[MAX_CONFIG_LEN] = {'\0'};
    char pSPSConfig[MAX_CONFIG_LEN] = {'\0'};
    uint32_t nSPSConfigSize = 0;
    uint32_t chroma_format_idc = 0;

    memset(pSPSConfig, 0x00, MAX_CONFIG_LEN);

    for (int32_t i = 0; i < MAX_CONFIG_LEN; i++)
    {
        uint8_t Comma = ',';
        char cmpConfig = *(szSpropparam + i);
        if (Comma == cmpConfig)
        {
            memset(pSPSConfig, 0x00, MAX_CONFIG_LEN);
            memcpy(pSPSConfig, szSpropparam, i);
        }
    }

    ret = ImsMediaBinaryFormat::Base00ToBinary(
            pbSPSConfig, &nSPSConfigSize, MAX_CONFIG_LEN, pSPSConfig, BINARY_FORMAT_BASE64);

    if (ret == false)
    {
        IMLOGW0("[ParseAvcSpropParam] sps convert fail");
        return RESULT_INVALID_PARAM;
    }

    uint8_t* pszSpropparam = reinterpret_cast<uint8_t*>(malloc(nSPSConfigSize));

    if (pszSpropparam == nullptr)
    {
        pInfo->nProfile = 0;
        pInfo->nLevel = 0;
        pInfo->nHeight = 0;
        pInfo->nWidth = 0;

        return RESULT_NO_MEMORY;
    }

    memcpy(pszSpropparam, pbSPSConfig, nSPSConfigSize);

    bitreader.SetBuffer(pszSpropparam, nSPSConfigSize);
    bitreader.Read(8);

    uint32_t Profile_idc = bitreader.Read(8);  // Read profile_idc

    // Read constraint
    bitreader.Read(1);  // Constraint_set0
    bitreader.Read(1);  // Constraint_set1
    bitreader.Read(1);  // Constraint_set2
    bitreader.Read(1);  // Constraint_set3
    bitreader.Read(1);  // Constraint_set4
    bitreader.Read(3);  // Read reserved_zeor_3bits

    uint32_t Level_idc = bitreader.Read(8);  // Read level_idc
    bitreader.ReadByUEMode();                // Read Seq_parameter_set_id

    if (Profile_idc == 100 || Profile_idc == 11 || Profile_idc == 122 || Profile_idc == 244 ||
            Profile_idc == 44 || Profile_idc == 83 || Profile_idc == 86 || Profile_idc == 118)
    {
        chroma_format_idc = bitreader.ReadByUEMode();

        if (chroma_format_idc == 3)
        {
            bitreader.Read(1);  // Separate_colour_plane
        }

        bitreader.ReadByUEMode();  // Read bit_depth_luma_minus8
        bitreader.ReadByUEMode();  // Read bit_depth_chroma_minus8
        bitreader.Read(1);         // Read qpprime_y_zero_transformypass

        // Read seq_scaling_matrix_present
        if (bitreader.Read(1))
        {
            uint32_t i = ((chroma_format_idc != 3) ? 8 : 12);
            // Set scaling_list... not implement
            bitreader.Read(i);
        }
    }

    pInfo->nProfile = Profile_idc;
    pInfo->nLevel = Level_idc;

    bitreader.ReadByUEMode();                                // Read log2_max_frame_num_minus4
    uint32_t pic_order_cnt_type = bitreader.ReadByUEMode();  // Read pic_order_cnt_type

    if (pic_order_cnt_type == 0)
    {
        bitreader.ReadByUEMode();  // Read log2_max_pic_order_cnt_lsb_minus4
    }
    else if (pic_order_cnt_type == 1)
    {
        bitreader.Read(1);         // Read delta_pic_order_always_zero_flag
        bitreader.ReadByUEMode();  // Reda offset_for_non_ref_pic...
        bitreader.ReadByUEMode();  // Read offset_for_top_to_bottom_field
        uint32_t num_ref_frames_in_pic_order_cnt_cycle =
                bitreader.ReadByUEMode();  // Read num_ref_frames_in_pic_order_cnt_cycle
        for (int32_t i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++)
        {
            bitreader.ReadByUEMode();  // Read offset_for_ref_frame[i];
        }
    }

    bitreader.ReadByUEMode();  // Read max_num_ref_frames
    bitreader.Read(1);         // Read gaps_in_frame_num_value_allowed

    uint32_t pic_width_in_mbs_minus1 = bitreader.ReadByUEMode();  // Read pic_width_in_mbs_minus1
    uint32_t pic_height_in_map_units_minus1 =
            bitreader.ReadByUEMode();  // Read pic_height_in_map_units_minus1
    uint32_t frame_mbs_only = bitreader.Read(1);

    pInfo->nWidth = pic_width_in_mbs_minus1 * 16 + 16;
    pInfo->nHeight = (2 - frame_mbs_only) * (pic_height_in_map_units_minus1 * 16 + 16);

    if (!frame_mbs_only)
    {
        bitreader.Read(1);  // Read mb_adaptive_frame_field
    }

    bitreader.Read(1);                            // Read direct_8x8_inferencce_flag
    uint32_t frame_cropping = bitreader.Read(1);  // Read frame_cropping_flag

    if (frame_cropping)
    {
        uint32_t frame_crop_left_offset = bitreader.ReadByUEMode();   // Read frame_crop_left_offset
        uint32_t frame_crop_right_offset = bitreader.ReadByUEMode();  // Read
                                                                      // frame_crop_right_offset
        uint32_t frame_crop_top_offset = bitreader.ReadByUEMode();    // Read frame_crop_top_offset
        uint32_t frame_crop_bottom_offset =
                bitreader.ReadByUEMode();  // Read frame_crop_bottom_offset
        uint32_t cropX, cropY = 0;

        if (chroma_format_idc == 0 /* Monochrome */)
        {
            cropX = 1;
            cropY = 2 - frame_mbs_only;
        }
        else
        {
            uint32_t subWidthC = (chroma_format_idc == 3) ? 1 : 2;
            uint32_t subHeightC = (chroma_format_idc == 1) ? 2 : 1;
            cropX = subWidthC;
            cropY = subHeightC * (2 - frame_mbs_only);
        }

        pInfo->nWidth -= (frame_crop_left_offset + frame_crop_right_offset) * cropX;
        pInfo->nHeight -= (frame_crop_top_offset + frame_crop_bottom_offset) * cropY;
    }

    free(pszSpropparam);

    IMLOGD4("[ParseAvcSpropParam] width[%d],height[%d],nProfile[%d],nLevel[%d]", pInfo->nWidth,
            pInfo->nHeight, pInfo->nProfile, pInfo->nLevel);

    return RESULT_SUCCESS;
}

ImsMediaResult ImsMediaVideoUtil::ParseHevcSpropParam(const char* szSpropparam, tCodecConfig* pInfo)
{
    uint32_t nSize = strlen(szSpropparam);

    if (nSize == 0)
    {
        return RESULT_INVALID_PARAM;
    }

    IMLOGD1("[ParseHevcSpropParam] szSpropparam[%s]", szSpropparam);

    uint8_t pbSPSConfig[MAX_CONFIG_LEN] = {'\0'};
    char pSPSConfig[MAX_CONFIG_LEN] = {'\0'};
    uint32_t nSPSConfigSize = 0;

    memset(pSPSConfig, 0x00, MAX_CONFIG_LEN);
    strlcpy(pSPSConfig, szSpropparam, MAX_CONFIG_LEN);

    uint8_t* pszSpropparam = nullptr;

    if (ImsMediaBinaryFormat::Base00ToBinary(pbSPSConfig, &nSPSConfigSize, MAX_CONFIG_LEN,
                pSPSConfig, BINARY_FORMAT_BASE64) == false)
    {
        IMLOGW0("[ParseAvcSpropParam] sps convert fail");
        return RESULT_INVALID_PARAM;
    }

    if (nSPSConfigSize == 0)
        return RESULT_INVALID_PARAM;

    pszSpropparam = reinterpret_cast<uint8_t*>(malloc(nSPSConfigSize));

    if (pszSpropparam == nullptr)
    {
        pInfo->nProfile = 0;
        pInfo->nLevel = 0;
        pInfo->nHeight = 0;
        pInfo->nWidth = 0;

        return RESULT_INVALID_PARAM;
    }

    memcpy(pszSpropparam, pbSPSConfig, nSPSConfigSize);

    // Check binary
    ImsMediaTrace::IMLOGD_BINARY("[ParseHevcSpropParam] sps=",
            reinterpret_cast<const char*>(pszSpropparam), nSPSConfigSize);

    uint32_t nOffset = 0;

    for (int32_t i = 0; i < nSPSConfigSize - 6; i++)
    {
        // NAL unit header offset
        if (pszSpropparam[i] == 0x00 && pszSpropparam[i + 1] == 0x00 &&
                pszSpropparam[i + 2] == 0x00 && pszSpropparam[i + 3] == 0x01 &&
                pszSpropparam[i + 4] == 0x42 && pszSpropparam[i + 5] == 0x01)
        {
            nOffset = i + 6;
            break;
        }
    }

    IMLOGD2("[ParseHevcSpropParam] nSPSConfigSize[%d], offset[%d]", nSPSConfigSize, nOffset);

    ImsMediaBitReader objBitReader;
    objBitReader.SetBuffer(pszSpropparam + nOffset, nSPSConfigSize - nOffset);

    objBitReader.Read(4);  // sps_video_parameter_set_id;
    uint32_t sps_max_sub_layers_minus1 = objBitReader.Read(3);

    IMLOGD1("[ParseHevcSpropParam] sps_max_sub_layers_minus1[%d]", sps_max_sub_layers_minus1);

    objBitReader.Read(1);  // sps_temporal_id_nesting_flag;

    /*-----------profile_tier_level start-----------------------*/
    objBitReader.Read(3);  // general_profile_spac, general_tier_flag
    uint32_t general_profile_idc = objBitReader.Read(5);

    IMLOGD1("[ParseHevcSpropParam] general_profile_idc[%d]", general_profile_idc);

    // skip 13byte - flags, not handle
    objBitReader.Read(24);
    objBitReader.Read(24);
    objBitReader.Read(24);
    objBitReader.Read(24);
    objBitReader.Read(8);

    uint32_t general_level_idc = objBitReader.Read(8);

    IMLOGD1("[ParseHevcSpropParam] general_level_idc[%d]", general_level_idc);

    uint8_t sub_layer_profile_present_flag[sps_max_sub_layers_minus1];
    uint8_t sub_layer_level_present_flag[sps_max_sub_layers_minus1];

    for (int32_t i = 0; i < sps_max_sub_layers_minus1; i++)
    {
        sub_layer_profile_present_flag[i] = objBitReader.Read(1);
        sub_layer_level_present_flag[i] = objBitReader.Read(1);
    }

    if (sps_max_sub_layers_minus1 > 0)
    {
        for (int32_t j = sps_max_sub_layers_minus1; j < 8; j++)
        {
            objBitReader.Read(2);
        }
    }

    for (int32_t i = 0; i < sps_max_sub_layers_minus1; i++)
    {
        if (sub_layer_profile_present_flag[i])
        {
            objBitReader.Read(2);   // sub_layer_profile_space
            objBitReader.Read(1);   // sub_layer_tier_flag
            objBitReader.Read(5);   // sub_layer_profile_idc
            objBitReader.Read(24);  // sub_layer_profile_compatibility_flag
            objBitReader.Read(8);   // sub_layer_profile_compatibility_flag
            objBitReader.Read(24);  // sub_layer_constraint_indicator_flags
            objBitReader.Read(24);  // sub_layer_constraint_indicator_flags
        }

        if (sub_layer_level_present_flag[i])
        {
            objBitReader.Read(8);  // sub_layer_level_idc
        }
    }

    /*-----------profile_tier_level done-----------------------*/
    objBitReader.ReadByUEMode();  // sps_seq_parameter_set_id

    uint32_t chroma_format_idc;
    chroma_format_idc = objBitReader.ReadByUEMode();

    IMLOGD1("[ParseHevcSpropParam] chroma_format_idc[%d]", chroma_format_idc);

    if (chroma_format_idc == 3)
    {
        objBitReader.Read(1);  // separate_colour_plane_flag
    }

    int32_t pic_width_in_luma_samples = objBitReader.ReadByUEMode();
    int32_t pic_height_in_luma_samples = objBitReader.ReadByUEMode();

    IMLOGD1("[ParseHevcSpropParam] pic_width_in_luma_samples[%d]", pic_width_in_luma_samples);

    IMLOGD1("[ParseHevcSpropParam] pic_height_in_luma_samples[%d]", pic_height_in_luma_samples);

    pInfo->nWidth = pic_width_in_luma_samples;
    pInfo->nHeight = pic_height_in_luma_samples;

    uint8_t conformance_window_flag = objBitReader.Read(1);

    IMLOGD1("[ParseHevcSpropParam] conformance_window_flag[%d]", conformance_window_flag);

    if (conformance_window_flag)
    {
        uint32_t conf_win_left_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_right_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_top_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_bottom_offset = objBitReader.ReadByUEMode();

        pInfo->nWidth -= conf_win_left_offset + conf_win_right_offset;
        pInfo->nHeight -= conf_win_top_offset + conf_win_bottom_offset;

        IMLOGD4("[ParseHevcSpropParam] frame_crop = [%u, %u, %u, %u]", conf_win_left_offset,
                conf_win_right_offset, conf_win_top_offset, conf_win_bottom_offset);
    }

    // Round down by 16 unit
    uint32_t nRoundDownWidth = (pInfo->nWidth) / 16 * 16;
    uint32_t nRoundDownHeight = (pInfo->nHeight) / 16 * 16;

    pInfo->nWidth = nRoundDownWidth;
    pInfo->nHeight = nRoundDownHeight;
    free(pszSpropparam);
    return RESULT_SUCCESS;
}

bool ImsMediaVideoUtil::ParseAvcSps(uint8_t* pbBuffer, uint32_t nBufferSize, tCodecConfig* pInfo)
{
    ImsMediaBitReader bitreader;
    uint32_t chroma_format_idc = 0;
    uint8_t* pszSPS = reinterpret_cast<uint8_t*>(malloc(nBufferSize));

    if (pszSPS == nullptr)
    {
        pInfo->nProfile = 0;
        pInfo->nLevel = 0;
        pInfo->nHeight = 0;
        pInfo->nWidth = 0;
        return false;
    }

    memcpy(pszSPS, pbBuffer, nBufferSize);

    IMLOGD_PACKET1(IM_PACKET_LOG_VIDEO, "[ParseAvcSps] pszSPS[%02X]", pszSPS[0]);

    bitreader.SetBuffer(pszSPS, nBufferSize);
    bitreader.Read(8);
    bitreader.Read(8);
    bitreader.Read(8);
    bitreader.Read(8);
    bitreader.Read(8);

    uint32_t Profile_idc = bitreader.Read(8);  // Read profile_idc
    // Read constraint
    uint32_t constraint_set0 = bitreader.Read(1);
    uint32_t constraint_set1 = bitreader.Read(1);
    uint32_t constraint_set2 = bitreader.Read(1);
    uint32_t constraint_set3 = bitreader.Read(1);
    uint32_t constraint_set4 = bitreader.Read(1);
    bitreader.Read(3);                       // Read reserved_zeor_3bits
    uint32_t Level_idc = bitreader.Read(8);  // Read level_idc
    bitreader.ReadByUEMode();                // Read Seq_parameter_set_id

    if (Profile_idc == 100 || Profile_idc == 110 || Profile_idc == 122 || Profile_idc == 244 ||
            Profile_idc == 44 || Profile_idc == 83 || Profile_idc == 86 || Profile_idc == 118)
    {
        chroma_format_idc = bitreader.ReadByUEMode();

        if (chroma_format_idc == 3)
        {
            bitreader.Read(1);  // separate_colour_plane
        }

        bitreader.ReadByUEMode();  // Read bit_depth_luma_minus8
        bitreader.ReadByUEMode();  // Read bit_depth_chroma_minus8
        bitreader.Read(1);         // Read qpprime_y_zero_transform_bypass

        // Read seq_scaling_matrix_present
        if (bitreader.Read(1))
        {
            uint32_t i = ((chroma_format_idc != 3) ? 8 : 12);
            // set scaling_list... not implement
            bitreader.Read(i);
        }
    }
    else if (Profile_idc == 183)
    {
        chroma_format_idc = 0;
    }
    else
    {
        chroma_format_idc = 1;  // if chroma_format_idc not present in SPS it should be set to 1;
    }
    pInfo->nProfile = Profile_idc;
    pInfo->nLevel = Level_idc;

    IMLOGD_PACKET5(IM_PACKET_LOG_VIDEO, "[ParseAvcSps] constraint_set[%d][%d][%d][%d][%d]",
            constraint_set0, constraint_set1, constraint_set2, constraint_set3, constraint_set4);
    IMLOGD_PACKET2(IM_PACKET_LOG_VIDEO, "[ParseAvcSps] Profile_idc[%d], Level_idc[%d]", Profile_idc,
            Level_idc);

    bitreader.ReadByUEMode();                                // Read log2_max_frame_num_minus4
    uint32_t pic_order_cnt_type = bitreader.ReadByUEMode();  // Read pic_order_cnt_type

    if (pic_order_cnt_type == 0)
    {
        bitreader.ReadByUEMode();  // Read log2_max_pic_order_cnt_lsb_minus4
    }
    else if (pic_order_cnt_type == 1)
    {
        bitreader.Read(1);         // Read delta_pic_order_always_zero_flag
        bitreader.ReadByUEMode();  // Reda offset_for_non_ref_pic...
        bitreader.ReadByUEMode();  // Read offset_for_top_to_bottom_field
        uint32_t num_ref_frames_in_pic_order_cnt_cycle = bitreader.ReadByUEMode();

        for (int32_t i = 0; i < num_ref_frames_in_pic_order_cnt_cycle; i++)
        {
            bitreader.ReadByUEMode();  // Read offset_for_ref_frame[i];
        }
    }

    bitreader.ReadByUEMode();  // Read max_num_ref_frames
    bitreader.Read(1);         // Read gaps_in_frame_num_value_allowed

    uint32_t pic_width_in_mbs_minus1 = bitreader.ReadByUEMode();
    uint32_t pic_height_in_map_units_minus1 = bitreader.ReadByUEMode();
    uint32_t frame_mbs_only = bitreader.Read(1);

    pInfo->nWidth = pic_width_in_mbs_minus1 * 16 + 16;
    pInfo->nHeight = (2 - frame_mbs_only) * (pic_height_in_map_units_minus1 * 16 + 16);

    if (!frame_mbs_only)
    {
        bitreader.Read(1);  // Read mb_adaptive_frame_field
    }

    bitreader.Read(1);                            // Read direct_8x8_inferencce_flag
    uint32_t frame_cropping = bitreader.Read(1);  // Read frame_cropping_flag

    if (frame_cropping)
    {
        uint32_t frame_crop_left_offset = bitreader.ReadByUEMode();
        uint32_t frame_crop_right_offset = bitreader.ReadByUEMode();
        uint32_t frame_crop_top_offset = bitreader.ReadByUEMode();
        uint32_t frame_crop_bottom_offset = bitreader.ReadByUEMode();
        uint32_t cropX, cropY = 0;
        if (chroma_format_idc == 0 /* monochrome */)
        {
            cropX = 1;
            cropY = 2 - frame_mbs_only;
        }
        else
        {
            uint32_t subWidthC = (chroma_format_idc == 3) ? 1 : 2;
            uint32_t subHeightC = (chroma_format_idc == 1) ? 2 : 1;
            cropX = subWidthC;
            cropY = subHeightC * (2 - frame_mbs_only);
        }

        pInfo->nWidth -= (frame_crop_left_offset + frame_crop_right_offset) * cropX;
        pInfo->nHeight -= (frame_crop_top_offset + frame_crop_bottom_offset) * cropY;
    }

    free(pszSPS);

    IMLOGD_PACKET2(IM_PACKET_LOG_VIDEO, "[ParseAvcSps] width[%d],height[%d]", pInfo->nWidth,
            pInfo->nHeight);
    IMLOGD_PACKET2(IM_PACKET_LOG_VIDEO, "[ParseAvcSps] nProfile[%d],nLevel[%d]", pInfo->nProfile,
            pInfo->nLevel);
    return true;
}

bool ImsMediaVideoUtil::ParseHevcSps(uint8_t* pbBuffer, uint32_t nBufferSize, tCodecConfig* pInfo)
{
    if (pbBuffer == nullptr || nBufferSize == 0)
    {
        pInfo->nProfile = 0;
        pInfo->nLevel = 0;
        pInfo->nHeight = 0;
        pInfo->nWidth = 0;
        return false;
    }

    uint32_t nOffset = 0;

    for (int32_t i = 0; i < nBufferSize - 6; i++)
    {
        // NAL unit header offset
        if (pbBuffer[i] == 0x00 && pbBuffer[i + 1] == 0x00 && pbBuffer[i + 2] == 0x00 &&
                pbBuffer[i + 3] == 0x01 && pbBuffer[i + 4] == 0x42 && pbBuffer[i + 5] == 0x01)
        {
            nOffset = i + 6;
            break;
        }
    }

    ImsMediaBitReader objBitReader;
    objBitReader.SetBuffer(pbBuffer + nOffset, nBufferSize - nOffset);
    objBitReader.Read(4);                                       // sps_video_parameter_set_id;
    uint32_t sps_max_sub_layers_minus1 = objBitReader.Read(3);  // 0
    objBitReader.Read(1);                                       // sps_temporal_id_nesting_flag;

    objBitReader.Read(3);                    // general_profile_spac, general_tier_flag
    pInfo->nProfile = objBitReader.Read(5);  // general_profile_idc

    // skip 10byte - flags, not handle
    objBitReader.Read(24);
    objBitReader.Read(24);
    objBitReader.Read(24);
    objBitReader.Read(8);

    pInfo->nLevel = objBitReader.Read(8);  // general_level_idc

    uint8_t sub_layer_profile_present_flag[sps_max_sub_layers_minus1];
    uint8_t sub_layer_level_present_flag[sps_max_sub_layers_minus1];

    for (int32_t i = 0; i < sps_max_sub_layers_minus1; i++)
    {
        sub_layer_profile_present_flag[i] = objBitReader.Read(1);
        sub_layer_level_present_flag[i] = objBitReader.Read(1);
    }

    if (sps_max_sub_layers_minus1 > 0)
    {
        for (int32_t j = sps_max_sub_layers_minus1; j < 8; j++)
        {
            objBitReader.Read(2);
        }
    }

    for (int32_t i = 0; i < sps_max_sub_layers_minus1; i++)
    {
        if (sub_layer_profile_present_flag[i])
        {
            objBitReader.Read(2);   // sub_layer_profile_space
            objBitReader.Read(1);   // sub_layer_tier_flag
            objBitReader.Read(5);   // sub_layer_profile_idc
            objBitReader.Read(24);  // sub_layer_profile_compatibility_flag
            objBitReader.Read(8);   // sub_layer_profile_compatibility_flag
            objBitReader.Read(24);  // sub_layer_constraint_indicator_flags
            objBitReader.Read(24);  // sub_layer_constraint_indicator_flags
        }

        if (sub_layer_level_present_flag[i])
        {
            objBitReader.Read(8);  // sub_layer_level_idc
        }
    }

    objBitReader.ReadByUEMode();  // sps_seq_parameter_set_id

    uint32_t chroma_format_idc = objBitReader.ReadByUEMode();

    if (chroma_format_idc == 3)
    {
        objBitReader.Read(1);  // separate_colour_plane_flag
    }

    pInfo->nWidth = objBitReader.ReadByUEMode();
    pInfo->nHeight = objBitReader.ReadByUEMode();

    uint8_t conformance_window_flag = objBitReader.Read(1);

    if (conformance_window_flag)
    {
        uint32_t conf_win_left_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_right_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_top_offset = objBitReader.ReadByUEMode();
        uint32_t conf_win_bottom_offset = objBitReader.ReadByUEMode();

        pInfo->nWidth -= conf_win_left_offset + conf_win_right_offset;
        pInfo->nHeight -= conf_win_top_offset + conf_win_bottom_offset;
    }

    // Round down by 16 unit
    uint32_t nRoundDownWidth = (pInfo->nWidth) / 16 * 16;
    uint32_t nRoundDownHeight = (pInfo->nHeight) / 16 * 16;

    pInfo->nWidth = nRoundDownWidth;
    pInfo->nHeight = nRoundDownHeight;

    return true;
}

uint8_t* FindAvcStartCode(uint8_t* pData, uint32_t nDataSize, uint32_t* pnSkipSize)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nSkipSize = 0;

    // Remove leading zero bytes and start code prefix
    while (nDataSize >= START_CODE_PREFIX_LEN)
    {
        // Start Code 00 00 00 01 case.
        if (pCurDataPos[0] == 0x00 && pCurDataPos[1] == 0x00 && pCurDataPos[2] == 0x00 &&
                pCurDataPos[3] == 0x01)
        {
            if (pnSkipSize)
                *pnSkipSize = nSkipSize;
            return pCurDataPos;
        }
        else
        {
            pCurDataPos += 1;
            nDataSize -= 1;
            nSkipSize += 1;
        }
    }

    if (pnSkipSize)
        *pnSkipSize = nSkipSize;
    return nullptr;
}

char* ImsMediaVideoUtil::GenerateVideoSprop(VideoConfig* pVideoConfig)
{
    if (pVideoConfig == nullptr)
    {
        IMLOGE0("[GenerateVideoSprop] pVideoConfig is null");
        return nullptr;
    }

    // Configure Encoder
    AMediaFormat* pFormat = AMediaFormat_new();
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_WIDTH, pVideoConfig->getResolutionWidth());
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_HEIGHT, pVideoConfig->getResolutionHeight());

    char kMimeType[128] = {'\0'};
    sprintf(kMimeType, "video/avc");
    if (pVideoConfig->getCodecType() == VideoConfig::CODEC_HEVC)
    {
        sprintf(kMimeType, "video/hevc");
    }

    IMLOGD1("[GenerateVideoSprop] MimeType[%s]", kMimeType);
    IMLOGD2("[GenerateVideoSprop] Resolution[%dx%d].", pVideoConfig->getResolutionWidth(),
            pVideoConfig->getResolutionHeight());
    IMLOGD2("[GenerateVideoSprop] Profile[%d] level[%d]", pVideoConfig->getCodecProfile(),
            pVideoConfig->getCodecLevel());
    IMLOGD2("[GenerateVideoSprop] Bitrate[%d], FrameRate[%d]", pVideoConfig->getBitrate(),
            pVideoConfig->getFramerate());

    AMediaFormat_setString(pFormat, AMEDIAFORMAT_KEY_MIME, kMimeType);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT,
            0x00000015);  // COLOR_FormatYUV420SemiPlanar

    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_BIT_RATE, pVideoConfig->getBitrate() * 1000);
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_PROFILE, pVideoConfig->getCodecProfile());
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_LEVEL, pVideoConfig->getCodecLevel());
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_BITRATE_MODE, 2);  // #2 : BITRATE_MODE_CBR
    AMediaFormat_setFloat(pFormat, AMEDIAFORMAT_KEY_FRAME_RATE, pVideoConfig->getFramerate());
    AMediaFormat_setInt32(
            pFormat, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, pVideoConfig->getIntraFrameInterval());
    AMediaFormat_setInt32(pFormat, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE,
            pVideoConfig->getResolutionWidth() * pVideoConfig->getResolutionHeight() * 10);

    AMediaCodec* pCodec = AMediaCodec_createEncoderByType(kMimeType);
    if (pCodec == nullptr)
    {
        IMLOGE1("[GenerateVideoSprop] Unable to create encoder. MimeType[%s]", kMimeType);
        return nullptr;
    }

    media_status_t err = AMediaCodec_configure(
            pCodec, pFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (err != AMEDIA_OK)
    {
        IMLOGE1("[GenerateVideoSprop] Encoder configure error[%d]", err);
        AMediaCodec_delete(pCodec);
        pCodec = nullptr;
        AMediaFormat_delete(pFormat);
        pFormat = nullptr;
        return nullptr;
    }

    err = AMediaCodec_start(pCodec);
    if (err != AMEDIA_OK)
    {
        IMLOGE1("[GenerateVideoSprop] Encoder start Err[%d]", err);
        AMediaCodec_delete(pCodec);
        pCodec = nullptr;
        AMediaFormat_delete(pFormat);
        pFormat = nullptr;
        return nullptr;
    }

    bool bSpsRead = false, bPpsRead = false;
    int8_t nMaxBufferReads = MAX_OUTPUT_BUFFER_READ_ATTEMPTS;
    char* pSpropStr = reinterpret_cast<char*>(malloc(MAX_CONFIG_LEN));
    pSpropStr[0] = '\0';

    while (!bSpsRead || !bPpsRead)
    {
        // Get output buffer
        AMediaCodecBufferInfo info;
        auto index = AMediaCodec_dequeueOutputBuffer(pCodec, &info, 100000);
        IMLOGD2("[GenerateVideoSprop] dequeueOutputBuffer returned index[%d] frameSize[%d]", index,
                info.size);
        if (index >= 0 && info.size > 0)
        {
            size_t buffCapacity = 0;
            uint8_t* buf = AMediaCodec_getOutputBuffer(pCodec, index, &buffCapacity);
            IMLOGD1("[GenerateVideoSprop] getOutputBuffer returned buffCapacity[%d]", buffCapacity);

            buf += info.offset;
            buffCapacity = info.size;

            while (buf != nullptr && buffCapacity > 0)
            {
                uint32_t skipLen = 0;
                uint8_t* tempBufPtr = FindAvcStartCode(buf, buffCapacity, &skipLen);
                if (tempBufPtr != nullptr)
                {
                    buf = tempBufPtr;
                    buffCapacity -= skipLen;
                }

                if (buf == nullptr || buffCapacity < START_CODE_PREFIX_LEN)
                    continue;

                // Remove start sequence
                buf += START_CODE_PREFIX_LEN;
                buffCapacity -= START_CODE_PREFIX_LEN;

                uint8_t frameType = buf[0] & 0x1F;

                // Extract frame
                int frameLen = buffCapacity;
                tempBufPtr = FindAvcStartCode(buf, buffCapacity, &skipLen);
                if (tempBufPtr != nullptr)
                {
                    frameLen = tempBufPtr - buf;
                }

                int16_t SpropStrlen = strlen(pSpropStr);
                bool ret = ImsMediaBinaryFormat::BinaryToBase00(pSpropStr + SpropStrlen,
                        MAX_CONFIG_LEN, buf, frameLen, BINARY_FORMAT_BASE64);
                if (ret == false)
                {
                    IMLOGE0("[GenerateVideoSprop] BinaryToBase64 failed");
                    free(pSpropStr);
                    pSpropStr = nullptr;
                    AMediaCodec_releaseOutputBuffer(pCodec, index, false);
                    goto JP_Exit_GenerateSprop;
                }

                if (frameType == FRAME_TYPE_SPS)  // Check if SPS
                {
                    strlcat(pSpropStr, ",", MAX_CONFIG_LEN);
                    bSpsRead = true;
                }
                else if (frameType == FRAME_TYPE_PPS)  // Check if PPS
                {
                    bPpsRead = true;
                }

                buffCapacity -= frameLen;
                buf += frameLen;
            }

            AMediaCodec_releaseOutputBuffer(pCodec, index, false);
        }
        else
        {
            IMLOGE2("[GenerateVideoSprop] dequeueOutputBuffer returned invalid index[%d] "
                    "info.size[%d]",
                    index, info.size);
        }

        if (--nMaxBufferReads <= 0)
        {
            free(pSpropStr);
            pSpropStr = nullptr;
            goto JP_Exit_GenerateSprop;
        }
    }

JP_Exit_GenerateSprop:
    // Stop Encoder
    if (pCodec != nullptr)
    {
        IMLOGD0("[GenerateVideoSprop] Stop encoder");
        AMediaCodec_signalEndOfInputStream(pCodec);
        AMediaCodec_stop(pCodec);
        AMediaCodec_delete(pCodec);
    }

    if (pFormat != nullptr)
    {
        AMediaFormat_delete(pFormat);
    }

    IMLOGD1("[GenerateVideoSprop] Returning sprop[%s]", pSpropStr);
    return pSpropStr;
}

void ImsMediaVideoUtil::ConvertBitrateToPower(
        const uint32_t nInputBitrate, uint32_t& nOutExp, uint32_t& nOutMantissa)
{
    if (nInputBitrate == 0)
    {
        return;
    }

    nOutExp = 0;
    nOutMantissa = (nInputBitrate % 2 == 0) ? (nInputBitrate) : (nInputBitrate + 1);

    while (nOutMantissa % 2 == 0)
    {
        nOutExp++;
        nOutMantissa = nOutMantissa >> 1;

        if (nOutMantissa >= 0x1ffff)
        {
            nOutMantissa = (nOutMantissa % 2 == 0) ? (nOutMantissa) : (nOutMantissa + 1);
        }
    }
}

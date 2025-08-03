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

#ifndef AUDIO_FLINGER_H_INCLUDED
#define AUDIO_FLINGER_H_INCLUDED

#define VOIP_MAX_VOC_PKT_SIZE 4096
#define PCM_BUFFER_SIZE       640
// #define PCM_BUFFER_SIZE 1600

struct voip_frame_hdr
{
    uint32_t timestamp;
    union
    {
        uint32_t frame_type;
        uint32_t packet_rate;
    };
};

struct voip_frame
{
    struct voip_frame_hdr frm_hdr;
    uint32_t pktlen;
    uint8_t voc_pkt[VOIP_MAX_VOC_PKT_SIZE];
};

// #define DEBUG_PCM_DUMP    // Write PCM Dump
#define PCM_DUMP_SIZE 960000

const uint32_t AMR_NB_NOTI_COUNT[8] = {
        12,  // 4.75
        13,  // 5.15
        15,  // 5.90
        17,  // 6.70
        19,  // 7.40
        20,  // 7.95
        26,  // 10.20
        31,  // 12.20
};
const uint32_t AMR_WB_NOTI_COUNT[9] = {
        17,  // 6.6
        23,  // 8.85
        32,  // 12.65
        36,  // 14.25
        40,  // 15.85
        46,  // 18.25
        50,  // 19.85
        58,  // 23.05
        60,  // 23.85
};

const uint32_t AMR_NB_INDEX[8] = {4750, 5150, 5900, 6700, 7400, 7950, 10200, 12200};

const uint32_t AMR_WB_INDEX[9] = {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};

#endif  // AUDIO_FLINGER_H_INCLUDED

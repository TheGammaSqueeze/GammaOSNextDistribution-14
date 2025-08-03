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

#ifndef AUDIO_AMRFMT_H_INCLUDED
#define AUDIO_AMRFMT_H_INCLUDED

#include <ImsMediaDefine.h>
#include <stdint.h>

#define IMSAMR_FRAME_BYTES              34

#define EVS_COMPACT_PRIMARY_PAYLOAD_NUM 13
#define EVS_COMPACT_AMRWBIO_PAYLOAD_NUM 10
#define EVS_COMPACT_PAYLOAD_MAX_NUM     32

#define AUDIO_STOP_TIMEOUT              1000

enum kImsAudioFrameEntype
{
    kImsAudioFrameGsmSid = 0,        /* GSM HR, FR or EFR : silence descriptor   */
    kImsAudioFrameGsmSpeechGood,     /* GSM HR, FR or EFR : good speech frame    */
    kImsAudioFrameGsmBfi,            /* GSM HR, FR or EFR : bad frame indicator  */
    kImsAudioFrameGsmInvalidSid,     /* GSM HR            : invalid SID frame    */
    kImsAudioFrameAmrSpeechGood,     /* AMR : good speech frame              */
    kImsAudioFrameAmrSpeechDegraded, /* AMR : degraded speech frame          */
    kImsAudioFrameAmrOnSet,          /* AMR : onset                          */
    kImsAudioFrameAmrSpeechBad,      /* AMR : bad speech frame               */
    kImsAudioFrameAmrSidFirst,       /* AMR : first silence descriptor       */
    kImsAudioFrameAmrSidUpdate,      /* AMR : successive silence descriptor  */
    kImsAudioFrameAmrSidBad,         /* AMR : bad silence descriptor frame   */
    kImsAudioFrameAmrNoData,         /* AMR : Nothing to Transmit     */
    kImsAudioFrameAmrSpeechLost,     /* downlink speech lost           */
    kImsAudioFrameMax
};

enum kImsAudioAmrMode
{
    kImsAudioAmrMode475 = 0,  /* 4.75 kbit/s                             */
    kImsAudioAmrMode515 = 1,  /* 5.15 kbit/s                             */
    kImsAudioAmrMode590 = 2,  /* 5.90 kbit/s                             */
    kImsAudioAmrMode670 = 3,  /* 6.70 kbit/s                             */
    kImsAudioAmrMode740 = 4,  /* 7.40 kbit/s                             */
    kImsAudioAmrMode795 = 5,  /* 7.95 kbit/s                             */
    kImsAudioAmrMode1020 = 6, /* 10.20 kbit/s                            */
    kImsAudioAmrMode1220 = 7, /* 12.20 kbit/s, also used for GSM EFR     */
    kImsAudioAmrModeSID = 8,  /* AMR SID */
    /* 9~13: for future use */
    kImsAudioAmrModeSPL = 14,    /* Speech Lost frame  */
    kImsAudioAmrModeNoData = 15, /* No Data */
    kImsAudioAmrModeMax
};

enum kImsAudioAmrWbMode
{
    kImsAudioAmrWbMode660 = 0,  /* 6.60 kbit/s */
    kImsAudioAmrWbMode885 = 1,  /* 8.85 kbit/s */
    kImsAudioAmrWbMode1265 = 2, /* 12.65 kbit/s */
    kImsAudioAmrWbMode1425 = 3, /* 14.25 kbit/s */
    kImsAudioAmrWbMode1585 = 4, /* 15.85 kbit/s */
    kImsAudioAmrWbMode1825 = 5, /* 18.25 kbit/s */
    kImsAudioAmrWbMode1985 = 6, /* 19.85 kbit/s */
    kImsAudioAmrWbMode2305 = 7, /* 23.05 kbit/s */
    kImsAudioAmrWbMode2385 = 8, /* 23.85 kbit/s */
    kImsAudioAmrWbModeSID = 9,  /* AMRWB SID */
    /* 10~13: for future use */
    kImsAudioAmrWbModeSPL = 14,    /* AMRWB Speech Lost frame */
    kImsAudioAmrWbModeNoData = 15, /* AMRWB No Data */
    kImsAudioAmrWbModeMax
};

enum kImsAudioEvsMode
{
    kImsAudioEvsAmrWbIoMode660 = 0,         /* 6.60 kbps AMR-IO*/
    kImsAudioEvsAmrWbIoMode885 = 1,         /* 8.85 kbps AMR-IO*/
    kImsAudioEvsAmrWbIoMode1265 = 2,        /* 12.65 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode1425 = 3,        /* 14.25 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode1585 = 4,        /* 15.85 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode1825 = 5,        /* 18.25 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode1985 = 6,        /* 19.85 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode2305 = 7,        /* 23.05 kbps AMR-IO */
    kImsAudioEvsAmrWbIoMode2385 = 8,        /* 23.85 kbps AMR-IO */
    kImsAudioEvsPrimaryMode5900 = 9,        /* 5.9 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode7200 = 10,       /* 7.2 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode8000 = 11,       /* 8.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode9600 = 12,       /* 9.6 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode13200 = 13,      /* 13.2 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode16400 = 14,      /* 16.4 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode24400 = 15,      /* 24.4 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode32000 = 16,      /* 32.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode48000 = 17,      /* 48.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode64000 = 18,      /* 64.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode96000 = 19,      /* 96.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryMode128000 = 20,     /* 128.0 kbps, EVS Primary */
    kImsAudioEvsPrimaryModeSID = 21,        /* 2.4 kbps, EVS Primary SID */
    kImsAudioEvsPrimaryModeSpeechLost = 22, /* SPEECH LOST */
    kImsAudioEvsPrimaryModeNoData = 23,     /* NO DATA */
    kImsAudioEvsModeMax
};

// TODO: need to remove this with respective changes
enum kImsAudioEvsAmrWbIoMode
{
    kImsAudioEvsAmrWbIoMode0660 = 0,  /* 6.60 kbit/s */
    kImsAudioEvsAmrWbIoMode0885 = 1,  /* 8.85 kbit/s */
    kImsAudioEvsAmrWbIoMode01265 = 2, /* 12.65 kbit/s */
    kImsAudioEvsAmrWbIoMode01425 = 3, /* 14.25 kbit/s */
    kImsAudioEvsAmrWbIoMode01585 = 4, /* 15.85 kbit/s */
    kImsAudioEvsAmrWbIoMode01825 = 5, /* 18.25 kbit/s */
    kImsAudioEvsAmrWbIoMode01985 = 6, /* 19.85 kbit/s */
    kImsAudioEvsAmrWbIoMode02305 = 7, /* 23.05 kbit/s */
    kImsAudioEvsAmrWbIoMode02385 = 8, /* 23.85 kbit/s */
    kImsAudioEvsAmrWbIoModeSID = 9,   /* AMRWB SID */
    /* 10~13: for future use */
    kImsAudioEvsAmrWbIoModeSPL = 14,    /* AMRWB Speech Lost frame */
    kImsAudioEvsAmrWbIoModeNoData = 15, /* AMRWB No Data */
    kImsAudioEvsAmrWbIoModeMax
};

class ImsMediaAudioUtil
{
public:
    static int32_t ConvertCodecType(int32_t type);
    static int32_t ConvertEvsCodecMode(int32_t evsMode);
    static uint32_t ConvertAmrModeToLen(uint32_t mode);
    static uint32_t ConvertAmrModeToBitLen(uint32_t mode);
    static uint32_t ConvertLenToAmrMode(uint32_t nLen);
    static void ConvertEvsBandwidthToStr(kEvsBandwidth bandwidth, char* nBandwidth, uint32_t nLen);
    static uint32_t ConvertAmrWbModeToLen(uint32_t mode);
    static uint32_t ConvertAmrWbModeToBitLen(uint32_t mode);
    static uint32_t ConvertLenToAmrWbMode(uint32_t nLen);
    static uint32_t ConvertLenToEVSAudioMode(uint32_t nLen);
    static uint32_t ConvertLenToEVSAMRIOAudioMode(uint32_t nLen);
    static uint32_t ConvertEVSAudioModeToBitLen(uint32_t mode);
    static uint32_t ConvertEVSAMRIOAudioModeToBitLen(uint32_t mode);
    static uint32_t ConvertAmrModeToBitrate(uint32_t mode);
    static uint32_t ConvertAmrWbModeToBitrate(uint32_t mode);
    static uint32_t GetMaximumAmrMode(int32_t codecType, int32_t bitmask);
    static uint32_t GetMaximumEvsMode(int32_t bitmask);
    static uint32_t GetBitrateEVS(int mode);
    static kRtpPayloadHeaderMode ConvertEVSPayloadMode(
            uint32_t nDataSize, kEvsCodecMode* pEVSCodecMode, uint32_t* pEVSCompactId);
    static kEvsCodecMode CheckEVSCodecMode(const uint32_t nAudioFrameLength);
    static int32_t ConvertEVSModeToBitRate(const int32_t mode);
    static kEvsBandwidth FindMaxEvsBandwidthFromRange(const int32_t EvsBandwidthRange);
    static bool CheckEVSPrimaryHeaderFullModeFromSize(uint32_t size);
    static ImsMediaSubType GetAmrFrameType(
            const int32_t codecType, const uint32_t frameTypeIndex, const uint32_t size);
};

#endif  // AUDIO_AMRFMT_H_INCLUDED

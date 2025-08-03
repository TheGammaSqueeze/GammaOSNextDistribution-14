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

#include <ImsMediaAudioUtil.h>
#include <ImsMediaDefine.h>
#include <AudioConfig.h>
#include <ImsMediaTrace.h>
#include <string.h>

#define MAX_AMR_MODE 8
#define MAX_EVS_MODE 20

static const uint32_t gaAMRWBLen[32] = {
        17,  // 6.6
        23,  // 8.85
        32,  // 12.65
        36,  // 14.25
        40,  // 15.85
        46,  // 18.25
        50,  // 19.85
        58,  // 23.05
        60,  // 23.85
        5,   // SID
        0,
};

static const uint32_t gaAMRWBbitLen[32] = {
        132,  // 6.6
        177,  // 8.85
        253,  // 12.65
        285,  // 14.25
        317,  // 15.85
        365,  // 18.25
        397,  // 19.85
        461,  // 23.05
        477,  // 23.85
        40,   // SID
        0,
};

static const uint32_t gaEVSPrimaryByteLen[32] = {
        7,    // 2.8 special case
        18,   // 7.2
        20,   // 8.0
        24,   // 9.6
        33,   // 13.2
        41,   // 16.4
        61,   // 24.4
        80,   // 32.0
        120,  // 48.0
        160,  // 64.0
        240,  // 96.0
        320,  // 128.0
        6,    // SID
        0,
};

static const uint32_t gaEVSPrimaryHeaderFullByteLen[32] = {
        8,    // 2.8 special case
        19,   // 7.2
        21,   // 8.0
        25,   // 9.6
        34,   // 13.2
        42,   // 16.4
        62,   // 24.4
        81,   // 32.0
        121,  // 48.0
        161,  // 64.0
        241,  // 96.0
        321,  // 128.0
        7,    // SID
};

static const uint32_t gaEVSPrimaryBitLen[32] = {
        56,    // 2.8 Special case
        144,   // 7.2
        160,   // 8.0
        192,   // 9.6
        264,   // 13.2
        328,   // 16.4
        488,   // 24.4
        640,   // 32.0
        960,   // 48.0
        1280,  // 64.0
        1920,  // 96.0
        2560,  // 128.0
        48,    // SID
        0,
};

static const uint32_t gaEVSAMRWBIOLen[32] = {
        17,  // 6.6
        23,  // 8.85
        32,  // 12.65
        36,  // 14.25
        40,  // 15.85
        46,  // 18.25
        50,  // 19.85
        58,  // 23.05
        60,  // 23.85
        5,   // SID
        0,
};

static const uint32_t gaEVSAmrWbIoBitLen[32] = {
        136,  // 6.6 AMR-WB IO
        184,  // 8.85 AMR-WB IO
        256,  // 12.65 AMR-WB IO
        288,  // 14.25 AMR-WB IO
        320,  // 15.85 AMR-WB IO
        368,  // 18.25 AMR-WB IO
        400,  // 19.85 AMR-WB IO
        464,  // 23.05 AMR-WB IO
        480,  // 23.85 AMR-WB IO
        40,   // SID for AMR-WB IO
        0,    /* Note that no Compact frame format EVS AMR-WB IO SID frames is defined.
           For such frames the Header-Full format with CMR byte shall be used*/
};

static const uint32_t gaAMRLen[16] = {
        12,  // 4.75
        13,  // 5.15
        15,  // 5.90
        17,  // 6.70
        19,  // 7.40
        20,  // 7.95
        26,  // 10.20
        31,  // 12.20
        5,   // SID
        0,
};

static const uint32_t gaAMRBitLen[16] = {
        95,   // 4.75
        103,  // 5.15
        118,  // 5.90
        134,  // 6.70
        148,  // 7.40
        159,  // 7.95
        204,  // 10.20
        244,  // 12.20
        39,   // SID
        0,
};

int32_t ImsMediaAudioUtil::ConvertCodecType(int32_t type)
{
    switch (type)
    {
        default:
        case AudioConfig::CODEC_AMR:
            return kAudioCodecAmr;
        case AudioConfig::CODEC_AMR_WB:
            return kAudioCodecAmrWb;
        case AudioConfig::CODEC_EVS:
            return kAudioCodecEvs;
        case AudioConfig::CODEC_PCMA:
            return kAudioCodecPcma;
        case AudioConfig::CODEC_PCMU:
            return kAudioCodecPcmu;
    }
}

void ImsMediaAudioUtil::ConvertEvsBandwidthToStr(
        kEvsBandwidth bandwidth, char* nBandwidth, uint32_t nLen)
{
    switch (bandwidth)
    {
        case kEvsBandwidthNone:
            strlcpy(nBandwidth, "NONE", nLen);
            break;
        case kEvsBandwidthNB:
            strlcpy(nBandwidth, "NB", nLen);
            break;
        case kEvsBandwidthWB:
            strlcpy(nBandwidth, "WB", nLen);
            break;
        case kEvsBandwidthSWB:
            strlcpy(nBandwidth, "SWB", nLen);
            break;
        case kEvsBandwidthFB:
            strlcpy(nBandwidth, "FB", nLen);
            break;
        default:
            strlcpy(nBandwidth, "SWB", nLen);
            break;
    }
}

int32_t ImsMediaAudioUtil::ConvertEvsCodecMode(int32_t evsMode)
{
    if (evsMode > MAX_AMR_MODE && evsMode <= MAX_EVS_MODE)
    {
        return kEvsCodecModePrimary;
    }
    else if (evsMode >= 0 && evsMode <= MAX_AMR_MODE)
    {
        return kEvsCodecModeAmrIo;
    }
    else
    {
        return kEvsCodecModeMax;
    }
}

uint32_t ImsMediaAudioUtil::ConvertAmrModeToLen(uint32_t mode)
{
    if (mode > kImsAudioAmrModeSID)
    {  // over SID
        return 0;
    }
    return gaAMRLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertAmrModeToBitLen(uint32_t mode)
{
    if (mode > kImsAudioAmrModeSID)
    {  // over SID
        return 0;
    }
    return gaAMRBitLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertLenToAmrMode(uint32_t nLen)
{
    uint32_t i;
    if (nLen == 0)
    {
        return kImsAudioAmrModeNoData;
    }

    for (i = 0; i <= kImsAudioAmrModeSID; i++)
    {
        if (gaAMRLen[i] == nLen)
            return i;
    }
    return kImsAudioAmrModeNoData;
}

uint32_t ImsMediaAudioUtil::ConvertAmrWbModeToLen(uint32_t mode)
{
    if (mode > kImsAudioAmrWbModeSID)
    {
        return 0;
    }
    return gaAMRWBLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(uint32_t mode)
{
    if (mode > kImsAudioAmrWbModeSID)
    {
        return 0;
    }
    return gaAMRWBbitLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertLenToAmrWbMode(uint32_t nLen)
{
    uint32_t i;
    if (nLen == 0)
    {
        return kImsAudioAmrWbModeNoData;
    }

    for (i = 0; i <= kImsAudioAmrWbModeSID; i++)
    {
        if (gaAMRWBLen[i] == nLen)
            return i;
    }
    return kImsAudioAmrWbModeNoData;
}

bool ImsMediaAudioUtil::CheckEVSPrimaryHeaderFullModeFromSize(uint32_t size)
{
    auto it = std::find(std::begin(gaEVSPrimaryHeaderFullByteLen),
            std::end(gaEVSPrimaryHeaderFullByteLen), size);

    // Check if the Evs size is headerfull or not
    if (it != std::end(gaEVSPrimaryHeaderFullByteLen))
    {
        return true;
    }

    return false;
}
uint32_t ImsMediaAudioUtil::ConvertLenToEVSAudioMode(uint32_t nLen)
{
    uint32_t i = 0;
    if (nLen == 0)
        return kImsAudioEvsPrimaryModeNoData;
    for (i = 0; i <= kImsAudioEvsPrimaryModeSID; i++)
    {
        if (gaEVSPrimaryByteLen[i] == nLen)
            return i;
    }
    IMLOGD0("[ConvertLenToEVSAudioMode] No primery bit len found....");
    return 0;
}

uint32_t ImsMediaAudioUtil::ConvertLenToEVSAMRIOAudioMode(uint32_t nLen)
{
    uint32_t i = 0;
    if (nLen == 0)
        return kImsAudioEvsAmrWbIoModeNoData;
    for (i = 0; i <= kImsAudioEvsAmrWbIoModeSID; i++)
    {
        if (gaEVSAMRWBIOLen[i] == nLen)
            return i;
    }
    return 0;
}

uint32_t ImsMediaAudioUtil::ConvertEVSAudioModeToBitLen(uint32_t mode)
{
    if (mode == 15)
        return 0;
    if (mode > 12)
        return 0;
    return gaEVSPrimaryBitLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertEVSAMRIOAudioModeToBitLen(uint32_t mode)
{
    if (mode == 15)
        return 0;
    if (mode > 9)
        return 0;
    return gaEVSAmrWbIoBitLen[mode];
}

uint32_t ImsMediaAudioUtil::ConvertAmrModeToBitrate(uint32_t mode)
{
    switch ((kImsAudioAmrMode)mode)
    {
        case kImsAudioAmrMode475:
            return 4750;
        case kImsAudioAmrMode515:
            return 5150;
        case kImsAudioAmrMode590:
            return 5900;
        case kImsAudioAmrMode670:
            return 6700;
        case kImsAudioAmrMode740:
            return 7400;
        case kImsAudioAmrMode795:
            return 7950;
        case kImsAudioAmrMode1020:
            return 10200;
        default:
        case kImsAudioAmrMode1220:
            return 12200;
    }
}

uint32_t ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(uint32_t mode)
{
    switch ((kImsAudioAmrWbMode)mode)
    {
        case kImsAudioAmrWbMode660:
            return 6600;
        case kImsAudioAmrWbMode885:
            return 8850;
        case kImsAudioAmrWbMode1265:
            return 12650;
        case kImsAudioAmrWbMode1425:
            return 14250;
        case kImsAudioAmrWbMode1585:
            return 15850;
        case kImsAudioAmrWbMode1825:
            return 18250;
        case kImsAudioAmrWbMode1985:
            return 19850;
        case kImsAudioAmrWbMode2305:
            return 23050;
        default:
        case kImsAudioAmrWbMode2385:
            return 23850;
    }
}

uint32_t ImsMediaAudioUtil::GetMaximumAmrMode(int32_t codecType, int32_t bitmask)
{
    uint32_t maxMode =
            (codecType == kAudioCodecAmr) ? kImsAudioAmrMode1220 : kImsAudioAmrWbMode2385;

    for (int32_t i = maxMode; i >= 0; i--)
    {
        if (bitmask & (1 << i))
        {
            maxMode = i;
            break;
        }
    }

    return maxMode;
}

uint32_t ImsMediaAudioUtil::GetMaximumEvsMode(int32_t bitmask)
{
    uint32_t maxMode = 0;

    for (int32_t i = 0; i <= MAX_EVS_MODE; i++)
    {
        if (bitmask & (1 << i))
        {
            maxMode = i;
        }
    }

    return maxMode;
}

int32_t ImsMediaAudioUtil::ConvertEVSModeToBitRate(const int32_t mode)
{
    switch ((kImsAudioEvsMode)mode)
    {
        case kImsAudioEvsAmrWbIoMode660:
            return 6600;
        case kImsAudioEvsAmrWbIoMode885:
            return 8850;
        case kImsAudioEvsAmrWbIoMode1265:
            return 12650;
        case kImsAudioEvsAmrWbIoMode1425:
            return 14250;
        case kImsAudioEvsAmrWbIoMode1585:
            return 15850;
        case kImsAudioEvsAmrWbIoMode1825:
            return 18250;
        case kImsAudioEvsAmrWbIoMode1985:
            return 19850;
        case kImsAudioEvsAmrWbIoMode2305:
            return 23050;
        case kImsAudioEvsAmrWbIoMode2385:
            return 23850;
        case kImsAudioEvsPrimaryMode5900:
            return 5900;
        case kImsAudioEvsPrimaryMode7200:
            return 7200;
        case kImsAudioEvsPrimaryMode8000:
            return 8000;
        case kImsAudioEvsPrimaryMode9600:
            return 9600;
        case kImsAudioEvsPrimaryMode13200:
            return 13200;
        case kImsAudioEvsPrimaryMode16400:
            return 16400;
        case kImsAudioEvsPrimaryMode24400:
            return 24400;
        case kImsAudioEvsPrimaryMode32000:
            return 32000;
        case kImsAudioEvsPrimaryMode48000:
            return 48000;
        case kImsAudioEvsPrimaryMode64000:
            return 64000;
        case kImsAudioEvsPrimaryMode96000:
            return 96000;
        case kImsAudioEvsPrimaryMode128000:
            return 128000;
        default:
            return 13200;
    }
}

kEvsCodecMode ImsMediaAudioUtil::CheckEVSCodecMode(const uint32_t nAudioFrameLength)
{
    switch (nAudioFrameLength)
    {
        // EVS AMR IO Mode Case
        case 17:
        case 23:
        case 32:
        case 36:
        case 40:
        case 46:
        case 50:
        case 58:
        case 60:
        case 5:
            return kEvsCodecModeAmrIo;
        // EVS Primary Mode Case
        case 7:
        case 18:
        case 20:
        case 24:
        case 33:
        case 41:
        case 61:
        case 80:
        case 120:
        case 160:
        case 240:
        case 320:
        case 6:
        default:
            return kEvsCodecModePrimary;
    }
}

kRtpPayloadHeaderMode ImsMediaAudioUtil::ConvertEVSPayloadMode(
        uint32_t nDataSize, kEvsCodecMode* pEVSCodecMode, uint32_t* pEVSCompactId)
{
    uint32_t i = 0;
    uint32_t nDataBitSize = 0;
    // nDataBitSize -= 2;
    nDataBitSize = nDataSize * 8;  // change byte to bit size

    // compact format & primary mode
    for (i = 0; i < EVS_COMPACT_PRIMARY_PAYLOAD_NUM; i++)
    {
        if (gaEVSPrimaryBitLen[i] == nDataBitSize)
        {
            *pEVSCodecMode = kEvsCodecModePrimary;
            *pEVSCompactId = i;
            return kRtpPayloadHeaderModeEvsCompact;
        }
    }

    // compact format & amr-wb io mode
    for (i = 0; i < EVS_COMPACT_AMRWBIO_PAYLOAD_NUM; i++)
    {
        if (gaEVSAmrWbIoBitLen[i] == nDataBitSize)
        {
            *pEVSCodecMode = kEvsCodecModeAmrIo;
            *pEVSCompactId = i;
            return kRtpPayloadHeaderModeEvsCompact;
        }
    }

    // TODO : need to check ID...
    *pEVSCodecMode = kEvsCodecModePrimary;
    *pEVSCompactId = EVS_COMPACT_PAYLOAD_MAX_NUM;
    return kRtpPayloadHeaderModeEvsHeaderFull;
}

kEvsBandwidth ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(const int32_t EvsBandwidthRange)
{
    if (EvsBandwidthRange & kEvsBandwidthFB)
    {
        return kEvsBandwidthFB;
    }
    else if (EvsBandwidthRange & kEvsBandwidthSWB)
    {
        return kEvsBandwidthSWB;
    }
    else if (EvsBandwidthRange & kEvsBandwidthWB)
    {
        return kEvsBandwidthWB;
    }
    else if (EvsBandwidthRange & kEvsBandwidthNB)
    {
        return kEvsBandwidthNB;
    }
    else
    {
        return kEvsBandwidthNone;
    }
}

ImsMediaSubType ImsMediaAudioUtil::GetAmrFrameType(
        const int32_t codecType, const uint32_t frameTypeIndex, const uint32_t size)
{
    ImsMediaSubType subType = MEDIASUBTYPE_AUDIO_NORMAL;
    if (codecType == kAudioCodecAmr)
    {
        if (size == 0 || frameTypeIndex == kImsAudioAmrModeNoData)
        {
            subType = MEDIASUBTYPE_AUDIO_NODATA;
        }
        else if (frameTypeIndex == kImsAudioAmrModeSID)
        {
            subType = MEDIASUBTYPE_AUDIO_SID;
        }
    }
    else if (codecType == kAudioCodecAmrWb)
    {
        if (size == 0 || frameTypeIndex == kImsAudioAmrWbModeNoData)
        {
            subType = MEDIASUBTYPE_AUDIO_NODATA;
        }
        else if (frameTypeIndex == kImsAudioAmrWbModeSID)
        {
            subType = MEDIASUBTYPE_AUDIO_SID;
        }
    }
    return subType;
}

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

#ifndef IMSMEDIA_VIDEO_UTIL_H_INCLUDED
#define IMSMEDIA_VIDEO_UTIL_H_INCLUDED

#include <stdint.h>
#include <ImsMediaDefine.h>
#include <VideoConfig.h>

#define MAX_CONFIG_LEN              256
#define MAX_CONFIG_INDEX            3
#define MAX_VIDEO_WIDTH             1920
#define MAX_VIDEO_HEIGHT            1920
#define MAX_WAIT_RESTART            1000
#define MAX_WAIT_CAMERA             1000
#define MAX_RTP_PAYLOAD_BUFFER_SIZE (MAX_VIDEO_WIDTH * MAX_VIDEO_HEIGHT * 3 >> 1)

enum kVideoResolution
{
    /* video resolu tion is not defined */
    kVideoResolutionInvalid,
    kVideoResolutionSqcifLandscape,  // 128x92
    kVideoResolutionSqcifPortrait,   // 92x128
    kVideoResolutionQcifLandscape,   // 176x144
    kVideoResolutionQcifPortrait,    // 144x176
    kVideoResolutionQvgaLandscape,   // 320x240
    kVideoResolutionQvgaPortrait,    // 240x320
    kVideoResolutionSifLandscape,    // 352x240
    kVideoResolutionSifPortrait,     // 240x352
    kVideoResolutionCifLandscape,    // 352x288
    kVideoResolutionCifPortrait,     // 288x352
    kVideoResolutionVgaLandscape,    // 640x480
    kVideoResolutionVgaPortrait,     // 480x640
    kVideoResolutionHdLandscape,     // 1280x720
    kVideoResolutionHdPortrait,      // 720x1280
    kVideoResolutionFhdLandscape,    // 1920x1280
    kVideoResolutionFhdPortrait,     // 1280x1920
};

enum kConfigFrameType
{
    kConfigSps = 0,
    kConfigPps = 1,
    kConfigVps = 2,
};

struct tCodecConfig
{
    uint32_t nWidth;
    uint32_t nHeight;
    uint32_t nProfile;
    uint32_t nLevel;
};

enum kRtcpFeedbackType
{
    kRtcpFeedbackNone = 0,
    kRtpFbNack = 1,   // Generic NACK
    kRtpFbTmmbr = 3,  // Temoporary Maximum Media Stream Bitrate Request
    kRtpFbTmmbn = 4,  // Temoporary Maximum Media Stream Bitrate Notification
    kPsfbBoundary = 10,
    kPsfbPli = 11,   // Picture Loss Indication
    kPsfbSli = 12,   // Slice Loss Indication
    kPsfbRrsi = 13,  // Reference Picture Selection Indication
    kPsfbFir = 14,   // Full Intra Request - same as "fast video update"
    kPsfbTstr = 15,  // Temporal-Spatial Tradeoff Request - used for changing framerate
    kPsfbTstn = 16,  // Temporal-Spatial Tradeoff Noficiation
    kPsfbVbcm = 17,  // Video Back Channel Message
};

enum kNackRequestType
{
    kRequestSendNackNone = 0,
    kRequestInitialNack,
    kRequestSecondNack,
    kRequestPli,
};

struct NackParams
{
public:
    NackParams() :
            PID(0),
            BLP(0),
            nSecNackCnt(0),
            bNackReport(false)
    {
    }
    NackParams(const NackParams& p)
    {
        PID = p.PID;
        BLP = p.BLP;
        nSecNackCnt = p.nSecNackCnt;
        bNackReport = p.bNackReport;
    }
    NackParams(uint16_t f, uint16_t b, uint16_t cnt, bool r) :
            PID(f),
            BLP(b),
            nSecNackCnt(cnt),
            bNackReport(r)
    {
    }
    uint16_t PID;
    uint16_t BLP;
    uint16_t nSecNackCnt;
    bool bNackReport;
};

struct TmmbrParams
{
public:
    TmmbrParams(uint32_t ss = 0, uint32_t e = 0, uint32_t m = 0, uint32_t o = 0) :
            ssrc(ss),
            exp(e),
            mantissa(m),
            overhead(o)
    {
    }
    TmmbrParams(const TmmbrParams& p)
    {
        ssrc = p.ssrc;
        exp = p.exp;
        mantissa = p.mantissa;
        overhead = p.overhead;
    }
    uint32_t ssrc;
    uint32_t exp;
    uint32_t mantissa;
    uint32_t overhead;
};

enum kCameraFacing
{
    kCameraFacingFront = 0,
    kCameraFacingRear,
};

struct InternalRequestEventParam
{
public:
    InternalRequestEventParam() :
            type(0),
            value(0)
    {
    }
    InternalRequestEventParam(uint32_t t, uint32_t v) :
            type(t),
            value(v)
    {
    }
    InternalRequestEventParam(uint32_t t, const NackParams& params) :
            type(t),
            nackParams(params)
    {
    }
    InternalRequestEventParam(uint32_t t, const TmmbrParams& params) :
            type(t),
            tmmbrParams(params)
    {
    }
    uint32_t type;
    union
    {
        uint32_t value;
        NackParams nackParams;
        TmmbrParams tmmbrParams;
    };
};

/**
 * @brief Utility class for video codec operation.
 */
class ImsMediaVideoUtil
{
public:
    ImsMediaVideoUtil();
    ~ImsMediaVideoUtil();
    static int32_t ConvertCodecType(int32_t type);
    static uint32_t GetResolutionFromSize(uint32_t nWidth, uint32_t nHeight);
    static ImsMediaResult ParseAvcSpropParam(const char* szSpropparam, tCodecConfig* pInfo);
    static ImsMediaResult ParseHevcSpropParam(const char* szSpropparam, tCodecConfig* pInfo);
    static bool ParseAvcSps(uint8_t* pbBuffer, uint32_t nBufferSize, tCodecConfig* pInfo);
    static bool ParseHevcSps(uint8_t* pbBuffer, uint32_t nBufferSize, tCodecConfig* pInfo);
    static char* GenerateVideoSprop(VideoConfig* pVideoConfig);
    static void ConvertBitrateToPower(
            const uint32_t nInputBitrate, uint32_t& nOutExp, uint32_t& nOutMantissa);
};

#endif  // IMSMEDIA_VIDEOUTIL_H_INCLUDED
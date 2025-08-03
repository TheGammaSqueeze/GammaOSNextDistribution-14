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

#ifndef IMS_MEDIA_DEFINE_H
#define IMS_MEDIA_DEFINE_H

#include <RtpConfig.h>
#include <AudioConfig.h>
#include <string.h>

#define DEFAULT_MTU     1500
#define SEQ_ROUND_QUARD 655  // 1% of FFFF
#define USHORT_SEQ_ROUND_COMPARE(a, b)                                                      \
    ((((a) >= (b)) && (((b) >= SEQ_ROUND_QUARD) || (((a) <= 0xffff - SEQ_ROUND_QUARD)))) || \
            (((a) <= SEQ_ROUND_QUARD) && ((b) >= 0xffff - SEQ_ROUND_QUARD)))
#define IMS_MEDIA_WORD_SIZE 4

using namespace android::telephony::imsmedia;

enum ImsMediaResult
{
    RESULT_SUCCESS = 0,
    RESULT_INVALID_PARAM,
    RESULT_NOT_READY,
    RESULT_NO_MEMORY,
    RESULT_NO_RESOURCES,
    RESULT_PORT_UNAVAILABLE,
    RESULT_NOT_SUPPORTED,
};

enum kImsMediaEventType
{
    kImsMediaEventNotifyError = 0,
    kImsMediaEventStateChanged,
    kImsMediaEventFirstPacketReceived,
    kImsMediaEventHeaderExtensionReceived,
    kImsMediaEventMediaQualityStatus,
    kImsMediaEventMediaInactivity,
    kImsMediaEventResolutionChanged,
    kImsMediaEventNotifyVideoDataUsage,
    kImsMediaEventNotifyRttReceived,
    kImsMediaEventNotifyVideoLowestBitrate,
};

// Internal Request Event
enum kImsMediaInternalRequestType
{
    kRequestAudioCmr = 300,
    kRequestAudioRttdUpdate,
    kRequestAudioCmrEvs,
    kRequestVideoCvoUpdate,
    kRequestVideoBitrateChange,
    kRequestVideoIdrFrame,
    kRequestVideoSendNack,
    kRequestVideoSendPictureLost,
    kRequestVideoSendTmmbr,
    kRequestVideoSendTmmbn,
    kRequestRoundTripTimeDelayUpdate = 310,
    kRequestAudioPlayingStatus,
    kCollectPacketInfo,
    kCollectOptionalInfo,
    kCollectRxRtpStatus,
    kCollectJitterBufferSize,
    kGetRtcpXrReportBlock,
    kRequestSendRtcpXrReport,
};

enum kImsMediaErrorNotify
{
    kNotifyErrorSocket = 400,
    kNotifyErrorSurfaceNotReady,
    kNotifyErrorCamera,
    kNotifyErrorEncoder,
    kNotifyErrorDecoder,
};

enum ImsMediaStreamType
{
    kStreamRtpTx,
    kStreamRtpRx,
    kStreamRtcp,
};

enum kImsMediaStreamType
{
    kStreamModeRtpTx,
    kStreamModeRtpRx,
    kStreamModeRtcp,
};

enum ImsMediaType
{
    IMS_MEDIA_AUDIO = 0,
    IMS_MEDIA_VIDEO,
    IMS_MEDIA_TEXT,
};

enum AccessNetworkType
{
    ACCESS_NETWORK_UNKNOWN = 0,
    ACCESS_NETWORK_GERAN = 1,
    ACCESS_NETWORK_UTRAN = 2,
    ACCESS_NETWORK_EUTRAN = 3,
    ACCESS_NETWORK_IWLAN = 5,
    ACCESS_NETWORK_NGRAN = 6,
};

enum kProtocolType
{
    kProtocolRtp = 0,
    kProtocolRtcp,
};

enum kEvsBandwidth
{
    kEvsBandwidthNone = 0,
    kEvsBandwidthNB = 1,
    kEvsBandwidthWB = 2,
    kEvsBandwidthSWB = 4,
    kEvsBandwidthFB = 8,
};

enum kEvsBitrate
{
    /* 6.6 kbps, AMR-IO */
    kEvsAmrIoModeBitrate00660 = 0,
    /* 8.85 kbps, AMR-IO */
    kEvsAmrIoModeBitrate00885 = 1,
    /* 12.65 kbps, AMR-IO */
    kEvsAmrIoModeBitrate01265 = 2,
    /* 14.25 kbps, AMR-IO */
    kEvsAmrIoModeBitrate01425 = 3,
    /* 15.85 kbps, AMR-IO */
    kEvsAmrIoModeBitrate01585 = 4,
    /* 18.25 kbps, AMR-IO */
    kEvsAmrIoModeBitrate01825 = 5,
    /* 19.85 kbps, AMR-IO */
    kEvsAmrIoModeBitrate01985 = 6,
    /* 23.05 kbps, AMR-IO */
    kEvsAmrIoModeBitrate02305 = 7,
    /* 23.85 kbps, AMR-IO */
    kEvsAmrIoModeBitrate02385 = 8,
    /* 5.9 kbps, EVS Primary - SC-VBR 2.8kbps, 7.2kbps, 8kbps*/
    kEvsPrimaryModeBitrate00590 = 9,
    /* 7.2 kbps, EVS Primary */
    kEvsPrimaryModeBitrate00720 = 10,
    /* 8 kbps, EVS Primary */
    kEvsPrimaryModeBitrate00800 = 11,
    /* 9.6 kbps, EVS Primary */
    kEvsPrimaryModeBitrate00960 = 12,
    /* 13.20 kbps, EVS Primary */
    kEvsPrimaryModeBitrate01320 = 13,
    /* 16.4 kbps, EVS Primary */
    kEvsPrimaryModeBitrate01640 = 14,
    /* 24.4 kbps, EVS Primary */
    kEvsPrimaryModeBitrate02440 = 15,
    /* 32 kbps, EVS Primary */
    kEvsPrimaryModeBitrate03200 = 16,
    /* 48 kbps, EVS Primary */
    kEvsPrimaryModeBitrate04800 = 17,
    /* 64 kbps, EVS Primary */
    kEvsPrimaryModeBitrate06400 = 18,
    /* 96 kbps, EVS Primary */
    kEvsPrimaryModeBitrate09600 = 19,
    /* 128 kbps, EVS Primary */
    kEvsPrimaryModeBitrate12800 = 20,
    /* 2.4 kbps, EVS Primary */
    kEvsPrimaryModeBitrateSID = 21,
    /* SPEECH LOST */
    kEvsPrimaryModeBitrateSpeechLost = 22,
    /* NO DATA */
    kEvsPrimaryModeBitrateNoData = 23,
};

enum kEvsCodecMode
{
    kEvsCodecModePrimary = 0,  // EVS PRIMARY mode 0
    kEvsCodecModeAmrIo = 1,    // EVS AMR-WB IO mode 1
    kEvsCodecModeMax = 0x7FFFFFFF
};

/* CMR Code in TS 26.445 */
enum kEvsCmrCodeType
{
    kEvsCmrCodeTypeNb = 0,      // 000: Narrow band
    kEvsCmrCodeTypeAmrIO = 1,   // 001: AMR IO mode
    kEvsCmrCodeTypeWb = 2,      // 010: Wide band
    kEvsCmrCodeTypeSwb = 3,     // 011: Super wide band
    kEvsCmrCodeTypeFb = 4,      // 100: Full band
    kEvsCmrCodeTypeWbCha = 5,   // 101: Wide band(13.2 Channel aware mode)
    kEvsCmrCodeTypeSwbCha = 6,  // 110: Super wide band (13.2 Channel aware mode)
    kEvsCmrCodeTypeNoReq = 7,   // 111: Reserved
};

/* CMR Definition in TS 26.445 */
enum kEvsCmrCodeDefine
{
    kEvsCmrCodeDefine59 = 0,      // 0000
    kEvsCmrCodeDefine72 = 1,      // 0001
    kEvsCmrCodeDefine80 = 2,      // 0010
    kEvsCmrCodeDefine96 = 3,      // 0011
    kEvsCmrCodeDefine132 = 4,     // 0100
    kEvsCmrCodeDefine164 = 5,     // 0101
    kEvsCmrCodeDefine244 = 6,     // 0110
    kEvsCmrCodeDefine320 = 7,     // 0111
    kEvsCmrCodeDefine480 = 8,     // 1000
    kEvsCmrCodeDefine640 = 9,     // 1001
    kEvsCmrCodeDefine960 = 10,    // 1010
    kEvsCmrCodeDefine1280 = 11,   // 1011
    kEvsCmrCodeDefineNoReq = 15,  // 1111

    // Channel aware mode
    kEvsCmrCodeDefineChaOffset2 = 0,   // 0000
    kEvsCmrCodeDefineChaOffset3 = 1,   // 0001
    kEvsCmrCodeDefineChaOffset5 = 2,   // 0010
    kEvsCmrCodeDefineChaOffset7 = 3,   // 0011
    kEvsCmrCodeDefineChaOffsetH2 = 4,  // 0100
    kEvsCmrCodeDefineChaOffsetH3 = 5,  // 0101
    kEvsCmrCodeDefineChaOffsetH5 = 6,  // 0110
    kEvsCmrCodeDefineChaOffsetH7 = 7,  // 0111

    // AMR WB-IO
    kEvsCmrCodeDefineAmrIo660 = 0,   // 0000
    kEvsCmrCodeDefineAmrIo885 = 1,   // 0001
    kEvsCmrCodeDefineAmrIo1265 = 2,  // 0010
    kEvsCmrCodeDefineAmrIo1425 = 3,  // 0011
    kEvsCmrCodeDefineAmrIo1585 = 4,  // 0100
    kEvsCmrCodeDefineAmrIo1825 = 5,  // 0101
    kEvsCmrCodeDefineAmrIo1985 = 6,  // 0110
    kEvsCmrCodeDefineAmrIo2305 = 7,  // 0111
    kEvsCmrCodeDefineAmrIo2385 = 8,  // 1000

    kEvsCmrCodeDefineENUM_MAX = 0x7FFFFFFF
};

enum ImsMediaSubType
{
    MEDIASUBTYPE_UNDEFINED = 0,
    // rtp payload header + encoded bitstream
    MEDIASUBTYPE_RTPPAYLOAD,
    // rtp packet
    MEDIASUBTYPE_RTPPACKET,
    // rtcp packet
    MEDIASUBTYPE_RTCPPACKET,
    // rtcp packet
    MEDIASUBTYPE_RTCPPACKET_BYE,
    // raw yuv or pcm data
    MEDIASUBTYPE_RAWDATA,
    MEDIASUBTYPE_RAWDATA_ROT90,
    MEDIASUBTYPE_RAWDATA_ROT90_FLIP,
    MEDIASUBTYPE_RAWDATA_ROT270,
    MEDIASUBTYPE_RAWDATA_CROP_ROT90,
    MEDIASUBTYPE_RAWDATA_CROP_ROT90_FLIP,
    MEDIASUBTYPE_RAWDATA_CROP_ROT270,
    MEDIASUBTYPE_RAWDATA_CROP,
    // dtmf packet with start bit set
    MEDIASUBTYPE_DTMFSTART,
    // dtmf payload
    MEDIASUBTYPE_DTMF_PAYLOAD,
    // dtmf packet with end bit set
    MEDIASUBTYPE_DTMFEND,
    MEDIASUBTYPE_VIDEO_CONFIGSTRING,
    MEDIASUBTYPE_VIDEO_IDR_FRAME,
    MEDIASUBTYPE_VIDEO_NON_IDR_FRAME,
    MEDIASUBTYPE_VIDEO_SEI_FRAME,
    MEDIASUBTYPE_ROT0 = 20,
    MEDIASUBTYPE_ROT90,
    MEDIASUBTYPE_ROT180,
    MEDIASUBTYPE_ROT270,
    MEDIASUBTYPE_REFRESHED,
    // rtt bitstream of t.140 format
    MEDIASUBTYPE_BITSTREAM_T140,
    // rtt bitstream of t.140 redundant format
    MEDIASUBTYPE_BITSTREAM_T140_RED,
    MEDIASUBTYPE_PCM_DATA,
    MEDIASUBTYPE_PCM_NO_DATA,
    // Jitter Buffer GetData not ready
    MEDIASUBTYPE_NOT_READY,
    MEDIASUBTYPE_BITSTREAM_CODECCONFIG,
    MEDIASUBTYPE_AUDIO_SID,
    MEDIASUBTYPE_AUDIO_NODATA,
    MEDIASUBTYPE_AUDIO_NORMAL,
    MEDIASUBTYPE_MAX
};

enum ImsMediaAudioMsgRequest
{
    kAudioOpenSession = 101,
    kAudioCloseSession,
    kAudioModifySession,
    kAudioAddConfig,
    kAudioDeleteConfig,
    kAudioConfirmConfig,
    kAudioSendDtmf,
    kAudioSendRtpHeaderExtension,
    kAudioSetMediaQualityThreshold,
    kAudioRequestRtpReceptionStats = 112,
    kAudioAdjustDelay,
};

enum ImsMediaAudioMsgResponse
{
    kAudioOpenSessionSuccess = 201,
    kAudioOpenSessionFailure,
    kAudioModifySessionResponse,
    kAudioAddConfigResponse,
    kAudioConfirmConfigResponse,
    kAudioFirstMediaPacketInd,
    kAudioRtpHeaderExtensionInd,
    kAudioMediaQualityStatusInd,
    kAudioTriggerAnbrQueryInd,
    kAudioDtmfReceivedInd,
    kAudioCallQualityChangedInd,
    kAudioSessionClosed,
    kAudioNotifyRtpReceptionStats,
};

enum ImsMediaVideoMsgRequest
{
    kVideoOpenSession = 101,
    kVideoCloseSession,
    kVideoModifySession,
    kVideoSetPreviewSurface,
    kVideoSetDisplaySurface,
    kVideoSendRtpHeaderExtension,
    kVideoSetMediaQualityThreshold,
    kVideoRequestDataUsage,
};

enum ImsMediaVideoMsgResponse
{
    kVideoOpenSessionSuccess = 201,
    kVideoOpenSessionFailure,
    kVideoModifySessionResponse,
    kVideoFirstMediaPacketInd,
    kVideoPeerDimensionChanged,
    kVideoRtpHeaderExtensionInd,
    kVideoMediaInactivityInd,
    kVideoBitrateInd,
    kVideoDataUsageInd,
    kVideoSessionClosed,
};

enum ImsMediaTextMsgRequest
{
    kTextOpenSession = 101,
    kTextCloseSession,
    kTextModifySession,
    kTextSetMediaQualityThreshold,
    kTextSendRtt,
};

enum ImsMediaTextMsgResponse
{
    kTextOpenSessionSuccess = 201,
    kTextOpenSessionFailure,
    kTextModifySessionResponse,
    kTextMediaInactivityInd,
    kTextRttReceived,
    kTextSessionClosed,
};

#define UNDEFINED_SOCKET_FD                        (-1)
#define T140_BUFFERING_TIME                        (300)
#define RTT_MAX_CHAR_PER_SEC                       (30)  // ATIS_GTT : 30 characters per second
#define RTT_MAX_UNICODE_UTF8                       (4)
#define MAX_RTT_LEN                                (RTT_MAX_CHAR_PER_SEC * RTT_MAX_UNICODE_UTF8)
#define PAYLOADENCODER_TEXT_MAX_REDUNDANT_INTERVAL (16383)

struct EventParamOpenSession
{
public:
    void* mConfig;
    int rtpFd;
    int rtcpFd;
    EventParamOpenSession(
            int rtp = UNDEFINED_SOCKET_FD, int rtcp = UNDEFINED_SOCKET_FD, void* config = nullptr) :
            mConfig(config),
            rtpFd(rtp),
            rtcpFd(rtcp)
    {
    }
};

struct EventParamDtmf
{
public:
    char digit;
    int duration;

    EventParamDtmf(char dig, int d)
    {
        digit = dig;
        duration = d;
    }
};

enum kAudioCodecType
{
    kAudioCodecNone = 0,
    kAudioCodecAmr,
    kAudioCodecAmrWb,
    kAudioCodecPcmu,
    kAudioCodecPcma,
    kAudioCodecEvs,
};

enum kVideoCodecType
{
    kVideoCodecNone = 0,
    kVideoCodecAvc,
    kVideoCodecHevc,
};

enum kRtpPayloadHeaderMode
{
    // Amr mode
    kRtpPayloadHeaderModeAmrOctetAligned = 0,  // octet aligned mode
    kRtpPayloadHeaderModeAmrEfficient = 1,     // efficient mode
                                               // Video packetization mode
    kRtpPayloadHeaderModeSingleNalUnit = 0,    // packet mode 0
    kRtpPayloadHeaderModeNonInterleaved = 1,   // packet mode 1
                                               // Evs mode
    kRtpPayloadHeaderModeEvsCompact = 0,       // EVS compact format 0
    kRtpPayloadHeaderModeEvsHeaderFull = 1,    // EVS header-full format 1
    kRtpPayloadHeaderModeMax
};

enum kIpVersion
{
    IPV4,
    IPV6,
};

enum StreamState
{
    /** The state of the stream created but any nodes are not created */
    kStreamStateIdle,
    /** The state of the stream and nodes are created */
    kStreamStateCreated,
    /** The state of the stream nodes are started and running */
    kStreamStateRunning,
    /** Video state wait surface in stating */
    kStreamStateWaitSurface,
};

enum SessionState
{
    /** The state that the session is created but graph is not created */
    kSessionStateOpened,
    /** The state that the session is created and the TX rtp StreamGraphs are running */
    kSessionStateSending,
    /** The state that the session is created and the RX rtp StreamGraphs are running */
    kSessionStateReceiving,
    /** The state that the session is created and the both TX and Rx rtp StreamGraphs are running */
    kSessionStateActive,
    /** The state that the session is created and the Rtp StreamGraphs is not running */
    kSessionStateSuspended,
    /** The state that the session is closed */
    kSessionStateClosed,
};

enum kSocketOption
{
    kSocketOptionNone = 0,
    kSocketOptionIpTos = 1,
    kSocketOptionIpTtl = 2,
};

enum kRtpPacketStatus
{
    kRtpStatusNotDefined = 0,
    kRtpStatusNormal,
    kRtpStatusLate,
    kRtpStatusDiscarded,
    kRtpStatusDuplicated,
    kRtpStatusLost,
};

enum kRtpDataType
{
    kRtpDataTypeNoData = 0,
    kRtpDataTypeSid,
    kRtpDataTypeNormal,
};

enum kAudioPlayingStatus
{
    kAudioTypeNoData = 0,
    kAudioTypeVoice,
};

enum kOptionalReportType
{
    kTimeToLive,
    kRoundTripDelay,
    kReportPacketLossGap,
    kAudioPlayingStatus,
};

/** TODO: change the name to avoid confusion by similarity */
struct RtpPacket
{
public:
    RtpPacket(const uint32_t s = 0, const uint32_t seq = 0, const uint32_t t = 0,
            const int32_t j = 0, const int32_t ts = 0, const int32_t a = 0,
            const kRtpDataType type = kRtpDataTypeNoData,
            const kRtpPacketStatus st = kRtpStatusNotDefined) :
            ssrc(s),
            seqNum(seq),
            TTL(t),
            jitter(j),
            timestamp(ts),
            arrival(a),
            rtpDataType(type),
            status(st)
    {
    }
    RtpPacket(const RtpPacket& p)
    {
        ssrc = p.ssrc;
        seqNum = p.seqNum;
        TTL = p.TTL;
        jitter = p.jitter;
        timestamp = p.timestamp;
        arrival = p.arrival;
        rtpDataType = p.rtpDataType;
        status = p.status;
    }

    uint32_t ssrc;
    uint32_t seqNum;
    uint32_t TTL;
    /** transit time difference */
    int32_t jitter;
    int32_t timestamp;
    /** arrival time */
    int32_t arrival;
    kRtpDataType rtpDataType;
    kRtpPacketStatus status;
};

/**
 * @brief It is lost packet data structure to store the start number of packet sequence and the
 * number of lost packets
 */
struct LostPacket
{
public:
    LostPacket(uint16_t s = 0, uint32_t num = 0, uint32_t time = 0, uint32_t opt = 0) :
            seqNum(s),
            numLoss(num),
            markedTime(time),
            option(opt)
    {
    }
    /** The rtp sequence number of beginning of lost packet */
    uint16_t seqNum;
    /** The number of lost packets */
    uint32_t numLoss;
    /** The time in milliseconds when determined to lost */
    uint32_t markedTime;
    /** optional parameter for nack */
    uint32_t option;
};

struct RtpHeaderExtensionInfo
{
public:
    enum
    {
        // RFC 8285#section-4.2, The bit pattern for one byte header
        kBitPatternForOneByteHeader = 0xBEDE,
        // RFC 8285#section-4.3, The bit pattern for two byte header
        kBitPatternForTwoByteHeader = 0x1000,
    };

    uint16_t definedByProfile;
    uint16_t length;  // length in word unit
    int8_t* extensionData;
    uint16_t extensionDataSize;

    RtpHeaderExtensionInfo(
            uint16_t profile = 0, uint16_t len = 0, int8_t* data = nullptr, uint16_t size = 0)
    {
        definedByProfile = profile;
        length = len;
        extensionData = nullptr;
        extensionDataSize = 0;
        setExtensionData(data, size);
    }

    RtpHeaderExtensionInfo(const RtpHeaderExtensionInfo& extension)
    {
        definedByProfile = extension.definedByProfile;
        extensionData = nullptr;
        length = extension.length;
        setExtensionData(extension.extensionData, extension.extensionDataSize);
    }

    ~RtpHeaderExtensionInfo()
    {
        if (extensionData != nullptr)
        {
            delete[] extensionData;
            extensionData = nullptr;
        }
    }

    RtpHeaderExtensionInfo& operator=(const RtpHeaderExtensionInfo& extension)
    {
        if (this != &extension)
        {
            definedByProfile = extension.definedByProfile;
            length = extension.length;
            setExtensionData(extension.extensionData, extension.extensionDataSize);
        }

        return *this;
    }

    void setExtensionData(int8_t* data, uint16_t dataSize)
    {
        if (extensionData != nullptr)
        {
            delete[] extensionData;
            extensionData = nullptr;
            extensionDataSize = 0;
        }

        if (data != nullptr)
        {
            extensionDataSize = dataSize;
            extensionData = new int8_t[extensionDataSize];
            memcpy(extensionData, data, extensionDataSize);
        }
    }
};

#define MAX_IP_LEN       128
#define MAX_REMOTE_POINT 40

class RtpAddress
{
public:
    RtpAddress(const char* ip = nullptr, uint32_t p = 0)
    {
        memset(this->ipAddress, 0, MAX_IP_LEN);
        if (ip != nullptr)
        {
            std::strncpy(ipAddress, ip, MAX_IP_LEN);
        }
        port = p;
    }
    ~RtpAddress() {}
    RtpAddress(const RtpAddress& address) :
            port(address.port)
    {
        memset(this->ipAddress, 0, MAX_IP_LEN);
        std::strncpy(this->ipAddress, address.ipAddress, MAX_IP_LEN);
    }
    RtpAddress& operator=(const RtpAddress& address)
    {
        if (this != &address)
        {
            memset(this->ipAddress, 0, MAX_IP_LEN);
            std::strncpy(this->ipAddress, address.ipAddress, MAX_IP_LEN);
            this->port = address.port;
        }

        return *this;
    }
    bool operator==(const RtpAddress& address)
    {
        return (std::strcmp(this->ipAddress, address.ipAddress) == 0 && this->port == address.port);
    }
    bool operator!=(const RtpAddress& address)
    {
        return (std::strcmp(this->ipAddress, address.ipAddress) != 0 || this->port != address.port);
    }
    char ipAddress[MAX_IP_LEN];
    uint32_t port;
};

struct RtcpRecvReport
{
public:
    RtcpRecvReport(const uint32_t s = 0, const uint32_t f = 0, const uint32_t c = 0,
            const uint32_t e = 0, const uint32_t j = 0, const uint32_t l = 0,
            const uint32_t d = 0) :
            ssrc(s),
            fractionLost(f),
            cumPktsLost(c),
            extHighSeqNum(e),
            jitter(j),
            lsr(l),
            delayLsr(d)
    {
    }

    RtcpRecvReport(const RtcpRecvReport& report) :
            ssrc(report.ssrc),
            fractionLost(report.fractionLost),
            cumPktsLost(report.cumPktsLost),
            extHighSeqNum(report.extHighSeqNum),
            jitter(report.jitter),
            lsr(report.lsr),
            delayLsr(report.delayLsr)

    {
    }

    RtcpRecvReport& operator=(const RtcpRecvReport& report)
    {
        if (this != &report)
        {
            ssrc = report.ssrc;
            fractionLost = report.fractionLost;
            cumPktsLost = report.cumPktsLost;
            extHighSeqNum = report.extHighSeqNum;
            jitter = report.jitter;
            lsr = report.lsr;
            delayLsr = report.delayLsr;
        }

        return *this;
    }

    uint32_t ssrc;
    uint32_t fractionLost;
    uint32_t cumPktsLost;
    uint32_t extHighSeqNum;
    uint32_t jitter;
    uint32_t lsr;
    uint32_t delayLsr;
};

struct RtcpSr
{
public:
    RtcpSr(const uint32_t ntpTsMsw = 0, const uint32_t ntpTsLsw = 0, const uint32_t rtpTs = 0,
            const uint32_t count = 0, const uint32_t octCount = 0,
            const RtcpRecvReport& report = RtcpRecvReport()) :
            ntpTimestampMsw(ntpTsMsw),
            ntpTimestampLsw(ntpTsLsw),
            rtpTimestamp(rtpTs),
            sendPktCount(count),
            sendOctCount(octCount),
            recvReport(report)
    {
    }

    uint32_t ntpTimestampMsw;
    uint32_t ntpTimestampLsw;
    uint32_t rtpTimestamp;
    uint32_t sendPktCount;
    uint32_t sendOctCount;
    RtcpRecvReport recvReport;  // only one RR block is supported.
};

struct RtcpRr
{
public:
    RtcpRr(const RtcpRecvReport& report) :
            recvReport(report)
    {
    }

    RtcpRecvReport recvReport;  // only one RR block is supported.
};

#endif

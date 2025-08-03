/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @brief   This file contains RTP protocol stack related constants, enums, structures and
 * callback defines.
 *
 */

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

#ifndef _RTP_SERVICE_TYPES_H_
#define _RTP_SERVICE_TYPES_H_

#include <RtpPfDatatypes.h>

typedef void* RTPSESSIONID;

typedef enum
{
    RTPSVC_RECEIVE_RTP_IND,
    RTPSVC_RECEIVE_RTCP_SR_IND,
    RTPSVC_RECEIVE_RTCP_RR_IND,
    RTPSVC_RECEIVE_RTCP_SDES_IND,
    RTPSVC_RECEIVE_RTCP_BYE_IND,
    RTPSVC_RECEIVE_RTCP_APP_IND,  // 5
    RTPSVC_SESS_READY_DEL_IND,
    RTPSVC_CREATE_MEMBER_IND,
    RTPSVC_DELETE_MEMBER_IND,
    RTPSVC_SSRC_COLLISION_CHANGED_IND,
    RTPSVC_MEMBER_COLLISION_IND,  // 10
    RTPSVC_RECEIVE_RTCP_TIMER_EXPIRY_IND,
    RTPSVC_UNKNOWN_ERR_IND,
    RTPSVC_RECEIVE_RTCP_FB_IND,
    RTPSVC_RECEIVE_RTCP_PAYLOAD_FB_IND,
    RTPSVC_LAST_IND_FROM_STACK = 0x7fff
} tRtpSvc_IndicationFromStack;

typedef RtpDt_Void (*RtpSvc_AppIndCbFunc)(
        tRtpSvc_IndicationFromStack eIndType, RtpDt_Void* pData, RtpDt_Void* pvUserData);

typedef RtpDt_Void (*RtpSvc_AppIndCbRtcp)(RtpDt_Void* pData, RtpDt_Void* pvUserData);
typedef RtpDt_Int32 (*RtpSvc_SendToPeerCb)(RtpDt_UChar*, RtpSvc_Length, RtpDt_Void* pvUserData);
typedef RtpDt_Int32 (*RtcpSvc_SendToPeerCb)(RtpDt_UChar*, RtpSvc_Length, RtpDt_Void* pvUserData);

typedef struct
{
    RtpDt_UInt32 payloadType;
    RtpDt_UInt32 samplingRate;
} tRtpSvc_SetPayloadParam;

typedef struct
{
    eRtp_Bool bMbit;
    RtpDt_UChar byPayLoadType;
    eRtp_Bool bUseLastTimestamp;
    RtpDt_UInt32 diffFromLastRtpTimestamp;

    // Rtp extension header
    eRtp_Bool bXbit;
    RtpDt_UInt16 wDefinedByProfile;
    RtpDt_UInt16 wExtLen;
    RtpDt_Int8* pExtData;
    RtpDt_Int32 nExtDataSize;
} tRtpSvc_SendRtpPacketParam;

/*
typedef struct
{
    eRtp_Bool    bMbit;
    RtpDt_UChar    byPayLoadType;
    eRtp_Bool    bUseLastTimestamp;
    RtpDt_UInt32    ssrc;
}RtpSvc_SendRtcpAppPacketParm;    */

typedef struct
{
    eRtp_Bool bMbit;
    RtpDt_UInt32 dwTimestamp;
    RtpDt_UInt32 dwPayloadType;
    RtpDt_UInt16 dwSeqNum;
    RtpDt_UInt32 dwSsrc;

    RtpDt_UInt16 wMsgHdrLen;
    RtpDt_UChar* pMsgHdr;

    RtpDt_UInt16 wMsgBodyLen;
    RtpDt_UChar* pMsgBody;

    /* RTP Header extension */
    RtpDt_UInt16 wDefinedByProfile;
    RtpDt_UInt16 wExtLen;
    RtpDt_UChar* pExtData;
    RtpDt_UInt16 wExtDataSize;
} tRtpSvcIndSt_ReceiveRtpInd;

typedef struct
{
    RtpDt_UInt16 wSubType;
    RtpDt_UInt32 dwName;
    RtpDt_UInt16 wMsgLen;  // total RTCP length
    RtpDt_UChar* pMsg;     // total RTCP Packet(Hdr + App Info)
} tRtpSvcIndSt_ReceiveRtcpAppInd;

// To support RTCP Feedback
typedef struct
{
    RtpDt_UInt16 wPayloadType;
    RtpDt_UInt16 wFmt;
    RtpDt_UInt32 dwMediaSsrc;
    RtpDt_UInt16 wMsgLen;  // total RTCP length
    RtpDt_UChar* pMsg;     // total RTCP Packet(Hdr + App Info)
} tRtpSvcIndSt_ReceiveRtcpFeedbackInd;

typedef struct
{
    RtpDt_UInt16 wMsgLen;
    RtpDt_UChar* pMsg;
} tRtpSvcIndSt_ReceiveOtherRtcpInd;

typedef struct
{
    RtpDt_UInt32 dwOldSsrc;
    RtpDt_UInt32 dwNewSsrc;
} tRtpSvcIndSt_SsrcCollisionInd;

typedef struct
{
    unsigned int ssrc;
    unsigned int fractionLost;
    unsigned int cumPktsLost;
    unsigned int extHighSeqNum;
    unsigned int jitter;
    unsigned int lsr;
    unsigned int delayLsr;
} tRtpSvcRecvReport;

typedef struct
{
    unsigned int ntpTimestampMsw;
    unsigned int ntpTimestampLsw;
    unsigned int rtpTimestamp;
    unsigned int sendPktCount;
    unsigned int sendOctCount;
    tRtpSvcRecvReport stRecvRpt;  // only one RR block is supported.
} tNotifyReceiveRtcpSrInd;

typedef struct
{
    tRtpSvcRecvReport stRecvRpt;  // only one RR block is supported.
} tNotifyReceiveRtcpRrInd;

#endif /* End of _RTP_SERVICE_TYPES_H_*/

/** @}*/

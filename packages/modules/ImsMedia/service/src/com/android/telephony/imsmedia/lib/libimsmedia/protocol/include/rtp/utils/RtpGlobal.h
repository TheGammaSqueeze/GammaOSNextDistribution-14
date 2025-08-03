
/** \addtogroup  RTP_Stack
 *  @{
 */

/**
 * @brief    This file holds data structures, enums and constants of RTP Stack
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

#ifndef __RTP_GLOBAL_H__
#define __RTP_GLOBAL_H__

#include <RtpPfDatatypes.h>
#include <RtpOsUtil.h>

#define RTP_INIT_TRUE_T_MIN      2.5
#define RTP_INIT_FALSE_T_MIN     5

#define RTP_CONF_RTCP_BW_FRAC    5
#define RTP_CONF_RTCP_RPT_INTRL  25
#define RTP_CONF_BW_SPLIT        25
#define RTP_CONF_MTU_SIZE        3000
#define RTP_CONF_SSRC_SEED       2

#define RTP_SIXTEEN              16
#define RTP_EIGHT                8
#define RTP_FOUR                 4
#define RTP_DEF_RTCP_BW_FRAC     5

#define RTP_DEF_RTCP_BW_SIZE     128

#define RTP_TWO_POWER_16         65536
#define RTP_SSRC_GEN_UTL         0xffffff00
#define RTP_DEF_MTU_SIZE         1350
#define MAX_RTCP_INACT_INTVL_CNT 5
#define RTP_MAX_SDES_TYPE        9
#define RTP_MAX_SDES_ITEMS       10
#define RTP_VERSION_NUM          2

#define RTP_BYTE2_BIT_SIZE       16
#define RTP_BYTE_BIT_SIZE        8

#define RTP_DEF_BYE_PKT_SIZE     8
#define RTP_DEF_BYE_REASON_SIZE  0
#define RTP_SDES_HDR_SIZE        4
#define RTP_DEF_APP_PKT_SIZE     12
#define RTP_DEF_REP_BLK_SIZE     24
#define RTP_DEF_SR_SPEC_SIZE     20
#define RTP_MAX_RECEP_REP_CNT    31

#define RTP_CVO_XHDR_LEN         8

#define RTCP_RC_SHIFT_VAL        8  // 12
#define RTCP_PT_SHIFT_VAL        0  // 7
#define RTCP_FIXED_HDR_LEN       8

#define RTP_MAX_PAYLOAD_TYPE     4

/* RTP error codes*/
typedef enum
{
    RTP_FAILURE = 0,
    RTP_SUCCESS = 1,
    RTP_MEMORY_FAIL = 2,
    RTP_INVALID_PARAMS = 3,
    RTP_OWN_SSRC_COLLISION = 4,
    RTP_REMOTE_SSRC_COLLISION = 5,
    RTP_MTU_EXCEEDED = 6,
    RTP_NEW_SSRC_RCVD = 7,
    RTP_OLD_SSRC_RCVD,
    RTP_INVALID_LEN,
    RTP_DECODE_ERROR,
    RTP_ENCODE_ERROR,
    RTP_BAD_SEQ,
    RTCP_SR_MISSING,
    RTCP_FLAG_NOT_ENABLED,
    RTP_APP_IF_NOT_DEFINED,
    RTP_RCVD_CSRC_ENTRY,
    RTP_NO_RTCP_SUPPORT,
    RTP_TIMER_PROC_ERR,
    RTP_INVALID_MSG,
    RTP_RTCP_ALREADY_RUNNING
} eRTP_STATUS_CODE;

/**
 * The reason for member leave indication used in IRtpAppInterface::rtpMemberLeaveInd
 */
typedef enum
{
    RTP_BYE_RECEIVED = 0,
    RTP_PEER_SSRC_TIMEOUT = 1
} eRTP_LEAVE_REASON;

typedef enum
{
    RTCP_SDES_END = 0,
    RTCP_SDES_CNAME = 1,
    RTCP_SDES_NAME = 2,
    RTCP_SDES_EMAIL = 3,
    RTCP_SDES_PHONE = 4,
    RTCP_SDES_LOC = 5,
    RTCP_SDES_TOOL = 6,
    RTCP_SDES_NOTE = 7,
    RTCP_SDES_PRIV = 8
} eRTCP_SDES_TYPE;

typedef enum
{
    RTCP_SR = 200,
    RTCP_RR = 201,
    RTCP_SDES = 202,
    RTCP_BYE = 203,
    RTCP_APP = 204,
    RTCP_RTPFB = 205,
    RTCP_PSFB = 206,
    RTCP_XR = 207
} eRTCP_TYPE;

// It is the source information for seq and jitter calculation
typedef struct
{
    RtpDt_UInt16 usMaxSeq;        /* highest seq. number seen */
    RtpDt_UInt32 uiCycles;        /* shifted count of seq. number cycles */
    RtpDt_UInt32 uiBaseSeq;       /* base seq number */
    RtpDt_UInt32 uiBadSeq;        /* last 'bad' seq number + 1 */
    RtpDt_UInt32 uiProbation;     /* sequ. packets till source is valid */
    RtpDt_UInt32 uiReceived;      /* packets received */
    RtpDt_UInt32 uiExpectedPrior; /* packet expected at last interval */
    RtpDt_UInt32 uiReceivedPrior; /* packet received at last interval */
    RtpDt_Int32 uiTransit;        /* relative trans time for prev pkt */
    RtpDt_Double uiJitter;        /* estimated jitter */
                                  /* ... */
} tRTP_SOURCE;

// It describes the Source Description Item
typedef struct
{
    RtpDt_UChar ucType;
    RtpDt_UChar ucLength;
    RtpDt_UChar* pValue;
    RtpDt_UInt32 uiFreq;
} tRTCP_SDES_ITEM;

// It describes the RTCP-XR Packet
typedef struct
{
    RtpDt_UChar* m_pBlockBuffer;
    RtpDt_UInt16 nlength;
} tRTCP_XR_DATA;

// RTP parser
#define RTP_WORD_SIZE        4
#define RTP_FIXED_HDR_LEN    12
#define RTCP_SR_PACKET_LENGTH 20
#define RTP_VER_SHIFT_VAL    14
#define RTP_PAD_SHIFT_VAL    13
#define RTP_EXT_SHIFT_VAL    12
#define RTP_CC_SHIFT_VAL     8
#define RTP_MARK_SHIFT_VAL   7
#define RTP_PLTYPE_SHIFT_VAL 0
#define RTP_HEX_1_BIT_MAX    0x0001
#define RTP_HEX_4_BIT_MAX    0x000F
#define RTP_HEX_7_BIT_MAX    0x007F
#define RTP_HEX_8_BIT_MAX    0x00FF
#define RTP_HEX_16_BIT_MAX   0xFFFF
#define RTP_HEX_24_BIT_MAX   0x007fffff
#define RTP_HEX_24_BIT_MIN   0xFF800000

#define RTP_FORM_HDR_UTL(_usUtlData, _actData, _shiftNum, _destData) \
    {                                                                \
        (_usUtlData) = (_actData);                                   \
        (_usUtlData) = (_usUtlData) << (_shiftNum);                  \
        (_destData) = (_usUtlData) | (_destData);                    \
        (_usUtlData) = RTP_ZERO;                                     \
    }

#endif  //__RTP_GLOBAL_H__

/** @}*/

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

#include <RtpStackUtil.h>

RtpStackUtil::RtpStackUtil() {}

RtpStackUtil::~RtpStackUtil() {}

RtpDt_UInt16 RtpStackUtil::getSequenceNumber(IN RtpDt_UChar* pucRtpHdrBuf)
{
    if (pucRtpHdrBuf == nullptr)
    {
        return RTP_ZERO;
    }

    RtpDt_UInt32 uiByte4Data = RTP_ZERO;
    RtpDt_UInt16 usSeqNum = RTP_ZERO;

    uiByte4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpHdrBuf)));
    usSeqNum = (RtpDt_UInt16)(uiByte4Data & RTP_HEX_16_BIT_MAX);

    return usSeqNum;
}

RtpDt_UInt32 RtpStackUtil::getRtpSsrc(IN RtpDt_UChar* pucRtpBuf)
{
    if (pucRtpBuf == nullptr)
    {
        return RTP_ZERO;
    }

    RtpDt_UInt32 uiByte4Data = RTP_ZERO;
    pucRtpBuf = pucRtpBuf + RTP_EIGHT;

    uiByte4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpBuf)));
    return uiByte4Data;
}

RtpDt_UInt32 RtpStackUtil::getRtcpSsrc(IN RtpDt_UChar* pucRtcpBuf)
{
    if (pucRtcpBuf == nullptr)
    {
        return RTP_ZERO;
    }
    pucRtcpBuf = pucRtcpBuf + RTP_WORD_SIZE;

    RtpDt_UInt32 uiByte4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtcpBuf)));
    return uiByte4Data;
}

RtpDt_UInt32 RtpStackUtil::generateNewSsrc(IN RtpDt_UInt32 uiTermNum)
{
    RtpDt_UInt32 uiTmpRand = RTP_ZERO;

    uiTmpRand = RtpOsUtil::Rand();
    uiTmpRand = uiTmpRand << RTP_EIGHT;
    uiTmpRand = uiTmpRand & RTP_SSRC_GEN_UTL;
    uiTmpRand = uiTmpRand | uiTermNum;

    return uiTmpRand;
}

RtpDt_UInt32 RtpStackUtil::getMidFourOctets(IN tRTP_NTP_TIME* pstNtpTs)
{
    if (pstNtpTs == nullptr)
    {
        return RTP_ZERO;
    }

    RtpDt_UInt32 uiNtpTs = pstNtpTs->m_uiNtpHigh32Bits;
    uiNtpTs = uiNtpTs << RTP_BYTE2_BIT_SIZE;
    RtpDt_UInt32 uiNtpLowTs = pstNtpTs->m_uiNtpLow32Bits;
    uiNtpLowTs = uiNtpLowTs >> RTP_BYTE2_BIT_SIZE;
    uiNtpTs = uiNtpTs | uiNtpLowTs;
    return uiNtpTs;
}

RtpDt_UInt32 RtpStackUtil::calcRtpTimestamp(IN RtpDt_UInt32 uiPrevRtpTs,
        IN tRTP_NTP_TIME* pstCurNtpTs, IN tRTP_NTP_TIME* pstPrevNtpTs,
        IN RtpDt_UInt32 uiSamplingRate)
{
    if (pstCurNtpTs == nullptr || pstPrevNtpTs == nullptr)
    {
        return RTP_ZERO;
    }

    RtpDt_Int32 iTimeDiffHigh32Bits = RTP_ZERO;
    RtpDt_Int32 iTimeDiffLow32Bits = RTP_ZERO;

    if ((RTP_ZERO != pstPrevNtpTs->m_uiNtpHigh32Bits) ||
            (RTP_ZERO != pstPrevNtpTs->m_uiNtpLow32Bits))
    {
        iTimeDiffHigh32Bits = pstCurNtpTs->m_uiNtpHigh32Bits - pstPrevNtpTs->m_uiNtpHigh32Bits;
        iTimeDiffLow32Bits = (pstCurNtpTs->m_uiNtpLow32Bits / 4294UL) -
                (pstPrevNtpTs->m_uiNtpLow32Bits / 4294UL);
    }
    else
    {
        iTimeDiffHigh32Bits = RTP_ZERO;
        iTimeDiffLow32Bits = RTP_ZERO;
    }

    // calc iTimeDiff in millisec
    RtpDt_Int32 iTimeDiff = (iTimeDiffHigh32Bits * 1000 * 1000) + iTimeDiffLow32Bits;

    /* the time diff high bit is in seconds and
       the time diff low bit is in micro seconds */

    RtpDt_UInt32 uiNewRtpTs = RTP_ZERO;

    if (RTP_ZERO == iTimeDiff)
    {
        uiNewRtpTs = uiPrevRtpTs;
    }
    else
    {
        RtpDt_Int32 temp = uiSamplingRate / 1000;
        uiNewRtpTs = uiPrevRtpTs + (temp * iTimeDiff / 1000);
    }
    return uiNewRtpTs;
}

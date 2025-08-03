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

#include <RtpReceiverInfo.h>
#include <RtpStackUtil.h>
#include <RtpTrace.h>
#include <string.h>

RtpReceiverInfo::RtpReceiverInfo() :
        m_uiSsrc(RTP_ZERO),
        m_bSender(eRTP_FALSE),
        m_uiTotalRcvdRtpPkts(RTP_ZERO),
        m_uiTotalRcvdRtpOcts(RTP_ZERO),
        m_pobjIpAddr(nullptr),
        m_usPort(RTP_ZERO),
        m_bIsCsrcFlag(eRTP_FALSE),
        m_prevRtpTimestamp(RTP_ZERO),
        m_stPreSrTimestamp(RTP_ZERO),
        m_stLastSrNtpTimestamp(RTP_ZERO),
        m_bIsFirstRtp(eRTP_TRUE)

{
    // m_stPrevNtpTimestamp
    m_stPrevNtpTimestamp.m_uiNtpHigh32Bits = RTP_ZERO;
    m_stPrevNtpTimestamp.m_uiNtpLow32Bits = RTP_ZERO;
    // m_stRtpSource
    memset(&m_stRtpSource, RTP_ZERO, sizeof(tRTP_SOURCE));
    m_stRtpSource.uiProbation = RTP_MIN_SEQUENTIAL;
    m_stRtpSource.uiTransit = RTP_MIN_SEQUENTIAL;
}

RtpReceiverInfo::~RtpReceiverInfo()
{
    if (m_pobjIpAddr != nullptr)
    {
        delete m_pobjIpAddr;
        m_pobjIpAddr = nullptr;
    }
}

eRtp_Bool RtpReceiverInfo::getCsrcFlag()
{
    return m_bIsCsrcFlag;
}

RtpDt_Void RtpReceiverInfo::setCsrcFlag(IN eRtp_Bool bIsCsrcFlag)
{
    m_bIsCsrcFlag = bIsCsrcFlag;
}

RtpDt_UInt32 RtpReceiverInfo::findLostRtpPkts()
{
    RtpDt_UInt32 uiExtendedMax =
            (m_stRtpSource.uiCycles << RTP_BYTE2_BIT_SIZE) + m_stRtpSource.usMaxSeq;
    RtpDt_UInt32 uiExpected = (uiExtendedMax - m_stRtpSource.uiBaseSeq) + RTP_ONE;

    // The number of packets received includes any that are late or duplicated, and hence may
    // be greater than the number expected, so the cumulative number of packets lost may be negative
    RtpDt_Int32 uiLostRtpPkts = uiExpected - m_stRtpSource.uiReceived;

    // Restrict cumulative lost number to 24-bits
    if (uiLostRtpPkts > RTP_HEX_24_BIT_MAX)
        uiLostRtpPkts = RTP_HEX_24_BIT_MAX;
    else if (uiLostRtpPkts < (RtpDt_Int32)RTP_HEX_24_BIT_MIN)
        uiLostRtpPkts = RTP_HEX_24_BIT_MIN;
    return uiLostRtpPkts;
}  // findLostRtpPkts

RtpDt_UInt32 RtpReceiverInfo::getExtSeqNum()
{
    RtpDt_UInt32 uiExtSeqNum = (m_stRtpSource.uiCycles << RTP_BYTE2_BIT_SIZE);
    uiExtSeqNum = uiExtSeqNum | m_stRtpSource.usMaxSeq;
    return uiExtSeqNum;
}  // getExtSeqNum

RtpDt_Void RtpReceiverInfo::calcJitter(IN RtpDt_UInt32 uiRcvRtpTs, IN RtpDt_UInt32 uiSamplingRate)
{
    tRTP_NTP_TIME stCurNtpTimestamp;

    // get current NTP Timestamp
    RtpOsUtil::GetNtpTime(stCurNtpTimestamp);
    RtpDt_UInt32 uiCurRtpTimestamp = RtpStackUtil::calcRtpTimestamp(
            m_prevRtpTimestamp, &stCurNtpTimestamp, &m_stPrevNtpTimestamp, uiSamplingRate);
    // calculate arrival
    RtpDt_UInt32 uiArrival = uiCurRtpTimestamp;
    RtpDt_Int32 iTransit = uiArrival - uiRcvRtpTs;
    RtpDt_Int32 iDifference = iTransit - m_stRtpSource.uiTransit;
    m_stRtpSource.uiTransit = iTransit;
    if (iDifference < RTP_ZERO)
    {
        iDifference = -iDifference;
    }

    /*m_stRtpSource.uiJitter += iDifference -
                                ((m_stRtpSource.uiJitter + RTP_EIGHT) >> RTP_FOUR);*/
    // Alternate division logic as per RFC 3550 sec A.8
    if (m_bIsFirstRtp == eRTP_TRUE)
    {
        m_stRtpSource.uiJitter = RTP_ZERO;
        m_bIsFirstRtp = eRTP_FALSE;
    }
    else
    {
        m_stRtpSource.uiJitter += (1. / 16.) * ((RtpDt_Double)iDifference - m_stRtpSource.uiJitter);
    }

    m_stPrevNtpTimestamp = stCurNtpTimestamp;
    m_prevRtpTimestamp = uiCurRtpTimestamp;
    return;
}  // calcJitter

RtpDt_UInt16 RtpReceiverInfo::fractionLost()
{
    RtpDt_UInt32 uiExtendedMax =
            (m_stRtpSource.uiCycles << RTP_BYTE2_BIT_SIZE) + m_stRtpSource.usMaxSeq;
    RtpDt_UInt32 uiExpected = (uiExtendedMax - m_stRtpSource.uiBaseSeq) + RTP_ONE;
    RtpDt_UInt32 iExpIntvl = uiExpected - m_stRtpSource.uiExpectedPrior;

    m_stRtpSource.uiExpectedPrior = uiExpected;
    RtpDt_UInt32 uiRcvdIntvl = m_stRtpSource.uiReceived - m_stRtpSource.uiReceivedPrior;
    m_stRtpSource.uiReceivedPrior = m_stRtpSource.uiReceived;

    RtpDt_Int32 iLostIntvl = iExpIntvl - uiRcvdIntvl;
    RtpDt_UInt16 ucFraction = RTP_ZERO;

    if ((iExpIntvl == RTP_ZERO) || (iLostIntvl <= RTP_ZERO))
    {
        ucFraction = RTP_ZERO;
    }
    else
    {
        ucFraction = (iLostIntvl << RTP_BYTE_BIT_SIZE) / iExpIntvl;
    }
    return ucFraction;
}  // fractionLost

RtpDt_Void RtpReceiverInfo::initSeq(IN RtpDt_UInt16 usSeq)
{
    m_stRtpSource.uiBaseSeq = usSeq;
    m_stRtpSource.usMaxSeq = usSeq;
    m_stRtpSource.uiBadSeq = RTP_SEQ_MOD + RTP_ONE; /* so seq == bad_seq is false */
    m_stRtpSource.uiCycles = RTP_ZERO;
    m_stRtpSource.uiReceived = RTP_ZERO;
    m_stRtpSource.uiReceivedPrior = RTP_ZERO;
    m_stRtpSource.uiExpectedPrior = RTP_ZERO;
    /* other initialization */
}  // initSeq

RtpDt_UInt32 RtpReceiverInfo::updateSeq(IN RtpDt_UInt16 usSeq)
{
    RtpDt_UInt16 usDelta = usSeq - m_stRtpSource.usMaxSeq;

    /*
     * Source is not valid until RTP_MIN_SEQUENTIAL packets with
     * sequential sequence numbers have been received.
     */
    if (m_stRtpSource.uiProbation)
    {
        /* packet is in sequence */
        if (usSeq == m_stRtpSource.usMaxSeq + RTP_ONE)
        {
            m_stRtpSource.uiProbation--;
            m_stRtpSource.usMaxSeq = usSeq;
            if (m_stRtpSource.uiProbation == RTP_ZERO)
            {
                initSeq(usSeq);
                m_stRtpSource.uiReceived++;
                return RTP_ONE;
            }
        }
        else
        {
            m_stRtpSource.uiProbation = RTP_MIN_SEQUENTIAL - RTP_ONE;
            m_stRtpSource.usMaxSeq = usSeq;
        }
        return RTP_ZERO;
    }
    else if (usDelta < RTP_MAX_DROPOUT)
    {
        /* in order, with permissible gap */
        if (usSeq < m_stRtpSource.usMaxSeq)
        {
            /*
             * Sequence number wrapped - count another 64K cycle.
             */
            m_stRtpSource.uiCycles += RTP_ONE;
        }
        m_stRtpSource.usMaxSeq = usSeq;
    }
    else if (usDelta <= RTP_SEQ_MOD - RTP_MAX_MISORDER)
    {
        /* the sequence number made a very large jump */
        if (usSeq == m_stRtpSource.uiBadSeq)
        {
            /*
             * Two sequential packets -- assume that the other side
             * restarted without telling us so just re-sync
             * (i.e., pretend this was the first packet).
             */
            initSeq(usSeq);
        }
        else
        {
            m_stRtpSource.uiBadSeq = (usSeq + RTP_ONE) & (RTP_SEQ_MOD - RTP_ONE);
            return RTP_ZERO;
        }
    }
    else
    {
        /* duplicate or reordered packet */
    }
    m_stRtpSource.uiReceived++;
    return RTP_ONE;
}  // updateSeq

RtpDt_UInt32 RtpReceiverInfo::getSsrc()
{
    return m_uiSsrc;
}

RtpDt_Void RtpReceiverInfo::setSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

eRtp_Bool RtpReceiverInfo::isSender()
{
    return m_bSender;
}

RtpDt_Void RtpReceiverInfo::setSenderFlag(IN eRtp_Bool bSender)
{
    m_bSender = bSender;
}

RtpDt_UInt32 RtpReceiverInfo::getTotalRcvdRtpPkts()
{
    return m_uiTotalRcvdRtpPkts;
}

RtpDt_Void RtpReceiverInfo::incrTotalRcvdRtpPkts()
{
    m_uiTotalRcvdRtpPkts += RTP_ONE;
}

RtpDt_Void RtpReceiverInfo::incrTotalRcvdRtpOcts(IN RtpDt_UInt32 uiRcvdOcts)
{
    m_uiTotalRcvdRtpOcts += uiRcvdOcts;
}

RtpBuffer* RtpReceiverInfo::getIpAddr()
{
    return m_pobjIpAddr;
}

eRTP_STATUS_CODE RtpReceiverInfo::setIpAddr(IN RtpBuffer* pobjIpAddr)
{
    RtpDt_UChar* pBuffer = pobjIpAddr->getBuffer();
    RtpDt_UInt32 uiLength = pobjIpAddr->getLength();
    m_pobjIpAddr = new RtpBuffer(uiLength, pBuffer);
    if (m_pobjIpAddr == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_MEMORY_FAIL;
    }
    return RTP_SUCCESS;
}

RtpDt_UInt16 RtpReceiverInfo::getPort()
{
    return m_usPort;
}

RtpDt_Void RtpReceiverInfo::setPort(IN RtpDt_UInt16 usPort)
{
    m_usPort = usPort;
}

RtpDt_Void RtpReceiverInfo::setpreSrTimestamp(IN tRTP_NTP_TIME* pstNtpTs)
{
    m_stPreSrTimestamp = RtpStackUtil::getMidFourOctets(pstNtpTs);
}

RtpDt_Void RtpReceiverInfo::setLastSrNtpTimestamp(IN tRTP_NTP_TIME* pstNtpTs)
{
    m_stLastSrNtpTimestamp = RtpStackUtil::getMidFourOctets(pstNtpTs);
}

RtpDt_Void RtpReceiverInfo::setprevRtpTimestamp(IN RtpDt_UInt32 pstRtpTs)
{
    m_prevRtpTimestamp = pstRtpTs;
}

RtpDt_Void RtpReceiverInfo::setprevNtpTimestamp(IN tRTP_NTP_TIME* pstNtpTs)
{
    m_stPrevNtpTimestamp.m_uiNtpHigh32Bits = pstNtpTs->m_uiNtpHigh32Bits;
    m_stPrevNtpTimestamp.m_uiNtpLow32Bits = pstNtpTs->m_uiNtpLow32Bits;
}

RtpDt_UInt32 RtpReceiverInfo::delaySinceLastSR()
{
    tRTP_NTP_TIME stCurNtpTimestamp = {RTP_ZERO, RTP_ZERO};
    RtpDt_UInt32 dDifference = RTP_ZERO;

    if (m_stLastSrNtpTimestamp == RTP_ZERO)
    {
        return RTP_ZERO;
    }
    RtpOsUtil::GetNtpTime(stCurNtpTimestamp);
    stCurNtpTimestamp.m_uiNtpHigh32Bits = RtpStackUtil::getMidFourOctets(&stCurNtpTimestamp);
    dDifference = stCurNtpTimestamp.m_uiNtpHigh32Bits - m_stLastSrNtpTimestamp;
    return dDifference;
}  // delaySinceLastSR

eRTP_STATUS_CODE RtpReceiverInfo::populateReportBlock(IN RtcpReportBlock* pobjRepBlk)
{
    // ssrc
    pobjRepBlk->setSsrc(m_uiSsrc);
    // jitter
    RtpDt_UInt32 uiJitter = (RtpDt_UInt32)m_stRtpSource.uiJitter;
    pobjRepBlk->setJitter(uiJitter);
    // uiJitter = uiJitter >> RTP_FOUR; // as per second logic

    // fraction lost
    pobjRepBlk->setFracLost((RtpDt_UChar)fractionLost());
    // cumulative number of packets lost
    pobjRepBlk->setCumNumPktLost(findLostRtpPkts());
    // extensible highest sequence number
    pobjRepBlk->setExtHighSeqRcv(getExtSeqNum());

    // Last SR timestamp
    RtpDt_UInt32 uiLSR = m_stPreSrTimestamp;
    pobjRepBlk->setLastSR(uiLSR);
    // delay since last sr
    pobjRepBlk->setDelayLastSR(delaySinceLastSR());

    return RTP_SUCCESS;
}  // populateReportBlock

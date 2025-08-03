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

#include <RtcpReportBlock.h>

RtcpReportBlock::RtcpReportBlock() :
        m_uiSsrc(RTP_ZERO),
        m_ucFracLost(RTP_ZERO),
        m_uiCumNumPktLost(RTP_ZERO),
        m_uiExtHighSeqRcv(RTP_ZERO),
        m_uiJitter(RTP_ZERO),
        m_uiLastSR(RTP_ZERO),
        m_uiDelayLastSR(RTP_ZERO)
{
}

RtcpReportBlock::~RtcpReportBlock() {}

RtpDt_Void RtcpReportBlock::setSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

RtpDt_UInt32 RtcpReportBlock::getSsrc()
{
    return m_uiSsrc;
}

RtpDt_Void RtcpReportBlock::setFracLost(IN RtpDt_UChar ucFracLost)
{
    m_ucFracLost = ucFracLost;
}

RtpDt_UChar RtcpReportBlock::getFracLost()
{
    return m_ucFracLost;
}

RtpDt_Void RtcpReportBlock::setCumNumPktLost(IN RtpDt_Int32 uiCumNumPktLost)
{
    m_uiCumNumPktLost = uiCumNumPktLost;
}

RtpDt_Int32 RtcpReportBlock::getCumNumPktLost()
{
    return m_uiCumNumPktLost;
}

RtpDt_Void RtcpReportBlock::setExtHighSeqRcv(IN RtpDt_UInt32 uiExtHighSeqRcv)
{
    m_uiExtHighSeqRcv = uiExtHighSeqRcv;
}

RtpDt_UInt32 RtcpReportBlock::getExtHighSeqRcv()
{
    return m_uiExtHighSeqRcv;
}

RtpDt_Void RtcpReportBlock::setJitter(IN RtpDt_UInt32 uiJitter)
{
    m_uiJitter = uiJitter;
}

RtpDt_UInt32 RtcpReportBlock::getJitter()
{
    return m_uiJitter;
}

RtpDt_Void RtcpReportBlock::setLastSR(IN RtpDt_UInt32 uiLastSR)
{
    m_uiLastSR = uiLastSR;
}

RtpDt_UInt32 RtcpReportBlock::getLastSR()
{
    return m_uiLastSR;
}

RtpDt_Void RtcpReportBlock::setDelayLastSR(IN RtpDt_UInt32 uiDelayLastSR)
{
    m_uiDelayLastSR = uiDelayLastSR;
}

RtpDt_UInt32 RtcpReportBlock::getDelayLastSR()
{
    return m_uiDelayLastSR;
}

eRtp_Bool RtcpReportBlock::decodeReportBlock(IN RtpDt_UChar* pcRepBlkBuf)
{
    // SSRC
    m_uiSsrc = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));
    pcRepBlkBuf = pcRepBlkBuf + RTP_WORD_SIZE;

    RtpDt_UInt32 uiTemp4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));
    pcRepBlkBuf = pcRepBlkBuf + RTP_WORD_SIZE;

    // cumulative number of packets lost (3 bytes)
    m_uiCumNumPktLost = uiTemp4Data & 0x00FFFFFF;
    // fraction lost (1 byte)
    uiTemp4Data = uiTemp4Data >> RTP_24;
    m_ucFracLost = (RtpDt_UChar)(uiTemp4Data & 0x000000FF);

    // extended highest sequence number received
    m_uiExtHighSeqRcv = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));
    pcRepBlkBuf = pcRepBlkBuf + RTP_WORD_SIZE;

    // interarrival jitter
    m_uiJitter = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));
    pcRepBlkBuf = pcRepBlkBuf + RTP_WORD_SIZE;

    // last SR
    m_uiLastSR = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));
    pcRepBlkBuf = pcRepBlkBuf + RTP_WORD_SIZE;

    // delay since last SR
    m_uiDelayLastSR = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRepBlkBuf)));

    return eRTP_SUCCESS;
}  // decodeReportBlock

eRtp_Bool RtcpReportBlock::formReportBlock(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
    pucBuffer = pucBuffer + uiCurPos;

    // m_uiSsrc
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiSsrc);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_ucFracLost
    RtpDt_UInt32 uiTempData = m_ucFracLost;
    uiTempData = uiTempData << RTP_24;
    // m_uiCumNumPktLost
    //  consider only 24-bits of uiCumNumPktLost
    uiTempData = uiTempData | (m_uiCumNumPktLost & 0X00FFFFFF);

    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(uiTempData);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_uiExtHighSeqRcv
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiExtHighSeqRcv);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_uiJitter
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiJitter);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_uiLastSR
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiLastSR);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_uiDelayLastSR
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiDelayLastSR);
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    pobjRtcpPktBuf->setLength(uiCurPos);

    return eRTP_SUCCESS;
}  // formReportBlock

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

#include <RtpHeader.h>
#include <RtpTrace.h>
#include <RtpError.h>

RtpHeader::RtpHeader() :
        m_ucVersion(RTP_ZERO),
        m_ucPadding(RTP_ZERO),
        m_ucExtension(RTP_ZERO),
        m_ucCsrcCount(RTP_ZERO),
        m_uiCsrcList(std::list<RtpDt_UInt32>()),
        m_ucMarker(RTP_ZERO),
        m_ucPayloadType(RTP_ZERO),
        m_usSequenceNumber(RTP_ZERO),
        m_uiTimestamp(RTP_ZERO),
        m_uiSsrc(RTP_ZERO)

{
}

RtpHeader::~RtpHeader()
{
    m_uiCsrcList.clear();
}

RtpDt_Void RtpHeader::setVersion(IN RtpDt_UChar ucVersion)
{
    m_ucVersion = ucVersion;
}

RtpDt_UChar RtpHeader::getVersion()
{
    return m_ucVersion;
}

RtpDt_Void RtpHeader::setPadding()
{
    m_ucPadding = RTP_ONE;
}

RtpDt_UChar RtpHeader::getPadding()
{
    return m_ucPadding;
}

RtpDt_Void RtpHeader::setExtension(RtpDt_UChar ext)
{
    m_ucExtension = ext;
}

RtpDt_UChar RtpHeader::getExtension()
{
    return m_ucExtension;
}

RtpDt_Void RtpHeader::setCsrcCount(IN RtpDt_UChar ucCsrcCount)
{
    m_ucCsrcCount = ucCsrcCount;
}

RtpDt_UChar RtpHeader::getCsrcCount()
{
    return m_ucCsrcCount;
}

std::list<RtpDt_UInt32>& RtpHeader::getCsrcList()
{
    return m_uiCsrcList;
}

RtpDt_Void RtpHeader::addElementToCsrcList(IN RtpDt_UInt32 uiCsrc)
{
    // append uiCsrc into list.
    m_uiCsrcList.push_back(uiCsrc);
    RTP_TRACE_MESSAGE("CsrcList[%d] = %d", m_uiCsrcList.size(), uiCsrc);
    return;
}

RtpDt_Void RtpHeader::setMarker()
{
    m_ucMarker = RTP_ONE;
}

RtpDt_UChar RtpHeader::getMarker()
{
    return m_ucMarker;
}

RtpDt_Void RtpHeader::setPayloadType(IN RtpDt_UChar ucPayloadType)
{
    m_ucPayloadType = ucPayloadType;
}

RtpDt_UChar RtpHeader::getPayloadType()
{
    return m_ucPayloadType;
}

RtpDt_Void RtpHeader::setSequenceNumber(IN RtpDt_UInt16 usSequenceNumber)
{
    m_usSequenceNumber = usSequenceNumber;
}

RtpDt_UInt16 RtpHeader::getSequenceNumber()
{
    return m_usSequenceNumber;
}

RtpDt_Void RtpHeader::setRtpTimestamp(IN RtpDt_UInt32 uiTimestamp)
{
    m_uiTimestamp = uiTimestamp;
}

RtpDt_UInt32 RtpHeader::getRtpTimestamp()
{
    return m_uiTimestamp;
}

RtpDt_Void RtpHeader::setRtpSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

RtpDt_UInt32 RtpHeader::getRtpSsrc()
{
    return m_uiSsrc;
}

eRtp_Bool RtpHeader::formHeader(IN RtpBuffer* pobjRtpPktBuf)
{
    RtpDt_UInt16 usTmpData = RTP_ZERO;
    RtpDt_UInt16 usUtlData = RTP_ZERO;

    // get RtpBuffer data
    RtpDt_UChar* pucRtpHeaderBuffer = pobjRtpPktBuf->getBuffer();

    // version 2 bits
    RTP_FORM_HDR_UTL(usUtlData, m_ucVersion, RTP_VER_SHIFT_VAL, usTmpData);

    // padding 1 bit
    RTP_FORM_HDR_UTL(usUtlData, m_ucPadding, RTP_PAD_SHIFT_VAL, usTmpData);

    // extension 1 bit
    RTP_FORM_HDR_UTL(usUtlData, m_ucExtension, RTP_EXT_SHIFT_VAL, usTmpData);

    // CC. CSRC count 4 bits.
    RTP_FORM_HDR_UTL(usUtlData, m_ucCsrcCount, RTP_CC_SHIFT_VAL, usTmpData);

    // Marker. 1 bit
    RTP_FORM_HDR_UTL(usUtlData, m_ucMarker, RTP_MARK_SHIFT_VAL, usTmpData);

    // payload type. 7 bits
    RTP_FORM_HDR_UTL(usUtlData, m_ucPayloadType, RTP_PLTYPE_SHIFT_VAL, usTmpData);

    RtpDt_UInt32 uiByte4Data = usTmpData;
    uiByte4Data = uiByte4Data << RTP_SIXTEEN;

    // sequence number. 16 bits
    uiByte4Data = uiByte4Data | m_usSequenceNumber;

    *(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)) = RtpOsUtil::Ntohl(uiByte4Data);
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_WORD_SIZE;

    // time stamp
    *(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)) = RtpOsUtil::Ntohl(m_uiTimestamp);
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_WORD_SIZE;

    // ssrc
    *(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)) = RtpOsUtil::Ntohl(m_uiSsrc);
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_WORD_SIZE;

    RtpDt_UInt32 uiBufLen = RTP_FIXED_HDR_LEN;

    // csrc list
    for (auto csrc : m_uiCsrcList)
    {
        *(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)) = RtpOsUtil::Ntohl(csrc);
        pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_WORD_SIZE;
    }
    RtpDt_UInt16 usSize = m_uiCsrcList.size();

    uiBufLen += (RTP_WORD_SIZE * usSize);

    pobjRtpPktBuf->setLength(uiBufLen);

    return eRTP_TRUE;
}

eRtp_Bool RtpHeader::decodeHeader(IN RtpBuffer* pobjRtpPktBuf, OUT RtpDt_UInt32& uiBufPos)
{
    // get RtpBuffer data
    RtpDt_UChar* pucRtpHeaderBuffer = pobjRtpPktBuf->getBuffer();
    RtpDt_UInt32 uiRtpBufferLength = pobjRtpPktBuf->getLength();

    if (uiRtpBufferLength < RTP_FIXED_HDR_LEN)
    {
        RTP_TRACE_ERROR("Invalid Rtp packet: Expected minimum Rtp packet length[%d], Received[%d]",
                RTP_FIXED_HDR_LEN, uiRtpBufferLength);
        return eRTP_FALSE;
    }

    RtpDt_UInt32 uiByte4Data =
            RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)));
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_FOUR;
    uiBufPos = uiBufPos + RTP_WORD_SIZE;

    // version 2 bits
    RtpDt_UInt16 usUtl2Data = (RtpDt_UInt16)(uiByte4Data >> RTP_SIXTEEN);
    m_ucVersion = (RtpDt_UChar)(usUtl2Data >> RTP_VER_SHIFT_VAL);

    // padding 1 bit
    m_ucPadding = (RtpDt_UChar)((usUtl2Data >> RTP_PAD_SHIFT_VAL) & RTP_HEX_1_BIT_MAX);

    // extension 1 bit
    m_ucExtension = (RtpDt_UChar)((usUtl2Data >> RTP_EXT_SHIFT_VAL) & RTP_HEX_1_BIT_MAX);

    // CC. CSRC count 4 bits.
    m_ucCsrcCount = (RtpDt_UChar)((usUtl2Data >> RTP_CC_SHIFT_VAL) & RTP_HEX_4_BIT_MAX);

    // Marker. 1 bit
    usUtl2Data = usUtl2Data & RTP_HEX_8_BIT_MAX;
    m_ucMarker = (RtpDt_UChar)((usUtl2Data >> RTP_MARK_SHIFT_VAL) & RTP_HEX_1_BIT_MAX);

    // payload type. 7 bits
    m_ucPayloadType = (RtpDt_UChar)((usUtl2Data)&RTP_HEX_7_BIT_MAX);

    // sequence number. 16 bits
    m_usSequenceNumber = (RtpDt_UInt16)(uiByte4Data & RTP_HEX_16_BIT_MAX);

    // timestamp
    m_uiTimestamp = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)));
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_FOUR;
    uiBufPos = uiBufPos + RTP_WORD_SIZE;

    // Synchronization source
    m_uiSsrc = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer)));
    pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_FOUR;
    uiBufPos = uiBufPos + RTP_WORD_SIZE;

    if (m_ucCsrcCount != 0)
    {
        RtpDt_UInt32 uiExpectedLength = RTP_FIXED_HDR_LEN + (m_ucCsrcCount * RTP_WORD_SIZE);
        if (uiRtpBufferLength < uiExpectedLength)
        {
            RTP_TRACE_ERROR(
                    "Invalid Rtp packet: Expected minimum Rtp packet length[%d], but received[%d]",
                    uiExpectedLength, uiRtpBufferLength);
            return eRTP_FALSE;
        }

        // csrc list
        for (RtpDt_UInt32 usCsrcIdx = RTP_ZERO; usCsrcIdx < m_ucCsrcCount; usCsrcIdx++)
        {
            addElementToCsrcList(
                    RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucRtpHeaderBuffer))));
            pucRtpHeaderBuffer = pucRtpHeaderBuffer + RTP_FOUR;
            uiBufPos = uiBufPos + RTP_WORD_SIZE;
        }
    }

    return eRTP_TRUE;
}

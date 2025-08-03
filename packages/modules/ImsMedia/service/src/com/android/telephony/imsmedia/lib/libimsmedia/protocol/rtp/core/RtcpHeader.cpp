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

#include <RtcpHeader.h>
#include <RtpTrace.h>

RtcpHeader::RtcpHeader() :
        m_ucVersion(RTP_ZERO),
        m_ucIsPadding(eRTP_FALSE),
        m_ucReceptionReportCount(RTP_ZERO),
        m_ucPacketType(RTP_ZERO),
        m_usLength(RTP_ZERO),
        m_uiSsrc(RTP_ZERO)
{
}

RtcpHeader::~RtcpHeader() {}

eRtp_Bool RtcpHeader::setVersion(IN RtpDt_UChar ucVersion)
{
    if (ucVersion > MAX_RTP_VERSION)
    {
        RTP_TRACE_ERROR("Invalid RTP version %d", ucVersion, 0);
        return eRTP_FALSE;
    }

    m_ucVersion = ucVersion;
    return eRTP_TRUE;
}

RtpDt_UChar RtcpHeader::getVersion()
{
    return m_ucVersion;
}

RtpDt_Void RtcpHeader::setPadding(eRtp_Bool padding)
{
    m_ucIsPadding = padding;
}

eRtp_Bool RtcpHeader::getPadding()
{
    return m_ucIsPadding;
}

eRtp_Bool RtcpHeader::setReceptionReportCount(IN RtpDt_UChar ucReceptionReportCount)
{
    if (ucReceptionReportCount > MAX_RECEPTION_REPORT_COUNT)
    {
        RTP_TRACE_ERROR("Invalid Reception Report Count %d", ucReceptionReportCount, 0);
        return eRTP_FALSE;
    }

    m_ucReceptionReportCount = ucReceptionReportCount;
    return eRTP_TRUE;
}

RtpDt_UChar RtcpHeader::getReceptionReportCount()
{
    return m_ucReceptionReportCount;
}

RtpDt_Void RtcpHeader::setPacketType(IN RtpDt_UChar ucPacketType)
{
    m_ucPacketType = ucPacketType;
}

RtpDt_UChar RtcpHeader::getPacketType()
{
    return m_ucPacketType;
}

RtpDt_Void RtcpHeader::setLength(IN RtpDt_UInt32 usLength)
{
    m_usLength = usLength;
}

RtpDt_UInt32 RtcpHeader::getLength()
{
    return m_usLength;
}

RtpDt_Void RtcpHeader::setSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

RtpDt_UInt32 RtcpHeader::getSsrc()
{
    return m_uiSsrc;
}

bool RtcpHeader::operator==(const RtcpHeader& objRtcpHeader) const
{
    return (m_ucVersion == objRtcpHeader.m_ucVersion &&
            m_ucIsPadding == objRtcpHeader.m_ucIsPadding &&
            m_ucReceptionReportCount == objRtcpHeader.m_ucReceptionReportCount &&
            m_ucPacketType == objRtcpHeader.m_ucPacketType &&
            m_usLength == objRtcpHeader.m_usLength && m_uiSsrc == objRtcpHeader.m_uiSsrc);
}

eRtp_Bool RtcpHeader::decodeRtcpHeader(IN RtpDt_UChar* pRtcpBuffer, RtpDt_Int32 length)
{
    if (length < RTP_WORD_SIZE)
        return eRTP_FALSE;

    RtpDt_UInt32 uiTemp4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pRtcpBuffer)));

    // Packet Length
    m_usLength = uiTemp4Data & 0x0000FFFF;
    m_usLength *= RTP_WORD_SIZE;  // Convert length from WORD count to Bytes count

    // Packet Type
    m_ucPacketType = (uiTemp4Data >> RTP_16) & 0x000000FF;

    // 8-MSB bits of 32-bit data.
    uiTemp4Data = uiTemp4Data >> RTP_24;

    // version. 2-MSB bits
    m_ucVersion = (RtpDt_UInt8)(uiTemp4Data >> RTP_SIX) & 0x00000003;

    // padding
    m_ucIsPadding = ((uiTemp4Data >> RTP_FIVE) & 0x00000001) ? eRTP_TRUE : eRTP_FALSE;

    // RC
    m_ucReceptionReportCount = RtpDt_UInt8(uiTemp4Data & 0x0000001F);

    // SSRC
    if (m_usLength)
    {
        pRtcpBuffer = pRtcpBuffer + RTP_WORD_SIZE;
        m_uiSsrc = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pRtcpBuffer)));
    }

    return eRTP_SUCCESS;
}  // decodeRtcpHeader

eRtp_Bool RtcpHeader::formRtcpHeader(OUT RtpBuffer* pobjRtcpPktBuf)
{
    // do partial encoding
    formPartialRtcpHeader(pobjRtcpPktBuf);

    RtpDt_UChar* pcRtcpHdrBuf = pobjRtcpPktBuf->getBuffer();
    RtpDt_UInt32 uiBufPos = pobjRtcpPktBuf->getLength();
    pcRtcpHdrBuf = pcRtcpHdrBuf + uiBufPos;

    // ssrc
    *(reinterpret_cast<RtpDt_UInt32*>(pcRtcpHdrBuf)) = RtpOsUtil::Ntohl(m_uiSsrc);

    uiBufPos = uiBufPos + RTP_WORD_SIZE;
    pobjRtcpPktBuf->setLength(uiBufPos);

    return eRTP_SUCCESS;
}  // formRtcpHeader

eRtp_Bool RtcpHeader::formPartialRtcpHeader(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt16 usUtlData = RTP_ZERO;
    RtpDt_UInt16 usTmpData = RTP_ZERO;
    RtpDt_UChar* pcRtcpHdrBuf = pobjRtcpPktBuf->getBuffer();
    RtpDt_UInt32 uiBufPos = pobjRtcpPktBuf->getLength();

    pcRtcpHdrBuf = pcRtcpHdrBuf + uiBufPos;

    // version 2 bits
    RTP_FORM_HDR_UTL(usUtlData, m_ucVersion, RTP_VER_SHIFT_VAL, usTmpData);
    // padding 1 bit
    RTP_FORM_HDR_UTL(usUtlData, m_ucIsPadding, RTP_PAD_SHIFT_VAL, usTmpData);
    // RC 5 bits
    RTP_FORM_HDR_UTL(usUtlData, m_ucReceptionReportCount, RTCP_RC_SHIFT_VAL, usTmpData);
    // PT 8 bits
    RTP_FORM_HDR_UTL(usUtlData, m_ucPacketType, RTCP_PT_SHIFT_VAL, usTmpData);

    RtpDt_UInt32 uiByte4Data = usTmpData;
    uiByte4Data = uiByte4Data << RTP_SIXTEEN;

    // convert m_usLength into words - 1
    m_usLength = m_usLength / RTP_WORD_SIZE;
    m_usLength = m_usLength - RTP_ONE;

    // length 16 bits
    uiByte4Data = uiByte4Data | m_usLength;

    *(reinterpret_cast<RtpDt_UInt32*>(pcRtcpHdrBuf)) = RtpOsUtil::Ntohl(uiByte4Data);

    uiBufPos = uiBufPos + RTP_WORD_SIZE;
    pobjRtcpPktBuf->setLength(uiBufPos);

    return eRTP_SUCCESS;

}  // end formPartialRtcpHeader

RtpDt_Void RtcpHeader::populateRtcpHeader(
        IN RtpDt_UChar ucReceptionReportCount, IN RtpDt_UChar ucPacketType, IN RtpDt_UInt32 uiSsrc)
{
    m_ucVersion = RTP_VERSION_NUM;
    m_ucReceptionReportCount = ucReceptionReportCount;
    m_ucPacketType = ucPacketType;
    m_uiSsrc = uiSsrc;
}

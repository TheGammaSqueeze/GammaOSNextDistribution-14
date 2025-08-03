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

#include <RtcpSrPacket.h>
#include <RtpTrace.h>

/*********************************************************
 * Function name        : RtcpSrPacket
 * Description          : Constructor
 * Return type          : None
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtcpSrPacket::RtcpSrPacket() :
        m_uiRtpTimestamp(RTP_ZERO),
        m_uiSendPktCount(RTP_ZERO),
        m_uiSendOctCount(RTP_ZERO)
{
    memset(&m_stNtpTimestamp, RTP_ZERO, sizeof(tRTP_NTP_TIME));
}

/*********************************************************
 * Function name        : ~RtcpSrPacket
 * Description          : Destructor
 * Return type          : None
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtcpSrPacket::~RtcpSrPacket() {}

RtpDt_Void RtcpSrPacket::setRtcpHdrInfo(RtcpHeader& rtcpHeader)
{
    RtcpHeader* pobjRtcpHdr = m_objRrPkt.getRtcpHdrInfo();
    *pobjRtcpHdr = rtcpHeader;
}

RtcpHeader* RtcpSrPacket::getRtcpHdrInfo()
{
    return m_objRrPkt.getRtcpHdrInfo();
}

/*********************************************************
 * Function name        : getRrPktInfo
 * Description          : get method for m_objRrPkt
 * Return type          : RtcpRrPacket*
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtcpRrPacket* RtcpSrPacket::getRrPktInfo()
{
    return &m_objRrPkt;
}

/*********************************************************
 * Function name        : getNtpTime
 * Description          : get method for m_stNtpTimestamp
 * Return type          : tRTP_NTP_TIME*
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
tRTP_NTP_TIME* RtcpSrPacket::getNtpTime()
{
    return &m_stNtpTimestamp;
}

/*********************************************************
 * Function name        : setRtpTimestamp
 * Description          : set method for m_uiRtpTimestamp
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 *                            RTP timestamp
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtcpSrPacket::setRtpTimestamp(IN RtpDt_UInt32 uiRtpTimestamp)
{
    m_uiRtpTimestamp = uiRtpTimestamp;
}

/*********************************************************
 * Function name        : getRtpTimestamp
 * Description          : get method for m_uiRtpTimestamp
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtcpSrPacket::getRtpTimestamp()
{
    return m_uiRtpTimestamp;
}

/*********************************************************
 * Function name        : setSendPktCount
 * Description          : set method for m_uiSendPktCount
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtcpSrPacket::setSendPktCount(IN RtpDt_UInt32 uiPktCount)
{
    m_uiSendPktCount = uiPktCount;
}

/*********************************************************
 * Function name        : getSendPktCount
 * Description          : get method for m_uiSendPktCount
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtcpSrPacket::getSendPktCount()
{
    return m_uiSendPktCount;
}

/*********************************************************
 * Function name        : setSendOctetCount
 * Description          : set method for m_uiSendOctCount
 * Return type          : RtpDt_Void
 * Argument             : RtpDt_UInt32 : In
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_Void RtcpSrPacket::setSendOctetCount(IN RtpDt_UInt32 uiOctetCount)
{
    m_uiSendOctCount = uiOctetCount;
}

/*********************************************************
 * Function name        : getSendOctetCount
 * Description          : get method for m_uiSendOctCount
 * Return type          : RtpDt_UInt32
 * Argument             : None
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
RtpDt_UInt32 RtcpSrPacket::getSendOctetCount()
{
    return m_uiSendOctCount;
}

/*********************************************************
 * Function name        : decodeSrPacket
 * Description          : Decodes and stores the information of the RTCP SR packet
 * Return type          : eRtp_Bool : eRTP_SUCCESS on successful decoding
 * Argument             : RtpDt_UChar* : In
 * Argument                : RtpDt_UInt32 : In
 *                            RTCP SR packet length
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
eRTP_STATUS_CODE RtcpSrPacket::decodeSrPacket(
        IN RtpDt_UChar* pucSrPktBuf, IN RtpDt_UInt16 usSrPktLen, IN RtpDt_UInt16 usExtHdrLen)
{
    /*
            0                   1                   2                   3
            0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    header |V=2|P|    RC   |   PT=SR=200   |             length            |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                         SSRC of sender                        |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
    sender |              NTP timestamp, most significant word             |
    info   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |             NTP timestamp, least significant word             |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                         RTP timestamp                         |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                     sender's packet count                     |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                      sender's octet count                     |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
    report |                 SSRC_1 (SSRC of first source)                 |
    block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      1    | fraction lost |       cumulative number of packets lost       |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |           extended highest sequence number received           |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                      interarrival jitter                      |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                         last SR (LSR)                         |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
           |                   delay since last SR (DLSR)                  |
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
    report |                 SSRC_2 (SSRC of second source)                |
    block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      2    :                               ...                             :
           +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
           |                  profile-specific extensions                  |
           +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    */

    if (pucSrPktBuf == nullptr || usSrPktLen < RTCP_SR_PACKET_LENGTH)
        return RTP_FAILURE;

    // NTP timestamp most significant word
    m_stNtpTimestamp.m_uiNtpHigh32Bits =
            RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucSrPktBuf)));
    pucSrPktBuf = pucSrPktBuf + RTP_WORD_SIZE;
    usSrPktLen = usSrPktLen - RTP_WORD_SIZE;

    // NTP timestamp least significant word
    m_stNtpTimestamp.m_uiNtpLow32Bits =
            RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucSrPktBuf)));
    pucSrPktBuf = pucSrPktBuf + RTP_WORD_SIZE;
    usSrPktLen = usSrPktLen - RTP_WORD_SIZE;

    // RTP timestamp
    m_uiRtpTimestamp = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucSrPktBuf)));
    pucSrPktBuf = pucSrPktBuf + RTP_WORD_SIZE;
    usSrPktLen = usSrPktLen - RTP_WORD_SIZE;

    // sender's packet count
    m_uiSendPktCount = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucSrPktBuf)));
    pucSrPktBuf = pucSrPktBuf + RTP_WORD_SIZE;
    usSrPktLen = usSrPktLen - RTP_WORD_SIZE;

    // sender's octet count
    m_uiSendOctCount = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucSrPktBuf)));
    pucSrPktBuf = pucSrPktBuf + RTP_WORD_SIZE;
    usSrPktLen = usSrPktLen - RTP_WORD_SIZE;

    // decode report block
    eRTP_STATUS_CODE eDecodeRes = RTP_FAILURE;
    eDecodeRes = m_objRrPkt.decodeRrPacket(pucSrPktBuf, usSrPktLen, usExtHdrLen);
    if (eDecodeRes != RTP_SUCCESS)
    {
        RTP_TRACE_WARNING(
                "RtcpPacket::decodeRtcpPacket, RR packet Decoding Error[%d]", eDecodeRes, RTP_ZERO);
        return eDecodeRes;
    }

    return RTP_SUCCESS;
}  // decodeSrPacket

/*********************************************************
 * Function name        : formSrPacket
 * Description          : Performs the encoding of the RTCP SR packet.
 * Return type          : eRtp_Bool : eRTP_SUCCESS on successful encoding
 * Argument             : RtpBuffer* : Out
 * Preconditions        : None
 * Side Effects            : None
 ********************************************************/
eRTP_STATUS_CODE RtcpSrPacket::formSrPacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    /*
        0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
header |V=2|P|    RC   |   PT=SR=200   |             length            |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         SSRC of sender                        |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
sender |              NTP timestamp, most significant word             |
info   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |             NTP timestamp, least significant word             |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         RTP timestamp                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                     sender's packet count                     |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      sender's octet count                     |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_1 (SSRC of first source)                 |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  1    | fraction lost |       cumulative number of packets lost       |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |           extended highest sequence number received           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                      interarrival jitter                      |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                         last SR (LSR)                         |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |                   delay since last SR (DLSR)                  |
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
report |                 SSRC_2 (SSRC of second source)                |
block  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  2    :                               ...                             :
       +=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
       |                  profile-specific extensions                  |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    */

    RTP_TRACE_MESSAGE("formSrPacket", 0, 0);
    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
    uiCurPos = uiCurPos + RTCP_FIXED_HDR_LEN;
    pucBuffer = pucBuffer + RTCP_FIXED_HDR_LEN;

    // get RTCP header information
    RtcpHeader* pobjRtcpHdr = m_objRrPkt.getRtcpHdrInfo();

    // encode m_stNtpTimestamp
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) =
            RtpOsUtil::Ntohl(m_stNtpTimestamp.m_uiNtpHigh32Bits);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) =
            RtpOsUtil::Ntohl(m_stNtpTimestamp.m_uiNtpLow32Bits);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // encode m_uiRtpTimestamp
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiRtpTimestamp);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // encode m_uiSendPktCount
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiSendPktCount);
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // encode m_uiSendOctCount
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(m_uiSendOctCount);
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // encode report blocks
    eRTP_STATUS_CODE eEncodeRes = RTP_FAILURE;

    pobjRtcpPktBuf->setLength(uiCurPos);
    eEncodeRes = m_objRrPkt.formRrPacket(pobjRtcpPktBuf, eRTP_FALSE);
    if (eEncodeRes != RTP_SUCCESS)
    {
        RTP_TRACE_WARNING("[formSrPacket], Report Block Encoding Error", RTP_ZERO, RTP_ZERO);
        return eEncodeRes;
    }

    // get length of the SR packet
    RtpDt_UInt32 uiSrPktLen = pobjRtcpPktBuf->getLength();
#ifdef ENABLE_PADDING
    pucBuffer = pobjRtcpPktBuf->getBuffer();
    pucBuffer = pucBuffer + uiSrPktLen;
    RtpDt_UInt32 uiPadLen = uiSrPktLen % RTP_WORD_SIZE;
    if (uiPadLen != RTP_ZERO)
    {
        uiSrPktLen = uiSrPktLen + uiPadLen;
        uiPadLen = RTP_WORD_SIZE - uiPadLen;
        memset(pucBuffer, RTP_ZERO, uiPadLen);
        pucBuffer = pucBuffer + uiPadLen;
        pucBuffer = pucBuffer - RTP_ONE;
        *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)uiPadLen;

        // set pad bit in header
        pobjRtcpHdr->setPadding();
        // set length in header
        pobjRtcpHdr->setLength(uiSrPktLen);
    }
    else
#endif
    {
        // set length in header
        pobjRtcpHdr->setLength(uiSrPktLen);
    }

    // set compound RTCP packet position to ZERO.
    pobjRtcpPktBuf->setLength(RTP_ZERO);
    // form rtcp header
    pobjRtcpHdr->formRtcpHeader(pobjRtcpPktBuf);
    // set the actual position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiSrPktLen);

    return RTP_SUCCESS;
}  // formSrPacket

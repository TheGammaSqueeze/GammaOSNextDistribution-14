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

#include <RtpPacket.h>
#include <RtpTrace.h>

RtpPacket::RtpPacket() :
        m_pobjExt(nullptr),
        m_pobjRtpPayload(nullptr)
#ifdef ENABLE_PADDING
        ,
        m_ucPadLen(RTP_ZERO)
#endif
{
}

RtpPacket::~RtpPacket()
{
    if (m_pobjExt != nullptr)
    {
        delete m_pobjExt;
        m_pobjExt = nullptr;
    }

    if (m_pobjRtpPayload != nullptr)
    {
        delete m_pobjRtpPayload;
        m_pobjRtpPayload = nullptr;
    }
}

RtpHeader* RtpPacket::getRtpHeader()
{
    return &m_objRtpHeader;
}

RtpDt_Void RtpPacket::setRtpPayload(IN RtpBuffer* pobjRtpPld)
{
    m_pobjRtpPayload = pobjRtpPld;
}

RtpDt_Void RtpPacket::setExtHeader(IN RtpBuffer* pobjExt)
{
    m_pobjExt = pobjExt;
}

RtpBuffer* RtpPacket::getExtHeader()
{
    return m_pobjExt;
}

RtpBuffer* RtpPacket::getRtpPayload()
{
    return m_pobjRtpPayload;
}

eRtp_Bool RtpPacket::formPacket(IN RtpBuffer* pobjRtpPktBuf)
{
    RtpDt_UInt32 uiRtpUtlBufLen = RTP_ZERO;
    RtpDt_UChar* pcRtpBuf = pobjRtpPktBuf->getBuffer();

    // fixed header
    eRtp_Bool bPackRes = m_objRtpHeader.formHeader(pobjRtpPktBuf);
    if (bPackRes != eRTP_TRUE)
    {
        RTP_TRACE_WARNING("formPacket Failed", RTP_ZERO, RTP_ZERO);
        return bPackRes;
    }

    RtpDt_UInt32 uiRtpBufPos = pobjRtpPktBuf->getLength();
    pcRtpBuf = pcRtpBuf + uiRtpBufPos;

    // extension header
    if (m_pobjExt != nullptr)
    {
        const RtpDt_UChar* pRtpUtlBuf = m_pobjExt->getBuffer();
        uiRtpUtlBufLen = m_pobjExt->getLength();
        memcpy(pcRtpBuf, pRtpUtlBuf, uiRtpUtlBufLen);
        pcRtpBuf += uiRtpUtlBufLen;
        uiRtpBufPos += uiRtpUtlBufLen;
    }

    // rtp packet
    if (m_pobjRtpPayload != nullptr)
    {
        const RtpDt_UChar* pRtpUtlBuf = m_pobjRtpPayload->getBuffer();
        uiRtpUtlBufLen = m_pobjRtpPayload->getLength();
        memcpy(pcRtpBuf, pRtpUtlBuf, uiRtpUtlBufLen);
        uiRtpBufPos += uiRtpUtlBufLen;
#ifdef ENABLE_PADDING
        // calculate pad Len
        pcRtpBuf += uiRtpUtlBufLen;
        RtpDt_UInt32 uiPadLen = uiRtpUtlBufLen % RTP_WORD_SIZE;
        if (uiPadLen != RTP_ZERO)
        {
            m_ucPadLen = RTP_WORD_SIZE - uiPadLen;
        }

        // padding
        if (m_ucPadLen > RTP_ZERO)
        {
            RtpDt_UChar ucTmpPadLen = m_ucPadLen - RTP_ONE;
            memset(pcRtpBuf, RTP_ZERO, m_ucPadLen);
            // pad length
            *(reinterpret_cast<RtpDt_UChar*>(pcRtpBuf + ucTmpPadLen)) = m_ucPadLen;
            uiRtpBufPos += m_ucPadLen;
        }
#endif
    }

    // set raw buffer length
    pobjRtpPktBuf->setLength(uiRtpBufPos);

    return eRTP_TRUE;
}

eRtp_Bool RtpPacket::decodePacket(IN RtpBuffer* pobjRtpPktBuf)
{
    RtpDt_UInt32 uiRtpBufPos = RTP_ZERO;
    RtpDt_UInt32 uiRtpUtlBufLen = RTP_ZERO;
    RtpDt_UChar* pcRtpBuf = pobjRtpPktBuf->getBuffer();

    uiRtpUtlBufLen = pobjRtpPktBuf->getLength();

    // decode fixed header
    m_objRtpHeader.decodeHeader(pobjRtpPktBuf, uiRtpBufPos);
    pcRtpBuf = pcRtpBuf + uiRtpBufPos;

    // Packet Validation
    // RTP version check
    if (m_objRtpHeader.getVersion() != RTP_VERSION_NUM)
    {
        return eRTP_FAILURE;
    }

    // extension header
    if (m_objRtpHeader.getExtension())
    {
        m_pobjExt = new RtpBuffer();

        // Get XHdr type and length
        RtpDt_UInt32 uiByte4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pcRtpBuf)));
        RtpDt_UInt16 uXHdrLen =
                (RtpDt_UInt16)(uiByte4Data & RTP_HEX_16_BIT_MAX);  // add header size

        uXHdrLen += 1;              // add a word for header type info
        uXHdrLen *= RTP_WORD_SIZE;  // convert word to byte

        if ((uXHdrLen <= 0) || ((uiRtpBufPos + uXHdrLen) > uiRtpUtlBufLen))
        {
            RTP_TRACE_ERROR("[decodePacket] Invalid Header Extension len[%d]", uXHdrLen, 0);
            return eRTP_FAILURE;
        }

        RtpDt_UChar* pRtpExtData = new RtpDt_UChar[uXHdrLen];

        memcpy(pRtpExtData, pcRtpBuf, uXHdrLen);
        m_pobjExt->setBufferInfo(uXHdrLen, pRtpExtData);

        pcRtpBuf = pcRtpBuf + uXHdrLen;
        uiRtpBufPos = uiRtpBufPos + uXHdrLen;
    }

    // rtp payload
    if (uiRtpUtlBufLen > uiRtpBufPos)
        uiRtpUtlBufLen -= uiRtpBufPos;
    else
        uiRtpUtlBufLen = 0;

    RtpDt_UChar ucPadBit = RTP_ZERO;
    ucPadBit = m_objRtpHeader.getPadding();

    if (ucPadBit > RTP_ZERO)
    {
        RtpDt_UChar ucPadLen = RTP_ZERO;
        RtpDt_UInt32 uiPadLenPos = uiRtpUtlBufLen;

        uiPadLenPos = uiPadLenPos - RTP_ONE;
        ucPadLen = *(reinterpret_cast<RtpDt_UChar*>(pcRtpBuf + uiPadLenPos));
        if (ucPadLen == RTP_ZERO)
        {
            return eRTP_FAILURE;
        }
        if (uiRtpUtlBufLen > ucPadLen)
            uiRtpUtlBufLen -= ucPadLen;
        else
            uiRtpUtlBufLen = 0;
    }

    m_pobjRtpPayload = new RtpBuffer();
    if (m_pobjRtpPayload == nullptr)
    {
        return eRTP_FAILURE;
    }
    RtpDt_UChar* pRtpUtlBuf = nullptr;
    pRtpUtlBuf = new RtpDt_UChar[uiRtpUtlBufLen];
    if (pRtpUtlBuf == nullptr)
    {
        return eRTP_FAILURE;
    }
    memcpy(pRtpUtlBuf, pcRtpBuf, uiRtpUtlBufLen);
    m_pobjRtpPayload->setBufferInfo(uiRtpUtlBufLen, pRtpUtlBuf);

    return eRTP_SUCCESS;
}

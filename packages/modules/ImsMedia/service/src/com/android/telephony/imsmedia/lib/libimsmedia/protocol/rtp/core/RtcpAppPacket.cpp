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

#include <RtcpAppPacket.h>
#include <string.h>

RtcpAppPacket::RtcpAppPacket() :
        m_uiName(RTP_ZERO),
        m_pAppData(nullptr)
{
}

RtcpAppPacket::~RtcpAppPacket()
{
    // m_pAppData
    if (m_pAppData != nullptr)
    {
        delete m_pAppData;
        m_pAppData = nullptr;
    }
}

RtpDt_Void RtcpAppPacket::setRtcpHdrInfo(RtcpHeader& objHeader)
{
    m_objRtcpHdr = objHeader;
}

RtcpHeader* RtcpAppPacket::getRtcpHdrInfo()
{
    return &m_objRtcpHdr;
}

RtpDt_UInt32 RtcpAppPacket::getName()
{
    return m_uiName;
}

RtpDt_Void RtcpAppPacket::setName(IN RtpDt_UInt32 uiName)
{
    m_uiName = uiName;
}

RtpBuffer* RtcpAppPacket::getAppData()
{
    return m_pAppData;
}

RtpDt_Void RtcpAppPacket::setAppData(IN RtpBuffer* pobjAppData)
{
    m_pAppData = pobjAppData;
}

eRTP_STATUS_CODE RtcpAppPacket::decodeAppPacket(IN RtpDt_UChar* pucAppBuf, IN RtpDt_UInt16 usAppLen)
{
    // name
    m_uiName = *(reinterpret_cast<RtpDt_UInt32*>(pucAppBuf));
    pucAppBuf = pucAppBuf + RTP_WORD_SIZE;

    RtpDt_UInt16 usTmpAppLen = usAppLen;

    // application dependent data
    usTmpAppLen = usTmpAppLen - RTP_WORD_SIZE;
    if (usTmpAppLen > 0)
    {
        RtpDt_UChar* pucTmpBuf = nullptr;
        m_pAppData = new RtpBuffer();
        pucTmpBuf = new RtpDt_UChar[usTmpAppLen];

        memcpy(pucTmpBuf, pucAppBuf, usTmpAppLen);
        m_pAppData->setBufferInfo(usTmpAppLen, pucTmpBuf);
    }

    return RTP_SUCCESS;
}  // decodeAppPacket

eRTP_STATUS_CODE RtcpAppPacket::formAppPacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiAppPktPos = pobjRtcpPktBuf->getLength();

    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();

    uiCurPos = uiCurPos + RTCP_FIXED_HDR_LEN;
    pucBuffer = pucBuffer + uiCurPos;

    // m_uiName
    *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = m_uiName;
    pucBuffer = pucBuffer + RTP_WORD_SIZE;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // m_pAppData
    if (m_pAppData != nullptr)
    {
        memcpy(pucBuffer, m_pAppData->getBuffer(), m_pAppData->getLength());
        uiCurPos = uiCurPos + m_pAppData->getLength();
    }
    // start padding
    {
        RtpDt_UInt32 uiAppPktLen = uiCurPos - uiAppPktPos;

#ifdef ENABLE_PADDING
        RtpDt_UInt32 uiPadLen = uiAppPktLen % RTP_WORD_SIZE;
        if (uiPadLen > RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            uiAppPktLen = uiAppPktLen + uiPadLen;
            uiCurPos = uiCurPos + uiPadLen;
            pucBuffer = pucBuffer + m_pAppData->getLength();
            memset(pucBuffer, RTP_ZERO, uiPadLen);

            pucBuffer = pucBuffer + uiPadLen;
            pucBuffer = pucBuffer - RTP_ONE;
            *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)uiPadLen;

            // set pad bit in header
            m_objRtcpHdr.setPadding();
            // set length in header
            m_objRtcpHdr.setLength(uiAppPktLen);
        }
        else
#endif
        {
            // set length in header
            m_objRtcpHdr.setLength(uiAppPktLen);
        }
        pobjRtcpPktBuf->setLength(uiAppPktPos);
        m_objRtcpHdr.formRtcpHeader(pobjRtcpPktBuf);
    }  // padding

    // set the actual position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiCurPos);

    return RTP_SUCCESS;
}  // formAppPacket

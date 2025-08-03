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

#include <RtcpPacket.h>
#include <RtpTrace.h>
#include <RtpError.h>
#include <RtpSession.h>

RtcpPacket::RtcpPacket() :
        m_objSrPktList(std::list<RtcpSrPacket*>()),
        m_objRrPktList(std::list<RtcpRrPacket*>()),
        m_objFbPktList(std::list<RtcpFbPacket*>()),
        m_pobjSdesPkt(nullptr),
        m_pobjByePkt(nullptr),
        m_pobjAppPkt(nullptr),
        m_pobjRtcpXrPkt(nullptr)

{
}  // Constructor

RtcpPacket::~RtcpPacket()
{
    // delete all RtcpSrPacket objects.
    for (const auto& pobjSrPkt : m_objSrPktList)
    {
        delete pobjSrPkt;
    }
    m_objSrPktList.clear();

    // delete all RtcpRrPacket objects
    for (const auto& pobjRrPkt : m_objRrPktList)
    {
        delete pobjRrPkt;
    }
    m_objRrPktList.clear();

    // delete all RtcpFbPacket objects
    for (const auto& pobjFbPkt : m_objFbPktList)
    {
        delete pobjFbPkt;
    }
    m_objFbPktList.clear();

    if (m_pobjSdesPkt != nullptr)
    {
        delete m_pobjSdesPkt;
        m_pobjSdesPkt = nullptr;
    }
    if (m_pobjByePkt != nullptr)
    {
        delete m_pobjByePkt;
        m_pobjByePkt = nullptr;
    }
    if (m_pobjAppPkt != nullptr)
    {
        delete m_pobjAppPkt;
        m_pobjAppPkt = nullptr;
    }

    if (m_pobjRtcpXrPkt != nullptr)
    {
        delete m_pobjRtcpXrPkt;
        m_pobjRtcpXrPkt = nullptr;
    }
}  // Destructor

RtcpHeader RtcpPacket::getHeader()
{
    return m_objHeader;
}

std::list<RtcpSrPacket*>& RtcpPacket::getSrPacketList()
{
    return m_objSrPktList;
}

std::list<RtcpRrPacket*>& RtcpPacket::getRrPacketList()
{
    return m_objRrPktList;
}

std::list<RtcpFbPacket*>& RtcpPacket::getFbPacketList()
{
    return m_objFbPktList;
}

RtcpSdesPacket* RtcpPacket::getSdesPacket()
{
    return m_pobjSdesPkt;
}

RtpDt_Void RtcpPacket::setSdesPacketData(IN RtcpSdesPacket* pobjSdesData)
{
    m_pobjSdesPkt = pobjSdesData;
}

RtcpByePacket* RtcpPacket::getByePacket()
{
    return m_pobjByePkt;
}

RtpDt_Void RtcpPacket::setByePacketData(IN RtcpByePacket* pobjByePktData)
{
    m_pobjByePkt = pobjByePktData;
}

RtcpAppPacket* RtcpPacket::getAppPacket()
{
    return m_pobjAppPkt;
}

RtpDt_Void RtcpPacket::setAppPktData(IN RtcpAppPacket* pobjAppData)
{
    m_pobjAppPkt = pobjAppData;
}

eRTP_STATUS_CODE RtcpPacket::addSrPacketData(IN RtcpSrPacket* pobjSrPkt)
{
    if (pobjSrPkt == nullptr)
    {
        return RTP_FAILURE;
    }
    m_objSrPktList.push_back(pobjSrPkt);
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtcpPacket::addRrPacketData(IN RtcpRrPacket* pobjRrPkt)
{
    if (pobjRrPkt == nullptr)
    {
        return RTP_FAILURE;
    }
    m_objRrPktList.push_back(pobjRrPkt);
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtcpPacket::addFbPacketData(IN RtcpFbPacket* pobjFbPkt)
{
    if (pobjFbPkt == nullptr)
    {
        return RTP_FAILURE;
    }
    m_objFbPktList.push_back(pobjFbPkt);
    return RTP_SUCCESS;
}

RtcpXrPacket* RtcpPacket::getXrPacket()
{
    return m_pobjRtcpXrPkt;
}

RtpDt_Void RtcpPacket::setXrPacket(IN RtcpXrPacket* pobjRtcpXrData)
{
    m_pobjRtcpXrPkt = pobjRtcpXrData;
}

eRTP_STATUS_CODE RtcpPacket::decodeRtcpPacket(IN RtpBuffer* pobjRtcpPktBuf,
        IN RtpDt_UInt16 usExtHdrLen, IN RtcpConfigInfo* pobjRtcpCfgInfo)
{
    RtpDt_UInt32 uiCurPos = RTP_ZERO;
    eRtp_Bool bSrPkt = eRTP_FALSE;
    eRtp_Bool bRrPkt = eRTP_FALSE;
    eRtp_Bool bFbPkt = eRTP_FALSE;
    eRtp_Bool bOtherPkt = eRTP_FALSE;

    if (pobjRtcpPktBuf == nullptr || pobjRtcpPktBuf->getBuffer() == nullptr ||
            pobjRtcpPktBuf->getLength() < RTP_WORD_SIZE)
        return RTP_INVALID_PARAMS;

    // Check RTCP with only common header case.
    if (pobjRtcpPktBuf->getLength() == RTP_WORD_SIZE)
    {
        m_objHeader.decodeRtcpHeader(pobjRtcpPktBuf->getBuffer(), pobjRtcpPktBuf->getLength());
        return RTP_SUCCESS;
    }

    // Get RTCP Compound packet
    RtpDt_UInt32 uiCompPktLen = pobjRtcpPktBuf->getLength();
    RtpDt_Int32 iTrackCompLen = uiCompPktLen;

    while (iTrackCompLen >= RTCP_FIXED_HDR_LEN)
    {
        RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
        pucBuffer += uiCurPos;

        m_objHeader.decodeRtcpHeader(pucBuffer, iTrackCompLen);
        uiCurPos += RTCP_FIXED_HDR_LEN;
        pucBuffer += RTCP_FIXED_HDR_LEN;
        iTrackCompLen -= RTCP_FIXED_HDR_LEN;

        RtpDt_UChar uiVersion = m_objHeader.getVersion();
        if (uiVersion != RTP_VERSION_NUM)
        {
            RTP_TRACE_ERROR("[DecodeRtcpPacket] RTCP version[%d] is Invalid.", uiVersion, RTP_ZERO);
            return RTP_INVALID_MSG;
        }

        // get length
        RtpDt_UInt16 usPktLen = m_objHeader.getLength();
        usPktLen -= RTP_WORD_SIZE;
        if (usPktLen > iTrackCompLen)
        {
            RTP_TRACE_ERROR("[DecodeRtcpPacket] Report length is Invalid. ReportLen:%d, RtcpLen:%d",
                    usPktLen, iTrackCompLen);
            return RTP_INVALID_MSG;
        }

        RTP_TRACE_MESSAGE("[DecodeRtcpPacket] packet length: %d, compound packet length: %d",
                usPktLen, iTrackCompLen);

        // get packet type
        RtpDt_UInt32 uiPktType = m_objHeader.getPacketType();

        RTP_TRACE_MESSAGE("[DecodeRtcpPacket] packet type: %d report count: %d", uiPktType,
                m_objHeader.getReceptionReportCount());

        eRTP_STATUS_CODE eDecodeRes = RTP_FAILURE;

        switch (uiPktType)
        {
            case RTCP_SR:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_SR", 0, 0);
                RtcpSrPacket* pobjSrPkt = new RtcpSrPacket();
                if (pobjSrPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                pobjSrPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = pobjSrPkt->decodeSrPacket(pucBuffer, usPktLen, usExtHdrLen);
                addSrPacketData(pobjSrPkt);
                bSrPkt = eRTP_TRUE;
                break;
            }  // RTCP_SR
            case RTCP_RR:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_RR", 0, 0);
                RtpDt_UInt16 uiRrPktLen = usPktLen;
                RtcpRrPacket* pobjRrPkt = new RtcpRrPacket();
                if (pobjRrPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                pobjRrPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = pobjRrPkt->decodeRrPacket(pucBuffer, uiRrPktLen, usExtHdrLen);
                addRrPacketData(pobjRrPkt);
                bRrPkt = eRTP_TRUE;
                break;
            }  // RTCP_RR
            case RTCP_SDES:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_SDES", 0, 0);
                if (m_pobjSdesPkt != nullptr)
                    delete m_pobjSdesPkt;

                m_pobjSdesPkt = new RtcpSdesPacket();
                if (m_pobjSdesPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                m_pobjSdesPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = m_pobjSdesPkt->decodeSdesPacket(pucBuffer, usPktLen, pobjRtcpCfgInfo);
                bOtherPkt = eRTP_TRUE;
                break;
            }  // RTCP_SDES
            case RTCP_BYE:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_BYE", 0, 0);
                if (m_pobjByePkt != nullptr)
                    delete m_pobjByePkt;

                m_pobjByePkt = new RtcpByePacket();
                if (m_pobjByePkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                m_pobjByePkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = m_pobjByePkt->decodeByePacket(pucBuffer, usPktLen);
                bOtherPkt = eRTP_TRUE;
                break;
            }  // RTCP_BYE
            case RTCP_APP:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_APP", 0, 0);
                if (m_pobjAppPkt != nullptr)
                    delete m_pobjAppPkt;

                m_pobjAppPkt = new RtcpAppPacket();
                if (m_pobjAppPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                m_pobjAppPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = m_pobjAppPkt->decodeAppPacket(pucBuffer, usPktLen);
                bOtherPkt = eRTP_TRUE;
                break;
            }  // RTCP_APP
            case RTCP_RTPFB:
            case RTCP_PSFB:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_RTPFB", 0, 0);
                RtcpFbPacket* pobjFbPkt = new RtcpFbPacket();
                if (pobjFbPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }

                pobjFbPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = pobjFbPkt->decodeRtcpFbPacket(pucBuffer, usPktLen);
                addFbPacketData(pobjFbPkt);
                bFbPkt = eRTP_TRUE;
                break;
            }  // RTCP_RTPFB || RTCP_PSFB
            case RTCP_XR:
            {
                RTP_TRACE_MESSAGE("[DecodeRtcpPacket] Decoding RTCP_XR", 0, 0);
                if (m_pobjRtcpXrPkt != nullptr)
                    delete m_pobjRtcpXrPkt;

                m_pobjRtcpXrPkt = new RtcpXrPacket();
                if (m_pobjRtcpXrPkt == nullptr)
                {
                    RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                    return RTP_MEMORY_FAIL;
                }
                m_pobjRtcpXrPkt->setRtcpHdrInfo(m_objHeader);
                eDecodeRes = m_pobjRtcpXrPkt->decodeRtcpXrPacket(pucBuffer, usPktLen, uiPktType);
                bOtherPkt = eRTP_TRUE;
                break;
            }  // RTCP_XR
            default:
            {
                RTP_TRACE_WARNING("[DecodeRtcpPacket], Invalid RTCP MSG type[%d] received",
                        uiPktType, RTP_ZERO);
                // Instead of returning failure, ignore unknown report block and continue to decode
                // next report block.
                eDecodeRes = RTP_SUCCESS;
            }  // default
        };     // switch

        if (eDecodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_ERROR("[DecodeRtcpPacket], Decoding Error[%d]", eDecodeRes, RTP_ZERO);
            return eDecodeRes;
        }

        iTrackCompLen -= usPktLen;
        uiCurPos += usPktLen;
    }  // while

    if ((bSrPkt == eRTP_FALSE) && (bRrPkt == eRTP_FALSE) && (bFbPkt == eRTP_FALSE) &&
            (bOtherPkt == eRTP_FALSE))
    {
        RTP_TRACE_ERROR("[DecodeRtcpPacket], no rtcp sr,rr,fb packets", 0, 0);
        return RTP_DECODE_ERROR;
    }

    return RTP_SUCCESS;
}  // decodeRtcpPacket

eRTP_STATUS_CODE RtcpPacket::formRtcpPacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RTP_TRACE_MESSAGE("formRtcpPacket", 0, 0);
    RtpDt_UInt16 usSrSize = m_objSrPktList.size();
    RtpDt_UInt16 usRrSize = m_objRrPktList.size();
    RtpDt_UInt16 usFbSize = m_objFbPktList.size();

    pobjRtcpPktBuf->setLength(RTP_ZERO);

    if ((usSrSize == RTP_ZERO) && (usRrSize == RTP_ZERO) && (m_pobjByePkt == nullptr))
    {
        RTP_TRACE_WARNING("[formRtcpPacket] m_pobjSrPkt is NULL", RTP_ZERO, RTP_ZERO);
        return RTP_FAILURE;
    }

    if ((m_pobjByePkt == nullptr) && (m_pobjSdesPkt == nullptr) && (m_pobjAppPkt == nullptr) &&
            (usFbSize == RTP_ZERO))
    {
        RTP_TRACE_WARNING("[formRtcpPacket] Not present 2nd pkt in Comp pkt", RTP_ZERO, RTP_ZERO);
        return RTP_FAILURE;
    }

    eRTP_STATUS_CODE eEncodeRes = RTP_FAILURE;

    for (auto& pobjSrPkt : m_objSrPktList)
    {
        // get key material element from list.
        eEncodeRes = pobjSrPkt->formSrPacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in SR pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    for (auto& pobjRrPkt : m_objRrPktList)
    {
        // get key material element from list.
        eEncodeRes = pobjRrPkt->formRrPacket(pobjRtcpPktBuf, eRTP_TRUE);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in RR pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    if (m_pobjSdesPkt != nullptr)
    {
        eEncodeRes = m_pobjSdesPkt->formSdesPacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in SDES pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    if (m_pobjAppPkt != nullptr)
    {
        eEncodeRes = m_pobjAppPkt->formAppPacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in APP pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }
    if (m_pobjByePkt != nullptr)
    {
        eEncodeRes = m_pobjByePkt->formByePacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in BYE pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    if (m_objFbPktList.size() != RTP_ZERO)
    {
        RtcpFbPacket* pobjRtcpFbPkt = m_objFbPktList.front();
        eEncodeRes = pobjRtcpFbPkt->formRtcpFbPacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in Fb pkt encoding.", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    if (m_pobjRtcpXrPkt != nullptr)
    {
        eEncodeRes = m_pobjRtcpXrPkt->formRtcpXrPacket(pobjRtcpPktBuf);
        if (eEncodeRes != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("[formRtcpPacket] Error in XR pkt encoding", RTP_ZERO, RTP_ZERO);
            return eEncodeRes;
        }
    }

    return RTP_SUCCESS;
}  // formRtcpPacket

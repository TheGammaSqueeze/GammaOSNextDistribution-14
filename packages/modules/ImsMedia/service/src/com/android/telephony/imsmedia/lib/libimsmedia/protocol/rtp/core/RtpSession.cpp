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

#include <algorithm>
#include <RtpSession.h>
#include <RtpTrace.h>
#include <RtpError.h>
#include <RtpStackUtil.h>
#include <RtpReceiverInfo.h>
#include <RtcpPacket.h>
#include <RtcpChunk.h>
#include <RtpSessionManager.h>
#include <RtcpFbPacket.h>

extern RtpDt_Void Rtp_RtcpTimerCb(IN RtpDt_Void* pvTimerId, IN RtpDt_Void* pvData);

RtpSession::RtpSession() :
        m_pobjTransAddr(nullptr),
        m_usRtpPort(RTP_ZERO),
        m_usRtcpPort(RTP_ZERO),
        m_pobjRtpStack(nullptr),
        m_usExtHdrLen(RTP_ZERO),
        m_pobjRtcpCfgInfo(nullptr),
        m_bEnableRTP(eRTP_FALSE),
        m_bEnableRTCP(eRTP_FALSE),
        m_bEnableRTCPBye(eRTP_FALSE),
        m_usRTCPTimerVal(RTP_ZERO),
        m_usSeqNum(RTP_ZERO),
        m_usSeqNumCycles(RTP_ZERO),
        m_pobjPayloadInfo(nullptr),
        m_pobjAppInterface(nullptr),
        m_uiSsrc(RTP_ZERO),
        m_uiSessionMtu(RTP_DEF_MTU_SIZE),
        m_uiRtpSendPktCount(RTP_ZERO),
        m_uiRtpSendOctCount(RTP_ZERO),
        m_uiRtcpSendPktCount(RTP_ZERO),
        m_uiRtcpSendOctCount(RTP_ZERO),
        m_bSelfCollisionByeSent(eRTP_FAILURE),
        m_pTimerId(nullptr),
        m_bRtcpSendPkt(eRTP_FALSE),
        m_bSndRtcpByePkt(eRTP_FALSE),
        m_lastRTTDelay(RTP_ZERO),
        m_bisXr(eRTP_FALSE),
        m_bFirstRtpRecvd(eRTP_FALSE)
{
    m_pobjRtcpCfgInfo = new RtcpConfigInfo();
    m_pobjRtpRcvrInfoList = new std::list<RtpReceiverInfo*>();
    m_pobjPayloadInfo = new RtpPayloadInfo();
    m_pobjUtlRcvrList = nullptr;
    m_stRtcpXr.m_pBlockBuffer = nullptr;
}

RtpSession::RtpSession(IN RtpStack* pobjStack) :
        m_pobjTransAddr(nullptr),
        m_usRtpPort(RTP_ZERO),
        m_usRtcpPort(RTP_ZERO),
        m_pobjRtpStack(pobjStack),
        m_usExtHdrLen(RTP_ZERO),
        m_pobjRtcpCfgInfo(nullptr),
        m_bEnableRTP(eRTP_FALSE),
        m_bEnableRTCP(eRTP_FALSE),
        m_bEnableRTCPBye(eRTP_FALSE),
        m_usRTCPTimerVal(RTP_ZERO),
        m_usSeqNum(RTP_ZERO),
        m_usSeqNumCycles(RTP_ZERO),
        m_pobjPayloadInfo(nullptr),
        m_pobjAppInterface(nullptr),
        m_uiSsrc(RTP_ZERO),
        m_uiSessionMtu(RTP_DEF_MTU_SIZE),
        m_uiRtpSendPktCount(RTP_ZERO),
        m_uiRtpSendOctCount(RTP_ZERO),
        m_uiRtcpSendPktCount(RTP_ZERO),
        m_uiRtcpSendOctCount(RTP_ZERO),
        m_bSelfCollisionByeSent(eRTP_FAILURE),
        m_pTimerId(nullptr),
        m_bRtcpSendPkt(eRTP_FALSE),
        m_bSndRtcpByePkt(eRTP_FALSE),
        m_lastRTTDelay(RTP_ZERO),
        m_bisXr(eRTP_FALSE),
        m_bFirstRtpRecvd(eRTP_FALSE)
{
    m_pobjRtcpCfgInfo = new RtcpConfigInfo();
    m_pobjPayloadInfo = new RtpPayloadInfo();
    m_pobjRtpRcvrInfoList = new std::list<RtpReceiverInfo*>();
    m_pobjUtlRcvrList = nullptr;
    m_stRtcpXr.m_pBlockBuffer = nullptr;
}

RtpSession::~RtpSession()
{
    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);

    if (m_pobjTransAddr != nullptr)
    {
        delete m_pobjTransAddr;
        m_pobjTransAddr = nullptr;
    }

    m_pobjRtpStack = nullptr;
    if (m_pobjRtcpCfgInfo != nullptr)
    {
        delete m_pobjRtcpCfgInfo;
        m_pobjRtcpCfgInfo = nullptr;
    }

    if (m_pobjPayloadInfo != nullptr)
    {
        delete m_pobjPayloadInfo;
        m_pobjPayloadInfo = nullptr;
    }

    if (m_pTimerId != nullptr)
    {
    }

    // delete all RTP session objects.
    RtpDt_UInt16 usSize = RTP_ZERO;
    usSize = m_pobjRtpRcvrInfoList->size();

    for (RtpDt_UInt32 uiCount = RTP_ZERO; uiCount < usSize; uiCount++)
    {
        RtpReceiverInfo* pobjRcvrElm = nullptr;
        // get the member information
        pobjRcvrElm = m_pobjRtpRcvrInfoList->front();
        m_pobjRtpRcvrInfoList->pop_front();
        delete pobjRcvrElm;
    }  // for

    delete m_pobjRtpRcvrInfoList;
    delete m_pobjAppInterface;
    m_pobjRtpRcvrInfoList = nullptr;
    m_pobjAppInterface = nullptr;
}

RtpDt_UInt16 RtpSession::getExtHdrLen()
{
    return m_usExtHdrLen;
}

RtpDt_Void RtpSession::setSsrc(IN RtpDt_UInt32 uiSsrc)
{
    m_uiSsrc = uiSsrc;
}

RtpDt_UInt32 RtpSession::getSsrc()
{
    return m_uiSsrc;
}

RtpDt_Void RtpSession::setSequenceNumber(IN RtpDt_UInt16 uiSeqNumber)
{
    m_usSeqNum = uiSeqNumber;
}

RtpDt_UInt16 RtpSession::getSequenceNumber()
{
    return m_usSeqNum;
}

RtpDt_Void RtpSession::setRtpPort(IN RtpDt_UInt16 usPort)
{
    m_usRtpPort = usPort;
}

RtpDt_UInt16 RtpSession::getRtpPort()
{
    return m_usRtpPort;
}

RtpDt_Void RtpSession::setRtpTransAddr(IN RtpBuffer* pobjDestTransAddr)
{
    if (m_pobjTransAddr != nullptr)
        delete m_pobjTransAddr;

    m_pobjTransAddr = pobjDestTransAddr;
}

RtpBuffer* RtpSession::getRtpTransAddr()
{
    return m_pobjTransAddr;
}

eRtp_Bool RtpSession::compareRtpSessions(IN RtpSession* pobjSession)
{
    if (pobjSession == nullptr)
    {
        RTP_TRACE_WARNING("compareRtpSessions, Input param is Null.", RTP_ZERO, RTP_ZERO);
        return eRTP_FAILURE;
    }

    if (m_uiSsrc == pobjSession->getSsrc())
    {
        if (m_usRtpPort == pobjSession->getRtpPort())
        {
            RtpBuffer* objRtpBuff = pobjSession->getRtpTransAddr();
            if (m_pobjTransAddr == nullptr && objRtpBuff == nullptr)
            {
                return eRTP_SUCCESS;
            }
            RtpDt_UChar* pcTranAddr1 = nullptr;
            if (m_pobjTransAddr != nullptr)
            {
                pcTranAddr1 = m_pobjTransAddr->getBuffer();
            }
            RtpDt_UChar* pcTranAddr2 = nullptr;
            if (objRtpBuff != nullptr)
            {
                pcTranAddr2 = objRtpBuff->getBuffer();
            }
            if (pcTranAddr1 == nullptr || pcTranAddr2 == nullptr)
            {
                return eRTP_FAILURE;
            }
            if (memcmp(pcTranAddr1, pcTranAddr2, m_pobjTransAddr->getLength()) == RTP_ZERO)
            {
                return eRTP_SUCCESS;
            }
        }
    }

    return eRTP_FAILURE;
}  // compareRtpSessions

RtpDt_Void Rtp_RtcpTimerCb(IN RtpDt_Void* pvTimerId, IN RtpDt_Void* pvData)
{
    RtpSessionManager* pobjActSesDb = RtpSessionManager::getInstance();
    RtpSession* pobjRtpSession = static_cast<RtpSession*>(pvData);
    if (pobjRtpSession == nullptr)
    {
        RTP_TRACE_WARNING("Rtp_RtcpTimerCb, pvTimerId is NULL.", RTP_ZERO, RTP_ZERO);
        return;
    }

    eRtp_Bool bResult = pobjActSesDb->isValidRtpSession(pvData);
    if (bResult != eRTP_TRUE)
    {
        return;
    }

    pobjRtpSession->rtcpTimerExpiry(pvTimerId);
}

RtpDt_UInt32 RtpSession::estimateRtcpPktSize()
{
    RtpDt_UInt32 uiEstRtcpSize = RTP_ZERO;
    RtpDt_UInt32 uiSdesItems = RTP_ZERO;

    uiSdesItems = m_pobjRtcpCfgInfo->getSdesItemCount();

    if ((m_bSelfCollisionByeSent == eRTP_TRUE) || (m_bSndRtcpByePkt == eRTP_TRUE))
    {
        uiEstRtcpSize = RTP_DEF_BYE_PKT_SIZE;
        uiEstRtcpSize += m_pobjRtcpCfgInfo->getByeReasonSize();

        RTP_TRACE_MESSAGE("estimateRtcpPktSize, [Bye packet size : %d]", uiEstRtcpSize, nullptr);
    }
    else if (uiSdesItems > RTP_ZERO)
    {
        RtpDt_UInt32 uiSdesPktSize = RTP_WORD_SIZE;
        uiSdesPktSize = uiSdesPktSize + m_pobjRtcpCfgInfo->estimateSdesPktSize();

        RTP_TRACE_MESSAGE("estimateRtcpPktSize, [uiSdesPktSize : %d]", uiSdesPktSize, nullptr);

        uiEstRtcpSize += uiSdesPktSize;
    }

    if (m_pobjRtcpCfgInfo->isRtcpAppPktSendEnable() == eRTP_TRUE)
    {
        uiEstRtcpSize += RTP_DEF_APP_PKT_SIZE;
        uiEstRtcpSize += m_pobjRtcpCfgInfo->getAppDepDataSize();

        RTP_TRACE_MESSAGE("estimateRtcpPktSize, [after app pkt size: %d]", uiEstRtcpSize, nullptr);
    }

    return uiEstRtcpSize;
}

eRTP_STATUS_CODE RtpSession::formSrList(IN RtpDt_UInt32 uiSndrCount, OUT RtcpPacket* pobjRtcpPkt)
{
    eRTP_STATUS_CODE eStatus = RTP_SUCCESS;
    RtpDt_UInt32 uiTmpFlg = RTP_ZERO;

    while (uiSndrCount > RTP_MAX_RECEP_REP_CNT)
    {
        RtcpSrPacket* pobjSrPkt = new RtcpSrPacket();
        if (pobjSrPkt == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        // construct SR packet
        eStatus = pobjRtcpPkt->addSrPacketData(pobjSrPkt);
        if (eStatus != RTP_SUCCESS)
        {
            return eStatus;
        }
        eStatus = populateSrpacket(pobjSrPkt, RTP_MAX_RECEP_REP_CNT);
        if (eStatus != RTP_SUCCESS)
        {
            return eStatus;
        }
        uiSndrCount = uiSndrCount - RTP_MAX_RECEP_REP_CNT;
        uiTmpFlg = RTP_ONE;
    }  // while
    if ((uiSndrCount > RTP_ZERO) || (uiTmpFlg == RTP_ZERO))
    {
        RtcpSrPacket* pobjSrPkt = new RtcpSrPacket();
        if (pobjSrPkt == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        // construct SR packet
        eStatus = pobjRtcpPkt->addSrPacketData(pobjSrPkt);
        if (eStatus != RTP_SUCCESS)
        {
            return eStatus;
        }
        eStatus = populateSrpacket(pobjSrPkt, uiSndrCount);
        if (eStatus != RTP_SUCCESS)
        {
            return eStatus;
        }
    }
    return RTP_SUCCESS;
}  // formSrList

eRTP_STATUS_CODE RtpSession::formRrList(IN RtpDt_UInt32 uiSndrCount, OUT RtcpPacket* pobjRtcpPkt)
{
    eRTP_STATUS_CODE eStatus = RTP_SUCCESS;
    RtpDt_UInt32 uiTmpFlg = RTP_ZERO;

    while (uiSndrCount > RTP_MAX_RECEP_REP_CNT)
    {
        RtcpRrPacket* pobjRrPkt = new RtcpRrPacket();
        if (pobjRrPkt == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        // construct RR packet
        eStatus = pobjRtcpPkt->addRrPacketData(pobjRrPkt);
        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("formRrList, error in addRrPacketData.", RTP_ZERO, RTP_ZERO);
            return eStatus;
        }
        eStatus = populateReportPacket(pobjRrPkt, eRTP_TRUE, RTP_MAX_RECEP_REP_CNT);
        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("formRrList, error in populateReportPacket.", RTP_ZERO, RTP_ZERO);
            return eStatus;
        }
        uiSndrCount = uiSndrCount - RTP_MAX_RECEP_REP_CNT;
        uiTmpFlg = RTP_ONE;
    }  // while
    if ((uiSndrCount > RTP_ZERO) || (uiTmpFlg == RTP_ZERO))
    {
        RtcpRrPacket* pobjRrPkt = new RtcpRrPacket();
        if (pobjRrPkt == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        // construct RR packet
        eStatus = pobjRtcpPkt->addRrPacketData(pobjRrPkt);
        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("formRrList, error in addRrPacketData.", RTP_ZERO, RTP_ZERO);
            return eStatus;
        }
        eStatus = populateReportPacket(pobjRrPkt, eRTP_TRUE, uiSndrCount);
        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_WARNING("formRrList, error in populateReportPacket.", RTP_ZERO, RTP_ZERO);
            return eStatus;
        }
    }

    return RTP_SUCCESS;
}  // formSrList

RtpDt_UInt32 RtpSession::numberOfReportBlocks(
        IN RtpDt_UInt32 uiMtuSize, IN RtpDt_UInt32 uiEstRtcpSize)
{
    RtpDt_UInt32 uiReportDefSize = RTCP_FIXED_HDR_LEN + RTP_DEF_SR_SPEC_SIZE;
    // determine the sender count
    RtpDt_UInt32 uiRemTotalSize = uiMtuSize - uiEstRtcpSize;
    // one SR packet size with 31 report blocks
    RtpDt_UInt32 uiReportMaxSize = uiReportDefSize + (RTP_MAX_RECEP_REP_CNT * RTP_DEF_REP_BLK_SIZE);

    RtpDt_UInt32 uiTotalNumofReport = uiRemTotalSize / uiReportMaxSize;

    uiRemTotalSize = uiRemTotalSize - (uiReportMaxSize * uiTotalNumofReport);
    uiRemTotalSize = uiRemTotalSize - uiReportDefSize;
    RtpDt_UInt32 uiRemRepBlkNum = uiRemTotalSize / RTP_DEF_REP_BLK_SIZE;
    uiRemRepBlkNum += uiTotalNumofReport * RTP_MAX_RECEP_REP_CNT;

    return uiRemRepBlkNum;
}

RtpDt_UInt32 RtpSession::calculateTotalRtcpSize(
        IN RtpDt_UInt32 uiSndrCount, IN RtpDt_UInt32 uiEstRtcpSize, IN eRtp_Bool isSR)
{
    RtpDt_UInt32 uiReortDefSize = RTP_ZERO;

    if (isSR == eRTP_TRUE)
    {
        uiReortDefSize = RTCP_FIXED_HDR_LEN + RTP_DEF_SR_SPEC_SIZE;
    }
    else
    {
        uiReortDefSize = RTCP_FIXED_HDR_LEN;
    }

    RtpDt_UInt32 uiTmpSndrCount = uiSndrCount;
    RtpDt_UInt32 uiReortFlg = RTP_ZERO;
    RtpDt_UInt32 uiReportTotalSize = RTP_ZERO;

    while (uiTmpSndrCount > RTP_MAX_RECEP_REP_CNT)
    {
        uiReportTotalSize += uiReortDefSize + (RTP_MAX_RECEP_REP_CNT * RTP_DEF_REP_BLK_SIZE);

        uiTmpSndrCount = uiTmpSndrCount - RTP_MAX_RECEP_REP_CNT;
        uiReportTotalSize += m_usExtHdrLen;
        uiReortFlg = RTP_ONE;
    }
    if ((uiTmpSndrCount > RTP_ZERO) || (uiReortFlg == RTP_ZERO))
    {
        uiReportTotalSize += uiReortDefSize + (uiTmpSndrCount * RTP_DEF_REP_BLK_SIZE);
    }

    RtpDt_UInt32 uiTotalRtcpSize = uiEstRtcpSize + uiReportTotalSize;

    return uiTotalRtcpSize;
}

RtpDt_Void RtpSession::rtpSetTimestamp()
{
    RtpOsUtil::GetNtpTime(m_stCurNtpRtcpTs);
    if (m_bRtcpSendPkt == eRTP_FALSE)
    {
        m_bRtcpSendPkt = eRTP_TRUE;
    }

    RtpDt_UInt32 uiSamplingRate = m_pobjPayloadInfo->getSamplingRate();

    // The RTP timestamp corresponds to the same instant as the NTP timestamp,
    // but it is expressed inthe units of the RTP media clock.
    // The value is generally not the same as the RTP timestamp of the previous data packet,
    // because some time will have elapsed since the data in that packet was samples
    // RTP Timestamp = Last RTP Pkt timestamp
    //                 + timegap between last RTP packet and current RTCP packet
    m_curRtcpTimestamp = RtpStackUtil::calcRtpTimestamp(
            m_curRtpTimestamp, &m_stCurNtpRtcpTs, &m_stCurNtpTimestamp, uiSamplingRate);
}

eRTP_STATUS_CODE RtpSession::rtpMakeCompoundRtcpPacket(IN_OUT RtcpPacket* objRtcpPkt)
{
    // estimate the size of the RTCP packet
    RtpDt_UInt32 uiEstRtcpSize = estimateRtcpPktSize();
    RtpDt_UInt32 uiSndrCount = getSenderCount();

    // get mtu size
    RtpStackProfile* pobjProfile = m_pobjRtpStack->getStackProfile();
    RtpDt_UInt32 uiMtuSize = pobjProfile->getMtuSize();

    RtpDt_UInt32 uiSdesItems = m_pobjRtcpCfgInfo->getSdesItemCount();

    eRTP_STATUS_CODE eEncRes = RTP_FAILURE;
    // check number of packets are sent
    if ((m_bRtpSendPkt == eRTP_TRUE) || (m_bSelfCollisionByeSent == eRTP_TRUE) ||
            (m_bSndRtcpByePkt == eRTP_TRUE))
    {
        RtpDt_UInt32 uiTotalRtcpSize = RTP_ZERO;

        uiTotalRtcpSize = calculateTotalRtcpSize(uiSndrCount, uiEstRtcpSize, eRTP_TRUE);
        if (uiTotalRtcpSize < uiMtuSize)
        {
            RTP_TRACE_MESSAGE(
                    "rtpMakeCompoundRtcpPacket,[uiTotalRtcpSize : %d] [Estimated Size : %d]",
                    uiTotalRtcpSize, uiEstRtcpSize);

            eEncRes = formSrList(uiSndrCount, objRtcpPkt);
            if (eEncRes != RTP_SUCCESS)
            {
                RTP_TRACE_ERROR("formSrList error: %d", eEncRes, 0);
                m_pobjAppInterface->rtcpTimerHdlErrorInd(eEncRes);
                return eEncRes;
            }
        }
        else
        {
            RtpDt_UInt32 uiRemRepBlkNum = RTP_ZERO;
            uiRemRepBlkNum = numberOfReportBlocks(uiMtuSize, uiEstRtcpSize);
            eEncRes = formSrList(uiRemRepBlkNum, objRtcpPkt);
            if (eEncRes != RTP_SUCCESS)
            {
                RTP_TRACE_ERROR("formSrList error: %d", eEncRes, 0);
                m_pobjAppInterface->rtcpTimerHdlErrorInd(eEncRes);
                return eEncRes;
            }
        }
    }  // SR
    else
    {
        RtpDt_UInt32 uiTotalRtcpSize = RTP_ZERO;

        uiTotalRtcpSize = calculateTotalRtcpSize(uiSndrCount, uiEstRtcpSize, eRTP_FALSE);
        if (uiTotalRtcpSize < uiMtuSize)
        {
            eEncRes = formRrList(uiSndrCount, objRtcpPkt);
            if (eEncRes != RTP_SUCCESS)
            {
                RTP_TRACE_ERROR("formRrList error: %d", eEncRes, 0);
                m_pobjAppInterface->rtcpTimerHdlErrorInd(eEncRes);
                return eEncRes;
            }
        }
        else
        {
            RtpDt_UInt32 uiRemRepBlkNum = RTP_ZERO;
            uiRemRepBlkNum = numberOfReportBlocks(uiMtuSize, uiEstRtcpSize);
            eEncRes = formRrList(uiRemRepBlkNum, objRtcpPkt);
            if (eEncRes != RTP_SUCCESS)
            {
                RTP_TRACE_ERROR("formRrList error: %d", eEncRes, 0);
                m_pobjAppInterface->rtcpTimerHdlErrorInd(eEncRes);
                return eEncRes;
            }
        }
    }  // RR

    if ((m_bSelfCollisionByeSent == eRTP_TRUE) || (m_bSndRtcpByePkt == eRTP_TRUE))
    {
        eRTP_STATUS_CODE eStatus = RTP_SUCCESS;
        // construct BYE packet
        eStatus = populateByePacket(objRtcpPkt);
        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_ERROR("populateByePacket error: %d", eEncRes, 0);
            m_pobjAppInterface->rtcpTimerHdlErrorInd(eStatus);
            return eStatus;
        }
    }
    else if (uiSdesItems > RTP_ZERO)
    {
        eRTP_STATUS_CODE eStatus = RTP_SUCCESS;
        eStatus = constructSdesPkt(objRtcpPkt);

        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_ERROR("constructSdesPkt error: %d", eEncRes, 0);
            m_pobjAppInterface->rtcpTimerHdlErrorInd(eStatus);
            return eStatus;
        }
    }

    if (m_bisXr == eRTP_TRUE)
    {
        eRTP_STATUS_CODE eStatus = RTP_SUCCESS;
        eStatus = populateRtcpXrPacket(objRtcpPkt);

        if (eStatus != RTP_SUCCESS)
        {
            RTP_TRACE_ERROR("populateRtcpXrPacket error: %d", eEncRes, 0);
            m_pobjAppInterface->rtcpTimerHdlErrorInd(eStatus);
            return eStatus;
        }

        m_bisXr = eRTP_FALSE;
    }

    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::rtpSendRtcpPacket(IN_OUT RtcpPacket* objRtcpPkt)
{
    RtpBuffer* pRtcpBuf = new RtpBuffer();

    if (pRtcpBuf == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        m_pobjAppInterface->rtcpTimerHdlErrorInd(RTP_MEMORY_FAIL);
        return RTP_FAILURE;
    }

    RtpDt_UChar* pcBuff = new RtpDt_UChar[RTP_DEF_MTU_SIZE];

    if (pcBuff == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        delete pRtcpBuf;
        m_pobjAppInterface->rtcpTimerHdlErrorInd(RTP_MEMORY_FAIL);
        return RTP_FAILURE;
    }

    pRtcpBuf->setBufferInfo(RTP_DEF_MTU_SIZE, pcBuff);

    // construct the packet
    eRTP_STATUS_CODE eEncRes = RTP_FAILURE;
    eEncRes = objRtcpPkt->formRtcpPacket(pRtcpBuf);
    if (eEncRes == RTP_SUCCESS)
    {
        // pass the RTCP buffer to application.
        eRtp_Bool bStatus = eRTP_FALSE;
        bStatus = m_pobjAppInterface->rtcpPacketSendInd(pRtcpBuf, this);
        if (bStatus == eRTP_FALSE)
        {
            RTP_TRACE_WARNING("rtpSendRtcpPacket, RTCP send error.", RTP_ZERO, RTP_ZERO);
        }
    }
    else
    {
        RTP_TRACE_ERROR("rtpSendRtcpPacket, error in formRtcpPacket.", RTP_ZERO, RTP_ZERO);
        m_pobjAppInterface->rtcpTimerHdlErrorInd(eEncRes);
    }

    // update average rtcp size
    m_objTimerInfo.updateAvgRtcpSize(pRtcpBuf->getLength());
    delete pRtcpBuf;

    if (m_stRtcpXr.m_pBlockBuffer != nullptr)
    {
        delete m_stRtcpXr.m_pBlockBuffer;
        m_stRtcpXr.m_pBlockBuffer = nullptr;
    }

    return RTP_SUCCESS;
}

RtpDt_Void RtpSession::rtcpTimerExpiry(IN RtpDt_Void* pvTimerId)
{
    // RtpDt_UInt32 uiSamplingRate = RTP_ZERO;
    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);

    eRtp_Bool bSessAlive = eRTP_FALSE;

    RtpSessionManager* pobjActSesDb = RtpSessionManager::getInstance();
    bSessAlive = pobjActSesDb->isValidRtpSession(this);

    if (!bSessAlive)
    {
        return;
    }

    if (m_pTimerId == pvTimerId)
    {
        m_pTimerId = nullptr;
    }

    RtpDt_UInt16 usMembers = m_pobjRtpRcvrInfoList->size();
    RtpDt_UInt32 uiTempTc = m_objTimerInfo.getTc();
    RtpDt_Double dTempT = rtcp_interval(usMembers);

    // convert uiTempT to milliseconds
    dTempT = dTempT * RTP_SEC_TO_MILLISEC;
    RtpDt_UInt32 uiRoundDiff = (RtpDt_UInt32)dTempT;
    uiRoundDiff = ((uiRoundDiff / 100) * 100);

    // uiTempTn = m_objTimerInfo.getTp() + (RtpDt_UInt32)dTempT;
    RtpDt_UInt32 uiTempTn = m_objTimerInfo.getTp() + uiRoundDiff;

    RTP_TRACE_MESSAGE(
            "rtcpTimerExpiry [Tp : %u] [difference = %u]", m_objTimerInfo.getTp(), uiRoundDiff);

    RTP_TRACE_MESSAGE(
            "rtcpTimerExpiry [Tp : %u] [difference = %f]", m_objTimerInfo.getTp(), dTempT);

    RTP_TRACE_MESSAGE("rtcpTimerExpiry before processing[Tn : %u] [Tc : %u]", uiTempTn, uiTempTc);

    m_objTimerInfo.setTn(uiTempTn);
    RtpDt_UInt32 uiTimerVal = RTP_ZERO;
    RtpDt_Void* pvData = nullptr;

    if ((m_bSelfCollisionByeSent != eRTP_TRUE) || (m_bSndRtcpByePkt != eRTP_TRUE))
    {
        if (uiTempTn > uiTempTc)
        {
            uiTimerVal = uiTempTn - uiTempTc;
            if (uiTimerVal > uiRoundDiff)
            {
                uiTimerVal = uiRoundDiff;
            }
            /*uiTempTn = uiTempTc + uiRoundDiff;
            m_objTimerInfo.setTn(uiTempTn);
            m_objTimerInfo.setTp(uiTempTc);*/

            RTP_TRACE_MESSAGE("rtcpTimerExpiry [Tn : %u] [Tc : %u]", uiTempTn, uiTempTc);
            eRtp_Bool bTSres = eRTP_FALSE;

            bTSres = m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
            m_pTimerId = nullptr;
            if (bTSres == eRTP_FALSE)
            {
                return;
            }
            if (m_bEnableRTCP == eRTP_TRUE)
            {
                RtpDt_Void* pvSTRes = m_pobjAppInterface->RtpStartTimer(
                        uiTimerVal, eRTP_FALSE, m_pfnTimerCb, reinterpret_cast<RtpDt_Void*>(this));
                if (pvSTRes == nullptr)
                {
                    return;
                }
                m_pTimerId = pvSTRes;
            }
            return;
        }
    }

    // set timestamp
    rtpSetTimestamp();

    RtcpPacket objRtcpPkt;
    eRTP_STATUS_CODE eEncRes = RTP_FAILURE;

    eEncRes = rtpMakeCompoundRtcpPacket(&objRtcpPkt);
    if (eEncRes != RTP_SUCCESS)
    {
        RTP_TRACE_ERROR("MakeCompoundRtcpPacket Error: %d", eEncRes, RTP_ZERO);
        return;
    }

    // check number of packets are sent
    eEncRes = rtpSendRtcpPacket(&objRtcpPkt);
    if (eEncRes != RTP_SUCCESS)
    {
        RTP_TRACE_ERROR("rtpSendRtcpPacket Error: %d", eEncRes, RTP_ZERO);
        return;
    }
    // set Tp with Tc
    m_objTimerInfo.setTp(uiTempTc);

    // recalculate timer in
    dTempT = rtcp_interval(usMembers);
    dTempT = dTempT * RTP_SEC_TO_MILLISEC;
    uiRoundDiff = (RtpDt_UInt32)dTempT;
    uiTempTn = uiTempTc + uiRoundDiff;
    // uiTempTn = uiTempTc + dTempT;
    m_objTimerInfo.setTn(uiTempTn);

    // restart the timer
    // uiTimerVal = m_objTimerInfo.getTn() - uiTempTc;
    if (m_usRTCPTimerVal > RTP_ZERO)
    {
        uiTimerVal = m_usRTCPTimerVal * RTP_SEC_TO_MILLISEC;
    }
    else
    {
        uiTimerVal = uiRoundDiff;
    }

    // uiTimerVal = (RtpDt_UInt32)dTempT;
    //  Reschedule the next report for time tn
    if (m_pTimerId != nullptr)
    {
        eRtp_Bool bTSres = eRTP_FALSE;
        bTSres = m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
        m_pTimerId = nullptr;
        if (bTSres == eRTP_FALSE)
        {
            return;
        }
    }

    if (m_bEnableRTCP == eRTP_TRUE)
    {
        RtpDt_Void* pvSTRes = nullptr;

        pvSTRes = m_pobjAppInterface->RtpStartTimer(
                uiTimerVal, eRTP_FALSE, m_pfnTimerCb, reinterpret_cast<RtpDt_Void*>(this));

        if (pvSTRes == nullptr)
        {
            return;
        }
        m_pTimerId = pvSTRes;
    }

    // set m_bInitial = false
    m_objTimerInfo.setInitial(eRTP_FALSE);

    // update we_sent
    if (m_objTimerInfo.getWeSent() == RTP_TWO)
    {
        m_objTimerInfo.setWeSent(RTP_ONE);
    }
    else
    {
        m_objTimerInfo.setWeSent(RTP_ZERO);
    }

    // set pmembers with members
    m_objTimerInfo.setPmembers(usMembers);

    // set m_bRtpSendPkt to false
    m_bRtpSendPkt = eRTP_FALSE;

    return;
}  // rtcpTimerExpiry

eRTP_STATUS_CODE RtpSession::populateSrpacket(
        OUT RtcpSrPacket* pobjSrPkt, IN RtpDt_UInt32 uiRecepCount)
{
    tRTP_NTP_TIME* pstNtpTime = pobjSrPkt->getNtpTime();

    // NTP timestamp, most significant word
    pstNtpTime->m_uiNtpHigh32Bits = m_stCurNtpRtcpTs.m_uiNtpHigh32Bits;
    // NTP timestamp, least significant word
    pstNtpTime->m_uiNtpLow32Bits = m_stCurNtpRtcpTs.m_uiNtpLow32Bits;
    // RTCP timestamp
    pobjSrPkt->setRtpTimestamp(m_curRtcpTimestamp);
    // sender's packet count
    pobjSrPkt->setSendPktCount(m_uiRtpSendPktCount);
    // sender's octet count
    pobjSrPkt->setSendOctetCount(m_uiRtpSendOctCount);

    eRTP_STATUS_CODE eRepPktSta = RTP_FAILURE;
    eRepPktSta = populateReportPacket(pobjSrPkt->getRrPktInfo(), eRTP_FALSE, uiRecepCount);

    if (eRepPktSta != RTP_SUCCESS)
    {
        return eRepPktSta;
    }

    return RTP_SUCCESS;
}  // populateSrpacket

RtpDt_Void RtpSession::cleanUtlReceiverList()
{
    // populate report blocks
    for (const auto& pobjRcvrElm : *m_pobjUtlRcvrList)
    {
        delete pobjRcvrElm;
    }
    m_pobjUtlRcvrList->clear();
}  // cleanUtlReceiverList

eRTP_STATUS_CODE RtpSession::populateReportPacket(
        OUT RtcpRrPacket* pobjRrPkt, IN eRtp_Bool bRrPkt, IN RtpDt_UInt32 uiRecepCount)
{
    RtcpHeader* pRtcpHdr = pobjRrPkt->getRtcpHdrInfo();
    std::list<RtcpReportBlock*>& pobjRepBlkLst = pobjRrPkt->getReportBlockList();

    // get receiver list size
    if (bRrPkt == eRTP_TRUE)
    {
        pRtcpHdr->populateRtcpHeader((RtpDt_UChar)uiRecepCount, (RtpDt_UChar)RTCP_RR, m_uiSsrc);
    }
    else
    {
        pRtcpHdr->populateRtcpHeader((RtpDt_UChar)uiRecepCount, (RtpDt_UChar)RTCP_SR, m_uiSsrc);
    }

    if (uiRecepCount == RTP_ZERO)
    {
        return RTP_SUCCESS;
    }

    std::list<RtpReceiverInfo*>* pobjTmpRcvrList = m_pobjRtpRcvrInfoList;
    m_pobjUtlRcvrList = new std::list<RtpReceiverInfo*>();
    if (m_pobjUtlRcvrList == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_MEMORY_FAIL;
    }

    eRtp_Bool bFirstPos = eRTP_TRUE;
    RtpDt_UInt32 uiTmpRecpCount = RTP_ZERO;
    std::list<RtpReceiverInfo*>::iterator iter;
    // populate report blocks
    for (auto& pobjRcvrElm : *m_pobjRtpRcvrInfoList)
    {
        // get the member information
        if ((pobjRcvrElm->isSender() == eRTP_TRUE) && (uiTmpRecpCount <= uiRecepCount))
        {
            RtcpReportBlock* pobjRepBlk = new RtcpReportBlock();
            if (pobjRepBlk == nullptr)
            {
                RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                cleanUtlReceiverList();
                delete m_pobjUtlRcvrList;
                m_pobjUtlRcvrList = nullptr;
                return RTP_MEMORY_FAIL;
            }
            pobjRcvrElm->populateReportBlock(pobjRepBlk);
            pobjRepBlkLst.push_back(pobjRepBlk);
            pobjRcvrElm->setSenderFlag(eRTP_FALSE);
            m_pobjUtlRcvrList->push_back(pobjRcvrElm);
            uiTmpRecpCount = uiTmpRecpCount + RTP_ONE;
        }
        else
        {
            if (pobjRcvrElm->getCsrcFlag() == eRTP_TRUE)
            {
                m_pobjUtlRcvrList->push_back(pobjRcvrElm);
            }
            else
            {
                if (bFirstPos == eRTP_TRUE)
                {
                    m_pobjUtlRcvrList->push_front(pobjRcvrElm);
                    iter = m_pobjUtlRcvrList->begin();
                    bFirstPos = eRTP_FALSE;
                }
                else
                {
                    m_pobjUtlRcvrList->insert(iter, pobjRcvrElm);
                }
            }
        }
    }

    m_pobjRtpRcvrInfoList->clear();
    m_pobjRtpRcvrInfoList = m_pobjUtlRcvrList;
    delete pobjTmpRcvrList;
#ifdef ENABLE_RTCPEXT
    // Extension header
    if (m_usExtHdrLen > RTP_ZERO)
    {
        RtpBuffer* pobjExtHdrInfo = new RtpBuffer();
        if (pobjExtHdrInfo == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        else
        {
            m_pobjAppInterface->getRtpHdrExtInfo(pobjExtHdrInfo);
            pobjRrPkt->setExtHdrInfo(pobjExtHdrInfo);
        }
    }
#endif

    return RTP_SUCCESS;
}  // populateReportPacket

eRTP_STATUS_CODE RtpSession::populateByePacket(IN_OUT RtcpPacket* pobjRtcpPkt)
{
    RtcpByePacket* pobjByePkt = new RtcpByePacket();
    if (pobjByePkt == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_MEMORY_FAIL;
    }
    RtcpHeader* pRtcpHdr = pobjByePkt->getRtcpHdrInfo();

    pobjRtcpPkt->setByePacketData(pobjByePkt);
    // populate App packet header.
    pRtcpHdr->populateRtcpHeader((RtpDt_UChar)RTP_ONE, RTCP_BYE, m_uiSsrc);

    return RTP_SUCCESS;
}  // populateByePacket

eRTP_STATUS_CODE RtpSession::populateAppPacket(IN_OUT RtcpPacket* pobjRtcpPkt)
{
    RtcpAppPacket* pobjAppPkt = new RtcpAppPacket();
    if (pobjAppPkt == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_MEMORY_FAIL;
    }
    RtpBuffer* pobjPayload = new RtpBuffer();
    if (pobjPayload == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        delete pobjAppPkt;
        return RTP_MEMORY_FAIL;
    }
    // fill application dependent data
    pobjAppPkt->setAppData(pobjPayload);
    RtcpHeader* pRtcpHdr = pobjAppPkt->getRtcpHdrInfo();

    RtpDt_UInt16 usSubType = RTP_ZERO;
    RtpDt_UInt32 uiName = RTP_ZERO;
    eRtp_Bool bStatus = eRTP_FALSE;

    pobjRtcpPkt->setAppPktData(pobjAppPkt);
    bStatus = m_pobjAppInterface->rtcpAppPayloadReqInd(usSubType, uiName, pobjPayload);
    if (bStatus != eRTP_TRUE)
    {
        return RTP_FAILURE;
    }
    // populate App packet header.
    pRtcpHdr->populateRtcpHeader((RtpDt_UChar)usSubType, RTCP_APP, m_uiSsrc);

    // fill name
    pobjAppPkt->setName(uiName);

    return RTP_SUCCESS;
}  // populateAppPacket

eRTP_STATUS_CODE RtpSession::populateRtcpFbPacket(IN_OUT RtcpPacket* pobjRtcpPkt,
        IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen,
        IN RtpDt_UInt32 uiMediaSSRC, IN RtpDt_UInt32 uiPayloadType)
{
    // create RtcpFbPacket
    RtcpFbPacket* pobjRtcpRtpFbPacket = new RtcpFbPacket();

    // create payload FCI buffer
    RtpBuffer* pobjPayload = new RtpBuffer(uiLen, reinterpret_cast<RtpDt_UChar*>(pcBuff));

    // set Media SSRC
    pobjRtcpRtpFbPacket->setMediaSsrc(uiMediaSSRC);

    // set FCI data
    pobjRtcpRtpFbPacket->setFCI(pobjPayload);

    // set feedback type
    pobjRtcpRtpFbPacket->setPayloadType((eRTCP_TYPE)uiPayloadType);

    // set the RTCP packet
    pobjRtcpPkt->addFbPacketData(pobjRtcpRtpFbPacket);

    // get and populate the RTCP header
    RtcpHeader* pRtcpHdr = pobjRtcpRtpFbPacket->getRtcpHdrInfo();

    pRtcpHdr->populateRtcpHeader((RtpDt_UChar)uiFbType, uiPayloadType, m_uiSsrc);

    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::constructSdesPkt(IN_OUT RtcpPacket* pobjRtcpPkt)
{
    if (m_pobjRtcpCfgInfo == nullptr || pobjRtcpPkt == nullptr)
        return RTP_FAILURE;

    RtpDt_UInt32 uiSdesItems = m_pobjRtcpCfgInfo->getSdesItemCount();

    RtcpSdesPacket* pobjSdesPkt = new RtcpSdesPacket();
    if (pobjSdesPkt == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_MEMORY_FAIL;
    }

    RtcpChunk* pobjChunk = new RtcpChunk();
    if (pobjChunk == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        delete pobjSdesPkt;
        return RTP_MEMORY_FAIL;
    }

    RtcpHeader* pRtcpHdr = pobjSdesPkt->getRtcpHdrInfo();
    if (pRtcpHdr == nullptr)
    {
        RTP_TRACE_ERROR("Failed to retrieve Rtcp Header Info", RTP_ZERO, RTP_ZERO);
        delete pobjSdesPkt;
        delete pobjChunk;
        return RTP_FAILURE;
    }

    std::list<RtcpChunk*>& pobjSdesList = pobjSdesPkt->getSdesChunkList();

    pobjRtcpPkt->setSdesPacketData(pobjSdesPkt);

    // populate SDES packet header.
    pRtcpHdr->populateRtcpHeader(RTP_ONE, RTCP_SDES, m_uiSsrc);

    pobjSdesList.push_back(pobjChunk);

    pobjChunk->setSsrc(m_uiSsrc);
    std::list<tRTCP_SDES_ITEM*>& pobjChunkList = pobjChunk->getSdesItemList();

    for (RtpDt_UInt32 uiCount = RTP_ZERO; uiCount < uiSdesItems; uiCount++)
    {
        tRTCP_SDES_ITEM* pstSdesItem = m_pobjRtcpCfgInfo->getRtcpSdesItem(uiCount);

        if (pstSdesItem && pstSdesItem->pValue != nullptr)
        {
            tRTCP_SDES_ITEM* pstTmpSdesItem = new tRTCP_SDES_ITEM();
            if (pstTmpSdesItem == nullptr)
            {
                return RTP_MEMORY_FAIL;
            }

            RtpDt_UChar* pucSdesBuf = new RtpDt_UChar[pstSdesItem->ucLength];
            if (pucSdesBuf == nullptr)
            {
                delete pstTmpSdesItem;
                return RTP_MEMORY_FAIL;
            }

            pstTmpSdesItem->ucType = pstSdesItem->ucType;
            pstTmpSdesItem->ucLength = pstSdesItem->ucLength;
            memcpy(pucSdesBuf, pstSdesItem->pValue, pstSdesItem->ucLength);
            pstTmpSdesItem->pValue = pucSdesBuf;
            pobjChunkList.push_back(pstTmpSdesItem);
        }
    }

    return RTP_SUCCESS;
}  // constructSdesPkt

eRTP_STATUS_CODE RtpSession::disableRtp()
{
    m_bEnableRTP = eRTP_FALSE;
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::enableRtp()
{
    m_bEnableRTP = eRTP_TRUE;
    return RTP_SUCCESS;
}

eRtp_Bool RtpSession::isRtpEnabled()
{
    return m_bEnableRTP;
}

eRTP_STATUS_CODE RtpSession::enableRtcp(eRtp_Bool enableRTCPBye)
{
    RtpStackProfile* pobjRtpProfile = m_pobjRtpStack->getStackProfile();
    if (pobjRtpProfile == nullptr)
        return RTP_FAILURE;

    // timer value shall be in milli seconds
    RtpDt_UInt32 uiTimerVal = RTP_INIT_TRUE_T_MIN * RTP_SEC_TO_MILLISEC;

    if (m_usRTCPTimerVal > RTP_ZERO)
    {
        uiTimerVal = m_usRTCPTimerVal * RTP_SEC_TO_MILLISEC;
    }

    if (m_bEnableRTCP == eRTP_TRUE)
    {
        RTP_TRACE_WARNING("enableRtcp, m_bEnableRTCP is already enabled.", RTP_ZERO, RTP_ZERO);

        return RTP_RTCP_ALREADY_RUNNING;
    }

    m_bEnableRTCP = eRTP_TRUE;
    m_bEnableRTCPBye = enableRTCPBye;

    RtpSessionManager* pobjActSesDb = RtpSessionManager::getInstance();
    pobjActSesDb->addRtpSession(reinterpret_cast<RtpDt_Void*>(this));
    RtpDt_Void* pvData = nullptr;

    if (m_pTimerId != nullptr && m_pobjAppInterface != nullptr)
    {
        eRtp_Bool bTSres = eRTP_FALSE;
        bTSres = m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
        m_pTimerId = nullptr;
        if (bTSres == eRTP_FALSE)
        {
            RTP_TRACE_WARNING("enableRtcp, Stop timer is returned NULL value.", RTP_ZERO, RTP_ZERO);
            return RTP_TIMER_PROC_ERR;
        }
    }
    RtpDt_Void* pvSTRes = nullptr;

    if (m_pobjAppInterface != nullptr)
    {
        m_pfnTimerCb = Rtp_RtcpTimerCb;
        // start RTCP timer with default value
        pvSTRes = m_pobjAppInterface->RtpStartTimer(
                uiTimerVal, eRTP_FALSE, m_pfnTimerCb, reinterpret_cast<RtpDt_Void*>(this));
        if (pvSTRes == nullptr)
        {
            RTP_TRACE_WARNING(
                    "enableRtcp, start timer is returned NULL value.", RTP_ZERO, RTP_ZERO);
            return RTP_TIMER_PROC_ERR;
        }
    }

    m_pTimerId = pvSTRes;
    RtpDt_UInt32 uiTempTc = m_objTimerInfo.getTc();
    m_objTimerInfo.setTp(uiTempTc);
    m_objTimerInfo.setTn(uiTempTc + uiTimerVal);

    // RTCP BW
    m_objTimerInfo.setRtcpBw(pobjRtpProfile->getRtcpBandwidth());

    // AVG RTCP SIZE
    m_objTimerInfo.setAvgRtcpSize(pobjRtpProfile->getRtcpBandwidth());

    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::disableRtcp()
{
    RtpDt_Void* pvData = nullptr;

    RtpSessionManager* pobjActSesDb = RtpSessionManager::getInstance();
    pobjActSesDb->removeRtpSession(this);

    m_bEnableRTCP = eRTP_FALSE;
    m_bEnableRTCPBye = eRTP_FALSE;
    if (m_pTimerId != nullptr && m_pobjAppInterface != nullptr)
    {
        m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
        m_pTimerId = nullptr;
    }

    m_objTimerInfo.cleanUp();

    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::initSession(
        IN IRtpAppInterface* pobjAppInterface, IN RtcpConfigInfo* pobjRtcpConfigInfo)
{
    // set m_pobjAppInterface
    if (pobjAppInterface != nullptr)
    {
        if (m_pobjAppInterface != nullptr)
            delete m_pobjAppInterface;

        m_pobjAppInterface = pobjAppInterface;
    }
    else
    {
        RTP_TRACE_WARNING("initSession, pobjAppInterface is NULL.", RTP_ZERO, RTP_ZERO);

        return RTP_INVALID_PARAMS;
    }

    // set pobjRtcpConfigInfo
    if (pobjRtcpConfigInfo != nullptr)
    {
        if (m_pobjRtcpCfgInfo != nullptr)
            delete m_pobjRtcpCfgInfo;

        m_pobjRtcpCfgInfo = pobjRtcpConfigInfo;
    }

    // m_usExtHdrLen = usExtHdrLen;
    // generate sequence number
    m_usSeqNum = (RtpDt_UInt16)RtpOsUtil::Rand();
    m_curRtpTimestamp = (RtpDt_UInt16)RtpOsUtil::Rand();
    RtpOsUtil::GetNtpTime(m_stCurNtpTimestamp);
    return RTP_SUCCESS;
}  // initSession

eRTP_STATUS_CODE RtpSession::setPayload(
        IN RtpPayloadInfo* pstPayloadInfo, IN RtpDt_UInt16 usExtHdrLen)

{
    // set RTP payload information
    if (pstPayloadInfo != nullptr)
    {
        if (m_pobjPayloadInfo == nullptr)
        {
            RTP_TRACE_ERROR("setPayload, m_pobjPayloadInfo is NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_INVALID_PARAMS;
        }
        m_pobjPayloadInfo->setRtpPayloadInfo(pstPayloadInfo);
    }
    else
    {
        RTP_TRACE_ERROR("setPayload, pstPayloadInfo is NULL.", RTP_ZERO, RTP_ZERO);

        return RTP_INVALID_PARAMS;
    }

    m_usExtHdrLen = usExtHdrLen;

    return RTP_SUCCESS;
}  // setpayload

eRTP_STATUS_CODE RtpSession::updatePayload(IN RtpPayloadInfo* pstPayloadInfo)
{
    m_pobjPayloadInfo->setRtpPayloadInfo(pstPayloadInfo);

    return RTP_SUCCESS;
}  // updatePayload

eRTP_STATUS_CODE RtpSession::setRTCPTimerValue(IN RtpDt_UInt16 usRTCPTimerVal)
{
    m_usRTCPTimerVal = usRTCPTimerVal;
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::deleteRtpSession()
{
    RtpDt_Void* pvData = nullptr;

    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);

    RtpSessionManager* pobjActSesDb = RtpSessionManager::getInstance();
    pobjActSesDb->removeRtpSession(this);

    if (m_pTimerId != nullptr)
    {
        m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
        m_pTimerId = nullptr;
    }

    for (auto& pobjRcvrElm : *m_pobjRtpRcvrInfoList)
    {
        m_pobjAppInterface->deleteRcvrInfo(
                pobjRcvrElm->getSsrc(), pobjRcvrElm->getIpAddr(), pobjRcvrElm->getPort());
    }

    return RTP_SUCCESS;
}  // deleteRtpSession

eRTP_STATUS_CODE RtpSession::collisionSendRtcpByePkt(IN RtpDt_UInt32 uiReceivedSsrc)
{
    (RtpDt_Void) uiReceivedSsrc;

    m_bSelfCollisionByeSent = eRTP_TRUE;

    return RTP_SUCCESS;
}  // collisionSendRtcpByePkt

eRTP_STATUS_CODE RtpSession::chkRcvdSsrcStatus(
        IN RtpBuffer* pobjRtpAddr, IN RtpDt_UInt16 usPort, IN RtpDt_UInt32 uiRcvdSsrc)
{
    eRTP_STATUS_CODE eResult = RTP_SUCCESS;
    checkSsrcCollisionOnRcv(pobjRtpAddr, usPort, uiRcvdSsrc, eResult);
    return eResult;
}  // chkRcvdSsrcStatus

RtpReceiverInfo* RtpSession::checkSsrcCollisionOnRcv(IN RtpBuffer* pobjRtpAddr,
        IN RtpDt_UInt16 usPort, IN RtpDt_UInt32 uiRcvdSsrc, OUT eRTP_STATUS_CODE& eResult)
{
    if (m_pobjRtpRcvrInfoList == nullptr)
    {
        eResult = RTP_INVALID_PARAMS;
        return nullptr;
    }

    for (auto& pobjRcvInfo : *m_pobjRtpRcvrInfoList)
    {
        RtpDt_UInt32 uiTmpSsrc = RTP_ZERO;
        if (pobjRcvInfo == nullptr)
            break;

        uiTmpSsrc = pobjRcvInfo->getSsrc();
        if (uiTmpSsrc == uiRcvdSsrc)
        {
            RtpBuffer* pobjTmpDestAddr = pobjRcvInfo->getIpAddr();
            RtpDt_UChar* pcDestAddr = pobjTmpDestAddr->getBuffer();
            RtpDt_UChar* pcRcvDestAddr = pobjRtpAddr->getBuffer();
            RtpDt_UInt16 usTmpPort = pobjRcvInfo->getPort();
            RtpDt_UInt32 uiRcvDestAddrLen = pobjRtpAddr->getLength();

            if (pobjRcvInfo->getCsrcFlag() == eRTP_TRUE)
            {
                eResult = RTP_RCVD_CSRC_ENTRY;
                return pobjRcvInfo;
            }

            if (usTmpPort != usPort)
            {
                RTP_TRACE_WARNING("checkSsrcCollisionOnRcv - Port prevPort[%d], receivedPort[%d]",
                        usTmpPort, usPort);
                eResult = RTP_REMOTE_SSRC_COLLISION;
                return pobjRcvInfo;
            }

            if (pcDestAddr == nullptr || pcRcvDestAddr == nullptr)
            {
                eResult = RTP_INVALID_PARAMS;
                return nullptr;
            }

            if (memcmp(pcDestAddr, pcRcvDestAddr, uiRcvDestAddrLen) != RTP_ZERO)
            {
                eResult = RTP_REMOTE_SSRC_COLLISION;
                return pobjRcvInfo;
            }

            eResult = RTP_OLD_SSRC_RCVD;
            return pobjRcvInfo;
        }
    }

    eResult = RTP_NEW_SSRC_RCVD;
    return nullptr;
}  // checkSsrcCollisionOnRcv

eRtp_Bool RtpSession::findEntryInCsrcList(
        IN std::list<RtpDt_UInt32>& pobjCsrcList, IN RtpDt_UInt32 uiSsrc)
{
    if (pobjCsrcList.empty())
    {
        return eRTP_FALSE;
    }

    auto result = std::find(pobjCsrcList.begin(), pobjCsrcList.end(), uiSsrc);
    return (result != pobjCsrcList.end()) ? eRTP_TRUE : eRTP_FALSE;

}  // findEntryInCsrcList

eRtp_Bool RtpSession::findEntryInRcvrList(IN RtpDt_UInt32 uiSsrc)
{
    for (auto& pobjRcvInfo : *m_pobjRtpRcvrInfoList)
    {
        if (pobjRcvInfo != nullptr && pobjRcvInfo->getSsrc() == uiSsrc)
        {
            return eRTP_TRUE;
        }
    }

    return eRTP_FALSE;
}  // findEntryInRcvrList

eRTP_STATUS_CODE RtpSession::processCsrcList(
        IN RtpHeader* pobjRtpHeader, IN RtpDt_UChar ucCsrcCount)
{
    eRtp_Bool bRcvrStatus = eRTP_FALSE;
    RtpDt_UInt16 usPos = RTP_ZERO;
    std::list<RtpDt_UInt32>& pobjCsrcList = pobjRtpHeader->getCsrcList();

    for (std::list<RtpDt_UInt32>::iterator listIterator = pobjCsrcList.begin();
            (usPos < ucCsrcCount && listIterator != pobjCsrcList.end()); usPos = usPos + RTP_ONE)
    {
        RtpDt_UInt32 csrc = (*listIterator);
        bRcvrStatus = findEntryInRcvrList(csrc);
        if (bRcvrStatus == eRTP_FALSE)
        {
            RtpReceiverInfo* pobjRcvInfo = nullptr;
            pobjRcvInfo = new RtpReceiverInfo();
            if (pobjRcvInfo == nullptr)
            {
                RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                return RTP_MEMORY_FAIL;
            }
            // fill pobjRcvInfo
            //  ssrc
            pobjRcvInfo->setSsrc(csrc);
            // m_bSender
            pobjRcvInfo->setSenderFlag(eRTP_FALSE);

            pobjRcvInfo->setCsrcFlag(eRTP_TRUE);

            // add entry into receiver list.
            m_pobjRtpRcvrInfoList->push_back(pobjRcvInfo);
            RTP_TRACE_MESSAGE("processCsrcList - added ssrc[%x] from port[%d] to receiver list",
                    pobjRcvInfo->getSsrc(), pobjRcvInfo->getPort());
        }
        ++listIterator;
    }
    return RTP_SUCCESS;
}  // processCsrcList

eRTP_STATUS_CODE RtpSession::processRcvdRtpPkt(IN RtpBuffer* pobjRtpAddr, IN RtpDt_UInt16 usPort,
        IN RtpBuffer* pobjRTPPacket, OUT RtpPacket* pobjRtpPkt)
{
    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);

    // validation
    if ((pobjRTPPacket == nullptr) || (pobjRtpPkt == nullptr) || (pobjRtpAddr == nullptr))
    {
        RTP_TRACE_WARNING(
                "processRcvdRtpPkt, pobjRTPPacket || pobjRtpPkt is NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_INVALID_PARAMS;
    }

    RtpDt_UInt32 uiRcvdOcts = pobjRTPPacket->getLength();

    // decode the packet
    eRtp_Bool eRtpDecodeRes = eRTP_FAILURE;
    eRtpDecodeRes = pobjRtpPkt->decodePacket(pobjRTPPacket);

    if (eRtpDecodeRes == eRTP_FAILURE)
    {
        RTP_TRACE_WARNING("processRcvdRtpPkt -RTP_DECODE_ERROR", RTP_ZERO, RTP_ZERO);
        return RTP_DECODE_ERROR;
    }

    RtpHeader* pobjRtpHeader = pobjRtpPkt->getRtpHeader();

    // check received payload type is matching with expected RTP payload types.
    if (!checkRtpPayloadType(pobjRtpHeader, m_pobjPayloadInfo))
    {
        RTP_TRACE_WARNING(
                "processRcvdRtpPkt -eRcvdResult == RTP_INVALID_PARAMS.invalid payload type)",
                RTP_ZERO, RTP_ZERO);

        return RTP_INVALID_PARAMS;
    }
    // check received ssrc is matching with the current RTP session.

    RtpDt_UInt32 uiReceivedSsrc = pobjRtpHeader->getRtpSsrc();
    RtpDt_UChar ucCsrcCount = pobjRtpHeader->getCsrcCount();
    eRtp_Bool bCsrcStatus = eRTP_FALSE;

    if (ucCsrcCount > RTP_ZERO)
    {
        std::list<RtpDt_UInt32>& pobjCsrcList = pobjRtpHeader->getCsrcList();
        bCsrcStatus = findEntryInCsrcList(pobjCsrcList, m_uiSsrc);
    }

    if ((uiReceivedSsrc == m_uiSsrc) || (bCsrcStatus == eRTP_TRUE))
    {
        RtpStackProfile* pobjRtpProfile = m_pobjRtpStack->getStackProfile();
        RtpDt_UInt32 uiTermNum = pobjRtpProfile->getTermNumber();
        eRTP_STATUS_CODE eByeRes = RTP_SUCCESS;

        // collision happened.
        if ((m_bEnableRTCP == eRTP_TRUE) && (m_bEnableRTCPBye == eRTP_TRUE) &&
                (m_bRtpSendPkt == eRTP_TRUE))
        {
            eByeRes = collisionSendRtcpByePkt(uiReceivedSsrc);
            if (eByeRes != RTP_SUCCESS)
            {
                RTP_TRACE_WARNING("processRcvdRtpPkt -eByeRes", RTP_ZERO, RTP_ZERO);
                return eByeRes;
            }
        }
        else
        {
            // generate SSRC
            RtpDt_UInt32 uiNewSsrc = RTP_ZERO;
            uiNewSsrc = RtpStackUtil::generateNewSsrc(uiTermNum);
            m_uiSsrc = uiNewSsrc;
        }
        RTP_TRACE_WARNING("processRcvdRtpPkt  RTP_OWN_SSRC_COLLISION)", RTP_ZERO, RTP_ZERO);

        return RTP_OWN_SSRC_COLLISION;
    }

    // check SSRC collision on m_objRtpRcvrInfoList
    eRTP_STATUS_CODE eRcvdResult = RTP_FAILURE;
    RtpReceiverInfo* pobjRcvInfo =
            checkSsrcCollisionOnRcv(pobjRtpAddr, usPort, uiReceivedSsrc, eRcvdResult);

    if (eRcvdResult == RTP_REMOTE_SSRC_COLLISION)
    {
        RTP_TRACE_WARNING(
                "processRcvdRtpPkt -eRcvdResult == RTP_REMOTE_SSRC_COLLISION)", RTP_ZERO, RTP_ZERO);
        return eRcvdResult;
    }

    if (eRcvdResult != RTP_NEW_SSRC_RCVD && pobjRcvInfo == nullptr)
    {
        RTP_TRACE_WARNING(
                "processRcvdRtpPkt -eRcvdResult == RTP_INVALID_PARAMS. pobjRcvInfo is NULL)",
                RTP_ZERO, RTP_ZERO);

        return RTP_INVALID_PARAMS;
    }

    if (eRcvdResult == RTP_NEW_SSRC_RCVD)
    {
        // add entry into the list.
        pobjRcvInfo = new RtpReceiverInfo();
        if (pobjRcvInfo == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }

        // initialize the rcvr info
        pobjRcvInfo->initSeq(pobjRtpHeader->getSequenceNumber());

        // populate pobjRcvInfo object
        // ip address
        pobjRcvInfo->setIpAddr(pobjRtpAddr);
        // port
        pobjRcvInfo->setPort(usPort);
        // ssrc
        pobjRcvInfo->setSsrc(uiReceivedSsrc);
        // m_bSender
        pobjRcvInfo->setSenderFlag(eRTP_TRUE);

        pobjRcvInfo->setprevRtpTimestamp(m_curRtpTimestamp);

        pobjRcvInfo->setprevNtpTimestamp(&m_stCurNtpTimestamp);

        m_pobjRtpRcvrInfoList->push_back(pobjRcvInfo);
        RTP_TRACE_MESSAGE("processRcvdRtpPkt - added ssrc[%x] from port[%d] to receiver list",
                pobjRcvInfo->getSsrc(), pobjRcvInfo->getPort());

        // first RTP packet received
        m_bFirstRtpRecvd = eRTP_TRUE;
    }  // RTP_NEW_SSRC_RCVD
    else if (m_bFirstRtpRecvd == eRTP_FALSE)
    {
        // initialize the receiver info
        pobjRcvInfo->initSeq(pobjRtpHeader->getSequenceNumber());
        // m_bSender
        pobjRcvInfo->setSenderFlag(eRTP_TRUE);
        // first RTP packet received
        m_bFirstRtpRecvd = eRTP_TRUE;
    }

    if (eRcvdResult == RTP_RCVD_CSRC_ENTRY)
    {
        pobjRcvInfo->initSeq(pobjRtpHeader->getSequenceNumber());
        // ip address
        pobjRcvInfo->setIpAddr(pobjRtpAddr);
        // port
        pobjRcvInfo->setPort(usPort);
        // m_bSender
        pobjRcvInfo->setSenderFlag(eRTP_TRUE);
    }  // RTP_RCVD_CSRC_ENTRY

    // process CSRC list
    processCsrcList(pobjRtpHeader, ucCsrcCount);

    if (pobjRcvInfo == nullptr)
        return RTP_SUCCESS;

    // calculate interarrival jitter
    pobjRcvInfo->calcJitter(pobjRtpHeader->getRtpTimestamp(), m_pobjPayloadInfo->getSamplingRate());

    // update ROC
    RtpDt_UInt16 usTempSeqNum = pobjRtpHeader->getSequenceNumber();
    RtpDt_UInt32 uiUpdateSeqRes = pobjRcvInfo->updateSeq(usTempSeqNum);

    // update statistics
    //  m_uiTotalRcvdRtpPkts
    pobjRcvInfo->incrTotalRcvdRtpPkts();
    // m_uiTotalRcvdRtpOcts
    pobjRcvInfo->incrTotalRcvdRtpOcts(uiRcvdOcts);

    // m_bSender
    pobjRcvInfo->setSenderFlag(eRTP_TRUE);

    if (uiUpdateSeqRes == RTP_ZERO)
    {
        RTP_TRACE_WARNING(
                "processRcvdRtpPkt -uiUpdateSeqRes == RTP_ZERO - RTP_BAD_SEQ)", RTP_ZERO, RTP_ZERO);
        return RTP_BAD_SEQ;
    }

    return RTP_SUCCESS;
}  // processRcvdRtpPkt

eRTP_STATUS_CODE RtpSession::populateRtpHeader(
        IN_OUT RtpHeader* pobjRtpHdr, IN eRtp_Bool eSetMarker, IN RtpDt_UChar ucPayloadType)
{
    // version
    pobjRtpHdr->setVersion((RtpDt_UChar)RTP_VERSION_NUM);

    // marker
    if (eSetMarker == eRTP_TRUE)
    {
        pobjRtpHdr->setMarker();
    }

    // payload type
    pobjRtpHdr->setPayloadType((RtpDt_UChar)ucPayloadType);

    // sequence number
    if (m_uiRtpSendPktCount == RTP_ZERO)
    {
        pobjRtpHdr->setSequenceNumber(m_usSeqNum);
    }
    else
    {
        m_usSeqNum++;
        pobjRtpHdr->setSequenceNumber(m_usSeqNum);
    }

    // Synchronization source
    pobjRtpHdr->setRtpSsrc(m_uiSsrc);

    return RTP_SUCCESS;
}  // populateRtpHeader

eRTP_STATUS_CODE RtpSession::createRtpPacket(IN RtpBuffer* pobjPayload, IN eRtp_Bool eSetMarker,
        IN RtpDt_UChar ucPayloadType, IN eRtp_Bool bUseLastTimestamp,
        IN RtpDt_UInt32 uiRtpTimestampDiff, IN RtpBuffer* pobjXHdr, OUT RtpBuffer* pRtpPkt)
{
    RtpPacket objRtpPacket;
    RtpHeader* pobjRtpHdr = objRtpPacket.getRtpHeader();

    // populate Rtp header information.
    populateRtpHeader(pobjRtpHdr, eSetMarker, ucPayloadType);
    if (pobjXHdr && pobjXHdr->getLength() > RTP_ZERO)
        pobjRtpHdr->setExtension(RTP_ONE);
    else
        pobjRtpHdr->setExtension(RTP_ZERO);

    // set timestamp
    m_stPrevNtpTimestamp = m_stCurNtpTimestamp;
    m_prevRtpTimestamp = m_curRtpTimestamp;
    RtpDt_UInt32 uiSamplingRate = RTP_ZERO;

    if (!bUseLastTimestamp)
    {
        m_stPrevNtpTimestamp = m_stCurNtpTimestamp;
        m_prevRtpTimestamp = m_curRtpTimestamp;
        RtpOsUtil::GetNtpTime(m_stCurNtpTimestamp);

        if (m_uiRtpSendPktCount == RTP_ZERO)
        {
            m_stPrevNtpTimestamp = m_stCurNtpTimestamp;
        }

        if (uiRtpTimestampDiff)
        {
            m_curRtpTimestamp += uiRtpTimestampDiff;
        }
        else
        {
            uiSamplingRate = m_pobjPayloadInfo->getSamplingRate();
            m_curRtpTimestamp = RtpStackUtil::calcRtpTimestamp(m_prevRtpTimestamp,
                    &m_stCurNtpTimestamp, &m_stPrevNtpTimestamp, uiSamplingRate);
        }
    }

    pobjRtpHdr->setRtpTimestamp(m_curRtpTimestamp);

    // set pobjPayload to RtpPacket
    objRtpPacket.setRtpPayload(pobjPayload);

    // construct RTP packet
    RtpDt_UInt32 uiRtpLength = pobjPayload->getLength();
#ifdef ENABLE_PADDING
    RtpDt_UInt32 uiPadLength = uiRtpLength % RTP_FOUR;
    if (uiPadLength > RTP_ZERO)
    {
        uiPadLength = RTP_FOUR - uiPadLength;
        uiRtpLength += uiPadLength;

        // set padding bit to header
        pobjRtpHdr->setPadding();
    }
#endif
    uiRtpLength += RTP_FIXED_HDR_LEN;
    uiRtpLength += (RTP_FOUR * pobjRtpHdr->getCsrcCount());
    if (pobjXHdr)
        uiRtpLength += pobjXHdr->getLength();

    RtpDt_UChar* pucRtpBuffer = new RtpDt_UChar[uiRtpLength];
    if (pucRtpBuffer == nullptr)
    {
        RTP_TRACE_WARNING("createRtpPacket, error in allocating memory ", RTP_ZERO, RTP_ZERO);
        // set rtp payload as NULL in RtpPacket
        objRtpPacket.setRtpPayload(nullptr);
        return RTP_MEMORY_FAIL;
    }
    memset(pucRtpBuffer, RTP_ZERO, uiRtpLength);

    pRtpPkt->setBufferInfo(uiRtpLength, pucRtpBuffer);

    if ((pobjXHdr && pobjXHdr->getLength() > RTP_ZERO))
    {
        objRtpPacket.setExtHeader(pobjXHdr);
    }
    else
    {
        delete pobjXHdr;
    }

    // encode the Rtp packet.
    eRtp_Bool bPackRes = eRTP_TRUE;
    bPackRes = objRtpPacket.formPacket(pRtpPkt);

    // set pobjPayload to NULL in both success and failure case
    objRtpPacket.setRtpPayload(nullptr);

    if (bPackRes != eRTP_TRUE)
    {
        RTP_TRACE_WARNING(
                "createRtpPacket - formPacket failed!! bPackRes != eRTP_TRUE", RTP_ZERO, RTP_ZERO);
        return RTP_ENCODE_ERROR;
    }

    // update statistics
    m_uiRtpSendPktCount++;
    m_uiRtpSendOctCount += pobjPayload->getLength();

    // set we_sent flag as true
    m_objTimerInfo.setWeSent(RTP_TWO);

    // set m_bRtpSendPkt to true
    m_bRtpSendPkt = eRTP_TRUE;

    return RTP_SUCCESS;
}  // createRtpPacket

RtpReceiverInfo* RtpSession::processRtcpPkt(
        IN RtpDt_UInt32 uiRcvdSsrc, IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort)
{
    eRTP_STATUS_CODE eRcvdResult = RTP_SUCCESS;

    // check SSRC collision on m_objRtpRcvrInfoList
    RtpReceiverInfo* pobjRcvInfo =
            checkSsrcCollisionOnRcv(pobjRtcpAddr, usPort, uiRcvdSsrc, eRcvdResult);

    if (eRcvdResult == RTP_NEW_SSRC_RCVD)
    {
        // add entry into the list.
        pobjRcvInfo = new RtpReceiverInfo();
        if (pobjRcvInfo == nullptr)
        {
            return nullptr;
        }
        // populate pobjRcvInfo object
        // ip address
        pobjRcvInfo->setIpAddr(pobjRtcpAddr);
        // port
        pobjRcvInfo->setPort(usPort);
        // ssrc
        pobjRcvInfo->setSsrc(uiRcvdSsrc);
        m_pobjRtpRcvrInfoList->push_back(pobjRcvInfo);
        RTP_TRACE_MESSAGE("processRtcpPkt - added ssrc[%x] from port[%d] to receiver list",
                pobjRcvInfo->getSsrc(), pobjRcvInfo->getPort());
    }
    else if (eRcvdResult != RTP_INVALID_PARAMS)
    {
        // m_bSender should be FALSE for RTCP
        /* if(pobjRcvInfo != nullptr)
         {
            pobjRcvInfo->setSenderFlag(eRTP_FALSE);
         }
        */
    }

    return pobjRcvInfo;
}  // processRtcpPkt

RtpDt_Void RtpSession::delEntryFromRcvrList(IN RtpDt_UInt32* puiSsrc)
{
    for (auto it = m_pobjRtpRcvrInfoList->begin(); it != m_pobjRtpRcvrInfoList->end();)
    {
        if ((*it)->getSsrc() == *puiSsrc)
        {
            it = m_pobjRtpRcvrInfoList->erase(it);
            break;
        }
        else
        {
            ++it;
        }
    }
}  // delEntryFromRcvrList

eRTP_STATUS_CODE RtpSession::processByePacket(
        IN RtcpByePacket* pobjByePkt, IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort)
{
    (RtpDt_Void) pobjRtcpAddr, (RtpDt_Void)usPort;

    RtcpHeader* pobjHdrInfo = pobjByePkt->getRtcpHdrInfo();
    RtpDt_UInt16 usNumSsrc = pobjHdrInfo->getReceptionReportCount();
    std::list<RtpDt_UInt32*>& pobjSsrcList = pobjByePkt->getSsrcList();
    RtpDt_UInt16 usPos = RTP_ZERO;

    // delete entry from receiver list
    for (std::list<RtpDt_UInt32*>::iterator listIterator = pobjSsrcList.begin();
            (usPos < usNumSsrc && listIterator != pobjSsrcList.end()); usPos = usPos + RTP_ONE)
    {
        if (usPos == RTP_ZERO)
        {
            RtpDt_UInt32 uiSsrc = pobjHdrInfo->getSsrc();
            delEntryFromRcvrList(&uiSsrc);
        }
        else
        {
            RtpDt_UInt32* puiSsrc = nullptr;
            // get element from list
            puiSsrc = (*listIterator);
            if (puiSsrc != nullptr)
            {
                delEntryFromRcvrList(puiSsrc);
            }
            ++listIterator;
        }
    }  // for

    // get size of the pobjSsrcList
    eRtp_Bool bByeResult = eRTP_FALSE;
    RtpDt_UInt16 usRcvrNum = m_pobjRtpRcvrInfoList->size();
    bByeResult = m_objTimerInfo.updateByePktInfo(usRcvrNum);

    if ((bByeResult == eRTP_TRUE) && (m_bEnableRTCP == eRTP_TRUE) &&
            (m_pobjAppInterface != nullptr))
    {
        // Reschedule the next report for time tn
        RtpDt_UInt32 uiTempTn = m_objTimerInfo.getTn();
        RtpDt_UInt32 uiTempTc = m_objTimerInfo.getTc();
        RTP_TRACE_MESSAGE(
                "processByePacket before processing[Tn : %u] [Tc : %u]", uiTempTn, uiTempTc);

        RtpDt_UInt16 usMembers = m_pobjRtpRcvrInfoList->size();
        uiTempTc = m_objTimerInfo.getTc();
        RtpDt_Double dTempT = rtcp_interval(usMembers);

        // convert uiTempT to milliseconds
        RtpDt_UInt32 uiTimerVal = RTP_ZERO;

        dTempT = dTempT * RTP_SEC_TO_MILLISEC;
        RtpDt_UInt32 uiRoundDiff = (RtpDt_UInt32)dTempT;
        uiRoundDiff = ((uiRoundDiff / 100) * 100);

        if (uiTempTn > uiTempTc)
        {
            uiTimerVal = uiTempTn - uiTempTc;
            if (uiTimerVal > uiRoundDiff)
            {
                uiTimerVal = uiRoundDiff;
            }
        }
        else
        {
            uiTimerVal = uiRoundDiff;
        }

        RTP_TRACE_MESSAGE("processByePacket [uiTimerVal : %u]", uiTimerVal, RTP_ZERO);
        RtpDt_Void* pvData = nullptr;
        eRtp_Bool bTSres = eRTP_FALSE;

        if (m_pTimerId != nullptr)
        {
            bTSres = m_pobjAppInterface->RtpStopTimer(m_pTimerId, &pvData);
            m_pTimerId = nullptr;
            if (bTSres == eRTP_FALSE)
            {
                return RTP_TIMER_PROC_ERR;
            }
        }
        RtpDt_Void* pvSTRes =
                m_pobjAppInterface->RtpStartTimer(uiTimerVal, eRTP_FALSE, m_pfnTimerCb, this);
        if (pvSTRes == nullptr)
        {
            return RTP_TIMER_PROC_ERR;
        }
        m_pTimerId = pvSTRes;
    }

    return RTP_SUCCESS;
}  // processByePacket

eRTP_STATUS_CODE RtpSession::processSdesPacket(IN RtcpSdesPacket* pobjSdesPkt)
{
    (RtpDt_Void) pobjSdesPkt;
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::processRcvdRtcpPkt(IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort,
        IN RtpBuffer* pobjRTCPBuf, OUT RtcpPacket* pobjRtcpPkt)
{
    if (m_bEnableRTCP != eRTP_TRUE)
    {
        RTP_TRACE_WARNING("[ProcessRcvdRtcpPkt], RTCP is not enabled", RTP_ZERO, RTP_ZERO);

        return RTP_NO_RTCP_SUPPORT;
    }

    // validity checking
    if (pobjRtcpAddr == nullptr || pobjRTCPBuf == nullptr || pobjRtcpPkt == nullptr)
    {
        RTP_TRACE_ERROR("[ProcessRcvdRtcpPkt] Invalid params. pobjRtcpAddr[%x] pobjRTCPBuf[%x]",
                pobjRtcpAddr, pobjRTCPBuf);
        return RTP_INVALID_PARAMS;
    }

    RtpDt_UInt16 usExtHdrLen = RTP_ZERO;
    eRTP_STATUS_CODE eDecodeStatus = RTP_FAILURE;

    tRTP_NTP_TIME stNtpTs = {RTP_ZERO, RTP_ZERO};
    RtpOsUtil::GetNtpTime(stNtpTs);
    RtpDt_UInt32 currentTime = RtpStackUtil::getMidFourOctets(&stNtpTs);
    // decode compound packet
    eDecodeStatus = pobjRtcpPkt->decodeRtcpPacket(pobjRTCPBuf, usExtHdrLen, m_pobjRtcpCfgInfo);
    if (eDecodeStatus != RTP_SUCCESS)
    {
        RTP_TRACE_ERROR(
                "[ProcessRcvdRtcpPkt], Error Decoding compound RTCP packet!", RTP_ZERO, RTP_ZERO);
        return eDecodeStatus;
    }

    // update average rtcp size
    RtpDt_UInt32 uiRcvdPktSize = pobjRTCPBuf->getLength();
    m_objTimerInfo.updateAvgRtcpSize(uiRcvdPktSize);

    std::list<RtcpSrPacket*>& pobjSrList = pobjRtcpPkt->getSrPacketList();

    if (pobjSrList.size() > RTP_ZERO)
    {
        // get key material element from list.
        RtcpSrPacket* pobjSrPkt = pobjSrList.front();

        RtcpRrPacket* pobjRRPkt = pobjSrPkt->getRrPktInfo();
        RtcpHeader* pobjRtcpHdr = pobjRRPkt->getRtcpHdrInfo();
        RtpDt_UInt32 uiRcvdSsrc = pobjRtcpHdr->getSsrc();

        // calculate RTTD
        std::list<RtcpReportBlock*>& pobjReportBlkList = pobjRRPkt->getReportBlockList();
        if (pobjReportBlkList.size() > RTP_ZERO)
        {
            RtcpReportBlock* pobjReportBlk = pobjReportBlkList.front();
            if (pobjReportBlk != nullptr)
            {
                calculateAndSetRTTD(
                        currentTime, pobjReportBlk->getLastSR(), pobjReportBlk->getDelayLastSR());
            }
        }
        // decrement rtcp port by one
        usPort = usPort - RTP_ONE;
        RtpReceiverInfo* pobjRcvInfo = processRtcpPkt(uiRcvdSsrc, pobjRtcpAddr, usPort);
        if (pobjRcvInfo != nullptr)
        {
            stNtpTs = {RTP_ZERO, RTP_ZERO};
            pobjRcvInfo->setpreSrTimestamp(pobjSrPkt->getNtpTime());
            RtpOsUtil::GetNtpTime(stNtpTs);
            pobjRcvInfo->setLastSrNtpTimestamp(&stNtpTs);
        }
    }  // RTCP SR

    // RTCP RR packet
    std::list<RtcpRrPacket*>& pobjRrList = pobjRtcpPkt->getRrPacketList();

    if (pobjRrList.size() > RTP_ZERO)
    {
        // get key material element from list.
        RtcpRrPacket* pobjRrPkt = pobjRrList.front();

        // calculate RTTD
        std::list<RtcpReportBlock*>& pobjReportBlkList = pobjRrPkt->getReportBlockList();
        if (pobjReportBlkList.size() > RTP_ZERO)
        {
            RtcpReportBlock* pobjReportBlk = pobjReportBlkList.front();
            if (pobjReportBlk != nullptr)
            {
                calculateAndSetRTTD(
                        currentTime, pobjReportBlk->getLastSR(), pobjReportBlk->getDelayLastSR());
            }
        }

        RtcpHeader* pobjRtcpHdr = pobjRrPkt->getRtcpHdrInfo();
        RtpDt_UInt32 uiRcvdSsrc = pobjRtcpHdr->getSsrc();

        // decrement rtcp port by one
        usPort = usPort - RTP_ONE;
        processRtcpPkt(uiRcvdSsrc, pobjRtcpAddr, usPort);
    }  // RTCP RR

    // RTCP SDES packet
    RtcpSdesPacket* pobjSdesPkt = pobjRtcpPkt->getSdesPacket();
    if (pobjSdesPkt != nullptr)
    {
        // sdes
        processSdesPacket(pobjSdesPkt);
    }

    // RTCP BYE packet
    RtcpByePacket* pobjByePkt = pobjRtcpPkt->getByePacket();
    if (pobjByePkt != nullptr)
    {
        // bye
        processByePacket(pobjByePkt, pobjRtcpAddr, usPort);
    }

    return RTP_SUCCESS;
}  // processRcvdRtcpPkt

eRtp_Bool RtpSession::sendRtcpByePacket()
{
    RtcpPacket objRtcpPkt;
    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);

    if (m_bEnableRTCP == eRTP_TRUE && m_bEnableRTCPBye == eRTP_TRUE)
    {
        m_bSndRtcpByePkt = eRTP_TRUE;

        // set timestamp
        rtpSetTimestamp();

        if (rtpMakeCompoundRtcpPacket(&objRtcpPkt) != RTP_SUCCESS)
        {
            return eRTP_FALSE;
        }

        if (rtpSendRtcpPacket(&objRtcpPkt) == RTP_SUCCESS)
        {
            if (m_bSelfCollisionByeSent == eRTP_TRUE)
            {
                // generate SSRC
                RtpStackProfile* pobjRtpProfile = m_pobjRtpStack->getStackProfile();
                RtpDt_UInt32 uiTermNum = pobjRtpProfile->getTermNumber();
                RtpDt_UInt32 uiNewSsrc = RTP_ZERO;
                uiNewSsrc = RtpStackUtil::generateNewSsrc(uiTermNum);
                m_uiSsrc = uiNewSsrc;
                RTP_TRACE_WARNING(
                        "sendRtcpByePacket::SSRC after collision: %x", m_uiSsrc, RTP_ZERO);
            }

            return eRTP_TRUE;
        }
    }

    return eRTP_FALSE;
}

eRtp_Bool RtpSession::sendRtcpRtpFbPacket(IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcbuff,
        IN RtpDt_UInt32 uiLen, IN RtpDt_UInt32 uiMediaSsrc)
{
    RtcpPacket objRtcpPkt;

    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);
    // set timestamp
    rtpSetTimestamp();

    if (rtpMakeCompoundRtcpPacket(&objRtcpPkt) != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }
    populateRtcpFbPacket(&objRtcpPkt, uiFbType, pcbuff, uiLen, uiMediaSsrc, RTCP_RTPFB);

    if (rtpSendRtcpPacket(&objRtcpPkt) == RTP_SUCCESS)
    {
        return eRTP_TRUE;
    }

    return eRTP_FALSE;
}

eRtp_Bool RtpSession::sendRtcpPayloadFbPacket(IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcbuff,
        IN RtpDt_UInt32 uiLen, IN RtpDt_UInt32 uiMediaSsrc)
{
    RtcpPacket objRtcpPkt;

    std::lock_guard<std::mutex> guard(m_objRtpSessionLock);
    // set timestamp
    rtpSetTimestamp();

    if (rtpMakeCompoundRtcpPacket(&objRtcpPkt) != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }

    populateRtcpFbPacket(&objRtcpPkt, uiFbType, pcbuff, uiLen, uiMediaSsrc, RTCP_PSFB);

    if (rtpSendRtcpPacket(&objRtcpPkt) == RTP_SUCCESS)
    {
        return eRTP_TRUE;
    }

    return eRTP_FALSE;
}

RtpDt_Double RtpSession::rtcp_interval(IN RtpDt_UInt16 usMembers)
{
    RtpDt_Double const RTCP_MIN_TIME = 5.;

    /**
     * The target RTCP bandwidth, i.e., the total bandwidth
     * that will be used for RTCP packets by all members of this session,
     * in octets per second.  This will be a specified fraction of the
     * "session bandwidth" parameter supplied to the application at
     * startup.
     */
    RtpDt_Double ulRtcp_bw = m_objTimerInfo.getRtcpBw();

    RtpDt_Double const RTCP_SENDER_BW_FRACTION = 0.25L;
    RtpDt_Double const RTCP_RCVR_BW_FRACTION = (1 - RTCP_SENDER_BW_FRACTION);
    RtpDt_Double const COMPENSATION = 2.71828 - 1.5;
    RtpDt_Double ulRtcp_min_time = RTCP_MIN_TIME;
    RtpDt_Double ulTimerVal = RTP_ZERO;
    RtpDt_Double dDefTimerVal = 2.5;

    if (usMembers == RTP_ZERO)
    {
        RTP_TRACE_MESSAGE("rtcp_interval usmebers is equal to 0", nullptr, nullptr);
        ulTimerVal = dDefTimerVal;
        return ulTimerVal;
    }

    /*
     * Very first call at application start-up uses half the min
     * delay for quicker notification while still allowing some time
     * before reporting for randomization and to learn about other
     * sources so the report interval will converage to the correct
     * interval more quickly.
     */
    if (m_objTimerInfo.isInitial())
    {
        ulRtcp_min_time /= RTP_TWO;
    }
    /*
     * Dedicate a fraction of the RTCP bandwidth to senders unless
     * the number of senders is large enough that their share is
     * more than that fraction.
     */

    RtpDt_Int32 uiNumMemComp = RTP_ZERO;
    uiNumMemComp = usMembers;
    RtpDt_UInt32 uiSenders = getSenderCount();

    if (uiSenders <= usMembers * RTCP_SENDER_BW_FRACTION)
    {
        if (m_objTimerInfo.getWeSent() != RTP_ZERO)
        {
            ulRtcp_bw *= RTCP_SENDER_BW_FRACTION;
            uiNumMemComp = uiSenders;
        }
        else
        {
            ulRtcp_bw *= RTCP_RCVR_BW_FRACTION;
            uiNumMemComp -= uiSenders;
        }
    }
    // m_objTimerInfo.setRtcpBw(ulRtcp_bw);

    /*
     * The effective number of sites times the average packet size is
     * the total number of octets sent when each site sends a report.
     * Dividing this by the effective bandwidth gives the time
     * interval over which those packets must be sent in order to
     * meet the bandwidth target, with a minimum enforced.  In that
     * time interval we send one report so this time is also our
     * average time between reports.
     */
    ulTimerVal = m_objTimerInfo.getAvgRtcpSize() * uiNumMemComp / ulRtcp_bw;
    if (ulTimerVal < ulRtcp_min_time)
    {
        ulTimerVal = ulRtcp_min_time;
    }

    /*
     * To avoid traffic bursts from unintended synchronization with
     * other sites, we then pick our actual next report interval as a
     * random number uniformly distributed between 0.5*t and 1.5*t.
     */

    RtpDt_Double ulRRand = RTP_ZERO;
    ulRRand = RtpOsUtil::RRand();
    ulTimerVal = ulTimerVal * ((ulRRand) + 0.5);
    ulTimerVal = ulTimerVal / COMPENSATION;
    /*if(dDefTimerVal > ulTimerVal)
      {
          RTP_TRACE_MESSAGE("rtcp_interval dDefTimerVal > ulTimerVal [gen =%f], [def = %f]",
                          ulTimerVal, dDefTimerVal);
          ulTimerVal = dDefTimerVal;
      }*/
    if (ulTimerVal < 0)
    {
        ulTimerVal = RTP_INIT_TRUE_T_MIN;
        RTP_TRACE_MESSAGE("Generated a negative timer value. using Default", nullptr, nullptr);
    }
    return ulTimerVal;
}  // rtcp_interval

RtpDt_UInt32 RtpSession::getSenderCount()
{
    RtpDt_UInt32 uiSenderCnt = RTP_ZERO;
    for (auto& pobjRcvrElm : *m_pobjRtpRcvrInfoList)
    {
        // get key material element from list.
        if (pobjRcvrElm != nullptr && pobjRcvrElm->isSender() == eRTP_TRUE &&
                pobjRcvrElm->getTotalRcvdRtpPkts() != 0)
        {
            uiSenderCnt = uiSenderCnt + RTP_ONE;
        }
    }
    return uiSenderCnt;
}  // getSenderCount

RtpDt_Void RtpSession::calculateAndSetRTTD(
        RtpDt_UInt32 currentTime, RtpDt_UInt32 lsr, RtpDt_UInt32 dlsr)
{
    if (lsr == 0 || dlsr == 0)
    {
        m_lastRTTDelay = 0;
    }
    else
    {
        m_lastRTTDelay = (currentTime - lsr - dlsr);
        m_lastRTTDelay =
                (m_lastRTTDelay >> 16) * 1000 + ((m_lastRTTDelay & 0x0000ffff) * 1000 / 65536);
    }
    RTP_TRACE_MESSAGE("calculateAndSetRTTD = %d", m_lastRTTDelay, 0);
}
eRTP_STATUS_CODE RtpSession::populateRtcpXrPacket(IN_OUT RtcpPacket* pobjRtcpPkt)
{
    // create RtcpXrPacket
    RtcpXrPacket* pobjRtcpXrPacket = new RtcpXrPacket();
    if (pobjRtcpXrPacket == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_FAILURE;
    }
    // set extended report block data
    RtpBuffer* pobjPayload = new RtpBuffer(m_stRtcpXr.nlength, m_stRtcpXr.m_pBlockBuffer);
    if (pobjPayload == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        delete pobjRtcpXrPacket;
        return RTP_FAILURE;
    }
    pobjRtcpXrPacket->setReportBlk(pobjPayload);

    // set the RTCP packet
    pobjRtcpPkt->setXrPacket(pobjRtcpXrPacket);

    // get and populate the RTCP header
    RtcpHeader* pRtcpHdr = pobjRtcpXrPacket->getRtcpHdrInfo();

    RtpDt_UInt16 usSubType = RTP_ZERO;
    pRtcpHdr->populateRtcpHeader((RtpDt_UChar)usSubType, RTCP_XR, m_uiSsrc);

    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtpSession::sendRtcpXrPacket(
        IN RtpDt_UChar* m_pBlockBuffer, IN RtpDt_UInt16 nblockLength)
{
    // set timestamp
    m_stRtcpXr.m_pBlockBuffer = new RtpDt_UChar[nblockLength];
    if (m_stRtcpXr.m_pBlockBuffer == nullptr)
    {
        RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
        return RTP_FAILURE;
    }
    memcpy(m_stRtcpXr.m_pBlockBuffer, m_pBlockBuffer, nblockLength);

    m_stRtcpXr.nlength = nblockLength;
    m_bisXr = eRTP_TRUE;

    return RTP_SUCCESS;
}

eRtp_Bool RtpSession::checkRtpPayloadType(
        IN RtpHeader* pobjRtpHeader, IN RtpPayloadInfo* m_pobjPayloadInfo)
{
    RtpDt_Int32 i = 0;
    for (; i < RTP_MAX_PAYLOAD_TYPE; i++)
    {
        if (pobjRtpHeader->getPayloadType() == m_pobjPayloadInfo->getPayloadType(i))
            break;
        RTP_TRACE_MESSAGE("checkRtpPayloadType rcvd payload = %d--- set payload =%d",
                pobjRtpHeader->getPayloadType(), m_pobjPayloadInfo->getPayloadType(i));
    }

    if (i == RTP_MAX_PAYLOAD_TYPE)
        return eRTP_FALSE;
    else
        return eRTP_TRUE;
}

RtpDt_UInt32 RtpSession::getRTTD()
{
    return m_lastRTTDelay;
}

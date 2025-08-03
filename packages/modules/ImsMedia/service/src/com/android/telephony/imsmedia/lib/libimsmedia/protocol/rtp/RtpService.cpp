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

#include <RtpService.h>
#include <RtpGlobal.h>
#include <RtpImpl.h>
#include <RtpStack.h>
#include <RtpTrace.h>
#include <RtpError.h>
#include <RtpStackUtil.h>

RtpStack* g_pobjRtpStack = nullptr;

RtpDt_Void addSdesItem(
        OUT RtcpConfigInfo* pobjRtcpCfgInfo, IN RtpDt_UChar* sdesName, IN RtpDt_UInt32 uiLength)
{
    tRTCP_SDES_ITEM stSdesItem;
    memset(&stSdesItem, RTP_ZERO, sizeof(tRTCP_SDES_ITEM));
    RtpDt_UInt32 uiIndex = RTP_ZERO;
    stSdesItem.ucType = RTP_ONE;  // RTCP_SDES_CNAME
    stSdesItem.ucLength = uiLength;
    stSdesItem.pValue = sdesName;
    pobjRtcpCfgInfo->addRtcpSdesItem(&stSdesItem, uiIndex);
    return;
}  // addSdesItem

RtpDt_Void populateReceiveRtpIndInfo(
        OUT tRtpSvcIndSt_ReceiveRtpInd* pstRtpIndMsg, IN RtpPacket* pobjRtpPkt)
{
    RtpHeader* pobjRtpHeader = pobjRtpPkt->getRtpHeader();
    pstRtpIndMsg->bMbit = pobjRtpHeader->getMarker() > 0 ? eRTP_TRUE : eRTP_FALSE;
    pstRtpIndMsg->dwTimestamp = pobjRtpHeader->getRtpTimestamp();
    pstRtpIndMsg->dwPayloadType = pobjRtpHeader->getPayloadType();
    pstRtpIndMsg->dwSeqNum = pobjRtpHeader->getSequenceNumber();
    pstRtpIndMsg->dwSsrc = pobjRtpHeader->getRtpSsrc();

    // Header length
    pstRtpIndMsg->wMsgHdrLen = RTP_FIXED_HDR_LEN;
    pstRtpIndMsg->wMsgHdrLen += RTP_WORD_SIZE * pobjRtpHeader->getCsrcCount();

    if (pobjRtpPkt->getExtHeader())
    {
        pstRtpIndMsg->wMsgHdrLen += pobjRtpPkt->getExtHeader()->getLength();
        RtpDt_UChar* pExtHdrBuffer = pobjRtpPkt->getExtHeader()->getBuffer();
        RtpDt_Int32 uiByte4Data =
                RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pExtHdrBuffer)));
        pstRtpIndMsg->wDefinedByProfile = uiByte4Data >> 16;
        pstRtpIndMsg->wExtLen = uiByte4Data & 0x00FF;
        pstRtpIndMsg->pExtData = pExtHdrBuffer + 4;
        pstRtpIndMsg->wExtDataSize = pobjRtpPkt->getExtHeader()->getLength() - 4;
    }
    else
    {
        pstRtpIndMsg->wDefinedByProfile = 0;
        pstRtpIndMsg->wExtLen = 0;
        pstRtpIndMsg->pExtData = nullptr;
        pstRtpIndMsg->wExtDataSize = 0;
    }
    // End Header length
    // play the payload
    RtpBuffer* pobjRtpPayload = pobjRtpPkt->getRtpPayload();
    // Header

    // body
    if (!pobjRtpPayload)
    {
        pstRtpIndMsg->wMsgBodyLen = 0;
        pstRtpIndMsg->pMsgBody = nullptr;
        return;
    }

    pstRtpIndMsg->wMsgBodyLen = pobjRtpPayload->getLength();
    pstRtpIndMsg->pMsgBody = reinterpret_cast<RtpDt_UChar*>(pobjRtpPayload->getBuffer());
}

eRtp_Bool populateRcvdReportFromStk(
        IN std::list<RtcpReportBlock*>& pobjRepBlkList, OUT tRtpSvcRecvReport* pstRcvdReport)
{
    if (pobjRepBlkList.size() > RTP_ZERO)
    {
        // application supports one RR
        RtcpReportBlock* pobjRepBlkElm = pobjRepBlkList.front();
        if (pobjRepBlkElm == nullptr)
        {
            return eRTP_FALSE;
        }

        pstRcvdReport->ssrc = pobjRepBlkElm->getSsrc();
        pstRcvdReport->fractionLost = pobjRepBlkElm->getFracLost();
        pstRcvdReport->cumPktsLost = pobjRepBlkElm->getCumNumPktLost();
        pstRcvdReport->extHighSeqNum = pobjRepBlkElm->getExtHighSeqRcv();
        pstRcvdReport->jitter = pobjRepBlkElm->getJitter();
        pstRcvdReport->lsr = pobjRepBlkElm->getLastSR();
        pstRcvdReport->delayLsr = pobjRepBlkElm->getDelayLastSR();

        RTP_TRACE_MESSAGE("Received RR info :  [SSRC = %u] [FRAC LOST = %u]", pstRcvdReport->ssrc,
                pstRcvdReport->fractionLost);

        RTP_TRACE_MESSAGE("Received RR info :  [CUM PKTS LOST = %u] [EXT HIGE SEQ NUM = %u]",
                pstRcvdReport->cumPktsLost, pstRcvdReport->extHighSeqNum);

        RTP_TRACE_MESSAGE("Received RR info :  [JITTER = %u] [LSR = %u]", pstRcvdReport->jitter,
                pstRcvdReport->lsr);

        RTP_TRACE_MESSAGE(
                "Received RR info :  [DELAY SINCE LSR = %u] ", pstRcvdReport->delayLsr, nullptr);
    }
    else
    {
        pstRcvdReport->ssrc = RTP_ZERO;
        pstRcvdReport->fractionLost = RTP_ZERO;
        pstRcvdReport->cumPktsLost = RTP_ZERO;
        pstRcvdReport->extHighSeqNum = RTP_ZERO;
        pstRcvdReport->jitter = RTP_ZERO;
        pstRcvdReport->lsr = RTP_ZERO;
        pstRcvdReport->delayLsr = RTP_ZERO;
    }

    return eRTP_TRUE;
}  // populateRcvdReportFromStk

eRtp_Bool populateRcvdRrInfoFromStk(
        IN std::list<RtcpRrPacket*>& pobjRrList, OUT tNotifyReceiveRtcpRrInd* pstRrInfo)
{
    if (pobjRrList.empty())
    {
        return eRTP_FALSE;
    }
    // application supports one RR
    RtcpRrPacket* pobjRrPkt = pobjRrList.front();
    if (pobjRrPkt == nullptr)
    {
        return eRTP_FALSE;
    }

    tRtpSvcRecvReport* pstRcvdReport = &(pstRrInfo->stRecvRpt);
    std::list<RtcpReportBlock*>& pobjRepBlkList = pobjRrPkt->getReportBlockList();
    return populateRcvdReportFromStk(pobjRepBlkList, pstRcvdReport);
}  // populateRcvdRrInfoFromStk

eRtp_Bool populateRcvdSrInfoFromStk(
        IN std::list<RtcpSrPacket*>& pobjSrList, OUT tNotifyReceiveRtcpSrInd* pstSrInfo)
{
    if (pobjSrList.empty())
    {
        return eRTP_FALSE;
    }
    // get SR packet data
    RtcpSrPacket* pobjSrPkt = pobjSrList.front();
    if (pobjSrPkt == nullptr)
    {
        return eRTP_FALSE;
    }

    pstSrInfo->ntpTimestampMsw = pobjSrPkt->getNtpTime()->m_uiNtpHigh32Bits;
    pstSrInfo->ntpTimestampLsw = pobjSrPkt->getNtpTime()->m_uiNtpLow32Bits;
    pstSrInfo->rtpTimestamp = pobjSrPkt->getRtpTimestamp();
    pstSrInfo->sendPktCount = pobjSrPkt->getSendPktCount();
    pstSrInfo->sendOctCount = pobjSrPkt->getSendOctetCount();

    RTP_TRACE_MESSAGE("Received SR info :  [NTP High 32 = %u] [NTP LOW 32 = %u]",
            pstSrInfo->ntpTimestampMsw, pstSrInfo->ntpTimestampLsw);

    RTP_TRACE_MESSAGE(
            "Received SR info :  [RTP timestamp = %u] ", pstSrInfo->rtpTimestamp, nullptr);

    RTP_TRACE_MESSAGE("Received SR info :  [SEND PKT COUNT = %u] [SEND OCTET COUNT = %u]",
            pstSrInfo->sendPktCount, pstSrInfo->sendOctCount);

    // populate tRtpSvcRecvReport
    tRtpSvcRecvReport* pstRcvdReport = &(pstSrInfo->stRecvRpt);
    RtcpRrPacket* pobjRepBlk = pobjSrPkt->getRrPktInfo();
    std::list<RtcpReportBlock*>& pobjRepBlkList = pobjRepBlk->getReportBlockList();
    return populateRcvdReportFromStk(pobjRepBlkList, pstRcvdReport);
}

eRtp_Bool populateRcvdFbInfoFromStk(
        IN RtcpFbPacket* m_pobjRtcpFbPkt, OUT tRtpSvcIndSt_ReceiveRtcpFeedbackInd* stFbRtcpMsg)
{
    if (m_pobjRtcpFbPkt != nullptr)
    {
        stFbRtcpMsg->wPayloadType = m_pobjRtcpFbPkt->getRtcpHdrInfo()->getPacketType();
        stFbRtcpMsg->wFmt = m_pobjRtcpFbPkt->getRtcpHdrInfo()->getReceptionReportCount();
        stFbRtcpMsg->dwMediaSsrc = m_pobjRtcpFbPkt->getMediaSsrc();
        stFbRtcpMsg->wMsgLen = m_pobjRtcpFbPkt->getRtcpHdrInfo()->getLength();
        if (m_pobjRtcpFbPkt->getFCI() != nullptr)
        {
            stFbRtcpMsg->pMsg = m_pobjRtcpFbPkt->getFCI()->getBuffer();
        }
        return eRTP_TRUE;
    }

    return eRTP_FALSE;
}

RtpDt_Void populateRtpProfile(OUT RtpStackProfile* pobjStackProfile)
{
    pobjStackProfile->setRtcpBandwidth(RTP_DEF_RTCP_BW_SIZE);
    pobjStackProfile->setMtuSize(RTP_CONF_MTU_SIZE);
    pobjStackProfile->setTermNumber(RTP_CONF_SSRC_SEED);
}

RtpBuffer* SetRtpHeaderExtension(IN tRtpSvc_SendRtpPacketParam* pstRtpParam)
{
    RtpBuffer* pobjXHdr = new RtpBuffer();

    // HDR extension
    if (pstRtpParam->bXbit)
    {
        const RtpDt_Int32 headerSize = 4;
        RtpDt_Int32 nBufferSize = headerSize + pstRtpParam->wExtLen * sizeof(int32_t);
        RtpDt_UChar* pBuf = new RtpDt_UChar[nBufferSize];

        if (pstRtpParam->wExtLen * sizeof(int32_t) != pstRtpParam->nExtDataSize)
        {
            RTP_TRACE_WARNING("SetRtpHeaderExtension invalid data size len[%d], size[%d]",
                    pstRtpParam->wExtLen, pstRtpParam->nExtDataSize);
        }

        // define by profile
        pBuf[0] = (((unsigned)pstRtpParam->wDefinedByProfile) >> 8) & 0x00ff;
        pBuf[1] = pstRtpParam->wDefinedByProfile & 0x00ff;

        // number of the extension data set
        pBuf[2] = (((unsigned)pstRtpParam->wExtLen) >> 8) & 0x00ff;
        pBuf[3] = (pstRtpParam->wExtLen) & 0x00ff;

        memcpy(pBuf + 4, pstRtpParam->pExtData, pstRtpParam->nExtDataSize);
        pobjXHdr->setBufferInfo(nBufferSize, pBuf);
    }
    else
    {
        pobjXHdr->setBufferInfo(0, nullptr);
    }

    return pobjXHdr;
}

RtpDt_UInt16 GetRtpHeaderExtensionSize(eRtp_Bool bEnableCVO)
{
    if (bEnableCVO)
        return RTP_CVO_XHDR_LEN;

    return 0;
}

GLOBAL eRtp_Bool IMS_RtpSvc_Initialize()
{
    if (g_pobjRtpStack == nullptr)
    {
        g_pobjRtpStack = new RtpStack();
        if (g_pobjRtpStack == nullptr)
        {
            return eRTP_FALSE;
        }

        RtpStackProfile* pobjStackProfile = new RtpStackProfile();
        if (pobjStackProfile == nullptr)
        {
            return eRTP_FALSE;
        }

        populateRtpProfile(pobjStackProfile);
        g_pobjRtpStack->setStackProfile(pobjStackProfile);
    }

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_Deinitialize()
{
    if (g_pobjRtpStack)
    {
        delete g_pobjRtpStack;
        g_pobjRtpStack = nullptr;
    }

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_CreateSession(IN RtpDt_Char* szLocalIP, IN RtpDt_UInt32 port,
        IN RtpDt_Void* pAppData, OUT RtpDt_UInt32* puSsrc, OUT RTPSESSIONID* hRtpSession)
{
    if (g_pobjRtpStack == nullptr || szLocalIP == nullptr)
    {
        return eRTP_FALSE;
    }

    RtpSession* pobjRtpSession = g_pobjRtpStack->createRtpSession();
    if (pobjRtpSession == nullptr)
    {
        return eRTP_FALSE;
    }

    // set ip and port
    RtpBuffer* pobjTransAddr = new RtpBuffer();
    RtpDt_UInt32 uiIpLen = strlen(szLocalIP) + 1;
    RtpDt_UChar* pcIpAddr = new RtpDt_UChar[uiIpLen];
    memcpy(pcIpAddr, szLocalIP, uiIpLen);
    pobjTransAddr->setBufferInfo(uiIpLen, pcIpAddr);

    pobjRtpSession->setRtpTransAddr(pobjTransAddr);
    pobjRtpSession->setRtpPort((RtpDt_UInt16)port);

    *puSsrc = pobjRtpSession->getSsrc();
    *hRtpSession = reinterpret_cast<RtpDt_Void*>(pobjRtpSession);

    RtpImpl* pobjRtpImpl = new RtpImpl();
    if (pobjRtpImpl == nullptr)
    {
        return eRTP_FALSE;
    }
    pobjRtpImpl->setAppdata(pAppData);

    RtcpConfigInfo* pobjRtcpConfigInfo = new RtcpConfigInfo();
    addSdesItem(
            pobjRtcpConfigInfo, reinterpret_cast<RtpDt_UChar*>(szLocalIP), strlen(szLocalIP) + 1);

    eRTP_STATUS_CODE eInitSta = pobjRtpSession->initSession(pobjRtpImpl, pobjRtcpConfigInfo);
    if (eInitSta != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SetPayload(IN RTPSESSIONID hRtpSession,
        IN tRtpSvc_SetPayloadParam* pstPayloadInfo, IN eRtp_Bool bEnableXHdr,
        IN RtpDt_UInt32 nNumOfPayloadParam)
{
    RtpDt_UInt32 payloadType[RTP_MAX_PAYLOAD_TYPE] = {0};

    for (RtpDt_UInt32 i = 0; i < nNumOfPayloadParam; i++)
    {
        RTP_TRACE_MESSAGE(
                "IMS_RtpSvc_SetPayload   payloadtype = %d", pstPayloadInfo[i].payloadType, 0);
        payloadType[i] = pstPayloadInfo[i].payloadType;
    }
    RtpPayloadInfo* pobjlPayloadInfo =
            new RtpPayloadInfo(payloadType, pstPayloadInfo[0].samplingRate, nNumOfPayloadParam);
    if (pobjlPayloadInfo == nullptr)
    {
        return eRTP_FALSE;
    }

    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
    {
        delete pobjlPayloadInfo;
        return eRTP_FALSE;
    }

    eRTP_STATUS_CODE eInitSta =
            pobjRtpSession->setPayload(pobjlPayloadInfo, GetRtpHeaderExtensionSize(bEnableXHdr));
    delete pobjlPayloadInfo;
    if (eInitSta != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SetRTCPInterval(IN RTPSESSIONID hRtpSession, IN RtpDt_UInt32 nInterval)
{
    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(reinterpret_cast<RtpSession*>(hRtpSession)) ==
                    eRTP_FAILURE)
        return eRTP_FALSE;

    (reinterpret_cast<RtpSession*>(hRtpSession))->setRTCPTimerValue(nInterval);
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_DeleteSession(IN RTPSESSIONID hRtpSession)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    eRTP_STATUS_CODE eDelRtpStrm = g_pobjRtpStack->deleteRtpSession(pobjRtpSession);
    if (eDelRtpStrm != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }

    delete pobjRtpSession;
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SendRtpPacket(IN RtpServiceListener* pobjRtpServiceListener,
        IN RTPSESSIONID hRtpSession, IN RtpDt_Char* pBuffer, IN RtpDt_UInt16 wBufferLength,
        IN tRtpSvc_SendRtpPacketParam* pstRtpParam)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    if (pobjRtpSession->isRtpEnabled() == eRTP_FALSE)
    {
        return eRTP_FALSE;
    }

    RtpBuffer* pobjRtpPayload = new RtpBuffer();
    if (pobjRtpPayload == nullptr)
    {
        RTP_TRACE_WARNING(
                "IMS_RtpSvc_SendRtpPacket pobjRtpPayload - Malloc failed", RTP_ZERO, RTP_ZERO);
        return eRTP_FALSE;
    }

    RtpBuffer* pobjRtpBuf = new RtpBuffer();
    if (pobjRtpBuf == nullptr)
    {
        delete pobjRtpPayload;
        RTP_TRACE_MESSAGE(
                "IMS_RtpSvc_SendRtpPacket pobjRtpBuf - Malloc failed", RTP_ZERO, RTP_ZERO);
        return eRTP_FALSE;
    }

    // Create RTP packet
    //  1. Set Marker bit
    eRtp_Bool bMbit = eRTP_FALSE;

    if (pstRtpParam->bMbit == eRTP_TRUE)
    {
        bMbit = eRTP_TRUE;
    }

    // 2. Set Payload
    pobjRtpPayload->setBufferInfo(wBufferLength, reinterpret_cast<RtpDt_UChar*>(pBuffer));
    eRtp_Bool bUseLastTimestamp = pstRtpParam->bUseLastTimestamp ? eRTP_TRUE : eRTP_FALSE;
    eRTP_STATUS_CODE eRtpCreateStat = pobjRtpSession->createRtpPacket(pobjRtpPayload, bMbit,
            pstRtpParam->byPayLoadType, bUseLastTimestamp, pstRtpParam->diffFromLastRtpTimestamp,
            SetRtpHeaderExtension(pstRtpParam), pobjRtpBuf);

    // 3. de-init and free the temp variable both in success and failure case
    pobjRtpPayload->setBufferInfo(RTP_ZERO, nullptr);
    delete pobjRtpPayload;

    if (eRtpCreateStat != RTP_SUCCESS)
    {
        delete pobjRtpBuf;
        RTP_TRACE_WARNING(
                "IMS_RtpSvc_SendRtpPacket - eRtpCreateStat != RTP_SUCCESS ", RTP_ZERO, RTP_ZERO);

        return eRTP_FALSE;
    }
    // End Create RTP packet

    if (pobjRtpSession->isRtpEnabled() == eRTP_FALSE)
    {
        delete pobjRtpBuf;
        return eRTP_FALSE;
    }

    // dispatch to peer
    if (pobjRtpServiceListener->OnRtpPacket(pobjRtpBuf->getBuffer(), pobjRtpBuf->getLength()) == -1)
    {
        delete pobjRtpBuf;
        RTP_TRACE_WARNING("On Rtp packet failed ..! OnRtpPacket", RTP_ZERO, RTP_ZERO);
        return eRTP_FALSE;
    }

    delete pobjRtpBuf;

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_ProcRtpPacket(IN RtpServiceListener* pvIRtpSession,
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* pMsg, IN RtpDt_UInt16 uiMsgLength,
        IN RtpDt_Char* pPeerIp, IN RtpDt_UInt16 uiPeerPort, OUT RtpDt_UInt32& uiPeerSsrc)
{
    tRtpSvc_IndicationFromStack stackInd = RTPSVC_RECEIVE_RTP_IND;
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
    {
        return eRTP_FALSE;
    }

    if (pobjRtpSession->isRtpEnabled() == eRTP_FALSE)
    {
        return eRTP_FALSE;
    }

    RtpPacket* pobjRtpPkt = new RtpPacket();
    if (pobjRtpPkt == nullptr)
    {
        RTP_TRACE_WARNING(
                "IMS_RtpSvc_ProcRtpPacket pobjRtpPkt - Malloc failed", RTP_ZERO, RTP_ZERO);
        return eRTP_FALSE;
    }

    RtpBuffer objRtpBuf;
    objRtpBuf.setBufferInfo(uiMsgLength, pMsg);

    RtpDt_UInt32 uiTransLen = strlen(reinterpret_cast<const RtpDt_Char*>(pPeerIp));
    RtpBuffer objRmtAddr;
    objRmtAddr.setBufferInfo(uiTransLen + 1, reinterpret_cast<RtpDt_UChar*>(pPeerIp));

    eRTP_STATUS_CODE eStatus =
            pobjRtpSession->processRcvdRtpPkt(&objRmtAddr, uiPeerPort, &objRtpBuf, pobjRtpPkt);
    objRtpBuf.setBufferInfo(RTP_ZERO, nullptr);
    objRmtAddr.setBufferInfo(RTP_ZERO, nullptr);
    if (eStatus != RTP_SUCCESS)
    {
        if (eStatus == RTP_OWN_SSRC_COLLISION)
            pobjRtpSession->sendRtcpByePacket();

        RTP_TRACE_WARNING("process packet failed with reason [%d]", eStatus, RTP_ZERO);
        delete pobjRtpPkt;
        return eRTP_FALSE;
    }

    uiPeerSsrc = pobjRtpPkt->getRtpHeader()->getRtpSsrc();

    // populate stRtpIndMsg
    tRtpSvcIndSt_ReceiveRtpInd stRtpIndMsg;
    stRtpIndMsg.pMsgHdr = pMsg;
    populateReceiveRtpIndInfo(&stRtpIndMsg, pobjRtpPkt);

    if (pobjRtpSession->isRtpEnabled() == eRTP_FALSE)
    {
        delete pobjRtpPkt;
        return eRTP_FALSE;
    }

    pvIRtpSession->OnPeerInd(stackInd, (RtpDt_Void*)&stRtpIndMsg);

    delete pobjRtpPkt;
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SessionEnableRTP(IN RTPSESSIONID rtpSessionId, IN eRtp_Bool bResetSsrc)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(rtpSessionId);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    // generate SSRC
    RtpDt_UInt32 oldSsrc = pobjRtpSession->getSsrc();
    if (oldSsrc == 0 || bResetSsrc == eRTP_TRUE)
    {
        RtpDt_UInt32 uiSsrc = RtpStackUtil::generateNewSsrc(RTP_CONF_SSRC_SEED);
        RTP_TRACE_WARNING("[SessionEnableRTP] SSRC changed [%x] -> [%x]", oldSsrc, uiSsrc);
        pobjRtpSession->setSsrc(uiSsrc);
    }

    if (pobjRtpSession->enableRtp() == RTP_SUCCESS)
        return eRTP_TRUE;

    return eRTP_FALSE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SessionDisableRTP(IN RTPSESSIONID rtpSessionId)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(rtpSessionId);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    if (pobjRtpSession->disableRtp() == RTP_SUCCESS)
        return eRTP_TRUE;

    return eRTP_FALSE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SessionEnableRTCP(
        IN RTPSESSIONID hRtpSession, IN eRtp_Bool enableRTCPBye)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    eRTP_STATUS_CODE eRtcpStatus = pobjRtpSession->enableRtcp((eRtp_Bool)enableRTCPBye);
    if (eRtcpStatus != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SessionDisableRTCP(IN RTPSESSIONID hRtpSession)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    eRTP_STATUS_CODE eRtcpStatus = RTP_SUCCESS;

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    eRtcpStatus = pobjRtpSession->disableRtcp();
    if (eRtcpStatus != RTP_SUCCESS)
    {
        return eRTP_FALSE;
    }

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpByePacket(IN RTPSESSIONID hRtpSession)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    pobjRtpSession->sendRtcpByePacket();
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpRtpFbPacket(IN RTPSESSIONID hRtpSession,
        IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen,
        IN RtpDt_UInt32 uiMediaSsrc)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    pobjRtpSession->sendRtcpRtpFbPacket(uiFbType, pcBuff, uiLen, uiMediaSsrc);

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpPayloadFbPacket(IN RTPSESSIONID hRtpSession,
        IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen,
        IN RtpDt_UInt32 uiMediaSsrc)
{
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    pobjRtpSession->sendRtcpPayloadFbPacket(uiFbType, pcBuff, uiLen, uiMediaSsrc);

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_ProcRtcpPacket(IN RtpServiceListener* pobjRtpServiceListener,
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* pMsg, IN RtpDt_UInt16 uiMsgLength,
        IN RtpDt_Char* pcIpAddr, IN RtpDt_UInt32 uiRtcpPort, OUT RtpDt_UInt32* uiPeerSsrc)
{
    (RtpDt_Void) uiPeerSsrc;

    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);

    if (g_pobjRtpStack == nullptr ||
            g_pobjRtpStack->isValidRtpSession(pobjRtpSession) == eRTP_FAILURE)
        return eRTP_FALSE;

    if (pMsg == nullptr || uiMsgLength == RTP_ZERO || pcIpAddr == nullptr)
    {
        return eRTP_FALSE;
    }

    RtpBuffer objRmtAddr;
    objRmtAddr.setBuffer(reinterpret_cast<RtpDt_UChar*>(pcIpAddr));
    objRmtAddr.setLength(strlen(pcIpAddr) + 1);

    // decrypt RTCP message
    RtpBuffer objRtcpBuf;
    objRtcpBuf.setBufferInfo(uiMsgLength, pMsg);

    // process RTCP message
    RtcpPacket objRtcpPkt;
    eRTP_STATUS_CODE eProcRtcpSta =
            pobjRtpSession->processRcvdRtcpPkt(&objRmtAddr, uiRtcpPort, &objRtcpBuf, &objRtcpPkt);

    // clean the data
    objRtcpBuf.setBufferInfo(RTP_ZERO, nullptr);
    objRmtAddr.setBufferInfo(RTP_ZERO, nullptr);
    RtpBuffer objRtpPayload;
    objRtpPayload.setBufferInfo(RTP_ZERO, nullptr);

    if (eProcRtcpSta != RTP_SUCCESS)
    {
        RTP_TRACE_WARNING("Rtcp packet processing is  failed", RTP_ZERO, RTP_ZERO);
        return eRTP_FALSE;
    }

    // inform to application
    std::list<RtcpSrPacket*>& pobjSrList = objRtcpPkt.getSrPacketList();
    if (pobjSrList.size() > RTP_ZERO)
    {
        tRtpSvc_IndicationFromStack stackInd = RTPSVC_RECEIVE_RTCP_SR_IND;
        tNotifyReceiveRtcpSrInd stSrRtcpMsg;

        if (populateRcvdSrInfoFromStk(pobjSrList, &stSrRtcpMsg) == eRTP_TRUE)
        {
            pobjRtpServiceListener->OnPeerInd(stackInd, (RtpDt_Void*)&stSrRtcpMsg);
        }

        RtpDt_UInt32 rttd = pobjRtpSession->getRTTD();
        pobjRtpServiceListener->OnPeerRtcpComponents((RtpDt_Void*)&rttd);
    }
    else
    {
        std::list<RtcpRrPacket*>& pobjRrList = objRtcpPkt.getRrPacketList();
        if (pobjRrList.size() > RTP_ZERO)
        {
            tRtpSvc_IndicationFromStack stackInd = RTPSVC_RECEIVE_RTCP_RR_IND;
            tNotifyReceiveRtcpRrInd stRrRtcpMsg;

            if (populateRcvdRrInfoFromStk(pobjRrList, &stRrRtcpMsg) == eRTP_TRUE)
            {
                pobjRtpServiceListener->OnPeerInd(stackInd, (RtpDt_Void*)&stRrRtcpMsg);
            }

            RtpDt_UInt32 rttd = pobjRtpSession->getRTTD();
            pobjRtpServiceListener->OnPeerRtcpComponents((RtpDt_Void*)&rttd);
        }
    }  // end else

    // process rtcp fb packet and inform to application
    std::list<RtcpFbPacket*>& pobjFbList = objRtcpPkt.getFbPacketList();

    for (auto& pobjFbPkt : pobjFbList)
    {
        // get Fb packet data
        if (pobjFbPkt == nullptr)
        {
            return eRTP_FALSE;
        }
        tRtpSvc_IndicationFromStack stackInd = RTPSVC_RECEIVE_RTCP_FB_IND;
        if (pobjFbPkt->getRtcpHdrInfo()->getPacketType() == RTCP_PSFB)
        {
            stackInd = RTPSVC_RECEIVE_RTCP_PAYLOAD_FB_IND;
        }
        tRtpSvcIndSt_ReceiveRtcpFeedbackInd stFbRtcpMsg;
        if (populateRcvdFbInfoFromStk(pobjFbPkt, &stFbRtcpMsg) == eRTP_TRUE)
            pobjRtpServiceListener->OnPeerInd(stackInd, (RtpDt_Void*)&stFbRtcpMsg);
    }  // pobjFbList End

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpXrPacket(
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* m_pBlockBuffer, IN RtpDt_UInt16 nblockLength)
{
    RTP_TRACE_MESSAGE("IMS_RtpSvc_SendRtcpXrPacket", 0, 0);

    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    pobjRtpSession->sendRtcpXrPacket(m_pBlockBuffer, nblockLength);

    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_SetRtpContext(RTPSESSIONID hRtpSession, RtpDt_UInt32 ssrc,
        RtpDt_UInt32 /*timestamp*/, RtpDt_UInt16 seqNumber)
{
    RTP_TRACE_MESSAGE("IMS_RtpSvc_SetRtpContext. ssrc:%d, sequenceNumber:%d", ssrc, seqNumber);
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    pobjRtpSession->setSequenceNumber(seqNumber);
    return eRTP_TRUE;
}

GLOBAL eRtp_Bool IMS_RtpSvc_GetRtpContext(RTPSESSIONID hRtpSession, RtpDt_UInt32& ssrc,
        RtpDt_UInt32& /*timestamp*/, RtpDt_UInt16& seqNumber)
{
    RTP_TRACE_MESSAGE("IMS_RtpSvc_GetRtpContext. ssrc:%d, sequenceNumber:%d", ssrc, seqNumber);
    RtpSession* pobjRtpSession = reinterpret_cast<RtpSession*>(hRtpSession);
    ssrc = pobjRtpSession->getSsrc();
    seqNumber = pobjRtpSession->getSequenceNumber();
    return eRTP_TRUE;
}
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

#include <IRtpSession.h>
#include <RtpService.h>
#include <ImsMediaTrace.h>
#include <ImsMediaVideoUtil.h>

std::list<IRtpSession*> IRtpSession::mListRtpSession;

IRtpSession* IRtpSession::GetInstance(
        ImsMediaType type, const RtpAddress& localAddress, const RtpAddress& peerAddress)
{
    IMLOGD1("[GetInstance] media[%d]", type);

    for (auto& i : mListRtpSession)
    {
        if (i != nullptr && i->isSameInstance(type, localAddress, peerAddress))
        {
            i->increaseRefCounter();
            return i;
        }
    }

    if (mListRtpSession.size() == 0)
    {
        IMLOGI0("[GetInstance] Initialize Rtp Stack");
        IMS_RtpSvc_Initialize();
    }

    IRtpSession* pSession = new IRtpSession(type, localAddress, peerAddress);
    mListRtpSession.push_back(pSession);
    pSession->increaseRefCounter();
    return pSession;
}

void IRtpSession::ReleaseInstance(IRtpSession* session)
{
    if (session == nullptr)
    {
        return;
    }

    IMLOGD2("[ReleaseInstance] media[%d], RefCount[%d]", session->getMediaType(),
            session->getRefCounter());
    session->decreaseRefCounter();

    if (session->getRefCounter() == 0)
    {
        mListRtpSession.remove(session);
        delete session;
    }

    if (mListRtpSession.size() == 0)
    {
        IMLOGI0("[ReleaseInstance] Deinitialize Rtp Stack");
        IMS_RtpSvc_Deinitialize();
    }
}

IRtpSession::IRtpSession(
        ImsMediaType mediatype, const RtpAddress& localAddress, const RtpAddress& peerAddress)
{
    mMediaType = mediatype;
    mRtpSessionId = 0;
    mRefCount = 0;
    mLocalAddress = localAddress;
    mPeerAddress = peerAddress;
    mRtpEncoderListener = nullptr;
    mRtpDecoderListener = nullptr;
    mRtcpEncoderListener = nullptr;
    mRtcpDecoderListener = nullptr;
    std::memset(mPayloadParam, 0, sizeof(tRtpSvc_SetPayloadParam) * RTP_MAX_PAYLOAD_TYPE);
    mNumPayloadParam = 0;
    mLocalRtpSsrc = 0;
    mPeerRtpSsrc = 0;
    mEnableRtcpTx = false;
    mEnableDTMF = false;
    mRtpDtmfPayloadType = 0;
    mPrevTimestamp = -1;
    mRtpStarted = 0;
    mRtcpStarted = 0;
    mNumRtpProcPacket = 0;
    mNumRtcpProcPacket = 0;
    mNumRtpPacket = 0;
    mNumSRPacket = 0;
    mNumRRPacket = 0;
    mNumRtpDataToSend = 0;
    mNumRtpPacketSent = 0;
    mNumRtcpPacketSent = 0;
    mRttd = -1;

    // create rtp stack session
    IMS_RtpSvc_CreateSession(
            mLocalAddress.ipAddress, mLocalAddress.port, this, &mLocalRtpSsrc, &mRtpSessionId);
    IMLOGD6("[IRtpSession] media[%d], localIp[%s], localPort[%d], peerIp[%s], peerPort[%d], "
            "sessionId[%d]",
            mMediaType, mLocalAddress.ipAddress, mLocalAddress.port, mPeerAddress.ipAddress,
            mPeerAddress.port, mRtpSessionId);
}

IRtpSession::~IRtpSession()
{
    IMS_RtpSvc_DeleteSession(mRtpSessionId);
    mRtpEncoderListener = nullptr;
    mRtpDecoderListener = nullptr;
    mRtcpEncoderListener = nullptr;
    mRtcpDecoderListener = nullptr;
}

bool IRtpSession::operator==(const IRtpSession& obj2)
{
    if (mMediaType == obj2.mMediaType && mLocalAddress == obj2.mLocalAddress &&
            mPeerAddress == obj2.mPeerAddress)
    {
        return true;
    }

    return false;
}

bool IRtpSession::isSameInstance(
        ImsMediaType mediatype, const RtpAddress& localAddress, const RtpAddress& peerAddress)
{
    if (mMediaType == mediatype && mLocalAddress == localAddress && mPeerAddress == peerAddress)
    {
        return true;
    }

    return false;
}

void IRtpSession::SetRtpEncoderListener(IRtpEncoderListener* pRtpEncoderListener)
{
    std::lock_guard<std::mutex> guard(mutexEncoder);
    mRtpEncoderListener = pRtpEncoderListener;
}

void IRtpSession::SetRtpDecoderListener(IRtpDecoderListener* pRtpDecoderListener)
{
    std::lock_guard<std::mutex> guard(mutexDecoder);
    mRtpDecoderListener = pRtpDecoderListener;
}

void IRtpSession::SetRtcpEncoderListener(IRtcpEncoderListener* pRtcpEncoderListener)
{
    std::lock_guard<std::mutex> guard(mutexEncoder);
    mRtcpEncoderListener = pRtcpEncoderListener;
}

void IRtpSession::SetRtcpDecoderListener(IRtcpDecoderListener* pRtcpDecoderListener)
{
    std::lock_guard<std::mutex> guard(mutexDecoder);
    mRtcpDecoderListener = pRtcpDecoderListener;
}

void IRtpSession::addPayloadType(RtpDt_UInt32 payloadType, RtpDt_UInt32 samplingRate)
{
    if (mNumPayloadParam >= RTP_MAX_PAYLOAD_TYPE)
    {
        IMLOGE1("[SetRtpPayloadParam] overflow[%d]", mNumPayloadParam);
        return;
    }

    mPayloadParam[mNumPayloadParam].payloadType = payloadType;
    mPayloadParam[mNumPayloadParam].samplingRate = samplingRate;
    mNumPayloadParam++;
}

void IRtpSession::SetRtpPayloadParam(int32_t payloadNumTx, int32_t payloadNumRx,
        int32_t samplingRate, int32_t subTxPayloadTypeNum, int32_t subRxPayloadTypeNum,
        int32_t subSamplingRate)
{
    mNumPayloadParam = 0;
    std::memset(mPayloadParam, 0, sizeof(tRtpSvc_SetPayloadParam) * RTP_MAX_PAYLOAD_TYPE);
    IMLOGD3("[SetRtpPayloadParam] localPayload[%d], peerPayload[%d], sampling[%d]", payloadNumTx,
            payloadNumRx, samplingRate);

    addPayloadType(payloadNumTx, samplingRate);

    if (payloadNumTx != payloadNumRx)
    {
        // rx parameter
        addPayloadType(payloadNumRx, samplingRate);
    }

    if (mMediaType == IMS_MEDIA_AUDIO || mMediaType == IMS_MEDIA_TEXT)
    {
        mEnableDTMF = false;

        if (subTxPayloadTypeNum != 0 && subRxPayloadTypeNum != 0)
        {
            IMLOGD3("[SetRtpPayloadParam] sub Txpayload[%d],sub Rxpayload[%d],sub samplingRate[%d]",
                    subTxPayloadTypeNum, subRxPayloadTypeNum, subSamplingRate);

            if (mMediaType == IMS_MEDIA_AUDIO)
            {
                mEnableDTMF = true;
            }

            addPayloadType(subTxPayloadTypeNum, subSamplingRate);

            if (subTxPayloadTypeNum != subRxPayloadTypeNum)
            {
                addPayloadType(subRxPayloadTypeNum, subSamplingRate);
            }
        }
    }

    IMS_RtpSvc_SetPayload(mRtpSessionId, mPayloadParam,
            mMediaType == IMS_MEDIA_VIDEO ? eRTP_TRUE : eRTP_FALSE, mNumPayloadParam);
}

void IRtpSession::SetRtcpInterval(int32_t nInterval)
{
    IMLOGD1("[SetRtcpInterval] nInterval[%d]", nInterval);
    IMS_RtpSvc_SetRTCPInterval(mRtpSessionId, nInterval);
}

void IRtpSession::StartRtp(bool bResetSsrc)
{
    IMLOGD1("[StartRtp] RtpStarted[%d]", mRtpStarted);

    if (mRtpStarted == 0)
    {
        IMLOGD0("[StartRtp] IMS_RtpSvc_SessionEnableRTP");
        IMS_RtpSvc_SessionEnableRTP(mRtpSessionId, bResetSsrc ? eRTP_TRUE : eRTP_FALSE);
    }

    mRtpStarted++;
}

void IRtpSession::StopRtp()
{
    IMLOGD1("[StopRtp] RtpStarted[%d]", mRtpStarted);

    if (mRtpStarted == 0)
    {
        return;
    }

    mRtpStarted--;

    if (mRtpStarted == 0)
    {
        IMS_RtpSvc_SessionDisableRTP(mRtpSessionId);
        IMLOGI0("[StopRtp] IMS_RtpSvc_SessionDisableRTP");
    }
}

void IRtpSession::StartRtcp(bool bSendRtcpBye)
{
    IMLOGD1("[StartRtcp] RtcpStarted[%d]", mRtcpStarted);

    if (mRtcpStarted == 0)
    {
        IMS_RtpSvc_SessionEnableRTCP(mRtpSessionId, static_cast<eRtp_Bool>(bSendRtcpBye));
    }

    mEnableRtcpTx = true;
    mRtcpStarted++;
}

void IRtpSession::StopRtcp()
{
    IMLOGD1("[StopRtcp] RtcpStarted[%d]", mRtcpStarted);

    if (mRtcpStarted == 0)
    {
        return;
    }

    mRtcpStarted--;

    if (mRtcpStarted == 0)
    {
        IMLOGI0("[StopRtcp] IMS_RtpSvc_SessionDisableRtcp");
        IMS_RtpSvc_SessionDisableRTCP(mRtpSessionId);
        mEnableRtcpTx = false;
    }
}

bool IRtpSession::SendRtpPacket(uint32_t payloadType, uint8_t* data, uint32_t dataSize,
        uint32_t timestamp, bool mark, uint32_t timeDiff, RtpHeaderExtensionInfo* extensionInfo)
{
    tRtpSvc_SendRtpPacketParam stRtpPacketParam;
    memset(&stRtpPacketParam, 0, sizeof(tRtpSvc_SendRtpPacketParam));
    IMLOGD_PACKET5(IM_PACKET_LOG_RTP,
            "SendRtpPacket, payloadType[%u], size[%u], TS[%u], mark[%d], extension[%d]",
            payloadType, dataSize, timestamp, mark, extensionInfo != nullptr);
    stRtpPacketParam.bMbit = mark ? eRTP_TRUE : eRTP_FALSE;
    stRtpPacketParam.byPayLoadType = payloadType;
    stRtpPacketParam.diffFromLastRtpTimestamp = timeDiff;
    stRtpPacketParam.bXbit = extensionInfo != nullptr ? eRTP_TRUE : eRTP_FALSE;

    if (extensionInfo != nullptr)
    {
        stRtpPacketParam.wDefinedByProfile = extensionInfo->definedByProfile;
        stRtpPacketParam.wExtLen = extensionInfo->length;
        stRtpPacketParam.pExtData = extensionInfo->extensionData;
        stRtpPacketParam.nExtDataSize = extensionInfo->extensionDataSize;
    }

    if (mPrevTimestamp == timestamp)
    {
        stRtpPacketParam.bUseLastTimestamp = eRTP_TRUE;
    }
    else
    {
        stRtpPacketParam.bUseLastTimestamp = eRTP_FALSE;
        mPrevTimestamp = timestamp;
    }

    mNumRtpDataToSend++;
    IMS_RtpSvc_SendRtpPacket(
            this, mRtpSessionId, reinterpret_cast<char*>(data), dataSize, &stRtpPacketParam);
    return true;
}

bool IRtpSession::ProcRtpPacket(uint8_t* pData, uint32_t nDataSize)
{
    IMLOGD_PACKET1(IM_PACKET_LOG_RTP, "[ProcRtpPacket] size[%d]", nDataSize);
    mNumRtpProcPacket++;

    /** if it is loopback, change the ssrc */
    if (mLocalAddress == mPeerAddress)
    {
        unsigned int ssrc;
        ssrc = *reinterpret_cast<uint32_t*>(pData + 8);
        ssrc++;
        *reinterpret_cast<uint32_t*>(pData + 8) = ssrc;

        IMLOGD1("[ProcRtcpPacket] loopback mode, ssrc changed[%d]", ssrc);
    }

    IMS_RtpSvc_ProcRtpPacket(this, mRtpSessionId, pData, nDataSize, mPeerAddress.ipAddress,
            mPeerAddress.port, mPeerRtpSsrc);
    return true;
}

bool IRtpSession::ProcRtcpPacket(uint8_t* pData, uint32_t nDataSize)
{
    IMLOGD_PACKET1(IM_PACKET_LOG_RTCP, "[ProcRtcpPacket] size[%d]", nDataSize);
    mNumRtcpProcPacket++;
    IMS_RtpSvc_ProcRtcpPacket(this, mRtpSessionId, pData, nDataSize, mPeerAddress.ipAddress,
            (mPeerAddress.port + 1), &mLocalRtpSsrc);
    return true;
}

int IRtpSession::OnRtpPacket(unsigned char* pData, RtpSvc_Length wLen)
{
    IMLOGD_PACKET1(IM_PACKET_LOG_RTP, "[OnRtpPacket] size[%d]", wLen);
    std::lock_guard<std::mutex> guard(mutexEncoder);

    if (mRtpEncoderListener)
    {
        mNumRtpPacketSent++;
        mRtpEncoderListener->OnRtpPacket(pData, wLen);
        return wLen;
    }
    return 0;
}

int IRtpSession::OnRtcpPacket(unsigned char* pData, RtpSvc_Length wLen)
{
    IMLOGD_PACKET0(IM_PACKET_LOG_RTCP, "[OnRtcpPacket] Enter");
    if (mEnableRtcpTx == false)
    {
        IMLOGD_PACKET0(IM_PACKET_LOG_RTCP, "[OnRtcpPacket] disabled");
        return wLen;
    }

    std::lock_guard<std::mutex> guard(mutexEncoder);
    if (mRtcpEncoderListener)
    {
        if (pData != nullptr)
        {
            mNumRtcpPacketSent++;
            mRtcpEncoderListener->OnRtcpPacket(pData, wLen);
            IMLOGD_PACKET0(IM_PACKET_LOG_RTCP, "[OnRtcpPacket] Send, Exit");
            return wLen;
        }
        else
        {
            IMLOGD_PACKET1(IM_PACKET_LOG_RTCP, "[OnRtcpPacket] pData[%x]", pData);
            return 0;
        }
    }
    return 0;
}

void IRtpSession::OnPeerInd(tRtpSvc_IndicationFromStack type, void* pMsg)
{
    IMLOGD_PACKET2(IM_PACKET_LOG_RTP, "[OnPeerInd] media[%d], type[%d]", mMediaType, type);
    std::lock_guard<std::mutex> guard(mutexDecoder);

    switch (type)
    {
        case RTPSVC_RECEIVE_RTP_IND:
            mNumRtpPacket++;

            if (mRtpDecoderListener)
            {
                tRtpSvcIndSt_ReceiveRtpInd* pstRtp =
                        reinterpret_cast<tRtpSvcIndSt_ReceiveRtpInd*>(pMsg);

                if ((mEnableDTMF == false || mRtpDtmfPayloadType != pstRtp->dwPayloadType) &&
                        pstRtp->dwPayloadType != 20)
                {
                    RtpHeaderExtensionInfo extensionInfo(pstRtp->wDefinedByProfile, pstRtp->wExtLen,
                            reinterpret_cast<int8_t*>(pstRtp->pExtData), pstRtp->wExtDataSize);

                    mRtpDecoderListener->OnMediaDataInd(pstRtp->pMsgBody, pstRtp->wMsgBodyLen,
                            pstRtp->dwTimestamp, pstRtp->bMbit, pstRtp->dwSeqNum,
                            pstRtp->dwPayloadType, pstRtp->dwSsrc, extensionInfo);
                }
            }
            break;

        case RTPSVC_RECEIVE_RTCP_SR_IND:
            mNumSRPacket++;

            if (mRtcpDecoderListener)
            {
                mRtcpDecoderListener->OnRtcpInd(type, pMsg);
            }
            break;
        case RTPSVC_RECEIVE_RTCP_RR_IND:
            mNumRRPacket++;

            if (mRtcpDecoderListener)
            {
                mRtcpDecoderListener->OnRtcpInd(type, pMsg);
            }
            break;
        case RTPSVC_RECEIVE_RTCP_FB_IND:
        case RTPSVC_RECEIVE_RTCP_PAYLOAD_FB_IND:
            if (mRtcpDecoderListener)
            {
                mRtcpDecoderListener->OnRtcpInd(type, pMsg);
            }
            break;
        default:
            IMLOGD1("[OnPeerInd] unhandled[%d]", type);
            break;
    }
}

void IRtpSession::OnPeerRtcpComponents(void* nMsg)
{
    IMLOGD0("[OnPeerRtcpComponents]");

    if (nMsg != nullptr && mRtcpDecoderListener != nullptr)
    {
        int32_t roundTripTimeDelay = *reinterpret_cast<int32_t*>(nMsg);
        mRtcpDecoderListener->OnEvent(kRequestRoundTripTimeDelayUpdate, roundTripTimeDelay);
    }
}

void IRtpSession::OnRtpStatsTimerExpired()
{
    IMLOGI8("[OnRtpStatsTimerExpired] media[%d], RXRtp[%03d/%03d], RXRtcp[%02d/%02d], "
            "TXRtp[%03d/%03d], TXRtcp[%02d]",
            mMediaType, mNumRtpProcPacket, mNumRtpPacket, mNumRtcpProcPacket,
            mNumSRPacket + mNumRRPacket, mNumRtpDataToSend, mNumRtpPacketSent, mNumRtcpPacketSent);

    std::lock_guard<std::mutex> guard(mutexDecoder);

    if (mRtpDecoderListener)
    {
        mRtpDecoderListener->OnNumReceivedPacket(mNumRtpProcPacket);
    }

    if (mRtcpDecoderListener)
    {
        mRtcpDecoderListener->OnNumReceivedPacket(mNumRtcpProcPacket, mNumRRPacket);
    }

    mNumRtpProcPacket = 0;
    mNumRtcpProcPacket = 0;
    mNumRtpPacket = 0;
    mNumSRPacket = 0;
    mNumRRPacket = 0;
    mNumRtpDataToSend = 0;
    mNumRtpPacketSent = 0;
    mNumRtcpPacketSent = 0;
}

void IRtpSession::SendRtcpXr(uint8_t* pPayload, uint32_t nSize)
{
    IMLOGD1("[SendRtcpXr] nSize[%d]", nSize);

    if (mRtpSessionId)
    {
        IMS_RtpSvc_SendRtcpXrPacket(mRtpSessionId, pPayload, nSize);
    }
}

bool IRtpSession::SendRtcpFeedback(int32_t type, uint8_t* pFic, uint32_t nFicSize)
{
    IMLOGD1("[SendRtcpFeedback] type[%d]", type);

    if (!mRtcpStarted)
    {
        return false;
    }

    eRtp_Bool bRet = eRTP_FALSE;

    if (kRtpFbNack <= type && type <= kRtpFbTmmbn)
    {
        // RTP-FB
        IMLOGD1("[SendRtcpFeedback] Send rtp feedback, type[%d]", type);
        bRet = IMS_RtpSvc_SendRtcpRtpFbPacket(
                mRtpSessionId, type, reinterpret_cast<char*>(pFic), nFicSize, mPeerRtpSsrc);
    }
    else if (kPsfbPli <= type && type <= kPsfbFir)
    {
        type -= kPsfbBoundary;
        // PSFB
        IMLOGD1("[SendRtcpFeedback] Send payload specific feedback, type[%d]", type);
        bRet = IMS_RtpSvc_SendRtcpPayloadFbPacket(
                mRtpSessionId, type, reinterpret_cast<char*>(pFic), nFicSize, mPeerRtpSsrc);
    }

    if (bRet != eRTP_TRUE)
    {
        IMLOGE0("[SendRtcpFeedback] error");
        return false;
    }

    return true;
}

ImsMediaType IRtpSession::getMediaType()
{
    return mMediaType;
}

void IRtpSession::increaseRefCounter()
{
    ++mRefCount;
    IMLOGD1("[increaseRefCounter] count[%d]", mRefCount.load());
}

void IRtpSession::decreaseRefCounter()
{
    --mRefCount;
    IMLOGD1("[decreaseRefCounter] count[%d]", mRefCount.load());
}

uint32_t IRtpSession::getRefCounter()
{
    return mRefCount.load();
}

void IRtpSession::SetRtpContext(uint32_t ssrc, uint32_t timestamp, uint16_t sequenceNumber)
{
    IMS_RtpSvc_SetRtpContext(mRtpSessionId, ssrc, timestamp, sequenceNumber);
}

void IRtpSession::GetRtpContext(uint32_t& ssrc, uint32_t& timestamp, uint16_t& sequenceNumber)
{
    IMS_RtpSvc_GetRtpContext(mRtpSessionId, ssrc, timestamp, sequenceNumber);
}

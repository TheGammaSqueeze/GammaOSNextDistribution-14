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

#include <RtcpDecoderNode.h>
#include <ImsMediaTrace.h>
#include <ImsMediaVideoUtil.h>

#ifdef DEBUG_BITRATE_CHANGE_SIMULATION
static int32_t gTestBitrate = 384000;
#endif

RtcpDecoderNode::RtcpDecoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mRtpSession = nullptr;
    mInactivityTime = 0;
    mNoRtcpTime = 0;
}

RtcpDecoderNode::~RtcpDecoderNode()
{
    if (mRtpSession != nullptr)
    {
        mRtpSession->SetRtcpEncoderListener(nullptr);
        IRtpSession::ReleaseInstance(mRtpSession);
        mRtpSession = nullptr;
    }
}

kBaseNodeId RtcpDecoderNode::GetNodeId()
{
    return kNodeIdRtcpDecoder;
}

ImsMediaResult RtcpDecoderNode::Start()
{
    IMLOGD0("[Start]");

    if (mRtpSession == nullptr)
    {
        mRtpSession = IRtpSession::GetInstance(mMediaType, mLocalAddress, mPeerAddress);

        if (mRtpSession == nullptr)
        {
            IMLOGE0("[Start] Can't create rtp session");
            return RESULT_NOT_READY;
        }
    }

    mRtpSession->SetRtcpDecoderListener(this);
    mNoRtcpTime = 0;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void RtcpDecoderNode::Stop()
{
    IMLOGD0("[Stop]");

    if (mRtpSession)
    {
        mRtpSession->StopRtcp();
    }

    mNodeState = kNodeStateStopped;
}

void RtcpDecoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum,
        ImsMediaSubType nDataType, uint32_t arrivalTime)
{
    (void)nDataType;
    (void)arrivalTime;

    IMLOGD_PACKET6(IM_PACKET_LOG_RTCP,
            "[OnMediaDataInd] media[%d] subtype[%d], Size[%d], TS[%u], Mark[%d], Seq[%d]",
            mMediaType, subtype, nDataSize, nTimeStamp, bMark, nSeqNum);
    if (mRtpSession != nullptr)
    {
        mRtpSession->ProcRtcpPacket(pData, nDataSize);
    }
}

bool RtcpDecoderNode::IsRunTime()
{
    return true;
}

bool RtcpDecoderNode::IsSourceNode()
{
    return false;
}

void RtcpDecoderNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    RtpConfig* pConfig = reinterpret_cast<RtpConfig*>(config);
    mPeerAddress = RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());
    IMLOGD2("[SetConfig] peer Ip[%s], port[%d]", mPeerAddress.ipAddress, mPeerAddress.port);
}

bool RtcpDecoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    RtpConfig* pConfig = reinterpret_cast<RtpConfig*>(config);
    RtpAddress peerAddress =
            RtpAddress(pConfig->getRemoteAddress().c_str(), pConfig->getRemotePort());

    return (mPeerAddress == peerAddress);
}

void RtcpDecoderNode::OnRtcpInd(tRtpSvc_IndicationFromStack type, void* data)
{
    if (data == nullptr)
    {
        return;
    }

    switch (type)
    {
        case RTPSVC_RECEIVE_RTCP_SR_IND:
        {
            tNotifyReceiveRtcpSrInd* payload = reinterpret_cast<tNotifyReceiveRtcpSrInd*>(data);

            if (mMediaType == IMS_MEDIA_AUDIO)
            {
                RtcpSr* rtcpSr = new RtcpSr(payload->ntpTimestampMsw, payload->ntpTimestampLsw,
                        payload->rtpTimestamp, payload->sendPktCount, payload->sendOctCount,
                        RtcpRecvReport(payload->stRecvRpt.ssrc, payload->stRecvRpt.fractionLost,
                                payload->stRecvRpt.cumPktsLost, payload->stRecvRpt.extHighSeqNum,
                                payload->stRecvRpt.jitter, payload->stRecvRpt.lsr,
                                payload->stRecvRpt.delayLsr));
                mCallback->SendEvent(
                        kCollectPacketInfo, kStreamRtcp, reinterpret_cast<uint64_t>(rtcpSr));
            }
#ifdef DEBUG_BITRATE_CHANGE_SIMULATION
            else if (mMediaType == IMS_MEDIA_VIDEO)
            {
                gTestBitrate *= 0.8;
                mCallback->SendEvent(kRequestVideoBitrateChange, gTestBitrate);
            }
#endif
        }
        break;
        case RTPSVC_RECEIVE_RTCP_RR_IND:
        {
            tNotifyReceiveRtcpRrInd* payload = reinterpret_cast<tNotifyReceiveRtcpRrInd*>(data);
            IMLOGD_PACKET2(IM_PACKET_LOG_RTCP, "[OnRtcpInd] RtcpRr - fractionLost[%d], jitter[%d]",
                    payload->stRecvRpt.fractionLost, payload->stRecvRpt.jitter);

            if (mMediaType == IMS_MEDIA_AUDIO)
            {
                mCallback->SendEvent(kCollectPacketInfo, kStreamRtcp);
            }
#ifdef DEBUG_BITRATE_CHANGE_SIMULATION
            else if (mMediaType == IMS_MEDIA_VIDEO)
            {
                gTestBitrate *= 0.8;
                mCallback->SendEvent(kRequestVideoBitrateChange, gTestBitrate);
            }
#endif
        }
        break;
        case RTPSVC_RECEIVE_RTCP_FB_IND:
        case RTPSVC_RECEIVE_RTCP_PAYLOAD_FB_IND:
        {
            tRtpSvcIndSt_ReceiveRtcpFeedbackInd* payload =
                    reinterpret_cast<tRtpSvcIndSt_ReceiveRtcpFeedbackInd*>(data);
            uint32_t feedbackType = 0;

            if (type == RTPSVC_RECEIVE_RTCP_FB_IND)
            {
                feedbackType = payload->wFmt;
            }
            else if (type == RTPSVC_RECEIVE_RTCP_PAYLOAD_FB_IND)
            {
                feedbackType = payload->wFmt + kPsfbBoundary;
            }

            switch (feedbackType)
            {
                case kRtpFbNack:
                    /** do nothing */
                    break;
                case kRtpFbTmmbr:
                    ReceiveTmmbr(payload);
                    break;
                case kRtpFbTmmbn:
                    break;
                case kPsfbPli:  // FALL_THROUGH
                case kPsfbFir:
                    RequestIdrFrame();
                    break;
                default:
                    IMLOGI2("[OnRtcpInd] unhandled payload[%d], fmt[%d]", payload->wPayloadType,
                            payload->wFmt);
                    break;
            }
        }
        break;
        default:
            IMLOGI1("[OnRtcpInd] unhandled type[%d]", type);
            break;
    }
}

void RtcpDecoderNode::OnNumReceivedPacket(uint32_t nNumRtcpSRPacket, uint32_t nNumRtcpRRPacket)
{
    IMLOGD_PACKET3(IM_PACKET_LOG_RTCP,
            "[OnNumReceivedPacket] InactivityTime[%d], numRtcpSR[%d], numRtcpRR[%d]",
            mInactivityTime, nNumRtcpSRPacket, nNumRtcpRRPacket);

    if (nNumRtcpSRPacket == 0 && nNumRtcpRRPacket == 0)
    {
        mNoRtcpTime++;
    }
    else
    {
        mNoRtcpTime = 0;
    }

    if (mInactivityTime != 0 && mNoRtcpTime == mInactivityTime)
    {
        if (mCallback != nullptr)
        {
            mCallback->SendEvent(kImsMediaEventMediaInactivity, kProtocolRtcp, mInactivityTime);
        }
    }
}

void RtcpDecoderNode::OnEvent(uint32_t event, uint32_t param)
{
    mCallback->SendEvent(event, param);
}

void RtcpDecoderNode::SetLocalAddress(const RtpAddress& address)
{
    mLocalAddress = address;
}

void RtcpDecoderNode::SetPeerAddress(const RtpAddress& address)
{
    mPeerAddress = address;
}

void RtcpDecoderNode::SetInactivityTimerSec(const uint32_t time)
{
    IMLOGD2("[SetInactivityTimerSec] media[%d], time[%d] reset", mMediaType, time);
    mInactivityTime = time;
    mNoRtcpTime = 0;
}

void RtcpDecoderNode::ReceiveTmmbr(const tRtpSvcIndSt_ReceiveRtcpFeedbackInd* payload)
{
    if (payload == nullptr || payload->pMsg == nullptr || mCallback == nullptr)
    {
        return;
    }

    // Read bitrate from TMMBR
    mBitReader.SetBuffer(payload->pMsg, 64);
    /** read 16 bit and combine it */
    uint32_t receivedSsrc = mBitReader.Read(16);
    receivedSsrc = (receivedSsrc << 16) | mBitReader.Read(16);
    uint32_t receivedExp = mBitReader.Read(6);
    uint32_t receivedMantissa = mBitReader.Read(17);
    uint32_t receivedOverhead = mBitReader.Read(9);
    uint32_t bitrate = receivedMantissa << receivedExp;

    IMLOGD3("[ReceiveTmmbr] received TMMBR, exp[%d], mantissa[%d], bitrate[%d]", receivedExp,
            receivedMantissa, bitrate);

    // Set the bitrate to encoder
    mCallback->SendEvent(kRequestVideoBitrateChange, bitrate);

    // Send TMMBN to peer
    uint32_t exp = 0;
    uint32_t mantissa = 0;
    ImsMediaVideoUtil::ConvertBitrateToPower(bitrate, exp, mantissa);

    InternalRequestEventParam* pParam = new InternalRequestEventParam(
            kRtpFbTmmbn, TmmbrParams(receivedSsrc, exp, mantissa, receivedOverhead));
    mCallback->SendEvent(kRequestVideoSendTmmbn, reinterpret_cast<uint64_t>(pParam));
}

void RtcpDecoderNode::RequestIdrFrame()
{
    IMLOGD0("[RequestIdrFrame]");

    if (mCallback != nullptr)
    {
        mCallback->SendEvent(kRequestVideoIdrFrame, 0);
    }
}
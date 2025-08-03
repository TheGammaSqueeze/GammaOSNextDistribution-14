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

#include <ImsMediaTimer.h>
#include <RtpImpl.h>
#include <RtpService.h>
#include <RtpTrace.h>
#include <string>

RtpImpl::RtpImpl()
{
    m_pvAppdata = nullptr;
}

RtpImpl::~RtpImpl() {}

eRtp_Bool RtpImpl::rtpSsrcCollisionInd(IN RtpDt_Int32 uiOldSsrc, IN RtpDt_Int32 uiNewSsrc)
{
    (RtpDt_Void) uiOldSsrc, (RtpDt_Void)uiNewSsrc;
    return eRTP_FALSE;
}

RtpDt_Void RtpImpl::setAppdata(IN RtpDt_Void* pvAppdata)
{
    m_pvAppdata = pvAppdata;
}

RtpDt_Void* RtpImpl::getAppdata()
{
    return m_pvAppdata;
}

eRtp_Bool RtpImpl::rtpNewMemberJoinInd(IN RtpDt_Int32 uiSsrc)
{
    (RtpDt_Void) uiSsrc;
    return eRTP_FALSE;
}

eRtp_Bool RtpImpl::rtpMemberLeaveInd(IN eRTP_LEAVE_REASON eLeaveReason, IN RtpDt_Int32 uiSsrc)
{
    (RtpDt_Void) eLeaveReason, (RtpDt_Void)uiSsrc;
    return eRTP_FALSE;
}

eRtp_Bool RtpImpl::rtcpPacketSendInd(IN RtpBuffer* pobjRtcpBuf, IN RtpSession* pobjRtpSession)
{
    RTP_TRACE_MESSAGE("rtcpPacketSendInd", 0, 0);
    RtpServiceListener* pobjRtpServiceListener =
            reinterpret_cast<RtpServiceListener*>(getAppdata());
    if (pobjRtpServiceListener == nullptr || pobjRtcpBuf == nullptr || pobjRtpSession == nullptr)
    {
        RTP_TRACE_ERROR("RTCP send failed. No listeners are set", 0, 0);
        return eRTP_FALSE;
    }

    // dispatch to peer
    if (pobjRtpServiceListener->OnRtcpPacket(pobjRtcpBuf->getBuffer(), pobjRtcpBuf->getLength()) ==
            -1)
    {
        RTP_TRACE_ERROR("Send RTCP: IRTPSession returned Error", 0, 0);
        pobjRtcpBuf->setBufferInfo(RTP_ZERO, nullptr);
        return eRTP_FALSE;
    }

    return eRTP_TRUE;
}

eRtp_Bool RtpImpl::rtcpAppPayloadReqInd(
        OUT RtpDt_UInt16& pusSubType, OUT RtpDt_UInt32& uiName, OUT RtpBuffer* pobjPayload)
{
    if (pobjPayload == nullptr)
    {
        return eRTP_FALSE;
    }

    (RtpDt_Void) pusSubType, (RtpDt_Void)uiName, (RtpDt_Void)pobjPayload;
    // To be implemented when Application-Defined RTCP Packet Type feature has to be enabled

    return eRTP_TRUE;
}

eRtp_Bool RtpImpl::getRtpHdrExtInfo(OUT RtpBuffer* pobjExtHdrInfo)
{
    if (pobjExtHdrInfo == nullptr)
    {
        return eRTP_FALSE;
    }

    // allocated memory will be released by the RTP stack
    std::string extInfo("extension header info");
    RtpDt_UChar* pcExtHdrInfo = new RtpDt_UChar[extInfo.size() + 1];
    strlcpy(reinterpret_cast<RtpDt_Char*>(pcExtHdrInfo), extInfo.data(), extInfo.size() + 1);
    pobjExtHdrInfo->setBufferInfo(extInfo.size(), pcExtHdrInfo);
    return eRTP_TRUE;
}

eRtp_Bool RtpImpl::deleteRcvrInfo(
        RtpDt_UInt32 uiRemoteSsrc, RtpBuffer* pobjDestAddr, RtpDt_UInt16 usRemotePort)
{
    (RtpDt_Void) uiRemoteSsrc, (RtpDt_Void)pobjDestAddr, (RtpDt_Void)usRemotePort;
    return eRTP_TRUE;
}

eRtp_Bool RtpImpl::rtcpTimerHdlErrorInd(IN eRTP_STATUS_CODE eStatus)
{
    (RtpDt_Void) eStatus;
    return eRTP_TRUE;
}

RtpDt_Void* RtpImpl::RtpStartTimer(IN RtpDt_UInt32 uiDuration, IN eRtp_Bool bRepeat,
        IN RTPCB_TIMERHANDLER pfnTimerCb, IN RtpDt_Void* pvData)
{
    RtpDt_Void* pvTimerId = reinterpret_cast<RtpDt_Void*>(ImsMediaTimer::TimerStart(
            (RtpDt_UInt32)uiDuration, (bool)bRepeat, (fn_TimerCb)pfnTimerCb, pvData));

    RTP_TRACE_MESSAGE("RtpStartTimer pvTimerId[%x], Duration= [%d]", pvTimerId, uiDuration);
    return pvTimerId;
}

eRtp_Bool RtpImpl::RtpStopTimer(IN RtpDt_Void* pTimerId, OUT RtpDt_Void** ppUserData)
{
    RTP_TRACE_MESSAGE("RtpStopTimer pvTimerId[%x]", pTimerId, 0);
    ImsMediaTimer::TimerStop((hTimerHandler)pTimerId, ppUserData);
    (void)ppUserData;
    return eRTP_TRUE;
}

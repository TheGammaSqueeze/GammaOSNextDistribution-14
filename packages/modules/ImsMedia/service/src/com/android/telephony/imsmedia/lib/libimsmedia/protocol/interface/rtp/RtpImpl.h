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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTP_IMPL_H_
#define __RTP_IMPL_H_

#include <IRtpAppInterface.h>
#include <RtpBuffer.h>
#include <RtpGlobal.h>

class IRtpAppInterface;

/**
 * @class    RtpImpl
 * @brief    This class implements RTP callback methods [IRtpAppInterface].
 *
 * @see IRtpAppInterface
 */
class RtpImpl : public IRtpAppInterface
{
private:
    RtpDt_Void* m_pvAppdata;

public:
    RtpImpl();
    ~RtpImpl();

    eRtp_Bool rtpSsrcCollisionInd(IN RtpDt_Int32 uiOldSsrc, IN RtpDt_Int32 uiNewSsrc);

    RtpDt_Void setAppdata(IN RtpDt_Void* pvAppdata);

    RtpDt_Void* getAppdata();

    eRtp_Bool rtpNewMemberJoinInd(IN RtpDt_Int32 uiSsrc);

    eRtp_Bool rtpMemberLeaveInd(IN eRTP_LEAVE_REASON eLeaveReason, IN RtpDt_Int32 uiSsrc);

    eRtp_Bool rtcpPacketSendInd(IN RtpBuffer* pobjRtcpBuf, IN RtpSession* pobjRtpSession);

    eRtp_Bool rtcpAppPayloadReqInd(
            OUT RtpDt_UInt16& usSubType, OUT RtpDt_UInt32& uiName, OUT RtpBuffer* pobjPayload);

    eRtp_Bool getRtpHdrExtInfo(OUT RtpBuffer* pobjExtHdrInfo);

    eRtp_Bool deleteRcvrInfo(
            IN RtpDt_UInt32 uiRemoteSsrc, IN RtpBuffer* pobjDestAddr, IN RtpDt_UInt16 usRemotePort);

    eRtp_Bool rtcpTimerHdlErrorInd(IN eRTP_STATUS_CODE eStatus);

    RtpDt_Void* RtpStartTimer(IN RtpDt_UInt32 uiDuration, IN eRtp_Bool bRepeat,
            IN RTPCB_TIMERHANDLER pfnTimerCb, IN RtpDt_Void* pvData);

    eRtp_Bool RtpStopTimer(IN RtpDt_Void* pTimerId, OUT RtpDt_Void** ppUserData);
};

#endif /* __RTP_IMPL_H_ */

/** @}*/

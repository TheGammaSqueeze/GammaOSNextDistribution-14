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

#ifndef __IRTP_APPINTERFACE_H__
#define __IRTP_APPINTERFACE_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>

typedef RtpDt_Void (*RTPCB_TIMERHANDLER)(RtpDt_Void* pvTimerId, RtpDt_Void* pvData);

class RtpSession;

/**
 * @class IRtpAppInterface
 *
 * @brief It is an interface class to the application. Callbacks will be implemented by the
 * application.
 */
class IRtpAppInterface
{
    // application handle.

public:
    // Destructor
    virtual ~IRtpAppInterface(){};

    /**
     * This callback function should handle SSRC collision reported by RTP stack.
     * If there is collision between the SSRC used by RTPstack and that chosen by another
     * participant then,
     * it must send an RTCP BYE for the original SSRC and select another SSRC for itself.
     *
     * @param uiOldSsrc Old SSRC received as part of RTP packets header.
     * @param uiNewSsrc New SSRC received as part of latest RTP packet header.
     */
    virtual eRtp_Bool rtpSsrcCollisionInd(IN RtpDt_Int32 uiOldSsrc, IN RtpDt_Int32 uiNewSsrc) = 0;

    /**
     * Store appdata that can be used when the call back is called on this object
     *
     * @param    pvAppdata pointer to app data which must be stored/associated with the RTP session.
     */
    virtual RtpDt_Void setAppdata(IN RtpDt_Void* pvAppdata) = 0;

    /**
     * Get method to fetch app data associated with the RTP session.
     *
     * @return   pointer to app associated with the RTP session which was previously set using
     * setAppdata.
     *
     * @see setAppdata
     */
    virtual RtpDt_Void* getAppdata() = 0;

    /**
     * This Callback function is called by RTP stack when an RTP packet with new SSRC is received.
     *
     * @uiSsrc  SSRC of the member who joined the session.
     */
    virtual eRtp_Bool rtpNewMemberJoinInd(IN RtpDt_Int32 uiSsrc) = 0;

    /**
     * Callback function called by RTP stack when a participant has left the conference or
     * has sent bye to resolve collision of ssrc.
     * The application should not treat this as a reason to delete/close the session
     *
     * @param eLeaveReason This param indicates if the leave reason is RTCP BYE or no RTP packets
     * are received by the member identified by uiSSrc.
     *
     * @param uiSsrc    SSRC of the participant.
     */
    virtual eRtp_Bool rtpMemberLeaveInd(
            IN eRTP_LEAVE_REASON eLeaveReason, IN RtpDt_Int32 uiSsrc) = 0;
    /**
     * This Callback is called when stack wants to send RTCP packet buffer
     * received to the network node.
     *
     * @param pobjRtcpPkt RTCP packet buffer to be sent.
     *
     * @param pobjRtpSession RTP Session which is sending the RTCP packet.
     */
    virtual eRtp_Bool rtcpPacketSendInd(
            IN RtpBuffer* pobjRtcpPkt, IN RtpSession* pobjRtpSession) = 0;

    /**
     * This callback is called by RTP Stack when it is forming app packet and the App
     * should provide stack with the sub-type, name of the packet and payload.
     *
     * @param usSubType This is an App defined sub-type. It can be used to differentiate between
     * app defined packet types.
     *
     * @param uiName App name String.
     *
     * @param pobjPayload App Payload Data.
     */
    virtual eRtp_Bool rtcpAppPayloadReqInd(
            OUT RtpDt_UInt16& usSubType, OUT RtpDt_UInt32& uiName, OUT RtpBuffer* pobjPayload) = 0;
    /**
     * This function is called when RTP Stack is preparing RTCP report block.
     * App need to fill the RTP extension header buffer.
     *
     * @param pobjExtHdrInfo RTP extension header buffer to be filled by the app.
     */
    virtual eRtp_Bool getRtpHdrExtInfo(OUT RtpBuffer* pobjExtHdrInfo) = 0;

    /**
     * This callback is called when receiver has left the rtp session or when the rtp session is
     * going to stop.
     *
     * @param uiRemoteSsrc SSRC of the participant/peer.
     *
     * @param pobjDestAddr IPAddress of the participant.
     *
     * @param usRemotePort RTP port of the participant.
     */
    virtual eRtp_Bool deleteRcvrInfo(IN RtpDt_UInt32 uiRemoteSsrc, IN RtpBuffer* pobjDestAddr,
            IN RtpDt_UInt16 usRemotePort) = 0;

    /**
     * This callback is called when there is any error while processing (packing or parsing)
     * RTCP packets.
     *
     * @param eStatus error enum indicating the error type.
     */
    virtual eRtp_Bool rtcpTimerHdlErrorInd(IN eRTP_STATUS_CODE eStatus) = 0;

    /**
     * This callback is called whenever RTP stacks needs to start the timer for sending periodic
     * RTCP packets.
     * App need to make sure that the timer expiry function pfnTimerCb is called at the uiDuration
     * intervals.
     *
     * @param   uiDuration   Timer expiry interval.
     *
     * @param   bRepeat      If the timer should repeat or one-time expire.
     *
     * @param   pfnTimerCb   Callback to be called when the timer expires.
     *
     * @param   pvData       Data to be passed to callback when timer expires.
     *
     * @return returns timer-id.
     */
    virtual RtpDt_Void* RtpStartTimer(IN RtpDt_UInt32 uiDuration, IN eRtp_Bool bRepeat,
            IN RTPCB_TIMERHANDLER pfnTimerCb, IN RtpDt_Void* pvData) = 0;

    /**
     * This callback is called when the RTCP timer need to be cancelled.
     *
     * @param pTimerId Id of the timer to be stopeed.
     */
    virtual eRtp_Bool RtpStopTimer(IN RtpDt_Void* pTimerId, OUT RtpDt_Void** pUserData) = 0;
};

#endif  //__IRTP_APPINTERFACE_H__

/** @}*/

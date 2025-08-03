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

#ifndef __RTP_SESSION_H__
#define __RTP_SESSION_H__

#include <RtpGlobal.h>
#include <RtpPayloadInfo.h>
#include <RtpStack.h>
#include <IRtpAppInterface.h>
#include <RtcpConfigInfo.h>
#include <RtpPacket.h>
#include <RtpTimerInfo.h>
#include <RtpReceiverInfo.h>
#include <RtcpPacket.h>
#include <mutex>
#include <list>

class RtpStack;

/**
 * @class   RtpSession
 * @brief   This is an interface class to the RTP application for RTP session handling.
 * An RTP session is capable of processing packets received for that session.
 * This represents the RTP and the associated RTCP session.
 * The RTP session has a reference to the RTP stack instance to which it belongs.
 * When a session is deleted, the stack is notified before deleting so that the session list in the
 * stack can be updated.
 * The destructor is made private so that this notification mechanism cannot be bypassed while
 * deleting the session object.
 */
class RtpSession
{
    std::mutex m_objRtpSessionLock;

    // Ip address assigned to RTP session
    RtpBuffer* m_pobjTransAddr;

    // RTP Port number assigned to RTP session
    RtpDt_UInt16 m_usRtpPort;

    // RTCP port assigned to RTP session
    RtpDt_UInt16 m_usRtcpPort;

    // The stack context for this session. This is got from constructor
    RtpStack* m_pobjRtpStack;

    // It tells RTP extension header support
    RtpDt_UInt16 m_usExtHdrLen;

    // RtcpConfigInfo
    RtcpConfigInfo* m_pobjRtcpCfgInfo;

    // Process RTP in this session
    eRtp_Bool m_bEnableRTP;

    // Use RTCP in this session
    eRtp_Bool m_bEnableRTCP;

    // Enable RTCP Bye
    eRtp_Bool m_bEnableRTCPBye;

    // App configured RTCP timer value
    RtpDt_UInt16 m_usRTCPTimerVal;

    // current sequence number that the will be used for next packet that will constructed.
    RtpDt_UInt16 m_usSeqNum;

    // Number of times the seqNum has wrapped at 2^16
    RtpDt_UInt16 m_usSeqNumCycles;

    /**
     * Payload descriptions used in the session
     */
    RtpPayloadInfo* m_pobjPayloadInfo;

    /**
     * Interface for app callbacks. These call backs will be used to notify app
     * about stack events.
     */
    IRtpAppInterface* m_pobjAppInterface;

    // our SSRC for this session
    RtpDt_UInt32 m_uiSsrc;

    // contains the state variables required for calculating RTCP Transmission Timer
    RtpTimerInfo m_objTimerInfo;

    // list of RtpReceiverInfo
    std::list<RtpReceiverInfo*>* m_pobjRtpRcvrInfoList;

    // list of RtpReceiverInfo
    std::list<RtpReceiverInfo*>* m_pobjUtlRcvrList;

    // MTU size to be used for this session. This will be used when preparing a
    // compound RTCP packet to limit the number of sources for which we are sending
    // the report
    RtpDt_UInt32 m_uiSessionMtu;

    // Count of number of RTP packets I have sent
    RtpDt_UInt32 m_uiRtpSendPktCount;

    // Count of number of octets of RTP I have sent
    RtpDt_UInt32 m_uiRtpSendOctCount;

    // Count of number of RTCP packets I have sent
    RtpDt_UInt32 m_uiRtcpSendPktCount;

    // Count of number of octets of RTCP I have sent
    RtpDt_UInt32 m_uiRtcpSendOctCount;

    /* SPR # 473 END */
    /*as per spec, if we receive our own packets then send bye once, change ssrc,
    and then ignore the received packets if they continue to loop back to us
    due to a faulty mixer/translator implementation.
    */
    eRtp_Bool m_bSelfCollisionByeSent;

    // Timer Id
    RtpDt_Void* m_pTimerId; /* Storing Timer Id to stop while deleting
                         session*/

    // Previous RTP timestamp
    RtpDt_UInt32 m_prevRtpTimestamp;

    // Current RTP timestamp
    RtpDt_UInt32 m_curRtpTimestamp;

    // Current Ntp Timestamp
    tRTP_NTP_TIME m_stCurNtpTimestamp;

    // Previous Ntp Timestamp
    tRTP_NTP_TIME m_stPrevNtpTimestamp;

    // it tells RTP packet has been sent after timer expiry
    eRtp_Bool m_bRtpSendPkt;

    // To control the RTCP transmission packets
    eRtp_Bool m_bRtcpTxFlag;

    // To control the RTCP reception packets
    eRtp_Bool m_bRtcpRxFlag;

    // Timer callback for RTCP
    RTPCB_TIMERHANDLER m_pfnTimerCb;

    // Current RTCP timestamp
    RtpDt_UInt32 m_curRtcpTimestamp;

    // Current Ntp Timestamp
    tRTP_NTP_TIME m_stCurNtpRtcpTs;

    // it tells RTCP packet has been sent
    eRtp_Bool m_bRtcpSendPkt;

    // It will be enabled @ delete session
    eRtp_Bool m_bSndRtcpByePkt;

    // it will store RTTD value
    RtpDt_UInt32 m_lastRTTDelay;

    // RTCP-XR data
    tRTCP_XR_DATA m_stRtcpXr;

    // to check if Xr packet is being sent
    eRtp_Bool m_bisXr;

    // it will check if first RTP packet received
    eRtp_Bool m_bFirstRtpRecvd;

    /**
     * It checks SSRC is present in the receiver list
     */
    eRtp_Bool findEntryInRcvrList(IN RtpDt_UInt32 uiSsrc);

    /**
     * It processes the Received CSRC list after receiving the RTP packet
     */
    eRTP_STATUS_CODE processCsrcList(IN RtpHeader* pobjRtpHeader, IN RtpDt_UChar ucCsrcCount);

    /**
     * Decodes received RTCP packet and adds entry to Receiver list
     * if SSRC is not present.
     */
    RtpReceiverInfo* processRtcpPkt(
            IN RtpDt_UInt32 uiRcvdSsrc, IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort);

    /**
     * It deletes entry from Receiver list
     */
    RtpDt_Void delEntryFromRcvrList(IN RtpDt_UInt32* puiSsrc);

    /**
     * It processes the Received RTCP BYE packet. Deletes entry from Receiver list.
     */
    eRTP_STATUS_CODE processByePacket(
            IN RtcpByePacket* pobjByePkt, IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort);

    /**
     * It processes the Received SDES packet
     */
    eRTP_STATUS_CODE processSdesPacket(IN RtcpSdesPacket* pobjSdesPkt);

    /**
     * Calculate the timer interval for RTCP
     */
    RtpDt_Double rtcp_interval(IN RtpDt_UInt16 usMembers);

    /**
     * It iterates through the pobjCsrcList to find uiSsrc.
     */
    eRtp_Bool findEntryInCsrcList(IN std::list<RtpDt_UInt32>& pobjCsrcList, IN RtpDt_UInt32 uiSsrc);

    /**
     * Checks if the received packet has the same ssrc as ours.
     */
    RtpReceiverInfo* checkSsrcCollisionOnRcv(IN RtpBuffer* pobjRtpAddr, IN RtpDt_UInt16 usPort,
            IN RtpDt_UInt32 uiRcvdSsrc, OUT eRTP_STATUS_CODE& eResult);

    /**
     * It sends the RTCP bye packet to  uiReceivedSsrc
     */
    eRTP_STATUS_CODE collisionSendRtcpByePkt(IN RtpDt_UInt32 uiReceivedSsrc);

    /**
     * It populates Rtp header
     */
    eRTP_STATUS_CODE populateRtpHeader(
            IN RtpHeader* pobjRtpHdr, IN eRtp_Bool eSetMarker, IN RtpDt_UChar ucPayloadType);

    /**
     * It calculates number of senders in the receiver list
     */
    RtpDt_UInt32 getSenderCount();

    /**
     * It populates RTCP SR packet
     */
    eRTP_STATUS_CODE populateSrpacket(OUT RtcpSrPacket* pobjSrPkt, IN RtpDt_UInt32 uiRecepCount);

    /**
     * It populates RTCP RR packet
     */
    eRTP_STATUS_CODE populateReportPacket(
            OUT RtcpRrPacket* pobjRrPkt, IN eRtp_Bool bRrPkt, IN RtpDt_UInt32 uiRecepCount);

    /**
     * It populates RTCP BYE packet
     */
    eRTP_STATUS_CODE populateByePacket(IN_OUT RtcpPacket* pobjRtcpPkt);

    /**
     * It populates RTCP APP packet
     */
    eRTP_STATUS_CODE populateAppPacket(IN_OUT RtcpPacket* pobjRtcpPkt);

    eRTP_STATUS_CODE populateRtcpFbPacket(IN_OUT RtcpPacket* pobjRtcpPkt, IN RtpDt_UInt32 uiFbType,
            IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen, IN RtpDt_UInt32 uiMediaSSRC,
            IN RtpDt_UInt32 uiPayloadType);

    /**
     * It constructs SR packet list
     */
    eRTP_STATUS_CODE formSrList(IN RtpDt_UInt32 uiSndrCount, OUT RtcpPacket* pobjRtcpPkt);
    /**
     * It constructs RR packet list
     */
    eRTP_STATUS_CODE formRrList(IN RtpDt_UInt32 uiSndrCount, OUT RtcpPacket* pobjRtcpPkt);

    /**
     * It estimates the total size of APP, SDES and BYE
     */
    RtpDt_UInt32 estimateRtcpPktSize();

    /**
     * It cleans utl receiver list
     */
    RtpDt_Void cleanUtlReceiverList();

    /**
     * it will set RTTD value
     */
    RtpDt_Void calculateAndSetRTTD(
            IN RtpDt_UInt32 currentTime, IN RtpDt_UInt32 lsr, IN RtpDt_UInt32 dlsr);

    eRTP_STATUS_CODE updatePayload(IN RtpPayloadInfo* pstPayloadInfo);

    /**
     * It iterates through the receiver list to verify the status of the uiRcvdSsrc.
     * @param[in] pobjRtpAddr ip address associated to uiRcvdSsrc
     * @param[in] usPort port number associated to uiRcvdSsrc
     * @param[in] uiRcvdSsrc received synchronization source.
     */
    eRTP_STATUS_CODE chkRcvdSsrcStatus(
            IN RtpBuffer* pobjRtpAddr, IN RtpDt_UInt16 usPort, IN RtpDt_UInt32 uiRcvdSsrc);

    RtpDt_UInt16 getRtpPort();

    RtpBuffer* getRtpTransAddr();

    RtpDt_UInt16 getExtHdrLen();

    /**
     * method for sending rtcp packet
     */
    eRTP_STATUS_CODE rtpSendRtcpPacket(IN_OUT RtcpPacket* objRtcpPkt);

    /**
     * method for setting timestamp for RTCP packet
     */
    RtpDt_Void rtpSetTimestamp();

    /**
     * method for making compound rtcp packet
     */
    eRTP_STATUS_CODE rtpMakeCompoundRtcpPacket(IN_OUT RtcpPacket* objRtcpPkt);

    /**
     * method for calculating total rtcp packet size
     */
    RtpDt_UInt32 calculateTotalRtcpSize(
            IN RtpDt_UInt32 uiSndrCount, IN RtpDt_UInt32 uiEstRtcpSize, IN eRtp_Bool isSR);

    /**
     * method for calculating number of report block in SR/RR
     */
    RtpDt_UInt32 numberOfReportBlocks(IN RtpDt_UInt32 uiMtuSize, IN RtpDt_UInt32 uiEstRtcpSize);

    eRTP_STATUS_CODE constructSdesPkt(IN_OUT RtcpPacket* pobjRtcpPkt);

    eRTP_STATUS_CODE populateRtcpXrPacket(IN_OUT RtcpPacket* pobjRtcpPkt);

    /**
     * Check of the received RTP packet payload type is matching with the expected payload types.
     *
     * @param RtpHeader
     * @return true if mathes and false otherwise.
     */
    eRtp_Bool checkRtpPayloadType(
            IN RtpHeader* pobjRtpHeader, IN RtpPayloadInfo* m_pobjPayloadInfo);

public:
    ~RtpSession();

    /**
     * Creates a session as part of a stack instance. Application will never create a session
     * directly by instantiating this object. It will use the Rtp_Stack::CreateRTPSession()
     * interface to do this.
     * @param pobjStack Instance of the stack to which this session belongs
     */
    RtpSession(IN RtpStack* pobjStack);

    /**
     * The default constructor is used only for unit test.
     */
    RtpSession();

    /**
     * Initialize the session with
     * - payload information. This will be the number used to send in the packet
     * - creating a ssrc for the session
     * - Application interface object for notifications
     * - RTCP related initialization
     * - Enables RTCP based on bRtcpEnable flag
     * - Starts an RTCP timer based on calculated timer interval
     *
     * This API would be invoked after creating the RTP session to initialize Rtp session info.
     *
     * @param[in] pstPayloadInfo payload type and sampling rate
     * @param[in] bRtcpEnable Pass eRTP_TRUE if application wants to disable RTCP
     * @param[in] pobjAppInterface This will be stored by stack for giving notifications to
     * application
     * @param[in] pobjRtcpConfigInfo This contains SDES info, CNAME, NAME, EMAIL, PHONE, TOOL,
     * - Frequency at which each SDES info shall be sent.
     * - Frequency at which the App packet shall be sent
     */
    eRTP_STATUS_CODE initSession(
            IN IRtpAppInterface* pobjAppInterface, IN RtcpConfigInfo* pobjRtcpConfigInfo);
    /**
     * Update the payload info for this stream if it changes after initSession().
     *
     * @param pstPayloadInfo: payload type and sampling rate
     */
    eRTP_STATUS_CODE setPayload(IN RtpPayloadInfo* pstPayloadInfo, IN RtpDt_UInt16 usExtHdrLen);

    eRTP_STATUS_CODE setRTCPTimerValue(IN RtpDt_UInt16 usRTCPTimerVal);

    /**
     * calls the delete stream of RTP stack.
     */
    eRTP_STATUS_CODE deleteRtpSession();

    /**
     * Decode a received RTP packet.
     * Check for ssrc collision
     * Information updated per session participant
     *   - Total packets received
     *   - Bytes of payload received
     *   - Sequence number of the packet received
     *   - roll over couter to track the number of times seq number has wrapped
     *   - received packet list.. to calculate packet loss
     *   - inter arrival jitter updation using
     *       - Last packet's receiver RTP time stamp
     *       - Last packet's sender RTP time stamp
     *       - last calculated jitter value
     *       - Current packet's receiver RTP timestamp
     *       - Current packet's sender RTP timestamp
     * update total number of members
     * update list of members
     * update total number of active senders
     * update list of active senders
     * @param[in] pobjRtpAddr Ip address from which packet is received
     * @param[in] usPort port number from which packet is received.
     * @param[in] pobjRTPPacket Buffer from network and the number of bytes in the buffer
     * @param[out] pobjRtpPkt Decoded RTP packet
     */
    eRTP_STATUS_CODE processRcvdRtpPkt(IN RtpBuffer* pobjRtpAddr, IN RtpDt_UInt16 usPort,
            IN RtpBuffer* pobjRTPPacket, OUT RtpPacket* pobjRtpPkt);

    /**
     * It constructs the RTP packet.
     *     - It allocates the memory for RTP packet
     *     - if any error is encountered in RTP packet construction,
     *        memory will be released by the RTP Stack.
     *     - if any error is NOT encountered in RTP packet construction,
     *        memory will be released by the Application.
     *     - It updates the Statistics parameters associated to the SSRC.
     *         - Update the number of packets that we have sent in uiSendPktCount
     *         - Update the number of bytes that we have sent:
     *            This includes RTP header. Update uiSendOctCount
     *
     * @param[in] pobjPayload Rtp payload with length
     * @param[in] eSetMarker if marker flag is set, marker bit will be set in RTP header.
     * @param[out] pRtpPkt Rtp packet with length.
     */
    eRTP_STATUS_CODE createRtpPacket(IN RtpBuffer* pobjPayload, IN eRtp_Bool eSetMarker,
            IN RtpDt_UChar ucPayloadType, IN eRtp_Bool bUseLastTimestamp,
            IN RtpDt_UInt32 uiRtpTimestampDiff, IN RtpBuffer* pobjXHdr, OUT RtpBuffer* pRtpPkt);

    /**
     * - Decode a received RTCP packet.
     * - Check for ssrc collision.
     * - update total number of members.
     * - update list of members.
     * - update total number of active senders.
     * - update list of active senders.
     * @param[in] pobjRtcpAddr Ip address from which packet is received
     * @param[in] usPort port number from which packet is received.
     * @param[in] pobjRTCPPacket Buffer from network and the number of bytes in the buffer
     * @param[out] pobjRtcpPkt Decoded RTCP packet
     */
    eRTP_STATUS_CODE processRcvdRtcpPkt(IN RtpBuffer* pobjRtcpAddr, IN RtpDt_UInt16 usPort,
            IN RtpBuffer* pobjRTCPPacket, OUT RtcpPacket* pobjRtcpPkt);

    eRtp_Bool sendRtcpByePacket();

    eRtp_Bool sendRtcpRtpFbPacket(IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff,
            IN RtpDt_UInt32 uiLen, IN RtpDt_UInt32 uiMediaSsrc);

    eRtp_Bool sendRtcpPayloadFbPacket(IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff,
            IN RtpDt_UInt32 uiLen, IN RtpDt_UInt32 uiMediaSsrc);

    /**
     * It sets the m_bRtcpTxFlag to control the RTCP data transmission.
     * @param[in] bRtcpTxFlag
     */
    RtpDt_Void setRtcpTxFlag(IN eRtp_Bool bRtcpTxFlag);

    /**
     * It sets the bRtcpRxFlag to control the RTCP data reception.
     * @param[in] bRtcpRxFlag
     */
    RtpDt_Void setRtcpRxFlag(IN eRtp_Bool bRtcpRxFlag);

    /**
     * It sets the m_bRtpTxFlag to control the RTCP data transmission.
     *
     * @param[in] bRtpTxFlag
     */
    RtpDt_Void setRtpTxFlag(IN eRtp_Bool bRtpTxFlag);

    /**
     *
     * It sets the bRtpRxFlag to control the RTP data reception.
     * @param[in] bRtpRxFlag
     */
    RtpDt_Void setRtpRxFlag(IN eRtp_Bool bRtpRxFlag);

    RtpDt_Void setSsrc(IN RtpDt_UInt32 uiSsrc);

    RtpDt_UInt32 getSsrc();

    RtpDt_Void setSequenceNumber(IN RtpDt_UInt16 uiSeqNumber);

    RtpDt_UInt16 getSequenceNumber();

    RtpDt_Void setRtpPort(IN RtpDt_UInt16 usPort);

    RtpDt_Void setRtpTransAddr(IN RtpBuffer* pobjDestTransAddr);

    /**
     * It compares the DestAddr, Port and SSRC with this object.
     *
     * @param pobjSession Session to be compared.
     */
    eRtp_Bool compareRtpSessions(IN RtpSession* pobjSession);

    /**
     * Handling of RTCP timer expiry.
     * It constructs the RTCP compound packet after rtcp timer expiry.
     * It re-calculates the RTCP timer value and starts the timer.
     */
    RtpDt_Void rtcpTimerExpiry(IN RtpDt_Void* pvTimerId);

    eRTP_STATUS_CODE disableRtcp();

    eRTP_STATUS_CODE enableRtcp(eRtp_Bool enableRTCPBye);

    /**
     * disables Rtp processing
     */
    eRTP_STATUS_CODE disableRtp();

    /**
     * enables Rtp Processing
     */
    eRTP_STATUS_CODE enableRtp();

    /**
     * To check if RTP processing is enabled
     */
    eRtp_Bool isRtpEnabled();

    eRTP_STATUS_CODE sendRtcpXrPacket(IN RtpDt_UChar* m_pBlockBuffer, IN RtpDt_UInt16 nblockLength);

    RtpDt_UInt32 getRTTD();
};

#endif  //__RTP_SESSION_H__

/** @}*/

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

/**
 * @brief Integration layer for RTP Protocol stack integration with ImsMedia (IRtpSession)
 */

#ifndef __RTP_SERVICE_H_
#define __RTP_SERVICE_H_

#include <RtpGlobal.h>
#include <RtpServiceTypes.h>
#include <stdint.h>

#define GLOBAL

class RtpServiceListener
{
public:
    RtpServiceListener() {}
    virtual ~RtpServiceListener() {}
    // receive RTP packet, send it to rtp tx node
    virtual int OnRtpPacket(unsigned char* pData, RtpSvc_Length wLen) = 0;
    // receive RTCP packet, send it to rtcp node
    virtual int OnRtcpPacket(unsigned char* pData, RtpSvc_Length wLen) = 0;
    // indication from the RtpStack
    virtual void OnPeerInd(tRtpSvc_IndicationFromStack eIndType, void* pMsg) = 0;
    // indication from the RtpStack
    virtual void OnPeerRtcpComponents(void* nMsg) = 0;
};

/**
 * Initialized RTP Protocol Stack. This API should be called only once per
 * lifecycle of the application. RTP Sessions can be created after successful
 * return of this function.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_Initialize();

/**
 * Deinitialized RTP Protocol Stack by freeying the memory used to manage RTPSessions.
 * This API should be called at the end of lifecycle of the application.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_Deinitialize();

/**
 * API should be used to create RTP Sessions. One RTP session per stream.
 * Same RTP Session can be used for both sending and receiving a given payload type.
 *
 * @param szLocalIP LocalIP address on which RTP packets will be received.
 *
 * @param uiPort RTP port on which packets will be received.
 *
 * @param pAppData Data to be store with the RTP Session.
 *
 * @param puSsrc SSRC of the newly created session
 *
 * @param hRtpSession handle of the newly created session.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_CreateSession(IN RtpDt_Char* szLocalIP, IN RtpDt_UInt32 port,
        IN RtpDt_Void* pAppData, OUT RtpDt_UInt32* puSsrc, OUT RTPSESSIONID* hRtpSession);

/**
 * This API should be called to set payload info of the RTP packets to be processed but
 * the RTP Stack.
 *
 * @param hRtpSession  A session handled to which payload params to be updated.
 *
 * @param pstPayloadInfo  Array of payload info which contains payload type, sampling rate
 * and framerate.
 *
 * @param bEnableXHdr   Flag to enable CVO RTP extension header for VT streams.
 *
 * @param nNumOfPayloadParam    Size of payload info array. If the RTP session has to process more
 * than one payload types like Audio Frames and telephony-events then this array can be used.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SetPayload(IN RTPSESSIONID hRtpSession,
        IN tRtpSvc_SetPayloadParam* pstPayloadInfo, IN eRtp_Bool bEnableXHdr,
        IN RtpDt_UInt32 nNumOfPayloadParam);

/**
 * This API can be used to set RTCP send interval.
 *
 * @param hRtpSession A session handled to which RTCP interval to be set.
 *
 * @param nInterval time difference between RTCP packets in seconds.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SetRTCPInterval(IN RTPSESSIONID hRtpSession, IN RtpDt_UInt32 nInterval);

/**
 * API to delete RTP session.
 *
 * @param hRtpSession RTP session to be deleted.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_DeleteSession(IN RTPSESSIONID hRtpSession);

/**
 * This API is should be called by the application to RTP encode and send the media
 * buffer to peer device.
 *
 * @param pobjRtpServiceListener media session Listener which will be used for sending the packet to
 * network nodes after RTP encoding.
 *
 * @param hRtpSession A session handled associated with the media stream.
 *
 * @param pBuffer Media buffer to be transferred to peer device.
 *
 * @param wBufferLength Media buffer length in bytes.
 *
 * @param pstRtpParam Packet info such as marker-bit (used in case of fragmented media packets),
 * payload-type number, Flag to use Previous RTP time-stamp (Ex: used in case of DTMF),
 * time difference since last media packet/buffer.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SendRtpPacket(IN RtpServiceListener* pobjRtpServiceListener,
        IN RTPSESSIONID hRtpSession, IN RtpDt_Char* pBuffer, IN RtpDt_UInt16 wBufferLength,
        IN tRtpSvc_SendRtpPacketParam* pstRtpParam);

/**
 * This API processes the received RTP packet. Processed information is sent using
 * callback OnPeerInd.
 *
 * @param pobjRtpServiceListener media session Listener used to call callback function and
 * pass extracted information back to the caller
 *
 * @param hRtpSession A session handled associated with the media stream.
 *
 * @param pMsg Received RTP packet buffer from Network node.
 *
 * @param uiMsgLength Length of RTP packet buffer in bytes.
 *
 * @param pPeerIp IP Address of the RTP packet sender. Used for SSRC collision check.
 *
 * @param uiPeerPort RTP port number.
 *
 * @param uiPeerSsrc SSRC of the Sender.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_ProcRtpPacket(IN RtpServiceListener* pobjRtpServiceListener,
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* pMsg, IN RtpDt_UInt16 uiMsgLength,
        IN RtpDt_Char* pPeerIp, IN RtpDt_UInt16 uiPeerPort, OUT RtpDt_UInt32& uiPeerSsrc);

/**
 * This API starts the RTP session. After successful return, stack is ready to send and
 * receive RTP packets.
 *
 * @param rtpSessionId A session handled associated with the media stream.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SessionEnableRTP(IN RTPSESSIONID rtpSessionId, IN eRtp_Bool bResetSsrc);

/**
 * This API stops processing TX and RX RTP packets.
 *
 * @param rtpSessionId session which need to be stopped.
 */

GLOBAL eRtp_Bool IMS_RtpSvc_SessionDisableRTP(IN RTPSESSIONID rtpSessionId);

/**
 * This API enables RTP session and starts sending periodic RTCP packets.
 *
 * @param hRtpSession RTP session to be started.
 *
 * @param enableRTCPBye Flag to control sending RTCP BYE packet when session is stopped.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SessionEnableRTCP(
        IN RTPSESSIONID hRtpSession, IN eRtp_Bool enableRTCPBye);

/**
 * This API stops RTCP timer and hence sending periodic RTCP packets.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SessionDisableRTCP(IN RTPSESSIONID hRtpSession);

/**
 * This API should be used to send RTCP BYE packet.
 *
 * @param hRtpSession RTP session which should send BYE packet.
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpByePacket(IN RTPSESSIONID hRtpSession);

/**
 * Method for sending RTP Fb message.
 *
 * @param hRtpSession     pointer RtpSession
 * @param uiFbType         Feedback Type
 * @param pcBuff           FCI buffer
 * @param uiLen            FCI buffer length
 * @param uiMediaSsrc      SSRC of media source
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpRtpFbPacket(IN RTPSESSIONID hRtpSession,
        IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen,
        IN RtpDt_UInt32 uiMediaSsrc);

/**
 * Method for sending RTCP Fb message.
 *
 * @param hRtpSession      RtpSession
 * @param uiFbType         Feedback Type
 * @param pcBuff           FCI buffer
 * @param uiLen            FCI buffer length
 * @param uiMediaSsrc      SSRC of media source
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpPayloadFbPacket(IN RTPSESSIONID hRtpSession,
        IN RtpDt_UInt32 uiFbType, IN RtpDt_Char* pcBuff, IN RtpDt_UInt32 uiLen,
        IN RtpDt_UInt32 uiMediaSsrc);

/**
 * Method for processing incoming RTCP packets.
 *
 * @param pobjRtpServiceListener   Media session Listener for sending processed info via callbacks
 * @param hRtpSession       RTP session handle
 * @param pMsg              Received RTCP packet buffer
 * @param uiMsgLength       RTCP buffer length in bytes
 * @param pcIpAddr          Peer IP address
 * @param uiRtcpPort        RTCP Port number
 * @param uiPeerSsrc        SSRC of the Source
 */
GLOBAL eRtp_Bool IMS_RtpSvc_ProcRtcpPacket(IN RtpServiceListener* pobjRtpServiceListener,
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* pMsg, IN RtpDt_UInt16 uiMsgLength,
        IN RtpDt_Char* pcIpAddr, IN RtpDt_UInt32 uiRtcpPort, OUT RtpDt_UInt32* uiPeerSsrc);
/**
 * Method to set RTCP XR info.
 *
 * @param m_hRtpSession     RTP session handle
 * @param m_pBlockBuffer    XR Block buffer
 * @param nblockLength      Buffer length in bytes
 */
GLOBAL eRtp_Bool IMS_RtpSvc_SendRtcpXrPacket(
        IN RTPSESSIONID hRtpSession, IN RtpDt_UChar* m_pBlockBuffer, IN RtpDt_UInt16 nblockLength);

GLOBAL eRtp_Bool IMS_RtpSvc_SetRtpContext(IN RTPSESSIONID hRtpSession, IN RtpDt_UInt32 ssrc,
        IN RtpDt_UInt32 timestamp, IN RtpDt_UInt16 seqNumber);

GLOBAL eRtp_Bool IMS_RtpSvc_GetRtpContext(IN RTPSESSIONID hRtpSession, IN RtpDt_UInt32& ssrc,
        IN RtpDt_UInt32& timestamp, IN RtpDt_UInt16& seqNumber);

#endif /* __RTP_SERVICE_H_ */

/** @}*/

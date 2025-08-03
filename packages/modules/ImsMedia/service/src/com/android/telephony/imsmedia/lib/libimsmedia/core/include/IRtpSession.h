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

#ifndef IRTP_SESSION_H
#define IRTP_SESSION_H

#include <ImsMediaDefine.h>
#include <AudioConfig.h>
#include <RtpService.h>
#include <list>
#include <atomic>
#include <stdint.h>
#include <mutex>

/*!
 * @class       IRtpEncoderListener
 */
class IRtpEncoderListener
{
public:
    IRtpEncoderListener() {}
    virtual ~IRtpEncoderListener() {}
    virtual void OnRtpPacket(unsigned char* pData, uint32_t wLen) = 0;
};

/*!
 * @class        IRtcpEncoderListener
 */
class IRtcpEncoderListener
{
public:
    IRtcpEncoderListener() {}
    virtual ~IRtcpEncoderListener() {}
    virtual void OnRtcpPacket(unsigned char* pData, uint32_t wLen) = 0;
};

/*!
 * @class        IRtpDecoderListener
 */
class IRtpDecoderListener
{
public:
    IRtpDecoderListener() {}
    virtual ~IRtpDecoderListener() {}
    virtual void OnMediaDataInd(unsigned char* data, uint32_t dataSize, uint32_t timestamp,
            bool mark, uint16_t seqNum, uint32_t payloadType, uint32_t ssrc,
            const RtpHeaderExtensionInfo& extensionInfo) = 0;
    virtual void OnNumReceivedPacket(uint32_t nNumRtpPacket) = 0;
};

/*!
 * @class        IRtcpDecoderListener
 */
class IRtcpDecoderListener
{
public:
    IRtcpDecoderListener() {}
    virtual ~IRtcpDecoderListener() {}
    virtual void OnRtcpInd(tRtpSvc_IndicationFromStack eIndType, void* pMsg) = 0;
    virtual void OnNumReceivedPacket(uint32_t nNumRtcpSRPacket, uint32_t nNumRtcpRRPacket) = 0;
    virtual void OnEvent(uint32_t event, uint32_t param) = 0;
};

/*!
 * @class        IRtpSession
 */
class IRtpSession : public RtpServiceListener
{
public:
    static IRtpSession* GetInstance(
            ImsMediaType type, const RtpAddress& localAddress, const RtpAddress& peerAddress);
    static void ReleaseInstance(IRtpSession* pSession);
    IRtpSession(
            ImsMediaType subtype, const RtpAddress& localAddress, const RtpAddress& peerAddress);
    virtual ~IRtpSession();
    bool operator==(const IRtpSession& obj2);
    bool isSameInstance(
            ImsMediaType subtype, const RtpAddress& localAddress, const RtpAddress& peerAddress);
    void SetRtpEncoderListener(IRtpEncoderListener* pRtpEncoderListener);
    void SetRtpDecoderListener(IRtpDecoderListener* pRtpDecoderListener);
    void SetRtcpEncoderListener(IRtcpEncoderListener* pRtcpEncoderListener);
    void SetRtcpDecoderListener(IRtcpDecoderListener* pRtcpDecoderListener);
    void SetRtpPayloadParam(int32_t payloadNumTx, int32_t payloadNumRx, int32_t samplingRate,
            int32_t subTxPayloadTypeNum = 0, int32_t subRxPayloadTypeNum = 0,
            int32_t subSamplingRate = 0);
    void SetRtcpInterval(int32_t nInterval);
    void StartRtp(bool bResetSsrc = false);
    void StopRtp();
    void StartRtcp(bool bSendRtcpBye = false);
    void StopRtcp();
    bool SendRtpPacket(uint32_t payloadType, uint8_t* data, uint32_t dataSize, uint32_t timestamp,
            bool mark, uint32_t nTimeDiff, RtpHeaderExtensionInfo* extensionInfo = nullptr);
    bool ProcRtpPacket(uint8_t* pData, uint32_t nDataSize);
    bool ProcRtcpPacket(uint8_t* pData, uint32_t nDataSize);
    // Sends RTP and RTCP statistics to listeners.
    void OnRtpStatsTimerExpired();
    void SendRtcpXr(uint8_t* pPayload, uint32_t nSize);
    bool SendRtcpFeedback(int32_t type, uint8_t* pFic, uint32_t nFicSize);
    void SetRtpContext(uint32_t ssrc, uint32_t timestamp, uint16_t sequenceNumber);
    void GetRtpContext(uint32_t& ssrc, uint32_t& timestamp, uint16_t& sequenceNumber);
    // receive Rtp packet, send it to rtp tx node
    virtual int OnRtpPacket(unsigned char* pData, RtpSvc_Length wLen);
    // receive Rtcp packet, send it to rtcp node
    virtual int OnRtcpPacket(unsigned char* pData, RtpSvc_Length wLen);
    // indication from the RtpStack
    virtual void OnPeerInd(tRtpSvc_IndicationFromStack eIndType, void* pMsg);
    // indication from the RtpStack
    virtual void OnPeerRtcpComponents(void* nMsg);

private:
    static std::list<IRtpSession*> mListRtpSession;
    ImsMediaType mMediaType;
    RTPSESSIONID mRtpSessionId;
    std::atomic<int32_t> mRefCount;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    // Listener
    IRtpEncoderListener* mRtpEncoderListener;
    IRtpDecoderListener* mRtpDecoderListener;
    IRtcpEncoderListener* mRtcpEncoderListener;
    IRtcpDecoderListener* mRtcpDecoderListener;
    // payload parameter
    tRtpSvc_SetPayloadParam mPayloadParam[RTP_MAX_PAYLOAD_TYPE];
    uint32_t mNumPayloadParam;
    // Rtp configure
    uint32_t mLocalRtpSsrc;
    uint32_t mPeerRtpSsrc;
    bool mEnableRtcpTx;
    bool mEnableDTMF;
    uint32_t mRtpDtmfPayloadType;
    // internal use
    uint32_t mPrevTimestamp;
    uint32_t mRtpStarted;
    uint32_t mRtcpStarted;
    uint32_t mNumRtpProcPacket;   // received packet
    uint32_t mNumRtcpProcPacket;  // received packet
    uint32_t mNumRtpPacket;       // received packet
    uint32_t mNumSRPacket;        // received packet
    uint32_t mNumRRPacket;        // received packet
    uint32_t mNumRtpDataToSend;
    uint32_t mNumRtpPacketSent;
    uint32_t mNumRtcpPacketSent;
    int32_t mRttd;
    std::mutex mutexDecoder;
    std::mutex mutexEncoder;

    ImsMediaType getMediaType();
    void increaseRefCounter();
    void decreaseRefCounter();
    uint32_t getRefCounter();
    void addPayloadType(RtpDt_UInt32 payloadType, RtpDt_UInt32 samplingRate);
};

#endif

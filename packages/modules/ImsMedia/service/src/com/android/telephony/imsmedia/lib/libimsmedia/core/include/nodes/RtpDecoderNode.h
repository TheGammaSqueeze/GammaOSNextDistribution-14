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

#ifndef RTP_DECODER_NODE_H
#define RTP_DECODER_NODE_H

#include <BaseNode.h>
#include <IRtpSession.h>
#include <RtpHeaderExtension.h>

/**
 * @brief This class is to depacketize the rtp packet and acquires sequence number, ssrc, timestamp,
 * mark flag from the rtp packet header by interfacing with the RtpStack module. This module can
 * simulate the packet loss, delay, duplicate and mixed order to check the jitter buffer handles the
 * variouse cases caused by the network condition.
 */
class RtpDecoderNode : public BaseNode, public IRtpDecoderListener
{
public:
    RtpDecoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~RtpDecoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t timestamp, bool mark, uint32_t nSeqNum, ImsMediaSubType nDataType,
            uint32_t arrivalTime = 0);
    virtual void OnMediaDataInd(unsigned char* data, uint32_t dataSize, uint32_t timestamp,
            bool mark, uint16_t seqNum, uint32_t payloadType, uint32_t ssrc,
            const RtpHeaderExtensionInfo& extensionInfo);
    // IRtpDecoderListener
    virtual void OnNumReceivedPacket(uint32_t nNumRtpPacket);

    /**
     * @brief Set the local ip address and port number
     */
    void SetLocalAddress(const RtpAddress& address);

    /**
     * @brief Set the peer ip address and port number
     */
    void SetPeerAddress(const RtpAddress& address);

    /**
     * @brief Set the inactivity timer in second unit
     */
    void SetInactivityTimerSec(const uint32_t time);

private:
    void processDtmf(uint8_t* data);
    std::list<RtpHeaderExtension>* DecodeRtpHeaderExtension(
            const RtpHeaderExtensionInfo& extensionInfo);

    IRtpSession* mRtpSession;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    int8_t mSamplingRate;
    int8_t mRtpPayloadTx;
    int8_t mRtpPayloadRx;
    int8_t mRtpTxDtmfPayload;
    int8_t mRtpRxDtmfPayload;
    int8_t mDtmfSamplingRate;
    int32_t mCvoValue;
    uint32_t mReceivingSSRC;
    uint32_t mInactivityTime;
    uint32_t mNoRtpTime;
    int8_t mRedundantPayload;
    uint32_t mArrivalTime;
    ImsMediaSubType mSubtype;
    bool mDtmfEndBit;
    uint64_t mPacketTimestamp;
#if defined(SIMULATION_LOSS) || defined(SIMULATION_DUPLICATE) || defined(SIMULATION_SSRC_CHANGE)
    uint32_t mPacketCounter;
#endif
#ifdef SIMULATION_DELAY
    uint32_t mNextTime;
#endif
#ifdef SIMULATION_REORDER
    ImsMediaDataQueue jitterData;
    uint32_t mReorderDataCount;
#endif
#ifdef SIMULATION_SSRC_CHANGE
    uint32_t mTestSsrc;
    uint16_t mTestSeq;
#endif
};

#endif

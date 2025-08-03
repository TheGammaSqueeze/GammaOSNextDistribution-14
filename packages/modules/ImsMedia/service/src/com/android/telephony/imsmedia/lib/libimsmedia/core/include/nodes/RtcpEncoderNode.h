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

#ifndef RTCPENCODERNODE_H_INCLUDED
#define RTCPENCODERNODE_H_INCLUDED

#include <BaseNode.h>
#include <IRtpSession.h>
#include <ImsMediaTimer.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaBitWriter.h>
#include <mutex>

#define BLOCK_LENGTH_STATISTICS   40
#define BLOCK_LENGTH_VOIP_METRICS 36

class RtcpEncoderNode : public BaseNode, public IRtcpEncoderListener
{
public:
    RtcpEncoderNode(BaseSessionCallback* callback = nullptr);
    ~RtcpEncoderNode();
    static void OnTimer(hTimerHandler hTimer, void* pUserData);
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnRtcpPacket(unsigned char* pData, uint32_t wLen);

    /**
     * @brief The methods operates when the timer is expired
     */
    virtual void ProcessTimer();

    /**
     * @brief Set the local ip address and port number
     */
    void SetLocalAddress(const RtpAddress& address);

    /**
     * @brief Set the peer ip address and port number
     */
    void SetPeerAddress(const RtpAddress& address);

    /**
     * @brief Creates NACK payload and request RtpStack to send it
     *
     * @param param The parameters to packetize the payload
     */
    bool SendNack(NackParams* param);

    /**
     * @brief Create PLI/FIR payload and request RtpStack to send it
     *
     * @param type The type of PLI or FIR
     */
    bool SendPictureLost(const uint32_t type);

    /**
     * @brief Create TMMBR/TMMBN payload and request RtpStack to send it
     *
     * @param type The type of TMMBR or TMMBN
     * @param param The parameters to packetize the payload
     */
    bool SendTmmbrn(const uint32_t type, TmmbrParams* param);

    /**
     * @brief Send Rtcp-Xr payload to the RtpStack to add the rtp header to send it to the network
     *
     * @param data The payload of the rtcp-xr report blocks
     * @param size The size of payload
     */
    bool SendRtcpXr(uint8_t* data, uint32_t size);

private:
    IRtpSession* mRtpSession;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    uint32_t mRtcpInterval;
    uint8_t* mRtcpXrPayload;
    bool mEnableRtcpBye;
    uint32_t mRtcpXrBlockTypes;
    int32_t mRtcpXrCounter;
    int32_t mRtcpFbTypes;
    hTimerHandler mTimer;
    std::mutex mMutexTimer;
    uint32_t mLastTimeSentPli;
    uint32_t mLastTimeSentFir;
    ImsMediaBitWriter mBitWriter;
};

#endif  // RTCPENCODERNODE_H_INCLUDED

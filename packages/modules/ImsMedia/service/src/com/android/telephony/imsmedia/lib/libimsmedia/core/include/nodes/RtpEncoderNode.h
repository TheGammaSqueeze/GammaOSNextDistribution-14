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

#ifndef RTP_ENCODER_NODE_H
#define RTP_ENCODER_NODE_H

#include <BaseNode.h>
#include <IRtpSession.h>
#include <RtpContextParams.h>
#include <RtpHeaderExtension.h>
#include <mutex>

class RtpEncoderNode : public BaseNode, public IRtpEncoderListener
{
public:
    RtpEncoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~RtpEncoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data, uint32_t size,
            uint32_t timestamp, bool mark, uint32_t seq,
            ImsMediaSubType dataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    // IRtpEncoderListener method
    virtual void OnRtpPacket(unsigned char* data, uint32_t nSize);

    /**
     * @brief Set the local ip address and port number
     */
    void SetLocalAddress(const RtpAddress& address);

    /**
     * @brief Set the peer ip address and port number
     */
    void SetPeerAddress(const RtpAddress& address);

    /**
     * @brief Set the camera facing and device orientation parameter for cvo extension in rtp header
     *
     * @param facing The facing of camera define in kCameraFacing in ImsMediaVideoUtil.h
     * @param orientation The orientation value to send in degree unit
     * @return true Return true when the extension data set properly
     * @return false Return false when the cvo configuration is disabled
     */
    bool SetCvoExtension(const int64_t facing, const int64_t orientation);

    /**
     * @brief Convert list of the RtpHeaderExtension to Rtp header extension payload
     */
    void SetRtpHeaderExtension(std::list<RtpHeaderExtension>* listExtension);

    /**
     * @brief Sets RTP stack with params such as SSRC, sequence number, timestamp, etc. RTP Packets
     * sent after this call will follow the new params set.
     *
     * @param rtpContextParams holds the rtp parameters such as ssrc, timestamp,
     * sequence number, etc.
     */
    void SetRtpContext(RtpContextParams& rtpContextParams);

    void GetRtpContext(RtpContextParams& rtpContextParams);

private:
    void ProcessAudioData(
            ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp);
    void ProcessVideoData(
            ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp, bool mark);
    void ProcessTextData(
            ImsMediaSubType subtype, uint8_t* data, uint32_t size, uint32_t timestamp, bool mark);

    IRtpSession* mRtpSession;
    std::mutex mMutex;
    RtpAddress mLocalAddress;
    RtpAddress mPeerAddress;
    bool mDtmfMode;
    bool mMark;
    uint32_t mPrevTimestamp;
    int8_t mSamplingRate;
    int8_t mRtpPayloadTx;
    int8_t mRtpPayloadRx;
    int8_t mRtpTxDtmfPayload;
    int8_t mRtpRxDtmfPayload;
    int8_t mDtmfSamplingRate;
    int32_t mDtmfTimestamp;
    int32_t mCvoValue;
    int8_t mRedundantPayload;
    int8_t mRedundantLevel;
    std::list<RtpHeaderExtensionInfo> mListRtpExtension;
    RtpContextParams mRtpContextParams;
    int32_t mArrivalTime;
};

#endif

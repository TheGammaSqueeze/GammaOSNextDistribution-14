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

#ifndef AUDIO_RTP_PAYLOAD_ENCODER_NODE_H
#define AUDIO_RTP_PAYLOAD_ENCODER_NODE_H

#include <BaseNode.h>
#include <ImsMediaBitWriter.h>

class AudioRtpPayloadEncoderNode : public BaseNode
{
public:
    AudioRtpPayloadEncoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~AudioRtpPayloadEncoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);

private:
    void EncodePayloadAmr(
            uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, uint32_t arrivalTime);
    void EncodePayloadEvs(
            uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, uint32_t arrivalTime);
    uint32_t CheckPaddingNecessity(uint32_t nTotalSize);

    int32_t mCodecType;
    bool mOctetAligned;
    int8_t mPtime;
    uint8_t mPayload[MAX_AUDIO_PAYLOAD_SIZE];
    bool mFirstFrame;
    uint32_t mTimestamp;
    uint32_t mMaxNumOfFrame;
    uint32_t mCurrNumOfFrame;
    uint32_t mCurrFramePos;
    uint32_t mTotalPayloadSize;
    ImsMediaBitWriter mBWHeader;
    ImsMediaBitWriter mBWPayload;
    kEvsBandwidth mEvsBandwidth;
    kEvsCodecMode mEvsCodecMode;
    int8_t mEvsOffset;
    int8_t mSendCMR;
    kEvsBitrate mEvsMode;
    int32_t mCoreEvsMode;
    kRtpPayloadHeaderMode mEvsPayloadHeaderMode;
};

#endif

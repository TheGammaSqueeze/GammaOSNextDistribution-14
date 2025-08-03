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

#ifndef TEXT_RTP_PAYLOAD_ENCODER_NODE_H
#define TEXT_RTP_PAYLOAD_ENCODER_NODE_H

#include <BaseNode.h>
#include <ImsMediaBitWriter.h>
#include <ImsMediaDataQueue.h>

class TextRtpPayloadEncoderNode : public BaseNode
{
public:
    TextRtpPayloadEncoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~TextRtpPayloadEncoderNode();
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
    void EncodeT140(uint8_t* data, uint32_t size, uint32_t timestamp, bool bMark);

    int32_t mCodecType;
    int8_t mRedundantPayload;
    int8_t mRedundantLevel;
    bool mKeepRedundantLevel;
    uint8_t mPayload[MAX_RTT_LEN];
    uint32_t mLastTimestampSent;
    uint32_t mLastMarkedTime;
    ImsMediaBitWriter mBWHeader;
    ImsMediaBitWriter mBWPayload;
    ImsMediaDataQueue mBufferQueue;
};

#endif

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

#ifndef TEXT_RTP_PAYLOAD_DECODER_NODE_H
#define TEXT_RTP_PAYLOAD_DECODER_NODE_H

#include <BaseNode.h>
#include <ImsMediaBitReader.h>
#include <ImsMediaDataQueue.h>

class TextRtpPayloadDecoderNode : public BaseNode
{
public:
    TextRtpPayloadDecoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~TextRtpPayloadDecoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data, uint32_t size,
            uint32_t timestamp, bool mark, uint32_t seqNum,
            ImsMediaSubType dataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);

private:
    void DecodeT140(uint8_t* data, uint32_t size, ImsMediaSubType subtype, uint32_t timestamp,
            bool mark, uint32_t nSeqNum);

    int32_t mCodecType;
    uint8_t mPayload[MAX_RTT_LEN];
    ImsMediaBitReader mBitReader;
};

#endif

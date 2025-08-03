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

#ifndef VIDEO_RTPPAYLOAD_DECODER_NODE_H_INCLUDED
#define VIDEO_RTPPAYLOAD_DECODER_NODE_H_INCLUDED

#include <ImsMediaDefine.h>
#include <BaseNode.h>

class VideoRtpPayloadDecoderNode : public BaseNode
{
public:
    VideoRtpPayloadDecoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~VideoRtpPayloadDecoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = MEDIASUBTYPE_UNDEFINED, uint32_t arrivalTime = 0);

private:
    void DecodeAvc(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp,
            bool bMark, uint32_t nSeqNum, uint32_t arrivalTime);
    void DecodeHevc(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum, uint32_t arrivalTime);

    uint32_t mCodecType;
    uint32_t mPayloadMode;
    uint8_t* mBuffer;
    uint8_t mSbitfirstByte;
};

#endif  // VIDEO_RTPPAYLOAD_DECODER_NODE_H_INCLUDED

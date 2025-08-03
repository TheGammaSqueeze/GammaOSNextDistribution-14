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

#ifndef JITTER_BUFFER_CONTROL_NODE_H
#define JITTER_BUFFER_CONTROL_NODE_H

#include <BaseJitterBuffer.h>
#include <BaseNode.h>

class JitterBufferControlNode : public BaseNode
{
public:
    JitterBufferControlNode(
            BaseSessionCallback* callback = nullptr, ImsMediaType type = IMS_MEDIA_AUDIO);
    virtual ~JitterBufferControlNode();
    void SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax);
    void Reset();
    virtual uint32_t GetDataCount();
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t timestamp, bool mark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);
    virtual bool GetData(ImsMediaSubType* psubtype, uint8_t** ppData, uint32_t* pnDataSize,
            uint32_t* ptimestamp, bool* pmark, uint32_t* pnSeqNum,
            ImsMediaSubType* pnDataType = nullptr, uint32_t* arrivalTime = nullptr);
    virtual bool GetRedundantFrame(uint32_t lostSeq, uint8_t** ppData = nullptr,
            uint32_t* pnDataSize = nullptr, bool* hasNextFrame = nullptr,
            uint8_t* nextFrameFirstByte = nullptr);
    virtual void DeleteData();

private:
    JitterBufferControlNode(const JitterBufferControlNode& objRHS);
    JitterBufferControlNode& operator=(const JitterBufferControlNode& objRHS);

protected:
    BaseJitterBuffer* mJitterBuffer;
    ImsMediaType mMediaType;
};

#endif
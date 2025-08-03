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

#include <JitterBufferControlNode.h>
#include <AudioJitterBuffer.h>
#include <VideoJitterBuffer.h>
#include <TextJitterBuffer.h>
#include <ImsMediaTimer.h>

JitterBufferControlNode::JitterBufferControlNode(BaseSessionCallback* callback, ImsMediaType type) :
        BaseNode(callback),
        mMediaType(type)
{
    if (mMediaType == IMS_MEDIA_AUDIO)
    {
        mJitterBuffer = new AudioJitterBuffer();
    }
    else if (mMediaType == IMS_MEDIA_VIDEO)
    {
        mJitterBuffer = new VideoJitterBuffer();
    }
    else if (mMediaType == IMS_MEDIA_TEXT)
    {
        mJitterBuffer = new TextJitterBuffer();
    }

    mJitterBuffer->SetSessionCallback(mCallback);
}

JitterBufferControlNode::~JitterBufferControlNode()
{
    if (mJitterBuffer != nullptr)
    {
        delete mJitterBuffer;
    }
}

void JitterBufferControlNode::SetJitterBufferSize(uint32_t nInit, uint32_t nMin, uint32_t nMax)
{
    if (mJitterBuffer)
    {
        mJitterBuffer->SetJitterBufferSize(nInit, nMin, nMax);
    }
}

void JitterBufferControlNode::Reset()
{
    if (mJitterBuffer)
    {
        mJitterBuffer->ClearBuffer();
        mJitterBuffer->Reset();
    }
}

uint32_t JitterBufferControlNode::GetDataCount()
{
    if (mJitterBuffer)
    {
        return mJitterBuffer->GetCount();
    }

    return 0;
}

void JitterBufferControlNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
        ImsMediaSubType nDataType, uint32_t arrivalTime)
{
    if (mJitterBuffer)
    {
        mJitterBuffer->Add(
                subtype, pData, nDataSize, nTimestamp, bMark, nSeqNum, nDataType, arrivalTime);
    }
}

bool JitterBufferControlNode::GetData(ImsMediaSubType* pSubtype, uint8_t** ppData,
        uint32_t* pnDataSize, uint32_t* pnTimestamp, bool* pbMark, uint32_t* pnSeqNum,
        ImsMediaSubType* pnDataType, uint32_t* arrivalTime)
{
    if (arrivalTime)
    {
        *arrivalTime = 0;
    }

    if (pnDataType)
    {
        *pnDataType = MEDIASUBTYPE_UNDEFINED;
    }

    if (mJitterBuffer)
    {
        return mJitterBuffer->Get(pSubtype, ppData, pnDataSize, pnTimestamp, pbMark, pnSeqNum,
                ImsMediaTimer::GetTimeInMilliSeconds(), pnDataType);
    }

    return false;
}

bool JitterBufferControlNode::GetRedundantFrame(uint32_t lostSeq, uint8_t** ppData,
        uint32_t* pnDataSize, bool* hasNextFrame, uint8_t* nextFrameFirstByte)
{
    if (mJitterBuffer)
    {
        return mJitterBuffer->GetRedundantFrame(
                lostSeq, ppData, pnDataSize, hasNextFrame, nextFrameFirstByte);
    }

    return false;
}

void JitterBufferControlNode::DeleteData()
{
    if (mJitterBuffer)
    {
        mJitterBuffer->Delete();
    }
}
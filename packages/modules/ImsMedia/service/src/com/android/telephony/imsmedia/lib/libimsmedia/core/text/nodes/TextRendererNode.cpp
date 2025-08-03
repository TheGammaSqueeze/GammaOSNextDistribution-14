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

#include <TextRendererNode.h>
#include <TextConfig.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>

/** Maximum waiting time when packet loss found */
#define TEXT_LOSS_MAX_WAITING_TIME (1000)

TextRendererNode::TextRendererNode(BaseSessionCallback* callback) :
        JitterBufferControlNode(callback, IMS_MEDIA_TEXT)
{
    mCodecType = TextConfig::TEXT_CODEC_NONE;
    mBOMReceived = false;
    mLastPlayedSeq = 0;
    mLossWaitTime = 0;
    mFirstFrameReceived = false;
}

TextRendererNode::~TextRendererNode() {}

kBaseNodeId TextRendererNode::GetNodeId()
{
    return kNodeIdTextRenderer;
}

ImsMediaResult TextRendererNode::Start()
{
    IMLOGD1("[Start] codec[%d]", mCodecType);

    if (mCodecType == TextConfig::TEXT_CODEC_NONE)
    {
        return RESULT_INVALID_PARAM;
    }

    mBOMReceived = false;
    mLastPlayedSeq = 0;
    mLossWaitTime = 0;
    mFirstFrameReceived = false;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void TextRendererNode::Stop()
{
    IMLOGD0("[Stop]");
    mNodeState = kNodeStateStopped;
}

bool TextRendererNode::IsRunTime()
{
    return false;
}

bool TextRendererNode::IsSourceNode()
{
    return false;
}

void TextRendererNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
    mCodecType = pConfig->getCodecType();
}

bool TextRendererNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    return (mCodecType == pConfig->getCodecType());
}

void TextRendererNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data, uint32_t size,
        uint32_t timestamp, bool mark, uint32_t seq, ImsMediaSubType dataType, uint32_t arrivalTime)
{
    if (subtype == MEDIASUBTYPE_REFRESHED)
    {
        mFirstFrameReceived = false;
        mBOMReceived = false;
        mLastPlayedSeq = 0;
        mLossWaitTime = 0;
    }

    JitterBufferControlNode::OnDataFromFrontNode(
            subtype, data, size, timestamp, mark, seq, dataType, arrivalTime);
}

void TextRendererNode::ProcessData()
{
    static const char* CHAR_REPLACEMENT = "\xEf\xbf\xbd";
    uint8_t* data;
    uint32_t size;
    uint32_t timestamp;
    uint32_t seq;
    bool mark;
    ImsMediaSubType subtype;
    ImsMediaSubType dataType;

    while (GetData(&subtype, &data, &size, &timestamp, &mark, &seq, &dataType))
    {
        IMLOGD_PACKET5(IM_PACKET_LOG_TEXT,
                "[ProcessData] size[%u], TS[%u], mark[%u], seq[%u], last seq[%u]", size, timestamp,
                mark, seq, mLastPlayedSeq);

        if (mFirstFrameReceived)
        {
            // detect lost packet
            uint16_t seqDiff = (uint16_t)seq - mLastPlayedSeq;

            if (seqDiff > 1)  // lost found
            {
                // Wait 1000 sec - RFC4103: 5.4 - Compensation for Packets Out of Order
                uint32_t nCurTime = ImsMediaTimer::GetTimeInMilliSeconds();

                if (mLossWaitTime == 0)
                {
                    mLossWaitTime = nCurTime;
                }

                if (nCurTime - mLossWaitTime <= TEXT_LOSS_MAX_WAITING_TIME)
                {
                    return;
                }

                int32_t lostCount = seqDiff - 1;
                IMLOGD1("[ProcessData] lostCount[%d]", lostCount);

                // Send a lost T140 as question mark
                for (int32_t nIndex = 1; nIndex <= lostCount; nIndex++)
                {
                    // send replacement character in case of lost packet detected
                    android::String8* text = new android::String8(CHAR_REPLACEMENT);
                    mCallback->SendEvent(
                            kImsMediaEventNotifyRttReceived, reinterpret_cast<uint64_t>(text), 0);

                    uint16_t lostSeq = mLastPlayedSeq + (uint16_t)nIndex;
                    IMLOGD_PACKET1(IM_PACKET_LOG_TEXT, "[ProcessData] LostSeq[%u]", lostSeq);
                }
            }

            mLossWaitTime = 0;  // reset loss wait
        }

        int32_t offset = size;
        uint8_t* dataPtr = data;

        // remove BOM from the string and send event
        while (offset > 0)
        {
            // remain last null data
            uint32_t transSize = 0;
            offset > MAX_RTT_LEN ? transSize = MAX_RTT_LEN : transSize = offset;

            if (mBOMReceived == false && offset >= 3 && dataPtr[0] == 0xef && dataPtr[1] == 0xbb &&
                    dataPtr[2] == 0xbf)
            {
                IMLOGD0("[ProcessData] got byte order mark");
                mBOMReceived = true;
                dataPtr += 3;
                transSize -= 3;
                offset -= 3;
            }

            // send event to notify to transfer rtt data received
            if (transSize > 0)
            {
                memset(mBuffer, 0, sizeof(mBuffer));
                memcpy(mBuffer, dataPtr, transSize);
                android::String8* text = new android::String8(mBuffer);
                mCallback->SendEvent(
                        kImsMediaEventNotifyRttReceived, reinterpret_cast<uint64_t>(text), 0);
                IMLOGD_PACKET2(
                        IM_PACKET_LOG_TEXT, "[ProcessData] size[%d] text[%s]", transSize, mBuffer);
            }

            dataPtr += transSize;
            offset -= transSize;
        }

        mFirstFrameReceived = true;
        mLastPlayedSeq = (uint16_t)seq;
        DeleteData();
    }
}
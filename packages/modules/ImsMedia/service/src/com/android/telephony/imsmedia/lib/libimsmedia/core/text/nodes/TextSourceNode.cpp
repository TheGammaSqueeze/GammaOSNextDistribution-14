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

#include <TextSourceNode.h>
#include <TextConfig.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>

#define RTT_MAX_CHAR_PER_BUFFERING (RTT_MAX_CHAR_PER_SEC / 3)

TextSourceNode::TextSourceNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = TextConfig::TEXT_CODEC_NONE;
    mRedundantLevel = 0;
    mRedundantCount = 0;
    mTimeLastSent = 0;
    mSentBOM = false;
}

TextSourceNode::~TextSourceNode() {}

kBaseNodeId TextSourceNode::GetNodeId()
{
    return kNodeIdTextSource;
}

ImsMediaResult TextSourceNode::Start()
{
    IMLOGD2("[Start] codec[%d], redundant level[%d]", mCodecType, mRedundantLevel);

    if (mCodecType == TextConfig::TEXT_CODEC_NONE)
    {
        return RESULT_INVALID_PARAM;
    }

    mRedundantCount = 0;
    mTimeLastSent = 0;
    mSentBOM = false;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void TextSourceNode::Stop()
{
    IMLOGD0("[Stop]");
    ClearDataQueue();
    mNodeState = kNodeStateStopped;
}

bool TextSourceNode::IsRunTime()
{
    return false;
}

bool TextSourceNode::IsSourceNode()
{
    return true;
}

void TextSourceNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
    mCodecType = pConfig->getCodecType();
    mRedundantLevel = pConfig->getRedundantLevel();
    mBitrate = pConfig->getBitrate();
}

bool TextSourceNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    return (mCodecType == pConfig->getCodecType() &&
            mRedundantLevel == pConfig->getRedundantLevel() && mBitrate == pConfig->getBitrate());
}

void TextSourceNode::ProcessData()
{
    // RFC 4103 recommended T.140 buffering time is 300ms
    if (mTimeLastSent != 0 &&
            ImsMediaTimer::GetTimeInMilliSeconds() - mTimeLastSent < T140_BUFFERING_TIME)
    {
        return;
    }

    std::lock_guard<std::mutex> guard(mMutex);

    if (GetDataCount() > 0 && !mSentBOM)
    {
        SendBom();
        mSentBOM = true;
        return;
    }

    ImsMediaSubType subtype = MEDIASUBTYPE_UNDEFINED;
    ImsMediaSubType datatype = MEDIASUBTYPE_UNDEFINED;
    uint8_t* data = NULL;
    uint32_t size = 0;
    uint32_t timestamp = 0;
    bool mark = false;
    uint32_t seq = 0;

    if (GetDataCount() > 0)
    {
        uint32_t stackedSize = 0;
        uint32_t numCharacter = 0;
        memset(mPayload, 0, sizeof(mPayload));

        while (GetData(&subtype, &data, &size, &timestamp, &mark, &seq, &datatype) &&
                (numCharacter < RTT_MAX_CHAR_PER_BUFFERING) &&
                (stackedSize + size < sizeof(mPayload)))
        {
            memcpy(mPayload + stackedSize, data, size);
            stackedSize += size;
            numCharacter++;
            DeleteData();
        }

        mTimeLastSent = ImsMediaTimer::GetTimeInMilliSeconds();
        IMLOGD_PACKET3(IM_PACKET_LOG_TEXT, "[ProcessData] sent length[%u], queue[%d], TS[%d]",
                stackedSize, mDataQueue.GetCount(), mTimeLastSent);
        SendDataToRearNode(
                MEDIASUBTYPE_BITSTREAM_T140, mPayload, stackedSize, mTimeLastSent, false, 0);
        mRedundantCount = mRedundantLevel + 1;
    }
    else if (mRedundantCount > 0)
    {
        /** RFC 4103. 5.2 : In the absence of new T.140 data for transmission, an empty T140block
         * should be sent after the selected buffering time, marking the start of an idle period
         */
        IMLOGD1("[ProcessData] send empty, redundant count[%d]", mRedundantCount);
        mTimeLastSent = ImsMediaTimer::GetTimeInMilliSeconds();
        // send default if there is no data to send
        SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, nullptr, 0, mTimeLastSent, false, 0);
        mRedundantCount--;
    }
}

void TextSourceNode::SendRtt(const android::String8* text)
{
    if (text == NULL || text->length() == 0)
    {
        IMLOGE0("[SendRtt] invalid data");
        return;
    }

    uint8_t* data = (uint8_t*)text->c_str();
    uint32_t size = text->length();
    uint32_t chunkSize = 0;

    while (size > 0)
    {
        // split with UTF-8
        if (data[0] >= 0xC2 && data[0] <= 0xDF && size >= 2)
        {
            chunkSize = 2;
        }
        else if (data[0] >= 0xE0 && data[0] <= 0xEF && size >= 3)
        {
            chunkSize = 3;
        }
        else if (data[0] >= 0xF0 && data[0] <= 0xFF && size >= 4)
        {
            chunkSize = 4;
        }
        else  // 1byte
        {
            chunkSize = 1;
        }

        {
            std::lock_guard<std::mutex> guard(mMutex);
            AddData(data, chunkSize, 0, false, 0);
            data += chunkSize;
            size -= chunkSize;
        }
    }

    IMLOGD2("[SendRtt] size[%u], queue[%d]", text->length(), mDataQueue.GetCount());
}

void TextSourceNode::SendBom()
{
    IMLOGD0("[ProcessData] send BOM");
    uint8_t bom[3] = {0xEF, 0xBB, 0xBF};

    mTimeLastSent = ImsMediaTimer::GetTimeInMilliSeconds();
    SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, bom, sizeof(bom), mTimeLastSent, false, 0);
    mRedundantCount = mRedundantLevel + 1;
}
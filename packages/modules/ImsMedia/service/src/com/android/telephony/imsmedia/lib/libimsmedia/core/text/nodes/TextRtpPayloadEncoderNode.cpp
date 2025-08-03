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

#include <TextRtpPayloadEncoderNode.h>
#include <TextConfig.h>
#include <ImsMediaTrace.h>

TextRtpPayloadEncoderNode::TextRtpPayloadEncoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = TextConfig::TEXT_CODEC_NONE;
    mLastTimestampSent = 0;
    mLastMarkedTime = 0;
}

TextRtpPayloadEncoderNode::~TextRtpPayloadEncoderNode()
{
    mBufferQueue.Clear();
}

kBaseNodeId TextRtpPayloadEncoderNode::GetNodeId()
{
    return kNodeIdTextPayloadEncoder;
}

ImsMediaResult TextRtpPayloadEncoderNode::Start()
{
    IMLOGD1("[Start] codec[%d]", mCodecType);

    if (mCodecType == TextConfig::TEXT_CODEC_NONE)
    {
        return RESULT_INVALID_PARAM;
    }

    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void TextRtpPayloadEncoderNode::Stop()
{
    IMLOGD0("[Stop]");
    mNodeState = kNodeStateStopped;
}

bool TextRtpPayloadEncoderNode::IsRunTime()
{
    return true;
}

bool TextRtpPayloadEncoderNode::IsSourceNode()
{
    return false;
}

void TextRtpPayloadEncoderNode::OnDataFromFrontNode(ImsMediaSubType /*subtype*/, uint8_t* data,
        uint32_t size, uint32_t timestamp, bool mark, uint32_t /*seq*/,
        ImsMediaSubType /*dataType*/, uint32_t /*arrivalTime*/)
{
    switch (mCodecType)
    {
        case TextConfig::TEXT_T140:
        case TextConfig::TEXT_T140_RED:
            EncodeT140(data, size, timestamp, mark);
            break;
        default:
            IMLOGE1("[ProcessData] invalid codec type[%d]", mCodecType);
            break;
    }
}

void TextRtpPayloadEncoderNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
    mCodecType = pConfig->getCodecType();
    mRedundantPayload = pConfig->getRedundantPayload();
    mRedundantLevel = pConfig->getRedundantLevel();
    mKeepRedundantLevel = pConfig->getKeepRedundantLevel();
}

bool TextRtpPayloadEncoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    return (mCodecType == pConfig->getCodecType() &&
            mRedundantPayload == pConfig->getRedundantPayload() &&
            mRedundantLevel == pConfig->getRedundantLevel() &&
            mKeepRedundantLevel == pConfig->getKeepRedundantLevel());
}

void TextRtpPayloadEncoderNode::EncodeT140(
        uint8_t* data, uint32_t size, uint32_t timestamp, bool mark)
{
    (void)mark;

    bool bNewMark = false;

    /** The RFC 4103 defines idle period as 300 ms or more of inactivity, and also requires
     * the RTP Marker bit to be set to one only in the first RTP packet sent after an idle period
     * (must be set to zero in any other RTP packet for sending text).*/

    if (size > 0 && timestamp > mLastTimestampSent + T140_BUFFERING_TIME)
    {
        bNewMark = true;  // Mark will be added only after Buffering time
    }

    uint32_t codecType = mCodecType;

    IMLOGD_PACKET6(IM_PACKET_LOG_PH,
            "[EncodeT140] Size[%u], Mark[%d], RedLevel[%d], TS[%u], LastTS[%u], queue size[%u]",
            size, bNewMark, mRedundantLevel, timestamp, mLastTimestampSent,
            mBufferQueue.GetCount());

    // check RFC4103 5.2 - send an empty T.140 at the beginning of idle period
    if (size == 0 && mBufferQueue.GetCount() == 0)
    {
        codecType = TextConfig::TEXT_T140;
    }

    // Queueing and sending a text data if it's T140 Redundancy mode
    if (codecType == TextConfig::TEXT_T140_RED)
    {
        // Remove overflowed data
        while (mBufferQueue.GetCount() > mRedundantLevel)
        {
            mBufferQueue.Delete();
        }

        // Remove very old redundant data
        DataEntry* pEntry = nullptr;

        while (mBufferQueue.Get(&pEntry) && pEntry != nullptr)
        {
            /** [RFC 4103] 10.1. Registration of MIME Media Type text/t140 Required parameters:
             * rate: The RTP timestamp clock rate, which is equal to the sampling rate.  The only
             * valid value is 1000*/
            uint32_t nTSInterval = timestamp - pEntry->nTimestamp;

            // Remove a very old redundant data
            IMLOGD_PACKET4(IM_PACKET_LOG_PH,
                    "[EncodeT140] timestamp[%u], pEntry->timestamp[%u], nTSInterval[%d], "
                    "arrivalTime[%u]",
                    timestamp, pEntry->nTimestamp, nTSInterval, pEntry->arrivalTime);

            // Check time interval and the number of remained use
            if (nTSInterval >= PAYLOADENCODER_TEXT_MAX_REDUNDANT_INTERVAL ||
                    pEntry->arrivalTime == 0)
            {
                pEntry = nullptr;
                mBufferQueue.Delete();
            }
            else
            {
                break;
            }
        }

        // Check non-redundant data is exist
        bool bRealData = false;

        for (uint32_t i = 0; i < mBufferQueue.GetCount(); i++)
        {
            if (mBufferQueue.GetAt(i, &pEntry) && pEntry != nullptr)
            {
                if (pEntry->nBufferSize > 0)
                {
                    IMLOGD_PACKET2(IM_PACKET_LOG_PH, "[EncodeT140] Found Data[%d/%d]", i,
                            mBufferQueue.GetCount());
                    bRealData = true;
                    break;
                }
            }
        }

        if (bNewMark == true)
        {
            if (bRealData == false)
            {
                // If there are redundant data only and mark is false (after IDLE period), clear
                // buffer
                mBufferQueue.Clear();
            }
            else
            {
                // If there are non-redundant (real) data and mark is true, set Mark False
                // (exception handling)
                IMLOGD0("[EncodeT140] reset marker");
                bNewMark = false;
            }
        }

        /** At the beginning of a TTY call, or after an idle period during a TTY call, when the
         * device needs to send new / non-redundant text data only, it shall send it using an RTP
         * packet that has a payload type (PT) of 111 (i.e., send in a T.140 RTP packet).*/
        if (mKeepRedundantLevel == true)
        {
            // add empty redundant payload
            uint32_t nTempRedCount = mBufferQueue.GetCount();
            for (uint32_t indexRed = 0; indexRed < mRedundantLevel - nTempRedCount; indexRed++)
            {
                DataEntry nullRED = DataEntry();
                nullRED.subtype = MEDIASUBTYPE_RTPPAYLOAD;
                nullRED.pbBuffer = nullptr;
                nullRED.nBufferSize = 0;
                nullRED.nTimestamp = timestamp;
                nullRED.arrivalTime = 1;  // Remained time to be retransmitted
                mBufferQueue.InsertAt(0, &nullRED);
                IMLOGD0("[EncodeT140] add null red");
            }
        }

        // Set Buffer
        memset(mPayload, 0, MAX_RTT_LEN);
        mBWHeader.SetBuffer(mPayload, MAX_RTT_LEN);
        mBWPayload.SetBuffer(mPayload, MAX_RTT_LEN);

        // Set a payload starting point
        if (mBufferQueue.GetCount() > 0)
        {
            mBWPayload.Seek(8 + mBufferQueue.GetCount() * 32);
        }

        // Write redundant header & data to payload
        pEntry = nullptr;

        for (uint32_t i = 0; i < mBufferQueue.GetCount(); i++)
        {
            if (mBufferQueue.GetAt(i, &pEntry) && pEntry != nullptr)
            {
                uint32_t nTSInterval = timestamp - pEntry->nTimestamp;
                mBWHeader.Write(1, 1);
                mBWHeader.Write(mRedundantPayload, 7);
                mBWHeader.Write(nTSInterval, 14);
                mBWHeader.Write(pEntry->nBufferSize, 10);
                pEntry->arrivalTime -= 1;

                IMLOGD_PACKET6(IM_PACKET_LOG_PH,
                        "[EncodeT140] RED payload [%d/%d] - RemaindTime[%d], RED payload[%d], "
                        "offset[%d], data size[%d]",
                        i, mBufferQueue.GetCount(), pEntry->arrivalTime, mRedundantPayload,
                        nTSInterval, pEntry->nBufferSize);

                if (pEntry->nBufferSize > 0 && pEntry->pbBuffer != nullptr)
                {
                    mBWPayload.WriteByteBuffer(pEntry->pbBuffer, pEntry->nBufferSize * 8);
                }
            }
        }

        // Write primary header & data
        {
            mBWHeader.Write(0, 1);
            mBWHeader.Write(mRedundantPayload, 7);
        }

        /** At the beginning of a TTY call, or after an idle period during a TTY call, when the
         * device needs to send new / non-redundant text data only, it shall send it using an RTP
         * packet that has a payload type (PT) of 111 (i.e., send in a T.140 RTP packet).*/

        if (size > 0 && data != nullptr)
        {
            mBWPayload.WriteByteBuffer(data, size * 8);
        }

        IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[EncodeT140] data size[%d], timestamp[%u], mark[%d]",
                mBWPayload.GetBufferSize(), timestamp, bNewMark);

        if (mBufferQueue.GetCount() > 0)
        {
            SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140_RED, mPayload,
                    mBWPayload.GetBufferSize(), timestamp, bNewMark, 0);
        }
        else
        {
            SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, mPayload, mBWPayload.GetBufferSize(),
                    timestamp, bNewMark, 0);
        }

        // Queueing a primary data
        DataEntry newEntry = DataEntry();
        newEntry.subtype = MEDIASUBTYPE_RTPPAYLOAD;
        newEntry.pbBuffer = data;
        newEntry.nBufferSize = size;
        newEntry.nTimestamp = timestamp;
        newEntry.arrivalTime = static_cast<uint32_t>(std::make_unsigned_t<int8_t>(
                mRedundantLevel));  // Remained time to be retransmitted
        mBufferQueue.Add(&newEntry);
    }
    else  // TEXT_T140
    {
        SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, data, size, timestamp, bNewMark, 0);
    }

    mLastTimestampSent = timestamp;
}
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

#include <TextRtpPayloadDecoderNode.h>
#include <TextConfig.h>
#include <ImsMediaTrace.h>
#include <list>

TextRtpPayloadDecoderNode::TextRtpPayloadDecoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = TextConfig::TEXT_CODEC_NONE;
}

TextRtpPayloadDecoderNode::~TextRtpPayloadDecoderNode() {}

kBaseNodeId TextRtpPayloadDecoderNode::GetNodeId()
{
    return kNodeIdTextPayloadDecoder;
}

ImsMediaResult TextRtpPayloadDecoderNode::Start()
{
    IMLOGD1("[Start] codec[%d]", mCodecType);

    if (mCodecType == TextConfig::TEXT_CODEC_NONE)
    {
        return RESULT_INVALID_PARAM;
    }

    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void TextRtpPayloadDecoderNode::Stop()
{
    IMLOGD0("[Stop]");
    mNodeState = kNodeStateStopped;
}

bool TextRtpPayloadDecoderNode::IsRunTime()
{
    return true;
}

bool TextRtpPayloadDecoderNode::IsSourceNode()
{
    return false;
}

void TextRtpPayloadDecoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* data,
        uint32_t size, uint32_t timestamp, bool mark, uint32_t seqNum, ImsMediaSubType dataType,
        uint32_t arrivalTime)
{
    (void)dataType;
    (void)arrivalTime;

    if (subtype == MEDIASUBTYPE_REFRESHED)
    {
        SendDataToRearNode(subtype, nullptr, size, 0, 0, 0, MEDIASUBTYPE_UNDEFINED);
        return;
    }

    switch (mCodecType)
    {
        case TextConfig::TEXT_T140:
        case TextConfig::TEXT_T140_RED:
            DecodeT140(data, size, subtype, timestamp, mark, seqNum);
            break;
        default:
            IMLOGE1("[OnDataFromFrontNode invalid codec type[%u]", mCodecType);
            break;
    }
}

void TextRtpPayloadDecoderNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);
    mCodecType = pConfig->getCodecType();
}

bool TextRtpPayloadDecoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    TextConfig* pConfig = reinterpret_cast<TextConfig*>(config);

    return (mCodecType == pConfig->getCodecType());
}

void TextRtpPayloadDecoderNode::DecodeT140(uint8_t* data, uint32_t size, ImsMediaSubType subtype,
        uint32_t timestamp, bool mark, uint32_t seq)
{
    IMLOGD_PACKET5(IM_PACKET_LOG_PH,
            "[DecodeT140] subtype[%u], size[%u], timestamp[%d], mark[%d], seq[%d]", subtype, size,
            timestamp, mark, seq);

    if (subtype == MEDIASUBTYPE_BITSTREAM_T140 || subtype == MEDIASUBTYPE_BITSTREAM_T140_RED)
    {
        std::list<uint32_t> listTimestampOffset;
        std::list<uint32_t> listLength;
        uint32_t readByte = 0;
        uint16_t redundantCount = 0;

        mBitReader.SetBuffer(data, size);

        /*
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |1|   T140 PT   |  timestamp offset of "R"  | "R" block length  |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        |0|   T140 PT   | "R" T.140 encoded redundant data              |
        +-+-+-+-+-+-+-+-+                               +---------------+
        |                                               |
        +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
        */

        // Primary Data Only
        if (subtype == MEDIASUBTYPE_BITSTREAM_T140 || size == 0)
        {
            SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, data, size, timestamp, mark, seq);
            return;
        }

        // Redundant data included
        while (mBitReader.Read(1) == 1)  // redundant flag bit
        {
            uint32_t payloadType = mBitReader.Read(7);  // T140 payload type
            uint32_t timestampOffset = mBitReader.Read(14);
            uint32_t length = mBitReader.Read(10);

            listTimestampOffset.push_back(timestampOffset);  // timestamp offset
            listLength.push_back(length);                    // block length

            IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[DecodeT140] PT[%u], TSOffset[%u], size[%u]",
                    payloadType, timestampOffset, length);
            readByte += 4;
            redundantCount++;
        }

        mBitReader.Read(7);  // T140 payload type (111)
        readByte += 1;

        // redundant data
        while (listTimestampOffset.size() > 0)
        {
            uint32_t redundantTimestamp = listTimestampOffset.front();
            uint32_t redundantLength = listLength.front();

            // read redundant payload
            mBitReader.ReadByteBuffer(mPayload, redundantLength * 8);
            readByte += redundantLength;

            uint16_t redundantSeqNum = seq - redundantCount;

            IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[DecodeT140] red TS[%u], size[%u], seq[%u]",
                    timestamp - redundantTimestamp, redundantLength, redundantSeqNum);
            SendDataToRearNode(MEDIASUBTYPE_BITSTREAM_T140, mPayload, redundantLength,
                    timestamp - redundantTimestamp, mark, redundantSeqNum);

            redundantCount--;
            listTimestampOffset.pop_front();
            listLength.pop_front();
        }

        // primary data
        if (size - readByte > 0)
        {
            mBitReader.ReadByteBuffer(mPayload, (size - readByte) * 8);
        }

        SendDataToRearNode(
                MEDIASUBTYPE_BITSTREAM_T140, mPayload, (size - readByte), timestamp, mark, seq);
    }
    else
    {
        IMLOGW1("[DecodeT140] INVALID media sub type[%u]", subtype);
    }
}

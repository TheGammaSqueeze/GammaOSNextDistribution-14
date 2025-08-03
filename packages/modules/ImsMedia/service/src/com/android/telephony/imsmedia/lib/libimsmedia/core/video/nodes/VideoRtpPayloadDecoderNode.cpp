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

#include <ImsMediaDefine.h>
#include <VideoRtpPayloadDecoderNode.h>
#include <ImsMediaVideoUtil.h>
#include <ImsMediaTrace.h>
#include <VideoConfig.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

using namespace android::telephony::imsmedia;

VideoRtpPayloadDecoderNode::VideoRtpPayloadDecoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = 0;
    mPayloadMode = 0;
    mBuffer = nullptr;
    mSbitfirstByte = 0;
}

VideoRtpPayloadDecoderNode::~VideoRtpPayloadDecoderNode() {}

kBaseNodeId VideoRtpPayloadDecoderNode::GetNodeId()
{
    return kNodeIdVideoPayloadDecoder;
}

ImsMediaResult VideoRtpPayloadDecoderNode::Start()
{
    IMLOGD2("[Start] Codec[%d], PayloadMode[%d]", mCodecType, mPayloadMode);

    mBuffer = reinterpret_cast<uint8_t*>(malloc(MAX_RTP_PAYLOAD_BUFFER_SIZE * sizeof(uint8_t)));

    if (mBuffer == nullptr)
    {
        return RESULT_NO_MEMORY;
    }

    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void VideoRtpPayloadDecoderNode::Stop()
{
    if (mBuffer != nullptr)
    {
        free(mBuffer);
        mBuffer = nullptr;
    }

    mNodeState = kNodeStateStopped;
}

bool VideoRtpPayloadDecoderNode::IsRunTime()
{
    return true;
}

bool VideoRtpPayloadDecoderNode::IsSourceNode()
{
    return false;
}

void VideoRtpPayloadDecoderNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    mCodecType = pConfig->getCodecType();
    mPayloadMode = pConfig->getPacketizationMode();
}

bool VideoRtpPayloadDecoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return false;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    return (mCodecType == pConfig->getCodecType() &&
            mPayloadMode == pConfig->getPacketizationMode());
}

void VideoRtpPayloadDecoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum,
        ImsMediaSubType nDataType, uint32_t arrivalTime)
{
    if (subtype == MEDIASUBTYPE_REFRESHED)
    {
        SendDataToRearNode(
                subtype, nullptr, nDataSize, 0, 0, 0, MEDIASUBTYPE_UNDEFINED, arrivalTime);
        return;
    }

    switch (mCodecType)
    {
        case VideoConfig::CODEC_AVC:
            DecodeAvc(subtype, pData, nDataSize, nTimeStamp, bMark, nSeqNum, arrivalTime);
            break;
        case VideoConfig::CODEC_HEVC:
            DecodeHevc(subtype, pData, nDataSize, nTimeStamp, bMark, nSeqNum, arrivalTime);
            break;
        default:
            IMLOGE1("[OnDataFromFrontNode] invalid codec type[%d]", mCodecType);
            SendDataToRearNode(MEDIASUBTYPE_UNDEFINED, pData, nDataSize, nTimeStamp, bMark, nSeqNum,
                    nDataType, arrivalTime);
            break;
    }
}

void VideoRtpPayloadDecoderNode::DecodeAvc(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum, uint32_t arrivalTime)
{
    if (pData == nullptr || nDataSize == 0 || mBuffer == nullptr)
    {
        return;
    }

    // check packet type
    uint8_t bPacketType = pData[0] & 0x1F;
    ImsMediaSubType eDataType = MEDIASUBTYPE_UNDEFINED;

    // make start code prefix
    mBuffer[0] = 0x00;
    mBuffer[1] = 0x00;
    mBuffer[2] = 0x00;
    mBuffer[3] = 0x01;

    IMLOGD_PACKET2(IM_PACKET_LOG_PH, "[DecodeAvc] [%02X] nDataSize[%d]", pData[0], nDataSize);

    if (bPacketType >= 1 && bPacketType <= 23)
    {  // Single NAL unit packet
        memcpy(mBuffer + 4, pData, nDataSize);

        if ((bPacketType & 0x1F) == 7 || (bPacketType & 0x1F) == 8)
        {
            eDataType = MEDIASUBTYPE_VIDEO_CONFIGSTRING;
        }
        else if ((bPacketType & 0x1F) == 5)
        {
            eDataType = MEDIASUBTYPE_VIDEO_IDR_FRAME;
        }
        else if ((bPacketType & 0x1F) == 6)
        {
            eDataType = MEDIASUBTYPE_VIDEO_SEI_FRAME;
        }
        else
        {
            eDataType = MEDIASUBTYPE_VIDEO_NON_IDR_FRAME;
        }

        SendDataToRearNode(subtype, mBuffer, nDataSize + 4, nTimeStamp, bMark, nSeqNum, eDataType,
                arrivalTime);
    }
    else if (bPacketType == 24)
    {  // STAP-A
        uint8_t* pCurrData = pData + 1;
        int32_t nRemainSize = (int32_t)(nDataSize - 1);

        if (mPayloadMode == kRtpPayloadHeaderModeSingleNalUnit)
        {
            IMLOGD_PACKET0(IM_PACKET_LOG_PH, "[DecodeAvc] Warning - single nal unit mode");
        }

        bPacketType = pCurrData[2] & 0x1F;

        if ((bPacketType & 0x1F) == 7 || (bPacketType & 0x1F) == 8)
        {
            eDataType = MEDIASUBTYPE_VIDEO_CONFIGSTRING;
        }
        else if ((bPacketType & 0x1F) == 5)
        {  // check idr frame
            eDataType = MEDIASUBTYPE_VIDEO_IDR_FRAME;
        }
        else
        {
            eDataType = MEDIASUBTYPE_VIDEO_NON_IDR_FRAME;
        }

        IMLOGD2("[DecodeAvc] eDataType[%u], nRemainSize[%u]", eDataType, nRemainSize);

        while (nRemainSize > 2)
        {
            // read NAL unit size
            uint32_t nNALUnitsize = pCurrData[0];
            nNALUnitsize = (nNALUnitsize << 8) + pCurrData[1];
            IMLOGD_PACKET1(IM_PACKET_LOG_PH, "[DecodeAvc] STAP-A nNALUnitsize[%d]", nNALUnitsize);
            pCurrData += 2;
            nRemainSize -= 2;
            // Read and Send NAL
            if (nRemainSize >= (int32_t)nNALUnitsize)
            {
                IMLOGD_PACKET1(IM_PACKET_LOG_PH, "[DecodeAvc] STAP-A [%02X] nNALUnitsize[%d]",
                        nNALUnitsize);
                memcpy(mBuffer + 4, pCurrData, nNALUnitsize);
                SendDataToRearNode(subtype, mBuffer, nNALUnitsize + 4, nTimeStamp, bMark, nSeqNum,
                        eDataType, arrivalTime);
            }
            pCurrData += nNALUnitsize;
            nRemainSize -= nNALUnitsize;
        }
    }
    else if (bPacketType == 28)
    {  // FU-A
        uint8_t bFUIndicator;
        uint8_t bFUHeader;
        uint8_t bNALUnitType;
        uint8_t bStartBit;
        uint8_t bEndBit;

        if (mPayloadMode == kRtpPayloadHeaderModeSingleNalUnit)
        {
            IMLOGW0("[DecodeAvc] Warning - (FU-A, 28) for single nal unit mode");
        }

        bFUIndicator = pData[0];
        bFUHeader = pData[1];
        bNALUnitType = (bFUIndicator & 0xE0) | (bFUHeader & 0x1F);
        bStartBit = (bFUHeader >> 7) & 0x01;
        bEndBit = (bFUHeader >> 6) & 0x01;

        if ((bNALUnitType & 0x1F) == 7 || (bNALUnitType & 0x1F) == 8)
        {
            eDataType = MEDIASUBTYPE_VIDEO_CONFIGSTRING;
        }
        else if ((bNALUnitType & 0x1F) == 5)
        {  // check idr frame
            eDataType = MEDIASUBTYPE_VIDEO_IDR_FRAME;
        }
        else
        {
            eDataType = MEDIASUBTYPE_VIDEO_NON_IDR_FRAME;
        }

        if (bStartBit)
        {
            mBuffer[4] = bNALUnitType;  // for video decoder
            memcpy(mBuffer + 5, pData + 2, nDataSize - 2);

            if (bEndBit)
            {
                SendDataToRearNode(subtype, mBuffer, nDataSize + 3, nTimeStamp, bMark, nSeqNum,
                        eDataType, arrivalTime);
            }
            else
            {
                SendDataToRearNode(subtype, mBuffer, nDataSize + 3, nTimeStamp, bEndBit, nSeqNum,
                        eDataType, arrivalTime);
            }
        }
        else
        {
            SendDataToRearNode(subtype, pData + 2, nDataSize - 2, nTimeStamp, bEndBit, nSeqNum,
                    eDataType, arrivalTime);
        }
    }
    else
    {
        IMLOGE1("[DecodeAvc] Unsupported payload type[%d]", bPacketType);
    }
}

void VideoRtpPayloadDecoderNode::DecodeHevc(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum, uint32_t arrivalTime)
{
    if (pData == nullptr || nDataSize == 0)
    {
        IMLOGE1("[DecodeHevc] INVALID Data, Size[%d]", nDataSize);
        return;
    }

    if (mBuffer == nullptr)
    {
        return;
    }

    // check packet type
    uint8_t bPacketType = (pData[0] & 0x7E) >> 1;
    ImsMediaSubType eDataType = MEDIASUBTYPE_UNDEFINED;

    // Please check Decoder Start Code...
    //  make start code prefix
    mBuffer[0] = 0x00;
    mBuffer[1] = 0x00;
    mBuffer[2] = 0x00;
    mBuffer[3] = 0x01;

    IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[DecodeHevc] [%02X %02X] nDataSize[%d]", pData[0], pData[1],
            nDataSize);

    if (bPacketType <= 40)
    {  // Single NAL unit packet
        memcpy(mBuffer + 4, pData, nDataSize);

        if (bPacketType >= 32 && bPacketType <= 34)
        {  // 32: VPS, 33: SPS, 34: PPS
            eDataType = MEDIASUBTYPE_VIDEO_CONFIGSTRING;
        }
        else if (bPacketType == 19 || bPacketType == 20)
        {  // IDR
            eDataType = MEDIASUBTYPE_VIDEO_IDR_FRAME;
        }
        else
        {
            eDataType = MEDIASUBTYPE_VIDEO_NON_IDR_FRAME;
        }

        SendDataToRearNode(subtype, mBuffer, nDataSize + 4, nTimeStamp, bMark, nSeqNum, eDataType,
                arrivalTime);
    }
    else if (bPacketType == 48)
    {  // Aggregation packet(AP)
       // need to implement
    }
    else if (bPacketType == 49)
    {  // FU-A
        uint8_t bFUIndicator1;
        uint8_t bFUIndicator2;
        uint8_t bFUHeader;
        uint8_t bNALUnitType;
        uint8_t bStartBit;
        uint8_t bEndBit;

        if (mPayloadMode == kRtpPayloadHeaderModeSingleNalUnit)
        {
            IMLOGW0("[DecodeHevc] Warning - invalid packet type(FU, 49) for single nal unit mode");
        }

        bFUIndicator1 = pData[0];
        bFUIndicator2 = pData[1];
        bFUHeader = pData[2];
        bNALUnitType = (bFUIndicator1 & 0x81) | ((bFUHeader & 0x3F) << 1);
        bStartBit = (bFUHeader >> 7) & 0x01;
        bEndBit = (bFUHeader >> 6) & 0x01;

        uint8_t frameType = (bNALUnitType & 0x7E) >> 1;

        if (frameType >= 32 && frameType <= 34)
        {  // 32: VPS, 33: SPS, 34: PPS
            eDataType = MEDIASUBTYPE_VIDEO_CONFIGSTRING;
        }
        else if (frameType == 19 || frameType == 20)
        {  // IDR
            eDataType = MEDIASUBTYPE_VIDEO_IDR_FRAME;
        }
        else
        {
            eDataType = MEDIASUBTYPE_VIDEO_NON_IDR_FRAME;
        }

        if (bStartBit)
        {
            mBuffer[4] = bNALUnitType;  // for decoder
            mBuffer[5] = bFUIndicator2;
            memcpy(mBuffer + 6, pData + 3, nDataSize - 3);
            // exclude FU header
            SendDataToRearNode(subtype, mBuffer, nDataSize + 3, nTimeStamp, bEndBit, nSeqNum,
                    eDataType, arrivalTime);
        }
        else
        {  // exclude start code
            SendDataToRearNode(subtype, pData + 3, nDataSize - 3, nTimeStamp, bEndBit, nSeqNum,
                    eDataType, arrivalTime);
        }
    }
    else
    {
        IMLOGE1("[DecodeHevc] Unsupported payload type[%d]", bPacketType);
    }
}

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
#include <VideoRtpPayloadEncoderNode.h>
#include <ImsMediaBinaryFormat.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <VideoConfig.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifdef DEBUG_JITTER_GEN_SIMULATION_REORDER
#define MEDIABUF_DATAPACKET_MAX 200
#else
#define MEDIABUF_DATAPACKET_MAX 1300
#endif

using namespace android::telephony::imsmedia;

VideoRtpPayloadEncoderNode::VideoRtpPayloadEncoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = VideoConfig::CODEC_AVC;
    mPayloadMode = kRtpPayloadHeaderModeNonInterleaved;
    mPrevMark = false;
    mBuffer = nullptr;
    memset(mVPS, 0, sizeof(mVPS));
    memset(mSPS, 0, sizeof(mSPS));
    memset(mPPS, 0, sizeof(mPPS));
    mSpsSize = 0;
    mPpsSize = 0;
    mVPSsize = 0;
    mMaxFragmentUnitSize = MEDIABUF_DATAPACKET_MAX;
}

VideoRtpPayloadEncoderNode::~VideoRtpPayloadEncoderNode() {}

kBaseNodeId VideoRtpPayloadEncoderNode::GetNodeId()
{
    return kNodeIdVideoPayloadEncoder;
}

ImsMediaResult VideoRtpPayloadEncoderNode::Start()
{
    mPrevMark = true;

    if (mMaxFragmentUnitSize == 0)
    {
        mMaxFragmentUnitSize = MEDIABUF_DATAPACKET_MAX;
    }

    IMLOGD3("[Start] codecType[%d], PayloadMode[%d], mtu[%d]", mCodecType, mPayloadMode,
            mMaxFragmentUnitSize);

    mBuffer = reinterpret_cast<uint8_t*>(malloc(MAX_RTP_PAYLOAD_BUFFER_SIZE * sizeof(uint8_t)));

    if (mBuffer == nullptr)
    {
        return RESULT_NO_MEMORY;
    }

    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void VideoRtpPayloadEncoderNode::Stop()
{
    if (mBuffer != nullptr)
    {
        free(mBuffer);
        mBuffer = nullptr;
    }

    mNodeState = kNodeStateStopped;
}

bool VideoRtpPayloadEncoderNode::IsRunTime()
{
    return true;
}

bool VideoRtpPayloadEncoderNode::IsSourceNode()
{
    return false;
}

void VideoRtpPayloadEncoderNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    mCodecType = pConfig->getCodecType();
    mPayloadMode = pConfig->getPacketizationMode();
    mMaxFragmentUnitSize = pConfig->getMaxMtuBytes();
}

bool VideoRtpPayloadEncoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return false;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    return (mCodecType == pConfig->getCodecType() &&
            mPayloadMode == pConfig->getPacketizationMode() &&
            mMaxFragmentUnitSize == pConfig->getMaxMtuBytes());
}

void VideoRtpPayloadEncoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
        ImsMediaSubType nDataType, uint32_t arrivalTime)
{
    (void)subtype;
    (void)nSeqNum;
    (void)nDataType;
    (void)arrivalTime;

    switch (mCodecType)
    {
        case VideoConfig::CODEC_AVC:
            EncodeAvc(pData, nDataSize, nTimestamp, bMark);
            break;
        case VideoConfig::CODEC_HEVC:
            EncodeHevc(pData, nDataSize, nTimestamp, bMark);
            break;
        default:
            IMLOGE1("[OnDataFromFrontNode] invalid codec type[%d]", mCodecType);
            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, pData, nDataSize, nTimestamp, bMark, 0);
            break;
    }
}

uint8_t* VideoRtpPayloadEncoderNode::FindAvcStartCode(
        uint8_t* pData, uint32_t nDataSize, uint32_t* pnSkipSize)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nSkipSize = 0;

    // remove leading zero bytes and start code prefix
    while (nDataSize >= 4)
    {
        // Start Code 00 00 00 01 case.
        if (pCurDataPos[0] == 0x00 && pCurDataPos[1] == 0x00 && pCurDataPos[2] == 0x00 &&
                pCurDataPos[3] == 0x01)
        {
            if (pnSkipSize)
                *pnSkipSize = nSkipSize;
            return pCurDataPos;
        }
        else
        {
            pCurDataPos += 1;
            nDataSize -= 1;
            nSkipSize += 1;
        }
    }

    if (pnSkipSize)
        *pnSkipSize = nSkipSize;
    return nullptr;
}

// [HEVC] return buffer position of h.265 start code
uint8_t* VideoRtpPayloadEncoderNode::FindHevcStartCode(
        uint8_t* pData, uint32_t nDataSize, uint32_t* pnSkipSize)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nSkipSize = 0;

    // remove leading zero bytes and start code prefix
    while (nDataSize >= 4)
    {
        // Start Code 00 00 00 01 case.
        if (pCurDataPos[0] == 0x00 && pCurDataPos[1] == 0x00 && pCurDataPos[2] == 0x00 &&
                pCurDataPos[3] == 0x01)
        {
            if (pnSkipSize)
                *pnSkipSize = nSkipSize;
            return pCurDataPos;
        }
        else
        {
            pCurDataPos += 1;
            nDataSize -= 1;
            nSkipSize += 1;
        }
    }

    if (pnSkipSize)
        *pnSkipSize = nSkipSize;
    return nullptr;
}

void VideoRtpPayloadEncoderNode::EncodeAvc(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, bool bMark)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nCurDataSize;
    uint32_t nSkipSize;
    uint8_t* pStartCodePos;
    uint8_t nNalUnitType;
    char spsEncoded[255];
    uint32_t spsEncodedSize = 0;
    char ppsEncoded[255];
    uint32_t ppsEncodedSize = 0;

    if (nDataSize > 5)
    {
        IMLOGD_PACKET8(IM_PACKET_LOG_PH,
                "[EncodeAvc] [%02X %02X %02X %02X %02X] nDataSize[%d], TS[%u], mark[%d]", pData[0],
                pData[1], pData[2], pData[3], pData[4], nDataSize, nTimestamp, bMark);
    }

    pStartCodePos = FindAvcStartCode(pCurDataPos, nDataSize, &nSkipSize);

    if (pStartCodePos == nullptr)
    {
        return;
    }

    // remove padding
    pCurDataPos = pStartCodePos + 4;
    nDataSize -= (nSkipSize + 4);
    nNalUnitType = pCurDataPos[0] & 0x1F;

    while (nNalUnitType == 7 || nNalUnitType == 8)  // config frame
    {
        // extract nal unit
        pStartCodePos = FindAvcStartCode(pCurDataPos + 1, nDataSize - 1);

        if (pStartCodePos == nullptr)
        {
            nCurDataSize = nDataSize;
        }
        else
        {
            nCurDataSize = pStartCodePos - pCurDataPos;
        }

        if (nNalUnitType == 7)
        {
            memset(mSPS, 0, MAX_CONFIG_LEN);
            memcpy(mSPS, pCurDataPos, nCurDataSize);
            mSpsSize = nCurDataSize;
            ImsMediaBinaryFormat::BinaryToBase00(
                    spsEncoded, spsEncodedSize, mSPS, mSpsSize, BINARY_FORMAT_BASE64);
            IMLOGD2("[EncodeAvc] save sps size[%d], data : %s", mSpsSize, spsEncoded);
        }
        else if (nNalUnitType == 8)
        {
            memset(mPPS, 0, MAX_CONFIG_LEN);
            memcpy(mPPS, pCurDataPos, nCurDataSize);
            mPpsSize = nCurDataSize;

            ImsMediaBinaryFormat::BinaryToBase00(
                    ppsEncoded, ppsEncodedSize, mPPS, mPpsSize, BINARY_FORMAT_BASE64);
            IMLOGD2("[EncodeAvc] save pps, size[%d], data : %s", mPpsSize, ppsEncoded);
        }

        if (nDataSize < nCurDataSize + 4)
        {
            return;
        }

        nDataSize -= (nCurDataSize + 4);
        pCurDataPos += (nCurDataSize + 4);
        nNalUnitType = pCurDataPos[0] & 0x1F;
    }

    if (nNalUnitType == 5)  // check idf frame, send sps/pps
    {
        // sps
        EncodeAvcNALUnit(mSPS, mSpsSize, nTimestamp, 1, 7);
        // pps
        EncodeAvcNALUnit(mPPS, mPpsSize, nTimestamp, 1, 8);
        IMLOGD0("[EncodeAvc] Send SPS, PPS when an I frame send");
    }

    if (nDataSize > 0)
    {
        EncodeAvcNALUnit(pCurDataPos, nDataSize, nTimestamp, bMark, nNalUnitType);
    }
}

void VideoRtpPayloadEncoderNode::EncodeAvcNALUnit(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nNalUnitType)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nMtu = mMaxFragmentUnitSize * 0.9;

    if (nDataSize > 5)
    {
        IMLOGD_PACKET8(IM_PACKET_LOG_PH,
                "[EncodeAvcNALUnit] [%02X%02X%02X%02X] size[%d], TS[%u], mark[%d], nalType[%d]",
                pData[0], pData[1], pData[2], pData[3], nDataSize, nTimestamp, bMark, nNalUnitType);
    }

    if (nDataSize > MAX_RTP_PAYLOAD_BUFFER_SIZE)
    {
        IMLOGE1("[EncodeAvcNALUnit] nDataSize[%d]", nDataSize);
        return;
    }

    if (mBuffer == nullptr)
    {
        return;
    }

    // make FU-A packets
    if (mPayloadMode == kRtpPayloadHeaderModeNonInterleaved && nDataSize > nMtu)
    {
        uint32_t nRemainSize = nDataSize;
        uint32_t nSendDataSize;
        bool bFirstPacket = true;
        bool bMbit;
        uint8_t bFUIndicator;
        uint8_t bFUHeader;
        uint8_t bNALUnitType;

        // Make FU indicator and save NAL unit type
        bFUIndicator = pCurDataPos[0] & 0xE0;  // copy F and NRI from NAL unit header
        bFUIndicator += 28;                    // Type is 28(FU-A)
        bNALUnitType = pCurDataPos[0] & 0x1F;  // copy NAL unit type from NAL unit header

        while (nRemainSize)
        {
            // set bMbit, nSendDataSize, bFUHeader
            if (nRemainSize <= nMtu)
            {
                bMbit = bMark;                    // RTP marker bit
                bFUHeader = 0x40 + bNALUnitType;  // set E bit
                nSendDataSize = nRemainSize;
            }
            else
            {
                bMbit = false;  // RTP marker bit

                if (bFirstPacket)
                    bFUHeader = 0x80 + bNALUnitType;  // set S bit
                else
                    bFUHeader = bNALUnitType;
                if (nRemainSize >= nMtu * 2)
                {
                    nSendDataSize = nMtu;
                }
                else
                {
                    nSendDataSize = nRemainSize / 2;
                }
            }

            /** pData is the buffer of front node (not mine) bacause this method is called in
             * OnDataFromFrontNode method. Then, to modify it, we have to copy it to
             * another buffer */
            if (bFirstPacket)
            {
                if (nSendDataSize <= MAX_RTP_PAYLOAD_BUFFER_SIZE - 1)
                {
                    memcpy(mBuffer + 1, pCurDataPos, nSendDataSize);
                }
                else
                {
                    IMLOGE1("[EncodeAvcNALUnit] memcpy Error!! - nDataSize[%d]", nSendDataSize);
                    return;
                }

                pCurDataPos += nSendDataSize;
                nRemainSize -= nSendDataSize;

                nSendDataSize += 1;
                bFirstPacket = false;
            }
            else
            {
                if (nSendDataSize <= MAX_RTP_PAYLOAD_BUFFER_SIZE - 2)
                {
                    memcpy(mBuffer + 2, pCurDataPos, nSendDataSize);
                }
                else
                {
                    IMLOGE1("[EncodeAvcNALUnit] memcpy Error!! - nDataSize[%d]", nSendDataSize);
                    return;
                }

                pCurDataPos += nSendDataSize;
                nRemainSize -= nSendDataSize;
                nSendDataSize += 2;
            }

            mBuffer[0] = bFUIndicator;
            mBuffer[1] = bFUHeader;

            // Insert CVO extension when the last packet of IDR frame
            if (nNalUnitType == 5)
            {
                SendDataToRearNode(
                        MEDIASUBTYPE_VIDEO_IDR_FRAME, mBuffer, nSendDataSize, nTimestamp, bMbit, 0);
            }
            else
            {
                SendDataToRearNode(
                        MEDIASUBTYPE_RTPPAYLOAD, mBuffer, nSendDataSize, nTimestamp, bMbit, 0);
            }
        }
    }
    else
    {
        IMLOGD_PACKET4(IM_PACKET_LOG_PH,
                "[EncodeAvcNALUnit] [%02X] nDataSize[%d], TS[%u], mark[%d]", pCurDataPos[0],
                nDataSize, nTimestamp, bMark);
        // Insert CVO extension when the last packet of IDR frame
        if (nNalUnitType == 5)
        {
            SendDataToRearNode(
                    MEDIASUBTYPE_VIDEO_IDR_FRAME, pCurDataPos, nDataSize, nTimestamp, bMark, 0);
        }
        else
        {
            SendDataToRearNode(
                    MEDIASUBTYPE_RTPPAYLOAD, pCurDataPos, nDataSize, nTimestamp, bMark, 0);
        }
    }
}

void VideoRtpPayloadEncoderNode::EncodeHevc(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, bool bMark)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nSkipSize;
    uint8_t* pStartCodePos;
    uint8_t nNalUnitType;

    if (nDataSize > 6)
    {
        IMLOGD_PACKET6(IM_PACKET_LOG_PH, "[EncodeHevc] [%02X %02X %02X %02X %02X %02X]", pData[0],
                pData[1], pData[2], pData[3], pData[4], pData[5]);
        IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[EncodeHevc] nDataSize[%d], TS[%u], mark[%d]", nDataSize,
                nTimestamp, bMark);
    }

    pStartCodePos = FindHevcStartCode(pCurDataPos, nDataSize, &nSkipSize);

    if (pStartCodePos == nullptr)
    {
        return;
    }

    pCurDataPos = pStartCodePos + 4;
    nDataSize -= (nSkipSize + 4);
    nNalUnitType = (pCurDataPos[0] >> 1) & 0x3F;

    // 32: VPS, 33: SPS, 34: PPS
    while (nNalUnitType == 32 || nNalUnitType == 33 || nNalUnitType == 34)
    {
        // extract nal unit
        // NAL unit header is 2 bytes on HEVC.
        pStartCodePos = FindHevcStartCode(pCurDataPos + 2, nDataSize - 2);

        if (pStartCodePos == nullptr)
        {
            break;
        }

        uint32_t nCurDataSize = pStartCodePos - pCurDataPos;
        EncodeHevcNALUnit(pCurDataPos, nCurDataSize, nTimestamp, bMark, nNalUnitType);

        if (nNalUnitType == 32)
        {
            memset(mVPS, 0, MAX_CONFIG_LEN);
            memcpy(mVPS, pCurDataPos, nCurDataSize);
            mVPSsize = nCurDataSize;
            IMLOGD1("[EncodeHevc] VPS Size [%d]", mVPSsize);
        }
        else if (nNalUnitType == 33)
        {
            memset(mSPS, 0, MAX_CONFIG_LEN);
            memcpy(mSPS, pCurDataPos, nCurDataSize);
            mSpsSize = nCurDataSize;
            IMLOGD1("[EncodeHevc] SPS Size [%d]", mSpsSize);
        }
        else if (nNalUnitType == 34)
        {
            memset(mPPS, 0, MAX_CONFIG_LEN);
            memcpy(mPPS, pCurDataPos, nCurDataSize);
            mPpsSize = nCurDataSize;
            IMLOGD1("[EncodeHevc] PPS Size [%d]", mPpsSize);
        }

        if (nDataSize < nCurDataSize + 4)
        {
            IMLOGE0("[EncodeHevc] error - extract nal unit!!!");
            return;
        }

        // exclude start code
        nDataSize -= (nCurDataSize + 4);
        pCurDataPos += (nCurDataSize + 4);
        nNalUnitType = (pCurDataPos[0] >> 1) & 0x3F;
    }

    // nal unit type 19, 20 are IDR picture, 21 is CRA picture
    if ((nNalUnitType == 19) || (nNalUnitType == 20) || (nNalUnitType == 21))
    {
        // sending vps/sps/pps on I-frame
        EncodeHevcNALUnit(mVPS, mVPSsize, nTimestamp, 1, nNalUnitType);
        EncodeHevcNALUnit(mSPS, mSpsSize, nTimestamp, 1, nNalUnitType);
        EncodeHevcNALUnit(mPPS, mPpsSize, nTimestamp, 1, nNalUnitType);
    }

    if (nDataSize > 0)
    {
        EncodeHevcNALUnit(pCurDataPos, nDataSize, nTimestamp, bMark, nNalUnitType);
        if (nNalUnitType == 34)
        {
            memset(mPPS, 0, MAX_CONFIG_LEN);
            memcpy(mPPS, pCurDataPos, nDataSize);
            mPpsSize = nDataSize;
            IMLOGD1("[EncodeHevc] PPS Size[%d]", mPpsSize);
        }
    }
}

void VideoRtpPayloadEncoderNode::EncodeHevcNALUnit(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nNalUnitType)
{
    uint8_t* pCurDataPos = pData;
    uint32_t nMtu = mMaxFragmentUnitSize * 0.9;

    if (nDataSize > 5)
    {
        IMLOGD_PACKET8(IM_PACKET_LOG_PH,
                "[EncodeHevcNALUnit] [%02X %02X %02X %02X] nDataSize[%d], TS[%u], mark[%d], "
                "nalType[%d]",
                pData[0], pData[1], pData[2], pData[3], nDataSize, nTimestamp, bMark, nNalUnitType);
    }

    if (mBuffer == nullptr)
    {
        return;
    }

    // Share payload header mode with h.264 - single nal unit mode, non interleaved mode
    // make FU-A packets
    if (mPayloadMode == kRtpPayloadHeaderModeNonInterleaved && nDataSize > nMtu)
    {
        uint32_t nRemainSize = nDataSize;
        uint32_t nSendDataSize;
        bool bFirstPacket = true;
        bool bMbit;
        uint8_t bFUIndicator1;
        uint8_t bFUIndicator2;
        uint8_t bFUHeader;

        // Make FU indicator and save NAL unit type
        // bNALUnitType = pCurDataPos[0] & 0x7E;         // copy NAL unit type from NAL unit header
        bFUIndicator1 = pCurDataPos[0] & 0x81;        // copy F and NRI from NAL unit header
        bFUIndicator1 = bFUIndicator1 + (0x31 << 1);  // Type is 49(FU-A)
        bFUIndicator2 = pCurDataPos[1];               // copy LayerId, TID

        while (nRemainSize)
        {
            // set bMbit, nSendDataSize, bFUHeader
            // last packet
            if (nRemainSize <= nMtu)
            {
                bMbit = bMark;                    // RTP marker bit
                bFUHeader = 0x40 | nNalUnitType;  // set E bit
                nSendDataSize = nRemainSize;
            }
            else
            {
                // First and Mid packets
                bMbit = false;  // RTP marker bit

                if (bFirstPacket)
                {
                    bFUHeader = 0x80 | nNalUnitType;  // set S bit
                }
                else
                {
                    bFUHeader = nNalUnitType;
                }

                if (nRemainSize >= nMtu * 2)
                {
                    nSendDataSize = nMtu;
                }
                else
                {
                    nSendDataSize = nRemainSize / 2;
                }
            }

            /** pData is the buffer of front node (not mine) bacause this method is called
             * in OnDataFromFrontNode method. then, to modify it, we have to copy it
             * to another buffer*/
            if (bFirstPacket)
            {
                memcpy(mBuffer + 1, pCurDataPos, nSendDataSize);
                pCurDataPos += nSendDataSize;
                nRemainSize -= nSendDataSize;
                nSendDataSize += 1;
                bFirstPacket = false;
            }
            else
            {
                memcpy(mBuffer + 3, pCurDataPos, nSendDataSize);
                pCurDataPos += nSendDataSize;
                nRemainSize -= nSendDataSize;
                nSendDataSize += 3;
            }

            mBuffer[0] = bFUIndicator1;
            mBuffer[1] = bFUIndicator2;
            mBuffer[2] = bFUHeader;

            // Insert CVO Rtp extension when the last packet of IDR frame
            // nal unit type 19, 20 are IDR picture, 21 is CRA picture
            if ((nNalUnitType == 19) || (nNalUnitType == 20) || (nNalUnitType == 21))
            {
                SendDataToRearNode(
                        MEDIASUBTYPE_VIDEO_IDR_FRAME, mBuffer, nSendDataSize, nTimestamp, bMbit, 0);
            }
            else
            {
                SendDataToRearNode(
                        MEDIASUBTYPE_RTPPAYLOAD, mBuffer, nSendDataSize, nTimestamp, bMbit, 0);
            }
        }
    }
    else
    {
        IMLOGD_PACKET5(IM_PACKET_LOG_PH,
                "[EncodeHevcNALUnit] [%02X %02X] nDataSize[%d], TS[%u], mark[%d]", pCurDataPos[0],
                pCurDataPos[1], nDataSize, nTimestamp, bMark);

        // Insert CVO Rtp extension when the last packet of IDR frame
        // nal unit type 19, 20 are IDR picture, 21 is CRA picture
        if ((nNalUnitType == 19) || (nNalUnitType == 20) || (nNalUnitType == 21))
        {
            SendDataToRearNode(
                    MEDIASUBTYPE_VIDEO_IDR_FRAME, pCurDataPos, nDataSize, nTimestamp, bMark, 0);
        }
        else
        {
            SendDataToRearNode(
                    MEDIASUBTYPE_RTPPAYLOAD, pCurDataPos, nDataSize, nTimestamp, bMark, 0);
        }
    }
}

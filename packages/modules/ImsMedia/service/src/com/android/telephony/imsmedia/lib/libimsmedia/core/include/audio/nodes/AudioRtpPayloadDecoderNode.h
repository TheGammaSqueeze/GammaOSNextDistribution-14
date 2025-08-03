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

#ifndef AUDIO_RTP_PAYLOAD_DECODER_NODE_H
#define AUDIO_RTP_PAYLOAD_DECODER_NODE_H

#include <ImsMediaDefine.h>
#include <BaseNode.h>
#include <ImsMediaBitReader.h>
#include <ImsMediaBitWriter.h>

/**
 * @brief this class is to decode the AMR/AMR-WB/EVS encoded rtp payload header and
 * send payload to next node.
 *
 */
class AudioRtpPayloadDecoderNode : public BaseNode
{
public:
    AudioRtpPayloadDecoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~AudioRtpPayloadDecoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = ImsMediaSubType::MEDIASUBTYPE_UNDEFINED,
            uint32_t arrivalTime = 0);

private:
    void DecodePayloadAmr(uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, uint32_t nSeqNum,
            uint32_t arrivalTime);
    void DecodePayloadEvs(uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, bool bMark,
            uint32_t nSeqNum, uint32_t arrivalTime);
    bool ProcessCMRForEVS(kRtpPayloadHeaderMode eEVSPayloadHeaderMode, kEvsCmrCodeType cmr_t,
            kEvsCmrCodeDefine cmr_d);

private:
    int32_t mCodecType;
    bool mOctetAligned;
    uint8_t mPayload[MAX_AUDIO_PAYLOAD_SIZE];
    std::list<uint32_t> mListFrameType;
    ImsMediaBitReader mBitReader;
    ImsMediaBitWriter mBitWriter;
    uint32_t mPrevCMR;
    int32_t mEvsBandwidth;
    kEvsCodecMode mEvsCodecMode;
    kRtpPayloadHeaderMode mEvsPayloadHeaderMode;
    kEvsBitrate mEvsMode;
    int32_t mCoreEvsMode;
    int8_t mEvsOffset;
    int32_t EvsChAOffset;
};

#endif

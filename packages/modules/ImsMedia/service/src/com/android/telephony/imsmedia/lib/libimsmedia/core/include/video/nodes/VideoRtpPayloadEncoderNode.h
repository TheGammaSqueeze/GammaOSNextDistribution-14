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

#ifndef VIDEO_RTP_PAYLOAD_ENCODER_NODE_H_INCLUDED
#define VIDEO_RTP_PAYLOAD_ENCODER_NODE_H_INCLUDED

#include <ImsMediaDefine.h>
#include <BaseNode.h>
#include <ImsMediaVideoUtil.h>

/**
 * @brief this class is to decode the Avc/Hevc encoded video rtp payload header and send payload
 * to next node.
 *
 */
class VideoRtpPayloadEncoderNode : public BaseNode
{
public:
    VideoRtpPayloadEncoderNode(BaseSessionCallback* callback = nullptr);
    virtual ~VideoRtpPayloadEncoderNode();
    virtual kBaseNodeId GetNodeId();
    virtual ImsMediaResult Start();
    virtual void Stop();
    virtual bool IsRunTime();
    virtual bool IsSourceNode();
    virtual void SetConfig(void* config);
    virtual bool IsSameConfig(void* config);
    virtual void OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData, uint32_t nDataSize,
            uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
            ImsMediaSubType nDataType = MEDIASUBTYPE_UNDEFINED, uint32_t arrivalTime = 0);

private:
    bool ResetStartTime();
    uint8_t* FindAvcStartCode(uint8_t* pData, uint32_t nDataSize, uint32_t* pnSkipSize = nullptr);
    uint8_t* FindHevcStartCode(uint8_t* pData, uint32_t nDataSize, uint32_t* pnSkipSize = nullptr);
    void EncodeAvc(uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, bool bMark);
    /** h.264 start coded has been removed at EncodeAvc()
     * pData starts with nal unit header */
    void EncodeAvcNALUnit(uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, bool bMark,
            uint32_t nNalUnitType);
    /* encode H.265 RTP payload header */
    void EncodeHevc(uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, bool bMark);
    /* encode H.265 RTP payload nal unit header */
    void EncodeHevcNALUnit(uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, bool bMark,
            uint32_t nNalUnitType);

    uint32_t mCodecType;
    uint32_t mPayloadMode;
    bool mPrevMark;
    uint8_t* mBuffer;
    uint8_t mVPS[MAX_CONFIG_LEN];
    uint8_t mSPS[MAX_CONFIG_LEN];
    uint8_t mPPS[MAX_CONFIG_LEN];
    uint32_t mVPSsize;
    uint32_t mSpsSize;
    uint32_t mPpsSize;
    uint32_t mMaxFragmentUnitSize;
};

#endif  // VIDEO_RTP_PAYLOAD_ENCODER_NODE_H_INCLUDED

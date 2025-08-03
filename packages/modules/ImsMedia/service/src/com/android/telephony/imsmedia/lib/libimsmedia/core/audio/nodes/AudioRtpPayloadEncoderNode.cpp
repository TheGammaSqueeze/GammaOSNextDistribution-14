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

#include <AudioRtpPayloadEncoderNode.h>
#include <ImsMediaAudioUtil.h>
#include <ImsMediaTrace.h>
#include <AudioConfig.h>
#include <EvsParams.h>

#define EVS_MODE_SID 12

AudioRtpPayloadEncoderNode::AudioRtpPayloadEncoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = 0;
    mOctetAligned = false;
    mPtime = 0;
    memset(mPayload, 0, sizeof(mPayload));
    mFirstFrame = false;
    mTimestamp = 0;
    mMaxNumOfFrame = 0;
    mCurrNumOfFrame = 0;
    mCurrFramePos = 0;
    mTotalPayloadSize = 0;
    mEvsBandwidth = kEvsBandwidthNone;
    mEvsCodecMode = kEvsCodecModePrimary;
    mEvsOffset = 0;
    mSendCMR = 0;
    mEvsMode = kEvsAmrIoModeBitrate00660;
    mCoreEvsMode = 0;
    mEvsPayloadHeaderMode = kRtpPayloadHeaderModeEvsCompact;
}

AudioRtpPayloadEncoderNode::~AudioRtpPayloadEncoderNode() {}

kBaseNodeId AudioRtpPayloadEncoderNode::GetNodeId()
{
    return kNodeIdAudioPayloadEncoder;
}

ImsMediaResult AudioRtpPayloadEncoderNode::Start()
{
    mMaxNumOfFrame = mPtime / 20;
    mEvsMode = (kEvsBitrate)ImsMediaAudioUtil::GetMaximumEvsMode(mCoreEvsMode);
    mEvsCodecMode = (kEvsCodecMode)ImsMediaAudioUtil::ConvertEvsCodecMode(mEvsMode);

    IMLOGD5("[Start] codecType[%d], mode[%d], num of frames[%d], evs bitrate[%d], evs mode[%d]",
            mCodecType, mOctetAligned, mMaxNumOfFrame, mEvsMode, mEvsCodecMode);

    if (mMaxNumOfFrame == 0 || mMaxNumOfFrame > MAX_FRAME_IN_PACKET)
    {
        IMLOGE1("[Start] Invalid ptime [%d]", mPtime);
        return RESULT_INVALID_PARAM;
    }

    mCurrNumOfFrame = 0;
    mCurrFramePos = 0;
    mFirstFrame = true;
    mTotalPayloadSize = 0;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void AudioRtpPayloadEncoderNode::Stop()
{
    IMLOGD0("[Stop]");
    mNodeState = kNodeStateStopped;
}

bool AudioRtpPayloadEncoderNode::IsRunTime()
{
    return true;
}

bool AudioRtpPayloadEncoderNode::IsSourceNode()
{
    return false;
}

void AudioRtpPayloadEncoderNode::OnDataFromFrontNode(ImsMediaSubType /*subtype*/, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
        ImsMediaSubType nDataType, uint32_t arrivalTime)
{
    switch (mCodecType)
    {
        case kAudioCodecAmr:
        case kAudioCodecAmrWb:
            EncodePayloadAmr(pData, nDataSize, nTimestamp, arrivalTime);
            break;
        case kAudioCodecPcmu:
        case kAudioCodecPcma:
            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, pData, nDataSize, nTimestamp, bMark,
                    nSeqNum, nDataType, arrivalTime);
            break;
        case kAudioCodecEvs:
            EncodePayloadEvs(pData, nDataSize, nTimestamp, arrivalTime);
            break;
        default:
            IMLOGE1("[OnDataFromFrontNode] invalid codec type[%d]", mCodecType);
            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, pData, nDataSize, nTimestamp, bMark,
                    nSeqNum, nDataType, arrivalTime);
            break;
    }
}

void AudioRtpPayloadEncoderNode::SetConfig(void* config)
{
    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (pConfig != nullptr)
    {
        mCodecType = ImsMediaAudioUtil::ConvertCodecType(pConfig->getCodecType());
        if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
        {
            mOctetAligned = pConfig->getAmrParams().getOctetAligned();
        }
        else if (mCodecType == kAudioCodecEvs)
        {
            mEvsBandwidth = (kEvsBandwidth)pConfig->getEvsParams().getEvsBandwidth();
            mEvsPayloadHeaderMode =
                    (kRtpPayloadHeaderMode)pConfig->getEvsParams().getUseHeaderFullOnly();
            mCoreEvsMode = pConfig->getEvsParams().getEvsMode();
            mEvsOffset = pConfig->getEvsParams().getChannelAwareMode();
            mSendCMR = pConfig->getEvsParams().getCodecModeRequest();
        }

        mPtime = pConfig->getPtimeMillis();
    }
}

bool AudioRtpPayloadEncoderNode::IsSameConfig(void* config)
{
    if (config == nullptr)
        return true;
    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (mCodecType == ImsMediaAudioUtil::ConvertCodecType(pConfig->getCodecType()))
    {
        if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
        {
            return (mOctetAligned == pConfig->getAmrParams().getOctetAligned());
        }
        else if (mCodecType == kAudioCodecEvs)
        {
            return (mEvsBandwidth == (kEvsBandwidth)pConfig->getEvsParams().getEvsBandwidth() &&
                    mEvsPayloadHeaderMode ==
                            (kRtpPayloadHeaderMode)pConfig->getEvsParams().getUseHeaderFullOnly() &&
                    mCoreEvsMode ==
                            ImsMediaAudioUtil::GetMaximumEvsMode(
                                    pConfig->getEvsParams().getEvsMode()) &&
                    mEvsOffset == pConfig->getEvsParams().getChannelAwareMode());
        }
    }

    return false;
}

void AudioRtpPayloadEncoderNode::EncodePayloadAmr(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimestamp, uint32_t arrivalTime)
{
    uint32_t nCmr = 15;
    uint32_t f, ft, q, nDataBitSize;

    // remove TOC from the encoder
    pData++;
    nDataSize -= 1;

    if (nDataSize > 4)
    {
        IMLOGD_PACKET5(IM_PACKET_LOG_PH, "[EncodePayloadAmr] src = %02X %02X %02X %02X, len[%d]",
                pData[0], pData[1], pData[2], pData[3], nDataSize);
    }

    IMLOGD_PACKET2(IM_PACKET_LOG_PH, "[EncodePayloadAmr] codectype[%d], octetAligned[%d]",
            mCodecType, mOctetAligned);

    mCurrNumOfFrame++;
    f = (mCurrNumOfFrame == mMaxNumOfFrame) ? 0 : 1;

    if (mCodecType == kAudioCodecAmr)
    {
        nCmr = 0x0F;
        ft = ImsMediaAudioUtil::ConvertLenToAmrMode(nDataSize);
        nDataBitSize = ImsMediaAudioUtil::ConvertAmrModeToBitLen(ft);
    }
    else
    {
        nCmr = 0x0F;
        ft = ImsMediaAudioUtil::ConvertLenToAmrWbMode(nDataSize);
        nDataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(ft);
    }

    q = 1;

    // the first paylaod
    if (mCurrNumOfFrame == 1)
    {
        memset(mPayload, 0, MAX_AUDIO_PAYLOAD_SIZE);
        mBWHeader.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
        mBWPayload.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
        mBWHeader.Write(nCmr, 4);

        if (mOctetAligned == true)
        {
            mBWHeader.Write(0, 4);
            mBWPayload.Seek(8 + mMaxNumOfFrame * 8);
        }
        else
        {
            mBWPayload.Seek(4 + mMaxNumOfFrame * 6);
        }

        mTimestamp = nTimestamp;
    }

    // Payload ToC
    mBWHeader.Write(f, 1);
    mBWHeader.Write(ft, 4);
    mBWHeader.Write(q, 1);

    if (mOctetAligned == true)
    {
        mBWHeader.AddPadding();
    }

    IMLOGD_PACKET2(IM_PACKET_LOG_PH, "[EncodePayloadAmr] nDataBitSize[%d], nDataSize[%d]",
            nDataBitSize, nDataSize);

    // Speech Frame
    mBWPayload.WriteByteBuffer(pData, nDataBitSize);

    if (mOctetAligned == true)
    {
        mBWPayload.AddPadding();
    }

    mTotalPayloadSize += nDataSize;

    if (mCurrNumOfFrame == mMaxNumOfFrame)
    {
        mBWHeader.Flush();
        mBWPayload.AddPadding();
        mBWPayload.Flush();
        uint32_t nTotalSize = mBWPayload.GetBufferSize();

        IMLOGD_PACKET7(IM_PACKET_LOG_PH,
                "[EncodePayloadAmr] result = %02X %02X %02X %02X %02X %02X, len[%d]", mPayload[0],
                mPayload[1], mPayload[2], mPayload[3], mPayload[4], mPayload[5], nTotalSize);

        if (mTotalPayloadSize > 0)
        {
            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, nTotalSize, mTimestamp,
                    mFirstFrame, 0, MEDIASUBTYPE_UNDEFINED, arrivalTime);
        }

        mCurrNumOfFrame = 0;
        mTotalPayloadSize = 0;

        if (mFirstFrame)
        {
            mFirstFrame = false;
        }
    }
}

void AudioRtpPayloadEncoderNode::EncodePayloadEvs(
        uint8_t* pData, uint32_t nDataSize, uint32_t nTimeStamp, uint32_t arrivalTime)
{
    if (nDataSize == 0)
    {
        return;
    }

    uint32_t nFrameType = 0;
    // compact or header-full format, default is compact formats
    // primary or amr-wb io mode, default is primary mode
    // primary or amr-wb io mode base on frameSize.
    mCurrNumOfFrame++;

    if (mEvsPayloadHeaderMode == kRtpPayloadHeaderModeEvsCompact)
    {
        memset(mPayload, 0, MAX_AUDIO_PAYLOAD_SIZE);
        mBWHeader.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
        mBWPayload.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);

        mTimestamp = nTimeStamp;
        // exactly one coded frame without any additional EVS RTP payload header
        if (mEvsCodecMode == kEvsCodecModePrimary)
        {
            // calculate nDataBitSize from nDataSize
            nFrameType = (uint32_t)ImsMediaAudioUtil::ConvertLenToEVSAudioMode(nDataSize);
            uint32_t nDataBitSize =
                    ImsMediaAudioUtil::ConvertEVSAudioModeToBitLen((kImsAudioEvsMode)nFrameType);

            if (nDataBitSize == 0)
            {
                return;
            }

            // special case, EVS Primary 2.8 kbps frame in Compact format
            if (nFrameType == 0)
            {
                // First data bit d(0) of the EVS Primary 2.8 kbps is always set to '0'
                pData[0] = pData[0] & 0x7f;
            }

            // write speech Frame
            mBWPayload.WriteByteBuffer(pData, nDataBitSize);
            mTotalPayloadSize += nDataSize;

            mBWHeader.AddPadding();
            mBWHeader.Flush();

            mBWPayload.AddPadding();
            mBWPayload.Flush();

            uint32_t nTotalSize = mBWPayload.GetBufferSize();

            IMLOGD_PACKET7(IM_PACKET_LOG_PH, "[EncodePayloadEvs] result =\
                %02X %02X %02X %02X %02X %02X, len[%d]",
                    mPayload[0], mPayload[1], mPayload[2], mPayload[3], mPayload[4], mPayload[5],
                    nTotalSize);

            if (mTotalPayloadSize > 0)
            {
                SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, nTotalSize, mTimestamp,
                        mFirstFrame, 0, MEDIASUBTYPE_UNDEFINED, arrivalTime);
            }

            mCurrNumOfFrame = 0;
            mTotalPayloadSize = 0;
            if (mFirstFrame)
                mFirstFrame = false;
        }
        // one 3-bit CMR field, one coded frame, and zero-padding bits if necessary
        else if (mEvsCodecMode == kEvsCodecModeAmrIo)
        {
            // calculate nDataBitSize from nDataSize
            nFrameType = (uint32_t)ImsMediaAudioUtil::ConvertLenToAmrWbMode(nDataSize);
            uint32_t nDataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(nFrameType);

            // 0: 6.6, 1: 8.85, 2: 12.65, 3: 15.85, 4: 18.25, 5: 23.05, 6: 23.85, 7: none
            // 0111(7) is no request.
            uint32_t nCmr = 0x07;

            // write CMR except SID
            // at EVS AMR WB IO Mode, SID packet does not include cmr field...and no processing
            if (nFrameType != kImsAudioAmrWbModeSID)
            {
                mBWHeader.Write(nCmr, 3);
                mBWPayload.Seek(3);

                // append a speech data bit(0) after the last speech data bit
                uint8_t nDataBit0 = 0;
                uint32_t i = 0;
                uint32_t remain = 0;

                nDataBit0 = pData[0] >> 7;
                for (i = 0; i < (nDataSize - 1); i++)
                {
                    pData[i] = pData[i] << 1;
                    pData[i] = pData[i] + (pData[i + 1] >> 7);
                }

                // set the last speech data byte
                remain = nDataBitSize % 8;
                if (remain == 0)
                    remain = 8;
                pData[nDataSize - 1] = pData[nDataSize - 1] << 1;
                nDataBit0 = nDataBit0 << (8 - remain);
                pData[nDataSize - 1] = pData[nDataSize - 1] + nDataBit0;
            }
            else  // kImsAudioAmrWbModeSID case
            {
                // EVS amr io mode's SID is used HF format.
                // set cmr
                nCmr = 0xff;  // no request - 0xff
                mBWHeader.Write(nCmr, 8);
                mBWPayload.Seek(8);

                // set ToC
                // Header Type identification bit(1bit) - always set to 0
                uint32_t toc_h = 0;
                // (1bit - always set to 0 in compact AMR WB IO mode)
                uint32_t toc_f = 0;
                // 1 1 1001 - EVS AMR IO Mode, Q bit set 1, 1001 indicate SID packet
                uint32_t ft = 0x39;

                mBWHeader.Write(toc_h, 1);
                mBWHeader.Write(toc_f, 1);
                mBWHeader.Write(ft, 6);
                mBWPayload.Seek(8);
            }

            // write speech Frame
            mBWPayload.WriteByteBuffer(pData, nDataBitSize);
            mTotalPayloadSize += nDataSize;

            mBWHeader.Flush();

            mBWPayload.AddPadding();
            mBWPayload.Flush();

            uint32_t nTotalSize = mBWPayload.GetBufferSize();

            IMLOGD_PACKET7(IM_PACKET_LOG_PH,
                    "[EncodePayloadEvs] Result = %02X %02X %02X %02X %02X %02X, len[%d]",
                    mPayload[0], mPayload[1], mPayload[2], mPayload[3], mPayload[4], mPayload[5],
                    nTotalSize);

            if (mTotalPayloadSize > 0)
            {
                SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, nTotalSize, mTimestamp,
                        mFirstFrame, 0, MEDIASUBTYPE_UNDEFINED, arrivalTime);
            }

            mCurrNumOfFrame = 0;
            mTotalPayloadSize = 0;
            if (mFirstFrame)
                mFirstFrame = false;
        }
        else
        {
            IMLOGE0("[EncodePayloadEvs] Invalid codec mode");
            return;
        }
    }
    else if (mEvsPayloadHeaderMode == kRtpPayloadHeaderModeEvsHeaderFull)
    {
        // 0111 1111 is no request.
        uint32_t nEVSBW = 0x07;
        uint32_t nEVSBR = 0x0f;

        uint32_t cmr_h, cmr_t, cmr_d = 0;  // CMR byte
        memset(mPayload, 0, MAX_AUDIO_PAYLOAD_SIZE);
        mBWHeader.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
        mBWPayload.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);

        if (mEvsCodecMode == kEvsCodecModePrimary)
        {
            nFrameType = (uint32_t)ImsMediaAudioUtil::ConvertLenToEVSAudioMode(nDataSize);

            if (nFrameType == EVS_MODE_SID || mSendCMR == 1)  // CMR value
            {
                // Header Type identification bit(1bit) - always set to 1
                cmr_h = 1;
                // Type of Request(3bits) - NB(000), IO(001), FB(100), WB(101), SWB(110)
                cmr_t = nEVSBW;
                // codec mode request(4bits)
                cmr_d = nEVSBR;
            }

            // set ToC byte
            uint32_t toc_h = 0;  // Header Type identification bit(1bit) - always set to 0
            uint32_t toc_f = (mCurrNumOfFrame == mMaxNumOfFrame) ? 0 : 1;  // (1bit)
            uint32_t toc_ft_m = 0;  // EVS mode(1bit), Primary mode is 0
            uint32_t toc_ft_q = 0;  // Q bit(1bit) - zero for kEvsCodecModePrimary
            uint32_t toc_ft_b =
                    ImsMediaAudioUtil::ConvertLenToEVSAudioMode(nDataSize);  // EVS bit rate(4bits)
            uint32_t nDataBitSize =
                    ImsMediaAudioUtil::ConvertEVSAudioModeToBitLen((kImsAudioEvsMode)toc_ft_b);

            // write CMR and seek the position of the first paylaod
            if (mCurrNumOfFrame == 1)
            {
                // set CMR byte - it's optional field...
                if (nFrameType == EVS_MODE_SID || mSendCMR == 1)
                {
                    // check writing CMR or not
                    // write CMR byte
                    mBWHeader.Write(cmr_h, 1);
                    mBWHeader.Write(cmr_t, 3);
                    mBWHeader.Write(cmr_d, 4);

                    mBWPayload.Seek(8);
                }

                // ToC field.
                mBWPayload.Seek(mMaxNumOfFrame * 8);  // jump ToC bytes
                mTimestamp = nTimeStamp;              // set timestamp as the first frame
            }

            // write ToC
            mBWHeader.Write(toc_h, 1);
            mBWHeader.Write(toc_f, 1);
            mBWHeader.Write(toc_ft_m, 1);
            mBWHeader.Write(toc_ft_q, 1);
            mBWHeader.Write(toc_ft_b, 4);

            // write Speech Frame
            mBWPayload.WriteByteBuffer(pData, nDataBitSize);
            mBWPayload.AddPadding();

            mTotalPayloadSize += nDataSize;

            if (mCurrNumOfFrame == mMaxNumOfFrame)
            {
                // mBWHeader.AddPadding();
                mBWHeader.Flush();

                mBWPayload.AddPadding();
                mBWPayload.Flush();

                uint32_t nTotalSize = mBWPayload.GetBufferSize();
                IMLOGD_PACKET7(IM_PACKET_LOG_PH,
                        "[EncodePayloadEvs] Result = %02X %02X %02X %02X %02X %02X, len[%d]",
                        mPayload[0], mPayload[1], mPayload[2], mPayload[3], mPayload[4],
                        mPayload[5], nTotalSize);

                if (mTotalPayloadSize > 0)
                {
                    SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload,
                            CheckPaddingNecessity(nTotalSize), mTimestamp, mFirstFrame, 0,
                            MEDIASUBTYPE_UNDEFINED, arrivalTime);
                }

                mCurrNumOfFrame = 0;
                mTotalPayloadSize = 0;
                if (mFirstFrame)
                    mFirstFrame = false;
            }
        }
        else if (mEvsCodecMode == kEvsCodecModeAmrIo)
        {
            // set CMR byte
            // at EVS AMR WB IO Mode, CMR field shall include.
            // Header Type identification bit(1bit) - always set to 1
            cmr_h = 1;
            /* Type of Request(3bits) - NB(000), IO(001), WB(010), SWB(011), FB(100), WB 13.2
             * channel-aware(101), SWB 13.2 channel-aware(110), reserved(111) */
            cmr_t = nEVSBW;
            // codec mode request(4bits) 1111 is no request.
            cmr_d = nEVSBR;

            // set ToC byte
            // Header Type identification bit(1bit) - always set to 0
            uint32_t toc_h = 0;
            // (1bit)
            uint32_t toc_f = (mCurrNumOfFrame == mMaxNumOfFrame) ? 0 : 1;
            // EVS mode(1bit), AMR-WB IO mode is 1
            uint32_t toc_ft_m = 1;
            // Q bit(1bit) - 1 for AMR_WB_IO
            // for ORG EVS to avoid the issue -#EURAVOLTE-567
            uint32_t toc_ft_q = 1;
            // EVS AMR WB IO bit rate(4bits)
            uint32_t toc_ft_b = (uint32_t)ImsMediaAudioUtil::ConvertLenToAmrWbMode(nDataSize);
            uint32_t nDataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(toc_ft_b);

            // write CMR and seek the position of the first paylaod
            if (mCurrNumOfFrame == 1)
            {
                // write CMR byte
                mBWHeader.Write(cmr_h, 1);
                mBWHeader.Write(cmr_t, 3);
                mBWHeader.Write(cmr_d, 4);

                // seek the position of the first paylaod
                // add speech data after CMR and ToC
                mBWPayload.Seek(8 + mMaxNumOfFrame * 8);

                mTimestamp = nTimeStamp;  // set timestamp as the first frame
            }

            // write ToC
            mBWHeader.Write(toc_h, 1);
            mBWHeader.Write(toc_f, 1);
            mBWHeader.Write(toc_ft_m, 1);
            mBWHeader.Write(toc_ft_q, 1);
            mBWHeader.Write(toc_ft_b, 4);

            // write Speech Frame
            mBWPayload.WriteByteBuffer(pData, nDataBitSize);
            mBWPayload.AddPadding();

            mTotalPayloadSize += nDataSize;

            if (mCurrNumOfFrame == mMaxNumOfFrame)
            {
                // mBWHeader.AddPadding();
                mBWHeader.Flush();

                mBWPayload.AddPadding();
                mBWPayload.Flush();

                uint32_t nTotalSize = mBWPayload.GetBufferSize();

                IMLOGD_PACKET7(IM_PACKET_LOG_PH,
                        "[EncodePayloadEvs] result = %02X %02X %02X %02X %02X %02X, len[%d]",
                        mPayload[0], mPayload[1], mPayload[2], mPayload[3], mPayload[4],
                        mPayload[5], nTotalSize);

                if (mTotalPayloadSize > 0)
                {
                    SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload,
                            CheckPaddingNecessity(nTotalSize), mTimestamp, mFirstFrame, 0,
                            MEDIASUBTYPE_UNDEFINED, arrivalTime);
                }

                mCurrNumOfFrame = 0;
                mTotalPayloadSize = 0;
                if (mFirstFrame)
                    mFirstFrame = false;
            }
        }
        else
        {
            IMLOGE0("[EncodePayloadEvs] invalid codec mode");
            return;
        }
    }
    else
    {
        IMLOGE0("[EncodePayloadEvs] invalid payload format");
        return;
    }

    return;
}

uint32_t AudioRtpPayloadEncoderNode::CheckPaddingNecessity(uint32_t nTotalSize)
{
    kEvsCodecMode evsCodecMode;
    uint32_t nEVSCompactId;
    uint32_t nSize = nTotalSize;

    // check EVS compact size
    while (nSize != 0 &&
            ImsMediaAudioUtil::ConvertEVSPayloadMode(nSize, &evsCodecMode, &nEVSCompactId) ==
                    kRtpPayloadHeaderModeEvsCompact)
    {
        mPayload[nSize] = 0;
        nSize++;
    }

    return nSize;
}

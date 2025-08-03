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

#include <AudioRtpPayloadDecoderNode.h>
#include <ImsMediaAudioUtil.h>
#include <ImsMediaTrace.h>
#include <AudioConfig.h>

AudioRtpPayloadDecoderNode::AudioRtpPayloadDecoderNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    mCodecType = 0;
    mOctetAligned = 0;
    memset(mPayload, 0, sizeof(mPayload));
    mListFrameType.clear();
    mPrevCMR = 15;
    mEvsBandwidth = kEvsBandwidthNone;
    mEvsCodecMode = kEvsCodecModePrimary;
    mEvsPayloadHeaderMode = kRtpPayloadHeaderModeEvsCompact;
    mEvsMode = kEvsAmrIoModeBitrate00660;
    mCoreEvsMode = 0;
    mEvsOffset = 0;
    EvsChAOffset = 0;
}

AudioRtpPayloadDecoderNode::~AudioRtpPayloadDecoderNode() {}

kBaseNodeId AudioRtpPayloadDecoderNode::GetNodeId()
{
    return kNodeIdAudioPayloadDecoder;
}

ImsMediaResult AudioRtpPayloadDecoderNode::Start()
{
    IMLOGD0("[Start]");
    mEvsMode = (kEvsBitrate)ImsMediaAudioUtil::GetMaximumEvsMode(mCoreEvsMode);
    mEvsCodecMode = (kEvsCodecMode)ImsMediaAudioUtil::ConvertEvsCodecMode(mEvsMode);

    mPrevCMR = mCodecType == kAudioCodecEvs ? 127 : 15;
    mListFrameType.clear();
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void AudioRtpPayloadDecoderNode::Stop()
{
    IMLOGD0("[Stop]");
    mNodeState = kNodeStateStopped;
}

bool AudioRtpPayloadDecoderNode::IsRunTime()
{
    return true;
}

bool AudioRtpPayloadDecoderNode::IsSourceNode()
{
    return false;
}

void AudioRtpPayloadDecoderNode::SetConfig(void* config)
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
            mEvsBandwidth = pConfig->getEvsParams().getEvsBandwidth();
            mCoreEvsMode = pConfig->getEvsParams().getEvsMode();
            mEvsPayloadHeaderMode =
                    (kRtpPayloadHeaderMode)pConfig->getEvsParams().getUseHeaderFullOnly();
            mEvsOffset = pConfig->getEvsParams().getChannelAwareMode();
        }
    }
}

bool AudioRtpPayloadDecoderNode::IsSameConfig(void* config)
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
            return (mEvsBandwidth == pConfig->getEvsParams().getEvsBandwidth() &&
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

void AudioRtpPayloadDecoderNode::OnDataFromFrontNode(ImsMediaSubType subtype, uint8_t* pData,
        uint32_t nDataSize, uint32_t nTimestamp, bool bMark, uint32_t nSeqNum,
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
        case kAudioCodecAmr:
        case kAudioCodecAmrWb:
            DecodePayloadAmr(pData, nDataSize, nTimestamp, nSeqNum, arrivalTime);
            break;
        case kAudioCodecPcmu:
        case kAudioCodecPcma:
            SendDataToRearNode(
                    MEDIASUBTYPE_RTPPAYLOAD, pData, nDataSize, nTimestamp, bMark, nSeqNum);
            break;
        case kAudioCodecEvs:
            DecodePayloadEvs(pData, nDataSize, nTimestamp, bMark, nSeqNum, arrivalTime);
            break;
        default:
            IMLOGE1("[OnDataFromFrontNode] invalid codec type[%d]", mCodecType);
            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, pData, nDataSize, nTimestamp, bMark,
                    nSeqNum, nDataType, arrivalTime);
            break;
    }
}

void AudioRtpPayloadDecoderNode::DecodePayloadAmr(uint8_t* pData, uint32_t nDataSize,
        uint32_t nTimestamp, uint32_t nSeqNum, uint32_t arrivalTime)
{
    if (pData == nullptr || nDataSize == 0)
    {
        return;
    }

    uint32_t timestamp = nTimestamp;
    uint32_t frameTypeIndex;
    uint32_t hasNextFrame;
    uint32_t cmr;
    uint32_t frameQualityIndicator;  // Q_Speech_Sid_Bad

    IMLOGD_PACKET5(IM_PACKET_LOG_PH,
            "[DecodePayloadAmr] codec type[%d], octetAligned[%d], size[%d], TS[%u], "
            "arrivalTime[%u]",
            mCodecType, mOctetAligned, nDataSize, timestamp, arrivalTime);

    mBitReader.SetBuffer(pData, nDataSize);
    // read cmr
    cmr = mBitReader.Read(4);

    if (mOctetAligned)
    {
        mBitReader.Read(4);
    }

#ifdef SIMULATE_CMR_AMR
    const int kMaxMode = mCodecType == kAudioCodecAmrWb ? 9 : 8;
    static int sCmr = 0;
    static int sCount = 0;
    if ((sCount++ % 250) == 0)  // every 5 second
    {
        mCallback->SendEvent(kRequestAudioCmr, (sCmr++ % kMaxMode));
    }
#endif

    if (cmr != mPrevCMR)
    {
        if ((mCodecType == kAudioCodecAmr && cmr <= 7) ||
                (mCodecType == kAudioCodecAmrWb && cmr <= 8))
        {
            IMLOGD2("[DecodePayloadAmr] CMR %d->%d", mPrevCMR, cmr);
            // send internal event to operate cmr operation in encoder side
            mCallback->SendEvent(kRequestAudioCmr, cmr);
            mPrevCMR = cmr;
        }
        else if (cmr == 15)
        {
            mCallback->SendEvent(kRequestAudioCmr, cmr);
            mPrevCMR = cmr;
        }
        else
        {
            IMLOGE1("[DecodePayloadAmr] invalid cmr value %d", cmr);
        }
    }

    // get number of frames
    do
    {
        hasNextFrame = mBitReader.Read(1);           // f(1)
        frameTypeIndex = mBitReader.Read(4);         // ft(4)
        frameQualityIndicator = mBitReader.Read(1);  // q(1)
        IMLOGD_PACKET3(IM_PACKET_LOG_PH, "[DecodePayloadAmr] ToC F=%d, FT=%d, Q=%d", hasNextFrame,
                frameTypeIndex, frameQualityIndicator);
        mListFrameType.push_back(frameTypeIndex);
        if (mOctetAligned)
        {
            mBitReader.Read(2);  // padding
        }
    } while (hasNextFrame == 1);

    // read speech frames
    while (mListFrameType.size() > 0)
    {
        uint32_t dataBitSize;
        frameTypeIndex = mListFrameType.front();
        if (mCodecType == kAudioCodecAmr)
        {
            dataBitSize = ImsMediaAudioUtil::ConvertAmrModeToBitLen(frameTypeIndex);
        }
        else
        {
            dataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(frameTypeIndex);
        }

        mListFrameType.pop_front();
        mBitWriter.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
        uint32_t bufferSize = (dataBitSize + 7) >> 3;

        ImsMediaSubType subType =
                ImsMediaAudioUtil::GetAmrFrameType(mCodecType, frameTypeIndex, bufferSize);

        // set TOC
        mBitWriter.Write(hasNextFrame, 1);
        mBitWriter.Write(frameTypeIndex, 4);
        mBitWriter.Write(frameQualityIndicator, 1);
        mBitWriter.Write(0, 2);
        mBitReader.ReadByteBuffer(mPayload + 1, dataBitSize);
        bufferSize++;

        if (mOctetAligned)
        {
            uint32_t paddingSize = (8 - (dataBitSize & 0x07)) & 0x07;
            mBitReader.Read(paddingSize);
        }

        IMLOGD_PACKET6(IM_PACKET_LOG_PH,
                "[DecodePayloadAmr] result = %02X %02X %02X %02X, len=%d, FT=%d", mPayload[0],
                mPayload[1], mPayload[2], mPayload[3], bufferSize, frameTypeIndex);

        // send remaining packet number in bundle as bMark value
        SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, bufferSize, timestamp,
                mListFrameType.size(), nSeqNum, subType, arrivalTime);

        timestamp += 20;
    }
}

void AudioRtpPayloadDecoderNode::DecodePayloadEvs(uint8_t* pData, uint32_t nDataSize,
        uint32_t nTimeStamp, bool bMark, uint32_t nSeqNum, uint32_t arrivalTime)
{
    if (pData == nullptr || nDataSize == 0)
    {
        return;
    }

    IMLOGD_PACKET4(IM_PACKET_LOG_PH,
            "[DecodePayloadEvs] codec type[%d], size[%d], TS[%u], arrivalTime[%u]", mCodecType,
            nDataSize, nTimeStamp, arrivalTime);

    kRtpPayloadHeaderMode eEVSReceivedPHFormat = kRtpPayloadHeaderModeEvsCompact;
    kEvsCodecMode kEvsCodecMode = kEvsCodecModePrimary;

#ifdef SIMULATE_CMR_EVS
    const int kMaxMode = 12;
    static int sCmr = 1;
    static int sCount = 0;
    if ((sCount++ % 250) == 0)  // every 5 second
    {
        mCallback->SendEvent(kRequestAudioCmrEvs, kEvsCmrCodeTypeSwb, (sCmr++ % kMaxMode));
    }
#endif

    // uint32_t nEVSBW = 0;
    // uint32_t nEVSBR = 0;
    uint32_t nEVSCompactId = 0;
    uint32_t nDataBitSize = 0;
    uint32_t timestamp = nTimeStamp;

    // CMR byte
    uint32_t h = 0;      // 1bit, Header Type identification bit(always set to 1)
    uint32_t cmr_t = 0;  // 3bits, Type of Request
    uint32_t cmr_d = 0;  // 4bits, Codec Mode Request

    // ToC byte
    uint32_t toc_f = 0;     // 1bit, follow another speech frame
    uint32_t toc_ft_m = 0;  // 1bit, EVS mode
    uint32_t toc_ft_q = 0;  // 1bit, AMR-WB Q bit
    uint32_t toc_ft_b = 0;  // 4bits, EVS bit rate
    ImsMediaSubType subType = MEDIASUBTYPE_AUDIO_NORMAL;

    mBitReader.SetBuffer(pData, nDataSize);

    // check RTP payload format
    eEVSReceivedPHFormat =
            ImsMediaAudioUtil::ConvertEVSPayloadMode(nDataSize, &kEvsCodecMode, &nEVSCompactId);

    if (nDataSize == 1)
    {
        subType = MEDIASUBTYPE_AUDIO_NODATA;
        nEVSCompactId = 13;
    }
    else if (nDataSize == 5 || nDataSize == 6)
    {
        subType = MEDIASUBTYPE_AUDIO_SID;
    }
    else if (nDataSize == 7 && (((pData[0] >> 7) == 1 && pData[1] == 12) || pData[0] == 12))
    {
        eEVSReceivedPHFormat = kRtpPayloadHeaderModeEvsHeaderFull;
        subType = MEDIASUBTYPE_AUDIO_SID;
        nEVSCompactId = 12;
    }
    else if (nDataSize == 8 && (pData[0] >> 7) == 1 && pData[1] == 12)
    {
        subType = MEDIASUBTYPE_AUDIO_SID;
        nEVSCompactId = 12;
    }

    if ((kEvsCodecMode == kEvsCodecModePrimary) && (nEVSCompactId == 0))  // special case
    {
        // first bit of the EVS Primary 2.8kbps in compact format is always set to '0'
        if ((pData[0] >> 7) == 0)
        {
            // EVS Primary 2.8 kbps frame in Compact format
            eEVSReceivedPHFormat = kRtpPayloadHeaderModeEvsCompact;
        }
        else
        {
            // EVS AMR-WB IO SID frame in Header-Full format with one CMR byte
            eEVSReceivedPHFormat = kRtpPayloadHeaderModeEvsHeaderFull;
        }
    }

    if (eEVSReceivedPHFormat == kRtpPayloadHeaderModeEvsCompact)
    {
        if (kEvsCodecMode == kEvsCodecModePrimary)
        {
            // calculate nDataBitSize from nDataSize
            uint32_t nFrameType = (uint32_t)ImsMediaAudioUtil::ConvertLenToEVSAudioMode(nDataSize);
            // TODO: remove kImsAudioEvsMode
            nDataBitSize =
                    ImsMediaAudioUtil::ConvertEVSAudioModeToBitLen((kImsAudioEvsMode)nFrameType);

            mBitReader.ReadByteBuffer(mPayload, nDataBitSize);

            IMLOGD_PACKET6(IM_PACKET_LOG_PH,
                    "[DecodePayloadEvs] Result=%02X %02X %02X %02X, len=%d,nFrameType=%d",
                    mPayload[0], mPayload[1], mPayload[2], mPayload[3], nDataSize, nFrameType);

            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, nDataSize, timestamp, bMark,
                    nSeqNum, subType, arrivalTime);
        }
        else if (kEvsCodecMode == kEvsCodecModeAmrIo)
        {
            // calculate nDataBitSize from nDataSize
            uint32_t nFrameType = (uint32_t)ImsMediaAudioUtil::ConvertLenToAmrWbMode(nDataSize);

            nDataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(nFrameType);

            // read cmr except SID
            // at EVS AMR WB IO Mode, SID packet does not include cmr field...
            if (nFrameType != kImsAudioAmrWbModeSID)
            {
                uint32_t cmr = mBitReader.Read(3);

                if (cmr != mPrevCMR)
                {
                    if (cmr != kEvsCmrCodeTypeNoReq)
                    {
                        ProcessCMRForEVS(kRtpPayloadHeaderModeEvsCompact, kEvsCmrCodeTypeNoReq,
                                (kEvsCmrCodeDefine)cmr);
                        // Save Prev CMR value for checking
                        mPrevCMR = cmr;
                    }
                    else
                    {
                        kEvsBandwidth nOriginBW =
                                ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(mEvsBandwidth);
                        uint32_t nOriginBR = mEvsMode;
                        kEvsCmrCodeType nCodeType = kEvsCmrCodeTypeNoReq;
                        kEvsCmrCodeDefine nCodeDefine = kEvsCmrCodeDefineNoReq;

                        if (mEvsCodecMode == kEvsCodecModePrimary)
                        {
                            // to convert EVS CMR type
                            nOriginBR -= (uint32_t)kEvsPrimaryModeBitrate00590;
                        }

                        // check AMR IO and ChA
                        if (mEvsCodecMode == kEvsCodecModeAmrIo)
                        {
                            nCodeType = kEvsCmrCodeTypeAmrIO;
                            nCodeDefine = (kEvsCmrCodeDefine)nOriginBR;
                        }
                        else if ((kEvsPrimaryModeBitrate01320 ==
                                         (nOriginBR + (uint32_t)kEvsPrimaryModeBitrate00590)) &&
                                ((EvsChAOffset > 0) && (EvsChAOffset < 8)))  // ChA case.
                        {
                            if (nOriginBW == kEvsBandwidthSWB)
                            {
                                nCodeType = kEvsCmrCodeTypeSwbCha;
                            }
                            else
                            {
                                nCodeType = kEvsCmrCodeTypeWbCha;
                            }

                            switch (EvsChAOffset)
                            {
                                case 2:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH2;
                                    break;
                                case 3:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH3;
                                    break;
                                case 5:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH5;
                                    break;
                                case 7:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH7;
                                    break;
                                default:
                                    IMLOGD3("[DecodePayloadEvs] no selected chmode offset[%d], "
                                            "originBW[%d], originBR[%d]",
                                            EvsChAOffset, nOriginBW, nOriginBR);
                                    nCodeType = kEvsCmrCodeTypeNoReq;
                                    nCodeDefine = kEvsCmrCodeDefineNoReq;
                                    break;
                            }
                        }
                        else  // primary case
                        {
                            nCodeDefine = (kEvsCmrCodeDefine)nOriginBR;

                            switch (nOriginBW)
                            {
                                case kEvsBandwidthNB:
                                    nCodeType = kEvsCmrCodeTypeNb;
                                    break;
                                case kEvsBandwidthWB:
                                    nCodeType = kEvsCmrCodeTypeWb;
                                    break;
                                case kEvsBandwidthSWB:
                                    nCodeType = kEvsCmrCodeTypeSwb;
                                    break;
                                case kEvsBandwidthFB:
                                    nCodeType = kEvsCmrCodeTypeFb;
                                    break;
                                default:
                                    IMLOGD2("[DecodePayloadEvs] no CodeType - primary mode, "
                                            "originBW[%d], "
                                            "originBR[%d]",
                                            nOriginBW, nOriginBR);
                                    nCodeType = kEvsCmrCodeTypeNoReq;
                                    nCodeDefine = kEvsCmrCodeDefineNoReq;
                                    break;
                            }
                        }

                        // process CMR
                        ProcessCMRForEVS(
                                kRtpPayloadHeaderModeEvsHeaderFull, nCodeType, nCodeDefine);

                        // Save Prev CMR value for checking
                        mPrevCMR = cmr;
                    }
                }
            }

            mBitReader.ReadByteBuffer(mPayload, nDataBitSize);

            // last data bit is speech first bit..
            if (nFrameType != kImsAudioAmrWbModeSID)
            {
                uint8_t nLastBit0 = 0;
                uint32_t i = 0;
                uint32_t remain = 0;

                remain = nDataBitSize % 8;
                if (remain == 0)
                {
                    nLastBit0 = mPayload[nDataSize - 1] & 0x01;
                }
                else
                {
                    nLastBit0 = (mPayload[nDataSize - 1] << (remain - 1)) >> 7;
                    mPayload[nDataSize - 1] = (mPayload[nDataSize - 1] >> (9 - remain))
                            << (9 - remain);
                }

                for (i = (nDataSize - 1); i > 0; i--)
                {
                    mPayload[i] = mPayload[i] >> 1;
                    mPayload[i] = mPayload[i] + ((mPayload[i - 1] & 0x01) << 7);
                }
                mPayload[0] = (mPayload[0] >> 1);
                mPayload[0] = mPayload[0] + (nLastBit0 << 7);
            }

            IMLOGD_PACKET6(IM_PACKET_LOG_PH,
                    "[DecodePayloadEvs] result = %02X %02X %02X %02X, len=%d, nFrameType=%d",
                    mPayload[0], mPayload[1], mPayload[2], mPayload[3], nDataSize, nFrameType);

            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, nDataSize, timestamp, bMark,
                    nSeqNum, subType, arrivalTime);
        }
        else
        {
            IMLOGI0("[DecodePayloadEvs] Invalid codec mode");
            return;
        }
    }
    else if (eEVSReceivedPHFormat == kRtpPayloadHeaderModeEvsHeaderFull)
    {
        do
        {
            h = mBitReader.Read(1);
            toc_f = 1;

            if (h == 1)  // CMR byte
            {
                cmr_t = mBitReader.Read(3);
                cmr_d = mBitReader.Read(4);
                uint32_t currCmr = (cmr_t << 4) + cmr_d;

                if (currCmr != mPrevCMR)
                {
                    // process cmr
                    if (currCmr != 127)
                    {
                        IMLOGI0("[DecodePayloadEvs] Process CMR");
                        // Process CMR
                        ProcessCMRForEVS(kRtpPayloadHeaderModeEvsHeaderFull, (kEvsCmrCodeType)cmr_t,
                                (kEvsCmrCodeDefine)cmr_d);

                        // Save Prev CMR value for checking
                        mPrevCMR = currCmr;
                    }
                    else
                    {
                        uint32_t nOriginBW =
                                ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(mEvsBandwidth);
                        uint32_t nOriginBR = mEvsMode;
                        kEvsCmrCodeType nCodeType = kEvsCmrCodeTypeNoReq;
                        kEvsCmrCodeDefine nCodeDefine = kEvsCmrCodeDefineNoReq;

                        if (mEvsCodecMode == kEvsCodecModePrimary)
                        {
                            // to convert evs primary mode
                            nOriginBR -= (uint32_t)kEvsPrimaryModeBitrate00590;
                        }

                        // check AMR IO and ChA
                        if (mEvsCodecMode == kEvsCodecModeAmrIo)
                        {
                            nCodeType = kEvsCmrCodeTypeAmrIO;
                            nCodeDefine = (kEvsCmrCodeDefine)nOriginBR;
                        }
                        else if ((kEvsPrimaryModeBitrate01320 ==
                                         (nOriginBR + (uint32_t)kEvsPrimaryModeBitrate00590)) &&
                                ((EvsChAOffset > 0) && (EvsChAOffset < 8)))  // ChA case.
                        {
                            if (nOriginBW == (uint32_t)kEvsBandwidthSWB)
                            {
                                nCodeType = kEvsCmrCodeTypeSwbCha;
                            }
                            else
                            {
                                nCodeType = kEvsCmrCodeTypeWbCha;
                            }

                            switch (EvsChAOffset)
                            {
                                case 2:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH2;
                                    break;
                                case 3:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH3;
                                    break;
                                case 5:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH5;
                                    break;
                                case 7:
                                    nCodeDefine = kEvsCmrCodeDefineChaOffsetH7;
                                    break;
                                default:
                                    IMLOGI3("[DecodePayloadEvs] No selected chmode offset[%d], "
                                            "originBW[%d], originBR[%d]",
                                            EvsChAOffset, nOriginBW, nOriginBR);
                                    nCodeType = kEvsCmrCodeTypeNoReq;
                                    nCodeDefine = kEvsCmrCodeDefineNoReq;
                                    break;
                            }
                        }
                        else  // primary case
                        {
                            nCodeDefine = (kEvsCmrCodeDefine)nOriginBR;

                            switch (nOriginBW)
                            {
                                case 0:
                                    nCodeType = kEvsCmrCodeTypeNb;
                                    break;
                                case 1:
                                    nCodeType = kEvsCmrCodeTypeWb;
                                    break;
                                case 2:
                                    nCodeType = kEvsCmrCodeTypeSwb;
                                    break;
                                case 3:
                                    nCodeType = kEvsCmrCodeTypeFb;
                                    break;
                                default:
                                    IMLOGI2("[DecodePayloadEvs] No CodeType - primary mode, "
                                            "originBW[%d], "
                                            "originBR[%d]",
                                            nOriginBW, nOriginBR);
                                    nCodeType = kEvsCmrCodeTypeNoReq;
                                    nCodeDefine = kEvsCmrCodeDefineNoReq;
                                    break;
                            }
                        }

                        // process CMR
                        ProcessCMRForEVS(
                                kRtpPayloadHeaderModeEvsHeaderFull, nCodeType, nCodeDefine);

                        // Save Prev CMR value for checking
                        mPrevCMR = currCmr;
                    }
                }
            }
            else  // ToC byte
            {
                toc_f = mBitReader.Read(1);
                toc_ft_m = mBitReader.Read(1);
                toc_ft_q = mBitReader.Read(1);
                toc_ft_b = mBitReader.Read(4);

                mListFrameType.push_back(toc_ft_b);
            }
        } while (toc_f == 1);

        //
        // read speech frames
        //
        while (mListFrameType.size() > 0)
        {
            mListFrameType.pop_front();

            if (toc_ft_m == 0)  // EVS Primary mode
            {
                nDataBitSize =
                        ImsMediaAudioUtil::ConvertEVSAudioModeToBitLen((kImsAudioEvsMode)toc_ft_b);
            }
            else  // AMR-WB IO mode
            {
                nDataBitSize = ImsMediaAudioUtil::ConvertAmrWbModeToBitLen(toc_ft_b);
            }

            mBitWriter.SetBuffer(mPayload, MAX_AUDIO_PAYLOAD_SIZE);
            uint32_t bufferSize = (nDataBitSize + 7) >> 3;

            // set TOC
            mBitWriter.Write(h, 1);
            mBitWriter.Write(toc_f, 1);
            mBitWriter.Write(toc_ft_m, 1);
            mBitWriter.Write(toc_ft_q, 1);
            mBitWriter.Write(toc_ft_b, 4);
            mBitReader.ReadByteBuffer(mPayload + 1, nDataBitSize);
            bufferSize++;

            // remove padding bit
            {
                uint32_t nPaddingSize;
                nPaddingSize = (8 - (nDataBitSize & 0x07)) & 0x07;
                mBitReader.Read(nPaddingSize);
            }

            IMLOGD_PACKET6(IM_PACKET_LOG_PH,
                    "[DecodePayloadEvs] result = %02X %02X %02X %02X, len=%d, eRate=%d",
                    mPayload[0], mPayload[1], mPayload[2], mPayload[3], bufferSize, toc_ft_b);

            SendDataToRearNode(MEDIASUBTYPE_RTPPAYLOAD, mPayload, bufferSize, timestamp,
                    mListFrameType.size(), nSeqNum, subType, arrivalTime);

            timestamp += 20;
        }
    }
    else
    {
        IMLOGE0("[DecodePayloadEvs] Invalid payload format");
        return;
    }
}

bool AudioRtpPayloadDecoderNode::ProcessCMRForEVS(
        kRtpPayloadHeaderMode eEVSPayloadHeaderMode, kEvsCmrCodeType cmr_t, kEvsCmrCodeDefine cmr_d)
{
    kEvsCmrCodeType eNewEVSCMRCodeType = kEvsCmrCodeTypeNoReq;
    kEvsCmrCodeDefine eNewEVSCMRCodeDefine = kEvsCmrCodeDefineNoReq;

    if (eEVSPayloadHeaderMode == kRtpPayloadHeaderModeEvsHeaderFull)
    {
        if (cmr_t < 8)  // cmr_type is 3bit validation.
        {
            eNewEVSCMRCodeType = cmr_t;
        }

        if (cmr_d < 16)  // cmr_define is 4bit validation
        {
            eNewEVSCMRCodeDefine = cmr_d;
        }
    }
    else if (eEVSPayloadHeaderMode == kRtpPayloadHeaderModeEvsCompact)  // only EVS AMR IO Mode
    {
        eNewEVSCMRCodeType = kEvsCmrCodeTypeAmrIO;

        switch (cmr_d)
        {
            case 0:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo660;
                break;
            case 1:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo885;
                break;
            case 2:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo1265;
                break;
            case 3:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo1585;
                break;
            case 4:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo1825;
                break;
            case 5:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo2305;
                break;
            case 6:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineAmrIo2385;
                break;
            case 7:
            default:
                eNewEVSCMRCodeDefine = kEvsCmrCodeDefineNoReq;
                break;
        }
    }
    else
    {
        IMLOGI0("[ProcessCMRForEVS] Invalid EVS codec mode");
        return false;
    }

    if (eNewEVSCMRCodeDefine == kEvsCmrCodeDefineNoReq)
    {
        IMLOGI0("[ProcessCMRForEVS] Invalid CMR Value");
        return true;
    }

    IMLOGD2("[ProcessCMRForEVS] Change request bnadwidth[%d], bitrate[%d]", eNewEVSCMRCodeType,
            eNewEVSCMRCodeDefine);

    if (mCallback)
    {
        mCallback->SendEvent(kRequestAudioCmrEvs, eNewEVSCMRCodeType, eNewEVSCMRCodeDefine);
    }

    return true;
}

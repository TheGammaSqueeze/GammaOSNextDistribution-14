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

#include <IAudioPlayerNode.h>
#include <ImsMediaAudioPlayer.h>
#include <ImsMediaDefine.h>
#include <ImsMediaTrace.h>
#include <ImsMediaTimer.h>
#include <ImsMediaAudioUtil.h>
#include <AudioConfig.h>
#include <AudioJitterBuffer.h>
#include <string.h>

#define MAX_CODEC_EVS_AMR_IO_MODE 9
#define JITTER_BUFFER_SIZE_INIT   3
#define JITTER_BUFFER_SIZE_MIN    3
#define JITTER_BUFFER_SIZE_MAX    11

IAudioPlayerNode::IAudioPlayerNode(BaseSessionCallback* callback) :
        JitterBufferControlNode(callback, IMS_MEDIA_AUDIO)
{
    std::unique_ptr<ImsMediaAudioPlayer> track(new ImsMediaAudioPlayer());
    mAudioPlayer = std::move(track);
    mConfig = nullptr;
    mAnbrDownlinkMode = 0;
    mIsOctetAligned = false;
    mIsDtxEnabled = false;
}

IAudioPlayerNode::~IAudioPlayerNode()
{
    if (mConfig != nullptr)
    {
        delete mConfig;
    }
}

kBaseNodeId IAudioPlayerNode::GetNodeId()
{
    return kNodeIdAudioPlayer;
}

ImsMediaResult IAudioPlayerNode::Start()
{
    IMLOGD2("[Start] codec[%d], mode[%d]", mCodecType, mMode);

    if (mJitterBuffer)
    {
        mJitterBuffer->SetCodecType(mCodecType);
        reinterpret_cast<AudioJitterBuffer*>(mJitterBuffer)
                ->SetEvsRedundantFrameOffset(mEvsChannelAwOffset);
    }

    // reset the jitter
    Reset();

    if (mAudioPlayer)
    {
        mAudioPlayer->SetCodec(mCodecType);
        mAudioPlayer->SetSamplingRate(mSamplingRate * 1000);
        mAudioPlayer->SetDtxEnabled(mIsDtxEnabled);
        mAudioPlayer->SetOctetAligned(mIsOctetAligned);
        int mode = (mCodecType == kAudioCodecEvs)
                ? ImsMediaAudioUtil::GetMaximumEvsMode(mMode)
                : ImsMediaAudioUtil::GetMaximumAmrMode(mCodecType, mMode);

        if (mCodecType == kAudioCodecEvs)
        {
            mAudioPlayer->SetEvsBandwidth((int32_t)mEvsBandwidth);
            mAudioPlayer->SetEvsPayloadHeaderMode(mEvsPayloadHeaderMode);
            mAudioPlayer->SetEvsChAwOffset(mEvsChannelAwOffset);
            mRunningCodecMode = ImsMediaAudioUtil::GetMaximumEvsMode(mMode);
            mAudioPlayer->SetEvsBitRate(
                    ImsMediaAudioUtil::ConvertEVSModeToBitRate(mRunningCodecMode));
            mAudioPlayer->SetCodecMode(mRunningCodecMode);
        }
        mAudioPlayer->SetCodecMode(mode);

        mAudioPlayer->Start();
    }
    else
    {
        IMLOGE0("[IAudioPlayer] Not able to start AudioPlayer");
    }

    mNodeState = kNodeStateRunning;
    StartThread("IAudioPlayerNode");
    return RESULT_SUCCESS;
}

void IAudioPlayerNode::Stop()
{
    IMLOGD0("[Stop]");
    StopThread();
    mCondition.reset();
    mCondition.wait_timeout(AUDIO_STOP_TIMEOUT);

    if (mAudioPlayer)
    {
        mAudioPlayer->Stop();
    }

    mNodeState = kNodeStateStopped;
}

bool IAudioPlayerNode::IsRunTime()
{
    return true;
}

bool IAudioPlayerNode::IsSourceNode()
{
    return false;
}

void IAudioPlayerNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    if (mConfig != nullptr)
    {
        delete mConfig;
        mConfig = nullptr;
    }

    mConfig = new AudioConfig(*static_cast<AudioConfig*>(config));
    mCodecType = ImsMediaAudioUtil::ConvertCodecType(mConfig->getCodecType());

    if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
    {
        mMode = mConfig->getAmrParams().getAmrMode();
        mIsOctetAligned = mConfig->getAmrParams().getOctetAligned();
    }
    else if (mCodecType == kAudioCodecEvs)
    {
        mMode = mConfig->getEvsParams().getEvsMode();
        mEvsChannelAwOffset = mConfig->getEvsParams().getChannelAwareMode();
        mEvsBandwidth = ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(
                mConfig->getEvsParams().getEvsBandwidth());
        mEvsPayloadHeaderMode = mConfig->getEvsParams().getUseHeaderFullOnly();
        mAnbrDownlinkMode = mConfig->getAnbrMode().getAnbrDownlinkCodecMode();

        if (mAnbrDownlinkMode != 0)
        {
            // TODO: need to send cmr bit using HF only mode
            IMLOGI1("[IAudioPlayerNode] DownlinkMode: %d", mAnbrDownlinkMode);
        }
    }

    mSamplingRate = mConfig->getSamplingRateKHz();
    mIsDtxEnabled = mConfig->getDtxEnabled();

    // set the jitter buffer size
    SetJitterBufferSize(JITTER_BUFFER_SIZE_INIT, JITTER_BUFFER_SIZE_MIN, JITTER_BUFFER_SIZE_MAX);
}

bool IAudioPlayerNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);

    if (mCodecType == ImsMediaAudioUtil::ConvertCodecType(pConfig->getCodecType()))
    {
        if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
        {
            return (mMode == pConfig->getAmrParams().getAmrMode() &&
                    mSamplingRate == pConfig->getSamplingRateKHz() &&
                    mIsDtxEnabled == pConfig->getDtxEnabled() &&
                    mIsOctetAligned == pConfig->getAmrParams().getOctetAligned());
        }
        else if (mCodecType == kAudioCodecEvs)
        {
            return (mMode == pConfig->getEvsParams().getEvsMode() &&
                    mEvsBandwidth ==
                            ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(
                                    pConfig->getEvsParams().getEvsBandwidth()) &&
                    mEvsChannelAwOffset == pConfig->getEvsParams().getChannelAwareMode() &&
                    mSamplingRate == pConfig->getSamplingRateKHz() &&
                    mEvsPayloadHeaderMode == pConfig->getEvsParams().getUseHeaderFullOnly() &&
                    mIsDtxEnabled == pConfig->getDtxEnabled());
        }
    }

    return false;
}

void IAudioPlayerNode::ProcessCmr(const uint32_t cmrType, const uint32_t cmrDefine)
{
    IMLOGD2("[ProcessCmr] cmr type[%d], cmrDefine[%d]", cmrType, cmrDefine);

    if (mAudioPlayer == nullptr)
    {
        return;
    }

    if (mCodecType == kAudioCodecEvs)
    {
        if (cmrType == kEvsCmrCodeTypeNoReq || cmrDefine == kEvsCmrCodeDefineNoReq)
        {
            int32_t mode = ImsMediaAudioUtil::GetMaximumEvsMode(mMode);

            if (mRunningCodecMode != mode)
            {
                mAudioPlayer->ProcessCmr(mode);
                mRunningCodecMode = mode;
            }
        }
        else
        {
            int32_t mode = MAX_CODEC_EVS_AMR_IO_MODE;

            IMLOGD1("[ProcessCmr] running codec mode[%d]", mRunningCodecMode);

            if ((cmrDefine + mode) > mRunningCodecMode)
            {
                IMLOGD1("[ProcessCmr] Skip to change the bitrate for ANBR codec Mode : %d",
                        (cmrDefine + mode));
            }
            else
            {
                switch (cmrType)
                {
                    case kEvsCmrCodeTypeNb:
                        mEvsBandwidth = kEvsBandwidthNB;
                        mode += cmrDefine;
                        break;
                    case kEvsCmrCodeTypeWb:
                        mEvsBandwidth = kEvsBandwidthWB;
                        mode += cmrDefine;
                        break;
                    case kEvsCmrCodeTypeSwb:
                        mEvsBandwidth = kEvsBandwidthSWB;
                        mode += cmrDefine;
                        break;
                    case kEvsCmrCodeTypeFb:
                        mEvsBandwidth = kEvsBandwidthFB;
                        mode += cmrDefine;
                        break;
                    case kEvsCmrCodeTypeWbCha:
                        mEvsBandwidth = kEvsBandwidthWB;
                        mode = kImsAudioEvsPrimaryMode13200;
                        break;
                    case kEvsCmrCodeTypeSwbCha:
                        mEvsBandwidth = kEvsBandwidthSWB;
                        mode = kImsAudioEvsPrimaryMode13200;
                        break;
                    case kEvsCmrCodeTypeAmrIO:
                        mode = cmrDefine;
                        break;
                    default:
                        break;
                }

                if (cmrType == kEvsCmrCodeTypeWbCha || cmrType == kEvsCmrCodeTypeSwbCha)
                {
                    switch (cmrDefine)
                    {
                        case kEvsCmrCodeDefineChaOffset2:
                        case kEvsCmrCodeDefineChaOffsetH2:
                            mEvsChannelAwOffset = 2;
                            break;
                        case kEvsCmrCodeDefineChaOffset3:
                        case kEvsCmrCodeDefineChaOffsetH3:
                            mEvsChannelAwOffset = 3;
                            break;
                        case kEvsCmrCodeDefineChaOffset5:
                        case kEvsCmrCodeDefineChaOffsetH5:
                            mEvsChannelAwOffset = 5;
                            break;
                        case kEvsCmrCodeDefineChaOffset7:
                        case kEvsCmrCodeDefineChaOffsetH7:
                            mEvsChannelAwOffset = 7;
                            break;
                        default:
                            mEvsChannelAwOffset = 3;
                            break;
                    }
                }

                mAudioPlayer->SetEvsBandwidth((int32_t)mEvsBandwidth);
                mAudioPlayer->SetEvsChAwOffset(mEvsChannelAwOffset);

                if (mode != mRunningCodecMode)
                {
                    mRunningCodecMode = mode;
                    mAudioPlayer->SetEvsBitRate(
                            ImsMediaAudioUtil::ConvertEVSModeToBitRate(mRunningCodecMode));
                    mAudioPlayer->SetCodecMode(mRunningCodecMode);
                }

                mAudioPlayer->ProcessCmr(mRunningCodecMode);
            }
        }
    }
}

void IAudioPlayerNode::AdjustDelay(const int32_t delayMs)
{
    if (mJitterBuffer != nullptr)
    {
        reinterpret_cast<AudioJitterBuffer*>(mJitterBuffer)->SetAdditionalDelay(delayMs);
    }
}

void* IAudioPlayerNode::run()
{
    IMLOGD0("[run] enter");
    SetThreadPriority(getpid(), gettid(), THREAD_PRIORITY_REALTIME);
    ImsMediaSubType subtype = MEDIASUBTYPE_UNDEFINED;
    ImsMediaSubType datatype = MEDIASUBTYPE_UNDEFINED;
    uint8_t* data = nullptr;
    uint32_t size = 0;
    uint32_t timestamp = 0;
    bool mark = false;
    uint32_t seq = 0;
    uint32_t lastPlayedSeq = 0;
    uint32_t currentTime = 0;
    uint64_t nNextTime = ImsMediaTimer::GetTimeInMicroSeconds();
    bool isFirstFrameReceived = false;

#ifdef FILE_DUMP
    FILE* file = fopen("/data/user_de/0/com.android.telephony.imsmedia/out.amr", "wb");
    const uint8_t noDataHeader = 0x7C;
    const uint8_t amrHeader[] = {0x23, 0x21, 0x41, 0x4d, 0x52, 0x0a};
    const uint8_t amrWbHeader[] = {0x23, 0x21, 0x41, 0x4d, 0x52, 0x2d, 0x57, 0x42, 0x0a};

    if (file)
    {
        // 1st header of AMR-WB file
        if (mCodecType == kAudioCodecAmr)
        {
            fwrite(amrHeader, sizeof(amrHeader), 1, file);
        }
        else if (mCodecType == kAudioCodecAmrWb)
        {
            fwrite(amrWbHeader, sizeof(amrWbHeader), 1, file);
        }
    }
#endif
    while (true)
    {
        if (IsThreadStopped())
        {
            IMLOGD0("[run] terminated");
            break;
        }

        if (GetData(&subtype, &data, &size, &timestamp, &mark, &seq, &datatype, &currentTime))
        {
            IMLOGD_PACKET3(IM_PACKET_LOG_AUDIO, "[run] write buffer size[%u], TS[%u], datatype[%u]",
                    size, timestamp, datatype);
#ifdef FILE_DUMP
            size > 0 ? std::fwrite(data, size, 1, file) : std::fwrite(&noDataHeader, 1, 1, file);
#endif
            lastPlayedSeq = seq;
            FrameType frameType = SPEECH;

            switch (datatype)
            {
                case MEDIASUBTYPE_AUDIO_SID:
                    frameType = SID;
                    break;
                case MEDIASUBTYPE_AUDIO_NODATA:
                    frameType = NO_DATA;
                    break;
                default:
                case MEDIASUBTYPE_AUDIO_NORMAL:
                    frameType = SPEECH;
                    break;
            }

            if (mCallback != nullptr)
            {
                mCallback->SendEvent(kRequestAudioPlayingStatus,
                        frameType == SPEECH ? kAudioTypeVoice : kAudioTypeNoData, 0);
            }

            if (mAudioPlayer->onDataFrame(data, size, frameType, false, 0))
            {
                // send buffering complete message to client
                if (!isFirstFrameReceived && mCallback != nullptr)
                {
                    mCallback->SendEvent(kImsMediaEventFirstPacketReceived,
                            reinterpret_cast<uint64_t>(new AudioConfig(*mConfig)));
                    isFirstFrameReceived = true;
                }
            }

            DeleteData();
        }
        else if (isFirstFrameReceived)
        {
            uint8_t nextFrameByte = 0;
            bool hasNextFrame = false;
            uint32_t lostSeq = lastPlayedSeq + 1;
            if (mRunningCodecMode == kImsAudioEvsPrimaryMode13200 &&
                    (mEvsChannelAwOffset == 2 || mEvsChannelAwOffset == 3 ||
                            mEvsChannelAwOffset == 5 || mEvsChannelAwOffset == 7) &&
                    GetRedundantFrame(lostSeq, &data, &size, &hasNextFrame, &nextFrameByte))
            {
                lastPlayedSeq++;
                mAudioPlayer->onDataFrame(data, size, LOST, hasNextFrame, nextFrameByte);

                if (mCallback != nullptr)
                {
                    mCallback->SendEvent(kRequestAudioPlayingStatus, kAudioTypeVoice, 0);
                }
            }
            else
            {
                IMLOGD_PACKET0(IM_PACKET_LOG_AUDIO, "[run] no data");
                mAudioPlayer->onDataFrame(nullptr, 0, NO_DATA, false, 0);

                if (mCallback != nullptr)
                {
                    mCallback->SendEvent(kRequestAudioPlayingStatus, kAudioTypeNoData, 0);
                }
            }

#ifdef FILE_DUMP
            std::fwrite(&noDataHeader, 1, 1, file);
#endif
        }

        nNextTime += 20000;
        uint64_t nCurrTime = ImsMediaTimer::GetTimeInMicroSeconds();
        int64_t nTime = nNextTime - nCurrTime;

        if (nTime < 0)
        {
            continue;
        }

        ImsMediaTimer::USleep(nTime);
    }
#ifdef FILE_DUMP
    if (file)
    {
        fclose(file);
    }
#endif
    mCondition.signal();
    return nullptr;
}

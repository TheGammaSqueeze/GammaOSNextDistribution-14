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

#include <IAudioSourceNode.h>
#include <ImsMediaAudioSource.h>
#include <ImsMediaTrace.h>
#include <ImsMediaAudioUtil.h>
#include <ImsMediaTimer.h>
#include <string.h>
#include <AudioConfig.h>
#include <RtpConfig.h>
#include <EvsParams.h>
#include <AnbrMode.h>

#define MAX_CODEC_EVS_AMR_IO_MODE 9

IAudioSourceNode::IAudioSourceNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    std::unique_ptr<ImsMediaAudioSource> recoder(new ImsMediaAudioSource());
    mAudioSource = std::move(recoder);
    mCodecType = 0;
    mCodecMode = 0;
    mRunningCodecMode = 0;
    mFirstFrame = false;
    mMediaDirection = 0;
    mAnbrUplinkMode = 0;
    mIsOctetAligned = false;
    mIsDtxEnabled = false;
}

IAudioSourceNode::~IAudioSourceNode() {}

kBaseNodeId IAudioSourceNode::GetNodeId()
{
    return kNodeIdAudioSource;
}

ImsMediaResult IAudioSourceNode::ProcessStart()
{
    IMLOGD2("[ProcessStart] codec[%d], mode[%d]", mCodecType, mCodecMode);

    if (mAudioSource)
    {
        mAudioSource->SetUplinkCallback(this);
        mAudioSource->SetCodec(mCodecType);
        mRunningCodecMode = ImsMediaAudioUtil::GetMaximumAmrMode(mCodecType, mCodecMode);
        mAudioSource->SetPtime(mPtime);
        mAudioSource->SetSamplingRate(mSamplingRate * 1000);
        mAudioSource->SetMediaDirection(mMediaDirection);
        mAudioSource->SetDtxEnabled(mIsDtxEnabled);
        mAudioSource->SetOctetAligned(mIsOctetAligned);

        if (mCodecType == kAudioCodecEvs)
        {
            mAudioSource->SetEvsBandwidth((int32_t)mEvsBandwidth);
            mAudioSource->SetEvsChAwOffset(mEvsChAwOffset);
            mRunningCodecMode = ImsMediaAudioUtil::GetMaximumEvsMode(mCodecMode);
            mAudioSource->SetEvsBitRate(
                    ImsMediaAudioUtil::ConvertEVSModeToBitRate(mRunningCodecMode));
        }
        mAudioSource->SetCodecMode(mRunningCodecMode);
        IMLOGD1("[ProcessStart] running codec mode: %d", mRunningCodecMode);

        if (mAudioSource->Start())
        {
            mNodeState = kNodeStateRunning;
            mFirstFrame = false;
            return RESULT_SUCCESS;
        }
    }
    else
    {
        IMLOGE0("[IAudioSourceNode] Not able to start AudioSource");
    }

    return RESULT_NOT_READY;
}

void IAudioSourceNode::Stop()
{
    IMLOGD0("[Stop]");

    if (mAudioSource)
    {
        mAudioSource->Stop();
    }

    mNodeState = kNodeStateStopped;
}

bool IAudioSourceNode::IsRunTime()
{
    return true;
}

bool IAudioSourceNode::IsRunTimeStart()
{
    return false;
}

bool IAudioSourceNode::IsSourceNode()
{
    return true;
}

void IAudioSourceNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    AudioConfig* pConfig = reinterpret_cast<AudioConfig*>(config);
    mCodecType = ImsMediaAudioUtil::ConvertCodecType(pConfig->getCodecType());

    if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
    {
        mCodecMode = pConfig->getAmrParams().getAmrMode();
        mIsOctetAligned = pConfig->getAmrParams().getOctetAligned();
    }
    else if (mCodecType == kAudioCodecEvs)
    {
        mCodecMode = pConfig->getEvsParams().getEvsMode();
        mEvsBandwidth = ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(
                pConfig->getEvsParams().getEvsBandwidth());
        mEvsChAwOffset = pConfig->getEvsParams().getChannelAwareMode();

        mAnbrUplinkMode = pConfig->getAnbrMode().getAnbrUplinkCodecMode();

        if (mAnbrUplinkMode != 0 && mCodecMode != mAnbrUplinkMode)
        {
            mCodecMode = mAnbrUplinkMode;
            IMLOGI2("[SetConfig] new codec mode: %d, uplink mode: %d", mCodecMode, mAnbrUplinkMode);
        }
    }

    mMediaDirection = pConfig->getMediaDirection();
    mSamplingRate = pConfig->getSamplingRateKHz();
    mPtime = pConfig->getPtimeMillis();
    mIsDtxEnabled = pConfig->getDtxEnabled();
}

bool IAudioSourceNode::IsSameConfig(void* config)
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
            return (mCodecMode == pConfig->getAmrParams().getAmrMode() &&
                    mSamplingRate == pConfig->getSamplingRateKHz() &&
                    mMediaDirection == pConfig->getMediaDirection() &&
                    mIsDtxEnabled == pConfig->getDtxEnabled() &&
                    mIsOctetAligned == pConfig->getAmrParams().getOctetAligned());
        }
        else if (mCodecType == kAudioCodecEvs)
        {
            return (mCodecMode == pConfig->getEvsParams().getEvsMode() &&
                    mEvsBandwidth ==
                            ImsMediaAudioUtil::FindMaxEvsBandwidthFromRange(
                                    pConfig->getEvsParams().getEvsBandwidth()) &&
                    mEvsChAwOffset == pConfig->getEvsParams().getChannelAwareMode() &&
                    mSamplingRate == pConfig->getSamplingRateKHz() &&
                    mMediaDirection == pConfig->getMediaDirection() &&
                    mIsDtxEnabled == pConfig->getDtxEnabled());
        }
    }

    return false;
}

void IAudioSourceNode::onDataFrame(uint8_t* buffer, uint32_t size, int64_t timestamp, uint32_t flag)
{
    IMLOGD_PACKET3(IM_PACKET_LOG_AUDIO, "[onDataFrame] size[%zu], TS[%ld], flag[%d]", size,
            timestamp, flag);
    SendDataToRearNode(MEDIASUBTYPE_UNDEFINED, buffer, size, ImsMediaTimer::GetTimeInMilliSeconds(),
            !mFirstFrame, 0, MEDIASUBTYPE_UNDEFINED, ImsMediaTimer::GetTimeInMilliSeconds());

    if (!mFirstFrame)
    {
        mFirstFrame = true;
    }
}

void IAudioSourceNode::ProcessCmr(const uint32_t cmrType, const uint32_t cmrDefine)
{
    IMLOGD2("[ProcessCmr] cmr type[%d], cmrDefine[%d]", cmrType, cmrDefine);

    if (mAudioSource == nullptr)
    {
        return;
    }

    if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
    {
        if (cmrType == 15)  // change mode to original one
        {
            int32_t mode = ImsMediaAudioUtil::GetMaximumAmrMode(mCodecType, mCodecMode);

            if (mRunningCodecMode != mode)
            {
                mAudioSource->ProcessCmr(mode);
                mRunningCodecMode = mode;
            }
        }
        else
        {
            if (mRunningCodecMode != cmrType)
            {
                if (cmrType > mRunningCodecMode && mAnbrUplinkMode == 0)
                {
                    triggerAnbrQuery(cmrType);
                }
                mAudioSource->ProcessCmr(cmrType);
                mRunningCodecMode = cmrType;
            }
        }
    }
    else if (mCodecType == kAudioCodecEvs)
    {
        if (cmrType == kEvsCmrCodeTypeNoReq || cmrDefine == kEvsCmrCodeDefineNoReq)
        {
            int32_t mode = ImsMediaAudioUtil::GetMaximumEvsMode(mCodecMode);

            if (mRunningCodecMode != mode)
            {
                mAudioSource->ProcessCmr(mode);
                mRunningCodecMode = mode;
            }
        }
        else
        {
            // triggering ANBR query- build triggerAnbrQuery (with mode: cmr)
            int32_t mode = MAX_CODEC_EVS_AMR_IO_MODE;

            if ((cmrDefine + mode) > mRunningCodecMode && mAnbrUplinkMode == 0)
            {
                triggerAnbrQuery(mode + cmrDefine);
            }

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
                        mEvsChAwOffset = 2;
                        break;
                    case kEvsCmrCodeDefineChaOffset3:
                    case kEvsCmrCodeDefineChaOffsetH3:
                        mEvsChAwOffset = 3;
                        break;
                    case kEvsCmrCodeDefineChaOffset5:
                    case kEvsCmrCodeDefineChaOffsetH5:
                        mEvsChAwOffset = 5;
                        break;
                    case kEvsCmrCodeDefineChaOffset7:
                    case kEvsCmrCodeDefineChaOffsetH7:
                        mEvsChAwOffset = 7;
                        break;
                    default:
                        mEvsChAwOffset = 3;
                        break;
                }
            }

            mAudioSource->SetEvsBandwidth((int32_t)mEvsBandwidth);
            mAudioSource->SetEvsChAwOffset(mEvsChAwOffset);

            if (mode != mRunningCodecMode)
            {
                mRunningCodecMode = mode;
                mAudioSource->SetEvsBitRate(
                        ImsMediaAudioUtil::ConvertEVSModeToBitRate(mRunningCodecMode));
                mAudioSource->SetCodecMode(mRunningCodecMode);
            }

            mAudioSource->ProcessCmr(mRunningCodecMode);
        }
    }
}

void IAudioSourceNode::triggerAnbrQuery(uint32_t cmr)
{
    AnbrMode param;

    IMLOGD1("[triggerAnbrQuery] - mode : %d", cmr);

    param.setAnbrUplinkCodecMode(cmr);
    param.setAnbrDownlinkCodecMode(0);

    AudioConfig* audioConfig = new AudioConfig();
    audioConfig->setAnbrMode(param);

    mCallback->SendEvent(kAudioTriggerAnbrQueryInd, reinterpret_cast<uint64_t>(audioConfig));
}

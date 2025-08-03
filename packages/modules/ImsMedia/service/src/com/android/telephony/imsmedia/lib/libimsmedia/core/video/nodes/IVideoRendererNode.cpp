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

#include <IVideoRendererNode.h>
#include <ImsMediaVideoRenderer.h>
#include <ImsMediaTrace.h>
#include <ImsMediaTimer.h>
#include <ImsMediaBitReader.h>
#include <VideoConfig.h>
#include <ImsMediaVideoUtil.h>
#include <VideoJitterBuffer.h>
#include <string.h>

using namespace android::telephony::imsmedia;

#define DEFAULT_UNDEFINED (-1)

IVideoRendererNode::IVideoRendererNode(BaseSessionCallback* callback) :
        JitterBufferControlNode(callback, IMS_MEDIA_VIDEO)
{
    std::unique_ptr<ImsMediaVideoRenderer> renderer(new ImsMediaVideoRenderer());
    mVideoRenderer = std::move(renderer);
    mVideoRenderer->SetSessionCallback(mCallback);

    if (mJitterBuffer)
    {
        mJitterBuffer->SetSessionCallback(mCallback);
    }

    mWindow = nullptr;
    mCondition.reset();
    mCodecType = DEFAULT_UNDEFINED;
    mWidth = 0;
    mHeight = 0;
    mSamplingRate = 0;
    mCvoValue = 0;
    memset(mConfigBuffer, 0, MAX_CONFIG_INDEX * MAX_CONFIG_LEN * sizeof(uint8_t));
    memset(mConfigLen, 0, MAX_CONFIG_INDEX * sizeof(uint32_t));
    mDeviceOrientation = 0;
    mFirstFrame = false;
    mSubtype = MEDIASUBTYPE_UNDEFINED;
    mFramerate = 0;
    mLossDuration = 0;
    mLossRateThreshold = 0;
}

IVideoRendererNode::~IVideoRendererNode() {}

kBaseNodeId IVideoRendererNode::GetNodeId()
{
    return kNodeIdVideoRenderer;
}

ImsMediaResult IVideoRendererNode::Start()
{
    IMLOGD1("[Start] codec[%d]", mCodecType);
    if (mJitterBuffer)
    {
        VideoJitterBuffer* jitter = reinterpret_cast<VideoJitterBuffer*>(mJitterBuffer);
        jitter->SetCodecType(mCodecType);
        jitter->SetFramerate(mFramerate);
        jitter->SetJitterBufferSize(15, 15, 25);
        jitter->StartTimer(mLossDuration / 1000, mLossRateThreshold);
    }

    Reset();
    std::lock_guard<std::mutex> guard(mMutex);

    if (mVideoRenderer)
    {
        mVideoRenderer->SetCodec(mCodecType);
        mVideoRenderer->SetResolution(mWidth, mHeight);
        mVideoRenderer->SetDeviceOrientation(mDeviceOrientation);
        mVideoRenderer->SetSurface(mWindow);

        if (!mVideoRenderer->Start())
        {
            return RESULT_NOT_READY;
        }
    }

    mFirstFrame = false;
    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void IVideoRendererNode::Stop()
{
    IMLOGD0("[Stop]");
    std::lock_guard<std::mutex> guard(mMutex);

    if (mVideoRenderer)
    {
        mVideoRenderer->Stop();
    }

    if (mJitterBuffer != nullptr)
    {
        VideoJitterBuffer* jitter = reinterpret_cast<VideoJitterBuffer*>(mJitterBuffer);
        jitter->StopTimer();
    }

    mNodeState = kNodeStateStopped;
}

bool IVideoRendererNode::IsRunTime()
{
    return false;
}

bool IVideoRendererNode::IsSourceNode()
{
    return false;
}

void IVideoRendererNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    mCodecType = ImsMediaVideoUtil::ConvertCodecType(pConfig->getCodecType());
    mSamplingRate = pConfig->getSamplingRateKHz();
    mWidth = pConfig->getResolutionWidth();
    mHeight = pConfig->getResolutionHeight();
    mCvoValue = pConfig->getCvoValue();
    mDeviceOrientation = pConfig->getDeviceOrientationDegree();
    mFramerate = pConfig->getFramerate();
}

bool IVideoRendererNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    return (mCodecType == ImsMediaVideoUtil::ConvertCodecType(pConfig->getCodecType()) &&
            mWidth == pConfig->getResolutionWidth() && mHeight == pConfig->getResolutionHeight() &&
            mCvoValue == pConfig->getCvoValue() &&
            mDeviceOrientation == pConfig->getDeviceOrientationDegree() &&
            mSamplingRate == pConfig->getSamplingRateKHz());
}

void IVideoRendererNode::ProcessData()
{
    std::lock_guard<std::mutex> guard(mMutex);
    uint8_t* data = nullptr;
    uint32_t dataSize = 0;
    uint32_t prevTimestamp = 0;
    bool mark = false;
    uint32_t seq = 0;
    uint32_t timestamp = 0;
    uint32_t frameSize = 0;
    ImsMediaSubType subtype = MEDIASUBTYPE_UNDEFINED;
    uint32_t initialSeq = 0;
    ImsMediaSubType dataType;

    while (GetData(&subtype, &data, &dataSize, &timestamp, &mark, &seq, &dataType))
    {
        IMLOGD_PACKET4(IM_PACKET_LOG_VIDEO,
                "[ProcessData] subtype[%d], Size[%d], TS[%d] frameSize[%d]", subtype, dataSize,
                timestamp, frameSize);

        if (prevTimestamp == 0)
        {
            prevTimestamp = timestamp;
        }
        else if (timestamp != prevTimestamp || (frameSize != 0 && hasStartingCode(data, dataSize)))
        {
            // break when the timestamp is changed or next data has another starting code
            break;
        }

        if (dataSize >= MAX_RTP_PAYLOAD_BUFFER_SIZE)
        {
            IMLOGE1("[ProcessData] exceed buffer size[%d]", dataSize);
            return;
        }

        memcpy(mBuffer + frameSize, data, dataSize);
        frameSize += dataSize;

        if (initialSeq == 0)
        {
            initialSeq = seq;
        }

        DeleteData();

        if (mark)
        {
            break;
        }
    }

    if (frameSize == 0)
    {
        return;
    }

    // remove AUD nal unit
    uint32_t size = frameSize;
    uint8_t* buffer = mBuffer;
    RemoveAUDNalUnit(mBuffer, frameSize, &buffer, &size);

    FrameType frameType = GetFrameType(buffer, size);

    if (frameType == SPS)
    {
        SaveConfigFrame(buffer, size, kConfigSps);
        tCodecConfig codecConfig;

        if (mCodecType == kVideoCodecAvc)
        {
            if (ImsMediaVideoUtil::ParseAvcSps(buffer, size, &codecConfig))
            {
                CheckResolution(codecConfig.nWidth, codecConfig.nHeight);
            }
        }
        else if (mCodecType == kVideoCodecHevc)
        {
            if (ImsMediaVideoUtil::ParseHevcSps(buffer, size, &codecConfig))
            {
                CheckResolution(codecConfig.nWidth, codecConfig.nHeight);
            }
        }

        return;
    }
    else if (frameType == PPS)
    {
        SaveConfigFrame(buffer, size, kConfigPps);
        return;
    }
    else if (frameType == VPS)
    {
        SaveConfigFrame(buffer, size, kConfigVps);
        return;
    }

    IMLOGD_PACKET2(IM_PACKET_LOG_VIDEO, "[ProcessData] frame type[%d] size[%d]", frameType, size);

    // TODO: Send PLI or FIR when I-frame wasn't received since beginning.

    if (!mFirstFrame)
    {
        IMLOGD0("[ProcessData] notify first frame");
        mFirstFrame = true;

        if (mCallback != nullptr)
        {
            mCallback->SendEvent(kImsMediaEventFirstPacketReceived);

            if (mCvoValue <= 0)
            {
                mCallback->SendEvent(kImsMediaEventResolutionChanged, mWidth, mHeight);
            }
        }
    }

    // cvo
    if (mCvoValue > 0)
    {
        if (mSubtype == MEDIASUBTYPE_UNDEFINED && subtype == MEDIASUBTYPE_UNDEFINED)
        {
            subtype = MEDIASUBTYPE_ROT0;
        }

        // rotation changed
        if (mSubtype != subtype && (subtype >= MEDIASUBTYPE_ROT0 && subtype <= MEDIASUBTYPE_ROT270))
        {
            mSubtype = subtype;
            int degree = 0;

            switch (mSubtype)
            {
                default:
                case MEDIASUBTYPE_ROT0:
                    degree = 0;
                    break;
                case MEDIASUBTYPE_ROT90:
                    degree = 90;
                    break;
                case MEDIASUBTYPE_ROT180:
                    degree = 180;
                    break;
                case MEDIASUBTYPE_ROT270:
                    degree = 270;
                    break;
            }

            mVideoRenderer->UpdatePeerOrientation(degree);
            NotifyPeerDimensionChanged();
        }
    }

    // send config frames before send I frame
    if (frameType == IDR)
    {
        QueueConfigFrame(timestamp);
    }

    uint32_t currentTime = ImsMediaTimer::GetTimeInMilliSeconds();

    if (mPrevTimestamp == 0)
    {
        mPrevTimestamp = currentTime;
    }
    else if (currentTime - mPrevTimestamp > 1000)
    {
        IMLOGD1("[ProcessData] current fps=%u", mCurrentFramerate);
        mPrevTimestamp = currentTime;
        mCurrentFramerate = 0;
    }

    mCurrentFramerate++;
    mVideoRenderer->OnDataFrame(buffer, size, timestamp, false);
}

void IVideoRendererNode::UpdateSurface(ANativeWindow* window)
{
    IMLOGD1("[UpdateSurface] surface[%p]", window);
    mWindow = window;
}

void IVideoRendererNode::UpdateRoundTripTimeDelay(int32_t delay)
{
    IMLOGD1("[UpdateRoundTripTimeDelay] delay[%d]", delay);

    if (mJitterBuffer != nullptr)
    {
        VideoJitterBuffer* jitter = reinterpret_cast<VideoJitterBuffer*>(mJitterBuffer);

        // calculate Response wait time : RWT = RTTD (mm) + 2 * frame duration
        jitter->SetResponseWaitTime((delay / DEMON_NTP2MSEC) + 2 * (1000 / mFramerate));
    }
}

void IVideoRendererNode::SetPacketLossParam(uint32_t time, uint32_t rate)
{
    IMLOGD2("[SetPacketLossParam] time[%d], rate[%d]", time, rate);

    mLossDuration = time;
    mLossRateThreshold = rate;
}

bool IVideoRendererNode::hasStartingCode(uint8_t* buffer, uint32_t bufferSize)
{
    if (bufferSize <= 4)
    {
        return false;
    }

    // Check for NAL unit delimiter 0x00000001
    if (buffer[0] == 0x00 && buffer[1] == 0x00 && buffer[2] == 0x00 && buffer[3] == 0x01)
    {
        return true;
    }

    return false;
}

FrameType IVideoRendererNode::GetFrameType(uint8_t* buffer, uint32_t bufferSize)
{
    if (!hasStartingCode(buffer, bufferSize))
    {
        return UNKNOWN;
    }

    uint8_t nalType = buffer[4];

    switch (mCodecType)
    {
        case kVideoCodecAvc:
        {
            if ((nalType & 0x1F) == 5)
            {
                return IDR;
            }
            else if ((nalType & 0x1F) == 7)
            {
                return SPS;
            }
            else if ((nalType & 0x1F) == 8)
            {
                return PPS;
            }
            else
            {
                return NonIDR;
            }

            break;
        }
        case kVideoCodecHevc:
        {
            if (((nalType >> 1) & 0x3F) == 19 || ((nalType >> 1) & 0x3F) == 20)
            {
                return IDR;
            }
            else if (((nalType >> 1) & 0x3F) == 32)
            {
                return VPS;
            }
            else if (((nalType >> 1) & 0x3F) == 33)
            {
                return SPS;
            }
            else if (((nalType >> 1) & 0x3F) == 34)
            {
                return PPS;
            }
            else
            {
                return NonIDR;
            }

            break;
        }
        default:
            IMLOGE1("[GetFrameType] Invalid video codec type %d", mCodecType);
    }

    return UNKNOWN;
}

void IVideoRendererNode::SaveConfigFrame(uint8_t* pbBuffer, uint32_t nBufferSize, uint32_t eMode)
{
    bool bSPSString = false;
    bool bPPSString = false;

    if (nBufferSize <= 4)
    {
        return;
    }

    IMLOGD_PACKET3(IM_PACKET_LOG_VIDEO, "[SaveConfigFrame] mode[%d], size[%d], data[%s]", eMode,
            nBufferSize,
            ImsMediaTrace::IMTrace_Bin2String(
                    reinterpret_cast<const char*>(pbBuffer), nBufferSize > 52 ? 52 : nBufferSize));

    switch (mCodecType)
    {
        case kVideoCodecAvc:
        {
            uint32_t nCurrSize = 0;
            uint32_t nOffset = 0;
            uint32_t nConfigSize = 0;
            uint8_t* nCurrBuff = pbBuffer;

            while (nCurrSize <= nBufferSize)
            {
                if (nCurrBuff[0] == 0x00 && nCurrBuff[1] == 0x00 && nCurrBuff[2] == 0x00 &&
                        nCurrBuff[3] == 0x01)
                {
                    if (eMode == kConfigSps && !bSPSString && ((nCurrBuff[4] & 0x1F) == 7))
                    {
                        nOffset = nCurrSize;
                        bSPSString = true;
                    }
                    else if (eMode == kConfigPps && !bPPSString && ((nCurrBuff[4] & 0x1F) == 8))
                    {
                        nOffset = nCurrSize;
                        bPPSString = true;
                    }
                    else if (bSPSString || bPPSString)
                    {
                        nConfigSize = nCurrSize - nOffset;
                        break;
                    }
                }

                nCurrBuff++;
                nCurrSize++;
            }

            if ((bSPSString || bPPSString) && nConfigSize == 0)
            {
                nConfigSize = nBufferSize - nOffset;
            }

            IMLOGD_PACKET3(IM_PACKET_LOG_VIDEO,
                    "[SaveConfigFrame] AVC Codec - bSps[%d], bPps[%d], size[%d]", bSPSString,
                    bPPSString, nConfigSize);

            // save
            if (bSPSString || bPPSString)
            {
                uint8_t* pConfigData = nullptr;
                uint32_t nConfigIndex = 0;

                if (eMode == kConfigSps)
                {
                    nConfigIndex = 0;
                }
                else if (eMode == kConfigPps)
                {
                    nConfigIndex = 1;
                }
                else
                {
                    return;
                }

                pConfigData = mConfigBuffer[nConfigIndex];

                if (0 != memcmp(pConfigData, pbBuffer + nOffset, nConfigSize))
                {
                    memcpy(pConfigData, pbBuffer + nOffset, nConfigSize);
                    mConfigLen[nConfigIndex] = nConfigSize;
                }
            }
            break;
        }

        case kVideoCodecHevc:
        {
            uint32_t nCurrSize = 0;
            uint32_t nOffset = 0;
            uint32_t nConfigSize = 0;
            uint8_t* nCurrBuff = pbBuffer;
            bool bVPSString = false;

            while (nCurrSize <= nBufferSize)
            {
                if (nCurrBuff[0] == 0x00 && nCurrBuff[1] == 0x00 && nCurrBuff[2] == 0x00 &&
                        nCurrBuff[3] == 0x01)
                {
                    if (eMode == kConfigVps && !bVPSString && (((nCurrBuff[4] >> 1) & 0x3F) == 32))
                    {
                        nOffset = nCurrSize;
                        bVPSString = true;
                        break;
                    }
                    else if (eMode == kConfigSps && !bSPSString &&
                            (((nCurrBuff[4] >> 1) & 0x3F) == 33))
                    {
                        nOffset = nCurrSize;
                        bSPSString = true;
                        break;
                    }
                    else if (eMode == kConfigPps && !bPPSString &&
                            (((nCurrBuff[4] >> 1) & 0x3F) == 34))
                    {
                        nOffset = nCurrSize;
                        bPPSString = true;
                        break;
                    }
                }

                nCurrBuff++;
                nCurrSize++;
            }

            if (bVPSString || bSPSString || bPPSString)
            {
                if ((nBufferSize - nOffset) > 0)
                {
                    nConfigSize = nBufferSize - nOffset;
                }
            }

            IMLOGD_PACKET4(IM_PACKET_LOG_VIDEO,
                    "[SaveConfigFrame - H265] bVPS[%d], bSPS[%d], bPPS[%d], nConfigSize[%d]",
                    bVPSString, bSPSString, bPPSString, nConfigSize);

            // save
            if (bVPSString || bSPSString || bPPSString)
            {
                uint8_t* pConfigData = nullptr;
                uint32_t nConfigIndex = 0;

                if (eMode == kConfigVps)
                {
                    nConfigIndex = 0;
                }
                else if (eMode == kConfigSps)
                {
                    nConfigIndex = 1;
                }
                else if (eMode == kConfigPps)
                {
                    nConfigIndex = 2;
                }
                else
                {
                    return;
                }

                pConfigData = mConfigBuffer[nConfigIndex];

                if (0 != memcmp(pConfigData, pbBuffer + nOffset, nConfigSize))
                {
                    memcpy(pConfigData, pbBuffer + nOffset, nConfigSize);
                    mConfigLen[nConfigIndex] = nConfigSize;
                }
            }
            break;
        }
        default:
            return;
    }
}

bool IVideoRendererNode::RemoveAUDNalUnit(
        uint8_t* inBuffer, uint32_t inBufferSize, uint8_t** outBuffer, uint32_t* outBufferSize)
{
    bool IsAudUnit = false;
    *outBuffer = inBuffer;
    *outBufferSize = inBufferSize;

    if (inBufferSize <= 4)
    {
        return false;
    }

    switch (mCodecType)
    {
        case kVideoCodecAvc:
        {
            uint32_t currSize = inBufferSize;
            uint8_t* currBuffer = inBuffer;
            uint32_t count = 0;

            while (currSize >= 5 && count <= 12)
            {
                if (IsAudUnit &&
                        (currBuffer[0] == 0x00 && currBuffer[1] == 0x00 && currBuffer[2] == 0x00 &&
                                currBuffer[3] == 0x01))
                {
                    *outBuffer = currBuffer;
                    *outBufferSize = currSize;
                    break;
                }
                if (currBuffer[0] == 0x00 && currBuffer[1] == 0x00 && currBuffer[2] == 0x00 &&
                        currBuffer[3] == 0x01 && currBuffer[4] == 0x09)
                {
                    IsAudUnit = true;
                }

                currBuffer++;
                currSize--;
                count++;
            }
        }
        break;
        case kVideoCodecHevc:
        default:
            return false;
    }

    return IsAudUnit;
}

void IVideoRendererNode::CheckResolution(uint32_t nWidth, uint32_t nHeight)
{
    if ((nWidth != 0 && nWidth != mWidth) || (nHeight != 0 && nHeight != mHeight))
    {
        IMLOGD4("[CheckResolution] resolution change[%dx%d] to [%dx%d]", mWidth, mHeight, nWidth,
                nHeight);
        mWidth = nWidth;
        mHeight = nHeight;

        NotifyPeerDimensionChanged();
    }
}

void IVideoRendererNode::QueueConfigFrame(uint32_t timestamp)
{
    uint32_t nNumOfConfigString = 0;
    if (mCodecType == kVideoCodecAvc)
    {
        nNumOfConfigString = 2;
    }
    else if (mCodecType == kVideoCodecHevc)
    {
        nNumOfConfigString = 3;
    }

    for (int32_t i = 0; i < nNumOfConfigString; i++)
    {
        uint8_t* configFrame = nullptr;
        uint32_t configLen = mConfigLen[i];
        configFrame = mConfigBuffer[i];

        if (configLen == 0 || mVideoRenderer == nullptr)
        {
            continue;
        }

        mVideoRenderer->OnDataFrame(configFrame, configLen, timestamp, true);
    }
}

void IVideoRendererNode::NotifyPeerDimensionChanged()
{
    if (mCallback == nullptr)
    {
        return;
    }

    IMLOGD1("[NotifyPeerDimensionChanged] subtype[%d]", mSubtype);

    // assume the device is portrait
    if (mWidth > mHeight)  // landscape
    {
        // local rotation
        if (mDeviceOrientation == 0 || mDeviceOrientation == 180)
        {
            // peer rotation
            if (mSubtype == MEDIASUBTYPE_ROT0 || mSubtype == MEDIASUBTYPE_ROT180)
            {
                mCallback->SendEvent(kImsMediaEventResolutionChanged, mWidth, mHeight);
            }
            else if (mSubtype == MEDIASUBTYPE_ROT90 || mSubtype == MEDIASUBTYPE_ROT270)
            {
                mCallback->SendEvent(kImsMediaEventResolutionChanged, mHeight, mWidth);
            }
        }
        else
        {
            // peer rotation
            if (mSubtype == MEDIASUBTYPE_ROT0 || mSubtype == MEDIASUBTYPE_ROT180)
            {
                mCallback->SendEvent(kImsMediaEventResolutionChanged, mHeight, mWidth);
            }
            else if (mSubtype == MEDIASUBTYPE_ROT90 || mSubtype == MEDIASUBTYPE_ROT270)
            {
                mCallback->SendEvent(kImsMediaEventResolutionChanged, mWidth, mHeight);
            }
        }
    }
    else  // portrait
    {
        // peer rotation
        if (mSubtype == MEDIASUBTYPE_ROT0 || mSubtype == MEDIASUBTYPE_ROT180)
        {
            mCallback->SendEvent(kImsMediaEventResolutionChanged, mWidth, mHeight);
        }
        else if (mSubtype == MEDIASUBTYPE_ROT90 || mSubtype == MEDIASUBTYPE_ROT270)
        {
            mCallback->SendEvent(kImsMediaEventResolutionChanged, mHeight, mWidth);
        }
    }
}
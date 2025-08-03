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

#include <IVideoSourceNode.h>
#include <ImsMediaVideoSource.h>
#include <ImsMediaTrace.h>
#include <VideoConfig.h>
#include <string.h>

using namespace android::telephony::imsmedia;

#define DEFAULT_UNDEFINE (-1)

IVideoSourceNode::IVideoSourceNode(BaseSessionCallback* callback) :
        BaseNode(callback)
{
    std::unique_ptr<ImsMediaVideoSource> source(new ImsMediaVideoSource());
    mVideoSource = std::move(source);
    mVideoSource->SetListener(this);
    mCodecType = DEFAULT_UNDEFINE;
    mVideoMode = DEFAULT_UNDEFINE;
    mCodecProfile = VideoConfig::CODEC_PROFILE_NONE;
    mCodecLevel = VideoConfig::CODEC_LEVEL_NONE;
    mCameraId = 0;
    mCameraZoom = 0;
    mWidth = 0;
    mHeight = 0;
    mFramerate = DEFAULT_FRAMERATE;
    mBitrate = DEFAULT_BITRATE;
    mSamplingRate = 0;
    mIntraInterval = 1;
    mImagePath = "";
    mDeviceOrientation = 0;
    mWindow = nullptr;
    mMinBitrateThreshold = 0;
    mBitrateNotified = false;
}

IVideoSourceNode::~IVideoSourceNode() {}

kBaseNodeId IVideoSourceNode::GetNodeId()
{
    return kNodeIdVideoSource;
}

ImsMediaResult IVideoSourceNode::Start()
{
    IMLOGD3("[Start] codec[%d], mode[%d], cameraId[%d]", mCodecType, mVideoMode, mCameraId);

    if (mVideoSource)
    {
        mVideoSource->SetCodecConfig(
                mCodecType, mCodecProfile, mCodecLevel, mBitrate, mFramerate, mIntraInterval);
        mVideoSource->SetVideoMode(mVideoMode);
        mVideoSource->SetResolution(mWidth, mHeight);

        if (mVideoMode == VideoConfig::VIDEO_MODE_PREVIEW ||
                mVideoMode == VideoConfig::VIDEO_MODE_RECORDING)
        {
            mVideoSource->SetCameraConfig(mCameraId, mCameraZoom);

            if (mWindow == nullptr)
            {
                IMLOGE0("[Start] surface is not ready");
                return RESULT_NOT_READY;
            }
        }
        else
        {
            mVideoSource->SetImagePath(mImagePath);
        }

        mVideoSource->SetSurface(mWindow);

        if (!mVideoSource->Start())
        {
            return RESULT_NOT_READY;
        }

        mVideoSource->SetDeviceOrientation(mDeviceOrientation);
        mBitrateNotified = false;
    }

    mNodeState = kNodeStateRunning;
    return RESULT_SUCCESS;
}

void IVideoSourceNode::Stop()
{
    IMLOGD0("[Stop]");

    if (mVideoSource)
    {
        mVideoSource->Stop();
    }

    ClearDataQueue();
    mNodeState = kNodeStateStopped;
}

bool IVideoSourceNode::IsRunTime()
{
    return false;
}

bool IVideoSourceNode::IsSourceNode()
{
    return true;
}

void IVideoSourceNode::SetConfig(void* config)
{
    if (config == nullptr)
    {
        return;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);
    mCodecType = ImsMediaVideoUtil::ConvertCodecType(pConfig->getCodecType());
    mVideoMode = pConfig->getVideoMode();
    mSamplingRate = pConfig->getSamplingRateKHz();
    mCodecProfile = pConfig->getCodecProfile();
    mCodecLevel = pConfig->getCodecLevel();
    mFramerate = pConfig->getFramerate();
    mBitrate = pConfig->getBitrate();
    mWidth = pConfig->getResolutionWidth();
    mHeight = pConfig->getResolutionHeight();
    mIntraInterval = pConfig->getIntraFrameInterval();

    if (mVideoMode == VideoConfig::VIDEO_MODE_PREVIEW ||
            mVideoMode == VideoConfig::VIDEO_MODE_RECORDING)
    {
        mCameraId = pConfig->getCameraId();
        mCameraZoom = pConfig->getCameraZoom();
    }
    else
    {
        mImagePath = pConfig->getPauseImagePath();
    }

    mDeviceOrientation = pConfig->getDeviceOrientationDegree();
}

bool IVideoSourceNode::IsSameConfig(void* config)
{
    if (config == nullptr)
    {
        return true;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);

    return (mCodecType == ImsMediaVideoUtil::ConvertCodecType(pConfig->getCodecType()) &&
            mVideoMode == pConfig->getVideoMode() &&
            mSamplingRate == pConfig->getSamplingRateKHz() &&
            mCodecProfile == pConfig->getCodecProfile() &&
            mCodecLevel == pConfig->getCodecLevel() && mFramerate == pConfig->getFramerate() &&
            mBitrate == pConfig->getBitrate() && mCameraId == pConfig->getCameraId() &&
            mCameraZoom == pConfig->getCameraZoom() && mWidth == pConfig->getResolutionWidth() &&
            mHeight == pConfig->getResolutionHeight() &&
            mDeviceOrientation == pConfig->getDeviceOrientationDegree());
}

ImsMediaResult IVideoSourceNode::UpdateConfig(void* config)
{
    IMLOGD1("[UpdateConfig] current mode[%d]", mVideoMode);

    if (config == nullptr)
    {
        return RESULT_INVALID_PARAM;
    }

    bool isRestart = false;

    if (IsSameConfig(config))
    {
        IMLOGD0("[UpdateConfig] no update");
        return RESULT_SUCCESS;
    }

    VideoConfig* pConfig = reinterpret_cast<VideoConfig*>(config);

    if (mCodecType != ImsMediaVideoUtil::ConvertCodecType(pConfig->getCodecType()) ||
            mVideoMode != pConfig->getVideoMode() || mCodecProfile != pConfig->getCodecProfile() ||
            mCodecLevel != pConfig->getCodecLevel() || mFramerate != pConfig->getFramerate() ||
            mCameraId != pConfig->getCameraId() || mWidth != pConfig->getResolutionWidth() ||
            mHeight != pConfig->getResolutionHeight())
    {
        isRestart = true;
    }
    else
    {
        if (mBitrate != pConfig->getBitrate())
        {
            /** TODO: bitrate change */
        }

        if (mDeviceOrientation != pConfig->getDeviceOrientationDegree())
        {
            mDeviceOrientation = pConfig->getDeviceOrientationDegree();
            mVideoSource->SetDeviceOrientation(mDeviceOrientation);
        }

        return RESULT_SUCCESS;
    }

    if (isRestart)
    {
        kBaseNodeState prevState = mNodeState;
        if (mNodeState == kNodeStateRunning)
        {
            Stop();
        }

        // reset the parameters
        SetConfig(config);

        if (prevState == kNodeStateRunning)
        {
            return Start();
        }
    }

    return RESULT_SUCCESS;
}

void IVideoSourceNode::ProcessData()
{
    std::lock_guard<std::mutex> guard(mMutex);
    uint8_t* data = nullptr;
    uint32_t dataSize = 0;
    uint32_t timestamp = 0;
    bool mark = false;
    uint32_t seq = 0;
    ImsMediaSubType subtype;
    ImsMediaSubType dataType;

    while (GetData(&subtype, &data, &dataSize, &timestamp, &mark, &seq, &dataType))
    {
        IMLOGD_PACKET1(IM_PACKET_LOG_VIDEO, "[ProcessData] size[%d]", dataSize);

        SendDataToRearNode(
                MEDIASUBTYPE_UNDEFINED, data, dataSize, timestamp, true, MEDIASUBTYPE_UNDEFINED);
        DeleteData();
    }
}

void IVideoSourceNode::UpdateSurface(ANativeWindow* window)
{
    IMLOGD1("[UpdateSurface] surface[%p]", window);
    mWindow = window;
}

void IVideoSourceNode::OnUplinkEvent(
        uint8_t* data, uint32_t size, int64_t timestamp, uint32_t /*flag*/)
{
    IMLOGD_PACKET2(
            IM_PACKET_LOG_VIDEO, "[OnUplinkEvent] size[%zu], timestamp[%ld]", size, timestamp);
    std::lock_guard<std::mutex> guard(mMutex);

    if (size > 0)
    {
        OnDataFromFrontNode(
                MEDIASUBTYPE_UNDEFINED, data, size, timestamp, true, MEDIASUBTYPE_UNDEFINED);
    }
}

void IVideoSourceNode::SetBitrateThreshold(int32_t bitrate)
{
    IMLOGD1("[SetBitrateThreshold] bitrate[%d]", bitrate);
    mMinBitrateThreshold = bitrate;
}

void IVideoSourceNode::OnEvent(int32_t type, int32_t param1, int32_t param2)
{
    IMLOGD3("[OnEvent] type[%d], param1[%d], param2[%d]", type, param1, param2);

    switch (type)
    {
        case kVideoSourceEventUpdateOrientation:
            if (mCallback != nullptr)
            {
                mCallback->SendEvent(kRequestVideoCvoUpdate, param1, param2);
            }
            break;
        case kVideoSourceEventCameraError:
            if (mCallback != nullptr)
            {
                mCallback->SendEvent(kImsMediaEventNotifyError, param1, param2);
            }
            break;
        case kRequestVideoBitrateChange:
            if (mVideoSource != nullptr)
            {
                if (mVideoSource->changeBitrate(param1))
                {
                    if (mMinBitrateThreshold != 0 && param1 <= mMinBitrateThreshold &&
                            mCallback != nullptr && !mBitrateNotified)
                    {
                        mCallback->SendEvent(kImsMediaEventNotifyVideoLowestBitrate, param1);
                        mBitrateNotified = true;
                    }
                }
            }
            break;
        case kRequestVideoIdrFrame:
            if (mVideoSource != nullptr)
            {
                mVideoSource->requestIdrFrame();
            }
            break;
        default:
            break;
    }
}
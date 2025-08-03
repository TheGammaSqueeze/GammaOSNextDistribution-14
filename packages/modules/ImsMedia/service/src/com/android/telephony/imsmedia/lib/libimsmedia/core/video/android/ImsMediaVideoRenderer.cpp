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

#include <ImsMediaVideoRenderer.h>
#include <ImsMediaTrace.h>
#include <ImsMediaTimer.h>
#include <ImsMediaVideoUtil.h>
#include <thread>

#define CODEC_TIMEOUT_NANO 100000
#define INTERVAL_MILLIS    10

ImsMediaVideoRenderer::ImsMediaVideoRenderer()
{
    mCallback = nullptr;
    mWindow = nullptr;
    mCodec = nullptr;
    mFormat = nullptr;
    mCodecType = -1;
    mWidth = 0;
    mHeight = 0;
    mFarOrientationDegree = 0;
    mNearOrientationDegree = 0;
    mStopped = false;
}

ImsMediaVideoRenderer::~ImsMediaVideoRenderer()
{
    while (!mFrameDatas.empty())
    {
        FrameData* frame = mFrameDatas.front();
        delete frame;
        mFrameDatas.pop_front();
    }
}

void ImsMediaVideoRenderer::SetSessionCallback(BaseSessionCallback* callback)
{
    mCallback = callback;
}

void ImsMediaVideoRenderer::SetCodec(int32_t codecType)
{
    IMLOGD1("[SetCodec] codec[%d]", codecType);
    mCodecType = codecType;
}

void ImsMediaVideoRenderer::SetResolution(uint32_t width, uint32_t height)
{
    IMLOGD2("[SetResolution] width[%d], height[%d]", width, height);
    mWidth = width;
    mHeight = height;
}

void ImsMediaVideoRenderer::SetDeviceOrientation(uint32_t orientation)
{
    IMLOGD1("[SetDeviceOrientation] orientation[%d]", orientation);
    mNearOrientationDegree = orientation;
}

void ImsMediaVideoRenderer::SetSurface(ANativeWindow* window)
{
    IMLOGD1("[SetSurface] surface[%p]", window);
    mWindow = window;
}

bool ImsMediaVideoRenderer::Start()
{
    IMLOGD0("[Start]");
    mFormat = AMediaFormat_new();
    AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_WIDTH, mWidth);
    AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_HEIGHT, mHeight);

    char kMimeType[128] = {'\0'};
    sprintf(kMimeType, "video/avc");

    if (mCodecType == kVideoCodecHevc)
    {
        sprintf(kMimeType, "video/hevc");
    }

    AMediaFormat_setString(mFormat, AMEDIAFORMAT_KEY_MIME, kMimeType);
    AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_COLOR_FORMAT,
            21);  // #21 : COLOR_FormatYUV420SemiPlanar
    AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, mWidth * mHeight * 10);
    AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_ROTATION, mFarOrientationDegree);

    mCodec = AMediaCodec_createDecoderByType(kMimeType);

    if (mCodec == nullptr)
    {
        IMLOGE0("[Start] Unable to create decoder");
        return false;
    }

    if (mWindow != nullptr)
    {
        ANativeWindow_acquire(mWindow);
    }

    media_status_t err = AMediaCodec_configure(mCodec, mFormat, mWindow, nullptr, 0);

    if (err != AMEDIA_OK)
    {
        IMLOGE1("[Start] configure error[%d]", err);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
        return false;
    }

    err = AMediaCodec_start(mCodec);

    if (err != AMEDIA_OK)
    {
        IMLOGE1("[Start] codec start[%d]", err);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
        return false;
    }

    mStopped = false;
    std::thread t1(&ImsMediaVideoRenderer::processBuffers, this);
    t1.detach();
    return true;
}

void ImsMediaVideoRenderer::Stop()
{
    IMLOGD0("[Stop]");

    mMutex.lock();
    mStopped = true;
    mMutex.unlock();
    mConditionExit.wait_timeout(MAX_WAIT_RESTART);

    if (mCodec != nullptr)
    {
        AMediaCodec_signalEndOfInputStream(mCodec);
        AMediaCodec_stop(mCodec);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }

    if (mWindow != nullptr)
    {
        ANativeWindow_release(mWindow);
    }

    if (mFormat != nullptr)
    {
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
    }
}

void ImsMediaVideoRenderer::OnDataFrame(
        uint8_t* buffer, uint32_t size, uint32_t timestamp, const bool isConfigFrame)
{
    if (size == 0 || buffer == nullptr)
    {
        return;
    }

    IMLOGD_PACKET2(IM_PACKET_LOG_VIDEO, "[OnDataFrame] frame size[%u], list[%d]", size,
            mFrameDatas.size());

    mMutex.lock();

    if (mStopped)
    {
        mMutex.unlock();
        return;
    }

    mMutex.unlock();

    mFrameDatas.push_back(new FrameData(buffer, size, timestamp, isConfigFrame));
}

void ImsMediaVideoRenderer::processBuffers()
{
    uint32_t nextTime = ImsMediaTimer::GetTimeInMilliSeconds();
    uint32_t timeDiff = 0;

    IMLOGD1("[processBuffers] enter time[%u]", nextTime);

    while (true)
    {
        mMutex.lock();

        if (mStopped)
        {
            mMutex.unlock();
            break;
        }

        mMutex.unlock();

        if (mFrameDatas.size() == 0)
        {
            continue;
        }

        auto index = AMediaCodec_dequeueInputBuffer(mCodec, CODEC_TIMEOUT_NANO);

        if (index >= 0)
        {
            size_t bufferSize = 0;
            uint8_t* inputBuffer = AMediaCodec_getInputBuffer(mCodec, index, &bufferSize);

            if (inputBuffer != nullptr)
            {
                FrameData* frame = mFrameDatas.front();
                memcpy(inputBuffer, frame->data, frame->size);
                IMLOGD_PACKET4(IM_PACKET_LOG_VIDEO,
                        "[processBuffers] queue input buffer index[%d], size[%d], TS[%d], "
                        "config[%d]",
                        index, frame->size, frame->timestamp, frame->isConfig);

                media_status_t err = AMediaCodec_queueInputBuffer(
                        mCodec, index, 0, frame->size, frame->timestamp * 1000, 0);

                if (err != AMEDIA_OK)
                {
                    IMLOGE1("[processBuffers] Unable to queue input buffers - err[%d]", err);
                }

                delete frame;
                mFrameDatas.pop_front();
            }
        }

        AMediaCodecBufferInfo info;
        index = AMediaCodec_dequeueOutputBuffer(mCodec, &info, CODEC_TIMEOUT_NANO);

        if (index >= 0)
        {
            IMLOGD_PACKET5(IM_PACKET_LOG_VIDEO,
                    "[processBuffers] index[%d], size[%d], offset[%d], time[%ld], flags[%d]", index,
                    info.size, info.offset, info.presentationTimeUs, info.flags);

            AMediaCodec_releaseOutputBuffer(mCodec, index, true);
        }
        else if (index == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED)
        {
            IMLOGD0("[processBuffers] output buffer changed");
        }
        else if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED)
        {
            if (mFormat != nullptr)
            {
                AMediaFormat_delete(mFormat);
            }
            mFormat = AMediaCodec_getOutputFormat(mCodec);
            IMLOGD1("[processBuffers] format changed, format[%s]", AMediaFormat_toString(mFormat));
        }
        else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER)
        {
            IMLOGD0("[processBuffers] no output buffer");
        }
        else
        {
            IMLOGD1("[processBuffers] unexpected index[%d]", index);
        }

        nextTime += INTERVAL_MILLIS;
        uint32_t nCurrTime = ImsMediaTimer::GetTimeInMilliSeconds();

        if (nextTime > nCurrTime)
        {
            timeDiff = nextTime - nCurrTime;
            IMLOGD_PACKET1(IM_PACKET_LOG_VIDEO, "[processBuffers] timeDiff[%u]", timeDiff);
            ImsMediaTimer::Sleep(timeDiff);
        }
    }

    mConditionExit.signal();
    IMLOGD0("[processBuffers] exit");
}

void ImsMediaVideoRenderer::UpdateDeviceOrientation(uint32_t degree)
{
    IMLOGD1("[UpdateDeviceOrientation] orientation[%d]", degree);
    mNearOrientationDegree = degree;
}

void ImsMediaVideoRenderer::UpdatePeerOrientation(uint32_t degree)
{
    IMLOGD1("[UpdatePeerOrientation] orientation[%d]", degree);

    if (mFarOrientationDegree != degree)
    {
        Stop();
        mFarOrientationDegree = degree;
        Start();
    }
}
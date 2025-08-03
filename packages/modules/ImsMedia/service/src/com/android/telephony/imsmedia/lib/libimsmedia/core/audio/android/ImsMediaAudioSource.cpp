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

#include <stdio.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <ImsMediaDefine.h>
#include <ImsMediaTimer.h>
#include <ImsMediaTrace.h>
#include <ImsMediaAudioUtil.h>
#include <ImsMediaAudioSource.h>
#include <utils/Errors.h>
#include <thread>

#define AAUDIO_STATE_TIMEOUT_NANO (100 * 1000000L)
#define NUM_FRAMES_PER_SEC        (50)
#define DEFAULT_SAMPLING_RATE     (8000)
#define CODEC_TIMEOUT_NANO        (100000)

using namespace android;

ImsMediaAudioSource::ImsMediaAudioSource()
{
    mAudioStream = nullptr;
    mCodec = nullptr;
    mFormat = nullptr;
    mCallback = nullptr;
    mCodecType = -1;
    mMode = 0;
    mPtime = 0;
    mSamplingRate = DEFAULT_SAMPLING_RATE;
    mBufferSize = 0;
    mEvsBandwidth = kEvsBandwidthNone;
    mEvsBitRate = 0;
    mEvsChAwOffset = 0;
    mIsEvsInitialized = false;
    mMediaDirection = 0;
    mIsDtxEnabled = false;
    mIsOctetAligned = false;
}

ImsMediaAudioSource::~ImsMediaAudioSource() {}

void ImsMediaAudioSource::SetUplinkCallback(IFrameCallback* callback)
{
    std::lock_guard<std::mutex> guard(mMutexUplink);
    mCallback = callback;
}

void ImsMediaAudioSource::SetCodec(int32_t type)
{
    IMLOGD1("[SetCodec] type[%d]", type);
    mCodecType = type;
}

void ImsMediaAudioSource::SetCodecMode(uint32_t mode)
{
    IMLOGD1("[SetCodecMode] mode[%d]", mode);
    mMode = mode;
}

void ImsMediaAudioSource::SetEvsBitRate(uint32_t bitrate)
{
    IMLOGD1("[SetEvsBitRate] bitrate[%d]", bitrate);
    mEvsBitRate = bitrate;
}

void ImsMediaAudioSource::SetSamplingRate(int32_t samplingRate)
{
    mSamplingRate = samplingRate;
}

void ImsMediaAudioSource::SetEvsChAwOffset(int32_t offset)
{
    mEvsChAwOffset = offset;
}

void ImsMediaAudioSource::SetPtime(uint32_t time)
{
    IMLOGD1("[SetPtime] Ptime[%d]", time);
    mPtime = time;
}

void ImsMediaAudioSource::SetEvsBandwidth(int32_t evsBandwidth)
{
    mEvsBandwidth = (kEvsBandwidth)evsBandwidth;
}

void ImsMediaAudioSource::SetMediaDirection(int32_t direction)
{
    mMediaDirection = direction;
}

void ImsMediaAudioSource::SetDtxEnabled(bool isDtxEnabled)
{
    mIsDtxEnabled = isDtxEnabled;
}

void ImsMediaAudioSource::SetOctetAligned(bool isOctetAligned)
{
    mIsOctetAligned = isOctetAligned;
}

bool ImsMediaAudioSource::Start()
{
    openAudioStream();

    if (mAudioStream == nullptr)
    {
        IMLOGE0("[Start] create audio stream failed");
        return false;
    }

    // configure and start codec
    if (!startCodec())
    {
        IMLOGE0("[Start] start codec failed");
        return false;
    }

    // start audio
    auto audioResult = AAudioStream_requestStart(mAudioStream);

    if (audioResult != AAUDIO_OK)
    {
        IMLOGE1("[Start] Error start stream[%s]", AAudio_convertResultToText(audioResult));

        stopCodec();
        return false;
    }

    aaudio_stream_state_t inputState = AAUDIO_STREAM_STATE_STARTING;
    aaudio_stream_state_t nextState = AAUDIO_STREAM_STATE_UNINITIALIZED;
    audioResult = AAudioStream_waitForStateChange(
            mAudioStream, inputState, &nextState, 10 * AAUDIO_STATE_TIMEOUT_NANO);

    if (audioResult != AAUDIO_OK)
    {
        IMLOGE1("[Start] Error start stream[%s]", AAudio_convertResultToText(audioResult));

        if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
        {
            AMediaCodec_delete(mCodec);
            mCodec = nullptr;
            AMediaFormat_delete(mFormat);
            mFormat = nullptr;
        }

        return false;
    }

    IMLOGI1("[Start] start stream state[%s]", AAudio_convertStreamStateToText(nextState));

    // start audio read thread
    StartThread("ImsMediaAudioSource");
    return true;
}

void ImsMediaAudioSource::Stop()
{
    IMLOGD0("[Stop]");
    StopThread();

    if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
    {
        mConditionExit.reset();
        mConditionExit.wait_timeout(AUDIO_STOP_TIMEOUT);
    }

    std::lock_guard<std::mutex> guard(mMutexUplink);

    if (mAudioStream != nullptr)
    {
        aaudio_stream_state_t inputState = AAUDIO_STREAM_STATE_STOPPING;
        aaudio_stream_state_t nextState = AAUDIO_STREAM_STATE_UNINITIALIZED;
        aaudio_result_t result = AAudioStream_requestStop(mAudioStream);

        if (result != AAUDIO_OK)
        {
            IMLOGE1("[Stop] Error stop stream[%s]", AAudio_convertResultToText(result));
        }

        // TODO: if it causes extra delay in stop, optimize later
        result = AAudioStream_waitForStateChange(
                mAudioStream, inputState, &nextState, AAUDIO_STATE_TIMEOUT_NANO);

        if (result != AAUDIO_OK)
        {
            IMLOGE1("[Stop] Error stop stream[%s]", AAudio_convertResultToText(result));
        }

        IMLOGI1("[Stop] stream state[%s]", AAudio_convertStreamStateToText(nextState));

        AAudioStream_close(mAudioStream);
        mAudioStream = nullptr;
    }

    stopCodec();
}

void ImsMediaAudioSource::ProcessCmr(const uint32_t cmr)
{
    IMLOGI1("[ProcessCmr] cmr[%d]", cmr);

    if (IsThreadStopped())
    {
        return;
    }

    std::lock_guard<std::mutex> guard(mMutexUplink);
    mMode = cmr;
    stopCodec();
    startCodec();
}

void ImsMediaAudioSource::audioErrorCallback(
        AAudioStream* stream, void* userData, aaudio_result_t error)
{
    if (stream == nullptr || userData == nullptr)
    {
        return;
    }

    aaudio_stream_state_t streamState = AAudioStream_getState(stream);
    IMLOGW2("[errorCallback] error[%s], state[%d]", AAudio_convertResultToText(error), streamState);

    if (error == AAUDIO_ERROR_DISCONNECTED)
    {
        // Handle stream restart on a separate thread
        std::thread streamRestartThread(&ImsMediaAudioSource::restartAudioStream,
                reinterpret_cast<ImsMediaAudioSource*>(userData));
        streamRestartThread.detach();
    }
}

void* ImsMediaAudioSource::run()
{
    IMLOGD0("[run] enter");
    uint32_t nNextTime = ImsMediaTimer::GetTimeInMilliSeconds();
    int16_t buffer[PCM_BUFFER_SIZE];
    int64_t ptsUsec = 0;
    uint32_t evsFlags = 2;
    uint8_t outputBuf[PCM_BUFFER_SIZE];
    int size = 0;

    outputBuf[0] = 0;

    for (;;)
    {
        if (IsThreadStopped())
        {
            IMLOGD0("[run] terminated");
            break;
        }

        mMutexUplink.lock();

        if (mAudioStream != nullptr &&
                AAudioStream_getState(mAudioStream) == AAUDIO_STREAM_STATE_STARTED)
        {
            aaudio_result_t readSize = AAudioStream_read(mAudioStream, buffer, mBufferSize, 0);

            if (readSize > 0)
            {
                IMLOGD_PACKET1(IM_PACKET_LOG_AUDIO, "[run] nReadSize[%d]", readSize);
                if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
                {
                    queueInputBuffer(buffer, readSize * sizeof(uint16_t));
                    dequeueOutputBuffer();
                }
                else if (mCodecType == kAudioCodecEvs)
                {
                    // TODO: Integration with libEVS is required.
                    if (!mIsEvsInitialized)
                    {
                        mIsEvsInitialized = true;
                    }

                    if (ptsUsec == 0)
                    {
                        ptsUsec = ImsMediaTimer::GetTimeInMicroSeconds() / 1000;
                    }

                    if (mCallback != nullptr && outputBuf[0] != 0)
                    {
                        // TODO: integration with libEVS is require to encode outputBuf
                        mCallback->onDataFrame(outputBuf, size, ptsUsec, evsFlags);
                    }
                    size = 0;
                }
            }
        }

        mMutexUplink.unlock();

        nNextTime += mPtime;
        uint32_t nCurrTime = ImsMediaTimer::GetTimeInMilliSeconds();

        if (nNextTime > nCurrTime)
        {
            ImsMediaTimer::Sleep(nNextTime - nCurrTime);
        }
    }

    mConditionExit.signal();
    return nullptr;
}

void ImsMediaAudioSource::openAudioStream()
{
    AAudioStreamBuilder* builder = nullptr;
    aaudio_result_t result = AAudio_createStreamBuilder(&builder);

    if (result != AAUDIO_OK)
    {
        IMLOGE1("[openAudioStream] Error creating stream builder[%s]",
                AAudio_convertResultToText(result));
        return;
    }

    // setup builder
    AAudioStreamBuilder_setInputPreset(builder, AAUDIO_INPUT_PRESET_VOICE_COMMUNICATION);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_INPUT);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 1);
    AAudioStreamBuilder_setSampleRate(builder, mSamplingRate);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_EXCLUSIVE);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setUsage(builder, AAUDIO_USAGE_VOICE_COMMUNICATION);
    AAudioStreamBuilder_setErrorCallback(builder, audioErrorCallback, this);
    AAudioStreamBuilder_setPrivacySensitive(builder, true);

    // open stream
    result = AAudioStreamBuilder_openStream(builder, &mAudioStream);
    AAudioStreamBuilder_delete(builder);

    if (result == AAUDIO_OK && mAudioStream != nullptr)
    {
        mBufferSize = AAudioStream_getFramesPerBurst(mAudioStream);
        IMLOGD3("[openAudioStream] samplingRate[%d], framesPerBurst[%d], "
                "performanceMode[%d]",
                AAudioStream_getSampleRate(mAudioStream), mBufferSize,
                AAudioStream_getPerformanceMode(mAudioStream));
        // Set the buffer size to the burst size - this will give us the minimum
        // possible latency
        AAudioStream_setBufferSizeInFrames(mAudioStream, mBufferSize);
    }
    else
    {
        IMLOGE1("[openAudioStream] Failed to openStream. Error[%s]",
                AAudio_convertResultToText(result));
        mAudioStream = nullptr;
    }
}

void ImsMediaAudioSource::restartAudioStream()
{
    std::lock_guard<std::mutex> guard(mMutexUplink);

    if (mAudioStream == nullptr)
    {
        return;
    }

    AAudioStream_requestStop(mAudioStream);
    AAudioStream_close(mAudioStream);
    mAudioStream = nullptr;
    openAudioStream();

    if (mAudioStream == nullptr)
    {
        return;
    }

    aaudio_stream_state_t inputState = AAUDIO_STREAM_STATE_STARTING;
    aaudio_stream_state_t nextState = AAUDIO_STREAM_STATE_UNINITIALIZED;
    aaudio_result_t result = AAudioStream_requestStart(mAudioStream);

    if (result != AAUDIO_OK)
    {
        IMLOGE1("[restartAudioStream] Error start stream[%s]", AAudio_convertResultToText(result));
        return;
    }

    result = AAudioStream_waitForStateChange(
            mAudioStream, inputState, &nextState, 10 * AAUDIO_STATE_TIMEOUT_NANO);

    if (result != AAUDIO_OK)
    {
        IMLOGE1("[restartAudioStream] Error start stream[%s]", AAudio_convertResultToText(result));
        return;
    }

    IMLOGI1("[restartAudioStream] start stream state[%s]",
            AAudio_convertStreamStateToText(nextState));
}

bool ImsMediaAudioSource::startCodec()
{
    char kMimeType[128] = {'\0'};
    int amrBitrate = 0;
    // TODO: Integration with libEVS is required.
    ImsMediaAudioUtil::ConvertEvsBandwidthToStr(mEvsBandwidth, mEvsbandwidthStr, MAX_EVS_BW_STRLEN);

    switch (mCodecType)
    {
        case kAudioCodecAmr:
            sprintf(kMimeType, "audio/3gpp");
            amrBitrate = ImsMediaAudioUtil::ConvertAmrModeToBitrate(mMode);
            break;
        case kAudioCodecAmrWb:
            sprintf(kMimeType, "audio/amr-wb");
            amrBitrate = ImsMediaAudioUtil::ConvertAmrWbModeToBitrate(mMode);
            break;
        case kAudioCodecEvs:
            // TODO: Integration with libEVS is required.
            sprintf(kMimeType, "audio/evs");
            break;
        default:
            return false;
    }

    IMLOGD1("[startCodec] codec type[%s]", kMimeType);

    if (mCodecType == kAudioCodecAmr || mCodecType == kAudioCodecAmrWb)
    {
        mFormat = AMediaFormat_new();
        AMediaFormat_setString(mFormat, AMEDIAFORMAT_KEY_MIME, kMimeType);
        AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, mSamplingRate);
        AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, 1);
        AMediaFormat_setInt32(mFormat, AMEDIAFORMAT_KEY_BIT_RATE, amrBitrate);

        mCodec = AMediaCodec_createEncoderByType(kMimeType);

        if (mCodec == nullptr)
        {
            IMLOGE1("[startCodec] unable to create %s codec instance", kMimeType);
            AMediaFormat_delete(mFormat);
            mFormat = nullptr;
            return false;
        }

        IMLOGD0("[startCodec] configure codec");
        media_status_t codecResult = AMediaCodec_configure(
                mCodec, mFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);

        if (codecResult != AMEDIA_OK)
        {
            IMLOGE2("[startCodec] unable to configure[%s] codec - err[%d]", kMimeType, codecResult);
            AMediaCodec_delete(mCodec);
            mCodec = nullptr;
            AMediaFormat_delete(mFormat);
            mFormat = nullptr;
            return false;
        }

        codecResult = AMediaCodec_start(mCodec);

        if (codecResult != AMEDIA_OK)
        {
            IMLOGE1("[Start] unable to start codec - err[%d]", codecResult);
            AMediaCodec_delete(mCodec);
            mCodec = nullptr;
            AMediaFormat_delete(mFormat);
            mFormat = nullptr;
            return false;
        }
    }
    else if (mCodecType == kAudioCodecEvs)
    {
        // TODO: Integration with libEVS is required.
        mIsEvsInitialized = true;
    }

    return true;
}

void ImsMediaAudioSource::stopCodec()
{
    if (mCodec != nullptr)
    {
        AMediaCodec_stop(mCodec);
        AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }

    if (mFormat != nullptr)
    {
        AMediaFormat_delete(mFormat);
        mFormat = nullptr;
    }
}

void ImsMediaAudioSource::queueInputBuffer(int16_t* buffer, uint32_t size)
{
    if (mCodec == nullptr)
    {
        return;
    }

    ssize_t index = AMediaCodec_dequeueInputBuffer(mCodec, 0);

    if (index >= 0)
    {
        size_t bufferSize = 0;
        uint8_t* inputBuffer = AMediaCodec_getInputBuffer(mCodec, index, &bufferSize);

        if (inputBuffer != nullptr)
        {
            memcpy(inputBuffer, buffer, size);
            IMLOGD_PACKET2(IM_PACKET_LOG_AUDIO,
                    "[queueInputBuffer] queue input buffer index[%d], size[%d]", index, size);

            auto err = AMediaCodec_queueInputBuffer(
                    mCodec, index, 0, size, ImsMediaTimer::GetTimeInMicroSeconds(), 0);

            if (err != AMEDIA_OK)
            {
                IMLOGE1("[queueInputBuffer] Unable to queue input buffers - err[%d]", err);
            }
        }
    }
}

void ImsMediaAudioSource::dequeueOutputBuffer()
{
    AMediaCodecBufferInfo info;
    auto index = AMediaCodec_dequeueOutputBuffer(mCodec, &info, CODEC_TIMEOUT_NANO);

    if (index >= 0)
    {
        IMLOGD_PACKET5(IM_PACKET_LOG_AUDIO,
                "[dequeueOutputBuffer] index[%d], size[%d], offset[%d], time[%ld], flags[%d]",
                index, info.size, info.offset, info.presentationTimeUs, info.flags);

        if (info.size > 0)
        {
            size_t buffCapacity;
            uint8_t* buf = AMediaCodec_getOutputBuffer(mCodec, index, &buffCapacity);

            if (mCallback != nullptr)
            {
                mCallback->onDataFrame(buf, info.size, info.presentationTimeUs, info.flags);
            }
        }

        AMediaCodec_releaseOutputBuffer(mCodec, index, false);
    }
    else if (index == AMEDIACODEC_INFO_OUTPUT_BUFFERS_CHANGED)
    {
        IMLOGD0("[dequeueOutputBuffer] Encoder output buffer changed");
    }
    else if (index == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED)
    {
        if (mFormat != nullptr)
        {
            AMediaFormat_delete(mFormat);
        }

        mFormat = AMediaCodec_getOutputFormat(mCodec);
        IMLOGD1("[dequeueOutputBuffer] Encoder format changed, format[%s]",
                AMediaFormat_toString(mFormat));
    }
    else if (index == AMEDIACODEC_INFO_TRY_AGAIN_LATER)
    {
        IMLOGD0("[dequeueOutputBuffer] no output buffer");
    }
    else
    {
        IMLOGD1("[dequeueOutputBuffer] unexpected index[%d]", index);
    }
}

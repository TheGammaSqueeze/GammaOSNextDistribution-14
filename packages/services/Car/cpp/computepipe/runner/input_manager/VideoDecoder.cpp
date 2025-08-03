// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#include <android-base/logging.h>
#include <android-base/strings.h>
#include <media/stagefright/MediaCodecConstants.h>

#include <fcntl.h>
#include <chrono>

#include "VideoDecoder.h"
#include "prebuilt_interface.h"

namespace android {
namespace automotive {
namespace computepipe {
namespace runner {
namespace input_manager {
namespace {

const int64_t kMicrosPerSecond = 1000 * 1000;
const int64_t kMediaCodecNonBlockingTimeoutUs = 5000;  // 5ms.
int kMaxInUseBuffers = 50;

int64_t getCurrentTime() {
    auto timePoint = std::chrono::system_clock::now();
    return std::chrono::time_point_cast<std::chrono::microseconds>(timePoint)
            .time_since_epoch()
            .count();
}

PixelFormat toPixelFormat(int mediaFormat) {
    switch (mediaFormat) {
        case COLOR_FormatYUV420SemiPlanar:
            return YUV_420;
        default:
            LOG(ERROR) << "Unsupported output format - " << mediaFormat;
            return PIXELFORMAT_MAX;
    }
}

}  // namespace

VideoDecoder::VideoDecoder(const proto::InputStreamConfig& config,
                           std::shared_ptr<InputEngineInterface> engineInterface) :
      mEngine(engineInterface),
      mConfig(config) {
    if (config.has_video_config() && config.video_config().has_file_path()) {
        mVideoPath = config.video_config().file_path();
    }
}

VideoDecoder::~VideoDecoder() {
    stopDecoding();
}

float VideoDecoder::getPlaybackFrameRate() {
    if (!mExtractor) {
        if (initializeMediaExtractor() != Status::SUCCESS) {
            LOG(ERROR) << "VideoDecoder: Received error initializing media extractor.";
            return 0;
        }
    }
    if (!mCodec) {
        if (initializeMediaDecoder() != Status::SUCCESS) {
            LOG(ERROR) << "VideoDecoder: Received error initializing media codec.";
            return 0;
        }
    }
    return mPlaybackFrameRate;
}

void VideoDecoder::setInitialTimestamp(int64_t timestampMicros) {
    mStartTimeMicros = timestampMicros;
}

Status VideoDecoder::startDecoding() {
    mStopThread = false;
    mDecoderThead = std::make_unique<std::thread>(&VideoDecoder::decoderThreadFunction, this);
    return Status::SUCCESS;
}

void VideoDecoder::stopDecoding() {
    mStopThread = true;
    if (mDecoderThead && mDecoderThead->joinable()) {
        mDecoderThead->join();
        mDecoderThead = nullptr;
    }
    releaseResources();
}

Status VideoDecoder::initializeMediaExtractor() {
    if (!mIsFdOpen) {
        mFd = open(mVideoPath.c_str(), 0, O_RDONLY);
        mIsFdOpen = true;
    }
    if (!mExtractor) {
        mExtractor = AMediaExtractor_new();
        off64_t size = lseek64(mFd, 0, SEEK_END);
        // Reset the offset.
        lseek(mFd, 0, SEEK_SET);
        media_status_t status = AMediaExtractor_setDataSourceFd(mExtractor, mFd, 0, size);
        if (status != AMEDIA_OK) {
            LOG(ERROR) << "VideoDecoder: Received error when initializing media extractor.";
            stopDecoding();
            return Status::INTERNAL_ERROR;
        }
    }
    return Status::SUCCESS;
}

Status VideoDecoder::initializeMediaDecoder() {
    int numTracks = AMediaExtractor_getTrackCount(mExtractor);
    AMediaFormat* format;
    const char* mime;
    int i;
    for (i = 0; i < numTracks; i++) {
        format = AMediaExtractor_getTrackFormat(mExtractor, i);
        if (!AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime)) {
            LOG(ERROR) << "VideoDecoder: Error in fetching format string";
        }

        if (android::base::StartsWith(mime, "video/")) {
            media_status_t status = AMediaExtractor_selectTrack(mExtractor, i);
            if (status != AMEDIA_OK) {
                LOG(ERROR) << "VideoDecoder: Media extractor returned error to select track.";
                return Status::INTERNAL_ERROR;
            }
            break;
        }
        AMediaFormat_delete(format);
    }
    if (i == numTracks) {
        LOG(ERROR) << "VideoDecoder: No video track in " << mVideoPath;
        return Status::INTERNAL_ERROR;
    }

    int frameRate;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, &frameRate);
    // TODO(b/156102135) - parse multiplier from input proto.
    float playbackRateMultiplier = 1.0;
    mPlaybackFrameRate = frameRate * playbackRateMultiplier;

    mCodec = AMediaCodec_createDecoderByType(mime);
    if (!mCodec) {
        LOG(ERROR) << "VideoDecoder: Unable to create decoder.";
        AMediaFormat_delete(format);
        releaseResources();
        return Status::INTERNAL_ERROR;
    }

    media_status_t status = AMediaCodec_configure(mCodec, format, nullptr, nullptr, 0);
    if (status != AMEDIA_OK) {
        LOG(ERROR) << "VideoDecoder: Received error in configuring mCodec.";
        AMediaFormat_delete(format);
        releaseResources();
        return Status::INTERNAL_ERROR;
    }
    return Status::SUCCESS;
}

void VideoDecoder::releaseResources() {
    if (mExtractor) {
        (void)AMediaExtractor_delete(mExtractor);
        mExtractor = nullptr;
    }
    if (mCodec) {
        while (mDecodedBuffers.size()) {
            std::pair<int, AMediaCodecBufferInfo> buffer = mDecodedBuffers.front();
            AMediaCodec_releaseOutputBuffer(mCodec, buffer.first, false);
            mDecodedBuffers.pop();
        }
        AMediaFormat* format = AMediaCodec_getOutputFormat(mCodec);
        AMediaFormat_delete(format);
        (void)AMediaCodec_delete(mCodec);
        mCodec = nullptr;
    }
    if (mIsFdOpen) {
        close(mFd);
        mIsFdOpen = false;
    }
}

void VideoDecoder::decoderThreadFunction() {
    if (!mExtractor || !mCodec) {
        CHECK(initializeMediaExtractor() == Status::SUCCESS);
        CHECK(initializeMediaDecoder() == Status::SUCCESS);
    }

    media_status_t status = AMediaCodec_start(mCodec);
    if (status != AMEDIA_OK) {
        LOG(ERROR) << "VideoDecoder: Received error in starting decoder.";
        mEngine->notifyInputError();
        return;
    }

    int frameIx = 0;
    int loopbackCount = mLoopbackCount;
    if (loopbackCount == 0) {
        sendEosFlag();
        return;
    }
    while (!mStopThread) {
        // Force 64bit integer arithmetic operations.
        int64_t frameIntervalMicros = kMicrosPerSecond / mPlaybackFrameRate;
        int64_t frameTimeMicros = frameIx * frameIntervalMicros + mStartTimeMicros;

        if (getCurrentTime() > frameTimeMicros) {
            if (readDecodedFrame(frameTimeMicros)) {
                frameIx++;
            }
        }
        addFramesToCodec();
        popFramesFromCodec();
        if (mExtractorFinished && (mCountQueuedBuffers == 0) && mDecodedBuffers.empty()) {
            --loopbackCount;
            if (loopbackCount == 0) {
                sendEosFlag();
                break;
            }
            LOG(ERROR) << "Remaining loopback count - " << loopbackCount;

            AMediaExtractor_seekTo(mExtractor, 0, AMEDIAEXTRACTOR_SEEK_CLOSEST_SYNC);
            AMediaCodec_flush(mCodec);

            mStartTimeMicros = frameTimeMicros + frameIntervalMicros;
            frameIx = 0;
            mExtractorFinished = false;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    }
    releaseResources();
}

void VideoDecoder::addFramesToCodec() {
    if (mExtractorFinished) {
        return;
    }
    while ((mCountQueuedBuffers + mDecodedBuffers.size()) <= kMaxInUseBuffers) {
        size_t sampleSize = AMediaExtractor_getSampleSize(mExtractor);
        int64_t presentationTime = AMediaExtractor_getSampleTime(mExtractor);
        int bufferIx = AMediaCodec_dequeueInputBuffer(mCodec, kMediaCodecNonBlockingTimeoutUs);
        if (bufferIx < 0) {
            if (bufferIx != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                LOG(ERROR) << "VideoDecoder: Received error in AMediaCodec_dequeueInputBuffer";
            }
            return;
        }
        size_t bufferSize;
        uint8_t* buffer = AMediaCodec_getInputBuffer(mCodec, bufferIx, &bufferSize);
        if (sampleSize > bufferSize) {
            LOG(ERROR) << "VideoDecoder: Buffer is not large enough.";
        }
        if (presentationTime < 0) {
            AMediaCodec_queueInputBuffer(mCodec, bufferIx, 0 /*offset*/, 0 /*size*/,
                                         presentationTime, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
            mExtractorFinished = true;
            return;
        }
        size_t read = AMediaExtractor_readSampleData(mExtractor, buffer, sampleSize);
        media_status_t status = AMediaCodec_queueInputBuffer(mCodec, bufferIx, 0 /*offset*/, read,
                                                             presentationTime, 0 /*flags*/);
        if (status != AMEDIA_OK) {
            LOG(ERROR) << "VideoDecoder: Received error in queueing input buffer.";
        }
        mCountQueuedBuffers++;
        AMediaExtractor_advance(mExtractor);
    }
}

void VideoDecoder::popFramesFromCodec() {
    while (mCountQueuedBuffers) {
        AMediaCodecBufferInfo info;
        int bufferIx = AMediaCodec_dequeueOutputBuffer(
                mCodec, &info, kMediaCodecNonBlockingTimeoutUs);
        if (bufferIx < 0) {
            if (bufferIx != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
                LOG(ERROR) << "VideoDecoder: Received error in AMediaCodec_dequeueOutputBuffer";
            }
            return;
        }
        mDecodedBuffers.push(std::pair<int, AMediaCodecBufferInfo>(bufferIx, info));
        mCountQueuedBuffers--;
    }
}

bool VideoDecoder::readDecodedFrame(int64_t frameTimeMicros) {
    if (mDecodedBuffers.empty()) {
        return false;
    }

    AMediaFormat* format = AMediaCodec_getOutputFormat(mCodec);

    int width, height, stride, outputFormat;
    bool success = AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_WIDTH, &width);
    success = success && AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_HEIGHT, &height);
    success = success && AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_STRIDE, &stride);
    success =
            success && AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, &outputFormat);
    if (!success) {
        LOG(ERROR) << "Failure to find frame parameters, exiting.";
        mEngine->notifyInputError();
        return false;
    }
    PixelFormat prebuiltFormat = toPixelFormat(outputFormat);

    std::pair<int, AMediaCodecBufferInfo> buffer = mDecodedBuffers.front();
    size_t decodedOutSize;
    uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(mCodec, buffer.first, &decodedOutSize);

    // Inject data to engine.
    InputFrame inputFrame(height, width, prebuiltFormat, stride,
                          outputBuffer + buffer.second.offset);
    mEngine->dispatchInputFrame(mConfig.stream_id(), frameTimeMicros, inputFrame);

    media_status_t status = AMediaCodec_releaseOutputBuffer(mCodec, buffer.first, false);
    if (status != AMEDIA_OK) {
        LOG(ERROR) << "VideoDecoder: received error in releasing output buffer.";
    }
    mDecodedBuffers.pop();
    return true;
}

void VideoDecoder::sendEosFlag() {
    AMediaFormat* format = AMediaCodec_getOutputFormat(mCodec);
    int outputFormat;
    AMediaFormat_getInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, &outputFormat);
    PixelFormat prebuiltFormat = toPixelFormat(outputFormat);
    InputFrame inputFrame(5, 5, prebuiltFormat, 5,
                          reinterpret_cast<unsigned char*>(
                                  const_cast<char*>(kEndOfInputStreamFlag)));
    mEngine->dispatchInputFrame(mConfig.stream_id(), 0, inputFrame);
}

}  // namespace input_manager
}  // namespace runner
}  // namespace computepipe
}  // namespace automotive
}  // namespace android

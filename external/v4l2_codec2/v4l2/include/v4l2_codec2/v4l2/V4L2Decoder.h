// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODER_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODER_H

#include <stdint.h>

#include <cstdint>
#include <memory>
#include <optional>
#include <utility>

#include <base/callback.h>
#include <base/memory/weak_ptr.h>

#include <ui/GraphicBuffer.h>
#include <ui/Rect.h>
#include <ui/Size.h>
#include <v4l2_codec2/common/Fourcc.h>
#include <v4l2_codec2/common/VideoTypes.h>
#include <v4l2_codec2/components/VideoDecoder.h>
#include <v4l2_codec2/components/VideoFrame.h>
#include <v4l2_codec2/components/VideoFramePool.h>
#include <v4l2_codec2/plugin_store/DmabufHelpers.h>
#include <v4l2_codec2/v4l2/V4L2Device.h>

namespace android {

// Currently we only support flexible pixel 420 format YCBCR_420_888 in Android.
// Here is the list of flexible 420 format.
constexpr std::initializer_list<uint32_t> kSupportedOutputFourccs = {
        Fourcc::YU12, Fourcc::YV12, Fourcc::YM12, Fourcc::YM21,
        Fourcc::NV12, Fourcc::NV21, Fourcc::NM12, Fourcc::NM21,
};

class V4L2Decoder : public VideoDecoder {
public:
    static std::unique_ptr<VideoDecoder> Create(
            uint32_t debugStreamId, const VideoCodec& codec, const size_t inputBufferSize,
            const size_t minNumOutputBuffers, GetPoolCB getPoolCB, OutputCB outputCb,
            ErrorCB errorCb, scoped_refptr<::base::SequencedTaskRunner> taskRunner, bool isSecure);
    ~V4L2Decoder() override;

    void decode(std::unique_ptr<ConstBitstreamBuffer> buffer, DecodeCB decodeCb) override;
    void drain(DecodeCB drainCb) override;
    void flush() override;

private:
    static constexpr size_t kNumInputBuffers = 16;

    enum class State {
        Idle,  // Not received any decode buffer after initialized, flushed, or drained.
        Decoding,
        Draining,
        Error,
    };
    static const char* StateToString(State state);

    struct DecodeRequest {
        DecodeRequest(std::unique_ptr<ConstBitstreamBuffer> buffer, DecodeCB decodeCb)
              : buffer(std::move(buffer)), decodeCb(std::move(decodeCb)) {}
        DecodeRequest(DecodeRequest&&) = default;
        ~DecodeRequest() = default;

        std::unique_ptr<ConstBitstreamBuffer> buffer;  // nullptr means Drain
        DecodeCB decodeCb;
    };

    V4L2Decoder(uint32_t debugStreamId, scoped_refptr<::base::SequencedTaskRunner> taskRunner);
    bool start(const VideoCodec& codec, const size_t inputBufferSize,
               const size_t minNumOutputBuffers, GetPoolCB getPoolCb, OutputCB outputCb,
               ErrorCB errorCb, bool isSecure);
    bool setupInputFormat(const uint32_t inputPixelFormat, const size_t inputBufferSize);

    // Sets minimal resolution and allocates minimal amount of output buffers for
    // drain done signaling.
    bool setupInitialOutput();
    // Find the first output format and sets output to its minimal resolution.
    bool setupMinimalOutputFormat();
    // Allocates the at least |minOutputBuffersCount| of output buffers using set format
    bool startOutputQueue(size_t minOutputBuffersCount, enum v4l2_memory memory);

    void pumpDecodeRequest();

    void serviceDeviceTask(bool event);
    bool dequeueResolutionChangeEvent();
    bool changeResolution();
    bool setupOutputFormat(const ui::Size& size);

    void tryFetchVideoFrame();
    void onVideoFrameReady(std::optional<VideoFramePool::FrameWithBlockId> frameWithBlockId);

    std::optional<size_t> getNumOutputBuffers();
    std::optional<struct v4l2_format> getFormatInfo();
    Rect getVisibleRect(const ui::Size& codedSize);
    bool sendV4L2DecoderCmd(bool start);

    void setState(State newState);
    void onError();

    uint32_t mDebugStreamId;

    std::unique_ptr<VideoFramePool> mVideoFramePool;

    scoped_refptr<V4L2Device> mDevice;
    scoped_refptr<V4L2Queue> mInputQueue;
    scoped_refptr<V4L2Queue> mOutputQueue;

    // Contains the initial EOS buffer, until DRC event is dequeued.
    sp<GraphicBuffer> mInitialEosBuffer;

    std::queue<DecodeRequest> mDecodeRequests;
    std::map<int32_t, DecodeCB> mPendingDecodeCbs;
    // Marks that we need to wait for DRC before drain can complete.
    bool mPendingDRC = false;
    // Holds information about secure playback, which won't allow decoder to
    // access frames in order to provide extra meta information (like checking
    // for pending DRC).
    bool mIsSecure;
    VideoCodec mCodec;

    // Tracks the last DMA buffer ID which was used for a given V4L2 input
    // buffer ID. Used to try to avoid re-importing buffers.
    unique_id_t mLastDmaBufferId[kNumInputBuffers];

    // The next input buffer ID to allocate. Note that since we don't un-allocate
    // ids, all entries less than this in mLastDmaBufferId are valid.
    size_t mNextInputBufferId = 0;

    size_t mMinNumOutputBuffers = 0;
    GetPoolCB mGetPoolCb;
    OutputCB mOutputCb;
    DecodeCB mDrainCb;
    ErrorCB mErrorCb;

    ui::Size mCodedSize;
    Rect mVisibleRect;

    // Currently enqueued frame at the deocder device, mapped using V4L2 buffer ID.
    std::map<size_t, std::unique_ptr<VideoFrame>> mFrameAtDevice;

    // A queue of previously enqueued frames, that were returned during flush
    // (STREAMOFF). Those frames will be reused as soon as `tryFetchVideoFrame`
    // is called. This is a workaround for b/297228544 and helps with general
    // responsiveness of the video playback due to b/270003218.
    std::queue<std::pair<size_t, std::unique_ptr<VideoFrame>>> mReuseFrameQueue;

    // Block IDs can be arbitrarily large, but we only have a limited number of
    // buffers. This maintains an association between a block ID and a specific
    // V4L2 buffer index.
    std::map<size_t, size_t> mBlockIdToV4L2Id;

    State mState = State::Idle;

    scoped_refptr<::base::SequencedTaskRunner> mTaskRunner;

    ::base::WeakPtr<V4L2Decoder> mWeakThis;
    ::base::WeakPtrFactory<V4L2Decoder> mWeakThisFactory{this};
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_DECODER_H

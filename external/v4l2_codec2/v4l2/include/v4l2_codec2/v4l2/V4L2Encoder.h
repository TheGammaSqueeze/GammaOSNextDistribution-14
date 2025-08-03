// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_V4L2_V4L2_ENCODER_H
#define ANDROID_V4L2_CODEC2_V4L2_V4L2_ENCODER_H

#include <stdint.h>
#include <memory>
#include <optional>
#include <queue>
#include <vector>

#include <base/memory/weak_ptr.h>
#include <base/sequenced_task_runner.h>
#include <ui/Size.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/components/VideoEncoder.h>

namespace android {

struct BitstreamBuffer;
struct VideoFramePlane;
class V4L2Device;
class V4L2Queue;

class V4L2Encoder : public VideoEncoder {
public:
    static std::unique_ptr<VideoEncoder> create(
            C2Config::profile_t profile, std::optional<uint8_t> level, const ui::Size& visibleSize,
            uint32_t stride, uint32_t keyFramePeriod, C2Config::bitrate_mode_t bitrateMode,
            uint32_t bitrate, std::optional<uint32_t> peakBitrate,
            FetchOutputBufferCB fetchOutputBufferCb, InputBufferDoneCB inputBufferDoneCb,
            OutputBufferDoneCB outputBufferDoneCb, DrainDoneCB drainDoneCb, ErrorCB errorCb,
            scoped_refptr<::base::SequencedTaskRunner> taskRunner);
    ~V4L2Encoder() override;

    bool encode(std::unique_ptr<InputFrame> frame) override;
    void drain() override;
    void flush() override;

    bool setBitrate(uint32_t bitrate) override;
    bool setPeakBitrate(uint32_t peakBitrate) override;
    bool setFramerate(uint32_t framerate) override;
    void requestKeyframe() override;

    VideoPixelFormat inputFormat() const override;
    const ui::Size& visibleSize() const override { return mVisibleSize; }
    const ui::Size& codedSize() const override { return mInputCodedSize; }

private:
    // Possible encoder states.
    enum class State {
        UNINITIALIZED,            // Not initialized yet or initialization failed.
        WAITING_FOR_INPUT_FRAME,  // Waiting for frames to be queued.
        WAITING_FOR_V4L2_BUFFER,  // Waiting for V4L2 input queue buffers.
        ENCODING,                 // Queuing input buffers.
        DRAINING,                 // Draining encoder.
        ERROR,                    // Encoder encountered an error.
    };

    // Contains a single encode request.
    struct EncodeRequest {
        EncodeRequest(std::unique_ptr<InputFrame> video_frame)
              : video_frame(std::move(video_frame)) {}
        ~EncodeRequest() = default;
        EncodeRequest(EncodeRequest&&) = default;
        EncodeRequest& operator=(EncodeRequest&&) = default;

        std::unique_ptr<InputFrame> video_frame;
        bool end_of_stream = false;
    };

    V4L2Encoder(scoped_refptr<::base::SequencedTaskRunner> taskRunner,
                FetchOutputBufferCB fetchOutputBufferCb, InputBufferDoneCB mInputBufferDoneCb,
                OutputBufferDoneCB mOutputBufferDoneCb, DrainDoneCB drainDoneCb, ErrorCB errorCb);

    // Initialize the V4L2 encoder for specified parameters.
    bool initialize(C2Config::profile_t outputProfile, std::optional<uint8_t> level,
                    const ui::Size& visibleSize, uint32_t stride, uint32_t keyFramePeriod,
                    C2Config::bitrate_mode_t bitrateMode, uint32_t bitrate,
                    std::optional<uint32_t> peakBitrate);

    // Handle the next encode request on the queue.
    void handleEncodeRequest();
    // Handle a request to flush the encoder.
    void handleFlushRequest();
    // Handle a request to drain the encoder.
    void handleDrainRequest();
    // Called when draining the encoder has completed.
    void onDrainDone(bool done);

    // Configure input format on the V4L2 device.
    bool configureInputFormat(VideoPixelFormat inputFormat, uint32_t stride);
    // Configure output format on the V4L2 device.
    bool configureOutputFormat(C2Config::profile_t outputProfile);
    // Configure required and optional controls on the V4L2 device.
    bool configureDevice(C2Config::profile_t outputProfile,
                         std::optional<const uint8_t> outputH264Level);
    // Configure required and optional H.264 controls on the V4L2 device.
    bool configureH264(C2Config::profile_t outputProfile,
                       std::optional<const uint8_t> outputH264Level);
    // Configure the specified bitrate mode on the V4L2 device.
    bool configureBitrateMode(C2Config::bitrate_mode_t bitrateMode);

    // Attempt to start the V4L2 device poller.
    bool startDevicePoll();
    // Attempt to stop the V4L2 device poller.
    bool stopDevicePoll();
    // Called by the V4L2 device poller whenever an error occurred.
    void onPollError();
    // Service I/O on the V4L2 device, called by the V4L2 device poller.
    void serviceDeviceTask(bool event);

    // Enqueue an input buffer to be encoded on the device input queue. Returns whether the
    // operation was successful.
    bool enqueueInputBuffer(std::unique_ptr<InputFrame> frame);
    // Enqueue an output buffer to store the encoded bitstream on the device output queue. Returns
    // whether the operation was successful.
    bool enqueueOutputBuffer();
    // Dequeue an input buffer the V4L2 device has finished encoding on the device input queue.
    // Returns whether a buffer could be dequeued.
    bool dequeueInputBuffer();
    // Dequeue an output buffer containing the encoded bitstream from the device output queue.
    // Returns whether the operation was successful.
    bool dequeueOutputBuffer();

    // Create input buffers on the V4L2 device input queue.
    bool createInputBuffers();
    // Create output buffers on the V4L2 device output queue.
    bool createOutputBuffers();
    // Destroy the input buffers on the V4L2 device input queue.
    void destroyInputBuffers();
    // Destroy the output buffers on the V4L2 device output queue.
    void destroyOutputBuffers();

    // Notify the client an error occurred and switch to the error state.
    void onError();

    // Change the state of the encoder.
    void setState(State state);
    // Get the specified encoder |state| as string.
    static const char* stateToString(State state);

    // The list of currently queued encode requests.
    std::queue<EncodeRequest> mEncodeRequests;

    // The video stream's visible size.
    ui::Size mVisibleSize;
    // The video stream's coded size.
    ui::Size mInputCodedSize;
    // The input layout configured on the V4L2 device.
    std::optional<VideoFrameLayout> mInputLayout;
    // Required output buffer byte size.
    uint32_t mOutputBufferSize = 0;

    // How often we want to request the V4L2 device to create a key frame.
    uint32_t mKeyFramePeriod = 0;
    // Key frame counter, a key frame will be requested each time it reaches zero.
    uint32_t mKeyFrameCounter = 0;

    // Whether we need to manually cache and prepend SPS and PPS to IDR frames.
    bool mInjectParamsBeforeIDR = false;
    // The latest cached SPS and PPS (without H.264 start code).
    std::vector<uint8_t> mCachedSPS;
    std::vector<uint8_t> mCachedPPS;

    // The V4L2 device and associated queues used to interact with the device.
    scoped_refptr<V4L2Device> mDevice;
    scoped_refptr<V4L2Queue> mInputQueue;
    scoped_refptr<V4L2Queue> mOutputQueue;

    // List of frames associated with each buffer in the V4L2 device input queue.
    std::vector<std::unique_ptr<InputFrame>> mInputBuffers;
    // List of bitstream buffers associated with each buffer in the V4L2 device output queue.
    std::vector<std::unique_ptr<BitstreamBuffer>> mOutputBuffers;

    // Callbacks to be triggered on various events.
    FetchOutputBufferCB mFetchOutputBufferCb;
    InputBufferDoneCB mInputBufferDoneCb;
    OutputBufferDoneCB mOutputBufferDoneCb;
    DrainDoneCB mDrainDoneCb;
    ErrorCB mErrorCb;

    // The current state of the encoder.
    State mState = State::UNINITIALIZED;

    scoped_refptr<::base::SequencedTaskRunner> mTaskRunner;

    ::base::WeakPtr<V4L2Encoder> mWeakThis;
    ::base::WeakPtrFactory<V4L2Encoder> mWeakThisFactory{this};
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_V4L2_V4L2_ENCODER_H

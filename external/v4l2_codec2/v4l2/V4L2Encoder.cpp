// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2Encoder"

#include <v4l2_codec2/v4l2/V4L2Encoder.h>

#include <stdint.h>
#include <optional>
#include <vector>

#include <base/bind.h>
#include <base/files/scoped_file.h>
#include <base/memory/ptr_util.h>
#include <log/log.h>
#include <ui/Rect.h>

#include <v4l2_codec2/common/EncodeHelpers.h>
#include <v4l2_codec2/common/Fourcc.h>
#include <v4l2_codec2/components/BitstreamBuffer.h>
#include <v4l2_codec2/v4l2/V4L2Device.h>

namespace android {

namespace {

// The maximum size for output buffer, which is chosen empirically for a 1080p video.
constexpr size_t kMaxBitstreamBufferSizeInBytes = 2 * 1024 * 1024;  // 2MB
// The frame size for 1080p (FHD) video in pixels.
constexpr int k1080PSizeInPixels = 1920 * 1080;
// The frame size for 1440p (QHD) video in pixels.
constexpr int k1440PSizeInPixels = 2560 * 1440;

// Use quadruple size of kMaxBitstreamBufferSizeInBytes when the input frame size is larger than
// 1440p, double if larger than 1080p. This is chosen empirically for some 4k encoding use cases and
// the Android CTS VideoEncoderTest (crbug.com/927284).
size_t GetMaxOutputBufferSize(const ui::Size& size) {
    if (getArea(size) > k1440PSizeInPixels) return kMaxBitstreamBufferSizeInBytes * 4;
    if (getArea(size) > k1080PSizeInPixels) return kMaxBitstreamBufferSizeInBytes * 2;
    return kMaxBitstreamBufferSizeInBytes;
}

// Define V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR control code if not present in header files.
#ifndef V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR
#define V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR (V4L2_CID_MPEG_BASE + 644)
#endif

}  // namespace

// static
std::unique_ptr<VideoEncoder> V4L2Encoder::create(
        C2Config::profile_t outputProfile, std::optional<uint8_t> level,
        const ui::Size& visibleSize, uint32_t stride, uint32_t keyFramePeriod,
        C2Config::bitrate_mode_t bitrateMode, uint32_t bitrate, std::optional<uint32_t> peakBitrate,
        FetchOutputBufferCB fetchOutputBufferCb, InputBufferDoneCB inputBufferDoneCb,
        OutputBufferDoneCB outputBufferDoneCb, DrainDoneCB drainDoneCb, ErrorCB errorCb,
        scoped_refptr<::base::SequencedTaskRunner> taskRunner) {
    ALOGV("%s()", __func__);

    std::unique_ptr<V4L2Encoder> encoder = ::base::WrapUnique<V4L2Encoder>(new V4L2Encoder(
            std::move(taskRunner), std::move(fetchOutputBufferCb), std::move(inputBufferDoneCb),
            std::move(outputBufferDoneCb), std::move(drainDoneCb), std::move(errorCb)));
    if (!encoder->initialize(outputProfile, level, visibleSize, stride, keyFramePeriod, bitrateMode,
                             bitrate, peakBitrate)) {
        return nullptr;
    }
    return encoder;
}

V4L2Encoder::V4L2Encoder(scoped_refptr<::base::SequencedTaskRunner> taskRunner,
                         FetchOutputBufferCB fetchOutputBufferCb,
                         InputBufferDoneCB inputBufferDoneCb, OutputBufferDoneCB outputBufferDoneCb,
                         DrainDoneCB drainDoneCb, ErrorCB errorCb)
      : mFetchOutputBufferCb(fetchOutputBufferCb),
        mInputBufferDoneCb(inputBufferDoneCb),
        mOutputBufferDoneCb(outputBufferDoneCb),
        mDrainDoneCb(std::move(drainDoneCb)),
        mErrorCb(std::move(errorCb)),
        mTaskRunner(std::move(taskRunner)) {
    ALOGV("%s()", __func__);

    mWeakThis = mWeakThisFactory.GetWeakPtr();
}

V4L2Encoder::~V4L2Encoder() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    mWeakThisFactory.InvalidateWeakPtrs();

    // Flushing the encoder will stop polling and streaming on the V4L2 device queues.
    flush();

    // Deallocate all V4L2 device input and output buffers.
    destroyInputBuffers();
    destroyOutputBuffers();
}

bool V4L2Encoder::encode(std::unique_ptr<InputFrame> frame) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState != State::UNINITIALIZED);

    // If we're in the error state we can immediately return, freeing the input buffer.
    if (mState == State::ERROR) {
        return false;
    }

    if (!frame) {
        ALOGW("Empty encode request scheduled");
        return false;
    }

    mEncodeRequests.push(EncodeRequest(std::move(frame)));

    // If we were waiting for encode requests, start encoding again.
    if (mState == State::WAITING_FOR_INPUT_FRAME) {
        setState(State::ENCODING);
        mTaskRunner->PostTask(FROM_HERE,
                              ::base::BindOnce(&V4L2Encoder::handleEncodeRequest, mWeakThis));
    }

    return true;
}

void V4L2Encoder::drain() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    // We can only start draining if all the requests in our input queue has been queued on the V4L2
    // device input queue, so we mark the last item in the input queue as EOS.
    if (!mEncodeRequests.empty()) {
        ALOGV("Marking last item (index: %" PRIu64 ") in encode request queue as EOS",
              mEncodeRequests.back().video_frame->index());
        mEncodeRequests.back().end_of_stream = true;
        return;
    }

    // Start a drain operation on the device. If no buffers are currently queued the device will
    // return an empty buffer with the V4L2_BUF_FLAG_LAST flag set.
    handleDrainRequest();
}

void V4L2Encoder::flush() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    handleFlushRequest();
}

bool V4L2Encoder::setBitrate(uint32_t bitrate) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                              {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_BITRATE, bitrate)})) {
        ALOGE("Setting bitrate to %u failed", bitrate);
        return false;
    }
    return true;
}

bool V4L2Encoder::setPeakBitrate(uint32_t peakBitrate) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                              {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_BITRATE_PEAK, peakBitrate)})) {
        // TODO(b/190336806): Our stack doesn't support dynamic peak bitrate changes yet, ignore
        // errors for now.
        ALOGW("Setting peak bitrate to %u failed", peakBitrate);
    }
    return true;
}

bool V4L2Encoder::setFramerate(uint32_t framerate) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (framerate == 0) {
        ALOGE("Requesting invalid framerate 0");
        return false;
    }

    struct v4l2_streamparm parms;
    memset(&parms, 0, sizeof(v4l2_streamparm));
    parms.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
    parms.parm.output.timeperframe.numerator = 1;
    parms.parm.output.timeperframe.denominator = framerate;
    if (mDevice->ioctl(VIDIOC_S_PARM, &parms) != 0) {
        ALOGE("Setting framerate to %u failed", framerate);
        return false;
    }
    return true;
}

void V4L2Encoder::requestKeyframe() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    mKeyFrameCounter = 0;
}

VideoPixelFormat V4L2Encoder::inputFormat() const {
    return mInputLayout ? mInputLayout.value().mFormat : VideoPixelFormat::UNKNOWN;
}

bool V4L2Encoder::initialize(C2Config::profile_t outputProfile, std::optional<uint8_t> level,
                             const ui::Size& visibleSize, uint32_t stride, uint32_t keyFramePeriod,
                             C2Config::bitrate_mode_t bitrateMode, uint32_t bitrate,
                             std::optional<uint32_t> peakBitrate) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(keyFramePeriod > 0);

    mVisibleSize = visibleSize;
    mKeyFramePeriod = keyFramePeriod;
    mKeyFrameCounter = 0;

    // Open the V4L2 device for encoding to the requested output format.
    // TODO(dstaessens): Avoid conversion to VideoCodecProfile and use C2Config::profile_t directly.
    uint32_t outputPixelFormat = V4L2Device::c2ProfileToV4L2PixFmt(outputProfile, false);
    if (!outputPixelFormat) {
        ALOGE("Invalid output profile %s", profileToString(outputProfile));
        return false;
    }

    mDevice = V4L2Device::create();
    if (!mDevice) {
        ALOGE("Failed to create V4L2 device");
        return false;
    }

    if (!mDevice->open(V4L2Device::Type::kEncoder, outputPixelFormat)) {
        ALOGE("Failed to open device for profile %s (%s)", profileToString(outputProfile),
              fourccToString(outputPixelFormat).c_str());
        return false;
    }

    // Make sure the device has all required capabilities (multi-planar Memory-To-Memory and
    // streaming I/O), and whether flushing is supported.
    if (!mDevice->hasCapabilities(V4L2_CAP_VIDEO_M2M_MPLANE | V4L2_CAP_STREAMING)) {
        ALOGE("Device doesn't have the required capabilities");
        return false;
    }
    if (!mDevice->isCommandSupported(V4L2_ENC_CMD_STOP)) {
        ALOGE("Device does not support flushing (V4L2_ENC_CMD_STOP)");
        return false;
    }

    // Get input/output queues so we can send encode request to the device and get back the results.
    mInputQueue = mDevice->getQueue(V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE);
    mOutputQueue = mDevice->getQueue(V4L2_BUF_TYPE_VIDEO_CAPTURE_MPLANE);
    if (!mInputQueue || !mOutputQueue) {
        ALOGE("Failed to get V4L2 device queues");
        return false;
    }

    // Configure the requested bitrate mode and bitrate on the device.
    if (!configureBitrateMode(bitrateMode) || !setBitrate(bitrate)) return false;

    // If the bitrate mode is VBR we also need to configure the peak bitrate on the device.
    if ((bitrateMode == C2Config::BITRATE_VARIABLE) && !setPeakBitrate(*peakBitrate)) return false;

    // First try to configure the specified output format, as changing the output format can affect
    // the configured input format.
    if (!configureOutputFormat(outputProfile)) return false;

    // Configure the input format. If the device doesn't support the specified format we'll use one
    // of the device's preferred formats in combination with an input format convertor.
    if (!configureInputFormat(kInputPixelFormat, stride)) return false;

    // Create input and output buffers.
    if (!createInputBuffers() || !createOutputBuffers()) return false;

    // Configure the device, setting all required controls.
    if (!configureDevice(outputProfile, level)) return false;

    // We're ready to start encoding now.
    setState(State::WAITING_FOR_INPUT_FRAME);
    return true;
}

void V4L2Encoder::handleEncodeRequest() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState == State::ENCODING || mState == State::ERROR);

    // If we're in the error state we can immediately return.
    if (mState == State::ERROR) {
        return;
    }

    // It's possible we flushed the encoder since this function was scheduled.
    if (mEncodeRequests.empty()) {
        return;
    }

    // Get the next encode request from the queue.
    EncodeRequest& encodeRequest = mEncodeRequests.front();

    // Check if the device has free input buffers available. If not we'll switch to the
    // WAITING_FOR_INPUT_BUFFERS state, and resume encoding once we've dequeued an input buffer.
    // Note: The input buffers are not copied into the device's input buffers, but rather a memory
    // pointer is imported. We still have to throttle the number of enqueues queued simultaneously
    // on the device however.
    if (mInputQueue->freeBuffersCount() == 0) {
        ALOGV("Waiting for device to return input buffers");
        setState(State::WAITING_FOR_V4L2_BUFFER);
        return;
    }

    // Request the next frame to be a key frame each time the counter reaches 0.
    if (mKeyFrameCounter == 0) {
        if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                                  {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_FORCE_KEY_FRAME)})) {
            ALOGE("Failed requesting key frame");
            onError();
            return;
        }
    }
    mKeyFrameCounter = (mKeyFrameCounter + 1) % mKeyFramePeriod;

    // Enqueue the input frame in the V4L2 device.
    uint64_t index = encodeRequest.video_frame->index();
    uint64_t timestamp = encodeRequest.video_frame->timestamp();
    bool end_of_stream = encodeRequest.end_of_stream;
    if (!enqueueInputBuffer(std::move(encodeRequest.video_frame))) {
        ALOGE("Failed to enqueue input frame (index: %" PRIu64 ", timestamp: %" PRId64 ")", index,
              timestamp);
        onError();
        return;
    }
    mEncodeRequests.pop();

    // Start streaming and polling on the input and output queue if required.
    if (!mInputQueue->isStreaming()) {
        ALOG_ASSERT(!mOutputQueue->isStreaming());
        if (!mOutputQueue->streamon() || !mInputQueue->streamon()) {
            ALOGE("Failed to start streaming on input and output queue");
            onError();
            return;
        }
        startDevicePoll();
    }

    // Queue buffers on output queue. These buffers will be used to store the encoded bitstream.
    while (mOutputQueue->freeBuffersCount() > 0) {
        if (!enqueueOutputBuffer()) return;
    }

    // Drain the encoder if requested.
    if (end_of_stream) {
        handleDrainRequest();
        return;
    }

    if (mEncodeRequests.empty()) {
        setState(State::WAITING_FOR_INPUT_FRAME);
        return;
    }

    // Schedule the next buffer to be encoded.
    mTaskRunner->PostTask(FROM_HERE,
                          ::base::BindOnce(&V4L2Encoder::handleEncodeRequest, mWeakThis));
}

void V4L2Encoder::handleFlushRequest() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    // Stop the device poll thread.
    stopDevicePoll();

    // Stop streaming on the V4L2 device, which stops all currently queued encode operations and
    // releases all buffers currently in use by the device.
    for (auto& queue : {mInputQueue, mOutputQueue}) {
        if (queue && queue->isStreaming() && !queue->streamoff()) {
            ALOGE("Failed to stop streaming on the device queue");
            onError();
        }
    }

    // Clear all outstanding encode requests and references to input and output queue buffers.
    while (!mEncodeRequests.empty()) {
        mEncodeRequests.pop();
    }
    for (auto& buf : mInputBuffers) {
        buf = nullptr;
    }
    for (auto& buf : mOutputBuffers) {
        buf = nullptr;
    }

    // Streaming and polling on the V4L2 device input and output queues will be resumed once new
    // encode work is queued.
    if (mState != State::ERROR) {
        setState(State::WAITING_FOR_INPUT_FRAME);
    }
}

void V4L2Encoder::handleDrainRequest() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (mState == State::DRAINING || mState == State::ERROR) {
        return;
    }

    setState(State::DRAINING);

    // If we're not streaming we can consider the request completed immediately.
    if (!mInputQueue->isStreaming()) {
        onDrainDone(true);
        return;
    }

    struct v4l2_encoder_cmd cmd;
    memset(&cmd, 0, sizeof(v4l2_encoder_cmd));
    cmd.cmd = V4L2_ENC_CMD_STOP;
    if (mDevice->ioctl(VIDIOC_ENCODER_CMD, &cmd) != 0) {
        ALOGE("Failed to stop encoder");
        onDrainDone(false);
        return;
    }
    ALOGV("%s(): Sent STOP command to encoder", __func__);
}

void V4L2Encoder::onDrainDone(bool done) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState == State::DRAINING || mState == State::ERROR);

    if (mState == State::ERROR) {
        return;
    }

    if (!done) {
        ALOGE("draining the encoder failed");
        mDrainDoneCb.Run(false);
        onError();
        return;
    }

    ALOGV("Draining done");
    mDrainDoneCb.Run(true);

    // Draining the encoder is done, we can now start encoding again.
    if (!mEncodeRequests.empty()) {
        setState(State::ENCODING);
        mTaskRunner->PostTask(FROM_HERE,
                              ::base::BindOnce(&V4L2Encoder::handleEncodeRequest, mWeakThis));
    } else {
        setState(State::WAITING_FOR_INPUT_FRAME);
    }
}

bool V4L2Encoder::configureInputFormat(VideoPixelFormat inputFormat, uint32_t stride) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState == State::UNINITIALIZED);
    ALOG_ASSERT(!mInputQueue->isStreaming());
    ALOG_ASSERT(!isEmpty(mVisibleSize));

    // First try to use the requested pixel format directly.
    std::optional<struct v4l2_format> format;
    auto fourcc = Fourcc::fromVideoPixelFormat(inputFormat, false);
    if (fourcc) {
        format = mInputQueue->setFormat(fourcc->toV4L2PixFmt(), mVisibleSize, 0, stride);
    }

    // If the device doesn't support the requested input format we'll try the device's preferred
    // input pixel formats and use a format convertor. We need to try all formats as some formats
    // might not be supported for the configured output format.
    if (!format) {
        std::vector<uint32_t> preferredFormats =
                mDevice->preferredInputFormat(V4L2Device::Type::kEncoder);
        for (uint32_t i = 0; !format && i < preferredFormats.size(); ++i) {
            format = mInputQueue->setFormat(preferredFormats[i], mVisibleSize, 0, stride);
        }
    }

    if (!format) {
        ALOGE("Failed to set input format to %s", videoPixelFormatToString(inputFormat).c_str());
        return false;
    }

    // Check whether the negotiated input format is valid. The coded size might be adjusted to match
    // encoder minimums, maximums and alignment requirements of the currently selected formats.
    auto layout = V4L2Device::v4L2FormatToVideoFrameLayout(*format);
    if (!layout) {
        ALOGE("Invalid input layout");
        return false;
    }

    mInputLayout = layout.value();
    if (!contains(Rect(mInputLayout->mCodedSize.width, mInputLayout->mCodedSize.height),
                  Rect(mVisibleSize.width, mVisibleSize.height))) {
        ALOGE("Input size %s exceeds encoder capability, encoder can handle %s",
              toString(mVisibleSize).c_str(), toString(mInputLayout->mCodedSize).c_str());
        return false;
    }

    // Calculate the input coded size from the format.
    // TODO(dstaessens): How is this different from mInputLayout->coded_size()?
    mInputCodedSize = V4L2Device::allocatedSizeFromV4L2Format(*format);

    // Configuring the input format might cause the output buffer size to change.
    auto outputFormat = mOutputQueue->getFormat();
    if (!outputFormat.first) {
        ALOGE("Failed to get output format (errno: %i)", outputFormat.second);
        return false;
    }
    uint32_t AdjustedOutputBufferSize = outputFormat.first->fmt.pix_mp.plane_fmt[0].sizeimage;
    if (mOutputBufferSize != AdjustedOutputBufferSize) {
        mOutputBufferSize = AdjustedOutputBufferSize;
        ALOGV("Output buffer size adjusted to: %u", mOutputBufferSize);
    }

    // The coded input size might be different from the visible size due to alignment requirements,
    // So we need to specify the visible rectangle. Note that this rectangle might still be adjusted
    // due to hardware limitations.
    Rect visibleRectangle(mVisibleSize.width, mVisibleSize.height);

    struct v4l2_rect rect;
    memset(&rect, 0, sizeof(rect));
    rect.left = visibleRectangle.left;
    rect.top = visibleRectangle.top;
    rect.width = visibleRectangle.width();
    rect.height = visibleRectangle.height();

    // Try to adjust the visible rectangle using the VIDIOC_S_SELECTION command. If this is not
    // supported we'll try to use the VIDIOC_S_CROP command instead. The visible rectangle might be
    // adjusted to conform to hardware limitations (e.g. round to closest horizontal and vertical
    // offsets, width and height).
    struct v4l2_selection selection_arg;
    memset(&selection_arg, 0, sizeof(selection_arg));
    selection_arg.type = V4L2_BUF_TYPE_VIDEO_OUTPUT;
    selection_arg.target = V4L2_SEL_TGT_CROP;
    selection_arg.r = rect;
    if (mDevice->ioctl(VIDIOC_S_SELECTION, &selection_arg) == 0) {
        visibleRectangle = Rect(selection_arg.r.left, selection_arg.r.top,
                                selection_arg.r.left + selection_arg.r.width,
                                selection_arg.r.top + selection_arg.r.height);
    } else {
        struct v4l2_crop crop;
        memset(&crop, 0, sizeof(v4l2_crop));
        crop.type = V4L2_BUF_TYPE_VIDEO_OUTPUT_MPLANE;
        crop.c = rect;
        if (mDevice->ioctl(VIDIOC_S_CROP, &crop) != 0 ||
            mDevice->ioctl(VIDIOC_G_CROP, &crop) != 0) {
            ALOGE("Failed to crop to specified visible rectangle");
            return false;
        }
        visibleRectangle = Rect(crop.c.left, crop.c.top, crop.c.left + crop.c.width,
                                crop.c.top + crop.c.height);
    }

    ALOGV("Input format set to %s (size: %s, adjusted size: %dx%d, coded size: %s)",
          videoPixelFormatToString(mInputLayout->mFormat).c_str(), toString(mVisibleSize).c_str(),
          visibleRectangle.width(), visibleRectangle.height(), toString(mInputCodedSize).c_str());

    mVisibleSize.set(visibleRectangle.width(), visibleRectangle.height());
    return true;
}

bool V4L2Encoder::configureOutputFormat(C2Config::profile_t outputProfile) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState == State::UNINITIALIZED);
    ALOG_ASSERT(!mOutputQueue->isStreaming());
    ALOG_ASSERT(!isEmpty(mVisibleSize));

    auto format = mOutputQueue->setFormat(V4L2Device::c2ProfileToV4L2PixFmt(outputProfile, false),
                                          mVisibleSize, GetMaxOutputBufferSize(mVisibleSize));
    if (!format) {
        ALOGE("Failed to set output format to %s", profileToString(outputProfile));
        return false;
    }

    // The device might adjust the requested output buffer size to match hardware requirements.
    mOutputBufferSize = format->fmt.pix_mp.plane_fmt[0].sizeimage;

    ALOGV("Output format set to %s (buffer size: %u)", profileToString(outputProfile),
          mOutputBufferSize);
    return true;
}

bool V4L2Encoder::configureDevice(C2Config::profile_t outputProfile,
                                  std::optional<const uint8_t> outputH264Level) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    // Enable frame-level bitrate control. This is the only mandatory general control.
    if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                              {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_FRAME_RC_ENABLE, 1)})) {
        ALOGW("Failed enabling bitrate control");
        // TODO(b/161508368): V4L2_CID_MPEG_VIDEO_FRAME_RC_ENABLE is currently not supported yet,
        // assume the operation was successful for now.
    }

    // Additional optional controls:
    // - Enable macroblock-level bitrate control.
    // - Set GOP length to 0 to disable periodic key frames.
    mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG, {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_MB_RC_ENABLE, 1),
                                                V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_GOP_SIZE, 0)});

    // All controls below are H.264-specific, so we can return here if the profile is not H.264.
    if (outputProfile >= C2Config::PROFILE_AVC_BASELINE &&
        outputProfile <= C2Config::PROFILE_AVC_ENHANCED_MULTIVIEW_DEPTH_HIGH) {
        return configureH264(outputProfile, outputH264Level);
    }

    return true;
}

bool V4L2Encoder::configureH264(C2Config::profile_t outputProfile,
                                std::optional<const uint8_t> outputH264Level) {
    // When encoding H.264 we want to prepend SPS and PPS to each IDR for resilience. Some
    // devices support this through the V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR control.
    // Otherwise we have to cache the latest SPS and PPS and inject these manually.
    if (mDevice->isCtrlExposed(V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR)) {
        if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                                  {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_PREPEND_SPSPPS_TO_IDR, 1)})) {
            ALOGE("Failed to configure device to prepend SPS and PPS to each IDR");
            return false;
        }
        mInjectParamsBeforeIDR = false;
        ALOGV("Device supports prepending SPS and PPS to each IDR");
    } else {
        mInjectParamsBeforeIDR = true;
        ALOGV("Device doesn't support prepending SPS and PPS to IDR, injecting manually.");
    }

    // Set the H.264 profile.
    const int32_t profile = V4L2Device::c2ProfileToV4L2H264Profile(outputProfile);
    if (profile < 0) {
        ALOGE("Trying to set invalid H.264 profile");
        return false;
    }
    if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                              {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_H264_PROFILE, profile)})) {
        ALOGE("Failed setting H.264 profile to %u", outputProfile);
        return false;
    }

    std::vector<V4L2ExtCtrl> h264Ctrls;

    // No B-frames, for lowest decoding latency.
    h264Ctrls.emplace_back(V4L2_CID_MPEG_VIDEO_B_FRAMES, 0);
    // Quantization parameter maximum value (for variable bitrate control).
    h264Ctrls.emplace_back(V4L2_CID_MPEG_VIDEO_H264_MAX_QP, 51);

    // Set H.264 output level. Use Level 4.0 as fallback default.
    int32_t h264Level =
            static_cast<int32_t>(outputH264Level.value_or(V4L2_MPEG_VIDEO_H264_LEVEL_4_0));
    h264Ctrls.emplace_back(V4L2_CID_MPEG_VIDEO_H264_LEVEL, h264Level);

    // Ask not to put SPS and PPS into separate bitstream buffers.
    h264Ctrls.emplace_back(V4L2_CID_MPEG_VIDEO_HEADER_MODE,
                           V4L2_MPEG_VIDEO_HEADER_MODE_JOINED_WITH_1ST_FRAME);

    // Ignore return value as these controls are optional.
    mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG, std::move(h264Ctrls));

    return true;
}

bool V4L2Encoder::configureBitrateMode(C2Config::bitrate_mode_t bitrateMode) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    v4l2_mpeg_video_bitrate_mode v4l2BitrateMode =
            V4L2Device::c2BitrateModeToV4L2BitrateMode(bitrateMode);
    if (!mDevice->setExtCtrls(V4L2_CTRL_CLASS_MPEG,
                              {V4L2ExtCtrl(V4L2_CID_MPEG_VIDEO_BITRATE_MODE, v4l2BitrateMode)})) {
        // TODO(b/190336806): Our stack doesn't support bitrate mode changes yet. We default to CBR
        // which is currently the only supported mode so we can safely ignore this for now.
        ALOGW("Setting bitrate mode to %u failed", v4l2BitrateMode);
    }
    return true;
}

bool V4L2Encoder::startDevicePoll() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (!mDevice->startPolling(mTaskRunner,
                               ::base::BindRepeating(&V4L2Encoder::serviceDeviceTask, mWeakThis),
                               ::base::BindRepeating(&V4L2Encoder::onPollError, mWeakThis))) {
        ALOGE("Device poll thread failed to start");
        onError();
        return false;
    }

    ALOGV("Device poll started");
    return true;
}

bool V4L2Encoder::stopDevicePoll() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (!mDevice->stopPolling()) {
        ALOGE("Failed to stop polling on the device");
        onError();
        return false;
    }

    ALOGV("Device poll stopped");
    return true;
}

void V4L2Encoder::onPollError() {
    ALOGV("%s()", __func__);
    onError();
}

void V4L2Encoder::serviceDeviceTask(bool /*event*/) {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState != State::UNINITIALIZED);

    if (mState == State::ERROR) {
        return;
    }

    // Dequeue completed input (VIDEO_OUTPUT) buffers, and recycle to the free list.
    while (mInputQueue->queuedBuffersCount() > 0) {
        if (!dequeueInputBuffer()) break;
    }

    // Dequeue completed output (VIDEO_CAPTURE) buffers, and recycle to the free list.
    while (mOutputQueue->queuedBuffersCount() > 0) {
        if (!dequeueOutputBuffer()) break;
    }

    ALOGV("%s() - done", __func__);
}

bool V4L2Encoder::enqueueInputBuffer(std::unique_ptr<InputFrame> frame) {
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mInputQueue->freeBuffersCount() > 0);
    ALOG_ASSERT(mState == State::ENCODING);
    ALOG_ASSERT(frame);
    ALOG_ASSERT(mInputLayout->mFormat == frame->pixelFormat());
    ALOG_ASSERT(mInputLayout->mPlanes.size() == frame->planes().size());

    auto format = frame->pixelFormat();
    auto& planes = frame->planes();
    auto index = frame->index();
    auto timestamp = frame->timestamp();

    ALOGV("%s(): queuing input buffer (index: %" PRId64 ")", __func__, index);

    auto buffer = mInputQueue->getFreeBuffer();
    if (!buffer) {
        ALOGE("Failed to get free buffer from device input queue");
        return false;
    }

    // Mark the buffer with the frame's timestamp so we can identify the associated output buffers.
    buffer->setTimeStamp(
            {.tv_sec = static_cast<time_t>(timestamp / ::base::Time::kMicrosecondsPerSecond),
             .tv_usec = static_cast<time_t>(timestamp % ::base::Time::kMicrosecondsPerSecond)});
    size_t bufferId = buffer->bufferId();

    std::vector<int> fds = frame->fds();
    if (mInputLayout->mMultiPlanar) {
        // If the input format is multi-planar, then we need to submit one memory plane per color
        // plane of our input frames.
        for (size_t i = 0; i < planes.size(); ++i) {
            size_t bytesUsed = ::base::checked_cast<size_t>(
                    getArea(planeSize(format, i, mInputLayout->mCodedSize)).value());

            // TODO(crbug.com/901264): The way to pass an offset within a DMA-buf is not defined
            // in V4L2 specification, so we abuse data_offset for now. Fix it when we have the
            // right interface, including any necessary validation and potential alignment.
            buffer->setPlaneDataOffset(i, planes[i].mOffset);
            bytesUsed += planes[i].mOffset;
            // Workaround: filling length should not be needed. This is a bug of videobuf2 library.
            buffer->setPlaneSize(i, mInputLayout->mPlanes[i].mSize + planes[i].mOffset);
            buffer->setPlaneBytesUsed(i, bytesUsed);
        }
    } else {
        ALOG_ASSERT(!planes.empty());
        // If the input format is single-planar, then we only submit one buffer which contains
        // all the color planes.
        size_t bytesUsed = allocationSize(format, mInputLayout->mCodedSize);

        // TODO(crbug.com/901264): The way to pass an offset within a DMA-buf is not defined
        // in V4L2 specification, so we abuse data_offset for now. Fix it when we have the
        // right interface, including any necessary validation and potential alignment.
        buffer->setPlaneDataOffset(0, planes[0].mOffset);
        bytesUsed += planes[0].mOffset;
        // Workaround: filling length should not be needed. This is a bug of videobuf2 library.
        buffer->setPlaneSize(0, bytesUsed);
        buffer->setPlaneBytesUsed(0, bytesUsed);
        // We only have one memory plane so we shall submit only one FD. The others are duplicates
        // of the first one anyway.
        fds.resize(1);
    }

    if (!std::move(*buffer).queueDMABuf(fds)) {
        ALOGE("Failed to queue input buffer using QueueDMABuf");
        onError();
        return false;
    }

    ALOGV("Queued buffer in input queue (index: %" PRId64 ", timestamp: %" PRId64
          ", bufferId: %zu)",
          index, timestamp, bufferId);

    ALOG_ASSERT(!mInputBuffers[bufferId]);
    mInputBuffers[bufferId] = std::move(frame);

    return true;
}

bool V4L2Encoder::enqueueOutputBuffer() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mOutputQueue->freeBuffersCount() > 0);

    auto buffer = mOutputQueue->getFreeBuffer();
    if (!buffer) {
        ALOGE("Failed to get free buffer from device output queue");
        onError();
        return false;
    }

    std::unique_ptr<BitstreamBuffer> bitstreamBuffer;
    mFetchOutputBufferCb.Run(mOutputBufferSize, &bitstreamBuffer);
    if (!bitstreamBuffer) {
        ALOGE("Failed to fetch output block");
        onError();
        return false;
    }

    size_t bufferId = buffer->bufferId();

    std::vector<int> fds;
    fds.push_back(bitstreamBuffer->dmabuf->handle()->data[0]);
    if (!std::move(*buffer).queueDMABuf(fds)) {
        ALOGE("Failed to queue output buffer using QueueDMABuf");
        onError();
        return false;
    }

    ALOG_ASSERT(!mOutputBuffers[bufferId]);
    mOutputBuffers[bufferId] = std::move(bitstreamBuffer);
    ALOGV("%s(): Queued buffer in output queue (bufferId: %zu)", __func__, bufferId);
    return true;
}

bool V4L2Encoder::dequeueInputBuffer() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState != State::UNINITIALIZED);
    ALOG_ASSERT(mInputQueue->queuedBuffersCount() > 0);

    if (mState == State::ERROR) {
        return false;
    }

    bool success;
    V4L2ReadableBufferRef buffer;
    std::tie(success, buffer) = mInputQueue->dequeueBuffer();
    if (!success) {
        ALOGE("Failed to dequeue buffer from input queue");
        onError();
        return false;
    }
    if (!buffer) {
        // No more buffers ready to be dequeued in input queue.
        return false;
    }

    uint64_t index = mInputBuffers[buffer->bufferId()]->index();
    int64_t timestamp = buffer->getTimeStamp().tv_usec +
                        buffer->getTimeStamp().tv_sec * ::base::Time::kMicrosecondsPerSecond;
    ALOGV("Dequeued buffer from input queue (index: %" PRId64 ", timestamp: %" PRId64
          ", bufferId: %zu)",
          index, timestamp, buffer->bufferId());

    mInputBuffers[buffer->bufferId()] = nullptr;

    mInputBufferDoneCb.Run(index);

    // If we previously used up all input queue buffers we can start encoding again now.
    if ((mState == State::WAITING_FOR_V4L2_BUFFER) && !mEncodeRequests.empty()) {
        setState(State::ENCODING);
        mTaskRunner->PostTask(FROM_HERE,
                              ::base::BindOnce(&V4L2Encoder::handleEncodeRequest, mWeakThis));
    }

    return true;
}

bool V4L2Encoder::dequeueOutputBuffer() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(mState != State::UNINITIALIZED);
    ALOG_ASSERT(mOutputQueue->queuedBuffersCount() > 0);

    if (mState == State::ERROR) {
        return false;
    }

    bool success;
    V4L2ReadableBufferRef buffer;
    std::tie(success, buffer) = mOutputQueue->dequeueBuffer();
    if (!success) {
        ALOGE("Failed to dequeue buffer from output queue");
        onError();
        return false;
    }
    if (!buffer) {
        // No more buffers ready to be dequeued in output queue.
        return false;
    }

    size_t encodedDataSize = buffer->getPlaneBytesUsed(0) - buffer->getPlaneDataOffset(0);
    ::base::TimeDelta timestamp = ::base::TimeDelta::FromMicroseconds(
            buffer->getTimeStamp().tv_usec +
            buffer->getTimeStamp().tv_sec * ::base::Time::kMicrosecondsPerSecond);

    ALOGV("Dequeued buffer from output queue (timestamp: %" PRId64
          ", bufferId: %zu, data size: %zu, EOS: %d)",
          timestamp.InMicroseconds(), buffer->bufferId(), encodedDataSize, buffer->isLast());

    if (!mOutputBuffers[buffer->bufferId()]) {
        ALOGE("Failed to find output block associated with output buffer");
        onError();
        return false;
    }

    std::unique_ptr<BitstreamBuffer> bitstreamBuffer =
            std::move(mOutputBuffers[buffer->bufferId()]);
    if (encodedDataSize > 0) {
        if (!mInjectParamsBeforeIDR) {
            // No need to inject SPS or PPS before IDR frames, we can just return the buffer as-is.
            mOutputBufferDoneCb.Run(encodedDataSize, timestamp.InMicroseconds(),
                                    buffer->isKeyframe(), std::move(bitstreamBuffer));
        } else if (!buffer->isKeyframe()) {
            // We need to inject SPS and PPS before IDR frames, but this frame is not a key frame.
            // We can return the buffer as-is, but need to update our SPS and PPS cache if required.
            C2ConstLinearBlock constBlock = bitstreamBuffer->dmabuf->share(
                    bitstreamBuffer->dmabuf->offset(), encodedDataSize, C2Fence());
            C2ReadView readView = constBlock.map().get();
            extractSPSPPS(readView.data(), encodedDataSize, &mCachedSPS, &mCachedPPS);
            mOutputBufferDoneCb.Run(encodedDataSize, timestamp.InMicroseconds(),
                                    buffer->isKeyframe(), std::move(bitstreamBuffer));
        } else {
            // We need to inject our cached SPS and PPS NAL units to the IDR frame. It's possible
            // this frame already has SPS and PPS NAL units attached, in which case we only need to
            // update our cached SPS and PPS.
            C2ConstLinearBlock constBlock = bitstreamBuffer->dmabuf->share(
                    bitstreamBuffer->dmabuf->offset(), encodedDataSize, C2Fence());
            C2ReadView readView = constBlock.map().get();

            // Allocate a new buffer to copy the data with prepended SPS and PPS into.
            std::unique_ptr<BitstreamBuffer> prependedBitstreamBuffer;
            mFetchOutputBufferCb.Run(mOutputBufferSize, &prependedBitstreamBuffer);
            if (!prependedBitstreamBuffer) {
                ALOGE("Failed to fetch output block");
                onError();
                return false;
            }
            C2WriteView writeView = prependedBitstreamBuffer->dmabuf->map().get();

            // If there is not enough space in the output buffer just return the original buffer.
            size_t newSize = prependSPSPPSToIDR(readView.data(), encodedDataSize, writeView.data(),
                                                writeView.size(), &mCachedSPS, &mCachedPPS);
            if (newSize > 0) {
                mOutputBufferDoneCb.Run(newSize, timestamp.InMicroseconds(), buffer->isKeyframe(),
                                        std::move(prependedBitstreamBuffer));
            } else {
                mOutputBufferDoneCb.Run(encodedDataSize, timestamp.InMicroseconds(),
                                        buffer->isKeyframe(), std::move(bitstreamBuffer));
            }
        }
    }

    // If the buffer is marked as last and we were flushing the encoder, flushing is now done.
    if ((mState == State::DRAINING) && buffer->isLast()) {
        onDrainDone(true);
        // Start the encoder again.
        struct v4l2_encoder_cmd cmd;
        memset(&cmd, 0, sizeof(v4l2_encoder_cmd));
        cmd.cmd = V4L2_ENC_CMD_START;
        if (mDevice->ioctl(VIDIOC_ENCODER_CMD, &cmd) != 0) {
            ALOGE("Failed to restart encoder after draining (V4L2_ENC_CMD_START)");
            onError();
            return false;
        }
    }

    // Queue a new output buffer to replace the one we dequeued.
    buffer = nullptr;
    enqueueOutputBuffer();

    return true;
}

bool V4L2Encoder::createInputBuffers() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(!mInputQueue->isStreaming());
    ALOG_ASSERT(mInputBuffers.empty());

    // No memory is allocated here, we just generate a list of buffers on the input queue, which
    // will hold memory handles to the real buffers.
    if (mInputQueue->allocateBuffers(kInputBufferCount, V4L2_MEMORY_DMABUF) < kInputBufferCount) {
        ALOGE("Failed to create V4L2 input buffers.");
        return false;
    }

    mInputBuffers.resize(mInputQueue->allocatedBuffersCount());
    return true;
}

bool V4L2Encoder::createOutputBuffers() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(!mOutputQueue->isStreaming());
    ALOG_ASSERT(mOutputBuffers.empty());

    // No memory is allocated here, we just generate a list of buffers on the output queue, which
    // will hold memory handles to the real buffers.
    if (mOutputQueue->allocateBuffers(kOutputBufferCount, V4L2_MEMORY_DMABUF) <
        kOutputBufferCount) {
        ALOGE("Failed to create V4L2 output buffers.");
        return false;
    }

    mOutputBuffers.resize(mOutputQueue->allocatedBuffersCount());
    return true;
}

void V4L2Encoder::destroyInputBuffers() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(!mInputQueue->isStreaming());

    if (!mInputQueue || mInputQueue->allocatedBuffersCount() == 0) return;
    mInputQueue->deallocateBuffers();
    mInputBuffers.clear();
}

void V4L2Encoder::destroyOutputBuffers() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());
    ALOG_ASSERT(!mOutputQueue->isStreaming());

    if (!mOutputQueue || mOutputQueue->allocatedBuffersCount() == 0) return;
    mOutputQueue->deallocateBuffers();
    mOutputBuffers.clear();
}

void V4L2Encoder::onError() {
    ALOGV("%s()", __func__);
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    if (mState != State::ERROR) {
        setState(State::ERROR);
        mErrorCb.Run();
    }
}

void V4L2Encoder::setState(State state) {
    ALOG_ASSERT(mTaskRunner->RunsTasksInCurrentSequence());

    // Check whether the state change is valid.
    switch (state) {
    case State::UNINITIALIZED:
        break;
    case State::WAITING_FOR_INPUT_FRAME:
        ALOG_ASSERT(mState != State::ERROR);
        break;
    case State::WAITING_FOR_V4L2_BUFFER:
        ALOG_ASSERT(mState == State::ENCODING);
        break;
    case State::ENCODING:
        ALOG_ASSERT(mState == State::WAITING_FOR_INPUT_FRAME ||
                    mState == State::WAITING_FOR_V4L2_BUFFER || mState == State::DRAINING);
        break;
    case State::DRAINING:
        ALOG_ASSERT(mState == State::ENCODING || mState == State::WAITING_FOR_INPUT_FRAME);
        break;
    case State::ERROR:
        break;
    }

    ALOGV("Changed encoder state from %s to %s", stateToString(mState), stateToString(state));
    mState = state;
}

const char* V4L2Encoder::stateToString(State state) {
    switch (state) {
    case State::UNINITIALIZED:
        return "UNINITIALIZED";
    case State::WAITING_FOR_INPUT_FRAME:
        return "WAITING_FOR_INPUT_FRAME";
    case State::WAITING_FOR_V4L2_BUFFER:
        return "WAITING_FOR_V4L2_BUFFER";
    case State::ENCODING:
        return "ENCODING";
    case State::DRAINING:
        return "DRAINING";
    case State::ERROR:
        return "ERROR";
    }
}

}  // namespace android

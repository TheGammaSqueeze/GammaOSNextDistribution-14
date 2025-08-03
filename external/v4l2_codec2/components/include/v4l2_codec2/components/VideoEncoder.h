// Copyright 2021 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_ENCODER_H
#define ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_ENCODER_H

#include <stdint.h>
#include <memory>
#include <vector>

#include <base/callback.h>
#include <ui/Size.h>

#include <v4l2_codec2/common/Common.h>
#include <v4l2_codec2/common/VideoPixelFormat.h>
#include <v4l2_codec2/common/VideoTypes.h>

namespace android {

struct BitstreamBuffer;

class VideoEncoder {
public:
    // Number of buffers on component delays.
    static constexpr size_t kInputBufferCount = 2;
    static constexpr size_t kOutputBufferCount = 2;
    static constexpr VideoPixelFormat kInputPixelFormat = VideoPixelFormat::NV12;

    // The peak bitrate in function of the target bitrate, used when the bitrate mode is VBR.
    static constexpr uint32_t kPeakBitrateMultiplier = 2u;

    // The InputFrame class can be used to store raw video frames.
    // Note: The InputFrame does not take ownership of the data. The file descriptor is not
    //       duplicated and the caller is responsible for keeping the data alive until the buffer
    //       is returned by an InputBufferDoneCB() call.
    class InputFrame {
    public:
        InputFrame(std::vector<int>&& fds, const std::vector<VideoFramePlane>& planes,
                   VideoPixelFormat pixelFormat, uint64_t index, int64_t timestamp);
        ~InputFrame() = default;

        const std::vector<int>& fds() const { return mFds; }
        const std::vector<VideoFramePlane>& planes() const { return mPlanes; }
        VideoPixelFormat pixelFormat() const { return mPixelFormat; }
        uint64_t index() const { return mIndex; }
        int64_t timestamp() const { return mTimestamp; }

    private:
        const std::vector<int> mFds;
        std::vector<VideoFramePlane> mPlanes;
        VideoPixelFormat mPixelFormat;
        uint64_t mIndex = 0;
        int64_t mTimestamp = 0;
    };

    using FetchOutputBufferCB =
            ::base::RepeatingCallback<void(uint32_t, std::unique_ptr<BitstreamBuffer>* buffer)>;
    // TODO(dstaessens): Change callbacks to OnceCallback provided when requesting encode/drain.
    using InputBufferDoneCB = ::base::RepeatingCallback<void(uint64_t)>;
    using OutputBufferDoneCB = ::base::RepeatingCallback<void(
            size_t, int64_t, bool, std::unique_ptr<BitstreamBuffer> buffer)>;
    using DrainDoneCB = ::base::RepeatingCallback<void(bool)>;
    using ErrorCB = ::base::RepeatingCallback<void()>;

    virtual ~VideoEncoder() = default;

    // Encode the frame, |InputBufferDoneCB| and |OutputBufferDoneCB| will be called when done.
    virtual bool encode(std::unique_ptr<InputFrame> buffer) = 0;
    // Drain the encoder, |mDrainDoneCb| will be called when done.
    virtual void drain() = 0;
    // Flush the encoder, pending drain operations will be aborted.
    virtual void flush() = 0;

    // Set the target bitrate to the specified value, will affect all non-processed frames.
    virtual bool setBitrate(uint32_t bitrate) = 0;
    // Set the peak bitrate to the specified value. The peak bitrate must be larger or equal to the
    // target bitrate and is ignored if the bitrate mode is constant.
    virtual bool setPeakBitrate(uint32_t peakBitrate) = 0;

    // Set the framerate to the specified value, will affect all non-processed frames.
    virtual bool setFramerate(uint32_t framerate) = 0;
    // Request the next frame encoded to be a key frame, will affect the next non-processed frame.
    virtual void requestKeyframe() = 0;

    virtual VideoPixelFormat inputFormat() const = 0;
    virtual const ui::Size& visibleSize() const = 0;
    virtual const ui::Size& codedSize() const = 0;
};

}  // namespace android

#endif  // ANDROID_V4L2_CODEC2_COMPONENTS_VIDEO_ENCODER_H

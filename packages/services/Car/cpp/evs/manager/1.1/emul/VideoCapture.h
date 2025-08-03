/*
 * Copyright 2020 The Android Open Source Project
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
#ifndef ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_EMULVIDEOCAPTURE_H
#define ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_EMULVIDEOCAPTURE_H

#include <android-base/chrono_utils.h>
#include <linux/videodev2.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/Timers.h>

#include <atomic>
#include <filesystem>
#include <functional>
#include <thread>

namespace {

// Careful changing these -- we're using bit-wise ops to manipulate these
enum RunModes {
    STOPPED = 0,
    RUN = 1,
    STOPPING = 2,
};

enum StreamEvent {
    INIT = 0,
    PERIODIC,
    STOP,
    TERMINATED,
};

struct imageMetadata {
    uint32_t width;   // Image width in pixels
    uint32_t height;  // Image height in pixels
    uint32_t stride;  // Number of bytes from one row in memory
    uint32_t format;  // Image format
};
}  // namespace

typedef struct {
    struct imageMetadata info;
    uint32_t sequence;         // Counting frames in sequence
    struct timeval timestamp;  // Tells when this frame is generated
} imageBufferDesc;

namespace android {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {

class VideoCapture : public MessageHandler {
public:
    explicit VideoCapture(){};
    virtual ~VideoCapture();
    bool open(const std::string& path, const std::chrono::nanoseconds interval);
    void close();

    bool startStream(
            std::function<void(VideoCapture*, imageBufferDesc*, void*)> callback = nullptr);
    void stopStream();

    // Valid only after open()
    __u32 getWidth() { return mBufferInfo.info.width; }
    __u32 getHeight() { return mBufferInfo.info.height; }
    __u32 getStride() { return mBufferInfo.info.stride; }
    __u32 getV4LFormat() { return mBufferInfo.info.format; }

    // NULL until stream is started
    void* getLatestData() { return mPixelBuffer; }
    bool isFrameReady() { return mFrameReady; }
    void markFrameConsumed() { returnFrame(); }
    bool isOpen() { return mVideoReady; }

    int setParameter(struct v4l2_control& control);
    int getParameter(struct v4l2_control& control);

private:
    void collectFrames();
    void markFrameReady();
    bool returnFrame();

    // Handles the message from the looper
    void handleMessage(const android::Message& message) override;

    // Looper to message the frame generator thread
    android::sp<android::Looper> mLooper;

    // Background thread to dispatch generated frames
    std::thread mCaptureThread;

    // Stream event to control the looper
    StreamEvent mCurrentStreamEvent = StreamEvent::INIT;

    // Directory where source files exist
    std::filesystem::path mSourceDir;
    std::filesystem::directory_iterator mSrcIter;

    // Last time the frame was generated and sent
    nsecs_t mLastTimeFrameSent;

    // Desired interval to generate and send a frame
    std::chrono::nanoseconds mDesiredFrameInterval = 1000ms;

    // Source image descriptor
    imageBufferDesc mBufferInfo = {};

    // Buffer to store the source pixel data
    char* mPixelBuffer = nullptr;

    // Current pixel buffer size
    size_t mPixelBufferSize = 0;

    // Callback to tell about new frames
    std::function<void(VideoCapture*, imageBufferDesc*, void*)> mCallback;

    // Used to signal the frame loop (see RunModes below)
    std::atomic<int> mRunMode;

    // Set when a frame has been delivered
    std::atomic<bool> mFrameReady;

    // flag to tell whether it is ready to start a stream
    bool mVideoReady = false;
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace android

#endif  // ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_EMULVIDEOCAPTURE_

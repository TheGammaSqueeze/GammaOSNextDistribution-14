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

#include "emul/VideoCapture.h"

#include <android-base/logging.h>
#include <processgroup/sched_policy.h>

#include <assert.h>
#include <errno.h>
#include <error.h>
#include <fcntl.h>
#include <memory.h>
#include <png.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

#include <fstream>
#include <iomanip>

namespace {

const char* kPngFileExtension = ".png";
const char* kDumpFileExtension = ".bin";

bool validatePng(std::ifstream& source) {
    const int kSigSize = 8;
    png_byte header[kSigSize] = {0};
    source.read((char*)header, kSigSize);

    return source.good() && (png_sig_cmp(header, 0, kSigSize) == 0);
}

void readPngDataFromStream(png_structp pngPtr, png_bytep data, png_size_t length) {
    png_voidp p = png_get_io_ptr(pngPtr);
    ((std::ifstream*)p)->read((char*)data, length);
}

char* fillBufferFromPng(const std::string& filename, imageMetadata& info) {
    // Open a PNG file
    std::ifstream source(filename, std::ios::in | std::ios::binary);
    if (!source.is_open()) {
        LOG(ERROR) << "Failed to open " << filename;
        return nullptr;
    }

    // Validate an input PNG file
    if (!validatePng(source)) {
        LOG(ERROR) << filename << " is not a valid PNG file";
        source.close();
        return nullptr;
    }

    // Prepare a control structure
    png_structp pngPtr = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (!pngPtr) {
        LOG(ERROR) << "Failed to create a control structure";
        source.close();
        return nullptr;
    }

    // Set up an image info
    png_infop infoPtr = png_create_info_struct(pngPtr);
    if (!infoPtr) {
        LOG(ERROR) << " Failed to initialize a png_info";
        png_destroy_read_struct(&pngPtr, nullptr, nullptr);
        source.close();
        return nullptr;
    }

    // Set up an error handler
    if (setjmp(png_jmpbuf(pngPtr))) {
        png_destroy_read_struct(&pngPtr, &infoPtr, nullptr);
        source.close();
        return nullptr;
    }

    // Set up PNG reader and fetch the remaining header bytes
    png_set_read_fn(pngPtr, (png_voidp)&source, readPngDataFromStream);
    const int kSigSize = 8;
    png_set_sig_bytes(pngPtr, kSigSize);
    png_read_info(pngPtr, infoPtr);

    // Get basic image information
    png_uint_32 width = png_get_image_width(pngPtr, infoPtr);
    png_uint_32 height = png_get_image_height(pngPtr, infoPtr);
    png_uint_32 bitdepth = png_get_bit_depth(pngPtr, infoPtr);
    png_uint_32 channels = png_get_channels(pngPtr, infoPtr);
    png_uint_32 colorType = png_get_color_type(pngPtr, infoPtr);

    // Record video device info
    info.width = width;
    info.height = height;
    switch (colorType) {
        case PNG_COLOR_TYPE_GRAY:
            png_set_expand_gray_1_2_4_to_8(pngPtr);
            bitdepth = 8;
            info.format = V4L2_PIX_FMT_GREY;
            break;

        case PNG_COLOR_TYPE_RGB:
            info.format = V4L2_PIX_FMT_XBGR32;
            break;

        case PNG_COLOR_TYPE_RGB_ALPHA:
            info.format = V4L2_PIX_FMT_ABGR32;
            break;

        default:
            LOG(INFO) << "Unsupported PNG color type: " << colorType;
            return nullptr;
    }

    // If the image has a transparency set, convert it to a full Alpha channel
    if (png_get_valid(pngPtr, infoPtr, PNG_INFO_tRNS)) {
        png_set_tRNS_to_alpha(pngPtr);
        channels += 1;
        info.format = V4L2_PIX_FMT_ABGR32;
    }

    // Refresh PNG info
    png_read_update_info(pngPtr, infoPtr);

    // Allocate a buffer to contain pixel data.  This buffer will be managed
    // by the caller.
    const int stride = png_get_rowbytes(pngPtr, infoPtr);
    info.stride = stride;
    LOG(DEBUG) << "width = " << width << ", height = " << height << ", bitdepth = " << bitdepth
               << ", channels = " << channels << ", colorType = " << colorType
               << ", stride = " << stride;

    char* buffer = new char[info.stride * height];
    png_bytep* rowPtrs = new png_bytep[height];
    for (int r = 0; r < height; ++r) {
        rowPtrs[r] = reinterpret_cast<unsigned char*>(buffer) + r * stride;
    }

    // Read the image
    png_read_image(pngPtr, rowPtrs);
    png_read_end(pngPtr, nullptr);

    // Clean up
    png_destroy_read_struct(&pngPtr, &infoPtr, nullptr);
    delete[] rowPtrs;
    source.close();

    return buffer;
}
}  // namespace

namespace android {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {

VideoCapture::~VideoCapture() {
    // Stop active stream
    stopStream();

    // Close the device
    close();
}

bool VideoCapture::open(const std::string& path, const std::chrono::nanoseconds interval) {
    // Report device properties
    LOG(INFO) << "Open a virtual video stream with data from " << path;

    // Store the source location
    if (!std::filesystem::exists(path) || !std::filesystem::is_directory(path)) {
        LOG(INFO) << path << " does not exist or is not a directory.";
        return false;
    }

    // Sets a directory iterator
    LOG(INFO) << "directory_iterator is set to " << path;
    mSrcIter = std::filesystem::directory_iterator(path);
    mSourceDir = path;

    // Set a frame rate
    mDesiredFrameInterval = interval;

    // Make sure we're initialized to the STOPPED state
    mRunMode = STOPPED;
    mFrameReady = false;
    mVideoReady = true;

    // Ready to go!
    return true;
}

void VideoCapture::close() {
    LOG(DEBUG) << __FUNCTION__;

    // Stream must be stopped first!
    assert(mRunMode == STOPPED);

    // Tell this is now closed
    mVideoReady = false;

    // Free allocated resources
    delete[] mPixelBuffer;
}

bool VideoCapture::startStream(
        std::function<void(VideoCapture*, imageBufferDesc*, void*)> callback) {
    // Set the state of our background thread
    int prevRunMode = mRunMode.fetch_or(RUN);
    if (prevRunMode & RUN) {
        // The background thread is already running, so we can't start a new stream
        LOG(ERROR) << "Already in RUN state, so we can't start a new streaming thread";
        return false;
    }

    // Remembers who to tell about new frames as they arrive
    mCallback = callback;

    // Fires up a thread to generate and dispatch the video frames
    mCaptureThread = std::thread([&]() {
        if (mCurrentStreamEvent != StreamEvent::INIT) {
            LOG(ERROR) << "Not in the right state to start a video stream.  Current state is "
                       << mCurrentStreamEvent;
            return;
        }

        // We'll periodically send a new frame
        mCurrentStreamEvent = StreamEvent::PERIODIC;

        // Sets a background priority
        if (set_sched_policy(0, SP_BACKGROUND) != 0) {
            PLOG(WARNING) << "Failed to set background scheduling priority";
        }

        // Sets a looper for the communication
        if (android::Looper::getForThread() != nullptr) {
            LOG(DEBUG) << "Use existing looper thread";
        }

        mLooper = android::Looper::prepare(/*opts=*/0);
        if (mLooper == nullptr) {
            LOG(ERROR) << "Failed to initialize the looper.  Exiting the thread.";
            return;
        }

        // Requests to start generating frames periodically
        mLooper->sendMessage(this, StreamEvent::PERIODIC);

        // Polling the messages until the stream stops
        while (mRunMode == RUN) {
            mLooper->pollAll(/*timeoutMillis=*/-1);
        }

        LOG(INFO) << "Capture thread is exiting!!!";
    });

    LOG(DEBUG) << "Stream started.";
    return true;
}

void VideoCapture::stopStream() {
    // Tell the background thread to stop
    int prevRunMode = mRunMode.fetch_or(STOPPING);
    if (prevRunMode == STOPPED) {
        // The background thread wasn't running, so set the flag back to STOPPED
        mRunMode = STOPPED;
    } else if (prevRunMode & STOPPING) {
        LOG(ERROR) << "stopStream called while stream is already stopping.  "
                   << "Reentrancy is not supported!";
        return;
    } else {
        // Block until the background thread is stopped
        if (mCaptureThread.joinable()) {
            // Removes all pending messages and awake the looper
            mLooper->removeMessages(this, StreamEvent::PERIODIC);
            mLooper->wake();
            mCaptureThread.join();
        } else {
            LOG(ERROR) << "Capture thread is not joinable";
        }

        mRunMode = STOPPED;
        LOG(DEBUG) << "Capture thread stopped.";
    }

    // Drop our reference to the frame delivery callback interface
    mCallback = nullptr;
}

void VideoCapture::markFrameReady() {
    mFrameReady = true;
}

bool VideoCapture::returnFrame() {
    // We're using a single buffer synchronousely so just need to set
    // mFrameReady as false.
    mFrameReady = false;

    return true;
}

// This runs on a background thread to receive and dispatch video frames
void VideoCapture::collectFrames() {
    const std::filesystem::directory_iterator end_iter;
    imageMetadata header = {};
    static uint64_t sequence = 0;  // counting frames

    while (mPixelBuffer == nullptr && mSrcIter != end_iter) {
        LOG(INFO) << "Synthesizing a frame from " << mSrcIter->path();
        auto ext = mSrcIter->path().extension();
        if (ext == kPngFileExtension) {
            // Read PNG image; a buffer will be allocated inside
            mPixelBuffer = fillBufferFromPng(mSrcIter->path(), header);

            // Update frame info
            mPixelBufferSize = header.stride * header.height;
        } else if (ext == kDumpFileExtension) {
            // Read files dumped by the reference EVS HAL implementation
            std::ifstream fin(mSrcIter->path(), std::ios::in | std::ios::binary);
            if (fin.is_open()) {
                // Read a header
                fin.read((char*)&header, sizeof(header));
                const size_t length = header.stride * header.height;

                // Allocate memory for pixel data
                mPixelBuffer = new char[length];
                mPixelBufferSize = length;

                // Read pixels
                fin.read(mPixelBuffer, length);
                if (fin.gcount() != length) {
                    LOG(WARNING) << mSrcIter->path() << " contains less than expected.";
                }
                fin.close();
            } else {
                PLOG(ERROR) << "Failed to open " << mSrcIter->path();
            }
        } else {
            LOG(DEBUG) << "Unsupported file extension.  Ignores " << mSrcIter->path().filename();
        }

        // Moves to next file
        ++mSrcIter;
    }

    // Fill the buffer metadata
    mBufferInfo.info = header;
    mBufferInfo.sequence = sequence++;

    int64_t now = nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC));
    mBufferInfo.timestamp.tv_sec = (time_t)(now / 1000LL);
    mBufferInfo.timestamp.tv_usec = (suseconds_t)((now % 1000LL) * 1000LL);

    if (mCallback != nullptr) {
        mCallback(this, &mBufferInfo, mPixelBuffer);
    }

    // Delete a consumed pixel buffer
    delete[] mPixelBuffer;
    mPixelBuffer = nullptr;
    mPixelBufferSize = 0;

    // If the last file is processed, reset the iterator to the first file.
    if (mSrcIter == end_iter) {
        LOG(DEBUG) << "Rewinds the iterator to the beginning.";
        mSrcIter = std::filesystem::directory_iterator(mSourceDir);
    }
}

int VideoCapture::setParameter(v4l2_control& /*control*/) {
    // Not implemented yet.
    return -ENOSYS;
}

int VideoCapture::getParameter(v4l2_control& /*control*/) {
    // Not implemented yet.
    return -ENOSYS;
}

void VideoCapture::handleMessage(const android::Message& message) {
    const auto received = static_cast<StreamEvent>(message.what);
    switch (received) {
        case StreamEvent::PERIODIC: {
            // Generates a new frame and send
            collectFrames();

            // Updates a timestamp and arms a message for next frame
            mLastTimeFrameSent = systemTime(SYSTEM_TIME_MONOTONIC);
            const auto next = mLastTimeFrameSent + mDesiredFrameInterval.count();
            mLooper->sendMessageAtTime(next, this, received);
            break;
        }

        case StreamEvent::STOP: {
            // Stopping a frame generation
            LOG(INFO) << "Stop generating frames";
            break;
        }

        default:
            LOG(WARNING) << "Unknown event is received: " << received;
            break;
    }
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace android

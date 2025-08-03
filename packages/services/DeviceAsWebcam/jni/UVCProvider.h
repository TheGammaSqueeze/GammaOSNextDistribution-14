/*
 * Copyright (C) 2023 The Android Open Source Project
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

#pragma once

#include <Buffer.h>
#include <DeviceAsWebcamServiceManager.h>
#include <FrameProvider.h>
#include <Utils.h>
#include <android-base/unique_fd.h>
#include <android/hardware_buffer.h>
#include <linux/usb/g_uvc.h>
#include <linux/usb/video.h>
#include <atomic>
#include <thread>
#include <vector>
#include <unordered_set>

namespace android {
namespace webcam {

typedef std::vector<struct epoll_event> Events;

using android::base::unique_fd;

class EpollW {
  public:
    Status init();
    Status add(int fd, uint32_t events);
    Status modify(int fd, uint32_t events);
    Status remove(int fd);
    Events waitForEvents();

  private:
    unique_fd mEpollFd;
};

struct ConfigFrame {
    uint32_t index = 0;
    uint32_t width = 0;
    uint32_t height = 0;
    std::vector<uint32_t> intervals;
};

struct ConfigFormat {
    uint32_t formatIndex = 0;
    uint32_t fcc;
    std::vector<ConfigFrame> frames;
};

struct ConfigEndPoint {
    uint32_t streamingInterval = 0;
    uint32_t streamingMaxPacketSize = 0;
    uint32_t streamingMaxBurst = 0;
};

struct ConfigStreaming {
    ConfigEndPoint ep;
    std::vector<ConfigFormat> formats;
    uint32_t interfaceNumber = 0;
};

struct UVCProperties {
    std::string videoNode;
    std::string udc;
    ConfigStreaming streaming;
    uint32_t controlInterfaceNumber = 0;
};

// This struct has the information needed to uniquely identify a 'chosen' format : the format index
// which indexes into the format list advertised by the UVC V4L2 node, the frame index which indexes
// the frame size in the frame size list per format, and finally the frame interval for the
// particular format and size chosen.
struct FormatTriplet {
    // Note: These are '1' indexed.
    uint8_t formatIndex = 1;
    uint8_t frameSizeIndex = 1;
    uint32_t frameInterval = 0;
    FormatTriplet(uint8_t formatIndex, uint8_t frameSizeIndex, uint32_t frameInterval)
        : formatIndex(formatIndex), frameSizeIndex(frameSizeIndex), frameInterval(frameInterval) {}
};

// This class manages all things related to UVC event handling.
class UVCProvider : public std::enable_shared_from_this<UVCProvider> {
  public:
    static std::string getVideoNode(const std::unordered_set<std::string>& ignoredNodes);

    UVCProvider() = default;
    ~UVCProvider();

    Status init();
    // Start listening for UVC events
    Status startService(const std::unordered_set<std::string>& ignoredNodes);

    void stopService();

    int encodeImage(AHardwareBuffer* hardwareBuffer, long timestamp, jint rotation);

    void watchStreamEvent();

  private:
    // Created after a UVC_SETUP event has been received and processed by UVCProvider
    // This class manages stream related events UVC_STREAMON / STREAMOFF and queries by the host
    // for probing and committing controls.
    class UVCDevice : public BufferCreatorAndDestroyer {
      public:
        explicit UVCDevice(std::weak_ptr<UVCProvider> parent,
                           const std::unordered_set<std::string>& ignoredNodes);
        ~UVCDevice() override = default;
        void closeUVCFd();
        [[nodiscard]] bool isInited() const;
        int getUVCFd() { return mUVCFd.get(); }
        int getINotifyFd() { return mINotifyFd.get(); }
        const char* getCurrentVideoNode() { return mVideoNode.c_str(); }
        void processSetupEvent(const struct usb_ctrlrequest* request,
                               struct uvc_request_data* response);
        void processSetupControlEvent(const struct usb_ctrlrequest* request,
                                      struct uvc_request_data* response);
        void processSetupStreamingEvent(const struct usb_ctrlrequest* request,
                                        struct uvc_request_data* response);

        void processSetupClassEvent(const struct usb_ctrlrequest* request,
                                    struct uvc_request_data* response);
        void processDataEvent(const struct uvc_request_data*);
        void processStreamOnEvent();
        void processStreamOffEvent();
        void processStreamEvent();
        Status encodeImage(AHardwareBuffer* buffer, long timestamp, int rotation);

        // BufferCreatorAndDestroyer overrides
        Status allocateAndMapBuffers(
                std::shared_ptr<Buffer>* consumerBuffer,
                std::vector<std::shared_ptr<Buffer>>* producerBuffers) override;
        void destroyBuffers(std::shared_ptr<Buffer>& consumerBuffer,
                            std::vector<std::shared_ptr<Buffer>>& producerBuffers) override;

      private:
        std::shared_ptr<UVCProperties> parseUvcProperties();
        std::vector<ConfigFormat> getFormats();

        void getFrameIntervals(ConfigFrame* frame, ConfigFormat* format);
        void getFormatFrames(ConfigFormat* format);

        Status openV4L2DeviceAndSubscribe(const std::string& videoNode);
        void setStreamingControl(struct uvc_streaming_control* streamingControl,
                                 const FormatTriplet* req);
        void commitControls();

        std::shared_ptr<Buffer> mapBuffer(uint32_t i);
        static Status unmapBuffer(std::shared_ptr<Buffer>& buffer);

        Status getFrameAndQueueBufferToGadgetDriver(bool firstBuffer = false);

        struct uvc_streaming_control mProbe {};
        struct uvc_streaming_control mCommit {};
        uint8_t mCurrentControlState = UVC_VS_CONTROL_UNDEFINED;
        std::weak_ptr<UVCProvider> mParent;
        std::shared_ptr<UVCProperties> mUVCProperties;
        std::shared_ptr<BufferManager> mBufferManager;
        std::shared_ptr<FrameProvider> mFrameProvider;

        unique_fd mUVCFd;
        unique_fd mINotifyFd;

        // Path to /dev/video*, this is the node we open up and poll the fd for uvc / v4l2 events.
        std::string mVideoNode;
        struct v4l2_format mV4l2Format {};
        uint32_t mFps = 0;
        bool mInited = false;
    };

    void stopAndWaitForUVCListenerThread();
    void startUVCListenerThread();
    void ListenToUVCFds();

    void processUVCEvent();
    // returns true if service is stopped. false otherwise.
    bool processINotifyEvent();

    std::shared_ptr<UVCDevice> mUVCDevice;
    std::thread mUVCListenerThread;
    volatile bool mListenToUVCFds = true;
    EpollW mEpollW;
};

}  // namespace webcam
}  // namespace android

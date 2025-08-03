/*
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

#ifndef HARDWARE_GOOGLE_PIXEL_USB_MONITORFFS_H_
#define HARDWARE_GOOGLE_PIXEL_USB_MONITORFFS_H_

#include <android-base/unique_fd.h>
#include <pixelusb/CommonUtils.h>

#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace android {
namespace hardware {
namespace google {
namespace pixel {
namespace usb {

using ::android::base::unique_fd;

// MonitorFfs automously manages gadget pullup by monitoring
// the ep file status. Restarts the usb gadget when the ep
// owner restarts.
class MonitorFfs {
  private:
    // Monitors the endpoints Inotify events.
    unique_fd mInotifyFd;
    // Control pipe for shutting down the mMonitor thread.
    // mMonitor exits when SHUTDOWN_MONITOR is written into
    // mEventFd/
    unique_fd mEventFd;
    // Pools on mInotifyFd and mEventFd.
    unique_fd mEpollFd;
    std::vector<int> mWatchFd;

    // Maintains the list of Endpoints.
    std::vector<std::string> mEndpointList;
    // protects the CV.
    std::mutex mLock;
    std::condition_variable mCv;
    // protects mInotifyFd, mEpollFd.
    std::mutex mLockFd;

    // Flag to maintain the current status of gadget pullup.
    bool mCurrentUsbFunctionsApplied;

    // Thread object that executes the ep monitoring logic.
    std::unique_ptr<std::thread> mMonitor;
    // Callback to be invoked when gadget is pulled up.
    void (*mCallback)(bool functionsApplied, void *payload);
    void *mPayload;
    // Name of the USB gadget. Used for pullup.
    const char *const mGadgetName;
    // Monitor State
    bool mMonitorRunning;

  public:
    MonitorFfs(const char *const gadget);
    // Inits all the UniqueFds.
    void reset();
    // Starts monitoring endpoints and pullup the gadget when
    // the descriptors are written.
    bool startMonitor();
    // Waits for timeout_ms for gadget pull up to happen.
    // Returns immediately if the gadget is already pulled up.
    bool waitForPullUp(int timeout_ms);
    // Adds the given fd to the watch list.
    bool addInotifyFd(std::string fd);
    // Adds the given endpoint to the watch list.
    void addEndPoint(std::string ep);
    // Registers the async callback from the caller to notify the caller
    // when the gadget pull up happens.
    void registerFunctionsAppliedCallback(void (*callback)(bool functionsApplied, void *(payload)),
                                          void *payload);
    bool isMonitorRunning();
    // Ep monitoring and the gadget pull up logic.
    static void *startMonitorFd(void *param);
};

}  // namespace usb
}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_GOOGLE_PIXEL_USB_MONITORFFS_H_

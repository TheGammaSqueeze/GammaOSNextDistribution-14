/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "EvsEnumerator.h"

#include "ConfigManager.h"
#include "EvsGlDisplay.h"
#include "EvsV4lCamera.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <cutils/android_filesystem_config.h>
#include <hardware_legacy/uevent.h>
#include <hwbinder/IPCThreadState.h>

#include <sys/inotify.h>

#include <string_view>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {
namespace implementation {

using std::chrono_literals::operator""s;
using CameraDesc_1_0 = ::android::hardware::automotive::evs::V1_0::CameraDesc;
using CameraDesc_1_1 = ::android::hardware::automotive::evs::V1_1::CameraDesc;

// NOTE:  All members values are static so that all clients operate on the same state
//        That is to say, this is effectively a singleton despite the fact that HIDL
//        constructs a new instance for each client.
std::unordered_map<std::string, EvsEnumerator::CameraRecord> EvsEnumerator::sCameraList;
wp<EvsGlDisplay> EvsEnumerator::sActiveDisplay;
std::mutex EvsEnumerator::sLock;
std::condition_variable EvsEnumerator::sCameraSignal;
std::unique_ptr<ConfigManager> EvsEnumerator::sConfigManager;
sp<IAutomotiveDisplayProxyService> EvsEnumerator::sDisplayProxy;
std::unordered_map<uint8_t, uint64_t> EvsEnumerator::sDisplayPortList;
uint64_t EvsEnumerator::sInternalDisplayId;

// Constants
constexpr std::chrono::seconds kEnumerationTimeout = 10s;
constexpr std::string_view kDevicePath = "/dev/";
constexpr std::string_view kPrefix = "video";
constexpr size_t kEventBufferSize = 512;

bool EvsEnumerator::checkPermission() {
    hardware::IPCThreadState* ipc = hardware::IPCThreadState::self();
    if (AID_AUTOMOTIVE_EVS != ipc->getCallingUid() && AID_ROOT != ipc->getCallingUid()) {
        LOG(ERROR) << "EVS access denied: "
                   << "pid = " << ipc->getCallingPid() << ", uid = " << ipc->getCallingUid();
        return false;
    }

    return true;
}

void EvsEnumerator::EvsHotplugThread(std::atomic<bool>& running) {
    // Watch new video devices.
    int notifyFd = inotify_init();
    if (notifyFd < 0) {
        LOG(ERROR) << "Failed to initialize inotify.  Exiting a thread loop";
        return;
    }

    auto watchFd = inotify_add_watch(notifyFd, kDevicePath.data(), IN_CREATE | IN_DELETE);
    if (watchFd < 0) {
        LOG(ERROR) << "Failed to add a watch.  Exiting a thread loop";
        return;
    }

    LOG(INFO) << "Start monitoring new V4L2 devices";

    char eventBuf[kEventBufferSize] = {};
    while (running) {
        size_t len = read(notifyFd, eventBuf, sizeof(eventBuf));
        if (len < sizeof(struct inotify_event)) {
            // We have no valid event.
            continue;
        }

        size_t offset = 0;
        while (offset < len) {
            struct inotify_event* event =
                    reinterpret_cast<struct inotify_event*>(&eventBuf[offset]);
            offset += sizeof(struct inotify_event) + event->len;
            if (event->wd != watchFd || strncmp(kPrefix.data(), event->name, kPrefix.length())) {
                continue;
            }

            std::string deviceId = std::string(kDevicePath) + std::string(event->name);
            if (event->mask & IN_CREATE) {
                // This adds a device without validation.
                CameraRecord cam(deviceId.data());
                if (sConfigManager) {
                    std::unique_ptr<ConfigManager::CameraInfo>& camInfo =
                            sConfigManager->getCameraInfo(deviceId);
                    if (camInfo) {
                        cam.desc.metadata.setToExternal((uint8_t*)camInfo->characteristics,
                                                        get_camera_metadata_size(
                                                                camInfo->characteristics));
                    }
                }
                {
                    LOG(INFO) << "adding a camera " << deviceId;
                    std::lock_guard<std::mutex> lock(sLock);
                    sCameraList.insert_or_assign(deviceId, std::move(cam));
                    sCameraSignal.notify_all();
                }
            } else if (event->mask & IN_DELETE) {
                LOG(INFO) << "removing a camera " << deviceId;
                std::lock_guard<std::mutex> lock(sLock);
                sCameraList.erase(deviceId);
                sCameraSignal.notify_all();
            }
        }
    }

    return;
}

EvsEnumerator::EvsEnumerator(sp<IAutomotiveDisplayProxyService> proxyService) {
    LOG(DEBUG) << "EvsEnumerator is created.";

    if (sConfigManager == nullptr) {
        /* loads and initializes ConfigManager in a separate thread */
        sConfigManager = ConfigManager::Create();
    }

    if (sDisplayProxy == nullptr) {
        /* sets a car-window service handle */
        sDisplayProxy = proxyService;
    }

    enumerateCameras();
    enumerateDisplays();
}

void EvsEnumerator::enumerateCameras() {
    // For every video* entry in the dev folder, see if it reports suitable capabilities
    // WARNING:  Depending on the driver implementations this could be slow, especially if
    //           there are timeouts or round trips to hardware required to collect the needed
    //           information.  Platform implementers should consider hard coding this list of
    //           known good devices to speed up the startup time of their EVS implementation.
    //           For example, this code might be replaced with nothing more than:
    //                   sCameraList.emplace("/dev/video0");
    //                   sCameraList.emplace("/dev/video1");
    LOG(INFO) << __FUNCTION__ << ": Starting dev/video* enumeration";
    unsigned videoCount = 0;
    unsigned captureCount = 0;
    DIR* dir = opendir("/dev");
    if (!dir) {
        LOG_FATAL("Failed to open /dev folder\n");
    }
    struct dirent* entry;
    {
        std::lock_guard<std::mutex> lock(sLock);

        while ((entry = readdir(dir)) != nullptr) {
            // We're only looking for entries starting with 'video'
            if (strncmp(entry->d_name, "video", 5) == 0) {
                std::string deviceName("/dev/");
                deviceName += entry->d_name;
                videoCount++;
                if (sCameraList.find(deviceName) != sCameraList.end()) {
                    LOG(INFO) << deviceName << " has been added already.";
                    captureCount++;
                } else if (qualifyCaptureDevice(deviceName.c_str())) {
                    sCameraList.emplace(deviceName, deviceName.c_str());
                    captureCount++;
                }
            }
        }
    }

    LOG(INFO) << "Found " << captureCount << " qualified video capture devices "
              << "of " << videoCount << " checked.";
}

void EvsEnumerator::enumerateDisplays() {
    LOG(INFO) << __FUNCTION__ << ": Starting display enumeration";
    if (!sDisplayProxy) {
        LOG(ERROR) << "AutomotiveDisplayProxyService is not available!";
        return;
    }

    sDisplayProxy->getDisplayIdList([](const auto& displayIds) {
        // The first entry of the list is the internal display.  See
        // SurfaceFlinger::getPhysicalDisplayIds() implementation.
        if (displayIds.size() > 0) {
            sInternalDisplayId = displayIds[0];
            for (const auto& id : displayIds) {
                const auto port = id & 0xFF;
                LOG(INFO) << "Display " << std::hex << id << " is detected on the port, " << port;
                sDisplayPortList.insert_or_assign(port, id);
            }
        }
    });

    LOG(INFO) << "Found " << sDisplayPortList.size() << " displays";
}

// Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
Return<void> EvsEnumerator::getCameraList(getCameraList_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Void();
    }

    {
        std::unique_lock<std::mutex> lock(sLock);
        if (sCameraList.size() < 1) {
            // No qualified device has been found.  Wait until new device is ready,
            // for 10 seconds.
            if (!sCameraSignal.wait_for(lock, kEnumerationTimeout,
                                        [] { return sCameraList.size() > 0; })) {
                LOG(DEBUG) << "Timer expired.  No new device has been added.";
            }
        }
    }

    const unsigned numCameras = sCameraList.size();

    // Build up a packed array of CameraDesc for return
    hidl_vec<CameraDesc_1_0> hidlCameras;
    hidlCameras.resize(numCameras);
    unsigned i = 0;
    for (const auto& [key, cam] : sCameraList) {
        hidlCameras[i++] = cam.desc.v1;
    }

    // Send back the results
    LOG(DEBUG) << "Reporting " << hidlCameras.size() << " cameras available";
    _hidl_cb(hidlCameras);

    // HIDL convention says we return Void if we sent our result back via callback
    return Void();
}

Return<sp<IEvsCamera_1_0>> EvsEnumerator::openCamera(const hidl_string& cameraId) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return nullptr;
    }

    // Is this a recognized camera id?
    CameraRecord* pRecord = findCameraById(cameraId);
    if (pRecord == nullptr) {
        LOG(ERROR) << cameraId << " does not exist!";
        return nullptr;
    }

    // Has this camera already been instantiated by another caller?
    sp<EvsV4lCamera> pActiveCamera = pRecord->activeInstance.promote();
    if (pActiveCamera != nullptr) {
        LOG(WARNING) << "Killing previous camera because of new caller";
        closeCamera(pActiveCamera);
    }

    // Construct a camera instance for the caller
    if (sConfigManager == nullptr) {
        pActiveCamera = EvsV4lCamera::Create(cameraId.c_str());
    } else {
        pActiveCamera =
                EvsV4lCamera::Create(cameraId.c_str(), sConfigManager->getCameraInfo(cameraId));
    }

    pRecord->activeInstance = pActiveCamera;
    if (pActiveCamera == nullptr) {
        LOG(ERROR) << "Failed to create new EvsV4lCamera object for " << cameraId;
    }

    return pActiveCamera;
}

Return<void> EvsEnumerator::closeCamera(const ::android::sp<IEvsCamera_1_0>& pCamera) {
    LOG(DEBUG) << __FUNCTION__;

    if (pCamera == nullptr) {
        LOG(ERROR) << "Ignoring call to closeCamera with null camera ptr";
        return Void();
    }

    // Get the camera id so we can find it in our list
    std::string cameraId;
    pCamera->getCameraInfo([&cameraId](CameraDesc_1_0 desc) { cameraId = desc.cameraId; });

    closeCamera_impl(pCamera, cameraId);

    return Void();
}

Return<sp<IEvsDisplay_1_0>> EvsEnumerator::openDisplay() {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return nullptr;
    }

    // If we already have a display active, then we need to shut it down so we can
    // give exclusive access to the new caller.
    sp<EvsGlDisplay> pActiveDisplay = sActiveDisplay.promote();
    if (pActiveDisplay != nullptr) {
        LOG(WARNING) << "Killing previous display because of new caller";
        closeDisplay(pActiveDisplay);
    }

    // Create a new display interface and return it.
    pActiveDisplay = new EvsGlDisplay(sDisplayProxy, sInternalDisplayId);
    sActiveDisplay = pActiveDisplay;

    LOG(DEBUG) << "Returning new EvsGlDisplay object " << pActiveDisplay.get();
    return pActiveDisplay;
}

Return<void> EvsEnumerator::closeDisplay(const ::android::sp<IEvsDisplay_1_0>& pDisplay) {
    LOG(DEBUG) << __FUNCTION__;

    // Do we still have a display object we think should be active?
    sp<EvsGlDisplay> pActiveDisplay = sActiveDisplay.promote();
    if (pActiveDisplay == nullptr) {
        LOG(ERROR) << "Somehow a display is being destroyed "
                   << "when the enumerator didn't know one existed";
    } else if (sActiveDisplay != pDisplay) {
        LOG(WARNING) << "Ignoring close of previously orphaned display - why did a client steal?";
    } else {
        // Drop the active display
        pActiveDisplay->forceShutdown();
        sActiveDisplay = nullptr;
    }

    return Void();
}

Return<EvsDisplayState> EvsEnumerator::getDisplayState() {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return EvsDisplayState::DEAD;
    }

    // Do we still have a display object we think should be active?
    sp<IEvsDisplay_1_0> pActiveDisplay = sActiveDisplay.promote();
    if (pActiveDisplay != nullptr) {
        return pActiveDisplay->getDisplayState();
    } else {
        return EvsDisplayState::NOT_OPEN;
    }
}

// Methods from ::android::hardware::automotive::evs::V1_1::IEvsEnumerator follow.
Return<void> EvsEnumerator::getCameraList_1_1(getCameraList_1_1_cb _hidl_cb) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Void();
    }

    {
        std::unique_lock<std::mutex> lock(sLock);
        if (sCameraList.size() < 1) {
            // No qualified device has been found.  Wait until new device is ready,
            if (!sCameraSignal.wait_for(lock, kEnumerationTimeout,
                                        [] { return sCameraList.size() > 0; })) {
                LOG(DEBUG) << "Timer expired.  No new device has been added.";
            }
        }
    }

    std::vector<CameraDesc_1_1> hidlCameras;
    if (sConfigManager == nullptr) {
        auto numCameras = sCameraList.size();

        // Build up a packed array of CameraDesc for return
        hidlCameras.resize(numCameras);
        unsigned i = 0;
        for (auto&& [key, cam] : sCameraList) {
            hidlCameras[i++] = cam.desc;
        }
    } else {
        // Build up a packed array of CameraDesc for return
        for (auto&& [key, cam] : sCameraList) {
            std::unique_ptr<ConfigManager::CameraInfo>& tempInfo =
                    sConfigManager->getCameraInfo(key);
            if (tempInfo != nullptr) {
                cam.desc.metadata.setToExternal((uint8_t*)tempInfo->characteristics,
                                                get_camera_metadata_size(
                                                        tempInfo->characteristics));
            }

            hidlCameras.emplace_back(cam.desc);
        }

        // Adding camera groups that represent logical camera devices
        auto camGroups = sConfigManager->getCameraGroupIdList();
        for (auto&& id : camGroups) {
            if (sCameraList.find(id) != sCameraList.end()) {
                // Already exists in the list
                continue;
            }

            std::unique_ptr<ConfigManager::CameraGroupInfo>& tempInfo =
                    sConfigManager->getCameraGroupInfo(id);
            CameraRecord cam(id.c_str());
            if (tempInfo != nullptr) {
                cam.desc.metadata.setToExternal((uint8_t*)tempInfo->characteristics,
                                                get_camera_metadata_size(
                                                        tempInfo->characteristics));
            }

            sCameraList.emplace(id, cam);
            hidlCameras.emplace_back(cam.desc);
        }
    }

    // Send back the results
    _hidl_cb(hidlCameras);

    // HIDL convention says we return Void if we sent our result back via callback
    return Void();
}

Return<sp<IEvsCamera_1_1>> EvsEnumerator::openCamera_1_1(const hidl_string& cameraId,
                                                         const Stream& streamCfg) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return nullptr;
    }

    // Is this a recognized camera id?
    CameraRecord* pRecord = findCameraById(cameraId);
    if (pRecord == nullptr) {
        LOG(ERROR) << cameraId << " does not exist!";
        return nullptr;
    }

    // Has this camera already been instantiated by another caller?
    sp<EvsV4lCamera> pActiveCamera = pRecord->activeInstance.promote();
    if (pActiveCamera != nullptr) {
        LOG(WARNING) << "Killing previous camera because of new caller";
        closeCamera(pActiveCamera);
    }

    // Construct a camera instance for the caller
    if (sConfigManager == nullptr) {
        LOG(WARNING) << "ConfigManager is not available.  "
                     << "Given stream configuration is ignored.";
        pActiveCamera = EvsV4lCamera::Create(cameraId.c_str());
    } else {
        pActiveCamera = EvsV4lCamera::Create(cameraId.c_str(),
                                             sConfigManager->getCameraInfo(cameraId), &streamCfg);
    }
    pRecord->activeInstance = pActiveCamera;
    if (pActiveCamera == nullptr) {
        LOG(ERROR) << "Failed to create new EvsV4lCamera object for " << cameraId;
    }

    return pActiveCamera;
}

Return<void> EvsEnumerator::getDisplayIdList(getDisplayIdList_cb _list_cb) {
    hidl_vec<uint8_t> ids;

    if (sDisplayPortList.size() > 0) {
        ids.resize(sDisplayPortList.size());
        unsigned i = 0;
        ids[i++] = sInternalDisplayId & 0xFF;
        for (const auto& [port, id] : sDisplayPortList) {
            if (sInternalDisplayId != id) {
                ids[i++] = port;
            }
        }
    }

    _list_cb(ids);
    return Void();
}

Return<sp<IEvsDisplay_1_1>> EvsEnumerator::openDisplay_1_1(uint8_t port) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return nullptr;
    }

    // If we already have a display active, then we need to shut it down so we can
    // give exclusive access to the new caller.
    sp<EvsGlDisplay> pActiveDisplay = sActiveDisplay.promote();
    if (pActiveDisplay != nullptr) {
        LOG(WARNING) << "Killing previous display because of new caller";
        closeDisplay(pActiveDisplay);
    }

    // Create a new display interface and return it
    if (sDisplayPortList.find(port) == sDisplayPortList.end()) {
        LOG(ERROR) << "No display is available on the port " << static_cast<int32_t>(port);
        return nullptr;
    }

    pActiveDisplay = new EvsGlDisplay(sDisplayProxy, sDisplayPortList[port]);
    sActiveDisplay = pActiveDisplay;

    LOG(DEBUG) << "Returning new EvsGlDisplay object " << pActiveDisplay.get();
    return pActiveDisplay;
}

void EvsEnumerator::closeCamera_impl(const sp<IEvsCamera_1_0>& pCamera,
                                     const std::string& cameraId) {
    // Find the named camera
    CameraRecord* pRecord = findCameraById(cameraId);

    // Is the display being destroyed actually the one we think is active?
    if (!pRecord) {
        LOG(ERROR) << "Asked to close a camera whose name isn't recognized";
    } else {
        sp<EvsV4lCamera> pActiveCamera = pRecord->activeInstance.promote();

        if (pActiveCamera == nullptr) {
            LOG(ERROR) << "Somehow a camera is being destroyed "
                       << "when the enumerator didn't know one existed";
        } else if (pActiveCamera != pCamera) {
            // This can happen if the camera was aggressively reopened,
            // orphaning this previous instance
            LOG(WARNING) << "Ignoring close of previously orphaned camera "
                         << "- why did a client steal?";
        } else {
            // Drop the active camera
            pActiveCamera->shutdown();
            pRecord->activeInstance = nullptr;
        }
    }

    return;
}

bool EvsEnumerator::qualifyCaptureDevice(const char* deviceName) {
    class FileHandleWrapper {
    public:
        FileHandleWrapper(int fd) { mFd = fd; }
        ~FileHandleWrapper() {
            if (mFd > 0) close(mFd);
        }
        operator int() const { return mFd; }

    private:
        int mFd = -1;
    };

    FileHandleWrapper fd = open(deviceName, O_RDWR, 0);
    if (fd < 0) {
        return false;
    }

    v4l2_capability caps;
    int result = ioctl(fd, VIDIOC_QUERYCAP, &caps);
    if (result < 0) {
        return false;
    }
    if (((caps.capabilities & V4L2_CAP_VIDEO_CAPTURE) == 0) ||
        ((caps.capabilities & V4L2_CAP_STREAMING) == 0)) {
        return false;
    }

    // Enumerate the available capture formats (if any)
    v4l2_fmtdesc formatDescription;
    formatDescription.type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    bool found = false;
    for (int i = 0; !found; i++) {
        formatDescription.index = i;
        if (ioctl(fd, VIDIOC_ENUM_FMT, &formatDescription) == 0) {
            LOG(INFO) << "Format: 0x" << std::hex << formatDescription.pixelformat << " Type: 0x"
                      << std::hex << formatDescription.type
                      << " Desc: " << formatDescription.description << " Flags: 0x" << std::hex
                      << formatDescription.flags;
            switch (formatDescription.pixelformat) {
                case V4L2_PIX_FMT_YUYV:
                    found = true;
                    break;
                case V4L2_PIX_FMT_NV21:
                    found = true;
                    break;
                case V4L2_PIX_FMT_NV16:
                    found = true;
                    break;
                case V4L2_PIX_FMT_YVU420:
                    found = true;
                    break;
                case V4L2_PIX_FMT_RGB32:
                    found = true;
                    break;
#ifdef V4L2_PIX_FMT_ARGB32  // introduced with kernel v3.17
                case V4L2_PIX_FMT_ARGB32:
                    found = true;
                    break;
                case V4L2_PIX_FMT_XRGB32:
                    found = true;
                    break;
#endif  // V4L2_PIX_FMT_ARGB32
                default:
                    LOG(WARNING) << "Unsupported, " << std::hex << formatDescription.pixelformat;
                    break;
            }
        } else {
            // No more formats available.
            break;
        }
    }

    return found;
}

EvsEnumerator::CameraRecord* EvsEnumerator::findCameraById(const std::string& cameraId) {
    // Find the named camera
    auto found = sCameraList.find(cameraId);
    if (sCameraList.end() != found) {
        // Found a match!
        return &found->second;
    }

    // We didn't find a match
    return nullptr;
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<void> EvsEnumerator::getUltrasonicsArrayList(getUltrasonicsArrayList_cb _hidl_cb) {
    hidl_vec<UltrasonicsArrayDesc> ultrasonicsArrayDesc;
    _hidl_cb(ultrasonicsArrayDesc);
    return Void();
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<sp<IEvsUltrasonicsArray>> EvsEnumerator::openUltrasonicsArray(
        const hidl_string& ultrasonicsArrayId) {
    (void)ultrasonicsArrayId;
    return sp<IEvsUltrasonicsArray>();
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<void> EvsEnumerator::closeUltrasonicsArray(
        const ::android::sp<IEvsUltrasonicsArray>& evsUltrasonicsArray) {
    (void)evsUltrasonicsArray;
    return Void();
}

using android::base::EqualsIgnoreCase;
using android::base::Result;
using android::base::StringPrintf;
using android::base::WriteStringToFd;
Return<void> EvsEnumerator::debug(const hidl_handle& fd, const hidl_vec<hidl_string>& options) {
    if (fd.getNativeHandle() != nullptr && fd->numFds > 0) {
        parseCommand(fd->data[0], options);
    } else {
        LOG(ERROR) << "Given file descriptor is not valid.";
    }

    return {};
}

void EvsEnumerator::parseCommand(int fd, const hidl_vec<hidl_string>& options) {
    if (options.size() < 1) {
        WriteStringToFd("No option is given.\n", fd);
        cmdHelp(fd);
        return;
    }

    const std::string command = options[0];
    if (EqualsIgnoreCase(command, "--help")) {
        cmdHelp(fd);
    } else if (EqualsIgnoreCase(command, "--dump")) {
        cmdDump(fd, options);
    } else {
        WriteStringToFd(StringPrintf("Invalid option: %s\n", command.c_str()), fd);
    }
}

void EvsEnumerator::cmdHelp(int fd) {
    WriteStringToFd("--help: shows this help.\n"
                    "--dump [id] [start|stop] [directory]\n"
                    "\tDump camera frames to a target directory\n",
                    fd);
}

void EvsEnumerator::cmdDump(int fd, const hidl_vec<hidl_string>& options) {
    if (options.size() < 3) {
        WriteStringToFd("Necessary argument is missing\n", fd);
        cmdHelp(fd);
        return;
    }

    EvsEnumerator::CameraRecord* pRecord = findCameraById(options[1]);
    if (pRecord == nullptr) {
        WriteStringToFd(StringPrintf("%s is not active\n", options[1].c_str()), fd);
        return;
    }

    auto device = pRecord->activeInstance.promote();
    if (device == nullptr) {
        WriteStringToFd(StringPrintf("%s seems dead\n", options[1].c_str()), fd);
        return;
    }

    const std::string command = options[2];
    if (EqualsIgnoreCase(command, "start")) {
        // --dump [device id] start [path]
        if (options.size() < 4) {
            WriteStringToFd("Necessary argument is missing\n", fd);
            cmdHelp(fd);
            return;
        }

        const std::string path = options[3];
        auto ret = device->startDumpFrames(path);
        if (!ret.ok()) {
            WriteStringToFd(StringPrintf("Failed to start storing frames: %s\n",
                                         ret.error().message().c_str()),
                            fd);
        }
    } else if (EqualsIgnoreCase(command, "stop")) {
        // --dump [device id] stop
        auto ret = device->stopDumpFrames();
        if (!ret.ok()) {
            WriteStringToFd(StringPrintf("Failed to stop storing frames: %s\n",
                                         ret.error().message().c_str()),
                            fd);
        }
    } else {
        WriteStringToFd(StringPrintf("Unknown command: %s", command.c_str()), fd);
        cmdHelp(fd);
    }

    return;
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

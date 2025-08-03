/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "Enumerator.h"

#include "HalDisplay.h"
#include "IPermissionsChecker.h"
#include "emul/EvsEmulatedCamera.h"
#include "stats/StatsCollector.h"

#include <android-base/chrono_utils.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <cutils/android_filesystem_config.h>
#include <hwbinder/IPCThreadState.h>

#include <regex>  // NOLINT
#include <vector>

namespace {

using ::android::automotive::evs::V1_1::implementation::IPermissionsChecker;
using ::android::base::EqualsIgnoreCase;
using ::android::base::Error;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::hardware::hidl_handle;
using ::android::hardware::IPCThreadState;
using ::android::hardware::Void;
using ::android::hardware::automotive::evs::V1_0::DisplayState;
using ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc;
using ::android::hardware::camera::device::V3_2::Stream;

using IEvsCamera_1_0 = ::android::hardware::automotive::evs::V1_0::IEvsCamera;
using CameraDesc_1_0 = ::android::hardware::automotive::evs::V1_0::CameraDesc;
using CameraDesc_1_1 = ::android::hardware::automotive::evs::V1_1::CameraDesc;

const char* kSingleIndent = "\t";
const char* kDumpOptionAll = "all";
const char* kDumpDeviceCamera = "camera";
const char* kDumpDeviceDisplay = "display";

const char* kDumpCameraCommandCurrent = "--current";
const char* kDumpCameraCommandCollected = "--collected";
const char* kDumpCameraCommandCustom = "--custom";
const char* kDumpCameraCommandCustomStart = "start";
const char* kDumpCameraCommandCustomStop = "stop";

const int kDumpCameraMinNumArgs = 4;
const int kOptionDumpDeviceTypeIndex = 1;
const int kOptionDumpCameraTypeIndex = 2;
const int kOptionDumpCameraCommandIndex = 3;
const int kOptionDumpCameraArgsStartIndex = 4;

const std::regex kEmulatedCameraNamePattern("emulated/[0-9]+", std::regex_constants::icase);

// Display ID 255 is reserved for the special purpose.
constexpr int kExclusiveMainDisplayId = 255;

// This surprisingly is not included in STL until C++20.
template <template <class> class Container, typename T>
constexpr bool contains(const Container<T>& container, const T& value) {
    return (std::find(container.begin(), container.end(), value) != container.end());
}

// Removes the target value if present, and optionally executes a lambda.
template <typename Container, typename T, typename RemovalLambda>
constexpr void removeIfPresent(
        Container* container, const T& value, RemovalLambda removalLambda = []() {}) {
    auto it = std::find(container->begin(), container->end(), value);
    if (it != container->end()) {
        container->erase(it);
        removalLambda();
    }
}

class ProdPermissionChecker : public IPermissionsChecker {
public:
    bool processHasPermissionsForEvs() override {
        IPCThreadState* ipc = IPCThreadState::self();
        const auto userId = ipc->getCallingUid() / AID_USER_OFFSET;
        const auto appId = ipc->getCallingUid() % AID_USER_OFFSET;
        if (AID_AUTOMOTIVE_EVS != appId && AID_ROOT != appId && AID_SYSTEM != appId) {
            LOG(ERROR) << "EVS access denied? "
                       << "pid = " << ipc->getCallingPid() << ", userId = " << userId
                       << ", appId = " << appId;
            return false;
        }

        return true;
    }
};

}  // namespace

namespace android::automotive::evs::V1_1::implementation {

Enumerator::Enumerator(std::unique_ptr<ServiceFactory> serviceFactory,
                       std::unique_ptr<IStatsCollector> statsCollector,
                       std::unique_ptr<IPermissionsChecker> permissionChecker) :
      mServiceFactory(std::move(serviceFactory)),
      mStatsCollector(std::move(statsCollector)),
      mPermissionChecker(std::move(permissionChecker)) {
    // Get an internal display identifier.
    mServiceFactory->getService()->getDisplayIdList(
            [this](const android::hardware::hidl_vec<unsigned char>& displayPorts) {
                for (unsigned char port : displayPorts) {
                    mDisplayPorts.push_back(port);
                }

                if (mDisplayPorts.empty()) {
                    LOG(WARNING) << "No display is available to EVS service.";
                } else {
                    // The first element must be the internal display
                    mInternalDisplayPort = mDisplayPorts.front();
                }
            });

    removeIfPresent(&mDisplayPorts, kExclusiveMainDisplayId, []() {
        LOG(WARNING) << kExclusiveMainDisplayId
                     << " is reserved so will not be available for EVS service.";
    });

    mMonitorEnabled = mStatsCollector->startCollection().ok();
}

std::unique_ptr<Enumerator> Enumerator::build(
        std::unique_ptr<ServiceFactory> serviceFactory,
        std::unique_ptr<IStatsCollector> statsCollector,
        std::unique_ptr<IPermissionsChecker> permissionChecker) {
    // Connect with the underlying hardware enumerator.
    if (!serviceFactory->getService()) {
        return nullptr;
    }

    return std::unique_ptr<Enumerator>{new Enumerator(std::move(serviceFactory),
                                                      std::move(statsCollector),
                                                      std::move(permissionChecker))};
}

std::unique_ptr<Enumerator> Enumerator::build(const char* hardwareServiceName) {
    if (!hardwareServiceName) {
        return nullptr;
    }

    return build(std::make_unique<ProdServiceFactory>(hardwareServiceName),
                 std::make_unique<StatsCollector>(), std::make_unique<ProdPermissionChecker>());
}

bool Enumerator::isLogicalCamera(const camera_metadata_t* metadata) {
    bool found = false;

    if (metadata == nullptr) {
        LOG(ERROR) << "Metadata is null";
        return found;
    }

    camera_metadata_ro_entry_t entry;
    int rc =
            find_camera_metadata_ro_entry(metadata, ANDROID_REQUEST_AVAILABLE_CAPABILITIES, &entry);
    if (0 != rc) {
        // No capabilities are found in metadata.
        LOG(DEBUG) << __FUNCTION__ << " does not find a target entry";
        return found;
    }

    for (size_t i = 0; i < entry.count; ++i) {
        uint8_t capability = entry.data.u8[i];
        if (capability == ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
            found = true;
            break;
        }
    }

    if (!found) {
        LOG(DEBUG) << __FUNCTION__ << " does not find a logical multi camera cap";
    }
    return found;
}

std::unordered_set<std::string> Enumerator::getPhysicalCameraIds(const std::string& id) {
    std::unordered_set<std::string> physicalCameras;
    if (mCameraDevices.find(id) == mCameraDevices.end()) {
        LOG(ERROR) << "Queried device " << id << " does not exist!";
        return physicalCameras;
    }

    const camera_metadata_t* metadata =
            reinterpret_cast<camera_metadata_t*>(&mCameraDevices[id].metadata[0]);
    if (!isLogicalCamera(metadata)) {
        // EVS assumes that the device w/o a valid metadata is a physical
        // device.
        LOG(INFO) << id << " is not a logical camera device.";
        physicalCameras.emplace(id);
        return physicalCameras;
    }

    camera_metadata_ro_entry entry;
    int rc = find_camera_metadata_ro_entry(metadata, ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS,
                                           &entry);
    if (0 != rc) {
        LOG(ERROR) << "No physical camera ID is found for a logical camera device " << id;
        return physicalCameras;
    }

    const uint8_t* ids = entry.data.u8;
    size_t start = 0;
    for (size_t i = 0; i < entry.count; ++i) {
        if (ids[i] == '\0') {
            if (start != i) {
                std::string id(reinterpret_cast<const char*>(ids + start));
                physicalCameras.emplace(id);
            }
            start = i + 1;
        }
    }

    LOG(INFO) << id << " consists of " << physicalCameras.size() << " physical camera devices.";
    return physicalCameras;
}

// Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
Return<void> Enumerator::getCameraList(getCameraList_cb list_cb) {
    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        list_cb({});
        return Void();
    }

    hardware::hidl_vec<CameraDesc_1_0> cameraList;
    mServiceFactory->getService()->getCameraList([&cameraList](auto enumeratedCameras) {
        cameraList.resize(enumeratedCameras.size());
        unsigned count = 0;
        for (auto&& cam : enumeratedCameras) {
            cameraList[count++] = cam;
        }
    });

    // Update the cached device list.
    for (auto&& desc : cameraList) {
        auto it = mCameraDevices.find(desc.cameraId);
        if (it != mCameraDevices.end()) {
            it->second.v1 = desc;
        } else {
            CameraDesc desc_1_1 = { .v1 = desc };
            mCameraDevices.emplace(desc.cameraId, desc_1_1);
        }
    }

    list_cb(cameraList);
    return Void();
}

Return<sp<IEvsCamera_1_0>> Enumerator::openCamera(const hidl_string& cameraId) {
    LOG(DEBUG) << __FUNCTION__;
    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        return nullptr;
    }

    // Is the underlying hardware camera already open?
    sp<HalCamera> hwCamera;
    if (mActiveCameras.find(cameraId) != mActiveCameras.end()) {
        hwCamera = mActiveCameras[cameraId];
    } else {
        // Is the hardware camera available?
        sp<IEvsCamera_1_1> device;
        if (std::regex_match(cameraId.c_str(), kEmulatedCameraNamePattern)) {
            if (mEmulatedCameraDevices.find(cameraId) == mEmulatedCameraDevices.end()) {
                LOG(ERROR) << cameraId << " is not available";
            } else {
                device = EvsEmulatedCamera::Create(cameraId.c_str(),
                                                   mEmulatedCameraDevices[cameraId]);
            }
        } else {
            device = IEvsCamera_1_1::castFrom(mServiceFactory->getService()->openCamera(cameraId))
                             .withDefault(nullptr);
        }
        if (device == nullptr) {
            LOG(ERROR) << "Failed to open hardware camera " << cameraId;
        } else {
            // Calculates the usage statistics record identifier
            auto fn = mCameraDevices.hash_function();
            auto recordId = fn(cameraId) & 0xFF;
            hwCamera = new HalCamera(device, cameraId, recordId);
            if (hwCamera == nullptr) {
                LOG(ERROR) << "Failed to allocate camera wrapper object";
                mServiceFactory->getService()->closeCamera(device);
            }
        }
    }

    // Construct a virtual camera wrapper for this hardware camera
    sp<VirtualCamera> clientCamera;
    if (hwCamera != nullptr) {
        clientCamera = hwCamera->makeVirtualCamera();
    }

    // Add the hardware camera to our list, which will keep it alive via ref count
    if (clientCamera != nullptr) {
        mActiveCameras.try_emplace(cameraId, hwCamera);
    } else {
        LOG(ERROR) << "Requested camera " << cameraId << " not found or not available";
    }

    // Send the virtual camera object back to the client by strong pointer which will keep it alive
    return clientCamera;
}

Return<void> Enumerator::closeCamera(const ::android::sp<IEvsCamera_1_0>& clientCamera) {
    LOG(DEBUG) << __FUNCTION__;

    if (clientCamera == nullptr) {
        LOG(ERROR) << "Ignoring call with null camera pointer.";
        return Void();
    }

    // All our client cameras are actually VirtualCamera objects
    sp<VirtualCamera> virtualCamera = reinterpret_cast<VirtualCamera*>(clientCamera.get());

    // Find the parent camera that backs this virtual camera
    for (auto&& halCamera : virtualCamera->getHalCameras()) {
        // Tell the virtual camera's parent to clean it up and drop it
        // NOTE:  The camera objects will only actually destruct when the sp<> ref counts get to
        //        zero, so it is important to break all cyclic references.
        halCamera->disownVirtualCamera(virtualCamera);

        // Did we just remove the last client of this camera?
        if (halCamera->getClientCount() == 0) {
            // Take this now unused camera out of our list
            // NOTE:  This should drop our last reference to the camera, resulting in its
            //        destruction.
            mActiveCameras.erase(halCamera->getId());
            mServiceFactory->getService()->closeCamera(halCamera->getHwCamera());
            if (mMonitorEnabled) {
                mStatsCollector->unregisterClientToMonitor(halCamera->getId());
            }
        }
    }

    // Make sure the virtual camera's stream is stopped
    virtualCamera->stopVideoStream();

    return Void();
}

// Methods from ::android::hardware::automotive::evs::V1_1::IEvsEnumerator follow.
Return<sp<IEvsCamera_1_1>> Enumerator::openCamera_1_1(const hidl_string& cameraId,
                                                      const Stream& streamCfg) {
    LOG(DEBUG) << __FUNCTION__;
    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        return nullptr;
    }

    // If hwCamera is null, a requested camera device is either a logical camera
    // device or a hardware camera, which is not being used now.
    std::unordered_set<std::string> physicalCameras = getPhysicalCameraIds(cameraId);
    std::vector<sp<HalCamera>> sourceCameras;
    sp<HalCamera> hwCamera;
    bool success = true;

    // 1. Try to open inactive camera devices.
    for (auto&& id : physicalCameras) {
        auto it = mActiveCameras.find(id);
        if (it == mActiveCameras.end()) {
            sp<IEvsCamera_1_1> device;
            if (std::regex_match(cameraId.c_str(), kEmulatedCameraNamePattern)) {
                if (mEmulatedCameraDevices.find(id) == mEmulatedCameraDevices.end()) {
                    LOG(ERROR) << cameraId << " is not available";
                } else {
                    device = EvsEmulatedCamera::Create(id.c_str(), mEmulatedCameraDevices[id]);
                }
            } else {
                device = mServiceFactory->getService()->openCamera_1_1(id, streamCfg);
            }

            if (device == nullptr) {
                LOG(ERROR) << "Failed to open hardware camera " << cameraId;
                success = false;
                break;
            } else {
                // Calculates the usage statistics record identifier
                auto fn = mCameraDevices.hash_function();
                auto recordId = fn(id) & 0xFF;
                hwCamera = new HalCamera(device, id, recordId, streamCfg);
                if (hwCamera == nullptr) {
                    LOG(ERROR) << "Failed to allocate camera wrapper object";
                    mServiceFactory->getService()->closeCamera(device);
                    success = false;
                    break;
                }
            }

            // Add the hardware camera to our list, which will keep it alive via ref count
            mActiveCameras.try_emplace(id, hwCamera);
            if (mMonitorEnabled) {
                mStatsCollector->registerClientToMonitor(hwCamera);
            }

            sourceCameras.push_back(hwCamera);
        } else {
            if (it->second->getStreamConfig().id != streamCfg.id) {
                LOG(WARNING) << "Requested camera is already active in different configuration.";
            } else {
                sourceCameras.push_back(it->second);
            }
        }
    }

    if (!success || sourceCameras.size() < 1) {
        LOG(ERROR) << "Failed to open any physical camera device";
        return nullptr;
    }

    // TODO(b/147170360): Implement a logic to handle a failure.
    // 3. Create a proxy camera object
    sp<VirtualCamera> clientCamera = new VirtualCamera(sourceCameras);
    if (clientCamera == nullptr) {
        // TODO(b/206829268): Any resource needs to be cleaned up explicitly?
        LOG(ERROR) << "Failed to create a client camera object";
    } else {
        if (physicalCameras.size() > 1) {
            // VirtualCamera, which represents a logical device, caches its
            // descriptor.
            clientCamera->setDescriptor(&mCameraDevices[cameraId]);
        }

        // 4. Owns created proxy camera object
        for (auto&& hwCamera : sourceCameras) {
            if (!hwCamera->ownVirtualCamera(clientCamera)) {
                // TODO(b/206829268): Remove a reference to this camera from a virtual camera.
                // object.
                LOG(ERROR) << hwCamera->getId() << " failed to own a created proxy camera object.";
            }
        }
    }

    // Send the virtual camera object back to the client by strong pointer which will keep it alive
    return clientCamera;
}

Return<void> Enumerator::getCameraList_1_1(getCameraList_1_1_cb list_cb) {
    LOG(DEBUG) << __FUNCTION__;
    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        list_cb({});
        return Void();
    }

    hardware::hidl_vec<CameraDesc_1_1> hidlCameras;
    mServiceFactory->getService()->getCameraList_1_1(
            [&hidlCameras](hardware::hidl_vec<CameraDesc_1_1> enumeratedCameras) {
                hidlCameras.resize(enumeratedCameras.size());
                unsigned count = 0;
                for (auto&& camdesc : enumeratedCameras) {
                    hidlCameras[count++] = camdesc;
                }
            });

    // Update the cached device list
    mCameraDevices.clear();
    for (auto&& desc : hidlCameras) {
        mCameraDevices.insert_or_assign(desc.v1.cameraId, desc);
    }

    // Add emulated devices if there is any
    if (mEmulatedCameraDevices.size() > 0) {
        int index = hidlCameras.size();
        hidlCameras.resize(hidlCameras.size() + mEmulatedCameraDevices.size());
        for (auto&& [id, desc] : mEmulatedCameraDevices) {
            hidlCameras[index++].v1.cameraId = id;
        }
    }

    list_cb(hidlCameras);
    return Void();
}

Return<sp<IEvsDisplay_1_0>> Enumerator::openDisplay() {
    LOG(DEBUG) << __FUNCTION__;

    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        return nullptr;
    }

    if (mDisplayOwnedExclusively) {
        LOG(ERROR) << "Display is owned exclusively by another client.";
        return nullptr;
    }

    // We simply keep track of the most recently opened display instance.
    // In the underlying layers we expect that a new open will cause the previous
    // object to be destroyed.  This avoids any race conditions associated with
    // create/destroy order and provides a cleaner restart sequence if the previous owner
    // is non-responsive for some reason.
    // Request exclusive access to the EVS display
    sp<IEvsDisplay_1_0> pActiveDisplay = mServiceFactory->getService()->openDisplay();
    if (pActiveDisplay == nullptr) {
        LOG(ERROR) << "EVS Display unavailable";

        return nullptr;
    }

    // Remember (via weak pointer) who we think the most recently opened display is so that
    // we can proxy state requests from other callers to it.
    // TODO(b/206829268): Because of b/129284474, an additional class, HalDisplay, has been defined
    // and wraps the IEvsDisplay object the driver returns.  We may want to remove this additional
    // class when it is fixed properly.
    sp<IEvsDisplay_1_0> pHalDisplay = new HalDisplay(pActiveDisplay, mInternalDisplayPort);
    mActiveDisplay = pHalDisplay;

    return pHalDisplay;
}

Return<void> Enumerator::closeDisplay(const ::android::sp<IEvsDisplay_1_0>& display) {
    LOG(DEBUG) << __FUNCTION__;

    sp<IEvsDisplay_1_0> pActiveDisplay = mActiveDisplay.promote();

    // Drop the active display
    if (display.get() != pActiveDisplay.get()) {
        LOG(WARNING) << "Ignoring call to closeDisplay with unrecognized display object.";
    } else {
        // Pass this request through to the hardware layer
        sp<HalDisplay> halDisplay = reinterpret_cast<HalDisplay*>(pActiveDisplay.get());
        mServiceFactory->getService()->closeDisplay(halDisplay->getHwDisplay());
        mActiveDisplay = nullptr;
        mDisplayOwnedExclusively = false;
    }

    return Void();
}

Return<DisplayState> Enumerator::getDisplayState() {
    LOG(DEBUG) << __FUNCTION__;
    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        return DisplayState::DEAD;
    }

    // Do we have a display object we think should be active?
    sp<IEvsDisplay_1_0> pActiveDisplay = mActiveDisplay.promote();
    if (pActiveDisplay != nullptr) {
        // Pass this request through to the hardware layer
        return pActiveDisplay->getDisplayState();
    } else {
        // We don't have a live display right now
        mActiveDisplay = nullptr;
        return DisplayState::NOT_OPEN;
    }
}

Return<sp<IEvsDisplay_1_1>> Enumerator::openDisplay_1_1(uint8_t id) {
    LOG(DEBUG) << __FUNCTION__;

    if (!mPermissionChecker->processHasPermissionsForEvs()) {
        return nullptr;
    }

    if (mDisplayOwnedExclusively) {
        LOG(ERROR) << "Display is owned exclusively by another client.";
        return nullptr;
    }

    if (id == kExclusiveMainDisplayId) {
        // The client requests to open the primary display exclusively.
        id = mInternalDisplayPort;
        mDisplayOwnedExclusively = true;
    } else if (std::find(mDisplayPorts.begin(), mDisplayPorts.end(), id) == mDisplayPorts.end()) {
        LOG(ERROR) << "No display is available on the port " << static_cast<int32_t>(id);
        return nullptr;
    }

    // We simply keep track of the most recently opened display instance.
    // In the underlying layers we expect that a new open will cause the previous
    // object to be destroyed.  This avoids any race conditions associated with
    // create/destroy order and provides a cleaner restart sequence if the previous owner
    // is non-responsive for some reason.
    // Request exclusive access to the EVS display
    sp<IEvsDisplay_1_1> pActiveDisplay = mServiceFactory->getService()->openDisplay_1_1(id);
    if (pActiveDisplay == nullptr) {
        LOG(ERROR) << "EVS Display unavailable";

        return nullptr;
    }

    // Remember (via weak pointer) who we think the most recently opened display is so that
    // we can proxy state requests from other callers to it.
    // TODO(b/206829268): Because of b/129284474, an additional class, HalDisplay, has been defined
    // and wraps the IEvsDisplay object the driver returns.  We may want to remove this additional
    // class when it is fixed properly.
    sp<IEvsDisplay_1_1> pHalDisplay = new HalDisplay(pActiveDisplay, id);
    mActiveDisplay = pHalDisplay;

    return pHalDisplay;
}

Return<void> Enumerator::getDisplayIdList(getDisplayIdList_cb _list_cb) {
    return mServiceFactory->getService()->getDisplayIdList(_list_cb);
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<void> Enumerator::getUltrasonicsArrayList(getUltrasonicsArrayList_cb _hidl_cb) {
    hardware::hidl_vec<UltrasonicsArrayDesc> ultrasonicsArrayDesc;
    _hidl_cb(ultrasonicsArrayDesc);
    return Void();
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<sp<IEvsUltrasonicsArray>> Enumerator::openUltrasonicsArray(
        const hidl_string& ultrasonicsArrayId) {
    (void)ultrasonicsArrayId;
    sp<IEvsUltrasonicsArray> pEvsUltrasonicsArray;
    return pEvsUltrasonicsArray;
}

// TODO(b/149874793): Add implementation for EVS Manager and Sample driver
Return<void> Enumerator::closeUltrasonicsArray(
        const ::android::sp<IEvsUltrasonicsArray>& evsUltrasonicsArray) {
    (void)evsUltrasonicsArray;
    return Void();
}

Return<void> Enumerator::debug(const hidl_handle& fd, const hidl_vec<hidl_string>& options) {
    if (fd.getNativeHandle() != nullptr && fd->numFds > 0) {
        cmdDump(fd->data[0], options);
    } else {
        LOG(ERROR) << "Given file descriptor is not valid.";
    }

    return {};
}

void Enumerator::cmdDump(int fd, const hidl_vec<hidl_string>& options) {
    if (options.size() == 0) {
        WriteStringToFd("No option is given.\n", fd);
        cmdHelp(fd);
        return;
    }

    const std::string option = options[0];
    if (EqualsIgnoreCase(option, "--help")) {
        cmdHelp(fd);
    } else if (EqualsIgnoreCase(option, "--list")) {
        cmdList(fd, options);
    } else if (EqualsIgnoreCase(option, "--dump")) {
        cmdDumpDevice(fd, options);
    } else if (EqualsIgnoreCase(option, "--configure-emulated-camera")) {
        cmdConfigureEmulatedCamera(fd, options);
    } else {
        WriteStringToFd(StringPrintf("Invalid option: %s\n", option.c_str()), fd);
    }
}

void Enumerator::cmdHelp(int fd) {
    WriteStringToFd("--help: shows this help.\n"
                    "--list [all|camera|display]: lists camera or display devices or both "
                    "available to EVS manager.\n"
                    "--dump camera [all|device_id] --[current|collected|custom] [args]\n"
                    "\tcurrent: shows the current status\n"
                    "\tcollected: shows 10 most recent periodically collected camera usage "
                    "statistics\n"
                    "\tcustom: starts/stops collecting the camera usage statistics\n"
                    "\t\tstart [interval] [duration]: starts collecting usage statistics "
                    "at every [interval] during [duration].  Interval and duration are in "
                    "milliseconds.\n"
                    "\t\tstop: stops collecting usage statistics and shows collected records.\n"
                    "--dump display: shows current status of the display\n"
                    "--configure-emulated-camera [id] [path] [width] [height] [interval]\n"
                    "\tid: emulated device id to use; emulated/[0-9]+\n"
                    "\tpath: a path to the directory where source files are stored\n"
                    "\twidth: image width in pixels\n"
                    "\theight: image height in pixels\n"
                    "\tinterval: interval between consecutive frames in milliseconds.\n",
                    fd);
}

void Enumerator::cmdList(int fd, const hidl_vec<hidl_string>& options) {
    bool listCameras = true;
    bool listDisplays = true;
    if (options.size() > 1) {
        const std::string option = options[1];
        const bool listAll = EqualsIgnoreCase(option, kDumpOptionAll);
        listCameras = listAll || EqualsIgnoreCase(option, kDumpDeviceCamera);
        listDisplays = listAll || EqualsIgnoreCase(option, kDumpDeviceDisplay);
        if (!listCameras && !listDisplays) {
            WriteStringToFd(StringPrintf("Unrecognized option, %s, is ignored.\n", option.c_str()),
                            fd);

            // Nothing to show, return
            return;
        }
    }

    std::string buffer;
    if (listCameras) {
        StringAppendF(&buffer, "Camera devices available to EVS service:\n");
        if (mCameraDevices.size() < 1) {
            // Camera devices may not be enumerated yet.  This may fail if the
            // user is not permitted to use EVS service.
            getCameraList_1_1([](const auto cameras) {
                if (cameras.size() < 1) {
                    LOG(WARNING) << "No camera device is available to EVS.";
                }
            });
        }

        for (auto& [id, desc] : mCameraDevices) {
            StringAppendF(&buffer, "%s%s\n", kSingleIndent, id.c_str());
        }

        StringAppendF(&buffer, "%sCamera devices currently in use:\n", kSingleIndent);
        for (auto& [id, ptr] : mActiveCameras) {
            StringAppendF(&buffer, "%s%s\n", kSingleIndent, id.c_str());
        }
        StringAppendF(&buffer, "\n");
    }

    if (listDisplays) {
        if (mServiceFactory->getService() != nullptr) {
            StringAppendF(&buffer, "Display devices available to EVS service:\n");
            // Get an internal display identifier.
            mServiceFactory->getService()->getDisplayIdList([&](const auto& displayPorts) {
                for (auto&& port : displayPorts) {
                    StringAppendF(&buffer, "%sdisplay port %u\n", kSingleIndent,
                                  static_cast<unsigned>(port));
                }
            });
        } else {
            LOG(WARNING) << "EVS HAL implementation is not available.";
        }
    }

    WriteStringToFd(buffer, fd);
}

void Enumerator::cmdDumpDevice(int fd, const hidl_vec<hidl_string>& options) {
    // Dumps both cameras and displays if the target device type is not given
    bool dumpCameras = false;
    bool dumpDisplays = false;
    const auto numOptions = options.size();
    if (numOptions > kOptionDumpDeviceTypeIndex) {
        const std::string target = options[kOptionDumpDeviceTypeIndex];
        dumpCameras = EqualsIgnoreCase(target, kDumpDeviceCamera);
        dumpDisplays = EqualsIgnoreCase(target, kDumpDeviceDisplay);
        if (!dumpCameras && !dumpDisplays) {
            WriteStringToFd(StringPrintf("Unrecognized option, %s, is ignored.\n", target.c_str()),
                            fd);
            cmdHelp(fd);
            return;
        }
    } else {
        WriteStringToFd(StringPrintf("Necessary arguments are missing.  "
                                     "Please check the usages:\n"),
                        fd);
        cmdHelp(fd);
        return;
    }

    if (dumpCameras) {
        // --dump camera [all|device_id] --[current|collected|custom] [args]
        if (numOptions < kDumpCameraMinNumArgs) {
            WriteStringToFd(StringPrintf("Necessary arguments are missing.  "
                                         "Please check the usages:\n"),
                            fd);
            cmdHelp(fd);
            return;
        }

        const std::string deviceId = options[kOptionDumpCameraTypeIndex];
        auto target = mActiveCameras.find(deviceId);
        const bool dumpAllCameras = EqualsIgnoreCase(deviceId, kDumpOptionAll);
        if (!dumpAllCameras && target == mActiveCameras.end()) {
            // Unknown camera identifier
            WriteStringToFd(StringPrintf("Given camera ID %s is unknown or not active.\n",
                                         deviceId.c_str()),
                            fd);
            return;
        }

        const std::string command = options[kOptionDumpCameraCommandIndex];
        std::string cameraInfo;
        if (EqualsIgnoreCase(command, kDumpCameraCommandCurrent)) {
            // Active stream configuration from each active HalCamera objects
            if (!dumpAllCameras) {
                StringAppendF(&cameraInfo, "HalCamera: %s\n%s", deviceId.c_str(),
                              target->second->toString(kSingleIndent).c_str());
            } else {
                for (auto&& [id, handle] : mActiveCameras) {
                    // Appends the current status
                    cameraInfo += handle->toString(kSingleIndent);
                }
            }
        } else if (EqualsIgnoreCase(command, kDumpCameraCommandCollected)) {
            // Reads the usage statistics from active HalCamera objects
            if (mMonitorEnabled) {
                std::unordered_map<std::string, std::string> usageStrings =
                        mStatsCollector->toString(kSingleIndent);
                if (!dumpAllCameras) {
                    cameraInfo += usageStrings[deviceId];
                } else {
                    for (auto&& [id, stats] : usageStrings) {
                        cameraInfo += stats;
                    }
                }
            } else {
                WriteStringToFd(StringPrintf("Client monitor is not available.\n"), fd);
                return;
            }
        } else if (EqualsIgnoreCase(command, kDumpCameraCommandCustom)) {
            // Additional arguments are expected for this command:
            // --dump camera device_id --custom start [interval] [duration]
            // or, --dump camera device_id --custom stop
            if (numOptions < kDumpCameraMinNumArgs + 1) {
                WriteStringToFd(StringPrintf("Necessary arguments are missing. "
                                             "Please check the usages:\n"),
                                fd);
                cmdHelp(fd);
                return;
            }

            if (!mMonitorEnabled) {
                WriteStringToFd(StringPrintf("Client monitor is not available."), fd);
                return;
            }

            const std::string subcommand = options[kOptionDumpCameraArgsStartIndex];
            if (EqualsIgnoreCase(subcommand, kDumpCameraCommandCustomStart)) {
                using std::chrono::duration_cast;
                using std::chrono::milliseconds;
                using std::chrono::nanoseconds;
                nanoseconds interval = 0ns;
                nanoseconds duration = 0ns;
                if (numOptions > kOptionDumpCameraArgsStartIndex + 2) {
                    duration = duration_cast<nanoseconds>(
                            milliseconds(std::stoi(options[kOptionDumpCameraArgsStartIndex + 2])));
                }

                if (numOptions > kOptionDumpCameraArgsStartIndex + 1) {
                    interval = duration_cast<nanoseconds>(
                            milliseconds(std::stoi(options[kOptionDumpCameraArgsStartIndex + 1])));
                }

                // Starts a custom collection
                auto result = mStatsCollector->startCustomCollection(interval, duration);
                if (!result.ok()) {
                    LOG(ERROR) << "Failed to start a custom collection.  " << result.error();
                    StringAppendF(&cameraInfo, "Failed to start a custom collection. %s\n",
                                  result.error().message().c_str());
                }
            } else if (EqualsIgnoreCase(subcommand, kDumpCameraCommandCustomStop)) {
                if (!mMonitorEnabled) {
                    WriteStringToFd(StringPrintf("Client monitor is not available."), fd);
                    return;
                }

                auto result = mStatsCollector->stopCustomCollection(deviceId);
                if (!result.ok()) {
                    LOG(ERROR) << "Failed to stop a custom collection.  " << result.error();
                    StringAppendF(&cameraInfo, "Failed to stop a custom collection. %s\n",
                                  result.error().message().c_str());
                } else {
                    // Pull the custom collection
                    cameraInfo += *result;
                }
            } else {
                WriteStringToFd(StringPrintf("Unknown argument: %s\n", subcommand.c_str()), fd);
                cmdHelp(fd);
                return;
            }
        } else {
            WriteStringToFd(StringPrintf("Unknown command: %s\n"
                                         "Please check the usages:\n",
                                         command.c_str()),
                            fd);
            cmdHelp(fd);
            return;
        }

        // Outputs the report
        WriteStringToFd(cameraInfo, fd);
    }

    if (dumpDisplays) {
        HalDisplay* pDisplay = reinterpret_cast<HalDisplay*>(mActiveDisplay.promote().get());
        if (!pDisplay) {
            WriteStringToFd("No active display is found.\n", fd);
        } else {
            WriteStringToFd(pDisplay->toString(kSingleIndent), fd);
        }
    }
}

void Enumerator::cmdConfigureEmulatedCamera(int fd, const hidl_vec<hidl_string>& options) {
    if (options.size() < 6) {
        WriteStringToFd(StringPrintf("Necessary arguments are missing.\n"), fd);
        cmdHelp(fd);
        return;
    }

    // --configure-emulated-camera [id] [path] [width] [height] [interval]
    const std::string id = options[1];
    if (!std::regex_match(id.c_str(), kEmulatedCameraNamePattern)) {
        WriteStringToFd(StringPrintf("%s does not match to the pattern.\n", id.c_str()), fd);
        return;
    }

    if (mCameraDevices.find(id) != mCameraDevices.end()) {
        WriteStringToFd(StringPrintf("Updating %s's configuration.  "
                                     "This will get effective when currently active stream is "
                                     "closed.\n",
                                     id.c_str()),
                        fd);
    }

    std::string sourceDir = options[2];
    int width = std::stoi(options[3]);
    int height = std::stoi(options[4]);
    std::chrono::nanoseconds interval = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::milliseconds(std::stoi(options[5])));
    WriteStringToFd(StringPrintf("Configuring %s as:\n"
                                 "\tResolution: %dx%d\n"
                                 "\tInterval: %f ms\n",
                                 id.c_str(), width, height, interval.count() / 1000000.),
                    fd);

    EmulatedCameraDesc desc = {width, height, sourceDir, interval};
    mEmulatedCameraDevices.insert_or_assign(id, std::move(desc));
}

}  // namespace android::automotive::evs::V1_1::implementation

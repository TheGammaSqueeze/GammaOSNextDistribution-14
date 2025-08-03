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

#include "Enumerator.h"

#include "AidlEnumerator.h"
#include "HalDisplay.h"
#include "utils/include/Utils.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/binder_manager.h>
#include <android/hardware/automotive/evs/1.1/IEvsEnumerator.h>
#include <cutils/android_filesystem_config.h>

namespace {

namespace hidlevs = ::android::hardware::automotive::evs;

using ::aidl::android::hardware::automotive::evs::CameraDesc;
using ::aidl::android::hardware::automotive::evs::DisplayState;
using ::aidl::android::hardware::automotive::evs::EvsResult;
using ::aidl::android::hardware::automotive::evs::IEvsCamera;
using ::aidl::android::hardware::automotive::evs::IEvsDisplay;
using ::aidl::android::hardware::automotive::evs::IEvsEnumerator;
using ::aidl::android::hardware::automotive::evs::IEvsEnumeratorStatusCallback;
using ::aidl::android::hardware::automotive::evs::IEvsUltrasonicsArray;
using ::aidl::android::hardware::automotive::evs::Stream;
using ::aidl::android::hardware::automotive::evs::UltrasonicsArrayDesc;
using ::android::base::EqualsIgnoreCase;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::ndk::ScopedAStatus;

// For status dump function
constexpr const char kSingleIndent[] = "\t";
constexpr const char kDumpOptionAll[] = "all";
constexpr const char kDumpDeviceCamera[] = "camera";
constexpr const char kDumpDeviceDisplay[] = "display";
constexpr const char kDumpCameraCommandCurrent[] = "--current";
constexpr const char kDumpCameraCommandCollected[] = "--collected";
constexpr const char kDumpCameraCommandCustom[] = "--custom";
constexpr const char kDumpCameraCommandCustomStart[] = "start";
constexpr const char kDumpCameraCommandCustomStop[] = "stop";
constexpr int kDumpCameraMinNumArgs = 4;
constexpr int kOptionDumpDeviceTypeIndex = 1;
constexpr int kOptionDumpCameraTypeIndex = 2;
constexpr int kOptionDumpCameraCommandIndex = 3;
constexpr int kOptionDumpCameraArgsStartIndex = 4;

// Parameters for HAL connection
constexpr int64_t kSleepTimeMilliseconds = 1000;
constexpr int64_t kTimeoutMilliseconds = 30000;

// UIDs allowed to use this service
const std::set<uid_t> kAllowedUids = {AID_AUTOMOTIVE_EVS, AID_SYSTEM, AID_ROOT};

}  // namespace

namespace aidl::android::automotive::evs::implementation {

Enumerator::~Enumerator() {
    if (mClientsMonitor) {
        mClientsMonitor->stopCollection();
    }
}

std::shared_ptr<IEvsEnumerator> Enumerator::connectToAidlHal(
        const std::string_view& hardwareServiceName, bool blocking) {
    // Connect with the underlying hardware enumerator
    const std::string separator("/");
    const std::string instanceName =
            std::string(Enumerator::descriptor) + separator + std::string(hardwareServiceName);
    if (!AServiceManager_isDeclared(instanceName.data())) {
        return nullptr;
    }

    std::add_pointer_t<AIBinder*(const char*)> getService;
    if (blocking) {
        getService = AServiceManager_waitForService;
    } else {
        getService = AServiceManager_checkService;
    }

    auto service = IEvsEnumerator::fromBinder(::ndk::SpAIBinder(getService(instanceName.data())));
    if (!service) {
        return nullptr;
    }

    // Register a device status callback
    mDeviceStatusCallback =
            ::ndk::SharedRefBase::make<EvsDeviceStatusCallbackImpl>(ref<Enumerator>());
    if (!service->registerStatusCallback(mDeviceStatusCallback).isOk()) {
        LOG(WARNING) << "Failed to register a device status callback";
    }

    return std::move(service);
}

std::shared_ptr<IEvsEnumerator> Enumerator::connectToHidlHal(
        const std::string_view& hardwareServiceName) {
    // Connect with the underlying hardware enumerator
    ::android::sp<hidlevs::V1_1::IEvsEnumerator> service =
            hidlevs::V1_1::IEvsEnumerator::tryGetService(hardwareServiceName.data());
    if (!service) {
        return nullptr;
    }

    return std::move(::ndk::SharedRefBase::make<AidlEnumerator>(service));
}

bool Enumerator::init(const std::string_view& hardwareServiceName) {
    LOG(DEBUG) << __FUNCTION__;

    if (mHwEnumerator) {
        LOG(INFO) << "Enumerator is initialized already.";
        return true;
    }

    // Connect to EVS HAL implementation
    auto retryCount = 0;
    while (!mHwEnumerator && retryCount < (kTimeoutMilliseconds / kSleepTimeMilliseconds)) {
        mHwEnumerator = connectToAidlHal(hardwareServiceName, /* blocking= */ false);
        if (!mHwEnumerator) {
            LOG(INFO) << "Failed to connect to AIDL EVS HAL implementation.  "
                      << "Trying to connect to HIDL EVS HAL implementation instead.";
            mHwEnumerator = connectToHidlHal(hardwareServiceName);
            if (!mHwEnumerator) {
                LOG(INFO) << "No EVS HAL implementation is available.  Retrying after "
                          << kSleepTimeMilliseconds << " ms";
                std::this_thread::sleep_for(std::chrono::milliseconds(kSleepTimeMilliseconds));
                ++retryCount;
            }
        }
    }

    if (!mHwEnumerator) {
        LOG(ERROR) << "Failed to connect EVS HAL.";
        return false;
    }

    // Get a list of available displays and identify the internal display
    if (!mHwEnumerator->getDisplayIdList(&mDisplayPorts).isOk()) {
        LOG(WARNING)
                << "Failed to get a list of available displays. EVS Display may not work properly "
                   "if an active EVS HAL service implements HIDL v1.1 or AIDL EVS interface.";
    }

    const size_t numDisplays = mDisplayPorts.size();
    mDisplayPorts.erase(std::remove_if(mDisplayPorts.begin(), mDisplayPorts.end(),
                                       [](const auto id) { return id == kExclusiveDisplayId; }),
                        mDisplayPorts.end());
    if (numDisplays != mDisplayPorts.size()) {
        LOG(WARNING)
                << kExclusiveDisplayId
                << " is reserved for the special purpose so will not be available for EVS service.";
    }

    // The first element is the internal display if a returned list is not
    // empty.
    mInternalDisplayPort = mDisplayPorts.empty() ? kDisplayIdUnavailable : mDisplayPorts.front();
    mDisplayOwnedExclusively = false;

    // Starts the statistics collection
    mMonitorEnabled = false;
    mClientsMonitor = new (std::nothrow) StatsCollector();
    if (mClientsMonitor) {
        if (auto result = mClientsMonitor->startCollection(); !result.ok()) {
            LOG(ERROR) << "Failed to start the usage monitor: " << result.error();
        } else {
            mMonitorEnabled = true;
        }
    }

    return true;
}

bool Enumerator::checkPermission() const {
    const auto uid = AIBinder_getCallingUid();
    if (!mDisablePermissionCheck && kAllowedUids.find(uid) == kAllowedUids.end()) {
        LOG(ERROR) << "EVS access denied: "
                   << "pid = " << AIBinder_getCallingPid() << ", uid = " << uid;
        return false;
    }

    return true;
}

bool Enumerator::isLogicalCamera(const camera_metadata_t* metadata) const {
    if (metadata == nullptr) {
        LOG(INFO) << "Camera metadata is invalid";
        return false;
    }

    camera_metadata_ro_entry_t entry;
    int rc =
            find_camera_metadata_ro_entry(metadata, ANDROID_REQUEST_AVAILABLE_CAPABILITIES, &entry);
    if (rc != ::android::OK) {
        // No capabilities are found in metadata.
        LOG(DEBUG) << "No capability is found";
        return false;
    }

    for (size_t i = 0; i < entry.count; ++i) {
        uint8_t capability = entry.data.u8[i];
        if (capability == ANDROID_REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) {
            return true;
        }
    }

    return false;
}

std::unordered_set<std::string> Enumerator::getPhysicalCameraIds(const std::string& id) {
    std::unordered_set<std::string> physicalCameras;
    if (mCameraDevices.find(id) == mCameraDevices.end()) {
        LOG(ERROR) << "Queried device " << id << " is unknown";
        return physicalCameras;
    }

    const camera_metadata_t* metadata =
            reinterpret_cast<camera_metadata_t*>(&mCameraDevices[id].metadata[0]);
    if (!isLogicalCamera(metadata)) {
        // EVS assumes that the device w/o a valid metadata is a physical device.
        LOG(INFO) << id << " is not a logical camera device.";
        physicalCameras.insert(id);
        return physicalCameras;
    }

    camera_metadata_ro_entry entry;
    int rc = find_camera_metadata_ro_entry(metadata, ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS,
                                           &entry);
    if (rc != ::android::OK) {
        LOG(ERROR) << "No physical camera ID is found for a logical camera device " << id;
        return physicalCameras;
    }

    const uint8_t* ids = entry.data.u8;
    size_t start = 0;
    for (size_t i = 0; i < entry.count; ++i) {
        if (ids[i] == '\0') {
            if (start != i) {
                std::string id(reinterpret_cast<const char*>(ids + start));
                physicalCameras.insert(id);
            }
            start = i + 1;
        }
    }

    LOG(INFO) << id << " consists of " << physicalCameras.size() << " physical camera devices.";
    return physicalCameras;
}

// Methods from ::aidl::android::hardware::automotive::evs::IEvsEnumerator
ScopedAStatus Enumerator::isHardware(bool* flag) {
    *flag = false;
    return ScopedAStatus::ok();
}

ScopedAStatus Enumerator::getCameraList(std::vector<CameraDesc>* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    {
        std::lock_guard lock(mLock);
        auto status = mHwEnumerator->getCameraList(_aidl_return);
        if (!status.isOk()) {
            return status;
        }

        for (auto&& desc : *_aidl_return) {
            mCameraDevices.insert_or_assign(desc.id, desc);
        }

        return status;
    }
}

ScopedAStatus Enumerator::getStreamList(const CameraDesc& desc, std::vector<Stream>* _aidl_return) {
    std::shared_lock lock(mLock);
    return mHwEnumerator->getStreamList(desc, _aidl_return);
}

ScopedAStatus Enumerator::closeCamera(const std::shared_ptr<IEvsCamera>& cameraObj) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    if (!cameraObj) {
        LOG(WARNING) << "Ignoring a call with an invalid camera object";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    {
        std::lock_guard lock(mLock);
        // All our client cameras are actually VirtualCamera objects
        VirtualCamera* virtualCamera = reinterpret_cast<VirtualCamera*>(cameraObj.get());

        auto it = std::find_if(mActiveCameraClients.begin(), mActiveCameraClients.end(),
                               [virtualCamera](std::weak_ptr<VirtualCamera>& client) {
                                   auto current = client.lock();
                                   return current.get() == virtualCamera;
                               });
        if (it == mActiveCameraClients.end()) {
            LOG(ERROR) << "Ignore a request to close unknown client, " << virtualCamera;
            return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
        }

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
                auto status = mHwEnumerator->closeCamera(halCamera->getHwCamera());
                if (!status.isOk()) {
                    LOG(WARNING) << "Failed to close a camera with id = " << halCamera->getId()
                                 << ", error = " << status.getServiceSpecificError();
                }
                if (mMonitorEnabled) {
                    mClientsMonitor->unregisterClientToMonitor(halCamera->getId());
                }
            }
        }

        // Make sure the virtual camera's stream is stopped
        virtualCamera->stopVideoStream();

        // Remove a closed client.
        mActiveCameraClients.erase(it);

        return ScopedAStatus::ok();
    }
}

ScopedAStatus Enumerator::openCamera(const std::string& id, const Stream& cfg,
                                     std::shared_ptr<IEvsCamera>* cameraObj) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    // If hwCamera is null, a requested camera device is either a logical camera
    // device or a hardware camera, which is not being used now.
    std::unordered_set<std::string> physicalCameras = getPhysicalCameraIds(id);
    std::vector<std::shared_ptr<HalCamera>> sourceCameras;
    bool success = true;

    {
        std::lock_guard lock(mLock);
        // 1. Try to open inactive camera devices.
        for (auto&& id : physicalCameras) {
            auto it = mActiveCameras.find(id);
            if (it == mActiveCameras.end()) {
                std::shared_ptr<IEvsCamera> device;
                auto status = mHwEnumerator->openCamera(id, cfg, &device);
                if (!status.isOk()) {
                    LOG(ERROR) << "Failed to open hardware camera " << id
                               << ", error = " << status.getServiceSpecificError();
                    success = false;
                    break;
                }

                // Calculates the usage statistics record identifier
                auto fn = mCameraDevices.hash_function();
                auto recordId = fn(id) & 0xFF;
                std::shared_ptr<HalCamera> hwCamera =
                        ::ndk::SharedRefBase::make<HalCamera>(device, id, recordId, cfg);
                if (!hwCamera) {
                    LOG(ERROR) << "Failed to allocate camera wrapper object";
                    mHwEnumerator->closeCamera(device);
                    success = false;
                    break;
                }

                // Add the hardware camera to our list, which will keep it alive via ref count
                mActiveCameras.insert_or_assign(id, hwCamera);
                if (mMonitorEnabled) {
                    mClientsMonitor->registerClientToMonitor(hwCamera);
                }
                sourceCameras.push_back(std::move(hwCamera));
            } else {
                if (it->second->getStreamConfig().id != cfg.id) {
                    LOG(WARNING)
                            << "Requested camera is already active in different configuration.";
                } else {
                    sourceCameras.push_back(it->second);
                }
            }
        }

        if (!success || sourceCameras.size() < 1) {
            LOG(ERROR) << "Failed to open any physical camera device";
            return Utils::buildScopedAStatusFromEvsResult(EvsResult::UNDERLYING_SERVICE_ERROR);
        }

        // TODO(b/147170360): Implement a logic to handle a failure.
        // 3. Create a proxy camera object
        std::shared_ptr<VirtualCamera> clientCamera =
                ::ndk::SharedRefBase::make<VirtualCamera>(sourceCameras);
        if (!clientCamera) {
            // TODO(b/213108625): Any resource needs to be cleaned up explicitly?
            LOG(ERROR) << "Failed to create a client camera object";
            return Utils::buildScopedAStatusFromEvsResult(EvsResult::UNDERLYING_SERVICE_ERROR);
        }

        if (physicalCameras.size() > 1) {
            // VirtualCamera, which represents a logical device, caches its
            // descriptor.
            clientCamera->setDescriptor(&mCameraDevices[id]);
        }

        // 4. Owns created proxy camera object
        for (auto&& hwCamera : sourceCameras) {
            if (!hwCamera->ownVirtualCamera(clientCamera)) {
                // TODO(b/213108625): Remove a reference to this camera from a virtual camera
                // object.
                LOG(ERROR) << hwCamera->getId() << " failed to own a created proxy camera object.";
            }
        }

        // Record a newly created object.
        mActiveCameraClients.push_back(clientCamera);

        // Send the virtual camera object back to the client by strong pointer which will keep it
        // alive
        *cameraObj = std::move(clientCamera);
        return ScopedAStatus::ok();
    }
}

ScopedAStatus Enumerator::openDisplay(int32_t id, std::shared_ptr<IEvsDisplay>* displayObj) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    {
        std::lock_guard lock(mLock);
        if (mDisplayOwnedExclusively) {
            if (!mActiveDisplay.expired()) {
                LOG(ERROR) << "Display is owned exclusively by another client.";
                return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_BUSY);
            }

            mDisplayOwnedExclusively = false;
        }

        bool flagExclusive = false;
        if (id == kExclusiveDisplayId) {
            // The client requests to open the primary display exclusively.
            id = mInternalDisplayPort;
            flagExclusive = true;
            LOG(DEBUG) << "EvsDisplay is now owned exclusively by process "
                       << AIBinder_getCallingPid();
        } else if (id == kDisplayIdUnavailable || mDisplayPorts.empty()) {
            // If any display port is not available, it's possible that a
            // running EVS HAL service implements HIDL EVS v1.0 interfaces.
            id = mInternalDisplayPort;
            LOG(WARNING) << "No display port is listed; Does a running EVS HAL service implement "
                            "HIDL EVS v1.0 interfaces?";
        } else if (std::find(mDisplayPorts.begin(), mDisplayPorts.end(), id) ==
                   mDisplayPorts.end()) {
            // If we know any available display port, a given display ID must be
            // one of them.
            LOG(ERROR) << "No display is available on the port " << id;
            return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
        }

        // We simply keep track of the most recently opened display instance.
        // In the underlying layers we expect that a new open will cause the previous
        // object to be destroyed.  This avoids any race conditions associated with
        // create/destroy order and provides a cleaner restart sequence if the previous owner
        // is non-responsive for some reason.
        // Request exclusive access to the EVS display
        std::shared_ptr<IEvsDisplay> displayHandle;
        if (auto status = mHwEnumerator->openDisplay(id, &displayHandle);
            !status.isOk() || !displayHandle) {
            // We may fail to open the display in following cases:
            // 1) If a running EVS HAL service implements HIDL EVS interfaces,
            // AidlEnumerator validates a given display ID and return a null if
            // it's out of [0, 255].
            // 2) If a running EVS HAL service implements AIDL EVS interfaces,
            // EVS HAL service will return a null if no display is associated
            // with a given display ID.
            LOG(ERROR) << "EVS Display unavailable";
            return status;
        }

        // Remember (via weak pointer) who we think the most recently opened display is so that
        // we can proxy state requests from other callers to it.
        std::shared_ptr<IEvsDisplay> pHalDisplay =
                ::ndk::SharedRefBase::make<HalDisplay>(displayHandle, id);
        *displayObj = pHalDisplay;
        mActiveDisplay = pHalDisplay;
        mDisplayOwnedExclusively = flagExclusive;

        return ScopedAStatus::ok();
    }
}

ScopedAStatus Enumerator::closeDisplay(const std::shared_ptr<IEvsDisplay>& displayObj) {
    LOG(DEBUG) << __FUNCTION__;

    if (!displayObj) {
        LOG(WARNING) << "Ignoring a call with an invalid display object";
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::INVALID_ARG);
    }

    {
        std::lock_guard lock(mLock);
        // Drop the active display
        std::shared_ptr<IEvsDisplay> pActiveDisplay = mActiveDisplay.lock();
        if (pActiveDisplay != displayObj) {
            LOG(WARNING) << "Ignoring call to closeDisplay with unrecognized display object.";
            return ScopedAStatus::ok();
        }

        // Pass this request through to the hardware layer
        HalDisplay* halDisplay = reinterpret_cast<HalDisplay*>(pActiveDisplay.get());
        mHwEnumerator->closeDisplay(halDisplay->getHwDisplay());
        mActiveDisplay.reset();
        mDisplayOwnedExclusively = false;

        return ScopedAStatus::ok();
    }
}

ScopedAStatus Enumerator::getDisplayState(DisplayState* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    {
        std::lock_guard lock(mLock);
        // Do we have a display object we think should be active?
        std::shared_ptr<IEvsDisplay> pActiveDisplay = mActiveDisplay.lock();
        if (pActiveDisplay) {
            // Pass this request through to the hardware layer
            return pActiveDisplay->getDisplayState(_aidl_return);
        } else {
            // We don't have a live display right now
            mActiveDisplay.reset();
            return Utils::buildScopedAStatusFromEvsResult(EvsResult::RESOURCE_NOT_AVAILABLE);
        }
    }
}

ScopedAStatus Enumerator::getDisplayStateById(int32_t displayId, DisplayState* _aidl_return) {
    LOG(DEBUG) << __FUNCTION__;
    if (!checkPermission()) {
        return Utils::buildScopedAStatusFromEvsResult(EvsResult::PERMISSION_DENIED);
    }

    return mHwEnumerator->getDisplayStateById(displayId, _aidl_return);
}

ScopedAStatus Enumerator::getDisplayIdList(std::vector<uint8_t>* _aidl_return) {
    std::shared_lock lock(mLock);
    return mHwEnumerator->getDisplayIdList(_aidl_return);
}

ScopedAStatus Enumerator::registerStatusCallback(
        const std::shared_ptr<IEvsEnumeratorStatusCallback>& callback) {
    std::lock_guard lock(mLock);
    mDeviceStatusCallbacks.insert(callback);
    return ScopedAStatus::ok();
}

ScopedAStatus Enumerator::getUltrasonicsArrayList(
        [[maybe_unused]] std::vector<UltrasonicsArrayDesc>* list) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

ScopedAStatus Enumerator::openUltrasonicsArray(
        [[maybe_unused]] const std::string& id,
        [[maybe_unused]] std::shared_ptr<IEvsUltrasonicsArray>* obj) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

ScopedAStatus Enumerator::closeUltrasonicsArray(
        [[maybe_unused]] const std::shared_ptr<IEvsUltrasonicsArray>& obj) {
    // TODO(b/149874793): Add implementation for EVS Manager and Sample driver
    return Utils::buildScopedAStatusFromEvsResult(EvsResult::NOT_IMPLEMENTED);
}

binder_status_t Enumerator::dump(int fd, const char** args, uint32_t numArgs) {
    if (fd < 0) {
        LOG(ERROR) << "Given file descriptor is not valid.";
        return STATUS_BAD_VALUE;
    }

    cmdDump(fd, args, numArgs);
    return STATUS_OK;
}

void Enumerator::cmdDump(int fd, const char** args, uint32_t numArgs) {
    if (numArgs < 1) {
        WriteStringToFd("No option is given.\n", fd);
        cmdHelp(fd);
        return;
    }

    const std::string option = args[0];
    if (EqualsIgnoreCase(option, "--help")) {
        cmdHelp(fd);
    } else if (EqualsIgnoreCase(option, "--list")) {
        cmdList(fd, args, numArgs);
    } else if (EqualsIgnoreCase(option, "--dump")) {
        cmdDumpDevice(fd, args, numArgs);
    } else {
        WriteStringToFd(StringPrintf("Invalid option: %s\n", option.data()), fd);
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
                    "--dump display: shows current status of the display\n",
                    fd);
}

void Enumerator::cmdList(int fd, const char** args, uint32_t numArgs) {
    bool listCameras = false;
    bool listDisplays = false;
    if (numArgs > 1) {
        const std::string option = args[1];
        const bool listAll = EqualsIgnoreCase(option, kDumpOptionAll);
        listCameras = listAll || EqualsIgnoreCase(option, kDumpDeviceCamera);
        listDisplays = listAll || EqualsIgnoreCase(option, kDumpDeviceDisplay);
        if (!listCameras && !listDisplays) {
            WriteStringToFd(StringPrintf("Unrecognized option, %s, is ignored.\n", option.data()),
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
            std::vector<CameraDesc> temp;
            (void)getCameraList(&temp);
        }

        for (auto& [id, desc] : mCameraDevices) {
            StringAppendF(&buffer, "%s%s\n", kSingleIndent, id.data());
        }

        StringAppendF(&buffer, "%sCamera devices currently in use:\n", kSingleIndent);
        for (auto& [id, ptr] : mActiveCameras) {
            StringAppendF(&buffer, "%s%s\n", kSingleIndent, id.data());
        }
        StringAppendF(&buffer, "\n");
    }

    if (listDisplays) {
        if (mHwEnumerator != nullptr) {
            StringAppendF(&buffer, "Display devices available to EVS service:\n");
            // Get an internal display identifier.
            if (mDisplayPorts.size() < 1) {
                (void)mHwEnumerator->getDisplayIdList(&mDisplayPorts);
            }

            for (auto&& port : mDisplayPorts) {
                StringAppendF(&buffer, "%sdisplay port %u\n", kSingleIndent,
                              static_cast<unsigned>(port));
            }
        } else {
            LOG(WARNING) << "EVS HAL implementation is not available.";
        }
    }

    WriteStringToFd(buffer, fd);
}

void Enumerator::cmdDumpDevice(int fd, const char** args, uint32_t numArgs) {
    // Dumps both cameras and displays if the target device type is not given
    bool dumpCameras = false;
    bool dumpDisplays = false;
    if (numArgs > kOptionDumpDeviceTypeIndex) {
        const std::string target = args[kOptionDumpDeviceTypeIndex];
        dumpCameras = EqualsIgnoreCase(target, kDumpDeviceCamera);
        dumpDisplays = EqualsIgnoreCase(target, kDumpDeviceDisplay);
        if (!dumpCameras && !dumpDisplays) {
            WriteStringToFd(StringPrintf("Unrecognized option, %s, is ignored.\n", target.data()),
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
        if (numArgs < kDumpCameraMinNumArgs) {
            WriteStringToFd(StringPrintf("Necessary arguments are missing.  "
                                         "Please check the usages:\n"),
                            fd);
            cmdHelp(fd);
            return;
        }

        const std::string deviceId = args[kOptionDumpCameraTypeIndex];
        auto target = mActiveCameras.find(deviceId);
        const bool dumpAllCameras = EqualsIgnoreCase(deviceId, kDumpOptionAll);
        if (!dumpAllCameras && target == mActiveCameras.end()) {
            // Unknown camera identifier
            WriteStringToFd(StringPrintf("Given camera ID %s is unknown or not active.\n",
                                         deviceId.data()),
                            fd);
            return;
        }

        const std::string command = args[kOptionDumpCameraCommandIndex];
        std::string cameraInfo;
        if (EqualsIgnoreCase(command, kDumpCameraCommandCurrent)) {
            // Active stream configuration from each active HalCamera objects
            if (!dumpAllCameras) {
                StringAppendF(&cameraInfo, "HalCamera: %s\n%s", deviceId.data(),
                              target->second->toString(kSingleIndent).data());
            } else {
                for (auto&& [_, handle] : mActiveCameras) {
                    // Appends the current status
                    cameraInfo += handle->toString(kSingleIndent);
                }
            }
        } else if (EqualsIgnoreCase(command, kDumpCameraCommandCollected)) {
            // Reads the usage statistics from active HalCamera objects
            std::unordered_map<std::string, std::string> usageStrings;
            if (mMonitorEnabled) {
                auto result = mClientsMonitor->toString(&usageStrings, kSingleIndent);
                if (!result.ok()) {
                    LOG(ERROR) << "Failed to get the monitoring result";
                    return;
                }

                if (!dumpAllCameras) {
                    cameraInfo += usageStrings[deviceId];
                } else {
                    for (auto&& [_, stats] : usageStrings) {
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
            if (numArgs < kDumpCameraMinNumArgs + 1) {
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

            const std::string subcommand = args[kOptionDumpCameraArgsStartIndex];
            if (EqualsIgnoreCase(subcommand, kDumpCameraCommandCustomStart)) {
                using ::std::chrono::duration_cast;
                using ::std::chrono::milliseconds;
                using ::std::chrono::nanoseconds;
                nanoseconds interval = 0ns;
                nanoseconds duration = 0ns;
                if (numArgs > kOptionDumpCameraArgsStartIndex + 2) {
                    duration = duration_cast<nanoseconds>(
                            milliseconds(std::stoi(args[kOptionDumpCameraArgsStartIndex + 2])));
                }

                if (numArgs > kOptionDumpCameraArgsStartIndex + 1) {
                    interval = duration_cast<nanoseconds>(
                            milliseconds(std::stoi(args[kOptionDumpCameraArgsStartIndex + 1])));
                }

                // Starts a custom collection
                auto result = mClientsMonitor->startCustomCollection(interval, duration);
                if (!result.ok()) {
                    LOG(ERROR) << "Failed to start a custom collection.  " << result.error();
                    StringAppendF(&cameraInfo, "Failed to start a custom collection. %s\n",
                                  result.error().message().data());
                }
            } else if (EqualsIgnoreCase(subcommand, kDumpCameraCommandCustomStop)) {
                if (!mMonitorEnabled) {
                    WriteStringToFd(StringPrintf("Client monitor is not available."), fd);
                    return;
                }

                auto result = mClientsMonitor->stopCustomCollection(deviceId);
                if (!result.ok()) {
                    LOG(ERROR) << "Failed to stop a custom collection.  " << result.error();
                    StringAppendF(&cameraInfo, "Failed to stop a custom collection. %s\n",
                                  result.error().message().data());
                } else {
                    // Pull the custom collection
                    cameraInfo += *result;
                }
            } else {
                WriteStringToFd(StringPrintf("Unknown argument: %s\n", subcommand.data()), fd);
                cmdHelp(fd);
                return;
            }
        } else {
            WriteStringToFd(StringPrintf("Unknown command: %s\n"
                                         "Please check the usages:\n",
                                         command.data()),
                            fd);
            cmdHelp(fd);
            return;
        }

        // Outputs the report
        WriteStringToFd(cameraInfo, fd);
    }

    if (dumpDisplays) {
        HalDisplay* pDisplay = reinterpret_cast<HalDisplay*>(mActiveDisplay.lock().get());
        if (pDisplay == nullptr) {
            WriteStringToFd("No active display is found.\n", fd);
        } else {
            WriteStringToFd(pDisplay->toString(kSingleIndent), fd);
        }
    }
}

void Enumerator::broadcastDeviceStatusChange(const std::vector<aidlevs::DeviceStatus>& list) {
    std::lock_guard lock(mLock);
    auto it = mDeviceStatusCallbacks.begin();
    while (it != mDeviceStatusCallbacks.end()) {
        if (!(*it)->deviceStatusChanged(list).isOk()) {
            mDeviceStatusCallbacks.erase(it);
        } else {
            ++it;
        }
    }
}

ScopedAStatus Enumerator::EvsDeviceStatusCallbackImpl::deviceStatusChanged(
        const std::vector<aidlevs::DeviceStatus>& list) {
    mEnumerator->broadcastDeviceStatusChange(list);
    return ScopedAStatus::ok();
}

bool Enumerator::init(std::shared_ptr<IEvsEnumerator>& hwEnumerator, bool enableMonitor) {
    LOG(DEBUG) << __FUNCTION__;

    // Register a device status callback
    mDeviceStatusCallback =
            ::ndk::SharedRefBase::make<EvsDeviceStatusCallbackImpl>(ref<Enumerator>());
    if (!hwEnumerator->registerStatusCallback(mDeviceStatusCallback).isOk()) {
        LOG(WARNING) << "Failed to register a device status callback";
    }

    // Get a list of available displays and identify the internal display
    if (!hwEnumerator->getDisplayIdList(&mDisplayPorts).isOk()) {
        LOG(WARNING)
                << "Failed to get a list of available displays. EVS Display may not work properly "
                   "if an active EVS HAL service implements HIDL v1.1 or AIDL EVS interface.";
    }

    const size_t numDisplays = mDisplayPorts.size();
    mDisplayPorts.erase(std::remove_if(mDisplayPorts.begin(), mDisplayPorts.end(),
                                       [](const auto id) { return id == kExclusiveDisplayId; }),
                        mDisplayPorts.end());
    if (numDisplays != mDisplayPorts.size()) {
        LOG(WARNING)
                << kExclusiveDisplayId
                << " is reserved for the special purpose so will not be available for EVS service.";
    }

    // The first element is the internal display if a returned list is not
    // empty.
    mInternalDisplayPort = mDisplayPorts.empty() ? kDisplayIdUnavailable : mDisplayPorts.front();
    mDisplayOwnedExclusively = false;
    mHwEnumerator = hwEnumerator;

    // Starts the statistics collection
    mMonitorEnabled = false;
    if (!enableMonitor) {
        return true;
    }

    mClientsMonitor = new (std::nothrow) StatsCollector();
    if (mClientsMonitor) {
        if (auto result = mClientsMonitor->startCollection(); !result.ok()) {
            LOG(ERROR) << "Failed to start the usage monitor: " << result.error();
        } else {
            mMonitorEnabled = true;
        }
    }

    return true;
}

void Enumerator::enablePermissionCheck(bool enable) {
    mDisablePermissionCheck = !enable;
}

}  // namespace aidl::android::automotive::evs::implementation

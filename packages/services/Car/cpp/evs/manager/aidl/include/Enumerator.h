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

#ifndef CPP_EVS_MANAGER_AIDL_INCLUDE_ENUMERATOR_H
#define CPP_EVS_MANAGER_AIDL_INCLUDE_ENUMERATOR_H

#include "HalCamera.h"
#include "VirtualCamera.h"
#include "stats/include/StatsCollector.h"

#include <aidl/android/hardware/automotive/evs/BnEvsEnumerator.h>
#include <aidl/android/hardware/automotive/evs/BnEvsEnumeratorStatusCallback.h>
#include <aidl/android/hardware/automotive/evs/IEvsDisplay.h>
#include <system/camera_metadata.h>

#include <list>
#include <shared_mutex>
#include <unordered_map>
#include <unordered_set>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

class Enumerator final : public ::aidl::android::hardware::automotive::evs::BnEvsEnumerator {
public:
    // Methods from ::aidl::android::hardware::automotive::evs::IEvsEnumerator
    ::ndk::ScopedAStatus isHardware(bool* flag) override;
    ::ndk::ScopedAStatus openCamera(const std::string& cameraId,
                                    const aidlevs::Stream& streamConfig,
                                    std::shared_ptr<aidlevs::IEvsCamera>* obj) override;
    ::ndk::ScopedAStatus closeCamera(const std::shared_ptr<aidlevs::IEvsCamera>& obj) override;
    ::ndk::ScopedAStatus getCameraList(std::vector<aidlevs::CameraDesc>* _aidl_return) override;
    ::ndk::ScopedAStatus getStreamList(const aidlevs::CameraDesc& desc,
                                       std::vector<aidlevs::Stream>* _aidl_return) override;
    ::ndk::ScopedAStatus openDisplay(int32_t displayId,
                                     std::shared_ptr<aidlevs::IEvsDisplay>* obj) override;
    ::ndk::ScopedAStatus closeDisplay(const std::shared_ptr<aidlevs::IEvsDisplay>& obj) override;
    ::ndk::ScopedAStatus getDisplayIdList(std::vector<uint8_t>* list) override;
    ::ndk::ScopedAStatus getDisplayState(aidlevs::DisplayState* state) override;
    ::ndk::ScopedAStatus getDisplayStateById(int32_t displayId,
                                             aidlevs::DisplayState* state) override;
    ::ndk::ScopedAStatus registerStatusCallback(
            const std::shared_ptr<aidlevs::IEvsEnumeratorStatusCallback>& callback) override;
    ::ndk::ScopedAStatus openUltrasonicsArray(
            const std::string& id, std::shared_ptr<aidlevs::IEvsUltrasonicsArray>* obj) override;
    ::ndk::ScopedAStatus closeUltrasonicsArray(
            const std::shared_ptr<aidlevs::IEvsUltrasonicsArray>& obj) override;
    ::ndk::ScopedAStatus getUltrasonicsArrayList(
            std::vector<aidlevs::UltrasonicsArrayDesc>* list) override;

    // Method from ::ndk::ICInterface
    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override;

    // Implementation details
    bool init(const std::string_view& hardwareServiceName);
    void broadcastDeviceStatusChange(const std::vector<aidlevs::DeviceStatus>& list);

    // Destructor
    virtual ~Enumerator();

    // TODO(b/235110887): We may eventually want to remove below two methods and
    //                    replace their functionality with other methods more
    //                    elegant.
    bool init(std::shared_ptr<aidlevs::IEvsEnumerator>& hwEnumerator, bool enableMonitor = false);
    void enablePermissionCheck(bool enable);

private:
    class EvsDeviceStatusCallbackImpl : public aidlevs::BnEvsEnumeratorStatusCallback {
    public:
        EvsDeviceStatusCallbackImpl(const std::shared_ptr<Enumerator>& enumerator) :
              mEnumerator(enumerator) {}
        ::ndk::ScopedAStatus deviceStatusChanged(
                const std::vector<aidlevs::DeviceStatus>& status) override;

    private:
        std::shared_ptr<Enumerator> mEnumerator;
    };

    bool checkPermission() const;
    bool isLogicalCamera(const camera_metadata_t* metadata) const;
    std::unordered_set<std::string> getPhysicalCameraIds(const std::string& id);
    std::shared_ptr<aidlevs::IEvsEnumerator> connectToAidlHal(
            const std::string_view& hardwareServiceName, bool blocking);
    std::shared_ptr<aidlevs::IEvsEnumerator> connectToHidlHal(
            const std::string_view& hardwareServiceName);

    void cmdDump(int fd, const char** args, uint32_t numArgs);
    void cmdHelp(int fd);
    void cmdList(int fd, const char** args, uint32_t numArgs);
    void cmdDumpDevice(int fd, const char** args, uint32_t numArgs);

    // Hardware enumerator
    std::shared_ptr<aidlevs::IEvsEnumerator> mHwEnumerator;

    // Display proxy object warpping hw display
    std::weak_ptr<aidlevs::IEvsDisplay> mActiveDisplay;

    // List of active camera proxy objects that wrap hw cameras
    std::unordered_map<std::string, std::shared_ptr<HalCamera>> mActiveCameras;

    // List of camera descriptors of enumerated hw cameras
    std::unordered_map<std::string, aidlevs::CameraDesc> mCameraDevices;

    // List of available physical display devices
    std::vector<uint8_t> mDisplayPorts;

    // Display port the internal display is connected to.
    int32_t mInternalDisplayPort;

    // Collecting camera usage statistics from clients
    ::android::sp<StatsCollector> mClientsMonitor;

    // Boolean flag to tell whether the camera usages are being monitored or not
    bool mMonitorEnabled;

    // Boolean flag to tell whether EvsDisplay is owned exclusively or not
    bool mDisplayOwnedExclusively;

    // Callback to listen to device status changes
    std::shared_ptr<EvsDeviceStatusCallbackImpl> mDeviceStatusCallback;

    // Mutex to protect resources related with a device status callback
    mutable std::shared_mutex mLock;

    // Clients to forward device status callback messages
    std::set<std::shared_ptr<aidlevs::IEvsEnumeratorStatusCallback>> mDeviceStatusCallbacks;

    bool mDisablePermissionCheck = false;

    std::list<std::weak_ptr<VirtualCamera>> mActiveCameraClients;
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_INCLUDE_ENUMERATOR_H

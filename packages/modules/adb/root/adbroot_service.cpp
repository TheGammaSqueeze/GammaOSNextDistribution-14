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

#include <android/binder_manager.h>
#include <android/content/pm/IPackageManagerNative.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/strings.h>
#include <binder/IBinder.h>
#include <binder/IServiceManager.h>
#include <cutils/multiuser.h>
#include <private/android_filesystem_config.h>

#include "adbroot_service.h"

namespace {
const std::string kStoragePath = "/data/adbroot/";
const std::string kEnabled = "enabled";

static ndk::ScopedAStatus SecurityException(const std::string& msg) {
    LOG(ERROR) << msg;
    return ndk::ScopedAStatus(AStatus_fromExceptionCodeWithMessage(EX_SECURITY, msg.c_str()));
}
}  // anonymous namespace

namespace android {
static bool isAutomotive() {
    sp<IServiceManager> serviceManager = defaultServiceManager();
    if (serviceManager.get() == nullptr) {
        LOG(ERROR) << "Unable to access native ServiceManager";
        return false;
    }

    sp<content::pm::IPackageManagerNative> packageManager;
    sp<IBinder> binder = serviceManager->waitForService(String16("package_native"));
    packageManager = interface_cast<content::pm::IPackageManagerNative>(binder);
    if (packageManager == nullptr) {
        LOG(ERROR) << "Unable to access native PackageManager";
        return false;
    }

    bool isAutomotive = false;
    binder::Status status =
        packageManager->hasSystemFeature(String16("android.hardware.type.automotive"), 0,
                                         &isAutomotive);
    if (!status.isOk()) {
        LOG(ERROR) << "Calling hasSystemFeature failed: " << status.exceptionMessage().c_str();
        return false;
    }

    return isAutomotive;
}
}  // namespace android

namespace aidl {
namespace android {
namespace adbroot {

using ::android::AutoMutex;
using ::android::base::ReadFileToString;
using ::android::base::SetProperty;
using ::android::base::Trim;
using ::android::base::WriteStringToFile;

static inline bool isAutomotive(uid_t uid) {
    appid_t appid = multiuser_get_app_id(uid);

    return appid == AID_SYSTEM && ::android::isAutomotive();
}

ADBRootService::ADBRootService() : enabled_(false) {
    std::string buf;
    if (ReadFileToString(kStoragePath + kEnabled, &buf)) {
        enabled_ = Trim(buf) == "1";
    }
}

void ADBRootService::Register() {
    auto service = ndk::SharedRefBase::make<ADBRootService>();
    binder_status_t status = AServiceManager_addService(
            service->asBinder().get(), getServiceName());

    if (status != STATUS_OK) {
        LOG(FATAL) << "Could not register adbroot service: " << status;
    }
}

ndk::ScopedAStatus ADBRootService::isSupported(bool* _aidl_return) {
    uid_t uid = AIBinder_getCallingUid();
    if (uid != AID_SYSTEM && uid != AID_SHELL && !isAutomotive(uid)) {
        return SecurityException("Caller must be system or shell");
    }

    AutoMutex _l(lock_);
    *_aidl_return = __android_log_is_debuggable();
    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ADBRootService::setEnabled(bool enabled) {
    uid_t uid = AIBinder_getCallingUid();
    if (uid != AID_SYSTEM && !isAutomotive(uid)) {
        return SecurityException("Caller must be system");
    }

    AutoMutex _l(lock_);

    if (enabled_ != enabled) {
        enabled_ = enabled;
        WriteStringToFile(std::to_string(enabled), kStoragePath + kEnabled);

        // Turning off adb root, restart adbd.
        if (!enabled) {
            SetProperty("service.adb.root", "0");
            SetProperty("ctl.restart", "adbd");
        }
    }

    return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus ADBRootService::getEnabled(bool* _aidl_return) {
    uid_t uid = AIBinder_getCallingUid();
    if (uid != AID_SYSTEM && uid != AID_SHELL && !isAutomotive(uid)) {
        return SecurityException("Caller must be system or shell");
    }

    AutoMutex _l(lock_);
    *_aidl_return = enabled_;
    return ndk::ScopedAStatus::ok();
}

}  // namespace adbroot
}  // namespace android
}  // namespace aidl

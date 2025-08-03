/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/******************************************************************************
 **
 ** The original Work has been changed by NXP.
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2020-2023 NXP
 **
 *********************************************************************************/
#define LOG_TAG "javacard.strongbox-service"

#include <aidl/android/hardware/security/keymint/SecurityLevel.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>

#include "JavacardKeyMintDevice.h"
#include "JavacardRemotelyProvisionedComponentDevice.h"
#include "JavacardSecureElement.h"
#include "JavacardSharedSecret.h"
#if defined OMAPI_TRANSPORT
#include <OmapiTransport.h>
#elif defined HAL_TO_HAL_TRANSPORT
#include <HalToHalTransport.h>
#else
#include <SocketTransport.h>
#endif
#include "keymint_utils.h"

using aidl::android::hardware::security::keymint::JavacardKeyMintDevice;
using aidl::android::hardware::security::keymint::JavacardRemotelyProvisionedComponentDevice;
using aidl::android::hardware::security::keymint::SecurityLevel;
using aidl::android::hardware::security::sharedsecret::JavacardSharedSecret;
using keymint::javacard::getOsPatchlevel;
using keymint::javacard::getOsVersion;
using keymint::javacard::getVendorPatchlevel;
using keymint::javacard::ITransport;
using keymint::javacard::JavacardSecureElement;
#if defined OMAPI_TRANSPORT
using keymint::javacard::OmapiTransport;
#elif defined HAL_TO_HAL_TRANSPORT
#else
using keymint::javacard::SocketTransport;
#endif

const std::vector<uint8_t> gStrongBoxAppletAID = {0xA0, 0x00, 0x00, 0x00, 0x62};

template <typename T, class... Args> std::shared_ptr<T> addService(Args&&... args) {
    std::shared_ptr<T> ser = ndk::SharedRefBase::make<T>(std::forward<Args>(args)...);
    auto instanceName = std::string(T::descriptor) + "/strongbox";
    LOG(INFO) << "adding javacard strongbox service instance: " << instanceName;
    binder_status_t status =
        AServiceManager_addService(ser->asBinder().get(), instanceName.c_str());
    CHECK(status == STATUS_OK);
    return ser;
}

int main() {
    LOG(INFO) << "Starting javacard strongbox service";
    ABinderProcess_setThreadPoolMaxThreadCount(0);
    // Javacard Secure Element
#if defined OMAPI_TRANSPORT
    std::shared_ptr<JavacardSecureElement> card =
        std::make_shared<JavacardSecureElement>(
            std::make_shared<OmapiTransport>(gStrongBoxAppletAID));
#elif defined HAL_TO_HAL_TRANSPORT
    std::shared_ptr<JavacardSecureElement> card =
        std::make_shared<JavacardSecureElement>(
            std::make_shared<HalToHalTransport>(gStrongBoxAppletAID));
#else
    std::shared_ptr<JavacardSecureElement> card =
        std::make_shared<JavacardSecureElement>(
            std::make_shared<SocketTransport>(gStrongBoxAppletAID));
#endif
    // Add Keymint Service
    addService<JavacardKeyMintDevice>(card);
    // Add Shared Secret Service
    addService<JavacardSharedSecret>(card);
    // Add Remotely Provisioned Component Service
    addService<JavacardRemotelyProvisionedComponentDevice>(card);

    LOG(INFO) << "Joining thread pool";
    ABinderProcess_joinThreadPool();
    return EXIT_FAILURE;  // should not reach
}

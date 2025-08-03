/*
 * Copyright (c) 2019, The Android Open Source Project
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

#define LOG_TAG "rkpd_client"

#include <atomic>

#include <android-base/logging.h>
#include <android/security/rkp/BnGetKeyCallback.h>
#include <android/security/rkp/BnGetRegistrationCallback.h>
#include <android/security/rkp/IGetKeyCallback.h>
#include <android/security/rkp/IRemoteProvisioning.h>
#include <binder/IServiceManager.h>
#include <binder/Status.h>
#include <rkp/support/rkpd_client.h>

namespace android::security::rkp::support {
namespace {

using ::android::binder::Status;
using ::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using ::android::hardware::security::keymint::RpcHardwareInfo;
using ::android::security::rkp::BnGetKeyCallback;
using ::android::security::rkp::BnGetRegistrationCallback;
using ::android::security::rkp::IGetKeyCallback;
using ::android::security::rkp::IRegistration;
using ::android::security::rkp::IRemoteProvisioning;
using ::android::security::rkp::RemotelyProvisionedKey;

constexpr const char* kRemoteProvisioningServiceName = "remote_provisioning";

std::optional<std::string> getRpcId(const sp<IRemotelyProvisionedComponent>& rpc) {
    RpcHardwareInfo rpcHwInfo;
    Status status = rpc->getHardwareInfo(&rpcHwInfo);
    if (!status.isOk()) {
        LOG(ERROR) << "Error getting remotely provisioned component hardware info: " << status;
        return std::nullopt;
    }

    if (!rpcHwInfo.uniqueId) {
        LOG(ERROR) << "Remotely provisioned component is missing a unique id. "
                   << "This is a bug in the vendor implementation.";
        return std::nullopt;
    }

    return *rpcHwInfo.uniqueId;
}

std::optional<String16> findRpcNameById(std::string_view targetRpcId) {
    auto instances = android::defaultServiceManager()->getDeclaredInstances(
        IRemotelyProvisionedComponent::descriptor);
    for (const auto& instance : instances) {
        auto rpcName = IRemotelyProvisionedComponent::descriptor + String16("/") + instance;
        sp<IRemotelyProvisionedComponent> rpc =
            android::waitForService<IRemotelyProvisionedComponent>(rpcName);

        auto rpcId = getRpcId(rpc);
        if (!rpcId) {
            continue;
        }
        if (*rpcId == targetRpcId) {
            return rpcName;
        }
    }

    LOG(ERROR) << "Remotely provisioned component with given unique ID: " << targetRpcId
               << " not found";
    return std::nullopt;
}

std::optional<String16> getRpcName(const sp<IRemotelyProvisionedComponent>& rpc) {
    std::optional<std::string> targetRpcId = getRpcId(rpc);
    if (!targetRpcId) {
        return std::nullopt;
    }
    return findRpcNameById(*targetRpcId);
}

class GetKeyCallback : public BnGetKeyCallback {
  public:
    GetKeyCallback(std::promise<std::optional<RemotelyProvisionedKey>> keyPromise)
        : keyPromise_(std::move(keyPromise)), called_() {}

    Status onSuccess(const RemotelyProvisionedKey& key) override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        keyPromise_.set_value(key);
        return Status::ok();
    }
    Status onCancel() override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        LOG(ERROR) << "GetKeyCallback cancelled";
        keyPromise_.set_value(std::nullopt);
        return Status::ok();
    }
    Status onError(IGetKeyCallback::ErrorCode error, const String16& description) override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        LOG(ERROR) << "GetKeyCallback failed: " << static_cast<int>(error) << ", " << description;
        keyPromise_.set_value(std::nullopt);
        return Status::ok();
    }

  private:
    std::promise<std::optional<RemotelyProvisionedKey>> keyPromise_;
    // This callback can only be called into once
    std::atomic_flag called_;
};

class GetRegistrationCallback : public BnGetRegistrationCallback {
  public:
    GetRegistrationCallback(std::promise<std::optional<RemotelyProvisionedKey>> keyPromise,
                            uint32_t keyId)
        : keyPromise_(std::move(keyPromise)), keyId_(keyId), called_() {}

    Status onSuccess(const sp<IRegistration>& registration) override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        auto cb = sp<GetKeyCallback>::make(std::move(keyPromise_));
        auto status = registration->getKey(keyId_, cb);
        if (!status.isOk()) {
            cb->onError(IGetKeyCallback::ErrorCode::ERROR_UNKNOWN,
                        String16("Failed to register GetKeyCallback"));
        }
        return Status::ok();
    }
    Status onCancel() override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        LOG(ERROR) << "GetRegistrationCallback cancelled";
        keyPromise_.set_value(std::nullopt);
        return Status::ok();
    }
    Status onError(const String16& error) override {
        if (called_.test_and_set()) {
            return Status::ok();
        }
        LOG(ERROR) << "GetRegistrationCallback failed: " << error;
        keyPromise_.set_value(std::nullopt);
        return Status::ok();
    }

  private:
    std::promise<std::optional<RemotelyProvisionedKey>> keyPromise_;
    int32_t keyId_;
    // This callback can only be called into once
    std::atomic_flag called_;
};

}  // namespace

std::optional<std::future<std::optional<RemotelyProvisionedKey>>>
getRpcKeyFuture(const sp<IRemotelyProvisionedComponent>& rpc, int32_t keyId) {
    std::promise<std::optional<RemotelyProvisionedKey>> keyPromise;
    auto keyFuture = keyPromise.get_future();

    auto rpcName = getRpcName(rpc);
    if (!rpcName) {
        LOG(ERROR) << "Failed to get IRemotelyProvisionedComponent name";
        return std::nullopt;
    }

    sp<IRemoteProvisioning> remoteProvisioning =
        android::waitForService<IRemoteProvisioning>(String16(kRemoteProvisioningServiceName));
    if (!remoteProvisioning) {
        LOG(ERROR) << "Failed to get IRemoteProvisioning HAL";
        return std::nullopt;
    }

    auto cb = sp<GetRegistrationCallback>::make(std::move(keyPromise), keyId);
    Status status = remoteProvisioning->getRegistration(*rpcName, cb);
    if (!status.isOk()) {
        LOG(ERROR) << "Failed getRegistration()";
        return std::nullopt;
    }

    return keyFuture;
}

std::optional<RemotelyProvisionedKey> getRpcKey(const sp<IRemotelyProvisionedComponent>& rpc,
                                                int32_t keyId, int32_t timeout_sec) {
    auto rpcKeyFuture = getRpcKeyFuture(rpc, keyId);
    if (!rpcKeyFuture) {
        LOG(ERROR) << "Failed getRpcKeyFuture()";
        return std::nullopt;
    }

    auto timeout = std::chrono::seconds(timeout_sec);
    if (rpcKeyFuture->wait_for(timeout) != std::future_status::ready) {
        LOG(ERROR) << "Waiting for remotely provisioned attestation key timed out";
        return std::nullopt;
    }

    return rpcKeyFuture->get();
}

}  // namespace android::security::rkp::support

/**
 * Copyright (c) 2020, The Android Open Source Project
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

#define LOG_TAG "carpowerpolicyd"
#define DEBUG false  // STOPSHIP if true.

#include "CarPowerPolicyServer.h"

#include <aidl/android/hardware/automotive/vehicle/StatusCode.h>
#include <aidl/android/hardware/automotive/vehicle/SubscribeOptions.h>
#include <aidl/android/hardware/automotive/vehicle/VehicleApPowerStateReport.h>
#include <aidl/android/hardware/automotive/vehicle/VehicleProperty.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <android/binder_status.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>
#include <private/android_filesystem_config.h>
#include <utils/String8.h>
#include <utils/SystemClock.h>
#include <utils/Timers.h>

#include <android_car_feature.h>
#include <inttypes.h>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

using ::aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegate;
using ::aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegateCallback;
using ::aidl::android::automotive::powerpolicy::internal::PowerPolicyFailureReason;
using ::aidl::android::automotive::powerpolicy::internal::PowerPolicyInitData;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy;
using ::aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter;
using ::aidl::android::frameworks::automotive::powerpolicy::ICarPowerPolicyChangeCallback;
using ::aidl::android::frameworks::automotive::powerpolicy::PowerComponent;
using ::aidl::android::frameworks::automotive::powerpolicy::internal::PolicyState;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehicleApPowerStateReport;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;

using ::android::defaultServiceManager;
using ::android::IBinder;
using ::android::Looper;
using ::android::Mutex;
using ::android::status_t;
using ::android::String16;
using ::android::uptimeMillis;
using ::android::Vector;
using ::android::wp;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::WriteStringToFd;
using ::android::car::feature::car_power_policy_refactoring;
using ::android::frameworks::automotive::vhal::HalPropError;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::ISubscriptionClient;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::frameworks::automotive::vhal::VhalClientResult;

using ::android::hardware::hidl_vec;
using ::android::hardware::interfacesEqual;
using ::android::hardware::Return;

using ::android::hidl::base::V1_0::IBase;
using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::ndk::SpAIBinder;

namespace {

const int32_t MSG_CONNECT_TO_VHAL = 1;  // Message to request of connecting to VHAL.

const nsecs_t kConnectionRetryIntervalNs = 200000000;  // 200 milliseconds.
const int32_t kMaxConnectionRetry = 25;                // Retry up to 5 seconds.

constexpr const char kCarServiceInterface[] = "car_service";
constexpr const char kCarPowerPolicyServerInterface[] =
        "android.frameworks.automotive.powerpolicy.ICarPowerPolicyServer/default";
constexpr const char kCarPowerPolicySystemNotificationInterface[] =
        "android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification/"
        "default";
constexpr const char kCarPowerPolicyDelegateInterface[] =
        "android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate/default";

std::vector<CallbackInfo>::const_iterator lookupPowerPolicyChangeCallback(
        const std::vector<CallbackInfo>& callbacks, const AIBinder* binder) {
    for (auto it = callbacks.begin(); it != callbacks.end(); it++) {
        if (it->binder.get() == binder) {
            return it;
        }
    }
    return callbacks.end();
}

ScopedAStatus checkSystemPermission() {
    if (IPCThreadState::self()->getCallingUid() != AID_SYSTEM) {
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_SECURITY,
                                                                  "Calling process does not have "
                                                                  "proper privilege");
    }
    return ScopedAStatus::ok();
}

PowerPolicyFailureReason convertErrorToFailureReason(int errorCode) {
    switch (errorCode) {
        case EX_ILLEGAL_ARGUMENT:
            return PowerPolicyFailureReason::POWER_POLICY_FAILURE_NOT_REGISTERED_ID;
        default:
            return PowerPolicyFailureReason::POWER_POLICY_FAILURE_UNKNOWN;
    }
}

}  // namespace

std::shared_ptr<CarPowerPolicyServer> CarPowerPolicyServer::sCarPowerPolicyServer = nullptr;

PropertyChangeListener::PropertyChangeListener(CarPowerPolicyServer* service) : mService(service) {}

void PropertyChangeListener::onPropertyEvent(
        const std::vector<std::unique_ptr<IHalPropValue>>& values) {
    for (const auto& value : values) {
        const std::string stringValue = value->getStringValue();
        int32_t propId = value->getPropId();
        if (propId == static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ)) {
            const auto& ret = mService->setPowerPolicyGroupInternal(stringValue);
            if (!ret.ok()) {
                ALOGW("Failed to set power policy group(%s): %s", stringValue.c_str(),
                      ret.error().message().c_str());
            }
        } else if (propId == static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ)) {
            const auto& ret = mService->applyPowerPolicy(stringValue,
                                                         /*carServiceExpected=*/false,
                                                         /*force=*/false);
            if (!ret.ok()) {
                ALOGW("Failed to apply power policy(%s): %s", stringValue.c_str(),
                      ret.error().message().c_str());
            }
        }
    }
}

void PropertyChangeListener::onPropertySetError(
        [[maybe_unused]] const std::vector<HalPropError>& errors) {
    return;
}

EventHandler::EventHandler(CarPowerPolicyServer* service) : mService(service) {}

void EventHandler::handleMessage(const Message& message) {
    switch (message.what) {
        case MSG_CONNECT_TO_VHAL:
            mService->connectToVhalHelper();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

RequestIdHandler::RequestIdHandler(CarPowerPolicyServer* service) : mService(service) {}

void RequestIdHandler::handleMessage(const Message& message) {
    mService->handleApplyPowerPolicyRequest(message.what);
}

CarServiceNotificationHandler::CarServiceNotificationHandler(CarPowerPolicyServer* service) :
      mService(service) {}

void CarServiceNotificationHandler::terminate() {
    Mutex::Autolock lock(mMutex);
    mService = nullptr;
}

binder_status_t CarServiceNotificationHandler::dump(int fd, const char** args, uint32_t numArgs) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip dumping, CarPowerPolicyServer is ending");
        return STATUS_OK;
    }
    return mService->dump(fd, args, numArgs);
}

ScopedAStatus CarServiceNotificationHandler::notifyCarServiceReady(PolicyState* policyState) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying CarServiceReady, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyCarServiceReady(policyState);
}

ScopedAStatus CarServiceNotificationHandler::notifyPowerPolicyChange(const std::string& policyId,
                                                                     bool force) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying PowerPolicyChange, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyPowerPolicyChange(policyId, force);
}

ScopedAStatus CarServiceNotificationHandler::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip notifying PowerPolicyDefinition, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    return mService->notifyPowerPolicyDefinition(policyId, enabledComponents, disabledComponents);
}

CarPowerPolicyDelegate::CarPowerPolicyDelegate(CarPowerPolicyServer* service) : mService(service) {}

void CarPowerPolicyDelegate::terminate() {
    Mutex::Autolock lock(mMutex);
    mService = nullptr;
}

binder_status_t CarPowerPolicyDelegate::dump(int fd, const char** args, uint32_t numArgs) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip dumping, CarPowerPolicyServer is ending");
        return STATUS_OK;
    }
    return mService->dump(fd, args, numArgs);
}

ScopedAStatus CarPowerPolicyDelegate::notifyCarServiceReady(
        const std::shared_ptr<ICarPowerPolicyDelegateCallback>& callback,
        PowerPolicyInitData* aidlReturn) {
    return runWithService(
            [callback, aidlReturn](CarPowerPolicyServer* service) -> ScopedAStatus {
                return service->notifyCarServiceReadyInternal(callback, aidlReturn);
            },
            "notifyCarServiceReady");
}

ScopedAStatus CarPowerPolicyDelegate::applyPowerPolicyAsync(int32_t requestId,
                                                            const std::string& policyId,
                                                            bool force) {
    return runWithService(
            [requestId, policyId, force](CarPowerPolicyServer* service) -> ScopedAStatus {
                return service->applyPowerPolicyAsync(requestId, policyId, force);
            },
            "applyPowerPolicyAsync");
}

ScopedAStatus CarPowerPolicyDelegate::setPowerPolicyGroup(const std::string& policyGroupId) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip setting power policy group, CarPowerPolicyServer is ending");
        return ScopedAStatus::ok();
    }
    if (const auto& ret = mService->setPowerPolicyGroupInternal(policyGroupId); !ret.ok()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(ret.error().code(),
                                                           ret.error().message().c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyDelegate::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    return runWithService(
            [policyId, enabledComponents,
             disabledComponents](CarPowerPolicyServer* service) -> ScopedAStatus {
                return service->notifyPowerPolicyDefinition(policyId, enabledComponents,
                                                            disabledComponents);
            },
            "notifyPowerPolicyDefinition");
}

ScopedAStatus CarPowerPolicyDelegate::notifyPowerPolicyGroupDefinition(
        const std::string& policyGroupId, const std::vector<std::string>& powerPolicyPerState) {
    return runWithService(
            [policyGroupId, powerPolicyPerState](CarPowerPolicyServer* service) -> ScopedAStatus {
                return service->notifyPowerPolicyGroupDefinition(policyGroupId,
                                                                 powerPolicyPerState);
            },
            "notifyPowerPolicyGroupDefinition");
}

ScopedAStatus CarPowerPolicyDelegate::applyPowerPolicyPerPowerStateChangeAsync(
        int32_t requestId, ICarPowerPolicyDelegate::PowerState state) {
    return runWithService(
            [requestId, state](CarPowerPolicyServer* service) -> ScopedAStatus {
                return service->applyPowerPolicyPerPowerStateChangeAsync(requestId, state);
            },
            "applyPowerPolicyPerPowerStateChangeAsync");
}

ScopedAStatus CarPowerPolicyDelegate::runWithService(
        const std::function<ScopedAStatus(CarPowerPolicyServer*)>& action,
        const std::string& actionTitle) {
    Mutex::Autolock lock(mMutex);
    if (mService == nullptr) {
        ALOGD("Skip %s, CarPowerPolicyServer is ending", actionTitle.c_str());
        return ScopedAStatus::ok();
    }
    return action(mService);
}

ISilentModeChangeHandler::~ISilentModeChangeHandler() {}

Result<std::shared_ptr<CarPowerPolicyServer>> CarPowerPolicyServer::startService(
        const sp<Looper>& looper) {
    if (sCarPowerPolicyServer != nullptr) {
        return Error(INVALID_OPERATION) << "Cannot start service more than once";
    }
    std::shared_ptr<CarPowerPolicyServer> server = SharedRefBase::make<CarPowerPolicyServer>();
    const auto& ret = server->init(looper);
    if (!ret.ok()) {
        return Error(ret.error().code())
                << "Failed to start car power policy server: " << ret.error();
    }
    sCarPowerPolicyServer = server;

    return sCarPowerPolicyServer;
}

void CarPowerPolicyServer::terminateService() {
    if (sCarPowerPolicyServer != nullptr) {
        sCarPowerPolicyServer->terminate();
        sCarPowerPolicyServer = nullptr;
    }
}

CarPowerPolicyServer::CarPowerPolicyServer() :
      mSilentModeHandler(this),
      mIsPowerPolicyLocked(false),
      mIsCarServiceInOperation(false),
      mIsFirstConnectionToVhal(true) {
    mEventHandler = new EventHandler(this);
    mRequestIdHandler = new RequestIdHandler(this);
    mClientDeathRecipient = ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(&CarPowerPolicyServer::onClientBinderDied));
    mCarServiceDeathRecipient = ScopedAIBinder_DeathRecipient(
            AIBinder_DeathRecipient_new(&CarPowerPolicyServer::onCarServiceBinderDied));
    mPropertyChangeListener = std::make_unique<PropertyChangeListener>(this);
    mLinkUnlinkImpl = std::make_unique<AIBinderLinkUnlinkImpl>();
}

// For test-only.
void CarPowerPolicyServer::setLinkUnlinkImpl(
        std::unique_ptr<CarPowerPolicyServer::LinkUnlinkImpl> impl) {
    mLinkUnlinkImpl = std::move(impl);
}

ScopedAStatus CarPowerPolicyServer::getCurrentPowerPolicy(CarPowerPolicy* aidlReturn) {
    Mutex::Autolock lock(mMutex);
    if (!isPowerPolicyAppliedLocked()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE,
                                                    "The current power policy is not set");
    }
    *aidlReturn = *mCurrentPowerPolicyMeta.powerPolicy;
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::getPowerComponentState(PowerComponent componentId,
                                                           bool* aidlReturn) {
    const auto& ret = mComponentHandler.getPowerComponentState(componentId);
    if (!ret.ok()) {
        std::string errorMsg = ret.error().message();
        ALOGW("getPowerComponentState(%s) failed: %s", toString(componentId).c_str(),
              errorMsg.c_str());
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                  errorMsg.c_str());
    }
    *aidlReturn = *ret;
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::registerPowerPolicyChangeCallback(
        const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback,
        const CarPowerPolicyFilter& filter) {
    if (callback == nullptr) {
        std::string errorMsg = "Cannot register a null callback";
        ALOGW("%s", errorMsg.c_str());
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                  errorMsg.c_str());
    }
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    SpAIBinder binder = callback->asBinder();
    AIBinder* clientId = binder.get();
    if (isRegisteredLocked(clientId)) {
        std::string errorStr = StringPrintf("The callback(pid: %d, uid: %d) is already registered.",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT, errorCause);
    }

    std::unique_ptr<OnClientBinderDiedContext> context =
            std::make_unique<OnClientBinderDiedContext>(
                    OnClientBinderDiedContext{.server = this, .clientId = clientId});
    binder_status_t status = mLinkUnlinkImpl->linkToDeath(clientId, mClientDeathRecipient.get(),
                                                          static_cast<void*>(context.get()));
    if (status != STATUS_OK) {
        std::string errorStr = StringPrintf("The given callback(pid: %d, uid: %d) is dead",
                                            callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot register a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE, errorCause);
    }
    // Insert into a map to keep the context object alive.
    mOnClientBinderDiedContexts[clientId] = std::move(context);
    mPolicyChangeCallbacks.emplace_back(binder, filter, callingPid);

    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, filter: %s) is registered", callingPid,
              toString(filter.components).c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::unregisterPowerPolicyChangeCallback(
        const std::shared_ptr<ICarPowerPolicyChangeCallback>& callback) {
    Mutex::Autolock lock(mMutex);
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    if (callback == nullptr) {
        std::string errorMsg = "Cannot unregister a null callback";
        ALOGW("%s", errorMsg.c_str());
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                  errorMsg.c_str());
    }
    AIBinder* clientId = callback->asBinder().get();
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, clientId);
    if (it == mPolicyChangeCallbacks.end()) {
        std::string errorStr =
                StringPrintf("The callback(pid: %d, uid: %d) has not been registered", callingPid,
                             callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot unregister a callback: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT, errorCause);
    }
    if (mOnClientBinderDiedContexts.find(clientId) != mOnClientBinderDiedContexts.end()) {
        // We don't set a callback for unlinkToDeath but need to call unlinkToDeath to clean up the
        // registered death recipient.
        mLinkUnlinkImpl->unlinkToDeath(clientId, mClientDeathRecipient.get(),
                                       static_cast<void*>(
                                               mOnClientBinderDiedContexts[clientId].get()));
        mOnClientBinderDiedContexts.erase(clientId);
    }
    mPolicyChangeCallbacks.erase(it);
    if (DEBUG) {
        ALOGD("Power policy change callback(pid: %d, uid: %d) is unregistered", callingPid,
              callingUid);
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::applyPowerPolicy(const std::string& policyId) {
    if (!car_power_policy_refactoring()) {
        ALOGE("Cannot execute applyPowerPolicy: car_power_policy_refactoring flag is not enabled");
        return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    if (const auto& ret =
                applyPowerPolicyInternal(policyId, /*force=*/false, /*notifyCarService=*/true);
        !ret.ok()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(ret.error().code(),
                                                           ret.error().message().c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::setPowerPolicyGroup(const std::string& policyGroupId) {
    if (!car_power_policy_refactoring()) {
        ALOGE("Cannot execute setPowerPolicyGroup: car_power_policy_refactoring flag is not "
              "enabled");
        return ScopedAStatus::fromExceptionCode(EX_UNSUPPORTED_OPERATION);
    }
    if (const auto& ret = setPowerPolicyGroupInternal(policyGroupId); !ret.ok()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(ret.error().code(),
                                                           ret.error().message().c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyCarServiceReady(PolicyState* policyState) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    mSilentModeHandler.stopMonitoringSilentModeHwState();
    Mutex::Autolock lock(mMutex);
    policyState->policyId =
            isPowerPolicyAppliedLocked() ? mCurrentPowerPolicyMeta.powerPolicy->policyId : "";
    policyState->policyGroupId = mCurrentPolicyGroupId;
    mIsCarServiceInOperation = true;
    ALOGI("CarService is now responsible for power policy management");
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyPowerPolicyChange(const std::string& policyId,
                                                            bool force) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret = applyPowerPolicy(policyId, /*carServiceExpected=*/true, force);
    if (!ret.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE,
                                                    StringPrintf("Failed to notify power policy "
                                                                 "change: %s",
                                                                 ret.error().message().c_str())
                                                            .c_str());
    }
    ALOGD("Policy change(%s) is notified by CarService", policyId.c_str());
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyPowerPolicyDefinition(
        const std::string& policyId, const std::vector<std::string>& enabledComponents,
        const std::vector<std::string>& disabledComponents) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret =
            mPolicyManager.definePowerPolicy(policyId, enabledComponents, disabledComponents);
    if (!ret.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                    StringPrintf("Failed to notify power policy "
                                                                 "definition: %s",
                                                                 ret.error().message().c_str())
                                                            .c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyPowerPolicyGroupDefinition(
        const std::string& policyGroupId, const std::vector<std::string>& powerPolicyPerState) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    const auto& ret = mPolicyManager.definePowerPolicyGroup(policyGroupId, powerPolicyPerState);
    if (!ret.ok()) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                    StringPrintf("Failed to notify power policy "
                                                                 "group definition: %s",
                                                                 ret.error().message().c_str())
                                                            .c_str());
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::applyPowerPolicyPerPowerStateChangeAsync(
        int32_t requestId, ICarPowerPolicyDelegate::PowerState state) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    VehicleApPowerStateReport apPowerState;
    std::string defaultPowerPolicyId;
    // TODO(b/318520417): Power policy should be updated according to SilentMode.
    // TODO(b/321319532): Create a map for default power policy in PolicyManager.
    switch (state) {
        case ICarPowerPolicyDelegate::PowerState::WAIT_FOR_VHAL:
            apPowerState = VehicleApPowerStateReport::WAIT_FOR_VHAL;
            defaultPowerPolicyId = kSystemPolicyIdInitialOn;
            break;
        case ICarPowerPolicyDelegate::PowerState::ON:
            apPowerState = VehicleApPowerStateReport::ON;
            defaultPowerPolicyId = kSystemPolicyIdAllOn;
            break;
        default:
            return ScopedAStatus::
                    fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                        StringPrintf("Power policy cannot be "
                                                                     "changed for power state(%d)",
                                                                     state)
                                                                .c_str());
    }
    std::string powerStateName = toString(apPowerState);
    ALOGI("Power policy change for new power state(%s) is requested", powerStateName.c_str());
    std::string currentPolicyGroupId;
    {
        Mutex::Autolock lock(mMutex);
        currentPolicyGroupId = mCurrentPolicyGroupId;
    }
    const auto& policy =
            mPolicyManager.getDefaultPowerPolicyForState(currentPolicyGroupId, apPowerState);
    std::string policyId;
    if (policy.ok()) {
        policyId = (*policy)->policyId;
        ALOGI("Vendor-configured policy(%s) is about to be applied for power state(%s)",
              policyId.c_str(), powerStateName.c_str());
    } else {
        policyId = defaultPowerPolicyId;
        ALOGI("Default policy(%s) is about to be applied for power state(%s)", policyId.c_str(),
              powerStateName.c_str());
    }

    if (auto ret = enqueuePowerPolicyRequest(requestId, policyId, /*force=*/false); !ret.isOk()) {
        ALOGW("Failed to apply power policy(%s) for power state(%s) with request ID(%d)",
              policyId.c_str(), powerStateName.c_str(), requestId);
        return ret;
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::applyPowerPolicyAsync(int32_t requestId,
                                                          const std::string& policyId, bool force) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    if (auto ret = enqueuePowerPolicyRequest(requestId, policyId, force); !ret.isOk()) {
        ALOGW("Failed to apply power policy(%s) with request ID(%d)", policyId.c_str(), requestId);
        return ret;
    }
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::enqueuePowerPolicyRequest(int32_t requestId,
                                                              const std::string& policyId,
                                                              bool force) {
    Mutex::Autolock lock(mMutex);
    if (mPolicyRequestById.count(requestId) > 0) {
        return ScopedAStatus::
                fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                    StringPrintf("Duplicated request ID(%d)",
                                                                 requestId)
                                                            .c_str());
    }
    mPolicyRequestById[requestId] = PolicyRequest{.policyId = policyId, .force = force};
    ALOGI("Queueing request ID(%d) for applying power policy(%s): force=%s", requestId,
          policyId.c_str(), force ? "true" : "false");
    mHandlerLooper->sendMessage(mRequestIdHandler, requestId);
    return ScopedAStatus::ok();
}

ScopedAStatus CarPowerPolicyServer::notifyCarServiceReadyInternal(
        const std::shared_ptr<ICarPowerPolicyDelegateCallback>& callback,
        PowerPolicyInitData* aidlReturn) {
    ScopedAStatus status = checkSystemPermission();
    if (!status.isOk()) {
        return status;
    }
    if (callback == nullptr) {
        std::string errorMsg = "Cannot register a null callback for notifyCarServiceReadyInternal";
        ALOGW("%s", errorMsg.c_str());
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_ARGUMENT,
                                                                  errorMsg.c_str());
    }
    Mutex::Autolock lock(mMutex);
    // Override with the newer callback.
    mPowerPolicyDelegateCallback = callback->asBinder();
    binder_status_t linkStatus =
            mLinkUnlinkImpl->linkToDeath(mPowerPolicyDelegateCallback.get(),
                                         mCarServiceDeathRecipient.get(), static_cast<void*>(this));
    if (linkStatus != STATUS_OK) {
        pid_t callingPid = IPCThreadState::self()->getCallingPid();
        uid_t callingUid = IPCThreadState::self()->getCallingUid();
        std::string errorStr =
                StringPrintf("CarService(pid: %d, uid: %d) is dead", callingPid, callingUid);
        const char* errorCause = errorStr.c_str();
        ALOGW("Cannot handle notifyCarServiceReady: %s", errorCause);
        return ScopedAStatus::fromServiceSpecificErrorWithMessage(EX_ILLEGAL_STATE, errorCause);
    }

    aidlReturn->registeredCustomComponents = std::move(mPolicyManager.getCustomComponents());
    aidlReturn->currentPowerPolicy = *mCurrentPowerPolicyMeta.powerPolicy;
    aidlReturn->registeredPolicies = std::move(mPolicyManager.getRegisteredPolicies());
    ALOGI("CarService registers ICarPowerPolicyDelegateCallback");
    return ScopedAStatus::ok();
}

status_t CarPowerPolicyServer::dump(int fd, const char** args, uint32_t numArgs) {
    Vector<String16> argsV;
    for (size_t i = 0; i < numArgs; i++) {
        argsV.push(String16(args[i]));
    }

    {
        Mutex::Autolock lock(mMutex);
        const char* indent = "  ";
        const char* doubleIndent = "    ";
        WriteStringToFd("CAR POWER POLICY DAEMON\n", fd);
        WriteStringToFd(StringPrintf("%sCarService is in operation: %s\n", indent,
                                     mIsCarServiceInOperation ? "true" : "false"),
                        fd);
        WriteStringToFd(StringPrintf("%sConnection to VHAL: %s\n", indent,
                                     mVhalService.get() ? "connected" : "disconnected"),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy: %s\n", indent,
                                     isPowerPolicyAppliedLocked()
                                             ? mCurrentPowerPolicyMeta.powerPolicy->policyId.c_str()
                                             : "not set"),
                        fd);
        WriteStringToFd(StringPrintf("%sLast uptime of applying power policy: %" PRId64 "ms\n",
                                     indent, mLastApplyPowerPolicyUptimeMs.value_or(-1)),
                        fd);
        WriteStringToFd(StringPrintf("%sPending power policy ID: %s\n", indent,
                                     mPendingPowerPolicyId.c_str()),
                        fd);
        WriteStringToFd(StringPrintf("%sCurrent power policy group ID: %s\n", indent,
                                     mCurrentPolicyGroupId.empty() ? "not set"
                                                                   : mCurrentPolicyGroupId.c_str()),
                        fd);
        WriteStringToFd(StringPrintf("%sLast uptime of setting default power policy group: "
                                     "%" PRId64 "ms\n",
                                     indent, mLastSetDefaultPowerPolicyGroupUptimeMs.value_or(-1)),
                        fd);
        WriteStringToFd(StringPrintf("%sPolicy change callbacks:%s\n", indent,
                                     mPolicyChangeCallbacks.size() ? "" : " none"),
                        fd);
        for (auto& callback : mPolicyChangeCallbacks) {
            WriteStringToFd(StringPrintf("%s- %s\n", doubleIndent,
                                         callbackToString(callback).c_str()),
                            fd);
        }
    }
    if (const auto& ret = mPolicyManager.dump(fd, argsV); !ret.ok()) {
        ALOGW("Failed to dump power policy handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    if (const auto& ret = mComponentHandler.dump(fd); !ret.ok()) {
        ALOGW("Failed to dump power component handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    if (const auto& ret = mSilentModeHandler.dump(fd, argsV); !ret.ok()) {
        ALOGW("Failed to dump Silent Mode handler: %s", ret.error().message().c_str());
        return ret.error().code();
    }
    return OK;
}

Result<void> CarPowerPolicyServer::init(const sp<Looper>& looper) {
    AIBinder* binderCarService = AServiceManager_checkService(kCarServiceInterface);
    {
        Mutex::Autolock lock(mMutex);
        // Before initializing power policy daemon, we need to update mIsCarServiceInOperation
        // according to whether CPMS is running.
        mIsCarServiceInOperation = binderCarService != nullptr;
    }
    mHandlerLooper = looper;
    mPolicyManager.init();
    mComponentHandler.init();
    mSilentModeHandler.init();

    binder_exception_t err =
            AServiceManager_addService(this->asBinder().get(), kCarPowerPolicyServerInterface);
    if (err != EX_NONE) {
        return Error(err) << "Failed to add carpowerpolicyd to ServiceManager";
    }

    if (car_power_policy_refactoring()) {
        ALOGI("Registering ICarPowerPolicyDelegate");
        mCarPowerPolicyDelegate = SharedRefBase::make<CarPowerPolicyDelegate>(this);
        if (err = AServiceManager_addService(mCarPowerPolicyDelegate->asBinder().get(),
                                             kCarPowerPolicyDelegateInterface);
            err != EX_NONE) {
            return Error(err) << "Failed to add car power policy delegate to ServiceManager";
        }
    } else {
        mCarServiceNotificationHandler = SharedRefBase::make<CarServiceNotificationHandler>(this);
        if (err = AServiceManager_addService(mCarServiceNotificationHandler->asBinder().get(),
                                             kCarPowerPolicySystemNotificationInterface);
            err != EX_NONE) {
            return Error(err)
                    << "Failed to add car power policy system notification to ServiceManager";
        }
    }

    connectToVhal();
    return {};
}

void CarPowerPolicyServer::terminate() {
    Mutex::Autolock lock(mMutex);
    mPolicyChangeCallbacks.clear();
    if (mVhalService != nullptr) {
        mSubscriptionClient->unsubscribe(
                {static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ),
                 static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ)});
    }

    if (car_power_policy_refactoring()) {
        if (mCarPowerPolicyDelegate != nullptr) {
            mCarPowerPolicyDelegate->terminate();
            mCarPowerPolicyDelegate = nullptr;
        }
    } else {
        if (mCarServiceNotificationHandler != nullptr) {
            mCarServiceNotificationHandler->terminate();
            mCarServiceNotificationHandler = nullptr;
        }
    }

    // Delete the deathRecipient so that all binders would be unlinked.
    mClientDeathRecipient = ScopedAIBinder_DeathRecipient();
    mSilentModeHandler.release();
    // Remove the messages so that mEventHandler and mRequestIdHandler would no longer be used.
    mHandlerLooper->removeMessages(mEventHandler);
    mHandlerLooper->removeMessages(mRequestIdHandler);
}

void CarPowerPolicyServer::onClientBinderDied(void* cookie) {
    OnClientBinderDiedContext* context = static_cast<OnClientBinderDiedContext*>(cookie);
    context->server->handleClientBinderDeath(context->clientId);
}

void CarPowerPolicyServer::onCarServiceBinderDied(void* cookie) {
    CarPowerPolicyServer* server = static_cast<CarPowerPolicyServer*>(cookie);
    server->handleCarServiceBinderDeath();
}

void CarPowerPolicyServer::handleClientBinderDeath(const AIBinder* clientId) {
    Mutex::Autolock lock(mMutex);
    auto it = lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, clientId);
    if (it != mPolicyChangeCallbacks.end()) {
        ALOGW("Power policy callback(pid: %d) died", it->pid);
        mPolicyChangeCallbacks.erase(it);
    }
    mOnClientBinderDiedContexts.erase(clientId);
}

void CarPowerPolicyServer::handleCarServiceBinderDeath() {
    Mutex::Autolock lock(mMutex);
    mPowerPolicyDelegateCallback = nullptr;
}

void CarPowerPolicyServer::handleVhalDeath() {
    {
        Mutex::Autolock lock(mMutex);
        ALOGW("VHAL has died.");
        mVhalService = nullptr;
    }
    connectToVhal();
}

void CarPowerPolicyServer::handleApplyPowerPolicyRequest(const int32_t requestId) {
    ALOGI("Handling request ID(%d) to apply power policy", requestId);
    PolicyRequest policyRequest;
    std::shared_ptr<ICarPowerPolicyDelegateCallback> callback;
    {
        Mutex::Autolock lock(mMutex);
        if (mPolicyRequestById.count(requestId) == 0) {
            ALOGW("Request ID(%d) for applying power policy is not found", requestId);
            return;
        }
        policyRequest = mPolicyRequestById[requestId];
        mPolicyRequestById.erase(requestId);
        callback = ICarPowerPolicyDelegateCallback::fromBinder(mPowerPolicyDelegateCallback);
        if (callback == nullptr) {
            ALOGW("ICarPowerPolicyDelegateCallback is not set");
        }
    }
    if (const auto& ret = applyPowerPolicyInternal(policyRequest.policyId, policyRequest.force,
                                                   /*notifyCarService=*/false);
        !ret.ok()) {
        ALOGW("%s", ret.error().message().c_str());
        if (callback != nullptr) {
            callback->onApplyPowerPolicyFailed(requestId,
                                               convertErrorToFailureReason(ret.error().code()));
        }
        return;
    }
    if (callback != nullptr) {
        callback->onApplyPowerPolicySucceeded(requestId, *mComponentHandler.getAccumulatedPolicy());
    }
}

Result<void> CarPowerPolicyServer::applyPowerPolicy(const std::string& policyId,
                                                    const bool carServiceInOperation,
                                                    const bool force) {
    auto policyMeta = mPolicyManager.getPowerPolicy(policyId);
    if (!policyMeta.ok()) {
        return Error() << "Failed to apply power policy: " << policyMeta.error().message();
    }

    std::vector<CallbackInfo> clients;
    if (Mutex::Autolock lock(mMutex); mIsCarServiceInOperation != carServiceInOperation) {
        return Error() << (mIsCarServiceInOperation
                                   ? "After CarService starts serving, power policy cannot be "
                                     "managed in car power policy daemon"
                                   : "Before CarService starts serving, power policy cannot be "
                                     "applied from CarService");
    } else {
        if (!canApplyPowerPolicyLocked(*policyMeta, force, /*out*/ clients)) {
            return {};
        }
    }
    applyAndNotifyPowerPolicy(*policyMeta, clients, /*notifyCarService=*/false);
    return {};
}

bool CarPowerPolicyServer::canApplyPowerPolicyLocked(const CarPowerPolicyMeta& policyMeta,
                                                     const bool force,
                                                     std::vector<CallbackInfo>& outClients) {
    const std::string& policyId = policyMeta.powerPolicy->policyId;
    bool isPolicyApplied = isPowerPolicyAppliedLocked();
    if (isPolicyApplied && mCurrentPowerPolicyMeta.powerPolicy->policyId == policyId) {
        ALOGI("Applying policy skipped: the given policy(ID: %s) is the current policy",
              policyId.c_str());
        return false;
    }
    if (policyMeta.isPreemptive) {
        if (isPolicyApplied && !mCurrentPowerPolicyMeta.isPreemptive) {
            mPendingPowerPolicyId = mCurrentPowerPolicyMeta.powerPolicy->policyId;
        }
        mIsPowerPolicyLocked = true;
    } else {
        if (force) {
            mPendingPowerPolicyId.clear();
            mIsPowerPolicyLocked = false;
        } else if (mIsPowerPolicyLocked) {
            ALOGI("%s is queued and will be applied after power policy get unlocked",
                  policyId.c_str());
            mPendingPowerPolicyId = policyId;
            return false;
        }
    }
    mCurrentPowerPolicyMeta = policyMeta;
    outClients = mPolicyChangeCallbacks;
    mLastApplyPowerPolicyUptimeMs = uptimeMillis();
    ALOGD("CurrentPowerPolicyMeta is updated to %s", policyId.c_str());
    return true;
}

void CarPowerPolicyServer::applyAndNotifyPowerPolicy(const CarPowerPolicyMeta& policyMeta,
                                                     const std::vector<CallbackInfo>& clients,
                                                     const bool notifyCarService) {
    CarPowerPolicyPtr policy = policyMeta.powerPolicy;
    const std::string& policyId = policy->policyId;
    mComponentHandler.applyPowerPolicy(policy);

    std::shared_ptr<ICarPowerPolicyDelegateCallback> callback = nullptr;
    if (car_power_policy_refactoring()) {
        {
            Mutex::Autolock lock(mMutex);
            callback = ICarPowerPolicyDelegateCallback::fromBinder(mPowerPolicyDelegateCallback);
        }
        if (callback != nullptr) {
            ALOGD("Asking CPMS to update power components for policy(%s)", policyId.c_str());
            callback->updatePowerComponents(*policy);
        } else {
            ALOGW("CarService isn't ready to update power components for policy(%s)",
                  policyId.c_str());
        }
    }

    if (const auto& ret = notifyVhalNewPowerPolicy(policy->policyId); !ret.ok()) {
        ALOGW("Failed to tell VHAL the new power policy(%s): %s", policy->policyId.c_str(),
              ret.error().message().c_str());
    }
    auto accumulatedPolicy = mComponentHandler.getAccumulatedPolicy();
    for (auto client : clients) {
        ICarPowerPolicyChangeCallback::fromBinder(client.binder)
                ->onPolicyChanged(*accumulatedPolicy);
    }
    if (notifyCarService && callback != nullptr) {
        callback->onPowerPolicyChanged(*accumulatedPolicy);
    }
    ALOGI("The current power policy is %s", policyId.c_str());
}

Result<void> CarPowerPolicyServer::applyPowerPolicyInternal(const std::string& policyId,
                                                            const bool force,
                                                            const bool notifyCarService) {
    auto policyMeta = mPolicyManager.getPowerPolicy(policyId);
    if (!policyMeta.ok()) {
        return Error(EX_ILLEGAL_ARGUMENT)
                << "Failed to apply power policy: " << policyMeta.error().message();
    }
    std::vector<CallbackInfo> clients;
    {
        Mutex::Autolock lock(mMutex);
        if (!canApplyPowerPolicyLocked(*policyMeta, force, /*out*/ clients)) {
            return {};
        }
    }
    applyAndNotifyPowerPolicy(*policyMeta, clients, notifyCarService);
    return {};
}

Result<void> CarPowerPolicyServer::setPowerPolicyGroupInternal(const std::string& groupId) {
    if (!mPolicyManager.isPowerPolicyGroupAvailable(groupId)) {
        return Error() << StringPrintf("Power policy group(%s) is not available", groupId.c_str());
    }
    Mutex::Autolock lock(mMutex);
    if (mIsCarServiceInOperation) {
        return Error() << "After CarService starts serving, power policy group cannot be set in "
                          "car power policy daemon";
    }
    mCurrentPolicyGroupId = groupId;
    ALOGI("The current power policy group is |%s|", groupId.c_str());
    return {};
}

void CarPowerPolicyServer::notifySilentModeChange(const bool isSilent) {
    std::string pendingPowerPolicyId;
    if (Mutex::Autolock lock(mMutex); mIsCarServiceInOperation) {
        return;
    } else {
        pendingPowerPolicyId = mPendingPowerPolicyId;
    }
    ALOGI("Silent Mode is set to %s", isSilent ? "silent" : "non-silent");
    Result<void> ret;
    if (isSilent) {
        ret = applyPowerPolicy(kSystemPolicyIdNoUserInteraction,
                               /*carServiceExpected=*/false, /*force=*/false);
    } else {
        ret = applyPowerPolicy(pendingPowerPolicyId,
                               /*carServiceExpected=*/false, /*force=*/true);
    }
    if (!ret.ok()) {
        ALOGW("Failed to apply power policy: %s", ret.error().message().c_str());
    }
}

bool CarPowerPolicyServer::isRegisteredLocked(const AIBinder* binder) {
    return lookupPowerPolicyChangeCallback(mPolicyChangeCallbacks, binder) !=
            mPolicyChangeCallbacks.end();
}

// This method ensures that the attempt to connect to VHAL occurs in the main thread.
void CarPowerPolicyServer::connectToVhal() {
    mRemainingConnectionRetryCount = kMaxConnectionRetry;
    mHandlerLooper->sendMessage(mEventHandler, MSG_CONNECT_TO_VHAL);
}

// connectToVhalHelper is always executed in the main thread.
void CarPowerPolicyServer::connectToVhalHelper() {
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService != nullptr) {
            return;
        }
    }
    std::shared_ptr<IVhalClient> vhalService = IVhalClient::tryCreate();
    if (vhalService == nullptr) {
        ALOGW("Failed to connect to VHAL. Retrying in %" PRId64 " ms.",
              nanoseconds_to_milliseconds(kConnectionRetryIntervalNs));
        mRemainingConnectionRetryCount--;
        if (mRemainingConnectionRetryCount <= 0) {
            ALOGE("Failed to connect to VHAL after %d attempt%s. Gave up.", kMaxConnectionRetry,
                  kMaxConnectionRetry > 1 ? "s" : "");
            return;
        }
        mHandlerLooper->sendMessageDelayed(kConnectionRetryIntervalNs, mEventHandler,
                                           MSG_CONNECT_TO_VHAL);
        return;
    }
    vhalService->addOnBinderDiedCallback(
            std::make_shared<IVhalClient::OnBinderDiedCallbackFunc>([this] { handleVhalDeath(); }));
    std::string currentPolicyId;
    {
        Mutex::Autolock lock(mMutex);
        mVhalService = vhalService;
        mSubscriptionClient = mVhalService->getSubscriptionClient(mPropertyChangeListener);
        if (isPowerPolicyAppliedLocked()) {
            currentPolicyId = mCurrentPowerPolicyMeta.powerPolicy->policyId;
        }
    }
    /*
     * When VHAL is first executed, a normal power management goes on. When VHAL is restarted due to
     * some reasons, the current policy is notified to VHAL.
     */
    if (mIsFirstConnectionToVhal) {
        applyInitialPowerPolicy();
        mIsFirstConnectionToVhal = false;
    } else if (!currentPolicyId.empty()) {
        notifyVhalNewPowerPolicy(currentPolicyId);
    }
    subscribeToVhal();
    ALOGI("Connected to VHAL");
    return;
}

void CarPowerPolicyServer::applyInitialPowerPolicy() {
    std::string policyId;
    std::string currentPolicyGroupId;
    CarPowerPolicyPtr powerPolicy;
    {
        Mutex::Autolock lock(mMutex);
        if (mIsCarServiceInOperation) {
            ALOGI("Skipping initial power policy application because CarService is running");
            return;
        }
        policyId = mPendingPowerPolicyId;
        currentPolicyGroupId = mCurrentPolicyGroupId;
    }
    if (policyId.empty()) {
        if (auto policy = mPolicyManager.getDefaultPowerPolicyForState(currentPolicyGroupId,
                                                                       VehicleApPowerStateReport::
                                                                               WAIT_FOR_VHAL);
            policy.ok()) {
            policyId = (*policy)->policyId;
        } else {
            policyId = kSystemPolicyIdInitialOn;
        }
    }
    if (const auto& ret = applyPowerPolicy(policyId, /*carServiceExpected=*/false, /*force=*/false);
        !ret.ok()) {
        ALOGW("Cannot apply the initial power policy(%s): %s", policyId.c_str(),
              ret.error().message().c_str());
        return;
    }
    ALOGD("Policy(%s) is applied as the initial one", policyId.c_str());
}

void CarPowerPolicyServer::subscribeToVhal() {
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_REQ),
                        [this](const IHalPropValue& value) {
                            std::string stringValue = value.getStringValue();
                            if (stringValue.size() > 0) {
                                const auto& ret = applyPowerPolicy(stringValue,
                                                                   /*carServiceExpected=*/false,
                                                                   /*force=*/false);
                                if (!ret.ok()) {
                                    ALOGW("Failed to apply power policy(%s): %s",
                                          stringValue.c_str(), ret.error().message().c_str());
                                }
                            }
                        });
    subscribeToProperty(static_cast<int32_t>(VehicleProperty::POWER_POLICY_GROUP_REQ),
                        [this](const IHalPropValue& value) {
                            std::string stringValue = value.getStringValue();
                            if (stringValue.size() > 0) {
                                const auto& ret = setPowerPolicyGroupInternal(stringValue);
                                if (ret.ok()) {
                                    Mutex::Autolock lock(mMutex);
                                    mLastSetDefaultPowerPolicyGroupUptimeMs = value.getTimestamp();
                                } else {
                                    ALOGW("Failed to set power policy group(%s): %s",
                                          stringValue.c_str(), ret.error().message().c_str());
                                }
                            }
                        });
}

void CarPowerPolicyServer::subscribeToProperty(
        int32_t prop, std::function<void(const IHalPropValue&)> processor) {
    if (!isPropertySupported(prop)) {
        ALOGW("Vehicle property(%d) is not supported by VHAL.", prop);
        return;
    }
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            ALOGW("Failed to subscribe to property(%d): VHAL is not ready", prop);
            return;
        }
        vhalService = mVhalService;
    }

    VhalClientResult<std::unique_ptr<IHalPropValue>> result =
            vhalService->getValueSync(*vhalService->createHalPropValue(prop));

    if (!result.ok()) {
        ALOGW("Failed to get vehicle property(%d) value, error: %s.", prop,
              result.error().message().c_str());
        return;
    }
    processor(*result.value());
    std::vector<SubscribeOptions> options = {
            {.propId = prop, .areaIds = {}},
    };

    if (auto result = mSubscriptionClient->subscribe(options); !result.ok()) {
        ALOGW("Failed to subscribe to vehicle property(%d), error: %s", prop,
              result.error().message().c_str());
    }
}

Result<void> CarPowerPolicyServer::notifyVhalNewPowerPolicy(const std::string& policyId) {
    int32_t prop = static_cast<int32_t>(VehicleProperty::CURRENT_POWER_POLICY);
    if (!isPropertySupported(prop)) {
        return Error() << StringPrintf("Vehicle property(%d) is not supported by VHAL.", prop);
    }
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            return Error() << "VHAL is not ready";
        }
        vhalService = mVhalService;
    }
    std::unique_ptr<IHalPropValue> propValue = vhalService->createHalPropValue(prop);
    propValue->setStringValue(policyId);

    VhalClientResult<void> result = vhalService->setValueSync(*propValue);
    if (!result.ok()) {
        return Error() << "Failed to set CURRENT_POWER_POLICY property";
    }
    ALOGD("Policy(%s) is notified to VHAL", policyId.c_str());
    return {};
}

bool CarPowerPolicyServer::isPropertySupported(const int32_t prop) {
    if (mSupportedProperties.count(prop) > 0) {
        return mSupportedProperties[prop];
    }
    StatusCode status;
    hidl_vec<int32_t> props = {prop};
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr) {
            ALOGW("Failed to check if property(%d) is supported: VHAL is not ready", prop);
            return false;
        }
        vhalService = mVhalService;
    }
    auto result = vhalService->getPropConfigs(props);
    mSupportedProperties[prop] = result.ok();
    return mSupportedProperties[prop];
}

bool CarPowerPolicyServer::isPowerPolicyAppliedLocked() const {
    return mCurrentPowerPolicyMeta.powerPolicy != nullptr;
}

std::string CarPowerPolicyServer::callbackToString(const CallbackInfo& callback) {
    const std::vector<PowerComponent>& components = callback.filter.components;
    return StringPrintf("callback(pid %d, filter: %s)", callback.pid, toString(components).c_str());
}

std::vector<CallbackInfo> CarPowerPolicyServer::getPolicyChangeCallbacks() {
    Mutex::Autolock lock(mMutex);
    return mPolicyChangeCallbacks;
}

size_t CarPowerPolicyServer::countOnClientBinderDiedContexts() {
    Mutex::Autolock lock(mMutex);
    return mOnClientBinderDiedContexts.size();
}

binder_status_t CarPowerPolicyServer::AIBinderLinkUnlinkImpl::linkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_linkToDeath(binder, recipient, cookie);
}

binder_status_t CarPowerPolicyServer::AIBinderLinkUnlinkImpl::unlinkToDeath(
        AIBinder* binder, AIBinder_DeathRecipient* recipient, void* cookie) {
    return AIBinder_unlinkToDeath(binder, recipient, cookie);
}

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

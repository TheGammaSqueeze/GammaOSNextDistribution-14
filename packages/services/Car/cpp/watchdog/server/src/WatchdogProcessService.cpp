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

#define LOG_TAG "carwatchdogd"
#define DEBUG false  // STOPSHIP if true.

#include "WatchdogProcessService.h"

#include "PackageInfoResolver.h"
#include "ServiceManager.h"
#include "UidProcStatsCollector.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/hardware/automotive/vehicle/BnVehicle.h>
#include <aidl/android/hardware/automotive/vehicle/ProcessTerminationReason.h>
#include <android-base/file.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android/util/ProtoOutputStream.h>
#include <binder/IPCThreadState.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/SystemClock.h>

#include <IVhalClient.h>
#include <VehicleHalTypes.h>
#include <inttypes.h>

#include <utility>

#include <carwatchdog_daemon_dump.proto.h>
#include <health_check_client_info.proto.h>
#include <performance_stats.proto.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::ICarWatchdogClient;
using ::aidl::android::automotive::watchdog::TimeoutLength;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem;
using ::aidl::android::automotive::watchdog::internal::ProcessIdentifier;
using ::aidl::android::hardware::automotive::vehicle::BnVehicle;
using ::aidl::android::hardware::automotive::vehicle::ProcessTerminationReason;
using ::aidl::android::hardware::automotive::vehicle::StatusCode;
using ::aidl::android::hardware::automotive::vehicle::SubscribeOptions;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropConfig;
using ::aidl::android::hardware::automotive::vehicle::VehicleProperty;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropertyStatus;
using ::aidl::android::hardware::automotive::vehicle::VehiclePropValue;
using ::android::sp;
using ::android::String16;
using ::android::base::Error;
using ::android::base::GetIntProperty;
using ::android::base::GetProperty;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::android::base::Trim;
using ::android::base::WriteStringToFd;
using ::android::binder::Status;
using ::android::frameworks::automotive::vhal::HalPropError;
using ::android::frameworks::automotive::vhal::IHalPropValue;
using ::android::frameworks::automotive::vhal::ISubscriptionClient;
using ::android::frameworks::automotive::vhal::IVhalClient;
using ::android::hardware::hidl_vec;
using ::android::hardware::interfacesEqual;
using ::android::hardware::Return;
using ::android::hidl::base::V1_0::IBase;
using ::android::hidl::manager::V1_0::IServiceManager;
using ::android::util::ProtoOutputStream;
using ::ndk::ScopedAIBinder_DeathRecipient;
using ::ndk::ScopedAStatus;
using ::ndk::SpAIBinder;

namespace {

const std::vector<TimeoutLength> kTimeouts = {TimeoutLength::TIMEOUT_CRITICAL,
                                              TimeoutLength::TIMEOUT_MODERATE,
                                              TimeoutLength::TIMEOUT_NORMAL};

// TimeoutLength is also used as a message ID. Other message IDs should start next to
// TimeoutLength::TIMEOUT_NORMAL.
const int32_t MSG_VHAL_WATCHDOG_ALIVE = static_cast<int>(TimeoutLength::TIMEOUT_NORMAL) + 1;
const int32_t MSG_VHAL_HEALTH_CHECK = MSG_VHAL_WATCHDOG_ALIVE + 1;
const int32_t MSG_CACHE_VHAL_PROCESS_IDENTIFIER = MSG_VHAL_HEALTH_CHECK + 1;

// VHAL is supposed to send heart beat every 3s. Car watchdog checks if there is the latest heart
// beat from VHAL within 3s, allowing 1s marginal time.
// If {@code ro.carwatchdog.vhal_healthcheck.interval} is set, car watchdog checks VHAL health at
// the given interval. The lower bound of the interval is 3s.
constexpr int32_t kDefaultVhalCheckIntervalSec = 3;
constexpr std::chrono::milliseconds kHealthCheckDelayMillis = 1s;
constexpr int32_t kMaxVhalPidCachingAttempts = 2;
constexpr std::chrono::nanoseconds kDefaultVhalPidCachingRetryDelayNs = 30s;
constexpr TimeoutLength kCarWatchdogServiceTimeoutDelay = TimeoutLength::TIMEOUT_CRITICAL;
constexpr int32_t kMissingIntPropertyValue = -1;

constexpr const char kPropertyVhalCheckInterval[] = "ro.carwatchdog.vhal_healthcheck.interval";
constexpr const char kPropertyClientCheckInterval[] = "ro.carwatchdog.client_healthcheck.interval";
constexpr const char kServiceName[] = "WatchdogProcessService";
constexpr const char kHidlVhalInterfaceName[] = "android.hardware.automotive.vehicle@2.0::IVehicle";

const std::function<sp<IServiceManager>()> kDefaultTryGetHidlServiceManager =
        []() -> sp<IServiceManager> { return IServiceManager::tryGetService(/*getStub=*/false); };

enum RegistrationError {
    ERR_ILLEGAL_STATE = 0,
    ERR_DUPLICATE_REGISTRATION,
};

ScopedAStatus toScopedAStatus(Result<void> resultWithRegistrationError) {
    if (resultWithRegistrationError.ok()) {
        return ScopedAStatus::ok();
    }
    if (resultWithRegistrationError.error().code() ==
        RegistrationError::ERR_DUPLICATE_REGISTRATION) {
        return ScopedAStatus::ok();
    }
    return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                       resultWithRegistrationError.error()
                                                               .message()
                                                               .c_str());
}

std::string toPidString(const std::vector<ProcessIdentifier>& processIdentifiers) {
    size_t size = processIdentifiers.size();
    if (size == 0) {
        return "";
    }
    std::string buffer;
    StringAppendF(&buffer, "%d", processIdentifiers[0].pid);
    for (size_t i = 1; i < size; i++) {
        StringAppendF(&buffer, ", %d", processIdentifiers[i].pid);
    }
    return buffer;
}

bool isSystemShuttingDown() {
    std::string sysPowerCtl;
    std::istringstream tokenStream(GetProperty("sys.powerctl", ""));
    std::getline(tokenStream, sysPowerCtl, ',');
    return sysPowerCtl == "reboot" || sysPowerCtl == "shutdown";
}

int64_t getStartTimeForPid(pid_t pid) {
    auto pidStat = UidProcStatsCollector::readStatFileForPid(pid);
    if (!pidStat.ok()) {
        return elapsedRealtime();
    }
    return pidStat->startTimeMillis;
}

void onBinderDied(void* cookie) {
    const auto& thiz = ServiceManager::getInstance()->getWatchdogProcessService();
    if (thiz == nullptr) {
        return;
    }
    thiz->handleBinderDeath(cookie);
}
Result<pid_t> queryHidlServiceManagerForVhalPid(const sp<IServiceManager>& hidlServiceManager) {
    pid_t pid = -1;
    Return<void> ret = hidlServiceManager->debugDump([&](auto& hals) {
        for (const auto& info : hals) {
            if (info.pid == static_cast<int>(IServiceManager::PidConstant::NO_PID)) {
                continue;
            }
            if (info.interfaceName == kHidlVhalInterfaceName) {
                pid = info.pid;
                return;
            }
        }
    });

    if (!ret.isOk()) {
        return Error() << "Failed to get VHAL process id from HIDL service manager";
    }
    if (pid == -1) {
        return Error() << "No VHAL service registered to HIDL service manager";
    }
    return pid;
}

int toProtoHealthCheckTimeout(TimeoutLength timeoutLength) {
    switch (timeoutLength) {
        case TimeoutLength::TIMEOUT_CRITICAL:
            return HealthCheckClientInfo::CRITICAL;
        case TimeoutLength::TIMEOUT_MODERATE:
            return HealthCheckClientInfo::MODERATE;
        case TimeoutLength::TIMEOUT_NORMAL:
            return HealthCheckClientInfo::NORMAL;
        default:
            return HealthCheckClientInfo::HEALTH_CHECK_TIMEOUT_UNSPECIFIED;
    }
}

}  // namespace

WatchdogProcessService::WatchdogProcessService(const sp<Looper>& handlerLooper) :
      WatchdogProcessService(IVhalClient::tryCreate, kDefaultTryGetHidlServiceManager,
                             getStartTimeForPid, kDefaultVhalPidCachingRetryDelayNs, handlerLooper,
                             sp<AIBinderDeathRegistrationWrapper>::make()) {}

WatchdogProcessService::WatchdogProcessService(
        const std::function<std::shared_ptr<IVhalClient>()>& tryCreateVhalClientFunc,
        const std::function<sp<IServiceManager>()>& tryGetHidlServiceManagerFunc,
        const std::function<int64_t(pid_t)>& getStartTimeForPidFunc,
        const std::chrono::nanoseconds& vhalPidCachingRetryDelayNs, const sp<Looper>& handlerLooper,
        const sp<AIBinderDeathRegistrationWrapperInterface>& deathRegistrationWrapper) :
      kTryCreateVhalClientFunc(tryCreateVhalClientFunc),
      kTryGetHidlServiceManagerFunc(tryGetHidlServiceManagerFunc),
      kGetStartTimeForPidFunc(getStartTimeForPidFunc),
      kVhalPidCachingRetryDelayNs(vhalPidCachingRetryDelayNs),
      mHandlerLooper(handlerLooper),
      mClientBinderDeathRecipient(
              ScopedAIBinder_DeathRecipient(AIBinder_DeathRecipient_new(onBinderDied))),
      mLastSessionId(0),
      mServiceStarted(false),
      mDeathRegistrationWrapper(deathRegistrationWrapper),
      mIsEnabled(true),
      mVhalService(nullptr),
      mTotalVhalPidCachingAttempts(0) {
    mVhalBinderDiedCallback =
            std::make_shared<IVhalClient::OnBinderDiedCallbackFunc>([this] { handleVhalDeath(); });
    for (const auto& timeout : kTimeouts) {
        mClientsByTimeout.insert(std::make_pair(timeout, ClientInfoMap()));
        mPingedClients.insert(std::make_pair(timeout, PingedClientMap()));
    }

    int32_t vhalHealthCheckIntervalSec =
            GetIntProperty(kPropertyVhalCheckInterval, kDefaultVhalCheckIntervalSec);
    vhalHealthCheckIntervalSec = std::max(vhalHealthCheckIntervalSec, kDefaultVhalCheckIntervalSec);
    mVhalHealthCheckWindowMillis = std::chrono::seconds(vhalHealthCheckIntervalSec);

    int32_t clientHealthCheckIntervalSec =
            GetIntProperty(kPropertyClientCheckInterval, kMissingIntPropertyValue);
    // Overridden timeout value must be greater than or equal to the maximum possible timeout value.
    // Otherwise, clients will be pinged more frequently than the guaranteed timeout duration.
    if (clientHealthCheckIntervalSec != kMissingIntPropertyValue) {
        int32_t normalSec = std::chrono::duration_cast<std::chrono::seconds>(
                                    getTimeoutDurationNs(TimeoutLength::TIMEOUT_NORMAL))
                                    .count();
        mOverriddenClientHealthCheckWindowNs = std::optional<std::chrono::seconds>{
                std::max(clientHealthCheckIntervalSec, normalSec)};
    }
}

WatchdogProcessService::~WatchdogProcessService() {
    terminate();
}

ScopedAStatus WatchdogProcessService::registerClient(
        const std::shared_ptr<ICarWatchdogClient>& client, TimeoutLength timeout) {
    if (client == nullptr) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Must provide non-null client");
    }
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    userid_t callingUserId = multiuser_get_user_id(callingUid);

    ClientInfo clientInfo(client, callingPid, callingUserId, kGetStartTimeForPidFunc(callingPid),
                          *this);
    return toScopedAStatus(registerClient(clientInfo, timeout));
}

ScopedAStatus WatchdogProcessService::unregisterClient(
        const std::shared_ptr<ICarWatchdogClient>& client) {
    if (client == nullptr) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Must provide non-null client");
    }
    Mutex::Autolock lock(mMutex);
    return unregisterClientLocked(kTimeouts, client->asBinder(), ClientType::Regular);
}

ScopedAStatus WatchdogProcessService::registerCarWatchdogService(
        const SpAIBinder& binder, const sp<WatchdogServiceHelperInterface>& helper) {
    pid_t callingPid = IPCThreadState::self()->getCallingPid();
    uid_t callingUid = IPCThreadState::self()->getCallingUid();
    userid_t callingUserId = multiuser_get_user_id(callingUid);

    if (helper == nullptr) {
        return ScopedAStatus::
                fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                             "Watchdog service helper instance is null");
    }
    ClientInfo clientInfo(helper, binder, callingPid, callingUserId,
                          kGetStartTimeForPidFunc(callingPid), *this);
    if (auto result = registerClient(clientInfo, kCarWatchdogServiceTimeoutDelay); !result.ok()) {
        return toScopedAStatus(result);
    }
    Mutex::Autolock lock(mMutex);
    if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0 &&
        mVhalService != nullptr && mVhalService->isAidlVhal() &&
        !mVhalProcessIdentifier.has_value()) {
        // When CarService is restarted in the middle handling the AIDL VHAL pid fetch request,
        // the request will fail. Restart the caching process only when the AIDL VHAL pid is
        // missing.
        mTotalVhalPidCachingAttempts = 0;
        mHandlerLooper->sendMessage(mMessageHandler, Message(MSG_CACHE_VHAL_PROCESS_IDENTIFIER));
    }
    return ScopedAStatus::ok();
}

void WatchdogProcessService::unregisterCarWatchdogService(const SpAIBinder& binder) {
    Mutex::Autolock lock(mMutex);

    std::vector<TimeoutLength> timeouts = {TimeoutLength::TIMEOUT_CRITICAL};
    unregisterClientLocked(timeouts, binder, ClientType::Service);
}

ScopedAStatus WatchdogProcessService::registerMonitor(
        const std::shared_ptr<ICarWatchdogMonitor>& monitor) {
    if (monitor == nullptr) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Must provide non-null monitor");
    }
    const auto binder = monitor->asBinder();
    {
        Mutex::Autolock lock(mMutex);
        if (mMonitor != nullptr) {
            if (mMonitor->asBinder() == binder) {
                return ScopedAStatus::ok();
            }
            AIBinder* aiBinder = mMonitor->asBinder().get();
            mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mClientBinderDeathRecipient.get(),
                                                     static_cast<void*>(aiBinder));
        }
        mMonitor = monitor;
    }

    AIBinder* aiBinder = binder.get();
    auto status =
            mDeathRegistrationWrapper->linkToDeath(aiBinder, mClientBinderDeathRecipient.get(),
                                                   static_cast<void*>(aiBinder));
    if (!status.isOk()) {
        {
            Mutex::Autolock lock(mMutex);
            if (mMonitor != nullptr && mMonitor->asBinder() == binder) {
                mMonitor.reset();
            }
        }
        ALOGW("Failed to register the monitor as it is dead.");
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                           "The monitor is dead.");
    }
    if (DEBUG) {
        ALOGD("Car watchdog monitor is registered");
    }
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogProcessService::unregisterMonitor(
        const std::shared_ptr<ICarWatchdogMonitor>& monitor) {
    if (monitor == nullptr) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Must provide non-null monitor");
    }
    const auto binder = monitor->asBinder();
    Mutex::Autolock lock(mMutex);
    if (mMonitor == nullptr || mMonitor->asBinder() != binder) {
        ALOGW("Failed to unregister the monitor as it has not been registered.");
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "The monitor has not been registered.");
    }
    AIBinder* aiBinder = binder.get();
    mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mClientBinderDeathRecipient.get(),
                                             static_cast<void*>(aiBinder));
    mMonitor.reset();
    if (DEBUG) {
        ALOGD("Car watchdog monitor is unregistered");
    }
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogProcessService::tellClientAlive(
        const std::shared_ptr<ICarWatchdogClient>& client, int32_t sessionId) {
    if (client == nullptr) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "Must provide non-null client");
    }
    Mutex::Autolock lock(mMutex);
    return tellClientAliveLocked(client->asBinder(), sessionId);
}

ScopedAStatus WatchdogProcessService::tellCarWatchdogServiceAlive(
        const std::shared_ptr<ICarWatchdogServiceForSystem>& service,
        const std::vector<ProcessIdentifier>& clientsNotResponding, int32_t sessionId) {
    if (service == nullptr) {
        return ScopedAStatus::
                fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                             "Must provide non-null car watchdog service");
    }
    ScopedAStatus status;
    {
        Mutex::Autolock lock(mMutex);
        if (DEBUG) {
            if (clientsNotResponding.size() > 0) {
                ALOGD("CarWatchdogService(session: %d) responded with non-responding clients: %s",
                      sessionId, toPidString(clientsNotResponding).c_str());
            }
        }
        status = tellClientAliveLocked(service->asBinder(), sessionId);
    }
    if (status.isOk()) {
        dumpAndKillAllProcesses(clientsNotResponding, /*reportToVhal=*/true);
    }
    return status;
}

ScopedAStatus WatchdogProcessService::tellDumpFinished(
        const std::shared_ptr<ICarWatchdogMonitor>& monitor,
        const ProcessIdentifier& processIdentifier) {
    Mutex::Autolock lock(mMutex);
    if (mMonitor == nullptr || monitor == nullptr || mMonitor->asBinder() != monitor->asBinder()) {
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                           "The monitor is not registered or an "
                                                           "invalid monitor is given");
    }
    ALOGI("Process(pid: %d) has been dumped and killed", processIdentifier.pid);
    return ScopedAStatus::ok();
}

void WatchdogProcessService::setEnabled(bool isEnabled) {
    Mutex::Autolock lock(mMutex);
    if (mIsEnabled == isEnabled) {
        return;
    }
    ALOGI("%s is %s", kServiceName, isEnabled ? "enabled" : "disabled");
    mIsEnabled = isEnabled;
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
    if (!mIsEnabled) {
        return;
    }
    if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0) {
        mVhalHeartBeat.eventTime = uptimeMillis();
        std::chrono::nanoseconds intervalNs =
                mVhalHealthCheckWindowMillis + kHealthCheckDelayMillis;
        mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                           Message(MSG_VHAL_HEALTH_CHECK));
    }
    for (const auto& timeout : kTimeouts) {
        mHandlerLooper->removeMessages(mMessageHandler, static_cast<int>(timeout));
        startHealthCheckingLocked(timeout);
    }
}

void WatchdogProcessService::onUserStateChange(userid_t userId, bool isStarted) {
    std::string buffer;
    Mutex::Autolock lock(mMutex);
    if (isStarted) {
        mStoppedUserIds.erase(userId);
    } else {
        mStoppedUserIds.insert(userId);
    }
}

void WatchdogProcessService::onDump(int fd) {
    Mutex::Autolock lock(mMutex);
    const char* indent = "  ";
    const char* doubleIndent = "    ";
    std::string buffer;
    WriteStringToFd("CAR WATCHDOG PROCESS SERVICE\n", fd);
    WriteStringToFd(StringPrintf("%s%s enabled: %s\n", indent, kServiceName,
                                 mIsEnabled ? "true" : "false"),
                    fd);
    WriteStringToFd(StringPrintf("%sRegistered clients\n", indent), fd);
    int count = 1;
    for (const auto& timeout : kTimeouts) {
        ClientInfoMap& clients = mClientsByTimeout[timeout];
        for (auto it = clients.begin(); it != clients.end(); it++, count++) {
            WriteStringToFd(StringPrintf("%sClient #%d: %s\n", doubleIndent, count,
                                         it->second.toString().c_str()),
                            fd);
        }
    }
    WriteStringToFd(StringPrintf("%sMonitor registered: %s\n", indent,
                                 mMonitor == nullptr ? "false" : "true"),
                    fd);
    WriteStringToFd(StringPrintf("%sisSystemShuttingDown: %s\n", indent,
                                 isSystemShuttingDown() ? "true" : "false"),
                    fd);
    buffer = "none";
    bool first = true;
    for (const auto& userId : mStoppedUserIds) {
        if (first) {
            buffer = StringPrintf("%d", userId);
            first = false;
        } else {
            StringAppendF(&buffer, ", %d", userId);
        }
    }
    WriteStringToFd(StringPrintf("%sStopped users: %s\n", indent, buffer.c_str()), fd);
    if (mVhalService != nullptr &&
        mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0) {
        int64_t systemUptime = uptimeMillis();
        WriteStringToFd(StringPrintf("%sVHAL health check is supported:\n%s\tVHAL health check "
                                     "interval: %lld millis\n%s\tVHAL heartbeat was updated "
                                     "%" PRIi64 " millis ago",
                                     indent, indent, mVhalHealthCheckWindowMillis.count(), indent,
                                     systemUptime - mVhalHeartBeat.eventTime),
                        fd);
        std::string vhalType = mVhalService->isAidlVhal() ? "AIDL" : "HIDL";
        if (mVhalProcessIdentifier.has_value()) {
            WriteStringToFd(StringPrintf("%s%s VHAL process identifier (PID = %d, Start time "
                                         "millis = "
                                         "%" PRIi64 ")",
                                         indent, vhalType.c_str(), mVhalProcessIdentifier->pid,
                                         mVhalProcessIdentifier->startTimeMillis),
                            fd);
        } else if (mTotalVhalPidCachingAttempts < kMaxVhalPidCachingAttempts) {
            WriteStringToFd(StringPrintf("%sStill fetching %s VHAL process identifier. "
                                         "Total attempts made = %d, Remaining attempts = %d",
                                         indent, vhalType.c_str(), mTotalVhalPidCachingAttempts,
                                         kMaxVhalPidCachingAttempts - mTotalVhalPidCachingAttempts),
                            fd);
        } else {
            WriteStringToFd(StringPrintf("%sFailed to fetch %s VHAL process identifier. "
                                         "Cannot terminate VHAL when VHAL becomes unresponsive",
                                         indent, vhalType.c_str()),
                            fd);
        }
    } else if (mVhalService != nullptr) {
        WriteStringToFd(StringPrintf("%sVHAL client is connected but the heartbeat property is not "
                                     "supported",
                                     indent),
                        fd);
    } else {
        WriteStringToFd(StringPrintf("%sVHAL client is not connected", indent), fd);
    }
}

void WatchdogProcessService::onDumpProto(ProtoOutputStream& outProto) {
    Mutex::Autolock lock(mMutex);

    uint64_t healthCheckServiceDumpToken =
            outProto.start(CarWatchdogDaemonDump::HEALTH_CHECK_SERVICE_DUMP);

    outProto.write(HealthCheckServiceDump::IS_ENABLED, mIsEnabled);
    outProto.write(HealthCheckServiceDump::IS_MONITOR_REGISTERED, mMonitor != nullptr);
    outProto.write(HealthCheckServiceDump::IS_SYSTEM_SHUT_DOWN_IN_PROGRESS, isSystemShuttingDown());

    for (const auto& userId : mStoppedUserIds) {
        outProto.write(HealthCheckServiceDump::STOPPED_USERS, static_cast<int>(userId));
    }
    auto criticalHealthCheckWindowMillis =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    getTimeoutDurationNs(TimeoutLength::TIMEOUT_CRITICAL))
                    .count();
    auto moderateHealthCheckWindowMillis =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    getTimeoutDurationNs(TimeoutLength::TIMEOUT_MODERATE))
                    .count();
    auto normalHealthCheckWindowMillis =
            std::chrono::duration_cast<std::chrono::milliseconds>(
                    getTimeoutDurationNs(TimeoutLength::TIMEOUT_NORMAL))
                    .count();
    outProto.write(HealthCheckServiceDump::CRITICAL_HEALTH_CHECK_WINDOW_MILLIS,
                   criticalHealthCheckWindowMillis);
    outProto.write(HealthCheckServiceDump::MODERATE_HEALTH_CHECK_WINDOW_MILLIS,
                   moderateHealthCheckWindowMillis);
    outProto.write(HealthCheckServiceDump::NORMAL_HEALTH_CHECK_WINDOW_MILLIS,
                   normalHealthCheckWindowMillis);

    // Vhal Health Check Info
    uint64_t vHalHealthCheckInfoToken =
            outProto.start(HealthCheckServiceDump::VHAL_HEALTH_CHECK_INFO);
    outProto.write(VhalHealthCheckInfo::IS_ENABLED, mVhalService != nullptr);
    outProto.write(VhalHealthCheckInfo::HEALTH_CHECK_WINDOW_MILLIS,
                   mVhalHealthCheckWindowMillis.count());
    outProto.write(VhalHealthCheckInfo::LAST_HEARTBEAT_UPDATE_AGO_MILLIS,
                   uptimeMillis() - mVhalHeartBeat.eventTime);
    int pidCachingProgressState = VhalHealthCheckInfo::FAILURE;
    if (mVhalProcessIdentifier.has_value()) {
        pidCachingProgressState = VhalHealthCheckInfo::SUCCESS;
    } else if (mTotalVhalPidCachingAttempts < kMaxVhalPidCachingAttempts) {
        pidCachingProgressState = VhalHealthCheckInfo::IN_PROGRESS;
    }
    outProto.write(VhalHealthCheckInfo::PID_CACHING_PROGRESS_STATE, pidCachingProgressState);
    outProto.write(VhalHealthCheckInfo::PID,
                   mVhalProcessIdentifier.has_value() ? mVhalProcessIdentifier->pid : -1);
    outProto.write(VhalHealthCheckInfo::START_TIME_MILLIS,
                   mVhalProcessIdentifier.has_value() ? mVhalProcessIdentifier->startTimeMillis
                                                      : -1);

    outProto.end(vHalHealthCheckInfoToken);

    // Health Check Client Info
    for (const auto& timeout : kTimeouts) {
        const ClientInfoMap& clients = mClientsByTimeout[timeout];
        for (auto it = clients.begin(); it != clients.end(); it++) {
            uint64_t healthCheckClientInfoToken =
                    outProto.start(HealthCheckServiceDump::REGISTERED_CLIENT_INFOS);
            const ClientInfo clientInfo = it->second;
            outProto.write(HealthCheckClientInfo::PID, clientInfo.kPid);

            uint64_t userPackageInfoToken =
                    outProto.start(HealthCheckClientInfo::USER_PACKAGE_INFO);
            outProto.write(UserPackageInfo::USER_ID, static_cast<int>(clientInfo.kUserId));
            outProto.write(UserPackageInfo::PACKAGE_NAME, clientInfo.packageName);
            outProto.end(userPackageInfoToken);

            outProto.write(HealthCheckClientInfo::CLIENT_TYPE, toProtoClientType(clientInfo.kType));
            outProto.write(HealthCheckClientInfo::START_TIME_MILLIS, clientInfo.kStartTimeMillis);
            outProto.write(HealthCheckClientInfo::HEALTH_CHECK_TIMEOUT,
                           toProtoHealthCheckTimeout(timeout));
            outProto.end(healthCheckClientInfoToken);
        }
    }

    outProto.end(healthCheckServiceDumpToken);
}

void WatchdogProcessService::doHealthCheck(int what) {
    mHandlerLooper->removeMessages(mMessageHandler, what);
    if (Mutex::Autolock lock(mMutex); !mIsEnabled) {
        return;
    }
    const TimeoutLength timeout = static_cast<TimeoutLength>(what);
    dumpAndKillClientsIfNotResponding(timeout);

    /* Generates a temporary/local vector containing clients.
     * Using a local copy may send unnecessary ping messages to clients after they are unregistered.
     * Clients should be able to handle them.
     */
    std::vector<ClientInfo> clientsToCheck;
    PingedClientMap* pingedClients = nullptr;
    {
        Mutex::Autolock lock(mMutex);
        pingedClients = &mPingedClients[timeout];
        pingedClients->clear();
        for (auto& [_, clientInfo] : mClientsByTimeout[timeout]) {
            if (mStoppedUserIds.count(clientInfo.kUserId) > 0) {
                continue;
            }
            int sessionId = getNewSessionId();
            clientInfo.sessionId = sessionId;
            clientsToCheck.push_back(clientInfo);
            pingedClients->insert(std::make_pair(sessionId, clientInfo));
        }
    }

    for (const auto& clientInfo : clientsToCheck) {
        if (auto status = clientInfo.checkIfAlive(timeout); !status.isOk()) {
            if (DEBUG) {
                ALOGW("Failed to send a ping message to client(pid: %d): %s", clientInfo.kPid,
                      status.getMessage());
            }
            {
                Mutex::Autolock lock(mMutex);
                pingedClients->erase(clientInfo.sessionId);
            }
        }
    }
    // Though the size of pingedClients is a more specific measure, clientsToCheck is used as a
    // conservative approach.
    if (clientsToCheck.size() > 0) {
        auto durationNs = getTimeoutDurationNs(timeout);
        mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler, Message(what));
    }
}

Result<void> WatchdogProcessService::start() {
    if (mServiceStarted) {
        return Error(INVALID_OPERATION) << "Cannot start process monitoring more than once";
    }
    auto thiz = sp<WatchdogProcessService>::fromExisting(this);
    mMessageHandler = sp<MessageHandlerImpl>::make(thiz);
    mPropertyChangeListener = std::make_shared<PropertyChangeListener>(thiz);
    mServiceStarted = true;
    reportWatchdogAliveToVhal();
    return {};
}

void WatchdogProcessService::terminate() {
    std::unique_ptr<ISubscriptionClient> propertySubscriptionClient;
    {
        Mutex::Autolock lock(mMutex);
        if (!mServiceStarted) {
            return;
        }
        for (auto& [_, clients] : mClientsByTimeout) {
            for (auto& [_, client] : clients) {
                client.unlinkToDeath(mClientBinderDeathRecipient.get());
            }
            clients.clear();
        }
        mClientsByTimeout.clear();
        if (mMonitor != nullptr) {
            AIBinder* aiBinder = mMonitor->asBinder().get();
            mDeathRegistrationWrapper->unlinkToDeath(aiBinder, mClientBinderDeathRecipient.get(),
                                                     static_cast<void*>(aiBinder));
            mMonitor.reset();
        }
        mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
        mServiceStarted = false;
        if (mVhalService == nullptr) {
            return;
        }
        if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) == 0) {
            propertySubscriptionClient =
                    mVhalService->getSubscriptionClient(mPropertyChangeListener);
        }
        mVhalService->removeOnBinderDiedCallback(mVhalBinderDiedCallback);
        resetVhalInfoLocked();
    }
    if (propertySubscriptionClient != nullptr) {
        std::vector<int32_t> propIds = {static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT)};
        auto result = propertySubscriptionClient->unsubscribe(propIds);
        if (!result.ok()) {
            ALOGW("Failed to unsubscribe from VHAL_HEARTBEAT.");
        }
    }
}

Result<void> WatchdogProcessService::registerClient(const ClientInfo& clientInfo,
                                                    TimeoutLength timeout) {
    uintptr_t cookieId = reinterpret_cast<uintptr_t>(clientInfo.getAIBinder());
    {
        Mutex::Autolock lock(mMutex);
        if (findClientAndProcessLocked(kTimeouts, clientInfo.getAIBinder(), nullptr)) {
            return Error(RegistrationError::ERR_DUPLICATE_REGISTRATION)
                    << "Failed to register (" << clientInfo.toString()
                    << ") as it is already registered";
        }

        ClientInfoMap& clients = mClientsByTimeout[timeout];
        clients.insert(std::make_pair(cookieId, clientInfo));
    }
    if (auto status = clientInfo.linkToDeath(mClientBinderDeathRecipient.get()); !status.isOk()) {
        Mutex::Autolock lock(mMutex);
        if (auto it = mClientsByTimeout.find(timeout); it != mClientsByTimeout.end()) {
            if (const auto& clientIt = it->second.find(cookieId); clientIt != it->second.end()) {
                it->second.erase(clientIt);
            }
        }
        return Error(RegistrationError::ERR_ILLEGAL_STATE)
                << "Failed to register (" << clientInfo.toString() << ") as it is dead";
    }
    if (DEBUG) {
        ALOGD("Car watchdog client (%s, timeout = %d) is registered", clientInfo.toString().c_str(),
              timeout);
    }
    Mutex::Autolock lock(mMutex);
    // If the client array becomes non-empty, start health checking.
    if (mClientsByTimeout[timeout].size() == 1) {
        startHealthCheckingLocked(timeout);
        ALOGI("Starting health checking for timeout = %d", timeout);
    }
    uid_t callingUid = IPCThreadState::self()->getCallingUid();

    // Lazy initialization of PackageInfoResolver.
    if (mPackageInfoResolver == nullptr) {
        mPackageInfoResolver = PackageInfoResolver::getInstance();
    }
    mPackageInfoResolver
            ->asyncFetchPackageNamesForUids({callingUid},
                                            [&](std::unordered_map<uid_t, std::string>
                                                        packageNames) {
                                                ClientInfoMap& clients =
                                                        this->mClientsByTimeout[timeout];
                                                auto client = clients.find(cookieId);
                                                // The client could have been unregistered by
                                                // the time that the packageName is updated.
                                                if (client != clients.end()) {
                                                    if (packageNames.find(callingUid) !=
                                                        packageNames.end()) {
                                                        client->second.packageName =
                                                                packageNames[callingUid];
                                                    } else {
                                                        ALOGW("Failed to resolve packageName "
                                                              "for calling uid: %i.",
                                                              callingUid);
                                                    }
                                                }
                                            });
    return {};
}

ScopedAStatus WatchdogProcessService::unregisterClientLocked(
        const std::vector<TimeoutLength>& timeouts, const SpAIBinder& binder,
        ClientType clientType) {
    const char* clientName = clientType == ClientType::Regular ? "client" : "service";
    bool result = findClientAndProcessLocked(timeouts, binder.get(),
                                             [&](ClientInfoMap& clients,
                                                 ClientInfoMap::const_iterator it) {
                                                 it->second.unlinkToDeath(
                                                         mClientBinderDeathRecipient.get());
                                                 clients.erase(it);
                                             });
    if (!result) {
        std::string errorStr =
                StringPrintf("The car watchdog %s has not been registered", clientName);
        const char* errorCause = errorStr.c_str();
        ALOGW("Failed to unregister the car watchdog %s: %s", clientName, errorCause);
        return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT, errorCause);
    }
    if (DEBUG) {
        ALOGD("Car watchdog %s is unregistered", clientName);
    }
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogProcessService::tellClientAliveLocked(const SpAIBinder& binder,
                                                            int32_t sessionId) {
    for (const auto& timeout : kTimeouts) {
        PingedClientMap& clients = mPingedClients[timeout];
        PingedClientMap::const_iterator it = clients.find(sessionId);
        if (it == clients.cend() || it->second.getAIBinder() != binder.get()) {
            continue;
        }
        clients.erase(it);
        return ScopedAStatus::ok();
    }
    return ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_ARGUMENT,
                                                       "The client is not registered or the "
                                                       "session ID is not found");
}

bool WatchdogProcessService::findClientAndProcessLocked(const std::vector<TimeoutLength>& timeouts,
                                                        AIBinder* aiBinder,
                                                        const Processor& processor) {
    return findClientAndProcessLocked(timeouts, reinterpret_cast<uintptr_t>(aiBinder), processor);
}

bool WatchdogProcessService::findClientAndProcessLocked(const std::vector<TimeoutLength>& timeouts,
                                                        uintptr_t binderPtrId,
                                                        const Processor& processor) {
    for (const auto& timeout : timeouts) {
        auto clientsByIdIt = mClientsByTimeout.find(timeout);
        if (clientsByIdIt == mClientsByTimeout.end()) {
            continue;
        }
        auto it = clientsByIdIt->second.find(binderPtrId);
        if (it == clientsByIdIt->second.end()) {
            continue;
        }
        if (processor != nullptr) {
            processor(clientsByIdIt->second, it);
        }
        return true;
    }

    return false;
}

Result<void> WatchdogProcessService::startHealthCheckingLocked(TimeoutLength timeout) {
    PingedClientMap& clients = mPingedClients[timeout];
    clients.clear();
    int what = static_cast<int>(timeout);
    auto durationNs = getTimeoutDurationNs(timeout);
    mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler, Message(what));
    return {};
}

Result<void> WatchdogProcessService::dumpAndKillClientsIfNotResponding(TimeoutLength timeout) {
    std::vector<ProcessIdentifier> processIdentifiers;
    std::vector<const ClientInfo*> clientsToNotify;
    {
        Mutex::Autolock lock(mMutex);
        PingedClientMap& clients = mPingedClients[timeout];
        for (PingedClientMap::const_iterator it = clients.cbegin(); it != clients.cend(); it++) {
            pid_t pid = -1;
            userid_t userId = -1;
            uint64_t startTimeMillis = 0;
            std::vector<TimeoutLength> timeouts = {timeout};
            findClientAndProcessLocked(timeouts, it->second.getAIBinder(),
                                       [&](ClientInfoMap& cachedClients,
                                           ClientInfoMap::const_iterator cachedClientsIt) {
                                           auto clientInfo = cachedClientsIt->second;
                                           pid = clientInfo.kPid;
                                           startTimeMillis = clientInfo.kStartTimeMillis;
                                           userId = clientInfo.kUserId;
                                           clientInfo.unlinkToDeath(
                                                   mClientBinderDeathRecipient.get());
                                           cachedClients.erase(cachedClientsIt);
                                       });
            if (pid != -1 && mStoppedUserIds.count(userId) == 0) {
                clientsToNotify.emplace_back(&it->second);
                ProcessIdentifier processIdentifier;
                processIdentifier.pid = pid;
                processIdentifier.startTimeMillis = startTimeMillis;
                processIdentifiers.push_back(processIdentifier);
            }
        }
    }
    for (const ClientInfo*& clientInfo : clientsToNotify) {
        clientInfo->prepareProcessTermination();
    }
    return dumpAndKillAllProcesses(processIdentifiers, /*reportToVhal=*/true);
}

Result<void> WatchdogProcessService::dumpAndKillAllProcesses(
        const std::vector<ProcessIdentifier>& processesNotResponding, bool reportToVhal) {
    size_t size = processesNotResponding.size();
    if (size == 0) {
        return {};
    }
    std::string pidString = toPidString(processesNotResponding);
    std::shared_ptr<ICarWatchdogMonitor> monitor;
    {
        Mutex::Autolock lock(mMutex);
        if (mMonitor == nullptr) {
            std::string errorMsg =
                    StringPrintf("Failed to dump and kill processes(pid = %s): Monitor is not set",
                                 pidString.c_str());
            ALOGW("%s", errorMsg.c_str());
            return Error() << errorMsg;
        }
        monitor = mMonitor;
    }
    if (isSystemShuttingDown()) {
        ALOGI("Skip dumping and killing processes(%s): The system is shutting down",
              pidString.c_str());
        return {};
    }
    if (reportToVhal) {
        reportTerminatedProcessToVhal(processesNotResponding);
    }
    monitor->onClientsNotResponding(processesNotResponding);
    if (DEBUG) {
        ALOGD("Dumping and killing processes is requested: %s", pidString.c_str());
    }
    return {};
}

// Handle when car watchdog clients die.
void WatchdogProcessService::handleBinderDeath(void* cookie) {
    uintptr_t cookieId = reinterpret_cast<uintptr_t>(cookie);

    // The same binder death recipient is used for both monitor and client deaths. So, check both
    // the monitor and all the clients until a match is found.
    Mutex::Autolock lock(mMutex);
    if (mMonitor != nullptr) {
        if (AIBinder* aiBinder = mMonitor->asBinder().get();
            reinterpret_cast<uintptr_t>(aiBinder) == cookieId) {
            mMonitor.reset();
            ALOGW("The monitor has died.");
            return;
        }
    }

    findClientAndProcessLocked(kTimeouts, cookieId,
                               [&](ClientInfoMap& clients, ClientInfoMap::const_iterator it) {
                                   ALOGW("Client(pid: %d) died", it->second.kPid);
                                   clients.erase(it);
                               });
}

// Handle when VHAL dies.
void WatchdogProcessService::handleVhalDeath() {
    Mutex::Autolock lock(mMutex);
    ALOGW("VHAL has died.");
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_HEALTH_CHECK);
    // Destroying mVHalService would remove all onBinderDied callbacks.
    resetVhalInfoLocked();
}

void WatchdogProcessService::reportWatchdogAliveToVhal() {
    if (mNotSupportedVhalProperties.count(VehicleProperty::WATCHDOG_ALIVE) > 0) {
        ALOGW("VHAL doesn't support WATCHDOG_ALIVE. Car watchdog will not update WATCHDOG_ALIVE.");
        return;
    }
    int64_t systemUptime = uptimeMillis();
    VehiclePropValue propValue{
            .prop = static_cast<int32_t>(VehicleProperty::WATCHDOG_ALIVE),
            .value.int64Values = {systemUptime},
    };
    const auto& ret = updateVhal(propValue);
    if (!ret.ok()) {
        ALOGW("Failed to update WATCHDOG_ALIVE VHAL property. Will try again in 3s, error: %s",
              ret.error().message().c_str());
    }
    // Update VHAL with the interval of TIMEOUT_CRITICAL(3s).
    auto durationNs = getTimeoutDurationNs(TimeoutLength::TIMEOUT_CRITICAL);
    mHandlerLooper->removeMessages(mMessageHandler, MSG_VHAL_WATCHDOG_ALIVE);
    mHandlerLooper->sendMessageDelayed(durationNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_WATCHDOG_ALIVE));
}

void WatchdogProcessService::reportTerminatedProcessToVhal(
        const std::vector<ProcessIdentifier>& processesNotResponding) {
    if (mNotSupportedVhalProperties.count(VehicleProperty::WATCHDOG_TERMINATED_PROCESS) > 0) {
        ALOGW("VHAL doesn't support WATCHDOG_TERMINATED_PROCESS. Terminated process is not "
              "reported to VHAL.");
        return;
    }
    for (auto&& processIdentifier : processesNotResponding) {
        const auto& retCmdLine = readProcCmdLine(processIdentifier.pid);
        if (!retCmdLine.ok()) {
            ALOGW("Failed to get process command line for pid(%d): %s", processIdentifier.pid,
                  retCmdLine.error().message().c_str());
            continue;
        }
        std::string procCmdLine = retCmdLine.value();
        VehiclePropValue propValue{
                .prop = static_cast<int32_t>(VehicleProperty::WATCHDOG_TERMINATED_PROCESS),
                .value.int32Values = {static_cast<int32_t>(
                        ProcessTerminationReason::NOT_RESPONDING)},
                .value.stringValue = procCmdLine,
        };
        const auto& retUpdate = updateVhal(propValue);
        if (!retUpdate.ok()) {
            ALOGW("Failed to update WATCHDOG_TERMINATED_PROCESS VHAL property(command line: %s)",
                  procCmdLine.c_str());
        }
    }
}

Result<void> WatchdogProcessService::updateVhal(const VehiclePropValue& value) {
    const auto& connectRet = connectToVhal();
    if (!connectRet.ok()) {
        std::string errorMsg = "VHAL is not connected: " + connectRet.error().message();
        ALOGW("%s", errorMsg.c_str());
        return Error() << errorMsg;
    }
    int32_t propId = value.prop;
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        if (mNotSupportedVhalProperties.count(static_cast<VehicleProperty>(propId)) > 0) {
            std::string errorMsg = StringPrintf("VHAL doesn't support property(id: %d)", propId);
            ALOGW("%s", errorMsg.c_str());
            return Error() << errorMsg;
        }
        vhalService = mVhalService;
    }

    auto halPropValue = vhalService->createHalPropValue(propId);
    halPropValue->setInt32Values(value.value.int32Values);
    halPropValue->setInt64Values(value.value.int64Values);
    halPropValue->setStringValue(value.value.stringValue);
    if (auto result = vhalService->setValueSync(*halPropValue); !result.ok()) {
        return Error() << "Failed to set propValue(" << propId
                       << ") to VHAL, error: " << result.error().message();
    }

    return {};
}

Result<std::string> WatchdogProcessService::readProcCmdLine(int32_t pid) {
    std::string cmdLinePath = StringPrintf("/proc/%d/cmdline", pid);
    std::string procCmdLine;
    if (ReadFileToString(cmdLinePath, &procCmdLine)) {
        std::replace(procCmdLine.begin(), procCmdLine.end(), '\0', ' ');
        procCmdLine = Trim(procCmdLine);
        return procCmdLine;
    }
    return Error() << "Failed to read " << cmdLinePath;
}

Result<void> WatchdogProcessService::connectToVhal() {
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService != nullptr) {
            return {};
        }
        mVhalService = kTryCreateVhalClientFunc();
        if (mVhalService == nullptr) {
            return Error() << "Failed to connect to VHAL.";
        }
        mVhalService->addOnBinderDiedCallback(mVhalBinderDiedCallback);
    }
    queryVhalProperties();
    subscribeToVhalHeartBeat();
    ALOGI("Successfully connected to VHAL.");
    return {};
}

void WatchdogProcessService::queryVhalProperties() {
    std::shared_ptr<IVhalClient> vhalService;
    {
        Mutex::Autolock lock(mMutex);
        vhalService = mVhalService;
    }
    std::unordered_set<VehicleProperty> notSupportedProperties;
    std::vector<VehicleProperty> propIds = {VehicleProperty::WATCHDOG_ALIVE,
                                            VehicleProperty::WATCHDOG_TERMINATED_PROCESS,
                                            VehicleProperty::VHAL_HEARTBEAT};
    for (const auto& propId : propIds) {
        if (auto result = vhalService->getPropConfigs({static_cast<int32_t>(propId)});
            !result.ok()) {
            notSupportedProperties.insert(propId);
        }
    }
    {
        Mutex::Autolock lock(mMutex);
        mNotSupportedVhalProperties = std::move(notSupportedProperties);
    }
}

void WatchdogProcessService::subscribeToVhalHeartBeat() {
    std::unique_ptr<ISubscriptionClient> propertySubscriptionClient;
    {
        Mutex::Autolock lock(mMutex);
        if (mNotSupportedVhalProperties.count(VehicleProperty::VHAL_HEARTBEAT) > 0) {
            ALOGW("VHAL doesn't support VHAL_HEARTBEAT. Checking VHAL health is disabled.");
            return;
        }

        mVhalHeartBeat = {
                .eventTime = 0,
                .value = 0,
        };
        propertySubscriptionClient = mVhalService->getSubscriptionClient(mPropertyChangeListener);
    }
    std::vector<SubscribeOptions> options = {
            {.propId = static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT), .areaIds = {}},
    };
    if (auto result = propertySubscriptionClient->subscribe(options); !result.ok()) {
        ALOGW("Failed to subscribe to VHAL_HEARTBEAT. Checking VHAL health is disabled. '%s'",
              result.error().message().c_str());
        return;
    }
    std::chrono::nanoseconds intervalNs = mVhalHealthCheckWindowMillis + kHealthCheckDelayMillis;
    mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_HEALTH_CHECK));
    // VHAL process identifier is required only when terminating the VHAL process. VHAL process is
    // terminated only when the VHAL is unhealthy. However, caching the process identifier as soon
    // as connecting to VHAL guarantees the correct PID is cached. Because the VHAL pid is queried
    // from the service manager, the caching should be performed outside the class level lock. So,
    // handle the caching in the handler thread after successfully subscribing to the VHAL_HEARTBEAT
    // property.
    mHandlerLooper->sendMessage(mMessageHandler, Message(MSG_CACHE_VHAL_PROCESS_IDENTIFIER));
    return;
}

const sp<WatchdogServiceHelperInterface> WatchdogProcessService::getWatchdogServiceHelperLocked() {
    ClientInfoMap& clients = mClientsByTimeout[kCarWatchdogServiceTimeoutDelay];
    for (const auto& [_, clientInfo] : clients) {
        if (clientInfo.kType == ClientType::Service) {
            return clientInfo.kWatchdogServiceHelper;
        }
    }
    return nullptr;
}

void WatchdogProcessService::cacheVhalProcessIdentifier() {
    // Ensure only one MSG_CACHE_VHAL_PROCESS_IDENTIFIER is present on the looper at any given time.
    // Duplicate messages could be posted when the CarService restarts during the caching attempts.
    // When duplicate messages are present, the following retry delay won't have any effect.
    mHandlerLooper->removeMessages(mMessageHandler, MSG_CACHE_VHAL_PROCESS_IDENTIFIER);
    bool isAidlVhal;
    sp<WatchdogServiceHelperInterface> serviceHelper;
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr || mVhalProcessIdentifier.has_value()) {
            return;
        }
        isAidlVhal = mVhalService->isAidlVhal();
        serviceHelper = getWatchdogServiceHelperLocked();
        // WatchdogServiceHelper is available only when the CarWatchdogService
        // is connected. So, if the WatchdogServiceHelper is not available,
        // postpone requesting the AIDL VHAL process identifier from
        // CarWatchdogService until the daemon is connected with the service.
        if (isAidlVhal && serviceHelper == nullptr) {
            if (DEBUG) {
                ALOGE("Skipping requesting AIDL VHAL pid from CarWatchdogService until the service "
                      "is connected");
            }
            return;
        }
        if (mTotalVhalPidCachingAttempts >= kMaxVhalPidCachingAttempts) {
            ALOGE("Failed to cache VHAL process identifier. Total attempts made to cache: %d",
                  mTotalVhalPidCachingAttempts);
            return;
        }
        mTotalVhalPidCachingAttempts++;
    }
    const auto retryCaching = [&](const std::string& logMessage) {
        ALOGW("%s. Retrying caching VHAL pid in %lld ms", logMessage.c_str(),
              kVhalPidCachingRetryDelayNs.count() / (1'000'000));
        mHandlerLooper->sendMessageDelayed(kVhalPidCachingRetryDelayNs.count(), mMessageHandler,
                                           Message(MSG_CACHE_VHAL_PROCESS_IDENTIFIER));
    };
    if (isAidlVhal) {
        if (const auto status = serviceHelper->requestAidlVhalPid(); !status.isOk()) {
            retryCaching(StringPrintf("Failed to request AIDL VHAL pid from CarWatchdogService: %s",
                                      status.getMessage()));
            return;
        }
        // CarWatchdogService responds with the PID via an asynchronous callback. When
        // CarWatchdogService cannot respond with the PID, the daemon must retry caching the PID but
        // this needs to happen asynchronously. So, post a retry message to ensure that the AIDL
        // VHAL PID is returned by the CarWatchdogService within the retry timeout.
        retryCaching("Requested AIDL VHAL pid from CarWatchdogService");
        return;
    }
    Result<pid_t> result;
    sp<IServiceManager> hidlServiceManager = kTryGetHidlServiceManagerFunc();
    if (hidlServiceManager == nullptr) {
        retryCaching("Failed to get HIDL service manager");
        return;
    }
    if (result = queryHidlServiceManagerForVhalPid(hidlServiceManager); !result.ok()) {
        retryCaching(result.error().message());
        return;
    }
    cacheVhalProcessIdentifierForPid(*result);
}

void WatchdogProcessService::onAidlVhalPidFetched(pid_t pid) {
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr || !mVhalService->isAidlVhal()) {
            return;
        }
    }
    cacheVhalProcessIdentifierForPid(pid);
}

void WatchdogProcessService::cacheVhalProcessIdentifierForPid(int32_t pid) {
    if (pid < 0) {
        ALOGE("Ignoring request to cache invalid VHAL pid (%d)", pid);
        return;
    }
    ProcessIdentifier processIdentifier;
    processIdentifier.pid = pid;
    processIdentifier.startTimeMillis = kGetStartTimeForPidFunc(pid);

    Mutex::Autolock lock(mMutex);
    mVhalProcessIdentifier = processIdentifier;
    mHandlerLooper->removeMessages(mMessageHandler, MSG_CACHE_VHAL_PROCESS_IDENTIFIER);
}

int32_t WatchdogProcessService::getNewSessionId() {
    // Make sure that session id is always positive number.
    if (++mLastSessionId <= 0) {
        mLastSessionId = 1;
    }
    return mLastSessionId;
}

void WatchdogProcessService::updateVhalHeartBeat(int64_t value) {
    bool wrongHeartBeat;
    {
        Mutex::Autolock lock(mMutex);
        if (!mIsEnabled) {
            return;
        }
        wrongHeartBeat = value <= mVhalHeartBeat.value;
        mVhalHeartBeat.eventTime = uptimeMillis();
        mVhalHeartBeat.value = value;
    }
    if (wrongHeartBeat) {
        ALOGW("VHAL updated heart beat with a wrong value. Terminating VHAL...");
        terminateVhal();
        return;
    }
    std::chrono::nanoseconds intervalNs = mVhalHealthCheckWindowMillis + kHealthCheckDelayMillis;
    mHandlerLooper->sendMessageDelayed(intervalNs.count(), mMessageHandler,
                                       Message(MSG_VHAL_HEALTH_CHECK));
}

void WatchdogProcessService::checkVhalHealth() {
    int64_t lastEventTime;
    int64_t currentUptime = uptimeMillis();
    {
        Mutex::Autolock lock(mMutex);
        if (mVhalService == nullptr || !mIsEnabled) {
            return;
        }
        lastEventTime = mVhalHeartBeat.eventTime;
    }
    if (currentUptime > lastEventTime + mVhalHealthCheckWindowMillis.count()) {
        ALOGW("VHAL failed to update heart beat within timeout. Terminating VHAL...");
        terminateVhal();
    }
}

void WatchdogProcessService::resetVhalInfoLocked() {
    mVhalService.reset();
    mVhalProcessIdentifier.reset();
    mTotalVhalPidCachingAttempts = 0;
    // Stop any pending caching attempts when the VHAL info is reset.
    mHandlerLooper->removeMessages(mMessageHandler, MSG_CACHE_VHAL_PROCESS_IDENTIFIER);
}

void WatchdogProcessService::terminateVhal() {
    std::optional<ProcessIdentifier> processIdentifier;
    {
        Mutex::Autolock lock(mMutex);
        processIdentifier = mVhalProcessIdentifier;
        resetVhalInfoLocked();
        if (!processIdentifier.has_value()) {
            ALOGE("Failed to terminate VHAL: failed to fetch VHAL PID");
            return;
        }
    }
    dumpAndKillAllProcesses(std::vector<ProcessIdentifier>(1, *processIdentifier),
                            /*reportToVhal=*/false);
}

std::chrono::nanoseconds WatchdogProcessService::getTimeoutDurationNs(
        const TimeoutLength& timeout) {
    // When a default timeout has been overridden by the |kPropertyClientCheckInterval| read-only
    // property override the timeout value for all timeout lengths.
    if (mOverriddenClientHealthCheckWindowNs.has_value()) {
        return mOverriddenClientHealthCheckWindowNs.value();
    }
    switch (timeout) {
        case TimeoutLength::TIMEOUT_CRITICAL:
            return 3s;  // 3s and no buffer time.
        case TimeoutLength::TIMEOUT_MODERATE:
            return 6s;  // 5s + 1s as buffer time.
        case TimeoutLength::TIMEOUT_NORMAL:
            return 12s;  // 10s + 2s as buffer time.
    }
}

std::string WatchdogProcessService::ClientInfo::toString() const {
    std::string buffer;
    StringAppendF(&buffer, "pid = %d, userId = %d, type = %s", kPid, kUserId,
                  kType == ClientType::Regular ? "regular" : "watchdog service");
    return buffer;
}

AIBinder* WatchdogProcessService::ClientInfo::getAIBinder() const {
    if (kType == ClientType::Regular) {
        return kClient->asBinder().get();
    }
    return kWatchdogServiceBinder.get();
}

ScopedAStatus WatchdogProcessService::ClientInfo::linkToDeath(
        AIBinder_DeathRecipient* recipient) const {
    if (kType == ClientType::Regular) {
        AIBinder* aiBinder = getAIBinder();
        return kService.mDeathRegistrationWrapper->linkToDeath(aiBinder, recipient,
                                                               static_cast<void*>(aiBinder));
    }
    // WatchdogServiceHelper is the binder death recipient for watchdog service, ergo
    // skip this step.
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogProcessService::ClientInfo::unlinkToDeath(
        AIBinder_DeathRecipient* recipient) const {
    if (kType == ClientType::Regular) {
        AIBinder* aiBinder = getAIBinder();
        return kService.mDeathRegistrationWrapper->unlinkToDeath(aiBinder, recipient,
                                                                 static_cast<void*>(aiBinder));
    }
    // WatchdogServiceHelper is the binder death recipient for watchdog service, ergo
    // skip this step.
    return ScopedAStatus::ok();
}

ScopedAStatus WatchdogProcessService::ClientInfo::checkIfAlive(TimeoutLength timeout) const {
    if (kType == ClientType::Regular) {
        return kClient->checkIfAlive(sessionId, timeout);
    }
    return kWatchdogServiceHelper->checkIfAlive(kWatchdogServiceBinder, sessionId, timeout);
}

ScopedAStatus WatchdogProcessService::ClientInfo::prepareProcessTermination() const {
    if (kType == ClientType::Regular) {
        return kClient->prepareProcessTermination();
    }
    return kWatchdogServiceHelper->prepareProcessTermination(kWatchdogServiceBinder);
}

void WatchdogProcessService::PropertyChangeListener::onPropertyEvent(
        const std::vector<std::unique_ptr<IHalPropValue>>& propValues) {
    for (const auto& value : propValues) {
        if (value->getPropId() == static_cast<int32_t>(VehicleProperty::VHAL_HEARTBEAT)) {
            if (value->getInt64Values().size() < 1) {
                ALOGE("Invalid VHAL_HEARTBEAT value, empty value");
            } else {
                kService->updateVhalHeartBeat(value->getInt64Values()[0]);
            }
            break;
        }
    }
}

void WatchdogProcessService::PropertyChangeListener::onPropertySetError(
        const std::vector<HalPropError>& errors) {
    for (const auto& error : errors) {
        if (error.propId != static_cast<int32_t>(VehicleProperty::WATCHDOG_ALIVE) &&
            error.propId != static_cast<int32_t>(VehicleProperty::WATCHDOG_TERMINATED_PROCESS)) {
            continue;
        }
        ALOGE("failed to set VHAL property, prop ID: %d, status: %d", error.propId,
              static_cast<int32_t>(error.status));
    }
}

int WatchdogProcessService::toProtoClientType(ClientType clientType) {
    switch (clientType) {
        case ClientType::Regular:
            return HealthCheckClientInfo::REGULAR;
        case ClientType::Service:
            return HealthCheckClientInfo::CAR_WATCHDOG_SERVICE;
        default:
            return HealthCheckClientInfo::CLIENT_TYPE_UNSPECIFIED;
    }
}

void WatchdogProcessService::MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case static_cast<int>(TimeoutLength::TIMEOUT_CRITICAL):
        case static_cast<int>(TimeoutLength::TIMEOUT_MODERATE):
        case static_cast<int>(TimeoutLength::TIMEOUT_NORMAL):
            kService->doHealthCheck(message.what);
            break;
        case MSG_VHAL_WATCHDOG_ALIVE:
            kService->reportWatchdogAliveToVhal();
            break;
        case MSG_VHAL_HEALTH_CHECK:
            kService->checkVhalHealth();
            break;
        case MSG_CACHE_VHAL_PROCESS_IDENTIFIER:
            kService->cacheVhalProcessIdentifier();
            break;
        default:
            ALOGW("Unknown message: %d", message.what);
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

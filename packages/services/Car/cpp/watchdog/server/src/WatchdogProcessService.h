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

#ifndef CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_
#define CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_

#include "AIBinderDeathRegistrationWrapper.h"

#include <aidl/android/automotive/watchdog/ICarWatchdogClient.h>
#include <aidl/android/automotive/watchdog/TimeoutLength.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogMonitor.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <aidl/android/automotive/watchdog/internal/ProcessIdentifier.h>
#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <android/binder_auto_utils.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <android/util/ProtoOutputStream.h>
#include <cutils/multiuser.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <IVhalClient.h>
#include <VehicleHalTypes.h>

#include <optional>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

// Forward declaration for testing use only.
namespace internal {

class WatchdogProcessServicePeer;

}  // namespace internal

class WatchdogServiceHelperInterface;
class PackageInfoResolverInterface;

class WatchdogProcessServiceInterface : virtual public android::RefBase {
public:
    virtual android::base::Result<void> start() = 0;
    virtual void terminate() = 0;
    virtual void onDump(int fd) = 0;
    virtual void onDumpProto(android::util::ProtoOutputStream& outProto) = 0;
    virtual void doHealthCheck(int what) = 0;
    virtual void handleBinderDeath(void* cookie) = 0;
    virtual ndk::ScopedAStatus registerClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            aidl::android::automotive::watchdog::TimeoutLength timeout) = 0;
    virtual ndk::ScopedAStatus unregisterClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&
                    client) = 0;
    virtual ndk::ScopedAStatus registerCarWatchdogService(
            const ndk::SpAIBinder& binder,
            const android::sp<WatchdogServiceHelperInterface>& helper) = 0;
    virtual void unregisterCarWatchdogService(const ndk::SpAIBinder& binder) = 0;
    virtual ndk::ScopedAStatus registerMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor) = 0;
    virtual ndk::ScopedAStatus unregisterMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&
                    monitor) = 0;
    virtual ndk::ScopedAStatus tellClientAlive(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            int32_t sessionId) = 0;
    virtual ndk::ScopedAStatus tellCarWatchdogServiceAlive(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service,
            const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&
                    clientsNotResponding,
            int32_t sessionId) = 0;
    virtual ndk::ScopedAStatus tellDumpFinished(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor,
            const aidl::android::automotive::watchdog::internal::ProcessIdentifier&
                    processIdentifier) = 0;
    virtual void setEnabled(bool isEnabled) = 0;
    virtual void onUserStateChange(userid_t userId, bool isStarted) = 0;
    virtual void onAidlVhalPidFetched(int32_t) = 0;
};

class WatchdogProcessService final : public WatchdogProcessServiceInterface {
public:
    explicit WatchdogProcessService(const android::sp<Looper>& handlerLooper);
    WatchdogProcessService(
            const std::function<std::shared_ptr<
                    android::frameworks::automotive::vhal::IVhalClient>()>& tryCreateVhalClientFunc,
            const std::function<android::sp<android::hidl::manager::V1_0::IServiceManager>()>&
                    tryGetHidlServiceManagerFunc,
            const std::function<int64_t(pid_t)>& getStartTimeForPidFunc,
            const std::chrono::nanoseconds& vhalPidCachingRetryDelayNs,
            const sp<Looper>& handlerLooper,
            const sp<AIBinderDeathRegistrationWrapperInterface>& deathRegistrationWrapper);
    ~WatchdogProcessService();

    android::base::Result<void> start() override;
    void terminate() override;
    void onDump(int fd) override;
    void onDumpProto(util::ProtoOutputStream& outProto) override;
    void doHealthCheck(int what) override;
    void handleBinderDeath(void* cookie) override;
    ndk::ScopedAStatus registerClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            aidl::android::automotive::watchdog::TimeoutLength timeout) override;
    ndk::ScopedAStatus unregisterClient(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client)
            override;
    ndk::ScopedAStatus registerCarWatchdogService(
            const ndk::SpAIBinder& binder,
            const android::sp<WatchdogServiceHelperInterface>& helper) override;
    void unregisterCarWatchdogService(const ndk::SpAIBinder& binder) override;
    ndk::ScopedAStatus registerMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor)
            override;
    ndk::ScopedAStatus unregisterMonitor(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor)
            override;
    ndk::ScopedAStatus tellClientAlive(
            const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>& client,
            int32_t sessionId) override;
    ndk::ScopedAStatus tellCarWatchdogServiceAlive(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&
                    service,
            const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&
                    clientsNotResponding,
            int32_t sessionId) override;
    ndk::ScopedAStatus tellDumpFinished(
            const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>& monitor,
            const aidl::android::automotive::watchdog::internal::ProcessIdentifier&
                    processIdentifier) override;
    void setEnabled(bool isEnabled) override;
    void onUserStateChange(userid_t userId, bool isStarted) override;
    void onAidlVhalPidFetched(int32_t) override;

private:
    enum ClientType {
        Regular,
        Service,
    };

    class ClientInfo {
    public:
        ClientInfo(const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&
                           client,
                   pid_t pid, userid_t userId, uint64_t startTimeMillis,
                   const WatchdogProcessService& service) :
              kPid(pid),
              kUserId(userId),
              kStartTimeMillis(startTimeMillis),
              kType(ClientType::Regular),
              kService(service),
              kClient(client) {}
        ClientInfo(const android::sp<WatchdogServiceHelperInterface>& helper,
                   const ndk::SpAIBinder& binder, pid_t pid, userid_t userId,
                   uint64_t startTimeMillis, const WatchdogProcessService& service) :
              kPid(pid),
              kUserId(userId),
              kStartTimeMillis(startTimeMillis),
              kType(ClientType::Service),
              kService(service),
              kWatchdogServiceHelper(helper),
              kWatchdogServiceBinder(binder) {}

        std::string toString() const;
        AIBinder* getAIBinder() const;
        ndk::ScopedAStatus linkToDeath(AIBinder_DeathRecipient* recipient) const;
        ndk::ScopedAStatus unlinkToDeath(AIBinder_DeathRecipient* recipient) const;
        ndk::ScopedAStatus checkIfAlive(
                aidl::android::automotive::watchdog::TimeoutLength timeout) const;
        ndk::ScopedAStatus prepareProcessTermination() const;

        const pid_t kPid;
        const userid_t kUserId;
        const int64_t kStartTimeMillis;
        const ClientType kType;
        const WatchdogProcessService& kService;
        const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient> kClient;
        const android::sp<WatchdogServiceHelperInterface> kWatchdogServiceHelper;
        const ndk::SpAIBinder kWatchdogServiceBinder;

        int sessionId;
        std::string packageName;
    };

    struct HeartBeat {
        int64_t eventTime;
        int64_t value;
    };

    typedef std::unordered_map<int, ClientInfo> PingedClientMap;

    class PropertyChangeListener final :
          public android::frameworks::automotive::vhal::ISubscriptionCallback {
    public:
        explicit PropertyChangeListener(const android::sp<WatchdogProcessService>& service) :
              kService(service) {}

        void onPropertyEvent(const std::vector<
                             std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>>&
                                     values) override;

        void onPropertySetError(
                const std::vector<android::frameworks::automotive::vhal::HalPropError>& errors)
                override;

    private:
        const android::sp<WatchdogProcessService> kService;
    };

    class MessageHandlerImpl final : public MessageHandler {
    public:
        explicit MessageHandlerImpl(const android::sp<WatchdogProcessService>& service) :
              kService(service) {}

        void handleMessage(const Message& message) override;

    private:
        const android::sp<WatchdogProcessService> kService;
    };

private:
    android::base::Result<void> registerClient(
            const ClientInfo& clientInfo,
            aidl::android::automotive::watchdog::TimeoutLength timeout);
    ndk::ScopedAStatus unregisterClientLocked(
            const std::vector<aidl::android::automotive::watchdog::TimeoutLength>& timeouts,
            const ndk::SpAIBinder& binder, ClientType clientType);
    ndk::ScopedAStatus tellClientAliveLocked(const ndk::SpAIBinder& binder, int32_t sessionId);
    android::base::Result<void> startHealthCheckingLocked(
            aidl::android::automotive::watchdog::TimeoutLength timeout);
    android::base::Result<void> dumpAndKillClientsIfNotResponding(
            aidl::android::automotive::watchdog::TimeoutLength timeout);
    android::base::Result<void> dumpAndKillAllProcesses(
            const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&
                    processesNotResponding,
            bool reportToVhal);
    int32_t getNewSessionId();
    android::base::Result<void> updateVhal(
            const aidl::android::hardware::automotive::vehicle::VehiclePropValue& value);
    android::base::Result<void> connectToVhal();
    void subscribeToVhalHeartBeat();
    const sp<WatchdogServiceHelperInterface> getWatchdogServiceHelperLocked();
    void cacheVhalProcessIdentifier();
    void cacheVhalProcessIdentifierForPid(int32_t pid);
    android::base::Result<void> requestAidlVhalPid();
    void reportWatchdogAliveToVhal();
    void reportTerminatedProcessToVhal(
            const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&
                    processesNotResponding);
    android::base::Result<std::string> readProcCmdLine(int32_t pid);
    void handleVhalDeath();
    void queryVhalProperties();
    void updateVhalHeartBeat(int64_t value);
    void checkVhalHealth();
    void resetVhalInfoLocked();
    void terminateVhal();

    using ClientInfoMap = std::unordered_map<uintptr_t, ClientInfo>;
    using Processor = std::function<void(ClientInfoMap&, ClientInfoMap::const_iterator)>;
    bool findClientAndProcessLocked(
            const std::vector<aidl::android::automotive::watchdog::TimeoutLength>& timeouts,
            AIBinder* binder, const Processor& processor);
    bool findClientAndProcessLocked(
            const std::vector<aidl::android::automotive::watchdog::TimeoutLength>& timeouts,
            uintptr_t binderPtrId, const Processor& processor);
    std::chrono::nanoseconds getTimeoutDurationNs(
            const aidl::android::automotive::watchdog::TimeoutLength& timeout);
    static int toProtoClientType(ClientType clientType);

private:
    const std::function<std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient>()>
            kTryCreateVhalClientFunc;
    const std::function<android::sp<android::hidl::manager::V1_0::IServiceManager>()>
            kTryGetHidlServiceManagerFunc;
    const std::function<int64_t(pid_t)> kGetStartTimeForPidFunc;
    const std::chrono::nanoseconds kVhalPidCachingRetryDelayNs;

    android::sp<Looper> mHandlerLooper;
    android::sp<MessageHandlerImpl> mMessageHandler;
    ndk::ScopedAIBinder_DeathRecipient mClientBinderDeathRecipient;
    std::unordered_set<aidl::android::hardware::automotive::vehicle::VehicleProperty>
            mNotSupportedVhalProperties;
    std::shared_ptr<PropertyChangeListener> mPropertyChangeListener;
    // mLastSessionId is accessed only within main thread. No need for mutual-exclusion.
    int32_t mLastSessionId;
    bool mServiceStarted;
    std::chrono::milliseconds mVhalHealthCheckWindowMillis;
    std::optional<std::chrono::nanoseconds> mOverriddenClientHealthCheckWindowNs;
    std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient::OnBinderDiedCallbackFunc>
            mVhalBinderDiedCallback;
    android::sp<AIBinderDeathRegistrationWrapperInterface> mDeathRegistrationWrapper;
    android::sp<PackageInfoResolverInterface> mPackageInfoResolver;

    android::Mutex mMutex;

    std::unordered_map<aidl::android::automotive::watchdog::TimeoutLength, ClientInfoMap>
            mClientsByTimeout GUARDED_BY(mMutex);
    std::unordered_map<aidl::android::automotive::watchdog::TimeoutLength, PingedClientMap>
            mPingedClients GUARDED_BY(mMutex);
    std::unordered_set<userid_t> mStoppedUserIds GUARDED_BY(mMutex);
    std::shared_ptr<aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor> mMonitor
            GUARDED_BY(mMutex);
    bool mIsEnabled GUARDED_BY(mMutex);
    std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient> mVhalService
            GUARDED_BY(mMutex);
    std::optional<aidl::android::automotive::watchdog::internal::ProcessIdentifier>
            mVhalProcessIdentifier GUARDED_BY(mMutex);
    int32_t mTotalVhalPidCachingAttempts GUARDED_BY(mMutex);
    HeartBeat mVhalHeartBeat GUARDED_BY(mMutex);

    // For unit tests.
    friend class internal::WatchdogProcessServicePeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_WATCHDOGPROCESSSERVICE_H_

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

#ifndef CPP_POWERPOLICY_SERVER_SRC_CARPOWERPOLICYSERVER_H_
#define CPP_POWERPOLICY_SERVER_SRC_CARPOWERPOLICYSERVER_H_

#include "PolicyManager.h"
#include "PowerComponentHandler.h"
#include "SilentModeHandler.h"

#include <aidl/android/automotive/powerpolicy/internal/BnCarPowerPolicyDelegate.h>
#include <aidl/android/automotive/powerpolicy/internal/PowerPolicyInitData.h>
#include <aidl/android/frameworks/automotive/powerpolicy/BnCarPowerPolicyServer.h>
#include <aidl/android/frameworks/automotive/powerpolicy/internal/BnCarPowerPolicySystemNotification.h>
#include <android-base/result.h>
#include <binder/IBinder.h>
#include <binder/Status.h>
#include <utils/Looper.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <utils/Vector.h>

#include <IVhalClient.h>

#include <optional>
#include <unordered_set>

namespace android {
namespace frameworks {
namespace automotive {
namespace powerpolicy {

struct CallbackInfo {
    CallbackInfo(
            ndk::SpAIBinder binder,
            const aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter& filter,
            int32_t pid) :
          binder(binder), filter(filter), pid(pid) {}

    ndk::SpAIBinder binder;
    aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter filter;
    pid_t pid;
};

// Forward declaration for testing use only.
namespace internal {

class CarPowerPolicyServerPeer;

}  // namespace internal

// Forward declaration for defining binder death handler and property change listener.
class CarPowerPolicyServer;

class PropertyChangeListener final :
      public android::frameworks::automotive::vhal::ISubscriptionCallback {
public:
    explicit PropertyChangeListener(CarPowerPolicyServer* service);

    void onPropertyEvent(const std::vector<
                         std::unique_ptr<android::frameworks::automotive::vhal::IHalPropValue>>&
                                 values) override;

    void onPropertySetError(const std::vector<android::frameworks::automotive::vhal::HalPropError>&
                                    errors) override;

private:
    CarPowerPolicyServer* mService;
};

class EventHandler : public android::MessageHandler {
public:
    explicit EventHandler(CarPowerPolicyServer* service);

    void handleMessage(const android::Message& message) override;

private:
    CarPowerPolicyServer* mService;
};

class RequestIdHandler : public android::MessageHandler {
public:
    explicit RequestIdHandler(CarPowerPolicyServer* service);

    void handleMessage(const android::Message& message) override;

private:
    CarPowerPolicyServer* mService;
};

// TODO(b/301025020): Remove CarServiceNotificationHandler once CarPowerPolicyDelegate is ready.
class CarServiceNotificationHandler :
      public aidl::android::frameworks::automotive::powerpolicy::internal::
              BnCarPowerPolicySystemNotification {
public:
    explicit CarServiceNotificationHandler(CarPowerPolicyServer* server);

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyCarServiceReady(
            aidl::android::frameworks::automotive::powerpolicy::internal::PolicyState* policyState)
            override EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyPowerPolicyChange(const std::string& policyId, bool force) override
            EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyPowerPolicyDefinition(
            const std::string& policyId, const std::vector<std::string>& enabledComponents,
            const std::vector<std::string>& disabledComponents) override EXCLUDES(mMutex);

    void terminate() EXCLUDES(mMutex);

private:
    android::Mutex mMutex;
    CarPowerPolicyServer* mService GUARDED_BY(mMutex);
};

class CarPowerPolicyDelegate final :
      public aidl::android::automotive::powerpolicy::internal::BnCarPowerPolicyDelegate {
public:
    explicit CarPowerPolicyDelegate(CarPowerPolicyServer* service);

    binder_status_t dump(int fd, const char** args, uint32_t numArgs) override EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyCarServiceReady(
            const std::shared_ptr<aidl::android::automotive::powerpolicy::internal::
                                          ICarPowerPolicyDelegateCallback>& callback,
            aidl::android::automotive::powerpolicy::internal::PowerPolicyInitData* aidlReturn)
            override;
    ndk::ScopedAStatus applyPowerPolicyAsync(int32_t requestId, const std::string& policyId,
                                             bool force) override EXCLUDES(mMutex);
    ndk::ScopedAStatus setPowerPolicyGroup(const std::string& policyGroupId) override
            EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyPowerPolicyDefinition(
            const std::string& policyId, const std::vector<std::string>& enabledComponents,
            const std::vector<std::string>& disabledComponents) override EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyPowerPolicyGroupDefinition(
            const std::string& policyGroupId,
            const std::vector<std::string>& powerPolicyPerState) override;
    ndk::ScopedAStatus applyPowerPolicyPerPowerStateChangeAsync(
            int32_t requestId,
            aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegate::PowerState
                    state);

    void terminate() EXCLUDES(mMutex);
    ndk::ScopedAStatus runWithService(
            const std::function<ndk::ScopedAStatus(CarPowerPolicyServer*)>& action,
            const std::string& actionTitle) EXCLUDES(mMutex);

private:
    android::Mutex mMutex;
    CarPowerPolicyServer* mService GUARDED_BY(mMutex);
};

/**
 * ISilentModeChangeHandler defines a method which is called when a Silent Mode hw state is changed.
 */
class ISilentModeChangeHandler {
public:
    virtual ~ISilentModeChangeHandler() = 0;

    // Called when Silent Mode is changed.
    virtual void notifySilentModeChange(const bool isSilent) = 0;
};

/**
 * CarPowerPolicyServer implements ISilentModeChangeHandler and ICarPowerPolicyServer.aidl.
 * It handles power policy requests and Silent Mode before Android framework takes control of the
 * device.
 */
class CarPowerPolicyServer final :
      public ISilentModeChangeHandler,
      public aidl::android::frameworks::automotive::powerpolicy::BnCarPowerPolicyServer {
public:
    static android::base::Result<std::shared_ptr<CarPowerPolicyServer>> startService(
            const android::sp<android::Looper>& looper);
    static void terminateService();

    CarPowerPolicyServer();
    android::base::Result<void> init(const sp<android::Looper>& looper);

    // Implements ICarPowerPolicyServer.aidl.
    status_t dump(int fd, const char** args, uint32_t numArgs) override EXCLUDES(mMutex);
    ndk::ScopedAStatus getCurrentPowerPolicy(
            aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicy* aidlReturn) override
            EXCLUDES(mMutex);
    ndk::ScopedAStatus getPowerComponentState(
            aidl::android::frameworks::automotive::powerpolicy::PowerComponent componentId,
            bool* aidlReturn) override;
    ndk::ScopedAStatus registerPowerPolicyChangeCallback(
            const std::shared_ptr<aidl::android::frameworks::automotive::powerpolicy::
                                          ICarPowerPolicyChangeCallback>& callback,
            const aidl::android::frameworks::automotive::powerpolicy::CarPowerPolicyFilter& filter)
            override EXCLUDES(mMutex);
    ndk::ScopedAStatus unregisterPowerPolicyChangeCallback(
            const std::shared_ptr<aidl::android::frameworks::automotive::powerpolicy::
                                          ICarPowerPolicyChangeCallback>& callback) override
            EXCLUDES(mMutex);
    ndk::ScopedAStatus applyPowerPolicy(const std::string& policyId) override EXCLUDES(mMutex);
    ndk::ScopedAStatus setPowerPolicyGroup(const std::string& policyGroupId) override;

    // Implements ICarPowerPolicySystemNotification.aidl.
    ndk::ScopedAStatus notifyCarServiceReady(
            aidl::android::frameworks::automotive::powerpolicy::internal::PolicyState* policyState)
            EXCLUDES(mMutex);
    ndk::ScopedAStatus notifyPowerPolicyChange(const std::string& policyId, bool force);
    ndk::ScopedAStatus notifyPowerPolicyDefinition(
            const std::string& policyId, const std::vector<std::string>& enabledComponents,
            const std::vector<std::string>& disabledComponents);
    ndk::ScopedAStatus notifyPowerPolicyGroupDefinition(
            const std::string& policyGroupId, const std::vector<std::string>& powerPolicyPerState);
    ndk::ScopedAStatus applyPowerPolicyPerPowerStateChangeAsync(
            int32_t requestId,
            aidl::android::automotive::powerpolicy::internal::ICarPowerPolicyDelegate::PowerState
                    state);

    // Internal implementation of ICarPowerPolicyDelegate.aidl.
    ndk::ScopedAStatus applyPowerPolicyAsync(int32_t requestId, const std::string& policyId,
                                             bool force);
    ndk::ScopedAStatus notifyCarServiceReadyInternal(
            const std::shared_ptr<aidl::android::automotive::powerpolicy::internal::
                                          ICarPowerPolicyDelegateCallback>& callback,
            aidl::android::automotive::powerpolicy::internal::PowerPolicyInitData* aidlReturn);

    // Implements ISilentModeChangeHandler.
    void notifySilentModeChange(const bool isSilent) EXCLUDES(mMutex);

    /**
     * Applies the given power policy.
     *
     * @param policyId ID of a power policy to apply.
     * @param carServiceInOperation expected Car Service running state.
     * @param force whether to apply the policy even when the current policy is a system
     *        power policy.
     */
    android::base::Result<void> applyPowerPolicy(const std::string& policyId,
                                                 const bool carServiceInOperation, const bool force)
            EXCLUDES(mMutex);
    /**
     * Sets the power policy group which contains rules to map a power state to a default power
     * policy to apply.
     */
    android::base::Result<void> setPowerPolicyGroupInternal(const std::string& groupId)
            EXCLUDES(mMutex);

    void connectToVhalHelper() EXCLUDES(mMutex);
    void handleClientBinderDeath(const AIBinder* client) EXCLUDES(mMutex);
    void handleCarServiceBinderDeath() EXCLUDES(mMutex);
    void handleVhalDeath() EXCLUDES(mMutex);
    void handleApplyPowerPolicyRequest(const int32_t requestId);

private:
    friend class ndk::SharedRefBase;

    // OnClientBinderDiedContext is a type used as a cookie passed deathRecipient. The
    // deathRecipient's onClientBinderDied function takes only a cookie as input and we have to
    // store all the contexts as the cookie.
    struct OnClientBinderDiedContext {
        CarPowerPolicyServer* server;
        const AIBinder* clientId;
    };

    class LinkUnlinkImpl {
    public:
        virtual ~LinkUnlinkImpl() = default;

        virtual binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                            void* cookie) = 0;
        virtual binder_status_t unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                              void* cookie) = 0;
    };

    class AIBinderLinkUnlinkImpl final : public LinkUnlinkImpl {
    public:
        binder_status_t linkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                    void* cookie) override;
        binder_status_t unlinkToDeath(AIBinder* binder, AIBinder_DeathRecipient* recipient,
                                      void* cookie) override;
    };

    struct PolicyRequest {
        std::string policyId;
        bool force;
    };

    void terminate() EXCLUDES(mMutex);
    bool isRegisteredLocked(const AIBinder* binder) REQUIRES(mMutex);
    void connectToVhal();
    void subscribeToVhal();
    void subscribeToProperty(
            int32_t prop,
            std::function<void(const android::frameworks::automotive::vhal::IHalPropValue&)>
                    processor) EXCLUDES(mMutex);
    bool isPropertySupported(const int32_t prop) EXCLUDES(mMutex);
    bool isPowerPolicyAppliedLocked() const REQUIRES(mMutex);
    bool canApplyPowerPolicyLocked(const CarPowerPolicyMeta& policyMeta, const bool force,
                                   std::vector<CallbackInfo>& outClients) REQUIRES(mMutex);
    void applyInitialPowerPolicy() EXCLUDES(mMutex);
    void applyAndNotifyPowerPolicy(const CarPowerPolicyMeta& policyMeta,
                                   const std::vector<CallbackInfo>& clients,
                                   const bool notifyCarService);
    android::base::Result<void> applyPowerPolicyInternal(const std::string& policyId,
                                                         const bool force,
                                                         const bool notifyCarService)
            EXCLUDES(mMutex);
    android::base::Result<void> notifyVhalNewPowerPolicy(const std::string& policyId)
            EXCLUDES(mMutex);
    ndk::ScopedAStatus enqueuePowerPolicyRequest(int32_t requestId, const std::string& policyId,
                                                 bool force) EXCLUDES(mMutex);

    static void onClientBinderDied(void* cookie);
    static void onCarServiceBinderDied(void* cookie);
    static std::string callbackToString(const CallbackInfo& callback);

    // For test-only.
    void setLinkUnlinkImpl(std::unique_ptr<LinkUnlinkImpl> impl);
    std::vector<CallbackInfo> getPolicyChangeCallbacks() EXCLUDES(mMutex);
    size_t countOnClientBinderDiedContexts() EXCLUDES(mMutex);

private:
    static std::shared_ptr<CarPowerPolicyServer> sCarPowerPolicyServer;

    android::sp<android::Looper> mHandlerLooper;
    android::sp<EventHandler> mEventHandler;
    android::sp<RequestIdHandler> mRequestIdHandler;
    PowerComponentHandler mComponentHandler;
    PolicyManager mPolicyManager;
    SilentModeHandler mSilentModeHandler;
    android::Mutex mMutex;
    CarPowerPolicyMeta mCurrentPowerPolicyMeta GUARDED_BY(mMutex);
    std::string mCurrentPolicyGroupId GUARDED_BY(mMutex);
    std::string mPendingPowerPolicyId GUARDED_BY(mMutex);
    bool mIsPowerPolicyLocked GUARDED_BY(mMutex);
    std::vector<CallbackInfo> mPolicyChangeCallbacks GUARDED_BY(mMutex);
    std::shared_ptr<android::frameworks::automotive::vhal::IVhalClient> mVhalService
            GUARDED_BY(mMutex);
    std::optional<int64_t> mLastApplyPowerPolicyUptimeMs GUARDED_BY(mMutex);
    std::optional<int64_t> mLastSetDefaultPowerPolicyGroupUptimeMs GUARDED_BY(mMutex);
    bool mIsCarServiceInOperation GUARDED_BY(mMutex);
    // No thread-safety guard is needed because only accessed through main thread handler.
    bool mIsFirstConnectionToVhal;
    std::unordered_map<int32_t, bool> mSupportedProperties;
    ndk::ScopedAIBinder_DeathRecipient mClientDeathRecipient GUARDED_BY(mMutex);
    ndk::ScopedAIBinder_DeathRecipient mCarServiceDeathRecipient GUARDED_BY(mMutex);
    // Thread-safe because only initialized once.
    std::shared_ptr<PropertyChangeListener> mPropertyChangeListener;
    std::unique_ptr<android::frameworks::automotive::vhal::ISubscriptionClient> mSubscriptionClient;
    std::shared_ptr<CarServiceNotificationHandler> mCarServiceNotificationHandler
            GUARDED_BY(mMutex);
    int32_t mRemainingConnectionRetryCount;
    // A stub for link/unlink operation. Can be replaced with mock implementation for testing.
    // Thread-safe because only initialized once or modified in test.
    std::unique_ptr<LinkUnlinkImpl> mLinkUnlinkImpl;

    std::shared_ptr<CarPowerPolicyDelegate> mCarPowerPolicyDelegate GUARDED_BY(mMutex);
    ndk::SpAIBinder mPowerPolicyDelegateCallback GUARDED_BY(mMutex);

    // A map of callback ptr to context that is required for handleClientBinderDeath.
    std::unordered_map<const AIBinder*, std::unique_ptr<OnClientBinderDiedContext>>
            mOnClientBinderDiedContexts GUARDED_BY(mMutex);
    std::unordered_map<uint32_t, PolicyRequest> mPolicyRequestById GUARDED_BY(mMutex);

    // For unit tests.
    friend class android::frameworks::automotive::powerpolicy::internal::CarPowerPolicyServerPeer;
};

}  // namespace powerpolicy
}  // namespace automotive
}  // namespace frameworks
}  // namespace android

#endif  // CPP_POWERPOLICY_SERVER_SRC_CARPOWERPOLICYSERVER_H_

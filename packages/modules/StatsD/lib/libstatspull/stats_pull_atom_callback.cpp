/*
 * Copyright (C) 2019, The Android Open Source Project
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

#include <aidl/android/os/BnPullAtomCallback.h>
#include <aidl/android/os/IPullAtomResultReceiver.h>
#include <aidl/android/os/IStatsd.h>
#include <aidl/android/util/StatsEventParcel.h>
#include <android/binder_auto_utils.h>
#include <android/binder_ibinder.h>
#include <android/binder_manager.h>
#include <stats_event.h>
#include <stats_pull_atom_callback.h>

#include <map>
#include <queue>
#include <thread>
#include <vector>

using Status = ::ndk::ScopedAStatus;
using aidl::android::os::BnPullAtomCallback;
using aidl::android::os::IPullAtomResultReceiver;
using aidl::android::os::IStatsd;
using aidl::android::util::StatsEventParcel;
using ::ndk::SharedRefBase;

struct AStatsEventList {
    std::vector<AStatsEvent*> data;
};

AStatsEvent* AStatsEventList_addStatsEvent(AStatsEventList* pull_data) {
    AStatsEvent* event = AStatsEvent_obtain();
    pull_data->data.push_back(event);
    return event;
}

constexpr int64_t DEFAULT_COOL_DOWN_MILLIS = 1000LL;  // 1 second.
constexpr int64_t DEFAULT_TIMEOUT_MILLIS = 1500LL;    // 1.5 seconds.

struct AStatsManager_PullAtomMetadata {
    int64_t cool_down_millis;
    int64_t timeout_millis;
    std::vector<int32_t> additive_fields;
};

AStatsManager_PullAtomMetadata* AStatsManager_PullAtomMetadata_obtain() {
    AStatsManager_PullAtomMetadata* metadata = new AStatsManager_PullAtomMetadata();
    metadata->cool_down_millis = DEFAULT_COOL_DOWN_MILLIS;
    metadata->timeout_millis = DEFAULT_TIMEOUT_MILLIS;
    metadata->additive_fields = std::vector<int32_t>();
    return metadata;
}

void AStatsManager_PullAtomMetadata_release(AStatsManager_PullAtomMetadata* metadata) {
    delete metadata;
}

void AStatsManager_PullAtomMetadata_setCoolDownMillis(AStatsManager_PullAtomMetadata* metadata,
                                                      int64_t cool_down_millis) {
    metadata->cool_down_millis = cool_down_millis;
}

int64_t AStatsManager_PullAtomMetadata_getCoolDownMillis(AStatsManager_PullAtomMetadata* metadata) {
    return metadata->cool_down_millis;
}

void AStatsManager_PullAtomMetadata_setTimeoutMillis(AStatsManager_PullAtomMetadata* metadata,
                                                     int64_t timeout_millis) {
    metadata->timeout_millis = timeout_millis;
}

int64_t AStatsManager_PullAtomMetadata_getTimeoutMillis(AStatsManager_PullAtomMetadata* metadata) {
    return metadata->timeout_millis;
}

void AStatsManager_PullAtomMetadata_setAdditiveFields(AStatsManager_PullAtomMetadata* metadata,
                                                      int32_t* additive_fields,
                                                      int32_t num_fields) {
    metadata->additive_fields.assign(additive_fields, additive_fields + num_fields);
}

int32_t AStatsManager_PullAtomMetadata_getNumAdditiveFields(
        AStatsManager_PullAtomMetadata* metadata) {
    return metadata->additive_fields.size();
}

void AStatsManager_PullAtomMetadata_getAdditiveFields(AStatsManager_PullAtomMetadata* metadata,
                                                      int32_t* fields) {
    std::copy(metadata->additive_fields.begin(), metadata->additive_fields.end(), fields);
}

class StatsPullAtomCallbackInternal : public BnPullAtomCallback {
  public:
    StatsPullAtomCallbackInternal(const AStatsManager_PullAtomCallback callback, void* cookie,
                                  const int64_t coolDownMillis, const int64_t timeoutMillis,
                                  const std::vector<int32_t> additiveFields)
        : mCallback(callback),
          mCookie(cookie),
          mCoolDownMillis(coolDownMillis),
          mTimeoutMillis(timeoutMillis),
          mAdditiveFields(additiveFields) {}

    Status onPullAtom(int32_t atomTag,
                      const std::shared_ptr<IPullAtomResultReceiver>& resultReceiver) override {
        AStatsEventList statsEventList;
        int successInt = mCallback(atomTag, &statsEventList, mCookie);
        bool success = successInt == AStatsManager_PULL_SUCCESS;

        // Convert stats_events into StatsEventParcels.
        std::vector<StatsEventParcel> parcels;

        // Resolves fuzz build failure in b/161575591.
#if defined(__ANDROID_APEX__) || defined(LIB_STATS_PULL_TESTS_FLAG)
        for (int i = 0; i < statsEventList.data.size(); i++) {
            size_t size;
            uint8_t* buffer = AStatsEvent_getBuffer(statsEventList.data[i], &size);

            StatsEventParcel p;
            // vector.assign() creates a copy, but this is inevitable unless
            // stats_event.h/c uses a vector as opposed to a buffer.
            p.buffer.assign(buffer, buffer + size);
            parcels.push_back(std::move(p));
        }
#endif

        Status status = resultReceiver->pullFinished(atomTag, success, parcels);
        if (!status.isOk()) {
            std::vector<StatsEventParcel> emptyParcels;
            resultReceiver->pullFinished(atomTag, /*success=*/false, emptyParcels);
        }
        for (int i = 0; i < statsEventList.data.size(); i++) {
            AStatsEvent_release(statsEventList.data[i]);
        }
        return Status::ok();
    }

    int64_t getCoolDownMillis() const { return mCoolDownMillis; }
    int64_t getTimeoutMillis() const { return mTimeoutMillis; }
    const std::vector<int32_t>& getAdditiveFields() const { return mAdditiveFields; }

  private:
    const AStatsManager_PullAtomCallback mCallback;
    void* mCookie;
    const int64_t mCoolDownMillis;
    const int64_t mTimeoutMillis;
    const std::vector<int32_t> mAdditiveFields;
};

/**
 * @brief pullersMutex is used to guard simultaneous access to pullers from below threads
 * Main thread
 * - AStatsManager_setPullAtomCallback()
 * - AStatsManager_clearPullAtomCallback()
 * Binder thread:
 * - StatsdProvider::binderDied()
 */
static std::mutex pullersMutex;

static std::map<int32_t, std::shared_ptr<StatsPullAtomCallbackInternal>> pullers;

class StatsdProvider {
public:
    StatsdProvider() : mDeathRecipient(AIBinder_DeathRecipient_new(binderDied)) {
    }

    ~StatsdProvider() {
        resetStatsService();
    }

    std::shared_ptr<IStatsd> getStatsService() {
        // There are host unit tests which are using libstatspull
        // Since we do not have statsd on host - the getStatsService() is no-op and
        // should return nullptr
#ifdef __ANDROID__
        std::lock_guard<std::mutex> lock(mStatsdMutex);
        if (!mStatsd) {
            // Fetch statsd
            ::ndk::SpAIBinder binder(AServiceManager_getService("stats"));
            mStatsd = IStatsd::fromBinder(binder);
            if (mStatsd) {
                AIBinder_linkToDeath(binder.get(), mDeathRecipient.get(), this);
            }
        }
#endif  //  __ANDROID__
        return mStatsd;
    }

    void resetStatsService() {
        std::lock_guard<std::mutex> lock(mStatsdMutex);
        mStatsd = nullptr;
    }

    static void binderDied(void* cookie) {
        StatsdProvider* statsProvider = static_cast<StatsdProvider*>(cookie);
        statsProvider->resetStatsService();

        std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
        if (statsService == nullptr) {
            return;
        }

        // Since we do not want to make an IPC with the lock held, we first create a
        // copy of the data with the lock held before iterating through the map.
        std::map<int32_t, std::shared_ptr<StatsPullAtomCallbackInternal>> pullersCopy;
        {
            std::lock_guard<std::mutex> lock(pullersMutex);
            pullersCopy = pullers;
        }
        for (const auto& it : pullersCopy) {
            statsService->registerNativePullAtomCallback(it.first, it.second->getCoolDownMillis(),
                                                         it.second->getTimeoutMillis(),
                                                         it.second->getAdditiveFields(), it.second);
        }
    }

private:
    /**
     * @brief mStatsdMutex is used to guard simultaneous access to mStatsd from below threads:
     * Work thread
     * - registerStatsPullAtomCallbackBlocking()
     * - unregisterStatsPullAtomCallbackBlocking()
     * Binder thread:
     * - StatsdProvider::binderDied()
     */
    std::mutex mStatsdMutex;
    std::shared_ptr<IStatsd> mStatsd;
    ::ndk::ScopedAIBinder_DeathRecipient mDeathRecipient;
};

static std::shared_ptr<StatsdProvider> statsProvider = std::make_shared<StatsdProvider>();

void registerStatsPullAtomCallbackBlocking(int32_t atomTag,
                                           std::shared_ptr<StatsdProvider> statsProvider,
                                           std::shared_ptr<StatsPullAtomCallbackInternal> cb) {
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService == nullptr) {
        // Statsd not available
        return;
    }

    statsService->registerNativePullAtomCallback(
            atomTag, cb->getCoolDownMillis(), cb->getTimeoutMillis(), cb->getAdditiveFields(), cb);
}

void unregisterStatsPullAtomCallbackBlocking(int32_t atomTag,
                                             std::shared_ptr<StatsdProvider> statsProvider) {
    const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();
    if (statsService == nullptr) {
        // Statsd not available
        return;
    }

    statsService->unregisterNativePullAtomCallback(atomTag);
}

class CallbackOperationsHandler {
    struct Cmd {
        enum Type { CMD_REGISTER, CMD_UNREGISTER };

        Type type;
        int atomTag;
        std::shared_ptr<StatsPullAtomCallbackInternal> callback;
    };

public:
    ~CallbackOperationsHandler() {
        if (mWorkThread.joinable()) {
            mWorkThread.join();
        }
    }

    static CallbackOperationsHandler& getInstance() {
        static CallbackOperationsHandler handler;
        return handler;
    }

    void registerCallback(int atomTag, std::shared_ptr<StatsPullAtomCallbackInternal> callback) {
        auto registerCmd = std::make_unique<Cmd>();
        registerCmd->type = Cmd::CMD_REGISTER;
        registerCmd->atomTag = atomTag;
        registerCmd->callback = std::move(callback);
        pushToQueue(std::move(registerCmd));

        startWorkerThread();
    }

    void unregisterCallback(int atomTag) {
        auto unregisterCmd = std::make_unique<Cmd>();
        unregisterCmd->type = Cmd::CMD_UNREGISTER;
        unregisterCmd->atomTag = atomTag;
        pushToQueue(std::move(unregisterCmd));

        startWorkerThread();
    }

private:
    std::atomic_bool mThreadAlive = false;
    std::thread mWorkThread;

    std::mutex mMutex;
    std::queue<std::unique_ptr<Cmd>> mCmdQueue;

    CallbackOperationsHandler() {
    }

    void pushToQueue(std::unique_ptr<Cmd> cmd) {
        std::unique_lock<std::mutex> lock(mMutex);
        mCmdQueue.push(std::move(cmd));
    }

    void startWorkerThread() {
        // Only spawn one thread to manage requests
        if (mThreadAlive) {
            return;
        }
        mThreadAlive = true;
        if (mWorkThread.joinable()) {
            mWorkThread.join();
        }
        mWorkThread = std::thread(&CallbackOperationsHandler::processCommands, this, statsProvider);
    }

    void processCommands(std::shared_ptr<StatsdProvider> statsProvider) {
        /**
         * First trying to obtain stats service instance
         * This is a blocking call, which waits on service readiness
         */
        const std::shared_ptr<IStatsd> statsService = statsProvider->getStatsService();

        if (!statsService) {
            // Statsd not available - dropping all submitted command requests
            std::queue<std::unique_ptr<Cmd>> emptyQueue;
            std::unique_lock<std::mutex> lock(mMutex);
            mCmdQueue.swap(emptyQueue);
            mThreadAlive = false;
            return;
        }

        while (true) {
            std::unique_ptr<Cmd> cmd = nullptr;
            {
                /**
                 * To guarantee sequential commands processing we need to lock mutex queue
                 */
                std::unique_lock<std::mutex> lock(mMutex);
                if (mCmdQueue.empty()) {
                    mThreadAlive = false;
                    return;
                }

                cmd = std::move(mCmdQueue.front());
                mCmdQueue.pop();
            }

            switch (cmd->type) {
                case Cmd::CMD_REGISTER: {
                    registerStatsPullAtomCallbackBlocking(cmd->atomTag, statsProvider,
                                                          cmd->callback);
                    break;
                }
                case Cmd::CMD_UNREGISTER: {
                    unregisterStatsPullAtomCallbackBlocking(cmd->atomTag, statsProvider);
                    break;
                }
            }
        }
    }
};

void AStatsManager_setPullAtomCallback(int32_t atom_tag, AStatsManager_PullAtomMetadata* metadata,
                                       AStatsManager_PullAtomCallback callback, void* cookie) {
    int64_t coolDownMillis =
            metadata == nullptr ? DEFAULT_COOL_DOWN_MILLIS : metadata->cool_down_millis;
    int64_t timeoutMillis = metadata == nullptr ? DEFAULT_TIMEOUT_MILLIS : metadata->timeout_millis;

    std::vector<int32_t> additiveFields;
    if (metadata != nullptr) {
        additiveFields = metadata->additive_fields;
    }

    std::shared_ptr<StatsPullAtomCallbackInternal> callbackBinder =
            SharedRefBase::make<StatsPullAtomCallbackInternal>(callback, cookie, coolDownMillis,
                                                               timeoutMillis, additiveFields);

    {
        std::lock_guard<std::mutex> lock(pullersMutex);
        // Always add to the map. If statsd is dead, we will add them when it comes back.
        pullers[atom_tag] = callbackBinder;
    }

    CallbackOperationsHandler::getInstance().registerCallback(atom_tag, callbackBinder);
}

void AStatsManager_clearPullAtomCallback(int32_t atom_tag) {
    {
        std::lock_guard<std::mutex> lock(pullersMutex);
        // Always remove the puller from our map.
        // If statsd is down, we will not register it when it comes back.
        pullers.erase(atom_tag);
    }

    CallbackOperationsHandler::getInstance().unregisterCallback(atom_tag);
}

/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_TELEMETRY_CARTELEMETRYD_SRC_TELEMETRYSERVER_H_
#define CPP_TELEMETRY_CARTELEMETRYD_SRC_TELEMETRYSERVER_H_

#include "LooperWrapper.h"
#include "RingBuffer.h"

#include <aidl/android/automotive/telemetry/internal/ICarDataListener.h>
#include <aidl/android/frameworks/automotive/telemetry/CallbackConfig.h>
#include <aidl/android/frameworks/automotive/telemetry/CarData.h>
#include <aidl/android/frameworks/automotive/telemetry/ICarTelemetryCallback.h>
#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <android-base/thread_annotations.h>
#include <gtest/gtest_prod.h>
#include <utils/Looper.h>

#include <cstdint>
#include <memory>
#include <unordered_map>
#include <unordered_set>

namespace android {
namespace automotive {
namespace telemetry {

using ::aidl::android::frameworks::automotive::telemetry::CallbackConfig;
using ::aidl::android::frameworks::automotive::telemetry::ICarTelemetryCallback;

struct TelemetryCallback {
    CallbackConfig config;
    std::shared_ptr<ICarTelemetryCallback> callback;

    TelemetryCallback() {}
    explicit TelemetryCallback(const std::shared_ptr<ICarTelemetryCallback>& cb) : callback(cb) {}
    explicit TelemetryCallback(const CallbackConfig& cfg,
                               const std::shared_ptr<ICarTelemetryCallback>& cb) :
          config(cfg), callback(cb) {}
    // Copy constructor & copy assignment
    TelemetryCallback(const TelemetryCallback& other) = default;
    TelemetryCallback& operator=(const TelemetryCallback& other) = default;
    // Move constructor & move assignment
    TelemetryCallback(TelemetryCallback&& other) = default;
    TelemetryCallback& operator=(TelemetryCallback&& other) = default;

    // Equal function
    bool operator==(const TelemetryCallback& other) const {
        return reinterpret_cast<uintptr_t>(callback->asBinder().get()) ==
                reinterpret_cast<uintptr_t>(other.callback->asBinder().get());
    }

    struct HashFunction {
        size_t operator()(const TelemetryCallback& tc) const {
            return std::hash<uintptr_t>()(
                    reinterpret_cast<uintptr_t>(tc.callback->asBinder().get()));
        }
    };
};

// This class contains the main logic of cartelemetryd native service.
//
//   [writer clients] -> ICarTelemetry  -----------.
//   [reader client] --> ICarTelemetryInternal -----`-> TelemetryServer
//
// TelemetryServer starts pushing CarData to ICarDataListener when there is a data available and
// the listener is set and alive. It uses `mLooper` for periodically pushing the data.
//
// This class is thread-safe.
class TelemetryServer {
public:
    explicit TelemetryServer(LooperWrapper* looper,
                             const std::chrono::nanoseconds& pushCarDataDelayNs, int maxBufferSize);

    /**
     * Dumps the current state for dumpsys.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void dump(int fd);

    /**
     * Writes incoming CarData to the RingBuffer.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void writeCarData(
            const std::vector<aidl::android::frameworks::automotive::telemetry::CarData>& dataList,
            uid_t publisherUid);

    /**
     * Adds a ICarTelemtryCallback and associate it with the CallbackConfig.
     *
     * <p>Expected to be called from a binder thread pool.

     * @return An empty okay result on success, or an error result if the callback already
     * exists.
     */
    android::base::Result<void> addCallback(const CallbackConfig& config,
                                            const std::shared_ptr<ICarTelemetryCallback>& callback);

    /**
     * Removes a ICarTelemetryCallback.
     *
     * <p>Expected to be called from a binder thread pool.
     *
     * @return An empty okay result on success, or an error result if the callback is not found.
     */
    android::base::Result<void> removeCallback(
            const std::shared_ptr<ICarTelemetryCallback>& callback);

    /**
     * Sets the listener and overrides the previous listener if it exists.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void setListener(
            const std::shared_ptr<aidl::android::automotive::telemetry::internal::ICarDataListener>&
                    listener);

    /**
     * Clears the ICarDataListener.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void clearListener();

    /**
     * Adds active CarData IDs, called by CarTelemetrydPublisher when the IDs
     * has active subscribers.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void addCarDataIds(const std::vector<int32_t>& ids);

    /**
     * Removes CarData IDs, called by CarTelemetrydPublisher when the IDs
     * no longer has subscribers.
     *
     * <p>Expected to be called from a binder thread pool.
     */
    void removeCarDataIds(const std::vector<int32_t>& ids);

    /**
     * Expected to be called from a binder thread pool.
     */
    std::shared_ptr<aidl::android::automotive::telemetry::internal::ICarDataListener> getListener();

private:
    class MessageHandlerImpl : public MessageHandler {
    public:
        explicit MessageHandlerImpl(TelemetryServer* server);

        void handleMessage(const Message& message) override;

    private:
        TelemetryServer* mTelemetryServer;  // not owned
    };

private:
    // Find the common elements in mCarDataIds and the argument ids
    std::vector<int32_t> findCarDataIdsIntersection(const std::vector<int32_t>& ids);
    // Periodically called by mLooper if there is a "push car data" messages.
    void pushCarDataToListeners();

    LooperWrapper* mLooper;  // not owned
    const std::chrono::nanoseconds mPushCarDataDelayNs;

    // A single mutex for all the sensitive operations. Threads must not lock it for long time,
    // as clients will be writing CarData to the ring buffer under this mutex.
    std::mutex mMutex;

    // Buffers vendor written CarData.
    RingBuffer mRingBuffer GUARDED_BY(mMutex);

    // Notifies listener when CarData is written.
    std::shared_ptr<aidl::android::automotive::telemetry::internal::ICarDataListener>
            mCarDataListener GUARDED_BY(mMutex);

    // Stores a set of CarData IDs that have subscribers in CarTelemetryService.
    // Used for filtering data.
    std::unordered_set<int32_t> mCarDataIds GUARDED_BY(mMutex);

    // A hashset of TelemetryCallbacks to keep track of which callbacks have been added.
    std::unordered_set<TelemetryCallback, TelemetryCallback::HashFunction> mCallbacks
            GUARDED_BY(mMutex);

    // Maps CarData ID to a set of callbacks that are associated with this ID.
    // Used for invoking the callback when this ID has been added or removed.
    std::unordered_map<int32_t,
                       std::unordered_set<TelemetryCallback, TelemetryCallback::HashFunction>>
            mIdToCallbacksMap GUARDED_BY(mMutex);

    // Handler for mLooper.
    android::sp<MessageHandlerImpl> mMessageHandler;

    // Friends are simplest way of testing if `pushCarDataToListeners()` can handle edge cases.
    friend class TelemetryServerTest;
    FRIEND_TEST(TelemetryServerTest, NoListenerButMultiplePushes);
    FRIEND_TEST(TelemetryServerTest, NoDataButMultiplePushes);
    // The following test accesses member variables such as `mCarDataIds` and `mCallbacks`
    // to check its contents.
    FRIEND_TEST(TelemetryServerTest, RemoveCarDataIdsReturnsOk);
    FRIEND_TEST(TelemetryServerTest, RemoveCallbackReturnsOk);
};

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

#endif  // CPP_TELEMETRY_CARTELEMETRYD_SRC_TELEMETRYSERVER_H_

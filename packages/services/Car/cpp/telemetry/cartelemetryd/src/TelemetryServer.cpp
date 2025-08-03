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

#include "TelemetryServer.h"

#include "CarTelemetryImpl.h"
#include "RingBuffer.h"

#include <aidl/android/automotive/telemetry/internal/CarDataInternal.h>
#include <android-base/logging.h>

#include <inttypes.h>  // for PRIu64 and friends

#include <cstdint>
#include <memory>

namespace android {
namespace automotive {
namespace telemetry {

namespace {

using ::aidl::android::automotive::telemetry::internal::CarDataInternal;
using ::aidl::android::automotive::telemetry::internal::ICarDataListener;
using ::aidl::android::frameworks::automotive::telemetry::CarData;
using ::aidl::android::frameworks::automotive::telemetry::ICarTelemetryCallback;
using ::android::base::Error;
using ::android::base::Result;

constexpr int kMsgPushCarDataToListener = 1;

// If ICarDataListener cannot accept data, the next push should be delayed little bit to allow
// the listener to recover.
constexpr const std::chrono::seconds kPushCarDataFailureDelaySeconds = 1s;
}  // namespace

TelemetryServer::TelemetryServer(LooperWrapper* looper,
                                 const std::chrono::nanoseconds& pushCarDataDelayNs,
                                 const int maxBufferSize) :
      mLooper(looper),
      mPushCarDataDelayNs(pushCarDataDelayNs),
      mRingBuffer(maxBufferSize),
      mMessageHandler(new MessageHandlerImpl(this)) {}

void TelemetryServer::setListener(const std::shared_ptr<ICarDataListener>& listener) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    mCarDataListener = listener;
    mLooper->sendMessageDelayed(mPushCarDataDelayNs.count(), mMessageHandler,
                                kMsgPushCarDataToListener);
}

void TelemetryServer::clearListener() {
    const std::scoped_lock<std::mutex> lock(mMutex);
    if (mCarDataListener == nullptr) {
        return;
    }
    mCarDataListener = nullptr;
    mLooper->removeMessages(mMessageHandler, kMsgPushCarDataToListener);
}

std::vector<int32_t> TelemetryServer::findCarDataIdsIntersection(const std::vector<int32_t>& ids) {
    std::vector<int32_t> interestedIds;
    for (int32_t id : ids) {
        if (mCarDataIds.find(id) != mCarDataIds.end()) {
            interestedIds.push_back(id);
        }
    }
    return interestedIds;
}

void TelemetryServer::addCarDataIds(const std::vector<int32_t>& ids) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    mCarDataIds.insert(ids.cbegin(), ids.cend());
    std::unordered_set<TelemetryCallback, TelemetryCallback::HashFunction> invokedCallbacks;
    LOG(VERBOSE) << "Received addCarDataIds call from CarTelemetryService, notifying callbacks";
    for (int32_t id : ids) {
        if (mIdToCallbacksMap.find(id) == mIdToCallbacksMap.end()) {
            // prevent out of range exception when calling unordered_map.at()
            continue;
        }
        const auto& callbacksForId = mIdToCallbacksMap.at(id);
        LOG(VERBOSE) << "Invoking " << callbacksForId.size() << " callbacks for ID=" << id;
        for (const TelemetryCallback& tc : callbacksForId) {
            if (invokedCallbacks.find(tc) != invokedCallbacks.end()) {
                // skipping already invoked callbacks
                continue;
            }
            invokedCallbacks.insert(tc);
            ndk::ScopedAStatus status =
                    tc.callback->onChange(findCarDataIdsIntersection(tc.config.carDataIds));
            if (status.getExceptionCode() == EX_TRANSACTION_FAILED &&
                status.getStatus() == STATUS_DEAD_OBJECT) {
                LOG(WARNING) << "Failed to invoke onChange() on a dead object, removing callback";
                removeCallback(tc.callback);
            }
        }
    }
}

void TelemetryServer::removeCarDataIds(const std::vector<int32_t>& ids) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    for (int32_t id : ids) {
        mCarDataIds.erase(id);
    }
    std::unordered_set<TelemetryCallback, TelemetryCallback::HashFunction> invokedCallbacks;
    LOG(VERBOSE) << "Received removeCarDataIds call from CarTelemetryService, notifying callbacks";
    for (int32_t id : ids) {
        if (mIdToCallbacksMap.find(id) == mIdToCallbacksMap.end()) {
            // prevent out of range exception when calling unordered_map.at()
            continue;
        }
        const auto& callbacksForId = mIdToCallbacksMap.at(id);
        LOG(VERBOSE) << "Invoking " << callbacksForId.size() << " callbacks for ID=" << id;
        for (const TelemetryCallback& tc : callbacksForId) {
            if (invokedCallbacks.find(tc) != invokedCallbacks.end()) {
                // skipping already invoked callbacks
                continue;
            }
            invokedCallbacks.insert(tc);
            ndk::ScopedAStatus status =
                    tc.callback->onChange(findCarDataIdsIntersection(tc.config.carDataIds));
            if (status.getExceptionCode() == EX_TRANSACTION_FAILED &&
                status.getStatus() == STATUS_DEAD_OBJECT) {
                LOG(WARNING) << "Failed to invoke onChange() on a dead object, removing callback";
                removeCallback(tc.callback);
            }
        }
    }
}

std::shared_ptr<ICarDataListener> TelemetryServer::getListener() {
    const std::scoped_lock<std::mutex> lock(mMutex);
    return mCarDataListener;
}

void TelemetryServer::dump(int fd) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    dprintf(fd, "  TelemetryServer:\n");
    mRingBuffer.dump(fd);
}

Result<void> TelemetryServer::addCallback(const CallbackConfig& config,
                                          const std::shared_ptr<ICarTelemetryCallback>& callback) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    TelemetryCallback cb(config, callback);
    if (mCallbacks.find(cb) != mCallbacks.end()) {
        const std::string msg = "The ICarTelemetryCallback already exists. "
                                "Use removeCarTelemetryCallback() to remove it first";
        LOG(WARNING) << msg;
        return Error(EX_ILLEGAL_ARGUMENT) << msg;
    }

    mCallbacks.insert(cb);

    // link each interested CarData ID with the new callback
    for (int32_t id : config.carDataIds) {
        if (mIdToCallbacksMap.find(id) == mIdToCallbacksMap.end()) {
            mIdToCallbacksMap[id] =
                    std::unordered_set<TelemetryCallback, TelemetryCallback::HashFunction>{cb};
        } else {
            mIdToCallbacksMap.at(id).insert(cb);
        }
        LOG(VERBOSE) << "CarData ID=" << id << " has " << mIdToCallbacksMap.at(id).size()
                     << " associated callbacks";
    }

    std::vector<int32_t> interestedIds = findCarDataIdsIntersection(config.carDataIds);
    if (interestedIds.size() == 0) {
        return {};
    }
    LOG(VERBOSE) << "Notifying new callback with active CarData IDs";
    ndk::ScopedAStatus status = callback->onChange(interestedIds);
    if (status.getExceptionCode() == EX_TRANSACTION_FAILED &&
        status.getStatus() == STATUS_DEAD_OBJECT) {
        removeCallback(callback);
        return Error(EX_ILLEGAL_ARGUMENT)
                << "Failed to invoke onChange() on a dead object, removing callback";
    }
    return {};
}

Result<void> TelemetryServer::removeCallback(
        const std::shared_ptr<ICarTelemetryCallback>& callback) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    auto it = mCallbacks.find(TelemetryCallback(callback));
    if (it == mCallbacks.end()) {
        constexpr char msg[] = "Attempting to remove a CarTelemetryCallback that does not exist";
        LOG(WARNING) << msg;
        return Error(EX_ILLEGAL_ARGUMENT) << msg;
    }

    const TelemetryCallback& tc = *it;
    // unlink callback from ID in the mIdToCallbacksMap
    for (int32_t id : tc.config.carDataIds) {
        if (mIdToCallbacksMap.find(id) == mIdToCallbacksMap.end()) {
            LOG(ERROR) << "The callback is not linked to its interested IDs.";
            continue;
        }
        auto& associatedCallbacks = mIdToCallbacksMap.at(id);
        auto associatedCallbackIterator = associatedCallbacks.find(tc);
        if (associatedCallbackIterator == associatedCallbacks.end()) {
            continue;
        }
        associatedCallbacks.erase(associatedCallbackIterator);
        LOG(VERBOSE) << "After unlinking a callback from ID=" << id << ", the ID has "
                     << mIdToCallbacksMap.at(id).size() << " associated callbacks";
        if (associatedCallbacks.size() == 0) {
            mIdToCallbacksMap.erase(id);
        }
    }

    mCallbacks.erase(it);
    LOG(VERBOSE) << "After removeCallback, there are " << mCallbacks.size()
                 << " callbacks in cartelemetryd";
    return {};
}

void TelemetryServer::writeCarData(const std::vector<CarData>& dataList, uid_t publisherUid) {
    const std::scoped_lock<std::mutex> lock(mMutex);
    bool bufferWasEmptyBefore = mRingBuffer.size() == 0;
    for (auto&& data : dataList) {
        // ignore data that has no subscribers in CarTelemetryService
        if (mCarDataIds.find(data.id) == mCarDataIds.end()) {
            LOG(VERBOSE) << "Ignoring CarData with ID=" << data.id;
            continue;
        }
        mRingBuffer.push({.mId = data.id,
                          .mContent = std::move(data.content),
                          .mPublisherUid = publisherUid});
    }
    // If the mRingBuffer was not empty, the message is already scheduled. It prevents scheduling
    // too many unnecessary idendical messages in the looper.
    if (mCarDataListener != nullptr && bufferWasEmptyBefore && mRingBuffer.size() > 0) {
        mLooper->sendMessageDelayed(mPushCarDataDelayNs.count(), mMessageHandler,
                                    kMsgPushCarDataToListener);
    }
}

// Runs on the main thread.
void TelemetryServer::pushCarDataToListeners() {
    std::vector<CarDataInternal> pendingCarDataInternals;
    {
        const std::scoped_lock<std::mutex> lock(mMutex);
        // Remove extra messages.
        mLooper->removeMessages(mMessageHandler, kMsgPushCarDataToListener);
        if (mCarDataListener == nullptr || mRingBuffer.size() == 0) {
            return;
        }
        // Push elements to pendingCarDataInternals in reverse order so we can send data
        // from the back of the pendingCarDataInternals vector.
        while (mRingBuffer.size() > 0) {
            auto carData = std::move(mRingBuffer.popBack());
            CarDataInternal data;
            data.id = carData.mId;
            data.content = std::move(carData.mContent);
            pendingCarDataInternals.push_back(data);
        }
    }

    // TODO(b/186477983): send data in batch to improve performance, but careful sending too
    //                    many data at once, as it could clog the Binder - it has <1MB limit.
    while (!pendingCarDataInternals.empty()) {
        ndk::ScopedAStatus status = ndk::ScopedAStatus::ok();
        {
            const std::scoped_lock<std::mutex> lock(mMutex);
            if (mCarDataListener != nullptr) {
                status = mCarDataListener->onCarDataReceived({pendingCarDataInternals.back()});
            } else {
                status = ndk::ScopedAStatus::
                        fromServiceSpecificErrorWithMessage(EX_NULL_POINTER,
                                                            "mCarDataListener is currently set to "
                                                            "null, will try again.");
            }
        }
        if (!status.isOk()) {
            LOG(WARNING) << "Failed to push CarDataInternal, will try again. Status: "
                         << status.getStatus()
                         << ", service-specific error: " << status.getServiceSpecificError()
                         << ", message: " << status.getMessage()
                         << ", exception code: " << status.getExceptionCode()
                         << ", description: " << status.getDescription();
            sleep(kPushCarDataFailureDelaySeconds.count());
        } else {
            pendingCarDataInternals.pop_back();
        }
    }
}

TelemetryServer::MessageHandlerImpl::MessageHandlerImpl(TelemetryServer* server) :
      mTelemetryServer(server) {}

void TelemetryServer::MessageHandlerImpl::handleMessage(const Message& message) {
    switch (message.what) {
        case kMsgPushCarDataToListener:
            mTelemetryServer->pushCarDataToListeners();
            break;
        default:
            LOG(WARNING) << "Unknown message: " << message.what;
    }
}

}  // namespace telemetry
}  // namespace automotive
}  // namespace android

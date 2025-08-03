/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "chre/core/timer_pool.h"
#include "chre/core/event_loop.h"
#include "chre/core/event_loop_manager.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/system_time.h"
#include "chre/util/lock_guard.h"

namespace chre {

TimerPool::TimerPool() {
  if (!mSystemTimer.init()) {
    FATAL_ERROR("Failed to initialize a system timer for the TimerPool");
  }
}

TimerHandle TimerPool::setSystemTimer(Nanoseconds duration,
                                      SystemEventCallbackFunction *callback,
                                      SystemCallbackType callbackType,
                                      void *data) {
  CHRE_ASSERT(callback != nullptr);
  TimerHandle timerHandle =
      setTimer(kSystemInstanceId, duration, data, callback, callbackType,
               true /* isOneShot */);

  if (timerHandle == CHRE_TIMER_INVALID) {
    FATAL_ERROR("Failed to set system timer");
  }

  return timerHandle;
}

uint32_t TimerPool::cancelAllNanoappTimers(const Nanoapp *nanoapp) {
  CHRE_ASSERT(nanoapp != nullptr);
  LockGuard<Mutex> lock(mMutex);

  uint32_t numTimersCancelled = 0;

  // Iterate backward as we remove requests from the list.
  for (int i = static_cast<int>(mTimerRequests.size()) - 1; i >= 0; i--) {
    size_t iAsSize = static_cast<size_t>(i);
    const TimerRequest &request = mTimerRequests[iAsSize];
    if (request.instanceId == nanoapp->getInstanceId()) {
      numTimersCancelled++;
      removeTimerRequestLocked(iAsSize);
    }
  }

  return numTimersCancelled;
}

TimerHandle TimerPool::setTimer(uint16_t instanceId, Nanoseconds duration,
                                const void *cookie,
                                SystemEventCallbackFunction *systemCallback,
                                SystemCallbackType callbackType,
                                bool isOneShot) {
  LockGuard<Mutex> lock(mMutex);

  TimerRequest timerRequest;
  timerRequest.instanceId = instanceId;
  timerRequest.timerHandle = generateTimerHandleLocked();
  timerRequest.expirationTime = SystemTime::getMonotonicTime() + duration;
  timerRequest.duration = duration;
  timerRequest.cookie = cookie;
  timerRequest.systemCallback = systemCallback;
  timerRequest.callbackType = callbackType;
  timerRequest.isOneShot = isOneShot;

  bool success = insertTimerRequestLocked(timerRequest);

  if (success) {
    if (mTimerRequests.size() == 1) {
      // If this timer request was the first, schedule it.
      handleExpiredTimersAndScheduleNextLocked();
    } else {
      // If there was already a timer pending before this, and we just inserted
      // to the top of the queue, just update the system timer. This is slightly
      // more efficient than calling into
      // handleExpiredTimersAndScheduleNextLocked().
      bool newRequestExpiresFirst =
          timerRequest.timerHandle == mTimerRequests.top().timerHandle;
      if (newRequestExpiresFirst) {
        mSystemTimer.set(handleSystemTimerCallback, this, duration);
      }
    }
  }

  return success ? timerRequest.timerHandle : CHRE_TIMER_INVALID;
}

bool TimerPool::cancelTimer(uint16_t instanceId, TimerHandle timerHandle) {
  LockGuard<Mutex> lock(mMutex);
  size_t index;
  bool success = false;
  TimerRequest *timerRequest =
      getTimerRequestByTimerHandleLocked(timerHandle, &index);

  if (timerRequest == nullptr) {
    LOGW("Failed to cancel timer ID %" PRIu32 ": not found", timerHandle);
  } else if (timerRequest->instanceId != instanceId) {
    LOGW("Failed to cancel timer ID %" PRIu32 ": permission denied",
         timerHandle);
  } else {
    removeTimerRequestLocked(index);
    success = true;
  }

  return success;
}

TimerPool::TimerRequest *TimerPool::getTimerRequestByTimerHandleLocked(
    TimerHandle timerHandle, size_t *index) {
  for (size_t i = 0; i < mTimerRequests.size(); i++) {
    if (mTimerRequests[i].timerHandle == timerHandle) {
      if (index != nullptr) {
        *index = i;
      }
      return &mTimerRequests[i];
    }
  }

  return nullptr;
}

bool TimerPool::TimerRequest::operator>(const TimerRequest &request) const {
  return expirationTime > request.expirationTime;
}

TimerHandle TimerPool::generateTimerHandleLocked() {
  TimerHandle timerHandle;
  if (mGenerateTimerHandleMustCheckUniqueness) {
    timerHandle = generateUniqueTimerHandleLocked();
  } else {
    timerHandle = mLastTimerHandle + 1;
    if (timerHandle == CHRE_TIMER_INVALID) {
      // TODO: Consider that uniqueness checking can be reset when the number
      // of timer requests reaches zero.
      mGenerateTimerHandleMustCheckUniqueness = true;
      timerHandle = generateUniqueTimerHandleLocked();
    }
  }

  mLastTimerHandle = timerHandle;
  return timerHandle;
}

TimerHandle TimerPool::generateUniqueTimerHandleLocked() {
  TimerHandle timerHandle = mLastTimerHandle;
  while (1) {
    timerHandle++;
    if (timerHandle != CHRE_TIMER_INVALID) {
      TimerRequest *timerRequest =
          getTimerRequestByTimerHandleLocked(timerHandle);
      if (timerRequest == nullptr) {
        return timerHandle;
      }
    }
  }
}

bool TimerPool::isNewTimerAllowedLocked(bool isNanoappTimer) const {
  static_assert(kMaxNanoappTimers <= kMaxTimerRequests,
                "Max number of nanoapp timers is too big");
  static_assert(kNumReservedNanoappTimers <= kMaxTimerRequests,
                "Number of reserved nanoapp timers is too big");

  bool allowed;
  if (isNanoappTimer) {
    allowed = (mNumNanoappTimers < kMaxNanoappTimers);
  } else {  // System timer
    // We must not allow more system timers than the required amount of
    // reserved timers for nanoapps.
    constexpr size_t kMaxSystemTimers =
        kMaxTimerRequests - kNumReservedNanoappTimers;
    size_t numSystemTimers = mTimerRequests.size() - mNumNanoappTimers;
    allowed = (numSystemTimers < kMaxSystemTimers);
  }

  return allowed;
}

bool TimerPool::insertTimerRequestLocked(const TimerRequest &timerRequest) {
  bool isNanoappTimer = (timerRequest.instanceId != kSystemInstanceId);
  bool success = isNewTimerAllowedLocked(isNanoappTimer) &&
                 mTimerRequests.push(timerRequest);

  if (!success) {
    LOG_OOM();
  } else if (isNanoappTimer) {
    mNumNanoappTimers++;
  }

  return success;
}

void TimerPool::popTimerRequestLocked() {
  CHRE_ASSERT(!mTimerRequests.empty());
  if (!mTimerRequests.empty()) {
    bool isNanoappTimer =
        (mTimerRequests.top().instanceId != kSystemInstanceId);
    mTimerRequests.pop();
    if (isNanoappTimer) {
      mNumNanoappTimers--;
    }
  }
}

void TimerPool::removeTimerRequestLocked(size_t index) {
  CHRE_ASSERT(index < mTimerRequests.size());
  if (index < mTimerRequests.size()) {
    bool isNanoappTimer =
        (mTimerRequests[index].instanceId != kSystemInstanceId);
    mTimerRequests.remove(index);
    if (isNanoappTimer) {
      mNumNanoappTimers--;
    }

    if (index == 0) {
      mSystemTimer.cancel();
      handleExpiredTimersAndScheduleNextLocked();
    }
  }
}

bool TimerPool::handleExpiredTimersAndScheduleNext() {
  LockGuard<Mutex> lock(mMutex);
  return handleExpiredTimersAndScheduleNextLocked();
}

bool TimerPool::handleExpiredTimersAndScheduleNextLocked() {
  bool handledExpiredTimer = false;

  while (!mTimerRequests.empty()) {
    Nanoseconds currentTime = SystemTime::getMonotonicTime();
    TimerRequest &currentTimerRequest = mTimerRequests.top();
    if (currentTime >= currentTimerRequest.expirationTime) {
      // This timer has expired, so post an event if it is a nanoapp timer, or
      // submit a deferred callback if it's a system timer.
      if (currentTimerRequest.instanceId == kSystemInstanceId) {
        EventLoopManagerSingleton::get()->deferCallback(
            currentTimerRequest.callbackType,
            const_cast<void *>(currentTimerRequest.cookie),
            currentTimerRequest.systemCallback);
      } else {
        EventLoopManagerSingleton::get()->getEventLoop().postEventOrDie(
            CHRE_EVENT_TIMER, const_cast<void *>(currentTimerRequest.cookie),
            nullptr /*freeCallback*/, currentTimerRequest.instanceId);
      }
      handledExpiredTimer = true;

      // Reschedule the timer if needed, and release the current request.
      if (!currentTimerRequest.isOneShot) {
        // Important: we need to make a copy of currentTimerRequest here,
        // because it's a reference to memory that may get moved during the
        // insert operation (thereby invalidating it).
        TimerRequest cyclicTimerRequest = currentTimerRequest;
        cyclicTimerRequest.expirationTime =
            currentTimerRequest.expirationTime + currentTimerRequest.duration;
        popTimerRequestLocked();
        CHRE_ASSERT(insertTimerRequestLocked(cyclicTimerRequest));
      } else {
        popTimerRequestLocked();
      }
    } else {
      // Update the system timer to reflect the duration until the closest
      // expiry (mTimerRequests is sorted by expiry, so we just do this for
      // the first timer found which has not expired yet)
      Nanoseconds duration = currentTimerRequest.expirationTime - currentTime;
      mSystemTimer.set(handleSystemTimerCallback, this, duration);
      break;
    }
  }

  return handledExpiredTimer;
}

bool TimerPool::hasNanoappTimers(uint16_t instanceId) {
  LockGuard<Mutex> lock(mMutex);

  for (size_t i = 0; i < mTimerRequests.size(); i++) {
    const TimerRequest &request = mTimerRequests[i];
    if (request.instanceId == instanceId) {
      return true;
    }
  }
  return false;
}

void TimerPool::handleSystemTimerCallback(void *timerPoolPtr) {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    auto *timerPool = static_cast<TimerPool *>(data);
    if (!timerPool->handleExpiredTimersAndScheduleNext()) {
      // Means that the system timer invoked our callback before the next
      // timer expired. Possible in rare race conditions with time removal,
      // but could indicate a faulty SystemTimer implementation if this
      // happens often. Not a major problem - we'll just reset the timer to
      // the next expiration.
      LOGW("Timer callback invoked prior to expiry");
    }
  };

  EventLoopManagerSingleton::get()->deferCallback(
      SystemCallbackType::TimerPoolTick, timerPoolPtr, callback);
}

}  // namespace chre

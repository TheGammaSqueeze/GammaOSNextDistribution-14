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

#ifndef CHRE_CORE_EVENT_H_
#define CHRE_CORE_EVENT_H_

#include "chre/core/event_loop_common.h"
#include "chre/platform/assert.h"
#include "chre/util/non_copyable.h"
#include "chre_api/chre/event.h"

#include <cstdint>

namespace chre {

//! Instance ID used for events sent by the system
constexpr uint16_t kSystemInstanceId = 0;

//! Target instance ID used to deliver a message to all nanoapps registered for
//! the event
constexpr uint16_t kBroadcastInstanceId = UINT16_MAX;

//! This value can be used in a nanoapp's own instance ID to indicate that the
//! ID is invalid/not assigned yet
constexpr uint16_t kInvalidInstanceId = kBroadcastInstanceId;

//! Default target group mask that results in the event being sent to any app
//! registered for it.
constexpr uint16_t kDefaultTargetGroupMask = UINT16_MAX;

class Event : public NonCopyable {
 public:
  Event() = delete;

  // Events targeted at nanoapps
  Event(uint16_t eventType_, void *eventData_,
        chreEventCompleteFunction *freeCallback_, bool isLowPriority_,
        uint16_t senderInstanceId_ = kSystemInstanceId,
        uint16_t targetInstanceId_ = kBroadcastInstanceId,
        uint16_t targetAppGroupMask_ = kDefaultTargetGroupMask)
      : eventType(eventType_),
        receivedTimeMillis(getTimeMillis()),
        eventData(eventData_),
        freeCallback(freeCallback_),
        senderInstanceId(senderInstanceId_),
        targetInstanceId(targetInstanceId_),
        targetAppGroupMask(targetAppGroupMask_),
        isLowPriority(isLowPriority_) {
    // Sending events to the system must only be done via the other constructor
    CHRE_ASSERT(targetInstanceId_ != kSystemInstanceId);
    CHRE_ASSERT(targetAppGroupMask_ > 0);
  }

  // Alternative constructor used for system-internal events (e.g. deferred
  // callbacks)
  Event(uint16_t eventType_, void *eventData_,
        SystemEventCallbackFunction *systemEventCallback_, void *extraData_)
      : eventType(eventType_),
        receivedTimeMillis(getTimeMillis()),
        eventData(eventData_),
        systemEventCallback(systemEventCallback_),
        extraData(extraData_),
        targetInstanceId(kSystemInstanceId),
        targetAppGroupMask(kDefaultTargetGroupMask),
        isLowPriority(false) {
    // Posting events to the system must always have a corresponding callback
    CHRE_ASSERT(systemEventCallback_ != nullptr);
  }

  void incrementRefCount() {
    mRefCount++;
    CHRE_ASSERT(mRefCount != 0);
  }

  void decrementRefCount() {
    CHRE_ASSERT(mRefCount > 0);
    mRefCount--;
  }

  bool isUnreferenced() const {
    return (mRefCount == 0);
  }

  //! @return true if this event has an associated callback which needs to be
  //! called prior to deallocating the event
  bool hasFreeCallback() {
    return (targetInstanceId == kSystemInstanceId || freeCallback != nullptr);
  }

  /**
   * Invoke the callback associated with this event with the applicable function
   * signature (passing extraData if this is a system event).
   *
   * The caller MUST confirm that hasFreeCallback() is true before calling this
   * method.
   */
  void invokeFreeCallback() {
    if (targetInstanceId == kSystemInstanceId) {
      systemEventCallback(eventType, eventData, extraData);
    } else {
      freeCallback(eventType, eventData);
    }
  }

  const uint16_t eventType;

  //! This value can serve as a proxy for how fast CHRE is processing events
  //! in its queue by substracting the newest event timestamp by the oldest one.
  const uint16_t receivedTimeMillis;
  void *const eventData;

  //! If targetInstanceId is kSystemInstanceId, senderInstanceId is always
  //! kSystemInstanceId (nanoapps can't send events to the system), so we
  //! utilize that to allow an extra 32 bits of data to be passed to the
  //! callback, which can reduce dynamic allocation in several cases. Therefore,
  //! if targetInstanceId == kSystemInstanceId, then we use the latter two
  //! elements in the following two unions
  union {
    chreEventCompleteFunction *const freeCallback;
    SystemEventCallbackFunction *const systemEventCallback;
  };
  union {
    const uint16_t senderInstanceId;
    void *const extraData;
  };
  const uint16_t targetInstanceId;

  // Bitmask that's used to limit the event delivery to some subset of listeners
  // registered for this type of event (useful when waking up listeners that can
  // have different power considerations). When left as the default value
  // (kDefaultTargetGroupMask), this has the same behavior as broadcasting to
  // all registered listeners.
  const uint16_t targetAppGroupMask;

  const bool isLowPriority;

 private:
  uint8_t mRefCount = 0;

  //! @return Monotonic time reference for initializing receivedTimeMillis
  static uint16_t getTimeMillis();
};

}  // namespace chre

#endif  // CHRE_CORE_EVENT_H_

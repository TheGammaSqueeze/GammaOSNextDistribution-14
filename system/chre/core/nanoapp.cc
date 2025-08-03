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

#include "chre/core/nanoapp.h"

#include "chre/core/event_loop_manager.h"
#include "chre/platform/assert.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/platform/tracing.h"
#include "chre/util/system/debug_dump.h"
#include "chre_api/chre/gnss.h"
#include "chre_api/chre/version.h"

#include <algorithm>
#include <cstdint>

#if CHRE_FIRST_SUPPORTED_API_VERSION < CHRE_API_VERSION_1_5
#define CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
#endif

namespace chre {

constexpr size_t Nanoapp::kMaxSizeWakeupBuckets;

Nanoapp::Nanoapp()
    : Nanoapp(EventLoopManagerSingleton::get()->getNextInstanceId()) {}

Nanoapp::Nanoapp(uint16_t instanceId) {
  // Push first bucket onto wakeup bucket queue
  cycleWakeupBuckets(SystemTime::getMonotonicTime());
  mInstanceId = instanceId;
}

bool Nanoapp::start() {
  // TODO(b/294116163): update trace with nanoapp instance id and nanoapp name
  CHRE_TRACE_INSTANT("Nanoapp start");
  mIsInNanoappStart = true;
  bool success = PlatformNanoapp::start();
  mIsInNanoappStart = false;
  return success;
}

bool Nanoapp::isRegisteredForBroadcastEvent(const Event *event) const {
  bool registered = false;
  uint16_t eventType = event->eventType;
  uint16_t targetGroupIdMask = event->targetAppGroupMask;

  // The host endpoint notification is a special case, because it requires
  // explicit registration using host endpoint IDs rather than masks.
  if (eventType == CHRE_EVENT_HOST_ENDPOINT_NOTIFICATION) {
    const auto *data =
        static_cast<const chreHostEndpointNotification *>(event->eventData);
    registered = isRegisteredForHostEndpointNotifications(data->hostEndpointId);
  } else {
    size_t foundIndex = registrationIndex(eventType);
    if (foundIndex < mRegisteredEvents.size()) {
      const EventRegistration &reg = mRegisteredEvents[foundIndex];
      if (targetGroupIdMask & reg.groupIdMask) {
        registered = true;
      }
    }
  }
  return registered;
}

void Nanoapp::registerForBroadcastEvent(uint16_t eventType,
                                        uint16_t groupIdMask) {
  size_t foundIndex = registrationIndex(eventType);
  if (foundIndex < mRegisteredEvents.size()) {
    mRegisteredEvents[foundIndex].groupIdMask |= groupIdMask;
  } else if (!mRegisteredEvents.push_back(
                 EventRegistration(eventType, groupIdMask))) {
    FATAL_ERROR_OOM();
  }
}

void Nanoapp::unregisterForBroadcastEvent(uint16_t eventType,
                                          uint16_t groupIdMask) {
  size_t foundIndex = registrationIndex(eventType);
  if (foundIndex < mRegisteredEvents.size()) {
    EventRegistration &reg = mRegisteredEvents[foundIndex];
    reg.groupIdMask &= ~groupIdMask;
    if (reg.groupIdMask == 0) {
      mRegisteredEvents.erase(foundIndex);
    }
  }
}

void Nanoapp::configureNanoappInfoEvents(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_NANOAPP_STARTED);
    registerForBroadcastEvent(CHRE_EVENT_NANOAPP_STOPPED);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_NANOAPP_STARTED);
    unregisterForBroadcastEvent(CHRE_EVENT_NANOAPP_STOPPED);
  }
}

void Nanoapp::configureHostSleepEvents(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_HOST_AWAKE);
    registerForBroadcastEvent(CHRE_EVENT_HOST_ASLEEP);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_HOST_AWAKE);
    unregisterForBroadcastEvent(CHRE_EVENT_HOST_ASLEEP);
  }
}

void Nanoapp::configureDebugDumpEvent(bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_DEBUG_DUMP);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_DEBUG_DUMP);
  }
}

void Nanoapp::configureUserSettingEvent(uint8_t setting, bool enable) {
  if (enable) {
    registerForBroadcastEvent(CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT + setting);
  } else {
    unregisterForBroadcastEvent(CHRE_EVENT_SETTING_CHANGED_FIRST_EVENT +
                                setting);
  }
}

void Nanoapp::processEvent(Event *event) {
  Nanoseconds eventStartTime = SystemTime::getMonotonicTime();
  // TODO(b/294116163): update trace with event type and nanoapp name so it can
  //                    be differentiated from other events
  CHRE_TRACE_START("Handle event", "nanoapp", getInstanceId());
  if (event->eventType == CHRE_EVENT_GNSS_DATA) {
    handleGnssMeasurementDataEvent(event);
  } else {
    handleEvent(event->senderInstanceId, event->eventType, event->eventData);
  }
  // TODO(b/294116163): update trace with nanoapp name
  CHRE_TRACE_END("Handle event", "nanoapp", getInstanceId());
  Nanoseconds eventProcessTime =
      SystemTime::getMonotonicTime() - eventStartTime;
  uint64_t eventTimeMs = Milliseconds(eventProcessTime).getMilliseconds();
  if (Milliseconds(eventProcessTime) >= Milliseconds(100)) {
    LOGE("Nanoapp 0x%" PRIx64 " took %" PRIu64
         " ms to process event type 0x%" PRIx16,
         getAppId(), eventTimeMs, event->eventType);
  }
  mEventProcessTime.addValue(eventTimeMs);
  mEventProcessTimeSinceBoot += eventTimeMs;
  mWakeupBuckets.back().eventProcessTime += eventTimeMs;
}

void Nanoapp::blameHostWakeup() {
  if (mWakeupBuckets.back().wakeupCount < UINT16_MAX) {
    ++mWakeupBuckets.back().wakeupCount;
  }
  if (mNumWakeupsSinceBoot < UINT32_MAX) ++mNumWakeupsSinceBoot;
}

void Nanoapp::blameHostMessageSent() {
  if (mWakeupBuckets.back().hostMessageCount < UINT16_MAX) {
    ++mWakeupBuckets.back().hostMessageCount;
  }
  if (mNumMessagesSentSinceBoot < UINT32_MAX) ++mNumMessagesSentSinceBoot;
}

void Nanoapp::cycleWakeupBuckets(Nanoseconds timestamp) {
  if (mWakeupBuckets.full()) {
    mWakeupBuckets.erase(0);
  }
  mWakeupBuckets.push_back(
      BucketedStats(0, 0, 0, timestamp.toRawNanoseconds()));
}

void Nanoapp::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print(" Id=%" PRIu16 " 0x%016" PRIx64 " ", getInstanceId(),
                  getAppId());
  PlatformNanoapp::logStateToBuffer(debugDump);
  debugDump.print(" v%" PRIu32 ".%" PRIu32 ".%" PRIu32 " tgtAPI=%" PRIu32
                  ".%" PRIu32 "\n",
                  CHRE_EXTRACT_MAJOR_VERSION(getAppVersion()),
                  CHRE_EXTRACT_MINOR_VERSION(getAppVersion()),
                  CHRE_EXTRACT_PATCH_VERSION(getAppVersion()),
                  CHRE_EXTRACT_MAJOR_VERSION(getTargetApiVersion()),
                  CHRE_EXTRACT_MINOR_VERSION(getTargetApiVersion()));
}

void Nanoapp::logMemAndComputeHeader(DebugDumpWrapper &debugDump) const {
  // Print table header
  // Nanoapp column sized to accommodate largest known name
  debugDump.print("\n%10sNanoapp%9s| Mem Alloc (Bytes) |%7sEvent Time (Ms)\n",
                  "", "", "");
  debugDump.print("%26s| Current |     Max |    Mean |     Max |   Total\n",
                  "");
}

void Nanoapp::logMemAndComputeEntry(DebugDumpWrapper &debugDump) const {
  debugDump.print("%25s |", getAppName());
  debugDump.print(" %7zu |", getTotalAllocatedBytes());
  debugDump.print(" %7zu |", getPeakAllocatedBytes());
  debugDump.print(" %7" PRIu64 " |", mEventProcessTime.getMean());
  debugDump.print(" %7" PRIu64 " |", mEventProcessTime.getMax());
  debugDump.print(" %7" PRIu64 "\n", mEventProcessTimeSinceBoot);
}

void Nanoapp::logMessageHistoryHeader(DebugDumpWrapper &debugDump) const {
  // Print time ranges for buckets
  Nanoseconds now = SystemTime::getMonotonicTime();
  uint64_t currentTimeMins = 0;
  uint64_t nextTimeMins = 0;
  uint64_t nanosecondsSince = 0;
  char bucketLabel = 'A';

  char bucketTags[kMaxSizeWakeupBuckets][4];
  for (int32_t i = kMaxSizeWakeupBuckets - 1; i >= 0; --i) {
    bucketTags[i][0] = '[';
    bucketTags[i][1] = bucketLabel++;
    bucketTags[i][2] = ']';
    bucketTags[i][3] = '\0';
  }

  debugDump.print(
      "\nHistogram stat buckets cover the following time ranges:\n");

  for (int32_t i = kMaxSizeWakeupBuckets - 1;
       i > static_cast<int32_t>(mWakeupBuckets.size() - 1); --i) {
    debugDump.print(" Bucket%s: N/A (unused)\n", bucketTags[i]);
  }

  for (int32_t i = static_cast<int32_t>(mWakeupBuckets.size() - 1); i >= 0;
       --i) {
    size_t idx = static_cast<size_t>(i);
    nanosecondsSince =
        now.toRawNanoseconds() - mWakeupBuckets[idx].creationTimestamp;
    currentTimeMins = (nanosecondsSince / kOneMinuteInNanoseconds);

    debugDump.print(" Bucket%s:", bucketTags[idx]);
    debugDump.print(" %3" PRIu64 "", nextTimeMins);
    debugDump.print(" - %3" PRIu64 " mins ago\n", currentTimeMins);
    nextTimeMins = currentTimeMins;
  }

  // Precompute column widths for Wakeup Histogram, Message Histogram, and Event
  // Time Histogram (ms). This allows the column width to be known and optimized
  // at compile time, and avoids use of inconsistently supported "%*" in printf
  //
  // A static_assert is used to ensure these calculations are updated whenever
  // the value of kMaxSizeWakeupBuckets changes
  static_assert(kMaxSizeWakeupBuckets == 5,
                "Update of nanoapp debug dump column widths requrired");

  // Print table header
  debugDump.print("\n%26s|", " Nanoapp ");
  debugDump.print("%11s|", " Total w/u ");
  // Wakeup Histogram = 2 + (4 * kMaxSizeWakeupBuckets);
  debugDump.print("%22s|", " Wakeup Histogram ");
  debugDump.print("%12s|", " Total Msgs ");
  // Message Histogram = 2 + (4 * kMaxSizeWakeupBuckets);
  debugDump.print("%22s|", " Message Histogram ");
  debugDump.print("%12s|", " Event Time ");
  // Event Time Histogram (ms) = 2 + (7 * kMaxSizeWakeupBuckets);
  debugDump.print("%37s", " Event Time Histogram (ms) ");

  debugDump.print("\n%26s|%11s|", "", "");
  for (int32_t i = kMaxSizeWakeupBuckets - 1; i >= 0; --i) {
    debugDump.print(" %3s", bucketTags[i]);
  }
  debugDump.print("  |%12s|", "");
  for (int32_t i = kMaxSizeWakeupBuckets - 1; i >= 0; --i) {
    debugDump.print(" %3s", bucketTags[i]);
  }
  debugDump.print("  |%12s|", "");
  for (int32_t i = kMaxSizeWakeupBuckets - 1; i >= 0; --i) {
    debugDump.print(" %7s", bucketTags[i]);
  }
  debugDump.print("\n");
}

void Nanoapp::logMessageHistoryEntry(DebugDumpWrapper &debugDump) const {
  debugDump.print("%25s |", getAppName());

  // Print wakeupCount and histogram
  debugDump.print(" %9" PRIu32 " | ", mNumWakeupsSinceBoot);
  for (size_t i = kMaxSizeWakeupBuckets - 1; i > 0; --i) {
    if (i >= mWakeupBuckets.size()) {
      debugDump.print(" --,");
    } else {
      debugDump.print(" %2" PRIu16 ",", mWakeupBuckets[i].wakeupCount);
    }
  }
  debugDump.print(" %2" PRIu16 "  |", mWakeupBuckets.front().wakeupCount);

  // Print hostMessage count and histogram
  debugDump.print(" %10" PRIu32 " | ", mNumMessagesSentSinceBoot);
  for (size_t i = kMaxSizeWakeupBuckets - 1; i > 0; --i) {
    if (i >= mWakeupBuckets.size()) {
      debugDump.print(" --,");
    } else {
      debugDump.print(" %2" PRIu16 ",", mWakeupBuckets[i].hostMessageCount);
    }
  }
  debugDump.print(" %2" PRIu16 "  |", mWakeupBuckets.front().hostMessageCount);

  // Print eventProcessingTime count and histogram
  debugDump.print(" %10" PRIu64 " | ", mEventProcessTimeSinceBoot);
  for (size_t i = kMaxSizeWakeupBuckets - 1; i > 0; --i) {
    if (i >= mWakeupBuckets.size()) {
      debugDump.print("     --,");
    } else {
      debugDump.print(" %6" PRIu64 ",", mWakeupBuckets[i].eventProcessTime);
    }
  }
  debugDump.print(" %6" PRIu64 "\n", mWakeupBuckets.front().eventProcessTime);
}

bool Nanoapp::permitPermissionUse(uint32_t permission) const {
  return !supportsAppPermissions() ||
         ((getAppPermissions() & permission) == permission);
}

size_t Nanoapp::registrationIndex(uint16_t eventType) const {
  size_t foundIndex = 0;
  for (; foundIndex < mRegisteredEvents.size(); ++foundIndex) {
    const EventRegistration &reg = mRegisteredEvents[foundIndex];
    if (reg.eventType == eventType) {
      break;
    }
  }
  return foundIndex;
}

void Nanoapp::handleGnssMeasurementDataEvent(const Event *event) {
#ifdef CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
  const struct chreGnssDataEvent *data =
      static_cast<const struct chreGnssDataEvent *>(event->eventData);
  if (getTargetApiVersion() < CHRE_API_VERSION_1_5 &&
      data->measurement_count > CHRE_GNSS_MAX_MEASUREMENT_PRE_1_5) {
    chreGnssDataEvent localEvent;
    memcpy(&localEvent, data, sizeof(struct chreGnssDataEvent));
    localEvent.measurement_count = CHRE_GNSS_MAX_MEASUREMENT_PRE_1_5;
    handleEvent(event->senderInstanceId, event->eventType, &localEvent);
  } else
#endif  // CHRE_GNSS_MEASUREMENT_BACK_COMPAT_ENABLED
  {
    handleEvent(event->senderInstanceId, event->eventType, event->eventData);
  }
}

bool Nanoapp::configureHostEndpointNotifications(uint16_t hostEndpointId,
                                                 bool enable) {
  bool success = true;
  bool registered = isRegisteredForHostEndpointNotifications(hostEndpointId);
  if (enable && !registered) {
    success = mRegisteredHostEndpoints.push_back(hostEndpointId);
    if (!success) {
      LOG_OOM();
    }
  } else if (!enable && registered) {
    size_t index = mRegisteredHostEndpoints.find(hostEndpointId);
    mRegisteredHostEndpoints.erase(index);
  }

  return success;
}

bool Nanoapp::publishRpcServices(struct chreNanoappRpcService *services,
                                 size_t numServices) {
  if (!mIsInNanoappStart) {
    LOGE("publishRpcServices must be called from nanoappStart");
    return false;
  }

  const size_t startSize = mRpcServices.size();
  const size_t endSize = startSize + numServices;
  if (endSize > kMaxRpcServices) {
    return false;
  }

  mRpcServices.reserve(endSize);

  bool success = true;

  for (size_t i = 0; i < numServices; i++) {
    if (!mRpcServices.push_back(services[i])) {
      LOG_OOM();
      success = false;
      break;
    }
  }

  if (success && mRpcServices.size() > 1) {
    for (size_t i = 0; i < mRpcServices.size() - 1; i++) {
      for (size_t j = i + 1; j < mRpcServices.size(); j++) {
        if (mRpcServices[i].id == mRpcServices[j].id) {
          LOGE("Service id = 0x%016" PRIx64 " can only be published once",
               mRpcServices[i].id);
          success = false;
        }
      }
    }
  }

  if (!success) {
    mRpcServices.resize(startSize);
  }

  return success;
}

void Nanoapp::linkHeapBlock(HeapBlockHeader *header) {
  header->data.next = mFirstHeader;
  mFirstHeader = header;
}

void Nanoapp::unlinkHeapBlock(HeapBlockHeader *header) {
  if (mFirstHeader == nullptr) {
    // The list is empty.
    return;
  }

  if (header == mFirstHeader) {
    mFirstHeader = header->data.next;
    return;
  }

  HeapBlockHeader *previous = mFirstHeader;
  HeapBlockHeader *current = mFirstHeader->data.next;

  while (current != nullptr) {
    if (current == header) {
      previous->data.next = current->data.next;
      break;
    }
    previous = current;
    current = current->data.next;
  }
}

}  // namespace chre

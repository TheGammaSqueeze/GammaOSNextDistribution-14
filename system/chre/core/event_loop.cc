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

#include "chre/core/event_loop.h"
#include <cinttypes>
#include <cstdint>

#include "chre/core/event.h"
#include "chre/core/event_loop_manager.h"
#include "chre/core/nanoapp.h"
#include "chre/platform/assert.h"
#include "chre/platform/context.h"
#include "chre/platform/fatal_error.h"
#include "chre/platform/log.h"
#include "chre/platform/system_time.h"
#include "chre/util/conditional_lock_guard.h"
#include "chre/util/lock_guard.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/event_callbacks.h"
#include "chre/util/system/stats_container.h"
#include "chre/util/time.h"
#include "chre_api/chre/version.h"

namespace chre {

// Out of line declaration required for nonintegral static types
constexpr Nanoseconds EventLoop::kIntervalWakeupBucket;

namespace {

#ifndef CHRE_STATIC_EVENT_LOOP
using DynamicMemoryPool =
    SynchronizedExpandableMemoryPool<Event, CHRE_EVENT_PER_BLOCK,
                                     CHRE_MAX_EVENT_BLOCKS>;
#endif
// TODO(b/264108686): Make this a compile time parameter.
// How many low priority event to remove if the event queue is full
// and a new event needs to be pushed.
constexpr size_t targetLowPriorityEventRemove = 4;

/**
 * Populates a chreNanoappInfo structure using info from the given Nanoapp
 * instance.
 *
 * @param app A potentially null pointer to the Nanoapp to read from
 * @param info The structure to populate - should not be null, but this function
 *        will handle that input
 *
 * @return true if neither app nor info were null, and info was populated
 */
bool populateNanoappInfo(const Nanoapp *app, struct chreNanoappInfo *info) {
  bool success = false;

  if (app != nullptr && info != nullptr) {
    info->appId = app->getAppId();
    info->version = app->getAppVersion();
    info->instanceId = app->getInstanceId();
    if (app->getTargetApiVersion() >= CHRE_API_VERSION_1_8) {
      CHRE_ASSERT(app->getRpcServices().size() <= Nanoapp::kMaxRpcServices);
      info->rpcServiceCount =
          static_cast<uint8_t>(app->getRpcServices().size());
      info->rpcServices = app->getRpcServices().data();
      memset(&info->reserved, 0, sizeof(info->reserved));
    }
    success = true;
  }

  return success;
}

#ifndef CHRE_STATIC_EVENT_LOOP
/**
 * @return true if a event is a low priority event.
 */
bool isLowPriorityEvent(Event *event) {
  CHRE_ASSERT_NOT_NULL(event);
  return event->isLowPriority;
}

void deallocateFromMemoryPool(Event *event, void *memoryPool) {
  static_cast<DynamicMemoryPool *>(memoryPool)->deallocate(event);
}
#endif

}  // anonymous namespace

bool EventLoop::findNanoappInstanceIdByAppId(uint64_t appId,
                                             uint16_t *instanceId) const {
  CHRE_ASSERT(instanceId != nullptr);
  ConditionalLockGuard<Mutex> lock(mNanoappsLock, !inEventLoopThread());

  bool found = false;
  for (const UniquePtr<Nanoapp> &app : mNanoapps) {
    if (app->getAppId() == appId) {
      *instanceId = app->getInstanceId();
      found = true;
      break;
    }
  }

  return found;
}

void EventLoop::forEachNanoapp(NanoappCallbackFunction *callback, void *data) {
  ConditionalLockGuard<Mutex> lock(mNanoappsLock, !inEventLoopThread());

  for (const UniquePtr<Nanoapp> &nanoapp : mNanoapps) {
    callback(nanoapp.get(), data);
  }
}

void EventLoop::invokeMessageFreeFunction(uint64_t appId,
                                          chreMessageFreeFunction *freeFunction,
                                          void *message, size_t messageSize) {
  Nanoapp *nanoapp = lookupAppByAppId(appId);
  if (nanoapp == nullptr) {
    LOGE("Couldn't find app 0x%016" PRIx64 " for message free callback", appId);
  } else {
    auto prevCurrentApp = mCurrentApp;
    mCurrentApp = nanoapp;
    freeFunction(message, messageSize);
    mCurrentApp = prevCurrentApp;
  }
}

void EventLoop::run() {
  LOGI("EventLoop start");

  while (mRunning) {
    // Events are delivered in a single stage: they arrive in the inbound event
    // queue mEvents (potentially posted from another thread), then within
    // this context these events are distributed to all interested Nanoapps,
    // with their free callback invoked after distribution.
    mEventPoolUsage.addValue(static_cast<uint32_t>(mEvents.size()));

    // mEvents.pop() will be a blocking call if mEvents.empty()
    Event *event = mEvents.pop();
    // Need size() + 1 since the to-be-processed event has already been removed.
    mPowerControlManager.preEventLoopProcess(mEvents.size() + 1);
    distributeEvent(event);

    mPowerControlManager.postEventLoopProcess(mEvents.size());
  }

  // Purge the main queue of events pending distribution. All nanoapps should be
  // prevented from sending events or messages at this point via
  // currentNanoappIsStopping() returning true.
  while (!mEvents.empty()) {
    freeEvent(mEvents.pop());
  }

  // Unload all running nanoapps
  while (!mNanoapps.empty()) {
    unloadNanoappAtIndex(mNanoapps.size() - 1);
  }

  LOGI("Exiting EventLoop");
}

bool EventLoop::startNanoapp(UniquePtr<Nanoapp> &nanoapp) {
  CHRE_ASSERT(!nanoapp.isNull());
  bool success = false;
  auto *eventLoopManager = EventLoopManagerSingleton::get();
  EventLoop &eventLoop = eventLoopManager->getEventLoop();
  uint16_t existingInstanceId;

  if (nanoapp.isNull()) {
    // no-op, invalid argument
  } else if (nanoapp->getTargetApiVersion() <
             CHRE_FIRST_SUPPORTED_API_VERSION) {
    LOGE("Incompatible nanoapp (target ver 0x%" PRIx32
         ", first supported ver 0x%" PRIx32 ")",
         nanoapp->getTargetApiVersion(),
         static_cast<uint32_t>(CHRE_FIRST_SUPPORTED_API_VERSION));
  } else if (eventLoop.findNanoappInstanceIdByAppId(nanoapp->getAppId(),
                                                    &existingInstanceId)) {
    LOGE("App with ID 0x%016" PRIx64 " already exists as instance ID %" PRIu16,
         nanoapp->getAppId(), existingInstanceId);
  } else {
    Nanoapp *newNanoapp = nanoapp.get();
    {
      LockGuard<Mutex> lock(mNanoappsLock);
      success = mNanoapps.push_back(std::move(nanoapp));
      // After this point, nanoapp is null as we've transferred ownership into
      // mNanoapps.back() - use newNanoapp to reference it
    }
    if (!success) {
      LOG_OOM();
    } else {
      mCurrentApp = newNanoapp;
      success = newNanoapp->start();
      mCurrentApp = nullptr;
      if (!success) {
        LOGE("Nanoapp %" PRIu16 " failed to start",
             newNanoapp->getInstanceId());
        unloadNanoapp(newNanoapp->getInstanceId(),
                      /*allowSystemNanoappUnload=*/true,
                      /*nanoappStarted=*/false);
      } else {
        notifyAppStatusChange(CHRE_EVENT_NANOAPP_STARTED, *newNanoapp);
      }
    }
  }

  return success;
}

bool EventLoop::unloadNanoapp(uint16_t instanceId,
                              bool allowSystemNanoappUnload,
                              bool nanoappStarted) {
  bool unloaded = false;

  for (size_t i = 0; i < mNanoapps.size(); i++) {
    if (instanceId == mNanoapps[i]->getInstanceId()) {
      if (!allowSystemNanoappUnload && mNanoapps[i]->isSystemNanoapp()) {
        LOGE("Refusing to unload system nanoapp");
      } else {
        // Make sure all messages sent by this nanoapp at least have their
        // associated free callback processing pending in the event queue (i.e.
        // there are no messages pending delivery to the host)
        EventLoopManagerSingleton::get()
            ->getHostCommsManager()
            .flushMessagesSentByNanoapp(mNanoapps[i]->getAppId());

        // Mark that this nanoapp is stopping early, so it can't send events or
        // messages during the nanoapp event queue flush
        mStoppingNanoapp = mNanoapps[i].get();

        // Distribute all inbound events we have at this time - here we're
        // interested in handling any message free callbacks generated by
        // flushInboundEventQueue()
        flushInboundEventQueue();

        // Post the unload event now (so we can reference the Nanoapp instance
        // directly), but nanoapps won't get it until after the unload
        // completes. No need to notify status change if nanoapps failed to
        // start.
        if (nanoappStarted) {
          notifyAppStatusChange(CHRE_EVENT_NANOAPP_STOPPED, *mStoppingNanoapp);
        }

        // Finally, we are at a point where there should not be any pending
        // events or messages sent by the app that could potentially reference
        // the nanoapp's memory, so we are safe to unload it
        unloadNanoappAtIndex(i, nanoappStarted);
        mStoppingNanoapp = nullptr;

        LOGD("Unloaded nanoapp with instanceId %" PRIu16, instanceId);
        unloaded = true;
      }
      break;
    }
  }

  return unloaded;
}

bool EventLoop::removeLowPriorityEventsFromBack(size_t removeNum) {
#ifdef CHRE_STATIC_EVENT_LOOP
  return false;
#else
  if (removeNum == 0) {
    return true;
  }

  size_t numRemovedEvent = mEvents.removeMatchedFromBack(
      isLowPriorityEvent, removeNum, deallocateFromMemoryPool, &mEventPool);
  if (numRemovedEvent == 0 || numRemovedEvent == SIZE_MAX) {
    LOGW("Cannot remove any low priority event");
  } else {
    mNumDroppedLowPriEvents += numRemovedEvent;
  }
  return numRemovedEvent > 0;
#endif
}

bool EventLoop::hasNoSpaceForHighPriorityEvent() {
  return mEventPool.full() &&
         !removeLowPriorityEventsFromBack(targetLowPriorityEventRemove);
}

// TODO(b/264108686): Refactor this function and postSystemEvent
void EventLoop::postEventOrDie(uint16_t eventType, void *eventData,
                               chreEventCompleteFunction *freeCallback,
                               uint16_t targetInstanceId,
                               uint16_t targetGroupMask) {
  if (mRunning) {
    if (hasNoSpaceForHighPriorityEvent() ||
        !allocateAndPostEvent(eventType, eventData, freeCallback,
                              /* isLowPriority= */ false, kSystemInstanceId,
                              targetInstanceId, targetGroupMask)) {
      FATAL_ERROR("Failed to post critical system event 0x%" PRIx16, eventType);
    }
  } else if (freeCallback != nullptr) {
    freeCallback(eventType, eventData);
  }
}

bool EventLoop::postSystemEvent(uint16_t eventType, void *eventData,
                                SystemEventCallbackFunction *callback,
                                void *extraData) {
  if (!mRunning) {
    return false;
  }

  if (hasNoSpaceForHighPriorityEvent()) {
    FATAL_ERROR("Failed to post critical system event 0x%" PRIx16
                ": Full of high priority "
                "events",
                eventType);
  }

  Event *event = mEventPool.allocate(eventType, eventData, callback, extraData);
  if (event == nullptr || !mEvents.push(event)) {
    FATAL_ERROR("Failed to post critical system event 0x%" PRIx16
                ": out of memory",
                eventType);
  }

  return true;
}

bool EventLoop::postLowPriorityEventOrFree(
    uint16_t eventType, void *eventData,
    chreEventCompleteFunction *freeCallback, uint16_t senderInstanceId,
    uint16_t targetInstanceId, uint16_t targetGroupMask) {
  bool eventPosted = false;

  if (mRunning) {
    eventPosted =
        allocateAndPostEvent(eventType, eventData, freeCallback,
                             /* isLowPriority= */ true, senderInstanceId,
                             targetInstanceId, targetGroupMask);
    if (!eventPosted) {
      LOGE("Failed to allocate event 0x%" PRIx16 " to instanceId %" PRIu16,
           eventType, targetInstanceId);
      ++mNumDroppedLowPriEvents;
    }
  }

  if (!eventPosted && freeCallback != nullptr) {
    freeCallback(eventType, eventData);
  }

  return eventPosted;
}

void EventLoop::stop() {
  auto callback = [](uint16_t /*type*/, void *data, void * /*extraData*/) {
    auto *obj = static_cast<EventLoop *>(data);
    obj->onStopComplete();
  };

  // Stop accepting new events and tell the main loop to finish
  postSystemEvent(static_cast<uint16_t>(SystemCallbackType::Shutdown),
                  /*eventData=*/this, callback, /*extraData=*/nullptr);
}

void EventLoop::onStopComplete() {
  mRunning = false;
}

Nanoapp *EventLoop::findNanoappByInstanceId(uint16_t instanceId) const {
  ConditionalLockGuard<Mutex> lock(mNanoappsLock, !inEventLoopThread());
  return lookupAppByInstanceId(instanceId);
}

bool EventLoop::populateNanoappInfoForAppId(
    uint64_t appId, struct chreNanoappInfo *info) const {
  ConditionalLockGuard<Mutex> lock(mNanoappsLock, !inEventLoopThread());
  Nanoapp *app = lookupAppByAppId(appId);
  return populateNanoappInfo(app, info);
}

bool EventLoop::populateNanoappInfoForInstanceId(
    uint16_t instanceId, struct chreNanoappInfo *info) const {
  ConditionalLockGuard<Mutex> lock(mNanoappsLock, !inEventLoopThread());
  Nanoapp *app = lookupAppByInstanceId(instanceId);
  return populateNanoappInfo(app, info);
}

bool EventLoop::currentNanoappIsStopping() const {
  return (mCurrentApp == mStoppingNanoapp || !mRunning);
}

void EventLoop::logStateToBuffer(DebugDumpWrapper &debugDump) const {
  debugDump.print("\nEvent Loop:\n");
  debugDump.print("  Max event pool usage: %" PRIu32 "/%zu\n",
                  mEventPoolUsage.getMax(), kMaxEventCount);
  debugDump.print("  Number of low priority events dropped: %" PRIu32 "\n",
                  mNumDroppedLowPriEvents);
  debugDump.print("  Mean event pool usage: %" PRIu32 "/%zu\n",
                  mEventPoolUsage.getMean(), kMaxEventCount);

  Nanoseconds timeSince =
      SystemTime::getMonotonicTime() - mTimeLastWakeupBucketCycled;
  uint64_t timeSinceMins =
      timeSince.toRawNanoseconds() / kOneMinuteInNanoseconds;
  uint64_t durationMins =
      kIntervalWakeupBucket.toRawNanoseconds() / kOneMinuteInNanoseconds;
  debugDump.print("  Nanoapp host wakeup tracking: cycled %" PRIu64
                  " mins ago, bucketDuration=%" PRIu64 "mins\n",
                  timeSinceMins, durationMins);

  debugDump.print("\nNanoapps:\n");

  if (mNanoapps.size()) {
    for (const UniquePtr<Nanoapp> &app : mNanoapps) {
      app->logStateToBuffer(debugDump);
    }

    mNanoapps[0]->logMemAndComputeHeader(debugDump);
    for (const UniquePtr<Nanoapp> &app : mNanoapps) {
      app->logMemAndComputeEntry(debugDump);
    }

    mNanoapps[0]->logMessageHistoryHeader(debugDump);
    for (const UniquePtr<Nanoapp> &app : mNanoapps) {
      app->logMessageHistoryEntry(debugDump);
    }
  }
}

bool EventLoop::allocateAndPostEvent(uint16_t eventType, void *eventData,
                                     chreEventCompleteFunction *freeCallback,
                                     bool isLowPriority,
                                     uint16_t senderInstanceId,
                                     uint16_t targetInstanceId,
                                     uint16_t targetGroupMask) {
  bool success = false;

  Event *event =
      mEventPool.allocate(eventType, eventData, freeCallback, isLowPriority,
                          senderInstanceId, targetInstanceId, targetGroupMask);
  if (event != nullptr) {
    success = mEvents.push(event);
  }

  return success;
}

void EventLoop::deliverNextEvent(const UniquePtr<Nanoapp> &app, Event *event) {
  // TODO: cleaner way to set/clear this? RAII-style?
  mCurrentApp = app.get();
  app->processEvent(event);
  mCurrentApp = nullptr;
}

void EventLoop::distributeEvent(Event *event) {
  bool eventDelivered = false;
  for (const UniquePtr<Nanoapp> &app : mNanoapps) {
    if ((event->targetInstanceId == chre::kBroadcastInstanceId &&
         app->isRegisteredForBroadcastEvent(event)) ||
        event->targetInstanceId == app->getInstanceId()) {
      eventDelivered = true;
      deliverNextEvent(app, event);
    }
  }
  // Log if an event unicast to a nanoapp isn't delivered, as this is could be
  // a bug (e.g. something isn't properly keeping track of when nanoapps are
  // unloaded), though it could just be a harmless transient issue (e.g. race
  // condition with nanoapp unload, where we post an event to a nanoapp just
  // after queues are flushed while it's unloading)
  if (!eventDelivered && event->targetInstanceId != kBroadcastInstanceId &&
      event->targetInstanceId != kSystemInstanceId) {
    LOGW("Dropping event 0x%" PRIx16 " from instanceId %" PRIu16 "->%" PRIu16,
         event->eventType, event->senderInstanceId, event->targetInstanceId);
  }
  CHRE_ASSERT(event->isUnreferenced());
  freeEvent(event);
}

void EventLoop::flushInboundEventQueue() {
  while (!mEvents.empty()) {
    distributeEvent(mEvents.pop());
  }
}

void EventLoop::freeEvent(Event *event) {
  if (event->hasFreeCallback()) {
    // TODO: find a better way to set the context to the creator of the event
    mCurrentApp = lookupAppByInstanceId(event->senderInstanceId);
    event->invokeFreeCallback();
    mCurrentApp = nullptr;
  }

  mEventPool.deallocate(event);
}

Nanoapp *EventLoop::lookupAppByAppId(uint64_t appId) const {
  for (const UniquePtr<Nanoapp> &app : mNanoapps) {
    if (app->getAppId() == appId) {
      return app.get();
    }
  }

  return nullptr;
}

Nanoapp *EventLoop::lookupAppByInstanceId(uint16_t instanceId) const {
  // The system instance ID always has nullptr as its Nanoapp pointer, so can
  // skip iterating through the nanoapp list for that case
  if (instanceId != kSystemInstanceId) {
    for (const UniquePtr<Nanoapp> &app : mNanoapps) {
      if (app->getInstanceId() == instanceId) {
        return app.get();
      }
    }
  }

  return nullptr;
}

void EventLoop::notifyAppStatusChange(uint16_t eventType,
                                      const Nanoapp &nanoapp) {
  auto *info = memoryAlloc<chreNanoappInfo>();
  if (info == nullptr) {
    LOG_OOM();
  } else {
    info->appId = nanoapp.getAppId();
    info->version = nanoapp.getAppVersion();
    info->instanceId = nanoapp.getInstanceId();

    postEventOrDie(eventType, info, freeEventDataCallback);
  }
}

void EventLoop::unloadNanoappAtIndex(size_t index, bool nanoappStarted) {
  const UniquePtr<Nanoapp> &nanoapp = mNanoapps[index];

  // Lock here to prevent the nanoapp instance from being accessed between the
  // time it is ended and fully erased
  LockGuard<Mutex> lock(mNanoappsLock);

  // Let the app know it's going away
  mCurrentApp = nanoapp.get();

  // nanoappEnd() is not invoked for nanoapps that return false in
  // nanoappStart(), per CHRE API
  if (nanoappStarted) {
    nanoapp->end();
  }

  // Cleanup resources.
#ifdef CHRE_WIFI_SUPPORT_ENABLED
  const uint32_t numDisabledWifiSubscriptions =
      EventLoopManagerSingleton::get()
          ->getWifiRequestManager()
          .disableAllSubscriptions(nanoapp.get());
  logDanglingResources("WIFI subscriptions", numDisabledWifiSubscriptions);
#endif  // CHRE_WIFI_SUPPORT_ENABLED

#ifdef CHRE_GNSS_SUPPORT_ENABLED
  const uint32_t numDisabledGnssSubscriptions =
      EventLoopManagerSingleton::get()
          ->getGnssManager()
          .disableAllSubscriptions(nanoapp.get());
  logDanglingResources("GNSS subscriptions", numDisabledGnssSubscriptions);
#endif  // CHRE_GNSS_SUPPORT_ENABLED

#ifdef CHRE_SENSORS_SUPPORT_ENABLED
  const uint32_t numDisabledSensorSubscriptions =
      EventLoopManagerSingleton::get()
          ->getSensorRequestManager()
          .disableAllSubscriptions(nanoapp.get());
  logDanglingResources("Sensor subscriptions", numDisabledSensorSubscriptions);
#endif  // CHRE_SENSORS_SUPPORT_ENABLED

#ifdef CHRE_AUDIO_SUPPORT_ENABLED
  const uint32_t numDisabledAudioRequests =
      EventLoopManagerSingleton::get()
          ->getAudioRequestManager()
          .disableAllAudioRequests(nanoapp.get());
  logDanglingResources("Audio requests", numDisabledAudioRequests);
#endif  // CHRE_AUDIO_SUPPORT_ENABLED

#ifdef CHRE_BLE_SUPPORT_ENABLED
  const uint32_t numDisabledBleScans = EventLoopManagerSingleton::get()
                                           ->getBleRequestManager()
                                           .disableActiveScan(nanoapp.get());
  logDanglingResources("BLE scan", numDisabledBleScans);
#endif  // CHRE_BLE_SUPPORT_ENABLED

  const uint32_t numCancelledTimers =
      getTimerPool().cancelAllNanoappTimers(nanoapp.get());
  logDanglingResources("timers", numCancelledTimers);

  const uint32_t numFreedBlocks =
      EventLoopManagerSingleton::get()->getMemoryManager().nanoappFreeAll(
          nanoapp.get());
  logDanglingResources("heap blocks", numFreedBlocks);

  // Destroy the Nanoapp instance
  mNanoapps.erase(index);

  mCurrentApp = nullptr;
}

void EventLoop::handleNanoappWakeupBuckets() {
  Nanoseconds now = SystemTime::getMonotonicTime();
  Nanoseconds duration = now - mTimeLastWakeupBucketCycled;
  if (duration > kIntervalWakeupBucket) {
    mTimeLastWakeupBucketCycled = now;
    for (auto &nanoapp : mNanoapps) {
      nanoapp->cycleWakeupBuckets(now);
    }
  }
}

void EventLoop::logDanglingResources(const char *name, uint32_t count) {
  if (count > 0) {
    LOGE("App 0x%016" PRIx64 " had %" PRIu32 " remaining %s at unload",
         mCurrentApp->getAppId(), count, name);
  }
}

}  // namespace chre

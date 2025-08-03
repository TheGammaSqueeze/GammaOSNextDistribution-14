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

#ifndef CHRE_CORE_EVENT_LOOP_H_
#define CHRE_CORE_EVENT_LOOP_H_

#include "chre/core/event.h"
#include "chre/core/nanoapp.h"
#include "chre/core/timer_pool.h"
#include "chre/platform/atomic.h"
#include "chre/platform/mutex.h"
#include "chre/platform/platform_nanoapp.h"
#include "chre/platform/power_control_manager.h"
#include "chre/platform/system_time.h"
#include "chre/util/dynamic_vector.h"
#include "chre/util/non_copyable.h"
#include "chre/util/system/debug_dump.h"
#include "chre/util/system/stats_container.h"
#include "chre/util/unique_ptr.h"
#include "chre_api/chre/event.h"

#ifdef CHRE_STATIC_EVENT_LOOP
#include "chre/util/fixed_size_blocking_queue.h"
#include "chre/util/synchronized_memory_pool.h"

// These default values can be overridden in the variant-specific makefile.
#ifndef CHRE_MAX_EVENT_COUNT
#define CHRE_MAX_EVENT_COUNT 96
#endif

#ifndef CHRE_MAX_UNSCHEDULED_EVENT_COUNT
#define CHRE_MAX_UNSCHEDULED_EVENT_COUNT 96
#endif
#else
#include "chre/util/blocking_segmented_queue.h"
#include "chre/util/synchronized_expandable_memory_pool.h"

// These default values can be overridden in the variant-specific makefile.
#ifndef CHRE_EVENT_PER_BLOCK
#define CHRE_EVENT_PER_BLOCK 24
#endif

#ifndef CHRE_MAX_EVENT_BLOCKS
#define CHRE_MAX_EVENT_BLOCKS 4
#endif

#endif

namespace chre {

/**
 * The EventLoop represents a single thread of execution that is shared among
 * zero or more nanoapps. As the name implies, the EventLoop is built around a
 * loop that delivers events to the nanoapps managed within for processing.
 */
class EventLoop : public NonCopyable {
 public:
  EventLoop()
      :
#ifndef CHRE_STATIC_EVENT_LOOP
        mEvents(kMaxEventBlock),
#endif
        mTimeLastWakeupBucketCycled(SystemTime::getMonotonicTime()),
        mRunning(true) {
  }

  /**
   * Synchronous callback used with forEachNanoapp
   */
  typedef void(NanoappCallbackFunction)(const Nanoapp *nanoapp, void *data);

  /**
   * Searches the set of nanoapps managed by this EventLoop for one with the
   * given app ID. If found, provides its instance ID, which can be used to send
   * events to the app.
   *
   * This function is safe to call from any thread.
   *
   * @param appId The nanoapp identifier to search for.
   * @param instanceId If this function returns true, will be populated with the
   *        instanceId associated with the given appId; otherwise unmodified.
   *        Must not be null.
   * @return true if the given app ID was found and instanceId was populated
   */
  bool findNanoappInstanceIdByAppId(uint64_t appId, uint16_t *instanceId) const;

  /*
   * Checks if the new wakeup buckets need to be pushed to nanoapps because the
   * wakeup bucket interval has been surpassed since we pushed and pushes to the
   * apps.
   */
  void handleNanoappWakeupBuckets();

  /**
   * Iterates over the list of Nanoapps managed by this EventLoop, and invokes
   * the supplied callback for each one. This holds a lock if necessary, so it
   * is safe to call from any thread.
   *
   * @param callback Function to invoke on each Nanoapp (synchronously)
   * @param data Arbitrary data to pass to the callback
   */
  void forEachNanoapp(NanoappCallbackFunction *callback, void *data);

  /**
   * Invokes a message to host free callback supplied by the given nanoapp
   * (identified by app ID). Ensures that the calling context is updated
   * appropriately.
   *
   * @param appId Identifies the nanoapp that sent this message and supplied the
   *        free callback
   * @param freeFunction The non-null message free callback given by the nanoapp
   * @param message Pointer to the message data
   * @param messageSize Size of the message
   */
  void invokeMessageFreeFunction(uint64_t appId,
                                 chreMessageFreeFunction *freeFunction,
                                 void *message, size_t messageSize);

  /**
   * Invokes the Nanoapp's start callback, and if successful, adds it to the
   * set of Nanoapps managed by this EventLoop. This function must only be
   * called from the context of the thread that runs this event loop (i.e. from
   * the same thread that will call run() or from a callback invoked within
   * run()).
   *
   * @param nanoapp The nanoapp that will be started. Upon success, this
   *        UniquePtr will become invalid, as the underlying Nanoapp instance
   *        will have been transferred to be managed by this EventLoop.
   * @return true if the app was started successfully
   */
  bool startNanoapp(UniquePtr<Nanoapp> &nanoapp);

  /**
   * Stops and unloads a nanoapp identified by its instance ID. The end entry
   * point will be invoked, and the chre::Nanoapp instance will be destroyed.
   * After this function returns, all references to the Nanoapp instance are
   * invalidated.
   *
   * @param instanceId The nanoapp's unique instance identifier
   * @param allowSystemNanoappUnload If false, this function will reject
   *        attempts to unload a system nanoapp
   * @param nanoappStarted Indicates whether the nanoapp successfully started
   *
   * @return true if the nanoapp with the given instance ID was found & unloaded
   */
  bool unloadNanoapp(uint16_t instanceId, bool allowSystemNanoappUnload,
                     bool nanoappStarted = true);

  /**
   * Executes the loop that blocks on the event queue and delivers received
   * events to nanoapps. Only returns after stop() is called (from another
   * context).
   */
  void run();

  /**
   * Signals the event loop currently executing in run() to exit gracefully at
   * the next available opportunity. This function is thread-safe.
   */
  void stop();

  /**
   * Posts an event to a nanoapp that is currently running (or all nanoapps if
   * the target instance ID is kBroadcastInstanceId). A senderInstanceId cannot
   * be provided to this method because it must only be used to post events
   * sent by the system. If the event fails to post and the event loop thread is
   * running, this is considered a fatal error. If the thread is not running
   * (e.g. CHRE is shutting down), the event is silently dropped and the free
   * callback is invoked prior to returning (if not null).
   *
   * Safe to call from any thread.
   *
   * @param eventType Event type identifier, which implies the type of eventData
   * @param eventData The data being posted
   * @param freeCallback Function to invoke to when the event has been processed
   *        by all recipients; this must be safe to call immediately, to handle
   *        the case where CHRE is shutting down
   * @param targetInstanceId The instance ID of the destination of this event
   * @param targetGroupMask Mask used to limit the recipients that are
   *        registered to receive this event
   *
   * @see postLowPriorityEventOrFree
   */
  void postEventOrDie(uint16_t eventType, void *eventData,
                      chreEventCompleteFunction *freeCallback,
                      uint16_t targetInstanceId = kBroadcastInstanceId,
                      uint16_t targetGroupMask = kDefaultTargetGroupMask);

  /**
   * Posts an event to a nanoapp that is currently running (or all nanoapps if
   * the target instance ID is kBroadcastInstanceId). If the event fails to
   * post, freeCallback is invoked prior to returning (if not null).
   *
   * Safe to call from any thread.
   *
   * @param eventType Event type identifier, which implies the type of eventData
   * @param eventData The data being posted
   * @param freeCallback Function to invoke to when the event has been processed
   *        by all recipients; this must be safe to call immediately, to handle
   *        the case where CHRE is shutting down
   * @param senderInstanceId The instance ID of the sender of this event
   * @param targetInstanceId The instance ID of the destination of this event
   * @param targetGroupMask Mask used to limit the recipients that are
   *        registered to receive this event
   *
   * @return true if the event was successfully added to the queue.
   *
   * @see chreSendEvent
   */
  bool postLowPriorityEventOrFree(
      uint16_t eventType, void *eventData,
      chreEventCompleteFunction *freeCallback,
      uint16_t senderInstanceId = kSystemInstanceId,
      uint16_t targetInstanceId = kBroadcastInstanceId,
      uint16_t targetGroupMask = kDefaultTargetGroupMask);

  /**
   * Posts an event for processing by the system from within the context of the
   * CHRE thread. Uses the same underlying event queue as is used for nanoapp
   * events, but gives the ability to provide an additional data pointer. If the
   * event loop is running and the system event can't be posted (i.e. queue is
   * full), then a fatal error is raised.
   *
   * Safe to call from any thread.
   *
   * @param eventType Event type identifier, which is forwarded to the callback
   * @param eventData Arbitrary data to pass to the callback
   * @param callback Function to invoke from the context of the CHRE thread
   * @param extraData Additional arbitrary data to provide to the callback
   *
   * @return true if successfully posted; false ONLY IF the CHRE event loop is
   *         shutting down and not accepting any new events - in this case,
   *         the callback will not be invoked and any allocated memory must be
   *         cleaned up
   *
   * @see postEventOrDie
   * @see EventLoopManager::deferCallback
   */
  bool postSystemEvent(uint16_t eventType, void *eventData,
                       SystemEventCallbackFunction *callback, void *extraData);

  /**
   * Returns a pointer to the currently executing Nanoapp, or nullptr if none is
   * currently executing. Must only be called from within the thread context
   * associated with this EventLoop.
   *
   * @return the currently executing nanoapp, or nullptr
   */
  Nanoapp *getCurrentNanoapp() const {
    return mCurrentApp;
  }

  /**
   * Gets the number of nanoapps currently associated with this event loop. Must
   * only be called within the context of this EventLoop.
   *
   * @return The number of nanoapps managed by this event loop
   */
  size_t getNanoappCount() const {
    return mNanoapps.size();
  }

  /**
   * Obtains the TimerPool associated with this event loop.
   *
   * @return The timer pool owned by this event loop.
   */
  TimerPool &getTimerPool() {
    return mTimerPool;
  }

  /**
   * Searches the set of nanoapps managed by this EventLoop for one with the
   * given instance ID.
   *
   * This function is safe to call from any thread.
   *
   * @param instanceId The nanoapp instance ID to search for.
   * @return a pointer to the found nanoapp or nullptr if no match was found.
   */
  Nanoapp *findNanoappByInstanceId(uint16_t instanceId) const;

  /**
   * Looks for an app with the given ID and if found, populates info with its
   * metadata. Safe to call from any thread.
   *
   * @see chreGetNanoappInfoByAppId
   */
  bool populateNanoappInfoForAppId(uint64_t appId,
                                   struct chreNanoappInfo *info) const;

  /**
   * Looks for an app with the given instance ID and if found, populates info
   * with its metadata. Safe to call from any thread.
   *
   * @see chreGetNanoappInfoByInstanceId
   */
  bool populateNanoappInfoForInstanceId(uint16_t instanceId,
                                        struct chreNanoappInfo *info) const;

  /**
   * @return true if the current Nanoapp (or entire CHRE) is being unloaded, and
   *         therefore it should not be allowed to send events or messages, etc.
   */
  bool currentNanoappIsStopping() const;

  /**
   * Prints state in a string buffer. Must only be called from the context of
   * the main CHRE thread.
   *
   * @param debugDump The debug dump wrapper where a string can be printed
   *     into one of the buffers.
   */
  void logStateToBuffer(DebugDumpWrapper &debugDump) const;

  /**
   * Returns a reference to the power control manager. This allows power
   * controls from subsystems outside the event loops.
   */
  PowerControlManager &getPowerControlManager() {
    return mPowerControlManager;
  }

  inline uint32_t getMaxEventQueueSize() const {
    return mEventPoolUsage.getMax();
  }

  inline uint32_t getMeanEventQueueSize() const {
    return mEventPoolUsage.getMean();
  }

  inline uint32_t getNumEventsDropped() const {
    return mNumDroppedLowPriEvents;
  }

 private:
#ifdef CHRE_STATIC_EVENT_LOOP
  //! The maximum number of events that can be active in the system.
  static constexpr size_t kMaxEventCount = CHRE_MAX_EVENT_COUNT;

  //! The maximum number of events that are awaiting to be scheduled. These
  //! events are in a queue to be distributed to apps.
  static constexpr size_t kMaxUnscheduledEventCount =
      CHRE_MAX_UNSCHEDULED_EVENT_COUNT;

  //! The memory pool to allocate incoming events from.
  SynchronizedMemoryPool<Event, kMaxEventCount> mEventPool;

  //! The blocking queue of incoming events from the system that have not been
  //! distributed out to apps yet.
  FixedSizeBlockingQueue<Event *, kMaxUnscheduledEventCount> mEvents;

#else
  //! The maximum number of event that can be stored in a block in mEventPool.
  static constexpr size_t kEventPerBlock = CHRE_EVENT_PER_BLOCK;

  //! The maximum number of event blocks that mEventPool can hold.
  static constexpr size_t kMaxEventBlock = CHRE_MAX_EVENT_BLOCKS;

  static constexpr size_t kMaxEventCount =
      CHRE_EVENT_PER_BLOCK * CHRE_MAX_EVENT_BLOCKS;

  //! The memory pool to allocate incoming events from.
  SynchronizedExpandableMemoryPool<Event, kEventPerBlock, kMaxEventBlock>
      mEventPool;

  //! The blocking queue of incoming events from the system that have not been
  //! distributed out to apps yet.
  BlockingSegmentedQueue<Event *, kEventPerBlock> mEvents;
#endif
  //! The time interval of nanoapp wakeup buckets, adjust in conjunction with
  //! Nanoapp::kMaxSizeWakeupBuckets.
  static constexpr Nanoseconds kIntervalWakeupBucket =
      Nanoseconds(180 * kOneMinuteInNanoseconds);

  //! The last time wakeup buckets were pushed onto the nanoapps.
  Nanoseconds mTimeLastWakeupBucketCycled;

  //! The timer used schedule timed events for tasks running in this event loop.
  TimerPool mTimerPool;

  //! The list of nanoapps managed by this event loop.
  DynamicVector<UniquePtr<Nanoapp>> mNanoapps;

  //! This lock *must* be held whenever we:
  //!   (1) make changes to the mNanoapps vector, or
  //!   (2) read the mNanoapps vector from a thread other than the one
  //!       associated with this EventLoop
  //! It is not necessary to acquire the lock when reading mNanoapps from within
  //! the thread context of this EventLoop.
  mutable Mutex mNanoappsLock;

  //! Indicates whether the event loop is running.
  AtomicBool mRunning;

  //! The nanoapp that is currently executing - must be set any time we call
  //! into the nanoapp's entry points or callbacks
  Nanoapp *mCurrentApp = nullptr;

  //! Set to the nanoapp we are in the process of unloading in unloadNanoapp()
  Nanoapp *mStoppingNanoapp = nullptr;

  //! The object which manages power related controls.
  PowerControlManager mPowerControlManager;

  //! The stats collection used to collect event pool usage
  StatsContainer<uint32_t> mEventPoolUsage;

  //! The number of events dropped due to capacity limits
  uint32_t mNumDroppedLowPriEvents = 0;

  /**
   * Modifies the run loop state so it no longer iterates on new events. This
   * should only be invoked by the event loop when it is ready to stop
   * processing new events.
   */
  void onStopComplete();

  /**
   * Allocates an event from the event pool and post it.
   *
   * @return true if the event has been successfully allocated and posted.
   *
   * @see postEventOrDie and postLowPriorityEventOrFree
   */
  bool allocateAndPostEvent(uint16_t eventType, void *eventData,
                            chreEventCompleteFunction *freeCallback,
                            bool isLowPriority, uint16_t senderInstanceId,
                            uint16_t targetInstanceId,
                            uint16_t targetGroupMask);
  /**
   * Remove some low priority events from back of the queue.
   *
   * @param removeNum Number of low priority events to be removed.
   * @return False if cannot remove any low priority event.
   */
  bool removeLowPriorityEventsFromBack(size_t removeNum);

  /**
   * Determine if there are space for high priority event.
   * During the processing of determining the vacant space, it might
   * remove low priority events to make space for high priority event.
   *
   * @return true if there are no space for a new high priority event.
   */
  bool hasNoSpaceForHighPriorityEvent();

  /**
   * Delivers the next event pending to the Nanoapp.
   */
  void deliverNextEvent(const UniquePtr<Nanoapp> &app, Event *event);

  /**
   * Given an event pulled from the main incoming event queue (mEvents), deliver
   * it to all Nanoapps that should receive the event, or free the event if
   * there are no valid recipients.
   *
   * @param event The Event to distribute to Nanoapps
   */
  void distributeEvent(Event *event);

  /**
   * Distribute all events pending in the inbound event queue. Note that this
   * function only guarantees that any events in the inbound queue at the time
   * it is called will be distributed to Nanoapp event queues - new events may
   * still be posted during or after this function call from other threads as
   * long as postEvent() will accept them.
   */
  void flushInboundEventQueue();

  /**
   * Call after when an Event has been delivered to all intended recipients.
   * Invokes the event's free callback (if given) and releases resources.
   *
   * @param event The event to be freed
   */
  void freeEvent(Event *event);

  /**
   * Finds a Nanoapp with the given 64-bit appId.
   *
   * Only safe to call within this EventLoop's thread, or if mNanoappsLock is
   * held.
   *
   * @param appId Nanoapp ID
   * @return Pointer to Nanoapp instance in this EventLoop with the given app
   *         ID, or nullptr if not found
   */
  Nanoapp *lookupAppByAppId(uint64_t appId) const;

  /**
   * Finds a Nanoapp with the given instanceId.
   *
   * Only safe to call within this EventLoop's thread, or if mNanoappsLock is
   * held.
   *
   * @param instanceId Nanoapp instance identifier
   * @return Nanoapp with the given instanceId, or nullptr if not found
   */
  Nanoapp *lookupAppByInstanceId(uint16_t instanceId) const;

  /**
   * Sends an event with payload struct chreNanoappInfo populated from the given
   * Nanoapp instance to inform other nanoapps about it starting/stopping.
   *
   * @param eventType Should be one of CHRE_EVENT_NANOAPP_{STARTED, STOPPED}
   * @param nanoapp The nanoapp instance whose status has changed
   */
  void notifyAppStatusChange(uint16_t eventType, const Nanoapp &nanoapp);

  /**
   * Stops and unloads the Nanoapp at the given index in mNanoapps.
   *
   * IMPORTANT: prior to calling this function, the event queues must be in a
   * safe condition for removal of this nanoapp. This means that there must not
   * be any pending events in this nanoapp's queue, and there must not be any
   * outstanding events sent by this nanoapp, as they may reference the
   * nanoapp's own memory (even if there is no free callback).
   *
   * @param index Index of the nanoapp in the list of nanoapps managed by event
   * loop.
   * @param nanoappStarted Indicates whether the nanoapp successfully started
   */
  void unloadNanoappAtIndex(size_t index, bool nanoappStarted = true);

  /**
   * Logs dangling resources when a nanoapp is unloaded.
   *
   * @param name The name of the resource.
   * @param count The number of dangling resources.
   */
  void logDanglingResources(const char *name, uint32_t count);
};

}  // namespace chre

#endif  // CHRE_CORE_EVENT_LOOP_H_

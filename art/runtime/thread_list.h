/*
 * Copyright (C) 2011 The Android Open Source Project
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

#ifndef ART_RUNTIME_THREAD_LIST_H_
#define ART_RUNTIME_THREAD_LIST_H_

#include <bitset>
#include <list>
#include <vector>

#include "barrier.h"
#include "base/histogram.h"
#include "base/mutex.h"
#include "base/macros.h"
#include "base/value_object.h"
#include "jni.h"
#include "reflective_handle_scope.h"
#include "suspend_reason.h"
#include "thread_state.h"

namespace art HIDDEN {
namespace gc {
namespace collector {
class GarbageCollector;
}  // namespace collector
class GcPauseListener;
}  // namespace gc
class Closure;
class IsMarkedVisitor;
class RootVisitor;
class Thread;
class TimingLogger;
enum VisitRootFlags : uint8_t;

class ThreadList {
 public:
  static constexpr uint32_t kMaxThreadId = 0xFFFF;
  static constexpr uint32_t kInvalidThreadId = 0;
  static constexpr uint32_t kMainThreadId = 1;
  static constexpr uint64_t kDefaultThreadSuspendTimeout =
      kIsDebugBuild ? 2'000'000'000ull : 4'000'000'000ull;
  // We fail more aggressively in debug builds to catch potential issues early.
  // The number of times we may retry when we find ourselves in a suspend-unfriendly state.
  static constexpr int kMaxSuspendRetries = kIsDebugBuild ? 500 : 5000;
  static constexpr useconds_t kThreadSuspendSleepUs = 100;

  explicit ThreadList(uint64_t thread_suspend_timeout_ns);
  ~ThreadList();

  void ShutDown();

  void DumpForSigQuit(std::ostream& os)
      REQUIRES(!Locks::thread_list_lock_, !Locks::mutator_lock_);
  // For thread suspend timeout dumps.
  EXPORT void Dump(std::ostream& os, bool dump_native_stack = true)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);
  pid_t GetLockOwner();  // For SignalCatcher.

  // Thread suspension support.
  EXPORT void ResumeAll()
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_)
      UNLOCK_FUNCTION(Locks::mutator_lock_);
  EXPORT bool Resume(Thread* thread, SuspendReason reason = SuspendReason::kInternal)
      REQUIRES(!Locks::thread_suspend_count_lock_) WARN_UNUSED;

  // Suspends all other threads and gets exclusive access to the mutator lock.
  // If long_suspend is true, then other threads who try to suspend will never timeout.
  // long_suspend is currenly used for hprof since large heaps take a long time.
  EXPORT void SuspendAll(const char* cause, bool long_suspend = false)
      EXCLUSIVE_LOCK_FUNCTION(Locks::mutator_lock_)
      REQUIRES(!Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_,
               !Locks::mutator_lock_);

  // Suspend a thread using a peer, typically used by the debugger. Returns the thread on success,
  // else null. The peer is used to identify the thread to avoid races with the thread terminating.
  EXPORT Thread* SuspendThreadByPeer(jobject peer, SuspendReason reason)
      REQUIRES(!Locks::mutator_lock_,
               !Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_);

  // Suspend a thread using its thread id, typically used by lock/monitor inflation. Returns the
  // thread on success else null. The thread id is used to identify the thread to avoid races with
  // the thread terminating. Note that as thread ids are recycled this may not suspend the expected
  // thread, that may be terminating. 'attempt_of_4' is zero if this is the only
  // attempt, or 1..4 to try 4 times with fractional timeouts.
  // TODO: Reconsider the use of thread_id, now that we have ThreadExitFlag.
  Thread* SuspendThreadByThreadId(uint32_t thread_id, SuspendReason reason, int attempt_of_4 = 0)
      REQUIRES(!Locks::mutator_lock_,
               !Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_);

  // Find an existing thread (or self) by its thread id (not tid).
  EXPORT Thread* FindThreadByThreadId(uint32_t thread_id) REQUIRES(Locks::thread_list_lock_);

  // Find an existing thread (or self) by its tid (not thread id).
  Thread* FindThreadByTid(int tid) REQUIRES(Locks::thread_list_lock_);

  // Does the thread list still contain the given thread, or one at the same address?
  // Used by Monitor to provide (mostly accurate) debugging information.
  bool Contains(Thread* thread) REQUIRES(Locks::thread_list_lock_);

  // Run a checkpoint on all threads. Return the total number of threads for which the checkpoint
  // function has been or will be called.
  // Running threads are not suspended but run the checkpoint inside of the suspend check. The
  // return value includes already suspended threads for b/24191051. Runs or requests the
  // callback, if non-null, inside the thread_list_lock critical section after determining the
  // runnable/suspended states of the threads. Does not wait for completion of the checkpoint
  // function in running threads. If the caller holds the mutator lock, then all instances of the
  // checkpoint function are run with the mutator lock. If the caller does not hold the mutator
  // lock (see mutator_gc_coord.md) then, since the checkpoint code may not acquire or release the
  // mutator lock, the checkpoint will have no way to access Java data.
  // TODO: Is it possible to just require the mutator lock here?
  EXPORT size_t RunCheckpoint(Closure* checkpoint_function,
                       Closure* callback = nullptr,
                       bool allow_lock_checking = true)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);

  // Convenience version of the above to disable lock checking inside Run function. Hopefully this
  // and the third parameter above will eventually disappear.
  size_t RunCheckpointUnchecked(Closure* checkpoint_function, Closure* callback = nullptr)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_) {
    return RunCheckpoint(checkpoint_function, callback, false);
  }

  // Run an empty checkpoint on threads. Wait until threads pass the next suspend point or are
  // suspended. This is used to ensure that the threads finish or aren't in the middle of an
  // in-flight mutator heap access (eg. a read barrier.) Runnable threads will respond by
  // decrementing the empty checkpoint barrier count. This works even when the weak ref access is
  // disabled. Only one concurrent use is currently supported.
  void RunEmptyCheckpoint()
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);

  // Used to flip thread roots from from-space refs to to-space refs. Used only by the concurrent
  // moving collectors during a GC, and hence cannot be called from multiple threads concurrently.
  //
  // Briefly suspends all threads to atomically install a checkpoint-like thread_flip_visitor
  // function to be run on each thread. Run flip_callback while threads are suspended.
  // Thread_flip_visitors are run by each thread before it becomes runnable, or by us. We do not
  // return until all thread_flip_visitors have been run.
  void FlipThreadRoots(Closure* thread_flip_visitor,
                       Closure* flip_callback,
                       gc::collector::GarbageCollector* collector,
                       gc::GcPauseListener* pause_listener)
      REQUIRES(!Locks::mutator_lock_,
               !Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_);

  // Iterates over all the threads.
  EXPORT void ForEach(void (*callback)(Thread*, void*), void* context)
      REQUIRES(Locks::thread_list_lock_);

  template<typename CallBack>
  void ForEach(CallBack cb) REQUIRES(Locks::thread_list_lock_) {
    ForEach([](Thread* t, void* ctx) REQUIRES(Locks::thread_list_lock_) {
      (*reinterpret_cast<CallBack*>(ctx))(t);
    }, &cb);
  }

  // Add/remove current thread from list.
  void Register(Thread* self)
      REQUIRES(Locks::runtime_shutdown_lock_)
      REQUIRES(!Locks::mutator_lock_,
               !Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_);
  void Unregister(Thread* self, bool should_run_callbacks)
      REQUIRES(!Locks::mutator_lock_,
               !Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_);

  // Wait until there are no Unregister() requests in flight. Only makes sense when we know that
  // no new calls can be made. e.g. because we're the last thread.
  void WaitForUnregisterToComplete(Thread* self) REQUIRES(Locks::thread_list_lock_);

  void VisitRoots(RootVisitor* visitor, VisitRootFlags flags) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitRootsForSuspendedThreads(RootVisitor* visitor)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitReflectiveTargets(ReflectiveValueVisitor* visitor) const REQUIRES(Locks::mutator_lock_);

  EXPORT void SweepInterpreterCaches(IsMarkedVisitor* visitor) const
      REQUIRES(Locks::mutator_lock_, !Locks::thread_list_lock_);

  // Return a copy of the thread list.
  std::list<Thread*> GetList() REQUIRES(Locks::thread_list_lock_) {
    return list_;
  }

  size_t Size() REQUIRES(Locks::thread_list_lock_) { return list_.size(); }

  void DumpNativeStacks(std::ostream& os)
      REQUIRES(!Locks::thread_list_lock_);

  Barrier* EmptyCheckpointBarrier() {
    return empty_checkpoint_barrier_.get();
  }

  void WaitForOtherNonDaemonThreadsToExit(bool check_no_birth = true)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_,
               !Locks::mutator_lock_);

  // Wait for suspend barrier to reach zero. Return a string possibly containing diagnostic
  // information on timeout, nothing on success.  The argument t specifies a thread to monitor for
  // the diagnostic information. If 0 is passed, we return an empty string on timeout.  Normally
  // the caller does not hold the mutator lock. See the comment at the call in
  // RequestSynchronousCheckpoint for the only exception.
  std::optional<std::string> WaitForSuspendBarrier(AtomicInteger* barrier,
                                                   pid_t t = 0,
                                                   int attempt_of_4 = 0)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);

 private:
  uint32_t AllocThreadId(Thread* self);
  void ReleaseThreadId(Thread* self, uint32_t id) REQUIRES(!Locks::allocated_thread_ids_lock_);

  void DumpUnattachedThreads(std::ostream& os, bool dump_native_stack)
      REQUIRES(!Locks::thread_list_lock_);

  void SuspendAllDaemonThreadsForShutdown()
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);

  void ResumeAllInternal(Thread* self)
      REQUIRES(Locks::thread_list_lock_, Locks::thread_suspend_count_lock_)
          UNLOCK_FUNCTION(Locks::mutator_lock_);

  // Helper to actually suspend a single thread. This is called with thread_list_lock_ held and
  // the caller guarantees that *thread is valid until that is released.  We "release the mutator
  // lock", by switching to self_state.  'attempt_of_4' is 0 if we only attempt once, and 1..4 if
  // we are going to try 4 times with a quarter of the full timeout. 'func_name' is used only to
  // identify ourselves for logging.
  bool SuspendThread(Thread* self,
                     Thread* thread,
                     SuspendReason reason,
                     ThreadState self_state,
                     const char* func_name,
                     int attempt_of_4) RELEASE(Locks::thread_list_lock_)
      RELEASE_SHARED(Locks::mutator_lock_);

  void SuspendAllInternal(Thread* self, SuspendReason reason = SuspendReason::kInternal)
      REQUIRES(!Locks::thread_list_lock_,
               !Locks::thread_suspend_count_lock_,
               !Locks::mutator_lock_);

  void AssertOtherThreadsAreSuspended(Thread* self)
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_);

  std::bitset<kMaxThreadId> allocated_ids_ GUARDED_BY(Locks::allocated_thread_ids_lock_);

  // The actual list of all threads.
  std::list<Thread*> list_ GUARDED_BY(Locks::thread_list_lock_);

  // Ongoing suspend all requests, used to ensure threads added to list_ respect SuspendAll, and
  // to ensure that only one SuspendAll ot FlipThreadRoots call is active at a time.  The value is
  // always either 0 or 1. Thread_suspend_count_lock must be held continuously while these two
  // functions modify suspend counts of all other threads and modify suspend_all_count_ .
  int suspend_all_count_ GUARDED_BY(Locks::thread_suspend_count_lock_);

  // Number of threads unregistering, ~ThreadList blocks until this hits 0.
  int unregistering_count_ GUARDED_BY(Locks::thread_list_lock_);

  // Thread suspend time histogram. Only modified when all the threads are suspended, so guarding
  // by mutator lock ensures no thread can read when another thread is modifying it.
  Histogram<uint64_t> suspend_all_histogram_ GUARDED_BY(Locks::mutator_lock_);

  // Whether or not the current thread suspension is long.
  bool long_suspend_;

  // Whether the shutdown function has been called. This is checked in the destructor. It is an
  // error to destroy a ThreadList instance without first calling ShutDown().
  bool shut_down_;

  // Thread suspension timeout in nanoseconds.
  const uint64_t thread_suspend_timeout_ns_;

  std::unique_ptr<Barrier> empty_checkpoint_barrier_;

  friend class Thread;

  friend class Mutex;
  friend class BaseMutex;

  DISALLOW_COPY_AND_ASSIGN(ThreadList);
};

// Helper for suspending all threads and getting exclusive access to the mutator lock.
class ScopedSuspendAll : public ValueObject {
 public:
  EXPORT explicit ScopedSuspendAll(const char* cause, bool long_suspend = false)
     EXCLUSIVE_LOCK_FUNCTION(Locks::mutator_lock_)
     REQUIRES(!Locks::thread_list_lock_,
              !Locks::thread_suspend_count_lock_,
              !Locks::mutator_lock_);
  // No REQUIRES(mutator_lock_) since the unlock function already asserts this.
  EXPORT ~ScopedSuspendAll()
      REQUIRES(!Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_)
      UNLOCK_FUNCTION(Locks::mutator_lock_);
};

}  // namespace art

#endif  // ART_RUNTIME_THREAD_LIST_H_

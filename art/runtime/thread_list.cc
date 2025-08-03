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

#include "thread_list.h"

#include <dirent.h>
#include <sys/types.h>
#include <unistd.h>

#include <map>
#include <sstream>
#include <tuple>
#include <vector>

#include "android-base/stringprintf.h"
#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"
#include "unwindstack/AndroidUnwinder.h"

#include "art_field-inl.h"
#include "base/aborting.h"
#include "base/histogram-inl.h"
#include "base/mutex-inl.h"
#include "base/systrace.h"
#include "base/time_utils.h"
#include "base/timing_logger.h"
#include "debugger.h"
#include "gc/collector/concurrent_copying.h"
#include "gc/gc_pause_listener.h"
#include "gc/heap.h"
#include "gc/reference_processor.h"
#include "gc_root.h"
#include "jni/jni_internal.h"
#include "lock_word.h"
#include "mirror/string.h"
#include "monitor.h"
#include "native_stack_dump.h"
#include "obj_ptr-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "thread.h"
#include "trace.h"
#include "well_known_classes.h"

#if ART_USE_FUTEXES
#include "linux/futex.h"
#include "sys/syscall.h"
#ifndef SYS_futex
#define SYS_futex __NR_futex
#endif
#endif  // ART_USE_FUTEXES

namespace art HIDDEN {

using android::base::StringPrintf;

static constexpr uint64_t kLongThreadSuspendThreshold = MsToNs(5);

// Whether we should try to dump the native stack of unattached threads. See commit ed8b723 for
// some history.
static constexpr bool kDumpUnattachedThreadNativeStackForSigQuit = true;

ThreadList::ThreadList(uint64_t thread_suspend_timeout_ns)
    : suspend_all_count_(0),
      unregistering_count_(0),
      suspend_all_histogram_("suspend all histogram", 16, 64),
      long_suspend_(false),
      shut_down_(false),
      thread_suspend_timeout_ns_(thread_suspend_timeout_ns),
      empty_checkpoint_barrier_(new Barrier(0)) {
  CHECK(Monitor::IsValidLockWord(LockWord::FromThinLockId(kMaxThreadId, 1, 0U)));
}

ThreadList::~ThreadList() {
  CHECK(shut_down_);
}

void ThreadList::ShutDown() {
  ScopedTrace trace(__PRETTY_FUNCTION__);
  // Detach the current thread if necessary. If we failed to start, there might not be any threads.
  // We need to detach the current thread here in case there's another thread waiting to join with
  // us.
  bool contains = false;
  Thread* self = Thread::Current();
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    contains = Contains(self);
  }
  if (contains) {
    Runtime::Current()->DetachCurrentThread();
  }
  WaitForOtherNonDaemonThreadsToExit();
  // The only caller of this function, ~Runtime, has already disabled GC and
  // ensured that the last GC is finished.
  gc::Heap* const heap = Runtime::Current()->GetHeap();
  CHECK(heap->IsGCDisabledForShutdown());

  // TODO: there's an unaddressed race here where a thread may attach during shutdown, see
  //       Thread::Init.
  SuspendAllDaemonThreadsForShutdown();

  shut_down_ = true;
}

bool ThreadList::Contains(Thread* thread) {
  return find(list_.begin(), list_.end(), thread) != list_.end();
}

pid_t ThreadList::GetLockOwner() {
  return Locks::thread_list_lock_->GetExclusiveOwnerTid();
}

void ThreadList::DumpNativeStacks(std::ostream& os) {
  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  unwindstack::AndroidLocalUnwinder unwinder;
  for (const auto& thread : list_) {
    os << "DUMPING THREAD " << thread->GetTid() << "\n";
    DumpNativeStack(os, unwinder, thread->GetTid(), "\t");
    os << "\n";
  }
}

void ThreadList::DumpForSigQuit(std::ostream& os) {
  {
    ScopedObjectAccess soa(Thread::Current());
    // Only print if we have samples.
    if (suspend_all_histogram_.SampleSize() > 0) {
      Histogram<uint64_t>::CumulativeData data;
      suspend_all_histogram_.CreateHistogram(&data);
      suspend_all_histogram_.PrintConfidenceIntervals(os, 0.99, data);  // Dump time to suspend.
    }
  }
  bool dump_native_stack = Runtime::Current()->GetDumpNativeStackOnSigQuit();
  Dump(os, dump_native_stack);
  DumpUnattachedThreads(os, dump_native_stack && kDumpUnattachedThreadNativeStackForSigQuit);
}

static void DumpUnattachedThread(std::ostream& os, pid_t tid, bool dump_native_stack)
    NO_THREAD_SAFETY_ANALYSIS {
  // TODO: No thread safety analysis as DumpState with a null thread won't access fields, should
  // refactor DumpState to avoid skipping analysis.
  Thread::DumpState(os, nullptr, tid);
  if (dump_native_stack) {
    DumpNativeStack(os, tid, "  native: ");
  }
  os << std::endl;
}

void ThreadList::DumpUnattachedThreads(std::ostream& os, bool dump_native_stack) {
  DIR* d = opendir("/proc/self/task");
  if (!d) {
    return;
  }

  Thread* self = Thread::Current();
  dirent* e;
  while ((e = readdir(d)) != nullptr) {
    char* end;
    pid_t tid = strtol(e->d_name, &end, 10);
    if (!*end) {
      Thread* thread;
      {
        MutexLock mu(self, *Locks::thread_list_lock_);
        thread = FindThreadByTid(tid);
      }
      if (thread == nullptr) {
        DumpUnattachedThread(os, tid, dump_native_stack);
      }
    }
  }
  closedir(d);
}

// Dump checkpoint timeout in milliseconds. Larger amount on the target, since the device could be
// overloaded with ANR dumps.
static constexpr uint32_t kDumpWaitTimeout = kIsTargetBuild ? 100000 : 20000;

// A closure used by Thread::Dump.
class DumpCheckpoint final : public Closure {
 public:
  DumpCheckpoint(bool dump_native_stack)
      : lock_("Dump checkpoint lock", kGenericBottomLock),
        os_(),
        // Avoid verifying count in case a thread doesn't end up passing through the barrier.
        // This avoids a SIGABRT that would otherwise happen in the destructor.
        barrier_(0, /*verify_count_on_shutdown=*/false),
        unwinder_(std::vector<std::string>{}, std::vector<std::string> {"oat", "odex"}),
        dump_native_stack_(dump_native_stack) {
  }

  void Run(Thread* thread) override {
    // Note thread and self may not be equal if thread was already suspended at the point of the
    // request.
    Thread* self = Thread::Current();
    CHECK(self != nullptr);
    std::ostringstream local_os;
    Thread::DumpOrder dump_order;
    {
      ScopedObjectAccess soa(self);
      dump_order = thread->Dump(local_os, unwinder_, dump_native_stack_);
    }
    {
      MutexLock mu(self, lock_);
      // Sort, so that the most interesting threads for ANR are printed first (ANRs can be trimmed).
      std::pair<Thread::DumpOrder, uint32_t> sort_key(dump_order, thread->GetThreadId());
      os_.emplace(sort_key, std::move(local_os));
    }
    barrier_.Pass(self);
  }

  // Called at the end to print all the dumps in sequential prioritized order.
  void Dump(Thread* self, std::ostream& os) {
    MutexLock mu(self, lock_);
    for (const auto& it : os_) {
      os << it.second.str() << std::endl;
    }
  }

  void WaitForThreadsToRunThroughCheckpoint(size_t threads_running_checkpoint) {
    Thread* self = Thread::Current();
    ScopedThreadStateChange tsc(self, ThreadState::kWaitingForCheckPointsToRun);
    bool timed_out = barrier_.Increment(self, threads_running_checkpoint, kDumpWaitTimeout);
    if (timed_out) {
      // Avoid a recursive abort.
      LOG((kIsDebugBuild && (gAborting == 0)) ? ::android::base::FATAL : ::android::base::ERROR)
          << "Unexpected time out during dump checkpoint.";
    }
  }

 private:
  // Storage for the per-thread dumps (guarded by lock since they are generated in parallel).
  // Map is used to obtain sorted order. The key is unique, but use multimap just in case.
  Mutex lock_;
  std::multimap<std::pair<Thread::DumpOrder, uint32_t>, std::ostringstream> os_ GUARDED_BY(lock_);
  // The barrier to be passed through and for the requestor to wait upon.
  Barrier barrier_;
  // A backtrace map, so that all threads use a shared info and don't reacquire/parse separately.
  unwindstack::AndroidLocalUnwinder unwinder_;
  // Whether we should dump the native stack.
  const bool dump_native_stack_;
};

void ThreadList::Dump(std::ostream& os, bool dump_native_stack) {
  Thread* self = Thread::Current();
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    os << "DALVIK THREADS (" << list_.size() << "):\n";
  }
  if (self != nullptr) {
    DumpCheckpoint checkpoint(dump_native_stack);
    size_t threads_running_checkpoint;
    {
      // Use SOA to prevent deadlocks if multiple threads are calling Dump() at the same time.
      ScopedObjectAccess soa(self);
      threads_running_checkpoint = RunCheckpoint(&checkpoint);
    }
    if (threads_running_checkpoint != 0) {
      checkpoint.WaitForThreadsToRunThroughCheckpoint(threads_running_checkpoint);
    }
    checkpoint.Dump(self, os);
  } else {
    DumpUnattachedThreads(os, dump_native_stack);
  }
}

void ThreadList::AssertOtherThreadsAreSuspended(Thread* self) {
  MutexLock mu(self, *Locks::thread_list_lock_);
  MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
  for (const auto& thread : list_) {
    if (thread != self) {
      CHECK(thread->IsSuspended())
            << "\nUnsuspended thread: <<" << *thread << "\n"
            << "self: <<" << *Thread::Current();
    }
  }
}

#if HAVE_TIMED_RWLOCK
// Attempt to rectify locks so that we dump thread list with required locks before exiting.
NO_RETURN static void UnsafeLogFatalForThreadSuspendAllTimeout() {
  // Increment gAborting before doing the thread list dump since we don't want any failures from
  // AssertThreadSuspensionIsAllowable in cases where thread suspension is not allowed.
  // See b/69044468.
  ++gAborting;
  Runtime* runtime = Runtime::Current();
  std::ostringstream ss;
  ss << "Thread suspend timeout\n";
  Locks::mutator_lock_->Dump(ss);
  ss << "\n";
  runtime->GetThreadList()->Dump(ss);
  --gAborting;
  LOG(FATAL) << ss.str();
  exit(0);
}
#endif

size_t ThreadList::RunCheckpoint(Closure* checkpoint_function,
                                 Closure* callback,
                                 bool allow_lock_checking) {
  Thread* self = Thread::Current();
  Locks::mutator_lock_->AssertNotExclusiveHeld(self);
  Locks::thread_list_lock_->AssertNotHeld(self);
  Locks::thread_suspend_count_lock_->AssertNotHeld(self);

  std::vector<Thread*> suspended_count_modified_threads;
  size_t count = 0;
  {
    // Call a checkpoint function for each thread. We directly invoke the function on behalf of
    // suspended threads.
    MutexLock mu(self, *Locks::thread_list_lock_);
    if (kIsDebugBuild && allow_lock_checking) {
      self->DisallowPreMonitorMutexes();
    }
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    count = list_.size();
    for (const auto& thread : list_) {
      if (thread != self) {
        bool requested_suspend = false;
        while (true) {
          if (thread->RequestCheckpoint(checkpoint_function)) {
            // This thread will run its checkpoint some time in the near future.
            if (requested_suspend) {
              // The suspend request is now unnecessary.
              thread->DecrementSuspendCount(self);
              Thread::resume_cond_->Broadcast(self);
              requested_suspend = false;
            }
            break;
          } else {
            // The thread was, and probably still is, suspended.
            if (!requested_suspend) {
              // This does not risk suspension cycles: We may have a pending suspension request,
              // but it cannot block us: Checkpoint Run() functions may not suspend, thus we cannot
              // be blocked from decrementing the count again.
              thread->IncrementSuspendCount(self);
              requested_suspend = true;
            }
            if (thread->IsSuspended()) {
              // We saw it suspended after incrementing suspend count, so it will stay that way.
              break;
            }
          }
          // We only get here if the thread entered kRunnable again. Retry immediately.
        }
        // At this point, either the thread was runnable, and will run the checkpoint itself,
        // or requested_suspend is true, and the thread is safely suspended.
        if (requested_suspend) {
          DCHECK(thread->IsSuspended());
          suspended_count_modified_threads.push_back(thread);
        }
      }
      // Thread either has honored or will honor the checkpoint, or it has been added to
      // suspended_count_modified_threads.
    }
    // Run the callback to be called inside this critical section.
    if (callback != nullptr) {
      callback->Run(self);
    }
  }

  // Run the checkpoint on ourself while we wait for threads to suspend.
  checkpoint_function->Run(self);

  bool mutator_lock_held = Locks::mutator_lock_->IsSharedHeld(self);
  bool repeat = true;
  // Run the checkpoint on the suspended threads.
  while (repeat) {
    repeat = false;
    for (auto& thread : suspended_count_modified_threads) {
      if (thread != nullptr) {
        // We know for sure that the thread is suspended at this point.
        DCHECK(thread->IsSuspended());
        if (mutator_lock_held) {
          // Make sure there is no pending flip function before running Java-heap-accessing
          // checkpoint on behalf of thread.
          Thread::EnsureFlipFunctionStarted(self, thread);
          if (thread->GetStateAndFlags(std::memory_order_acquire)
                  .IsAnyOfFlagsSet(Thread::FlipFunctionFlags())) {
            // There is another thread running the flip function for 'thread'.
            // Instead of waiting for it to complete, move to the next thread.
            repeat = true;
            continue;
          }
        }  // O.w. the checkpoint will not access Java data structures, and doesn't care whether
           // the flip function has been called.
        checkpoint_function->Run(thread);
        {
          MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
          thread->DecrementSuspendCount(self);
        }
        // We are done with 'thread' so set it to nullptr so that next outer
        // loop iteration, if any, skips 'thread'.
        thread = nullptr;
      }
    }
  }
  DCHECK(std::all_of(suspended_count_modified_threads.cbegin(),
                     suspended_count_modified_threads.cend(),
                     [](Thread* thread) { return thread == nullptr; }));

  {
    // Imitate ResumeAll, threads may be waiting on Thread::resume_cond_ since we raised their
    // suspend count. Now the suspend_count_ is lowered so we must do the broadcast.
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    Thread::resume_cond_->Broadcast(self);
  }

  if (kIsDebugBuild && allow_lock_checking) {
    self->AllowPreMonitorMutexes();
  }
  return count;
}

void ThreadList::RunEmptyCheckpoint() {
  Thread* self = Thread::Current();
  Locks::mutator_lock_->AssertNotExclusiveHeld(self);
  Locks::thread_list_lock_->AssertNotHeld(self);
  Locks::thread_suspend_count_lock_->AssertNotHeld(self);
  std::vector<uint32_t> runnable_thread_ids;
  size_t count = 0;
  Barrier* barrier = empty_checkpoint_barrier_.get();
  barrier->Init(self, 0);
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    for (Thread* thread : list_) {
      if (thread != self) {
        while (true) {
          if (thread->RequestEmptyCheckpoint()) {
            // This thread will run an empty checkpoint (decrement the empty checkpoint barrier)
            // some time in the near future.
            ++count;
            if (kIsDebugBuild) {
              runnable_thread_ids.push_back(thread->GetThreadId());
            }
            break;
          }
          if (thread->GetState() != ThreadState::kRunnable) {
            // It's seen suspended, we are done because it must not be in the middle of a mutator
            // heap access.
            break;
          }
        }
      }
    }
  }

  // Wake up the threads blocking for weak ref access so that they will respond to the empty
  // checkpoint request. Otherwise we will hang as they are blocking in the kRunnable state.
  Runtime::Current()->GetHeap()->GetReferenceProcessor()->BroadcastForSlowPath(self);
  Runtime::Current()->BroadcastForNewSystemWeaks(/*broadcast_for_checkpoint=*/true);
  {
    ScopedThreadStateChange tsc(self, ThreadState::kWaitingForCheckPointsToRun);
    uint64_t total_wait_time = 0;
    bool first_iter = true;
    while (true) {
      // Wake up the runnable threads blocked on the mutexes that another thread, which is blocked
      // on a weak ref access, holds (indirectly blocking for weak ref access through another thread
      // and a mutex.) This needs to be done periodically because the thread may be preempted
      // between the CheckEmptyCheckpointFromMutex call and the subsequent futex wait in
      // Mutex::ExclusiveLock, etc. when the wakeup via WakeupToRespondToEmptyCheckpoint
      // arrives. This could cause a *very rare* deadlock, if not repeated. Most of the cases are
      // handled in the first iteration.
      for (BaseMutex* mutex : Locks::expected_mutexes_on_weak_ref_access_) {
        mutex->WakeupToRespondToEmptyCheckpoint();
      }
      static constexpr uint64_t kEmptyCheckpointPeriodicTimeoutMs = 100;  // 100ms
      static constexpr uint64_t kEmptyCheckpointTotalTimeoutMs = 600 * 1000;  // 10 minutes.
      size_t barrier_count = first_iter ? count : 0;
      first_iter = false;  // Don't add to the barrier count from the second iteration on.
      bool timed_out = barrier->Increment(self, barrier_count, kEmptyCheckpointPeriodicTimeoutMs);
      if (!timed_out) {
        break;  // Success
      }
      // This is a very rare case.
      total_wait_time += kEmptyCheckpointPeriodicTimeoutMs;
      if (kIsDebugBuild && total_wait_time > kEmptyCheckpointTotalTimeoutMs) {
        std::ostringstream ss;
        ss << "Empty checkpoint timeout\n";
        ss << "Barrier count " << barrier->GetCount(self) << "\n";
        ss << "Runnable thread IDs";
        for (uint32_t tid : runnable_thread_ids) {
          ss << " " << tid;
        }
        ss << "\n";
        Locks::mutator_lock_->Dump(ss);
        ss << "\n";
        LOG(FATAL_WITHOUT_ABORT) << ss.str();
        // Some threads in 'runnable_thread_ids' are probably stuck. Try to dump their stacks.
        // Avoid using ThreadList::Dump() initially because it is likely to get stuck as well.
        {
          ScopedObjectAccess soa(self);
          MutexLock mu1(self, *Locks::thread_list_lock_);
          for (Thread* thread : GetList()) {
            uint32_t tid = thread->GetThreadId();
            bool is_in_runnable_thread_ids =
                std::find(runnable_thread_ids.begin(), runnable_thread_ids.end(), tid) !=
                runnable_thread_ids.end();
            if (is_in_runnable_thread_ids &&
                thread->ReadFlag(ThreadFlag::kEmptyCheckpointRequest)) {
              // Found a runnable thread that hasn't responded to the empty checkpoint request.
              // Assume it's stuck and safe to dump its stack.
              thread->Dump(LOG_STREAM(FATAL_WITHOUT_ABORT),
                           /*dump_native_stack=*/ true,
                           /*force_dump_stack=*/ true);
            }
          }
        }
        LOG(FATAL_WITHOUT_ABORT)
            << "Dumped runnable threads that haven't responded to empty checkpoint.";
        // Now use ThreadList::Dump() to dump more threads, noting it may get stuck.
        Dump(LOG_STREAM(FATAL_WITHOUT_ABORT));
        LOG(FATAL) << "Dumped all threads.";
      }
    }
  }
}

// Separate function to disable just the right amount of thread-safety analysis.
ALWAYS_INLINE void AcquireMutatorLockSharedUncontended(Thread* self)
    ACQUIRE_SHARED(*Locks::mutator_lock_) NO_THREAD_SAFETY_ANALYSIS {
  bool success = Locks::mutator_lock_->SharedTryLock(self, /*check=*/false);
  CHECK(success);
}

// A checkpoint/suspend-all hybrid to switch thread roots from
// from-space to to-space refs. Used to synchronize threads at a point
// to mark the initiation of marking while maintaining the to-space
// invariant.
void ThreadList::FlipThreadRoots(Closure* thread_flip_visitor,
                                 Closure* flip_callback,
                                 gc::collector::GarbageCollector* collector,
                                 gc::GcPauseListener* pause_listener) {
  TimingLogger::ScopedTiming split("ThreadListFlip", collector->GetTimings());
  Thread* self = Thread::Current();
  Locks::mutator_lock_->AssertNotHeld(self);
  Locks::thread_list_lock_->AssertNotHeld(self);
  Locks::thread_suspend_count_lock_->AssertNotHeld(self);
  CHECK_NE(self->GetState(), ThreadState::kRunnable);

  collector->GetHeap()->ThreadFlipBegin(self);  // Sync with JNI critical calls.

  // ThreadFlipBegin happens before we suspend all the threads, so it does not
  // count towards the pause.
  const uint64_t suspend_start_time = NanoTime();
  VLOG(threads) << "Suspending all for thread flip";
  SuspendAllInternal(self);
  if (pause_listener != nullptr) {
    pause_listener->StartPause();
  }

  // Run the flip callback for the collector.
  Locks::mutator_lock_->ExclusiveLock(self);
  suspend_all_histogram_.AdjustAndAddValue(NanoTime() - suspend_start_time);
  flip_callback->Run(self);

  std::vector<Thread*> flipping_threads;  // All suspended threads. Includes us.
  int thread_count;
  // Flipping threads might exit between the time we resume them and try to run the flip function.
  // Track that in a parallel vector.
  std::unique_ptr<ThreadExitFlag[]> exit_flags;
  {
    TimingLogger::ScopedTiming split2("ResumeRunnableThreads", collector->GetTimings());
    MutexLock mu(self, *Locks::thread_list_lock_);
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    thread_count = list_.size();
    exit_flags.reset(new ThreadExitFlag[thread_count]);
    flipping_threads.resize(thread_count, nullptr);
    int i = 1;
    for (Thread* thread : list_) {
      // Set the flip function for all threads because once we start resuming any threads,
      // they may need to run the flip function on behalf of other threads, even this one.
      DCHECK(thread == self || thread->IsSuspended());
      thread->SetFlipFunction(thread_flip_visitor);
      // Put ourselves first, so other threads are more likely to have finished before we get
      // there.
      int thread_index = thread == self ? 0 : i++;
      flipping_threads[thread_index] = thread;
      thread->NotifyOnThreadExit(&exit_flags[thread_index]);
    }
    DCHECK(i == thread_count);
  }

  if (pause_listener != nullptr) {
    pause_listener->EndPause();
  }
  // Any new threads created after this will be created by threads that already ran their flip
  // functions. In the normal GC use case in which the flip function converts all local references
  // to to-space references, these newly created threads will also see only to-space references.

  // Resume threads, making sure that we do not release suspend_count_lock_ until we've reacquired
  // the mutator_lock_ in shared mode, and decremented suspend_all_count_.  This avoids a
  // concurrent SuspendAll, and ensures that newly started threads see a correct value of
  // suspend_all_count.
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    Locks::thread_suspend_count_lock_->Lock(self);
    ResumeAllInternal(self);
  }

  collector->RegisterPause(NanoTime() - suspend_start_time);

  // Since all threads were suspended, they will attempt to run the flip function before
  // reentering a runnable state. We will also attempt to run the flip functions ourselves.  Any
  // intervening checkpoint request will do the same.  Exactly one of those flip function attempts
  // will succeed, and the target thread will not be able to reenter a runnable state until one of
  // them does.

  // Try to run the closure on the other threads.
  TimingLogger::ScopedTiming split3("RunningThreadFlips", collector->GetTimings());
  // Reacquire the mutator lock while holding suspend_count_lock. This cannot fail, since we
  // do not acquire the mutator lock unless suspend_all_count was read as 0 while holding
  // suspend_count_lock. We did not release suspend_count_lock since releasing the mutator
  // lock.
  AcquireMutatorLockSharedUncontended(self);

  Locks::thread_suspend_count_lock_->Unlock(self);
  // Concurrent SuspendAll may now see zero suspend_all_count_, but block on mutator_lock_.

  collector->GetHeap()->ThreadFlipEnd(self);

  for (int i = 0; i < thread_count; ++i) {
    bool finished;
    Thread::EnsureFlipFunctionStarted(
        self, flipping_threads[i], Thread::StateAndFlags(0), &exit_flags[i], &finished);
    if (finished) {
      MutexLock mu2(self, *Locks::thread_list_lock_);
      flipping_threads[i]->UnregisterThreadExitFlag(&exit_flags[i]);
      flipping_threads[i] = nullptr;
    }
  }
  // Make sure all flips complete before we return.
  for (int i = 0; i < thread_count; ++i) {
    if (UNLIKELY(flipping_threads[i] != nullptr)) {
      flipping_threads[i]->WaitForFlipFunctionTestingExited(self, &exit_flags[i]);
      MutexLock mu2(self, *Locks::thread_list_lock_);
      flipping_threads[i]->UnregisterThreadExitFlag(&exit_flags[i]);
    }
  }

  Thread::DCheckUnregisteredEverywhere(&exit_flags[0], &exit_flags[thread_count - 1]);

  Locks::mutator_lock_->SharedUnlock(self);
}

// True only for debugging suspend timeout code. The resulting timeouts are short enough that
// failures are expected.
static constexpr bool kShortSuspendTimeouts = false;

static constexpr unsigned kSuspendBarrierIters = kShortSuspendTimeouts ? 5 : 20;

#if ART_USE_FUTEXES

// Returns true if it timed out.
static bool WaitOnceForSuspendBarrier(AtomicInteger* barrier,
                                      int32_t cur_val,
                                      uint64_t timeout_ns) {
  timespec wait_timeout;
  if (kShortSuspendTimeouts) {
    timeout_ns = MsToNs(kSuspendBarrierIters);
    CHECK_GE(NsToMs(timeout_ns / kSuspendBarrierIters), 1ul);
  } else {
    DCHECK_GE(NsToMs(timeout_ns / kSuspendBarrierIters), 10ul);
  }
  InitTimeSpec(false, CLOCK_MONOTONIC, NsToMs(timeout_ns / kSuspendBarrierIters), 0, &wait_timeout);
  if (futex(barrier->Address(), FUTEX_WAIT_PRIVATE, cur_val, &wait_timeout, nullptr, 0) != 0) {
    if (errno == ETIMEDOUT) {
      return true;
    } else if (errno != EAGAIN && errno != EINTR) {
      PLOG(FATAL) << "futex wait for suspend barrier failed";
    }
  }
  return false;
}

#else

static bool WaitOnceForSuspendBarrier(AtomicInteger* barrier,
                                      int32_t cur_val,
                                      uint64_t timeout_ns) {
  // In the normal case, aim for a couple of hundred milliseconds.
  static constexpr unsigned kInnerIters =
      kShortSuspendTimeouts ? 1'000 : (timeout_ns / 1000) / kSuspendBarrierIters;
  DCHECK_GE(kInnerIters, 1'000u);
  for (int i = 0; i < kInnerIters; ++i) {
    sched_yield();
    if (barrier->load(std::memory_order_acquire) == 0) {
      return false;
    }
  }
  return true;
}

#endif  // ART_USE_FUTEXES

// Return a short string describing the scheduling state of the thread with the given tid.
static std::string GetThreadState(pid_t t) {
#if defined(__linux__)
  static constexpr int BUF_SIZE = 90;
  char file_name_buf[BUF_SIZE];
  char buf[BUF_SIZE];
  snprintf(file_name_buf, BUF_SIZE, "/proc/%d/stat", t);
  int stat_fd = open(file_name_buf, O_RDONLY | O_CLOEXEC);
  if (stat_fd < 0) {
    return std::string("failed to get thread state: ") + std::string(strerror(errno));
  }
  CHECK(stat_fd >= 0) << strerror(errno);
  ssize_t bytes_read = TEMP_FAILURE_RETRY(read(stat_fd, buf, BUF_SIZE));
  CHECK(bytes_read >= 0) << strerror(errno);
  int ret = close(stat_fd);
  DCHECK(ret == 0) << strerror(errno);
  buf[BUF_SIZE - 1] = '\0';
  return buf;
#else
  return "unknown state";
#endif
}

std::optional<std::string> ThreadList::WaitForSuspendBarrier(AtomicInteger* barrier,
                                                             pid_t t,
                                                             int attempt_of_4) {
  // Only fail after kIter timeouts, to make us robust against app freezing.
#if ART_USE_FUTEXES
  const uint64_t start_time = NanoTime();
#endif
  uint64_t timeout_ns =
      attempt_of_4 == 0 ? thread_suspend_timeout_ns_ : thread_suspend_timeout_ns_ / 4;
  bool collect_state = (t != 0 && (attempt_of_4 == 0 || attempt_of_4 == 4));
  int32_t cur_val = barrier->load(std::memory_order_acquire);
  if (cur_val <= 0) {
    DCHECK_EQ(cur_val, 0);
    return std::nullopt;
  }
  unsigned i = 0;
  if (WaitOnceForSuspendBarrier(barrier, cur_val, timeout_ns)) {
    i = 1;
  }
  cur_val = barrier->load(std::memory_order_acquire);
  if (cur_val <= 0) {
    DCHECK_EQ(cur_val, 0);
    return std::nullopt;
  }

  // Long wait; gather information in case of timeout.
  std::string sampled_state = collect_state ? GetThreadState(t) : "";
  while (i < kSuspendBarrierIters) {
    if (WaitOnceForSuspendBarrier(barrier, cur_val, timeout_ns)) {
      ++i;
#if ART_USE_FUTEXES
      if (!kShortSuspendTimeouts) {
        CHECK_GE(NanoTime() - start_time, i * timeout_ns / kSuspendBarrierIters - 1'000'000);
      }
#endif
    }
    cur_val = barrier->load(std::memory_order_acquire);
    if (cur_val <= 0) {
      DCHECK_EQ(cur_val, 0);
      return std::nullopt;
    }
  }
  return collect_state ? "Target states: [" + sampled_state + ", " + GetThreadState(t) + "]" +
                             std::to_string(cur_val) + "@" + std::to_string((uintptr_t)barrier) +
                             " Final wait time: " + PrettyDuration(NanoTime() - start_time) :
                         "";
}

void ThreadList::SuspendAll(const char* cause, bool long_suspend) {
  Thread* self = Thread::Current();

  if (self != nullptr) {
    VLOG(threads) << *self << " SuspendAll for " << cause << " starting...";
  } else {
    VLOG(threads) << "Thread[null] SuspendAll for " << cause << " starting...";
  }
  {
    ScopedTrace trace("Suspending mutator threads");
    const uint64_t start_time = NanoTime();

    SuspendAllInternal(self);
    // All threads are known to have suspended (but a thread may still own the mutator lock)
    // Make sure this thread grabs exclusive access to the mutator lock and its protected data.
#if HAVE_TIMED_RWLOCK
    while (true) {
      if (Locks::mutator_lock_->ExclusiveLockWithTimeout(self,
                                                         NsToMs(thread_suspend_timeout_ns_),
                                                         0)) {
        break;
      } else if (!long_suspend_) {
        // Reading long_suspend without the mutator lock is slightly racy, in some rare cases, this
        // could result in a thread suspend timeout.
        // Timeout if we wait more than thread_suspend_timeout_ns_ nanoseconds.
        UnsafeLogFatalForThreadSuspendAllTimeout();
      }
    }
#else
    Locks::mutator_lock_->ExclusiveLock(self);
#endif

    long_suspend_ = long_suspend;

    const uint64_t end_time = NanoTime();
    const uint64_t suspend_time = end_time - start_time;
    suspend_all_histogram_.AdjustAndAddValue(suspend_time);
    if (suspend_time > kLongThreadSuspendThreshold) {
      LOG(WARNING) << "Suspending all threads took: " << PrettyDuration(suspend_time);
    }

    if (kDebugLocking) {
      // Debug check that all threads are suspended.
      AssertOtherThreadsAreSuspended(self);
    }
  }

  // SuspendAllInternal blocks if we are in the middle of a flip.
  DCHECK(!self->ReadFlag(ThreadFlag::kPendingFlipFunction));
  DCHECK(!self->ReadFlag(ThreadFlag::kRunningFlipFunction));

  ATraceBegin((std::string("Mutator threads suspended for ") + cause).c_str());

  if (self != nullptr) {
    VLOG(threads) << *self << " SuspendAll complete";
  } else {
    VLOG(threads) << "Thread[null] SuspendAll complete";
  }
}

// Ensures all threads running Java suspend and that those not running Java don't start.
void ThreadList::SuspendAllInternal(Thread* self, SuspendReason reason) {
  // self can be nullptr if this is an unregistered thread.
  Locks::mutator_lock_->AssertNotExclusiveHeld(self);
  Locks::thread_list_lock_->AssertNotHeld(self);
  Locks::thread_suspend_count_lock_->AssertNotHeld(self);
  if (kDebugLocking && self != nullptr) {
    CHECK_NE(self->GetState(), ThreadState::kRunnable);
  }

  // First request that all threads suspend, then wait for them to suspend before
  // returning. This suspension scheme also relies on other behaviour:
  // 1. Threads cannot be deleted while they are suspended or have a suspend-
  //    request flag set - (see Unregister() below).
  // 2. When threads are created, they are created in a suspended state (actually
  //    kNative) and will never begin executing Java code without first checking
  //    the suspend-request flag.

  // The atomic counter for number of threads that need to pass the barrier.
  AtomicInteger pending_threads;

  for (int iter_count = 1;; ++iter_count) {
    {
      MutexLock mu(self, *Locks::thread_list_lock_);
      MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
      if (suspend_all_count_ == 0) {
        // Never run multiple SuspendAlls concurrently.
        // If we are asked to suspend ourselves, we proceed anyway, but must ignore suspend
        // request from other threads until we resume them.
        bool found_myself = false;
        // Update global suspend all state for attaching threads.
        ++suspend_all_count_;
        pending_threads.store(list_.size() - (self == nullptr ? 0 : 1), std::memory_order_relaxed);
        // Increment everybody else's suspend count.
        for (const auto& thread : list_) {
          if (thread == self) {
            found_myself = true;
          } else {
            VLOG(threads) << "requesting thread suspend: " << *thread;
            DCHECK_EQ(suspend_all_count_, 1);
            thread->IncrementSuspendCount(self, &pending_threads, nullptr, reason);
            if (thread->IsSuspended()) {
              // Effectively pass the barrier on behalf of the already suspended thread.
              // The thread itself cannot yet have acted on our request since we still hold the
              // suspend_count_lock_, and it will notice that kActiveSuspendBarrier has already
              // been cleared if and when it acquires the lock in PassActiveSuspendBarriers().
              DCHECK_EQ(thread->tlsPtr_.active_suspendall_barrier, &pending_threads);
              pending_threads.fetch_sub(1, std::memory_order_seq_cst);
              thread->tlsPtr_.active_suspendall_barrier = nullptr;
              if (!thread->HasActiveSuspendBarrier()) {
                thread->AtomicClearFlag(ThreadFlag::kActiveSuspendBarrier);
              }
            }
            // else:
            // The target thread was not yet suspended, and hence will be forced to execute
            // TransitionFromRunnableToSuspended shortly. Since we set the kSuspendRequest flag
            // before checking, and it checks kActiveSuspendBarrier after noticing kSuspendRequest,
            // it must notice kActiveSuspendBarrier when it does. Thus it is guaranteed to
            // decrement the suspend barrier. We're relying on store; load ordering here, but
            // that's not a problem, since state and flags all reside in the same atomic, and
            // are thus properly ordered, even for relaxed accesses.
          }
        }
        self->AtomicSetFlag(ThreadFlag::kSuspensionImmune, std::memory_order_relaxed);
        DCHECK(self == nullptr || found_myself);
        break;
      }
    }
    if (iter_count >= kMaxSuspendRetries) {
      LOG(FATAL) << "Too many SuspendAll retries: " << iter_count;
    } else {
      MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
      DCHECK_LE(suspend_all_count_, 1);
      if (suspend_all_count_ != 0) {
        // This may take a while, and we're not runnable, and thus would otherwise not block.
        Thread::resume_cond_->WaitHoldingLocks(self);
        continue;
      }
    }
    // We're already not runnable, so an attempt to suspend us should succeed.
  }

  Thread* culprit = nullptr;
  pid_t tid = 0;
  std::ostringstream oss;
  for (int attempt_of_4 = 1; attempt_of_4 <= 4; ++attempt_of_4) {
    auto result = WaitForSuspendBarrier(&pending_threads, tid, attempt_of_4);
    if (!result.has_value()) {
      // Wait succeeded.
      break;
    }
    if (attempt_of_4 == 3) {
      // Second to the last attempt; Try to gather more information in case we time out.
      MutexLock mu(self, *Locks::thread_list_lock_);
      MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
      oss << "Unsuspended threads: ";
      for (const auto& thread : list_) {
        if (thread != self && !thread->IsSuspended()) {
          culprit = thread;
          oss << *thread << ", ";
        }
      }
      if (culprit != nullptr) {
        tid = culprit->GetTid();
      }
    } else if (attempt_of_4 == 4) {
      // Final attempt still timed out.
      if (culprit == nullptr) {
        LOG(FATAL) << "SuspendAll timeout. Couldn't find holdouts.";
      } else {
        std::string name;
        culprit->GetThreadName(name);
        oss << "Info for " << *culprit << ":";
        std::string thr_descr =
            StringPrintf("%s tid: %d, state&flags: 0x%x, priority: %d,  barrier value: %d, ",
                         name.c_str(),
                         tid,
                         culprit->GetStateAndFlags(std::memory_order_relaxed).GetValue(),
                         culprit->GetNativePriority(),
                         pending_threads.load());
        oss << thr_descr << result.value();
        culprit->AbortInThis("SuspendAll timeout: " + oss.str());
      }
    }
  }
}

void ThreadList::ResumeAll() {
  Thread* self = Thread::Current();
  if (kDebugLocking) {
    // Debug check that all threads are suspended.
    AssertOtherThreadsAreSuspended(self);
  }
  MutexLock mu(self, *Locks::thread_list_lock_);
  MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
  ResumeAllInternal(self);
}

// Holds thread_list_lock_ and suspend_count_lock_
void ThreadList::ResumeAllInternal(Thread* self) {
  DCHECK_NE(self->GetState(), ThreadState::kRunnable);
  if (self != nullptr) {
    VLOG(threads) << *self << " ResumeAll starting";
  } else {
    VLOG(threads) << "Thread[null] ResumeAll starting";
  }

  ATraceEnd();

  ScopedTrace trace("Resuming mutator threads");

  long_suspend_ = false;

  Locks::mutator_lock_->ExclusiveUnlock(self);

  // Decrement the suspend counts for all threads.
  for (const auto& thread : list_) {
    if (thread != self) {
      thread->DecrementSuspendCount(self);
    }
  }

  // Update global suspend all state for attaching threads. Unblocks other SuspendAlls once
  // suspend_count_lock_ is released.
  --suspend_all_count_;
  self->AtomicClearFlag(ThreadFlag::kSuspensionImmune, std::memory_order_relaxed);
  // Pending suspend requests for us will be handled when we become Runnable again.

  // Broadcast a notification to all suspended threads, some or all of
  // which may choose to wake up.  No need to wait for them.
  if (self != nullptr) {
    VLOG(threads) << *self << " ResumeAll waking others";
  } else {
    VLOG(threads) << "Thread[null] ResumeAll waking others";
  }
  Thread::resume_cond_->Broadcast(self);

  if (self != nullptr) {
    VLOG(threads) << *self << " ResumeAll complete";
  } else {
    VLOG(threads) << "Thread[null] ResumeAll complete";
  }
}

bool ThreadList::Resume(Thread* thread, SuspendReason reason) {
  // This assumes there was an ATraceBegin when we suspended the thread.
  ATraceEnd();

  Thread* self = Thread::Current();
  DCHECK_NE(thread, self);
  VLOG(threads) << "Resume(" << reinterpret_cast<void*>(thread) << ") starting..." << reason;

  {
    // To check Contains.
    MutexLock mu(self, *Locks::thread_list_lock_);
    // To check IsSuspended.
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    if (UNLIKELY(!thread->IsSuspended())) {
      LOG(reason == SuspendReason::kForUserCode ? ERROR : FATAL)
          << "Resume(" << reinterpret_cast<void*>(thread) << ") thread not suspended";
      return false;
    }
    if (!Contains(thread)) {
      // We only expect threads within the thread-list to have been suspended otherwise we can't
      // stop such threads from delete-ing themselves.
      LOG(reason == SuspendReason::kForUserCode ? ERROR : FATAL)
          << "Resume(" << reinterpret_cast<void*>(thread) << ") thread not within thread list";
      return false;
    }
    thread->DecrementSuspendCount(self, /*for_user_code=*/(reason == SuspendReason::kForUserCode));
    Thread::resume_cond_->Broadcast(self);
  }

  VLOG(threads) << "Resume(" << reinterpret_cast<void*>(thread) << ") finished waking others";
  return true;
}

bool ThreadList::SuspendThread(Thread* self,
                               Thread* thread,
                               SuspendReason reason,
                               ThreadState self_state,
                               const char* func_name,
                               int attempt_of_4) {
  bool is_suspended = false;
  VLOG(threads) << func_name << "starting";
  pid_t tid = thread->GetTid();
  uint8_t suspended_count;
  uint8_t checkpoint_count;
  WrappedSuspend1Barrier wrapped_barrier{};
  static_assert(sizeof wrapped_barrier.barrier_ == sizeof(uint32_t));
  ThreadExitFlag tef;
  bool exited = false;
  thread->NotifyOnThreadExit(&tef);
  int iter_count = 1;
  do {
    {
      Locks::mutator_lock_->AssertSharedHeld(self);
      Locks::thread_list_lock_->AssertHeld(self);
      // Note: this will transition to runnable and potentially suspend.
      DCHECK(Contains(thread));
      // This implementation fails if thread == self. Let the clients handle that case
      // appropriately.
      CHECK_NE(thread, self) << func_name << "(self)";
      VLOG(threads) << func_name << " suspending: " << *thread;
      {
        MutexLock suspend_count_mu(self, *Locks::thread_suspend_count_lock_);
        if (LIKELY(self->GetSuspendCount() == 0)) {
          suspended_count = thread->suspended_count_;
          checkpoint_count = thread->checkpoint_count_;
          thread->IncrementSuspendCount(self, nullptr, &wrapped_barrier, reason);
          if (thread->IsSuspended()) {
            // See the discussion in mutator_gc_coord.md and SuspendAllInternal for the race here.
            thread->RemoveFirstSuspend1Barrier(&wrapped_barrier);
            // PassActiveSuspendBarriers couldn't have seen our barrier, since it also acquires
            // 'thread_suspend_count_lock_'. `wrapped_barrier` will not be accessed.
            if (!thread->HasActiveSuspendBarrier()) {
              thread->AtomicClearFlag(ThreadFlag::kActiveSuspendBarrier);
            }
            is_suspended = true;
          }
          DCHECK_GT(thread->GetSuspendCount(), 0);
          break;
        }
        // Else we hold the suspend count lock but another thread is trying to suspend us,
        // making it unsafe to try to suspend another thread in case we get a cycle.
        // Start the loop again, which will allow this thread to be suspended.
      }
    }
    // All locks are released, and we should quickly exit the suspend-unfriendly state. Retry.
    if (iter_count >= kMaxSuspendRetries) {
      LOG(FATAL) << "Too many suspend retries";
    }
    Locks::thread_list_lock_->ExclusiveUnlock(self);
    {
      ScopedThreadSuspension sts(self, ThreadState::kSuspended);
      usleep(kThreadSuspendSleepUs);
      ++iter_count;
    }
    Locks::thread_list_lock_->ExclusiveLock(self);
    exited = tef.HasExited();
  } while (!exited);
  thread->UnregisterThreadExitFlag(&tef);
  Locks::thread_list_lock_->ExclusiveUnlock(self);
  self->TransitionFromRunnableToSuspended(self_state);
  if (exited) {
    // This is OK: There's a race in inflating a lock and the owner giving up ownership and then
    // dying.
    LOG(WARNING) << StringPrintf("Thread with tid %d exited before suspending", tid);
    return false;
  }
  // Now wait for target to decrement suspend barrier.
  std::optional<std::string> failure_info;
  if (!is_suspended) {
    failure_info = WaitForSuspendBarrier(&wrapped_barrier.barrier_, tid, attempt_of_4);
    if (!failure_info.has_value()) {
      is_suspended = true;
    }
  }
  while (!is_suspended) {
    if (attempt_of_4 > 0 && attempt_of_4 < 4) {
      // Caller will try again. Give up and resume the thread for now.  We need to make sure
      // that wrapped_barrier is removed from the list before we deallocate it.
      MutexLock suspend_count_mu(self, *Locks::thread_suspend_count_lock_);
      if (wrapped_barrier.barrier_.load() == 0) {
        // Succeeded in the meantime.
        is_suspended = true;
        continue;
      }
      thread->RemoveSuspend1Barrier(&wrapped_barrier);
      if (!thread->HasActiveSuspendBarrier()) {
        thread->AtomicClearFlag(ThreadFlag::kActiveSuspendBarrier);
      }
      // Do not call Resume(), since we are probably not fully suspended.
      thread->DecrementSuspendCount(self,
                                    /*for_user_code=*/(reason == SuspendReason::kForUserCode));
      Thread::resume_cond_->Broadcast(self);
      return false;
    }
    std::string name;
    thread->GetThreadName(name);
    WrappedSuspend1Barrier* first_barrier;
    {
      MutexLock suspend_count_mu(self, *Locks::thread_suspend_count_lock_);
      first_barrier = thread->tlsPtr_.active_suspend1_barriers;
    }
    // 'thread' should still have a suspend request pending, and hence stick around. Try to abort
    // there, since its stack trace is much more interesting than ours.
    std::string message = StringPrintf(
        "%s timed out: %d (%s), state&flags: 0x%x, priority: %d,"
        " barriers: %p, ours: %p, barrier value: %d, nsusps: %d, ncheckpts: %d, thread_info: %s",
        func_name,
        thread->GetTid(),
        name.c_str(),
        thread->GetStateAndFlags(std::memory_order_relaxed).GetValue(),
        thread->GetNativePriority(),
        first_barrier,
        &wrapped_barrier,
        wrapped_barrier.barrier_.load(),
        thread->suspended_count_ - suspended_count,
        thread->checkpoint_count_ - checkpoint_count,
        failure_info.value().c_str());
    // Check one last time whether thread passed the suspend barrier. Empirically this seems to
    // happen maybe between 1 and 5% of the time.
    if (wrapped_barrier.barrier_.load() != 0) {
      // thread still has a pointer to wrapped_barrier. Returning and continuing would be unsafe
      // without additional cleanup.
      thread->AbortInThis(message);
      UNREACHABLE();
    }
    is_suspended = true;
  }
  // wrapped_barrier.barrier_ will no longer be accessed.
  VLOG(threads) << func_name << " suspended: " << *thread;
  if (ATraceEnabled()) {
    std::string name;
    thread->GetThreadName(name);
    ATraceBegin(
        StringPrintf("%s suspended %s for tid=%d", func_name, name.c_str(), thread->GetTid())
            .c_str());
  }
  if (kIsDebugBuild) {
    CHECK(thread->IsSuspended());
    MutexLock suspend_count_mu(self, *Locks::thread_suspend_count_lock_);
    thread->CheckBarrierInactive(&wrapped_barrier);
  }
  return true;
}

Thread* ThreadList::SuspendThreadByPeer(jobject peer, SuspendReason reason) {
  Thread* const self = Thread::Current();
  ThreadState old_self_state = self->GetState();
  self->TransitionFromSuspendedToRunnable();
  Locks::thread_list_lock_->ExclusiveLock(self);
  ObjPtr<mirror::Object> thread_ptr = self->DecodeJObject(peer);
  Thread* thread = Thread::FromManagedThread(self, thread_ptr);
  if (thread == nullptr || !Contains(thread)) {
    if (thread == nullptr) {
      ObjPtr<mirror::Object> name = WellKnownClasses::java_lang_Thread_name->GetObject(thread_ptr);
      std::string thr_name = (name == nullptr ? "<unknown>" : name->AsString()->ToModifiedUtf8());
      LOG(WARNING) << "No such thread for suspend"
                   << ": " << peer << ":" << thr_name;
    } else {
      LOG(WARNING) << "SuspendThreadByPeer failed for unattached thread: "
                   << reinterpret_cast<void*>(thread);
    }
    Locks::thread_list_lock_->ExclusiveUnlock(self);
    self->TransitionFromRunnableToSuspended(old_self_state);
    return nullptr;
  }
  VLOG(threads) << "SuspendThreadByPeer found thread: " << *thread;
  // Releases thread_list_lock_ and mutator lock.
  bool success = SuspendThread(self, thread, reason, old_self_state, __func__, 0);
  Locks::thread_list_lock_->AssertNotHeld(self);
  return success ? thread : nullptr;
}

Thread* ThreadList::SuspendThreadByThreadId(uint32_t thread_id,
                                            SuspendReason reason,
                                            int attempt_of_4) {
  Thread* const self = Thread::Current();
  ThreadState old_self_state = self->GetState();
  CHECK_NE(thread_id, kInvalidThreadId);
  VLOG(threads) << "SuspendThreadByThreadId starting";
  self->TransitionFromSuspendedToRunnable();
  Locks::thread_list_lock_->ExclusiveLock(self);
  Thread* thread = FindThreadByThreadId(thread_id);
  if (thread == nullptr) {
    // There's a race in inflating a lock and the owner giving up ownership and then dying.
    LOG(WARNING) << StringPrintf("No such thread id %d for suspend", thread_id);
    Locks::thread_list_lock_->ExclusiveUnlock(self);
    self->TransitionFromRunnableToSuspended(old_self_state);
    return nullptr;
  }
  DCHECK(Contains(thread));
  VLOG(threads) << "SuspendThreadByThreadId found thread: " << *thread;
  // Releases thread_list_lock_ and mutator lock.
  bool success = SuspendThread(self, thread, reason, old_self_state, __func__, attempt_of_4);
  Locks::thread_list_lock_->AssertNotHeld(self);
  return success ? thread : nullptr;
}

Thread* ThreadList::FindThreadByThreadId(uint32_t thread_id) {
  for (const auto& thread : list_) {
    if (thread->GetThreadId() == thread_id) {
      return thread;
    }
  }
  return nullptr;
}

Thread* ThreadList::FindThreadByTid(int tid) {
  for (const auto& thread : list_) {
    if (thread->GetTid() == tid) {
      return thread;
    }
  }
  return nullptr;
}

void ThreadList::WaitForOtherNonDaemonThreadsToExit(bool check_no_birth) {
  ScopedTrace trace(__PRETTY_FUNCTION__);
  Thread* self = Thread::Current();
  Locks::mutator_lock_->AssertNotHeld(self);
  while (true) {
    Locks::runtime_shutdown_lock_->Lock(self);
    if (check_no_birth) {
      // No more threads can be born after we start to shutdown.
      CHECK(Runtime::Current()->IsShuttingDownLocked());
      CHECK_EQ(Runtime::Current()->NumberOfThreadsBeingBorn(), 0U);
    } else {
      if (Runtime::Current()->NumberOfThreadsBeingBorn() != 0U) {
        // Awkward. Shutdown_cond_ is private, but the only live thread may not be registered yet.
        // Fortunately, this is used mostly for testing, and not performance-critical.
        Locks::runtime_shutdown_lock_->Unlock(self);
        usleep(1000);
        continue;
      }
    }
    MutexLock mu(self, *Locks::thread_list_lock_);
    Locks::runtime_shutdown_lock_->Unlock(self);
    // Also wait for any threads that are unregistering to finish. This is required so that no
    // threads access the thread list after it is deleted. TODO: This may not work for user daemon
    // threads since they could unregister at the wrong time.
    bool done = unregistering_count_ == 0;
    if (done) {
      for (const auto& thread : list_) {
        if (thread != self && !thread->IsDaemon()) {
          done = false;
          break;
        }
      }
    }
    if (done) {
      break;
    }
    // Wait for another thread to exit before re-checking.
    Locks::thread_exit_cond_->Wait(self);
  }
}

void ThreadList::SuspendAllDaemonThreadsForShutdown() {
  ScopedTrace trace(__PRETTY_FUNCTION__);
  Thread* self = Thread::Current();
  size_t daemons_left = 0;
  {
    // Tell all the daemons it's time to suspend.
    MutexLock mu(self, *Locks::thread_list_lock_);
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    for (const auto& thread : list_) {
      // This is only run after all non-daemon threads have exited, so the remainder should all be
      // daemons.
      CHECK(thread->IsDaemon()) << *thread;
      if (thread != self) {
        thread->IncrementSuspendCount(self);
        ++daemons_left;
      }
      // We are shutting down the runtime, set the JNI functions of all the JNIEnvs to be
      // the sleep forever one.
      thread->GetJniEnv()->SetFunctionsToRuntimeShutdownFunctions();
    }
  }
  if (daemons_left == 0) {
    // No threads left; safe to shut down.
    return;
  }
  // There is not a clean way to shut down if we have daemons left. We have no mechanism for
  // killing them and reclaiming thread stacks. We also have no mechanism for waiting until they
  // have truly finished touching the memory we are about to deallocate. We do the best we can with
  // timeouts.
  //
  // If we have any daemons left, wait until they are (a) suspended and (b) they are not stuck
  // in a place where they are about to access runtime state and are not in a runnable state.
  // We attempt to do the latter by just waiting long enough for things to
  // quiesce. Examples: Monitor code or waking up from a condition variable.
  //
  // Give the threads a chance to suspend, complaining if they're slow. (a)
  bool have_complained = false;
  static constexpr size_t kTimeoutMicroseconds = 2000 * 1000;
  static constexpr size_t kSleepMicroseconds = 1000;
  bool all_suspended = false;
  for (size_t i = 0; !all_suspended && i < kTimeoutMicroseconds / kSleepMicroseconds; ++i) {
    bool found_running = false;
    {
      MutexLock mu(self, *Locks::thread_list_lock_);
      for (const auto& thread : list_) {
        if (thread != self && thread->GetState() == ThreadState::kRunnable) {
          if (!have_complained) {
            LOG(WARNING) << "daemon thread not yet suspended: " << *thread;
            have_complained = true;
          }
          found_running = true;
        }
      }
    }
    if (found_running) {
      // Sleep briefly before checking again. Max total sleep time is kTimeoutMicroseconds.
      usleep(kSleepMicroseconds);
    } else {
      all_suspended = true;
    }
  }
  if (!all_suspended) {
    // We can get here if a daemon thread executed a fastnative native call, so that it
    // remained in runnable state, and then made a JNI call after we called
    // SetFunctionsToRuntimeShutdownFunctions(), causing it to permanently stay in a harmless
    // but runnable state. See b/147804269 .
    LOG(WARNING) << "timed out suspending all daemon threads";
  }
  // Assume all threads are either suspended or somehow wedged.
  // Wait again for all the now "suspended" threads to actually quiesce. (b)
  static constexpr size_t kDaemonSleepTime = 400'000;
  usleep(kDaemonSleepTime);
  std::list<Thread*> list_copy;
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    // Half-way through the wait, set the "runtime deleted" flag, causing any newly awoken
    // threads to immediately go back to sleep without touching memory. This prevents us from
    // touching deallocated memory, but it also prevents mutexes from getting released. Thus we
    // only do this once we're reasonably sure that no system mutexes are still held.
    for (const auto& thread : list_) {
      DCHECK(thread == self || !all_suspended || thread->GetState() != ThreadState::kRunnable);
      // In the !all_suspended case, the target is probably sleeping.
      thread->GetJniEnv()->SetRuntimeDeleted();
      // Possibly contended Mutex acquisitions are unsafe after this.
      // Releasing thread_list_lock_ is OK, since it can't block.
    }
  }
  // Finally wait for any threads woken before we set the "runtime deleted" flags to finish
  // touching memory.
  usleep(kDaemonSleepTime);
#if defined(__has_feature)
#if __has_feature(address_sanitizer) || __has_feature(hwaddress_sanitizer)
  // Sleep a bit longer with -fsanitize=address, since everything is slower.
  usleep(2 * kDaemonSleepTime);
#endif
#endif
  // At this point no threads should be touching our data structures anymore.
}

void ThreadList::Register(Thread* self) {
  DCHECK_EQ(self, Thread::Current());
  CHECK(!shut_down_);

  if (VLOG_IS_ON(threads)) {
    std::ostringstream oss;
    self->ShortDump(oss);  // We don't hold the mutator_lock_ yet and so cannot call Dump.
    LOG(INFO) << "ThreadList::Register() " << *self  << "\n" << oss.str();
  }

  // Atomically add self to the thread list and make its thread_suspend_count_ reflect ongoing
  // SuspendAll requests.
  MutexLock mu(self, *Locks::thread_list_lock_);
  MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
  if (suspend_all_count_ == 1) {
    self->IncrementSuspendCount(self);
  } else {
    DCHECK_EQ(suspend_all_count_, 0);
  }
  CHECK(!Contains(self));
  list_.push_back(self);
  if (gUseReadBarrier) {
    gc::collector::ConcurrentCopying* const cc =
        Runtime::Current()->GetHeap()->ConcurrentCopyingCollector();
    // Initialize according to the state of the CC collector.
    self->SetIsGcMarkingAndUpdateEntrypoints(cc->IsMarking());
    if (cc->IsUsingReadBarrierEntrypoints()) {
      self->SetReadBarrierEntrypoints();
    }
    self->SetWeakRefAccessEnabled(cc->IsWeakRefAccessEnabled());
  }
}

void ThreadList::Unregister(Thread* self, bool should_run_callbacks) {
  DCHECK_EQ(self, Thread::Current());
  CHECK_NE(self->GetState(), ThreadState::kRunnable);
  Locks::mutator_lock_->AssertNotHeld(self);
  if (self->tls32_.disable_thread_flip_count != 0) {
    LOG(FATAL) << "Incomplete PrimitiveArrayCritical section at exit: " << *self << "count = "
               << self->tls32_.disable_thread_flip_count;
  }

  VLOG(threads) << "ThreadList::Unregister() " << *self;

  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    ++unregistering_count_;
  }

  // Any time-consuming destruction, plus anything that can call back into managed code or
  // suspend and so on, must happen at this point, and not in ~Thread. The self->Destroy is what
  // causes the threads to join. It is important to do this after incrementing unregistering_count_
  // since we want the runtime to wait for the daemon threads to exit before deleting the thread
  // list.
  self->Destroy(should_run_callbacks);

  uint32_t thin_lock_id = self->GetThreadId();
  while (true) {
    // Remove and delete the Thread* while holding the thread_list_lock_ and
    // thread_suspend_count_lock_ so that the unregistering thread cannot be suspended.
    // Note: deliberately not using MutexLock that could hold a stale self pointer.
    {
      MutexLock mu(self, *Locks::thread_list_lock_);
      if (!Contains(self)) {
        std::string thread_name;
        self->GetThreadName(thread_name);
        std::ostringstream os;
        DumpNativeStack(os, GetTid(), "  native: ", nullptr);
        LOG(FATAL) << "Request to unregister unattached thread " << thread_name << "\n" << os.str();
        UNREACHABLE();
      } else {
        MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
        Thread::StateAndFlags state_and_flags = self->GetStateAndFlags(std::memory_order_acquire);
        if (!state_and_flags.IsFlagSet(ThreadFlag::kRunningFlipFunction) &&
            !state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest)) {
          list_.remove(self);
          self->SignalExitFlags();
          break;
        }
      }
    }
    // In the case where we are not suspended yet, sleep to leave other threads time to execute.
    // This is important if there are realtime threads. b/111277984
    usleep(1);
    // We failed to remove the thread due to a suspend request or the like, loop and try again.
  }
  delete self;

  // Release the thread ID after the thread is finished and deleted to avoid cases where we can
  // temporarily have multiple threads with the same thread id. When this occurs, it causes
  // problems in FindThreadByThreadId / SuspendThreadByThreadId.
  ReleaseThreadId(nullptr, thin_lock_id);

  // Clear the TLS data, so that the underlying native thread is recognizably detached.
  // (It may wish to reattach later.)
#ifdef __BIONIC__
  __get_tls()[TLS_SLOT_ART_THREAD_SELF] = nullptr;
#else
  CHECK_PTHREAD_CALL(pthread_setspecific, (Thread::pthread_key_self_, nullptr), "detach self");
  Thread::self_tls_ = nullptr;
#endif

  // Signal that a thread just detached.
  MutexLock mu(nullptr, *Locks::thread_list_lock_);
  --unregistering_count_;
  Locks::thread_exit_cond_->Broadcast(nullptr);
}

void ThreadList::ForEach(void (*callback)(Thread*, void*), void* context) {
  for (const auto& thread : list_) {
    callback(thread, context);
  }
}

void ThreadList::WaitForUnregisterToComplete(Thread* self) {
  // We hold thread_list_lock_ .
  while (unregistering_count_ != 0) {
    LOG(WARNING) << "Waiting for a thread to finish unregistering";
    Locks::thread_exit_cond_->Wait(self);
  }
}

void ThreadList::VisitRootsForSuspendedThreads(RootVisitor* visitor) {
  Thread* const self = Thread::Current();
  std::vector<Thread*> threads_to_visit;

  // Tell threads to suspend and copy them into list.
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    for (Thread* thread : list_) {
      thread->IncrementSuspendCount(self);
      if (thread == self || thread->IsSuspended()) {
        threads_to_visit.push_back(thread);
      } else {
        thread->DecrementSuspendCount(self);
      }
    }
  }

  // Visit roots without holding thread_list_lock_ and thread_suspend_count_lock_ to prevent lock
  // order violations.
  for (Thread* thread : threads_to_visit) {
    thread->VisitRoots(visitor, kVisitRootFlagAllRoots);
  }

  // Restore suspend counts.
  {
    MutexLock mu2(self, *Locks::thread_suspend_count_lock_);
    for (Thread* thread : threads_to_visit) {
      thread->DecrementSuspendCount(self);
    }
    Thread::resume_cond_->Broadcast(self);
  }
}

void ThreadList::VisitRoots(RootVisitor* visitor, VisitRootFlags flags) const {
  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  for (const auto& thread : list_) {
    thread->VisitRoots(visitor, flags);
  }
}

void ThreadList::VisitReflectiveTargets(ReflectiveValueVisitor *visitor) const {
  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  for (const auto& thread : list_) {
    thread->VisitReflectiveTargets(visitor);
  }
}

void ThreadList::SweepInterpreterCaches(IsMarkedVisitor* visitor) const {
  MutexLock mu(Thread::Current(), *Locks::thread_list_lock_);
  for (const auto& thread : list_) {
    thread->SweepInterpreterCache(visitor);
  }
}

uint32_t ThreadList::AllocThreadId(Thread* self) {
  MutexLock mu(self, *Locks::allocated_thread_ids_lock_);
  for (size_t i = 0; i < allocated_ids_.size(); ++i) {
    if (!allocated_ids_[i]) {
      allocated_ids_.set(i);
      return i + 1;  // Zero is reserved to mean "invalid".
    }
  }
  LOG(FATAL) << "Out of internal thread ids";
  UNREACHABLE();
}

void ThreadList::ReleaseThreadId(Thread* self, uint32_t id) {
  MutexLock mu(self, *Locks::allocated_thread_ids_lock_);
  --id;  // Zero is reserved to mean "invalid".
  DCHECK(allocated_ids_[id]) << id;
  allocated_ids_.reset(id);
}

ScopedSuspendAll::ScopedSuspendAll(const char* cause, bool long_suspend) {
  Runtime::Current()->GetThreadList()->SuspendAll(cause, long_suspend);
}

ScopedSuspendAll::~ScopedSuspendAll() {
  Runtime::Current()->GetThreadList()->ResumeAll();
}

}  // namespace art

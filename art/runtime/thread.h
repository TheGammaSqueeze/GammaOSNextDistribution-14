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

#ifndef ART_RUNTIME_THREAD_H_
#define ART_RUNTIME_THREAD_H_

#include <atomic>
#include <bitset>
#include <deque>
#include <iosfwd>
#include <list>
#include <memory>
#include <string>

#include "base/atomic.h"
#include "base/bit_field.h"
#include "base/bit_utils.h"
#include "base/enums.h"
#include "base/locks.h"
#include "base/macros.h"
#include "base/safe_map.h"
#include "base/value_object.h"
#include "entrypoints/jni/jni_entrypoints.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "handle.h"
#include "handle_scope.h"
#include "interpreter/interpreter_cache.h"
#include "interpreter/shadow_frame.h"
#include "javaheapprof/javaheapsampler.h"
#include "jvalue.h"
#include "managed_stack.h"
#include "offsets.h"
#include "read_barrier_config.h"
#include "reflective_handle_scope.h"
#include "runtime_globals.h"
#include "runtime_stats.h"
#include "suspend_reason.h"
#include "thread_state.h"

namespace unwindstack {
class AndroidLocalUnwinder;
}  // namespace unwindstack

namespace art HIDDEN {

namespace gc {
namespace accounting {
template<class T> class AtomicStack;
}  // namespace accounting
namespace collector {
class SemiSpace;
}  // namespace collector
}  // namespace gc

namespace instrumentation {
struct InstrumentationStackFrame;
}  // namespace instrumentation

namespace mirror {
class Array;
class Class;
class ClassLoader;
class Object;
template<class T> class ObjectArray;
template<class T> class PrimitiveArray;
using IntArray = PrimitiveArray<int32_t>;
class StackTraceElement;
class String;
class Throwable;
}  // namespace mirror

namespace verifier {
class VerifierDeps;
}  // namespace verifier

class ArtMethod;
class BaseMutex;
class ClassLinker;
class Closure;
class Context;
class DeoptimizationContextRecord;
class DexFile;
class FrameIdToShadowFrame;
class IsMarkedVisitor;
class JavaVMExt;
class JNIEnvExt;
class Monitor;
class RootVisitor;
class ScopedObjectAccessAlreadyRunnable;
class ShadowFrame;
class StackedShadowFrameRecord;
class Thread;
class ThreadList;
enum VisitRootFlags : uint8_t;

// A piece of data that can be held in the CustomTls. The destructor will be called during thread
// shutdown. The thread the destructor is called on is not necessarily the same thread it was stored
// on.
class TLSData {
 public:
  virtual ~TLSData() {}
};

// Thread priorities. These must match the Thread.MIN_PRIORITY,
// Thread.NORM_PRIORITY, and Thread.MAX_PRIORITY constants.
enum ThreadPriority {
  kMinThreadPriority = 1,
  kNormThreadPriority = 5,
  kMaxThreadPriority = 10,
};

enum class ThreadFlag : uint32_t {
  // If set, implies that suspend_count_ > 0 and the Thread should enter the safepoint handler.
  kSuspendRequest = 1u << 0,

  // Request that the thread do some checkpoint work and then continue.
  kCheckpointRequest = 1u << 1,

  // Request that the thread do empty checkpoint and then continue.
  kEmptyCheckpointRequest = 1u << 2,

  // Register that at least 1 suspend barrier needs to be passed.
  // Changes to this flag are guarded by suspend_count_lock_ .
  kActiveSuspendBarrier = 1u << 3,

  // Marks that a "flip function" needs to be executed on this thread.
  // Set only while holding thread_list_lock_.
  kPendingFlipFunction = 1u << 4,

  // Marks that the "flip function" is being executed by another thread.
  //
  // This is used to guard against multiple threads trying to run the
  // "flip function" for the same thread while the thread is suspended.
  //
  // Set when we have some way to ensure that the thread cannot disappear out from under us,
  // Either:
  //   1) Set by the thread itself,
  //   2) by a thread holding thread_list_lock_, or
  //   3) while the target has a pending suspension request.
  // Once set, prevents a thread from exiting.
  kRunningFlipFunction = 1u << 5,

  // We are responsible for resuming all other threads. We ignore suspension requests,
  // but not checkpoint requests, until a more opportune time. GC code should
  // in any case not check for such requests; other clients of SuspendAll might.
  // Prevents a situation in which we are asked to suspend just before we suspend all
  // other threads, and then notice the suspension request and suspend ourselves,
  // leading to deadlock. Guarded by suspend_count_lock_ .
  // TODO(b/296639267): Generalize use to prevent SuspendAll from blocking
  // in-progress GC.
  kSuspensionImmune = 1u << 6,

  // Request that compiled JNI stubs do not transition to Native or Runnable with
  // inlined code, but take a slow path for monitoring method entry and exit events.
  kMonitorJniEntryExit = 1u << 7,

  // Indicates the last flag. Used for checking that the flags do not overlap thread state.
  kLastFlag = kMonitorJniEntryExit
};

enum class StackedShadowFrameType {
  kShadowFrameUnderConstruction,
  kDeoptimizationShadowFrame,
};

// The type of method that triggers deoptimization. It contains info on whether
// the deoptimized method should advance dex_pc.
enum class DeoptimizationMethodType {
  kKeepDexPc,  // dex pc is required to be kept upon deoptimization.
  kDefault     // dex pc may or may not advance depending on other conditions.
};

// For the CC colector, normal weak reference access can be disabled on a per-thread basis, while
// processing references.  After finishing, the reference processor asynchronously sets the
// per-thread flags back to kEnabled with release memory ordering semantics. Each mutator thread
// should check its flag with acquire semantics before assuming that it is enabled. However,
// that is often too expensive, so the reading thread sets it to kVisiblyEnabled after seeing it
// kEnabled.  The Reference.get() intrinsic can thus read it in relaxed mode, and reread (by
// resorting to the slow path) with acquire semantics if it sees a value of kEnabled rather than
// kVisiblyEnabled.
enum class WeakRefAccessState : int32_t {
  kVisiblyEnabled = 0,  // Enabled, and previously read with acquire load by this thread.
  kEnabled,
  kDisabled
};

// See Thread.tlsPtr_.active_suspend1_barriers below for explanation.
struct WrappedSuspend1Barrier {
  // TODO(b/23668816): At least weaken CHECKs to DCHECKs once the bug is fixed.
  static constexpr int kMagic = 0xba8;
  WrappedSuspend1Barrier() : magic_(kMagic), barrier_(1), next_(nullptr) {}
  int magic_;
  AtomicInteger barrier_;
  struct WrappedSuspend1Barrier* next_ GUARDED_BY(Locks::thread_suspend_count_lock_);
};

// Mostly opaque structure allocated by the client of NotifyOnThreadExit.  Allows a client to
// check whether the thread still exists after temporarily releasing thread_list_lock_, usually
// because we need to wait for something.
class ThreadExitFlag {
 public:
  ThreadExitFlag() : exited_(false) {}
  bool HasExited() REQUIRES(Locks::thread_list_lock_) { return exited_; }

 private:
  // All ThreadExitFlags associated with a thread and with exited_ == false are in a doubly linked
  // list.  tlsPtr_.thread_exit_flags points to the first element.  first.prev_ and last.next_ are
  // null. This list contains no ThreadExitFlags with exited_ == true;
  ThreadExitFlag* next_ GUARDED_BY(Locks::thread_list_lock_);
  ThreadExitFlag* prev_ GUARDED_BY(Locks::thread_list_lock_);
  bool exited_ GUARDED_BY(Locks::thread_list_lock_);
  friend class Thread;
};

// This should match RosAlloc::kNumThreadLocalSizeBrackets.
static constexpr size_t kNumRosAllocThreadLocalSizeBracketsInThread = 16;

static constexpr size_t kSharedMethodHotnessThreshold = 0x1fff;

// Thread's stack layout for implicit stack overflow checks:
//
//   +---------------------+  <- highest address of stack memory
//   |                     |
//   .                     .  <- SP
//   |                     |
//   |                     |
//   +---------------------+  <- stack_end
//   |                     |
//   |  Gap                |
//   |                     |
//   +---------------------+  <- stack_begin
//   |                     |
//   | Protected region    |
//   |                     |
//   +---------------------+  <- lowest address of stack memory
//
// The stack always grows down in memory.  At the lowest address is a region of memory
// that is set mprotect(PROT_NONE).  Any attempt to read/write to this region will
// result in a segmentation fault signal.  At any point, the thread's SP will be somewhere
// between the stack_end and the highest address in stack memory.  An implicit stack
// overflow check is a read of memory at a certain offset below the current SP (8K typically).
// If the thread's SP is below the stack_end address this will be a read into the protected
// region.  If the SP is above the stack_end address, the thread is guaranteed to have
// at least 8K of space.  Because stack overflow checks are only performed in generated code,
// if the thread makes a call out to a native function (through JNI), that native function
// might only have 4K of memory (if the SP is adjacent to stack_end).

class EXPORT Thread {
 public:
  static const size_t kStackOverflowImplicitCheckSize;
  static constexpr bool kVerifyStack = kIsDebugBuild;

  // Creates a new native thread corresponding to the given managed peer.
  // Used to implement Thread.start.
  static void CreateNativeThread(JNIEnv* env, jobject peer, size_t stack_size, bool daemon);

  // Attaches the calling native thread to the runtime, returning the new native peer.
  // Used to implement JNI AttachCurrentThread and AttachCurrentThreadAsDaemon calls.
  static Thread* Attach(const char* thread_name,
                        bool as_daemon,
                        jobject thread_group,
                        bool create_peer,
                        bool should_run_callbacks);
  // Attaches the calling native thread to the runtime, returning the new native peer.
  static Thread* Attach(const char* thread_name, bool as_daemon, jobject thread_peer);

  // Reset internal state of child thread after fork.
  void InitAfterFork();

  // Get the currently executing thread, frequently referred to as 'self'. This call has reasonably
  // high cost and so we favor passing self around when possible.
  // TODO: mark as PURE so the compiler may coalesce and remove?
  static Thread* Current();

  // Get the thread from the JNI environment.
  static Thread* ForEnv(JNIEnv* env);

  // For implicit overflow checks we reserve an extra piece of memory at the bottom of the stack
  // (lowest memory). The higher portion of the memory is protected against reads and the lower is
  // available for use while throwing the StackOverflow exception.
  ALWAYS_INLINE static size_t GetStackOverflowProtectedSize();

  // On a runnable thread, check for pending thread suspension request and handle if pending.
  void AllowThreadSuspension() REQUIRES_SHARED(Locks::mutator_lock_);

  // Process pending thread suspension request and handle if pending.
  void CheckSuspend(bool implicit = false) REQUIRES_SHARED(Locks::mutator_lock_);

  // Process a pending empty checkpoint if pending.
  void CheckEmptyCheckpointFromWeakRefAccess(BaseMutex* cond_var_mutex);
  void CheckEmptyCheckpointFromMutex();

  static Thread* FromManagedThread(Thread* self, ObjPtr<mirror::Object> thread_peer)
      REQUIRES(Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);
  static Thread* FromManagedThread(const ScopedObjectAccessAlreadyRunnable& ts, jobject thread)
      REQUIRES(Locks::thread_list_lock_, !Locks::thread_suspend_count_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Translates 172 to pAllocArrayFromCode and so on.
  template<PointerSize size_of_pointers>
  static void DumpThreadOffset(std::ostream& os, uint32_t offset);

  // Dumps a one-line summary of thread state (used for operator<<).
  void ShortDump(std::ostream& os) const;

  // Order of threads for ANRs (ANRs can be trimmed, so we print important ones first).
  enum class DumpOrder : uint8_t {
    kMain,     // Always print the main thread first (there might not be one).
    kBlocked,  // Then print all threads that are blocked due to waiting on lock.
    kLocked,   // Then print all threads that are holding some lock already.
    kDefault,  // Print all other threads which might not be interesting for ANR.
  };

  // Dumps the detailed thread state and the thread stack (used for SIGQUIT).
  DumpOrder Dump(std::ostream& os,
                 bool dump_native_stack = true,
                 bool force_dump_stack = false) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  DumpOrder Dump(std::ostream& os,
                 unwindstack::AndroidLocalUnwinder& unwinder,
                 bool dump_native_stack = true,
                 bool force_dump_stack = false) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  DumpOrder DumpJavaStack(std::ostream& os,
                          bool check_suspended = true,
                          bool dump_locks = true) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Dumps the SIGQUIT per-thread header. 'thread' can be null for a non-attached thread, in which
  // case we use 'tid' to identify the thread, and we'll include as much information as we can.
  static void DumpState(std::ostream& os, const Thread* thread, pid_t tid)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ThreadState GetState() const {
    return GetStateAndFlags(std::memory_order_relaxed).GetState();
  }

  ThreadState SetState(ThreadState new_state);

  int GetSuspendCount() const REQUIRES(Locks::thread_suspend_count_lock_) {
    return tls32_.suspend_count;
  }

  int GetUserCodeSuspendCount() const REQUIRES(Locks::thread_suspend_count_lock_,
                                               Locks::user_code_suspension_lock_) {
    return tls32_.user_code_suspend_count;
  }

  bool IsSuspended() const {
    // We need to ensure that once we return true, all prior accesses to the Java data by "this"
    // thread are complete. Hence we need "acquire" ordering here, and "release" when the flags
    // are set.
    StateAndFlags state_and_flags = GetStateAndFlags(std::memory_order_acquire);
    return state_and_flags.GetState() != ThreadState::kRunnable &&
           state_and_flags.IsFlagSet(ThreadFlag::kSuspendRequest);
  }

  void DecrDefineClassCount() {
    tls32_.define_class_counter--;
  }

  void IncrDefineClassCount() {
    tls32_.define_class_counter++;
  }
  uint32_t GetDefineClassCount() const {
    return tls32_.define_class_counter;
  }

  // Increment suspend count and optionally install at most one suspend barrier.
  // Must hold thread_list_lock, OR be called with self == this, so that the Thread cannot
  // disappear while we're running. If it's known that this == self, and thread_list_lock_
  // is not held, FakeMutexLock should be used to fake-acquire thread_list_lock_ for
  // static checking purposes.
  ALWAYS_INLINE
  void IncrementSuspendCount(Thread* self,
                             AtomicInteger* suspendall_barrier,
                             WrappedSuspend1Barrier* suspend1_barrier,
                             SuspendReason reason) REQUIRES(Locks::thread_suspend_count_lock_)
      REQUIRES(Locks::thread_list_lock_);

  // The same, but default reason to kInternal, and barriers to nullptr.
  ALWAYS_INLINE void IncrementSuspendCount(Thread* self) REQUIRES(Locks::thread_suspend_count_lock_)
      REQUIRES(Locks::thread_list_lock_);

  // Follows one of the above calls. For_user_code indicates if SuspendReason was kForUserCode.
  // Generally will need to be closely followed by Thread::resume_cond_->Broadcast(self);
  // since there may be waiters. DecrementSuspendCount() itself does not do this, since we often
  // wake more than a single thread.
  ALWAYS_INLINE void DecrementSuspendCount(Thread* self, bool for_user_code = false)
      REQUIRES(Locks::thread_suspend_count_lock_);

 private:
  NO_RETURN static void UnsafeLogFatalForSuspendCount(Thread* self, Thread* thread);

 public:
  // Requests a checkpoint closure to run on another thread. The closure will be run when the
  // thread notices the request, either in an explicit runtime CheckSuspend() call, or in a call
  // originating from a compiler generated suspend point check. This returns true if the closure
  // was added and will (eventually) be executed. It returns false if this was impossible
  // because the thread was suspended, and we thus did nothing.
  //
  // Since multiple closures can be queued and some closures can delay other threads from running,
  // no closure should attempt to suspend another thread while running.
  // TODO We should add some debug option that verifies this.
  //
  // This guarantees that the RequestCheckpoint invocation happens-before the function invocation:
  // RequestCheckpointFunction holds thread_suspend_count_lock_, and RunCheckpointFunction
  // acquires it.
  bool RequestCheckpoint(Closure* function)
      REQUIRES(Locks::thread_suspend_count_lock_);

  // RequestSynchronousCheckpoint releases the thread_list_lock_ as a part of its execution. This is
  // due to the fact that Thread::Current() needs to go to sleep to allow the targeted thread to
  // execute the checkpoint for us if it is Runnable. The wait_state is the state that the thread
  // will go into while it is awaiting the checkpoint to be run.
  // The closure may be run on Thread::Current() on behalf of "this" thread.
  // Thus for lock ordering purposes, the closure should be runnable by the caller. This also
  // sometimes makes it reasonable to pass ThreadState::kRunnable as wait_state: We may wait on
  // a condition variable for the "this" thread to act, but for lock ordering purposes, this is
  // exactly as though Thread::Current() had run the closure.
  // NB Since multiple closures can be queued and some closures can delay other threads from running
  // no closure should attempt to suspend another thread while running.
  bool RequestSynchronousCheckpoint(Closure* function,
                                    ThreadState wait_state = ThreadState::kWaiting)
      REQUIRES_SHARED(Locks::mutator_lock_) RELEASE(Locks::thread_list_lock_)
          REQUIRES(!Locks::thread_suspend_count_lock_);

  bool RequestEmptyCheckpoint()
      REQUIRES(Locks::thread_suspend_count_lock_);

  Closure* GetFlipFunction() { return tlsPtr_.flip_function.load(std::memory_order_relaxed); }

  // Set the flip function. This is done with all threads suspended, except for the calling thread.
  void SetFlipFunction(Closure* function) REQUIRES(Locks::thread_suspend_count_lock_)
      REQUIRES(Locks::thread_list_lock_);

  // Wait for the flip function to complete if still running on another thread. Assumes the "this"
  // thread remains live.
  void WaitForFlipFunction(Thread* self) const REQUIRES(!Locks::thread_suspend_count_lock_);

  // An enhanced version of the above that uses tef to safely return if the thread exited in the
  // meantime.
  void WaitForFlipFunctionTestingExited(Thread* self, ThreadExitFlag* tef)
      REQUIRES(!Locks::thread_suspend_count_lock_, !Locks::thread_list_lock_);

  gc::accounting::AtomicStack<mirror::Object>* GetThreadLocalMarkStack() {
    CHECK(gUseReadBarrier);
    return tlsPtr_.thread_local_mark_stack;
  }
  void SetThreadLocalMarkStack(gc::accounting::AtomicStack<mirror::Object>* stack) {
    CHECK(gUseReadBarrier);
    tlsPtr_.thread_local_mark_stack = stack;
  }

  uint8_t* GetThreadLocalGcBuffer() {
    DCHECK(gUseUserfaultfd);
    return tlsPtr_.thread_local_gc_buffer;
  }
  void SetThreadLocalGcBuffer(uint8_t* buf) {
    DCHECK(gUseUserfaultfd);
    tlsPtr_.thread_local_gc_buffer = buf;
  }

  // Called when thread detected that the thread_suspend_count_ was non-zero. Gives up share of
  // mutator_lock_ and waits until it is resumed and thread_suspend_count_ is zero.
  // Should be called only when the kSuspensionImmune flag is clear. Requires this == Current();
  void FullSuspendCheck(bool implicit = false)
      REQUIRES(!Locks::thread_suspend_count_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Transition from non-runnable to runnable state acquiring share on mutator_lock_.
  ALWAYS_INLINE ThreadState TransitionFromSuspendedToRunnable()
      REQUIRES(!Locks::thread_suspend_count_lock_)
      SHARED_LOCK_FUNCTION(Locks::mutator_lock_);

  // Transition from runnable into a state where mutator privileges are denied. Releases share of
  // mutator lock.
  ALWAYS_INLINE void TransitionFromRunnableToSuspended(ThreadState new_state)
      REQUIRES(!Locks::thread_suspend_count_lock_, !Roles::uninterruptible_)
      UNLOCK_FUNCTION(Locks::mutator_lock_);

  // Once called thread suspension will cause an assertion failure.
  const char* StartAssertNoThreadSuspension(const char* cause) ACQUIRE(Roles::uninterruptible_) {
    Roles::uninterruptible_.Acquire();  // No-op.
    if (kIsDebugBuild) {
      CHECK(cause != nullptr);
      const char* previous_cause = tlsPtr_.last_no_thread_suspension_cause;
      tls32_.no_thread_suspension++;
      tlsPtr_.last_no_thread_suspension_cause = cause;
      return previous_cause;
    } else {
      return nullptr;
    }
  }

  // End region where no thread suspension is expected.
  void EndAssertNoThreadSuspension(const char* old_cause) RELEASE(Roles::uninterruptible_) {
    if (kIsDebugBuild) {
      CHECK_IMPLIES(old_cause == nullptr, tls32_.no_thread_suspension == 1);
      CHECK_GT(tls32_.no_thread_suspension, 0U);
      tls32_.no_thread_suspension--;
      tlsPtr_.last_no_thread_suspension_cause = old_cause;
    }
    Roles::uninterruptible_.Release();  // No-op.
  }

  // End region where no thread suspension is expected. Returns the current open region in case we
  // want to reopen it. Used for ScopedAllowThreadSuspension. Not supported if no_thread_suspension
  // is larger than one.
  const char* EndAssertNoThreadSuspension() RELEASE(Roles::uninterruptible_) WARN_UNUSED {
    const char* ret = nullptr;
    if (kIsDebugBuild) {
      CHECK_EQ(tls32_.no_thread_suspension, 1u);
      tls32_.no_thread_suspension--;
      ret = tlsPtr_.last_no_thread_suspension_cause;
      tlsPtr_.last_no_thread_suspension_cause = nullptr;
    }
    Roles::uninterruptible_.Release();  // No-op.
    return ret;
  }

  void AssertThreadSuspensionIsAllowable(bool check_locks = true) const;

  // Return true if thread suspension is allowable.
  bool IsThreadSuspensionAllowable() const;

  bool IsDaemon() const {
    return tls32_.daemon;
  }

  size_t NumberOfHeldMutexes() const;

  bool HoldsLock(ObjPtr<mirror::Object> object) const REQUIRES_SHARED(Locks::mutator_lock_);

  /*
   * Changes the priority of this thread to match that of the java.lang.Thread object.
   *
   * We map a priority value from 1-10 to Linux "nice" values, where lower
   * numbers indicate higher priority.
   */
  void SetNativePriority(int newPriority);

  /*
   * Returns the priority of this thread by querying the system.
   * This is useful when attaching a thread through JNI.
   *
   * Returns a value from 1 to 10 (compatible with java.lang.Thread values).
   */
  int GetNativePriority() const;

  // Guaranteed to be non-zero.
  uint32_t GetThreadId() const {
    return tls32_.thin_lock_thread_id;
  }

  pid_t GetTid() const {
    return tls32_.tid;
  }

  // Returns the java.lang.Thread's name, or null if this Thread* doesn't have a peer.
  ObjPtr<mirror::String> GetThreadName() const REQUIRES_SHARED(Locks::mutator_lock_);

  // Sets 'name' to the java.lang.Thread's name. This requires no transition to managed code,
  // allocation, or locking.
  void GetThreadName(std::string& name) const;

  // Sets the thread's name.
  void SetThreadName(const char* name) REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns the thread-specific CPU-time clock in microseconds or -1 if unavailable.
  uint64_t GetCpuMicroTime() const;

  mirror::Object* GetPeer() const REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK(Thread::Current() == this) << "Use GetPeerFromOtherThread instead";
    CHECK(tlsPtr_.jpeer == nullptr);
    return tlsPtr_.opeer;
  }
  // GetPeer is not safe if called on another thread in the middle of the thread flip and
  // the thread's stack may have not been flipped yet and peer may be a from-space (stale) ref.
  // This function will force a flip for the other thread if necessary.
  // Since we hold a shared mutator lock, a new flip function cannot be concurrently installed.
  // The target thread must be suspended, so that it cannot disappear during the call.
  // We should ideally not hold thread_list_lock_ . GetReferenceKind in ti_heap.cc, currently does
  // hold it, but in a context in which we do not invoke EnsureFlipFunctionStarted().
  mirror::Object* GetPeerFromOtherThread() REQUIRES_SHARED(Locks::mutator_lock_);

  // A version of the above that requires thread_list_lock_, but does not require the thread to
  // be suspended. This may temporarily release thread_list_lock_. It thus needs a ThreadExitFlag
  // describing the thread's status, so we can tell if it exited in the interim. Returns null if
  // the thread exited.
  mirror::Object* LockedGetPeerFromOtherThread(ThreadExitFlag* tef)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::thread_list_lock_);

  // A convenience version of the above that creates the ThreadExitFlag locally. This is often
  // unsafe if more than one thread is being processed. A prior call may have released
  // thread_list_lock_, and thus the NotifyOnThreadExit() call here could see a deallocated
  // Thread. We must hold the thread_list_lock continuously between obtaining the Thread*
  // and calling NotifyOnThreadExit().
  mirror::Object* LockedGetPeerFromOtherThread() REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(Locks::thread_list_lock_) {
    ThreadExitFlag tef;
    NotifyOnThreadExit(&tef);
    mirror::Object* result = LockedGetPeerFromOtherThread(&tef);
    UnregisterThreadExitFlag(&tef);
    return result;
  }

  bool HasPeer() const {
    return tlsPtr_.jpeer != nullptr || tlsPtr_.opeer != nullptr;
  }

  RuntimeStats* GetStats() {
    return &tls64_.stats;
  }

  bool IsStillStarting() const;

  bool IsExceptionPending() const {
    return tlsPtr_.exception != nullptr;
  }

  bool IsAsyncExceptionPending() const {
    return tlsPtr_.async_exception != nullptr;
  }

  mirror::Throwable* GetException() const REQUIRES_SHARED(Locks::mutator_lock_) {
    return tlsPtr_.exception;
  }

  void AssertPendingException() const;
  void AssertPendingOOMException() const REQUIRES_SHARED(Locks::mutator_lock_);
  void AssertNoPendingException() const;
  void AssertNoPendingExceptionForNewException(const char* msg) const;

  void SetException(ObjPtr<mirror::Throwable> new_exception) REQUIRES_SHARED(Locks::mutator_lock_);

  // Set an exception that is asynchronously thrown from a different thread. This will be checked
  // periodically and might overwrite the current 'Exception'. This can only be called from a
  // checkpoint.
  //
  // The caller should also make sure that the thread has been deoptimized so that the exception
  // could be detected on back-edges.
  void SetAsyncException(ObjPtr<mirror::Throwable> new_exception)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void ClearException() REQUIRES_SHARED(Locks::mutator_lock_) {
    tlsPtr_.exception = nullptr;
  }

  // Move the current async-exception to the main exception. This should be called when the current
  // thread is ready to deal with any async exceptions. Returns true if there is an async exception
  // that needs to be dealt with, false otherwise.
  bool ObserveAsyncException() REQUIRES_SHARED(Locks::mutator_lock_);

  // Find catch block and perform long jump to appropriate exception handle. When
  // is_method_exit_exception is true, the exception was thrown by the method exit callback and we
  // should not send method unwind for the method on top of the stack since method exit callback was
  // already called.
  NO_RETURN void QuickDeliverException(bool is_method_exit_exception = false)
      REQUIRES_SHARED(Locks::mutator_lock_);

  Context* GetLongJumpContext();
  void ReleaseLongJumpContext(Context* context) {
    if (tlsPtr_.long_jump_context != nullptr) {
      ReleaseLongJumpContextInternal();
    }
    tlsPtr_.long_jump_context = context;
  }

  // Get the current method and dex pc. If there are errors in retrieving the dex pc, this will
  // abort the runtime iff abort_on_error is true.
  ArtMethod* GetCurrentMethod(uint32_t* dex_pc,
                              bool check_suspended = true,
                              bool abort_on_error = true) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Returns whether the given exception was thrown by the current Java method being executed
  // (Note that this includes native Java methods).
  bool IsExceptionThrownByCurrentMethod(ObjPtr<mirror::Throwable> exception) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  void SetTopOfStack(ArtMethod** top_method) {
    tlsPtr_.managed_stack.SetTopQuickFrame(top_method);
  }

  void SetTopOfStackGenericJniTagged(ArtMethod** top_method) {
    tlsPtr_.managed_stack.SetTopQuickFrameGenericJniTagged(top_method);
  }

  void SetTopOfShadowStack(ShadowFrame* top) {
    tlsPtr_.managed_stack.SetTopShadowFrame(top);
  }

  bool HasManagedStack() const {
    return tlsPtr_.managed_stack.HasTopQuickFrame() || tlsPtr_.managed_stack.HasTopShadowFrame();
  }

  // If 'msg' is null, no detail message is set.
  void ThrowNewException(const char* exception_class_descriptor, const char* msg)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  // If 'msg' is null, no detail message is set. An exception must be pending, and will be
  // used as the new exception's cause.
  void ThrowNewWrappedException(const char* exception_class_descriptor, const char* msg)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  void ThrowNewExceptionF(const char* exception_class_descriptor, const char* fmt, ...)
      __attribute__((format(printf, 3, 4)))
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  void ThrowNewExceptionV(const char* exception_class_descriptor, const char* fmt, va_list ap)
      REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(!Roles::uninterruptible_);

  // OutOfMemoryError is special, because we need to pre-allocate an instance.
  // Only the GC should call this.
  void ThrowOutOfMemoryError(const char* msg) REQUIRES_SHARED(Locks::mutator_lock_)
      REQUIRES(!Roles::uninterruptible_);

  static void Startup();
  static void FinishStartup();
  static void Shutdown();

  // Notify this thread's thread-group that this thread has started.
  // Note: the given thread-group is used as a fast path and verified in debug build. If the value
  //       is null, the thread's thread-group is loaded from the peer.
  void NotifyThreadGroup(ScopedObjectAccessAlreadyRunnable& soa, jobject thread_group = nullptr)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Request notification when this thread is unregistered, typically because it has exited.
  //
  // The ThreadExitFlag status is only changed when we remove the thread from the thread list,
  // which we only do once no suspend requests are outstanding, and no flip-functions are still
  // running.
  //
  // The caller must allocate a fresh ThreadExitFlag, and pass it in. The caller is responsible
  // for either waiting until the thread has exited, or unregistering the ThreadExitFlag, and
  // then, and only then, deallocating the ThreadExitFlag.  (This scheme avoids an allocation and
  // questions about what to do if the allocation fails. Allows detection of thread exit after
  // temporary release of thread_list_lock_)
  void NotifyOnThreadExit(ThreadExitFlag* tef) REQUIRES(Locks::thread_list_lock_);
  void UnregisterThreadExitFlag(ThreadExitFlag* tef) REQUIRES(Locks::thread_list_lock_);

  // Is the ThreadExitFlag currently registered in this thread, which has not yet terminated?
  // Intended only for testing.
  bool IsRegistered(ThreadExitFlag* query_tef) REQUIRES(Locks::thread_list_lock_);

  // For debuggable builds, CHECK that neither first nor last, nor any ThreadExitFlag with an
  // address in-between, is currently registered with any thread.
  static void DCheckUnregisteredEverywhere(ThreadExitFlag* first, ThreadExitFlag* last)
      REQUIRES(!Locks::thread_list_lock_);

  // Called when thread is unregistered. May be called repeatedly, in which case only newly
  // registered clients are processed.
  void SignalExitFlags() REQUIRES(Locks::thread_list_lock_);

  // JNI methods
  JNIEnvExt* GetJniEnv() const {
    return tlsPtr_.jni_env;
  }

  // Convert a jobject into a Object*
  ObjPtr<mirror::Object> DecodeJObject(jobject obj) const REQUIRES_SHARED(Locks::mutator_lock_);
  // Checks if the weak global ref has been cleared by the GC without decoding it.
  bool IsJWeakCleared(jweak obj) const REQUIRES_SHARED(Locks::mutator_lock_);

  mirror::Object* GetMonitorEnterObject() const REQUIRES_SHARED(Locks::mutator_lock_) {
    return tlsPtr_.monitor_enter_object;
  }

  void SetMonitorEnterObject(mirror::Object* obj) REQUIRES_SHARED(Locks::mutator_lock_) {
    tlsPtr_.monitor_enter_object = obj;
  }

  // Implements java.lang.Thread.interrupted.
  bool Interrupted();
  // Implements java.lang.Thread.isInterrupted.
  bool IsInterrupted();
  void Interrupt(Thread* self) REQUIRES(!wait_mutex_);
  void SetInterrupted(bool i) {
    tls32_.interrupted.store(i, std::memory_order_seq_cst);
  }
  void Notify() REQUIRES(!wait_mutex_);

  ALWAYS_INLINE void PoisonObjectPointers() {
    ++poison_object_cookie_;
  }

  ALWAYS_INLINE static void PoisonObjectPointersIfDebug();

  ALWAYS_INLINE uintptr_t GetPoisonObjectCookie() const {
    return poison_object_cookie_;
  }

  // Parking for 0ns of relative time means an untimed park, negative (though
  // should be handled in java code) returns immediately
  void Park(bool is_absolute, int64_t time) REQUIRES_SHARED(Locks::mutator_lock_);
  void Unpark();

 private:
  void NotifyLocked(Thread* self) REQUIRES(wait_mutex_);

 public:
  Mutex* GetWaitMutex() const LOCK_RETURNED(wait_mutex_) {
    return wait_mutex_;
  }

  ConditionVariable* GetWaitConditionVariable() const REQUIRES(wait_mutex_) {
    return wait_cond_;
  }

  Monitor* GetWaitMonitor() const REQUIRES(wait_mutex_) {
    return wait_monitor_;
  }

  void SetWaitMonitor(Monitor* mon) REQUIRES(wait_mutex_) {
    wait_monitor_ = mon;
  }

  // Waiter link-list support.
  Thread* GetWaitNext() const {
    return tlsPtr_.wait_next;
  }

  void SetWaitNext(Thread* next) {
    tlsPtr_.wait_next = next;
  }

  jobject GetClassLoaderOverride() {
    return tlsPtr_.class_loader_override;
  }

  void SetClassLoaderOverride(jobject class_loader_override);

  // Create the internal representation of a stack trace, that is more time
  // and space efficient to compute than the StackTraceElement[].
  jobject CreateInternalStackTrace(const ScopedObjectAccessAlreadyRunnable& soa) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Convert an internal stack trace representation (returned by CreateInternalStackTrace) to a
  // StackTraceElement[]. If output_array is null, a new array is created, otherwise as many
  // frames as will fit are written into the given array. If stack_depth is non-null, it's updated
  // with the number of valid frames in the returned array.
  static jobjectArray InternalStackTraceToStackTraceElementArray(
      const ScopedObjectAccessAlreadyRunnable& soa, jobject internal,
      jobjectArray output_array = nullptr, int* stack_depth = nullptr)
      REQUIRES_SHARED(Locks::mutator_lock_);

  static jint InternalStackTraceToStackFrameInfoArray(
      const ScopedObjectAccessAlreadyRunnable& soa,
      jlong mode,  // See java.lang.StackStreamFactory for the mode flags
      jobject internal,
      jint startLevel,
      jint batchSize,
      jint startIndex,
      jobjectArray output_array)  // java.lang.StackFrameInfo[]
      REQUIRES_SHARED(Locks::mutator_lock_);

  jobjectArray CreateAnnotatedStackTrace(const ScopedObjectAccessAlreadyRunnable& soa) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  bool HasDebuggerShadowFrames() const {
    return tlsPtr_.frame_id_to_shadow_frame != nullptr;
  }

  // This is done by GC using a checkpoint (or in a stop-the-world pause).
  void SweepInterpreterCache(IsMarkedVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitRoots(RootVisitor* visitor, VisitRootFlags flags)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void VisitReflectiveTargets(ReflectiveValueVisitor* visitor)
      REQUIRES(Locks::mutator_lock_);

  // Check that the thread state is valid. Try to fail if the thread has erroneously terminated.
  // Note that once the thread has been terminated, it can also be deallocated.  But even if the
  // thread state has been overwritten, the value is unlikely to be in the correct range.
  void VerifyState() {
    if (kIsDebugBuild) {
      ThreadState state = GetState();
      StateAndFlags::ValidateThreadState(state);
      DCHECK_NE(state, ThreadState::kTerminated);
    }
  }

  void VerifyStack() REQUIRES_SHARED(Locks::mutator_lock_) {
    if (kVerifyStack) {
      VerifyStackImpl();
    }
  }

  //
  // Offsets of various members of native Thread class, used by compiled code.
  //

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThinLockIdOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, thin_lock_thread_id));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TidOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, tid));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> InterruptedOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, interrupted));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> WeakRefAccessEnabledOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, weak_ref_access_enabled));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadFlagsOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, state_and_flags));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> IsGcMarkingOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, is_gc_marking));
  }

  template <PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> DeoptCheckRequiredOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, is_deopt_check_required));
  }

  static constexpr size_t IsGcMarkingSize() {
    return sizeof(tls32_.is_gc_marking);
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> SharedMethodHotnessOffset() {
    return ThreadOffset<pointer_size>(
        OFFSETOF_MEMBER(Thread, tls32_) +
        OFFSETOF_MEMBER(tls_32bit_sized_values, shared_method_hotness));
  }

  // Deoptimize the Java stack.
  void DeoptimizeWithDeoptimizationException(JValue* result) REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadOffsetFromTlsPtr(size_t tls_ptr_offset) {
    size_t base = OFFSETOF_MEMBER(Thread, tlsPtr_);
    size_t scale = (pointer_size > kRuntimePointerSize) ?
      static_cast<size_t>(pointer_size) / static_cast<size_t>(kRuntimePointerSize) : 1;
    size_t shrink = (kRuntimePointerSize > pointer_size) ?
      static_cast<size_t>(kRuntimePointerSize) / static_cast<size_t>(pointer_size) : 1;
    return ThreadOffset<pointer_size>(base + ((tls_ptr_offset * scale) / shrink));
  }

 public:
  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> QuickEntryPointOffset(
      size_t quick_entrypoint_offset) {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, quick_entrypoints) + quick_entrypoint_offset);
  }

  static constexpr uint32_t QuickEntryPointOffsetWithSize(size_t quick_entrypoint_offset,
                                                          PointerSize pointer_size) {
    if (pointer_size == PointerSize::k32) {
      return QuickEntryPointOffset<PointerSize::k32>(quick_entrypoint_offset).
          Uint32Value();
    } else {
      return QuickEntryPointOffset<PointerSize::k64>(quick_entrypoint_offset).
          Uint32Value();
    }
  }

  template<PointerSize pointer_size>
  static ThreadOffset<pointer_size> JniEntryPointOffset(size_t jni_entrypoint_offset) {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, jni_entrypoints) + jni_entrypoint_offset);
  }

  // Return the entry point offset integer value for ReadBarrierMarkRegX, where X is `reg`.
  template <PointerSize pointer_size>
  static constexpr int32_t ReadBarrierMarkEntryPointsOffset(size_t reg) {
    // The entry point list defines 30 ReadBarrierMarkRegX entry points.
    DCHECK_LT(reg, 30u);
    // The ReadBarrierMarkRegX entry points are ordered by increasing
    // register number in Thread::tls_Ptr_.quick_entrypoints.
    return QUICK_ENTRYPOINT_OFFSET(pointer_size, pReadBarrierMarkReg00).Int32Value()
        + static_cast<size_t>(pointer_size) * reg;
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> SelfOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values, self));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ExceptionOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values, exception));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> PeerOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values, opeer));
  }


  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> CardTableOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values, card_table));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadSuspendTriggerOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, suspend_trigger));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadLocalPosOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                thread_local_pos));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadLocalEndOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                thread_local_end));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadLocalObjectsOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                thread_local_objects));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> RosAllocRunsOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                rosalloc_runs));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadLocalAllocStackTopOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                thread_local_alloc_stack_top));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> ThreadLocalAllocStackEndOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                thread_local_alloc_stack_end));
  }

  template <PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TraceBufferIndexOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, method_trace_buffer_index));
  }

  template <PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TraceBufferPtrOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, method_trace_buffer));
  }

  // Size of stack less any space reserved for stack overflow
  size_t GetStackSize() const {
    return tlsPtr_.stack_size - (tlsPtr_.stack_end - tlsPtr_.stack_begin);
  }

  ALWAYS_INLINE uint8_t* GetStackEndForInterpreter(bool implicit_overflow_check) const;

  uint8_t* GetStackEnd() const {
    return tlsPtr_.stack_end;
  }

  // Set the stack end to that to be used during a stack overflow
  void SetStackEndForStackOverflow() REQUIRES_SHARED(Locks::mutator_lock_);

  // Set the stack end to that to be used during regular execution
  ALWAYS_INLINE void ResetDefaultStackEnd();

  bool IsHandlingStackOverflow() const {
    return tlsPtr_.stack_end == tlsPtr_.stack_begin;
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> StackEndOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, stack_end));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> JniEnvOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, jni_env));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TopOfManagedStackOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, managed_stack) +
        ManagedStack::TaggedTopQuickFrameOffset());
  }

  const ManagedStack* GetManagedStack() const {
    return &tlsPtr_.managed_stack;
  }

  // Linked list recording fragments of managed stack.
  void PushManagedStackFragment(ManagedStack* fragment) {
    tlsPtr_.managed_stack.PushManagedStackFragment(fragment);
  }
  void PopManagedStackFragment(const ManagedStack& fragment) {
    tlsPtr_.managed_stack.PopManagedStackFragment(fragment);
  }

  ALWAYS_INLINE ShadowFrame* PushShadowFrame(ShadowFrame* new_top_frame);
  ALWAYS_INLINE ShadowFrame* PopShadowFrame();

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TopShadowFrameOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(
        OFFSETOF_MEMBER(tls_ptr_sized_values, managed_stack) +
        ManagedStack::TopShadowFrameOffset());
  }

  // Is the given obj in one of this thread's JNI transition frames?
  bool IsJniTransitionReference(jobject obj) const REQUIRES_SHARED(Locks::mutator_lock_);

  // Convert a global (or weak global) jobject into a Object*
  ObjPtr<mirror::Object> DecodeGlobalJObject(jobject obj) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  void HandleScopeVisitRoots(RootVisitor* visitor, uint32_t thread_id)
      REQUIRES_SHARED(Locks::mutator_lock_);

  BaseHandleScope* GetTopHandleScope() REQUIRES_SHARED(Locks::mutator_lock_) {
    return tlsPtr_.top_handle_scope;
  }

  void PushHandleScope(BaseHandleScope* handle_scope) REQUIRES_SHARED(Locks::mutator_lock_) {
    DCHECK_EQ(handle_scope->GetLink(), tlsPtr_.top_handle_scope);
    tlsPtr_.top_handle_scope = handle_scope;
  }

  BaseHandleScope* PopHandleScope() REQUIRES_SHARED(Locks::mutator_lock_) {
    BaseHandleScope* handle_scope = tlsPtr_.top_handle_scope;
    DCHECK(handle_scope != nullptr);
    tlsPtr_.top_handle_scope = tlsPtr_.top_handle_scope->GetLink();
    return handle_scope;
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> TopHandleScopeOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                top_handle_scope));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> MutatorLockOffset() {
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                mutator_lock));
  }

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> HeldMutexOffset(LockLevel level) {
    DCHECK_LT(enum_cast<size_t>(level), arraysize(tlsPtr_.held_mutexes));
    return ThreadOffsetFromTlsPtr<pointer_size>(OFFSETOF_MEMBER(tls_ptr_sized_values,
                                                                held_mutexes[level]));
  }

  BaseReflectiveHandleScope* GetTopReflectiveHandleScope() {
    return tlsPtr_.top_reflective_handle_scope;
  }

  void PushReflectiveHandleScope(BaseReflectiveHandleScope* scope) {
    DCHECK_EQ(scope->GetLink(), tlsPtr_.top_reflective_handle_scope);
    DCHECK_EQ(scope->GetThread(), this);
    tlsPtr_.top_reflective_handle_scope = scope;
  }

  BaseReflectiveHandleScope* PopReflectiveHandleScope() {
    BaseReflectiveHandleScope* handle_scope = tlsPtr_.top_reflective_handle_scope;
    DCHECK(handle_scope != nullptr);
    tlsPtr_.top_reflective_handle_scope = tlsPtr_.top_reflective_handle_scope->GetLink();
    return handle_scope;
  }

  bool GetIsGcMarking() const {
    DCHECK(gUseReadBarrier);
    return tls32_.is_gc_marking;
  }

  void SetIsGcMarkingAndUpdateEntrypoints(bool is_marking);

  bool IsDeoptCheckRequired() const { return tls32_.is_deopt_check_required; }

  void SetDeoptCheckRequired(bool flag) { tls32_.is_deopt_check_required = flag; }

  bool GetWeakRefAccessEnabled() const;  // Only safe for current thread.

  void SetWeakRefAccessEnabled(bool enabled) {
    DCHECK(gUseReadBarrier);
    WeakRefAccessState new_state = enabled ?
        WeakRefAccessState::kEnabled : WeakRefAccessState::kDisabled;
    tls32_.weak_ref_access_enabled.store(new_state, std::memory_order_release);
  }

  uint32_t GetDisableThreadFlipCount() const {
    return tls32_.disable_thread_flip_count;
  }

  void IncrementDisableThreadFlipCount() {
    ++tls32_.disable_thread_flip_count;
  }

  void DecrementDisableThreadFlipCount() {
    DCHECK_GT(tls32_.disable_thread_flip_count, 0U);
    --tls32_.disable_thread_flip_count;
  }

  // Returns true if the thread is a runtime thread (eg from a ThreadPool).
  bool IsRuntimeThread() const {
    return is_runtime_thread_;
  }

  void SetIsRuntimeThread(bool is_runtime_thread) {
    is_runtime_thread_ = is_runtime_thread;
  }

  uint32_t CorePlatformApiCookie() {
    return core_platform_api_cookie_;
  }

  void SetCorePlatformApiCookie(uint32_t cookie) {
    core_platform_api_cookie_ = cookie;
  }

  // Returns true if the thread is allowed to load java classes.
  bool CanLoadClasses() const;

  // Returns the fake exception used to activate deoptimization.
  static mirror::Throwable* GetDeoptimizationException() {
    // Note that the mirror::Throwable must be aligned to kObjectAlignment or else it cannot be
    // represented by ObjPtr.
    return reinterpret_cast<mirror::Throwable*>(0x100);
  }

  // Currently deoptimization invokes verifier which can trigger class loading
  // and execute Java code, so there might be nested deoptimizations happening.
  // We need to save the ongoing deoptimization shadow frames and return
  // values on stacks.
  // 'from_code' denotes whether the deoptimization was explicitly made from
  // compiled code.
  // 'method_type' contains info on whether deoptimization should advance
  // dex_pc.
  void PushDeoptimizationContext(const JValue& return_value,
                                 bool is_reference,
                                 ObjPtr<mirror::Throwable> exception,
                                 bool from_code,
                                 DeoptimizationMethodType method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void PopDeoptimizationContext(JValue* result,
                                ObjPtr<mirror::Throwable>* exception,
                                bool* from_code,
                                DeoptimizationMethodType* method_type)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void AssertHasDeoptimizationContext()
      REQUIRES_SHARED(Locks::mutator_lock_);
  void PushStackedShadowFrame(ShadowFrame* sf, StackedShadowFrameType type);
  ShadowFrame* PopStackedShadowFrame();
  ShadowFrame* MaybePopDeoptimizedStackedShadowFrame();

  // For debugger, find the shadow frame that corresponds to a frame id.
  // Or return null if there is none.
  ShadowFrame* FindDebuggerShadowFrame(size_t frame_id)
      REQUIRES_SHARED(Locks::mutator_lock_);
  // For debugger, find the bool array that keeps track of the updated vreg set
  // for a frame id.
  bool* GetUpdatedVRegFlags(size_t frame_id) REQUIRES_SHARED(Locks::mutator_lock_);
  // For debugger, find the shadow frame that corresponds to a frame id. If
  // one doesn't exist yet, create one and track it in frame_id_to_shadow_frame.
  ShadowFrame* FindOrCreateDebuggerShadowFrame(size_t frame_id,
                                               uint32_t num_vregs,
                                               ArtMethod* method,
                                               uint32_t dex_pc)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Delete the entry that maps from frame_id to shadow_frame.
  void RemoveDebuggerShadowFrameMapping(size_t frame_id)
      REQUIRES_SHARED(Locks::mutator_lock_);

  std::vector<ArtMethod*>* GetStackTraceSample() const {
    DCHECK(!IsAotCompiler());
    return tlsPtr_.deps_or_stack_trace_sample.stack_trace_sample;
  }

  void SetStackTraceSample(std::vector<ArtMethod*>* sample) {
    DCHECK(!IsAotCompiler());
    tlsPtr_.deps_or_stack_trace_sample.stack_trace_sample = sample;
  }

  verifier::VerifierDeps* GetVerifierDeps() const {
    DCHECK(IsAotCompiler());
    return tlsPtr_.deps_or_stack_trace_sample.verifier_deps;
  }

  // It is the responsability of the caller to make sure the verifier_deps
  // entry in the thread is cleared before destruction of the actual VerifierDeps
  // object, or the thread.
  void SetVerifierDeps(verifier::VerifierDeps* verifier_deps) {
    DCHECK(IsAotCompiler());
    DCHECK(verifier_deps == nullptr || tlsPtr_.deps_or_stack_trace_sample.verifier_deps == nullptr);
    tlsPtr_.deps_or_stack_trace_sample.verifier_deps = verifier_deps;
  }

  uintptr_t* GetMethodTraceBuffer() { return tlsPtr_.method_trace_buffer; }

  size_t* GetMethodTraceIndexPtr() { return &tlsPtr_.method_trace_buffer_index; }

  uintptr_t* SetMethodTraceBuffer(uintptr_t* buffer) {
    return tlsPtr_.method_trace_buffer = buffer;
  }

  uint64_t GetTraceClockBase() const {
    return tls64_.trace_clock_base;
  }

  void SetTraceClockBase(uint64_t clock_base) {
    tls64_.trace_clock_base = clock_base;
  }

  BaseMutex* GetHeldMutex(LockLevel level) const {
    return tlsPtr_.held_mutexes[level];
  }

  void SetHeldMutex(LockLevel level, BaseMutex* mutex) {
    tlsPtr_.held_mutexes[level] = mutex;
  }

  // Possibly check that no mutexes at level kMonitorLock or above are subsequently acquired.
  // Only invoked by the thread itself.
  void DisallowPreMonitorMutexes();

  // Undo the effect of the previous call. Again only invoked by the thread itself.
  void AllowPreMonitorMutexes();

  bool ReadFlag(ThreadFlag flag) const {
    return GetStateAndFlags(std::memory_order_relaxed).IsFlagSet(flag);
  }

  void AtomicSetFlag(ThreadFlag flag, std::memory_order order = std::memory_order_seq_cst) {
    // Since we discard the returned value, memory_order_release will often suffice.
    tls32_.state_and_flags.fetch_or(enum_cast<uint32_t>(flag), order);
  }

  void AtomicClearFlag(ThreadFlag flag, std::memory_order order = std::memory_order_seq_cst) {
    // Since we discard the returned value, memory_order_release will often suffice.
    tls32_.state_and_flags.fetch_and(~enum_cast<uint32_t>(flag), order);
  }

  void ResetQuickAllocEntryPointsForThread();

  // Returns the remaining space in the TLAB.
  size_t TlabSize() const {
    return tlsPtr_.thread_local_end - tlsPtr_.thread_local_pos;
  }

  // Returns pos offset from start.
  size_t GetTlabPosOffset() const {
    return tlsPtr_.thread_local_pos - tlsPtr_.thread_local_start;
  }

  // Returns the remaining space in the TLAB if we were to expand it to maximum capacity.
  size_t TlabRemainingCapacity() const {
    return tlsPtr_.thread_local_limit - tlsPtr_.thread_local_pos;
  }

  // Expand the TLAB by a fixed number of bytes. There must be enough capacity to do so.
  void ExpandTlab(size_t bytes) {
    tlsPtr_.thread_local_end += bytes;
    DCHECK_LE(tlsPtr_.thread_local_end, tlsPtr_.thread_local_limit);
  }

  // Called from Concurrent mark-compact GC to slide the TLAB pointers backwards
  // to adjust to post-compact addresses.
  void AdjustTlab(size_t slide_bytes);

  // Doesn't check that there is room.
  mirror::Object* AllocTlab(size_t bytes);
  void SetTlab(uint8_t* start, uint8_t* end, uint8_t* limit);
  bool HasTlab() const;
  void ResetTlab();
  uint8_t* GetTlabStart() {
    return tlsPtr_.thread_local_start;
  }
  uint8_t* GetTlabPos() {
    return tlsPtr_.thread_local_pos;
  }
  uint8_t* GetTlabEnd() {
    return tlsPtr_.thread_local_end;
  }
  // Remove the suspend trigger for this thread by making the suspend_trigger_ TLS value
  // equal to a valid pointer.
  void RemoveSuspendTrigger() {
    tlsPtr_.suspend_trigger.store(reinterpret_cast<uintptr_t*>(&tlsPtr_.suspend_trigger),
                                  std::memory_order_relaxed);
  }

  // Trigger a suspend check by making the suspend_trigger_ TLS value an invalid pointer.
  // The next time a suspend check is done, it will load from the value at this address
  // and trigger a SIGSEGV.
  // Only needed if Runtime::implicit_suspend_checks_ is true. On some platforms, and in the
  // interpreter, client code currently just looks at the thread flags directly to determine
  // whether we should suspend, so this call is not always necessary.
  void TriggerSuspend() { tlsPtr_.suspend_trigger.store(nullptr, std::memory_order_release); }

  // Push an object onto the allocation stack.
  bool PushOnThreadLocalAllocationStack(mirror::Object* obj)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Set the thread local allocation pointers to the given pointers.
  void SetThreadLocalAllocationStack(StackReference<mirror::Object>* start,
                                     StackReference<mirror::Object>* end);

  // Resets the thread local allocation pointers.
  void RevokeThreadLocalAllocationStack();

  size_t GetThreadLocalBytesAllocated() const {
    return tlsPtr_.thread_local_end - tlsPtr_.thread_local_start;
  }

  size_t GetThreadLocalObjectsAllocated() const {
    return tlsPtr_.thread_local_objects;
  }

  void* GetRosAllocRun(size_t index) const {
    return tlsPtr_.rosalloc_runs[index];
  }

  void SetRosAllocRun(size_t index, void* run) {
    tlsPtr_.rosalloc_runs[index] = run;
  }

  bool ProtectStack(bool fatal_on_error = true);
  bool UnprotectStack();

  uint32_t DecrementForceInterpreterCount() REQUIRES(Locks::thread_list_lock_) {
    return --tls32_.force_interpreter_count;
  }

  uint32_t IncrementForceInterpreterCount() REQUIRES(Locks::thread_list_lock_) {
    return ++tls32_.force_interpreter_count;
  }

  void SetForceInterpreterCount(uint32_t value) REQUIRES(Locks::thread_list_lock_) {
    tls32_.force_interpreter_count = value;
  }

  uint32_t ForceInterpreterCount() const {
    return tls32_.force_interpreter_count;
  }

  bool IsForceInterpreter() const {
    return tls32_.force_interpreter_count != 0;
  }

  bool IncrementMakeVisiblyInitializedCounter() {
    tls32_.make_visibly_initialized_counter += 1u;
    DCHECK_LE(tls32_.make_visibly_initialized_counter, kMakeVisiblyInitializedCounterTriggerCount);
    if (tls32_.make_visibly_initialized_counter == kMakeVisiblyInitializedCounterTriggerCount) {
      tls32_.make_visibly_initialized_counter = 0u;
      return true;
    }
    return false;
  }

  void InitStringEntryPoints();

  void ModifyDebugDisallowReadBarrier(int8_t delta) {
    if (kCheckDebugDisallowReadBarrierCount) {
      debug_disallow_read_barrier_ += delta;
    }
  }

  uint8_t GetDebugDisallowReadBarrierCount() const {
    return kCheckDebugDisallowReadBarrierCount ? debug_disallow_read_barrier_ : 0u;
  }

  // Gets the current TLSData associated with the key or nullptr if there isn't any. Note that users
  // do not gain ownership of TLSData and must synchronize with SetCustomTls themselves to prevent
  // it from being deleted.
  TLSData* GetCustomTLS(const char* key) REQUIRES(!Locks::custom_tls_lock_);

  // Sets the tls entry at 'key' to data. The thread takes ownership of the TLSData. The destructor
  // will be run when the thread exits or when SetCustomTLS is called again with the same key.
  void SetCustomTLS(const char* key, TLSData* data) REQUIRES(!Locks::custom_tls_lock_);

  // Returns true if the current thread is the jit sensitive thread.
  bool IsJitSensitiveThread() const {
    return this == jit_sensitive_thread_;
  }

  bool IsSystemDaemon() const REQUIRES_SHARED(Locks::mutator_lock_);

  // Cause the 'this' thread to abort the process by sending SIGABRT.  Thus we should get an
  // asynchronous stack trace for 'this' thread, rather than waiting for it to process a
  // checkpoint. Useful mostly to discover why a thread isn't responding to a suspend request or
  // checkpoint. The caller should "suspend" (in the Java sense) 'thread' before invoking this, so
  // 'thread' can't get deallocated before we access it.
  NO_RETURN void AbortInThis(std::string message);

  // Returns true if StrictMode events are traced for the current thread.
  static bool IsSensitiveThread() {
    if (is_sensitive_thread_hook_ != nullptr) {
      return (*is_sensitive_thread_hook_)();
    }
    return false;
  }

  // Set to the read barrier marking entrypoints to be non-null.
  void SetReadBarrierEntrypoints();

  ObjPtr<mirror::Object> CreateCompileTimePeer(const char* name,
                                               bool as_daemon,
                                               jobject thread_group)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE InterpreterCache* GetInterpreterCache() {
    return &interpreter_cache_;
  }

  // Clear all thread-local interpreter caches.
  //
  // Since the caches are keyed by memory pointer to dex instructions, this must be
  // called when any dex code is unloaded (before different code gets loaded at the
  // same memory location).
  //
  // If presence of cache entry implies some pre-conditions, this must also be
  // called if the pre-conditions might no longer hold true.
  static void ClearAllInterpreterCaches();

  template<PointerSize pointer_size>
  static constexpr ThreadOffset<pointer_size> InterpreterCacheOffset() {
    return ThreadOffset<pointer_size>(OFFSETOF_MEMBER(Thread, interpreter_cache_));
  }

  static constexpr int InterpreterCacheSizeLog2() {
    return WhichPowerOf2(InterpreterCache::kSize);
  }

  static constexpr uint32_t AllThreadFlags() {
    return enum_cast<uint32_t>(ThreadFlag::kLastFlag) |
           (enum_cast<uint32_t>(ThreadFlag::kLastFlag) - 1u);
  }

  static constexpr uint32_t SuspendOrCheckpointRequestFlags() {
    return enum_cast<uint32_t>(ThreadFlag::kSuspendRequest) |
           enum_cast<uint32_t>(ThreadFlag::kCheckpointRequest) |
           enum_cast<uint32_t>(ThreadFlag::kEmptyCheckpointRequest);
  }

  static constexpr uint32_t FlipFunctionFlags() {
    return enum_cast<uint32_t>(ThreadFlag::kPendingFlipFunction) |
           enum_cast<uint32_t>(ThreadFlag::kRunningFlipFunction);
  }

  static constexpr uint32_t StoredThreadStateValue(ThreadState state) {
    return StateAndFlags::EncodeState(state);
  }

  void ResetSharedMethodHotness() {
    tls32_.shared_method_hotness = kSharedMethodHotnessThreshold;
  }

  uint32_t GetSharedMethodHotness() const {
    return tls32_.shared_method_hotness;
  }

  uint32_t DecrementSharedMethodHotness() {
    tls32_.shared_method_hotness = (tls32_.shared_method_hotness - 1) & 0xffff;
    return tls32_.shared_method_hotness;
  }

 private:
  // We pretend to acquire this while running a checkpoint to detect lock ordering issues.
  // Initialized lazily.
  static std::atomic<Mutex*> cp_placeholder_mutex_;

  explicit Thread(bool daemon);

  // A successfully started thread is only deleted by the thread itself.
  // Threads are deleted after they have been removed from the thread list while holding
  // suspend_count_lock_ and thread_list_lock_. We refuse to do this while either kSuspendRequest
  // or kRunningFlipFunction are set. We can prevent Thread destruction by holding either of those
  // locks, ensuring that either of those flags are set, or possibly by registering and checking a
  // ThreadExitFlag.
  ~Thread() REQUIRES(!Locks::mutator_lock_, !Locks::thread_suspend_count_lock_);

  // Thread destruction actions that do not invalidate the thread. Checkpoints and flip_functions
  // may still be called on this Thread object, though not by this thread, during and after the
  // Destroy() call.
  void Destroy(bool should_run_callbacks);

  // Deletes and clears the tlsPtr_.jpeer field. Done in a way so that both it and opeer cannot be
  // observed to be set at the same time by instrumentation.
  void DeleteJPeer(JNIEnv* env);

  // Attaches the calling native thread to the runtime, returning the new native peer.
  // Used to implement JNI AttachCurrentThread and AttachCurrentThreadAsDaemon calls.
  template <typename PeerAction>
  static Thread* Attach(const char* thread_name,
                        bool as_daemon,
                        PeerAction p,
                        bool should_run_callbacks);

  void CreatePeer(const char* name, bool as_daemon, jobject thread_group);

  template<bool kTransactionActive>
  static void InitPeer(ObjPtr<mirror::Object> peer,
                       bool as_daemon,
                       ObjPtr<mirror::Object> thread_group,
                       ObjPtr<mirror::String> thread_name,
                       jint thread_priority)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Avoid use, callers should use SetState.
  // Used only by `Thread` destructor and stack trace collection in semi-space GC (currently
  // disabled by `kStoreStackTraces = false`). May not be called on a runnable thread other
  // than Thread::Current().
  // NO_THREAD_SAFETY_ANALYSIS: This function is "Unsafe" and can be called in
  // different states, so clang cannot perform the thread safety analysis.
  ThreadState SetStateUnsafe(ThreadState new_state) NO_THREAD_SAFETY_ANALYSIS {
    StateAndFlags old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
    ThreadState old_state = old_state_and_flags.GetState();
    if (old_state == new_state) {
      // Nothing to do.
    } else if (old_state == ThreadState::kRunnable) {
      DCHECK_EQ(this, Thread::Current());
      // Need to run pending checkpoint and suspend barriers. Run checkpoints in runnable state in
      // case they need to use a ScopedObjectAccess. If we are holding the mutator lock and a SOA
      // attempts to TransitionFromSuspendedToRunnable, it results in a deadlock.
      TransitionToSuspendedAndRunCheckpoints(new_state);
      // Since we transitioned to a suspended state, check the pass barrier requests.
      CheckActiveSuspendBarriers();
    } else {
      while (true) {
        StateAndFlags new_state_and_flags = old_state_and_flags;
        new_state_and_flags.SetState(new_state);
        if (LIKELY(tls32_.state_and_flags.CompareAndSetWeakAcquire(
                old_state_and_flags.GetValue(), new_state_and_flags.GetValue()))) {
          break;
        }
        // Reload state and flags.
        old_state_and_flags = GetStateAndFlags(std::memory_order_relaxed);
        DCHECK_EQ(old_state, old_state_and_flags.GetState());
      }
    }
    return old_state;
  }

  MutatorMutex* GetMutatorLock() RETURN_CAPABILITY(Locks::mutator_lock_) {
    DCHECK_EQ(tlsPtr_.mutator_lock, Locks::mutator_lock_);
    return tlsPtr_.mutator_lock;
  }

  void VerifyStackImpl() REQUIRES_SHARED(Locks::mutator_lock_);

  void DumpState(std::ostream& os) const REQUIRES_SHARED(Locks::mutator_lock_);
  DumpOrder DumpStack(std::ostream& os,
                      bool dump_native_stack = true,
                      bool force_dump_stack = false) const
      REQUIRES_SHARED(Locks::mutator_lock_);
  DumpOrder DumpStack(std::ostream& os,
                      unwindstack::AndroidLocalUnwinder& unwinder,
                      bool dump_native_stack = true,
                      bool force_dump_stack = false) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Out-of-line conveniences for debugging in gdb.
  static Thread* CurrentFromGdb();  // Like Thread::Current.
  // Like Thread::Dump(std::cerr).
  void DumpFromGdb() const REQUIRES_SHARED(Locks::mutator_lock_);

  // A wrapper around CreateCallback used when userfaultfd GC is used to
  // identify the GC by stacktrace.
  static NO_INLINE void* CreateCallbackWithUffdGc(void* arg);
  static void* CreateCallback(void* arg);

  void HandleUncaughtExceptions() REQUIRES_SHARED(Locks::mutator_lock_);
  void RemoveFromThreadGroup() REQUIRES_SHARED(Locks::mutator_lock_);

  // Initialize a thread.
  //
  // The third parameter is not mandatory. If given, the thread will use this JNIEnvExt. In case
  // Init succeeds, this means the thread takes ownership of it. If Init fails, it is the caller's
  // responsibility to destroy the given JNIEnvExt. If the parameter is null, Init will try to
  // create a JNIEnvExt on its own (and potentially fail at that stage, indicated by a return value
  // of false).
  bool Init(ThreadList*, JavaVMExt*, JNIEnvExt* jni_env_ext = nullptr)
      REQUIRES(Locks::runtime_shutdown_lock_);
  void InitCardTable();
  void InitCpu();
  void CleanupCpu();
  void InitTlsEntryPoints();
  void InitTid();
  void InitPthreadKeySelf();
  bool InitStackHwm();

  void SetUpAlternateSignalStack();
  void TearDownAlternateSignalStack();
  void MadviseAwayAlternateSignalStack();

  ALWAYS_INLINE void TransitionToSuspendedAndRunCheckpoints(ThreadState new_state)
      REQUIRES(!Locks::thread_suspend_count_lock_, !Roles::uninterruptible_)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // Call PassActiveSuspendBarriers() if there are active barriers. Only called on current thread.
  ALWAYS_INLINE void CheckActiveSuspendBarriers()
      REQUIRES(!Locks::thread_suspend_count_lock_, !Locks::mutator_lock_, !Roles::uninterruptible_);

  // Decrement all "suspend barriers" for the current thread, notifying threads that requested our
  // suspension. Only called on current thread, when suspended. If suspend_count_ > 0 then we
  // promise that we are and will remain "suspended" until the suspend count is decremented.
  bool PassActiveSuspendBarriers()
      REQUIRES(!Locks::thread_suspend_count_lock_, !Locks::mutator_lock_);

  // Add an entry to active_suspend1_barriers.
  ALWAYS_INLINE void AddSuspend1Barrier(WrappedSuspend1Barrier* suspend1_barrier)
      REQUIRES(Locks::thread_suspend_count_lock_);

  // Remove last-added entry from active_suspend1_barriers.
  // Only makes sense if we're still holding thread_suspend_count_lock_ since insertion.
  // We redundantly pass in the barrier to be removed in order to enable a DCHECK.
  ALWAYS_INLINE void RemoveFirstSuspend1Barrier(WrappedSuspend1Barrier* suspend1_barrier)
      REQUIRES(Locks::thread_suspend_count_lock_);

  // Remove the "barrier" from the list no matter where it appears. Called only under exceptional
  // circumstances. The barrier must be in the list.
  ALWAYS_INLINE void RemoveSuspend1Barrier(WrappedSuspend1Barrier* suspend1_barrier)
      REQUIRES(Locks::thread_suspend_count_lock_);

  ALWAYS_INLINE bool HasActiveSuspendBarrier() REQUIRES(Locks::thread_suspend_count_lock_);

  // CHECK that the given barrier is no longer on our list.
  ALWAYS_INLINE void CheckBarrierInactive(WrappedSuspend1Barrier* suspend1_barrier)
      REQUIRES(Locks::thread_suspend_count_lock_);

  // Registers the current thread as the jit sensitive thread. Should be called just once.
  static void SetJitSensitiveThread() {
    if (jit_sensitive_thread_ == nullptr) {
      jit_sensitive_thread_ = Thread::Current();
    } else {
      LOG(WARNING) << "Attempt to set the sensitive thread twice. Tid:"
          << Thread::Current()->GetTid();
    }
  }

  static void SetSensitiveThreadHook(bool (*is_sensitive_thread_hook)()) {
    is_sensitive_thread_hook_ = is_sensitive_thread_hook;
  }

  // Runs a single checkpoint function. If there are no more pending checkpoint functions it will
  // clear the kCheckpointRequest flag. The caller is responsible for calling this in a loop until
  // the kCheckpointRequest flag is cleared.
  void RunCheckpointFunction()
      REQUIRES(!Locks::thread_suspend_count_lock_)
      REQUIRES_SHARED(Locks::mutator_lock_);
  void RunEmptyCheckpoint();

  // Install the protected region for implicit stack checks.
  void InstallImplicitProtection();

  template <bool kPrecise>
  void VisitRoots(RootVisitor* visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  static bool IsAotCompiler();

  void ReleaseLongJumpContextInternal();

  void SetCachedThreadName(const char* name);

  // Helper class for manipulating the 32 bits of atomically changed state and flags.
  class StateAndFlags {
   public:
    explicit StateAndFlags(uint32_t value) :value_(value) {}

    uint32_t GetValue() const {
      return value_;
    }

    void SetValue(uint32_t value) {
      value_ = value;
    }

    bool IsAnyOfFlagsSet(uint32_t flags) const {
      DCHECK_EQ(flags & ~AllThreadFlags(), 0u);
      return (value_ & flags) != 0u;
    }

    bool IsFlagSet(ThreadFlag flag) const {
      return (value_ & enum_cast<uint32_t>(flag)) != 0u;
    }

    void SetFlag(ThreadFlag flag) {
      value_ |= enum_cast<uint32_t>(flag);
    }

    StateAndFlags WithFlag(ThreadFlag flag) const {
      StateAndFlags result = *this;
      result.SetFlag(flag);
      return result;
    }

    StateAndFlags WithoutFlag(ThreadFlag flag) const {
      StateAndFlags result = *this;
      result.ClearFlag(flag);
      return result;
    }

    void ClearFlag(ThreadFlag flag) {
      value_ &= ~enum_cast<uint32_t>(flag);
    }

    ThreadState GetState() const {
      ThreadState state = ThreadStateField::Decode(value_);
      ValidateThreadState(state);
      return state;
    }

    void SetState(ThreadState state) {
      ValidateThreadState(state);
      value_ = ThreadStateField::Update(state, value_);
    }

    StateAndFlags WithState(ThreadState state) const {
      StateAndFlags result = *this;
      result.SetState(state);
      return result;
    }

    static constexpr uint32_t EncodeState(ThreadState state) {
      ValidateThreadState(state);
      return ThreadStateField::Encode(state);
    }

    static constexpr void ValidateThreadState(ThreadState state) {
      if (kIsDebugBuild && state != ThreadState::kRunnable) {
        CHECK_GE(state, ThreadState::kTerminated);
        CHECK_LE(state, ThreadState::kSuspended);
        CHECK_NE(state, ThreadState::kObsoleteRunnable);
      }
    }

    // The value holds thread flags and thread state.
    uint32_t value_;

    static constexpr size_t kThreadStateBitSize = BitSizeOf<std::underlying_type_t<ThreadState>>();
    static constexpr size_t kThreadStatePosition = BitSizeOf<uint32_t>() - kThreadStateBitSize;
    using ThreadStateField = BitField<ThreadState, kThreadStatePosition, kThreadStateBitSize>;
    static_assert(
        WhichPowerOf2(enum_cast<uint32_t>(ThreadFlag::kLastFlag)) < kThreadStatePosition);
  };
  static_assert(sizeof(StateAndFlags) == sizeof(uint32_t), "Unexpected StateAndFlags size");

  StateAndFlags GetStateAndFlags(std::memory_order order) const {
    return StateAndFlags(tls32_.state_and_flags.load(order));
  }

  // Format state and flags as a hex string. For diagnostic output.
  std::string StateAndFlagsAsHexString() const;

  // Run the flip function and notify other threads that may have tried
  // to do that concurrently.
  void RunFlipFunction(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_);

  // Ensure that thread flip function for thread target started running. If no other thread is
  // executing it, the calling thread shall run the flip function and then notify other threads
  // that have tried to do that concurrently. After this function returns, the
  // `ThreadFlag::kPendingFlipFunction` is cleared but another thread may still be running the
  // flip function as indicated by the `ThreadFlag::kRunningFlipFunction`. Optional arguments:
  //  - old_state_and_flags indicates the current and state and flags value for the thread, with
  //    at least kPendingFlipFunction set. The thread should logically acquire the
  //    mutator lock before running the flip function.  A special zero value indicates that the
  //    thread already holds the mutator lock, and the actual state_and_flags must be read.
  //    A non-zero value implies this == Current().
  //  - If tef is non-null, we check that the target thread has not yet exited, as indicated by
  //    tef. In that case, we acquire thread_list_lock_ as needed.
  //  - If finished is non-null, we assign to *finished to indicate whether the flip was known to
  //    be completed when we returned.
  //  Returns true if and only if we acquired the mutator lock (which implies that we ran the flip
  //  function after finding old_state_and_flags unchanged).
  static bool EnsureFlipFunctionStarted(Thread* self,
                                        Thread* target,
                                        StateAndFlags old_state_and_flags = StateAndFlags(0),
                                        ThreadExitFlag* tef = nullptr,
                                        /*out*/ bool* finished = nullptr)
      TRY_ACQUIRE_SHARED(true, Locks::mutator_lock_);

  static void ThreadExitCallback(void* arg);

  // Maximum number of suspend barriers.
  static constexpr uint32_t kMaxSuspendBarriers = 3;

  // Has Thread::Startup been called?
  static bool is_started_;

  // TLS key used to retrieve the Thread*.
  static pthread_key_t pthread_key_self_;

  // Used to notify threads that they should attempt to resume, they will suspend again if
  // their suspend count is > 0.
  static ConditionVariable* resume_cond_ GUARDED_BY(Locks::thread_suspend_count_lock_);

  // Hook passed by framework which returns true
  // when StrictMode events are traced for the current thread.
  static bool (*is_sensitive_thread_hook_)();
  // Stores the jit sensitive thread (which for now is the UI thread).
  static Thread* jit_sensitive_thread_;

  static constexpr uint32_t kMakeVisiblyInitializedCounterTriggerCount = 128;

  /***********************************************************************************************/
  // Thread local storage. Fields are grouped by size to enable 32 <-> 64 searching to account for
  // pointer size differences. To encourage shorter encoding, more frequently used values appear
  // first if possible.
  /***********************************************************************************************/

  struct alignas(4) tls_32bit_sized_values {
    // We have no control over the size of 'bool', but want our boolean fields
    // to be 4-byte quantities.
    using bool32_t = uint32_t;

    explicit tls_32bit_sized_values(bool is_daemon)
        : state_and_flags(0u),
          suspend_count(0),
          thin_lock_thread_id(0),
          tid(0),
          daemon(is_daemon),
          throwing_OutOfMemoryError(false),
          no_thread_suspension(0),
          thread_exit_check_count(0),
          is_gc_marking(false),
          is_deopt_check_required(false),
          weak_ref_access_enabled(WeakRefAccessState::kVisiblyEnabled),
          disable_thread_flip_count(0),
          user_code_suspend_count(0),
          force_interpreter_count(0),
          make_visibly_initialized_counter(0),
          define_class_counter(0),
          num_name_readers(0),
          shared_method_hotness(kSharedMethodHotnessThreshold) {}

    // The state and flags field must be changed atomically so that flag values aren't lost.
    // See `StateAndFlags` for bit assignments of `ThreadFlag` and `ThreadState` values.
    // Keeping the state and flags together allows an atomic CAS to change from being
    // Suspended to Runnable without a suspend request occurring.
    Atomic<uint32_t> state_and_flags;
    static_assert(sizeof(state_and_flags) == sizeof(uint32_t),
                  "Size of state_and_flags and uint32 are different");

    // A non-zero value is used to tell the current thread to enter a safe point
    // at the next poll.
    int suspend_count GUARDED_BY(Locks::thread_suspend_count_lock_);

    // Thin lock thread id. This is a small integer used by the thin lock implementation.
    // This is not to be confused with the native thread's tid, nor is it the value returned
    // by java.lang.Thread.getId --- this is a distinct value, used only for locking. One
    // important difference between this id and the ids visible to managed code is that these
    // ones get reused (to ensure that they fit in the number of bits available).
    uint32_t thin_lock_thread_id;

    // System thread id.
    uint32_t tid;

    // Is the thread a daemon?
    const bool32_t daemon;

    // A boolean telling us whether we're recursively throwing OOME.
    bool32_t throwing_OutOfMemoryError;

    // A positive value implies we're in a region where thread suspension isn't expected.
    uint32_t no_thread_suspension;

    // How many times has our pthread key's destructor been called?
    uint32_t thread_exit_check_count;

    // True if the GC is in the marking phase. This is used for the CC collector only. This is
    // thread local so that we can simplify the logic to check for the fast path of read barriers of
    // GC roots.
    bool32_t is_gc_marking;

    // True if we need to check for deoptimization when returning from the runtime functions. This
    // is required only when a class is redefined to prevent executing code that has field offsets
    // embedded. For non-debuggable apps redefinition is not allowed and this flag should always be
    // set to false.
    bool32_t is_deopt_check_required;

    // Thread "interrupted" status; stays raised until queried or thrown.
    Atomic<bool32_t> interrupted;

    AtomicInteger park_state_;

    // Determines whether the thread is allowed to directly access a weak ref
    // (Reference::GetReferent() and system weaks) and to potentially mark an object alive/gray.
    // This is used for concurrent reference processing of the CC collector only. This is thread
    // local so that we can enable/disable weak ref access by using a checkpoint and avoid a race
    // around the time weak ref access gets disabled and concurrent reference processing begins
    // (if weak ref access is disabled during a pause, this is not an issue.) Other collectors use
    // Runtime::DisallowNewSystemWeaks() and ReferenceProcessor::EnableSlowPath().  Can be
    // concurrently accessed by GetReferent() and set (by iterating over threads).
    // Can be changed from kEnabled to kVisiblyEnabled by readers. No other concurrent access is
    // possible when that happens.
    mutable std::atomic<WeakRefAccessState> weak_ref_access_enabled;

    // A thread local version of Heap::disable_thread_flip_count_. This keeps track of how many
    // levels of (nested) JNI critical sections the thread is in and is used to detect a nested JNI
    // critical section enter.
    uint32_t disable_thread_flip_count;

    // How much of 'suspend_count_' is by request of user code, used to distinguish threads
    // suspended by the runtime from those suspended by user code.
    // This should have GUARDED_BY(Locks::user_code_suspension_lock_) but auto analysis cannot be
    // told that AssertHeld should be good enough.
    int user_code_suspend_count GUARDED_BY(Locks::thread_suspend_count_lock_);

    // Count of how many times this thread has been forced to interpreter. If this is not 0 the
    // thread must remain in interpreted code as much as possible.
    uint32_t force_interpreter_count;

    // Counter for calls to initialize a class that's initialized but not visibly initialized.
    // When this reaches kMakeVisiblyInitializedCounterTriggerCount, we call the runtime to
    // make initialized classes visibly initialized. This is needed because we usually make
    // classes visibly initialized in batches but we do not want to be stuck with a class
    // initialized but not visibly initialized for a long time even if no more classes are
    // being initialized anymore.
    uint32_t make_visibly_initialized_counter;

    // Counter for how many nested define-classes are ongoing in this thread. Used to allow waiting
    // for threads to be done with class-definition work.
    uint32_t define_class_counter;

    // A count of the number of readers of tlsPtr_.name that may still be looking at a string they
    // retrieved.
    mutable std::atomic<uint32_t> num_name_readers;
    static_assert(std::atomic<uint32_t>::is_always_lock_free);

    // Thread-local hotness counter for shared memory methods. Initialized with
    // `kSharedMethodHotnessThreshold`. The interpreter decrements it and goes
    // into the runtime when hitting zero. Note that all previous decrements
    // could have been executed by another method than the one seeing zero.
    // There is a second level counter in `Jit::shared_method_counters_` to make
    // sure we at least have a few samples before compiling a method.
    uint32_t shared_method_hotness;
  } tls32_;

  struct alignas(8) tls_64bit_sized_values {
    tls_64bit_sized_values() : trace_clock_base(0) {
    }

    // The clock base used for tracing.
    uint64_t trace_clock_base;

    RuntimeStats stats;
  } tls64_;

  struct alignas(sizeof(void*)) tls_ptr_sized_values {
      tls_ptr_sized_values() : card_table(nullptr),
                               exception(nullptr),
                               stack_end(nullptr),
                               managed_stack(),
                               suspend_trigger(nullptr),
                               jni_env(nullptr),
                               tmp_jni_env(nullptr),
                               self(nullptr),
                               opeer(nullptr),
                               jpeer(nullptr),
                               stack_begin(nullptr),
                               stack_size(0),
                               deps_or_stack_trace_sample(),
                               wait_next(nullptr),
                               monitor_enter_object(nullptr),
                               top_handle_scope(nullptr),
                               class_loader_override(nullptr),
                               long_jump_context(nullptr),
                               stacked_shadow_frame_record(nullptr),
                               deoptimization_context_stack(nullptr),
                               frame_id_to_shadow_frame(nullptr),
                               name(nullptr),
                               pthread_self(0),
                               last_no_thread_suspension_cause(nullptr),
                               active_suspendall_barrier(nullptr),
                               active_suspend1_barriers(nullptr),
                               thread_local_pos(nullptr),
                               thread_local_end(nullptr),
                               thread_local_start(nullptr),
                               thread_local_limit(nullptr),
                               thread_local_objects(0),
                               checkpoint_function(nullptr),
                               thread_local_alloc_stack_top(nullptr),
                               thread_local_alloc_stack_end(nullptr),
                               mutator_lock(nullptr),
                               flip_function(nullptr),
                               thread_local_mark_stack(nullptr),
                               async_exception(nullptr),
                               top_reflective_handle_scope(nullptr),
                               method_trace_buffer(nullptr),
                               method_trace_buffer_index(0),
                               thread_exit_flags(nullptr) {
      std::fill(held_mutexes, held_mutexes + kLockLevelCount, nullptr);
    }

    // The biased card table, see CardTable for details.
    uint8_t* card_table;

    // The pending exception or null.
    mirror::Throwable* exception;

    // The end of this thread's stack. This is the lowest safely-addressable address on the stack.
    // We leave extra space so there's room for the code that throws StackOverflowError.
    uint8_t* stack_end;

    // The top of the managed stack often manipulated directly by compiler generated code.
    ManagedStack managed_stack;

    // In certain modes, setting this to 0 will trigger a SEGV and thus a suspend check.  It is
    // normally set to the address of itself. It should be cleared with release semantics to ensure
    // that prior state changes etc. are visible to any thread that faults as a result.
    // We assume that the kernel ensures that such changes are then visible to the faulting
    // thread, even if it is not an acquire load that faults. (Indeed, it seems unlikely that the
    // ordering semantics associated with the faulting load has any impact.)
    std::atomic<uintptr_t*> suspend_trigger;

    // Every thread may have an associated JNI environment
    JNIEnvExt* jni_env;

    // Temporary storage to transfer a pre-allocated JNIEnvExt from the creating thread to the
    // created thread.
    JNIEnvExt* tmp_jni_env;

    // Initialized to "this". On certain architectures (such as x86) reading off of Thread::Current
    // is easy but getting the address of Thread::Current is hard. This field can be read off of
    // Thread::Current to give the address.
    Thread* self;

    // Our managed peer (an instance of java.lang.Thread). The jobject version is used during thread
    // start up, until the thread is registered and the local opeer_ is used.
    mirror::Object* opeer;
    jobject jpeer;

    // The "lowest addressable byte" of the stack.
    uint8_t* stack_begin;

    // Size of the stack.
    size_t stack_size;

    // Sampling profiler and AOT verification cannot happen on the same run, so we share
    // the same entry for the stack trace and the verifier deps.
    union DepsOrStackTraceSample {
      DepsOrStackTraceSample() {
        verifier_deps = nullptr;
        stack_trace_sample = nullptr;
      }
      // Pointer to previous stack trace captured by sampling profiler.
      std::vector<ArtMethod*>* stack_trace_sample;
      // When doing AOT verification, per-thread VerifierDeps.
      verifier::VerifierDeps* verifier_deps;
    } deps_or_stack_trace_sample;

    // The next thread in the wait set this thread is part of or null if not waiting.
    Thread* wait_next;

    // If we're blocked in MonitorEnter, this is the object we're trying to lock.
    mirror::Object* monitor_enter_object;

    // Top of linked list of handle scopes or null for none.
    BaseHandleScope* top_handle_scope;

    // Needed to get the right ClassLoader in JNI_OnLoad, but also
    // useful for testing.
    jobject class_loader_override;

    // Thread local, lazily allocated, long jump context. Used to deliver exceptions.
    Context* long_jump_context;

    // For gc purpose, a shadow frame record stack that keeps track of:
    // 1) shadow frames under construction.
    // 2) deoptimization shadow frames.
    StackedShadowFrameRecord* stacked_shadow_frame_record;

    // Deoptimization return value record stack.
    DeoptimizationContextRecord* deoptimization_context_stack;

    // For debugger, a linked list that keeps the mapping from frame_id to shadow frame.
    // Shadow frames may be created before deoptimization happens so that the debugger can
    // set local values there first.
    FrameIdToShadowFrame* frame_id_to_shadow_frame;

    // A cached copy of the java.lang.Thread's (modified UTF-8) name.
    // If this is not null or kThreadNameDuringStartup, then it owns the malloc memory holding
    // the string. Updated in an RCU-like manner.
    std::atomic<const char*> name;
    static_assert(std::atomic<const char*>::is_always_lock_free);

    // A cached pthread_t for the pthread underlying this Thread*.
    pthread_t pthread_self;

    // If no_thread_suspension_ is > 0, what is causing that assertion.
    const char* last_no_thread_suspension_cause;

    // After a thread observes a suspend request and enters a suspended state,
    // it notifies the requestor by arriving at a "suspend barrier". This consists of decrementing
    // the atomic integer representing the barrier. (This implementation was introduced in 2015 to
    // minimize cost. There may be other options.) These atomic integer barriers are always
    // stored on the requesting thread's stack. They are referenced from the target thread's
    // data structure in one of two ways; in either case the data structure referring to these
    // barriers is guarded by suspend_count_lock:
    // 1. A SuspendAll barrier is directly referenced from the target thread. Only one of these
    // can be active at a time:
    AtomicInteger* active_suspendall_barrier GUARDED_BY(Locks::thread_suspend_count_lock_);
    // 2. For individual thread suspensions, active barriers are embedded in a struct that is used
    // to link together all suspend requests for this thread. Unlike the SuspendAll case, each
    // barrier is referenced by a single target thread, and thus can appear only on a single list.
    // The struct as a whole is still stored on the requesting thread's stack.
    WrappedSuspend1Barrier* active_suspend1_barriers GUARDED_BY(Locks::thread_suspend_count_lock_);

    // thread_local_pos and thread_local_end must be consecutive for ldrd and are 8 byte aligned for
    // potentially better performance.
    uint8_t* thread_local_pos;
    uint8_t* thread_local_end;

    // Thread-local allocation pointer. Can be moved above the preceding two to correct alignment.
    uint8_t* thread_local_start;

    // Thread local limit is how much we can expand the thread local buffer to, it is greater or
    // equal to thread_local_end.
    uint8_t* thread_local_limit;

    size_t thread_local_objects;

    // Pending checkpoint function or null if non-pending. If this checkpoint is set and someone
    // requests another checkpoint, it goes to the checkpoint overflow list.
    Closure* checkpoint_function GUARDED_BY(Locks::thread_suspend_count_lock_);

    // Entrypoint function pointers.
    // TODO: move this to more of a global offset table model to avoid per-thread duplication.
    JniEntryPoints jni_entrypoints;
    QuickEntryPoints quick_entrypoints;

    // There are RosAlloc::kNumThreadLocalSizeBrackets thread-local size brackets per thread.
    void* rosalloc_runs[kNumRosAllocThreadLocalSizeBracketsInThread];

    // Thread-local allocation stack data/routines.
    StackReference<mirror::Object>* thread_local_alloc_stack_top;
    StackReference<mirror::Object>* thread_local_alloc_stack_end;

    // Pointer to the mutator lock.
    // This is the same as `Locks::mutator_lock_` but cached for faster state transitions.
    MutatorMutex* mutator_lock;

    // Support for Mutex lock hierarchy bug detection.
    BaseMutex* held_mutexes[kLockLevelCount];

    // The function used for thread flip.  Set while holding Locks::thread_suspend_count_lock_ and
    // with all other threads suspended.  May be cleared while being read.
    std::atomic<Closure*> flip_function;

    union {
      // Thread-local mark stack for the concurrent copying collector.
      gc::accounting::AtomicStack<mirror::Object>* thread_local_mark_stack;
      // Thread-local page-sized buffer for userfaultfd GC.
      uint8_t* thread_local_gc_buffer;
    };

    // The pending async-exception or null.
    mirror::Throwable* async_exception;

    // Top of the linked-list for reflective-handle scopes or null if none.
    BaseReflectiveHandleScope* top_reflective_handle_scope;

    // Pointer to a thread-local buffer for method tracing.
    uintptr_t* method_trace_buffer;

    // The index of the next free entry in method_trace_buffer.
    size_t method_trace_buffer_index;

    // Pointer to the first node of an intrusively doubly-linked list of ThreadExitFlags.
    ThreadExitFlag* thread_exit_flags GUARDED_BY(Locks::thread_list_lock_);
  } tlsPtr_;

  // Small thread-local cache to be used from the interpreter.
  // It is keyed by dex instruction pointer.
  // The value is opcode-depended (e.g. field offset).
  InterpreterCache interpreter_cache_;

  // All fields below this line should not be accessed by native code. This means these fields can
  // be modified, rearranged, added or removed without having to modify asm_support.h

  // Guards the 'wait_monitor_' members.
  Mutex* wait_mutex_ DEFAULT_MUTEX_ACQUIRED_AFTER;

  // Condition variable waited upon during a wait.
  ConditionVariable* wait_cond_ GUARDED_BY(wait_mutex_);
  // Pointer to the monitor lock we're currently waiting on or null if not waiting.
  Monitor* wait_monitor_ GUARDED_BY(wait_mutex_);

  // Debug disable read barrier count, only is checked for debug builds and only in the runtime.
  uint8_t debug_disallow_read_barrier_ = 0;

  // Counters used only for debugging and error reporting.  Likely to wrap.  Small to avoid
  // increasing Thread size.
  // We currently maintain these unconditionally, since it doesn't cost much, and we seem to have
  // persistent issues with suspension timeouts, which these should help to diagnose.
  // TODO: Reconsider this.
  std::atomic<uint8_t> suspended_count_ = 0;   // Number of times we entered a suspended state after
                                               // running checkpoints.
  std::atomic<uint8_t> checkpoint_count_ = 0;  // Number of checkpoints we started running.

  // Note that it is not in the packed struct, may not be accessed for cross compilation.
  uintptr_t poison_object_cookie_ = 0;

  // Pending extra checkpoints if checkpoint_function_ is already used.
  std::list<Closure*> checkpoint_overflow_ GUARDED_BY(Locks::thread_suspend_count_lock_);

  // Custom TLS field that can be used by plugins or the runtime. Should not be accessed directly by
  // compiled code or entrypoints.
  SafeMap<std::string, std::unique_ptr<TLSData>, std::less<>> custom_tls_
      GUARDED_BY(Locks::custom_tls_lock_);

#if !defined(__BIONIC__)
#if !defined(ANDROID_HOST_MUSL)
    __attribute__((tls_model("initial-exec")))
#endif
  static thread_local Thread* self_tls_;
#endif

  // True if the thread is some form of runtime thread (ex, GC or JIT).
  bool is_runtime_thread_;

  // Set during execution of JNI methods that get field and method id's as part of determining if
  // the caller is allowed to access all fields and methods in the Core Platform API.
  uint32_t core_platform_api_cookie_ = 0;

  friend class gc::collector::SemiSpace;  // For getting stack traces.
  friend class Runtime;  // For CreatePeer.
  friend class QuickExceptionHandler;  // For dumping the stack.
  friend class ScopedThreadStateChange;
  friend class StubTest;  // For accessing entrypoints.
  friend class ThreadList;  // For ~Thread, Destroy and EnsureFlipFunctionStarted.
  friend class EntrypointsOrderTest;  // To test the order of tls entries.
  friend class JniCompilerTest;  // For intercepting JNI entrypoint calls.

  DISALLOW_COPY_AND_ASSIGN(Thread);
};

class SCOPED_CAPABILITY ScopedAssertNoThreadSuspension {
 public:
  ALWAYS_INLINE ScopedAssertNoThreadSuspension(const char* cause,
                                               bool enabled = true)
      ACQUIRE(Roles::uninterruptible_)
      : enabled_(enabled) {
    if (!enabled_) {
      return;
    }
    if (kIsDebugBuild) {
      self_ = Thread::Current();
      old_cause_ = self_->StartAssertNoThreadSuspension(cause);
    } else {
      Roles::uninterruptible_.Acquire();  // No-op.
    }
  }
  ALWAYS_INLINE ~ScopedAssertNoThreadSuspension() RELEASE(Roles::uninterruptible_) {
    if (!enabled_) {
      return;
    }
    if (kIsDebugBuild) {
      self_->EndAssertNoThreadSuspension(old_cause_);
    } else {
      Roles::uninterruptible_.Release();  // No-op.
    }
  }

 private:
  Thread* self_;
  const bool enabled_;
  const char* old_cause_;
};

class ScopedAllowThreadSuspension {
 public:
  ALWAYS_INLINE ScopedAllowThreadSuspension() RELEASE(Roles::uninterruptible_) {
    if (kIsDebugBuild) {
      self_ = Thread::Current();
      old_cause_ = self_->EndAssertNoThreadSuspension();
    } else {
      Roles::uninterruptible_.Release();  // No-op.
    }
  }
  ALWAYS_INLINE ~ScopedAllowThreadSuspension() ACQUIRE(Roles::uninterruptible_) {
    if (kIsDebugBuild) {
      CHECK(self_->StartAssertNoThreadSuspension(old_cause_) == nullptr);
    } else {
      Roles::uninterruptible_.Acquire();  // No-op.
    }
  }

 private:
  Thread* self_;
  const char* old_cause_;
};


class ScopedStackedShadowFramePusher {
 public:
  ScopedStackedShadowFramePusher(Thread* self, ShadowFrame* sf) : self_(self), sf_(sf) {
    DCHECK_EQ(sf->GetLink(), nullptr);
    self_->PushStackedShadowFrame(sf, StackedShadowFrameType::kShadowFrameUnderConstruction);
  }
  ~ScopedStackedShadowFramePusher() {
    ShadowFrame* sf = self_->PopStackedShadowFrame();
    DCHECK_EQ(sf, sf_);
  }

 private:
  Thread* const self_;
  ShadowFrame* const sf_;

  DISALLOW_COPY_AND_ASSIGN(ScopedStackedShadowFramePusher);
};

// Only works for debug builds.
class ScopedDebugDisallowReadBarriers {
 public:
  explicit ScopedDebugDisallowReadBarriers(Thread* self) : self_(self) {
    self_->ModifyDebugDisallowReadBarrier(1);
  }
  ~ScopedDebugDisallowReadBarriers() {
    self_->ModifyDebugDisallowReadBarrier(-1);
  }

 private:
  Thread* const self_;
};

class ThreadLifecycleCallback {
 public:
  virtual ~ThreadLifecycleCallback() {}

  virtual void ThreadStart(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_) = 0;
  virtual void ThreadDeath(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_) = 0;
};

// Store an exception from the thread and suppress it for the duration of this object.
class ScopedExceptionStorage {
 public:
  EXPORT explicit ScopedExceptionStorage(Thread* self) REQUIRES_SHARED(Locks::mutator_lock_);
  void SuppressOldException(const char* message = "") REQUIRES_SHARED(Locks::mutator_lock_);
  EXPORT ~ScopedExceptionStorage() REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  Thread* self_;
  StackHandleScope<1> hs_;
  MutableHandle<mirror::Throwable> excp_;
};

EXPORT std::ostream& operator<<(std::ostream& os, const Thread& thread);
std::ostream& operator<<(std::ostream& os, StackedShadowFrameType thread);

}  // namespace art

#endif  // ART_RUNTIME_THREAD_H_

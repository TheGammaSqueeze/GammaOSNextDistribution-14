/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include "monitor.h"

#include <memory>
#include <string>

#include "base/atomic.h"
#include "barrier.h"
#include "base/time_utils.h"
#include "class_linker-inl.h"
#include "common_runtime_test.h"
#include "handle_scope-inl.h"
#include "jni/java_vm_ext.h"
#include "mirror/class-inl.h"
#include "mirror/string-inl.h"  // Strings are easiest to allocate
#include "object_lock.h"
#include "scoped_thread_state_change-inl.h"
#include "thread_pool.h"

namespace art HIDDEN {

class MonitorTest : public CommonRuntimeTest {
 protected:
  MonitorTest() {
    use_boot_image_ = true;  // Make the Runtime creation cheaper.
  }

  void SetUpRuntimeOptions(RuntimeOptions *options) override {
    // Use a smaller heap
    SetUpRuntimeOptionsForFillHeap(options);

    options->push_back(std::make_pair("-Xint", nullptr));
  }

 public:
  std::unique_ptr<Monitor> monitor_;
  jobject object_;
  jobject watchdog_object_;
  // One exception test is for waiting on another Thread's lock. This is used to race-free &
  // loop-free pass
  Thread* thread_;
  std::unique_ptr<Barrier> barrier_;
  std::unique_ptr<Barrier> complete_barrier_;
  bool completed_;
};

// Check that an exception can be thrown correctly.
// This test is potentially racy, but the timeout is long enough that it should work.

class CreateTask : public Task {
 public:
  CreateTask(MonitorTest* monitor_test, uint64_t initial_sleep, int64_t millis, bool expected) :
      monitor_test_(monitor_test), initial_sleep_(initial_sleep), millis_(millis),
      expected_(expected) {}

  void Run(Thread* self) override {
    ScopedObjectAccess soa(self);
    StackHandleScope<1u> hs(self);
    Handle<mirror::Object> obj = hs.NewHandle(soa.Decode<mirror::Object>(monitor_test_->object_));

    monitor_test_->thread_ = self;        // Pass the Thread.
    obj->MonitorEnter(self);  // Lock the object. This should transition
    LockWord lock_after = obj->GetLockWord(false);  // it to thinLocked.
    LockWord::LockState new_state = lock_after.GetState();

    // Cannot use ASSERT only, as analysis thinks we'll keep holding the mutex.
    if (LockWord::LockState::kThinLocked != new_state) {
      obj->MonitorExit(self);         // To appease analysis.
      ASSERT_EQ(LockWord::LockState::kThinLocked, new_state);  // To fail the test.
      return;
    }

    // Force a fat lock by running identity hashcode to fill up lock word.
    obj->IdentityHashCode();
    LockWord lock_after2 = obj->GetLockWord(false);
    LockWord::LockState new_state2 = lock_after2.GetState();

    // Cannot use ASSERT only, as analysis thinks we'll keep holding the mutex.
    if (LockWord::LockState::kFatLocked != new_state2) {
      obj->MonitorExit(self);         // To appease analysis.
      ASSERT_EQ(LockWord::LockState::kFatLocked, new_state2);  // To fail the test.
      return;
    }

    {
      // Need to drop the mutator lock to use the barrier.
      ScopedThreadSuspension sts(self, ThreadState::kSuspended);
      monitor_test_->barrier_->Wait(self);           // Let the other thread know we're done.
    }

    // Give the other task a chance to do its thing.
    NanoSleep(initial_sleep_ * 1000 * 1000);

    // Now try to Wait on the Monitor.
    Monitor::Wait(self, obj.Get(), millis_, 0, true, ThreadState::kTimedWaiting);

    // Check the exception status against what we expect.
    EXPECT_EQ(expected_, self->IsExceptionPending());
    if (expected_) {
      self->ClearException();
    }

    {
      // Need to drop the mutator lock to use the barrier.
      ScopedThreadSuspension sts(self, ThreadState::kSuspended);
      monitor_test_->complete_barrier_->Wait(self);  // Wait for test completion.
    }

    obj->MonitorExit(self);  // Release the object. Appeases analysis.
  }

  void Finalize() override {
    delete this;
  }

 private:
  MonitorTest* monitor_test_;
  uint64_t initial_sleep_;
  int64_t millis_;
  bool expected_;
};


class UseTask : public Task {
 public:
  UseTask(MonitorTest* monitor_test, uint64_t initial_sleep, int64_t millis, bool expected) :
      monitor_test_(monitor_test), initial_sleep_(initial_sleep), millis_(millis),
      expected_(expected) {}

  void Run(Thread* self) override {
    monitor_test_->barrier_->Wait(self);  // Wait for the other thread to set up the monitor.

    {
      ScopedObjectAccess soa(self);

      // Give the other task a chance to do its thing.
      NanoSleep(initial_sleep_ * 1000 * 1000);

      ObjPtr<mirror::Object> obj = soa.Decode<mirror::Object>(monitor_test_->object_);
      Monitor::Wait(self, obj, millis_, 0, true, ThreadState::kTimedWaiting);

      // Check the exception status against what we expect.
      EXPECT_EQ(expected_, self->IsExceptionPending());
      if (expected_) {
        self->ClearException();
      }
    }

    monitor_test_->complete_barrier_->Wait(self);  // Wait for test completion.
  }

  void Finalize() override {
    delete this;
  }

 private:
  MonitorTest* monitor_test_;
  uint64_t initial_sleep_;
  int64_t millis_;
  bool expected_;
};

class InterruptTask : public Task {
 public:
  InterruptTask(MonitorTest* monitor_test, uint64_t initial_sleep, uint64_t millis) :
      monitor_test_(monitor_test), initial_sleep_(initial_sleep), millis_(millis) {}

  void Run(Thread* self) override {
    monitor_test_->barrier_->Wait(self);  // Wait for the other thread to set up the monitor.

    {
      ScopedObjectAccess soa(self);

      // Give the other task a chance to do its thing.
      NanoSleep(initial_sleep_ * 1000 * 1000);

      // Interrupt the other thread.
      monitor_test_->thread_->Interrupt(self);

      // Give it some more time to get to the exception code.
      NanoSleep(millis_ * 1000 * 1000);

      // Now try to Wait.
      ObjPtr<mirror::Object> obj = soa.Decode<mirror::Object>(monitor_test_->object_);
      Monitor::Wait(self, obj, 10, 0, true, ThreadState::kTimedWaiting);

      // No check here, as depending on scheduling we may or may not fail.
      if (self->IsExceptionPending()) {
        self->ClearException();
      }
    }

    monitor_test_->complete_barrier_->Wait(self);  // Wait for test completion.
  }

  void Finalize() override {
    delete this;
  }

 private:
  MonitorTest* monitor_test_;
  uint64_t initial_sleep_;
  uint64_t millis_;
};

class WatchdogTask : public Task {
 public:
  explicit WatchdogTask(MonitorTest* monitor_test) : monitor_test_(monitor_test) {}

  void Run(Thread* self) override {
    ScopedObjectAccess soa(self);
    StackHandleScope<1u> hs(self);
    Handle<mirror::Object> watchdog_obj =
        hs.NewHandle(soa.Decode<mirror::Object>(monitor_test_->watchdog_object_));

    watchdog_obj->MonitorEnter(self);        // Lock the object.

    watchdog_obj->Wait(self, 30 * 1000, 0);  // Wait for 30s, or being woken up.

    watchdog_obj->MonitorExit(self);         // Release the lock.

    if (!monitor_test_->completed_) {
      LOG(FATAL) << "Watchdog timeout!";
    }
  }

  void Finalize() override {
    delete this;
  }

 private:
  MonitorTest* monitor_test_;
};

static void CommonWaitSetup(MonitorTest* test, ClassLinker* class_linker, uint64_t create_sleep,
                            int64_t c_millis, bool c_expected, bool interrupt, uint64_t use_sleep,
                            int64_t u_millis, bool u_expected, const char* pool_name) {
  Thread* const self = Thread::Current();
  ScopedObjectAccess soa(self);
  // First create the object we lock. String is easiest.
  StackHandleScope<2u> hs(soa.Self());
  Handle<mirror::Object> obj =
      hs.NewHandle(mirror::String::AllocFromModifiedUtf8(self, "hello, world!"));
  test->object_ = soa.Vm()->AddGlobalRef(self, obj.Get());
  ASSERT_TRUE(test->object_ != nullptr);
  Handle<mirror::Object> watchdog_obj =
      hs.NewHandle(mirror::String::AllocFromModifiedUtf8(self, "hello, world!"));
  test->watchdog_object_ = soa.Vm()->AddGlobalRef(self, watchdog_obj.Get());
  ASSERT_TRUE(test->watchdog_object_ != nullptr);

  // Create the barrier used to synchronize.
  test->barrier_ = std::make_unique<Barrier>(2);
  test->complete_barrier_ = std::make_unique<Barrier>(3);
  test->completed_ = false;

  // Our job: Fill the heap, then try Wait.
  {
    VariableSizedHandleScope vhs(soa.Self());
    test->FillHeap(soa.Self(), class_linker, &vhs);

    // Now release everything.
  }

  // Need to drop the mutator lock to allow barriers.
  ScopedThreadSuspension sts(soa.Self(), ThreadState::kNative);
  std::unique_ptr<ThreadPool> thread_pool(ThreadPool::Create(pool_name, 3));
  thread_pool->AddTask(self, new CreateTask(test, create_sleep, c_millis, c_expected));
  if (interrupt) {
    thread_pool->AddTask(self, new InterruptTask(test, use_sleep, static_cast<uint64_t>(u_millis)));
  } else {
    thread_pool->AddTask(self, new UseTask(test, use_sleep, u_millis, u_expected));
  }
  thread_pool->AddTask(self, new WatchdogTask(test));
  thread_pool->StartWorkers(self);

  // Wait on completion barrier.
  test->complete_barrier_->Wait(self);
  test->completed_ = true;

  // Wake the watchdog.
  {
    ScopedObjectAccess soa2(self);
    watchdog_obj->MonitorEnter(self);     // Lock the object.
    watchdog_obj->NotifyAll(self);        // Wake up waiting parties.
    watchdog_obj->MonitorExit(self);      // Release the lock.
  }

  thread_pool->StopWorkers(self);
}


// First test: throwing an exception when trying to wait in Monitor with another thread.
TEST_F(MonitorTest, CheckExceptionsWait1) {
  // Make the CreateTask wait 10ms, the UseTask wait 10ms.
  // => The use task will get the lock first and get to self == owner check.
  // This will lead to OOM and monitor error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);
  CommonWaitSetup(this, class_linker_, 10, 50, false, false, 2, 50, true,
                  "Monitor test thread pool 1");
}

// Second test: throwing an exception for invalid wait time.
TEST_F(MonitorTest, CheckExceptionsWait2) {
  // Make the CreateTask wait 0ms, the UseTask wait 10ms.
  // => The create task will get the lock first and get to ms >= 0
  // This will lead to OOM and monitor error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);
  CommonWaitSetup(this, class_linker_, 0, -1, true, false, 10, 50, true,
                  "Monitor test thread pool 2");
}

// Third test: throwing an interrupted-exception.
TEST_F(MonitorTest, CheckExceptionsWait3) {
  // Make the CreateTask wait 0ms, then Wait for a long time. Make the InterruptTask wait 10ms,
  // after which it will interrupt the create task and then wait another 10ms.
  // => The create task will get to the interrupted-exception throw.
  // This will lead to OOM and monitor error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);
  CommonWaitSetup(this, class_linker_, 0, 500, true, true, 10, 50, true,
                  "Monitor test thread pool 3");
}

class TryLockTask : public Task {
 public:
  explicit TryLockTask(jobject obj) : obj_(obj) {}

  void Run(Thread* self) override {
    ScopedObjectAccess soa(self);
    StackHandleScope<1u> hs(self);
    Handle<mirror::Object> obj = hs.NewHandle(soa.Decode<mirror::Object>(obj_));
    // Lock is held by other thread, try lock should fail.
    ObjectTryLock<mirror::Object> lock(self, obj);
    EXPECT_FALSE(lock.Acquired());
  }

  void Finalize() override {
    delete this;
  }

 private:
  jobject obj_;
};

// Test trylock in deadlock scenarios.
TEST_F(MonitorTest, TestTryLock) {
  ScopedLogSeverity sls(LogSeverity::FATAL);

  Thread* const self = Thread::Current();
  std::unique_ptr<ThreadPool> thread_pool(ThreadPool::Create("the pool", 2));
  ScopedObjectAccess soa(self);
  StackHandleScope<1> hs(self);
  Handle<mirror::Object> obj1(
      hs.NewHandle<mirror::Object>(mirror::String::AllocFromModifiedUtf8(self, "hello, world!")));
  jobject g_obj1 = soa.Vm()->AddGlobalRef(self, obj1.Get());
  ASSERT_TRUE(g_obj1 != nullptr);
  {
    ObjectLock<mirror::Object> lock1(self, obj1);
    {
      ObjectTryLock<mirror::Object> trylock(self, obj1);
      EXPECT_TRUE(trylock.Acquired());
    }
    // Test failure case.
    thread_pool->AddTask(self, new TryLockTask(g_obj1));
    thread_pool->StartWorkers(self);
    ScopedThreadSuspension sts(self, ThreadState::kSuspended);
    thread_pool->Wait(Thread::Current(), /*do_work=*/false, /*may_hold_locks=*/false);
  }
  // Test that the trylock actually locks the object.
  {
    ObjectTryLock<mirror::Object> trylock(self, obj1);
    EXPECT_TRUE(trylock.Acquired());
    obj1->Notify(self);
    // Since we hold the lock there should be no monitor state exeception.
    self->AssertNoPendingException();
  }
  thread_pool->StopWorkers(self);
}


}  // namespace art

/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "thread.h"

#include "android-base/logging.h"
#include "base/locks.h"
#include "base/mutex.h"
#include "common_runtime_test.h"
#include "thread-current-inl.h"
#include "thread-inl.h"

namespace art HIDDEN {

class ThreadTest : public CommonRuntimeTest {};

// Ensure that basic list operations on ThreadExitFlags work. These are rarely
// exercised in practice, since normally only one flag is registered at a time.

TEST_F(ThreadTest, ThreadExitFlagTest) {
  Thread* self = Thread::Current();
  ThreadExitFlag tefs[3];
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    self->NotifyOnThreadExit(&tefs[2]);
    ASSERT_TRUE(self->IsRegistered(&tefs[2]));
    ASSERT_FALSE(tefs[2].HasExited());
    ASSERT_FALSE(self->IsRegistered(&tefs[1]));
    self->NotifyOnThreadExit(&tefs[1]);
    self->NotifyOnThreadExit(&tefs[0]);
    ASSERT_TRUE(self->IsRegistered(&tefs[0]));
    ASSERT_TRUE(self->IsRegistered(&tefs[1]));
    ASSERT_TRUE(self->IsRegistered(&tefs[2]));
    self->UnregisterThreadExitFlag(&tefs[1]);
    ASSERT_TRUE(self->IsRegistered(&tefs[0]));
    ASSERT_FALSE(self->IsRegistered(&tefs[1]));
    ASSERT_TRUE(self->IsRegistered(&tefs[2]));
    self->UnregisterThreadExitFlag(&tefs[2]);
    ASSERT_TRUE(self->IsRegistered(&tefs[0]));
    ASSERT_FALSE(self->IsRegistered(&tefs[1]));
    ASSERT_FALSE(self->IsRegistered(&tefs[2]));
  }
  Thread::DCheckUnregisteredEverywhere(&tefs[1], &tefs[2]);
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    self->UnregisterThreadExitFlag(&tefs[0]);
    ASSERT_FALSE(self->IsRegistered(&tefs[0]));
    ASSERT_FALSE(self->IsRegistered(&tefs[1]));
    ASSERT_FALSE(self->IsRegistered(&tefs[2]));
  }
  Thread::DCheckUnregisteredEverywhere(&tefs[0], &tefs[2]);
}

TEST_F(ThreadTest, ThreadExitSignalTest) {
  Thread* self = Thread::Current();
  ThreadExitFlag tefs[3];
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    self->NotifyOnThreadExit(&tefs[2]);
    ASSERT_TRUE(self->IsRegistered(&tefs[2]));
    ASSERT_FALSE(self->IsRegistered(&tefs[1]));
    self->NotifyOnThreadExit(&tefs[1]);
    ASSERT_TRUE(self->IsRegistered(&tefs[1]));
    self->SignalExitFlags();
    ASSERT_TRUE(tefs[1].HasExited());
    ASSERT_TRUE(tefs[2].HasExited());
  }
  Thread::DCheckUnregisteredEverywhere(&tefs[1], &tefs[2]);
  {
    MutexLock mu(self, *Locks::thread_list_lock_);
    self->NotifyOnThreadExit(&tefs[0]);
    tefs[2].~ThreadExitFlag();  //  Destroy and reinitialize.
    new (&tefs[2]) ThreadExitFlag();
    self->NotifyOnThreadExit(&tefs[2]);
    ASSERT_FALSE(tefs[0].HasExited());
    ASSERT_TRUE(tefs[1].HasExited());
    ASSERT_FALSE(tefs[2].HasExited());
    self->SignalExitFlags();
    ASSERT_TRUE(tefs[0].HasExited());
    ASSERT_TRUE(tefs[1].HasExited());
    ASSERT_TRUE(tefs[2].HasExited());
  }
  Thread::DCheckUnregisteredEverywhere(&tefs[0], &tefs[2]);
}

}  // namespace art

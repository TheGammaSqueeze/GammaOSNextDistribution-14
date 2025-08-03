/*
 * Copyright 2023 The Android Open Source Project
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

#include "module_jniloop_unittest.h"

#include <base/callback.h>
#include <base/functional/bind.h>
#include <base/location.h>
#include <base/threading/platform_thread.h>
#include <sys/syscall.h>

#include <string>

#include "btif/include/btif_jni_task.h"
#include "gtest/gtest.h"
#include "module.h"
#include "os/handler.h"
#include "os/thread.h"

using namespace bluetooth;

namespace {
constexpr int sync_timeout_in_ms = 3000;

std::promise<pid_t> external_function_promise;
std::promise<pid_t> private_impl_promise;
std::promise<pid_t> protected_method_promise;

}  // namespace

// Global function with C linkage
void external_function_jni(int /* a */, double /* b */, char /* c */) {
  external_function_promise.set_value(base::PlatformThread::CurrentId());
}

// Module private implementation that is inaccessible externally
struct TestJniModule::PrivateImpl : public ModuleJniloop {
  const int kMaxTestModuleRecurseDepth = 10;

  void privateCallableMethod(int /* a */, double /* b */, char /* c */) {
    private_impl_promise.set_value(base::PlatformThread::CurrentId());
  }

  void repostMethodTest(int /* a */, double /* b */, char /* c */) {
    private_impl_promise.set_value(base::PlatformThread::CurrentId());
  }

  void privateCallableRepostMethod(
      std::shared_ptr<TestJniModule::PrivateImpl> ptr, int a, double b, char c) {
    PostMethodOnJni(ptr, &PrivateImpl::repostMethodTest, a, b, c);
  }

  void privateCallableRecursiveMethod(
      std::shared_ptr<TestJniModule::PrivateImpl> ptr, int depth, double b, char c) {
    if (depth > kMaxTestModuleRecurseDepth) {
      private_impl_promise.set_value(base::PlatformThread::CurrentId());
      return;
    }
    PostMethodOnJni(ptr, &PrivateImpl::privateCallableRecursiveMethod, ptr, depth + 1, b, c);
  }
};

// Protected module method executed on handler
void TestJniModule::call_on_handler_protected_method(int loop_tid, int a, int b, int c) {
  protected_method_promise = std::promise<pid_t>();
  auto future = protected_method_promise.get_future();
  CallOn(this, &TestJniModule::protected_method, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Global external function executed on jni loop
void TestJniModule::call_on_jni_external_function(int loop_tid, int a, double b, char c) {
  external_function_promise = std::promise<pid_t>();
  auto future = external_function_promise.get_future();
  PostFunctionOnJni(&external_function_jni, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on main loop
void TestJniModule::call_on_jni(int loop_tid, int a, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnJni(pimpl_, &TestJniModule::PrivateImpl::privateCallableMethod, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on jni loop and reposted
void TestJniModule::call_on_jni_repost(int loop_tid, int a, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnJni(
      pimpl_, &TestJniModule::PrivateImpl::privateCallableRepostMethod, pimpl_, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on jni loop recursively
void TestJniModule::call_on_jni_recurse(int loop_tid, int depth, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnJni(
      pimpl_, &TestJniModule::PrivateImpl::privateCallableRecursiveMethod, pimpl_, depth, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

void TestJniModule::protected_method(int /* a */, int /* b */, int /* c */) {
  protected_method_promise.set_value(base::PlatformThread::CurrentId());
}

bool TestJniModule::IsStarted() const {
  return pimpl_ != nullptr;
}

void TestJniModule::Start() {
  ASSERT_FALSE(IsStarted());
  pimpl_ = std::make_shared<TestJniModule::PrivateImpl>();
}

void TestJniModule::Stop() {
  ASSERT_TRUE(IsStarted());
  pimpl_.reset();
}

std::string TestJniModule::ToString() const {
  return std::string(__func__);
}

const bluetooth::ModuleFactory TestJniModule::Factory =
    bluetooth::ModuleFactory([]() { return new TestJniModule(); });

//
// Module GDx Testing Below
//
class ModuleGdxJniTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_framework_tid_ = base::PlatformThread::CurrentId();
    module_ = new TestJniModule();
    jni_thread_startup();
    jniloop_tid_ = get_jniloop_tid();
  }

  void TearDown() override {
    sync_jni_handler();
    jni_thread_shutdown();
    delete module_;
  }

  void sync_jni_handler() {
    std::promise promise = std::promise<void>();
    std::future future = promise.get_future();
    post_on_bt_jni([&promise]() { promise.set_value(); });
    future.wait_for(std::chrono::milliseconds(sync_timeout_in_ms));
  };

  static pid_t get_jniloop_tid() {
    std::promise<pid_t> pid_promise = std::promise<pid_t>();
    auto future = pid_promise.get_future();
    post_on_bt_jni([&pid_promise]() { pid_promise.set_value(base::PlatformThread::CurrentId()); });
    return future.get();
  }

  pid_t test_framework_tid_{-1};
  pid_t jniloop_tid_{-1};
  TestModuleRegistry module_registry_;
  TestJniModule* module_;
};

class ModuleGdxWithJniStackTest : public ModuleGdxJniTest {
 protected:
  void SetUp() override {
    ModuleGdxJniTest::SetUp();
    module_registry_.InjectTestModule(&TestJniModule::Factory, module_ /* pass ownership */);
    module_ = nullptr;  // ownership is passed
    handler_tid_ = get_handler_tid(module_registry_.GetTestModuleHandler(&TestJniModule::Factory));
  }

  static pid_t get_handler_tid(os::Handler* handler) {
    std::promise<pid_t> handler_tid_promise = std::promise<pid_t>();
    std::future<pid_t> future = handler_tid_promise.get_future();
    handler->Post(common::BindOnce(
        [](std::promise<pid_t> promise) { promise.set_value(base::PlatformThread::CurrentId()); },
        std::move(handler_tid_promise)));
    return future.get();
  }

  void TearDown() override {
    module_registry_.StopAll();
    ModuleGdxJniTest::TearDown();
  }

  TestJniModule* Mod() {
    return module_registry_.GetModuleUnderTest<TestJniModule>();
  }

  pid_t handler_tid_{-1};
};

TEST_F(ModuleGdxJniTest, nop) {}

TEST_F(ModuleGdxJniTest, lifecycle) {
  ::bluetooth::os::Thread* thread =
      new bluetooth::os::Thread("Name", bluetooth::os::Thread::Priority::REAL_TIME);
  ASSERT_FALSE(module_registry_.IsStarted<TestJniModule>());
  module_registry_.Start<TestJniModule>(thread);
  ASSERT_TRUE(module_registry_.IsStarted<TestJniModule>());
  module_registry_.StopAll();
  ASSERT_FALSE(module_registry_.IsStarted<TestJniModule>());
  delete thread;
}

TEST_F(ModuleGdxWithJniStackTest, call_on_handler_protected_method) {
  Mod()->call_on_handler_protected_method(handler_tid_, 1, 2, 3);
}

TEST_F(ModuleGdxWithJniStackTest, test_call_on_jni) {
  Mod()->call_on_jni(jniloop_tid_, 1, 2, 3);
}

TEST_F(ModuleGdxWithJniStackTest, test_call_external_function) {
  Mod()->call_on_jni_external_function(jniloop_tid_, 1, 2.3, 'c');
}

TEST_F(ModuleGdxWithJniStackTest, test_call_on_jni_repost) {
  Mod()->call_on_jni_repost(jniloop_tid_, 1, 2, 3);
}

TEST_F(ModuleGdxWithJniStackTest, test_call_on_jni_recurse) {
  Mod()->call_on_jni_recurse(jniloop_tid_, 1, 2, 3);
}

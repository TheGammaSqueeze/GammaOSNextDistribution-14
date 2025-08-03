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

#include "module_mainloop_unittest.h"

#include <base/callback.h>
#include <base/functional/bind.h>
#include <base/location.h>
#include <base/threading/platform_thread.h>
#include <sys/syscall.h>

#include <string>

#include "gtest/gtest.h"
#include "module.h"
#include "os/handler.h"
#include "os/thread.h"
#include "stack/include/main_thread.h"

using namespace bluetooth;

namespace {
constexpr int sync_timeout_in_ms = 3000;

std::promise<pid_t> external_function_promise;
std::promise<pid_t> private_impl_promise;
std::promise<pid_t> protected_method_promise;

}  // namespace

// Global function with C linkage
void external_function_main(int /* a */, double /* b */, char /* c */) {
  external_function_promise.set_value(base::PlatformThread::CurrentId());
}

// Module private implementation that is inaccessible externally
struct TestModule::PrivateImpl : public ModuleMainloop {
  const int kMaxTestModuleRecurseDepth = 10;

  void privateCallableMethod(int /* a */, double /* b */, char /* c */) {
    private_impl_promise.set_value(base::PlatformThread::CurrentId());
  }

  void repostMethodTest(int /* a */, double /* b */, char /* c */) {
    private_impl_promise.set_value(base::PlatformThread::CurrentId());
  }

  void privateCallableRepostMethod(
      std::shared_ptr<TestModule::PrivateImpl> ptr, int a, double b, char c) {
    PostMethodOnMain(ptr, &PrivateImpl::repostMethodTest, a, b, c);
  }

  void privateCallableRecursiveMethod(
      std::shared_ptr<TestModule::PrivateImpl> ptr, int depth, double b, char c) {
    if (depth > kMaxTestModuleRecurseDepth) {
      private_impl_promise.set_value(base::PlatformThread::CurrentId());
      return;
    }
    PostMethodOnMain(ptr, &PrivateImpl::privateCallableRecursiveMethod, ptr, depth + 1, b, c);
  }
};

// Protected module method executed on handler
void TestModule::call_on_handler_protected_method(int loop_tid, int a, int b, int c) {
  protected_method_promise = std::promise<pid_t>();
  auto future = protected_method_promise.get_future();
  CallOn(this, &TestModule::protected_method, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Global external function executed on main loop
void TestModule::call_on_main_external_function(int loop_tid, int a, double b, char c) {
  external_function_promise = std::promise<pid_t>();
  auto future = external_function_promise.get_future();
  PostFunctionOnMain(&external_function_main, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on main loop
void TestModule::call_on_main(int loop_tid, int a, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnMain(pimpl_, &TestModule::PrivateImpl::privateCallableMethod, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on main loop and reposted
void TestModule::call_on_main_repost(int loop_tid, int a, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnMain(pimpl_, &TestModule::PrivateImpl::privateCallableRepostMethod, pimpl_, a, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

// Private implementation method executed on main loop recursively
void TestModule::call_on_main_recurse(int loop_tid, int depth, int b, int c) {
  private_impl_promise = std::promise<pid_t>();
  auto future = private_impl_promise.get_future();
  PostMethodOnMain(
      pimpl_, &TestModule::PrivateImpl::privateCallableRecursiveMethod, pimpl_, depth, b, c);
  ASSERT_EQ(future.wait_for(std::chrono::seconds(3)), std::future_status::ready);
  ASSERT_EQ(future.get(), loop_tid);
}

void TestModule::protected_method(int /* a */, int /* b */, int /* c */) {
  protected_method_promise.set_value(base::PlatformThread::CurrentId());
}

bool TestModule::IsStarted() const {
  return pimpl_ != nullptr;
}

void TestModule::Start() {
  ASSERT_FALSE(IsStarted());
  pimpl_ = std::make_shared<TestModule::PrivateImpl>();
}

void TestModule::Stop() {
  ASSERT_TRUE(IsStarted());
  pimpl_.reset();
}

std::string TestModule::ToString() const {
  return std::string(__func__);
}

const bluetooth::ModuleFactory TestModule::Factory =
    bluetooth::ModuleFactory([]() { return new TestModule(); });

//
// Module GDx Testing Below
//
class ModuleMainGdxTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_framework_tid_ = base::PlatformThread::CurrentId();
    module_ = new TestModule();
    main_thread_start_up();
    mainloop_tid_ = get_mainloop_tid();
  }

  void TearDown() override {
    sync_main_handler();
    main_thread_shut_down();
    delete module_;
  }

  void sync_main_handler() {
    std::promise promise = std::promise<void>();
    std::future future = promise.get_future();
    post_on_bt_main([&promise]() { promise.set_value(); });
    future.wait_for(std::chrono::milliseconds(sync_timeout_in_ms));
  };

  static pid_t get_mainloop_tid() {
    std::promise<pid_t> pid_promise = std::promise<pid_t>();
    auto future = pid_promise.get_future();
    post_on_bt_main([&pid_promise]() { pid_promise.set_value(base::PlatformThread::CurrentId()); });
    return future.get();
  }

  pid_t test_framework_tid_{-1};
  pid_t mainloop_tid_{-1};
  TestModuleRegistry module_registry_;
  TestModule* module_;
};

class ModuleMainGdxWithStackTest : public ModuleMainGdxTest {
 protected:
  void SetUp() override {
    ModuleMainGdxTest::SetUp();
    module_registry_.InjectTestModule(&TestModule::Factory, module_ /* pass ownership */);
    module_ = nullptr;  // ownership is passed
    handler_tid_ = get_handler_tid(module_registry_.GetTestModuleHandler(&TestModule::Factory));
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
    ModuleMainGdxTest::TearDown();
  }

  TestModule* Mod() {
    return module_registry_.GetModuleUnderTest<TestModule>();
  }

  pid_t handler_tid_{-1};
};

TEST_F(ModuleMainGdxTest, nop) {}

TEST_F(ModuleMainGdxTest, lifecycle) {
  ::bluetooth::os::Thread* thread =
      new bluetooth::os::Thread("Name", bluetooth::os::Thread::Priority::REAL_TIME);
  ASSERT_FALSE(module_registry_.IsStarted<TestModule>());
  module_registry_.Start<TestModule>(thread);
  ASSERT_TRUE(module_registry_.IsStarted<TestModule>());
  module_registry_.StopAll();
  ASSERT_FALSE(module_registry_.IsStarted<TestModule>());
  delete thread;
}

TEST_F(ModuleMainGdxWithStackTest, call_on_handler_protected_method) {
  Mod()->call_on_handler_protected_method(handler_tid_, 1, 2, 3);
}

TEST_F(ModuleMainGdxWithStackTest, test_call_on_main) {
  Mod()->call_on_main(mainloop_tid_, 1, 2, 3);
}

TEST_F(ModuleMainGdxWithStackTest, test_call_external_function) {
  Mod()->call_on_main_external_function(mainloop_tid_, 1, 2.3, 'c');
}

TEST_F(ModuleMainGdxWithStackTest, test_call_on_main_repost) {
  Mod()->call_on_main_repost(mainloop_tid_, 1, 2, 3);
}

TEST_F(ModuleMainGdxWithStackTest, test_call_on_main_recurse) {
  Mod()->call_on_main_recurse(mainloop_tid_, 1, 2, 3);
}

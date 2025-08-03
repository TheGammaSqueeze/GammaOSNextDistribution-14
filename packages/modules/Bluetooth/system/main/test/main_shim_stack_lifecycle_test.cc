/*
 *  Copyright 2024 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

#include <bluetooth/log.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <unistd.h>

#include <atomic>
#include <cstdlib>
#include <memory>
#include <thread>

#include "common/strings.h"
#include "gd/module_jniloop.h"
#include "gd/module_mainloop.h"
#include "main/shim/stack.h"
#include "module.h"
#include "os/thread.h"
#include "stack/include/main_thread.h"
#include "test/mock/mock_main_shim_entry.h"

using ::testing::_;

using namespace bluetooth;
using namespace testing;

namespace {
constexpr int kSyncMainLoopTimeoutMs = 3000;
constexpr int kWaitUntilHandlerStoppedMs = 2000;
constexpr size_t kNumTestClients = 3;
constexpr size_t kNumTestModules = 3;
constexpr int kNumIters = 100;
constexpr int kAbruptStackShutdownIter = kNumIters * 3 / 4;
constexpr char kTestStackThreadName[] = "test_stack_thread";
constexpr char kTestDataTag[] = "This is a test";

inline void maybe_yield() {
  if (std::rand() & 1) std::this_thread::yield();
}

constexpr size_t kTagLength = 48 + sizeof(' ') + sizeof(' ');
inline void log_tag(std::string tag) {
  std::string prepend(kTagLength / 2 - tag.size() / 2, '=');
  std::string append(kTagLength / 2 - tag.size() / 2, '=');
  log::info("{} {} {}", prepend, tag, append);
}

class MainThread {
 public:
  MainThread() { main_thread_start_up(); }

  ~MainThread() {
    sync_main_handler();
    main_thread_shut_down();
  }

 private:
  void sync_main_handler() {
    std::promise promise = std::promise<void>();
    std::future future = promise.get_future();
    post_on_bt_main([&promise]() { promise.set_value(); });
    future.wait_for(std::chrono::milliseconds(kSyncMainLoopTimeoutMs));
  }
};

class TestStackManager {
 public:
  TestStackManager() {
    // Start is executed by the test after each test adds the default
    // or their own modules
  }

  ~TestStackManager() {
    log::debug("Deleting stack manager");
    Stop();
  }

  TestStackManager(const TestStackManager&) = delete;

  template <typename T>
  void AddModule() {
    modules_.add<T>();
  }

  void Start() {
    if (stack_started_) return;
    log::info("Started stack manager");
    stack_started_ = true;
    bluetooth::os::Thread* stack_thread = new bluetooth::os::Thread(
        kTestStackThreadName, bluetooth::os::Thread::Priority::NORMAL);
    bluetooth::shim::Stack::GetInstance()->StartModuleStack(&modules_,
                                                            stack_thread);
  }

  void Stop() {
    if (!stack_started_) return;
    stack_started_ = false;
    bluetooth::shim::Stack::GetInstance()->Stop();
  }

  // NOTE: Stack manager *must* be active else method returns nullptr
  // if stack manager has not started or shutdown
  template <typename T>
  static T* GetUnsafeModule() {
    return bluetooth::shim::Stack::GetInstance()
        ->GetStackManager()
        ->GetInstance<T>();
  }

  size_t NumModules() const { return modules_.NumModules(); }

 private:
  bluetooth::ModuleList modules_;
  bool stack_started_{false};
};

// Data returned via callback from a stack managed module
struct TestCallbackData {
  int iter;
  std::string tag;
};

// Data sent to a stack managed module via a module API
struct TestData {
  int iter;
  std::string tag;
  std::function<void(TestCallbackData callback_data)> callback;
};

class TestStackModuleBase : public bluetooth::Module,
                            public ModuleMainloop,
                            public ModuleJniloop {
 public:
  TestStackModuleBase(const TestStackModuleBase&) = delete;
  TestStackModuleBase& operator=(const TestStackModuleBase&) = delete;

  virtual ~TestStackModuleBase(){};
  static const ModuleFactory Factory;

  virtual void TestMethod(TestData test_data) const {
    log::info("Test base class iter:{} tag:{}", test_data.iter,
              test_data.tag.c_str());
  }

 protected:
  void ListDependencies(ModuleList* list) const override{};
  void Start() override { log::error("Started TestStackModuleBase"); };
  void Stop() override { log::error("Stopped TestStackModuleBase"); };
  std::string ToString() const override { return std::string("TestFunction"); }

  TestStackModuleBase() = default;
};

class TestStackModule1 : public TestStackModuleBase {
 public:
  TestStackModule1(const TestStackModule1&) = delete;
  TestStackModule1& operator=(const TestStackModule1&) = delete;
  virtual ~TestStackModule1() = default;

  static const ModuleFactory Factory;

  void TestMethod(TestData test_data) const override;

 private:
  struct impl;
  std::shared_ptr<impl> impl_;
  TestStackModule1();
};

struct TestStackModule1::impl : public ModuleMainloop, public ModuleJniloop {
  void test(TestData test_data) {
    TestCallbackData callback_data{
        .iter = test_data.iter,
        .tag = std::string(__func__),
    };
    PostFunctionOnMain(
        [](std::function<void(TestCallbackData callback_data)> callback,
           TestCallbackData data) { callback(data); },
        test_data.callback, callback_data);
  }
};

TestStackModule1::TestStackModule1() : TestStackModuleBase() {
  impl_ = std::make_shared<impl>();
}

void TestStackModule1::TestMethod(TestData test_data) const {
  PostMethodOnMain(impl_, &impl::test, test_data);
}

class TestStackModule2 : public TestStackModuleBase {
 public:
  TestStackModule2(const TestStackModule2&) = delete;
  TestStackModule2& operator=(const TestStackModule2&) = delete;
  virtual ~TestStackModule2() = default;

  static const ModuleFactory Factory;

  void TestMethod(TestData test_data) const override;

 private:
  struct impl;
  std::shared_ptr<impl> impl_;
  TestStackModule2();
};

struct TestStackModule2::impl : public ModuleMainloop, public ModuleJniloop {
  void test(TestData test_data) {
    TestCallbackData callback_data{
        .iter = test_data.iter,
        .tag = std::string(__func__),
    };
    PostFunctionOnMain(
        [](std::function<void(TestCallbackData callback_data)> callback,
           TestCallbackData data) { callback(data); },
        test_data.callback, callback_data);
  }
};

TestStackModule2::TestStackModule2() : TestStackModuleBase() {
  impl_ = std::make_shared<impl>();
}

void TestStackModule2::TestMethod(TestData test_data) const {
  PostMethodOnMain(impl_, &impl::test, test_data);
}

class TestStackModule3 : public TestStackModuleBase {
 public:
  TestStackModule3(const TestStackModule3&) = delete;
  TestStackModule3& operator=(const TestStackModule3&) = delete;
  virtual ~TestStackModule3() = default;

  static const ModuleFactory Factory;

  void TestMethod(TestData test_data) const override;

 private:
  struct impl;
  std::shared_ptr<impl> impl_;
  TestStackModule3();
};

struct TestStackModule3::impl : public ModuleMainloop, public ModuleJniloop {
  void test(TestData test_data) {
    TestCallbackData callback_data{
        .iter = test_data.iter,
        .tag = std::string(__func__),
    };
    PostFunctionOnMain(
        [](std::function<void(TestCallbackData callback_data)> callback,
           TestCallbackData data) { callback(data); },
        test_data.callback, callback_data);
  }
};

TestStackModule3::TestStackModule3() : TestStackModuleBase() {
  impl_ = std::make_shared<impl>();
}

void TestStackModule3::TestMethod(TestData test_data) const {
  PostMethodOnMain(impl_, &impl::test, test_data);
}

class TestStackModule4 : public TestStackModuleBase {
 public:
  TestStackModule4(const TestStackModule4&) = delete;
  TestStackModule4& operator=(const TestStackModule3&) = delete;
  virtual ~TestStackModule4() = default;

  static const ModuleFactory Factory;

  void TestMethod(TestData test_data) const override {
    log::info("mod:{} iter:{} tag:{}", __func__, test_data.iter,
              test_data.tag.c_str());
  }

 private:
  struct impl;
  std::shared_ptr<impl> impl_;
  TestStackModule4() : TestStackModuleBase() {}
};

struct TestStackModule4::impl : public ModuleMainloop, public ModuleJniloop {};

}  // namespace

const ModuleFactory TestStackModuleBase::Factory =
    ModuleFactory([]() { return new TestStackModuleBase(); });

const ModuleFactory TestStackModule1::Factory =
    ModuleFactory([]() { return new TestStackModule1(); });
const ModuleFactory TestStackModule2::Factory =
    ModuleFactory([]() { return new TestStackModule2(); });
const ModuleFactory TestStackModule3::Factory =
    ModuleFactory([]() { return new TestStackModule3(); });
const ModuleFactory TestStackModule4::Factory =
    ModuleFactory([]() { return new TestStackModule4(); });

class StackWithMainThreadUnitTest : public ::testing::Test {
 protected:
  void SetUp() override { main_thread_ = std::make_unique<MainThread>(); }
  void TearDown() override { main_thread_.reset(); }

 private:
  std::unique_ptr<MainThread> main_thread_;
};

class StackLifecycleUnitTest : public StackWithMainThreadUnitTest {
 public:
  std::shared_ptr<TestStackManager> StackManager() const {
    return stack_manager_;
  }

 protected:
  void SetUp() override {
    StackWithMainThreadUnitTest::SetUp();
    stack_manager_ = std::make_shared<TestStackManager>();
  }

  void TearDown() override {
    stack_manager_.reset();
    StackWithMainThreadUnitTest::TearDown();
  }

 private:
  std::shared_ptr<TestStackManager> stack_manager_;
};

TEST_F(StackLifecycleUnitTest, no_modules_in_stack) {
  ASSERT_EQ(0U, StackManager()->NumModules());
}

class StackLifecycleWithDefaultModulesUnitTest : public StackLifecycleUnitTest {
 protected:
  void SetUp() override {
    StackLifecycleUnitTest::SetUp();
    StackManager()->AddModule<TestStackModule1>();
    StackManager()->AddModule<TestStackModule2>();
    StackManager()->AddModule<TestStackModule3>();
    StackManager()->Start();
    ASSERT_EQ(3U, StackManager()->NumModules());
  }

  void TearDown() override { StackLifecycleUnitTest::TearDown(); }
};

struct CallablePostCnt {
  size_t success{0};
  size_t misses{0};
  CallablePostCnt operator+=(const CallablePostCnt& post_cnt) {
    return CallablePostCnt(
        {success += post_cnt.success, misses += post_cnt.misses});
  }
};

// Provide a client user of the stack manager module services
class Client {
 public:
  Client(int id) : id_(id) {}
  Client(const Client&) = default;
  virtual ~Client() = default;

  // Start up the client a thread and handler
  void Start() {
    log::info("Started client {}", id_);
    thread_ = new os::Thread(common::StringFormat("ClientThread%d", id_),
                             os::Thread::Priority::NORMAL);
    handler_ = new os::Handler(thread_);
    handler_->Post(common::BindOnce(
        [](int id) { log::info("Started client {}", id); }, id_));
  }

  // Ensure all the client handlers are running
  void Await() {
    std::promise<void> promise;
    std::future future = promise.get_future();
    handler_->Post(
        base::BindOnce([](std::promise<void> promise) { promise.set_value(); },
                       std::move(promise)));
    future.wait();
  }

  // Post a work task on behalf of this client
  void Post(common::OnceClosure closure) {
    if (quiesced_) {
      post_cnt_.misses++;
      maybe_yield();
    } else {
      post_cnt_.success++;
      handler_->Post(std::move(closure));
      maybe_yield();
    }
  }

  // Safely prevent new work tasks from being posted
  void Quiesce() {
    if (quiesced_) return;
    quiesced_ = true;
    std::promise promise = std::promise<void>();
    std::future future = promise.get_future();
    handler_->Post(common::BindOnce(
        [](std::promise<void> promise) { promise.set_value(); },
        std::move(promise)));
    future.wait_for(std::chrono::milliseconds(kSyncMainLoopTimeoutMs));
  }

  // Stops the client and associated resources
  void Stop() {
    if (!quiesced_) {
      Quiesce();
    }
    handler_->Clear();
    handler_->WaitUntilStopped(
        std::chrono::milliseconds(kWaitUntilHandlerStoppedMs));
    delete handler_;
    delete thread_;
  }

  int Id() const { return id_; }

  CallablePostCnt GetCallablePostCnt() const { return post_cnt_; }

  std::string Name() const {
    return common::StringFormat("%s%d", __func__, id_);
  }

 private:
  int id_{0};
  CallablePostCnt post_cnt_{};
  bool quiesced_{false};
  os::Handler* handler_{nullptr};
  os::Thread* thread_{nullptr};
};

// Convenience object to handle multiple clients with logging
class ClientGroup {
 public:
  ClientGroup(){};

  void Start() {
    for (auto& c : clients_) {
      c->Start();
    }
    log_tag("STARTING");
  }

  void Await() {
    for (auto& c : clients_) {
      c->Await();
    }
    log_tag("STARTED");
  }

  void Quiesce() {
    log_tag("QUIESCING");
    for (auto& c : clients_) {
      c->Quiesce();
    }
    log_tag("QUIESCED");
  }

  void Stop() {
    for (auto& c : clients_) {
      c->Stop();
    }
    log_tag("STOPPED");
  }

  void Dump() const {
    for (auto& c : clients_) {
      log::info("Callable post cnt client_id:{} success:{} misses:{}", c->Id(),
                c->GetCallablePostCnt().success,
                c->GetCallablePostCnt().misses);
    }
  }

  CallablePostCnt GetCallablePostCnt() const {
    CallablePostCnt post_cnt{};
    for (auto& c : clients_) {
      post_cnt += c->GetCallablePostCnt();
    }
    return post_cnt;
  }

  size_t NumClients() const { return kNumTestClients; }

  std::unique_ptr<Client> clients_[kNumTestClients] = {
      std::make_unique<Client>(1), std::make_unique<Client>(2),
      std::make_unique<Client>(3)};
};

TEST_F(StackLifecycleWithDefaultModulesUnitTest, clients_start) {
  ClientGroup client_group;

  client_group.Start();
  client_group.Await();

  // Clients are operational

  client_group.Quiesce();
  client_group.Stop();
}

TEST_F(StackLifecycleWithDefaultModulesUnitTest, client_using_stack_manager) {
  ClientGroup client_group;
  client_group.Start();
  client_group.Await();

  for (int i = 0; i < kNumIters; i++) {
    for (auto& c : client_group.clients_) {
      c->Post(base::BindOnce(
          [](int id, int iter,
             std::shared_ptr<TestStackManager> stack_manager) {
            stack_manager->GetUnsafeModule<TestStackModule1>()->TestMethod({
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            });
          },
          c->Id(), i, StackManager()));
      c->Post(base::BindOnce(
          [](int id, int iter,
             std::shared_ptr<TestStackManager> stack_manager) {
            stack_manager->GetUnsafeModule<TestStackModule2>()->TestMethod({
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            });
          },
          c->Id(), i, StackManager()));
      c->Post(base::BindOnce(
          [](int id, int iter,
             std::shared_ptr<TestStackManager> stack_manager) {
            stack_manager->GetUnsafeModule<TestStackModule3>()->TestMethod({
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            });
          },
          c->Id(), i, StackManager()));
    }
  }

  client_group.Quiesce();
  client_group.Stop();
  client_group.Dump();

  ASSERT_EQ(client_group.NumClients() * kNumIters * kNumTestModules,
            client_group.GetCallablePostCnt().success +
                client_group.GetCallablePostCnt().misses);
}

TEST_F(StackLifecycleWithDefaultModulesUnitTest,
       client_using_stack_manager_when_shutdown) {
  struct Counters {
    struct {
      std::atomic_size_t cnt{0};
    } up, down;
  } counters;

  ClientGroup client_group;
  client_group.Start();
  client_group.Await();

  for (int i = 0; i < kNumIters; i++) {
    for (auto& c : client_group.clients_) {
      c->Post(base::BindOnce(
          [](int id, int iter, Counters* counters,
             std::shared_ptr<TestStackManager> stack_manager) {
            TestData test_data = {
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            };
            if (bluetooth::shim::Stack::GetInstance()
                    ->CallOnModule<TestStackModule1>(
                        [test_data](TestStackModule1* mod) {
                          mod->TestMethod(test_data);
                        })) {
              counters->up.cnt++;
            } else {
              counters->down.cnt++;
            }
          },
          c->Id(), i, &counters, StackManager()));
      c->Post(base::BindOnce(
          [](int id, int iter, Counters* counters,
             std::shared_ptr<TestStackManager> stack_manager) {
            TestData test_data = {
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            };
            if (bluetooth::shim::Stack::GetInstance()
                    ->CallOnModule<TestStackModule2>(
                        [test_data](TestStackModule2* mod) {
                          mod->TestMethod(test_data);
                        })) {
              counters->up.cnt++;
            } else {
              counters->down.cnt++;
            }
          },
          c->Id(), i, &counters, StackManager()));
      c->Post(base::BindOnce(
          [](int id, int iter, Counters* counters,
             std::shared_ptr<TestStackManager> stack_manager) {
            TestData test_data = {
                .iter = iter,
                .tag = std::string(kTestDataTag),
                .callback = [](TestCallbackData data) {},
            };
            if (bluetooth::shim::Stack::GetInstance()
                    ->CallOnModule<TestStackModule3>(
                        [test_data](TestStackModule3* mod) {
                          mod->TestMethod(test_data);
                        })) {
              counters->up.cnt++;
            } else {
              counters->down.cnt++;
            }
          },
          c->Id(), i, &counters, StackManager()));
    }
    // Abruptly shutdown stack at some point through the iterations
    if (i == kAbruptStackShutdownIter) {
      log_tag("SHUTTING DOWN STACK");
      StackManager()->Stop();
    }
  }

  client_group.Quiesce();
  client_group.Stop();
  log::info("Execution stack availability counters up:{} down:{}",
            counters.up.cnt, counters.down.cnt);

  ASSERT_EQ(client_group.NumClients() * kNumIters * kNumTestModules,
            client_group.GetCallablePostCnt().success +
                client_group.GetCallablePostCnt().misses);
}

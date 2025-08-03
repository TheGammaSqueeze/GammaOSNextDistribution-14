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

#include "module_state_dumper_unittest.h"

#include <base/callback.h>
#include <base/functional/bind.h>
#include <base/location.h>
#include <base/threading/platform_thread.h>
#include <sys/syscall.h>

#include <sstream>
#include <string>

#include "gtest/gtest.h"
#include "module_dumper.h"
#include "module_state_dumper.h"
#include "os/handler.h"
#include "os/thread.h"
#include "stack/include/main_thread.h"

using namespace bluetooth;

namespace {

constexpr int sync_timeout_in_ms = 3000;
constexpr char title[] = "module_state_dumper_test";

}  // namespace

// Module private implementation that is inaccessible externally
struct StateDumperTestModule::PrivateImpl : public ModuleMainloop {};

bool StateDumperTestModule::IsStarted() const {
  return pimpl_ != nullptr;
}

void StateDumperTestModule::Start() {
  ASSERT_FALSE(IsStarted());
  pimpl_ = std::make_shared<StateDumperTestModule::PrivateImpl>();
}

void StateDumperTestModule::Stop() {
  ASSERT_TRUE(IsStarted());
  pimpl_.reset();
}

std::string StateDumperTestModule::ToString() const {
  return std::string(__func__);
}

const bluetooth::ModuleFactory StateDumperTestModule::Factory =
    bluetooth::ModuleFactory([]() { return new StateDumperTestModule(); });

DumpsysDataFinisher StateDumperTestModule::GetDumpsysData(
    flatbuffers::FlatBufferBuilder* /* builder */) const {
  LOG_INFO("flatbuffers");
  return EmptyDumpsysDataFinisher;
}

void StateDumperTestModule::GetDumpsysData() const {
  LOG_INFO("void");
}

void StateDumperTestModule::GetDumpsysData(int fd) const {
  LOG_INFO("fd");
  dprintf(fd, "GetDumpsysData(int fd)");
}

void StateDumperTestModule::GetDumpsysData(std::ostringstream& oss) const {
  LOG_INFO("oss");
  oss << "GetDumpsysData(std::ostringstream& oss)";
}

//
// Module GDx Testing Below
//
class ModuleStateDumperTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test_framework_tid_ = base::PlatformThread::CurrentId();
    module_ = new StateDumperTestModule();
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
  StateDumperTestModule* module_;
};

class ModuleStateDumperWithStackTest : public ModuleStateDumperTest {
 protected:
  void SetUp() override {
    ModuleStateDumperTest::SetUp();
    module_registry_.InjectTestModule(
        &StateDumperTestModule::Factory, module_ /* pass ownership */);
    module_ = nullptr;  // ownership is passed
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
    ModuleStateDumperTest::TearDown();
  }

  StateDumperTestModule* Mod() {
    return module_registry_.GetModuleUnderTest<StateDumperTestModule>();
  }

  pid_t handler_tid_{-1};
};

TEST_F(ModuleStateDumperTest, lifecycle) {
  ::bluetooth::os::Thread* thread =
      new bluetooth::os::Thread("Name", bluetooth::os::Thread::Priority::REAL_TIME);
  ASSERT_FALSE(module_registry_.IsStarted<StateDumperTestModule>());
  module_registry_.Start<StateDumperTestModule>(thread);
  ASSERT_TRUE(module_registry_.IsStarted<StateDumperTestModule>());
  module_registry_.StopAll();
  ASSERT_FALSE(module_registry_.IsStarted<StateDumperTestModule>());
  delete thread;
}

TEST_F(ModuleStateDumperWithStackTest, dump_state) {
  ModuleDumper dumper(STDOUT_FILENO, module_registry_, title);

  std::string output;
  std::ostringstream oss;
  dumper.DumpState(&output, oss);

  LOG_INFO("DUMP STATE");
  LOG_INFO("%s", oss.str().c_str());
  LOG_INFO("%s", output.c_str());
}

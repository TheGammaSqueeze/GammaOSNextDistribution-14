/*
 * Copyright 2019 The Android Open Source Project
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

#include <sys/types.h>
#include <unistd.h>

#include <csignal>
#include <cstring>
#include <memory>
#include <optional>
#include <string>
#include <thread>

#include "stack_manager.h"

// clang-format off
#include <client/linux/handler/exception_handler.h>
#include <unwindstack/AndroidUnwinder.h>
// clang-format on

#include "common/init_flags.h"
#include "facade/grpc_root_server.h"
#include "hal/hci_hal_host.h"
#include "hal/snoop_logger.h"
#include "include/check.h"
#include "os/log.h"
#include "os/parameter_provider.h"
#include "os/system_properties.h"

using ::bluetooth::ModuleList;
using ::bluetooth::StackManager;
using ::bluetooth::hal::HciHalHostRootcanalConfig;
using ::bluetooth::os::Thread;

extern "C" const char* __asan_default_options() {
  return "detect_container_overflow=0";
}

namespace {
::bluetooth::facade::GrpcRootServer grpc_root_server;

std::promise<void> interrupt_promise;
std::future<void> interrupt_future;
bool interrupted = false;
struct sigaction old_act = {};
void interrupt_handler(int signal_number) {
  if (!interrupted) {
    interrupted = true;
    LOG_INFO("Stopping gRPC root server due to signal: %s[%d]", strsignal(signal_number), signal_number);
    interrupt_promise.set_value();
  } else {
    LOG_WARN("Already interrupted by signal: %s[%d]", strsignal(signal_number), signal_number);
  }
  if (old_act.sa_handler != nullptr && old_act.sa_handler != SIG_IGN && old_act.sa_handler != SIG_DFL) {
    LOG_INFO("Calling saved signal handler");
    old_act.sa_handler(signal_number);
  }
}
struct sigaction new_act = {.sa_handler = interrupt_handler};

bool crash_callback(const void* crash_context, size_t crash_context_size, void* /* context */) {
  std::optional<pid_t> tid;
  if (crash_context_size >= sizeof(google_breakpad::ExceptionHandler::CrashContext)) {
    auto* ctx = static_cast<const google_breakpad::ExceptionHandler::CrashContext*>(crash_context);
    tid = ctx->tid;
    int signal_number = ctx->siginfo.si_signo;
    LOG_ERROR("Process crashed, signal: %s[%d], tid: %d", strsignal(signal_number), signal_number, ctx->tid);
  } else {
    LOG_ERROR("Process crashed, signal: unknown, tid: unknown");
  }
  unwindstack::AndroidLocalUnwinder unwinder;
  unwindstack::AndroidUnwinderData data;
  if (!unwinder.Unwind(tid, data)) {
    LOG_ERROR("Unwind failed");
    return false;
  }
  LOG_ERROR("Backtrace:");
  for (const auto& frame : data.frames) {
    LOG_ERROR("%s", unwinder.FormatFrame(frame).c_str());
  }
  return true;
}

// Need to stop server on a thread that is not part of a signal handler due to an issue with gRPC
// See: https://github.com/grpc/grpc/issues/24884
void thread_check_shutdown() {
  LOG_INFO("shutdown thread waiting for interruption");
  interrupt_future.wait();
  LOG_INFO("interrupted, stopping server");
  grpc_root_server.StopServer();
}

}  // namespace

// The entry point for the binary with libbluetooth + facades
int main(int argc, const char** argv) {
  google_breakpad::MinidumpDescriptor descriptor(google_breakpad::MinidumpDescriptor::kMicrodumpOnConsole);
  google_breakpad::ExceptionHandler eh(descriptor, nullptr, nullptr, nullptr, true, -1);
  eh.set_crash_handler(crash_callback);

  int root_server_port = 8897;
  int grpc_port = 8899;

  bluetooth::common::InitFlags::SetAllForTesting();

  const std::string arg_grpc_root_server_port = "--root-server-port=";
  const std::string arg_grpc_server_port = "--grpc-port=";
  const std::string arg_rootcanal_port = "--rootcanal-port=";
  const std::string arg_btsnoop_path = "--btsnoop=";
  const std::string arg_btsnooz_path = "--btsnooz=";
  const std::string arg_btconfig_path = "--btconfig=";
  for (int i = 1; i < argc; i++) {
    std::string arg = argv[i];
    if (arg.find(arg_grpc_root_server_port) == 0) {
      auto port_number = arg.substr(arg_grpc_root_server_port.size());
      root_server_port = std::stoi(port_number);
    }
    if (arg.find(arg_grpc_server_port) == 0) {
      auto port_number = arg.substr(arg_grpc_server_port.size());
      grpc_port = std::stoi(port_number);
    }
    if (arg.find(arg_rootcanal_port) == 0) {
      auto port_number = arg.substr(arg_rootcanal_port.size());
      HciHalHostRootcanalConfig::Get()->SetPort(std::stoi(port_number));
    }
    if (arg.find(arg_btsnoop_path) == 0) {
      auto btsnoop_path = arg.substr(arg_btsnoop_path.size());
      ::bluetooth::os::ParameterProvider::OverrideSnoopLogFilePath(btsnoop_path);
      CHECK(::bluetooth::os::SetSystemProperty(
          ::bluetooth::hal::SnoopLogger::kBtSnoopLogModeProperty, ::bluetooth::hal::SnoopLogger::kBtSnoopLogModeFull));
    }
    if (arg.find(arg_btsnooz_path) == 0) {
      auto btsnooz_path = arg.substr(arg_btsnooz_path.size());
      ::bluetooth::os::ParameterProvider::OverrideSnoozLogFilePath(btsnooz_path);
    }
    if (arg.find(arg_btconfig_path) == 0) {
      auto btconfig_path = arg.substr(arg_btconfig_path.size());
      ::bluetooth::os::ParameterProvider::OverrideConfigFilePath(btconfig_path);
    }
  }

  int ret = sigaction(SIGINT, &new_act, &old_act);
  if (ret < 0) {
    LOG_ERROR("sigaction error: %s", strerror(errno));
  }

  LOG_INFO("Starting Server");
  grpc_root_server.StartServer("0.0.0.0", root_server_port, grpc_port);
  LOG_INFO("Server started");
  auto wait_thread = std::thread([] { grpc_root_server.RunGrpcLoop(); });
  interrupt_future = interrupt_promise.get_future();
  auto shutdown_thread = std::thread{thread_check_shutdown};
  wait_thread.join();
  LOG_INFO("Server terminated");
  shutdown_thread.join();
  LOG_INFO("Shutdown thread terminated");

  return 0;
}

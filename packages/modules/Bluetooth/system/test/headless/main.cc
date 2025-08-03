/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "bt_headless"

#include <fcntl.h>
#include <stdio.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <unistd.h>

#include <iostream>
#include <unordered_map>

#include "base/logging.h"     // LOG() stdout and android log
#include "os/log.h"           // android log only
#include "test/headless/adapter/adapter.h"
#include "test/headless/connect/connect.h"
#include "test/headless/discovery/discovery.h"
#include "test/headless/dumpsys/dumpsys.h"
#include "test/headless/get_options.h"
#include "test/headless/headless.h"
#include "test/headless/log.h"
#include "test/headless/mode/mode.h"
#include "test/headless/nop/nop.h"
#include "test/headless/pairing/pairing.h"
#include "test/headless/read/read.h"
#include "test/headless/scan/scan.h"
#include "test/headless/sdp/sdp.h"
#include "test/headless/util.h"

using namespace bluetooth::test::headless;

int console_fd = -1;

namespace {

constexpr char kRedirectedStderrFilename[] = "/dev/null";
FILE* redirected_stderr_{nullptr};

// Ok...so if `stderr` stream is closed, the Android logging system will find
// another stream to write `LOG(<LogLevel>)` messages...any...stream.
// Unfortunately the next stream/fd is the bluetooth snooplog output
// file, so then a btsnoop_hci.log will interleave both raw hci packet
// data and Android LOG files preventing proper capture of hci traffic.
// To mitigate this, the `stderr` stream is redirected to another user
// provided stream, may be `/dev/null`, may be any file if so desired.
// This keeps everybody happy.
void start_trick_the_android_logging_subsystem() {
  redirected_stderr_ = freopen(kRedirectedStderrFilename, "w", stderr);
  ASSERT_LOG(redirected_stderr_ != nullptr,
             "Unable to open redirected stderr file");
}

void stop_trick_the_android_logging_subsystem() {
  ASSERT(redirected_stderr_ != nullptr);
  fclose(redirected_stderr_);
  redirected_stderr_ = nullptr;
}

void clear_logcat() {
  int pid;
  if ((pid = fork())) {
    // parent process
    int status;
    waitpid(pid, &status, 0);  // wait for the child to exit
    ASSERT_LOG(WIFEXITED(status), "Unable to clear logcat");
  } else {
    // child process
    const char exec[] = "/system/bin/logcat";
    const char arg0[] = "-c";

    execl(exec, exec, arg0, NULL);

    ASSERT_LOG(false, "Should not return from exec process");
  }
}

class Main : public HeadlessTest<int> {
 public:
  Main(const bluetooth::test::headless::GetOpt& options)
      : HeadlessTest<int>(options) {
    test_nodes_.emplace(
        "adapter",
        std::make_unique<bluetooth::test::headless::Adapter>(options));
    test_nodes_.emplace(
        "dumpsys",
        std::make_unique<bluetooth::test::headless::Dumpsys>(options));
    test_nodes_.emplace(
        "connect",
        std::make_unique<bluetooth::test::headless::Connect>(options));
    test_nodes_.emplace(
        "mode", std::make_unique<bluetooth::test::headless::Mode>(options));
    test_nodes_.emplace(
        "nop", std::make_unique<bluetooth::test::headless::Nop>(options));
    test_nodes_.emplace(
        "pairing",
        std::make_unique<bluetooth::test::headless::Pairing>(options));
    test_nodes_.emplace(
        "read", std::make_unique<bluetooth::test::headless::Read>(options));
    test_nodes_.emplace(
        "scan", std::make_unique<bluetooth::test::headless::Scan>(options));
    test_nodes_.emplace(
        "sdp", std::make_unique<bluetooth::test::headless::Sdp>(options));
    test_nodes_.emplace(
        "discovery",
        std::make_unique<bluetooth::test::headless::Discovery>(options));
  }

  int Run() override {
    console_fd = fcntl(STDERR_FILENO, F_DUPFD_CLOEXEC, STDERR_FILENO);
    ASSERT(console_fd != -1);
    if (options_.close_stderr_) {
      fclose(stderr);
    }

    if (options_.clear_logcat_) {
      clear_logcat();
    }

    start_trick_the_android_logging_subsystem();
    if (is_android_running()) {
      LOG_CONSOLE("Android must be shutdown for binary to run");
      LOG_CONSOLE("     /system/bin/stop");
      return -1;
    }
    LOG_CONSOLE("bt_headless version:\'%s\'", build_id().c_str());
    int rc = HeadlessTest<int>::Run();
    stop_trick_the_android_logging_subsystem();
    return rc;
  }
};

}  // namespace

int main(int argc, char** argv) {
  fflush(nullptr);
  setvbuf(stdout, nullptr, _IOLBF, 0);

  bluetooth::test::headless::GetOpt options(argc, argv);
  if (!options.IsValid()) {
    return -1;
  }

  Main main(options);
  return main.Run();
}

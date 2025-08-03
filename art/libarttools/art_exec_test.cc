/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <sys/capability.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <csignal>
#include <filesystem>
#include <functional>
#include <string>
#include <utility>

#include "android-base/file.h"
#include "android-base/logging.h"
#include "android-base/scopeguard.h"
#include "android-base/strings.h"
#include "base/common_art_test.h"
#include "base/file_utils.h"
#include "base/globals.h"
#include "base/macros.h"
#include "base/os.h"
#include "base/scoped_cap.h"
#include "exec_utils.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "system/thread_defs.h"

#ifdef ART_TARGET_ANDROID
#include "android-modules-utils/sdk_level.h"
#endif

namespace art {
namespace {

using ::android::base::make_scope_guard;
using ::android::base::ScopeGuard;
using ::android::base::Split;
using ::testing::Contains;
using ::testing::ElementsAre;
using ::testing::HasSubstr;
using ::testing::Not;

constexpr uid_t kRoot = 0;
constexpr uid_t kNobody = 9999;

// This test executes a few Linux system commands such as "ls", which are linked against system
// libraries. In many ART gtests we set LD_LIBRARY_PATH to make the test binaries link to libraries
// from the ART module first, and if that setting is propagated to the system commands they may also
// try to link to those libraries instead of the system ones they are built against. This is
// particularly noticeable when 32-bit tests run on a 64-bit system. Hence we need to set
// LD_LIBRARY_PATH to an empty string here.
// TODO(b/247108425): Remove this when ART gtests no longer use LD_LIBRARY_PATH.
constexpr const char* kEmptyLdLibraryPath = "--env=LD_LIBRARY_PATH=";

std::string GetArtBin(const std::string& name) {
  return ART_FORMAT("{}/bin/{}", GetArtRoot(), name);
}

std::string GetBin(const std::string& name) {
  return ART_FORMAT("{}/bin/{}", GetAndroidRoot(), name);
}

// Executes the command, waits for it to finish, and keeps it in a waitable state until the current
// scope exits.
std::pair<pid_t, ScopeGuard<std::function<void()>>> ScopedExecAndWait(
    std::vector<std::string>& args) {
  std::vector<char*> execv_args;
  execv_args.reserve(args.size() + 1);
  for (std::string& arg : args) {
    execv_args.push_back(arg.data());
  }
  execv_args.push_back(nullptr);

  pid_t pid = fork();
  if (pid == 0) {
    execv(execv_args[0], execv_args.data());
    UNREACHABLE();
  } else if (pid > 0) {
    siginfo_t info;
    CHECK_EQ(TEMP_FAILURE_RETRY(waitid(P_PID, pid, &info, WEXITED | WNOWAIT)), 0);
    CHECK_EQ(info.si_code, CLD_EXITED);
    CHECK_EQ(info.si_status, 0);
    std::function<void()> cleanup([=] {
      siginfo_t info;
      CHECK_EQ(TEMP_FAILURE_RETRY(waitid(P_PID, pid, &info, WEXITED)), 0);
    });
    return std::make_pair(pid, make_scope_guard(std::move(cleanup)));
  } else {
    LOG(FATAL) << "Failed to call fork";
    UNREACHABLE();
  }
}

// Grants the current process the given root capability.
void SetCap(cap_flag_t flag, cap_value_t value) {
  ScopedCap cap(cap_get_proc());
  CHECK_NE(cap.Get(), nullptr);
  cap_value_t caps[]{value};
  CHECK_EQ(cap_set_flag(cap.Get(), flag, /*ncap=*/1, caps, CAP_SET), 0);
  CHECK_EQ(cap_set_proc(cap.Get()), 0);
}

// Returns true if the given process has the given root capability.
bool GetCap(pid_t pid, cap_flag_t flag, cap_value_t value) {
  ScopedCap cap(cap_get_pid(pid));
  CHECK_NE(cap.Get(), nullptr);
  cap_flag_value_t flag_value;
  CHECK_EQ(cap_get_flag(cap.Get(), value, flag, &flag_value), 0);
  return flag_value == CAP_SET;
}

class ArtExecTest : public testing::Test {
 protected:
  void SetUp() override {
    testing::Test::SetUp();
    if (!kIsTargetAndroid) {
      GTEST_SKIP() << "art_exec is for device only";
    }
    if (getuid() != kRoot) {
      GTEST_SKIP() << "art_exec requires root";
    }
    art_exec_bin_ = GetArtBin("art_exec");
  }

  std::string art_exec_bin_;
};

TEST_F(ArtExecTest, Command) {
  std::string error_msg;
  int ret = ExecAndReturnCode({art_exec_bin_, "--", GetBin("sh"), "-c", "exit 123"}, &error_msg);
  ASSERT_EQ(ret, 123) << error_msg;
}

TEST_F(ArtExecTest, SetTaskProfiles) {
// The condition is always true because ArtExecTest is run on device only.
#ifdef ART_TARGET_ANDROID
  if (!android::modules::sdklevel::IsAtLeastU()) {
    GTEST_SKIP() << "This test depends on a libartpalette API that is only available on U+";
  }
#endif

  std::string filename = "/data/local/tmp/art-exec-test-XXXXXX";
  ScratchFile scratch_file(new File(mkstemp(filename.data()), filename, /*check_usage=*/false));
  ASSERT_GE(scratch_file.GetFd(), 0);

  std::vector<std::string> args{art_exec_bin_,
                                "--set-task-profile=ProcessCapacityHigh",
                                kEmptyLdLibraryPath,
                                "--",
                                GetBin("sh"),
                                "-c",
                                "cat /proc/self/cgroup > " + filename};
  auto [pid, scope_guard] = ScopedExecAndWait(args);
  std::string cgroup;
  ASSERT_TRUE(android::base::ReadFileToString(filename, &cgroup));
  EXPECT_THAT(cgroup, HasSubstr(":cpuset:/foreground\n"));
}

TEST_F(ArtExecTest, SetPriority) {
  std::vector<std::string> args{
      art_exec_bin_, "--set-priority=background", kEmptyLdLibraryPath, "--", GetBin("true")};
  auto [pid, scope_guard] = ScopedExecAndWait(args);
  EXPECT_EQ(getpriority(PRIO_PROCESS, pid), ANDROID_PRIORITY_BACKGROUND);
}

TEST_F(ArtExecTest, DropCapabilities) {
  // Switch to a non-root user, but still keep the CAP_FOWNER capability available and inheritable.
  // The order of the following calls matters.
  CHECK_EQ(cap_setuid(kNobody), 0);
  SetCap(CAP_INHERITABLE, CAP_FOWNER);
  SetCap(CAP_EFFECTIVE, CAP_FOWNER);
  ASSERT_EQ(cap_set_ambient(CAP_FOWNER, CAP_SET), 0);

  // Make sure the test is set up correctly (i.e., the child process should normally have the
  // inherited root capability: CAP_FOWNER).
  {
    std::vector<std::string> args{art_exec_bin_, kEmptyLdLibraryPath, "--", GetBin("true")};
    auto [pid, scope_guard] = ScopedExecAndWait(args);
    ASSERT_TRUE(GetCap(pid, CAP_EFFECTIVE, CAP_FOWNER));
  }

  {
    std::vector<std::string> args{
        art_exec_bin_, "--drop-capabilities", kEmptyLdLibraryPath, "--", GetBin("true")};
    auto [pid, scope_guard] = ScopedExecAndWait(args);
    EXPECT_FALSE(GetCap(pid, CAP_EFFECTIVE, CAP_FOWNER));
  }
}

TEST_F(ArtExecTest, CloseFds) {
  std::unique_ptr<File> file1(OS::OpenFileForReading("/dev/zero"));
  std::unique_ptr<File> file2(OS::OpenFileForReading("/dev/zero"));
  std::unique_ptr<File> file3(OS::OpenFileForReading("/dev/zero"));
  ASSERT_NE(file1, nullptr);
  ASSERT_NE(file2, nullptr);
  ASSERT_NE(file3, nullptr);

  std::string filename = "/data/local/tmp/art-exec-test-XXXXXX";
  ScratchFile scratch_file(new File(mkstemp(filename.data()), filename, /*check_usage=*/false));
  ASSERT_GE(scratch_file.GetFd(), 0);

  std::vector<std::string> args{art_exec_bin_,
                                ART_FORMAT("--keep-fds={}:{}", file3->Fd(), file2->Fd()),
                                kEmptyLdLibraryPath,
                                "--",
                                GetBin("sh"),
                                "-c",
                                ART_FORMAT("("
                                           "readlink /proc/self/fd/{} || echo;"
                                           "readlink /proc/self/fd/{} || echo;"
                                           "readlink /proc/self/fd/{} || echo;"
                                           ") > {}",
                                           file1->Fd(),
                                           file2->Fd(),
                                           file3->Fd(),
                                           filename)};

  ScopedExecAndWait(args);

  std::string open_fds;
  ASSERT_TRUE(android::base::ReadFileToString(filename, &open_fds));

  // `file1` should be closed, while the other two should be open. There's a blank line at the end.
  EXPECT_THAT(Split(open_fds, "\n"), ElementsAre(Not("/dev/zero"), "/dev/zero", "/dev/zero", ""));
}

TEST_F(ArtExecTest, Env) {
  std::string filename = "/data/local/tmp/art-exec-test-XXXXXX";
  ScratchFile scratch_file(new File(mkstemp(filename.data()), filename, /*check_usage=*/false));
  ASSERT_GE(scratch_file.GetFd(), 0);

  std::vector<std::string> args{art_exec_bin_,
                                "--env=FOO=BAR",
                                kEmptyLdLibraryPath,
                                "--",
                                GetBin("sh"),
                                "-c",
                                "env > " + filename};

  ScopedExecAndWait(args);

  std::string envs;
  ASSERT_TRUE(android::base::ReadFileToString(filename, &envs));

  EXPECT_THAT(Split(envs, "\n"), Contains("FOO=BAR"));
}

}  // namespace
}  // namespace art

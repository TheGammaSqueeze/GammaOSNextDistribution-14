/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "palette/palette.h"

#include <jni.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <filesystem>

#ifdef ART_TARGET_ANDROID
#include "android-modules-utils/sdk_level.h"
#include "android/api-level.h"
#endif

#include "base/common_art_test.h"
#include "gtest/gtest.h"

namespace {

pid_t GetTid() {
#ifdef __BIONIC__
  return gettid();
#else  // __BIONIC__
  return syscall(__NR_gettid);
#endif  // __BIONIC__
}

#ifdef ART_TARGET_ANDROID
bool PaletteSetTaskProfilesIsSupported(palette_status_t res) {
  if (android::modules::sdklevel::IsAtLeastU()) {
    return true;
  }
  EXPECT_EQ(PALETTE_STATUS_NOT_SUPPORTED, res)
      << "Device API level: " << android_get_device_api_level();
  return false;
}
#endif

}  // namespace

class PaletteClientTest : public testing::Test {};

TEST_F(PaletteClientTest, SchedPriority) {
  int32_t tid = GetTid();
  int32_t saved_priority;
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteSchedGetPriority(tid, &saved_priority));

  EXPECT_EQ(PALETTE_STATUS_INVALID_ARGUMENT, PaletteSchedSetPriority(tid, /*java_priority=*/ 0));
  EXPECT_EQ(PALETTE_STATUS_INVALID_ARGUMENT, PaletteSchedSetPriority(tid, /*java_priority=*/ -1));
  EXPECT_EQ(PALETTE_STATUS_INVALID_ARGUMENT, PaletteSchedSetPriority(tid, /*java_priority=*/ 11));

  EXPECT_EQ(PALETTE_STATUS_OK, PaletteSchedSetPriority(tid, /*java_priority=*/ 1));
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteSchedSetPriority(tid, saved_priority));
}

TEST_F(PaletteClientTest, Trace) {
  bool enabled = false;
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteTraceEnabled(&enabled));
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteTraceBegin("Hello world!"));
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteTraceEnd());
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteTraceIntegerValue("Beans", /*value=*/ 3));
}

TEST_F(PaletteClientTest, Ashmem) {
#ifndef ART_TARGET_ANDROID
  GTEST_SKIP() << "ashmem is only supported on Android";
#else
  int fd;
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteAshmemCreateRegion("ashmem-test", 4096, &fd));
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteAshmemSetProtRegion(fd, PROT_READ | PROT_EXEC));
  EXPECT_EQ(0, close(fd));
#endif
}

class PaletteClientJniTest : public art::CommonArtTest {};

TEST_F(PaletteClientJniTest, JniInvocation) {
  bool enabled;
  EXPECT_EQ(PALETTE_STATUS_OK, PaletteShouldReportJniInvocations(&enabled));

  std::string boot_class_path_string =
      GetClassPathOption("-Xbootclasspath:", GetLibCoreDexFileNames());
  std::string boot_class_path_locations_string =
      GetClassPathOption("-Xbootclasspath-locations:", GetLibCoreDexLocations());

  JavaVMOption options[] = {
      {.optionString = boot_class_path_string.c_str(), .extraInfo = nullptr},
      {.optionString = boot_class_path_locations_string.c_str(), .extraInfo = nullptr},
  };
  JavaVMInitArgs vm_args = {
      .version = JNI_VERSION_1_6,
      .nOptions = std::size(options),
      .options = options,
      .ignoreUnrecognized = JNI_TRUE,
  };

  JavaVM* jvm = nullptr;
  JNIEnv* env = nullptr;
  EXPECT_EQ(JNI_OK, JNI_CreateJavaVM(&jvm, &env, &vm_args));
  ASSERT_NE(nullptr, env);

  PaletteNotifyBeginJniInvocation(env);
  PaletteNotifyEndJniInvocation(env);

  EXPECT_EQ(JNI_OK, jvm->DestroyJavaVM());
}

TEST_F(PaletteClientTest, SetTaskProfiles) {
#ifndef ART_TARGET_ANDROID
  GTEST_SKIP() << "SetTaskProfiles is only supported on Android";
#else
  if (!std::filesystem::exists("/sys/fs/cgroup/cgroup.controllers")) {
    // This is intended to detect ART chroot setups, where SetTaskProfiles won't work.
    GTEST_SKIP() << "Kernel cgroup support missing";
  }

  const char* profiles[] = {"ProcessCapacityHigh", "TimerSlackNormal"};
  palette_status_t res = PaletteSetTaskProfiles(GetTid(), &profiles[0], 2);
  if (PaletteSetTaskProfilesIsSupported(res)) {
    // SetTaskProfiles will only work fully if we run as root. Otherwise it'll
    // return false which is mapped to PALETTE_STATUS_FAILED_CHECK_LOG.
    if (getuid() == 0) {
      EXPECT_EQ(PALETTE_STATUS_OK, res);
    } else {
      EXPECT_EQ(PALETTE_STATUS_FAILED_CHECK_LOG, res);
    }
  }
#endif
}

TEST_F(PaletteClientTest, SetTaskProfilesCpp) {
#ifndef ART_TARGET_ANDROID
  GTEST_SKIP() << "SetTaskProfiles is only supported on Android";
#else
  if (!std::filesystem::exists("/sys/fs/cgroup/cgroup.controllers")) {
    // This is intended to detect ART chroot setups, where SetTaskProfiles won't work.
    GTEST_SKIP() << "Kernel cgroup support missing";
  }

  std::vector<std::string> profiles = {"ProcessCapacityHigh", "TimerSlackNormal"};
  palette_status_t res = PaletteSetTaskProfiles(GetTid(), profiles);
  if (PaletteSetTaskProfilesIsSupported(res)) {
    // SetTaskProfiles will only work fully if we run as root. Otherwise it'll
    // return false which is mapped to PALETTE_STATUS_FAILED_CHECK_LOG.
    if (getuid() == 0) {
      EXPECT_EQ(PALETTE_STATUS_OK, res);
    } else {
      EXPECT_EQ(PALETTE_STATUS_FAILED_CHECK_LOG, res);
    }
  }
#endif
}

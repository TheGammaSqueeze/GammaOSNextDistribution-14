/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "test_base.h"

#include <gtest/gtest.h>

#include "chre/core/event_loop_manager.h"
#include "chre/core/init.h"
#include "chre/platform/linux/platform_log.h"
#include "chre/util/time.h"
#include "chre_api/chre/version.h"
#include "inc/test_util.h"
#include "test_util.h"

namespace chre {

TEST_F(TestBase, InfoStructOldVersionCheckForAppPermission) {
  constexpr uint8_t kInfoStructVersionOld = 2;

  constexpr uint64_t kAppId = 0x01234;
  constexpr uint32_t kAppVersion = 0;
  constexpr uint32_t kAppPerms = 0;

  UniquePtr<Nanoapp> oldnanoapp = createStaticNanoapp(
      kInfoStructVersionOld, "Test nanoapp", kAppId, kAppVersion, kAppPerms,
      defaultNanoappStart, defaultNanoappHandleEvent, defaultNanoappEnd);

  EXPECT_FALSE(oldnanoapp->supportsAppPermissions());
}

TEST_F(TestBase, InfoStructCurrentVersionCheckForAppPermission) {
  constexpr uint8_t kInfoStructVersionCurrent = 3;

  constexpr uint64_t kAppId = 0x56789;
  constexpr uint32_t kAppVersion = 0;
  constexpr uint32_t kAppPerms = 0;

  UniquePtr<Nanoapp> currentnanoapp = createStaticNanoapp(
      kInfoStructVersionCurrent, "Test nanoapp", kAppId, kAppVersion, kAppPerms,
      defaultNanoappStart, defaultNanoappHandleEvent, defaultNanoappEnd);

  EXPECT_TRUE(currentnanoapp->supportsAppPermissions());
}

TEST_F(TestBase, InfoStructFutureVersionCheckForAppPermission) {
  constexpr uint8_t kInfoStructVersionFuture = 4;

  constexpr uint64_t kAppId = 0xabcde;
  constexpr uint32_t kAppVersion = 0;
  constexpr uint32_t kAppPerms = 0;

  UniquePtr<Nanoapp> futurenanoapp = createStaticNanoapp(
      kInfoStructVersionFuture, "Test nanoapp", kAppId, kAppVersion, kAppPerms,
      defaultNanoappStart, defaultNanoappHandleEvent, defaultNanoappEnd);

  EXPECT_TRUE(futurenanoapp->supportsAppPermissions());
}

}  // namespace chre

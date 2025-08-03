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

#include "gtest/gtest.h"

#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <sys/system_properties.h>

TEST(SystemProperties, Get) {
  char value[PROP_VALUE_MAX];
  ASSERT_GT(__system_property_get("ro.build.version.sdk", value), 0);
  EXPECT_GE(atoi(value), 19);
}

void ForEachCallback(const prop_info* pi, void* cookie) {
  char name[PROP_NAME_MAX];
  char value[PROP_VALUE_MAX];
  __system_property_read(pi, name, value);
  if (strcmp(name, "ro.build.version.sdk") == 0) {
    *reinterpret_cast<bool*>(cookie) = true;
  }
}

TEST(SystemProperties, ForEach) {
  bool has_build_version_sdk = false;
  ASSERT_EQ(__system_property_foreach(ForEachCallback, &has_build_version_sdk), 0);
  ASSERT_TRUE(has_build_version_sdk);
}

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

#include <cutils/properties.h>
#include <gtest/gtest.h>

#include <string>

#include "os/system_properties.h"

namespace testing {

using bluetooth::os::ClearSystemPropertiesForHost;
using bluetooth::os::GetSystemProperty;
using bluetooth::os::SetSystemProperty;

TEST(SystemPropertiesTest, set_and_get_test) {
  ASSERT_TRUE(SetSystemProperty("persist.bluetooth.factoryreset", "true"));
  auto ret = GetSystemProperty("persist.bluetooth.factoryreset");
  ASSERT_TRUE(ret);
  ASSERT_EQ(ret, "true");
  ASSERT_TRUE(SetSystemProperty("persist.bluetooth.factoryreset", "false"));
  ret = GetSystemProperty("persist.bluetooth.factoryreset");
  ASSERT_TRUE(ret);
  ASSERT_EQ(ret, "false");
  ret = GetSystemProperty("persist.bluetooth.factoryreset_do_not_exist");
  ASSERT_FALSE(ret);
  if (ClearSystemPropertiesForHost()) {
    ASSERT_FALSE(GetSystemProperty("persist.bluetooth.factoryreset"));
  }
}

TEST(SystemPropertiesTest, getUint32) {
  std::string property("SystemPropertiesTest_getUint32");
  uint32_t value = 42;
  ASSERT_TRUE(SetSystemProperty(property, std::to_string(value)));
  ASSERT_EQ(bluetooth::os::GetSystemPropertyUint32(property, 0), value);
}

TEST(SystemPropertiesTest, getUint32BaseHex) {
  std::string property("SystemPropertiesTest_getUint32BaseHex");
  uint32_t value = 42;
  std::stringstream stream;
  stream << std::showbase << std::hex << value;
  ASSERT_TRUE(SetSystemProperty(property, stream.str()));
  ASSERT_EQ(bluetooth::os::GetSystemPropertyUint32Base(property, 0), value);
  ASSERT_EQ(bluetooth::os::GetSystemPropertyUint32Base(property, 1, 10), 0u);  // if parsed as a dec
}

}  // namespace testing
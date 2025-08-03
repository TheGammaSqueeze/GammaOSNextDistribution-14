/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <string>

#include <android-base/test_utils.h>
#include <gtest/gtest.h>

#include "Common.h"
#include "TypeChecker.h"

using android::properties::ParsePropertyInfoFile;
using android::properties::PropertyInfoEntry;

namespace {

constexpr const char* kApi =
    R"(
props {
    owner: Platform
    module: "android.platprop"
    prop {
        api_name: "prop1"
        type: Long
        scope: Public
        access: ReadWrite
        prop_name: "prop1"
    }
    prop {
        api_name: "prop2"
        type: Enum
        scope: Public
        access: Writeonce
        enum_values: "a|b|c"
        prop_name: "ro.public.prop2"
    }
    prop {
        api_name: "prop3"
        type: Boolean
        scope: Public
        access: ReadWrite
        prop_name: "ctl.start$prop3"
    }
    prop {
        api_name: "prop4"
        type: String
        scope: Public
        access: Readonly
        prop_name: "ro.prop4"
        legacy_prop_name: "ro.legacy.prop4"
        deprecated: true
    }

}
)";

constexpr const char* kContexts =
    R"(
prop1           u:object_r:default_prop:s0 exact int
ro.public.prop2 u:object_r:foo_prop:s0 exact enum c b a
ctl.start$prop3 u:object_r:ctl_prop:s0 exact bool
ro.prop4        u:object_r:bar_prop:s0 exact string
ro.legacy.prop4 u:object_r:baz_prop:s0 exact string
)";

constexpr const char* kBadContexts =
    R"(
prop1           u:object_r:default_prop:s0 exact double
ro.public.prop2 u:object_r:foo_prop:s0 exact enum a c
ctl.start$prop3 u:object_r:ctl_prop:s0 exact string
ro.prop4        u:object_r:bar_prop:s0 exact int
ro.legacy.prop4 u:object_r:baz_prop:s0 exact bool
)";

}  // namespace

TEST(SyspropTest, TypeCheckerTest) {
  TemporaryFile api_file;
  close(api_file.fd);
  api_file.fd = -1;
  ASSERT_TRUE(android::base::WriteStringToFile(kApi, api_file.path));

  std::string err;
  auto api = ParseApiFile(api_file.path);
  ASSERT_RESULT_OK(api);

  // Good property_contexts tests
  std::vector<PropertyInfoEntry> entries;
  std::vector<std::string> errors;
  ParsePropertyInfoFile(kContexts, true, &entries, &errors);
  ASSERT_TRUE(errors.empty());

  auto res = CheckPropertyTypes(*api, entries);
  EXPECT_TRUE(res.ok());

  // Bad property_contexts tests
  std::vector<PropertyInfoEntry> bad_entries;
  ParsePropertyInfoFile(kBadContexts, true, &bad_entries, &errors);
  ASSERT_TRUE(errors.empty());

  auto bad_res = CheckPropertyTypes(*api, bad_entries);
  EXPECT_FALSE(bad_res.ok());

  EXPECT_EQ(
      bad_res.error().message(),
      "Type of prop 'prop1' is incompatible with property_contexts\n"
      "In sysprop_library: Long\n"
      "In property_contexts: double (should be 'int')\n"
      "\n"
      "Type of prop 'ro.public.prop2' is incompatible with property_contexts\n"
      "In sysprop_library: Enum a|b|c\n"
      "In property_contexts: enum a c (should be 'enum a b c')\n"
      "\n"
      "Type of prop 'ctl.start$prop3' is incompatible with property_contexts\n"
      "In sysprop_library: Boolean\n"
      "In property_contexts: string (should be 'bool')\n"
      "\n"
      "Type of prop 'ro.prop4' is incompatible with property_contexts\n"
      "In sysprop_library: String\n"
      "In property_contexts: int (should be 'string')\n"
      "\n"
      "Type of prop 'ro.legacy.prop4' is incompatible with property_contexts\n"
      "In sysprop_library: String\n"
      "In property_contexts: bool (should be 'string')\n");
}

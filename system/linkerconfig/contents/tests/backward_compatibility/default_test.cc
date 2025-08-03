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

#include <gtest/gtest.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/baseconfig.h"
#include "linkerconfig/configwriter.h"
#include "linkerconfig/variables.h"
#include "testbase.h"

using android::linkerconfig::contents::Context;
using android::linkerconfig::modules::ApexInfo;

struct linkerconfig_default_backward_compatibility : ::testing::Test {
  void SetUp() override {
    MockVariables("Q");
    ApexInfo vndk_apex;
    vndk_apex.name = "com.android.vndk.vQ";
    ctx.SetApexModules({vndk_apex});
  }
  Context ctx;
};

TEST_F(linkerconfig_default_backward_compatibility, common_system_section) {
  auto config = android::linkerconfig::contents::CreateBaseConfiguration(ctx);

  auto system_section = config.GetSection("system");
  ASSERT_TRUE(system_section);

  auto default_namespace = system_section->GetNamespace("default");
  ASSERT_TRUE(default_namespace);

  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/vendor/framework"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/vendor/app"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/vendor/priv-app"));
  ASSERT_TRUE(
      ContainsPermittedPath(default_namespace, "/system/vendor/framework"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/system/vendor/app"));
  ASSERT_TRUE(
      ContainsPermittedPath(default_namespace, "/system/vendor/priv-app"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/odm/framework"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/odm/app"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/odm/priv-app"));

  auto sphal_namespace = system_section->GetNamespace("sphal");
  ASSERT_TRUE(sphal_namespace);

  ASSERT_TRUE(ContainsSearchPath(sphal_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(sphal_namespace, "/vendor/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(sphal_namespace, "/vendor/${LIB}/hw"));

  ASSERT_TRUE(ContainsPermittedPath(sphal_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsPermittedPath(sphal_namespace, "/vendor/${LIB}"));
  ASSERT_TRUE(ContainsPermittedPath(sphal_namespace, "/vendor/odm/${LIB}"));
  ASSERT_TRUE(ContainsPermittedPath(sphal_namespace, "/system/vendor/${LIB}"));

  auto rs_namespace = system_section->GetNamespace("rs");
  ASSERT_TRUE(rs_namespace);

  ASSERT_TRUE(ContainsSearchPath(rs_namespace, "/odm/${LIB}/vndk-sp"));
  ASSERT_TRUE(ContainsSearchPath(rs_namespace, "/vendor/${LIB}/vndk-sp"));
  ASSERT_TRUE(ContainsSearchPath(rs_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(rs_namespace, "/vendor/${LIB}"));

  ASSERT_TRUE(ContainsPermittedPath(rs_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsPermittedPath(rs_namespace, "/vendor/${LIB}"));
  ASSERT_TRUE(ContainsPermittedPath(rs_namespace, "/system/vendor/${LIB}"));

  auto vndk_namespace = system_section->GetNamespace("vndk");
  ASSERT_TRUE(vndk_namespace);

  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/odm/${LIB}/vndk-sp"));
  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/vendor/${LIB}/vndk-sp"));

  ASSERT_TRUE(ContainsPermittedPath(vndk_namespace, "/odm/${LIB}/hw"));
  ASSERT_TRUE(ContainsPermittedPath(vndk_namespace, "/odm/${LIB}/egl"));
  ASSERT_TRUE(ContainsPermittedPath(vndk_namespace, "/vendor/${LIB}/hw"));
  ASSERT_TRUE(ContainsPermittedPath(vndk_namespace, "/vendor/${LIB}/egl"));
}

TEST_F(linkerconfig_default_backward_compatibility, common_vendor_section) {
  auto config = android::linkerconfig::contents::CreateBaseConfiguration(ctx);

  auto vendor_section = config.GetSection("vendor");
  ASSERT_TRUE(vendor_section);

  auto default_namespace = vendor_section->GetNamespace("default");
  ASSERT_TRUE(default_namespace);

  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/vendor/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/vendor/${LIB}/hw"));
  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/vendor/${LIB}/egl"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/odm"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/vendor"));
  ASSERT_TRUE(ContainsPermittedPath(default_namespace, "/system/vendor"));

  auto vndk_namespace = vendor_section->GetNamespace("vndk");
  ASSERT_TRUE(vndk_namespace);

  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/odm/${LIB}/vndk"));
  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/odm/${LIB}/vndk-sp"));
  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/vendor/${LIB}/vndk"));
  ASSERT_TRUE(ContainsSearchPath(vndk_namespace, "/vendor/${LIB}/vndk-sp"));
}

TEST_F(linkerconfig_default_backward_compatibility,
       common_unrestricted_section) {
  auto config = android::linkerconfig::contents::CreateBaseConfiguration(ctx);

  auto unrestricted_section = config.GetSection("unrestricted");
  ASSERT_TRUE(unrestricted_section);

  auto default_namespace = unrestricted_section->GetNamespace("default");
  ASSERT_TRUE(default_namespace);

  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/odm/${LIB}"));
  ASSERT_TRUE(ContainsSearchPath(default_namespace, "/vendor/${LIB}"));
}

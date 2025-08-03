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

#include "linkerconfig/baseconfig.h"
#include "configurationtest.h"
#include "linkerconfig/configwriter.h"
#include "mockenv.h"

using android::linkerconfig::contents::Context;
using android::linkerconfig::contents::CreateBaseConfiguration;
using android::linkerconfig::modules::ApexInfo;
using android::linkerconfig::modules::ConfigWriter;

TEST(linkerconfig_configuration_fulltest, baseconfig_test) {
  MockGenericVariables();
  Context ctx;
  ctx.SetApexModules({CreateTestVndkApex()});
  auto base_config = CreateBaseConfiguration(ctx);
  ConfigWriter config_writer;

  base_config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST(linkerconfig_configuration_fulltest,
     baseconfig_vndk_using_core_variant_test) {
  MockGenericVariables();
  MockVndkUsingCoreVariant();
  Context ctx;
  ctx.SetApexModules({CreateTestVndkApex()});
  auto base_config = CreateBaseConfiguration(ctx);
  ConfigWriter config_writer;

  base_config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST(linkerconfig_configuration_fulltest, baseconfig_vndk_27_test) {
  MockGenericVariables();
  MockVndkVersion("27");
  Context ctx;
  ctx.SetApexModules({CreateTestVndkApex()});
  auto base_config = CreateBaseConfiguration(ctx);
  ConfigWriter config_writer;

  base_config.WriteConfig(config_writer);

  VerifyConfiguration(config_writer.ToString());
}

TEST(linkerconfig_configuration_fulltest,
     apexes_with_jni_are_visible_to_system_section) {
  MockGenericVariables();
  Context ctx;
  ctx.SetApexModules(
      {CreateTestVndkApex(),
       ApexInfo(
           "foo", "", {}, {}, {"libjni.so"}, {}, false, true, false, false)});
  auto config = CreateBaseConfiguration(ctx);

  auto* section = config.GetSection("system");
  ASSERT_TRUE(section);
  auto* ns = section->GetNamespace("foo");
  ASSERT_TRUE(ns);
  ASSERT_TRUE(ns->IsVisible());

  ConfigWriter config_writer;
  config.WriteConfig(config_writer);
  VerifyConfiguration(config_writer.ToString());
}

TEST(linkerconfig_configuration_fulltest,
     vendor_apex_configured_to_use_vndk_can_load_vndk) {
  MockGenericVariables();
  Context ctx;

  android::linkerconfig::proto::LinkerConfig vendor_config;
  vendor_config.add_requirelibs("libapexprovide.so");
  vendor_config.add_providelibs("libvendorprovide.so");
  ctx.SetVendorConfig(vendor_config);

  // Vendor apex requires :vndk
  auto vendor_apex = ApexInfo("vendor_apex",
                              "/apex/vendor_apex",
                              {"libapexprovide.so"},
                              {":vndk", "libvendorprovide.so"},
                              {},
                              {},
                              false,
                              true,
                              true,
                              false);
  vendor_apex.original_path = "/vendor/apex/com.android.vendor";
  ctx.SetApexModules({vendor_apex,
                      // To generate vendor section
                      ApexInfo("com.android.vndk.v",
                               "/apex/com.android.vndk.v",
                               {},
                               {},
                               {},
                               {},
                               false,
                               true,
                               true,
                               false)});

  auto config = CreateBaseConfiguration(ctx);

  auto* section = config.GetSection("vendor");
  ASSERT_TRUE(section);

  // vendor apex should be able to load vndk libraries
  auto shared_libs =
      section->GetNamespace("vendor_apex")->GetLink("vndk").GetSharedLibs();
  ASSERT_TRUE(std::find(shared_libs.begin(),
                        shared_libs.end(),
                        "vndk_core_libraries") != shared_libs.end());

  ConfigWriter config_writer;
  config.WriteConfig(config_writer);
  VerifyConfiguration(config_writer.ToString());
}

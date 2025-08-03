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

#define LOG_TAG "derive_sdk_test"

#include "derive_sdk.h"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-modules-utils/sdk_level.h>
#include <gtest/gtest.h>
#include <stdlib.h>
#include <sys/stat.h>

#include <cstdlib>

#include "packages/modules/common/proto/sdk.pb.h"

#define EXPECT_ALL(n) \
  {                   \
    EXPECT_R(n);      \
    EXPECT_S(n);      \
    EXPECT_T(n);      \
    EXPECT_U(n);      \
  }

#define EXPECT_R(n) EXPECT_EQ(GetR(), (n))

// Only expect the S extension level to be set on S+ devices.
#define EXPECT_S(n) EXPECT_EQ(GetS(), android::modules::sdklevel::IsAtLeastS() ? (n) : -1)

// Only expect the T extension level to be set on T+ devices.
#define EXPECT_T(n) EXPECT_EQ(GetT(), android::modules::sdklevel::IsAtLeastT() ? (n) : -1)

// Only expect the U extension level to be set on U+ devices.
#define EXPECT_U(n) EXPECT_EQ(GetU(), android::modules::sdklevel::IsAtLeastU() ? (n) : -1)

class DeriveSdkTest : public ::testing::Test {
 protected:
  void TearDown() override { android::derivesdk::SetSdkLevels("/apex"); }

  const std::string dir() { return std::string(dir_.path); }

  const std::string EtcDir(const std::string& apex) {
    return dir() + "/" + apex + "/etc";
  }

  void AddVersionToDb(const int version, const std::unordered_map<SdkModule, int>& requirements) {
    ExtensionVersion* sdk = db_.add_versions();
    sdk->set_version(version);
    for (auto pair : requirements) {
      ExtensionVersion_ModuleRequirement* req = sdk->add_requirements();
      req->set_module(pair.first);
      req->mutable_version()->set_version(pair.second);
    }
    WriteProto(db_, EtcDir("com.android.sdkext") + "/extensions_db.pb");
  }

  void AddExtensionVersion(const int version,
                           const std::unordered_map<SdkModule, int>& requirements) {
    AddVersionToDb(version, requirements);
    ASSERT_TRUE(android::derivesdk::SetSdkLevels(dir()));
  }

  void SetApexVersion(const std::string apex, int version) {
    SdkVersion sdk_version;
    sdk_version.set_version(version);
    WriteProto(sdk_version, EtcDir(apex) + "/sdkinfo.pb");

    ASSERT_TRUE(android::derivesdk::SetSdkLevels(dir()));
  }

  void WriteProto(const google::protobuf::MessageLite& proto,
                  const std::string& path) {
    std::string buf;
    proto.SerializeToString(&buf);
    std::string cmd("mkdir -p " + path.substr(0, path.find_last_of('/')));
    ASSERT_EQ(0, system(cmd.c_str()));
    ASSERT_TRUE(android::base::WriteStringToFile(buf, path, true));
  }

  int GetR() { return android::base::GetIntProperty("build.version.extensions.r", -1); }

  int GetS() { return android::base::GetIntProperty("build.version.extensions.s", -1); }

  int GetT() { return android::base::GetIntProperty("build.version.extensions.t", -1); }

  int GetU() { return android::base::GetIntProperty("build.version.extensions.u", -1); }

  void EXPECT_ADSERVICES(int n) {
    int actual = android::base::GetIntProperty("build.version.extensions.ad_services", -1);
    // Only expect the AdServices extension level to be set on T+ devices.
    EXPECT_EQ(actual, android::modules::sdklevel::IsAtLeastT() ? n : -1);
  }

  ExtensionDatabase db_;
  TemporaryDir dir_;
};

TEST_F(DeriveSdkTest, OneDessert_OneVersion_OneApex) {
  AddExtensionVersion(3, {{SdkModule::SDK_EXTENSIONS, 2}});
  EXPECT_ALL(0);

  SetApexVersion("com.android.sdkext", 3);
  EXPECT_ALL(3);
}

TEST_F(DeriveSdkTest, OneDessert_OneVersion_TwoApexes) {
  AddExtensionVersion(5, {
                             {SdkModule::MEDIA, 5},
                             {SdkModule::SDK_EXTENSIONS, 2},
                         });
  EXPECT_ALL(0);

  // Only sdkext
  SetApexVersion("com.android.sdkext", 2);
  EXPECT_ALL(0);

  // Only media
  SetApexVersion("com.android.sdkext", 0);
  SetApexVersion("com.android.media", 5);
  EXPECT_ALL(0);

  // Both
  SetApexVersion("com.android.sdkext", 2);
  EXPECT_ALL(5);
}

TEST_F(DeriveSdkTest, OneDessert_ManyVersions) {
  AddExtensionVersion(1, {
                             {SdkModule::MEDIA, 1},
                         });
  EXPECT_ALL(0);
  SetApexVersion("com.android.media", 1);
  EXPECT_ALL(1);

  AddExtensionVersion(2, {
                             {SdkModule::MEDIA, 1},
                             {SdkModule::MEDIA_PROVIDER, 2},
                             {SdkModule::SDK_EXTENSIONS, 2},
                         });
  EXPECT_ALL(1);
  SetApexVersion("com.android.mediaprovider", 2);
  EXPECT_ALL(1);
  SetApexVersion("com.android.sdkext", 2);
  EXPECT_ALL(2);

  AddExtensionVersion(3, {
                             {SdkModule::MEDIA, 3},
                             {SdkModule::MEDIA_PROVIDER, 2},
                             {SdkModule::SDK_EXTENSIONS, 3},
                         });
  EXPECT_ALL(2);
  SetApexVersion("com.android.media", 3);
  EXPECT_ALL(2);
  SetApexVersion("com.android.sdkext", 3);
  EXPECT_ALL(3);
}

TEST_F(DeriveSdkTest, TwoDesserts_ManyVersions) {
  AddExtensionVersion(1, {
                             {SdkModule::TETHERING, 1},
                         });
  EXPECT_ALL(0);

  // Only tethering v1
  SetApexVersion("com.android.tethering", 1);
  EXPECT_ALL(1);

  // V2 defined
  AddExtensionVersion(2, {
                             {SdkModule::ART, 2},
                             {SdkModule::TETHERING, 1},
                         });
  EXPECT_R(2);
  EXPECT_S(1);

  // Only art v2
  SetApexVersion("com.android.tethering", 0);
  SetApexVersion("com.android.art", 2);
  EXPECT_ALL(0);

  // Both
  SetApexVersion("com.android.tethering", 1);
  EXPECT_ALL(2);

  // V3 defined
  AddExtensionVersion(3, {
                             {SdkModule::ART, 3},
                             {SdkModule::MEDIA, 3},
                             {SdkModule::TETHERING, 1},
                         });
  EXPECT_ALL(2);

  // Only media v3
  SetApexVersion("com.android.media", 3);
  EXPECT_R(3);
  EXPECT_S(2);

  // Only art v3
  SetApexVersion("com.android.media", 0);
  SetApexVersion("com.android.art", 3);
  EXPECT_ALL(2);

  // Both
  SetApexVersion("com.android.media", 3);
  EXPECT_ALL(3);
}

TEST_F(DeriveSdkTest, UnmappedModule) {
  AddVersionToDb(5, {
                        {static_cast<SdkModule>(77), 5},  // Doesn't exist.
                        {SdkModule::SDK_EXTENSIONS, 2},
                    });

  ASSERT_FALSE(android::derivesdk::SetSdkLevels(dir()));
}

TEST_F(DeriveSdkTest, AdServicesPreV7) {
  AddExtensionVersion(1, {
                             {SdkModule::TETHERING, 1},
                         });
  EXPECT_ALL(0);
  EXPECT_ADSERVICES(1);

  SetApexVersion("com.android.tethering", 1);
  EXPECT_ALL(1);

  // V2 defined
  AddExtensionVersion(2, {
                             {SdkModule::AD_SERVICES, 2},
                             {SdkModule::TETHERING, 2},
                         });
  EXPECT_ALL(1);
  EXPECT_ADSERVICES(1);

  // Only adservices v2
  SetApexVersion("com.android.adservices", 2);
  EXPECT_ALL(1);
  EXPECT_ADSERVICES(2);

  // Both v2
  SetApexVersion("com.android.tethering", 2);
  EXPECT_ALL(2);
  EXPECT_ADSERVICES(2);

  // Only tethering v2. R and S extension are bumped, but T requires adserices.
  SetApexVersion("com.android.adservices", 0);
  SetApexVersion("com.android.tethering", 2);
  EXPECT_R(2);
  EXPECT_S(2);
  EXPECT_T(1);
  EXPECT_ADSERVICES(1);
}

TEST_F(DeriveSdkTest, AdServicesPostV7) {
  // Need to add a base version with an R module to prevent the
  // dessert extension versions from getting bumped.
  AddExtensionVersion(1, {
                             {SdkModule::TETHERING, 1},
                         });

  // Only adservices v2
  SetApexVersion("com.android.adservices", 2);
  EXPECT_ALL(0);
  EXPECT_ADSERVICES(1);

  // From v7 and onwards, we only care about the adservices version
  SetApexVersion("com.android.adservices", 7);
  EXPECT_ALL(0);
  EXPECT_ADSERVICES(7);

  SetApexVersion("com.android.adservices", 10);
  EXPECT_ALL(0);
  EXPECT_ADSERVICES(10);
}

TEST_F(DeriveSdkTest, Tiramisu) {
  AddExtensionVersion(1, {
                             {SdkModule::AD_SERVICES, 1},
                             {SdkModule::APPSEARCH, 2},
                             {SdkModule::ON_DEVICE_PERSONALIZATION, 3},
                         });
  EXPECT_T(0);

  SetApexVersion("com.android.adservices", 1);
  EXPECT_T(0);

  SetApexVersion("com.android.appsearch", 2);
  EXPECT_T(0);

  SetApexVersion("com.android.ondevicepersonalization", 3);
  EXPECT_T(1);
}

TEST_F(DeriveSdkTest, UpsideDownCake) {
  AddExtensionVersion(1, {
                             {SdkModule::CONFIG_INFRASTRUCTURE, 1},
                             {SdkModule::HEALTH_FITNESS, 2},
                         });
  EXPECT_U(0);

  SetApexVersion("com.android.configinfrastructure", 1);
  EXPECT_U(0);

  SetApexVersion("com.android.healthfitness", 2);
  EXPECT_U(1);
}

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}

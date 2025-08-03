/*
 * Copyright 2024 The Android Open Source Project
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

#include "audio_hal_interface/hal_version_manager.h"

#include <gtest/gtest.h>

using bluetooth::audio::BluetoothAudioHalTransport;
using bluetooth::audio::BluetoothAudioHalVersion;
using bluetooth::audio::HalVersionManager;

class BluetoothAudioHalVersionTest : public ::testing::Test {};

TEST_F(BluetoothAudioHalVersionTest, versionOperatorEqual) {
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0 ==
              BluetoothAudioHalVersion::VERSION_2_0);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1 ==
              BluetoothAudioHalVersion::VERSION_2_1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1 ==
              BluetoothAudioHalVersion::VERSION_AIDL_V1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2 ==
              BluetoothAudioHalVersion::VERSION_AIDL_V2);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3 ==
              BluetoothAudioHalVersion::VERSION_AIDL_V3);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4 ==
              BluetoothAudioHalVersion::VERSION_AIDL_V4);
}

TEST_F(BluetoothAudioHalVersionTest, versionOperatorLessOrEqual) {
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0 <
              BluetoothAudioHalVersion::VERSION_2_1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0 <=
              BluetoothAudioHalVersion::VERSION_2_1);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1 <
              BluetoothAudioHalVersion::VERSION_AIDL_V1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1 <=
              BluetoothAudioHalVersion::VERSION_AIDL_V1);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1 <
              BluetoothAudioHalVersion::VERSION_AIDL_V2);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1 <=
              BluetoothAudioHalVersion::VERSION_AIDL_V2);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2 <
              BluetoothAudioHalVersion::VERSION_AIDL_V3);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2 <=
              BluetoothAudioHalVersion::VERSION_AIDL_V3);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3 <
              BluetoothAudioHalVersion::VERSION_AIDL_V4);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3 <=
              BluetoothAudioHalVersion::VERSION_AIDL_V4);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_1 <
               BluetoothAudioHalVersion::VERSION_2_0);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_1 <=
               BluetoothAudioHalVersion::VERSION_2_0);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V1 <
               BluetoothAudioHalVersion::VERSION_2_1);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V1 <=
               BluetoothAudioHalVersion::VERSION_2_1);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V2 <
               BluetoothAudioHalVersion::VERSION_AIDL_V1);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V2 <=
               BluetoothAudioHalVersion::VERSION_AIDL_V1);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V3 <
               BluetoothAudioHalVersion::VERSION_AIDL_V2);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V3 <=
               BluetoothAudioHalVersion::VERSION_AIDL_V2);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V4 <
               BluetoothAudioHalVersion::VERSION_AIDL_V3);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V4 <=
               BluetoothAudioHalVersion::VERSION_AIDL_V3);
}

TEST_F(BluetoothAudioHalVersionTest, versionOperatorGreaterOrEqual) {
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1 >
              BluetoothAudioHalVersion::VERSION_2_0);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1 >=
              BluetoothAudioHalVersion::VERSION_2_0);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1 >
              BluetoothAudioHalVersion::VERSION_2_1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1 >=
              BluetoothAudioHalVersion::VERSION_2_1);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2 >
              BluetoothAudioHalVersion::VERSION_AIDL_V1);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2 >=
              BluetoothAudioHalVersion::VERSION_AIDL_V1);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3 >
              BluetoothAudioHalVersion::VERSION_AIDL_V2);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3 >=
              BluetoothAudioHalVersion::VERSION_AIDL_V2);

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4 >
              BluetoothAudioHalVersion::VERSION_AIDL_V3);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4 >=
              BluetoothAudioHalVersion::VERSION_AIDL_V3);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_0 >
               BluetoothAudioHalVersion::VERSION_2_1);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_0 >=
               BluetoothAudioHalVersion::VERSION_2_1);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_1 >
               BluetoothAudioHalVersion::VERSION_AIDL_V1);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_1 >=
               BluetoothAudioHalVersion::VERSION_AIDL_V1);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V1 >
               BluetoothAudioHalVersion::VERSION_AIDL_V2);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V1 >=
               BluetoothAudioHalVersion::VERSION_AIDL_V2);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V2 >
               BluetoothAudioHalVersion::VERSION_AIDL_V3);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V2 >=
               BluetoothAudioHalVersion::VERSION_AIDL_V3);

  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V3 >
               BluetoothAudioHalVersion::VERSION_AIDL_V4);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V3 >=
               BluetoothAudioHalVersion::VERSION_AIDL_V4);
}

TEST_F(BluetoothAudioHalVersionTest, HIDL_VERSION_2_0) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_2_0,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::HIDL, 2, 0));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_2_0.getTransport(),
            BluetoothAudioHalTransport::HIDL);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0.isHIDL());
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_0.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0.toString().find(
                  "transport: HIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0.toString().find(
                  "major: 2") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_0.toString().find(
                  "minor: 0") != std::string::npos);
}

TEST_F(BluetoothAudioHalVersionTest, HIDL_VERSION_2_1) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_2_1,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::HIDL, 2, 1));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_2_1.getTransport(),
            BluetoothAudioHalTransport::HIDL);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1.isHIDL());
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_2_1.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1.toString().find(
                  "transport: HIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1.toString().find(
                  "major: 2") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_2_1.toString().find(
                  "minor: 1") != std::string::npos);
}

TEST_F(BluetoothAudioHalVersionTest, AIDL_VERSIONS_V1) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V1,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 1, 0));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V1.getTransport(),
            BluetoothAudioHalTransport::AIDL);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V1.isHIDL());
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1.toString().find(
                  "transport: AIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1.toString().find(
                  "major: 1") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V1.toString().find(
                  "minor: 0") != std::string::npos);
}

TEST_F(BluetoothAudioHalVersionTest, AIDL_VERSIONS_V2) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V2,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 2, 0));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V2.getTransport(),
            BluetoothAudioHalTransport::AIDL);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V2.isHIDL());
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2.toString().find(
                  "transport: AIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2.toString().find(
                  "major: 2") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V2.toString().find(
                  "minor: 0") != std::string::npos);
}

TEST_F(BluetoothAudioHalVersionTest, AIDL_VERSIONS_V3) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V3,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 3, 0));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V3.getTransport(),
            BluetoothAudioHalTransport::AIDL);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V3.isHIDL());
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3.toString().find(
                  "transport: AIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3.toString().find(
                  "major: 3") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V3.toString().find(
                  "minor: 0") != std::string::npos);
}

TEST_F(BluetoothAudioHalVersionTest, AIDL_VERSIONS_V4) {
  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V4,
            BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 4, 0));

  EXPECT_EQ(BluetoothAudioHalVersion::VERSION_AIDL_V4.getTransport(),
            BluetoothAudioHalTransport::AIDL);
  EXPECT_FALSE(BluetoothAudioHalVersion::VERSION_AIDL_V4.isHIDL());
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4.isAIDL());

  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4.toString().find(
                  "transport: AIDL") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4.toString().find(
                  "major: 4") != std::string::npos);
  EXPECT_TRUE(BluetoothAudioHalVersion::VERSION_AIDL_V4.toString().find(
                  "minor: 0") != std::string::npos);
}

/**
 * An example of future AIDL version (next one will be V5), we check that next
 * AIDL version will be larger than existing AIDL versions
 */
TEST_F(BluetoothAudioHalVersionTest, AIDL_VERSIONS_Vx) {
  EXPECT_TRUE(BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 5, 0) >
              BluetoothAudioHalVersion::VERSION_AIDL_V4);
  EXPECT_FALSE(BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 5, 0)
                   .isHIDL());
  EXPECT_TRUE(BluetoothAudioHalVersion(BluetoothAudioHalTransport::AIDL, 5, 0)
                  .isAIDL());
}

/*
 * Copyright 2022 The Android Open Source Project
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

#include "a2dp_vendor_ldac.h"
#include "a2dp_vendor_opus.h"

// namespace
namespace bluetooth {
namespace testing {

class A2DPVendorRegressionTests : public ::testing::Test {
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

// regression test for b/259704719
TEST_F(A2DPVendorRegressionTests, OOB_In_A2DP_VendorBuildCodecHeaderLdac) {
  BT_HDR hdr{};
  hdr.len = sizeof(BT_HDR);
  A2DP_VendorBuildCodecHeaderLdac(nullptr, &hdr, 0);
}

// regression test for b/259704719
TEST_F(A2DPVendorRegressionTests, OOB_In_A2DP_VendorBuildCodecHeaderOpus) {
  BT_HDR hdr{};
  hdr.len = sizeof(BT_HDR);
  A2DP_VendorBuildCodecHeaderOpus(nullptr, &hdr, 0);
}

}  // namespace testing
}  // namespace bluetooth

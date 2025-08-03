/******************************************************************************
 *
 *  Copyright 2021 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <base/logging.h>
#include <gtest/gtest.h>

#include <memory>

#include "bta/hf_client/bta_hf_client_int.h"
#include "bta/include/bta_hf_client_api.h"
#include "test/fake/fake_osi.h"

class BtaHfClientAddRecordTest : public ::testing::Test {
 protected:
  void SetUp() override { fake_osi_ = std::make_unique<test::fake::FakeOsi>(); }

  void TearDown() override {}
  std::unique_ptr<test::fake::FakeOsi> fake_osi_;
};

TEST_F(BtaHfClientAddRecordTest, test_hf_client_add_record) {
  tBTA_HF_CLIENT_FEAT features = get_default_hf_client_features();
  uint32_t sdp_handle = 0;
  uint8_t scn = 0;

  bta_hf_client_add_record("Handsfree", scn, features, sdp_handle);
  ASSERT_EQ(HFP_VERSION_1_7, get_default_hfp_version());
}


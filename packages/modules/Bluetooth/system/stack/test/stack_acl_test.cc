/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0(the "License");
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
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>

#include "common/init_flags.h"
#include "hci/controller_interface_mock.h"
#include "stack/acl/acl.h"
#include "stack/btm/btm_int_types.h"
#include "stack/btm/security_device_record.h"
#include "stack/include/acl_api.h"
#include "stack/include/acl_hci_link_interface.h"
#include "stack/include/hcidefs.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_main_shim_entry.h"
#include "types/hci_role.h"
#include "types/raw_address.h"

tBTM_CB btm_cb;

namespace {
const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

const RawAddress kRawAddress = RawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
}  // namespace

namespace bluetooth {
namespace testing {

std::set<const RawAddress> copy_of_connected_with_both_public_and_random_set();

}  // namespace testing
}  // namespace bluetooth

class StackAclTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    bluetooth::common::InitFlags::Load(test_flags);
    bluetooth::hci::testing::mock_controller_ = &controller_;
  }
  void TearDown() override {
    bluetooth::hci::testing::mock_controller_ = nullptr;
  }

  tBTM_SEC_DEV_REC device_record_;
  bluetooth::hci::testing::MockControllerInterface controller_;
};

TEST_F(StackAclTest, nop) {}

TEST_F(StackAclTest, acl_process_extended_features) {
  const uint16_t hci_handle = 0x123;
  const tBT_TRANSPORT transport = BT_TRANSPORT_LE;
  const tHCI_ROLE link_role = HCI_ROLE_CENTRAL;

  btm_acl_created(kRawAddress, hci_handle, link_role, transport);
  tACL_CONN* p_acl = btm_acl_for_bda(kRawAddress, transport);
  ASSERT_NE(nullptr, p_acl);

  // Handle typical case
  {
    const uint8_t max_page = 3;
    memset((void*)p_acl->peer_lmp_feature_valid, 0,
           HCI_EXT_FEATURES_PAGE_MAX + 1);
    acl_process_extended_features(hci_handle, 1, max_page, 0xf123456789abcde);
    acl_process_extended_features(hci_handle, 2, max_page, 0xef123456789abcd);
    acl_process_extended_features(hci_handle, 3, max_page, 0xdef123456789abc);

    /* page 0 is the standard feature set */
    ASSERT_FALSE(p_acl->peer_lmp_feature_valid[0]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[1]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[2]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[3]);
  }

  // Handle extreme case
  {
    const uint8_t max_page = 255;
    memset((void*)p_acl->peer_lmp_feature_valid, 0,
           HCI_EXT_FEATURES_PAGE_MAX + 1);
    for (int i = 1; i < HCI_EXT_FEATURES_PAGE_MAX + 1; i++) {
      acl_process_extended_features(hci_handle, static_cast<uint8_t>(i),
                                    max_page, 0x123456789abcdef);
    }
    /* page 0 is the standard feature set */
    ASSERT_FALSE(p_acl->peer_lmp_feature_valid[0]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[1]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[2]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[3]);
  }

  // Handle case where device returns max page of zero
  {
    memset((void*)p_acl->peer_lmp_feature_valid, 0,
           HCI_EXT_FEATURES_PAGE_MAX + 1);
    acl_process_extended_features(hci_handle, 1, 0, 0xdef123456789abc);
    ASSERT_FALSE(p_acl->peer_lmp_feature_valid[0]);
    ASSERT_TRUE(p_acl->peer_lmp_feature_valid[1]);
    ASSERT_FALSE(p_acl->peer_lmp_feature_valid[2]);
    ASSERT_FALSE(p_acl->peer_lmp_feature_valid[3]);
  }

  btm_acl_removed(hci_handle);
}

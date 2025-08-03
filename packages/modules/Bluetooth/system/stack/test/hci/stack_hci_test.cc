/*
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
 */

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstring>
#include <utility>

#include "stack/include/hcidefs.h"
#include "stack/include/l2cdefs.h"
#include "test/common/mock_functions.h"

using testing::_;
using testing::DoAll;
using testing::NotNull;
using testing::Pointee;
using testing::Return;
using testing::SaveArg;
using testing::SaveArgPointee;
using testing::StrEq;
using testing::StrictMock;
using testing::Test;

class StackHciTest : public Test {
 public:
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
  void TearDown() override {}
};

TEST_F(StackHciTest, hci_preamble) {
  {
    HciDataPreamble preamble;

    ASSERT_EQ(sizeof(preamble), HCI_DATA_PREAMBLE_SIZE);

    preamble.bits.handle = 0xfff;
    preamble.bits.boundary = 0x3;
    preamble.bits.broadcast = 0x1;
    preamble.bits.unused15 = 0x0;
    preamble.bits.length = 0xffff;

    ASSERT_EQ(0x7fff, preamble.raw.word0);
    ASSERT_EQ(0xffff, preamble.raw.word1);

    const uint8_t exp[] = {0xff, 0x7f, 0xff, 0xff};
    uint8_t act[sizeof(preamble)];
    preamble.Serialize(act);
    ASSERT_EQ(0, std::memcmp(exp, act, sizeof(preamble)));
  }

  {
    HciDataPreamble preamble;
    preamble.raw.word0 =
        0x123 | (L2CAP_PKT_START_NON_FLUSHABLE << L2CAP_PKT_TYPE_SHIFT);
    preamble.raw.word1 = 0x4567;

    ASSERT_EQ(sizeof(preamble), HCI_DATA_PREAMBLE_SIZE);

    ASSERT_EQ(0x0123, preamble.raw.word0);
    ASSERT_EQ(0x4567, preamble.raw.word1);

    const uint8_t exp[] = {0x23, 0x01, 0x67, 0x45};
    uint8_t act[sizeof(preamble)];
    preamble.Serialize(act);
    ASSERT_EQ(0, std::memcmp(exp, act, sizeof(preamble)));
  }
  {
    HciDataPreamble preamble;
    preamble.raw.word0 = 0x123 | (L2CAP_PKT_START << L2CAP_PKT_TYPE_SHIFT);
    preamble.raw.word1 = 0x4567;

    ASSERT_EQ(sizeof(preamble), HCI_DATA_PREAMBLE_SIZE);

    ASSERT_EQ(0x2123, preamble.raw.word0);
    ASSERT_EQ(0x4567, preamble.raw.word1);

    const uint8_t exp[] = {0x23, 0x21, 0x67, 0x45};
    uint8_t act[sizeof(preamble)];
    preamble.Serialize(act);
    ASSERT_EQ(0, std::memcmp(exp, act, sizeof(preamble)));
  }

  {
    HciDataPreamble preamble;
    preamble.raw.word0 = 0x0 | (L2CAP_PKT_START << L2CAP_PKT_TYPE_SHIFT);
    preamble.raw.word1 = 0x0;

    ASSERT_EQ(sizeof(preamble), HCI_DATA_PREAMBLE_SIZE);

    ASSERT_EQ(0x2000, preamble.raw.word0);
    ASSERT_EQ(0x0000, preamble.raw.word1);

    const uint8_t exp[] = {0x00, 0x20, 0x00, 0x00};
    uint8_t act[sizeof(preamble)];
    preamble.Serialize(act);
    ASSERT_EQ(0, std::memcmp(exp, act, sizeof(preamble)));
  }

  {
    HciDataPreamble preamble;
    preamble.raw.word0 = 0x0 | (L2CAP_PKT_START << L2CAP_PKT_TYPE_SHIFT);
    preamble.raw.word1 = 0x0;

    ASSERT_TRUE(preamble.IsFlushable());

    preamble.raw.word0 =
        0x0 | (L2CAP_PKT_START << L2CAP_PKT_START_NON_FLUSHABLE);
    ASSERT_TRUE(!preamble.IsFlushable());
  }
}

TEST_F(StackHciTest, hci_error_code_text) {
  std::vector<std::pair<tHCI_ERROR_CODE, std::string>> errors = {
      std::make_pair(HCI_SUCCESS, "HCI_SUCCESS"),
      std::make_pair(HCI_ERR_ILLEGAL_COMMAND, "HCI_ERR_ILLEGAL_COMMAND"),
      std::make_pair(HCI_ERR_NO_CONNECTION, "HCI_ERR_NO_CONNECTION"),
      std::make_pair(HCI_ERR_HW_FAILURE, "HCI_ERR_HW_FAILURE"),
      std::make_pair(HCI_ERR_PAGE_TIMEOUT, "HCI_ERR_PAGE_TIMEOUT"),
      std::make_pair(HCI_ERR_AUTH_FAILURE, "HCI_ERR_AUTH_FAILURE"),
      std::make_pair(HCI_ERR_KEY_MISSING, "HCI_ERR_KEY_MISSING"),
      std::make_pair(HCI_ERR_MEMORY_FULL, "HCI_ERR_MEMORY_FULL"),
      std::make_pair(HCI_ERR_CONNECTION_TOUT, "HCI_ERR_CONNECTION_TOUT"),
      std::make_pair(HCI_ERR_MAX_NUM_OF_CONNECTIONS,
                     "HCI_ERR_MAX_NUM_OF_CONNECTIONS"),
      std::make_pair(HCI_ERR_MAX_NUM_OF_SCOS, "HCI_ERR_MAX_NUM_OF_SCOS"),
      std::make_pair(HCI_ERR_CONNECTION_EXISTS, "HCI_ERR_CONNECTION_EXISTS"),
      std::make_pair(HCI_ERR_COMMAND_DISALLOWED, "HCI_ERR_COMMAND_DISALLOWED"),
      std::make_pair(HCI_ERR_HOST_REJECT_RESOURCES,
                     "HCI_ERR_HOST_REJECT_RESOURCES"),
      std::make_pair(HCI_ERR_HOST_REJECT_SECURITY,
                     "HCI_ERR_HOST_REJECT_SECURITY"),
      std::make_pair(HCI_ERR_HOST_REJECT_DEVICE, "HCI_ERR_HOST_REJECT_DEVICE"),
      std::make_pair(HCI_ERR_HOST_TIMEOUT, "HCI_ERR_HOST_TIMEOUT"),
      std::make_pair(HCI_ERR_ILLEGAL_PARAMETER_FMT,
                     "HCI_ERR_ILLEGAL_PARAMETER_FMT"),
      std::make_pair(HCI_ERR_PEER_USER, "HCI_ERR_PEER_USER"),
      std::make_pair(HCI_ERR_REMOTE_LOW_RESOURCE,
                     "HCI_ERR_REMOTE_LOW_RESOURCE"),
      std::make_pair(HCI_ERR_REMOTE_POWER_OFF, "HCI_ERR_REMOTE_POWER_OFF"),
      std::make_pair(HCI_ERR_CONN_CAUSE_LOCAL_HOST,
                     "HCI_ERR_CONN_CAUSE_LOCAL_HOST"),
      std::make_pair(HCI_ERR_REPEATED_ATTEMPTS, "HCI_ERR_REPEATED_ATTEMPTS"),
      std::make_pair(HCI_ERR_PAIRING_NOT_ALLOWED,
                     "HCI_ERR_PAIRING_NOT_ALLOWED"),
      std::make_pair(HCI_ERR_UNSUPPORTED_REM_FEATURE,
                     "HCI_ERR_UNSUPPORTED_REM_FEATURE"),
      std::make_pair(HCI_ERR_UNSPECIFIED, "HCI_ERR_UNSPECIFIED"),
      std::make_pair(HCI_ERR_LMP_RESPONSE_TIMEOUT,
                     "HCI_ERR_LMP_RESPONSE_TIMEOUT"),
      std::make_pair(HCI_ERR_LMP_ERR_TRANS_COLLISION,
                     "HCI_ERR_LMP_ERR_TRANS_COLLISION"),
      std::make_pair(HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE,
                     "HCI_ERR_ENCRY_MODE_NOT_ACCEPTABLE"),
      std::make_pair(HCI_ERR_UNIT_KEY_USED, "HCI_ERR_UNIT_KEY_USED"),
      std::make_pair(HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED,
                     "HCI_ERR_PAIRING_WITH_UNIT_KEY_NOT_SUPPORTED"),
      std::make_pair(HCI_ERR_DIFF_TRANSACTION_COLLISION,
                     "HCI_ERR_DIFF_TRANSACTION_COLLISION"),
      std::make_pair(HCI_ERR_INSUFFCIENT_SECURITY,
                     "HCI_ERR_INSUFFCIENT_SECURITY"),
      std::make_pair(HCI_ERR_ROLE_SWITCH_PENDING,
                     "HCI_ERR_ROLE_SWITCH_PENDING"),
      std::make_pair(HCI_ERR_ROLE_SWITCH_FAILED, "HCI_ERR_ROLE_SWITCH_FAILED"),
      std::make_pair(HCI_ERR_HOST_BUSY_PAIRING, "HCI_ERR_HOST_BUSY_PAIRING"),
      std::make_pair(HCI_ERR_UNACCEPT_CONN_INTERVAL,
                     "HCI_ERR_UNACCEPT_CONN_INTERVAL"),
      std::make_pair(HCI_ERR_ADVERTISING_TIMEOUT,
                     "HCI_ERR_ADVERTISING_TIMEOUT"),
      std::make_pair(HCI_ERR_CONN_FAILED_ESTABLISHMENT,
                     "HCI_ERR_CONN_FAILED_ESTABLISHMENT"),
      std::make_pair(HCI_ERR_LIMIT_REACHED, "HCI_ERR_LIMIT_REACHED"),
  };
  for (const auto& error : errors) {
    ASSERT_STREQ(error.second.c_str(),
                 hci_error_code_text(error.first).c_str());
  }
  for (const auto& error : errors) {
    ASSERT_STREQ(error.second.c_str(),
                 hci_error_code_text(error.first).c_str());
  }
  auto unknown = base::StringPrintf("UNKNOWN[0x%02hx]",
                                    std::numeric_limits<std::uint8_t>::max());
  ASSERT_STREQ(
      unknown.c_str(),
      hci_error_code_text(static_cast<tHCI_ERROR_CODE>(
                              std::numeric_limits<std::uint8_t>::max()))
          .c_str());
}

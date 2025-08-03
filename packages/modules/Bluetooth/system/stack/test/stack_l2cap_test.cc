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

#include "common/init_flags.h"
#include "device/include/controller.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/l2cap_controller_interface.h"
#include "stack/include/l2cap_hci_link_interface.h"
#include "stack/include/l2cdefs.h"
#include "stack/l2cap/l2c_int.h"

tBTM_CB btm_cb;
extern tL2C_CB l2cb;

void l2c_link_send_to_lower_br_edr(tL2C_LCB* p_lcb, BT_HDR* p_buf);
void l2c_link_send_to_lower_ble(tL2C_LCB* p_lcb, BT_HDR* p_buf);

namespace {
constexpr uint16_t kAclBufferCountClassic = 123;
constexpr uint8_t kAclBufferCountBle = 45;

}  // namespace

class StackL2capTest : public ::testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::SetAllForTesting();
    controller_.get_acl_buffer_count_classic = []() {
      return kAclBufferCountClassic;
    };
    controller_.get_acl_buffer_count_ble = []() { return kAclBufferCountBle; };
    controller_.SupportsBle = []() -> bool { return true; };
    l2c_init();
  }

  void TearDown() override {
    l2c_free();
    controller_ = {};
  }

  controller_t controller_;
};

TEST_F(StackL2capTest, l2cble_process_data_length_change_event) {
  l2cb.lcb_pool[0].tx_data_len = 0xdead;

  // ACL unknown and legal inputs
  l2cble_process_data_length_change_event(0x1234, 0x001b, 0x001b);
  ASSERT_EQ(0xdead, l2cb.lcb_pool[0].tx_data_len);

  l2cb.lcb_pool[0].in_use = true;
  l2cu_set_lcb_handle(l2cb.lcb_pool[0], 0x1234);
  ASSERT_EQ(0x1234, l2cb.lcb_pool[0].Handle());

  // ACL known and illegal inputs
  l2cble_process_data_length_change_event(0x1234, 1, 1);
  ASSERT_EQ(0xdead, l2cb.lcb_pool[0].tx_data_len);

  // ACL known and legal inputs
  l2cble_process_data_length_change_event(0x1234, 0x001b, 0x001b);
  ASSERT_EQ(0x001b, l2cb.lcb_pool[0].tx_data_len);
}

class StackL2capChannelTest : public StackL2capTest {
 protected:
  void SetUp() override { StackL2capTest::SetUp(); }

  void TearDown() override { StackL2capTest::TearDown(); }

  tL2C_CCB ccb_ = {
      .in_use = true,
      .chnl_state = CST_OPEN,  // tL2C_CHNL_STATE
      .local_conn_cfg =
          {
              // tL2CAP_LE_CFG_INFO
              .result = 0,
              .mtu = 100,
              .mps = 100,
              .credits = L2CA_LeCreditDefault(),
              .number_of_channels = L2CAP_CREDIT_BASED_MAX_CIDS,
          },
      .peer_conn_cfg =
          {
              // tL2CAP_LE_CFG_INFO
              .result = 0,
              .mtu = 100,
              .mps = 100,
              .credits = L2CA_LeCreditDefault(),
              .number_of_channels = L2CAP_CREDIT_BASED_MAX_CIDS,
          },
      .is_first_seg = false,
      .ble_sdu = nullptr,     // BT_HDR*; Buffer for storing unassembled sdu
      .ble_sdu_length = 0,    /* Length of unassembled sdu length*/
      .p_next_ccb = nullptr,  // struct t_l2c_ccb* Next CCB in the chain
      .p_prev_ccb = nullptr,  // struct t_l2c_ccb* Previous CCB in the chain
      .p_lcb = nullptr,  // struct t_l2c_linkcb* Link this CCB is assigned to
      .local_cid = 40,
      .remote_cid = 80,
      .l2c_ccb_timer = nullptr,  // alarm_t* CCB Timer Entry
      .p_rcb = nullptr,          // tL2C_RCB* Registration CB for this Channel
      .config_done = 0,          // Configuration flag word
      .remote_config_rsp_result = 0,  // The config rsp result from remote
      .local_id = 12,                 // Transaction ID for local trans
      .remote_id = 22,                // Transaction ID for local
      .flags = 0,
      .connection_initiator = false,
      .our_cfg = {},   // tL2CAP_CFG_INFO Our saved configuration options
      .peer_cfg = {},  // tL2CAP_CFG_INFO Peer's saved configuration options
      .xmit_hold_q = nullptr,  // fixed_queue_t*  Transmit data hold queue
      .cong_sent = false,
      .buff_quota = 0,

      .ccb_priority =
          L2CAP_CHNL_PRIORITY_HIGH,  // tL2CAP_CHNL_PRIORITY Channel priority
      .tx_data_rate = 0,  // tL2CAP_CHNL_PRIORITY  Channel Tx data rate
      .rx_data_rate = 0,  // tL2CAP_CHNL_PRIORITY  Channel Rx data rate

      .ertm_info =
          {
              // .tL2CAP_ERTM_INFO
              .preferred_mode = 0,
          },
      .fcrb =
          {
              // tL2C_FCRB
              .next_tx_seq = 0,
              .last_rx_ack = 0,
              .next_seq_expected = 0,
              .last_ack_sent = 0,
              .num_tries = 0,
              .max_held_acks = 0,
              .remote_busy = false,
              .rej_sent = false,
              .srej_sent = false,
              .wait_ack = false,
              .rej_after_srej = false,
              .send_f_rsp = false,
              .rx_sdu_len = 0,
              .p_rx_sdu =
                  nullptr,  // BT_HDR* Buffer holding the SDU being received
              .waiting_for_ack_q = nullptr,  // fixed_queue_t*
              .srej_rcv_hold_q = nullptr,    // fixed_queue_t*
              .retrans_q = nullptr,          // fixed_queue_t*
              .ack_timer = nullptr,          // alarm_t*
              .mon_retrans_timer = nullptr,  // alarm_t*
          },
      .tx_mps = 0,
      .max_rx_mtu = 0,
      .fcr_cfg_tries = 0,
      .peer_cfg_already_rejected = false,
      .out_cfg_fcr_present = false,
      .is_flushable = false,
      .fixed_chnl_idle_tout = 0,
      .tx_data_len = 0,
      .remote_credit_count = 0,
      .ecoc = false,
      .reconfig_started = false,
      .metrics = {},
  };
};

TEST_F(StackL2capChannelTest, l2c_lcc_proc_pdu__FirstSegment) {
  ccb_.is_first_seg = true;

  BT_HDR* p_buf = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + 32);
  p_buf->len = 32;

  l2c_lcc_proc_pdu(&ccb_, p_buf);
}

TEST_F(StackL2capChannelTest, l2c_lcc_proc_pdu__NextSegment) {
  BT_HDR* p_buf = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + 32);
  p_buf->len = 32;

  l2c_lcc_proc_pdu(&ccb_, p_buf);
}

TEST_F(StackL2capChannelTest, l2c_link_init) {
  l2cb.num_lm_acl_bufs = 0;
  l2cb.controller_xmit_window = 0;

  l2c_link_init(controller_.get_acl_buffer_count_classic());

  ASSERT_EQ(controller_.get_acl_buffer_count_classic(), l2cb.num_lm_acl_bufs);
  ASSERT_EQ(controller_.get_acl_buffer_count_classic(),
            l2cb.controller_xmit_window);
}

TEST_F(StackL2capTest, l2cap_result_code_text) {
  std::vector<std::pair<tL2CAP_CONN, std::string>> results = {
      std::make_pair(L2CAP_CONN_OK, "L2CAP_CONN_OK"),
      std::make_pair(L2CAP_CONN_PENDING, "L2CAP_CONN_PENDING"),
      std::make_pair(L2CAP_CONN_NO_PSM, "L2CAP_CONN_NO_PSM"),
      std::make_pair(L2CAP_CONN_SECURITY_BLOCK, "L2CAP_CONN_SECURITY_BLOCK"),
      std::make_pair(L2CAP_CONN_NO_RESOURCES, "L2CAP_CONN_NO_RESOURCES"),
      std::make_pair(L2CAP_CONN_TIMEOUT, "L2CAP_CONN_TIMEOUT"),
      std::make_pair(L2CAP_CONN_OTHER_ERROR, "L2CAP_CONN_OTHER_ERROR"),
      std::make_pair(L2CAP_CONN_ACL_CONNECTION_FAILED,
                     "L2CAP_CONN_ACL_CONNECTION_FAILED"),
      std::make_pair(L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED,
                     "L2CAP_CONN_CLIENT_SECURITY_CLEARANCE_FAILED"),
      std::make_pair(L2CAP_CONN_NO_LINK, "L2CAP_CONN_NO_LINK"),
      std::make_pair(L2CAP_CONN_CANCEL, "L2CAP_CONN_CANCEL"),
      std::make_pair(L2CAP_CONN_INSUFFICIENT_AUTHENTICATION,
                     "L2CAP_CONN_INSUFFICIENT_AUTHENTICATION"),
      std::make_pair(L2CAP_CONN_INSUFFICIENT_AUTHORIZATION,
                     "L2CAP_CONN_INSUFFICIENT_AUTHORIZATION"),
      std::make_pair(L2CAP_CONN_INSUFFICIENT_ENCRYP_KEY_SIZE,
                     "L2CAP_CONN_INSUFFICIENT_ENCRYP_KEY_SIZE"),
      std::make_pair(L2CAP_CONN_INSUFFICIENT_ENCRYP,
                     "L2CAP_CONN_INSUFFICIENT_ENCRYP"),
      std::make_pair(L2CAP_CONN_INVALID_SOURCE_CID,
                     "L2CAP_CONN_INVALID_SOURCE_CID"),
      std::make_pair(L2CAP_CONN_SOURCE_CID_ALREADY_ALLOCATED,
                     "L2CAP_CONN_SOURCE_CID_ALREADY_ALLOCATED"),
      std::make_pair(L2CAP_CONN_UNACCEPTABLE_PARAMETERS,
                     "L2CAP_CONN_UNACCEPTABLE_PARAMETERS"),
      std::make_pair(L2CAP_CONN_INVALID_PARAMETERS,
                     "L2CAP_CONN_INVALID_PARAMETERS"),
  };
  for (const auto& result : results) {
    ASSERT_STREQ(result.second.c_str(),
                 l2cap_result_code_text(result.first).c_str());
  }
  std::ostringstream oss;
  oss << "UNKNOWN[" << std::numeric_limits<std::uint16_t>::max() << "]";
  ASSERT_STREQ(
      oss.str().c_str(),
      l2cap_result_code_text(
          static_cast<tL2CAP_CONN>(std::numeric_limits<std::uint16_t>::max()))
          .c_str());
}

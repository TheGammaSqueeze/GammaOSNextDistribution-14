/*
 * Copyright 2020 The Android Open Source Project
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

//#include <dlfcn.h>
#include <gtest/gtest.h>
#include <sys/types.h>

#include <cstdint>
#include <cstring>

#include "osi/include/allocator.h"
#include "stack/avdt/avdt_int.h"
#include "stack/include/avdt_api.h"
#include "stack/test/common/mock_stack_avdt_msg.h"
#include "test/common/mock_functions.h"
#include "types/raw_address.h"

#ifndef UNUSED_ATTR
#define UNUSED_ATTR
#endif

class StackAvdtpTest : public ::testing::Test {
 protected:
  StackAvdtpTest() = default;

  virtual ~StackAvdtpTest() = default;
 protected:
  static AvdtpRcb reg_ctrl_block_;
  static uint8_t callback_event_;
  static uint8_t scb_handle_;

 protected:
  static void AvdtConnCallback(uint8_t handle, const RawAddress& bd_addr,
                               uint8_t event, tAVDT_CTRL* p_data,
                               uint8_t scb_index) {
    inc_func_call_count(__func__);
    callback_event_ = event;
  }

  static void StreamCtrlCallback(uint8_t handle, const RawAddress& bd_addr,
                                 uint8_t event, tAVDT_CTRL* p_data,
                                 uint8_t scb_index) {
    inc_func_call_count(__func__);
    callback_event_ = event;
  }

  static void AvdtReportCallback(uint8_t handle, AVDT_REPORT_TYPE type,
                                 tAVDT_REPORT_DATA* p_data) {
    inc_func_call_count(__func__);
  }

  static void SetUpTestCase() {
    reg_ctrl_block_.ctrl_mtu = 672;
    reg_ctrl_block_.ret_tout = 4;
    reg_ctrl_block_.sig_tout = 4;
    reg_ctrl_block_.idle_tout = 10;
    reg_ctrl_block_.scb_index = 0;
    AVDT_Register(&reg_ctrl_block_, AvdtConnCallback);

    uint8_t peer_id = 1;
    scb_handle_ = 0;
    AvdtpStreamConfig avdtp_stream_config{};
    avdtp_stream_config.cfg.psc_mask = AVDT_PSC_DELAY_RPT;
    avdtp_stream_config.p_avdt_ctrl_cback = StreamCtrlCallback;
    avdtp_stream_config.p_report_cback = AvdtReportCallback;
    avdtp_stream_config.tsep = AVDT_TSEP_SNK;
    // We have to reuse the stream since there is only AVDT_NUM_SEPS *
    // AVDT_NUM_LINKS
    ASSERT_EQ(AVDT_CreateStream(peer_id, &scb_handle_, avdtp_stream_config), AVDT_SUCCESS);
  }

  static void TearDownTestCase() { AVDT_Deregister(); }

  void SetUp() override {
    callback_event_ = AVDT_MAX_EVT + 1;
    reset_mock_function_count_map();
  }

  void TearDown() override {
    auto pscb = avdt_scb_by_hdl(scb_handle_);
    tAVDT_SCB_EVT data;
    // clean up the SCB state
    avdt_scb_event(pscb, AVDT_SCB_MSG_ABORT_RSP_EVT, &data);
    avdt_scb_event(pscb, AVDT_SCB_TC_CLOSE_EVT, &data);
    ASSERT_EQ(AVDT_RemoveStream(scb_handle_), AVDT_SUCCESS);
    // fallback to default settings (delay report + sink)
    pscb->stream_config.cfg.psc_mask = AVDT_PSC_DELAY_RPT;
    pscb->stream_config.tsep = AVDT_TSEP_SNK;
  }
};

AvdtpRcb StackAvdtpTest::reg_ctrl_block_{};
uint8_t StackAvdtpTest::callback_event_ = AVDT_MAX_EVT + 1;
uint8_t StackAvdtpTest::scb_handle_ = 0;

TEST_F(StackAvdtpTest, test_delay_report_as_accept) {
  // Get SCB ready to send response
  auto pscb = avdt_scb_by_hdl(scb_handle_);
  pscb->in_use = true;

  // Send SetConfig response
  uint8_t label = 0;
  uint8_t err_code = 0;
  uint8_t category = 0;

  mock_avdt_msg_send_cmd_clear_history();
  mock_avdt_msg_send_rsp_clear_history();
  ASSERT_EQ(AVDT_ConfigRsp(scb_handle_, label, err_code, category), AVDT_SUCCESS);

  // Config response sent
  ASSERT_EQ(get_func_call_count("avdt_msg_send_rsp"), 1);
  ASSERT_EQ(mock_avdt_msg_send_rsp_get_sig_id_at(0), AVDT_SIG_SETCONFIG);

  // Delay report command sent
  ASSERT_EQ(get_func_call_count("avdt_msg_send_cmd"), 1);
  ASSERT_EQ(mock_avdt_msg_send_cmd_get_sig_id_at(0), AVDT_SIG_DELAY_RPT);

  // Delay report confirmed
  tAVDT_SCB_EVT data;
  ASSERT_EQ(get_func_call_count("StreamCtrlCallback"), 0);
  avdt_scb_hdl_delay_rpt_rsp(pscb, &data);
  ASSERT_EQ(callback_event_, AVDT_DELAY_REPORT_CFM_EVT);
}

TEST_F(StackAvdtpTest, test_no_delay_report_if_not_sink) {
  // Get SCB ready to send response
  auto pscb = avdt_scb_by_hdl(scb_handle_);
  pscb->in_use = true;

  // Change the scb to SRC
  pscb->stream_config.tsep = AVDT_TSEP_SRC;

  // Send SetConfig response
  uint8_t label = 0;
  uint8_t err_code = 0;
  uint8_t category = 0;
  ASSERT_EQ(AVDT_ConfigRsp(scb_handle_, label, err_code, category), AVDT_SUCCESS);
  ASSERT_EQ(get_func_call_count("avdt_msg_send_rsp"),
            1);  // Config response sent
  ASSERT_EQ(get_func_call_count("avdt_msg_send_cmd"),
            0);  // Delay report command not sent
}

TEST_F(StackAvdtpTest, test_no_delay_report_if_not_enabled) {
  // Get SCB ready to send response
  auto pscb = avdt_scb_by_hdl(scb_handle_);
  pscb->in_use = true;

  // Disable the scb's delay report mask
  pscb->stream_config.cfg.psc_mask &= ~AVDT_PSC_DELAY_RPT;

  // Send SetConfig response
  uint8_t label = 0;
  uint8_t err_code = 0;
  uint8_t category = 0;
  ASSERT_EQ(AVDT_ConfigRsp(scb_handle_, label, err_code, category), AVDT_SUCCESS);
  ASSERT_EQ(get_func_call_count("avdt_msg_send_rsp"),
            1);  // Config response sent
  ASSERT_EQ(get_func_call_count("avdt_msg_send_cmd"),
            0);  // Delay report command not sent
}

TEST_F(StackAvdtpTest, test_delay_report_as_init) {
  auto pscb = avdt_scb_by_hdl(scb_handle_);
  pscb->in_use = true;

  tAVDT_SCB_EVT data;

  // Delay report -> Open command
  mock_avdt_msg_send_cmd_clear_history();
  avdt_scb_event(pscb, AVDT_SCB_MSG_SETCONFIG_RSP_EVT, &data);
  ASSERT_EQ(get_func_call_count("avdt_msg_send_cmd"), 2);
  ASSERT_EQ(mock_avdt_msg_send_cmd_get_sig_id_at(0), AVDT_SIG_DELAY_RPT);
  ASSERT_EQ(mock_avdt_msg_send_cmd_get_sig_id_at(1), AVDT_SIG_OPEN);
}

TEST_F(StackAvdtpTest, test_SR_reporting_handler) {
  constexpr uint8_t sender_report_packet[] = {
      // Header
      0x80, 0xc8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // Sender Info
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // Report Block #1
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
  uint16_t packet_length = sizeof(sender_report_packet);
  tAVDT_SCB_EVT data;
  auto pscb = avdt_scb_by_hdl(scb_handle_);

  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = packet_length, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, sender_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // no payload
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, sender_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // only reporting header
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 8, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, sender_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // reporting header + sender info
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 28, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, sender_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 2);
}

TEST_F(StackAvdtpTest, test_RR_reporting_handler) {
  constexpr uint8_t receiver_report_packet[] = {
      // Header
      0x80, 0xc9, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      // Report Block #1
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
  uint16_t packet_length = sizeof(receiver_report_packet);
  tAVDT_SCB_EVT data;
  auto pscb = avdt_scb_by_hdl(scb_handle_);

  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = packet_length, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, receiver_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // no payload
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, receiver_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // only reporting header
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 8, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, receiver_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // reporting header + report block
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 32, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, receiver_report_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 2);
}

TEST_F(StackAvdtpTest, test_SDES_reporting_handler) {
  constexpr uint8_t source_description_packet[] = {// Header
                                                   0x80, 0xca, 0x00, 0x00,
                                                   // Chunk #1
                                                   0x00, 0x00, 0x00, 0x00,
                                                   // SDES Item (CNAME=1)
                                                   0x01, 0x05, 0x00, 0x00, 0x00,
                                                   0x00, 0x00, 0x00};
  uint16_t packet_length = sizeof(source_description_packet);
  tAVDT_SCB_EVT data;
  auto pscb = avdt_scb_by_hdl(scb_handle_);

  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = packet_length, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, source_description_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // no payload
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, source_description_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // only reporting header
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 4, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, source_description_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // SDES Item (CNAME) with empty value
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 10, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, source_description_packet, packet_length);
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);

  // SDES Item (not CNAME) which is not supported
  data.p_pkt = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + packet_length);
  *data.p_pkt = {.len = 10, .layer_specific = AVDT_CHAN_REPORT};
  memcpy(data.p_pkt->data, source_description_packet, packet_length);
  *(data.p_pkt->data + 8) = 0x02;
  *(data.p_pkt->data + 9) = 0x00;
  avdt_scb_hdl_pkt(pscb, &data);
  ASSERT_EQ(get_func_call_count("AvdtReportCallback"), 1);
}

void avdt_scb_hdl_pkt_no_frag(AvdtpScb* p_scb, tAVDT_SCB_EVT* p_data);
// regression tests for b/258057241 (CVE-2022-40503)
// The regression tests are divided into 2 tests:
// avdt_scb_hdl_pkt_no_frag_regression_test1 verifies that
// OOB access resulted from integer overflow
// from the ex_len field in the packet is properly handled

TEST_F(StackAvdtpTest, avdt_scb_hdl_pkt_no_frag_regression_test0) {
  const uint16_t extra_size = 0;
  BT_HDR* p_pkt = (BT_HDR*)osi_malloc(sizeof(BT_HDR) + extra_size);
  ASSERT_NE(p_pkt, nullptr);
  tAVDT_SCB_EVT evt_data = {
      .p_pkt = p_pkt,
  };
  p_pkt->len = 0;

  // get the stream control block
  AvdtpScb* pscb = avdt_scb_by_hdl(scb_handle_);
  ASSERT_NE(pscb, nullptr);

  // any memory issue would be caught be the address sanitizer
  avdt_scb_hdl_pkt_no_frag(pscb, &evt_data);

  // here we would also assume that p_pkt would have been freed
  // by avdt_scb_hdl_pkt_no_frag by calling osi_free_and_reset
  // thus vt_data.p_pkt will be set to nullptr
  ASSERT_EQ(evt_data.p_pkt, nullptr);
}

TEST_F(StackAvdtpTest, avdt_scb_hdl_pkt_no_frag_regression_test1) {
  const uint16_t extra_size = 100;
  BT_HDR* p_pkt = (BT_HDR*)osi_malloc(sizeof(BT_HDR) + extra_size);
  ASSERT_NE(p_pkt, nullptr);
  tAVDT_SCB_EVT evt_data = {
      .p_pkt = p_pkt,
  };

  // setup p_pkt
  // no overflow here
  p_pkt->len = extra_size;
  p_pkt->offset = 0;

  uint8_t* p = (uint8_t*)(p_pkt + 1);
  // fill the p_pkt with 0xff to
  // make ex_len * 4 overflow
  memset(p, 0xff, extra_size);

  // get the stream control block
  AvdtpScb* pscb = avdt_scb_by_hdl(scb_handle_);
  ASSERT_NE(pscb, nullptr);

  // any memory issue would be caught be the address sanitizer
  avdt_scb_hdl_pkt_no_frag(pscb, &evt_data);

  // here we would also assume that p_pkt would have been freed
  // by avdt_scb_hdl_pkt_no_frag by calling osi_free_and_reset
  // thus vt_data.p_pkt will be set to nullptr
  ASSERT_EQ(evt_data.p_pkt, nullptr);
}

// avdt_scb_hdl_pkt_no_frag_regression_test2 verifies that
// OOB access resulted from integer overflow
// from the pad_len field in the packet is properly handled
TEST_F(StackAvdtpTest, avdt_scb_hdl_pkt_no_frag_regression_test2) {
  const uint16_t extra_size = 100;
  BT_HDR* p_pkt = (BT_HDR*)osi_malloc(sizeof(BT_HDR) + extra_size);
  ASSERT_NE(p_pkt, nullptr);
  tAVDT_SCB_EVT evt_data = {
      .p_pkt = p_pkt,
  };

  // setup p_pkt
  // no overflow here
  p_pkt->len = extra_size;
  p_pkt->offset = 0;

  uint8_t* p = (uint8_t*)(p_pkt + 1);
  // zero out all bytes first
  memset(p, 0, extra_size);
  // setup o_v, o_p, o_x, o_cc
  *p = 0xff;
  // set the pad_len to be 0xff
  p[extra_size - 1] = 0xff;

  // get the stream control block
  AvdtpScb* pscb = avdt_scb_by_hdl(scb_handle_);
  ASSERT_NE(pscb, nullptr);

  // any memory issue would be caught be the address sanitizer
  avdt_scb_hdl_pkt_no_frag(pscb, &evt_data);

  // here we would also assume that p_pkt would have been freed
  // by avdt_scb_hdl_pkt_no_frag by calling osi_free_and_reset
  // thus vt_data.p_pkt will be set to nullptr
  ASSERT_EQ(evt_data.p_pkt, nullptr);
}

// avdt_scb_hdl_pkt_no_frag_regression_test3 verifies that
// zero length packets are filtered out
TEST_F(StackAvdtpTest, avdt_scb_hdl_pkt_no_frag_regression_test3) {
  // 12 btyes of minimal + 15 * oc (4 bytes each) + 4 btye to ex_len
  const uint16_t extra_size = 12 + 15 * 4 + 4;
  BT_HDR* p_pkt = (BT_HDR*)osi_malloc(sizeof(BT_HDR) + extra_size);
  ASSERT_NE(p_pkt, nullptr);
  tAVDT_SCB_EVT evt_data = {
      .p_pkt = p_pkt,
  };

  // setup p_pkt
  // no overflow here
  p_pkt->len = extra_size;
  p_pkt->offset = 0;

  uint8_t* p = (uint8_t*)(p_pkt + 1);
  // fill the p_pkt with 0 to
  // make ex_len * 4 overflow
  memset(p, 0, extra_size);
  // setup
  // o_v = 0b10
  // o_p = 0b01 // with padding
  // o_x = 0b10
  // o_cc = 0b1111
  *p = 0xff;

  // get the stream control block
  AvdtpScb* pscb = avdt_scb_by_hdl(scb_handle_);
  ASSERT_NE(pscb, nullptr);

  // any memory issue would be caught be the address sanitizer
  avdt_scb_hdl_pkt_no_frag(pscb, &evt_data);

  // here we would also assume that p_pkt would have been freed
  // by avdt_scb_hdl_pkt_no_frag by calling osi_free_and_reset
  // thus vt_data.p_pkt will be set to nullptr
  ASSERT_EQ(evt_data.p_pkt, nullptr);
}

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

#include <fuzzer/FuzzedDataProvider.h>

#include <cstdint>
#include <functional>
#include <vector>

#include "osi/include/allocator.h"
#include "stack/include/bnep_api.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_btif_config.h"
#include "test/mock/mock_stack_acl.h"
#include "test/mock/mock_stack_btm_dev.h"
#include "test/mock/mock_stack_l2cap_api.h"
#include "test/mock/mock_stack_l2cap_ble.h"
#include "types/bluetooth/uuid.h"

using bluetooth::Uuid;

namespace {

constexpr uint16_t kDummyCid = 0x1234;
constexpr uint8_t kDummyId = 0x77;
constexpr uint8_t kDummyRemoteAddr[] = {0x77, 0x88, 0x99, 0xAA, 0xBB, 0xCC};
constexpr uint8_t kDummySrcUuid[] = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55,
                                     0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb,
                                     0xcc, 0xdd, 0xee, 0xff};
constexpr uint8_t kDummyDstUuid[] = {0x00, 0x00, 0x00, 0x00, 0x22, 0x22,
                                     0x22, 0x22, 0x33, 0x33, 0x55, 0x55,
                                     0x55, 0x55, 0x55, 0x59};

// Set up default callback structure
static tL2CAP_APPL_INFO appl_info;

class FakeBtStack {
 public:
  FakeBtStack() {
    test::mock::stack_l2cap_api::L2CA_DataWrite.body = [](uint16_t cid,
                                                          BT_HDR* p_data) {
      CHECK(cid == kDummyCid);
      osi_free(p_data);
      return L2CAP_DW_SUCCESS;
    };
    test::mock::stack_l2cap_api::L2CA_DisconnectReq.body = [](uint16_t cid) {
      CHECK(cid == kDummyCid);
      return true;
    };
    test::mock::stack_l2cap_api::L2CA_ConnectReq2.body =
        [](uint16_t psm, const RawAddress& p_bd_addr, uint16_t sec_level) {
          CHECK(p_bd_addr == kDummyRemoteAddr);
          return kDummyCid;
        };
    test::mock::stack_l2cap_api::L2CA_Register2.body =
        [](uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, bool enable_snoop,
           tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu,
           uint16_t required_remote_mtu, uint16_t sec_level) {
          appl_info = p_cb_info;
          return psm;
        };
    test::mock::stack_l2cap_api::L2CA_Deregister.body = [](uint16_t psm) {};
  }

  ~FakeBtStack() {
    test::mock::stack_l2cap_api::L2CA_DataWrite = {};
    test::mock::stack_l2cap_api::L2CA_ConnectReq2 = {};
    test::mock::stack_l2cap_api::L2CA_DisconnectReq = {};
    test::mock::stack_l2cap_api::L2CA_Register2 = {};
    test::mock::stack_l2cap_api::L2CA_Deregister = {};
  }
};

class Fakes {
 public:
  test::fake::FakeOsi fake_osi;
  FakeBtStack fake_stack;
};

}  // namespace

// Verify the passed data is readable
static void ConsumeData(const uint8_t* data, size_t size) {
  volatile uint8_t checksum = 0;
  for (size_t i = 0; i < size; i++) {
    checksum ^= data[i];
  }
}

static void Fuzz(const uint8_t* data, size_t size) {
  tBNEP_REGISTER reg = {
      .p_conn_ind_cb =
          [](uint16_t handle, const RawAddress& bd_addr,
             const bluetooth::Uuid& remote_uuid,
             const bluetooth::Uuid& local_uuid,
             bool is_role_change) { BNEP_ConnectResp(handle, BNEP_SUCCESS); },
      .p_conn_state_cb = [](uint16_t handle, const RawAddress& rem_bda,
                            tBNEP_RESULT result, bool is_role_change) {},
      .p_data_ind_cb = [](uint16_t handle, const RawAddress& src,
                          const RawAddress& dst, uint16_t protocol,
                          uint8_t* p_data, uint16_t len,
                          bool fw_ext_present) { ConsumeData(p_data, len); },
      .p_tx_data_flow_cb = [](uint16_t handle, tBNEP_RESULT event) {},
      .p_filter_ind_cb =
          [](uint16_t handle, bool indication, tBNEP_RESULT result,
             uint16_t num_filters,
             uint8_t* p_filters) { ConsumeData(p_filters, num_filters); },
      .p_mfilter_ind_cb =
          [](uint16_t handle, bool indication, tBNEP_RESULT result,
             uint16_t num_mfilters,
             uint8_t* p_mfilters) { ConsumeData(p_mfilters, num_mfilters); },
  };

  BNEP_Init();
  if (BNEP_SUCCESS != BNEP_Register(&reg)) {
    return;
  }

  FuzzedDataProvider fdp(data, size);
  bool is_server = fdp.ConsumeBool();
  if (is_server) {
    // Simulating an inbound connection event
    appl_info.pL2CA_ConnectInd_Cb(kDummyRemoteAddr, kDummyCid, 0, kDummyId);
  } else {
    // Initiating an outbound connection
    uint16_t handle;
    BNEP_Connect(kDummyRemoteAddr, Uuid::From128BitBE(kDummySrcUuid),
                 Uuid::From128BitBE(kDummyDstUuid), &handle, 0);

    // Simulating outbound connection confirm event
    appl_info.pL2CA_ConnectCfm_Cb(kDummyCid, L2CAP_CONN_OK);
  }

  // Simulating configuration confirmation event
  tL2CAP_CFG_INFO cfg = {};
  appl_info.pL2CA_ConfigCfm_Cb(kDummyCid, 0, &cfg);

  // Feeding input packets
  constexpr uint16_t kMaxPacketSize = 1024;
  while (fdp.remaining_bytes() > 0) {
    auto size = fdp.ConsumeIntegralInRange<uint16_t>(0, kMaxPacketSize);
    auto bytes = fdp.ConsumeBytes<uint8_t>(size);
    BT_HDR* hdr = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + bytes.size());
    hdr->len = bytes.size();
    std::copy(bytes.cbegin(), bytes.cend(), hdr->data);
    appl_info.pL2CA_DataInd_Cb(kDummyCid, hdr);
  }

  // Simulating disconnecting event
  appl_info.pL2CA_DisconnectInd_Cb(kDummyCid, false);

  BNEP_Deregister();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  auto fakes = std::make_unique<Fakes>();
  Fuzz(Data, Size);
  return 0;
}

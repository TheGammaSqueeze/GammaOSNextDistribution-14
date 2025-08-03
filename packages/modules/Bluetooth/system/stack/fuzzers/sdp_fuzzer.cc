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
#include <string>
#include <vector>

#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/sdpdefs.h"
#include "stack/sdp/internal/sdp_api.h"
#include "stack/sdp/sdpint.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_btif_config.h"
#include "test/mock/mock_stack_l2cap_api.h"
#include "types/bluetooth/uuid.h"

namespace {

#define SDP_DB_SIZE 0x10000

constexpr uint16_t kDummyCID = 0x1234;
constexpr uint16_t kDummyPSM = 0x7788;
constexpr uint8_t kDummyID = 0x99;
constexpr uint8_t kDummyAddr[] = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66};

// Set up default callback structure
tL2CAP_APPL_INFO cb_info = {
    .pL2CA_ConnectInd_Cb = [](const RawAddress& bd_addr, uint16_t lcid,
                              uint16_t psm,
                              uint8_t id) {},  // tL2CA_CONNECT_IND_CB
    .pL2CA_ConnectCfm_Cb = [](uint16_t lcid,
                              uint16_t result) {},  // tL2CA_CONNECT_CFM_CB
    .pL2CA_ConfigInd_Cb = [](uint16_t lcid,
                             tL2CAP_CFG_INFO* p_cfg) {},  // tL2CA_CONFIG_IND_CB
    .pL2CA_ConfigCfm_Cb = [](uint16_t lcid, uint16_t initiator,
                             tL2CAP_CFG_INFO* p_cfg) {},  // tL2CA_CONFIG_CFM_CB
    .pL2CA_DisconnectInd_Cb =
        [](uint16_t lcid, bool should_ack) {},  // tL2CA_DISCONNECT_IND_CB
    .pL2CA_DisconnectCfm_Cb =
        [](uint16_t lcid, uint16_t result) {},  // tL2CA_DISCONNECT_CFM_CB
    .pL2CA_DataInd_Cb = [](uint16_t lcid,
                           BT_HDR* data) {},  // tL2CA_DATA_IND_CB
    .pL2CA_CongestionStatus_Cb =
        [](uint16_t lcid, bool is_congested) {},  // tL2CA_CONGESTION_STATUS_CB
    .pL2CA_TxComplete_Cb = [](uint16_t lcid,
                              uint16_t num_sdu) {},  // tL2CA_TX_COMPLETE_CB
    .pL2CA_Error_Cb = [](uint16_t lcid,
                         uint16_t error_type) {},  // tL2CA_ERROR_CB
    .pL2CA_CreditBasedConnectInd_Cb =
        [](const RawAddress& bdaddr, std::vector<uint16_t>& lcids, uint16_t psm,
           uint16_t peer_mtu,
           uint8_t identifier) {},  // tL2CA_CREDIT_BASED_CONNECT_IND_CB
    .pL2CA_CreditBasedConnectCfm_Cb =
        [](const RawAddress& bdaddr, uint16_t lcid, uint16_t peer_mtu,
           uint16_t result) {},  // tL2CA_CREDIT_BASED_CONNECT_CFM_CB
    .pL2CA_CreditBasedReconfigCompleted_Cb =
        [](const RawAddress& bdaddr, uint16_t lcid, bool is_local_cfg,
           tL2CAP_LE_CFG_INFO* p_cfg) {
        },  // tL2CA_CREDIT_BASED_RECONFIG_COMPLETED_CB
    .pL2CA_CreditBasedCollisionInd_Cb =
        [](const RawAddress& bdaddr) {},  // tL2CA_CREDIT_BASED_COLLISION_IND_CB
};

class FakeL2cap {
 public:
  FakeL2cap() {
    test::mock::stack_l2cap_api::L2CA_ConnectReq.body =
        [](uint16_t psm, const RawAddress& raw_address) { return kDummyCID; };
    test::mock::stack_l2cap_api::L2CA_ConnectReq2.body =
        [](uint16_t psm, const RawAddress& p_bd_addr, uint16_t sec_level) {
          return L2CA_ConnectReq(psm, p_bd_addr);
        };
    test::mock::stack_l2cap_api::L2CA_DataWrite.body = [](uint16_t cid,
                                                          BT_HDR* p_data) {
      auto len = p_data->len;
      osi_free(p_data);
      return (uint8_t)len;
    };
    test::mock::stack_l2cap_api::L2CA_DisconnectReq.body = [](uint16_t lcid) {
      return true;
    };
    test::mock::stack_l2cap_api::L2CA_Register2.body =
        [](uint16_t psm, const tL2CAP_APPL_INFO& p_cb_info, bool enable_snoop,
           tL2CAP_ERTM_INFO* p_ertm_info, uint16_t my_mtu,
           uint16_t required_remote_mtu, uint16_t sec_level) {
          cb_info = p_cb_info;
          return psm;
        };
  }

  ~FakeL2cap() {
    test::mock::stack_l2cap_api::L2CA_ConnectReq = {};
    test::mock::stack_l2cap_api::L2CA_ConnectReq2 = {};
    test::mock::stack_l2cap_api::L2CA_DataWrite = {};
    test::mock::stack_l2cap_api::L2CA_DisconnectReq = {};
    test::mock::stack_l2cap_api::L2CA_Register2 = {};
  }
};

class FakeBtifConfig {
 public:
  FakeBtifConfig() {
    test::mock::btif_config::btif_config_set_bin.body =
        [](const std::string&, const std::string&, const uint8_t*, size_t) {
          // This function is not properly mocked. The abort here allows us to
          // catch any cases using this mock.
          abort();
          return true;
        };
    test::mock::btif_config::btif_config_set_int.body =
        [](const std::string& section, const std::string& key, int value) {
          // This function is not properly mocked. The abort here allows us to
          // catch any cases using this mock.
          abort();
          return true;
        };
  }

  ~FakeBtifConfig() {
    test::mock::btif_config::btif_config_set_bin = {};
    test::mock::btif_config::btif_config_set_int = {};
  }
};

class Fakes {
 public:
  test::fake::FakeOsi fake_osi;
  FakeL2cap fake_l2cap;
  FakeBtifConfig fake_btif_config;
};

}  // namespace

static void FuzzAsServer(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp(data, size);
  std::vector<std::vector<uint8_t>> attrs;

  sdp_init();
  auto rec_num = fdp.ConsumeIntegralInRange<uint8_t>(0, 10);
  for (uint8_t i = 0; i < rec_num; i++) {
    auto handle = SDP_CreateRecord();
    auto attr_num = fdp.ConsumeIntegralInRange<uint8_t>(0, 10);
    for (uint8_t s = 0; s < attr_num; s++) {
      auto id = (i == 0) ? ATTR_ID_BT_PROFILE_DESC_LIST
                         : fdp.ConsumeIntegral<uint16_t>();
      auto type = fdp.ConsumeIntegral<uint8_t>();
      auto len = fdp.ConsumeIntegralInRange<uint16_t>(1, 512);
      auto data = fdp.ConsumeBytes<uint8_t>(len);

      if (data.size() == 0) {
        break;
      }

      attrs.push_back(data);
      SDP_AddAttribute(handle, id, type, data.size(), data.data());
    }
  }

  cb_info.pL2CA_ConnectInd_Cb(RawAddress(kDummyAddr), kDummyCID, kDummyPSM,
                              kDummyID);

  tL2CAP_CFG_INFO cfg = {};
  cb_info.pL2CA_ConfigCfm_Cb(kDummyCID, 0, &cfg);

  while (fdp.remaining_bytes() > 0) {
    auto size = fdp.ConsumeIntegralInRange<uint16_t>(0, 1024);
    auto bytes = fdp.ConsumeBytes<uint8_t>(size);
    BT_HDR* hdr = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + bytes.size());
    hdr->len = bytes.size();
    std::copy(bytes.cbegin(), bytes.cend(), hdr->data);
    cb_info.pL2CA_DataInd_Cb(kDummyCID, hdr);
  }

  cb_info.pL2CA_DisconnectInd_Cb(kDummyCID, false);
  sdp_free();
}

static void FuzzAsClient(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp(data, size);
  std::shared_ptr<tSDP_DISCOVERY_DB> p_db(
      (tSDP_DISCOVERY_DB*)malloc(SDP_DB_SIZE), free);

  std::vector<bluetooth::Uuid> init_uuids;
  std::vector<uint16_t> init_attrs;

  sdp_init();

  uint8_t num_uuid =
      fdp.ConsumeIntegralInRange<uint8_t>(0, SDP_MAX_UUID_FILTERS);
  uint8_t num_attr =
      fdp.ConsumeIntegralInRange<uint8_t>(0, SDP_MAX_ATTR_FILTERS);

  for (uint8_t i = 0; i < num_uuid; i++) {
    init_uuids.push_back(
        bluetooth::Uuid::From16Bit(fdp.ConsumeIntegral<uint16_t>()));
  }

  for (uint8_t i = 0; i < num_attr; i++) {
    init_attrs.push_back(fdp.ConsumeIntegral<uint16_t>());
  }

  SDP_InitDiscoveryDb(p_db.get(), SDP_DB_SIZE, init_uuids.size(),
                      init_uuids.data(), init_attrs.size(), init_attrs.data());

  bool is_di_discover = fdp.ConsumeBool();
  if (is_di_discover) {
    SDP_ServiceSearchRequest(
        kDummyAddr, p_db.get(),
        [](const RawAddress& bd_addr, tSDP_RESULT result) {});
  } else {
    SDP_ServiceSearchAttributeRequest(
        kDummyAddr, p_db.get(),
        [](const RawAddress& bd_addr, tSDP_RESULT result) {});
  }
  cb_info.pL2CA_ConnectCfm_Cb(kDummyCID, L2CAP_CONN_OK);

  tL2CAP_CFG_INFO cfg = {};
  cb_info.pL2CA_ConfigCfm_Cb(kDummyCID, 0, &cfg);

  while (fdp.remaining_bytes() > 0) {
    auto size = fdp.ConsumeIntegralInRange<uint16_t>(0, 1024);
    auto bytes = fdp.ConsumeBytes<uint8_t>(size);
    BT_HDR* hdr = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + bytes.size());
    hdr->len = bytes.size();
    std::copy(bytes.cbegin(), bytes.cend(), hdr->data);
    cb_info.pL2CA_DataInd_Cb(kDummyCID, hdr);
  }

  cb_info.pL2CA_DisconnectInd_Cb(kDummyCID, false);
  sdp_free();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  auto fakes = std::make_unique<Fakes>();

  FuzzAsServer(Data, Size);
  FuzzAsClient(Data, Size);
  return 0;
}

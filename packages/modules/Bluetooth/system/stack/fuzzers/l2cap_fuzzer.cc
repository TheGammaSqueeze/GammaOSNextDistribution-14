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

#include <base/location.h>
#include <fuzzer/FuzzedDataProvider.h>

#include <cstdint>
#include <string>
#include <vector>

#include "btif/include/stack_manager_t.h"
#include "hal/snoop_logger.h"
#include "include/check.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_psm_types.h"
#include "stack/include/l2c_api.h"
#include "stack/include/l2cap_acl_interface.h"
#include "stack/include/l2cap_controller_interface.h"
#include "stack/include/l2cap_hci_link_interface.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_device_controller.h"
#include "test/mock/mock_stack_acl.h"
#include "test/mock/mock_stack_btm_devctl.h"

using bluetooth::Uuid;

// Verify the passed data is readable
static void ConsumeData(const uint8_t* data, size_t size) {
  volatile uint8_t checksum = 0;
  for (size_t i = 0; i < size; i++) {
    checksum ^= data[i];
  }
}

tBTM_CB btm_cb;

bt_status_t do_in_main_thread(base::Location const&,
                              base::OnceCallback<void()>) {
  // this is not properly mocked, so we use abort to catch if this is used in
  // any test cases
  abort();
}
bt_status_t do_in_main_thread_delayed(base::Location const&,
                                      base::OnceCallback<void()>,
                                      std::chrono::microseconds) {
  // this is not properly mocked, so we use abort to catch if this is used in
  // any test cases
  abort();
}

namespace bluetooth {
namespace os {
uint32_t GetSystemPropertyUint32Base(const std::string& property,
                                     uint32_t default_value, int base) {
  return default_value;
}
}  // namespace os

namespace hal {
class SnoopLogger;

const std::string SnoopLogger::kBtSnoopLogModeFiltered = "filtered";

std::string SnoopLogger::GetBtSnoopMode() { return "filtered"; }
void SnoopLogger::AcceptlistL2capChannel(uint16_t, uint16_t, uint16_t) {}
void SnoopLogger::AddA2dpMediaChannel(uint16_t, uint16_t, uint16_t) {}
void SnoopLogger::AddRfcommL2capChannel(uint16_t, uint16_t, uint16_t) {}
void SnoopLogger::ClearL2capAcceptlist(uint16_t, uint16_t, uint16_t) {}
void SnoopLogger::RemoveA2dpMediaChannel(uint16_t, uint16_t) {}
void SnoopLogger::SetL2capChannelClose(uint16_t, uint16_t, uint16_t) {}
void SnoopLogger::SetL2capChannelOpen(uint16_t, uint16_t, uint16_t, uint16_t,
                                      bool) {}
}  // namespace hal
}  // namespace bluetooth

namespace {

class FakeBtStack {
 public:
  FakeBtStack() {
    test::mock::stack_btm_devctl::BTM_IsDeviceUp.body = []() { return true; };
    test::mock::stack_acl::acl_create_le_connection.body =
        [](const RawAddress& bd_addr) { return true; };
    test::mock::stack_acl::acl_create_classic_connection.body =
        [](const RawAddress& bd_addr, bool there_are_high_priority_channels,
           bool is_bonding) { return true; };

    test::mock::stack_acl::acl_send_data_packet_br_edr.body =
        [](const RawAddress& bd_addr, BT_HDR* hdr) {
          ConsumeData((const uint8_t*)hdr, hdr->offset + hdr->len);
          osi_free(hdr);
        };
    test::mock::stack_acl::acl_send_data_packet_ble.body =
        [](const RawAddress& bd_addr, BT_HDR* hdr) {
          ConsumeData((const uint8_t*)hdr, hdr->offset + hdr->len);
          osi_free(hdr);
        };

    GetInterfaceToProfiles()->profileSpecific_HACK->GetHearingAidDeviceCount =
        []() { return 1; };

    test::mock::device_controller::ble_supported = true;
    test::mock::device_controller::acl_data_size_classic = 512;
    test::mock::device_controller::acl_data_size_ble = 512;
    test::mock::device_controller::iso_data_size = 512;
    test::mock::device_controller::ble_suggested_default_data_length = 512;
  }

  ~FakeBtStack() {
    test::mock::stack_btm_devctl::BTM_IsDeviceUp = {};
    test::mock::stack_acl::acl_create_le_connection = {};
    test::mock::stack_acl::acl_create_classic_connection = {};
    test::mock::stack_acl::acl_send_data_packet_br_edr = {};
    test::mock::stack_acl::acl_send_data_packet_ble = {};
  }
};

class Fakes {
 public:
  test::fake::FakeOsi fake_osi;
  FakeBtStack fake_stack;
};

}  // namespace

constexpr uint8_t kAttAddr[] = {0x11, 0x78, 0x78, 0x78, 0x78, 0x78};
constexpr uint16_t kAttHndl = 0x0111;

constexpr uint8_t kEattAddr[] = {0x22, 0x78, 0x78, 0x78, 0x78, 0x78};

constexpr uint8_t kSmpBrAddr[] = {0x33, 0x78, 0x78, 0x78, 0x78, 0x78};
constexpr uint16_t kSmpBrHndl = 0x0222;

constexpr uint16_t kNumClassicAclBuffer = 100;
constexpr uint16_t kNumLeAclBuffer = 100;

void l2c_link_hci_conn_comp(tHCI_STATUS status, uint16_t handle,
                            const RawAddress& p_bda);

static void Fuzz(const uint8_t* data, size_t size) {
  memset(&btm_cb, 0, sizeof(btm_cb));

  l2c_init();

  l2c_link_init(kNumClassicAclBuffer);
  l2c_link_processs_ble_num_bufs(kNumLeAclBuffer);

  tL2CAP_FIXED_CHNL_REG reg = {
      .pL2CA_FixedConn_Cb = [](uint16_t, const RawAddress&, bool, uint16_t,
                               tBT_TRANSPORT) {},
      .pL2CA_FixedData_Cb =
          [](uint16_t, const RawAddress&, BT_HDR* hdr) {
            ConsumeData((const uint8_t*)hdr, hdr->offset + hdr->len);
          },
      .pL2CA_FixedCong_Cb = [](const RawAddress&, bool) {},
      .default_idle_tout = 1000,
  };

  tL2CAP_APPL_INFO appl_info = {
      .pL2CA_ConnectInd_Cb = [](const RawAddress&, uint16_t, uint16_t,
                                uint8_t) {},
      .pL2CA_ConnectCfm_Cb = [](uint16_t, uint16_t) {},
      .pL2CA_ConfigInd_Cb = [](uint16_t, tL2CAP_CFG_INFO*) {},
      .pL2CA_ConfigCfm_Cb = [](uint16_t, uint16_t, tL2CAP_CFG_INFO*) {},
      .pL2CA_DisconnectInd_Cb = [](uint16_t, bool) {},
      .pL2CA_DisconnectCfm_Cb = [](uint16_t, uint16_t) {},
      .pL2CA_DataInd_Cb =
          [](uint16_t, BT_HDR* hdr) {
            ConsumeData((const uint8_t*)hdr, hdr->offset + hdr->len);
          },
      .pL2CA_CongestionStatus_Cb = [](uint16_t, bool) {},
      .pL2CA_TxComplete_Cb = [](uint16_t, uint16_t) {},
      .pL2CA_Error_Cb = [](uint16_t, uint16_t) {},
      .pL2CA_CreditBasedConnectInd_Cb = [](const RawAddress&,
                                           std::vector<uint16_t>&, uint16_t,
                                           uint16_t, uint8_t) {},
      .pL2CA_CreditBasedConnectCfm_Cb = [](const RawAddress&, uint16_t,
                                           uint16_t, uint16_t) {},
      .pL2CA_CreditBasedReconfigCompleted_Cb = [](const RawAddress&, uint16_t,
                                                  bool, tL2CAP_LE_CFG_INFO*) {},
      .pL2CA_CreditBasedCollisionInd_Cb = [](const RawAddress&) {},
  };
  CHECK(L2CA_Register2(BT_PSM_ATT, appl_info, false, nullptr, L2CAP_MTU_SIZE, 0,
                       BTM_SEC_NONE));
  CHECK(L2CA_RegisterLECoc(BT_PSM_EATT, appl_info, BTM_SEC_NONE, {}));

  CHECK(L2CA_RegisterFixedChannel(L2CAP_ATT_CID, &reg));
  CHECK(L2CA_ConnectFixedChnl(L2CAP_ATT_CID, kAttAddr));
  CHECK(l2cble_conn_comp(kAttHndl, HCI_ROLE_CENTRAL, kAttAddr, BLE_ADDR_PUBLIC,
                         100, 100, 100));

  CHECK(L2CA_RegisterFixedChannel(L2CAP_SMP_BR_CID, &reg));
  CHECK(L2CA_ConnectFixedChnl(L2CAP_SMP_BR_CID, kSmpBrAddr));
  l2c_link_hci_conn_comp(HCI_SUCCESS, kSmpBrHndl, kSmpBrAddr);

  auto att_cid = L2CA_ConnectReq(BT_PSM_ATT, kAttAddr);
  CHECK(att_cid != 0);

  tL2CAP_LE_CFG_INFO cfg;
  auto eatt_cid = L2CA_ConnectLECocReq(BT_PSM_EATT, kEattAddr, &cfg, 0);
  CHECK(eatt_cid != 0);

  FuzzedDataProvider fdp(data, size);

  // Feeding input packets
  constexpr uint16_t kMinPacketSize = 4 + L2CAP_PKT_OVERHEAD;
  constexpr uint16_t kMaxPacketSize = 1024;
  for (;;) {
    auto size =
        fdp.ConsumeIntegralInRange<uint16_t>(kMinPacketSize, kMaxPacketSize);
    auto bytes = fdp.ConsumeBytes<uint8_t>(size);
    if (bytes.size() < kMinPacketSize) {
      break;
    }

    BT_HDR* hdr = (BT_HDR*)osi_calloc(sizeof(BT_HDR) + bytes.size());
    hdr->len = bytes.size();
    std::copy(bytes.cbegin(), bytes.cend(), hdr->data);
    l2c_rcv_acl_data(hdr);
  }

  L2CA_DisconnectReq(att_cid);
  L2CA_DisconnectLECocReq(eatt_cid);

  L2CA_RemoveFixedChnl(L2CAP_SMP_BR_CID, kSmpBrAddr);
  l2c_link_hci_disc_comp(kSmpBrHndl, HCI_SUCCESS);

  L2CA_RemoveFixedChnl(L2CAP_ATT_CID, kAttAddr);
  l2c_link_hci_disc_comp(kAttHndl, HCI_SUCCESS);

  l2cu_device_reset();
  l2c_free();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  auto fakes = std::make_unique<Fakes>();
  Fuzz(Data, Size);
  return 0;
}

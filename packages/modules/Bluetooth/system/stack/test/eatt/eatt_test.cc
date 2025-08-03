/*
 * Copyright 2020 HIMSA II K/S - www.himsa.dk.
 * Represented by EHIMA - www.ehima.com
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

#include <bluetooth/log.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <vector>

#include "bind_helpers.h"
#include "l2c_api.h"
#include "mock_btif_storage.h"
#include "mock_btm_api_layer.h"
#include "mock_controller.h"
#include "mock_eatt.h"
#include "mock_gatt_layer.h"
#include "mock_l2cap_layer.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_psm_types.h"
#include "types/raw_address.h"

using testing::_;
using testing::DoAll;
using testing::MockFunction;
using testing::NiceMock;
using testing::NotNull;
using testing::Return;
using testing::SaveArg;
using testing::SaveArgPointee;
using testing::StrictMock;

using bluetooth::eatt::EattChannel;
using bluetooth::eatt::EattChannelState;
using namespace bluetooth;

#define BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK 0x01

/* Needed for testing context */
static tGATT_TCB test_tcb;
void btif_storage_add_eatt_supported(const RawAddress& addr) { return; }
void gatt_consolidate(const RawAddress& identity_addr, const RawAddress& rpa) {}
void gatt_data_process(tGATT_TCB& tcb, uint16_t cid, BT_HDR* p_buf) { return; }
tGATT_TCB* gatt_find_tcb_by_addr(const RawAddress& bda,
                                 tBT_TRANSPORT transport) {
  log::info("");
  return &test_tcb;
}

namespace {
const RawAddress test_address({0x11, 0x11, 0x11, 0x11, 0x11, 0x11});

class EattTest : public testing::Test {
 protected:
  void ConnectDeviceEattSupported(int num_of_accepted_connections,
                                  bool collision = false) {
    ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
        .WillByDefault(
            [](const RawAddress& addr,
               base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
              std::move(cb).Run(addr, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
              return true;
            });
    ON_CALL(gatt_interface_, GetEattSupport)
        .WillByDefault([](const RawAddress& addr) { return true; });

    std::vector<uint16_t> test_local_cids{61, 62, 63, 64, 65};
    EXPECT_CALL(l2cap_interface_,
                ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
        .WillOnce(Return(test_local_cids));

    eatt_instance_->Connect(test_address);

    if (collision) {
      /* Collision should be handled only if all channels has been rejected in
       * first place.*/
      if (num_of_accepted_connections == 0) {
        EXPECT_CALL(l2cap_interface_,
                    ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
            .Times(1);
      }

      l2cap_app_info_.pL2CA_CreditBasedCollisionInd_Cb(test_address);
    }

    int i = 0;
    for (uint16_t cid : test_local_cids) {
      EattChannel* channel =
          eatt_instance_->FindEattChannelByCid(test_address, cid);
      ASSERT_TRUE(channel != nullptr);
      ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_PENDING);

      if (i < num_of_accepted_connections) {
        l2cap_app_info_.pL2CA_CreditBasedConnectCfm_Cb(
            test_address, cid, EATT_MIN_MTU_MPS, L2CAP_CONN_OK);
        connected_cids_.push_back(cid);

        ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
        ASSERT_TRUE(channel->tx_mtu_ == EATT_MIN_MTU_MPS);
      } else {
        l2cap_app_info_.pL2CA_Error_Cb(cid, L2CAP_CONN_NO_RESOURCES);

        EattChannel* channel =
            eatt_instance_->FindEattChannelByCid(test_address, cid);
        ASSERT_TRUE(channel == nullptr);
      }
      i++;
    }

    ASSERT_TRUE(test_tcb.eatt == num_of_accepted_connections);
  }

  void ConnectDeviceBothSides(int num_of_accepted_connections,
                              std::vector<uint16_t>& incoming_cids) {
    base::OnceCallback<void(const RawAddress&, uint8_t)> eatt_supp_feat_cb;

    ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
        .WillByDefault(
            [&eatt_supp_feat_cb](
                const RawAddress& addr,
                base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
              eatt_supp_feat_cb = std::move(cb);
              return true;
            });

    // Return false to trigger supported features request
    ON_CALL(gatt_interface_, GetEattSupport)
        .WillByDefault([](const RawAddress& addr) { return false; });

    std::vector<uint16_t> test_local_cids{61, 62, 63, 64, 65};
    EXPECT_CALL(l2cap_interface_,
                ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
        .WillOnce(Return(test_local_cids));

    eatt_instance_->Connect(test_address);

    // Let the remote connect while we are trying to connect
    EXPECT_CALL(
        l2cap_interface_,
        ConnectCreditBasedRsp(test_address, 1, incoming_cids, L2CAP_CONN_OK, _))
        .WillOnce(Return(true));
    l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
        test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);

    // Respond to feature request scheduled by the connect request
    ASSERT_TRUE(eatt_supp_feat_cb);
    if (eatt_supp_feat_cb) {
      std::move(eatt_supp_feat_cb)
          .Run(test_address, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
    }

    int i = 0;
    for (uint16_t cid : test_local_cids) {
      EattChannel* channel =
          eatt_instance_->FindEattChannelByCid(test_address, cid);
      ASSERT_TRUE(channel != nullptr);
      ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_PENDING);

      if (i < num_of_accepted_connections) {
        l2cap_app_info_.pL2CA_CreditBasedConnectCfm_Cb(
            test_address, cid, EATT_MIN_MTU_MPS, L2CAP_CONN_OK);
        connected_cids_.push_back(cid);

        ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
        ASSERT_TRUE(channel->tx_mtu_ == EATT_MIN_MTU_MPS);
      } else {
        l2cap_app_info_.pL2CA_Error_Cb(cid, L2CAP_CONN_NO_RESOURCES);

        EattChannel* channel =
            eatt_instance_->FindEattChannelByCid(test_address, cid);
        ASSERT_TRUE(channel == nullptr);
      }
      i++;
    }

    // Check the incoming CIDs as well
    for (auto cid : incoming_cids) {
      EattChannel* channel =
          eatt_instance_->FindEattChannelByCid(test_address, cid);
      ASSERT_NE(nullptr, channel);
      ASSERT_EQ(channel->state_, EattChannelState::EATT_CHANNEL_OPENED);
      ASSERT_TRUE(channel->tx_mtu_ == EATT_MIN_MTU_MPS);
    }

    ASSERT_EQ(test_tcb.eatt,
              num_of_accepted_connections + incoming_cids.size());
  }

  void DisconnectEattByPeer(void) {
    for (uint16_t cid : connected_cids_)
      l2cap_app_info_.pL2CA_DisconnectInd_Cb(cid, true);
    ASSERT_TRUE(test_tcb.eatt == 0);
  }

  void DisconnectEattDevice(std::vector<uint16_t> cids) {
    EXPECT_CALL(l2cap_interface_, DisconnectRequest(_)).Times(cids.size());
    eatt_instance_->Disconnect(test_address);

    ASSERT_TRUE(test_tcb.eatt == 0);
  }

  void SetUp() override {
    bluetooth::l2cap::SetMockInterface(&l2cap_interface_);
    bluetooth::manager::SetMockBtmApiInterface(&btm_api_interface_);
    bluetooth::manager::SetMockBtifStorageInterface(&btif_storage_interface_);
    bluetooth::gatt::SetMockGattInterface(&gatt_interface_);
    controller::SetMockControllerInterface(&controller_interface);

    // Clear the static memory for each test case
    memset(&test_tcb, 0, sizeof(test_tcb));

    EXPECT_CALL(l2cap_interface_, RegisterLECoc(BT_PSM_EATT, _, _))
        .WillOnce(DoAll(SaveArg<1>(&l2cap_app_info_), Return(BT_PSM_EATT)));

    ON_CALL(btif_storage_interface_, LoadBondedEatt).WillByDefault([]() {
      return;
    });

    hci_role_ = HCI_ROLE_CENTRAL;

    ON_CALL(l2cap_interface_, GetBleConnRole(_))
        .WillByDefault(DoAll(Return(hci_role_)));

    ON_CALL(controller_interface, GetAclDataSizeBle())
        .WillByDefault(Return(128));

    eatt_instance_ = EattExtension::GetInstance();
    eatt_instance_->Start();

    Test::SetUp();
  }

  void TearDown() override {
    EXPECT_CALL(l2cap_interface_, DeregisterLECoc(BT_PSM_EATT)).Times(1);

    eatt_instance_->Stop();
    eatt_instance_ = nullptr;
    hci_role_ = 0;
    connected_cids_.clear();

    bluetooth::gatt::SetMockGattInterface(nullptr);
    bluetooth::l2cap::SetMockInterface(nullptr);
    bluetooth::manager::SetMockBtifStorageInterface(nullptr);
    bluetooth::manager::SetMockBtmApiInterface(nullptr);
    controller::SetMockControllerInterface(nullptr);

    Test::TearDown();
  }

  bluetooth::manager::MockBtifStorageInterface btif_storage_interface_;
  bluetooth::manager::MockBtmApiInterface btm_api_interface_;
  bluetooth::l2cap::MockL2capInterface l2cap_interface_;
  bluetooth::gatt::MockGattInterface gatt_interface_;
  controller::MockControllerInterface controller_interface;

  tL2CAP_APPL_INFO l2cap_app_info_;
  EattExtension* eatt_instance_;
  std::vector<uint16_t> connected_cids_;
  uint8_t hci_role_ = HCI_ROLE_CENTRAL;
};

TEST_F(EattTest, ConnectSucceed) {
  ConnectDeviceEattSupported(1);
  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, IncomingEattConnectionByUnknownDevice) {
  std::vector<uint16_t> incoming_cids{71, 72, 73, 74, 75};

  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault(
          [](const RawAddress& addr, tBT_TRANSPORT transport) { return true; });
  EXPECT_CALL(
      l2cap_interface_,
      ConnectCreditBasedRsp(test_address, 1, incoming_cids, L2CAP_CONN_OK, _))
      .WillOnce(Return(true));

  l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
      test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);

  DisconnectEattDevice(incoming_cids);
}

TEST_F(EattTest, IncomingEattConnectionByKnownDevice) {
  hci_role_ = HCI_ROLE_PERIPHERAL;
  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault(
          [](const RawAddress& addr, tBT_TRANSPORT transport) { return true; });
  ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
      .WillByDefault(
          [](const RawAddress& addr,
             base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
            std::move(cb).Run(addr, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
            return true;
          });
  ON_CALL(gatt_interface_, GetEattSupport)
      .WillByDefault([](const RawAddress& addr) { return true; });

  eatt_instance_->Connect(test_address);
  std::vector<uint16_t> incoming_cids{71, 72, 73, 74, 75};

  EXPECT_CALL(
      l2cap_interface_,
      ConnectCreditBasedRsp(test_address, 1, incoming_cids, L2CAP_CONN_OK, _))
      .WillOnce(Return(true));

  l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
      test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);

  DisconnectEattDevice(incoming_cids);

  hci_role_ = HCI_ROLE_CENTRAL;
}

TEST_F(EattTest, IncomingEattConnectionByKnownDeviceEncryptionOff) {
  hci_role_ = HCI_ROLE_PERIPHERAL;
  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault([](const RawAddress& addr, tBT_TRANSPORT transport) {
        return false;
      });
  ON_CALL(btm_api_interface_, IsLinkKeyKnown)
      .WillByDefault(
          [](const RawAddress& addr, tBT_TRANSPORT transport) { return true; });
  ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
      .WillByDefault(
          [](const RawAddress& addr,
             base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
            std::move(cb).Run(addr, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
            return true;
          });
  ON_CALL(gatt_interface_, GetEattSupport)
      .WillByDefault([](const RawAddress& addr) { return true; });

  eatt_instance_->Connect(test_address);
  std::vector<uint16_t> incoming_cids{71, 72, 73, 74, 75};

  EXPECT_CALL(l2cap_interface_,
              ConnectCreditBasedRsp(test_address, 1, _,
                                    L2CAP_LE_RESULT_INSUFFICIENT_ENCRYP, _))
      .WillOnce(Return(true));

  l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
      test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);

  hci_role_ = HCI_ROLE_CENTRAL;
}

TEST_F(EattTest, IncomingEattConnectionByUnknownDeviceEncryptionOff) {
  std::vector<uint16_t> incoming_cids{71, 72, 73, 74, 75};

  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault([](const RawAddress& addr, tBT_TRANSPORT transport) {
        return false;
      });
  ON_CALL(btm_api_interface_, IsLinkKeyKnown)
      .WillByDefault([](const RawAddress& addr, tBT_TRANSPORT transport) {
        return false;
      });
  EXPECT_CALL(
      l2cap_interface_,
      ConnectCreditBasedRsp(test_address, 1, _,
                            L2CAP_LE_RESULT_INSUFFICIENT_AUTHENTICATION, _))
      .WillOnce(Return(true));

  l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
      test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);
}

TEST_F(EattTest, ReconnectInitiatedByRemoteSucceed) {
  ConnectDeviceEattSupported(1);
  DisconnectEattDevice(connected_cids_);
  std::vector<uint16_t> incoming_cids{71, 72, 73, 74, 75};

  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault(
          [](const RawAddress& addr, tBT_TRANSPORT transport) { return true; });

  EXPECT_CALL(
      l2cap_interface_,
      ConnectCreditBasedRsp(test_address, 1, incoming_cids, L2CAP_CONN_OK, _))
      .WillOnce(Return(true));

  l2cap_app_info_.pL2CA_CreditBasedConnectInd_Cb(
      test_address, incoming_cids, BT_PSM_EATT, EATT_MIN_MTU_MPS, 1);

  DisconnectEattDevice(incoming_cids);
}

TEST_F(EattTest, ConnectInitiatedWhenRemoteConnects) {
  ON_CALL(btm_api_interface_, IsEncrypted)
      .WillByDefault(
          [](const RawAddress& addr, tBT_TRANSPORT transport) { return true; });

  std::vector<uint16_t> incoming_cids{71, 72, 73, 74};
  ConnectDeviceBothSides(1, incoming_cids);

  std::vector<uint16_t> disconnecting_cids;
  disconnecting_cids.insert(disconnecting_cids.end(), incoming_cids.begin(),
                            incoming_cids.end());
  disconnecting_cids.insert(disconnecting_cids.end(), connected_cids_.begin(),
                            connected_cids_.end());
  DisconnectEattDevice(disconnecting_cids);
}

TEST_F(EattTest, ConnectSucceedMultipleChannels) {
  ConnectDeviceEattSupported(5);
  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ConnectFailedEattNotSupported) {
  ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
      .WillByDefault(
          [](const RawAddress& addr,
             base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
            std::move(cb).Run(addr, 0);
            return true;
          });
  ON_CALL(gatt_interface_, GetEattSupport)
      .WillByDefault([](const RawAddress& addr) { return false; });

  EXPECT_CALL(l2cap_interface_,
              ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
      .Times(0);
  eatt_instance_->Connect(test_address);
  ASSERT_TRUE(eatt_instance_->IsEattSupportedByPeer(test_address) == false);
}

TEST_F(EattTest, ConnectFailedSlaveOnTheLink) {
  EXPECT_CALL(l2cap_interface_,
              ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
      .Times(0);

  hci_role_ = HCI_ROLE_PERIPHERAL;
  eatt_instance_->Connect(test_address);

  /* Back to default btm role */
  hci_role_ = HCI_ROLE_CENTRAL;
}

TEST_F(EattTest, DisonnectByPeerSucceed) {
  ConnectDeviceEattSupported(2);

  uint16_t cid = connected_cids_[0];
  EattChannel* channel =
      eatt_instance_->FindEattChannelByCid(test_address, cid);
  ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);

  DisconnectEattByPeer();

  channel = eatt_instance_->FindEattChannelByCid(test_address, cid);
  ASSERT_TRUE(channel == nullptr);
}

TEST_F(EattTest, ReconfigAllSucceed) {
  ConnectDeviceEattSupported(3);

  std::vector<uint16_t> cids;
  EXPECT_CALL(l2cap_interface_, ReconfigCreditBasedConnsReq(_, _, _))
      .WillOnce(DoAll(SaveArg<1>(&cids), Return(true)));

  uint16_t new_mtu = 300;
  eatt_instance_->ReconfigureAll(test_address, new_mtu);

  ASSERT_TRUE(cids.size() == connected_cids_.size());

  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_OK, .mtu = new_mtu};

  for (uint16_t cid : cids) {
    l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cid,
                                                          true, &cfg);

    EattChannel* channel =
        eatt_instance_->FindEattChannelByCid(test_address, cid);
    ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
    ASSERT_TRUE(channel->rx_mtu_ == new_mtu);
  }

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ReconfigAllFailed) {
  ConnectDeviceEattSupported(4);

  std::vector<uint16_t> cids;
  EXPECT_CALL(l2cap_interface_, ReconfigCreditBasedConnsReq(_, _, _))
      .WillOnce(DoAll(SaveArg<1>(&cids), Return(true)));

  uint16_t new_mtu = 300;
  eatt_instance_->ReconfigureAll(test_address, new_mtu);

  ASSERT_TRUE(cids.size() == connected_cids_.size());

  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_FAILED_NO_REASON,
                            .mtu = new_mtu};

  for (uint16_t cid : cids) {
    l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cid,
                                                          true, &cfg);

    EattChannel* channel =
        eatt_instance_->FindEattChannelByCid(test_address, cid);
    ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
    ASSERT_TRUE(channel->rx_mtu_ != new_mtu);
  }

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ReconfigSingleSucceed) {
  ConnectDeviceEattSupported(2);

  std::vector<uint16_t> cids;
  EXPECT_CALL(l2cap_interface_, ReconfigCreditBasedConnsReq(_, _, _))
      .WillOnce(DoAll(SaveArg<1>(&cids), Return(true)));

  uint16_t new_mtu = 300;
  eatt_instance_->Reconfigure(test_address, connected_cids_[1], new_mtu);

  ASSERT_TRUE(cids.size() == 1);

  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_OK, .mtu = new_mtu};

  auto it = std::find(connected_cids_.begin(), connected_cids_.end(), cids[0]);
  ASSERT_TRUE(it != connected_cids_.end());

  l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cids[0],
                                                        true, &cfg);
  EattChannel* channel =
      eatt_instance_->FindEattChannelByCid(test_address, cids[0]);
  ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
  ASSERT_TRUE(channel->rx_mtu_ == new_mtu);

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ReconfigSingleFailed) {
  ConnectDeviceEattSupported(2);

  std::vector<uint16_t> cids;
  EXPECT_CALL(l2cap_interface_, ReconfigCreditBasedConnsReq(_, _, _))
      .WillOnce(DoAll(SaveArg<1>(&cids), Return(true)));

  uint16_t new_mtu = 300;
  eatt_instance_->ReconfigureAll(test_address, new_mtu);

  ASSERT_TRUE(cids.size() == connected_cids_.size());

  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_FAILED_NO_REASON,
                            .mtu = new_mtu};

  auto it = std::find(connected_cids_.begin(), connected_cids_.end(), cids[0]);
  ASSERT_TRUE(it != connected_cids_.end());

  l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cids[0],
                                                        true, &cfg);
  EattChannel* channel =
      eatt_instance_->FindEattChannelByCid(test_address, cids[0]);
  ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
  ASSERT_TRUE(channel->rx_mtu_ != new_mtu);

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ReconfigPeerSucceed) {
  ConnectDeviceEattSupported(3);

  uint16_t new_mtu = 300;
  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_OK, .mtu = new_mtu};

  for (uint16_t cid : connected_cids_) {
    l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cid,
                                                          false, &cfg);

    EattChannel* channel =
        eatt_instance_->FindEattChannelByCid(test_address, cid);
    ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
    ASSERT_TRUE(channel->tx_mtu_ == new_mtu);
  }

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, ReconfigPeerFailed) {
  ConnectDeviceEattSupported(2);

  uint16_t new_mtu = 300;
  tL2CAP_LE_CFG_INFO cfg = {.result = L2CAP_CFG_FAILED_NO_REASON,
                            .mtu = new_mtu};

  for (uint16_t cid : connected_cids_) {
    l2cap_app_info_.pL2CA_CreditBasedReconfigCompleted_Cb(test_address, cid,
                                                          false, &cfg);

    EattChannel* channel =
        eatt_instance_->FindEattChannelByCid(test_address, cid);
    ASSERT_TRUE(channel->state_ == EattChannelState::EATT_CHANNEL_OPENED);
    ASSERT_TRUE(channel->tx_mtu_ != new_mtu);
  }

  DisconnectEattDevice(connected_cids_);
}

TEST_F(EattTest, DoubleDisconnect) {
  ConnectDeviceEattSupported(1);
  DisconnectEattDevice(connected_cids_);

  /* Force second disconnect */
  eatt_instance_->Disconnect(test_address);
}

TEST_F(EattTest, TestCollisionHandling) {
  ConnectDeviceEattSupported(0, true /* collision*/);
  ConnectDeviceEattSupported(5, true /* collision*/);
}

TEST_F(EattTest, ChannelUnavailableWhileOpening) {
  // arrange
  ON_CALL(gatt_interface_, ClientReadSupportedFeatures)
      .WillByDefault(
          [](const RawAddress& addr,
             base::OnceCallback<void(const RawAddress&, uint8_t)> cb) {
            std::move(cb).Run(addr, BLE_GATT_SVR_SUP_FEAT_EATT_BITMASK);
            return true;
          });
  ON_CALL(gatt_interface_, GetEattSupport).WillByDefault(Return(true));

  // expect
  EXPECT_CALL(l2cap_interface_,
              ConnectCreditBasedReq(BT_PSM_EATT, test_address, _))
      .WillOnce(Return(std::vector<uint16_t>{61}));

  // act: start
  eatt_instance_->Connect(test_address);
  auto available_channel_for_request =
      eatt_instance_->GetChannelAvailableForClientRequest(test_address);
  auto available_channel_for_indication =
      eatt_instance_->GetChannelAvailableForIndication(test_address);

  // assert
  ASSERT_EQ(available_channel_for_request, nullptr);
  ASSERT_EQ(available_channel_for_indication, nullptr);
}

TEST_F(EattTest, ChannelUnavailableWhileReconfiguring) {
  // arrange
  ON_CALL(l2cap_interface_, ReconfigCreditBasedConnsReq(_, _, _))
      .WillByDefault(Return(true));
  ConnectDeviceEattSupported(/* num_of_accepted_connections = */ 1);

  // act: reconfigure, then get available channels
  eatt_instance_->Reconfigure(test_address, connected_cids_[0], 300);
  auto available_channel_for_request =
      eatt_instance_->GetChannelAvailableForClientRequest(test_address);
  auto available_channel_for_indication =
      eatt_instance_->GetChannelAvailableForIndication(test_address);

  // assert
  ASSERT_EQ(available_channel_for_request, nullptr);
  ASSERT_EQ(available_channel_for_indication, nullptr);
}

}  // namespace

/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "btm_iso_api.h"
#include "hci/include/hci_layer.h"
#include "main/shim/hci_layer.h"
#include "mock_controller.h"
#include "mock_hcic_layer.h"
#include "osi/include/allocator.h"
#include "stack/btm/btm_dev.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/hcidefs.h"
#include "test/mock/mock_main_shim_hci_layer.h"

using bluetooth::hci::IsoManager;
using testing::_;
using testing::AnyNumber;
using testing::AtLeast;
using testing::Eq;
using testing::Matcher;
using testing::Return;
using testing::SaveArg;
using testing::StrictMock;
using testing::Test;

// for function pointer testing purpose
bool IsIsoActive = false;

tBTM_SEC_DEV_REC* btm_find_dev_by_handle(uint16_t handle) { return nullptr; }
void BTM_LogHistory(const std::string& tag, const RawAddress& bd_addr,
                    const std::string& msg, const std::string& extra) {}

namespace bluetooth::shim {
class IsoInterface {
 public:
  virtual void HciSend(BT_HDR* packet) = 0;
  virtual ~IsoInterface() = default;
};

class MockIsoInterface : public IsoInterface {
 public:
  MOCK_METHOD((void), HciSend, (BT_HDR * p_msg), (override));
};

static MockIsoInterface* iso_interface = nullptr;
static void SetMockIsoInterface(MockIsoInterface* interface) {
  iso_interface = interface;
}

static void set_data_cb(
    base::Callback<void(const base::Location&, BT_HDR*)> /* send_data_cb */) {
  FAIL() << __func__ << " should never be called";
}

static void transmit_command(const BT_HDR* command,
                             command_complete_cb complete_callback,
                             command_status_cb status_cb, void* context) {
  FAIL() << __func__ << " should never be called";
}

static void transmit_downward(void* data, uint16_t iso_Data_size) {
  iso_interface->HciSend((BT_HDR*)data);
  osi_free(data);
}

static hci_t interface = {.set_data_cb = set_data_cb,
                          .transmit_command = transmit_command,
                          .transmit_downward = transmit_downward};

}  // namespace bluetooth::shim

namespace {
class MockCigCallbacks : public bluetooth::hci::iso_manager::CigCallbacks {
 public:
  MockCigCallbacks() = default;
  MockCigCallbacks(const MockCigCallbacks&) = delete;
  MockCigCallbacks& operator=(const MockCigCallbacks&) = delete;

  ~MockCigCallbacks() override = default;

  MOCK_METHOD((void), OnSetupIsoDataPath,
              (uint8_t status, uint16_t conn_handle, uint8_t cig_id),
              (override));
  MOCK_METHOD((void), OnRemoveIsoDataPath,
              (uint8_t status, uint16_t conn_handle, uint8_t cig_id),
              (override));
  MOCK_METHOD((void), OnIsoLinkQualityRead,
              (uint8_t conn_handle, uint8_t cig_id, uint32_t txUnackedPackets,
               uint32_t txFlushedPackets, uint32_t txLastSubeventPackets,
               uint32_t retransmittedPackets, uint32_t crcErrorPackets,
               uint32_t rxUnreceivedPackets, uint32_t duplicatePackets),
              (override));

  MOCK_METHOD((void), OnCisEvent, (uint8_t event, void* data), (override));
  MOCK_METHOD((void), OnCigEvent, (uint8_t event, void* data), (override));
};

class MockBigCallbacks : public bluetooth::hci::iso_manager::BigCallbacks {
 public:
  MockBigCallbacks() = default;
  MockBigCallbacks(const MockBigCallbacks&) = delete;
  MockBigCallbacks& operator=(const MockBigCallbacks&) = delete;

  ~MockBigCallbacks() override = default;

  MOCK_METHOD((void), OnSetupIsoDataPath,
              (uint8_t status, uint16_t conn_handle, uint8_t big_id),
              (override));
  MOCK_METHOD((void), OnRemoveIsoDataPath,
              (uint8_t status, uint16_t conn_handle, uint8_t big_id),
              (override));

  MOCK_METHOD((void), OnBigEvent, (uint8_t event, void* data), (override));
};
}  // namespace

class IsoManagerTest : public Test {
 protected:
  void SetUp() override {
    bluetooth::shim::SetMockIsoInterface(&iso_interface_);
    hcic::SetMockHcicInterface(&hcic_interface_);
    controller::SetMockControllerInterface(&controller_interface_);
    bluetooth::shim::testing::hci_layer_set_interface(
        &bluetooth::shim::interface);

    big_callbacks_.reset(new MockBigCallbacks());
    cig_callbacks_.reset(new MockCigCallbacks());
    IsIsoActive = false;

    EXPECT_CALL(controller_interface_, GetIsoBufferCount())
        .Times(AtLeast(1))
        .WillRepeatedly(Return(6));
    EXPECT_CALL(controller_interface_, GetIsoDataSize())
        .Times(AtLeast(1))
        .WillRepeatedly(Return(1024));

    InitIsoManager();
  }

  void TearDown() override {
    CleanupIsoManager();

    big_callbacks_.reset();
    cig_callbacks_.reset();

    bluetooth::shim::SetMockIsoInterface(nullptr);
    hcic::SetMockHcicInterface(nullptr);
    controller::SetMockControllerInterface(nullptr);
    bluetooth::shim::testing::hci_layer_set_interface(nullptr);
  }

  virtual void InitIsoManager() {
    manager_instance_ = IsoManager::GetInstance();
    manager_instance_->Start();
    manager_instance_->RegisterCigCallbacks(cig_callbacks_.get());
    manager_instance_->RegisterBigCallbacks(big_callbacks_.get());
    manager_instance_->RegisterOnIsoTrafficActiveCallback(iso_active_callback);

    // Default mock SetCigParams action
    volatile_test_cig_create_cmpl_evt_ = kDefaultCigParamsEvt;
    ON_CALL(hcic_interface_, SetCigParams)
        .WillByDefault([this](auto cig_id, auto,
                              base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
          uint8_t hci_mock_rsp_buffer
              [3 + sizeof(uint16_t) * this->volatile_test_cig_create_cmpl_evt_
                                          .conn_handles.size()];
          uint8_t* p = hci_mock_rsp_buffer;

          UINT8_TO_STREAM(p, this->volatile_test_cig_create_cmpl_evt_.status);
          UINT8_TO_STREAM(p, cig_id);
          UINT8_TO_STREAM(
              p, this->volatile_test_cig_create_cmpl_evt_.conn_handles.size());
          for (auto handle :
               this->volatile_test_cig_create_cmpl_evt_.conn_handles) {
            UINT16_TO_STREAM(p, handle);
          }

          std::move(cb).Run(
              hci_mock_rsp_buffer,
              3 + sizeof(uint16_t) * this->volatile_test_cig_create_cmpl_evt_
                                         .conn_handles.size());
          return 0;
        });

    // Default mock CreateCis action
    ON_CALL(hcic_interface_, CreateCis)
        .WillByDefault([](uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                          base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
          for (const EXT_CIS_CREATE_CFG* cis = cis_cfg; num_cis != 0;
               num_cis--, cis++) {
            std::vector<uint8_t> buf(28);
            uint8_t* p = buf.data();
            UINT8_TO_STREAM(p, HCI_SUCCESS);
            UINT16_TO_STREAM(p, cis->cis_conn_handle);
            UINT24_TO_STREAM(p, 0xEA);    // CIG sync delay
            UINT24_TO_STREAM(p, 0xEB);    // CIS sync delay
            UINT24_TO_STREAM(p, 0xEC);    // transport latency mtos
            UINT24_TO_STREAM(p, 0xED);    // transport latency stom
            UINT8_TO_STREAM(p, 0x01);     // phy mtos
            UINT8_TO_STREAM(p, 0x02);     // phy stom
            UINT8_TO_STREAM(p, 0x01);     // nse
            UINT8_TO_STREAM(p, 0x02);     // bn mtos
            UINT8_TO_STREAM(p, 0x03);     // bn stom
            UINT8_TO_STREAM(p, 0x04);     // ft mtos
            UINT8_TO_STREAM(p, 0x05);     // ft stom
            UINT16_TO_STREAM(p, 0x00FA);  // Max PDU mtos
            UINT16_TO_STREAM(p, 0x00FB);  // Max PDU stom
            UINT16_TO_STREAM(p, 0x0C60);  // ISO interval

            IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_CIS_EST_EVT,
                                                      buf.data(), buf.size());
          }
        });

    // Default mock disconnect action
    ON_CALL(hcic_interface_, Disconnect)
        .WillByDefault([](uint16_t handle, uint8_t reason) {
          IsoManager::GetInstance()->HandleDisconnect(handle, reason);
        });

    // Default mock CreateBig HCI action
    volatile_test_big_params_evt_ = kDefaultBigParamsEvt;
    ON_CALL(hcic_interface_, CreateBig)
        .WillByDefault(
            [this](auto big_handle,
                   bluetooth::hci::iso_manager::big_create_params big_params) {
              std::vector<uint8_t> buf(big_params.num_bis * sizeof(uint16_t) +
                                       18);
              uint8_t* p = buf.data();
              UINT8_TO_STREAM(p, HCI_SUCCESS);
              UINT8_TO_STREAM(p, big_handle);

              ASSERT_TRUE(big_params.num_bis <=
                          volatile_test_big_params_evt_.conn_handles.size());

              UINT24_TO_STREAM(p, volatile_test_big_params_evt_.big_sync_delay);
              UINT24_TO_STREAM(
                  p, volatile_test_big_params_evt_.transport_latency_big);
              UINT8_TO_STREAM(p, big_params.phy);
              UINT8_TO_STREAM(p, volatile_test_big_params_evt_.nse);
              UINT8_TO_STREAM(p, volatile_test_big_params_evt_.bn);
              UINT8_TO_STREAM(p, volatile_test_big_params_evt_.pto);
              UINT8_TO_STREAM(p, volatile_test_big_params_evt_.irc);
              UINT16_TO_STREAM(p, volatile_test_big_params_evt_.max_pdu);
              UINT16_TO_STREAM(p, volatile_test_big_params_evt_.iso_interval);

              UINT8_TO_STREAM(p, big_params.num_bis);
              for (auto i = 0; i < big_params.num_bis; ++i) {
                UINT16_TO_STREAM(p,
                                 volatile_test_big_params_evt_.conn_handles[i]);
              }

              IsoManager::GetInstance()->HandleHciEvent(
                  HCI_BLE_CREATE_BIG_CPL_EVT, buf.data(), buf.size());
            });

    // Default mock TerminateBig HCI action
    ON_CALL(hcic_interface_, TerminateBig)
        .WillByDefault([](auto big_handle, uint8_t reason) {
          std::vector<uint8_t> buf(2);
          uint8_t* p = buf.data();
          UINT8_TO_STREAM(p, big_handle);
          UINT8_TO_STREAM(p, reason);

          IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                                    buf.data(), buf.size());
        });

    // Default mock SetupIsoDataPath HCI action
    ON_CALL(hcic_interface_, SetupIsoDataPath)
        .WillByDefault(
            [](uint16_t iso_handle, uint8_t /* data_path_dir */,
               uint8_t /* data_path_id */, uint8_t /* codec_id_format */,
               uint16_t /* codec_id_company */, uint16_t /* codec_id_vendor */,
               uint32_t /* controller_delay */,
               std::vector<uint8_t> /* codec_conf */,
               base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
              std::vector<uint8_t> buf(3);
              uint8_t* p = buf.data();
              UINT8_TO_STREAM(p, HCI_SUCCESS);
              UINT16_TO_STREAM(p, iso_handle);

              std::move(cb).Run(buf.data(), buf.size());
            });

    // Default mock RemoveIsoDataPath HCI action
    ON_CALL(hcic_interface_, RemoveIsoDataPath)
        .WillByDefault([](uint16_t iso_handle, uint8_t data_path_dir,
                          base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
          std::vector<uint8_t> buf(3);
          uint8_t* p = buf.data();
          UINT8_TO_STREAM(p, HCI_SUCCESS);
          UINT16_TO_STREAM(p, iso_handle);

          std::move(cb).Run(buf.data(), buf.size());
        });
  }

  virtual void CleanupIsoManager() {
    manager_instance_->Stop();
    manager_instance_ = nullptr;
  }

  static const bluetooth::hci::iso_manager::big_create_params kDefaultBigParams;
  static const bluetooth::hci::iso_manager::cig_create_params kDefaultCigParams;
  static const bluetooth::hci::iso_manager::cig_create_params
      kDefaultCigParams2;
  static const bluetooth::hci::iso_manager::cig_create_cmpl_evt
      kDefaultCigParamsEvt;
  static const bluetooth::hci::iso_manager::big_create_cmpl_evt
      kDefaultBigParamsEvt;
  static const bluetooth::hci::iso_manager::iso_data_path_params
      kDefaultIsoDataPathParams;

  bluetooth::hci::iso_manager::cig_create_cmpl_evt
      volatile_test_cig_create_cmpl_evt_;
  bluetooth::hci::iso_manager::big_create_cmpl_evt
      volatile_test_big_params_evt_;

  IsoManager* manager_instance_;
  bluetooth::shim::MockIsoInterface iso_interface_;
  hcic::MockHcicInterface hcic_interface_;
  controller::MockControllerInterface controller_interface_;

  std::unique_ptr<MockBigCallbacks> big_callbacks_;
  std::unique_ptr<MockCigCallbacks> cig_callbacks_;
  void (*iso_active_callback)(bool) = [](bool active) { IsIsoActive = active; };
};

const bluetooth::hci::iso_manager::cig_create_cmpl_evt
    IsoManagerTest::kDefaultCigParamsEvt = {
        .status = 0x00,
        .cig_id = 128,
        .conn_handles = std::vector<uint16_t>({0x0EFF, 0x00FF}),
};

const bluetooth::hci::iso_manager::big_create_cmpl_evt
    IsoManagerTest::kDefaultBigParamsEvt = {
        .status = 0x00,
        .big_id = 0,
        .big_sync_delay = 0x0080de,
        .transport_latency_big = 0x00cefe,
        .phy = 0x02,
        .nse = 4,
        .bn = 1,
        .pto = 0,
        .irc = 4,
        .max_pdu = 108,
        .iso_interval = 6,
        .conn_handles = std::vector<uint16_t>({0x0EFE, 0x0E00}),
};

const bluetooth::hci::iso_manager::iso_data_path_params
    IsoManagerTest::kDefaultIsoDataPathParams = {
        .data_path_dir = bluetooth::hci::iso_manager::kIsoDataPathDirectionOut,
        .data_path_id = bluetooth::hci::iso_manager::kIsoDataPathHci,
        .codec_id_format = 0x06,
        .codec_id_company = 0,
        .codec_id_vendor = 0,
        .controller_delay = 0,
        .codec_conf = {0x02, 0x01, 0x02},
};

const bluetooth::hci::iso_manager::big_create_params
    IsoManagerTest::kDefaultBigParams = {
        .adv_handle = 0x00,
        .num_bis = 2,
        .sdu_itv = 0x002710,
        .max_sdu_size = 108,
        .max_transport_latency = 0x3c,
        .rtn = 3,
        .phy = 0x02,
        .packing = 0x00,
        .framing = 0x00,
        .enc = 0,
        .enc_code = std::array<uint8_t, 16>({0}),
};

const bluetooth::hci::iso_manager::cig_create_params
    IsoManagerTest::kDefaultCigParams = {
        .sdu_itv_mtos = 0x00002710,
        .sdu_itv_stom = 0x00002711,
        .sca = bluetooth::hci::iso_manager::kIsoSca0To20Ppm,
        .packing = 0x00,
        .framing = 0x01,
        .max_trans_lat_stom = 0x000A,
        .max_trans_lat_mtos = 0x0009,
        .cis_cfgs =
            {
                // CIS #1
                {
                    .cis_id = 1,
                    .max_sdu_size_mtos = 0x0028,
                    .max_sdu_size_stom = 0x0027,
                    .phy_mtos = 0x04,
                    .phy_stom = 0x03,
                    .rtn_mtos = 0x02,
                    .rtn_stom = 0x01,
                },
                // CIS #2
                {
                    .cis_id = 2,
                    .max_sdu_size_mtos = 0x0029,
                    .max_sdu_size_stom = 0x002A,
                    .phy_mtos = 0x09,
                    .phy_stom = 0x08,
                    .rtn_mtos = 0x07,
                    .rtn_stom = 0x06,
                },
            },
};

const bluetooth::hci::iso_manager::cig_create_params
    IsoManagerTest::kDefaultCigParams2 = {
        .sdu_itv_mtos = 0x00002709,
        .sdu_itv_stom = 0x00002700,
        .sca = bluetooth::hci::iso_manager::kIsoSca0To20Ppm,
        .packing = 0x01,
        .framing = 0x00,
        .max_trans_lat_stom = 0x000B,
        .max_trans_lat_mtos = 0x0006,
        .cis_cfgs =
            {
                // CIS #1
                {
                    .cis_id = 1,
                    .max_sdu_size_mtos = 0x0022,
                    .max_sdu_size_stom = 0x0022,
                    .phy_mtos = 0x01,
                    .phy_stom = 0x02,
                    .rtn_mtos = 0x02,
                    .rtn_stom = 0x01,
                },
                // CIS #2
                {
                    .cis_id = 2,
                    .max_sdu_size_mtos = 0x002A,
                    .max_sdu_size_stom = 0x002B,
                    .phy_mtos = 0x06,
                    .phy_stom = 0x06,
                    .rtn_mtos = 0x07,
                    .rtn_stom = 0x07,
                },
            },
};

class IsoManagerDeathTest : public IsoManagerTest {};

class IsoManagerDeathTestNoInit : public IsoManagerTest {
 protected:
  void InitIsoManager() override { /* DO NOTHING */
  }

  void CleanupIsoManager() override { /* DO NOTHING */
  }
};

class IsoManagerDeathTestNoCleanup : public IsoManagerTest {
 protected:
  void CleanupIsoManager() override { /* DO NOTHING */
  }
};

bool operator==(const EXT_CIS_CFG& x, const EXT_CIS_CFG& y) {
  return ((x.cis_id == y.cis_id) &&
          (x.max_sdu_size_mtos == y.max_sdu_size_mtos) &&
          (x.max_sdu_size_stom == y.max_sdu_size_stom) &&
          (x.phy_mtos == y.phy_mtos) && (x.phy_stom == y.phy_stom) &&
          (x.rtn_mtos == y.rtn_mtos) && (x.rtn_stom == y.rtn_stom));
}

bool operator==(
    const struct bluetooth::hci::iso_manager::cig_create_params& x,
    const struct bluetooth::hci::iso_manager::cig_create_params& y) {
  return ((x.sdu_itv_mtos == y.sdu_itv_mtos) &&
          (x.sdu_itv_stom == y.sdu_itv_stom) && (x.sca == y.sca) &&
          (x.packing == y.packing) && (x.framing == y.framing) &&
          (x.max_trans_lat_stom == y.max_trans_lat_stom) &&
          (x.max_trans_lat_mtos == y.max_trans_lat_mtos) &&
          std::is_permutation(x.cis_cfgs.begin(), x.cis_cfgs.end(),
                              y.cis_cfgs.begin()));
}

bool operator==(
    const struct bluetooth::hci::iso_manager::big_create_params& x,
    const struct bluetooth::hci::iso_manager::big_create_params& y) {
  return ((x.adv_handle == y.adv_handle) && (x.num_bis == y.num_bis) &&
          (x.sdu_itv == y.sdu_itv) && (x.max_sdu_size == y.max_sdu_size) &&
          (x.max_transport_latency == y.max_transport_latency) &&
          (x.rtn == y.rtn) && (x.phy == y.phy) && (x.packing == y.packing) &&
          (x.framing == y.framing) && (x.enc == y.enc) &&
          (x.enc_code == y.enc_code));
}

namespace iso_matchers {
MATCHER_P(Eq, value, "") { return (arg == value); }
MATCHER_P2(EqPointedArray, value, len, "") {
  return (!std::memcmp(arg, value, len));
}
}  // namespace iso_matchers

TEST_F(IsoManagerTest, SingletonAccess) {
  auto* iso_mgr = IsoManager::GetInstance();
  ASSERT_EQ(manager_instance_, iso_mgr);
}

TEST_F(IsoManagerTest, RegisterCallbacks) {
  auto* iso_mgr = IsoManager::GetInstance();
  ASSERT_EQ(manager_instance_, iso_mgr);

  iso_mgr->RegisterBigCallbacks(big_callbacks_.get());
  iso_mgr->RegisterCigCallbacks(cig_callbacks_.get());
  iso_mgr->RegisterOnIsoTrafficActiveCallback(iso_active_callback);
}

TEST_F(IsoManagerDeathTestNoInit, RegisterNullBigCallbacks) {
  IsoManager::GetInstance()->Start();

  ASSERT_EXIT(IsoManager::GetInstance()->RegisterBigCallbacks(nullptr),
              ::testing::KilledBySignal(SIGABRT), "Invalid BIG callbacks");

  // Manual cleanup as IsoManagerDeathTest has no 'generic' cleanup
  IsoManager::GetInstance()->Stop();
}

TEST_F(IsoManagerDeathTestNoInit, RegisterNullCigCallbacks) {
  IsoManager::GetInstance()->Start();

  ASSERT_EXIT(IsoManager::GetInstance()->RegisterCigCallbacks(nullptr),
              ::testing::KilledBySignal(SIGABRT), "Invalid CIG callbacks");

  // Manual cleanup as IsoManagerDeathTest has no 'generic' cleanup
  IsoManager::GetInstance()->Stop();
}

// Verify hci layer being called by the Iso Manager
TEST_F(IsoManagerTest, CreateCigHciCall) {
  for (uint8_t i = 220; i != 60; ++i) {
    EXPECT_CALL(hcic_interface_,
                SetCigParams(i, iso_matchers::Eq(kDefaultCigParams), _))
        .Times(1)
        .RetiresOnSaturation();
    IsoManager::GetInstance()->CreateCig(i, kDefaultCigParams);
  }
}

// Check handling create cig request twice with the same CIG id
TEST_F(IsoManagerDeathTest, CreateSameCigTwice) {
  bluetooth::hci::iso_manager::cig_create_cmpl_evt evt;
  evt.status = 0x01;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_create_cmpl_evt*>(
            data);
        return 0;
      });

  volatile_test_cig_create_cmpl_evt_.cig_id = 127;
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  ASSERT_EQ(evt.status, HCI_SUCCESS);

  // Second call with the same CIG ID should fail
  ASSERT_EXIT(IsoManager::GetInstance()->CreateCig(
                  volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams),
              ::testing::KilledBySignal(SIGABRT), "already exists");
}

// Check for handling invalid length response from the faulty controller
TEST_F(IsoManagerDeathTest, CreateCigCallbackInvalidRspPacket) {
  uint8_t hci_mock_rsp_buffer[] = {0x00, 0x00};
  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  ASSERT_EXIT(IsoManager::GetInstance()->CreateCig(128, kDefaultCigParams),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

// Check for handling invalid length response from the faulty controller
TEST_F(IsoManagerDeathTest, CreateCigCallbackInvalidRspPacket2) {
  uint8_t hci_mock_rsp_buffer[] = {0x00, 0x00, 0x02, 0x01, 0x00};
  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  ASSERT_EXIT(IsoManager::GetInstance()->CreateCig(128, kDefaultCigParams),
              ::testing::KilledBySignal(SIGABRT), "Invalid CIS count");
}

// Check if IsoManager properly handles error responses from HCI layer
TEST_F(IsoManagerTest, CreateCigCallbackInvalidStatus) {
  uint8_t rsp_cig_id = 128;
  uint8_t rsp_status = 0x01;
  uint8_t rsp_cis_cnt = 3;
  uint8_t hci_mock_rsp_buffer[] = {rsp_status, rsp_cig_id, rsp_cis_cnt};

  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  bluetooth::hci::iso_manager::cig_create_cmpl_evt evt;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_create_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->CreateCig(rsp_cig_id, kDefaultCigParams);
  ASSERT_EQ(evt.cig_id, rsp_cig_id);
  ASSERT_EQ(evt.status, rsp_status);
  ASSERT_TRUE(evt.conn_handles.empty());
}

// Check valid callback response
TEST_F(IsoManagerTest, CreateCigCallbackValid) {
  bluetooth::hci::iso_manager::cig_create_cmpl_evt evt;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_create_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  ASSERT_EQ(evt.cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.status, volatile_test_cig_create_cmpl_evt_.status);
  ASSERT_EQ(evt.conn_handles.size(), 2u);
  ASSERT_TRUE(
      std::is_permutation(evt.conn_handles.begin(), evt.conn_handles.end(),
                          std::vector<uint16_t>({0x0EFF, 0x00FF}).begin()));
}

TEST_F(IsoManagerTest, CreateCigLateArrivingCallback) {
  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault([&](auto cig_id, auto,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        iso_cb = std::move(cb);
      });

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(*cig_callbacks_, OnCigEvent(_, _)).Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  uint8_t hci_mock_rsp_buffer[3 + sizeof(uint16_t) *
                                      volatile_test_cig_create_cmpl_evt_
                                          .conn_handles.size()];
  uint8_t* p = hci_mock_rsp_buffer;
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.status);
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.cig_id);
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.conn_handles.size());
  for (auto handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    UINT16_TO_STREAM(p, handle);
  }
  std::move(iso_cb).Run(
      hci_mock_rsp_buffer,
      3 + sizeof(uint16_t) *
              volatile_test_cig_create_cmpl_evt_.conn_handles.size());
}

// Check if CIG reconfigure triggers HCI layer call
TEST_F(IsoManagerTest, ReconfigureCigHciCall) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  EXPECT_CALL(hcic_interface_,
              SetCigParams(volatile_test_cig_create_cmpl_evt_.cig_id,
                           iso_matchers::Eq(kDefaultCigParams), _))
      .Times(1);
  IsoManager::GetInstance()->ReconfigureCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
}

// Verify handlidng invalid call - reconfiguring invalid CIG
TEST_F(IsoManagerDeathTest, ReconfigureCigWithNoSuchCig) {
  ASSERT_EXIT(IsoManager::GetInstance()->ReconfigureCig(128, kDefaultCigParams),
              ::testing::KilledBySignal(SIGABRT), "No such cig");
}

TEST_F(IsoManagerDeathTest, ReconfigureCigInvalidRspPacket) {
  uint8_t hci_mock_rsp_buffer[] = {0x00, 0x00};

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });
  ASSERT_EXIT(IsoManager::GetInstance()->ReconfigureCig(
                  volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerDeathTest, ReconfigureCigInvalidRspPacket2) {
  uint8_t hci_mock_rsp_buffer[] = {0x00, 0x00, 0x02, 0x01, 0x00};

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });
  ASSERT_EXIT(
      IsoManager::GetInstance()->ReconfigureCig(
          volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams2),
      ::testing::KilledBySignal(SIGABRT), "Invalid CIS count");
}

TEST_F(IsoManagerTest, ReconfigureCigInvalidStatus) {
  uint8_t rsp_cig_id = 128;
  uint8_t rsp_status = 0x01;
  uint8_t rsp_cis_cnt = 3;
  uint8_t hci_mock_rsp_buffer[] = {rsp_status, rsp_cig_id, rsp_cis_cnt};

  IsoManager::GetInstance()->CreateCig(rsp_cig_id, kDefaultCigParams);

  // Set-up the invalid response
  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  bluetooth::hci::iso_manager::cig_create_cmpl_evt evt;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnReconfigureCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_create_cmpl_evt*>(
            data);
        return 0;
      });
  IsoManager::GetInstance()->ReconfigureCig(rsp_cig_id, kDefaultCigParams2);

  ASSERT_EQ(evt.cig_id, rsp_cig_id);
  ASSERT_EQ(evt.status, rsp_status);
  ASSERT_TRUE(evt.conn_handles.empty());
}

TEST_F(IsoManagerTest, ReconfigureCigValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cig_create_cmpl_evt evt;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnReconfigureCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_create_cmpl_evt*>(
            data);
        return 0;
      });

  // Verify valid reconfiguration request
  IsoManager::GetInstance()->ReconfigureCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams2);
  ASSERT_EQ(evt.cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.status, volatile_test_cig_create_cmpl_evt_.status);
  ASSERT_TRUE(std::is_permutation(
      evt.conn_handles.begin(), evt.conn_handles.end(),
      volatile_test_cig_create_cmpl_evt_.conn_handles.begin()));
}

TEST_F(IsoManagerTest, ReconfigureCigLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  ON_CALL(hcic_interface_, SetCigParams)
      .WillByDefault([&](auto cig_id, auto,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        iso_cb = std::move(cb);
      });
  IsoManager::GetInstance()->ReconfigureCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams2);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(*cig_callbacks_, OnCigEvent(_, _)).Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  uint8_t hci_mock_rsp_buffer[3 + sizeof(uint16_t) *
                                      volatile_test_cig_create_cmpl_evt_
                                          .conn_handles.size()];
  uint8_t* p = hci_mock_rsp_buffer;
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.status);
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.cig_id);
  UINT8_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.conn_handles.size());
  for (auto handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    UINT16_TO_STREAM(p, handle);
  }
  std::move(iso_cb).Run(
      hci_mock_rsp_buffer,
      3 + sizeof(uint16_t) *
              volatile_test_cig_create_cmpl_evt_.conn_handles.size());
}

TEST_F(IsoManagerTest, RemoveCigHciCall) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  EXPECT_CALL(hcic_interface_,
              RemoveCig(volatile_test_cig_create_cmpl_evt_.cig_id, _))
      .Times(1);
  IsoManager::GetInstance()->RemoveCig(
      volatile_test_cig_create_cmpl_evt_.cig_id);
}

TEST_F(IsoManagerDeathTest, RemoveCigWithNoSuchCig) {
  ASSERT_EXIT(IsoManager::GetInstance()->RemoveCig(
                  volatile_test_cig_create_cmpl_evt_.cig_id),
              ::testing::KilledBySignal(SIGABRT), "No such cig");
}

TEST_F(IsoManagerDeathTest, RemoveCigForceNoSuchCig) {
  EXPECT_CALL(hcic_interface_,
              RemoveCig(volatile_test_cig_create_cmpl_evt_.cig_id, _))
      .Times(1);
  IsoManager::GetInstance()->RemoveCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, true);
}

TEST_F(IsoManagerDeathTest, RemoveSameCigTwice) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, RemoveCig)
      .WillByDefault(
          [this](auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            uint8_t hci_mock_rsp_buffer[2];
            uint8_t* p = hci_mock_rsp_buffer;

            UINT8_TO_STREAM(p, HCI_SUCCESS);
            UINT8_TO_STREAM(p, this->volatile_test_cig_create_cmpl_evt_.cig_id);

            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  IsoManager::GetInstance()->RemoveCig(
      volatile_test_cig_create_cmpl_evt_.cig_id);

  ASSERT_EXIT(IsoManager::GetInstance()->RemoveCig(
                  volatile_test_cig_create_cmpl_evt_.cig_id),
              ::testing::KilledBySignal(SIGABRT), "No such cig");
}

TEST_F(IsoManagerDeathTest, RemoveCigInvalidRspPacket) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, RemoveCig)
      .WillByDefault([](auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        uint8_t hci_mock_rsp_buffer[] = {0x00};  // status byte only

        std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
        return 0;
      });
  ASSERT_EXIT(IsoManager::GetInstance()->RemoveCig(
                  volatile_test_cig_create_cmpl_evt_.cig_id),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerTest, RemoveCigInvalidStatus) {
  uint8_t rsp_status = 0x02;
  uint8_t hci_mock_rsp_buffer[] = {rsp_status,
                                   volatile_test_cig_create_cmpl_evt_.cig_id};

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, RemoveCig)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  bluetooth::hci::iso_manager::cig_remove_cmpl_evt evt;
  ON_CALL(*cig_callbacks_,
          OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnRemoveCmpl, _))
      .WillByDefault([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_remove_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->RemoveCig(
      volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.status, rsp_status);
}

TEST_F(IsoManagerTest, RemoveCigValid) {
  uint8_t hci_mock_rsp_buffer[] = {HCI_SUCCESS,
                                   volatile_test_cig_create_cmpl_evt_.cig_id};

  ASSERT_EQ(IsIsoActive, false);
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  ASSERT_EQ(IsIsoActive, true);

  ON_CALL(hcic_interface_, RemoveCig)
      .WillByDefault(
          [&hci_mock_rsp_buffer](
              auto, base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            std::move(cb).Run(hci_mock_rsp_buffer, sizeof(hci_mock_rsp_buffer));
            return 0;
          });

  bluetooth::hci::iso_manager::cig_remove_cmpl_evt evt;
  EXPECT_CALL(
      *cig_callbacks_,
      OnCigEvent(bluetooth::hci::iso_manager::kIsoEventCigOnRemoveCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::cig_remove_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->RemoveCig(
      volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
  ASSERT_EQ(evt.status, HCI_SUCCESS);
  ASSERT_EQ(IsIsoActive, false);
}

TEST_F(IsoManagerTest, EstablishCisHciCall) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }

  EXPECT_CALL(hcic_interface_,
              CreateCis(2,
                        iso_matchers::EqPointedArray(
                            params.conn_pairs.data(),
                            params.conn_pairs.size() *
                                sizeof(params.conn_pairs.data()[0])),
                        _))
      .Times(1);
  IsoManager::GetInstance()->EstablishCis(params);
}

TEST_F(IsoManagerDeathTest, EstablishCisWithNoSuchCis) {
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }

  ASSERT_EXIT(
      IsoManager::GetInstance()->IsoManager::GetInstance()->EstablishCis(
          params),
      ::testing::KilledBySignal(SIGABRT), "No such cis");
}

TEST_F(IsoManagerDeathTest, ConnectSameCisTwice) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  ASSERT_EXIT(
      IsoManager::GetInstance()->IsoManager::GetInstance()->EstablishCis(
          params),
      ::testing::KilledBySignal(SIGABRT), "already connected or connecting");
}

TEST_F(IsoManagerDeathTest, EstablishCisInvalidResponsePacket) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  ON_CALL(hcic_interface_, CreateCis)
      .WillByDefault([this](uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                            base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
          std::vector<uint8_t> buf(27);
          uint8_t* p = buf.data();
          UINT8_TO_STREAM(p, HCI_SUCCESS);
          UINT16_TO_STREAM(p, handle);
          UINT24_TO_STREAM(p, 0xEA);    // CIG sync delay
          UINT24_TO_STREAM(p, 0xEB);    // CIS sync delay
          UINT24_TO_STREAM(p, 0xEC);    // transport latency mtos
          UINT24_TO_STREAM(p, 0xED);    // transport latency stom
          UINT8_TO_STREAM(p, 0x01);     // phy mtos
          UINT8_TO_STREAM(p, 0x02);     // phy stom
          UINT8_TO_STREAM(p, 0x01);     // nse
          UINT8_TO_STREAM(p, 0x02);     // bn mtos
          UINT8_TO_STREAM(p, 0x03);     // bn stom
          UINT8_TO_STREAM(p, 0x04);     // ft mtos
          UINT8_TO_STREAM(p, 0x05);     // ft stom
          UINT16_TO_STREAM(p, 0x00FA);  // Max PDU mtos
          UINT16_TO_STREAM(p, 0x00FB);  // Max PDU stom

          IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_CIS_EST_EVT,
                                                    buf.data(), buf.size());
        }
      });

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }

  ASSERT_EXIT(
      IsoManager::GetInstance()->IsoManager::GetInstance()->EstablishCis(
          params),
      ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerTest, EstablishCisInvalidCommandStatus) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  uint16_t invalid_status = 0x0001;

  ON_CALL(hcic_interface_, CreateCis)
      .WillByDefault([invalid_status](
                         uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        std::move(cb).Run((uint8_t*)&invalid_status, sizeof(invalid_status));
        return 0;
      });

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(kDefaultCigParams.cis_cfgs.size())
      .WillRepeatedly([this, invalid_status](uint8_t type, void* data) {
        bluetooth::hci::iso_manager::cis_establish_cmpl_evt* evt =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        ASSERT_EQ(evt->status, invalid_status);
        ASSERT_TRUE(
            std::find(volatile_test_cig_create_cmpl_evt_.conn_handles.begin(),
                      volatile_test_cig_create_cmpl_evt_.conn_handles.end(),
                      evt->cis_conn_hdl) !=
            volatile_test_cig_create_cmpl_evt_.conn_handles.end());
      });

  // Establish all CISes
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);
}

TEST_F(IsoManagerTest, EstablishCisInvalidStatus) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  uint8_t invalid_status = 0x01;

  ON_CALL(hcic_interface_, CreateCis)
      .WillByDefault([this, invalid_status](
                         uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
          std::vector<uint8_t> buf(28);
          uint8_t* p = buf.data();
          UINT8_TO_STREAM(p, invalid_status);
          UINT16_TO_STREAM(p, handle);
          UINT24_TO_STREAM(p, 0xEA);    // CIG sync delay
          UINT24_TO_STREAM(p, 0xEB);    // CIS sync delay
          UINT24_TO_STREAM(p, 0xEC);    // transport latency mtos
          UINT24_TO_STREAM(p, 0xED);    // transport latency stom
          UINT8_TO_STREAM(p, 0x01);     // phy mtos
          UINT8_TO_STREAM(p, 0x02);     // phy stom
          UINT8_TO_STREAM(p, 0x01);     // nse
          UINT8_TO_STREAM(p, 0x02);     // bn mtos
          UINT8_TO_STREAM(p, 0x03);     // bn stom
          UINT8_TO_STREAM(p, 0x04);     // ft mtos
          UINT8_TO_STREAM(p, 0x05);     // ft stom
          UINT16_TO_STREAM(p, 0x00FA);  // Max PDU mtos
          UINT16_TO_STREAM(p, 0x00FB);  // Max PDU stom
          UINT16_TO_STREAM(p, 0x0C60);  // ISO interval

          IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_CIS_EST_EVT,
                                                    buf.data(), buf.size());
        }
      });

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(kDefaultCigParams.cis_cfgs.size())
      .WillRepeatedly([this, invalid_status](uint8_t type, void* data) {
        bluetooth::hci::iso_manager::cis_establish_cmpl_evt* evt =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        ASSERT_EQ(evt->status, invalid_status);
        ASSERT_TRUE(
            std::find(volatile_test_cig_create_cmpl_evt_.conn_handles.begin(),
                      volatile_test_cig_create_cmpl_evt_.conn_handles.end(),
                      evt->cis_conn_hdl) !=
            volatile_test_cig_create_cmpl_evt_.conn_handles.end());
      });

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);
}

TEST_F(IsoManagerTest, EstablishCisValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(kDefaultCigParams.cis_cfgs.size())
      .WillRepeatedly([this](uint8_t type, void* data) {
        bluetooth::hci::iso_manager::cis_establish_cmpl_evt* evt =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        ASSERT_EQ(evt->status, HCI_SUCCESS);
        ASSERT_TRUE(
            std::find(volatile_test_cig_create_cmpl_evt_.conn_handles.begin(),
                      volatile_test_cig_create_cmpl_evt_.conn_handles.end(),
                      evt->cis_conn_hdl) !=
            volatile_test_cig_create_cmpl_evt_.conn_handles.end());
      });

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);
}

TEST_F(IsoManagerTest, EstablishCisLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  EXT_CIS_CREATE_CFG cis_create_cfg;
  uint8_t cis_num = 0;
  ON_CALL(hcic_interface_, CreateCis)
      .WillByDefault([&](uint8_t num_cis, const EXT_CIS_CREATE_CFG* cis_cfg,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        cis_create_cfg = *cis_cfg;
        cis_num = num_cis;
        iso_cb = std::move(cb);
      });

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  // Command complete with error will trigger the callback without
  // injecting any additional HCI events
  std::vector<uint8_t> buf(1);
  uint8_t* p = buf.data();
  UINT8_TO_STREAM(p, 0x01);  // status
  std::move(iso_cb).Run(buf.data(), buf.size());
}

TEST_F(IsoManagerTest, ReconnectCisValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  // trigger HCI disconnection event
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->HandleDisconnect(handle, 0x16);
  }

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(kDefaultCigParams.cis_cfgs.size())
      .WillRepeatedly([this](uint8_t type, void* data) {
        bluetooth::hci::iso_manager::cis_establish_cmpl_evt* evt =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        ASSERT_EQ(evt->status, HCI_SUCCESS);
        ASSERT_TRUE(
            std::find(volatile_test_cig_create_cmpl_evt_.conn_handles.begin(),
                      volatile_test_cig_create_cmpl_evt_.conn_handles.end(),
                      evt->cis_conn_hdl) !=
            volatile_test_cig_create_cmpl_evt_.conn_handles.end());
      });
  IsoManager::GetInstance()->EstablishCis(params);
}

TEST_F(IsoManagerTest, DisconnectCisHciCall) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    EXPECT_CALL(hcic_interface_, Disconnect(handle, 0x16))
        .Times(1)
        .RetiresOnSaturation();
    IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(handle,
                                                                        0x16);
  }
}

TEST_F(IsoManagerDeathTest, DisconnectCisWithNoSuchCis) {
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    ASSERT_EXIT(
        IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(
            handle, 0x16),
        ::testing::KilledBySignal(SIGABRT), "No such cis");
  }
}

TEST_F(IsoManagerDeathTest, DisconnectSameCisTwice) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(handle,
                                                                        0x16);
  }

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    ASSERT_EXIT(
        IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(
            handle, 0x16),
        ::testing::KilledBySignal(SIGABRT), "Not connected");
  }
}

TEST_F(IsoManagerTest, DisconnectCisValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  uint8_t disconnect_reason = 0x16;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    EXPECT_CALL(*cig_callbacks_, OnCisEvent)
        .WillOnce([this, handle, disconnect_reason](uint8_t event_code,
                                                    void* data) {
          ASSERT_EQ(event_code,
                    bluetooth::hci::iso_manager::kIsoEventCisDisconnected);
          auto* event =
              static_cast<bluetooth::hci::iso_manager::cis_disconnected_evt*>(
                  data);
          ASSERT_EQ(event->reason, disconnect_reason);
          ASSERT_EQ(event->cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
          ASSERT_EQ(event->cis_conn_hdl, handle);
        })
        .RetiresOnSaturation();
    IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(
        handle, disconnect_reason);
  }
}

// Check if we properly ignore not ISO related disconnect events
TEST_F(IsoManagerDeathTest, DisconnectCisInvalidResponse) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  // Make the HCI layer send invalid handles in disconnect event
  ON_CALL(hcic_interface_, Disconnect)
      .WillByDefault([](uint16_t handle, uint8_t reason) {
        IsoManager::GetInstance()->HandleDisconnect(handle + 1, reason);
      });

  // We don't expect any calls as these are not ISO handles
  ON_CALL(*cig_callbacks_,
          OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDisconnected, _))
      .WillByDefault([](uint8_t event_code, void* data) { FAIL(); });

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->IsoManager::GetInstance()->DisconnectCis(handle,
                                                                        0x16);
  }
}

TEST_F(IsoManagerTest, CreateBigHciCall) {
  for (uint8_t i = 220; i != 60; ++i) {
    EXPECT_CALL(hcic_interface_,
                CreateBig(i, iso_matchers::Eq(kDefaultBigParams)))
        .Times(1)
        .RetiresOnSaturation();
    IsoManager::GetInstance()->CreateBig(i, kDefaultBigParams);
  }
}

TEST_F(IsoManagerTest, CreateBigValid) {
  bluetooth::hci::iso_manager::big_create_cmpl_evt evt;
  evt.status = 0x01;
  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::big_create_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->CreateBig(0x01, kDefaultBigParams);
  ASSERT_EQ(evt.status, HCI_SUCCESS);
}

TEST_F(IsoManagerDeathTest, CreateBigInvalidResponsePacket) {
  ON_CALL(hcic_interface_, CreateBig)
      .WillByDefault(
          [](auto big_handle,
             bluetooth::hci::iso_manager::big_create_params big_params) {
            std::vector<uint8_t> buf(18);
            uint8_t* p = buf.data();
            UINT8_TO_STREAM(p, 0x00);
            UINT8_TO_STREAM(p, big_handle);

            UINT24_TO_STREAM(p, 0x0080de);       // big_sync_delay
            UINT24_TO_STREAM(p, 0x00cefe);       // transport_latency_big
            UINT8_TO_STREAM(p, big_params.phy);  // phy
            UINT8_TO_STREAM(p, 4);               // nse
            UINT8_TO_STREAM(p, 1);               // bn
            UINT8_TO_STREAM(p, 0);               // pto
            UINT8_TO_STREAM(p, 4);               // irc
            UINT16_TO_STREAM(p, 108);            // max_pdu
            UINT16_TO_STREAM(p, 6);              // iso_interval
            UINT8_TO_STREAM(p, 0);               // num BISes

            IsoManager::GetInstance()->HandleHciEvent(
                HCI_BLE_CREATE_BIG_CPL_EVT, buf.data(), buf.size());
          });

  ASSERT_EXIT(IsoManager::GetInstance()->CreateBig(0x01, kDefaultBigParams),
              ::testing::KilledBySignal(SIGABRT),
              "num_bis != 0. Bis count is 0");
}

TEST_F(IsoManagerDeathTest, CreateBigInvalidResponsePacket2) {
  ON_CALL(hcic_interface_, CreateBig)
      .WillByDefault(
          [](auto big_handle,
             bluetooth::hci::iso_manager::big_create_params big_params) {
            std::vector<uint8_t> buf(18);
            uint8_t* p = buf.data();
            UINT8_TO_STREAM(p, 0x00);
            UINT8_TO_STREAM(p, big_handle);

            UINT24_TO_STREAM(p, 0x0080de);       // big_sync_delay
            UINT24_TO_STREAM(p, 0x00cefe);       // transport_latency_big
            UINT8_TO_STREAM(p, big_params.phy);  // phy
            UINT8_TO_STREAM(p, 4);               // nse
            UINT8_TO_STREAM(p, 1);               // bn
            UINT8_TO_STREAM(p, 0);               // pto
            UINT8_TO_STREAM(p, 4);               // irc
            UINT16_TO_STREAM(p, 108);            // max_pdu
            UINT16_TO_STREAM(p, 6);              // iso_interval
            UINT8_TO_STREAM(p, big_params.num_bis);

            IsoManager::GetInstance()->HandleHciEvent(
                HCI_BLE_CREATE_BIG_CPL_EVT, buf.data(), buf.size());
          });

  ASSERT_EXIT(IsoManager::GetInstance()->CreateBig(0x01, kDefaultBigParams),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerTest, CreateBigInvalidStatus) {
  bluetooth::hci::iso_manager::big_create_cmpl_evt evt;
  evt.status = 0x00;
  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::big_create_cmpl_evt*>(
            data);
        return 0;
      });

  ON_CALL(hcic_interface_, CreateBig)
      .WillByDefault(
          [](auto big_handle,
             bluetooth::hci::iso_manager::big_create_params big_params) {
            std::vector<uint8_t> buf(big_params.num_bis * sizeof(uint16_t) +
                                     18);
            uint8_t* p = buf.data();
            UINT8_TO_STREAM(p, 0x01);
            UINT8_TO_STREAM(p, big_handle);

            UINT24_TO_STREAM(p, 0x0080de);       // big_sync_delay
            UINT24_TO_STREAM(p, 0x00cefe);       // transport_latency_big
            UINT8_TO_STREAM(p, big_params.phy);  // phy
            UINT8_TO_STREAM(p, 4);               // nse
            UINT8_TO_STREAM(p, 1);               // bn
            UINT8_TO_STREAM(p, 0);               // pto
            UINT8_TO_STREAM(p, 4);               // irc
            UINT16_TO_STREAM(p, 108);            // max_pdu
            UINT16_TO_STREAM(p, 6);              // iso_interval

            UINT8_TO_STREAM(p, big_params.num_bis);
            static uint8_t conn_hdl = 0x01;
            for (auto i = 0; i < big_params.num_bis; ++i) {
              UINT16_TO_STREAM(p, conn_hdl++);
            }

            IsoManager::GetInstance()->HandleHciEvent(
                HCI_BLE_CREATE_BIG_CPL_EVT, buf.data(), buf.size());
          });

  IsoManager::GetInstance()->CreateBig(0x01, kDefaultBigParams);
  ASSERT_EQ(evt.status, 0x01);
  ASSERT_EQ(evt.big_id, 0x01);
  ASSERT_EQ(evt.conn_handles.size(), kDefaultBigParams.num_bis);
}

TEST_F(IsoManagerDeathTest, CreateSameBigTwice) {
  bluetooth::hci::iso_manager::big_create_cmpl_evt evt;
  evt.status = 0x01;
  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnCreateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt = *static_cast<bluetooth::hci::iso_manager::big_create_cmpl_evt*>(
            data);
        return 0;
      });

  IsoManager::GetInstance()->CreateBig(0x01, kDefaultBigParams);
  ASSERT_EQ(evt.status, HCI_SUCCESS);
  ASSERT_EQ(evt.big_id, 0x01);
  ASSERT_EQ(evt.conn_handles.size(), kDefaultBigParams.num_bis);
}

TEST_F(IsoManagerTest, TerminateBigHciCall) {
  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  EXPECT_CALL(hcic_interface_, TerminateBig(big_id, reason)).Times(1);
  IsoManager::GetInstance()->TerminateBig(big_id, reason);
}

TEST_F(IsoManagerDeathTest, TerminateSameBigTwice) {
  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnTerminateCmpl, _));

  IsoManager::GetInstance()->TerminateBig(big_id, reason);
  ASSERT_EXIT(IsoManager::GetInstance()->TerminateBig(big_id, reason),
              ::testing::KilledBySignal(SIGABRT), "No such big");
}

TEST_F(IsoManagerDeathTest, TerminateBigNoSuchBig) {
  const uint8_t big_id = 0x01;
  const uint8_t reason = 0x16;  // Terminated by local host

  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnCreateCmpl, _));
  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);

  ASSERT_EXIT(IsoManager::GetInstance()->TerminateBig(big_id + 1, reason),
              ::testing::KilledBySignal(SIGABRT), "No such big");
}

TEST_F(IsoManagerDeathTest, TerminateBigInvalidResponsePacket) {
  ON_CALL(hcic_interface_, TerminateBig)
      .WillByDefault([](auto big_handle, uint8_t reason) {
        std::vector<uint8_t> buf(1);
        uint8_t* p = buf.data();
        UINT8_TO_STREAM(p, reason);

        IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                                  buf.data(), buf.size());
      });

  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  ASSERT_EXIT(IsoManager::GetInstance()->TerminateBig(big_id, reason),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerDeathTest, TerminateBigInvalidResponsePacket2) {
  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host

  ON_CALL(hcic_interface_, TerminateBig)
      .WillByDefault([](auto big_handle, uint8_t reason) {
        std::vector<uint8_t> buf(3);
        uint8_t* p = buf.data();
        UINT8_TO_STREAM(p, reason);

        IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                                  buf.data(), buf.size());
      });

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  ASSERT_EXIT(IsoManager::GetInstance()->TerminateBig(big_id, reason),
              ::testing::KilledBySignal(SIGABRT), "Invalid packet length");
}

TEST_F(IsoManagerTest, TerminateBigInvalidResponseBigId) {
  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host

  ON_CALL(hcic_interface_, TerminateBig)
      .WillByDefault([](auto big_handle, uint8_t reason) {
        std::vector<uint8_t> buf(2);
        uint8_t* p = buf.data();
        UINT8_TO_STREAM(p, reason);
        UINT8_TO_STREAM(p, big_handle + 1);

        IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                                  buf.data(), buf.size());
      });

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  ASSERT_EXIT(IsoManager::GetInstance()->TerminateBig(big_id, reason),
              ::testing::KilledBySignal(SIGABRT), "No such big");
}

TEST_F(IsoManagerTest, TerminateBigValid) {
  const uint8_t big_id = 0x22;
  const uint8_t reason = 0x16;  // Terminated by local host
  bluetooth::hci::iso_manager::big_terminate_cmpl_evt evt;
  ASSERT_EQ(IsIsoActive, false);

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);
  ASSERT_EQ(IsIsoActive, true);

  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnTerminateCmpl, _))
      .WillOnce([&evt](uint8_t type, void* data) {
        evt =
            *static_cast<bluetooth::hci::iso_manager::big_terminate_cmpl_evt*>(
                data);
        return 0;
      });

  IsoManager::GetInstance()->TerminateBig(big_id, reason);
  ASSERT_EQ(evt.big_id, big_id);
  ASSERT_EQ(evt.reason, reason);
  ASSERT_EQ(IsIsoActive, false);
}

TEST_F(IsoManagerTest, SetupIsoDataPathValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;

  // Setup data paths for all CISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kIsoDataPathDirectionIn;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    EXPECT_CALL(*cig_callbacks_,
                OnSetupIsoDataPath(HCI_SUCCESS, handle,
                                   volatile_test_cig_create_cmpl_evt_.cig_id))
        .Times(1)
        .RetiresOnSaturation();

    path_params.data_path_dir =
        (bluetooth::hci::iso_manager::kIsoDataPathDirectionIn + handle) % 2;

    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }

  // Setup data paths for all BISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kIsoDataPathDirectionOut;
  for (auto& handle : volatile_test_big_params_evt_.conn_handles) {
    std::cerr << "setting up BIS data path on conn_hdl: " << int{handle};
    EXPECT_CALL(*big_callbacks_,
                OnSetupIsoDataPath(HCI_SUCCESS, handle,
                                   volatile_test_big_params_evt_.big_id))
        .Times(1)
        .RetiresOnSaturation();

    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }
}

TEST_F(IsoManagerTest, SetupIsoDataPathTwice) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  // Establish CISes
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  // Setup data paths for all CISes twice
  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
    // Should be possible to reconfigure
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }

  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  // Setup data paths for all BISes twice
  for (auto& handle : volatile_test_big_params_evt_.conn_handles) {
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
    // Should be possible to reconfigure
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }
}

TEST_F(IsoManagerTest, SetupIsoDataPathInvalidStatus) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;

  uint8_t setup_datapath_rsp_status = HCI_SUCCESS;
  ON_CALL(hcic_interface_, SetupIsoDataPath)
      .WillByDefault([&setup_datapath_rsp_status](
                         uint16_t iso_handle, uint8_t, uint8_t, uint8_t,
                         uint16_t, uint16_t, uint32_t, std::vector<uint8_t>,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        std::vector<uint8_t> buf(3);
        uint8_t* p = buf.data();
        UINT8_TO_STREAM(p, setup_datapath_rsp_status);
        UINT16_TO_STREAM(p, iso_handle);

        std::move(cb).Run(buf.data(), buf.size());
      });

  // Try to setup data paths for all CISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kIsoDataPathDirectionIn;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    // Mock the response with status != HCI_SUCCESS
    EXPECT_CALL(*cig_callbacks_,
                OnSetupIsoDataPath(0x11, handle,
                                   volatile_test_cig_create_cmpl_evt_.cig_id))
        .Times(1)
        .RetiresOnSaturation();
    setup_datapath_rsp_status = 0x11;
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

    // It should be possible to retry on the same handle after the first
    // failure
    EXPECT_CALL(*cig_callbacks_,
                OnSetupIsoDataPath(HCI_SUCCESS, handle,
                                   volatile_test_cig_create_cmpl_evt_.cig_id))
        .Times(1)
        .RetiresOnSaturation();
    setup_datapath_rsp_status = HCI_SUCCESS;
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }

  // Try to setup data paths for all BISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kIsoDataPathDirectionOut;
  for (auto& handle : volatile_test_big_params_evt_.conn_handles) {
    EXPECT_CALL(
        *big_callbacks_,
        OnSetupIsoDataPath(0x11, handle, volatile_test_big_params_evt_.big_id))
        .Times(1)
        .RetiresOnSaturation();
    setup_datapath_rsp_status = 0x11;
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

    EXPECT_CALL(*big_callbacks_,
                OnSetupIsoDataPath(HCI_SUCCESS, handle,
                                   volatile_test_big_params_evt_.big_id))
        .Times(1)
        .RetiresOnSaturation();
    setup_datapath_rsp_status = HCI_SUCCESS;
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);
  }
}

TEST_F(IsoManagerTest, SetupIsoDataPathLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;

  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  ON_CALL(hcic_interface_, SetupIsoDataPath)
      .WillByDefault(
          [&iso_cb](uint16_t iso_handle, uint8_t /* data_path_dir */,
                    uint8_t /* data_path_id */, uint8_t /* codec_id_format */,
                    uint16_t /* codec_id_company */,
                    uint16_t /* codec_id_vendor */,
                    uint32_t /* controller_delay */,
                    std::vector<uint8_t> /* codec_conf */,
                    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            iso_cb = std::move(cb);
          });
  // Setup and remove data paths for all CISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput;
  auto& handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(*cig_callbacks_, OnSetupIsoDataPath(_, handle, _)).Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  std::vector<uint8_t> buf(3);
  uint8_t* p = buf.data();
  UINT8_TO_STREAM(p, HCI_SUCCESS);
  UINT16_TO_STREAM(p, handle);
  std::move(iso_cb).Run(buf.data(), buf.size());
}

TEST_F(IsoManagerTest, RemoveIsoDataPathValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;

  // Setup and remove data paths for all CISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

    EXPECT_CALL(*cig_callbacks_,
                OnRemoveIsoDataPath(HCI_SUCCESS, handle,
                                    volatile_test_cig_create_cmpl_evt_.cig_id))
        .Times(1)
        .RetiresOnSaturation();
    IsoManager::GetInstance()->RemoveIsoDataPath(handle,
                                                 path_params.data_path_dir);
  }

  // Setup and remove data paths for all BISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kIsoDataPathDirectionOut;
  for (auto& handle : volatile_test_big_params_evt_.conn_handles) {
    std::cerr << "setting up BIS data path on conn_hdl: " << int{handle};
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

    EXPECT_CALL(*big_callbacks_,
                OnRemoveIsoDataPath(HCI_SUCCESS, handle,
                                    volatile_test_big_params_evt_.big_id))
        .Times(1)
        .RetiresOnSaturation();
    IsoManager::GetInstance()->RemoveIsoDataPath(handle,
                                                 path_params.data_path_dir);
  }
}

TEST_F(IsoManagerDeathTest, RemoveIsoDataPathNoSuchPath) {
  // Check on CIS
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  uint16_t iso_handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  ASSERT_EXIT(
      IsoManager::GetInstance()->RemoveIsoDataPath(
          iso_handle, bluetooth::hci::iso_manager::kIsoDataPathDirectionOut),
      ::testing::KilledBySignal(SIGABRT), "path not set");

  IsoManager::GetInstance()->EstablishCis({.conn_pairs = {{iso_handle, 1}}});
  ASSERT_EXIT(
      IsoManager::GetInstance()->RemoveIsoDataPath(
          iso_handle, bluetooth::hci::iso_manager::kIsoDataPathDirectionOut),
      ::testing::KilledBySignal(SIGABRT), "path not set");

  // Check on BIS
  iso_handle = volatile_test_big_params_evt_.conn_handles[0];
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  ASSERT_EXIT(
      IsoManager::GetInstance()->RemoveIsoDataPath(
          iso_handle, bluetooth::hci::iso_manager::kIsoDataPathDirectionOut),
      ::testing::KilledBySignal(SIGABRT), "path not set");
}

TEST_F(IsoManagerDeathTest, RemoveIsoDataPathTwice) {
  // Check on CIS
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  uint16_t iso_handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({.conn_pairs = {{iso_handle, 1}}});
  IsoManager::GetInstance()->SetupIsoDataPath(iso_handle,
                                              kDefaultIsoDataPathParams);
  IsoManager::GetInstance()->RemoveIsoDataPath(
      iso_handle, kDefaultIsoDataPathParams.data_path_dir);
  ASSERT_EXIT(
      IsoManager::GetInstance()->RemoveIsoDataPath(
          iso_handle, bluetooth::hci::iso_manager::kIsoDataPathDirectionOut),
      ::testing::KilledBySignal(SIGABRT), "path not set");

  // Check on BIS
  iso_handle = volatile_test_big_params_evt_.conn_handles[0];
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  IsoManager::GetInstance()->SetupIsoDataPath(iso_handle,
                                              kDefaultIsoDataPathParams);
  IsoManager::GetInstance()->RemoveIsoDataPath(
      iso_handle, kDefaultIsoDataPathParams.data_path_dir);
  ASSERT_EXIT(
      IsoManager::GetInstance()->RemoveIsoDataPath(
          iso_handle, bluetooth::hci::iso_manager::kIsoDataPathDirectionOut),
      ::testing::KilledBySignal(SIGABRT), "path not set");
}

// Check if HCI status other than HCI_SUCCESS is being propagated to the caller
TEST_F(IsoManagerTest, RemoveIsoDataPathInvalidStatus) {
  // Mock invalid status response
  uint8_t remove_datapath_rsp_status = 0x12;
  ON_CALL(hcic_interface_, RemoveIsoDataPath)
      .WillByDefault([&remove_datapath_rsp_status](
                         uint16_t iso_handle, uint8_t data_path_dir,
                         base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
        std::vector<uint8_t> buf(3);
        uint8_t* p = buf.data();
        UINT8_TO_STREAM(p, remove_datapath_rsp_status);
        UINT16_TO_STREAM(p, iso_handle);

        std::move(cb).Run(buf.data(), buf.size());
      });

  // Check on CIS
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  uint16_t iso_handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({.conn_pairs = {{iso_handle, 1}}});
  IsoManager::GetInstance()->SetupIsoDataPath(iso_handle,
                                              kDefaultIsoDataPathParams);

  EXPECT_CALL(*cig_callbacks_,
              OnRemoveIsoDataPath(remove_datapath_rsp_status, iso_handle,
                                  volatile_test_cig_create_cmpl_evt_.cig_id))
      .Times(1);
  IsoManager::GetInstance()->RemoveIsoDataPath(
      iso_handle, kDefaultIsoDataPathParams.data_path_dir);

  // Check on BIS
  iso_handle = volatile_test_big_params_evt_.conn_handles[0];
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  IsoManager::GetInstance()->SetupIsoDataPath(iso_handle,
                                              kDefaultIsoDataPathParams);

  EXPECT_CALL(*big_callbacks_,
              OnRemoveIsoDataPath(remove_datapath_rsp_status, iso_handle,
                                  volatile_test_big_params_evt_.big_id))
      .Times(1);
  IsoManager::GetInstance()->RemoveIsoDataPath(
      iso_handle, kDefaultIsoDataPathParams.data_path_dir);
}

TEST_F(IsoManagerTest, RemoveIsoDataPathLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  // Establish all CISes before setting up their data paths
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  bluetooth::hci::iso_manager::iso_data_path_params path_params =
      kDefaultIsoDataPathParams;

  // Setup and remove data paths for all CISes
  path_params.data_path_dir =
      bluetooth::hci::iso_manager::kRemoveIsoDataPathDirectionInput;
  auto& handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  ON_CALL(hcic_interface_, RemoveIsoDataPath)
      .WillByDefault(
          [&iso_cb](uint16_t iso_handle, uint8_t data_path_dir,
                    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            iso_cb = std::move(cb);
          });
  IsoManager::GetInstance()->RemoveIsoDataPath(handle,
                                               path_params.data_path_dir);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(*cig_callbacks_,
              OnRemoveIsoDataPath(HCI_SUCCESS, handle,
                                  volatile_test_cig_create_cmpl_evt_.cig_id))
      .Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  std::vector<uint8_t> buf(3);
  uint8_t* p = buf.data();
  UINT8_TO_STREAM(p, HCI_SUCCESS);
  UINT16_TO_STREAM(p, handle);
  std::move(iso_cb).Run(buf.data(), buf.size());
}

TEST_F(IsoManagerTest, SendIsoDataWithNoCigConnected) {
  std::vector<uint8_t> data_vec(108, 0);
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->SendIsoData(handle, data_vec.data(),
                                         data_vec.size());
  EXPECT_CALL(iso_interface_, HciSend).Times(0);
}

TEST_F(IsoManagerTest, SendIsoDataCigValid) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    bluetooth::hci::iso_manager::iso_data_path_params path_params =
        kDefaultIsoDataPathParams;
    path_params.data_path_dir =
        bluetooth::hci::iso_manager::kIsoDataPathDirectionOut;
    IsoManager::GetInstance()->SetupIsoDataPath(handle, path_params);

    for (uint8_t num_pkts = 2; num_pkts != 0; num_pkts--) {
      constexpr uint8_t data_len = 108;

      EXPECT_CALL(iso_interface_, HciSend)
          .WillOnce([handle, data_len](BT_HDR* p_msg) {
            uint8_t* p = p_msg->data;
            uint16_t msg_handle;
            uint16_t iso_load_len;

            ASSERT_NE(p_msg, nullptr);
            ASSERT_EQ(p_msg->len, data_len + ((p_msg->layer_specific &
                                               BT_ISO_HDR_CONTAINS_TS)
                                                  ? 12
                                                  : 8));

            // Verify packet internals
            STREAM_TO_UINT16(msg_handle, p);
            ASSERT_EQ(msg_handle, handle);

            STREAM_TO_UINT16(iso_load_len, p);
            ASSERT_EQ(
                iso_load_len,
                data_len +
                    ((p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS) ? 8 : 4));

            if (p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS) {
              STREAM_SKIP_UINT16(p);  // skip ts LSB halfword
              STREAM_SKIP_UINT16(p);  // skip ts MSB halfword
            }
            STREAM_SKIP_UINT16(p);  // skip seq_nb

            uint16_t msg_data_len;
            STREAM_TO_UINT16(msg_data_len, p);
            ASSERT_EQ(msg_data_len, data_len);
          })
          .RetiresOnSaturation();

      std::vector<uint8_t> data_vec(data_len, 0);
      IsoManager::GetInstance()->SendIsoData(handle, data_vec.data(),
                                             data_vec.size());
    }
  }
}

TEST_F(IsoManagerTest, SendIsoDataBigValid) {
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  for (auto& handle : volatile_test_big_params_evt_.conn_handles) {
    IsoManager::GetInstance()->SetupIsoDataPath(handle,
                                                kDefaultIsoDataPathParams);
    for (uint8_t num_pkts = 2; num_pkts != 0; num_pkts--) {
      constexpr uint8_t data_len = 108;

      EXPECT_CALL(iso_interface_, HciSend)
          .WillOnce([handle, data_len](BT_HDR* p_msg) {
            uint8_t* p = p_msg->data;
            uint16_t msg_handle;
            uint16_t iso_load_len;

            ASSERT_NE(p_msg, nullptr);
            ASSERT_EQ(p_msg->len, data_len + ((p_msg->layer_specific &
                                               BT_ISO_HDR_CONTAINS_TS)
                                                  ? 12
                                                  : 8));

            // Verify packet internals
            STREAM_TO_UINT16(msg_handle, p);
            ASSERT_EQ(msg_handle, handle);

            STREAM_TO_UINT16(iso_load_len, p);
            ASSERT_EQ(
                iso_load_len,
                data_len +
                    ((p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS) ? 8 : 4));

            uint16_t msg_data_len;
            uint16_t msg_dummy;
            if (p_msg->layer_specific & BT_ISO_HDR_CONTAINS_TS) {
              STREAM_TO_UINT16(msg_dummy, p);  // skip ts LSB halfword
              STREAM_TO_UINT16(msg_dummy, p);  // skip ts MSB halfword
            }
            STREAM_TO_UINT16(msg_dummy, p);  // skip seq_nb

            STREAM_TO_UINT16(msg_data_len, p);
            ASSERT_EQ(msg_data_len, data_len);
          })
          .RetiresOnSaturation();

      std::vector<uint8_t> data_vec(data_len, 0);
      IsoManager::GetInstance()->SendIsoData(handle, data_vec.data(),
                                             data_vec.size());
    }
  }
}

TEST_F(IsoManagerTest, SendIsoDataNoCredits) {
  uint8_t num_buffers = controller_interface_.GetIsoBufferCount();
  std::vector<uint8_t> data_vec(108, 0);

  // Check on CIG
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  IsoManager::GetInstance()->SetupIsoDataPath(
      volatile_test_cig_create_cmpl_evt_.conn_handles[0],
      kDefaultIsoDataPathParams);

  /* Try sending twice as much data as we can ignoring the credit limits and
   * expect the redundant packets to be ignored and not propagated down to the
   * HCI.
   */
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_cig_create_cmpl_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }

  // Return all credits for this one handle
  uint8_t mock_rsp[5];
  uint8_t* p = mock_rsp;
  UINT8_TO_STREAM(p, 1);
  UINT16_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.conn_handles[0]);
  UINT16_TO_STREAM(p, num_buffers);
  IsoManager::GetInstance()->HandleNumComplDataPkts(mock_rsp, sizeof(mock_rsp));

  // Check on BIG
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  IsoManager::GetInstance()->SetupIsoDataPath(
      volatile_test_big_params_evt_.conn_handles[0], kDefaultIsoDataPathParams);

  /* Try sending twice as much data as we can ignoring the credit limits and
   * expect the redundant packets to be ignored and not propagated down to the
   * HCI.
   */
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers);
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_big_params_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }
}

TEST_F(IsoManagerTest, SendIsoDataCreditsReturned) {
  uint8_t num_buffers = controller_interface_.GetIsoBufferCount();
  std::vector<uint8_t> data_vec(108, 0);

  // Check on CIG
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  IsoManager::GetInstance()->SetupIsoDataPath(
      volatile_test_cig_create_cmpl_evt_.conn_handles[0],
      kDefaultIsoDataPathParams);

  /* Try sending twice as much data as we can, ignoring the credits limit and
   * expect the redundant packets to be ignored and not propagated down to the
   * HCI.
   */
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_cig_create_cmpl_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }

  // Return all credits for this one handle
  uint8_t mock_rsp[5];
  uint8_t* p = mock_rsp;
  UINT8_TO_STREAM(p, 1);
  UINT16_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.conn_handles[0]);
  UINT16_TO_STREAM(p, num_buffers);
  IsoManager::GetInstance()->HandleNumComplDataPkts(mock_rsp, sizeof(mock_rsp));

  // Expect some more events go down the HCI
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_cig_create_cmpl_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }

  // Return all credits for this one handle
  p = mock_rsp;
  UINT8_TO_STREAM(p, 1);
  UINT16_TO_STREAM(p, volatile_test_cig_create_cmpl_evt_.conn_handles[0]);
  UINT16_TO_STREAM(p, num_buffers);
  IsoManager::GetInstance()->HandleNumComplDataPkts(mock_rsp, sizeof(mock_rsp));

  // Check on BIG
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);
  IsoManager::GetInstance()->SetupIsoDataPath(
      volatile_test_big_params_evt_.conn_handles[0], kDefaultIsoDataPathParams);

  /* Try sending twice as much data as we can, ignoring the credits limit and
   * expect the redundant packets to be ignored and not propagated down to the
   * HCI.
   */
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_big_params_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }

  // Return all credits for this one handle
  p = mock_rsp;
  UINT8_TO_STREAM(p, 1);
  UINT16_TO_STREAM(p, volatile_test_big_params_evt_.conn_handles[0]);
  UINT16_TO_STREAM(p, num_buffers);
  IsoManager::GetInstance()->HandleNumComplDataPkts(mock_rsp, sizeof(mock_rsp));

  // Expect some more events go down the HCI
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < (2 * num_buffers); i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_big_params_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }
}

TEST_F(IsoManagerTest, SendIsoDataCreditsReturnedByDisconnection) {
  uint8_t num_buffers = controller_interface_.GetIsoBufferCount();
  std::vector<uint8_t> data_vec(108, 0);

  // Check on CIG
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    IsoManager::GetInstance()->SetupIsoDataPath(handle,
                                                kDefaultIsoDataPathParams);
  }

  /* Sending lot of ISO data to first ISO and getting all the credits */
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < num_buffers; i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_cig_create_cmpl_evt_.conn_handles[0], data_vec.data(),
        data_vec.size());
  }

  /* Return all credits by disconnecting CIS */
  IsoManager::GetInstance()->HandleDisconnect(
      volatile_test_cig_create_cmpl_evt_.conn_handles[0], 16);

  /* Try to send ISO data on the second ISO. Expect credits being available.*/
  EXPECT_CALL(iso_interface_, HciSend).Times(num_buffers).RetiresOnSaturation();
  for (uint8_t i = 0; i < num_buffers; i++) {
    IsoManager::GetInstance()->SendIsoData(
        volatile_test_cig_create_cmpl_evt_.conn_handles[1], data_vec.data(),
        data_vec.size());
  }
}

TEST_F(IsoManagerDeathTest, SendIsoDataWithNoDataPath) {
  std::vector<uint8_t> data_vec(108, 0);

  // Check on CIG
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& conn_handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({conn_handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  EXPECT_CALL(iso_interface_, HciSend).Times(0);
  IsoManager::GetInstance()->SendIsoData(
      volatile_test_cig_create_cmpl_evt_.conn_handles[0], data_vec.data(),
      data_vec.size());

  // Check on BIG
  IsoManager::GetInstance()->CreateBig(volatile_test_big_params_evt_.big_id,
                                       kDefaultBigParams);

  EXPECT_CALL(iso_interface_, HciSend).Times(0);
  IsoManager::GetInstance()->SendIsoData(
      volatile_test_big_params_evt_.conn_handles[0], data_vec.data(),
      data_vec.size());
}

TEST_F(IsoManagerDeathTest, SendIsoDataWithNoCigBigHandle) {
  std::vector<uint8_t> data_vec(108, 0);
  ASSERT_EXIT(IsoManager::GetInstance()->SendIsoData(134, data_vec.data(),
                                                     data_vec.size()),
              ::testing::KilledBySignal(SIGABRT), "No such iso");
}

TEST_F(IsoManagerTest, HandleDisconnectNoSuchHandle) {
  // Don't expect any callbacks when connection handle is not for ISO.
  EXPECT_CALL(*cig_callbacks_, OnCigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCisEvent).Times(0);
  EXPECT_CALL(*big_callbacks_, OnBigEvent).Times(0);

  IsoManager::GetInstance()->HandleDisconnect(123, 16);
}

TEST_F(IsoManagerTest, HandleDisconnectValidCig) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  EXPECT_CALL(*big_callbacks_, OnBigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCisEvent).Times(0);

  // Expect disconnect event exactly once
  EXPECT_CALL(*cig_callbacks_, OnCisEvent)
      .WillOnce([this, handle](uint8_t event_code, void* data) {
        ASSERT_EQ(event_code,
                  bluetooth::hci::iso_manager::kIsoEventCisDisconnected);
        auto* event =
            static_cast<bluetooth::hci::iso_manager::cis_disconnected_evt*>(
                data);
        ASSERT_EQ(event->reason, 16);
        ASSERT_EQ(event->cig_id, volatile_test_cig_create_cmpl_evt_.cig_id);
        ASSERT_EQ(event->cis_conn_hdl, handle);
      });

  IsoManager::GetInstance()->HandleDisconnect(handle, 16);
}

TEST_F(IsoManagerTest, HandleDisconnectDisconnectedCig) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  EXPECT_CALL(*big_callbacks_, OnBigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCisEvent).Times(0);

  // Expect disconnect event exactly once
  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDisconnected, _))
      .Times(1)
      .RetiresOnSaturation();
  IsoManager::GetInstance()->HandleDisconnect(handle, 16);

  // This one was once connected - expect no events
  IsoManager::GetInstance()->HandleDisconnect(handle, 16);

  // This one was never connected - expect no events
  handle = volatile_test_cig_create_cmpl_evt_.conn_handles[1];
  IsoManager::GetInstance()->HandleDisconnect(handle, 16);
}

TEST_F(IsoManagerTest, HandleDisconnectLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  EXPECT_CALL(*big_callbacks_, OnBigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCigEvent).Times(0);
  EXPECT_CALL(*cig_callbacks_, OnCisEvent).Times(0);

  // Expect disconnect event exactly once
  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDisconnected, _))
      .Times(0);

  // Expect no callback on late arriving event
  IsoManager::GetInstance()->Stop();
  IsoManager::GetInstance()->HandleDisconnect(handle, 16);
}

TEST_F(IsoManagerTest, HandleIsoData) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDataAvailable, _))
      .Times(1);

  std::vector<uint8_t> dummy_msg(18);
  uint8_t* p = dummy_msg.data();
  UINT16_TO_STREAM(p, BT_EVT_TO_BTU_HCI_ISO);
  UINT16_TO_STREAM(p, 10);  // .len
  UINT16_TO_STREAM(p, 0);   // .offset
  UINT16_TO_STREAM(p, 0);   // .layer_specific
  UINT16_TO_STREAM(p, handle);
  IsoManager::GetInstance()->HandleIsoData(dummy_msg.data());
}

/* This test case simulates HCI thread scheduling events on the main thread,
 * without knowing the we are already shutting down the stack and Iso Manager
 * is already stopped.
 */
TEST_F(IsoManagerDeathTestNoCleanup, HandleLateArivingEventHandleIsoData) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  // Stop iso manager before trying to call the HCI callbacks
  IsoManager::GetInstance()->Stop();

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDataAvailable, _))
      .Times(0);

  // Expect no assert on this call - should be gracefully ignored
  std::vector<uint8_t> dummy_msg(18);
  uint8_t* p = dummy_msg.data();
  UINT16_TO_STREAM(p, BT_EVT_TO_BTU_HCI_ISO);
  UINT16_TO_STREAM(p, 10);  // .len
  UINT16_TO_STREAM(p, 0);   // .offset
  UINT16_TO_STREAM(p, 0);   // .layer_specific
  UINT16_TO_STREAM(p, handle);
  IsoManager::GetInstance()->HandleIsoData(dummy_msg.data());
}

/* This test case simulates HCI thread scheduling events on the main thread,
 * without knowing the we are already shutting down the stack and Iso Manager
 * is already stopped.
 */
TEST_F(IsoManagerDeathTestNoCleanup, HandleLateArivingEventHandleDisconnect) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  // Stop iso manager before trying to call the HCI callbacks
  IsoManager::GetInstance()->Stop();

  // Expect no event when callback is being called on a stopped iso manager
  EXPECT_CALL(*cig_callbacks_, OnCisEvent).Times(0);
  // Expect no assert on this call - should be gracefully ignored
  IsoManager::GetInstance()->HandleDisconnect(handle, 16);
}

/* This test case simulates HCI thread scheduling events on the main thread,
 * without knowing the we are already shutting down the stack and Iso Manager
 * is already stopped.
 */
TEST_F(IsoManagerDeathTestNoCleanup,
       HandleLateArivingEventHandleNumComplDataPkts) {
  uint8_t num_buffers = controller_interface_.GetIsoBufferCount();

  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  // Stop iso manager before trying to call the HCI callbacks
  IsoManager::GetInstance()->Stop();

  // Expect no assert on this call - should be gracefully ignored
  uint8_t mock_rsp[5];
  uint8_t* p = mock_rsp;
  UINT8_TO_STREAM(p, 1);
  UINT16_TO_STREAM(p, handle);
  UINT16_TO_STREAM(p, num_buffers);
  IsoManager::GetInstance()->HandleNumComplDataPkts(mock_rsp, sizeof(mock_rsp));
}

/* This test case simulates HCI thread scheduling events on the main thread,
 * without knowing the we are already shutting down the stack and Iso Manager
 * is already stopped.
 */
TEST_F(IsoManagerDeathTestNoCleanup, HandleLateArivingEventHandleHciEvent) {
  const uint8_t big_id = 0x22;

  IsoManager::GetInstance()->CreateBig(big_id, kDefaultBigParams);

  // Stop iso manager before trying to call the HCI callbacks
  IsoManager::GetInstance()->Stop();
  EXPECT_CALL(
      *big_callbacks_,
      OnBigEvent(bluetooth::hci::iso_manager::kIsoEventBigOnTerminateCmpl, _))
      .Times(0);

  // Expect no assert on this call - should be gracefully ignored
  std::vector<uint8_t> buf(2);
  uint8_t* p = buf.data();
  UINT8_TO_STREAM(p, big_id);
  UINT8_TO_STREAM(p, 16);  // Terminated by local host
  IsoManager::GetInstance()->HandleHciEvent(HCI_BLE_TERM_BIG_CPL_EVT,
                                            buf.data(), buf.size());
}

TEST_F(IsoManagerTest, HandleIsoDataSameSeqNb) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->EstablishCis({{{handle, 1}}});

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisDataAvailable, _))
      .Times(2);

  std::vector<uint8_t> dummy_msg(18);
  uint8_t* p = dummy_msg.data();
  UINT16_TO_STREAM(p, BT_EVT_TO_BTU_HCI_ISO);
  UINT16_TO_STREAM(p, 10);  // .len
  UINT16_TO_STREAM(p, 0);   // .offset
  UINT16_TO_STREAM(p, 0);   // .layer_specific
  UINT16_TO_STREAM(p, handle);

  IsoManager::GetInstance()->HandleIsoData(dummy_msg.data());
  IsoManager::GetInstance()->HandleIsoData(dummy_msg.data());
}

TEST_F(IsoManagerTest, ReadIsoLinkQualityLateArrivingCallback) {
  IsoManager::GetInstance()->CreateCig(
      volatile_test_cig_create_cmpl_evt_.cig_id, kDefaultCigParams);

  EXPECT_CALL(
      *cig_callbacks_,
      OnCisEvent(bluetooth::hci::iso_manager::kIsoEventCisEstablishCmpl, _))
      .Times(kDefaultCigParams.cis_cfgs.size())
      .WillRepeatedly([this](uint8_t type, void* data) {
        bluetooth::hci::iso_manager::cis_establish_cmpl_evt* evt =
            static_cast<bluetooth::hci::iso_manager::cis_establish_cmpl_evt*>(
                data);

        ASSERT_EQ(evt->status, HCI_SUCCESS);
        ASSERT_TRUE(
            std::find(volatile_test_cig_create_cmpl_evt_.conn_handles.begin(),
                      volatile_test_cig_create_cmpl_evt_.conn_handles.end(),
                      evt->cis_conn_hdl) !=
            volatile_test_cig_create_cmpl_evt_.conn_handles.end());
      });

  // Establish all CISes
  bluetooth::hci::iso_manager::cis_establish_params params;
  for (auto& handle : volatile_test_cig_create_cmpl_evt_.conn_handles) {
    params.conn_pairs.push_back({handle, 1});
  }
  IsoManager::GetInstance()->EstablishCis(params);

  // Catch the callback
  base::OnceCallback<void(uint8_t*, uint16_t)> iso_cb;
  ON_CALL(hcic_interface_, ReadIsoLinkQuality)
      .WillByDefault(
          [&iso_cb](uint16_t iso_handle,
                    base::OnceCallback<void(uint8_t*, uint16_t)> cb) {
            iso_cb = std::move(cb);
          });
  auto handle = volatile_test_cig_create_cmpl_evt_.conn_handles[0];
  IsoManager::GetInstance()->ReadIsoLinkQuality(handle);

  // Stop the IsoManager before calling the callback
  IsoManager::GetInstance()->Stop();

  // Call the callback and expect no call
  EXPECT_CALL(*cig_callbacks_,
              OnIsoLinkQualityRead(handle, _, _, _, _, _, _, _, _))
      .Times(0);
  ASSERT_FALSE(iso_cb.is_null());

  std::vector<uint8_t> buf(31);
  uint8_t* p = buf.data();
  UINT8_TO_STREAM(p, HCI_SUCCESS);
  UINT16_TO_STREAM(p, handle);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  UINT32_TO_STREAM(p, 0);
  std::move(iso_cb).Run(buf.data(), buf.size());
}

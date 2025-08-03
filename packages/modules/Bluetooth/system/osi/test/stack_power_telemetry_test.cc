#include "osi/include/stack_power_telemetry.h"

#include <gtest/gtest.h>

#include "osi/src/stack_power_telemetry.cc"
#include "types/raw_address.h"

class PowerTelemetryTest : public ::testing::Test {
 protected:
  uint16_t handle = 123;
  RawAddress bdaddr;
  bool isConnected = true;

  void reset() {
    power_telemetry::GetInstance().pimpl_->LogDataTransfer();
    power_telemetry::GetInstance().pimpl_->idx_containers = 0;
    for (int i = 0; i < kLogEntriesSize; i++) {
      power_telemetry::GetInstance().pimpl_->log_data_containers_[i] =
          LogDataContainer();
    }
  }

  void SetUp() override {
    // Enable the feature flag
    power_telemerty_enabled_ = true;
    RawAddress::FromString("00:00:00:00:00:00", bdaddr);
  }
};

TEST_F(PowerTelemetryTest, test_getCurrentLogDataContainer) {
  reset();

  // Record smth, log index move to 1
  power_telemetry::GetInstance().pimpl_->RecordLogDataContainer();
  ASSERT_EQ(1, power_telemetry::GetInstance().pimpl_->idx_containers);
}

TEST_F(PowerTelemetryTest, test_recordLogDataContainer) {
  reset();

  // Create maximum number of nodes
  for (int i = 0; i < kLogEntriesSize - 1; i++) {
    power_telemetry::GetInstance().pimpl_->RecordLogDataContainer();
    ASSERT_EQ(i + 1, power_telemetry::GetInstance().pimpl_->idx_containers);
  }

  // Create 1 more node. index integer should be 0
  power_telemetry::GetInstance().pimpl_->RecordLogDataContainer();
  ASSERT_EQ(0, power_telemetry::GetInstance().pimpl_->idx_containers);
}

TEST_F(PowerTelemetryTest, test_LogInqScanDetails) {
  reset();

  power_telemetry::GetInstance().LogInqScanStopped();
  ASSERT_EQ(0, power_telemetry::GetInstance().pimpl_->inq_scan.count_);

  power_telemetry::GetInstance().LogInqScanStarted();
  ASSERT_EQ(1, power_telemetry::GetInstance().pimpl_->inq_scan.count_);
}

TEST_F(PowerTelemetryTest, test_LogBleScan) {
  reset();

  power_telemetry::GetInstance().LogBleScan(10);
  ASSERT_EQ(10, (int)power_telemetry::GetInstance().pimpl_->ble_scan.count_);
}

TEST_F(PowerTelemetryTest, test_LogBleAdvDetails) {
  reset();

  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  // Failed Case. Shouldn't crash if run false first
  power_telemetry::GetInstance().LogBleAdvStopped();
  ASSERT_EQ(0, (int)ldc.adv_list.size());

  // Add new BleAdv data
  power_telemetry::GetInstance().LogBleAdvStarted();
  ASSERT_EQ(1, (int)ldc.adv_list.size());
  ASSERT_NE(0, ldc.adv_list.back().active.begin);

  // BleAdv data update endTime
  power_telemetry::GetInstance().LogBleAdvStopped();
  ASSERT_EQ(1, (int)ldc.adv_list.size());
  ASSERT_NE(0, ldc.adv_list.back().active.end);

  // Add new BleAdv data
  power_telemetry::GetInstance().LogBleAdvStarted();
  ASSERT_EQ(2, (int)ldc.adv_list.size());
}

TEST_F(PowerTelemetryTest, test_LogTxPower) {
  reset();

  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();
  tBTM_TX_POWER_RESULT dummy_res;
  dummy_res.rem_bda = bdaddr;

  // Failed Case. Shouldn't crash if no init data
  dummy_res.status = BTM_SUCCESS;
  void* p = &dummy_res;
  power_telemetry::GetInstance().LogTxPower(p);

  // init data
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);

  // Successful case
  dummy_res.tx_power = 100;
  power_telemetry::GetInstance().LogTxPower(p);
  ASSERT_EQ(dummy_res.tx_power,
            ldc.acl.link_details_map[handle].tx_power_level);

  // Failed case
  dummy_res.tx_power = 99;
  dummy_res.status = BTM_UNDEFINED;
  power_telemetry::GetInstance().LogTxPower(p);
  ASSERT_NE(dummy_res.tx_power,
            ldc.acl.link_details_map[handle].tx_power_level);
}

TEST_F(PowerTelemetryTest, test_LogAclLinkDetails) {
  reset();
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  // Failed Case. Shouldn't crash if first invoke function with false
  isConnected = false;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);
  ASSERT_EQ(0, (int)ldc.acl.link_details_list.size());

  // Successful case
  isConnected = true;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);
  ASSERT_EQ(1, (int)ldc.acl.link_details_map.count(handle));
  ASSERT_EQ(0, (int)ldc.acl.link_details_list.size());
  ASSERT_EQ(1, (int)ldc.sniff_activity_map.count(handle));

  isConnected = false;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);
  ASSERT_EQ(0, (int)ldc.acl.link_details_map.count(handle));
  ASSERT_EQ(1, (int)ldc.acl.link_details_list.size());
}

TEST_F(PowerTelemetryTest, test_LogScoLinkDetails) {
  reset();
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  // Failed Case. Shouldn't crash if first invoke function with false
  isConnected = false;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                false);
  ASSERT_EQ(0, (int)ldc.sco.link_details_list.size());

  // Successful case
  isConnected = true;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                false);
  ASSERT_EQ(1, (int)ldc.sco.link_details_map.count(handle));
  ASSERT_EQ(0, (int)ldc.sco.link_details_list.size());

  isConnected = false;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                false);
  ASSERT_EQ(0, (int)ldc.sco.link_details_map.count(handle));
  ASSERT_EQ(1, (int)ldc.sco.link_details_list.size());
}

TEST_F(PowerTelemetryTest, test_LogHciCmdEvtDetails) {
  reset();

  // After log hci_cmd, the number of it should be 1
  power_telemetry::GetInstance().LogHciCmdDetail();
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->cmd.count_);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->event.count_);

  // After log hci_evt, the number of it should be 1
  power_telemetry::GetInstance().LogHciEvtDetail();
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->cmd.count_);
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->event.count_);
}

TEST_F(PowerTelemetryTest, test_LogSniffActivity) {
  reset();
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  power_telemetry::GetInstance().LogSniffStarted(handle, bdaddr);
  ASSERT_EQ(1, (int)ldc.sniff_activity_map[handle].sniff_count);
  ASSERT_EQ(0, (int)ldc.sniff_activity_map[handle].active_count);

  power_telemetry::GetInstance().LogSniffStopped(handle, bdaddr);
  ASSERT_EQ(1, (int)ldc.sniff_activity_map[handle].sniff_count);
  ASSERT_EQ(1, (int)ldc.sniff_activity_map[handle].active_count);
}

TEST_F(PowerTelemetryTest, test_LogDataTransfer) {
  reset();

  // We should create new record. index should be 1
  power_telemetry::GetInstance().pimpl_->LogDataTransfer();
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->idx_containers);
}

TEST_F(PowerTelemetryTest, test_LogScanStarted) {
  reset();

  power_telemetry::GetInstance().LogScanStarted();
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->scan.count_);
}

TEST_F(PowerTelemetryTest, test_LogAclPktDetails) {
  reset();

  // scanCount should be 1
  power_telemetry::GetInstance().LogTxAclPktData(10);
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->tx.pkt_);
  ASSERT_EQ(10, (int)power_telemetry::GetInstance().pimpl_->tx.len_);

  power_telemetry::GetInstance().LogRxAclPktData(11);
  ASSERT_EQ(1, (int)power_telemetry::GetInstance().pimpl_->rx.pkt_);
  ASSERT_EQ(11, (int)power_telemetry::GetInstance().pimpl_->rx.len_);
}

TEST_F(PowerTelemetryTest, test_LogChannelConnected) {
  reset();
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  power_telemetry::GetInstance().LogChannelConnected(BT_PSM_RFCOMM, 0, 0,
                                                     bdaddr);
  ASSERT_EQ(1, (int)ldc.channel_map[bdaddr].size());
  ASSERT_EQ(State::kConnected, ldc.channel_map[bdaddr].back().state);

  power_telemetry::GetInstance().LogChannelConnected(BT_PSM_RFCOMM, 0, 0,
                                                     bdaddr);
  ASSERT_EQ(2, (int)ldc.channel_map[bdaddr].size());
  ASSERT_EQ(State::kConnected, ldc.channel_map[bdaddr].back().state);
}

TEST_F(PowerTelemetryTest, test_LogChannelDisconnected) {
  reset();
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();

  power_telemetry::GetInstance().LogChannelConnected(0, 0, 0, bdaddr);
  power_telemetry::GetInstance().LogChannelDisconnected(0, 0, 0, bdaddr);
  ASSERT_EQ(State::kDisconnected, ldc.channel_map[bdaddr].back().state);

  RawAddress dummyAddr;
  RawAddress::FromString("00:00:00:00:00:11", dummyAddr);
  power_telemetry::GetInstance().LogChannelDisconnected(0, 0, 0, bdaddr);
  ASSERT_EQ(1, (int)ldc.channel_map[bdaddr].size());
}

TEST_F(PowerTelemetryTest, test_LogTxBytes) {
  reset();

  power_telemetry::GetInstance().LogTxBytes(BT_PSM_RFCOMM, 0, 0, bdaddr, 10);
  ASSERT_EQ(10, (int)power_telemetry::GetInstance().pimpl_->rfc.tx.bytes_);

  power_telemetry::GetInstance().LogTxBytes(0, 0, 0, bdaddr, 11);
  ASSERT_EQ(11, (int)power_telemetry::GetInstance().pimpl_->l2c.tx.bytes_);
}

TEST_F(PowerTelemetryTest, test_LogRxBytes) {
  reset();

  power_telemetry::GetInstance().LogRxBytes(BT_PSM_RFCOMM, 0, 0, bdaddr, 10);
  ASSERT_EQ(10, (int)power_telemetry::GetInstance().pimpl_->rfc.rx.bytes_);

  power_telemetry::GetInstance().LogRxBytes(0, 0, 0, bdaddr, 11);
  ASSERT_EQ(11, (int)power_telemetry::GetInstance().pimpl_->l2c.rx.bytes_);
}

TEST_F(PowerTelemetryTest, test_feature_flag) {
  reset();

  // init data
  isConnected = true;
  LogDataContainer& ldc =
      power_telemetry::GetInstance().pimpl_->GetCurrentLogDataContainer();
  tBTM_TX_POWER_RESULT dummy_res;
  dummy_res.rem_bda = bdaddr;
  dummy_res.status = BTM_SUCCESS;
  void* p = &dummy_res;
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);

  // Set feature flag to false
  // All function shouldn't work if flag is false
  power_telemerty_enabled_ = false;

  power_telemetry::GetInstance().Dumpsys(0);
  ASSERT_EQ(0, power_telemetry::GetInstance().pimpl_->idx_containers);

  power_telemetry::GetInstance().LogRxBytes(BT_PSM_RFCOMM, 0, 0, bdaddr, 87);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->rfc.rx.bytes_);

  power_telemetry::GetInstance().LogTxBytes(BT_PSM_RFCOMM, 0, 0, bdaddr, 10);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->rfc.tx.bytes_);

  power_telemetry::GetInstance().LogChannelConnected(0, 0, 0, bdaddr);
  ASSERT_EQ(0, (int)ldc.channel_map.count(bdaddr));

  power_telemetry::GetInstance().LogChannelDisconnected(0, 0, 0, bdaddr);
  ASSERT_EQ(0, (int)ldc.channel_map.count(bdaddr));

  power_telemetry::GetInstance().LogTxAclPktData(10);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->tx.pkt_);

  power_telemetry::GetInstance().LogRxAclPktData(11);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->rx.pkt_);

  power_telemetry::GetInstance().LogScanStarted();
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->scan.count_);

  power_telemetry::GetInstance().pimpl_->LogDataTransfer();
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->idx_containers);

  power_telemetry::GetInstance().LogSniffStarted(handle, bdaddr);
  ASSERT_EQ(0, (int)ldc.sniff_activity_map[handle].sniff_count);

  power_telemetry::GetInstance().LogHciCmdDetail();
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->cmd.count_);

  power_telemetry::GetInstance().LogHciEvtDetail();
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->event.count_);

  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                false);
  ASSERT_EQ(0, (int)ldc.sco.link_details_map.count(handle));

  // Set to 1 because of fake data
  power_telemetry::GetInstance().LogLinkDetails(handle, bdaddr, isConnected,
                                                true);
  ASSERT_EQ(1, (int)ldc.acl.link_details_map.count(handle));

  dummy_res.tx_power = 100;
  power_telemetry::GetInstance().LogTxPower(p);
  ASSERT_EQ(0, ldc.acl.link_details_map[handle].tx_power_level);

  power_telemetry::GetInstance().LogBleScan(10);
  ASSERT_EQ(0, (int)power_telemetry::GetInstance().pimpl_->ble_scan.count_);

  power_telemetry::GetInstance().LogBleAdvStarted();
  ASSERT_EQ(0, (int)ldc.adv_list.size());

  power_telemetry::GetInstance().LogInqScanStarted();
  ASSERT_EQ(0, power_telemetry::GetInstance().pimpl_->inq_scan.count_);

  power_telemetry::GetInstance().pimpl_->RecordLogDataContainer();
  ASSERT_EQ(0, power_telemetry::GetInstance().pimpl_->idx_containers);

  power_telemerty_enabled_ = true;
}

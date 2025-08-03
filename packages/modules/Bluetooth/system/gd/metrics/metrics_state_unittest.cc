#include "metrics_state.h"

#include <gmock/gmock.h>

#include <cstdint>
#include <vector>

#include "gtest/gtest.h"
#include "hci/address.h"
#include "metrics_state.h"
#include "os/metrics.h"

//
using android::bluetooth::hci::StatusEnum;
using android::bluetooth::le::LeAclConnectionState;
using android::bluetooth::le::LeConnectionOriginType;
using android::bluetooth::le::LeConnectionState;
using android::bluetooth::le::LeConnectionType;

LeAclConnectionState le_acl_state = LeAclConnectionState::LE_ACL_UNSPECIFIED;
LeConnectionOriginType origin_type = LeConnectionOriginType::ORIGIN_UNSPECIFIED;
LeConnectionType connection_type = LeConnectionType::CONNECTION_TYPE_UNSPECIFIED;
StatusEnum status = StatusEnum::STATUS_UNKNOWN;
bluetooth::hci::Address remote_address = bluetooth::hci::Address::kEmpty;
int latency = 0;
int acl_latency = 0;
bool is_cancelled = false;

namespace bluetooth {
namespace metrics {

const hci::Address address1 = hci::Address({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
const hci::Address empty_address = hci::Address::kEmpty;

class TestMetricsLoggerModule : public BaseMetricsLoggerModule {
 public:
  TestMetricsLoggerModule() {}
  void LogMetricBluetoothLESession(os::LEConnectionSessionOptions session_options);
  virtual ~TestMetricsLoggerModule() {}
};

void TestMetricsLoggerModule::LogMetricBluetoothLESession(
    os::LEConnectionSessionOptions session_options) {
  le_acl_state = session_options.acl_connection_state;
  origin_type = session_options.origin_type;
  connection_type = session_options.transaction_type;
  is_cancelled = session_options.is_cancelled;
  status = session_options.status;
  remote_address = session_options.remote_address;
}

class MockMetricsCollector {
 public:
  static LEConnectionMetricsRemoteDevice* GetLEConnectionMetricsCollector();

  static LEConnectionMetricsRemoteDevice* le_connection_metrics_remote_device;
};



LEConnectionMetricsRemoteDevice* MockMetricsCollector::le_connection_metrics_remote_device =
    new LEConnectionMetricsRemoteDevice(new TestMetricsLoggerModule());

LEConnectionMetricsRemoteDevice* MockMetricsCollector::GetLEConnectionMetricsCollector() {
  return MockMetricsCollector::le_connection_metrics_remote_device;
}

namespace {

class LEConnectionMetricsRemoteDeviceTest : public ::testing::Test {};

TEST(LEConnectionMetricsRemoteDeviceTest, Initialize) {
  ASSERT_EQ(0, 0);
}

TEST(LEConnectionMetricsRemoteDeviceTest, ConnectionSuccess) {
  auto argument_list = std::vector<std::pair<os::ArgumentType, int>>();
  argument_list.push_back(std::make_pair(
      os::ArgumentType::ACL_STATUS_CODE,
      static_cast<int>(android::bluetooth::hci::StatusEnum::STATUS_SUCCESS)));

  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_START,
      argument_list);

  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_END,
      argument_list);
  // assert that these are equal
  ASSERT_EQ(le_acl_state, LeAclConnectionState::LE_ACL_SUCCESS);
  ASSERT_EQ(origin_type, LeConnectionOriginType::ORIGIN_NATIVE);
  ASSERT_EQ(connection_type, LeConnectionType::CONNECTION_TYPE_LE_ACL);
  ASSERT_EQ(remote_address, address1);
  ASSERT_EQ(is_cancelled, false);
}

TEST(LEConnectionMetricsRemoteDeviceTest, ConnectionFailed) {
  auto argument_list = std::vector<std::pair<os::ArgumentType, int>>();
  argument_list.push_back(std::make_pair(
      os::ArgumentType::ACL_STATUS_CODE,
      static_cast<int>(android::bluetooth::hci::StatusEnum::STATUS_NO_CONNECTION)));

  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_START,
      argument_list);

  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_END,
      argument_list);
  // assert that these are equal
  ASSERT_EQ(le_acl_state, LeAclConnectionState::LE_ACL_FAILED);
  ASSERT_EQ(origin_type, LeConnectionOriginType::ORIGIN_NATIVE);
  ASSERT_EQ(connection_type, LeConnectionType::CONNECTION_TYPE_LE_ACL);
  ASSERT_EQ(remote_address, address1);
  ASSERT_EQ(is_cancelled, false);
}

TEST(LEConnectionMetricsRemoteDeviceTest, Cancellation) {
  auto argument_list = std::vector<std::pair<os::ArgumentType, int>>();
  auto no_connection_argument_list = std::vector<std::pair<os::ArgumentType, int>>();
  no_connection_argument_list.push_back(std::make_pair(
      os::ArgumentType::ACL_STATUS_CODE,
      static_cast<int>(android::bluetooth::hci::StatusEnum::STATUS_NO_CONNECTION)));

  // Start of the LE-ACL Connection
  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_START,
      argument_list);

  // Cancellation of the LE-ACL Connection
  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      empty_address,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_CANCEL,
      argument_list);

  // Ending of the LE-ACL Connection
  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_END,
      no_connection_argument_list);

  ASSERT_EQ(le_acl_state, LeAclConnectionState::LE_ACL_FAILED);
  ASSERT_EQ(origin_type, LeConnectionOriginType::ORIGIN_NATIVE);
  ASSERT_EQ(connection_type, LeConnectionType::CONNECTION_TYPE_LE_ACL);
  ASSERT_EQ(remote_address, address1);
  ASSERT_EQ(is_cancelled, true);
}

TEST(LEConnectionMetricsRemoteDeviceTest, Timeout) {
  auto argument_list = std::vector<std::pair<os::ArgumentType, int>>();

  // Start of the LE-ACL Connection
  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_START,
      argument_list);

  // Timeout of the LE-ACL Connection
  MockMetricsCollector::GetLEConnectionMetricsCollector()->AddStateChangedEvent(
      address1,
      LeConnectionOriginType::ORIGIN_NATIVE,
      LeConnectionType::CONNECTION_TYPE_LE_ACL,
      LeConnectionState::STATE_LE_ACL_TIMEOUT,
      argument_list);

  ASSERT_EQ(le_acl_state, LeAclConnectionState::LE_ACL_FAILED);
  ASSERT_EQ(origin_type, LeConnectionOriginType::ORIGIN_NATIVE);
  ASSERT_EQ(connection_type, LeConnectionType::CONNECTION_TYPE_LE_ACL);
  ASSERT_EQ(remote_address, address1);
  ASSERT_EQ(is_cancelled, false);
}

}  // namespace
}  // namespace metrics
}  // namespace bluetooth

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

#include "hal/snoop_logger.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <netinet/in.h>
#include <sys/socket.h>

#include <future>
#include <unordered_map>

#include "common/init_flags.h"
#include "hal/snoop_logger_common.h"
#include "hal/syscall_wrapper_impl.h"
#include "os/fake_timer/fake_timerfd.h"
#include "os/system_properties.h"
#include "os/utils.h"

namespace testing {

using bluetooth::hal::SnoopLoggerCommon;
using bluetooth::hal::SnoopLoggerSocket;
using bluetooth::hal::SnoopLoggerSocketInterface;
using bluetooth::hal::SnoopLoggerSocketThread;
using bluetooth::hal::SyscallWrapperImpl;
using bluetooth::os::fake_timer::fake_timerfd_advance;
using bluetooth::os::fake_timer::fake_timerfd_reset;

namespace {
std::vector<uint8_t> kInformationRequest = {
    0xfe,
    0x2e,
    0x0a,
    0x00,
    0x06,
    0x00,
    0x01,
    0x00,
    0x0a,
    0x02,
    0x02,
    0x00,
    0x02,
    0x00,
};

std::vector<uint8_t> kSdpConnectionRequest = {
    0x08, 0x20, 0x0c, 0x00, 0x08, 0x00, 0x01, 0x00, 0x02, 0x0c, 0x04, 0x00, 0x01, 0x00, 0x44, 0x00};

std::vector<uint8_t> kAvdtpSuspend = {0x02, 0x02, 0x00, 0x07, 0x00, 0x03, 0x00, 0x8d, 0x00, 0x90, 0x09, 0x04};

std::vector<uint8_t> kHfpAtNrec0 = {0x02, 0x02, 0x20, 0x13, 0x00, 0x0f, 0x00, 0x41, 0x00, 0x09, 0xff, 0x15,
                                    0x01, 0x41, 0x54, 0x2b, 0x4e, 0x52, 0x45, 0x43, 0x3d, 0x30, 0x0d, 0x5c};

std::vector<uint8_t> kQualcommConnectionRequest = {0xdc, 0x2e, 0x54, 0x00, 0x50, 0x00, 0xff, 0x00, 0x00, 0x0a,
                                                   0x0f, 0x09, 0x01, 0x00, 0x5c, 0x93, 0x01, 0x00, 0x42, 0x00};

std::vector<uint8_t> kA2dpMediaPacket = {
    0x0b, 0x20, 0x3a, 0x00, 0x36, 0x00, 0x40, 0xa0, 0x80, 0xe0, 0x07, 0x7f, 0x00, 0x1e, 0x08, 0x00,
    0x00, 0x00, 0x00, 0x02, 0x47, 0xfc, 0x00, 0x00, 0xb0, 0x90, 0x80, 0x03, 0x00, 0x20, 0x21, 0x11,
    0x45, 0x00, 0x14, 0x50, 0x01, 0x46, 0xf0, 0x81, 0x0a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a,
    0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5a, 0x5e,
};

std::vector<bluetooth::hal::HciPacket> kTestData = {
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x35,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x3f,
     0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x85,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x1f,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x99,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x13, 0x00, 0x0f, 0x00, 0x40, 0x00, 0x9f, 0xff, 0xc6, 0x01,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x99,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x1f,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x12, 0x00, 0x0e, 0x00, 0x41, 0x00, 0x9d, 0xff, 0x01,
     0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x8f,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x89,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x1f,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x20, 0x11, 0x00, 0x0d, 0x00, 0x41, 0x00, 0x9d, 0xef, 0x43,
     0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00},
    {0x02, 0x00, 0x12, 0x00, 0x0e, 0x00, 0x40, 0x00, 0x9f, 0xff, 0x11,
     0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}};

}  // namespace

using bluetooth::TestModuleRegistry;
using bluetooth::hal::SnoopLogger;
using namespace std::chrono_literals;

const char* test_flags[] = {
    "INIT_logging_debug_enabled_for_all=true",
    nullptr,
};

// Expose protected constructor for test
class TestSnoopLoggerModule : public SnoopLogger {
 public:
  TestSnoopLoggerModule(
      std::string snoop_log_path,
      std::string snooz_log_path,
      size_t max_packets_per_file,
      const std::string& btsnoop_mode,
      bool qualcomm_debug_log_enabled,
      bool snoop_log_persists)
      : SnoopLogger(
            std::move(snoop_log_path),
            std::move(snooz_log_path),
            max_packets_per_file,
            SnoopLogger::GetMaxPacketsPerBuffer(),
            btsnoop_mode,
            qualcomm_debug_log_enabled,
            20ms,
            5ms,
            snoop_log_persists) {}

  std::string ToString() const override {
    return std::string("TestSnoopLoggerModule");
  }

  void CallGetDumpsysData(flatbuffers::FlatBufferBuilder* builder) {
    GetDumpsysData(builder);
  }

  SnoopLoggerSocketThread* GetSocketThread() {
    return snoop_logger_socket_thread_.get();
  }

  static uint32_t GetL2capHeaderSize() {
    return L2CAP_HEADER_SIZE;
  }

  static size_t GetMaxFilteredSize() {
    return MAX_HCI_ACL_LEN - PACKET_TYPE_LENGTH;
  }
};

class SnoopLoggerModuleTest : public Test {
 public:
  flatbuffers::FlatBufferBuilder* builder_;
  TestModuleRegistry* test_registry;

 protected:
  void SetUp() override {
    const testing::TestInfo* const test_info =
        testing::UnitTest::GetInstance()->current_test_info();

    LOG_DEBUG(
        "Setup for test %s in test suite %s.\n", test_info->name(), test_info->test_suite_name());
    const std::filesystem::path temp_dir_ = std::filesystem::temp_directory_path();

    temp_snoop_log_ = temp_dir_ / (std::string(test_info->name()) + "_btsnoop_hci.log");
    temp_snoop_log_last_ = temp_dir_ / (std::string(test_info->name()) + "_btsnoop_hci.log.last");
    temp_snooz_log_ = temp_dir_ / (std::string(test_info->name()) + "_btsnooz_hci.log");
    temp_snooz_log_last_ = temp_dir_ / (std::string(test_info->name()) + "_btsnooz_hci.log.last");
    temp_snoop_log_filtered =
        temp_dir_ / (std::string(test_info->name()) + "_btsnoop_hci.log.filtered");
    temp_snoop_log_filtered_last =
        temp_dir_ / (std::string(test_info->name()) + "_btsnoop_hci.log.filtered.last");
    builder_ = new flatbuffers::FlatBufferBuilder();

    DeleteSnoopLogFiles();
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_filtered));
    ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_filtered_last));
    ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_last_));

    test_registry = new TestModuleRegistry();

    bluetooth::common::InitFlags::Load(test_flags);
  }

  void TearDown() override {
    DeleteSnoopLogFiles();
    delete builder_;
    fake_timerfd_reset();
    test_registry->StopAll();
    delete test_registry;

    const testing::TestInfo* const test_info =
        testing::UnitTest::GetInstance()->current_test_info();
    LOG_DEBUG(
        "TearDown for test %s in test suite %s.\n",
        test_info->name(),
        test_info->test_suite_name());
  }

  std::filesystem::path temp_snoop_log_;
  std::filesystem::path temp_snoop_log_last_;
  std::filesystem::path temp_snooz_log_;
  std::filesystem::path temp_snooz_log_last_;
  std::filesystem::path temp_snoop_log_filtered;
  std::filesystem::path temp_snoop_log_filtered_last;

 private:
  void DeleteSnoopLogFiles() {
    if (std::filesystem::exists(temp_snoop_log_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_));
    }
    if (std::filesystem::exists(temp_snoop_log_last_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_last_));
    }
    if (std::filesystem::exists(temp_snoop_log_filtered)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
    }
    if (std::filesystem::exists(temp_snoop_log_filtered_last)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered_last));
    }
    if (std::filesystem::exists(temp_snooz_log_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snooz_log_));
    }
    if (std::filesystem::exists(temp_snooz_log_last_)) {
      ASSERT_TRUE(std::filesystem::remove(temp_snooz_log_last_));
    }
  }
};

TEST_F(SnoopLoggerModuleTest, empty_snoop_log_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  test_registry->StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(std::filesystem::file_size(temp_snoop_log_), sizeof(SnoopLoggerCommon::FileHeaderType));
}

TEST_F(SnoopLoggerModuleTest, disable_snoop_log_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_one_packet_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);

  test_registry->StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());
}

TEST_F(SnoopLoggerModuleTest, capture_hci_cmd_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());

  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_signal_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kSdpConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kSdpConnectionRequest.size());

  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_short_data_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kAvdtpSuspend, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kAvdtpSuspend.size());

  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, capture_l2cap_long_data_packet_btsnooz_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);

  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, snoop_log_persists) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      true);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->Capture(
      kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);

  test_registry->StopAll();
  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
}

void sync_handler(bluetooth::os::Handler* handler) {
  std::promise<void> promise;
  auto future = promise.get_future();
  handler->Post(bluetooth::common::BindOnce(
      &std::promise<void>::set_value, bluetooth::common::Unretained(&promise)));
  auto future_status = future.wait_for(std::chrono::seconds(1));
  ASSERT_EQ(future_status, std::future_status::ready);
}

TEST_F(SnoopLoggerModuleTest, delete_old_snooz_log_files) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  std::filesystem::create_directories(temp_snooz_log_);

  auto* handler = test_registry->GetTestModuleHandler(&SnoopLogger::Factory);
  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  handler->Post(bluetooth::common::BindOnce(fake_timerfd_advance, 10));
  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  handler->Post(bluetooth::common::BindOnce(fake_timerfd_advance, 15));
  sync_handler(handler);
  handler->Post(bluetooth::common::BindOnce(
      [](std::filesystem::path path) { ASSERT_FALSE(std::filesystem::exists(path)); }, temp_snooz_log_));
  sync_handler(handler);

  test_registry->StopAll();
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, rotate_file_at_new_session_test) {
  // Start once
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(),
        temp_snooz_log_.string(),
        10,
        SnoopLogger::kBtSnoopLogModeFull,
        false,
        false);
    test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(
        kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    test_registry->StopAll();
  }

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());

  // Start again
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(),
        temp_snooz_log_.string(),
        10,
        SnoopLogger::kBtSnoopLogModeFull,
        false,
        false);
    test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(
        kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    snoop_logger->Capture(
        kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
    test_registry->StopAll();
  }

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) +
          (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 2);
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_last_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size());
}

TEST_F(SnoopLoggerModuleTest, rotate_file_after_full_test) {
  // Actual test
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      false,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  for (int i = 0; i < 11; i++) {
    snoop_logger->Capture(kInformationRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::CMD);
  }

  test_registry->StopAll();

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) +
          (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 1);
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_last_),
      sizeof(SnoopLoggerCommon::FileHeaderType) +
          (sizeof(SnoopLogger::PacketHeaderType) + kInformationRequest.size()) * 10);
}

TEST_F(SnoopLoggerModuleTest, qualcomm_debug_log_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
  snoop_logger->Capture(
      kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->CallGetDumpsysData(builder_);

  ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
  ASSERT_EQ(
      std::filesystem::file_size(temp_snooz_log_),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          kQualcommConnectionRequest.size());

  test_registry->StopAll();

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, qualcomm_debug_log_regression_test) {
  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(),
        temp_snooz_log_.string(),
        10,
        SnoopLogger::kBtSnoopLogModeDisabled,
        true,
        false);
    test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(
        kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    snoop_logger->CallGetDumpsysData(builder_);

    ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_EQ(
        std::filesystem::file_size(temp_snooz_log_),
        sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);
    test_registry->StopAll();
  }

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));

  {
    auto* snoop_logger = new TestSnoopLoggerModule(
        temp_snoop_log_.string(),
        temp_snooz_log_.string(),
        10,
        SnoopLogger::kBtSnoopLogModeDisabled,
        false,
        false);
    test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);
    snoop_logger->Capture(
        kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
    snoop_logger->CallGetDumpsysData(builder_);

    ASSERT_TRUE(std::filesystem::exists(temp_snooz_log_));
    ASSERT_EQ(
        std::filesystem::file_size(temp_snooz_log_),
        sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) + 14);
    test_registry->StopAll();
  }

  // Verify states after test
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_));
  ASSERT_FALSE(std::filesystem::exists(temp_snoop_log_last_));
  ASSERT_FALSE(std::filesystem::exists(temp_snooz_log_));
}

TEST_F(SnoopLoggerModuleTest, filter_tracker_test) {
  std::unordered_map<uint16_t, bluetooth::hal::FilterTracker> filter_list;
  uint16_t handle = 1;
  uint16_t local_cid = 0x40;
  uint16_t remote_cid = 0x41;
  uint8_t dlci = 0x02;

  filter_list[handle].AddL2capCid(local_cid, remote_cid);
  ASSERT_TRUE(filter_list[handle].IsAcceptlistedL2cap(true, local_cid));
  ASSERT_TRUE(filter_list[handle].IsAcceptlistedL2cap(false, remote_cid));

  filter_list[handle].RemoveL2capCid(local_cid, remote_cid);
  ASSERT_FALSE(filter_list[handle].IsAcceptlistedL2cap(true, local_cid));
  ASSERT_FALSE(filter_list[handle].IsAcceptlistedL2cap(false, remote_cid));

  filter_list[handle].AddRfcommDlci(dlci);
  ASSERT_TRUE(filter_list[handle].IsAcceptlistedDlci(dlci));

  filter_list[handle].SetRfcommCid(local_cid, remote_cid);
  ASSERT_TRUE(filter_list[handle].IsRfcommChannel(true, local_cid));
  ASSERT_TRUE(filter_list[handle].IsRfcommChannel(false, remote_cid));

  filter_list[handle].RemoveL2capCid(local_cid, remote_cid);
  ASSERT_FALSE(filter_list[handle].IsAcceptlistedL2cap(true, local_cid));
  ASSERT_FALSE(filter_list[handle].IsAcceptlistedL2cap(false, remote_cid));
  ASSERT_FALSE(filter_list[handle].IsAcceptlistedDlci(dlci));
}

TEST_F(SnoopLoggerModuleTest, a2dp_packets_filtered_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0001;
  uint16_t remote_cid = 0xa040;

  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty, "true"));
  auto filter_a2dp_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty);
  ASSERT_TRUE(filter_a2dp_property && filter_a2dp_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // Simulate A2dp Media channel setup
  snoop_logger->AddA2dpMediaChannel(conn_handle, local_cid, remote_cid);

  snoop_logger->Capture(
      kA2dpMediaPacket, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));
  // Should filter packet
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType));
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, a2dp_packets_filtered_negative_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0001;
  uint16_t remote_cid = 0xa040;

  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty, "true"));
  auto filter_a2dp_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty);
  ASSERT_TRUE(filter_a2dp_property && filter_a2dp_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // Simulate A2dp Media channel setup
  snoop_logger->AddA2dpMediaChannel(conn_handle, local_cid, remote_cid);
  snoop_logger->RemoveA2dpMediaChannel(conn_handle, local_cid);

  snoop_logger->Capture(
      kA2dpMediaPacket, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileA2dpProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));
  // Should not filter
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          kA2dpMediaPacket.size());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, headers_filtered_test) {
  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterHeadersProperty, "true"));
  auto filter_headers_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterHeadersProperty);
  ASSERT_TRUE(filter_headers_property && filter_headers_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  std::vector<uint8_t> kAclPacket = {
      0x0b, 0x20, 0x18, 0x00, 0x14, 0x00, 0x44, 0x00, 0x1b, 0x2f, 0x21, 0x41, 0x54, 0x2b,
      0x43, 0x4d, 0x45, 0x52, 0x3d, 0x33, 0x2c, 0x30, 0x2c, 0x30, 0x2c, 0x31, 0x0d, 0x8f,
  };

  snoop_logger->Capture(kAclPacket, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(
      bluetooth::os::SetSystemProperty(SnoopLogger::kBtSnoopLogFilterHeadersProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));
  LOG_INFO(
      "const size: %d",
      (int)(sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType)));

  // Packet should be filtered
  const size_t file_size = (size_t)std::filesystem::file_size(temp_snoop_log_filtered);
  const size_t expected_file_size = sizeof(SnoopLoggerCommon::FileHeaderType) +
                                    sizeof(SnoopLogger::PacketHeaderType) +
                                    TestSnoopLoggerModule::GetMaxFilteredSize();
  ASSERT_EQ(file_size, expected_file_size);
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, rfcomm_channel_filtered_sabme_ua_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0044;
  uint16_t remote_cid = 0x3040;

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "true"));
  auto filter_rfcomm_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty);
  ASSERT_TRUE(filter_rfcomm_property);
  ASSERT_TRUE(filter_rfcomm_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // Simulate Rfcomm channel
  snoop_logger->AddRfcommL2capChannel(conn_handle, local_cid, remote_cid);
  std::vector<uint8_t> kRfcommSabme = {
      0x0b, 0x20, 0x18, 0x00, 0x14, 0x00, 0x44, 0x00, 0x1b, 0x2f, 0x21, 0x41, 0x54, 0x2b,
      0x43, 0x4d, 0x45, 0x52, 0x3d, 0x33, 0x2c, 0x30, 0x2c, 0x30, 0x2c, 0x31, 0x0d, 0x8f,
  };
  std::vector<uint8_t> kRfcommUa = {
      0x0b, 0x20, 0x18, 0x00, 0x14, 0x00, 0x44, 0x00, 0x1b, 0x63, 0x21, 0x41, 0x54, 0x2b,
      0x43, 0x4d, 0x45, 0x52, 0x3d, 0x33, 0x2c, 0x30, 0x2c, 0x30, 0x2c, 0x31, 0x0d, 0x8f,
  };

  snoop_logger->Capture(
      kRfcommSabme, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->Capture(kRfcommUa, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->ClearL2capAcceptlist(conn_handle, local_cid, remote_cid);
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packets should not be filtered because because they are SAMBE and UA events.
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + 2 * sizeof(SnoopLogger::PacketHeaderType) +
          kRfcommSabme.size() + kRfcommUa.size());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, rfcomm_channel_filtered_acceptlisted_dlci_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x3040;
  uint8_t dlci = 0x04;
  uint8_t dlci_byte = dlci << 2;

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "true"));
  auto filter_rfcomm_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty);
  ASSERT_TRUE(filter_rfcomm_property);
  ASSERT_TRUE(filter_rfcomm_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // Simulate Rfcomm channel
  snoop_logger->AddRfcommL2capChannel(conn_handle, local_cid, remote_cid);
  snoop_logger->AcceptlistRfcommDlci(conn_handle, local_cid, dlci);

  std::vector<uint8_t> kRfcommDlci = {
      0x0b, 0x20, 0x12, 0x00, 0x0e, 0x00, 0x41, 0x00, dlci_byte, 0xef, 0x15,
      0x83, 0x11, 0x06, 0xf0, 0x07, 0x00, 0x9d, 0x02, 0x00,      0x07, 0x70,
  };

  snoop_logger->Capture(
      kRfcommDlci, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->ClearL2capAcceptlist(conn_handle, local_cid, remote_cid);
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packet should not be filtered because DLCI acceptlisted
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          kRfcommDlci.size());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, rfcomm_channel_filtered_not_acceptlisted_dlci_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x3040;
  uint8_t dlci = 0x04;
  uint8_t dlci_byte = dlci << 2;

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "true"));
  auto filter_rfcomm_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty);
  ASSERT_TRUE(filter_rfcomm_property);
  ASSERT_TRUE(filter_rfcomm_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // Simulate Rfcomm channel
  snoop_logger->AddRfcommL2capChannel(conn_handle, local_cid, remote_cid);

  std::vector<uint8_t> kRfcommDlci = {
      0x0b, 0x20, 0x12, 0x00, 0x0e, 0x00, 0x41, 0x00, dlci_byte, 0xef, 0x15,
      0x83, 0x11, 0x06, 0xf0, 0x07, 0x00, 0x9d, 0x02, 0x00,      0x07, 0x70,
  };

  snoop_logger->Capture(
      kRfcommDlci, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->ClearL2capAcceptlist(conn_handle, local_cid, remote_cid);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packet should be filtered because DLCI not acceptlisted
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          TestSnoopLoggerModule::GetL2capHeaderSize());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, rfcomm_channel_filtered_not_acceptlisted_l2cap_channel_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x3040;

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "true"));
  auto filter_rfcomm_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty);
  ASSERT_TRUE(filter_rfcomm_property);
  ASSERT_TRUE(filter_rfcomm_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  std::vector<uint8_t> kRfcommL2capChannel = {
      0x0b, 0x20, 0x12, 0x00, 0x0e, 0x00, 0x41, 0x00, 0x00, 0xef, 0x15,
      0x83, 0x11, 0x06, 0xf0, 0x07, 0x00, 0x9d, 0x02, 0x00, 0x07, 0x70,
  };

  snoop_logger->Capture(
      kRfcommL2capChannel, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->ClearL2capAcceptlist(conn_handle, local_cid, remote_cid);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packet should be filtered because L2CAP channel not acceptlisted
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          TestSnoopLoggerModule::GetL2capHeaderSize());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, rfcomm_channel_filtered_acceptlisted_l2cap_channel_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x3040;

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "true"));
  auto filter_rfcomm_property =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty);
  ASSERT_TRUE(filter_rfcomm_property);
  ASSERT_TRUE(filter_rfcomm_property.value() == "true");

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->AcceptlistL2capChannel(conn_handle, local_cid, remote_cid);

  std::vector<uint8_t> kRfcommL2capChannel = {
      0x0b, 0x20, 0x12, 0x00, 0x0e, 0x00, 0x41, 0x00, 0x00, 0xef, 0x15,
      0x83, 0x11, 0x06, 0xf0, 0x07, 0x00, 0x9d, 0x02, 0x00, 0x07, 0x70,
  };

  snoop_logger->Capture(
      kRfcommL2capChannel, SnoopLogger::Direction::INCOMING, SnoopLogger::PacketType::ACL);
  snoop_logger->ClearL2capAcceptlist(conn_handle, local_cid, remote_cid);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileRfcommProperty, "false"));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packet should not be filtered because L2CAP channel acceptlisted
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          kRfcommL2capChannel.size());
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, profiles_filtered_hfp_hf_test) {
  // Actual test
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0043;
  uint16_t remote_cid = 0x3040;
  uint8_t dlci = 0x06;
  uint16_t psm = 0x0003;
  uint16_t profile_uuid_hfp_hf = 0x111f;
  bool flow = true;

  const std::string clcc_pattern = "\x0d\x0a+CLCC:";
  const uint16_t HEADER_SIZE = 12;

  // Set pbap and map filtering modes
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterPbapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty);
  ASSERT_TRUE(
      filterPbapModeProperty &&
      (filterPbapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterMapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty);
  ASSERT_TRUE(
      filterMapModeProperty &&
      (filterMapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->SetL2capChannelOpen(conn_handle, local_cid, remote_cid, psm, false);
  snoop_logger->SetRfcommPortOpen(conn_handle, local_cid, dlci, profile_uuid_hfp_hf, flow);

  std::vector<uint8_t> kPhoneNumber = {
      0x0b, 0x00, 0x30, 0x00, 0x2c, 0x00, 0x40, 0x30, 0x19, 0xff, 0x4f, 0x01, 0x0d,
      0x0a, 0x2b, 0x43, 0x4c, 0x43, 0x43, 0x3a, 0x20, 0x31, 0x2c, 0x31, 0x2c, 0x34,
      0x2c, 0x30, 0x2c, 0x30, 0x2c, 0x22, 0x2b, 0x39, 0x39, 0x31, 0x32, 0x33, 0x34,
      0x35, 0x36, 0x37, 0x38, 0x39, 0x22, 0x2c, 0x31, 0x34, 0x35, 0x0d, 0x0a, 0x49,
  };

  snoop_logger->Capture(
      kPhoneNumber, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->SetL2capChannelClose(conn_handle, local_cid, remote_cid);
  snoop_logger->SetRfcommPortClose(conn_handle, local_cid, dlci, profile_uuid_hfp_hf);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packet should be filtered
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) + sizeof(SnoopLogger::PacketHeaderType) +
          HEADER_SIZE + strlen(clcc_pattern.c_str()));
  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, profiles_filtered_pbap_magic_test) {
  // Actual test
  constexpr uint16_t PROFILE_PSM_PBAP = 0x1025;
  constexpr uint16_t PROFILE_UUID_PBAP = 0x112f;
  uint16_t conn_handle = 0x0002;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x0040;
  uint8_t dlci = 0x27;
  uint16_t psm = PROFILE_PSM_PBAP;
  uint16_t profile_uuid_pbap = PROFILE_UUID_PBAP;
  bool flow = true;
  const std::string magic_string = "PROHIBITED";
  const uint16_t HEADER_SIZE = 8;

  // Set pbap and map filtering modes
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterPbapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty);
  ASSERT_TRUE(
      filterPbapModeProperty &&
      (filterPbapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterMapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty);
  ASSERT_TRUE(
      filterMapModeProperty &&
      (filterMapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      15,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->SetL2capChannelOpen(conn_handle, local_cid, remote_cid, psm, false);
  snoop_logger->SetRfcommPortOpen(conn_handle, local_cid, dlci, profile_uuid_pbap, flow);

  for (int i = 0; i < (int)kTestData.size(); i++) {
    snoop_logger->Capture(
        kTestData[i], (SnoopLogger::Direction)(i % 2), SnoopLogger::PacketType::ACL);
  }

  snoop_logger->SetL2capChannelClose(conn_handle, local_cid, remote_cid);
  snoop_logger->SetRfcommPortClose(conn_handle, local_cid, dlci, profile_uuid_pbap);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packets should be filtered
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) +
          (int)kTestData.size() *
              (sizeof(SnoopLogger::PacketHeaderType) + HEADER_SIZE + strlen(magic_string.c_str())));

  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, profiles_filtered_pbap_header_test) {
  // Actual test
  constexpr uint16_t PROFILE_PSM_PBAP = 0x1025;
  constexpr uint16_t PROFILE_UUID_PBAP = 0x112f;
  uint16_t conn_handle = 0x0002;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x0040;
  uint8_t dlci = 0x27;
  uint16_t psm = PROFILE_PSM_PBAP;
  uint16_t profile_uuid_pbap = PROFILE_UUID_PBAP;
  bool flow = true;
  const uint16_t HEADER_SIZE = 8;

  // Set pbap and map filtering modes
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeHeader));
  auto filterPbapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty);
  ASSERT_TRUE(
      filterPbapModeProperty &&
      (filterPbapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeHeader) !=
       std::string::npos));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeHeader));
  auto filterMapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty);
  ASSERT_TRUE(
      filterMapModeProperty &&
      (filterMapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeHeader) !=
       std::string::npos));

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      15,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->SetL2capChannelOpen(conn_handle, local_cid, remote_cid, psm, false);
  snoop_logger->SetRfcommPortOpen(conn_handle, local_cid, dlci, profile_uuid_pbap, flow);

  for (int i = 0; i < (int)kTestData.size(); i++) {
    snoop_logger->Capture(
        kTestData[i], (SnoopLogger::Direction)(i % 2), SnoopLogger::PacketType::ACL);
  }

  snoop_logger->SetL2capChannelClose(conn_handle, local_cid, remote_cid);
  snoop_logger->SetRfcommPortClose(conn_handle, local_cid, dlci, profile_uuid_pbap);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packets should be filtered
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType) +
          (int)kTestData.size() * (sizeof(SnoopLogger::PacketHeaderType) + HEADER_SIZE));

  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

TEST_F(SnoopLoggerModuleTest, profiles_filtered_pbap_fullfilter_test) {
  // Actual test
  constexpr uint16_t PROFILE_PSM_PBAP = 0x1025;
  constexpr uint16_t PROFILE_UUID_PBAP = 0x112f;
  uint16_t conn_handle = 0x0002;
  uint16_t local_cid = 0x0041;
  uint16_t remote_cid = 0x0040;
  uint8_t dlci = 0x27;
  uint16_t psm = PROFILE_PSM_PBAP;
  uint16_t profile_uuid_pbap = PROFILE_UUID_PBAP;
  bool flow = true;

  // Set pbap and map filtering modes
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter));
  auto filterPbapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty);
  ASSERT_TRUE(
      filterPbapModeProperty &&
      (filterPbapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter) !=
       std::string::npos));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter));
  auto filterMapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty);
  ASSERT_TRUE(
      filterMapModeProperty &&
      (filterMapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeFullfillter) !=
       std::string::npos));

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      15,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  TestModuleRegistry test_registry;
  test_registry.InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  snoop_logger->SetL2capChannelOpen(conn_handle, local_cid, remote_cid, psm, false);
  snoop_logger->SetRfcommPortOpen(conn_handle, local_cid, dlci, profile_uuid_pbap, flow);

  for (int i = 0; i < (int)kTestData.size(); i++) {
    snoop_logger->Capture(
        kTestData[i], (SnoopLogger::Direction)(i % 2), SnoopLogger::PacketType::ACL);
  }

  snoop_logger->SetL2capChannelClose(conn_handle, local_cid, remote_cid);
  snoop_logger->SetRfcommPortClose(conn_handle, local_cid, dlci, profile_uuid_pbap);

  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  test_registry.StopAll();

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));

  // Verify states after test
  ASSERT_TRUE(std::filesystem::exists(temp_snoop_log_filtered));

  // Packets should be filtered
  ASSERT_EQ(
      std::filesystem::file_size(temp_snoop_log_filtered),
      sizeof(SnoopLoggerCommon::FileHeaderType));

  ASSERT_TRUE(std::filesystem::remove(temp_snoop_log_filtered));
}

static constexpr int INVALID_FD = -1;

TEST_F(SnoopLoggerModuleTest, socket_disabled_connect_fail_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  int ret;

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret != 0);

  test_registry->StopAll();
  close(socket_fd);
}

TEST_F(SnoopLoggerModuleTest, default_socket_enabled_capture_recv_test) {
  int ret;
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(SnoopLoggerSocket::DEFAULT_LISTEN_PORT_);

  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char recv_buf1[sizeof(SnoopLoggerCommon::FileHeaderType)];
  char recv_buf2[sizeof(SnoopLogger::PacketHeaderType)];
  char recv_buf3[99];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [&] {
    recv(socket_fd, recv_buf1, sizeof(recv_buf1), 0);
    recv(socket_fd, recv_buf2, sizeof(recv_buf2), 0);
    return recv(socket_fd, recv_buf3, sizeof(recv_buf3), 0);
  });

  snoop_logger->GetSocketThread()->GetSocket()->WaitForClientSocketConnected();

  snoop_logger->Capture(kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  a.wait();
  bytes_read = a.get();

  ASSERT_TRUE(std::memcmp(recv_buf1, &SnoopLoggerCommon::kBtSnoopFileHeader, sizeof(recv_buf1)) == 0);
  ASSERT_EQ(bytes_read, static_cast<int>(kHfpAtNrec0.size()));
  ASSERT_TRUE(std::memcmp(recv_buf3, kHfpAtNrec0.data(), kHfpAtNrec0.size()) == 0);

  test_registry->StopAll();
  close(socket_fd);
}

TEST_F(SnoopLoggerModuleTest, custom_socket_register_enabled_capture_recv_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  int new_port = 8873;
  SyscallWrapperImpl syscall_if;
  auto sls = std::make_unique<SnoopLoggerSocket>(&syscall_if, SnoopLoggerSocket::DEFAULT_LOCALHOST_, new_port);
  SnoopLoggerSocketThread slsThread(std::move(sls));
  auto thread_start_future = slsThread.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  snoop_logger->RegisterSocket(&slsThread);

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(new_port);

  int ret = 0;
  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char recv_buf1[sizeof(SnoopLoggerCommon::FileHeaderType)];
  char recv_buf2[sizeof(SnoopLogger::PacketHeaderType)];
  char recv_buf3[99];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [socket_fd, &recv_buf1, &recv_buf2, &recv_buf3] {
    recv(socket_fd, recv_buf1, sizeof(recv_buf1), 0);
    recv(socket_fd, recv_buf2, sizeof(recv_buf2), 0);
    return recv(socket_fd, recv_buf3, sizeof(recv_buf3), 0);
  });

  slsThread.GetSocket()->WaitForClientSocketConnected();

  snoop_logger->Capture(kHfpAtNrec0, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  a.wait();
  bytes_read = a.get();

  ASSERT_TRUE(std::memcmp(recv_buf1, &SnoopLoggerCommon::kBtSnoopFileHeader, sizeof(recv_buf1)) == 0);
  ASSERT_EQ(bytes_read, static_cast<int>(kHfpAtNrec0.size()));
  ASSERT_TRUE(std::memcmp(recv_buf3, kHfpAtNrec0.data(), kHfpAtNrec0.size()) == 0);

  test_registry->StopAll();
  close(socket_fd);
}

TEST_F(SnoopLoggerModuleTest, custom_socket_interface_register_logging_disabled_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeDisabled,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  class SnoopLoggerSocketMock : public SnoopLoggerSocketInterface {
   public:
    bool write_called;
    SnoopLoggerSocketMock() {
      write_called = false;
    }
    virtual void Write(const void* /* data */, size_t /* length */) {
      write_called = true;
    }
  };

  SnoopLoggerSocketMock mock;

  snoop_logger->RegisterSocket(&mock);
  snoop_logger->Capture(kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  ASSERT_FALSE(mock.write_called);

  test_registry->StopAll();
}

TEST_F(SnoopLoggerModuleTest, custom_socket_interface_register_logging_enabled_test) {
  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFull,
      true,
      false);
  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  class SnoopLoggerSocketMock : public SnoopLoggerSocketInterface {
   public:
    bool write_called;
    SnoopLoggerSocketMock() {
      write_called = false;
    }
    virtual void Write(const void* /* data */, size_t /* length */) {
      write_called = true;
    }
  };

  SnoopLoggerSocketMock mock;

  snoop_logger->RegisterSocket(&mock);
  snoop_logger->Capture(kQualcommConnectionRequest, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);

  ASSERT_TRUE(mock.write_called);

  test_registry->StopAll();
}

TEST_F(SnoopLoggerModuleTest, custom_socket_profiles_filtered_hfp_hf_test) {
  uint16_t conn_handle = 0x000b;
  uint16_t local_cid = 0x0043;
  uint16_t remote_cid = 0x3040;
  uint8_t dlci = 0x06;
  uint16_t psm = 0x0003;
  uint16_t profile_uuid_hfp_hf = 0x111f;
  bool flow = true;
  const std::string clcc_pattern = "\x0d\x0a+CLCC:";
  const uint16_t HEADER_SIZE = 12;
  size_t expected_data_size = HEADER_SIZE + strlen(clcc_pattern.c_str());
  std::vector<uint8_t> kPhoneNumber = {
      0x0b, 0x00, 0x30, 0x00, 0x2c, 0x00, 0x40, 0x30, 0x19, 0xff, 0x4f, 0x01, 0x0d,
      0x0a, 0x2b, 0x43, 0x4c, 0x43, 0x43, 0x3a, 0x20, 0x31, 0x2c, 0x31, 0x2c, 0x34,
      0x2c, 0x30, 0x2c, 0x30, 0x2c, 0x22, 0x2b, 0x39, 0x39, 0x31, 0x32, 0x33, 0x34,
      0x35, 0x36, 0x37, 0x38, 0x39, 0x22, 0x2c, 0x31, 0x34, 0x35, 0x0d, 0x0a, 0x49,
  };

  // Set pbap and map filtering modes
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterPbapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty);
  ASSERT_TRUE(
      filterPbapModeProperty &&
      (filterPbapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeMagic));
  auto filterMapModeProperty =
      bluetooth::os::GetSystemProperty(SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty);
  ASSERT_TRUE(
      filterMapModeProperty &&
      (filterMapModeProperty->find(SnoopLogger::kBtSnoopLogFilterProfileModeMagic) !=
       std::string::npos));

  auto* snoop_logger = new TestSnoopLoggerModule(
      temp_snoop_log_.string(),
      temp_snooz_log_.string(),
      10,
      SnoopLogger::kBtSnoopLogModeFiltered,
      false,
      false);

  test_registry->InjectTestModule(&SnoopLogger::Factory, snoop_logger);

  int new_port = 8873;
  SyscallWrapperImpl syscall_if;
  auto sls = std::make_unique<SnoopLoggerSocket>(
      &syscall_if, SnoopLoggerSocket::DEFAULT_LOCALHOST_, new_port);
  SnoopLoggerSocketThread slsThread(std::move(sls));
  auto thread_start_future = slsThread.Start();
  thread_start_future.wait();
  ASSERT_TRUE(thread_start_future.get());

  snoop_logger->RegisterSocket(&slsThread);

  snoop_logger->SetL2capChannelOpen(conn_handle, local_cid, remote_cid, psm, false);
  snoop_logger->SetRfcommPortOpen(conn_handle, local_cid, dlci, profile_uuid_hfp_hf, flow);

  // // Create a TCP socket file descriptor
  int socket_fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, IPPROTO_TCP);
  ASSERT_TRUE(socket_fd != INVALID_FD);

  struct sockaddr_in addr;
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = htonl(SnoopLoggerSocket::DEFAULT_LOCALHOST_);
  addr.sin_port = htons(new_port);

  int ret = 0;
  // Connect to snoop logger socket
  RUN_NO_INTR(ret = connect(socket_fd, (struct sockaddr*)&addr, sizeof(addr)));
  ASSERT_TRUE(ret == 0);

  char recv_buf1[sizeof(SnoopLoggerCommon::FileHeaderType)];
  char recv_buf2[sizeof(SnoopLogger::PacketHeaderType)];
  char recv_buf3[kPhoneNumber.size()];
  int bytes_read = -1;

  auto a = std::async(std::launch::async, [socket_fd, &recv_buf1, &recv_buf2, &recv_buf3] {
    recv(socket_fd, recv_buf1, sizeof(recv_buf1), 0);
    recv(socket_fd, recv_buf2, sizeof(recv_buf2), 0);
    return recv(socket_fd, recv_buf3, sizeof(recv_buf3), 0);
  });

  slsThread.GetSocket()->WaitForClientSocketConnected();

  snoop_logger->Capture(
      kPhoneNumber, SnoopLogger::Direction::OUTGOING, SnoopLogger::PacketType::ACL);
  snoop_logger->SetL2capChannelClose(conn_handle, local_cid, remote_cid);
  snoop_logger->SetRfcommPortClose(conn_handle, local_cid, dlci, profile_uuid_hfp_hf);

  a.wait();
  bytes_read = a.get();

  ASSERT_TRUE(
      std::memcmp(recv_buf1, &SnoopLoggerCommon::kBtSnoopFileHeader, sizeof(recv_buf1)) == 0);
  ASSERT_EQ(bytes_read, static_cast<int>(expected_data_size));
  ASSERT_TRUE(std::memcmp(recv_buf3, kPhoneNumber.data(), expected_data_size) == 0);

  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfileMapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));
  ASSERT_TRUE(bluetooth::os::SetSystemProperty(
      SnoopLogger::kBtSnoopLogFilterProfilePbapModeProperty,
      SnoopLogger::kBtSnoopLogFilterProfileModeDisabled));

  test_registry->StopAll();
  close(socket_fd);
}
}  // namespace testing

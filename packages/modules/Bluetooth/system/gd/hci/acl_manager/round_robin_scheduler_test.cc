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

#include "hci/acl_manager/round_robin_scheduler.h"

#include <gtest/gtest.h>

#include "common/bidi_queue.h"
#include "common/callback.h"
#include "hci/controller.h"
#include "hci/hci_packets.h"
#include "os/handler.h"
#include "os/log.h"
#include "packet/raw_builder.h"

using ::bluetooth::common::BidiQueue;
using ::bluetooth::common::Callback;
using ::bluetooth::os::Handler;
using ::bluetooth::os::Thread;

using namespace std::chrono_literals;

namespace bluetooth {
namespace hci {
namespace acl_manager {
namespace {

class TestController : public Controller {
 public:
  uint16_t GetNumAclPacketBuffers() const {
    return max_acl_packet_credits_;
  }

  uint16_t GetAclPacketLength() const {
    return hci_mtu_;
  }

  LeBufferSize GetLeBufferSize() const {
    LeBufferSize le_buffer_size;
    le_buffer_size.le_data_packet_length_ = le_hci_mtu_;
    le_buffer_size.total_num_le_packets_ = le_max_acl_packet_credits_;
    return le_buffer_size;
  }

  void RegisterCompletedAclPacketsCallback(CompletedAclPacketsCallback cb) {
    acl_credits_callback_ = cb;
  }

  void SendCompletedAclPacketsCallback(uint16_t handle, uint16_t credits) {
    acl_credits_callback_.Invoke(handle, credits);
  }

  void UnregisterCompletedAclPacketsCallback() {
    acl_credits_callback_ = {};
  }

  const uint16_t max_acl_packet_credits_ = 10;
  const uint16_t hci_mtu_ = 1024;
  const uint16_t le_max_acl_packet_credits_ = 15;
  const uint16_t le_hci_mtu_ = 27;

 private:
  CompletedAclPacketsCallback acl_credits_callback_;
};

class RoundRobinSchedulerTest : public ::testing::Test {
 public:
  void SetUp() override {
    thread_ = new Thread("thread", Thread::Priority::NORMAL);
    handler_ = new Handler(thread_);
    controller_ = new TestController();
    round_robin_scheduler_ = new RoundRobinScheduler(handler_, controller_, hci_queue_.GetUpEnd());
    hci_queue_.GetDownEnd()->RegisterDequeue(
        handler_, common::Bind(&RoundRobinSchedulerTest::HciDownEndDequeue, common::Unretained(this)));
  }

  void TearDown() override {
    hci_queue_.GetDownEnd()->UnregisterDequeue();
    delete round_robin_scheduler_;
    delete controller_;
    handler_->Clear();
    delete handler_;
    delete thread_;
  }

  void sync_handler() {
    ASSERT(thread_ != nullptr);
    ASSERT(thread_->GetReactor()->WaitForIdle(2s));
  }

  void EnqueueAclUpEnd(AclConnection::QueueUpEnd* queue_up_end, std::vector<uint8_t> packet) {
    if (enqueue_promise_ != nullptr) {
      enqueue_future_->wait();
    }
    enqueue_promise_ = std::make_unique<std::promise<void>>();
    enqueue_future_ = std::make_unique<std::future<void>>(enqueue_promise_->get_future());
    queue_up_end->RegisterEnqueue(handler_, common::Bind(&RoundRobinSchedulerTest::enqueue_callback,
                                                         common::Unretained(this), queue_up_end, packet));
  }

  std::unique_ptr<packet::BasePacketBuilder> enqueue_callback(AclConnection::QueueUpEnd* queue_up_end,
                                                              std::vector<uint8_t> packet) {
    auto packet_one = std::make_unique<packet::RawBuilder>(2000);
    packet_one->AddOctets(packet);
    queue_up_end->UnregisterEnqueue();
    enqueue_promise_->set_value();
    return packet_one;
  };

  void HciDownEndDequeue() {
    auto packet = hci_queue_.GetDownEnd()->TryDequeue();
    // Convert from a Builder to a View
    auto bytes = std::make_shared<std::vector<uint8_t>>();
    bluetooth::packet::BitInserter i(*bytes);
    bytes->reserve(packet->size());
    packet->Serialize(i);
    auto packet_view = bluetooth::packet::PacketView<bluetooth::packet::kLittleEndian>(bytes);
    AclView acl_packet_view = AclView::Create(packet_view);
    ASSERT_TRUE(acl_packet_view.IsValid());
    PacketView<true> count_view = acl_packet_view.GetPayload();
    sent_acl_packets_.push(acl_packet_view);

    packet_count_--;
    if (packet_count_ == 0) {
      std::promise<void>* prom = packet_promise_.release();
      prom->set_value();
      delete prom;
    }
  }

  void VerifyPacket(uint16_t handle, std::vector<uint8_t> packet) {
    auto acl_packet_view = sent_acl_packets_.front();
    ASSERT_EQ(handle, acl_packet_view.GetHandle());
    auto payload = acl_packet_view.GetPayload();
    for (size_t i = 0; i < payload.size(); i++) {
      ASSERT_EQ(payload[i], packet[i]);
    }
    sent_acl_packets_.pop();
  }

  void SetPacketFuture(uint16_t count) {
    ASSERT_EQ(packet_promise_, nullptr) << "Promises, Promises, ... Only one at a time.";
    packet_count_ = count;
    packet_promise_ = std::make_unique<std::promise<void>>();
    packet_future_ = std::make_unique<std::future<void>>(packet_promise_->get_future());
  }

  BidiQueue<AclView, AclBuilder> hci_queue_{3};
  Thread* thread_;
  Handler* handler_;
  TestController* controller_;
  RoundRobinScheduler* round_robin_scheduler_;
  std::queue<AclView> sent_acl_packets_;
  uint16_t packet_count_;
  std::unique_ptr<std::promise<void>> packet_promise_;
  std::unique_ptr<std::future<void>> packet_future_;
  std::unique_ptr<std::promise<void>> enqueue_promise_;
  std::unique_ptr<std::future<void>> enqueue_future_;
};

TEST_F(RoundRobinSchedulerTest, startup_teardown) {}

TEST_F(RoundRobinSchedulerTest, register_unregister_connection) {
  uint16_t handle = 0x01;
  auto connection_queue = std::make_shared<AclConnection::Queue>(10);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);
  round_robin_scheduler_->Unregister(handle);
}

TEST_F(RoundRobinSchedulerTest, buffer_packet) {
  uint16_t handle = 0x01;
  auto connection_queue = std::make_shared<AclConnection::Queue>(10);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(2));
  AclConnection::QueueUpEnd* queue_up_end = connection_queue->GetUpEnd();
  std::vector<uint8_t> packet1 = {0x01, 0x02, 0x03};
  std::vector<uint8_t> packet2 = {0x04, 0x05, 0x06};
  EnqueueAclUpEnd(queue_up_end, packet1);
  EnqueueAclUpEnd(queue_up_end, packet2);

  packet_future_->wait();
  VerifyPacket(handle, packet1);
  VerifyPacket(handle, packet2);
  ASSERT_EQ(round_robin_scheduler_->GetCredits(), controller_->max_acl_packet_credits_ - 2);

  round_robin_scheduler_->Unregister(handle);
}

TEST_F(RoundRobinSchedulerTest, buffer_packet_from_two_connections) {
  uint16_t handle = 0x01;
  uint16_t le_handle = 0x02;
  auto connection_queue = std::make_shared<AclConnection::Queue>(10);
  auto le_connection_queue = std::make_shared<AclConnection::Queue>(10);

  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::LE, le_handle, le_connection_queue);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(2));
  AclConnection::QueueUpEnd* queue_up_end = connection_queue->GetUpEnd();
  AclConnection::QueueUpEnd* le_queue_up_end = le_connection_queue->GetUpEnd();
  std::vector<uint8_t> packet = {0x01, 0x02, 0x03};
  std::vector<uint8_t> le_packet = {0x04, 0x05, 0x06};
  EnqueueAclUpEnd(le_queue_up_end, le_packet);
  EnqueueAclUpEnd(queue_up_end, packet);

  packet_future_->wait();
  VerifyPacket(le_handle, le_packet);
  VerifyPacket(handle, packet);
  ASSERT_EQ(round_robin_scheduler_->GetCredits(), controller_->max_acl_packet_credits_ - 1);
  ASSERT_EQ(round_robin_scheduler_->GetLeCredits(), controller_->le_max_acl_packet_credits_ - 1);

  round_robin_scheduler_->Unregister(handle);
  round_robin_scheduler_->Unregister(le_handle);
}

TEST_F(RoundRobinSchedulerTest, do_not_register_when_credits_is_zero) {
  uint16_t handle = 0x01;
  auto connection_queue = std::make_shared<AclConnection::Queue>(15);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(10));
  AclConnection::QueueUpEnd* queue_up_end = connection_queue->GetUpEnd();
  for (uint8_t i = 0; i < 15; i++) {
    std::vector<uint8_t> packet = {0x01, 0x02, 0x03, i};
    EnqueueAclUpEnd(queue_up_end, packet);
  }

  packet_future_->wait();
  for (uint8_t i = 0; i < 10; i++) {
    std::vector<uint8_t> packet = {0x01, 0x02, 0x03, i};
    VerifyPacket(handle, packet);
  }
  ASSERT_EQ(round_robin_scheduler_->GetCredits(), 0);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(5));
  controller_->SendCompletedAclPacketsCallback(0x01, 10);
  sync_handler();
  packet_future_->wait();
  for (uint8_t i = 10; i < 15; i++) {
    std::vector<uint8_t> packet = {0x01, 0x02, 0x03, i};
    VerifyPacket(handle, packet);
  }
  ASSERT_EQ(round_robin_scheduler_->GetCredits(), 5);

  round_robin_scheduler_->Unregister(handle);
}

TEST_F(RoundRobinSchedulerTest, reveived_completed_callback_with_unknown_handle) {
  controller_->SendCompletedAclPacketsCallback(0x00, 1);
  sync_handler();
  EXPECT_EQ(round_robin_scheduler_->GetCredits(), controller_->max_acl_packet_credits_);
  EXPECT_EQ(round_robin_scheduler_->GetLeCredits(), controller_->le_max_acl_packet_credits_);
}

TEST_F(RoundRobinSchedulerTest, buffer_packet_intervally) {
  uint16_t handle1 = 0x01;
  uint16_t handle2 = 0x02;
  uint16_t le_handle1 = 0x03;
  uint16_t le_handle2 = 0x04;
  auto connection_queue1 = std::make_shared<AclConnection::Queue>(10);
  auto connection_queue2 = std::make_shared<AclConnection::Queue>(10);
  auto le_connection_queue1 = std::make_shared<AclConnection::Queue>(10);
  auto le_connection_queue2 = std::make_shared<AclConnection::Queue>(10);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(18));
  AclConnection::QueueUpEnd* queue_up_end1 = connection_queue1->GetUpEnd();
  AclConnection::QueueUpEnd* queue_up_end2 = connection_queue2->GetUpEnd();
  AclConnection::QueueUpEnd* le_queue_up_end1 = le_connection_queue1->GetUpEnd();
  AclConnection::QueueUpEnd* le_queue_up_end2 = le_connection_queue2->GetUpEnd();

  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle1, connection_queue1);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle2, connection_queue2);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::LE, le_handle1, le_connection_queue1);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::LE, le_handle2, le_connection_queue2);

  std::vector<uint8_t> packet = {0x01, 0x02, 0x03};
  EnqueueAclUpEnd(queue_up_end1, packet);
  EnqueueAclUpEnd(le_queue_up_end2, packet);
  for (uint8_t i = 0; i < 4; i++) {
    std::vector<uint8_t> packet1 = {0x01, 0x02, 0x03, i};
    std::vector<uint8_t> packet2 = {0x02, 0x02, 0x03, i};
    std::vector<uint8_t> le_packet1 = {0x04, 0x05, 0x06, i};
    std::vector<uint8_t> le_packet2 = {0x05, 0x05, 0x06, i};
    EnqueueAclUpEnd(queue_up_end1, packet1);
    EnqueueAclUpEnd(queue_up_end2, packet2);
    EnqueueAclUpEnd(le_queue_up_end1, le_packet1);
    EnqueueAclUpEnd(le_queue_up_end2, le_packet2);
  }

  packet_future_->wait();
  VerifyPacket(handle1, packet);
  VerifyPacket(le_handle2, packet);
  for (uint8_t i = 0; i < 4; i++) {
    std::vector<uint8_t> packet1 = {0x01, 0x02, 0x03, i};
    std::vector<uint8_t> packet2 = {0x02, 0x02, 0x03, i};
    std::vector<uint8_t> le_packet1 = {0x04, 0x05, 0x06, i};
    std::vector<uint8_t> le_packet2 = {0x05, 0x05, 0x06, i};
    VerifyPacket(handle1, packet1);
    VerifyPacket(handle2, packet2);
    VerifyPacket(le_handle1, le_packet1);
    VerifyPacket(le_handle2, le_packet2);
  }

  ASSERT_EQ(round_robin_scheduler_->GetCredits(), controller_->max_acl_packet_credits_ - 9);
  ASSERT_EQ(round_robin_scheduler_->GetLeCredits(), controller_->le_max_acl_packet_credits_ - 9);

  round_robin_scheduler_->Unregister(handle1);
  round_robin_scheduler_->Unregister(handle2);
  round_robin_scheduler_->Unregister(le_handle1);
  round_robin_scheduler_->Unregister(le_handle2);
}

TEST_F(RoundRobinSchedulerTest, send_fragments_without_interval) {
  uint16_t handle = 0x01;
  uint16_t le_handle = 0x02;
  auto connection_queue = std::make_shared<AclConnection::Queue>(10);
  auto le_connection_queue = std::make_shared<AclConnection::Queue>(10);

  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::LE, le_handle, le_connection_queue);

  ASSERT_NO_FATAL_FAILURE(SetPacketFuture(5));
  AclConnection::QueueUpEnd* queue_up_end = connection_queue->GetUpEnd();
  AclConnection::QueueUpEnd* le_queue_up_end = le_connection_queue->GetUpEnd();
  std::vector<uint8_t> packet(controller_->hci_mtu_, 0xff);
  std::vector<uint8_t> packet_part1(controller_->hci_mtu_, 0xff);
  std::vector<uint8_t> packet_part2 = {0x03, 0x02, 0x01};
  packet.insert(packet.end(), packet_part2.begin(), packet_part2.end());

  std::vector<uint8_t> le_packet;
  std::vector<uint8_t> le_packet_part1;
  std::vector<uint8_t> le_packet_part2;
  std::vector<uint8_t> le_packet_part3;
  for (uint8_t i = 0; i < controller_->le_hci_mtu_; i++) {
    le_packet.push_back(i);
    le_packet_part1.push_back(i);
    le_packet_part2.push_back(i * 2);
    le_packet_part3.push_back(i * 3);
  }
  le_packet.insert(le_packet.end(), le_packet_part2.begin(), le_packet_part2.end());
  le_packet.insert(le_packet.end(), le_packet_part3.begin(), le_packet_part3.end());

  EnqueueAclUpEnd(le_queue_up_end, le_packet);
  EnqueueAclUpEnd(queue_up_end, packet);

  packet_future_->wait();
  VerifyPacket(le_handle, le_packet_part1);
  VerifyPacket(le_handle, le_packet_part2);
  VerifyPacket(le_handle, le_packet_part3);
  VerifyPacket(handle, packet_part1);
  VerifyPacket(handle, packet_part2);
  ASSERT_EQ(round_robin_scheduler_->GetCredits(), controller_->max_acl_packet_credits_ - 2);
  ASSERT_EQ(round_robin_scheduler_->GetLeCredits(), controller_->le_max_acl_packet_credits_ - 3);

  round_robin_scheduler_->Unregister(handle);
  round_robin_scheduler_->Unregister(le_handle);
}

TEST_F(RoundRobinSchedulerTest, receive_le_credit_when_next_fragment_is_classic) {
  uint16_t handle = 0x01;
  uint16_t le_handle = 0x02;
  auto connection_queue = std::make_shared<AclConnection::Queue>(20);
  auto le_connection_queue = std::make_shared<AclConnection::Queue>(20);

  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::CLASSIC, handle, connection_queue);
  round_robin_scheduler_->Register(RoundRobinScheduler::ConnectionType::LE, le_handle, le_connection_queue);

  ASSERT_NO_FATAL_FAILURE(
      SetPacketFuture(controller_->le_max_acl_packet_credits_ + controller_->max_acl_packet_credits_));
  AclConnection::QueueUpEnd* queue_up_end = connection_queue->GetUpEnd();
  AclConnection::QueueUpEnd* le_queue_up_end = le_connection_queue->GetUpEnd();
  std::vector<uint8_t> huge_packet(2000);
  std::vector<uint8_t> packet = {0x01, 0x02, 0x03};
  std::vector<uint8_t> le_packet = {0x04, 0x05, 0x06};

  // Make le_acl_packet_credits_ = 0;
  for (uint16_t i = 0; i < controller_->le_max_acl_packet_credits_; i++) {
    EnqueueAclUpEnd(le_queue_up_end, le_packet);
  }

  // Make acl_packet_credits_ = 0 and remain 1 acl fragment in fragments_to_send_
  for (uint16_t i = 0; i < controller_->max_acl_packet_credits_ - 1; i++) {
    EnqueueAclUpEnd(queue_up_end, packet);
  }
  EnqueueAclUpEnd(queue_up_end, huge_packet);

  packet_future_->wait();

  // Trigger start_round_robin
  controller_->SendCompletedAclPacketsCallback(0x02, 1);
  std::this_thread::sleep_for(std::chrono::milliseconds(20));

  ASSERT_EQ(round_robin_scheduler_->GetCredits(), 0);
  ASSERT_EQ(round_robin_scheduler_->GetLeCredits(), 1);

  round_robin_scheduler_->Unregister(handle);
  round_robin_scheduler_->Unregister(le_handle);
}

}  // namespace
}  // namespace acl_manager
}  // namespace hci
}  // namespace bluetooth

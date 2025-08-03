/*
 * Copyright 2019 The Android Open Source Project
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

#include "l2cap/internal/scheduler_fifo.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "l2cap/internal/channel_impl_mock.h"
#include "l2cap/internal/data_controller_mock.h"
#include "l2cap/internal/data_pipeline_manager_mock.h"
#include "os/handler.h"
#include "os/mock_queue.h"
#include "os/thread.h"
#include "packet/raw_builder.h"

namespace bluetooth {
namespace l2cap {
namespace internal {
namespace {

using ::testing::_;
using ::testing::Return;

std::unique_ptr<packet::BasePacketBuilder> CreateSdu(std::vector<uint8_t> payload) {
  auto raw_builder = std::make_unique<packet::RawBuilder>();
  raw_builder->AddOctets(payload);
  return raw_builder;
}

PacketView<kLittleEndian> GetPacketView(std::unique_ptr<packet::BasePacketBuilder> packet) {
  auto bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter i(*bytes);
  bytes->reserve(packet->size());
  packet->Serialize(i);
  return packet::PacketView<packet::kLittleEndian>(bytes);
}

class MyDataController : public testing::MockDataController {
 public:
  std::unique_ptr<BasePacketBuilder> GetNextPacket() override {
    auto next = std::move(next_packets.front());
    next_packets.pop();
    return next;
  }

  std::queue<std::unique_ptr<BasePacketBuilder>> next_packets;
};

class L2capSchedulerFifoTest : public ::testing::Test {
 protected:
  void SetUp() override {
    thread_ = new os::Thread("test_thread", os::Thread::Priority::NORMAL);
    queue_handler_ = new os::Handler(thread_);
    mock_data_pipeline_manager_ = new testing::MockDataPipelineManager(queue_handler_, &queue_end_);
    fifo_ = new Fifo(mock_data_pipeline_manager_, &queue_end_, queue_handler_);
  }

  void TearDown() override {
    delete fifo_;
    delete mock_data_pipeline_manager_;
    queue_handler_->Clear();
    delete queue_handler_;
    delete thread_;
  }

  os::Thread* thread_ = nullptr;
  os::Handler* queue_handler_ = nullptr;
  os::MockIQueueDequeue<Scheduler::LowerDequeue> dequeue_;
  os::MockIQueueEnqueue<Scheduler::LowerEnqueue> enqueue_;
  common::BidiQueueEnd<Scheduler::LowerEnqueue, Scheduler::LowerDequeue> queue_end_{&enqueue_, &dequeue_};
  testing::MockDataPipelineManager* mock_data_pipeline_manager_ = nullptr;
  MyDataController data_controller_1_;
  MyDataController data_controller_2_;
  Fifo* fifo_ = nullptr;
};

TEST_F(L2capSchedulerFifoTest, send_packet) {
  auto frame = BasicFrameBuilder::Create(1, CreateSdu({'a', 'b', 'c'}));
  data_controller_1_.next_packets.push(std::move(frame));
  EXPECT_CALL(*mock_data_pipeline_manager_, GetDataController(_)).WillOnce(Return(&data_controller_1_));
  EXPECT_CALL(*mock_data_pipeline_manager_, OnPacketSent(1));
  fifo_->OnPacketsReady(1, 1);
  enqueue_.run_enqueue();
  auto&& packet = enqueue_.enqueued.front();
  auto packet_view = GetPacketView(std::move(packet));
  auto basic_frame_view = BasicFrameView::Create(packet_view);
  ASSERT_TRUE(basic_frame_view.IsValid());
  ASSERT_EQ(basic_frame_view.GetChannelId(), 1);
  auto payload = basic_frame_view.GetPayload();
  ASSERT_EQ(std::string(payload.begin(), payload.end()), "abc");
  enqueue_.enqueued.pop();
}

TEST_F(L2capSchedulerFifoTest, prioritize_channel) {
  auto frame = BasicFrameBuilder::Create(1, CreateSdu({'a', 'b', 'c'}));
  data_controller_1_.next_packets.push(std::move(frame));
  frame = BasicFrameBuilder::Create(2, CreateSdu({'d', 'e', 'f'}));
  data_controller_2_.next_packets.push(std::move(frame));

  EXPECT_CALL(*mock_data_pipeline_manager_, GetDataController(1)).WillRepeatedly(Return(&data_controller_1_));
  EXPECT_CALL(*mock_data_pipeline_manager_, GetDataController(2)).WillRepeatedly(Return(&data_controller_2_));
  EXPECT_CALL(*mock_data_pipeline_manager_, OnPacketSent(1));
  EXPECT_CALL(*mock_data_pipeline_manager_, OnPacketSent(2));
  fifo_->SetChannelTxPriority(1, true);
  fifo_->OnPacketsReady(2, 1);
  fifo_->OnPacketsReady(1, 1);
  enqueue_.run_enqueue(2);
  auto packet1 = std::move(enqueue_.enqueued.front());
  auto packet_view = GetPacketView(std::move(packet1));
  auto basic_frame_view = BasicFrameView::Create(packet_view);
  ASSERT_TRUE(basic_frame_view.IsValid());
  ASSERT_EQ(basic_frame_view.GetChannelId(), 1);
  auto payload = basic_frame_view.GetPayload();
  ASSERT_EQ(std::string(payload.begin(), payload.end()), "abc");
  enqueue_.enqueued.pop();

  auto packet2 = std::move(enqueue_.enqueued.front());
  packet_view = GetPacketView(std::move(packet2));
  basic_frame_view = BasicFrameView::Create(packet_view);
  ASSERT_TRUE(basic_frame_view.IsValid());
  ASSERT_EQ(basic_frame_view.GetChannelId(), 2);
  payload = basic_frame_view.GetPayload();
  ASSERT_EQ(std::string(payload.begin(), payload.end()), "def");
  enqueue_.enqueued.pop();
}

TEST_F(L2capSchedulerFifoTest, remove_channel) {
  auto frame = BasicFrameBuilder::Create(1, CreateSdu({'a', 'b', 'c'}));
  data_controller_1_.next_packets.push(std::move(frame));
  frame = BasicFrameBuilder::Create(2, CreateSdu({'d', 'e', 'f'}));
  data_controller_2_.next_packets.push(std::move(frame));

  EXPECT_CALL(*mock_data_pipeline_manager_, GetDataController(1)).WillRepeatedly(Return(&data_controller_1_));
  EXPECT_CALL(*mock_data_pipeline_manager_, GetDataController(2)).WillRepeatedly(Return(&data_controller_2_));
  EXPECT_CALL(*mock_data_pipeline_manager_, OnPacketSent(2));
  fifo_->OnPacketsReady(1, 1);
  fifo_->OnPacketsReady(2, 1);
  fifo_->RemoveChannel(1);
  enqueue_.run_enqueue(1);
  auto packet1 = std::move(enqueue_.enqueued.front());
  auto packet_view = GetPacketView(std::move(packet1));
  auto basic_frame_view = BasicFrameView::Create(packet_view);
  ASSERT_TRUE(basic_frame_view.IsValid());
  ASSERT_EQ(basic_frame_view.GetChannelId(), 2);
  auto payload = basic_frame_view.GetPayload();
  ASSERT_EQ(std::string(payload.begin(), payload.end()), "def");
  enqueue_.enqueued.pop();
}

}  // namespace
}  // namespace internal
}  // namespace l2cap
}  // namespace bluetooth

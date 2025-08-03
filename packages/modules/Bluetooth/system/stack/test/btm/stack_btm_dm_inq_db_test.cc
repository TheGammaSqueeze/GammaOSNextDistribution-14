/*
 * Copyright 2023 The Android Open Source Project
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

#include <base/strings/stringprintf.h>
#include <gtest/gtest.h>
#include <stdlib.h>

#include <deque>
#include <mutex>
#include <queue>

#include "stack/btm/btm_int_types.h"
#include "stack/btm/neighbor_inquiry.h"
#include "stack/include/inq_hci_link_interface.h"
#include "test/common/mock_functions.h"
#include "test/fake/fake_looper.h"
#include "test/fake/fake_osi.h"
#include "test/fake/fake_thread.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_osi_thread.h"

extern tBTM_CB btm_cb;
extern void btm_init_inq_result_flt(void);
extern void btm_clr_inq_result_flt(void);

namespace {
constexpr size_t kNumberOfThreads = 8;
constexpr size_t kEntriesPerThread =
    static_cast<size_t>(BTM_INQ_DB_SIZE) / kNumberOfThreads;

constexpr RawAddress* kClearAllEntries = nullptr;
}  // namespace

namespace bluetooth {
namespace legacy {
namespace testing {
void btm_clr_inq_db(const RawAddress* p_bda);
uint16_t btm_get_num_bd_entries();
}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

class BtmDmInqDbWithMockTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    fake_osi_ = std::make_unique<test::fake::FakeOsi>();
    test::mock::osi_thread::thread_new.body =
        [](const char* name) -> thread_t* {
      thread_t* thread = new thread_t;
      thread->name_ = std::string(name);
      thread_start_arg_t start_arg;
      start_arg.thread = thread;
      pthread_create(&thread->pthread_, nullptr, run_message_loop, &start_arg);
      // Wait for thread to start up with semaphore before continuing
      start_arg.start_sem.wait();
      return thread;
    };
    test::mock::osi_thread::thread_post.body =
        [](thread_t* thread, thread_func func, void* context) -> bool {
      if (!thread->is_running()) return false;
      {
        std::lock_guard<std::mutex> lock(thread->work_queue_semaphore.mutex_);
        thread->work_queue.push(std::make_pair(func, context));
      }
      thread->work_queue_semaphore.notify();
      return true;
    };
    test::mock::osi_thread::thread_free.body = [](thread_t* thread) {
      thread->quiesce();
      pthread_join(thread->pthread_, nullptr);
      thread->work_queue = {};
      delete thread;
    };
  }

  void TearDown() override {
    test::mock::osi_thread::thread_free = {};
    test::mock::osi_thread::thread_post = {};
    test::mock::osi_thread::thread_new = {};
  }
  std::unique_ptr<test::fake::FakeOsi> fake_osi_;
};

class BtmDmInqDbTest : public BtmDmInqDbWithMockTest {
 protected:
  void SetUp() override {
    BtmDmInqDbWithMockTest::SetUp();
    bluetooth::legacy::testing::btm_clr_inq_db(kClearAllEntries);
    btm_init_inq_result_flt();
  }

  void TearDown() override {
    btm_clr_inq_result_flt();
    bluetooth::legacy::testing::btm_clr_inq_db(kClearAllEntries);
    BtmDmInqDbWithMockTest::TearDown();
  }
};

class BtmDmInqDbThreadedTest : public BtmDmInqDbTest {
 protected:
  void SetUp() override { BtmDmInqDbTest::SetUp(); }

  void TearDown() override { BtmDmInqDbTest::TearDown(); }

  void setup_thread() {
    for (size_t i = 0; i < kNumberOfThreads; i++) {
      std::string name = base::StringPrintf("thread:%zu", i);
      threads[i] = thread_new(name.c_str());
    }
  }

  void teardown_thread() {
    for (size_t i = 0; i < kNumberOfThreads; i++) {
      thread_free(threads[i]);
    }
  }

  thread_t* threads[kNumberOfThreads];
};

struct context_t {
  std::deque<tINQ_DB_ENT*> inq_db_queue[kNumberOfThreads];
};

struct entry_data_t {
  int thread_id;
  std::deque<tINQ_DB_ENT*>* inq_db_queue;
};

RawAddress RawAddressMaker(int thread_id, int subid) {
  RawAddress bd_addr = {};
  // tODO Use const std::array<uint8_t, kLength> array)
  bd_addr.address[0] = 0x0a;
  bd_addr.address[1] = 0x0b;
  bd_addr.address[2] = 0x0c;
  bd_addr.address[3] = 0x0d;
  bd_addr.address[4] = (uint8_t)thread_id;
  bd_addr.address[5] = (uint8_t)subid;
  return bd_addr;
}

void allocate_db_entry(void* context) {
  entry_data_t* data = static_cast<entry_data_t*>(context);
  RawAddress p_bda =
      RawAddressMaker(data->thread_id, (int)data->inq_db_queue->size());
  tINQ_DB_ENT* ent = btm_inq_db_new(p_bda);
  data->inq_db_queue->push_back(ent);
}

TEST_F(BtmDmInqDbThreadedTest, btm_inq_db_new) {
  this->setup_thread();

  context_t context = {};

  for (size_t j = 0; j < kEntriesPerThread; j++) {
    for (size_t i = 0; i < kNumberOfThreads; i++) {
      data_t* data = static_cast<data_t*>(calloc(sizeof(data_t), 1));
      data->thread_id = i;
      data->inq_db_queue = &context.inq_db_queue[i];
      ASSERT_TRUE(
          thread_post(threads[i], allocate_db_entry, static_cast<void*>(data)));
    }
  }

  this->teardown_thread();

  int failed = 0;
  for (size_t i = 0; i < kNumberOfThreads; i++) {
    ASSERT_EQ(kEntriesPerThread, context.inq_db_queue[i].size());
    for (const auto& it : context.inq_db_queue[i]) {
      RawAddress exp = it->inq_info.results.remote_bd_addr;
      exp.address[4] = i;
      if (exp != it->inq_info.results.remote_bd_addr) {
        EXPECT_EQ(exp, it->inq_info.results.remote_bd_addr);
        failed++;
      }
    }
  }
  ASSERT_EQ(0, failed);
}

struct address_data_t {
  int thread_id;
  int offset;
};

void check_address(void* context) {
  address_data_t* data = static_cast<address_data_t*>(context);
  RawAddress p_bda = RawAddressMaker(data->thread_id, data->offset);
  // Make sure it's new
  ASSERT_FALSE(btm_inq_find_bdaddr(p_bda));
}

TEST_F(BtmDmInqDbThreadedTest, btm_inq_find_bdaddr) {
  this->setup_thread();
  context_t context = {};

  for (size_t j = 0; j < kEntriesPerThread; j++) {
    for (size_t i = 0; i < kNumberOfThreads; i++) {
      address_data_t* data =
          static_cast<address_data_t*>(calloc(sizeof(address_data_t), 1));
      data->thread_id = i;
      data->offset = (int)j;
      ASSERT_TRUE(
          thread_post(threads[i], check_address, static_cast<void*>(data)));
    }
  }

  this->teardown_thread();

  ASSERT_EQ((uint16_t)(kNumberOfThreads * kEntriesPerThread),
            bluetooth::legacy::testing::btm_get_num_bd_entries());
}

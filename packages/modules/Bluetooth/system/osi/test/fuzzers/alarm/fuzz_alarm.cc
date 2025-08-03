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

#include <fcntl.h>
#include <fuzzer/FuzzedDataProvider.h>
#include "osi/include/alarm.h"

#include "common/message_loop_thread.h"

using base::Closure;
using bluetooth::common::MessageLoopThread;

#define MAX_CONCURRENT_ALARMS 25
#define MAX_BUFFER_LEN 4096
#define MAX_ALARM_DURATION 25

class btsemaphore {
 public:
  void post() {
    std::lock_guard<std::mutex> lock(mMutex);
    ++mCount;
    mCondition.notify_one();
  }

  void wait() {
    std::unique_lock<std::mutex> lock(mMutex);
    while (!mCount) {
      mCondition.wait(lock);
    }
    --mCount;
  }

  bool try_wait() {
    std::lock_guard<std::mutex> lock(mMutex);
    if (mCount) {
      --mCount;
      return true;
    }
    return false;
  }

 private:
  std::mutex mMutex;
  std::condition_variable mCondition;
  unsigned long mCount = 0;
};
static btsemaphore semaphore;
static int cb_counter;
static MessageLoopThread* thread = new MessageLoopThread("fake main thread");

bluetooth::common::MessageLoopThread* get_main_thread() { return thread; }

static void cb(void* data) {
  ++cb_counter;
  semaphore.post();
}

void setup() {
  cb_counter = 0;
}
void teardown() { }

alarm_t* fuzz_init_alarm(FuzzedDataProvider* dataProvider) {
  size_t name_len =
      dataProvider->ConsumeIntegralInRange<size_t>(0, MAX_BUFFER_LEN);
  std::vector<char> alarm_name_vect =
      dataProvider->ConsumeBytesWithTerminator<char>(name_len, '\0');
  char* alarm_name = alarm_name_vect.data();

  // Determine if our alarm will be periodic
  if (dataProvider->ConsumeBool()) {
    return alarm_new_periodic(alarm_name);
  } else {
    return alarm_new(alarm_name);
  }
}

bool fuzz_set_alarm(alarm_t* alarm, uint64_t interval, alarm_callback_t cb,
                    FuzzedDataProvider* dataProvider) {
  // Generate a random buffer (or null)
  void* data_buffer = nullptr;
  size_t buff_len =
      dataProvider->ConsumeIntegralInRange<size_t>(1, MAX_BUFFER_LEN);
  if (buff_len == 0) {
    return false;
  }

  // allocate our space
  std::vector<uint8_t> data_vector =
      dataProvider->ConsumeBytes<uint8_t>(buff_len);
  data_buffer = data_vector.data();

  // Make sure alarm is non-null
  if (alarm) {
    // Should this alarm be regular or on mloop?
    if (dataProvider->ConsumeBool()) {
      alarm_set_on_mloop(alarm, interval, cb, data_buffer);
    } else {
      alarm_set(alarm, interval, cb, data_buffer);
    }
  }

  return true;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* Data, size_t Size) {
  // Init our wrapper
  FuzzedDataProvider dataProvider(Data, Size);

  // Perform setup
  setup();

  alarm_t* alarm = nullptr;
  // Should our alarm be valid or null?
  if (dataProvider.ConsumeBool()) {
    // Init our alarm
    alarm = fuzz_init_alarm(&dataProvider);
  }

  // Set up the alarm & cancel
  // Alarm must be non-null, or set() will trigger assert
  if (alarm) {
    if (!fuzz_set_alarm(alarm, MAX_ALARM_DURATION, cb, &dataProvider)) {
      alarm_free(alarm);
      return 0;
    }
    alarm_cancel(alarm);
  }

  // Check if scheduled
  alarm_is_scheduled(alarm);

  if (alarm) {
    // Set up another set of alarms & let these ones run
    int num_alarms =
        dataProvider.ConsumeIntegralInRange<uint8_t>(0, MAX_CONCURRENT_ALARMS);
    for (int i = 0; i < num_alarms; i++) {
      uint64_t interval =
          dataProvider.ConsumeIntegralInRange<uint64_t>(0, MAX_ALARM_DURATION);
      if (!fuzz_set_alarm(alarm, interval, cb, &dataProvider)) {
        num_alarms = i;
        break;
      }
      alarm_get_remaining_ms(alarm);
    }

    // Wait for them to complete
    for (int i = 1; i <= num_alarms; i++) {
      semaphore.wait();
    }
  }

  // Free the alarm object
  alarm_free(alarm);

  // dump debug data to /dev/null
  int debug_fd = open("/dev/null", O_RDWR);
  alarm_debug_dump(debug_fd);

  // Cleanup
  alarm_cleanup();

  // Perform teardown
  teardown();

  return 0;
}

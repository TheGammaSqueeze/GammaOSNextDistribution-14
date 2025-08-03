/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "location/lbs/contexthub/nanoapps/nearby/app_manager.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

// The following functions define the entry points of Nearby nanoapp.

#define LOG_TAG "[NEARBY][APP_MAIN]"

// Only define one of the macros below.
#if defined(MOCK_PRESENCE_V1) || defined(MOCK_PRESENCE_V0) || \
    defined(MOCK_SUBSEQUENT_PAIR) || defined(MOCK_FAST_PAIR)
#define MOCK_BLE_EVENT true
#include "location/lbs/contexthub/nanoapps/nearby/mock_ble.h"
extern uint32_t mock_ble_timer_id;
extern uint32_t mock_ble_flush_complete_timer_id;
#endif

bool nanoappStart(void) {
  // Initialize the AppManager singleton.
  // Must be done before invoking AppManagerSingleton::get().
  ::nearby::AppManagerSingleton::init();
  return ::nearby::AppManagerSingleton::get()->IsInitialized();
}

void nanoappEnd(void) {
  ::nearby::AppManagerSingleton::deinit();
}

void nanoappHandleEvent(uint32_t sender_instance_id, uint16_t event_type,
                        const void *event_data) {
#ifdef MOCK_BLE_EVENT
  if (event_type == CHRE_EVENT_BLE_ADVERTISEMENT) {
    // Throw away real BLE events.
    return;
  }
  if (event_type == CHRE_EVENT_TIMER) {
    if (event_data == &mock_ble_timer_id) {
      // Change a timer event to a mock BLE event.
      LOGI("Mocked BLE event.");
      event_data = &nearby::MockBle::kBleEvent;
      event_type = CHRE_EVENT_BLE_ADVERTISEMENT;
    } else if (event_data == &mock_ble_flush_complete_timer_id) {
      // Change a timer event to a mock batch complete event.
      LOGI("Mocked BLE batch complete event.");
      event_data = &nearby::MockBle::kBleBatchCompleteEvent;
      event_type = CHRE_EVENT_BLE_BATCH_COMPLETE;
      ::nearby::AppManagerSingleton::get()->HandleEvent(sender_instance_id,
                                                        event_type, event_data);

      // Change a timer event to a mock BLE flush complete event.
      LOGI("Mocked BLE flush complete event.");
      event_data = &nearby::MockBle::kBleFlushCompleteEvent;
      event_type = CHRE_EVENT_BLE_FLUSH_COMPLETE;
      ::nearby::AppManagerSingleton::get()->HandleEvent(sender_instance_id,
                                                        event_type, event_data);
      return;
    }
  }
#endif
  ::nearby::AppManagerSingleton::get()->HandleEvent(sender_instance_id,
                                                    event_type, event_data);
}

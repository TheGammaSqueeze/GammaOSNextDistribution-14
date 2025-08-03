/*
 * Copyright 2017 The Android Open Source Project
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

#include <list>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "bta_gatt_api.h"

class BtaGattServerQueue {
 public:
  static void Clean(uint16_t conn_id);
  static void SendNotification(uint16_t conn_id, uint16_t handle,
                               std::vector<uint8_t> value, bool need_confirm);
  static void NotificationCallback(uint16_t conn_id);
  static void CongestionCallback(uint16_t conn_id, bool congested);

  /* Holds pending GATT operations */
  struct gatts_operation {
    uint8_t type;
    uint16_t attr_id;
    std::vector<uint8_t> value;
    bool need_confirm;
  };

 private:
  static bool is_congested;
  static void mark_as_not_executing(uint16_t conn_id);
  static void gatts_execute_next_op(uint16_t conn_id);

  // maps connection id to operations waiting for execution
  static std::unordered_map<uint16_t, std::list<gatts_operation>>
      gatts_op_queue;

  // maps connection id to congestion status of each device
  static std::unordered_map<uint16_t, bool> congestion_queue;

  // contain connection ids that currently execute operations
  static std::unordered_set<uint16_t> gatts_op_queue_executing;
};
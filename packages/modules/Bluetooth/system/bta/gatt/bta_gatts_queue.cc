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

#define LOG_TAG "gatt"

#include <bluetooth/log.h>

#include <list>
#include <unordered_map>
#include <unordered_set>

#include "bta_gatt_server_queue.h"
#include "os/log.h"

using gatts_operation = BtaGattServerQueue::gatts_operation;
using bluetooth::Uuid;
using namespace bluetooth;

constexpr uint8_t GATT_NOTIFY = 1;

std::unordered_map<uint16_t, std::list<gatts_operation>>
    BtaGattServerQueue::gatts_op_queue;
std::unordered_set<uint16_t> BtaGattServerQueue::gatts_op_queue_executing;
std::unordered_map<uint16_t, bool> BtaGattServerQueue::congestion_queue;

void BtaGattServerQueue::mark_as_not_executing(uint16_t conn_id) {
  gatts_op_queue_executing.erase(conn_id);
}

void BtaGattServerQueue::gatts_execute_next_op(uint16_t conn_id) {
  log::verbose("conn_id=0x{:x}", conn_id);

  if (gatts_op_queue.empty()) {
    log::verbose("op queue is empty");
    return;
  }

  auto ptr = congestion_queue.find(conn_id);

  if (ptr != congestion_queue.end()) {
    bool is_congested = ptr->second;
    log::verbose("congestion queue exist, conn_id: {}, is_congested: {}",
                 conn_id, is_congested);
    if (is_congested) {
      log::verbose("lower layer is congested");
      return;
    }
  }

  auto map_ptr = gatts_op_queue.find(conn_id);

  if (map_ptr == gatts_op_queue.end()) {
    log::verbose("Queue is null");
    return;
  }

  if (map_ptr->second.empty()) {
    log::verbose("queue is empty for conn_id: {}", conn_id);
    return;
  }

  if (gatts_op_queue_executing.count(conn_id)) {
    log::verbose("can't enqueue next op, already executing");
    return;
  }

  gatts_operation op = map_ptr->second.front();
  log::verbose("op.type={}, attr_id={}", op.type, op.attr_id);

  if (op.type == GATT_NOTIFY) {
    BTA_GATTS_HandleValueIndication(conn_id, op.attr_id, op.value,
                                    op.need_confirm);
    gatts_op_queue_executing.insert(conn_id);
  }
}

void BtaGattServerQueue::Clean(uint16_t conn_id) {
  log::verbose("conn_id=0x{:x}", conn_id);

  gatts_op_queue.erase(conn_id);
  gatts_op_queue_executing.erase(conn_id);
}

void BtaGattServerQueue::SendNotification(uint16_t conn_id, uint16_t handle,
                                          std::vector<uint8_t> value,
                                          bool need_confirm) {
  gatts_op_queue[conn_id].emplace_back(
      gatts_operation{.type = GATT_NOTIFY,
                      .attr_id = handle,
                      .value = value,
                      .need_confirm = need_confirm});
  gatts_execute_next_op(conn_id);
}

void BtaGattServerQueue::NotificationCallback(uint16_t conn_id) {
  auto map_ptr = gatts_op_queue.find(conn_id);
  if (map_ptr == gatts_op_queue.end() || map_ptr->second.empty()) {
    log::verbose("no more operations queued for conn_id {}", conn_id);
    return;
  }

  gatts_operation op = map_ptr->second.front();
  map_ptr->second.pop_front();
  mark_as_not_executing(conn_id);
  gatts_execute_next_op(conn_id);
}

void BtaGattServerQueue::CongestionCallback(uint16_t conn_id, bool congested) {
  log::verbose("conn_id: {}, congested: {}", conn_id, congested);

  congestion_queue[conn_id] = congested;
  if (!congested) {
    gatts_execute_next_op(conn_id);
  }
}

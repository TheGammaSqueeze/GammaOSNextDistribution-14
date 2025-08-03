/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#pragma once

#include <base/logging.h>
#include <bluetooth/log.h>

#include <list>
#include <optional>
#include <vector>

#include "hardware/bt_has.h"
#include "has_preset.h"
#include "osi/include/alarm.h"

namespace le_audio {
namespace has {
/* HAS control point Change Id */
enum class PresetCtpChangeId : uint8_t {
  PRESET_GENERIC_UPDATE = 0,
  PRESET_DELETED,
  PRESET_AVAILABLE,
  PRESET_UNAVAILABLE,
  /* NOTICE: Values below are for internal use only of this particular
   * implementation, and do not correspond to any bluetooth specification.
   */
  CHANGE_ID_MAX_ = PRESET_UNAVAILABLE,
};
std::ostream& operator<<(std::ostream& out, const PresetCtpChangeId value);

/* HAS control point Opcodes */
enum class PresetCtpOpcode : uint8_t {
  READ_PRESETS = 1,
  READ_PRESET_RESPONSE,
  PRESET_CHANGED,
  WRITE_PRESET_NAME,
  SET_ACTIVE_PRESET,
  SET_NEXT_PRESET,
  SET_PREV_PRESET,
  SET_ACTIVE_PRESET_SYNC,
  SET_NEXT_PRESET_SYNC,
  SET_PREV_PRESET_SYNC,
  /* NOTICE: Values below are for internal use only of this particular
   * implementation, and do not correspond to any bluetooth specification.
   */
  OP_MAX_ = SET_PREV_PRESET_SYNC,
  OP_NONE_ = OP_MAX_ + 1,
};
std::ostream& operator<<(std::ostream& out, const PresetCtpOpcode value);

static constexpr uint16_t PresetCtpOpcode2Bitmask(PresetCtpOpcode op) {
  return ((uint16_t)0b1 << static_cast<std::underlying_type_t<PresetCtpOpcode>>(
              op));
}

/* Mandatory opcodes if control point characteristic exists */
static constexpr uint16_t kControlPointMandatoryOpcodesBitmask =
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::READ_PRESETS) |
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_ACTIVE_PRESET) |
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_NEXT_PRESET) |
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_PREV_PRESET);

/* Optional coordinated operation opcodes */
static constexpr uint16_t kControlPointSynchronizedOpcodesBitmask =
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC) |
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_NEXT_PRESET_SYNC) |
    PresetCtpOpcode2Bitmask(PresetCtpOpcode::SET_PREV_PRESET_SYNC);

/* Represents HAS Control Point value notification */
struct HasCtpNtf {
  PresetCtpOpcode opcode;
  PresetCtpChangeId change_id;
  bool is_last;
  union {
    uint8_t index;
    uint8_t prev_index;
  };
  std::optional<HasPreset> preset;

  static std::optional<HasCtpNtf> FromCharacteristicValue(uint16_t len,
                                                          const uint8_t* value);
};
std::ostream& operator<<(std::ostream& out, const HasCtpNtf& value);

/* Represents HAS Control Point operation request */
struct HasCtpOp {
  std::variant<RawAddress, int> addr_or_group;
  PresetCtpOpcode opcode;
  uint8_t index;
  uint8_t num_of_indices;
  std::optional<std::string> name;
  uint16_t op_id;

  HasCtpOp(std::variant<RawAddress, int> addr_or_group_id, PresetCtpOpcode op,
           uint8_t index = bluetooth::has::kHasPresetIndexInvalid,
           uint8_t num_of_indices = 1,
           std::optional<std::string> name = std::nullopt)
      : addr_or_group(addr_or_group_id),
        opcode(op),
        index(index),
        num_of_indices(num_of_indices),
        name(name) {
    /* Skip 0 on roll-over */
    last_op_id_ += 1;
    if (last_op_id_ == 0) last_op_id_ = 1;
    op_id = last_op_id_;
  }

  std::vector<uint8_t> ToCharacteristicValue(void) const;

  bool IsGroupRequest() const {
    return std::holds_alternative<int>(addr_or_group);
  }

  int GetGroupId() const {
    return std::holds_alternative<int>(addr_or_group)
               ? std::get<int>(addr_or_group)
               : -1;
  }

  RawAddress GetDeviceAddr() const {
    return std::holds_alternative<RawAddress>(addr_or_group)
               ? std::get<RawAddress>(addr_or_group)
               : RawAddress::kEmpty;
  }

  bool IsSyncedOperation() const {
    return (opcode == PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC) ||
           (opcode == PresetCtpOpcode::SET_NEXT_PRESET_SYNC) ||
           (opcode == PresetCtpOpcode::SET_PREV_PRESET_SYNC);
  }

 private:
  /* It's fine for this to roll-over eventually */
  static uint16_t last_op_id_;
};
std::ostream& operator<<(std::ostream& out, const HasCtpOp& value);

/* Used to track group operations. SetCompleted() allows to mark
 * a single device as operation-completed when notification is received.
 * When all the devices are SetComplete'd, timeout timer is being canceled and
 * a group operation can be considered completed (IsFullyCompleted() == true).
 *
 * NOTICE: A single callback and reference counter is being used for all the
 *         coordinator instances, therefore creating more instances result
 *         in timeout timer being rescheduled. User should remove all the
 *         pending op. coordinators in the timer timeout callback.
 */
struct HasCtpGroupOpCoordinator {
  std::list<RawAddress> devices;
  HasCtpOp operation;
  std::list<bluetooth::has::PresetInfo> preset_info_verification_list;

  static size_t ref_cnt;
  static alarm_t* operation_timeout_timer;
  static constexpr uint16_t kOperationTimeoutMs = 10000u;
  static alarm_callback_t cb;

  static void Initialize(alarm_callback_t c = nullptr) {
    operation_timeout_timer = nullptr;
    ref_cnt = 0;
    cb = c;
  }

  static void Cleanup() {
    if (operation_timeout_timer != nullptr) {
      if (alarm_is_scheduled(operation_timeout_timer)) {
        DLOG(INFO) << __func__ << +ref_cnt;
        alarm_cancel(operation_timeout_timer);
      }
      alarm_free(operation_timeout_timer);
      operation_timeout_timer = nullptr;
    }

    ref_cnt = 0;
  }

  static bool IsFullyCompleted() { return ref_cnt == 0; }
  static bool IsPending() { return ref_cnt != 0; }

  HasCtpGroupOpCoordinator() = delete;
  HasCtpGroupOpCoordinator& operator=(const HasCtpGroupOpCoordinator&) = delete;
  /* NOTICE: It cannot be non-copyable if we want to put it into the std::map.
   * The default copy constructor and copy assignment operator would break the
   * reference counting, so we must increment ref_cnt for all the temporary
   * copies.
   */
  HasCtpGroupOpCoordinator(const HasCtpGroupOpCoordinator& other)
      : devices(other.devices),
        operation(other.operation),
        preset_info_verification_list(other.preset_info_verification_list) {
    ref_cnt += other.devices.size();
  }

  HasCtpGroupOpCoordinator(const std::vector<RawAddress>& targets,
                           HasCtpOp operation)
      : operation(operation) {
    LOG_ASSERT(targets.size() != 0) << " Empty device list error.";
    if (targets.size() != 1) {
      LOG_ASSERT(operation.IsGroupRequest()) << " Must be a group operation!";
      LOG_ASSERT(operation.GetGroupId() != -1) << " Must set valid group_id!";
    }

    devices = std::list<RawAddress>(targets.cbegin(), targets.cend());

    ref_cnt += devices.size();
    if (operation_timeout_timer == nullptr) {
      operation_timeout_timer = alarm_new("GroupOpTimer");
    }

    if (alarm_is_scheduled(operation_timeout_timer))
      alarm_cancel(operation_timeout_timer);

    LOG_ASSERT(cb != nullptr) << " Timeout timer callback not set!";
    alarm_set_on_mloop(operation_timeout_timer, kOperationTimeoutMs, cb,
                       nullptr);
  }

  ~HasCtpGroupOpCoordinator() {
    /* Check if cleanup wasn't already called */
    if (ref_cnt != 0) {
      ref_cnt -= devices.size();
      if (ref_cnt == 0) {
        Cleanup();
      }
    }
  }

  bool SetCompleted(RawAddress addr) {
    auto result = false;

    auto it = std::find(devices.begin(), devices.end(), addr);
    if (it != devices.end()) {
      devices.erase(it);
      --ref_cnt;
      result = true;
    }

    if (ref_cnt == 0) {
      alarm_cancel(operation_timeout_timer);
      alarm_free(operation_timeout_timer);
      operation_timeout_timer = nullptr;
    }

    return result;
  }
};

}  // namespace has
}  // namespace le_audio

namespace fmt {
template <>
struct formatter<le_audio::has::HasCtpNtf> : ostream_formatter {};
template <>
struct formatter<le_audio::has::HasCtpOp> : ostream_formatter {};
}  // namespace fmt

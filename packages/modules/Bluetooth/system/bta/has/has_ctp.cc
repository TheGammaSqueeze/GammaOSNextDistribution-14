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

#include "has_ctp.h"

#include <bluetooth/log.h>

#include "os/log.h"
#include "stack/include/bt_types.h"

using namespace bluetooth;

namespace le_audio {
namespace has {

static bool ParsePresetGenericUpdate(uint16_t& len, const uint8_t* value,
                                     HasCtpNtf& ntf) {
  if (len < sizeof(ntf.prev_index) + HasPreset::kCharValueMinSize) {
    log::error("Invalid preset value length={} for generic update.", len);
    return false;
  }

  STREAM_TO_UINT8(ntf.index, value);
  len -= 1;

  ntf.preset = HasPreset::FromCharacteristicValue(len, value);
  return true;
}

static bool ParsePresetIndex(uint16_t& len, const uint8_t* value,
                             HasCtpNtf& ntf) {
  if (len < sizeof(ntf.index)) {
    log::error("Invalid preset value length={} for generic update.", len);
    return false;
  }

  STREAM_TO_UINT8(ntf.index, value);
  len -= 1;
  return true;
}

static bool ParsePresetReadResponse(uint16_t& len, const uint8_t* value,
                                    HasCtpNtf& ntf) {
  if (len < sizeof(ntf.is_last) + HasPreset::kCharValueMinSize) {
    log::error("Invalid preset value length={}", len);
    return false;
  }

  STREAM_TO_UINT8(ntf.is_last, value);
  len -= 1;

  ntf.preset = HasPreset::FromCharacteristicValue(len, value);
  return true;
}

static bool ParsePresetChanged(uint16_t len, const uint8_t* value,
                               HasCtpNtf& ntf) {
  if (len < sizeof(ntf.is_last) + sizeof(ntf.change_id)) {
    log::error("Invalid preset value length={}", len);
    return false;
  }

  uint8_t change_id;
  STREAM_TO_UINT8(change_id, value);
  len -= 1;
  if (change_id > static_cast<std::underlying_type_t<PresetCtpChangeId>>(
                      PresetCtpChangeId::CHANGE_ID_MAX_)) {
    log::error("Invalid preset chenge_id={}", change_id);
    return false;
  }
  ntf.change_id = PresetCtpChangeId(change_id);
  STREAM_TO_UINT8(ntf.is_last, value);
  len -= 1;

  switch (ntf.change_id) {
    case PresetCtpChangeId::PRESET_GENERIC_UPDATE:
      return ParsePresetGenericUpdate(len, value, ntf);
    case PresetCtpChangeId::PRESET_AVAILABLE:
      return ParsePresetIndex(len, value, ntf);
    case PresetCtpChangeId::PRESET_UNAVAILABLE:
      return ParsePresetIndex(len, value, ntf);
    case PresetCtpChangeId::PRESET_DELETED:
      return ParsePresetIndex(len, value, ntf);
    default:
      return false;
  }

  return true;
}

std::optional<HasCtpNtf> HasCtpNtf::FromCharacteristicValue(
    uint16_t len, const uint8_t* value) {
  if (len < 3) {
    log::error("Invalid Cp notification.");
    return std::nullopt;
  }

  uint8_t op;
  STREAM_TO_UINT8(op, value);
  --len;

  if ((op != static_cast<std::underlying_type_t<PresetCtpOpcode>>(
                 PresetCtpOpcode::READ_PRESET_RESPONSE)) &&
      (op != static_cast<std::underlying_type_t<PresetCtpOpcode>>(
                 PresetCtpOpcode::PRESET_CHANGED))) {
    log::error("Received invalid opcode in control point notification: {}", op);
    return std::nullopt;
  }

  HasCtpNtf ntf;
  ntf.opcode = PresetCtpOpcode(op);
  if (ntf.opcode == le_audio::has::PresetCtpOpcode::PRESET_CHANGED) {
    if (!ParsePresetChanged(len, value, ntf)) return std::nullopt;

  } else if (ntf.opcode ==
             le_audio::has::PresetCtpOpcode::READ_PRESET_RESPONSE) {
    if (!ParsePresetReadResponse(len, value, ntf)) return std::nullopt;
  }

  return ntf;
}

uint16_t HasCtpOp::last_op_id_ = 0;

std::vector<uint8_t> HasCtpOp::ToCharacteristicValue() const {
  std::vector<uint8_t> value;
  auto* pp = value.data();

  switch (opcode) {
    case PresetCtpOpcode::READ_PRESETS:
      value.resize(3);
      pp = value.data();
      UINT8_TO_STREAM(
          pp, static_cast<std::underlying_type_t<PresetCtpOpcode>>(opcode));
      UINT8_TO_STREAM(pp, index);
      UINT8_TO_STREAM(pp, num_of_indices);
      break;
    case PresetCtpOpcode::SET_ACTIVE_PRESET:
    case PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC:
      value.resize(2);
      pp = value.data();
      UINT8_TO_STREAM(
          pp, static_cast<std::underlying_type_t<PresetCtpOpcode>>(opcode));
      UINT8_TO_STREAM(pp, index);
      break;

    case PresetCtpOpcode::SET_NEXT_PRESET:
    case PresetCtpOpcode::SET_NEXT_PRESET_SYNC:
    case PresetCtpOpcode::SET_PREV_PRESET:
    case PresetCtpOpcode::SET_PREV_PRESET_SYNC:
      value.resize(1);
      pp = value.data();
      UINT8_TO_STREAM(
          pp, static_cast<std::underlying_type_t<PresetCtpOpcode>>(opcode));
      break;

    case PresetCtpOpcode::WRITE_PRESET_NAME: {
      auto name_str = name.value_or("");
      value.resize(2 + name_str.length());
      pp = value.data();

      UINT8_TO_STREAM(
          pp, static_cast<std::underlying_type_t<PresetCtpOpcode>>(opcode));
      UINT8_TO_STREAM(pp, index);
      memcpy(pp, name_str.c_str(), name_str.length());
    } break;

    default:
      LOG_ASSERT(false) << __func__ << "Bad control point operation!";
      break;
  }

  return value;
}

#define CASE_SET_PTR_TO_TOKEN_STR(en) \
  case (en):                          \
    ch = #en;                         \
    break;

std::ostream& operator<<(std::ostream& out, const PresetCtpChangeId value) {
  const char* ch = 0;
  switch (value) {
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpChangeId::PRESET_GENERIC_UPDATE);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpChangeId::PRESET_DELETED);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpChangeId::PRESET_AVAILABLE);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpChangeId::PRESET_UNAVAILABLE);
    default:
      ch = "INVALID_CHANGE_ID";
      break;
  }
  return out << ch;
}

std::ostream& operator<<(std::ostream& out, const PresetCtpOpcode value) {
  const char* ch = 0;
  switch (value) {
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::READ_PRESETS);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::READ_PRESET_RESPONSE);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::PRESET_CHANGED);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::WRITE_PRESET_NAME);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_ACTIVE_PRESET);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_NEXT_PRESET);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_PREV_PRESET);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_NEXT_PRESET_SYNC);
    CASE_SET_PTR_TO_TOKEN_STR(PresetCtpOpcode::SET_PREV_PRESET_SYNC);
    default:
      ch = "NOT_A_VALID_OPCODE";
      break;
  }
  return out << ch;
}
#undef SET_CH_TO_TOKENIZED

std::ostream& operator<<(std::ostream& out, const HasCtpOp& op) {
  out << "\"HasCtpOp\": {";
  if (std::holds_alternative<int>(op.addr_or_group)) {
    out << "\"group_id\": " << std::get<int>(op.addr_or_group);
  } else if (std::holds_alternative<RawAddress>(op.addr_or_group)) {
    out << "\"address\": \""
    << ADDRESS_TO_LOGGABLE_STR(std::get<RawAddress>(op.addr_or_group)) << "\"";
  } else {
    out << "\"bad value\"";
  }
  out << ", \"id\": " << op.op_id << ", \"opcode\": \"" << op.opcode << "\""
      << ", \"index\": " << +op.index << ", \"name\": \""
      << op.name.value_or("<none>") << "\""
      << "}";
  return out;
}

std::ostream& operator<<(std::ostream& out, const HasCtpNtf& ntf) {
  out << "\"HasCtpNtf\": {";
  out << "\"opcode\": \"" << ntf.opcode << "\"";

  if (ntf.opcode == PresetCtpOpcode::READ_PRESET_RESPONSE) {
    out << ", \"is_last\": " << (ntf.is_last ? "\"True\"" : "\"False\"");
    if (ntf.preset.has_value()) {
      out << ", \"preset\": " << ntf.preset.value();
    } else {
      out << ", \"preset\": \"None\"";
    }

  } else if (ntf.opcode == PresetCtpOpcode::PRESET_CHANGED) {
    out << ", \"change_id\": " << ntf.change_id;
    out << ", \"is_last\": " << (ntf.is_last ? "\"True\"" : "\"False\"");
    switch (ntf.change_id) {
      case PresetCtpChangeId::PRESET_GENERIC_UPDATE:
        out << ", \"prev_index\": " << +ntf.prev_index;
        if (ntf.preset.has_value()) {
          out << ", \"preset\": {" << ntf.preset.value() << "}";
        } else {
          out << ", \"preset\": \"None\"";
        }
        break;
      case PresetCtpChangeId::PRESET_DELETED:
      case PresetCtpChangeId::PRESET_AVAILABLE:
      case PresetCtpChangeId::PRESET_UNAVAILABLE:
        out << ", \"index\": " << +ntf.index;
        break;
      default:
        break;
    }
  }
  out << "}";

  return out;
}

}  // namespace has
}  // namespace le_audio

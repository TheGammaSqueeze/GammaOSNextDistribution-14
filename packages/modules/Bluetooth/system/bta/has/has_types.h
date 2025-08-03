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

#include <bluetooth/log.h>

#include <numeric>
#include <optional>
#include <set>
#include <vector>

#include "gap_api.h"
#include "hardware/bt_has.h"
#include "has_ctp.h"
#include "has_journal.h"
#include "has_preset.h"
#include "internal_include/bt_trace.h"
#include "stack/include/bt_types.h"
#include "stack/include/gatt_api.h"

namespace le_audio {
namespace has {

/* Helper class to pass some minimal context through the GATT operation API. */
union HasGattOpContext {
 public:
  void* ptr = nullptr;
  struct {
    /* Ctp. Operation ID or 0 if not a control point operation context */
    uint16_t ctp_op_id;

    /* Additional user flags */
    uint8_t context_flags;
  };

  /* Flags describing operation context */
  static constexpr uint8_t kContextFlagsEnableNotification = 0x01;
  static constexpr uint8_t kIsNotNull = 0x02;

  static constexpr uint8_t kStatusCodeNotSet = 0xF0;

  HasGattOpContext(const HasCtpOp& ctp_op, uint8_t flags = 0) {
    ctp_op_id = ctp_op.op_id;
    /* Differ from nullptr in at least 1 bit when everything else is 0 */
    context_flags = flags | kIsNotNull;
  }
  HasGattOpContext(uint8_t flags) : ctp_op_id(0) {
    context_flags = flags | kIsNotNull;
  }
  HasGattOpContext(void* pp) {
    ptr = pp;
    /* Differ from nullptr in at least 1 bit when everything else is 0 */
    context_flags |= kIsNotNull;
  }
  operator void*() { return ptr; }
};

/* Context must be constrained to void* size to pass through the GATT API */
static_assert(sizeof(HasGattOpContext) <= sizeof(void*));

/* Service UUIDs */
static const bluetooth::Uuid kUuidHearingAccessService =
    bluetooth::Uuid::From16Bit(0x1854);
static const bluetooth::Uuid kUuidHearingAidFeatures =
    bluetooth::Uuid::From16Bit(0x2BDA);
static const bluetooth::Uuid kUuidHearingAidPresetControlPoint =
    bluetooth::Uuid::From16Bit(0x2BDB);
static const bluetooth::Uuid kUuidActivePresetIndex =
    bluetooth::Uuid::From16Bit(0x2BDC);

static const uint8_t kStartPresetIndex = 1;
static const uint8_t kMaxNumOfPresets = 255;

/* Base device class for the GATT-based service clients */
class GattServiceDevice {
 public:
  RawAddress addr;
  uint16_t conn_id = GATT_INVALID_CONN_ID;
  uint16_t service_handle = GAP_INVALID_HANDLE;
  bool is_connecting_actively = false;

  uint8_t gatt_svc_validation_steps = 0xFE;
  bool isGattServiceValid() { return gatt_svc_validation_steps == 0; }

  GattServiceDevice(const RawAddress& addr, bool connecting_actively = false)
      : addr(addr), is_connecting_actively(connecting_actively) {}

  GattServiceDevice() : GattServiceDevice(RawAddress::kEmpty) {}

  bool IsConnected() const { return conn_id != GATT_INVALID_CONN_ID; }

  class MatchAddress {
   private:
    RawAddress addr;

   public:
    MatchAddress(RawAddress addr) : addr(addr) {}
    bool operator()(const GattServiceDevice& other) const {
      return (addr == other.addr);
    }
  };

  class MatchConnId {
   private:
    uint16_t conn_id;

   public:
    MatchConnId(uint16_t conn_id) : conn_id(conn_id) {}
    bool operator()(const GattServiceDevice& other) const {
      return (conn_id == other.conn_id);
    }
  };

  void Dump(std::ostream& os) const {
    os << "\"addr\": \"" << addr << "\"";
    os << ", \"conn_id\": " << conn_id;
    os << ", \"is_gatt_service_valid\": "
       << (gatt_svc_validation_steps == 0 ? "\"True\"" : "\"False\"") << "("
       << +gatt_svc_validation_steps << ")";
    os << ", \"is_connecting_actively\": "
       << (is_connecting_actively ? "\"True\"" : "\"False\"");
  }
};

/* Build on top of the base GattServiceDevice extends the base device context
 * with service specific informations such as the currently active preset,
 * all available presets, and supported optional operations. It also stores
 * HAS service specific GATT informations such as characteristic handles.
 */
class HasDevice : public GattServiceDevice {
  uint8_t features = 0x00;
  uint16_t supported_opcodes_bitmask = 0x0000;

  void RefreshSupportedOpcodesBitmask(void) {
    supported_opcodes_bitmask = 0;

    /* Some opcodes are mandatory but the characteristics aren't - these are
     * conditional then.
     */
    if ((cp_handle != GAP_INVALID_HANDLE) &&
        (active_preset_handle != GAP_INVALID_HANDLE)) {
      supported_opcodes_bitmask |= kControlPointMandatoryOpcodesBitmask;
    }

    if (features & bluetooth::has::kFeatureBitPresetSynchronizationSupported) {
      supported_opcodes_bitmask |= kControlPointMandatoryOpcodesBitmask;
      supported_opcodes_bitmask |= kControlPointSynchronizedOpcodesBitmask;
    }

    if (features & bluetooth::has::kFeatureBitWritablePresets) {
      supported_opcodes_bitmask |=
          PresetCtpOpcode2Bitmask(PresetCtpOpcode::WRITE_PRESET_NAME);
    }
  }

 public:
  /* Char handle and current ccc value */
  uint16_t active_preset_handle = GAP_INVALID_HANDLE;
  uint16_t active_preset_ccc_handle = GAP_INVALID_HANDLE;
  uint16_t cp_handle = GAP_INVALID_HANDLE;
  uint16_t cp_ccc_handle = GAP_INVALID_HANDLE;
  uint8_t cp_ccc_val = 0;
  uint16_t features_handle = GAP_INVALID_HANDLE;
  uint16_t features_ccc_handle = GAP_INVALID_HANDLE;

  bool features_notifications_enabled = false;

  /* Presets in the ascending order of their indices */
  std::set<HasPreset, HasPreset::ComparatorDesc> has_presets;
  uint8_t currently_active_preset = bluetooth::has::kHasPresetIndexInvalid;

  std::list<HasCtpNtf> ctp_notifications_;
  HasJournal has_journal_;

  HasDevice(const RawAddress& addr, uint8_t features)
      : GattServiceDevice(addr) {
    UpdateFeatures(features);
  }

  void ConnectionCleanUp() {
    conn_id = GATT_INVALID_CONN_ID;
    is_connecting_actively = false;
    ctp_notifications_.clear();
  }

  using GattServiceDevice::GattServiceDevice;

  uint8_t GetFeatures() const { return features; }

  void UpdateFeatures(uint8_t new_features) {
    features = new_features;
    /* Update the dependent supported feature set */
    RefreshSupportedOpcodesBitmask();
  }

  void ClearSvcData() {
    GattServiceDevice::service_handle = GAP_INVALID_HANDLE;
    GattServiceDevice::gatt_svc_validation_steps = 0xFE;

    active_preset_handle = GAP_INVALID_HANDLE;
    active_preset_ccc_handle = GAP_INVALID_HANDLE;
    cp_handle = GAP_INVALID_HANDLE;
    cp_ccc_handle = GAP_INVALID_HANDLE;
    features_handle = GAP_INVALID_HANDLE;
    features_ccc_handle = GAP_INVALID_HANDLE;

    features = 0;
    features_notifications_enabled = false;

    supported_opcodes_bitmask = 0x00;
    currently_active_preset = bluetooth::has::kHasPresetIndexInvalid;

    has_presets.clear();
  }

  inline bool SupportsPresets() const {
    return (active_preset_handle != GAP_INVALID_HANDLE) &&
           (cp_handle != GAP_INVALID_HANDLE);
  }

  inline bool SupportsActivePresetNotification() const {
    return active_preset_ccc_handle != GAP_INVALID_HANDLE;
  }

  inline bool SupportsFeaturesNotification() const {
    return features_ccc_handle != GAP_INVALID_HANDLE;
  }

  inline bool HasFeaturesNotificationEnabled() const {
    return features_notifications_enabled;
  }

  inline bool SupportsOperation(PresetCtpOpcode op) {
    auto mask = PresetCtpOpcode2Bitmask(op);
    return (supported_opcodes_bitmask & mask) == mask;
  }

  bool IsValidPreset(uint8_t preset_index, bool writable_only = false) const {
    if (has_presets.count(preset_index)) {
      return writable_only ? has_presets.find(preset_index)->IsWritable()
                           : true;
    }
    return false;
  }

  const HasPreset* GetPreset(uint8_t preset_index,
                             bool writable_only = false) const {
    if (has_presets.count(preset_index)) {
      decltype(has_presets)::iterator preset = has_presets.find(preset_index);
      if (writable_only) return preset->IsWritable() ? &*preset : nullptr;
      return &*preset;
    }
    return nullptr;
  }

  std::optional<bluetooth::has::PresetInfo> GetPresetInfo(uint8_t index) const {
    if (has_presets.count(index)) {
      auto preset = *has_presets.find(index);
      return bluetooth::has::PresetInfo({.preset_index = preset.GetIndex(),
                                         .writable = preset.IsWritable(),
                                         .available = preset.IsAvailable(),
                                         .preset_name = preset.GetName()});
    }
    return std::nullopt;
  }

  std::vector<bluetooth::has::PresetInfo> GetAllPresetInfo() const {
    std::vector<bluetooth::has::PresetInfo> all_info;
    all_info.reserve(has_presets.size());

    for (auto const& preset : has_presets) {
      DLOG(INFO) << __func__ << " preset: " << preset;
      all_info.push_back({.preset_index = preset.GetIndex(),
                          .writable = preset.IsWritable(),
                          .available = preset.IsAvailable(),
                          .preset_name = preset.GetName()});
    }
    return all_info;
  }

  /* Calculates the buffer space that all the preset will use when serialized */
  uint8_t SerializedPresetsSize() const {
    /* Two additional bytes are for the header and the number of presets */
    return std::accumulate(has_presets.begin(), has_presets.end(), 0,
                           [](uint8_t current, auto const& preset) {
                             return current + preset.SerializedSize();
                           }) +
           2;
  }

  /* Serializes all the presets into a binary blob for persistent storage */
  bool SerializePresets(std::vector<uint8_t>& out) const {
    auto buffer_size = SerializedPresetsSize();
    auto buffer_offset = out.size();

    out.resize(out.size() + buffer_size);
    auto p_out = out.data() + buffer_offset;

    UINT8_TO_STREAM(p_out, kHasDeviceBinaryBlobHdr);
    UINT8_TO_STREAM(p_out, has_presets.size());

    auto* const p_end = p_out + buffer_size;
    for (auto& preset : has_presets) {
      if (p_out + preset.SerializedSize() >= p_end) {
        bluetooth::log::error("Serialization error.");
        return false;
      }
      p_out = preset.Serialize(p_out, p_end - p_out);
    }

    return true;
  }

  /* Deserializes all the presets from a binary blob read from the persistent
   * storage.
   */
  static bool DeserializePresets(const uint8_t* p_in, size_t len,
                                 HasDevice& device) {
    HasPreset preset;
    if (len < 2 + preset.SerializedSize()) {
      bluetooth::log::error(
          "Deserialization error. Invalid input buffer size length.");
      return false;
    }
    auto* p_end = p_in + len;

    uint8_t hdr;
    STREAM_TO_UINT8(hdr, p_in);
    if (hdr != kHasDeviceBinaryBlobHdr) {
      bluetooth::log::error("Deserialization error. Bad header.");
      return false;
    }

    uint8_t num_presets;
    STREAM_TO_UINT8(num_presets, p_in);

    device.has_presets.clear();
    while (p_in < p_end) {
      auto* p_new = HasPreset::Deserialize(p_in, p_end - p_in, preset);
      if (p_new <= p_in) {
        bluetooth::log::error("Deserialization error. Invalid preset found.");
        device.has_presets.clear();
        return false;
      }

      device.has_presets.insert(preset);
      p_in = p_new;
    }

    return device.has_presets.size() == num_presets;
  }

  friend std::ostream& operator<<(std::ostream& os, const HasDevice& b);

  void Dump(std::ostream& os) const {
    GattServiceDevice::Dump(os);
    os << ", \"features\": \"" << loghex(features) << "\"";
    os << ", \"features_notifications_enabled\": "
       << (features_notifications_enabled ? "\"Enabled\"" : "\"Disabled\"");
    os << ", \"ctp_notifications size\": " << ctp_notifications_.size();
    os << ",\n";

    os << "    "
       << "\"presets\": [";
    for (auto const& preset : has_presets) {
      os << "\n      " << preset << ",";
    }
    os << "\n    ],\n";

    os << "    "
       << "\"Ctp. notifications process queue\": {";
    if (ctp_notifications_.size() != 0) {
      size_t ntf_pos = 0;
      for (auto const& ntf : ctp_notifications_) {
        os << "\n      ";
        if (ntf_pos == 0) {
          os << "\"latest\": ";
        } else {
          os << "\"-" << ntf_pos << "\": ";
        }

        os << ntf << ",";
        ++ntf_pos;
      }
    }
    os << "\n    },\n";

    os << "    "
       << "\"event history\": {";
    size_t pos = 0;
    for (auto const& record : has_journal_) {
      os << "\n      ";
      if (pos == 0) {
        os << "\"latest\": ";
      } else {
        os << "\"-" << pos << "\": ";
      }

      os << record << ",";
      ++pos;
    }
    os << "\n    }";
  }

 private:
  static constexpr int kHasDeviceBinaryBlobHdr = 0x55;
};

}  // namespace has
}  // namespace le_audio

namespace fmt {
template <>
struct formatter<le_audio::has::HasDevice> : ostream_formatter {};
}  // namespace fmt

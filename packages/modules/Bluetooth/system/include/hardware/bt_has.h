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

#include <hardware/bluetooth.h>

#include <variant>
#include <vector>

namespace bluetooth {
namespace has {

/** Connection State */
enum class ConnectionState : uint8_t {
  DISCONNECTED = 0,
  CONNECTING,
  CONNECTED,
  DISCONNECTING,
};

/** Results codes for the failed preset operations */
enum class ErrorCode : uint8_t {
  NO_ERROR = 0,
  SET_NAME_NOT_ALLOWED,     // Preset cannot be written (read only preset)
  OPERATION_NOT_SUPPORTED,  // If theres no optional characteristic,
                            // or request opcode is invalid or not supported
  OPERATION_NOT_POSSIBLE,   // Operation cannot be performed at this time
  INVALID_PRESET_NAME_LENGTH,
  INVALID_PRESET_INDEX,
  GROUP_OPERATION_NOT_SUPPORTED,
  PROCEDURE_ALREADY_IN_PROGRESS,
};

enum class PresetInfoReason : uint8_t {
  ALL_PRESET_INFO = 0,
  PRESET_INFO_UPDATE,
  PRESET_DELETED,
  PRESET_AVAILABILITY_CHANGED,
  PRESET_INFO_REQUEST_RESPONSE,
};

struct PresetInfo {
  uint8_t preset_index;

  bool writable;
  bool available;
  std::string preset_name;
};

/** Service supported feature bits */
static constexpr uint8_t kFeatureBitHearingAidTypeBinaural = 0x00;
static constexpr uint8_t kFeatureBitHearingAidTypeMonaural = 0x01;
static constexpr uint8_t kFeatureBitHearingAidTypeBanded = 0x02;
static constexpr uint8_t kFeatureBitPresetSynchronizationSupported = 0x04;
static constexpr uint8_t kFeatureBitIndependentPresets = 0x08;
static constexpr uint8_t kFeatureBitDynamicPresets = 0x10;
static constexpr uint8_t kFeatureBitWritablePresets = 0x20;

/** Invalid values for the group and preset identifiers */
static constexpr uint8_t kHasPresetIndexInvalid = 0x00;
static constexpr int kHasGroupIdInvalid = -1;

class HasClientCallbacks {
 public:
  virtual ~HasClientCallbacks() = default;

  /** Callback for profile connection state change */
  virtual void OnConnectionState(ConnectionState state,
                                 const RawAddress& addr) = 0;

  /** Callback for the new available device */
  virtual void OnDeviceAvailable(const RawAddress& addr, uint8_t features) = 0;

  /** Callback for getting device HAS flags */
  virtual void OnFeaturesUpdate(const RawAddress& addr, uint8_t features) = 0;

  /** Callback for the currently active preset */
  virtual void OnActivePresetSelected(
      std::variant<RawAddress, int> addr_or_group_id, uint8_t preset_index) = 0;

  /** Callbacks for the active preset selection error */
  virtual void OnActivePresetSelectError(
      std::variant<RawAddress, int> addr_or_group_id, ErrorCode error_code) = 0;

  /** Callbacks for the preset details event */
  virtual void OnPresetInfo(std::variant<RawAddress, int> addr_or_group_id,
                            PresetInfoReason change_id,
                            std::vector<PresetInfo> info_records) = 0;

  /** Callback for the preset details get error */
  virtual void OnPresetInfoError(std::variant<RawAddress, int> addr_or_group_id,
                                 uint8_t preset_index,
                                 ErrorCode error_code) = 0;

  /** Callback for the preset name set error */
  virtual void OnSetPresetNameError(
      std::variant<RawAddress, int> addr_or_group_id, uint8_t preset_index,
      ErrorCode error_code) = 0;
};

class HasClientInterface {
 public:
  virtual ~HasClientInterface() = default;

  /** Register the Hearing Aid Service Client profile callbacks */
  virtual void Init(HasClientCallbacks* callbacks) = 0;

  /** Connect to HAS service */
  virtual void Connect(const RawAddress& addr) = 0;

  /** Disconnect from HAS service */
  virtual void Disconnect(const RawAddress& addr) = 0;

  /** Select preset by the index as currently active */
  virtual void SelectActivePreset(
      std::variant<RawAddress, int> addr_or_group_id, uint8_t preset_index) = 0;

  /** Select next preset as currently active */
  virtual void NextActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) = 0;

  /** Select previous preset as currently active */
  virtual void PreviousActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) = 0;

  /** Get preset name by the index */
  virtual void GetPresetInfo(const RawAddress& addr, uint8_t preset_index) = 0;

  /** Set preset name by the index */
  virtual void SetPresetName(std::variant<RawAddress, int> addr_or_group_id,
                             uint8_t preset_index, std::string name) = 0;

  /** Called when HAS capable device is unbonded */
  virtual void RemoveDevice(const RawAddress& addr) = 0;

  /** Closes the interface */
  virtual void Cleanup(void) = 0;
};

}  // namespace has
}  // namespace bluetooth

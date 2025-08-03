/*
 * Copyright 2022 The Android Open Source Project
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
#include "metrics/chromeos/metrics_event.h"

#include <base/files/file_path.h>
#include <base/files/file_util.h>
#include <base/strings/pattern.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <base/strings/stringprintf.h>

#include <map>
#include <utility>

#include "common/init_flags.h"
#include "hci/hci_packets.h"
#include "include/hardware/bluetooth.h"
#include "include/hardware/bt_av.h"
#include "include/hardware/bt_hf.h"
#include "include/hardware/bt_hh.h"
#include "stack/include/hci_error_code.h"

namespace bluetooth {
namespace metrics {

namespace {
// these consts path below are for getting the chipset info
constexpr char kChipsetInfoWlanDirPath[] = "/sys/class/net/wlan0/device";
constexpr char kChipsetInfoMlanDirPath[] = "/sys/class/net/mlan0/device";
constexpr char kChipsetInfoModaliasPath[] = "/sys/class/bluetooth/hci%d/device/modalias";
constexpr char kChipInfoModuleDirPath[] = "/sys/class/bluetooth/hci%d/device/driver/module";
}  // namespace

// topshim::btif::BtBondState is a copy of hardware/bluetooth.h:bt_bond_state_t
typedef bt_bond_state_t BtBondState;
// topshim::btif::BtAclState is a copy of hardware/bluetooth.h:bt_acl_state_t
typedef bt_acl_state_t BtAclState;
// topshim::btif::BtConnectionDirection is a copy of hardware/bluetooth.h:bt_conn_direction_t
typedef bt_conn_direction_t BtConnectionDirection;
// topshim::btif::BtStatus is a copy of hardware/bluetooth.h:bt_status_t
typedef bt_status_t BtStatus;
// topshim::profile::a2dp::BtavConnectionState is a copy of hardware/bt_av.h:btav_connection_state_t
typedef btav_connection_state_t BtavConnectionState;
// topshim::profile::hid_host::BthhConnectionState is a copy of hardware/bt_hh.h:bthh_connection_state_t
typedef bthh_connection_state_t BthhConnectionState;
// topshim::profile::hid_host::BthfConnectionState is a copy of hardware/bt_hh.h:bthf_connection_state_t
typedef headset::bthf_connection_state_t BthfConnectionState;

// A copy of topshim::btif::BtDeviceType
enum class BtDeviceType {
  Unknown = 0,
  Bredr,
  Ble,
  Dual,
};

// A normalized connection state ENUM definition all profiles
enum class ProfilesConnectionState {
  DISCONNECTED = 0,
  CONNECTING,
  CONNECTED,
  DISCONNECTING,
  UNKNOWN,
};

// ENUM definition for Bluetooth profiles in sync with ::uuid::Profiles
enum class ProfilesFloss {
  A2dpSink = 0,
  A2dpSource,
  AdvAudioDist,
  Hsp,
  HspAg,
  Hfp,
  HfpAg,
  AvrcpController,
  AvrcpTarget,
  ObexObjectPush,
  Hid,
  Hogp,
  Panu,
  Nap,
  Bnep,
  PbapPce,
  PbapPse,
  Map,
  Mns,
  Mas,
  Sap,
  HearingAid,
  LeAudio,
  Dip,
  VolumeControl,
  GenericMediaControl,
  MediaControl,
  CoordinatedSet,
};

static PairingState StatusToPairingState(uint32_t status) {
  switch ((BtStatus)status) {
    case BtStatus::BT_STATUS_SUCCESS:
      return PairingState::PAIR_SUCCEED;
    case BtStatus::BT_STATUS_FAIL:
      return PairingState::PAIR_FAIL_FAILED;
    case BtStatus::BT_STATUS_NOMEM:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case BtStatus::BT_STATUS_BUSY:
      return PairingState::PAIR_FAIL_BUSY;
    case BtStatus::BT_STATUS_UNSUPPORTED:
      return PairingState::PAIR_FAIL_NOT_SUPPORTED;
    case BtStatus::BT_STATUS_PARM_INVALID:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case BtStatus::BT_STATUS_AUTH_FAILURE:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case BtStatus::BT_STATUS_RMT_DEV_DOWN:
      return PairingState::PAIR_FAIL_ESTABLISH_CONN;
    case BtStatus::BT_STATUS_AUTH_REJECTED:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case BtStatus::BT_STATUS_NOT_READY:
    case BtStatus::BT_STATUS_DONE:
    case BtStatus::BT_STATUS_UNHANDLED:
    default:
      return PairingState::PAIR_FAIL_UNKNOWN;
  }
}

static PairingState FailReasonToPairingState(int32_t fail_reason) {
  switch ((hci::ErrorCode)fail_reason) {
    case hci::ErrorCode::SUCCESS:
      return PairingState::PAIR_SUCCEED;
    case hci::ErrorCode::UNKNOWN_HCI_COMMAND:
      return PairingState::PAIR_FAIL_UNKNOWN_COMMAND;
    case hci::ErrorCode::UNKNOWN_CONNECTION:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case hci::ErrorCode::HARDWARE_FAILURE:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::PAGE_TIMEOUT:
      return PairingState::PAIR_FAIL_ESTABLISH_CONN;
    case hci::ErrorCode::AUTHENTICATION_FAILURE:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case hci::ErrorCode::PIN_OR_KEY_MISSING:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case hci::ErrorCode::MEMORY_CAPACITY_EXCEEDED:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case hci::ErrorCode::CONNECTION_TIMEOUT:
      return PairingState::PAIR_FAIL_ESTABLISH_CONN;
    case hci::ErrorCode::CONNECTION_LIMIT_EXCEEDED:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case hci::ErrorCode::SYNCHRONOUS_CONNECTION_LIMIT_EXCEEDED:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case hci::ErrorCode::CONNECTION_ALREADY_EXISTS:
      return PairingState::PAIR_FAIL_ALREADY_PAIRED;
    case hci::ErrorCode::COMMAND_DISALLOWED:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::CONNECTION_REJECTED_LIMITED_RESOURCES:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case hci::ErrorCode::CONNECTION_REJECTED_SECURITY_REASONS:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case hci::ErrorCode::CONNECTION_REJECTED_UNACCEPTABLE_BD_ADDR:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case hci::ErrorCode::CONNECTION_ACCEPT_TIMEOUT:
      return PairingState::PAIR_FAIL_ESTABLISH_CONN;
    case hci::ErrorCode::UNSUPPORTED_FEATURE_OR_PARAMETER_VALUE:
      return PairingState::PAIR_FAIL_NOT_SUPPORTED;
    case hci::ErrorCode::INVALID_HCI_COMMAND_PARAMETERS:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case hci::ErrorCode::REMOTE_USER_TERMINATED_CONNECTION:
      return PairingState::PAIR_FAIL_DISCONNECTED;
    case hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_LOW_RESOURCES:
      return PairingState::PAIR_FAIL_DISCONNECTED;
    case hci::ErrorCode::REMOTE_DEVICE_TERMINATED_CONNECTION_POWER_OFF:
      return PairingState::PAIR_FAIL_DISCONNECTED;
    case hci::ErrorCode::CONNECTION_TERMINATED_BY_LOCAL_HOST:
      return PairingState::PAIR_FAIL_DISCONNECTED;
    case hci::ErrorCode::REPEATED_ATTEMPTS:
      return PairingState::PAIR_FAIL_BUSY;
    case hci::ErrorCode::PAIRING_NOT_ALLOWED:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::UNKNOWN_LMP_PDU:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::UNSUPPORTED_REMOTE_OR_LMP_FEATURE:
      return PairingState::PAIR_FAIL_NOT_SUPPORTED;
    case hci::ErrorCode::INVALID_LMP_OR_LL_PARAMETERS:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case hci::ErrorCode::UNSPECIFIED_ERROR:
      return PairingState::PAIR_FAIL_UNKNOWN;
    case hci::ErrorCode::UNSUPPORTED_LMP_OR_LL_PARAMETER:
      return PairingState::PAIR_FAIL_NOT_SUPPORTED;
    case hci::ErrorCode::ROLE_CHANGE_NOT_ALLOWED:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::TRANSACTION_RESPONSE_TIMEOUT:
      return PairingState::PAIR_FAIL_TIMEOUT;
    case hci::ErrorCode::LINK_LAYER_COLLISION:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::ENCRYPTION_MODE_NOT_ACCEPTABLE:
      return PairingState::PAIR_FAIL_AUTH_FAILED;
    case hci::ErrorCode::ROLE_SWITCH_FAILED:
      return PairingState::PAIR_FAIL_FAILED;
    case hci::ErrorCode::HOST_BUSY_PAIRING:
      return PairingState::PAIR_FAIL_BUSY;
    case hci::ErrorCode::CONTROLLER_BUSY:
      return PairingState::PAIR_FAIL_BUSY;
    case hci::ErrorCode::CONNECTION_FAILED_ESTABLISHMENT:
      return PairingState::PAIR_FAIL_ESTABLISH_CONN;
    case hci::ErrorCode::LIMIT_REACHED:
      return PairingState::PAIR_FAIL_NO_RESOURCES;
    case hci::ErrorCode::PACKET_TOO_LONG:
      return PairingState::PAIR_FAIL_INVALID_PARAMS;
    case hci::ErrorCode::SCO_OFFSET_REJECTED:
    case hci::ErrorCode::SCO_INTERVAL_REJECTED:
    case hci::ErrorCode::SCO_AIR_MODE_REJECTED:
    case hci::ErrorCode::ADVERTISING_TIMEOUT:
    case hci::ErrorCode::UNKNOWN_ADVERTISING_IDENTIFIER:
    case hci::ErrorCode::STATUS_UNKNOWN:
      return PairingState::PAIR_FAIL_UNKNOWN;
    default:
      return PairingState::PAIR_FAIL_UNKNOWN;
  }
}

AdapterState ToAdapterState(uint32_t state) {
  return state == 1 ? AdapterState::ON : AdapterState::OFF;
}

SuspendIdState ToSuspendIdState(uint32_t state) {
  return state == 1 ? SuspendIdState::Recorded : SuspendIdState::NoRecord;
}

ConnectionType ToPairingDeviceType(std::string addr, uint32_t device_type) {
  // A map stores the pending ConnectionType used to match a pairing event with unknown type.
  // map<address, type>
  static std::map<std::string, ConnectionType> pending_type;

  switch ((BtDeviceType)device_type) {
    case BtDeviceType::Ble:
      pending_type[addr] = ConnectionType::CONN_TYPE_LE;
      return ConnectionType::CONN_TYPE_LE;
    case BtDeviceType::Bredr:
      pending_type[addr] = ConnectionType::CONN_TYPE_BREDR;
      return ConnectionType::CONN_TYPE_BREDR;
    case BtDeviceType::Dual:
    case BtDeviceType::Unknown:
      if (pending_type.find(addr) != pending_type.end()) {
        return pending_type[addr];
      } else {
        return ConnectionType::CONN_TYPE_UNKNOWN;
      }
  }
}

PairingState ToPairingState(uint32_t status, uint32_t bond_state, int32_t fail_reason) {
  PairingState pairing_state = PairingState::PAIR_FAIL_UNKNOWN;

  // The Bonding is a transitional state during the pairing process. Ignore it by returning the starting again.
  if ((BtBondState)bond_state == BtBondState::BT_BOND_STATE_BONDING) return PairingState::PAIR_STARTING;

  if ((BtStatus)status == BtStatus::BT_STATUS_SUCCESS && (hci::ErrorCode)fail_reason == hci::ErrorCode::SUCCESS) {
    if ((BtBondState)bond_state == BtBondState::BT_BOND_STATE_BONDED) {
      return PairingState::PAIR_SUCCEED;
    } else {  // must be BtBondState::BT_BOND_STATE_NONE as BT_BOND_STATE_BONDING case has been
              // checked early
      // This implies the event is from forgetting a device. Return an absurd value to let caller
      // know.
      return PairingState::PAIR_FAIL_END;
    }
  }

  // TODO(b/287392029): Translate cases of bond cancelled into PairingState:PAIR_FAIL_CANCELLED

  // When both status and fail reason are provided and disagree with each other, overwrite status with the fail reason
  // as fail reason is generated closer to the HCI and provides a more accurate description.
  if (status) pairing_state = StatusToPairingState(status);
  if (fail_reason) pairing_state = FailReasonToPairingState(fail_reason);

  return pairing_state;
}

int64_t StatusToProfileConnectionState(uint32_t status, StateChangeType type) {
  int64_t state;
  if (StateChangeType::STATE_CHANGE_TYPE_CONNECT == type) {
    switch ((BtStatus)status) {
      case BtStatus::BT_STATUS_SUCCESS:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_SUCCEED;
        break;
      case BtStatus::BT_STATUS_BUSY:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_BUSY_CONNECTING;
        break;
      case BtStatus::BT_STATUS_DONE:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_ALREADY_CONNECTED;
        break;
      case BtStatus::BT_STATUS_UNSUPPORTED:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_PROFILE_NOT_SUPPORTED;
        break;
      case BtStatus::BT_STATUS_PARM_INVALID:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_UNKNOWN_ERROR;
        break;
      case BtStatus::BT_STATUS_AUTH_FAILURE:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_CONNECTION_REFUSED;
        break;
      case BtStatus::BT_STATUS_RMT_DEV_DOWN:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_REMOTE_UNAVAILABLE;
        break;
      case BtStatus::BT_STATUS_AUTH_REJECTED:
      case BtStatus::BT_STATUS_FAIL:
      case BtStatus::BT_STATUS_NOT_READY:
      case BtStatus::BT_STATUS_NOMEM:
      case BtStatus::BT_STATUS_UNHANDLED:
      default:
        state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_UNKNOWN_ERROR;
        break;
    }
  } else {
    switch ((BtStatus)status) {
      case BtStatus::BT_STATUS_SUCCESS:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_SUCCEED;
        break;
      case BtStatus::BT_STATUS_BUSY:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_BUSY_DISCONNECTING;
        break;
      case BtStatus::BT_STATUS_DONE:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_ALREADY_DISCONNECTED;
        break;
      case BtStatus::BT_STATUS_UNSUPPORTED:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_UNKNOWN_ERROR;
        break;
      case BtStatus::BT_STATUS_PARM_INVALID:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_INVALID_PARAMS;
        break;
      case BtStatus::BT_STATUS_AUTH_FAILURE:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_DISCONNECTION_REFUSED;
        break;
      case BtStatus::BT_STATUS_RMT_DEV_DOWN:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_UNKNOWN_ERROR;
        break;
      case BtStatus::BT_STATUS_AUTH_REJECTED:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_DISCONNECTION_REFUSED;
        break;
      case BtStatus::BT_STATUS_FAIL:
      case BtStatus::BT_STATUS_NOT_READY:
      case BtStatus::BT_STATUS_NOMEM:
      case BtStatus::BT_STATUS_UNHANDLED:
      default:
        state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_UNKNOWN_ERROR;
        break;
    }
  }

  return state;
}

static std::pair<uint32_t, uint32_t> ToProfileConnectionState(uint32_t profile, uint32_t state) {
  std::pair<uint32_t, uint32_t> output;

  switch ((ProfilesFloss)profile) {
    case ProfilesFloss::A2dpSink:
      output.first = (uint32_t)Profile::A2DP;
      switch ((BtavConnectionState)state) {
        case BtavConnectionState::BTAV_CONNECTION_STATE_CONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTED;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_CONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTING;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_DISCONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTED;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_DISCONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTING;
          break;
        default:
          output.second = (uint32_t)ProfilesConnectionState::UNKNOWN;
          break;
      }
      break;
    // case ProfilesFloss::A2dpSource:
    // case ProfilesFloss::AdvAudioDist:
    // case ProfilesFloss::Hsp:
    // case ProfilesFloss::HspAg:
    case ProfilesFloss::Hfp:
      output.first = (uint32_t)Profile::HFP;
      switch ((BthfConnectionState)state) {
        case BthfConnectionState::BTHF_CONNECTION_STATE_DISCONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTED;
          break;
        case BthfConnectionState::BTHF_CONNECTION_STATE_CONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTING;
          break;
        case BthfConnectionState::BTHF_CONNECTION_STATE_CONNECTED:
        case BthfConnectionState::BTHF_CONNECTION_STATE_SLC_CONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTED;
          break;
        case BthfConnectionState::BTHF_CONNECTION_STATE_DISCONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTING;
          break;
        default:
          output.second = (uint32_t)ProfilesConnectionState::UNKNOWN;
          break;
      }
      break;
    // case ProfilesFloss::HfpAg:
    case ProfilesFloss::AvrcpController:
      output.first = (uint32_t)Profile::AVRCP;
      switch ((BtavConnectionState)state) {
        case BtavConnectionState::BTAV_CONNECTION_STATE_CONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTED;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_CONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTING;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_DISCONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTED;
          break;
        case BtavConnectionState::BTAV_CONNECTION_STATE_DISCONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTING;
          break;
        default:
          output.second = (uint32_t)ProfilesConnectionState::UNKNOWN;
          break;
      }
      break;
    // case ProfilesFloss::AvrcpTarget:
    // case ProfilesFloss::ObexObjectPush:
    case ProfilesFloss::Hid:
    case ProfilesFloss::Hogp:
      output.first = (uint32_t)Profile::HID;
      switch ((BthhConnectionState)state) {
        case BthhConnectionState::BTHH_CONN_STATE_CONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTED;
          break;
        case BthhConnectionState::BTHH_CONN_STATE_CONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::CONNECTING;
          break;
        case BthhConnectionState::BTHH_CONN_STATE_DISCONNECTED:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTED;
          break;
        case BthhConnectionState::BTHH_CONN_STATE_DISCONNECTING:
          output.second = (uint32_t)ProfilesConnectionState::DISCONNECTING;
          break;
        case BthhConnectionState::BTHH_CONN_STATE_UNKNOWN:
          output.second = (uint32_t)ProfilesConnectionState::UNKNOWN;
          break;
      }
      break;
    // case ProfilesFloss::Panu:
    // case ProfilesFloss::Nap:
    // case ProfilesFloss::Bnep:
    // case ProfilesFloss::PbapPce:
    // case ProfilesFloss::PbapPse:
    // case ProfilesFloss::Map:
    // case ProfilesFloss::Mns:
    // case ProfilesFloss::Mas:
    // case ProfilesFloss::Sap:
    // case ProfilesFloss::HearingAid:
    // case ProfilesFloss::LeAudio:
    // case ProfilesFloss::Dip:
    // case ProfilesFloss::VolumeControl:
    // case ProfilesFloss::GenericMediaControl:
    // case ProfilesFloss::MediaControl:
    // case ProfilesFloss::CoordinatedSet:
    default:
      output = std::make_pair((uint32_t)Profile::UNKNOWN, state);
      break;
  }

  return output;
}

ProfileConnectionEvent ToProfileConnectionEvent(std::string addr, uint32_t profile, uint32_t status, uint32_t state) {
  ProfileConnectionEvent event;
  // A map stores the pending StateChangeType used to match a (dis)connection event with unknown type.
  // map<std::pair<address, profile>, type>
  static std::map<std::pair<std::string, uint32_t>, StateChangeType> pending_type;

  auto profile_state_pair = ToProfileConnectionState(profile, state);
  auto key = std::make_pair(addr, profile_state_pair.first);
  event.profile = (int64_t)profile_state_pair.first;

  switch ((ProfilesConnectionState)profile_state_pair.second) {
    case ProfilesConnectionState::CONNECTED:
      event.type = (int64_t)StateChangeType::STATE_CHANGE_TYPE_CONNECT;
      event.state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_SUCCEED;
      pending_type.erase(key);
      break;
    case ProfilesConnectionState::CONNECTING:
      event.type = (int64_t)StateChangeType::STATE_CHANGE_TYPE_CONNECT;
      event.state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_STARTING;
      pending_type[key] = StateChangeType::STATE_CHANGE_TYPE_CONNECT;
      break;
    case ProfilesConnectionState::DISCONNECTED:
      event.type = pending_type.find(key) != pending_type.end()
                       ? (int64_t)pending_type[key]
                       : (int64_t)StateChangeType::STATE_CHANGE_TYPE_DISCONNECT;
      // If the profile successfully disconnected for a connect intent, i.e., a connection is attempted but received a
      // disconnection state update. Report this as an unknown error.
      if (StateChangeType::STATE_CHANGE_TYPE_CONNECT == (StateChangeType)event.type &&
          BtStatus::BT_STATUS_SUCCESS == (BtStatus)status) {
        event.state = (int64_t)MetricProfileConnectionStatus::PROFILE_CONN_STATE_UNKNOWN_ERROR;
      } else {
        event.state = StatusToProfileConnectionState(status, (StateChangeType)event.type);
      }
      pending_type.erase(key);
      break;
    case ProfilesConnectionState::DISCONNECTING:
      event.type = (int64_t)StateChangeType::STATE_CHANGE_TYPE_DISCONNECT;
      event.state = (int64_t)MetricProfileDisconnectionStatus::PROFILE_DISCONN_STATE_STARTING;
      pending_type[key] = StateChangeType::STATE_CHANGE_TYPE_DISCONNECT;
      break;
    default:
      event.profile = (int64_t)Profile::UNKNOWN;
      break;
  }

  return event;
}

static int64_t ToAclConnectionStatus(uint32_t status, StateChangeType type, uint32_t hci_reason) {
  int64_t state;
  if (StateChangeType::STATE_CHANGE_TYPE_CONNECT == type) {
    switch ((BtStatus)status) {
      case BtStatus::BT_STATUS_SUCCESS:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_SUCCEED;
        break;
      case BtStatus::BT_STATUS_BUSY:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_BUSY;
        break;
      case BtStatus::BT_STATUS_DONE:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_ALREADY;
        break;
      case BtStatus::BT_STATUS_UNSUPPORTED:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_NOT_SUPPORTED;
        break;
      case BtStatus::BT_STATUS_PARM_INVALID:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_INVALID_PARAMS;
        break;
      case BtStatus::BT_STATUS_AUTH_FAILURE:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_AUTH_FAILED;
        break;
      case BtStatus::BT_STATUS_RMT_DEV_DOWN:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_DISCONNECTED;
        break;
      case BtStatus::BT_STATUS_AUTH_REJECTED:
      case BtStatus::BT_STATUS_FAIL:
      case BtStatus::BT_STATUS_NOT_READY:
      case BtStatus::BT_STATUS_NOMEM:
      case BtStatus::BT_STATUS_UNHANDLED:
      default:
        state = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_UNKNOWN;
        break;
    }
  } else {
    switch (hci_reason) {
      case HCI_ERR_CONNECTION_TOUT:
        state = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_TIMEOUT;
        break;
      case HCI_ERR_PEER_USER:
      case HCI_ERR_REMOTE_LOW_RESOURCE:
      case HCI_ERR_REMOTE_POWER_OFF:
        state = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_REMOTE;
        break;
      case HCI_ERR_CONN_CAUSE_LOCAL_HOST:
        state = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_LOCAL_HOST;
        // TODO: distinguish from ACL_DISCONN_STATE_LOCAL_HOST_SUSPEND
        break;
      case HCI_ERR_AUTH_FAILURE:
      case HCI_ERR_KEY_MISSING:
      case HCI_ERR_HOST_REJECT_SECURITY:
        state = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_AUTH_FAILURE;
        break;
      default:
        state = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_UNKNOWN;
        break;
    }
  }

  return state;
}

// pending acl conn event is map<addr, pair<state, time>>
static std::map<std::string, std::pair<uint32_t, int64_t>> pending_acl_events;

void PendingAclConnectAttemptEvent(std::string addr, int64_t time, uint32_t acl_state) {
  pending_acl_events[addr] = std::make_pair(acl_state, time);
}

AclConnectionEvent ToAclConnectionEvent(
    std::string addr, int64_t time, uint32_t acl_status, uint32_t acl_state, uint32_t direction, uint32_t hci_reason) {
  AclConnectionEvent event;

  if (pending_acl_events.find(addr) == pending_acl_events.end()) {
    // No attempt found! Assume initiated by system.
    event.initiator = (int64_t)MetricAclConnectionInitiator::ACL_CONNECTION_INITIATOR_SYSTEM;
    event.direction = direction;
    event.start_time = time;

    // There is no failed disconnection. Therefore on failure, assume it's a connection attempt.
    if (acl_state == (uint32_t)BtAclState::BT_ACL_STATE_CONNECTED ||
        acl_status != (uint32_t)BtStatus::BT_STATUS_SUCCESS) {
      event.state = (int64_t)StateChangeType::STATE_CHANGE_TYPE_CONNECT;
    } else {
      event.state = (int64_t)StateChangeType::STATE_CHANGE_TYPE_DISCONNECT;
    }
  } else {
    // connection attempt found. Assume initiated by client.
    std::pair<uint32_t, int64_t> pending_event = pending_acl_events[addr];
    pending_acl_events.erase(addr);
    event.initiator = (int64_t)MetricAclConnectionInitiator::ACL_CONNECTION_INITIATOR_CLIENT;
    event.direction = (int64_t)MetricAclConnectionDirection::ACL_CONNECTION_OUTGOING;
    event.start_time = pending_event.second;

    if (pending_event.first == (uint32_t)BtAclState::BT_ACL_STATE_CONNECTED) {
      event.state = (int64_t)StateChangeType::STATE_CHANGE_TYPE_CONNECT;
    } else {
      event.state = (int64_t)StateChangeType::STATE_CHANGE_TYPE_DISCONNECT;
    }
  }

  if (event.state == (int64_t)StateChangeType::STATE_CHANGE_TYPE_CONNECT) {
    event.start_status = (int64_t)MetricAclConnectionStatus::ACL_CONN_STATE_STARTING;
  } else {
    event.start_status = (int64_t)MetricAclDisconnectionStatus::ACL_DISCONN_STATE_STARTING;
  }

  event.status = ToAclConnectionStatus(acl_status, (StateChangeType)event.state, hci_reason);

  return event;
}

static int64_t GetChipsetInfoId(const char* path, const char* file) {
  std::string content;
  int64_t id;

  if (base::ReadFileToString(base::FilePath(path).Append(file), &content)) {
    if (base::HexStringToInt64(base::CollapseWhitespaceASCII(content, false), &id)) {
      return id;
    }
  }
  return 0;
}

static std::string GetChipsetInfoModuleName() {
  std::string module;
  int adapter_index = bluetooth::common::InitFlags::GetAdapterIndex();
  std::string path = base::StringPrintf(kChipsetInfoModaliasPath, adapter_index);

  if (base::ReadFileToString(base::FilePath(path), &module)) {
    return base::CollapseWhitespaceASCII(module, false);
  }
  return "";
}

static MetricTransportType GetChipsetInfoTransport(void) {
  MetricTransportType transport = MetricTransportType::TRANSPORT_TYPE_UNKNOWN;
  base::FilePath module_realpath;
  std::string module_name;
  int adapter_index = bluetooth::common::InitFlags::GetAdapterIndex();
  std::string path = base::StringPrintf(kChipInfoModuleDirPath, adapter_index);

  // examples of module_realpath: /sys/module/btusb and /sys/module/hci_uart
  module_realpath = base::MakeAbsoluteFilePath(base::FilePath(path));
  if (module_realpath.empty()) {
    return transport;
  }

  module_name = module_realpath.BaseName().value();
  if (base::MatchPattern(module_name, "*usb*"))
    transport = MetricTransportType::TRANSPORT_TYPE_USB;
  else if (base::MatchPattern(module_name, "*uart*"))
    transport = MetricTransportType::TRANSPORT_TYPE_UART;
  else if (base::MatchPattern(module_name, "*sdio*"))
    transport = MetricTransportType::TRANSPORT_TYPE_SDIO;

  return transport;
}

MetricsChipsetInfo GetMetricsChipsetInfo() {
  MetricsChipsetInfo info;

  info.vid = GetChipsetInfoId(kChipsetInfoWlanDirPath, "vendor");
  info.pid = GetChipsetInfoId(kChipsetInfoWlanDirPath, "device");

  if (!info.vid || !info.pid) {
    info.vid = GetChipsetInfoId(kChipsetInfoMlanDirPath, "vendor");
    info.pid = GetChipsetInfoId(kChipsetInfoMlanDirPath, "device");
  }

  if (!info.vid || !info.pid) {
    info.chipset_string = GetChipsetInfoModuleName();
  }

  info.transport = (int)GetChipsetInfoTransport();
  return info;
}

}  // namespace metrics
}  // namespace bluetooth

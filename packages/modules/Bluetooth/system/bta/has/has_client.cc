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

#include <base/functional/bind.h>
#include <base/functional/callback.h>
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <bluetooth/log.h>
#include <hardware/bt_gatt_types.h>
#include <hardware/bt_has.h>

#include <list>
#include <map>
#include <mutex>
#include <string>
#include <vector>

#include "bta_csis_api.h"
#include "bta_gatt_api.h"
#include "bta_gatt_queue.h"
#include "bta_has_api.h"
#include "bta_le_audio_uuids.h"
#include "btm_sec.h"
#include "gap_api.h"
#include "gatt_api.h"
#include "has_types.h"
#include "include/check.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/bt_types.h"

using base::Closure;
using bluetooth::Uuid;
using bluetooth::csis::CsisClient;
using bluetooth::has::ConnectionState;
using bluetooth::has::ErrorCode;
using bluetooth::has::kFeatureBitPresetSynchronizationSupported;
using bluetooth::has::kHasPresetIndexInvalid;
using bluetooth::has::PresetInfo;
using bluetooth::has::PresetInfoReason;
using le_audio::has::HasClient;
using le_audio::has::HasCtpGroupOpCoordinator;
using le_audio::has::HasCtpNtf;
using le_audio::has::HasCtpOp;
using le_audio::has::HasDevice;
using le_audio::has::HasGattOpContext;
using le_audio::has::HasJournalRecord;
using le_audio::has::HasPreset;
using le_audio::has::kControlPointMandatoryOpcodesBitmask;
using le_audio::has::kControlPointSynchronizedOpcodesBitmask;
using le_audio::has::kUuidActivePresetIndex;
using le_audio::has::kUuidHearingAccessService;
using le_audio::has::kUuidHearingAidFeatures;
using le_audio::has::kUuidHearingAidPresetControlPoint;
using le_audio::has::PresetCtpChangeId;
using le_audio::has::PresetCtpOpcode;
using namespace bluetooth;

void btif_storage_add_leaudio_has_device(const RawAddress& address,
                                         std::vector<uint8_t> presets_bin,
                                         uint8_t features,
                                         uint8_t active_preset);
bool btif_storage_get_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t>& presets_bin,
                                          uint8_t& active_preset);
void btif_storage_set_leaudio_has_presets(const RawAddress& address,
                                          std::vector<uint8_t> presets_bin);
bool btif_storage_get_leaudio_has_features(const RawAddress& address,
                                           uint8_t& features);
void btif_storage_set_leaudio_has_features(const RawAddress& address,
                                           uint8_t features);
void btif_storage_set_leaudio_has_active_preset(const RawAddress& address,
                                                uint8_t active_preset);
void btif_storage_remove_leaudio_has(const RawAddress& address);

bool gatt_profile_get_eatt_support(const RawAddress& remote_bda);

namespace {
class HasClientImpl;
HasClientImpl* instance;
std::mutex instance_mutex;

/**
 * -----------------------------------------------------------------------------
 * Hearing Access Service - Client role
 * -----------------------------------------------------------------------------
 * Overview:
 *
 * This is Hearing Access Service client class.
 *
 * Each connected peer device supporting Hearing Access Service (HAS) is being
 * connected and has its characteristics discovered. All the characteristics
 * and descriptors (incl. the optional ones) are being read or written during
 * this initial connection stage. Encryption is also verified. If all of this
 * succeeds the appropriate callbacks are being called to notify upper layer
 * about the successful HAS device connection and its features and the list
 * of available audio configuration presets.
 *
 * Each HA device is expected to have the HAS service instantiated. It must
 * contain Hearing Aid Features characteristic and optionally Presets Control
 * Point and Active Preset Index characteristics, allowing the user to read
 * preset details, switch currently active preset and possibly rename some of
 * them.
 *
 * Hearing Aid Features characteristic informs the client about the type of
 * Hearign Aids device (Monaural, Binaural or Banded), which operations are
 * supported via the Preset Control Point characteristic, about dynamically
 * changing list of available presets, writable presets and the support for
 * synchronised preset change operations on the Binaural Hearing Aid devices.
 */
class HasClientImpl : public HasClient {
 public:
  HasClientImpl(bluetooth::has::HasClientCallbacks* callbacks,
                base::Closure initCb)
      : gatt_if_(0), callbacks_(callbacks) {
    BTA_GATTC_AppRegister(
        [](tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
          if (instance && p_data) instance->GattcCallback(event, p_data);
        },
        base::Bind(
            [](base::Closure initCb, uint8_t client_id, uint8_t status) {
              if (status != GATT_SUCCESS) {
                log::error(
                    "Can't start Hearing Aid Service client profile - no gatt "
                    "clients left!");
                return;
              }
              instance->gatt_if_ = client_id;
              initCb.Run();
            },
            initCb),
        true);
  }

  ~HasClientImpl() override = default;

  void Connect(const RawAddress& address) override {
    DLOG(INFO) << __func__ << ": " <<  ADDRESS_TO_LOGGABLE_STR(address);

    std::vector<RawAddress> addresses = {address};
    auto csis_api = CsisClient::Get();
    if (csis_api != nullptr) {
      // Connect entire CAS set of devices
      auto group_id = csis_api->GetGroupId(
          address, bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE));
      addresses = csis_api->GetDeviceList(group_id);
    }

    if (addresses.empty()) {
      log::warn("{} is not part of any set", ADDRESS_TO_LOGGABLE_STR(address));
      addresses = {address};
    }

    for (auto const& addr : addresses) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(addr));
      if (device == devices_.end()) {
        devices_.emplace_back(addr, true);
        BTA_GATTC_Open(gatt_if_, addr, BTM_BLE_DIRECT_CONNECTION, false);

      } else {
        device->is_connecting_actively = true;
        if (!device->IsConnected())
          BTA_GATTC_Open(gatt_if_, addr, BTM_BLE_DIRECT_CONNECTION, false);
      }
    }
  }

  void AddFromStorage(const RawAddress& address, uint8_t features,
                      uint16_t is_acceptlisted) {
    DLOG(INFO) << __func__ << ": " << ADDRESS_TO_LOGGABLE_STR(address)
               << ", features=" << loghex(features)
               << ", isAcceptlisted=" << is_acceptlisted;

    /* Notify upper layer about the device */
    callbacks_->OnDeviceAvailable(address, features);
    if (is_acceptlisted) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(address));
      if (device == devices_.end())
        devices_.push_back(HasDevice(address, features));

      /* Connect in background */
      BTA_GATTC_Open(gatt_if_, address, BTM_BLE_BKG_CONNECT_ALLOW_LIST, false);
    }
  }

  void Disconnect(const RawAddress& address) override {
    DLOG(INFO) << __func__ << ": " << ADDRESS_TO_LOGGABLE_STR(address);

    std::vector<RawAddress> addresses = {address};
    auto csis_api = CsisClient::Get();
    if (csis_api != nullptr) {
      // Disconnect entire CAS set of devices
      auto group_id = csis_api->GetGroupId(
          address, bluetooth::Uuid::From16Bit(UUID_COMMON_AUDIO_SERVICE));
      addresses = csis_api->GetDeviceList(group_id);
    }

    if (addresses.empty()) {
      log::warn("{} is not part of any set", ADDRESS_TO_LOGGABLE_STR(address));
      addresses = {address};
    }

    for (auto const& addr : addresses) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(addr));
      if (device == devices_.end()) {
        log::warn("Device not connected to profile{}",
                  ADDRESS_TO_LOGGABLE_STR(addr));
        return;
      }

      auto conn_id = device->conn_id;
      auto is_connecting_actively = device->is_connecting_actively;
      devices_.erase(device);

      if (conn_id != GATT_INVALID_CONN_ID) {
        BTA_GATTC_Close(conn_id);
        callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, addr);
      } else {
        /* Removes active connection. */
        if (is_connecting_actively) {
          BTA_GATTC_CancelOpen(gatt_if_, addr, true);
          callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, addr);
        }
      }

      /* Removes all registrations for connection. */
      BTA_GATTC_CancelOpen(0, addr, false);
    }
  }

  void UpdateJournalOpEntryStatus(HasDevice& device, HasGattOpContext context,
                                  tGATT_STATUS status) {
    /* Find journal entry by the context and update */
    auto journal_entry = std::find_if(
        device.has_journal_.begin(), device.has_journal_.end(),
        [&context](auto const& record) {
          if (record.is_operation) {
            return HasGattOpContext(record.op_context_handle) == context;
          }
          return false;
        });

    if (journal_entry == device.has_journal_.end()) {
      log::warn(
          "Journaling error or journal length limit was set to low. Unable to "
          "log the operation outcome.");
      return;
    }

    if (journal_entry == device.has_journal_.end()) {
      log::error("Unable to find operation context in the journal!");
      return;
    }

    journal_entry->op_status = status;
  }

  std::optional<HasCtpOp> ExtractPendingCtpOp(uint16_t op_id) {
    auto op_it =
        std::find_if(pending_operations_.begin(), pending_operations_.end(),
                     [op_id](auto const& el) { return op_id == el.op_id; });

    if (op_it != pending_operations_.end()) {
      auto op = *op_it;
      pending_operations_.erase(op_it);

      return op;
    }
    return std::nullopt;
  }

  void EnqueueCtpOp(HasCtpOp op) { pending_operations_.push_back(op); }

  void OnHasActivePresetCycleStatus(uint16_t conn_id, tGATT_STATUS status,
                                    void* user_data) {
    DLOG(INFO) << __func__ << " status: " << +status;

    auto device = GetDevice(conn_id);
    if (!device) {
      log::warn("Device not connected to profile, conn_id={}", conn_id);
      return;
    }

    /* Journal update */
    LOG_ASSERT(user_data != nullptr) << "Has operation context is missing!";
    auto context = HasGattOpContext(user_data);
    UpdateJournalOpEntryStatus(*device, context, status);

    auto op_opt = ExtractPendingCtpOp(context.ctp_op_id);
    if (status == GATT_SUCCESS) return;

    /* This could be one of the coordinated group preset change request */
    pending_group_operation_timeouts_.erase(context.ctp_op_id);

    /* Error handling */
    if (!op_opt.has_value()) {
      log::error("Unknown operation error");
      return;
    }
    auto op = op_opt.value();
    callbacks_->OnActivePresetSelectError(op.addr_or_group,
                                          GattStatus2SvcErrorCode(status));

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
    }
  }

  void OnHasPresetNameSetStatus(uint16_t conn_id, tGATT_STATUS status,
                                void* user_data) {
    auto device = GetDevice(conn_id);
    if (!device) {
      log::warn("Device not connected to profile, conn_id={}", conn_id);
      return;
    }

    LOG_ASSERT(user_data != nullptr) << "Has operation context is missing!";
    HasGattOpContext context(user_data);

    /* Journal update */
    UpdateJournalOpEntryStatus(*device, context, status);

    auto op_opt = ExtractPendingCtpOp(context.ctp_op_id);
    if (status == GATT_SUCCESS) return;

    /* This could be one of the coordinated group preset change request */
    pending_group_operation_timeouts_.erase(context.ctp_op_id);

    /* Error handling */
    if (!op_opt.has_value()) {
      log::error("Unknown operation error");
      return;
    }
    auto op = op_opt.value();
    callbacks_->OnSetPresetNameError(device->addr, op.index,
                                     GattStatus2SvcErrorCode(status));
    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
    }
  }

  void OnHasPresetNameGetStatus(uint16_t conn_id, tGATT_STATUS status,
                                void* user_data) {
    auto device = GetDevice(conn_id);
    if (!device) {
      log::warn("Device not connected to profile, conn_id={}", conn_id);
      return;
    }

    LOG_ASSERT(user_data != nullptr) << "Has operation context is missing!";
    HasGattOpContext context(user_data);

    /* Journal update */
    UpdateJournalOpEntryStatus(*device, context, status);

    auto op_opt = ExtractPendingCtpOp(context.ctp_op_id);
    if (status == GATT_SUCCESS) return;

    /* Error handling */
    if (!op_opt.has_value()) {
      log::error("Unknown operation error");
      return;
    }
    auto op = op_opt.value();
    callbacks_->OnPresetInfoError(device->addr, op.index,
                                  GattStatus2SvcErrorCode(status));

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
    } else {
      log::error("Devices {}: Control point not usable. Disconnecting!",
                 ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      BTA_GATTC_Close(device->conn_id);
    }
  }

  void OnHasPresetIndexOperation(uint16_t conn_id, tGATT_STATUS status,
                                 void* user_data) {
    DLOG(INFO) << __func__;

    auto device = GetDevice(conn_id);
    if (!device) {
      log::warn("Device not connected to profile, conn_id={}", conn_id);
      return;
    }

    LOG_ASSERT(user_data != nullptr) << "Has operation context is missing!";
    HasGattOpContext context(user_data);

    /* Journal update */
    UpdateJournalOpEntryStatus(*device, context, status);

    auto op_opt = ExtractPendingCtpOp(context.ctp_op_id);
    if (status == GATT_SUCCESS) return;

    /* This could be one of the coordinated group preset change request */
    pending_group_operation_timeouts_.erase(context.ctp_op_id);

    /* Error handling */
    if (!op_opt.has_value()) {
      log::error("Unknown operation error");
      return;
    }

    auto op = op_opt.value();
    if (op.opcode == PresetCtpOpcode::READ_PRESETS) {
      callbacks_->OnPresetInfoError(device->addr, op.index,
                                    GattStatus2SvcErrorCode(status));

    } else {
      callbacks_->OnActivePresetSelectError(op.addr_or_group,
                                            GattStatus2SvcErrorCode(status));
    }

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
    } else {
      log::error("Devices {}: Control point not usable. Disconnecting!",
                 ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      BTA_GATTC_Close(device->conn_id);
    }
  }

  void CpReadAllPresetsOperation(HasCtpOp operation) {
    DLOG(INFO) << __func__ << " Operation: " << operation;

    if (std::holds_alternative<int>(operation.addr_or_group)) {
      log::error("Read all presets on the entire group not supported.");
      callbacks_->OnPresetInfoError(operation.addr_or_group, operation.index,
                                    ErrorCode::OPERATION_NOT_POSSIBLE);
      return;
    }

    auto device = std::find_if(
        devices_.begin(), devices_.end(),
        HasDevice::MatchAddress(std::get<RawAddress>(operation.addr_or_group)));
    if (device == devices_.end()) {
      log::warn("Device not connected to profile addr: {}",
                ADDRESS_TO_LOGGABLE_STR(
                    std::get<RawAddress>(operation.addr_or_group)));
      callbacks_->OnPresetInfoError(device->addr, operation.index,
                                    ErrorCode::OPERATION_NOT_POSSIBLE);
      return;
    }

    if (!device->SupportsPresets()) {
      callbacks_->OnPresetInfoError(device->addr, operation.index,
                                    ErrorCode::OPERATION_NOT_SUPPORTED);
    }

    auto context = HasGattOpContext(operation);

    /* Journal update */
    device->has_journal_.Append(HasJournalRecord(operation, context));

    /* Write to control point */
    EnqueueCtpOp(operation);
    BtaGattQueue::WriteCharacteristic(
        device->conn_id, device->cp_handle, operation.ToCharacteristicValue(),
        GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* user_data) {
          if (instance)
            instance->OnHasPresetNameGetStatus(conn_id, status, user_data);
        },
        context);
  }

  ErrorCode CpPresetIndexOperationWriteReq(HasDevice& device,
                                           HasCtpOp& operation) {
    DLOG(INFO) << __func__ << " Operation: " << operation;

    if (!device.IsConnected()) return ErrorCode::OPERATION_NOT_POSSIBLE;

    if (!device.SupportsPresets()) return ErrorCode::OPERATION_NOT_SUPPORTED;

    if (!device.SupportsOperation(operation.opcode))
      return operation.IsGroupRequest()
                 ? ErrorCode::GROUP_OPERATION_NOT_SUPPORTED
                 : ErrorCode::OPERATION_NOT_SUPPORTED;

    if (!device.IsValidPreset(operation.index))
      return ErrorCode::INVALID_PRESET_INDEX;

    auto context = HasGattOpContext(operation);

    /* Journal update */
    device.has_journal_.Append(HasJournalRecord(operation, context));

    /* Write to control point */
    EnqueueCtpOp(operation);
    BtaGattQueue::WriteCharacteristic(
        device.conn_id, device.cp_handle, operation.ToCharacteristicValue(),
        GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* user_data) {
          if (instance)
            instance->OnHasPresetIndexOperation(conn_id, status, user_data);
        },
        context);

    return ErrorCode::NO_ERROR;
  }

  bool AreAllDevicesAvailable(const std::vector<RawAddress>& addresses) {
    for (auto& addr : addresses) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(addr));
      if (device == devices_.end() || !device->IsConnected()) {
        return false;
      }
    }
    return true;
  }

  ErrorCode CpPresetOperationCaller(
      HasCtpOp operation,
      std::function<ErrorCode(HasDevice& device, HasCtpOp& operation)>
          write_cb) {
    DLOG(INFO) << __func__ << " Operation: " << operation;
    auto status = ErrorCode::NO_ERROR;

    if (operation.IsGroupRequest()) {
      auto csis_api = CsisClient::Get();
      if (csis_api == nullptr) {
        /* No CSIS means no group operations */
        status = ErrorCode::GROUP_OPERATION_NOT_SUPPORTED;

      } else {
        auto group_id = operation.GetGroupId();
        auto addresses = csis_api->GetDeviceList(group_id);

        /* Perform the operation only when all the devices are available */
        if (!AreAllDevicesAvailable(addresses)) {
          addresses.clear();
        }

        if (addresses.empty()) {
          status = ErrorCode::OPERATION_NOT_POSSIBLE;

        } else {
          /* Make this a coordinated operation */
          pending_group_operation_timeouts_.emplace(
              operation.op_id, HasCtpGroupOpCoordinator(addresses, operation));

          if (operation.IsSyncedOperation()) {
            status = ErrorCode::GROUP_OPERATION_NOT_SUPPORTED;

            /* Clear the error if we find device to forward the operation */
            bool was_sent = false;
            for (auto& addr : addresses) {
              auto device = std::find_if(devices_.begin(), devices_.end(),
                                         HasDevice::MatchAddress(addr));
              if (device != devices_.end()) {
                status = write_cb(*device, operation);
                if (status == ErrorCode::NO_ERROR) {
                  was_sent = true;
                  break;
                }
              }
            }
            if (!was_sent) status = ErrorCode::OPERATION_NOT_POSSIBLE;

          } else {
            status = ErrorCode::GROUP_OPERATION_NOT_SUPPORTED;

            for (auto& addr : addresses) {
              auto device = std::find_if(devices_.begin(), devices_.end(),
                                         HasDevice::MatchAddress(addr));
              if (device != devices_.end()) {
                status = write_cb(*device, operation);
                if (status != ErrorCode::NO_ERROR) break;
              }
            }
          }

          /* Erase group op coordinator on error */
          if (status != ErrorCode::NO_ERROR) {
            pending_group_operation_timeouts_.erase(operation.op_id);
          }
        }
      }

    } else {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(std::get<RawAddress>(
                                     operation.addr_or_group)));
      status = ErrorCode::OPERATION_NOT_POSSIBLE;
      if (device != devices_.end()) status = write_cb(*device, operation);
    }

    return status;
  }

  void CpPresetIndexOperation(HasCtpOp operation) {
    log::info("Operation: {}", operation);

    auto status = CpPresetOperationCaller(
        operation, [](HasDevice& device, HasCtpOp operation) -> ErrorCode {
          if (instance)
            return instance->CpPresetIndexOperationWriteReq(device, operation);
          return ErrorCode::OPERATION_NOT_POSSIBLE;
        });

    if (status != ErrorCode::NO_ERROR) {
      switch (operation.opcode) {
        case PresetCtpOpcode::READ_PRESETS:
          LOG_ASSERT(
              std::holds_alternative<RawAddress>(operation.addr_or_group))
              << " Unsupported group operation!";

          callbacks_->OnPresetInfoError(
              std::get<RawAddress>(operation.addr_or_group), operation.index,
              status);
          break;
        case PresetCtpOpcode::SET_ACTIVE_PRESET:
        case PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC:
          callbacks_->OnActivePresetSelectError(operation.addr_or_group,
                                                status);
          break;
        default:
          break;
      }
    }
  }

  ErrorCode CpPresetsCycleOperationWriteReq(HasDevice& device,
                                            HasCtpOp& operation) {
    DLOG(INFO) << __func__ << " addr: " << ADDRESS_TO_LOGGABLE_STR(device.addr)
               << " operation: " << operation;

    if (!device.IsConnected()) return ErrorCode::OPERATION_NOT_POSSIBLE;

    if (!device.SupportsPresets()) return ErrorCode::OPERATION_NOT_SUPPORTED;

    if (!device.SupportsOperation(operation.opcode))
      return operation.IsGroupRequest()
                 ? ErrorCode::GROUP_OPERATION_NOT_SUPPORTED
                 : ErrorCode::OPERATION_NOT_SUPPORTED;

    auto context = HasGattOpContext(operation);

    /* Journal update */
    device.has_journal_.Append(HasJournalRecord(operation, context));

    /* Write to control point */
    EnqueueCtpOp(operation);
    BtaGattQueue::WriteCharacteristic(
        device.conn_id, device.cp_handle, operation.ToCharacteristicValue(),
        GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* user_data) {
          if (instance)
            instance->OnHasActivePresetCycleStatus(conn_id, status, user_data);
        },
        context);
    return ErrorCode::NO_ERROR;
  }

  void CpPresetsCycleOperation(HasCtpOp operation) {
    DLOG(INFO) << __func__ << " Operation: " << operation;

    auto status = CpPresetOperationCaller(
        operation, [](HasDevice& device, HasCtpOp operation) -> ErrorCode {
          if (instance)
            return instance->CpPresetsCycleOperationWriteReq(device, operation);
          return ErrorCode::OPERATION_NOT_POSSIBLE;
        });

    if (status != ErrorCode::NO_ERROR)
      callbacks_->OnActivePresetSelectError(operation.addr_or_group, status);
  }

  ErrorCode CpWritePresetNameOperationWriteReq(HasDevice& device,
                                               HasCtpOp operation) {
    DLOG(INFO) << __func__ << " addr: " << ADDRESS_TO_LOGGABLE_STR(device.addr)
               << " operation: " << operation;

    if (!device.IsConnected()) return ErrorCode::OPERATION_NOT_POSSIBLE;

    if (!device.SupportsPresets()) return ErrorCode::OPERATION_NOT_SUPPORTED;

    if (!device.IsValidPreset(operation.index, true))
      return device.IsValidPreset(operation.index)
                 ? ErrorCode::SET_NAME_NOT_ALLOWED
                 : ErrorCode::INVALID_PRESET_INDEX;

    if (!device.SupportsOperation(operation.opcode))
      return ErrorCode::OPERATION_NOT_SUPPORTED;

    if (operation.name.value_or("").length() >
        le_audio::has::HasPreset::kPresetNameLengthLimit)
      return ErrorCode::INVALID_PRESET_NAME_LENGTH;

    auto context = HasGattOpContext(operation, operation.index);

    /* Journal update */
    device.has_journal_.Append(HasJournalRecord(operation, context));

    /* Write to control point */
    EnqueueCtpOp(operation);
    BtaGattQueue::WriteCharacteristic(
        device.conn_id, device.cp_handle, operation.ToCharacteristicValue(),
        GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           const uint8_t* value, void* user_data) {
          if (instance)
            instance->OnHasPresetNameSetStatus(conn_id, status, user_data);
        },
        context);

    return ErrorCode::NO_ERROR;
  }

  void CpWritePresetNameOperation(HasCtpOp operation) {
    DLOG(INFO) << __func__ << " operation: " << operation;

    auto status = ErrorCode::NO_ERROR;

    std::vector<RawAddress> addresses;
    if (operation.IsGroupRequest()) {
      auto csis_api = CsisClient::Get();
      if (csis_api != nullptr) {
        addresses = csis_api->GetDeviceList(operation.GetGroupId());

        /* Make this a coordinated operation */
        pending_group_operation_timeouts_.emplace(
            operation.op_id, HasCtpGroupOpCoordinator(addresses, operation));
      }

    } else {
      addresses = {operation.GetDeviceAddr()};
    }

    status = ErrorCode::OPERATION_NOT_POSSIBLE;

    /* Perform the operation only when all the devices are available */
    if (!AreAllDevicesAvailable(addresses)) {
      addresses.clear();
    }

    for (auto& addr : addresses) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(addr));
      if (device != devices_.end()) {
        status = CpWritePresetNameOperationWriteReq(*device, operation);
        if (status != ErrorCode::NO_ERROR) {
          log::error("Control point write error: {}", (int)status);
          break;
        }
      }
    }

    if (status != ErrorCode::NO_ERROR) {
      if (operation.IsGroupRequest())
        pending_group_operation_timeouts_.erase(operation.op_id);

      callbacks_->OnSetPresetNameError(operation.addr_or_group, operation.index,
                                       status);
    }
  }

  bool shouldRequestSyncedOp(std::variant<RawAddress, int> addr_or_group_id,
                             PresetCtpOpcode opcode) {
    /* Do not select locally synced ops when not performing group operations,
     * You never know if the user will make another call for the other devices
     * in this set even though the may support locally synced operations.
     */
    if (std::holds_alternative<RawAddress>(addr_or_group_id)) return false;

    auto csis_api = CsisClient::Get();
    if (csis_api == nullptr) return false;

    auto addresses = csis_api->GetDeviceList(std::get<int>(addr_or_group_id));
    if (addresses.empty()) return false;

    for (auto& addr : addresses) {
      auto device = std::find_if(devices_.begin(), devices_.end(),
                                 HasDevice::MatchAddress(addr));
      if (device != devices_.end()) {
        if (device->SupportsOperation(opcode)) return true;
      }
    }

    return false;
  }

  void SelectActivePreset(std::variant<RawAddress, int> addr_or_group_id,
                          uint8_t preset_index) override {
    DLOG(INFO) << __func__;

    auto opcode = shouldRequestSyncedOp(addr_or_group_id,
                                        PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC)
                      ? PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC
                      : PresetCtpOpcode::SET_ACTIVE_PRESET;

    CpPresetIndexOperation(HasCtpOp(addr_or_group_id, opcode, preset_index));
  }

  void NextActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) override {
    DLOG(INFO) << __func__;

    auto opcode = shouldRequestSyncedOp(addr_or_group_id,
                                        PresetCtpOpcode::SET_NEXT_PRESET_SYNC)
                      ? PresetCtpOpcode::SET_NEXT_PRESET_SYNC
                      : PresetCtpOpcode::SET_NEXT_PRESET;

    CpPresetsCycleOperation(HasCtpOp(addr_or_group_id, opcode));
  }

  void PreviousActivePreset(
      std::variant<RawAddress, int> addr_or_group_id) override {
    DLOG(INFO) << __func__;

    auto opcode = shouldRequestSyncedOp(addr_or_group_id,
                                        PresetCtpOpcode::SET_PREV_PRESET_SYNC)
                      ? PresetCtpOpcode::SET_PREV_PRESET_SYNC
                      : PresetCtpOpcode::SET_PREV_PRESET;

    CpPresetsCycleOperation(HasCtpOp(addr_or_group_id, opcode));
  }

  void GetPresetInfo(const RawAddress& address, uint8_t preset_index) override {
    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(address));
    if (device == devices_.end()) {
      log::warn("Device not connected to profile{}",
                ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    DLOG(INFO) << __func__ << " preset idx: " << +preset_index;

    /* Due to mandatory control point notifications or indications, preset
     * details are always up to date. However we have to be able to do the
     * READ_PRESET_BY_INDEX, to pass the test specification requirements.
     */
    if (osi_property_get_bool("persist.bluetooth.has.always_use_preset_cache",
                              true)) {
      auto* preset = device->GetPreset(preset_index);
      if (preset == nullptr) {
        log::error("Invalid preset request{}",
                   ADDRESS_TO_LOGGABLE_STR(address));
        callbacks_->OnPresetInfoError(address, preset_index,
                                      ErrorCode::INVALID_PRESET_INDEX);
        return;
      }

      callbacks_->OnPresetInfo(address,
                               PresetInfoReason::PRESET_INFO_REQUEST_RESPONSE,
                               {{.preset_index = preset_index,
                                 .writable = preset->IsWritable(),
                                 .available = preset->IsAvailable(),
                                 .preset_name = preset->GetName()}});
    } else {
      CpPresetIndexOperation(
          HasCtpOp(address, PresetCtpOpcode::READ_PRESETS, preset_index));
    }
  }

  void SetPresetName(std::variant<RawAddress, int> addr_or_group_id,
                     uint8_t preset_index, std::string name) override {
    DLOG(INFO) << __func__ << "preset_idx: " << +preset_index
               << ", name: " << name;

    CpWritePresetNameOperation(HasCtpOp(addr_or_group_id,
                                        PresetCtpOpcode::WRITE_PRESET_NAME,
                                        preset_index, 1 /* Don't care */, name));
  }

  void CleanUp() {
    BTA_GATTC_AppDeregister(gatt_if_);
    for (auto& device : devices_) {
      if (device.conn_id != GATT_INVALID_CONN_ID)
        BTA_GATTC_Close(device.conn_id);
      DoDisconnectCleanUp(device);
    }

    devices_.clear();
    pending_operations_.clear();
  }

  void Dump(int fd) const {
    std::stringstream stream;
    stream << " APP ID: " << +gatt_if_ << " \n";
    if (devices_.size()) {
      stream << "  {\"Known HAS devices\": [";
      for (const auto& device : devices_) {
        stream << "\n    {";
        device.Dump(stream);
        stream << "\n    },\n";
      }
      stream << "  ]}\n\n";
    } else {
      stream << "  \"No known HAS devices\"\n\n";
    }
    dprintf(fd, "%s", stream.str().c_str());
  }

  void OnGroupOpCoordinatorTimeout(void* p) {
    log::error(
        "Coordinated operation timeout:  not all the devices notified their "
        "state change on time.");

    /* Clear pending group operations */
    pending_group_operation_timeouts_.clear();
    HasCtpGroupOpCoordinator::Cleanup();
  }

 private:
  void WriteAllNeededCcc(const HasDevice& device) {
    if (device.conn_id == GATT_INVALID_CONN_ID) {
      log::error("Device {} is not connected",
                 ADDRESS_TO_LOGGABLE_CSTR(device.addr));
      return;
    }

    /* Write CCC values even remote should have it */
    log::info("Subscribing for notification/indications");
    if (device.SupportsFeaturesNotification()) {
      SubscribeForNotifications(device.conn_id, device.addr,
                                device.features_handle,
                                device.features_ccc_handle);
    }

    if (device.SupportsPresets()) {
      SubscribeForNotifications(device.conn_id, device.addr, device.cp_handle,
                                device.cp_ccc_handle, device.cp_ccc_val);
      SubscribeForNotifications(device.conn_id, device.addr,
                                device.active_preset_handle,
                                device.active_preset_ccc_handle);
    }

    if (osi_property_get_bool("persist.bluetooth.has.always_use_preset_cache",
                              true) == false) {
      CpReadAllPresetsOperation(HasCtpOp(
          device.addr, PresetCtpOpcode::READ_PRESETS,
          le_audio::has::kStartPresetIndex, le_audio::has::kMaxNumOfPresets));
    }
  }

  void OnEncrypted(HasDevice& device) {
    DLOG(INFO) << __func__ << ": " << ADDRESS_TO_LOGGABLE_STR(device.addr);

    if (device.isGattServiceValid()) {
      device.is_connecting_actively = false;
      NotifyHasDeviceValid(device);
      callbacks_->OnPresetInfo(device.addr, PresetInfoReason::ALL_PRESET_INFO,
                               device.GetAllPresetInfo());
      callbacks_->OnActivePresetSelected(device.addr,
                                         device.currently_active_preset);
      WriteAllNeededCcc(device);
    } else {
      BTA_GATTC_ServiceSearchRequest(device.conn_id,
                                     &kUuidHearingAccessService);
    }
  }

  void NotifyHasDeviceValid(const HasDevice& device) {
    DLOG(INFO) << __func__ << " addr:" << ADDRESS_TO_LOGGABLE_STR(device.addr);

    std::vector<uint8_t> preset_indices;
    preset_indices.reserve(device.has_presets.size());
    for (auto const& preset : device.has_presets) {
      preset_indices.push_back(preset.GetIndex());
    }

    /* Notify that we are ready to go */
    callbacks_->OnConnectionState(ConnectionState::CONNECTED, device.addr);
  }

  void MarkDeviceValidIfInInitialDiscovery(HasDevice& device) {
    if (device.isGattServiceValid()) return;

    --device.gatt_svc_validation_steps;

    if (device.isGattServiceValid()) {
      device.is_connecting_actively = false;

      std::vector<uint8_t> presets_bin;
      if (device.SerializePresets(presets_bin)) {
        btif_storage_add_leaudio_has_device(device.addr, presets_bin,
                                            device.GetFeatures(),
                                            device.currently_active_preset);
      }
      NotifyHasDeviceValid(device);
    }
  }

  void OnGattWriteCcc(uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
                      void* user_data) {
    DLOG(INFO) << __func__ << ": handle=" << loghex(handle);

    auto device = GetDevice(conn_id);
    if (!device) {
      log::error("unknown conn_id={}", loghex(conn_id));
      BtaGattQueue::Clean(conn_id);
      return;
    }

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      ClearDeviceInformationAndStartSearch(device);
      return;
    }

    HasGattOpContext context(user_data);
    bool enabling_ntf = context.context_flags &
                        HasGattOpContext::kContextFlagsEnableNotification;

    if (handle == device->features_ccc_handle) {
      if (status == GATT_SUCCESS)
        device->features_notifications_enabled = enabling_ntf;

    } else if ((handle == device->active_preset_ccc_handle) ||
               (handle == device->cp_ccc_handle)) {
      /* Both of these CCC are mandatory */
      if (enabling_ntf && (status != GATT_SUCCESS)) {
        log::error("Failed to register for notifications on handle={}",
                   loghex(handle));
        BTA_GATTC_Close(conn_id);
        return;
      }
    }
  }

  void OnHasNotification(uint16_t conn_id, uint16_t handle, uint16_t len,
                         const uint8_t* value) {
    auto device = GetDevice(conn_id);
    if (!device) {
      log::warn("Skipping unknown device, conn_id={}", loghex(conn_id));
      return;
    }

    if (handle == device->features_handle) {
      OnHasFeaturesValue(&(*device), GATT_SUCCESS, handle, len, value);

    } else if (handle == device->cp_handle) {
      OnHasCtpValueNotification(&(*device), len, value);

    } else if (handle == device->active_preset_handle) {
      OnHasActivePresetValue(&(*device), GATT_SUCCESS, handle, len, value);
    }
  }

  /* Gets the device from variant, possibly searching by conn_id */
  HasDevice* GetDevice(
      std::variant<uint16_t, HasDevice*> conn_id_device_variant) {
    HasDevice* device = nullptr;

    if (std::holds_alternative<HasDevice*>(conn_id_device_variant)) {
      device = std::get<HasDevice*>(conn_id_device_variant);
    } else {
      auto it = std::find_if(
          devices_.begin(), devices_.end(),
          HasDevice::MatchConnId(std::get<uint16_t>(conn_id_device_variant)));
      if (it != devices_.end()) device = &(*it);
    }

    return device;
  }

  void OnHasFeaturesValue(
      std::variant<uint16_t, HasDevice*> conn_id_device_variant,
      tGATT_STATUS status, uint16_t handle, uint16_t len, const uint8_t* value,
      void* user_data = nullptr) {
    DLOG(INFO) << __func__;

    auto device = GetDevice(conn_id_device_variant);
    if (!device) {
      log::error("Unknown device!");
      return;
    }

    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
      return;
    }

    if (len != 1) {
      log::error("Invalid features value length={} at handle={}", len,
                 loghex(handle));
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    /* Store features value */
    uint8_t features;
    STREAM_TO_UINT8(features, value);
    device->UpdateFeatures(features);

    if (device->isGattServiceValid()) {
      btif_storage_set_leaudio_has_features(device->addr, features);
    }

    /* Journal update */
    device->has_journal_.Append(HasJournalRecord(features, true));

    /* When service is not yet validated, report the available device with
     * features.
     */
    if (!device->isGattServiceValid())
      callbacks_->OnDeviceAvailable(device->addr, device->GetFeatures());

    /* Notify features */
    callbacks_->OnFeaturesUpdate(device->addr, device->GetFeatures());

    MarkDeviceValidIfInInitialDiscovery(*device);
  }

  /* Translates GATT statuses to application specific error codes */
  static ErrorCode GattStatus2SvcErrorCode(tGATT_STATUS status) {
    switch (status) {
      case 0x80:
        /* Invalid Opcode */
        /* Unlikely to happen as we would not allow unsupported operations */
        return ErrorCode::OPERATION_NOT_SUPPORTED;
      case 0x81:
        /* Write Name Not Allowed */
        return ErrorCode::SET_NAME_NOT_ALLOWED;
      case 0x82:
        /* Synchronization Not Supported */
        return ErrorCode::OPERATION_NOT_SUPPORTED;
      case 0x83:
        /* Preset Operation Not Possible */
        return ErrorCode::OPERATION_NOT_POSSIBLE;
      case 0x84:
        /* Preset Name Too Long */
        return ErrorCode::INVALID_PRESET_NAME_LENGTH;
      case 0xFE:
        /* Procedure Already in Progress */
        return ErrorCode::PROCEDURE_ALREADY_IN_PROGRESS;
      default:
        return ErrorCode::OPERATION_NOT_POSSIBLE;
    }
  }

  void OnHasPresetReadResponseNotification(HasDevice& device) {
    DLOG(INFO) << __func__;

    while (device.ctp_notifications_.size() != 0) {
      auto ntf = device.ctp_notifications_.front();
      /* Process only read response events */
      if (ntf.opcode != PresetCtpOpcode::READ_PRESET_RESPONSE) break;

      /* Update preset values */
      if (ntf.preset.has_value()) {
        device.has_presets.erase(ntf.preset->GetIndex());
        device.has_presets.insert(ntf.preset.value());
      }

      /* We currently do READ_ALL_PRESETS only during the service validation.
       * If service is already valid, this must be the READ_PRESET_BY_INDEX.
       */
      if (device.isGattServiceValid()) {
        auto info = device.GetPresetInfo(ntf.preset.value().GetIndex());
        if (info.has_value())
          callbacks_->OnPresetInfo(
              device.addr, PresetInfoReason::PRESET_INFO_REQUEST_RESPONSE,
              {{info.value()}});
      }

      /* Journal update */
      device.has_journal_.Append(HasJournalRecord(ntf));
      device.ctp_notifications_.pop_front();
    }

    auto in_svc_validation = !device.isGattServiceValid();
    MarkDeviceValidIfInInitialDiscovery(device);

    /* We currently do READ_ALL_PRESETS only during the service validation.
     * ALL_PRESET_INFO will be sent only during this initial phase.
     */
    if (in_svc_validation) {
      callbacks_->OnPresetInfo(device.addr, PresetInfoReason::ALL_PRESET_INFO,
                               device.GetAllPresetInfo());

      /* If this was the last validation step then send the currently active
       * preset as well.
       */
      if (device.isGattServiceValid())
        callbacks_->OnActivePresetSelected(device.addr,
                                           device.currently_active_preset);
    }
  }

  void OnHasPresetGenericUpdate(HasDevice& device) {
    DLOG(ERROR) << __func__;

    std::vector<PresetInfo> updated_infos;
    std::vector<PresetInfo> deleted_infos;

    /* Process the entire train of preset changes with generic updates */
    while (device.ctp_notifications_.size() != 0) {
      auto nt = device.ctp_notifications_.front();

      /* Break if not a generic update anymore */
      if (nt.opcode != PresetCtpOpcode::PRESET_CHANGED) break;
      if (nt.change_id != PresetCtpChangeId::PRESET_GENERIC_UPDATE) break;

      if (nt.preset.has_value()) {
        /* Erase old value if exist */
        device.has_presets.erase(nt.preset->GetIndex());

        /* Erase in-between indices */
        if (nt.prev_index != 0) {
          auto it = device.has_presets.begin();
          while (it != device.has_presets.end()) {
            if ((it->GetIndex() > nt.prev_index) &&
                (it->GetIndex() < nt.preset->GetIndex())) {
              auto info = device.GetPresetInfo(it->GetIndex());
              if (info.has_value()) deleted_infos.push_back(info.value());

              it = device.has_presets.erase(it);

            } else {
              ++it;
            }
          }
        }
        /* Update presets */
        device.has_presets.insert(*nt.preset);

        auto info = device.GetPresetInfo(nt.preset->GetIndex());
        if (info.has_value()) updated_infos.push_back(info.value());
      }

      /* Journal update */
      device.has_journal_.Append(HasJournalRecord(nt));
      device.ctp_notifications_.pop_front();
    }

    if (device.isGattServiceValid()) {
      /* Update preset values in the storage */
      std::vector<uint8_t> presets_bin;
      if (device.SerializePresets(presets_bin)) {
        btif_storage_set_leaudio_has_presets(device.addr, presets_bin);
      }

      /* Check for the matching coordinated group op. to use group callbacks */
      for (auto it = pending_group_operation_timeouts_.rbegin();
           it != pending_group_operation_timeouts_.rend(); ++it) {
        auto& group_op_coordinator = it->second;

        /* Here we interested only in valid preset name changes */
        if (!((group_op_coordinator.operation.opcode ==
               PresetCtpOpcode::WRITE_PRESET_NAME) &&
              group_op_coordinator.operation.name.has_value()))
          continue;

        /* Match preset update results with the triggering operation */
        auto renamed_preset_info = std::find_if(
            updated_infos.begin(), updated_infos.end(),
            [&group_op_coordinator](const auto& info) {
              return (group_op_coordinator.operation.name.value() ==
                      info.preset_name);
            });
        if (renamed_preset_info == updated_infos.end()) continue;

        if (group_op_coordinator.SetCompleted(device.addr)) {
          group_op_coordinator.preset_info_verification_list.push_back(
              *renamed_preset_info);

          /* Call the proper group operation completion callback */
          if (group_op_coordinator.IsFullyCompleted()) {
            callbacks_->OnPresetInfo(
                group_op_coordinator.operation.GetGroupId(),
                PresetInfoReason::PRESET_INFO_UPDATE, {*renamed_preset_info});
            pending_group_operation_timeouts_.erase(it->first);
          }

          /* Erase it from the 'updated_infos' since later we'll be sending
           * this as a group callback when the other device completes the
           * coordinated group name change.
           *
           * WARNING: There might an issue with callbacks call reordering due to
           *  some of them being kept for group callbacks called later, when all
           *  the grouped devices complete the coordinated group rename
           *  operation. In most cases this should not be a major problem.
           */
          updated_infos.erase(renamed_preset_info);
          break;
        }
      }

      if (!updated_infos.empty())
        callbacks_->OnPresetInfo(
            device.addr, PresetInfoReason::PRESET_INFO_UPDATE, updated_infos);

      if (!deleted_infos.empty())
        callbacks_->OnPresetInfo(device.addr, PresetInfoReason::PRESET_DELETED,
                                 deleted_infos);
    }
  }

  void OnHasPresetAvailabilityChanged(HasDevice& device) {
    DLOG(INFO) << __func__;

    std::vector<PresetInfo> infos;

    while (device.ctp_notifications_.size() != 0) {
      auto nt = device.ctp_notifications_.front();

      /* Process only preset change notifications */
      if (nt.opcode != PresetCtpOpcode::PRESET_CHANGED) break;

      auto preset = device.has_presets.extract(nt.index).value();
      auto new_props = preset.GetProperties();

      /* Process only the preset availability changes and then notify */
      if ((nt.change_id != PresetCtpChangeId::PRESET_AVAILABLE) &&
          (nt.change_id != PresetCtpChangeId::PRESET_UNAVAILABLE))
        break;

      /* Availability change */
      if (nt.change_id == PresetCtpChangeId::PRESET_AVAILABLE) {
        new_props |= HasPreset::kPropertyAvailable;
      } else {
        new_props &= !HasPreset::kPropertyAvailable;
      }
      device.has_presets.insert(
          HasPreset(preset.GetIndex(), new_props, preset.GetName()));

      auto info = device.GetPresetInfo(nt.index);
      if (info.has_value()) infos.push_back(info.value());

      /* Journal update */
      device.has_journal_.Append(HasJournalRecord(nt));
      device.ctp_notifications_.pop_front();
    }

    /* Update preset storage */
    if (device.isGattServiceValid()) {
      std::vector<uint8_t> presets_bin;
      if (device.SerializePresets(presets_bin)) {
        btif_storage_set_leaudio_has_presets(device.addr, presets_bin);
      }
    }

    callbacks_->OnPresetInfo(
        device.addr, PresetInfoReason::PRESET_AVAILABILITY_CHANGED, infos);
  }

  void OnHasPresetDeleted(HasDevice& device) {
    DLOG(INFO) << __func__;

    std::vector<PresetInfo> infos;
    bool is_deleted = false;

    while (device.ctp_notifications_.size() != 0) {
      auto nt = device.ctp_notifications_.front();

      /* Process only preset change notifications */
      if (nt.opcode != PresetCtpOpcode::PRESET_CHANGED) break;

      /* Process only the deletions and then notify */
      if (nt.change_id != PresetCtpChangeId::PRESET_DELETED) break;

      auto info = device.GetPresetInfo(nt.index);
      if (info.has_value()) infos.push_back(info.value());

      if (device.has_presets.count(nt.index)) {
        is_deleted = true;
        device.has_presets.erase(nt.index);
      }

      /* Journal update */
      device.has_journal_.Append(HasJournalRecord(nt));
      device.ctp_notifications_.pop_front();
    }

    /* Update preset storage */
    if (device.isGattServiceValid()) {
      std::vector<uint8_t> presets_bin;
      if (device.SerializePresets(presets_bin)) {
        btif_storage_set_leaudio_has_presets(device.addr, presets_bin);
      }
    }

    if (is_deleted)
      callbacks_->OnPresetInfo(device.addr, PresetInfoReason::PRESET_DELETED,
                               infos);
  }

  void ProcessCtpNotificationQueue(HasDevice& device) {
    std::vector<PresetInfo> infos;

    while (device.ctp_notifications_.size() != 0) {
      auto ntf = device.ctp_notifications_.front();
      DLOG(INFO) << __func__ << " ntf: " << ntf;

      if (ntf.opcode == PresetCtpOpcode::PRESET_CHANGED) {
        switch (ntf.change_id) {
          case PresetCtpChangeId::PRESET_GENERIC_UPDATE:
            OnHasPresetGenericUpdate(device);
            break;
          case PresetCtpChangeId::PRESET_AVAILABLE:
            OnHasPresetAvailabilityChanged(device);
            break;
          case PresetCtpChangeId::PRESET_UNAVAILABLE:
            OnHasPresetAvailabilityChanged(device);
            break;
          case PresetCtpChangeId::PRESET_DELETED:
            OnHasPresetDeleted(device);
            break;
          default:
            log::error("Invalid notification: {}", ntf);
            break;
        }

      } else if (ntf.opcode == PresetCtpOpcode::READ_PRESET_RESPONSE) {
        OnHasPresetReadResponseNotification(device);

      } else {
        log::error("Unsupported preset notification: {}", ntf);
      }
    }
  }

  void OnHasCtpValueNotification(HasDevice* device, uint16_t len,
                                 const uint8_t* value) {
    auto ntf_opt = HasCtpNtf::FromCharacteristicValue(len, value);
    if (!ntf_opt.has_value()) {
      log::error("Unhandled notification for device: {}", *device);
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    auto ntf = ntf_opt.value();
    DLOG(INFO) << __func__ << ntf;

    device->ctp_notifications_.push_back(ntf);
    if (ntf.is_last) ProcessCtpNotificationQueue(*device);
  }

  void OnHasActivePresetValue(
      std::variant<uint16_t, HasDevice*> conn_id_device_variant,
      tGATT_STATUS status, uint16_t handle, uint16_t len, const uint8_t* value,
      void* user_data = nullptr) {
    DLOG(INFO) << __func__;

    auto device = GetDevice(conn_id_device_variant);
    if (!device) {
      log::error("Skipping unknown device!");
      return;
    }

    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->addr));
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error("Could not read characteristic at handle=0x{:04x}", handle);
        BTA_GATTC_Close(device->conn_id);
      }
    }

    if (len != 1) {
      log::error("Invalid preset value length={} at handle={}", len,
                 loghex(handle));
      BTA_GATTC_Close(device->conn_id);
      return;
    }

    /* Get the active preset value */
    auto* pp = value;
    STREAM_TO_UINT8(device->currently_active_preset, pp);

    if (device->isGattServiceValid()) {
      btif_storage_set_leaudio_has_active_preset(
          device->addr, device->currently_active_preset);
    }

    /* Journal update */
    device->has_journal_.Append(
        HasJournalRecord(device->currently_active_preset, false));

    /* If svc not marked valid, this might be the last validation step. */
    MarkDeviceValidIfInInitialDiscovery(*device);

    if (device->isGattServiceValid()) {
      if (!pending_group_operation_timeouts_.empty()) {
        for (auto it = pending_group_operation_timeouts_.rbegin();
             it != pending_group_operation_timeouts_.rend(); ++it) {
          auto& group_op_coordinator = it->second;

          bool matches = false;
          switch (group_op_coordinator.operation.opcode) {
            case PresetCtpOpcode::SET_ACTIVE_PRESET:
              [[fallthrough]];
            case PresetCtpOpcode::SET_NEXT_PRESET:
              [[fallthrough]];
            case PresetCtpOpcode::SET_PREV_PRESET:
              [[fallthrough]];
            case PresetCtpOpcode::SET_ACTIVE_PRESET_SYNC:
              [[fallthrough]];
            case PresetCtpOpcode::SET_NEXT_PRESET_SYNC:
              [[fallthrough]];
            case PresetCtpOpcode::SET_PREV_PRESET_SYNC: {
              if (group_op_coordinator.SetCompleted(device->addr)) {
                matches = true;
                break;
              }
            } break;
            default:
              /* Ignore */
              break;
          }
          if (group_op_coordinator.IsFullyCompleted()) {
            callbacks_->OnActivePresetSelected(
                group_op_coordinator.operation.GetGroupId(),
                device->currently_active_preset);
            pending_group_operation_timeouts_.erase(it->first);
          }
          if (matches) break;
        }

      } else {
        callbacks_->OnActivePresetSelected(device->addr,
                                           device->currently_active_preset);
      }
    }
  }

  void DeregisterNotifications(HasDevice& device) {
    /* Deregister from optional features notifications */
    if (device.features_ccc_handle != GAP_INVALID_HANDLE) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, device.addr,
                                           device.features_handle);
    }

    /* Deregister from active presets notifications if presets exist */
    if (device.active_preset_ccc_handle != GAP_INVALID_HANDLE) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, device.addr,
                                           device.active_preset_handle);
    }

    /* Deregister from control point notifications */
    if (device.cp_ccc_handle != GAP_INVALID_HANDLE) {
      BTA_GATTC_DeregisterForNotifications(gatt_if_, device.addr,
                                           device.cp_handle);
    }
  }

  /* Cleans up after the device disconnection */
  void DoDisconnectCleanUp(HasDevice& device,
                           bool invalidate_gatt_service = true) {
    log::debug(": device={}", ADDRESS_TO_LOGGABLE_CSTR(device.addr));

    DeregisterNotifications(device);

    if (device.conn_id != GATT_INVALID_CONN_ID) {
      BtaGattQueue::Clean(device.conn_id);
      if (invalidate_gatt_service) device.gatt_svc_validation_steps = 0xFE;
    }

    /* Clear pending operations */
    auto addr = device.addr;
    pending_operations_.erase(
        std::remove_if(
            pending_operations_.begin(), pending_operations_.end(),
            [&addr](auto& el) {
              if (std::holds_alternative<RawAddress>(el.addr_or_group)) {
                return std::get<RawAddress>(el.addr_or_group) == addr;
              }
              return false;
            }),
        pending_operations_.end());

    device.ConnectionCleanUp();
  }

  /* These below are all GATT service discovery, validation, cache & storage */
  bool CacheAttributeHandles(const gatt::Service& service, HasDevice* device) {
    DLOG(INFO) << __func__ << ": device="
               << ADDRESS_TO_LOGGABLE_STR(device->addr);

    for (const gatt::Characteristic& charac : service.characteristics) {
      if (charac.uuid == kUuidActivePresetIndex) {
        /* Find the mandatory CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        if (ccc_handle == GAP_INVALID_HANDLE) {
          log::error("no HAS Active Preset CCC descriptor found!");
          return false;
        }
        device->active_preset_ccc_handle = ccc_handle;
        device->active_preset_handle = charac.value_handle;

      } else if (charac.uuid == kUuidHearingAidPresetControlPoint) {
        /* Find the mandatory CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        if (ccc_handle == GAP_INVALID_HANDLE) {
          log::error("no HAS Control Point CCC descriptor found!");
          return false;
        }
        uint8_t ccc_val = 0;
        if (charac.properties & GATT_CHAR_PROP_BIT_NOTIFY)
          ccc_val |= GATT_CHAR_CLIENT_CONFIG_NOTIFICATION;

        if (charac.properties & GATT_CHAR_PROP_BIT_INDICATE)
          ccc_val |= GATT_CHAR_CLIENT_CONFIG_INDICTION;

        if (ccc_val == 0) {
          log::error("Invalid properties for the control point 0x{:02x}",
                     charac.properties);
          return false;
        }

        device->cp_ccc_handle = ccc_handle;
        device->cp_handle = charac.value_handle;
        device->cp_ccc_val = ccc_val;
      } else if (charac.uuid == kUuidHearingAidFeatures) {
        /* Find the optional CCC descriptor */
        uint16_t ccc_handle =
            FindCccHandle(device->conn_id, charac.value_handle);
        device->features_ccc_handle = ccc_handle;
        device->features_handle = charac.value_handle;
      }
    }
    return true;
  }

  bool LoadHasDetailsFromStorage(HasDevice* device) {
    DLOG(INFO) << __func__ << ": device="
               << ADDRESS_TO_LOGGABLE_STR(device->addr);

    std::vector<uint8_t> presets_bin;
    uint8_t active_preset;

    if (!btif_storage_get_leaudio_has_presets(device->addr, presets_bin,
                                              active_preset))
      return false;

    if (!HasDevice::DeserializePresets(presets_bin.data(), presets_bin.size(),
                                       *device))
      return false;

    log::verbose("Loading HAS service details from storage.");

    device->currently_active_preset = active_preset;

    /* Update features and refresh opcode support map */
    uint8_t val;
    if (btif_storage_get_leaudio_has_features(device->addr, val))
      device->UpdateFeatures(val);

    /* With all the details loaded we can already mark it as valid */
    device->gatt_svc_validation_steps = 0;
    device->is_connecting_actively = false;

    NotifyHasDeviceValid(*device);
    callbacks_->OnPresetInfo(device->addr, PresetInfoReason::ALL_PRESET_INFO,
                             device->GetAllPresetInfo());
    callbacks_->OnActivePresetSelected(device->addr,
                                       device->currently_active_preset);
    if (device->conn_id == GATT_INVALID_CONN_ID) return true;

    /* Be mistrustful here: write CCC values even remote should have it */
    log::info("Subscribing for notification/indications");
    WriteAllNeededCcc(*device);

    return true;
  }

  bool StartInitialHasDetailsReadAndValidation(const gatt::Service& service,
                                               HasDevice* device) {
    // Validate service structure
    if (device->features_handle == GAP_INVALID_HANDLE) {
      /* Missing key characteristic */
      log::error("Service has broken structure");
      return false;
    }

    if (device->cp_handle != GAP_INVALID_HANDLE) {
      if (device->active_preset_handle == GAP_INVALID_HANDLE) return false;
      if (device->active_preset_ccc_handle == GAP_INVALID_HANDLE) return false;
    }

    /* Number of reads or notifications required to validate the service */
    device->gatt_svc_validation_steps = 1 + (device->SupportsPresets() ? 2 : 0);

    /* Read the initial features */
    BtaGattQueue::ReadCharacteristic(
        device->conn_id, device->features_handle,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle, uint16_t len,
           uint8_t* value, void* user_data) {
          if (instance)
            instance->OnHasFeaturesValue(conn_id, status, handle, len, value,
                                         user_data);
        },
        nullptr);

    /* Register for features notifications */
    if (device->SupportsFeaturesNotification()) {
      SubscribeForNotifications(device->conn_id, device->addr,
                                device->features_handle,
                                device->features_ccc_handle);
    } else {
      log::warn("server does not support features notification");
    }

    /* If Presets are supported we should read them all and subscribe for the
     * mandatory active preset index notifications.
     */
    if (device->SupportsPresets()) {
      /* Subscribe for active preset notifications */
      SubscribeForNotifications(device->conn_id, device->addr,
                                device->active_preset_handle,
                                device->active_preset_ccc_handle);

      SubscribeForNotifications(device->conn_id, device->addr,
                                device->cp_handle, device->cp_ccc_handle,
                                device->cp_ccc_val);

      /* Get all the presets */
      CpReadAllPresetsOperation(HasCtpOp(
          device->addr, PresetCtpOpcode::READ_PRESETS,
          le_audio::has::kStartPresetIndex, le_audio::has::kMaxNumOfPresets));

      /* Read the current active preset index */
      BtaGattQueue::ReadCharacteristic(
          device->conn_id, device->active_preset_handle,
          [](uint16_t conn_id, tGATT_STATUS status, uint16_t handle,
             uint16_t len, uint8_t* value, void* user_data) {
            if (instance)
              instance->OnHasActivePresetValue(conn_id, status, handle, len,
                                               value, user_data);
          },
          nullptr);
    } else {
      log::warn(
          "server can only report HAS features, other functionality is "
          "disabled");
    }

    return true;
  }

  bool OnHasServiceFound(const gatt::Service& service, void* context) {
    DLOG(INFO) << __func__;

    auto* device = static_cast<HasDevice*>(context);

    /* Initially validate and store GATT service discovery data */
    if (!CacheAttributeHandles(service, device)) return false;

    /* If deatails are loaded from storage we are done here */
    if (LoadHasDetailsFromStorage(device)) return true;

    /* No storred details - read all the details and validate */
    return StartInitialHasDetailsReadAndValidation(service, device);
  }

  /* These below are all generic event handlers calling in HAS specific code. */
  void GattcCallback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
    DLOG(INFO) << __func__ << ": event = " << static_cast<int>(event);

    switch (event) {
      case BTA_GATTC_DEREG_EVT:
        break;

      case BTA_GATTC_OPEN_EVT:
        OnGattConnected(p_data->open);
        break;

      case BTA_GATTC_CLOSE_EVT:
        OnGattDisconnected(p_data->close);
        break;

      case BTA_GATTC_SEARCH_CMPL_EVT:
        OnGattServiceSearchComplete(p_data->search_cmpl);
        break;

      case BTA_GATTC_NOTIF_EVT:
        OnGattNotification(p_data->notify);
        break;

      case BTA_GATTC_ENC_CMPL_CB_EVT:
        OnLeEncryptionComplete(p_data->enc_cmpl.remote_bda,
            BTM_IsEncrypted(p_data->enc_cmpl.remote_bda, BT_TRANSPORT_LE));
        break;

      case BTA_GATTC_SRVC_CHG_EVT:
        OnGattServiceChangeEvent(p_data->remote_bda);
        break;

      case BTA_GATTC_SRVC_DISC_DONE_EVT:
        OnGattServiceDiscoveryDoneEvent(p_data->remote_bda);
        break;

      default:
        break;
    }
  }

  void OnGattConnected(const tBTA_GATTC_OPEN& evt) {
    log::info("{}, conn_id=0x{:04x}, transport={}, status={}(0x{:02x})",
              ADDRESS_TO_LOGGABLE_CSTR(evt.remote_bda), evt.conn_id,
              bt_transport_text(evt.transport), gatt_status_text(evt.status),
              evt.status);

    if (evt.transport != BT_TRANSPORT_LE) {
      log::warn("Only LE connection is allowed (transport {})",
                bt_transport_text(evt.transport));
      BTA_GATTC_Close(evt.conn_id);
      return;
    }

    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(evt.remote_bda));
    if (device == devices_.end()) {
      log::warn("Skipping unknown device, address={}",
                ADDRESS_TO_LOGGABLE_STR(evt.remote_bda));
      BTA_GATTC_Close(evt.conn_id);
      return;
    }

    if (evt.status != GATT_SUCCESS) {
      if (!device->is_connecting_actively) {
        // acceptlist connection failed, that's ok.
        return;
      }

      log::warn("Failed to connect to server device");
      devices_.erase(device);
      callbacks_->OnConnectionState(ConnectionState::DISCONNECTED,
                                    evt.remote_bda);
      return;
    }

    device->conn_id = evt.conn_id;

    if (BTM_SecIsSecurityPending(device->addr)) {
      /* if security collision happened, wait for encryption done
       * (BTA_GATTC_ENC_CMPL_CB_EVT)
       */
      return;
    }

    /* verify bond */
    if (BTM_IsEncrypted(device->addr, BT_TRANSPORT_LE)) {
      /* if link has been encrypted */
      OnEncrypted(*device);
      return;
    }

    int result = BTM_SetEncryption(device->addr, BT_TRANSPORT_LE, nullptr,
                                   nullptr, BTM_BLE_SEC_ENCRYPT);

    log::info("Encryption required for {}. Request result: 0x{:02x}",
              ADDRESS_TO_LOGGABLE_CSTR(device->addr), result);

    if (result == BTM_ERR_KEY_MISSING) {
      log::error("Link key unknown for {}, disconnect profile",
                 ADDRESS_TO_LOGGABLE_CSTR(device->addr));
      BTA_GATTC_Close(device->conn_id);
    }
  }

  void OnGattDisconnected(const tBTA_GATTC_CLOSE& evt) {
    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(evt.remote_bda));
    if (device == devices_.end()) {
      log::warn("Skipping unknown device disconnect, conn_id={}",
                loghex(evt.conn_id));
      return;
    }
    DLOG(INFO) << __func__ << ": device="
               << ADDRESS_TO_LOGGABLE_STR(device->addr)
               << ": reason=" << loghex(static_cast<int>(evt.reason));

    /* Don't notify disconnect state for background connection that failed */
    if (device->is_connecting_actively || device->isGattServiceValid())
      callbacks_->OnConnectionState(ConnectionState::DISCONNECTED,
                                    evt.remote_bda);

    auto peer_disconnected = (evt.reason == GATT_CONN_TIMEOUT) ||
                             (evt.reason == GATT_CONN_TERMINATE_PEER_USER);
    DoDisconnectCleanUp(*device, peer_disconnected ? false : true);

    /* Connect in background - is this ok? */
    if (peer_disconnected)
      BTA_GATTC_Open(gatt_if_, device->addr, BTM_BLE_BKG_CONNECT_ALLOW_LIST,
                     false);
  }

  void OnGattServiceSearchComplete(const tBTA_GATTC_SEARCH_CMPL& evt) {
    auto device = GetDevice(evt.conn_id);
    if (!device) {
      log::warn("Skipping unknown device, conn_id={}", loghex(evt.conn_id));
      return;
    }

    DLOG(INFO) << __func__;

    /* verify link is encrypted */
    if (!BTM_IsEncrypted(device->addr, BT_TRANSPORT_LE)) {
      log::warn("Device not yet bonded - waiting for encryption");
      return;
    }

    /* Ignore if our service data is valid (service discovery initiated by
     * someone else?)
     */
    if (!device->isGattServiceValid()) {
      if (evt.status != GATT_SUCCESS) {
        log::error("Service discovery failed");
        BTA_GATTC_Close(device->conn_id);
        return;
      }

      const std::list<gatt::Service>* all_services =
          BTA_GATTC_GetServices(device->conn_id);

      auto service =
          std::find_if(all_services->begin(), all_services->end(),
                       [](const gatt::Service& svc) {
                         return svc.uuid == kUuidHearingAccessService;
                       });
      if (service == all_services->end()) {
        log::error("No service found");
        BTA_GATTC_Close(device->conn_id);
        return;
      }

      /* Call the service specific verifier callback */
      if (!instance->OnHasServiceFound(*service, &(*device))) {
        log::error("Not a valid service!");
        BTA_GATTC_Close(device->conn_id);
        return;
      }
    }
  }

  void OnGattNotification(const tBTA_GATTC_NOTIFY& evt) {
    /* Reject invalid lengths */
    if (evt.len > GATT_MAX_ATTR_LEN) {
      log::error("rejected BTA_GATTC_NOTIF_EVT. is_notify = {}, len={}",
                 evt.is_notify, static_cast<int>(evt.len));
    }
    if (!evt.is_notify) BTA_GATTC_SendIndConfirm(evt.conn_id, evt.cid);

    OnHasNotification(evt.conn_id, evt.handle, evt.len, evt.value);
  }

  void OnLeEncryptionComplete(const RawAddress& address, bool success) {
    DLOG(INFO) << __func__ << ": " << ADDRESS_TO_LOGGABLE_STR(address);

    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(address));
    if (device == devices_.end()) {
      log::warn("Skipping unknown device{}", ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    if (!success) {
      log::error("Encryption failed for device {}",
                 ADDRESS_TO_LOGGABLE_STR(address));

      BTA_GATTC_Close(device->conn_id);
      return;
    }

    if (device->isGattServiceValid()) {
      instance->OnEncrypted(*device);
    } else {
      BTA_GATTC_ServiceSearchRequest(device->conn_id,
                                     &kUuidHearingAccessService);
    }
  }

  void ClearDeviceInformationAndStartSearch(HasDevice* device) {
    if (!device) {
      log::error("Device is null");
      return;
    }

    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(device->addr));

    if (!device->isGattServiceValid()) {
      log::info("Service already invalidated");
      return;
    }

    /* Invalidate service discovery results */
    DeregisterNotifications(*device);
    BtaGattQueue::Clean(device->conn_id);
    device->ClearSvcData();
    btif_storage_remove_leaudio_has(device->addr);
    BTA_GATTC_ServiceSearchRequest(device->conn_id, &kUuidHearingAccessService);
  }

  void OnGattServiceChangeEvent(const RawAddress& address) {
    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(address));
    if (device == devices_.end()) {
      log::warn("Skipping unknown device: {}",
                ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));
    ClearDeviceInformationAndStartSearch(&(*device));
  }

  void OnGattServiceDiscoveryDoneEvent(const RawAddress& address) {
    auto device = std::find_if(devices_.begin(), devices_.end(),
                               HasDevice::MatchAddress(address));
    if (device == devices_.end()) {
      log::warn("Skipping unknown device: {}",
                ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    DLOG(INFO) << __func__ << ": address="
               << ADDRESS_TO_LOGGABLE_STR(address);

    if (!device->isGattServiceValid())
      BTA_GATTC_ServiceSearchRequest(device->conn_id,
                                     &kUuidHearingAccessService);
  }

  static uint16_t FindCccHandle(uint16_t conn_id, uint16_t char_handle) {
    const gatt::Characteristic* p_char =
        BTA_GATTC_GetCharacteristic(conn_id, char_handle);
    if (!p_char) {
      log::warn("No such characteristic: {}", char_handle);
      return GAP_INVALID_HANDLE;
    }

    for (const gatt::Descriptor& desc : p_char->descriptors) {
      if (desc.uuid == Uuid::From16Bit(GATT_UUID_CHAR_CLIENT_CONFIG))
        return desc.handle;
    }

    return GAP_INVALID_HANDLE;
  }

  void SubscribeForNotifications(
      uint16_t conn_id, const RawAddress& address, uint16_t value_handle,
      uint16_t ccc_handle,
      uint16_t ccc_val = GATT_CHAR_CLIENT_CONFIG_NOTIFICATION) {
    if (value_handle != GAP_INVALID_HANDLE) {
      tGATT_STATUS register_status =
          BTA_GATTC_RegisterForNotifications(gatt_if_, address, value_handle);
      DLOG(INFO) << __func__ << ": BTA_GATTC_RegisterForNotifications, status="
                 << loghex(+register_status)
                 << " value=" << loghex(value_handle)
                 << " ccc=" << loghex(ccc_handle);

      if (register_status != GATT_SUCCESS) return;
    }

    std::vector<uint8_t> value(2);
    uint8_t* value_ptr = value.data();
    UINT16_TO_STREAM(value_ptr, ccc_val);
    BtaGattQueue::WriteDescriptor(
        conn_id, ccc_handle, std::move(value), GATT_WRITE,
        [](uint16_t conn_id, tGATT_STATUS status, uint16_t value_handle,
           uint16_t len, const uint8_t* value, void* data) {
          if (instance)
            instance->OnGattWriteCcc(conn_id, status, value_handle, data);
        },
        HasGattOpContext(HasGattOpContext::kContextFlagsEnableNotification));
  }

  uint8_t gatt_if_;
  bluetooth::has::HasClientCallbacks* callbacks_;
  std::list<HasDevice> devices_;
  std::list<HasCtpOp> pending_operations_;

  typedef std::map<decltype(HasCtpOp::op_id), HasCtpGroupOpCoordinator>
      has_operation_timeouts_t;
  has_operation_timeouts_t pending_group_operation_timeouts_;
};

}  // namespace

alarm_t* HasCtpGroupOpCoordinator::operation_timeout_timer = nullptr;
size_t HasCtpGroupOpCoordinator::ref_cnt = 0u;
alarm_callback_t HasCtpGroupOpCoordinator::cb = [](void*) {};

void HasClient::Initialize(bluetooth::has::HasClientCallbacks* callbacks,
                           base::Closure initCb) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (instance) {
    log::error("Already initialized!");
    return;
  }

  HasCtpGroupOpCoordinator::Initialize([](void* p) {
    if (instance) instance->OnGroupOpCoordinatorTimeout(p);
  });
  instance = new HasClientImpl(callbacks, initCb);
}

bool HasClient::IsHasClientRunning() { return instance; }

HasClient* HasClient::Get(void) {
  CHECK(instance);
  return instance;
};

void HasClient::AddFromStorage(const RawAddress& addr, uint8_t features,
                               uint16_t is_acceptlisted) {
  if (!instance) {
    log::error("Not initialized yet");
  }

  instance->AddFromStorage(addr, features, is_acceptlisted);
};

void HasClient::CleanUp() {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  HasClientImpl* ptr = instance;
  instance = nullptr;

  if (ptr) {
    ptr->CleanUp();
    delete ptr;
  }

  HasCtpGroupOpCoordinator::Cleanup();
};

void HasClient::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  dprintf(fd, "Hearing Access Service Client:\n");
  if (instance)
    instance->Dump(fd);
  else
    dprintf(fd, "  no instance\n\n");
}

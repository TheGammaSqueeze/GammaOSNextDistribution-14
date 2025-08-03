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
#include <base/logging.h>
#include <base/strings/string_number_conversions.h>
#include <base/strings/string_util.h>
#include <bluetooth/log.h>
#include <hardware/bt_gatt_types.h>
#include <hardware/bt_vc.h>

#include <mutex>
#include <string>
#include <vector>

#include "bta/le_audio/le_audio_types.h"
#include "bta_csis_api.h"
#include "bta_gatt_api.h"
#include "bta_gatt_queue.h"
#include "bta_vc_api.h"
#include "devices.h"
#include "include/check.h"
#include "internal_include/bt_trace.h"
#include "os/log.h"
#include "osi/include/osi.h"
#include "stack/btm/btm_sec.h"
#include "stack/include/bt_types.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using base::Closure;
using bluetooth::Uuid;
using bluetooth::csis::CsisClient;
using bluetooth::vc::ConnectionState;
using namespace bluetooth::vc::internal;
using namespace bluetooth;

namespace {
class VolumeControlImpl;
VolumeControlImpl* instance;
std::mutex instance_mutex;

/**
 * Overview:
 *
 * This is Volume Control Implementation class which realize Volume Control
 * Profile (VCP)
 *
 * Each connected peer device supporting Volume Control Service (VCS) is on the
 * list of devices (volume_control_devices_). When VCS is discovered on the peer
 * device, Android does search for all the instances Volume Offset Service
 * (VOCS). Note that AIS and VOCS are optional.
 *
 * Once all the mandatory characteristis for all the services are discovered,
 * Fluoride calls ON_CONNECTED callback.
 *
 * It is assumed that whenever application changes general audio options in this
 * profile e.g. Volume up/down, mute/unmute etc, profile configures all the
 * devices which are active Le Audio devices.
 *
 * Peer devices has at maximum one instance of VCS and 0 or more instance of
 * VOCS. Android gets access to External Audio Outputs using appropriate ID.
 * Also each of the External Device has description
 * characteristic and Type which gives the application hint what it is a device.
 * Examples of such devices:
 *   External Output: 1 instance to controller ballance between set of devices
 *   External Output: each of 5.1 speaker set etc.
 */
class VolumeControlImpl : public VolumeControl {
 public:
  ~VolumeControlImpl() override = default;

  VolumeControlImpl(bluetooth::vc::VolumeControlCallbacks* callbacks,
                    const base::Closure& initCb)
      : gatt_if_(0), callbacks_(callbacks), latest_operation_id_(0) {
    BTA_GATTC_AppRegister(
        gattc_callback_static,
        base::Bind(
            [](const base::Closure& initCb, uint8_t client_id, uint8_t status) {
              if (status != GATT_SUCCESS) {
                log::error(
                    "Can't start Volume Control profile - no gatt clients "
                    "left!");
                return;
              }
              instance->gatt_if_ = client_id;
              initCb.Run();
            },
            initCb),
        true);
  }

  void StartOpportunisticConnect(const RawAddress& address) {
    /* Oportunistic works only for direct connect,
     * but in fact this is background connect
     */
    log::info(": {}", ADDRESS_TO_LOGGABLE_CSTR(address));
    BTA_GATTC_Open(gatt_if_, address, BTM_BLE_DIRECT_CONNECTION, true);
  }

  void Connect(const RawAddress& address) override {
    log::info(": {}", ADDRESS_TO_LOGGABLE_CSTR(address));

    auto device = volume_control_devices_.FindByAddress(address);
    if (!device) {
      volume_control_devices_.Add(address, true);
    } else {
      device->connecting_actively = true;

      if (device->IsConnected()) {
        log::warn("address={}, connection_id={} already connected.",
                  ADDRESS_TO_LOGGABLE_STR(address), device->connection_id);

        if (device->IsReady()) {
          callbacks_->OnConnectionState(ConnectionState::CONNECTED,
                                        device->address);
        } else {
          OnGattConnected(GATT_SUCCESS, device->connection_id, gatt_if_,
                          device->address, BT_TRANSPORT_LE, GATT_MAX_MTU_SIZE);
        }
        return;
      }
    }

    StartOpportunisticConnect(address);
  }

  void AddFromStorage(const RawAddress& address) {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));
    volume_control_devices_.Add(address, false);
    StartOpportunisticConnect(address);
  }

  void OnGattConnected(tGATT_STATUS status, uint16_t connection_id,
                       tGATT_IF /*client_if*/, RawAddress address,
                       tBT_TRANSPORT transport, uint16_t /*mtu*/) {
    log::info("{}, conn_id=0x{:04x}, transport={}, status={}(0x{:02x})",
              ADDRESS_TO_LOGGABLE_CSTR(address), connection_id,
              bt_transport_text(transport), gatt_status_text(status), status);

    if (transport != BT_TRANSPORT_LE) {
      log::warn("Only LE connection is allowed (transport {})",
                bt_transport_text(transport));
      BTA_GATTC_Close(connection_id);
      return;
    }

    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("Skipping unknown device, address={}",
                 ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    if (status != GATT_SUCCESS) {
      log::info("Failed to connect to Volume Control device");
      device_cleanup_helper(device, device->connecting_actively);
      return;
    }

    device->connection_id = connection_id;

    /* Make sure to remove device from background connect.
     * It will be added back if needed, when device got disconnected
     */
    BTA_GATTC_CancelOpen(gatt_if_, address, false);

    if (device->IsEncryptionEnabled()) {
      OnEncryptionComplete(address, BTM_SUCCESS);
      return;
    }

    if (!device->EnableEncryption()) {
      log::error("Link key is not known for {}, disconnect profile",
                 ADDRESS_TO_LOGGABLE_CSTR(address));
      device->Disconnect(gatt_if_);
    }
  }

  void OnEncryptionComplete(const RawAddress& address, uint8_t success) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("Skipping unknown device {}",
                 ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    if (success != BTM_SUCCESS) {
      log::error("encryption failed status: {}", int{success});
      // If the encryption failed, do not remove the device.
      // Disconnect only, since the Android will try to re-enable encryption
      // after disconnection
      device_cleanup_helper(device, device->connecting_actively);
      return;
    }

    log::info("{} status: {}", ADDRESS_TO_LOGGABLE_STR(address), success);

    if (device->HasHandles()) {
      device->EnqueueInitialRequests(gatt_if_, chrc_read_callback_static,
                                     OnGattWriteCccStatic);

    } else {
      BTA_GATTC_ServiceSearchRequest(device->connection_id,
                                     &kVolumeControlUuid);
    }
  }

  void ClearDeviceInformationAndStartSearch(VolumeControlDevice* device) {
    if (!device) {
      log::error("Device is null");
      return;
    }

    log::info("address={}", ADDRESS_TO_LOGGABLE_CSTR(device->address));
    if (device->known_service_handles_ == false) {
      log::info("Device already is waiting for new services");
      return;
    }

    std::vector<RawAddress> devices = {device->address};
    device->DeregisterNotifications(gatt_if_);

    RemovePendingVolumeControlOperations(devices,
                                         bluetooth::groups::kGroupUnknown);
    device->ResetHandles();
    BTA_GATTC_ServiceSearchRequest(device->connection_id, &kVolumeControlUuid);
  }

  void OnServiceChangeEvent(const RawAddress& address) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("Skipping unknown device {}",
                 ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    ClearDeviceInformationAndStartSearch(device);
  }

  void OnServiceDiscDoneEvent(const RawAddress& address) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("Skipping unknown device {}",
                 ADDRESS_TO_LOGGABLE_STR(address));
      return;
    }

    if (device->known_service_handles_ == false) {
      BTA_GATTC_ServiceSearchRequest(device->connection_id,
                                     &kVolumeControlUuid);
    }
  }

  void OnServiceSearchComplete(uint16_t connection_id, tGATT_STATUS status) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByConnId(connection_id);
    if (!device) {
      log::error("Skipping unknown device, connection_id={}",
                 loghex(connection_id));
      return;
    }

    /* Known device, nothing to do */
    if (device->IsReady()) return;

    if (status != GATT_SUCCESS) {
      /* close connection and report service discovery complete with error */
      log::error("Service discovery failed");
      device_cleanup_helper(device, device->connecting_actively);
      return;
    }

    if (!device->IsEncryptionEnabled()) {
      log::warn("Device not yet bonded - waiting for encryption");
      return;
    }

    bool success = device->UpdateHandles();
    if (!success) {
      log::error("Incomplete service database");
      device_cleanup_helper(device, device->connecting_actively);
      return;
    }

    device->EnqueueInitialRequests(gatt_if_, chrc_read_callback_static,
                                   OnGattWriteCccStatic);
  }

  void OnCharacteristicValueChanged(uint16_t conn_id, tGATT_STATUS status,
                                    uint16_t handle, uint16_t len,
                                    uint8_t* value, void* data,
                                    bool is_notification) {
    VolumeControlDevice* device = volume_control_devices_.FindByConnId(conn_id);
    if (!device) {
      log::info("unknown conn_id={}", loghex(conn_id));
      return;
    }

    if (status != GATT_SUCCESS) {
      log::info("status=0x{:02x}", static_cast<int>(status));
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->address));
        ClearDeviceInformationAndStartSearch(device);
      }
      return;
    }

    if (handle == device->volume_state_handle) {
      OnVolumeControlStateReadOrNotified(device, len, value, is_notification);
      verify_device_ready(device, handle);
      return;
    }
    if (handle == device->volume_flags_handle) {
      OnVolumeControlFlagsChanged(device, len, value);
      verify_device_ready(device, handle);
      return;
    }

    const gatt::Service* service = BTA_GATTC_GetOwningService(conn_id, handle);
    if (service == nullptr) return;

    VolumeOffset* offset =
        device->audio_offsets.FindByServiceHandle(service->handle);
    if (offset != nullptr) {
      if (handle == offset->state_handle) {
        OnExtAudioOutStateChanged(device, offset, len, value);
      } else if (handle == offset->audio_location_handle) {
        OnExtAudioOutLocationChanged(device, offset, len, value);
      } else if (handle == offset->audio_descr_handle) {
        OnOffsetOutputDescChanged(device, offset, len, value);
      } else {
        log::error("unknown offset handle={}", loghex(handle));
        return;
      }

      verify_device_ready(device, handle);
      return;
    }

    log::error("unknown handle={}", loghex(handle));
  }

  void OnNotificationEvent(uint16_t conn_id, uint16_t handle, uint16_t len,
                           uint8_t* value) {
    log::info("handle={}", loghex(handle));
    OnCharacteristicValueChanged(conn_id, GATT_SUCCESS, handle, len, value,
                                 nullptr, true);
  }

  void VolumeControlReadCommon(uint16_t conn_id, uint16_t handle) {
    BtaGattQueue::ReadCharacteristic(conn_id, handle, chrc_read_callback_static,
                                     nullptr);
  }

  void HandleAutonomusVolumeChange(VolumeControlDevice* device,
                                   bool is_volume_change, bool is_mute_change) {
    DLOG(INFO) << __func__ << ADDRESS_TO_LOGGABLE_STR(device->address)
               << " is volume change: " << is_volume_change
               << " is mute change: " << is_mute_change;

    if (!is_volume_change && !is_mute_change) {
      log::error("Autonomous change but volume and mute did not changed.");
      return;
    }

    auto csis_api = CsisClient::Get();
    if (!csis_api) {
      DLOG(INFO) << __func__ << " Csis is not available";
      callbacks_->OnVolumeStateChanged(device->address, device->volume,
                                       device->mute, true);
      return;
    }

    auto group_id =
        csis_api->GetGroupId(device->address, le_audio::uuid::kCapServiceUuid);
    if (group_id == bluetooth::groups::kGroupUnknown) {
      DLOG(INFO) << __func__ << " No group for device "
                 << ADDRESS_TO_LOGGABLE_STR(device->address);
      callbacks_->OnVolumeStateChanged(device->address, device->volume,
                                       device->mute, true);
      return;
    }

    auto devices = csis_api->GetDeviceList(group_id);
    for (auto it = devices.begin(); it != devices.end();) {
      auto dev = volume_control_devices_.FindByAddress(*it);
      if (!dev || !dev->IsConnected() || (dev->address == device->address)) {
        it = devices.erase(it);
      } else {
        it++;
      }
    }

    if (devices.empty() && (is_volume_change || is_mute_change)) {
      log::info("No more devices in the group right now");
      callbacks_->OnGroupVolumeStateChanged(group_id, device->volume,
                                            device->mute, true);
      return;
    }

    if (is_volume_change) {
      std::vector<uint8_t> arg({device->volume});
      PrepareVolumeControlOperation(devices, group_id, true,
                                    kControlPointOpcodeSetAbsoluteVolume, arg);
    }

    if (is_mute_change) {
      std::vector<uint8_t> arg;
      uint8_t opcode =
          device->mute ? kControlPointOpcodeMute : kControlPointOpcodeUnmute;
      PrepareVolumeControlOperation(devices, group_id, true, opcode, arg);
    }

    StartQueueOperation();
  }

  void OnVolumeControlStateReadOrNotified(VolumeControlDevice* device,
                                          uint16_t len, uint8_t* value,
                                          bool is_notification) {
    if (len != 3) {
      log::info("malformed len={}", loghex(len));
      return;
    }

    uint8_t vol;
    uint8_t mute;
    uint8_t* pp = value;
    STREAM_TO_UINT8(vol, pp);
    STREAM_TO_UINT8(mute, pp);
    STREAM_TO_UINT8(device->change_counter, pp);

    bool is_volume_change = (device->volume != vol);
    device->volume = vol;

    bool is_mute_change = (device->mute != mute);
    device->mute = mute;

    log::info("volume {} mute {} change_counter {}", loghex(device->volume),
              loghex(device->mute), loghex(device->change_counter));

    if (!device->IsReady()) {
      log::info("Device: {} is not ready yet.",
                ADDRESS_TO_LOGGABLE_CSTR(device->address));
      return;
    }

    /* This is just a read, send single notification */
    if (!is_notification) {
      callbacks_->OnVolumeStateChanged(device->address, device->volume,
                                       device->mute, false);
      return;
    }

    auto addr = device->address;
    auto op = find_if(ongoing_operations_.begin(), ongoing_operations_.end(),
                      [addr](auto& operation) {
                        auto it = find(operation.devices_.begin(),
                                       operation.devices_.end(), addr);
                        return it != operation.devices_.end();
                      });
    if (op == ongoing_operations_.end()) {
      DLOG(INFO) << __func__ << " Could not find operation id for device: "
                 << ADDRESS_TO_LOGGABLE_STR(device->address)
                 << ". Autonomus change";
      HandleAutonomusVolumeChange(device, is_volume_change, is_mute_change);
      return;
    }

    DLOG(INFO) << __func__ << " operation found: " << op->operation_id_
               << " for group id: " << op->group_id_;

    /* Received notification from the device we do expect */
    auto it = find(op->devices_.begin(), op->devices_.end(), device->address);
    op->devices_.erase(it);
    if (!op->devices_.empty()) {
      DLOG(INFO) << __func__ << " wait for more responses for operation_id: "
                 << op->operation_id_;
      return;
    }

    if (op->IsGroupOperation()) {
      callbacks_->OnGroupVolumeStateChanged(op->group_id_, device->volume,
                                            device->mute, op->is_autonomous_);
    } else {
      /* op->is_autonomous_ will always be false,
         since we only make it true for group operations */
      callbacks_->OnVolumeStateChanged(device->address, device->volume,
                                       device->mute, false);
    }

    ongoing_operations_.erase(op);
    StartQueueOperation();
  }

  void OnVolumeControlFlagsChanged(VolumeControlDevice* device, uint16_t len,
                                   uint8_t* value) {
    device->flags = *value;

    log::info("flags {}", loghex(device->flags));
  }

  void OnExtAudioOutStateChanged(VolumeControlDevice* device,
                                 VolumeOffset* offset, uint16_t len,
                                 uint8_t* value) {
    if (len != 3) {
      log::info("malformed len={}", loghex(len));
      return;
    }

    uint8_t* pp = value;
    STREAM_TO_UINT16(offset->offset, pp);
    STREAM_TO_UINT8(offset->change_counter, pp);

    log::info("{}", base::HexEncode(value, len));
    log::info("id: {} offset: {} counter: {}", loghex(offset->id),
              loghex(offset->offset), loghex(offset->change_counter));

    if (!device->IsReady()) {
      log::info("Device: {} is not ready yet.",
                ADDRESS_TO_LOGGABLE_CSTR(device->address));
      return;
    }

    callbacks_->OnExtAudioOutVolumeOffsetChanged(device->address, offset->id,
                                                 offset->offset);
  }

  void OnExtAudioOutLocationChanged(VolumeControlDevice* device,
                                    VolumeOffset* offset, uint16_t len,
                                    uint8_t* value) {
    if (len != 4) {
      log::info("malformed len={}", loghex(len));
      return;
    }

    uint8_t* pp = value;
    STREAM_TO_UINT32(offset->location, pp);

    log::info("{}", base::HexEncode(value, len));
    log::info("id {}location {}", loghex(offset->id), loghex(offset->location));

    if (!device->IsReady()) {
      log::info("Device: {} is not ready yet.",
                ADDRESS_TO_LOGGABLE_CSTR(device->address));
      return;
    }

    callbacks_->OnExtAudioOutLocationChanged(device->address, offset->id,
                                             offset->location);
  }

  void OnExtAudioOutCPWrite(uint16_t connection_id, tGATT_STATUS status,
                            uint16_t handle, void* /*data*/) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByConnId(connection_id);
    if (!device) {
      log::error("Skipping unknown device disconnect, connection_id={}",
                 loghex(connection_id));
      return;
    }

    log::info("Offset Control Point write response handle{} status: {}",
              loghex(handle), loghex((int)(status)));

    /* TODO Design callback API to notify about changes */
  }

  void OnOffsetOutputDescChanged(VolumeControlDevice* device,
                                 VolumeOffset* offset, uint16_t len,
                                 uint8_t* value) {
    std::string description = std::string(value, value + len);
    if (!base::IsStringUTF8(description)) description = "<invalid utf8 string>";

    log::info("{}", description);

    if (!device->IsReady()) {
      log::info("Device: {} is not ready yet.",
                ADDRESS_TO_LOGGABLE_CSTR(device->address));
      return;
    }

    callbacks_->OnExtAudioOutDescriptionChanged(device->address, offset->id,
                                                std::move(description));
  }

  void OnGattWriteCcc(uint16_t connection_id, tGATT_STATUS status,
                      uint16_t handle, uint16_t len, const uint8_t* value,
                      void* /*data*/) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByConnId(connection_id);
    if (!device) {
      log::info("unknown connection_id={}", loghex(connection_id));
      BtaGattQueue::Clean(connection_id);
      return;
    }

    if (status != GATT_SUCCESS) {
      if (status == GATT_DATABASE_OUT_OF_SYNC) {
        log::info("Database out of sync for {}, conn_id: 0x{:04x}",
                  ADDRESS_TO_LOGGABLE_CSTR(device->address), connection_id);
        ClearDeviceInformationAndStartSearch(device);
      } else {
        log::error(
            "Failed to register for notification: 0x{:04x}, status 0x{:02x}",
            handle, status);
        device_cleanup_helper(device, true);
      }
      return;
    }

    log::info("Successfully registered on ccc: 0x{:04x}, device: {}", handle,
              ADDRESS_TO_LOGGABLE_CSTR(device->address));

    verify_device_ready(device, handle);
  }

  static void OnGattWriteCccStatic(uint16_t connection_id, tGATT_STATUS status,
                                   uint16_t handle, uint16_t len,
                                   const uint8_t* value, void* data) {
    if (!instance) {
      log::error("No instance={}", handle);
      return;
    }

    instance->OnGattWriteCcc(connection_id, status, handle, len, value, data);
  }

  void Dump(int fd) {
    dprintf(fd, "APP ID: %d\n", gatt_if_);
    volume_control_devices_.DebugDump(fd);
  }

  void Disconnect(const RawAddress& address) override {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));

    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::warn("Device not connected to profile {}",
                ADDRESS_TO_LOGGABLE_CSTR(address));
      callbacks_->OnConnectionState(ConnectionState::DISCONNECTED, address);
      return;
    }

    log::info("GAP_EVT_CONN_CLOSED: {}",
              ADDRESS_TO_LOGGABLE_STR(device->address));
    device->connecting_actively = false;
    device_cleanup_helper(device, true);
  }

  void Remove(const RawAddress& address) override {
    log::info("{}", ADDRESS_TO_LOGGABLE_CSTR(address));

    /* Removes all registrations for connection. */
    BTA_GATTC_CancelOpen(gatt_if_, address, false);

    Disconnect(address);
  }

  void OnGattDisconnected(uint16_t connection_id, tGATT_IF /*client_if*/,
                          RawAddress remote_bda, tGATT_DISCONN_REASON reason) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByConnId(connection_id);
    if (!device) {
      log::error("Skipping unknown device disconnect, connection_id={}",
                 loghex(connection_id));
      return;
    }

    if (!device->IsConnected()) {
      log::error(
          "Skipping disconnect of the already disconnected device, "
          "connection_id={}",
          loghex(connection_id));
      return;
    }

    bool notify = device->IsReady() || device->connecting_actively;
    device_cleanup_helper(device, notify);

    if (reason != GATT_CONN_TERMINATE_LOCAL_HOST &&
        device->connecting_actively) {
      StartOpportunisticConnect(remote_bda);
    }
  }

  void RemoveDeviceFromOperationList(const RawAddress& addr, int operation_id) {
    auto op = find_if(ongoing_operations_.begin(), ongoing_operations_.end(),
                      [operation_id](auto& operation) {
                        return operation.operation_id_ == operation_id;
                      });

    if (op == ongoing_operations_.end()) {
      log::error("Could not find operation id: {}", operation_id);
      return;
    }

    auto it = find(op->devices_.begin(), op->devices_.end(), addr);
    if (it != op->devices_.end()) {
      op->devices_.erase(it);
      if (op->devices_.empty()) {
        ongoing_operations_.erase(op);
        StartQueueOperation();
      }
      return;
    }
  }

  void RemovePendingVolumeControlOperations(std::vector<RawAddress>& devices,
                                            int group_id) {
    for (auto op = ongoing_operations_.begin();
         op != ongoing_operations_.end();) {
      // We only remove operations that don't affect the mute field.
      if (op->IsStarted() ||
          (op->opcode_ != kControlPointOpcodeSetAbsoluteVolume &&
           op->opcode_ != kControlPointOpcodeVolumeUp &&
           op->opcode_ != kControlPointOpcodeVolumeDown)) {
        op++;
        continue;
      }
      if (group_id != bluetooth::groups::kGroupUnknown &&
          op->group_id_ == group_id) {
        op = ongoing_operations_.erase(op);
        continue;
      }
      for (auto const& addr : devices) {
        auto it = find(op->devices_.begin(), op->devices_.end(), addr);
        if (it != op->devices_.end()) {
          op->devices_.erase(it);
        }
      }
      if (op->devices_.empty()) {
        op = ongoing_operations_.erase(op);
      } else {
        op++;
      }
    }
  }

  void OnWriteControlResponse(uint16_t connection_id, tGATT_STATUS status,
                              uint16_t handle, void* data) {
    VolumeControlDevice* device =
        volume_control_devices_.FindByConnId(connection_id);
    if (!device) {
      log::error("Skipping unknown device disconnect, connection_id={}",
                 loghex(connection_id));
      return;
    }

    log::info("Write response handle: {} status: {}", loghex(handle),
              loghex((int)(status)));

    if (status == GATT_SUCCESS) return;

    /* In case of error, remove device from the tracking operation list */
    RemoveDeviceFromOperationList(device->address, PTR_TO_INT(data));

    if (status == GATT_DATABASE_OUT_OF_SYNC) {
      log::info("Database out of sync for {}",
                ADDRESS_TO_LOGGABLE_CSTR(device->address));
      ClearDeviceInformationAndStartSearch(device);
    }
  }

  static void operation_callback(void* data) {
    instance->CancelVolumeOperation(PTR_TO_INT(data));
  }

  void StartQueueOperation(void) {
    log::info("");
    if (ongoing_operations_.empty()) {
      return;
    };

    auto op = &ongoing_operations_.front();

    log::info("operation_id: {}", op->operation_id_);

    if (op->IsStarted()) {
      log::info("wait until operation {} is complete", op->operation_id_);
      return;
    }

    op->Start();

    alarm_set_on_mloop(op->operation_timeout_, 3000, operation_callback,
                       INT_TO_PTR(op->operation_id_));
    devices_control_point_helper(
        op->devices_, op->opcode_,
        op->arguments_.size() == 0 ? nullptr : &(op->arguments_));
  }

  void CancelVolumeOperation(int operation_id) {
    log::info("canceling operation_id: {}", operation_id);

    auto op = find_if(
        ongoing_operations_.begin(), ongoing_operations_.end(),
        [operation_id](auto& it) { return it.operation_id_ == operation_id; });

    if (op == ongoing_operations_.end()) {
      log::error("Could not find operation_id: {}", operation_id);
      return;
    }

    /* Possibly close GATT operations */
    ongoing_operations_.erase(op);
    StartQueueOperation();
  }

  void PrepareVolumeControlOperation(std::vector<RawAddress> devices,
                                     int group_id, bool is_autonomous,
                                     uint8_t opcode,
                                     std::vector<uint8_t>& arguments) {
    log::debug(
        "num of devices: {}, group_id: {}, is_autonomous: {}  opcode: {}, arg "
        "size: {}",
        devices.size(), group_id, is_autonomous ? "true" : "false", opcode,
        arguments.size());

    if (std::find_if(ongoing_operations_.begin(), ongoing_operations_.end(),
                     [opcode, &devices, &arguments](const VolumeOperation& op) {
                       if (op.opcode_ != opcode) return false;
                       if (!std::equal(op.arguments_.begin(),
                                       op.arguments_.end(), arguments.begin()))
                         return false;
                       // Filter out all devices which have the exact operation
                       // already scheduled
                       devices.erase(
                           std::remove_if(devices.begin(), devices.end(),
                                          [&op](auto d) {
                                            return find(op.devices_.begin(),
                                                        op.devices_.end(),
                                                        d) != op.devices_.end();
                                          }),
                           devices.end());
                       return devices.empty();
                     }) == ongoing_operations_.end()) {
      ongoing_operations_.emplace_back(latest_operation_id_++, group_id,
                                       is_autonomous, opcode, arguments,
                                       devices);
    }
  }

  void MuteUnmute(std::variant<RawAddress, int> addr_or_group_id, bool mute) {
    std::vector<uint8_t> arg;

    uint8_t opcode = mute ? kControlPointOpcodeMute : kControlPointOpcodeUnmute;

    if (std::holds_alternative<RawAddress>(addr_or_group_id)) {
      VolumeControlDevice* dev = volume_control_devices_.FindByAddress(
          std::get<RawAddress>(addr_or_group_id));
      if (dev != nullptr) {
        log::debug("Address: {}: isReady: {}",
                   ADDRESS_TO_LOGGABLE_CSTR(dev->address),
                   dev->IsReady() ? "true" : "false");
        if (dev->IsReady() && (dev->mute != mute)) {
          std::vector<RawAddress> devices = {dev->address};
          PrepareVolumeControlOperation(
              devices, bluetooth::groups::kGroupUnknown, false, opcode, arg);
        }
      }
    } else {
      /* Handle group change */
      auto group_id = std::get<int>(addr_or_group_id);
      log::debug("group: {}", group_id);
      auto csis_api = CsisClient::Get();
      if (!csis_api) {
        log::error("Csis is not there");
        return;
      }

      auto devices = csis_api->GetDeviceList(group_id);
      if (devices.empty()) {
        log::error("group id: {} has no devices", group_id);
        return;
      }

      bool muteNotChanged = false;
      bool deviceNotReady = false;

      for (auto it = devices.begin(); it != devices.end();) {
        auto dev = volume_control_devices_.FindByAddress(*it);
        if (!dev) {
          it = devices.erase(it);
          continue;
        }

        if (!dev->IsReady() || (dev->mute == mute)) {
          it = devices.erase(it);
          muteNotChanged =
              muteNotChanged ? muteNotChanged : (dev->mute == mute);
          deviceNotReady = deviceNotReady ? deviceNotReady : !dev->IsReady();
          continue;
        }
        it++;
      }

      if (devices.empty()) {
        log::debug(
            "No need to update mute for group id: {} . muteNotChanged: {}, "
            "deviceNotReady: {}",
            group_id, muteNotChanged, deviceNotReady);
        return;
      }

      PrepareVolumeControlOperation(devices, group_id, false, opcode, arg);
    }

    StartQueueOperation();
  }

  void Mute(std::variant<RawAddress, int> addr_or_group_id) override {
    LOG_DEBUG();
    MuteUnmute(addr_or_group_id, true /* mute */);
  }

  void UnMute(std::variant<RawAddress, int> addr_or_group_id) override {
    LOG_DEBUG();
    MuteUnmute(addr_or_group_id, false /* mute */);
  }

  void SetVolume(std::variant<RawAddress, int> addr_or_group_id,
                 uint8_t volume) override {
    DLOG(INFO) << __func__ << " vol: " << +volume;

    std::vector<uint8_t> arg({volume});
    uint8_t opcode = kControlPointOpcodeSetAbsoluteVolume;

    if (std::holds_alternative<RawAddress>(addr_or_group_id)) {
      log::debug("Address: {}:", ADDRESS_TO_LOGGABLE_CSTR(
                                     std::get<RawAddress>(addr_or_group_id)));
      VolumeControlDevice* dev = volume_control_devices_.FindByAddress(
          std::get<RawAddress>(addr_or_group_id));
      if (dev != nullptr) {
        log::debug("Address: {}: isReady: {}",
                   ADDRESS_TO_LOGGABLE_CSTR(dev->address),
                   dev->IsReady() ? "true" : "false");
        if (dev->IsReady() && (dev->volume != volume)) {
          std::vector<RawAddress> devices = {dev->address};
          RemovePendingVolumeControlOperations(
              devices, bluetooth::groups::kGroupUnknown);
          PrepareVolumeControlOperation(
              devices, bluetooth::groups::kGroupUnknown, false, opcode, arg);
        }
      }
    } else {
      /* Handle group change */
      auto group_id = std::get<int>(addr_or_group_id);
      DLOG(INFO) << __func__ << " group: " << group_id;
      auto csis_api = CsisClient::Get();
      if (!csis_api) {
        log::error("Csis is not there");
        return;
      }

      auto devices = csis_api->GetDeviceList(group_id);
      if (devices.empty()) {
        log::error("group id: {} has no devices", group_id);
        return;
      }

      bool volumeNotChanged = false;
      bool deviceNotReady = false;

      for (auto it = devices.begin(); it != devices.end();) {
        auto dev = volume_control_devices_.FindByAddress(*it);
        if (!dev) {
          it = devices.erase(it);
          continue;
        }

        if (!dev->IsReady() || (dev->volume == volume)) {
          it = devices.erase(it);
          volumeNotChanged =
              volumeNotChanged ? volumeNotChanged : (dev->volume == volume);
          deviceNotReady = deviceNotReady ? deviceNotReady : !dev->IsReady();
          continue;
        }

        it++;
      }

      if (devices.empty()) {
        log::debug(
            "No need to update volume for group id: {} . volumeNotChanged: {}, "
            "deviceNotReady: {}",
            group_id, volumeNotChanged, deviceNotReady);
        return;
      }

      RemovePendingVolumeControlOperations(devices, group_id);
      PrepareVolumeControlOperation(devices, group_id, false, opcode, arg);
    }

    StartQueueOperation();
  }

  /* Methods to operate on Volume Control Offset Service (VOCS) */
  void GetExtAudioOutVolumeOffset(const RawAddress& address,
                                  uint8_t ext_output_id) override {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }

    device->GetExtAudioOutVolumeOffset(ext_output_id, chrc_read_callback_static,
                                       nullptr);
  }

  void SetExtAudioOutVolumeOffset(const RawAddress& address,
                                  uint8_t ext_output_id,
                                  int16_t offset_val) override {
    std::vector<uint8_t> arg(2);
    uint8_t* ptr = arg.data();
    UINT16_TO_STREAM(ptr, offset_val);
    ext_audio_out_control_point_helper(
        address, ext_output_id, kVolumeOffsetControlPointOpcodeSet, &arg);
  }

  void GetExtAudioOutLocation(const RawAddress& address,
                              uint8_t ext_output_id) override {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }

    device->GetExtAudioOutLocation(ext_output_id, chrc_read_callback_static,
                                   nullptr);
  }

  void SetExtAudioOutLocation(const RawAddress& address, uint8_t ext_output_id,
                              uint32_t location) override {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }

    device->SetExtAudioOutLocation(ext_output_id, location);
  }

  void GetExtAudioOutDescription(const RawAddress& address,
                                 uint8_t ext_output_id) override {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }

    device->GetExtAudioOutDescription(ext_output_id, chrc_read_callback_static,
                                      nullptr);
  }

  void SetExtAudioOutDescription(const RawAddress& address,
                                 uint8_t ext_output_id,
                                 std::string descr) override {
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }

    device->SetExtAudioOutDescription(ext_output_id, descr);
  }

  void CleanUp() {
    log::info("");
    volume_control_devices_.Disconnect(gatt_if_);
    volume_control_devices_.Clear();
    ongoing_operations_.clear();
    BTA_GATTC_AppDeregister(gatt_if_);
  }

 private:
  tGATT_IF gatt_if_;
  bluetooth::vc::VolumeControlCallbacks* callbacks_;
  VolumeControlDevices volume_control_devices_;

  /* Used to track volume control operations */
  std::list<VolumeOperation> ongoing_operations_;
  int latest_operation_id_;

  void verify_device_ready(VolumeControlDevice* device, uint16_t handle) {
    if (device->IsReady()) return;

    // VerifyReady sets the device_ready flag if all remaining GATT operations
    // are completed
    if (device->VerifyReady(handle)) {
      log::info("Outstanding reads completed.");

      callbacks_->OnDeviceAvailable(device->address,
                                    device->audio_offsets.Size());
      callbacks_->OnConnectionState(ConnectionState::CONNECTED,
                                    device->address);

      // once profile connected we can notify current states
      callbacks_->OnVolumeStateChanged(device->address, device->volume,
                                       device->mute, false);

      for (auto const& offset : device->audio_offsets.volume_offsets) {
        callbacks_->OnExtAudioOutVolumeOffsetChanged(device->address, offset.id,
                                                     offset.offset);
      }

      device->EnqueueRemainingRequests(gatt_if_, chrc_read_callback_static,
                                       OnGattWriteCccStatic);
    }
  }

  void device_cleanup_helper(VolumeControlDevice* device, bool notify) {
    device->Disconnect(gatt_if_);
    if (notify)
      callbacks_->OnConnectionState(ConnectionState::DISCONNECTED,
                                    device->address);
  }

  void devices_control_point_helper(std::vector<RawAddress>& devices,
                                    uint8_t opcode,
                                    const std::vector<uint8_t>* arg,
                                    int operation_id = -1) {
    volume_control_devices_.ControlPointOperation(
        devices, opcode, arg,
        [](uint16_t connection_id, tGATT_STATUS status, uint16_t handle,
           uint16_t len, const uint8_t* value, void* data) {
          if (instance)
            instance->OnWriteControlResponse(connection_id, status, handle,
                                             data);
        },
        INT_TO_PTR(operation_id));
  }

  void ext_audio_out_control_point_helper(const RawAddress& address,
                                          uint8_t ext_output_id, uint8_t opcode,
                                          const std::vector<uint8_t>* arg) {
    log::info("{} id={} op={}", ADDRESS_TO_LOGGABLE_STR(address),
              loghex(ext_output_id), loghex(opcode));
    VolumeControlDevice* device =
        volume_control_devices_.FindByAddress(address);
    if (!device) {
      log::error("no such device!");
      return;
    }
    device->ExtAudioOutControlPointOperation(
        ext_output_id, opcode, arg,
        [](uint16_t connection_id, tGATT_STATUS status, uint16_t handle,
           uint16_t len, const uint8_t* value, void* data) {
          if (instance)
            instance->OnExtAudioOutCPWrite(connection_id, status, handle, data);
        },
        nullptr);
  }

  void gattc_callback(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
    log::info("event = {}", static_cast<int>(event));

    if (p_data == nullptr) return;

    switch (event) {
      case BTA_GATTC_OPEN_EVT: {
        tBTA_GATTC_OPEN& o = p_data->open;
        OnGattConnected(o.status, o.conn_id, o.client_if, o.remote_bda,
                        o.transport, o.mtu);

      } break;

      case BTA_GATTC_CLOSE_EVT: {
        tBTA_GATTC_CLOSE& c = p_data->close;
        OnGattDisconnected(c.conn_id, c.client_if, c.remote_bda, c.reason);
      } break;

      case BTA_GATTC_SEARCH_CMPL_EVT:
        OnServiceSearchComplete(p_data->search_cmpl.conn_id,
                                p_data->search_cmpl.status);
        break;

      case BTA_GATTC_NOTIF_EVT: {
        tBTA_GATTC_NOTIFY& n = p_data->notify;
        if (!n.is_notify || n.len > GATT_MAX_ATTR_LEN) {
          log::error("rejected BTA_GATTC_NOTIF_EVT. is_notify={}, len={}",
                     n.is_notify, static_cast<int>(n.len));
          break;
        }
        OnNotificationEvent(n.conn_id, n.handle, n.len, n.value);
      } break;

      case BTA_GATTC_ENC_CMPL_CB_EVT: {
        uint8_t encryption_status;
        if (BTM_IsEncrypted(p_data->enc_cmpl.remote_bda, BT_TRANSPORT_LE)) {
          encryption_status = BTM_SUCCESS;
        } else {
          encryption_status = BTM_FAILED_ON_SECURITY;
        }
        OnEncryptionComplete(p_data->enc_cmpl.remote_bda, encryption_status);
      } break;

      case BTA_GATTC_SRVC_CHG_EVT:
        OnServiceChangeEvent(p_data->remote_bda);
        break;

      case BTA_GATTC_SRVC_DISC_DONE_EVT:
        OnServiceDiscDoneEvent(p_data->remote_bda);
        break;

      default:
        break;
    }
  }

  static void gattc_callback_static(tBTA_GATTC_EVT event, tBTA_GATTC* p_data) {
    if (instance) instance->gattc_callback(event, p_data);
  }

  static void chrc_read_callback_static(uint16_t conn_id, tGATT_STATUS status,
                                        uint16_t handle, uint16_t len,
                                        uint8_t* value, void* data) {
    if (instance)
      instance->OnCharacteristicValueChanged(conn_id, status, handle, len,
                                             value, data, false);
  }
};
}  // namespace

void VolumeControl::Initialize(bluetooth::vc::VolumeControlCallbacks* callbacks,
                               const base::Closure& initCb) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (instance) {
    log::error("Already initialized!");
    return;
  }

  instance = new VolumeControlImpl(callbacks, initCb);
}

bool VolumeControl::IsVolumeControlRunning() { return instance; }

VolumeControl* VolumeControl::Get(void) {
  CHECK(instance);
  return instance;
};

void VolumeControl::AddFromStorage(const RawAddress& address) {
  if (!instance) {
    log::error("Not initialized yet");
    return;
  }

  instance->AddFromStorage(address);
};

void VolumeControl::CleanUp() {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  if (!instance) {
    log::error("Not initialized!");
    return;
  }

  VolumeControlImpl* ptr = instance;
  instance = nullptr;

  ptr->CleanUp();

  delete ptr;
};

void VolumeControl::DebugDump(int fd) {
  std::scoped_lock<std::mutex> lock(instance_mutex);
  dprintf(fd, "Volume Control Manager:\n");
  if (instance) instance->Dump(fd);
  dprintf(fd, "\n");
}

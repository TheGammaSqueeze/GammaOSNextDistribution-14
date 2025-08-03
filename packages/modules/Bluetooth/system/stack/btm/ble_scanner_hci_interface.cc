/*
 * Copyright 2019 HIMSA II K/S - www.himsa.com.
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

#include "ble_scanner_hci_interface.h"

#include <base/functional/bind.h>
#include <base/logging.h>
#include <bluetooth/log.h>

#include "btm_api.h"
#include "device/include/controller.h"
#include "stack/include/bt_types.h"
#include "stack/include/hcimsgs.h"
#include "types/raw_address.h"

using namespace bluetooth;

namespace {
BleScannerHciInterface* instance = nullptr;

static void status_callback(base::Callback<void(uint8_t)> cb, uint8_t* data,
                            uint16_t len) {
  uint8_t status;

  LOG_ASSERT(len == 1) << "Received bad response length: " << len;
  STREAM_TO_UINT8(status, data);

  cb.Run(status);
}

static void status_handle_callback(base::Callback<void(uint8_t, uint16_t)> cb,
                                   uint8_t* data, uint16_t len) {
  uint8_t status;
  uint16_t handle = HCI_INVALID_HANDLE;

  LOG_ASSERT((len > 0) && (len < 4)) << "Received bad response length: " << len;
  uint8_t* pp = data;
  STREAM_TO_UINT8(status, pp);

  if (status == HCI_SUCCESS) {
    LOG_ASSERT(len == 3) << "Received bad response length: " << len;

    STREAM_TO_UINT16(handle, pp);
    handle = handle & 0x0EFF;

  } else {
    log::verbose("hci response error code: {}", int{status});
  }
  cb.Run(status, handle);
}

/**
 * BleScannerHciInterface allows the caller to sync to a periodic advertising
 * train and receive periodic advertising data events through a registered
 * observer's callbacks. It also provides a synchronisation transfer API,
 * including in-controller allow list support. The right feature-complete
 * interface implementation is chosen during the init phase based on the
 * controller's list of supported features.
 */
class BleScannerImplBase : public BleScannerHciInterface {
 public:
  void SetScanEventObserver(ScanEventObserver* observer) override {
    // TODO: Support multiple observers if ever needed.
    scan_event_observer = observer;
  }

  void PeriodicScanStart(uint8_t options, uint8_t set_id, uint8_t adv_addr_type,
                         const RawAddress& adv_addr, uint16_t skip_num,
                         uint16_t sync_timeout,
                         uint8_t sync_cte_type) override {
    btsnd_hcic_ble_periodic_advertising_create_sync(
        options, set_id, adv_addr_type, adv_addr, skip_num, sync_timeout,
        sync_cte_type);
  }

  void PeriodicScanCancelStart(status_cb command_complete) override {
    btsnd_hcic_ble_periodic_advertising_create_sync_cancel(
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicScanTerminate(uint16_t sync_handle,
                             status_cb command_complete) override {
    btsnd_hcic_ble_periodic_advertising_terminate_sync(
        sync_handle, base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicScanResultEvtEnable(uint16_t sync_handle, bool enable,
                                   status_cb command_complete) override {
    btsnd_hcic_ble_set_periodic_advertising_receive_enable(
        sync_handle, enable,
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicAdvertiserListGetSize(
      BleScannerHciInterface::list_size_cb command_complete) override {
    command_complete.Run(
        controller_get_interface()->get_ble_periodic_advertiser_list_size());
  }

  void PeriodicAdvertiserListAddDevice(uint8_t adv_addr_type,
                                       RawAddress& adv_addr, uint8_t set_id,
                                       status_cb command_complete) override {
    btsnd_hci_ble_add_device_to_periodic_advertiser_list(
        adv_addr_type, adv_addr, set_id,
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicAdvertiserListRemoveDevice(uint8_t adv_addr_type,
                                          RawAddress& adv_addr, uint8_t set_id,
                                          status_cb command_complete) override {
    btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
        adv_addr_type, adv_addr, set_id,
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicAdvertiserListClear(status_cb command_complete) override {
    btsnd_hci_ble_clear_periodic_advertiser_list(
        base::Bind(&status_callback, std::move(command_complete)));
  };

  void PeriodicAdvSyncTransfer(
      const RawAddress& bd_addr, uint16_t service_data, uint16_t sync_handle,
      BleScannerHciInterface::handle_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    btsnd_hcic_ble_periodic_advertising_sync_transfer(
        acl_handle, service_data, sync_handle,
        base::Bind(&status_handle_callback, std::move(command_complete)));
  }

  void PeriodicAdvSetInfoTransfer(const RawAddress& bd_addr,
                                  uint16_t service_data, uint8_t adv_handle,
                                  handle_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    btsnd_hcic_ble_periodic_advertising_set_info_transfer(
        acl_handle, service_data, adv_handle,
        base::Bind(&status_handle_callback, std::move(command_complete)));
  }

  void SetPeriodicAdvSyncTransferParams(const RawAddress& bd_addr, uint8_t mode,
                                        uint16_t skip, uint16_t sync_timeout,
                                        uint8_t cte_type, bool set_defaults,
                                        status_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    if (set_defaults)
      btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
          acl_handle, mode, skip, sync_timeout, cte_type,
          base::Bind(&status_callback, std::move(command_complete)));
    else
      btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
          acl_handle, mode, skip, sync_timeout, cte_type,
          base::Bind(&status_callback, std::move(command_complete)));
  }

  void OnPeriodicAdvSyncEstablished(uint8_t status, uint16_t sync_handle,
                                    uint8_t adv_sid, uint8_t adv_addr_type,
                                    RawAddress adv_addr, uint8_t adv_phy,
                                    uint16_t adv_interval,
                                    uint8_t adv_clock_accuracy) {
    if (scan_event_observer) {
      scan_event_observer->OnPeriodicScanEstablished(
          status, sync_handle, adv_sid, adv_addr_type, adv_addr, adv_phy,
          adv_interval, adv_clock_accuracy);
    }
  }

  void OnPeriodicScanResult(uint16_t sync_handle, uint8_t tx_power, int8_t rssi,
                            uint8_t cte_type, uint8_t pkt_data_status,
                            uint8_t pkt_data_len, const uint8_t* p_pkt_data) {
    // The observer should handle the caching and reassembly of the fragmented
    // packet.
    if (scan_event_observer) {
      scan_event_observer->OnPeriodicScanResult(sync_handle, tx_power, rssi,
                                                cte_type, pkt_data_status,
                                                pkt_data_len, p_pkt_data);
    }
  }

  void OnPeriodicSyncLost(uint16_t sync_handle) {
    if (scan_event_observer)
      scan_event_observer->OnPeriodicScanLost(sync_handle);
  }

 private:
  ScanEventObserver* scan_event_observer = nullptr;
};

class BleScannerListImpl : public virtual BleScannerImplBase {
  void PeriodicAdvertiserListAddDevice(uint8_t adv_addr_type,
                                       RawAddress& adv_addr, uint8_t set_id,
                                       status_cb command_complete) override {
    btsnd_hci_ble_add_device_to_periodic_advertiser_list(
        adv_addr_type, adv_addr, set_id,
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicAdvertiserListRemoveDevice(uint8_t adv_addr_type,
                                          RawAddress& adv_addr, uint8_t set_id,
                                          status_cb command_complete) override {
    btsnd_hci_ble_remove_device_from_periodic_advertiser_list(
        adv_addr_type, adv_addr, set_id,
        base::Bind(&status_callback, std::move(command_complete)));
  }

  void PeriodicAdvertiserListClear(status_cb command_complete) override {
    btsnd_hci_ble_clear_periodic_advertiser_list(
        base::Bind(&status_callback, std::move(command_complete)));
  };
};

class BleScannerSyncTransferImpl : public virtual BleScannerImplBase {
  void PeriodicAdvSyncTransfer(
      const RawAddress& bd_addr, uint16_t service_data, uint16_t sync_handle,
      BleScannerHciInterface::handle_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    btsnd_hcic_ble_periodic_advertising_sync_transfer(
        acl_handle, service_data, sync_handle,
        base::Bind(&status_handle_callback, std::move(command_complete)));
  }

  void PeriodicAdvSetInfoTransfer(const RawAddress& bd_addr,
                                  uint16_t service_data, uint8_t adv_handle,
                                  handle_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    btsnd_hcic_ble_periodic_advertising_set_info_transfer(
        acl_handle, service_data, adv_handle,
        base::Bind(&status_handle_callback, std::move(command_complete)));
  }

  void SetPeriodicAdvSyncTransferParams(const RawAddress& bd_addr, uint8_t mode,
                                        uint16_t skip, uint16_t sync_timeout,
                                        uint8_t cte_type, bool set_defaults,
                                        status_cb command_complete) override {
    uint16_t acl_handle = BTM_GetHCIConnHandle(bd_addr, BT_TRANSPORT_LE);

    if (acl_handle == HCI_INVALID_HANDLE) {
      log::error("Wrong mode: no LE link exist or LE not supported");
      return;
    }

    if (set_defaults)
      btsnd_hcic_ble_set_default_periodic_advertising_sync_transfer_params(
          acl_handle, mode, skip, sync_timeout, cte_type,
          base::Bind(&status_callback, std::move(command_complete)));
    else
      btsnd_hcic_ble_set_periodic_advertising_sync_transfer_params(
          acl_handle, mode, skip, sync_timeout, cte_type,
          base::Bind(&status_callback, std::move(command_complete)));
  }
};

class BleScannerCompleteImpl : public BleScannerListImpl,
                               public BleScannerSyncTransferImpl {
  // Not much to do here :)
};

}  // namespace

void BleScannerHciInterface::Initialize() {
  LOG_ASSERT(instance == nullptr) << "Was already initialized.";

  if ((controller_get_interface()->get_ble_periodic_advertiser_list_size()) &&
      (controller_get_interface()
           ->SupportsBlePeriodicAdvertisingSyncTransferSender())) {
    log::info("Advertiser list in controller can be used");
    log::info("Periodic Adv Sync Transfer Sender role is supported");
    instance = new BleScannerCompleteImpl();
  } else if (controller_get_interface()
                 ->SupportsBlePeriodicAdvertisingSyncTransferSender()) {
    log::info("Periodic Adv Sync Transfer Sender role is supported");
    instance = new BleScannerSyncTransferImpl();
  } else if (controller_get_interface()
                 ->get_ble_periodic_advertiser_list_size()) {
    log::info("Periodic Adv Sync Transfer Recipient role is supported");
    instance = new BleScannerListImpl();
  }
  // TODO: Implement periodic adv. sync. recipient role if ever needed.
}

BleScannerHciInterface* BleScannerHciInterface::Get() { return instance; }

void BleScannerHciInterface::CleanUp() {
  delete instance;
  instance = nullptr;
}

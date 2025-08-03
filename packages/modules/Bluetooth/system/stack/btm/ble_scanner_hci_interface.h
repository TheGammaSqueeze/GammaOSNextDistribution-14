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

#ifndef BLE_SCANNER_HCI_INTERFACE_H
#define BLE_SCANNER_HCI_INTERFACE_H

#include <base/functional/callback.h>

#include <vector>

#include "types/raw_address.h"

class BleScannerHciInterface {
 public:
  using status_cb = base::Callback<void(uint8_t /* status */)>;
  using list_size_cb = base::Callback<void(int8_t /* list_size */)>;
  using handle_cb =
      base::Callback<void(uint8_t /* status */, uint16_t /* adv_handle */)>;

  static void Initialize();
  static BleScannerHciInterface* Get();
  static void CleanUp();

  virtual ~BleScannerHciInterface() = default;

  class ScanEventObserver {
   public:
    virtual ~ScanEventObserver() = default;
    virtual void OnPeriodicScanResult(uint16_t sync_handle, uint8_t tx_power,
                                      int8_t rssi, uint8_t cte_type,
                                      uint8_t pkt_data_status,
                                      uint8_t pkt_data_len,
                                      const uint8_t* pkt_data) = 0;
    virtual void OnPeriodicScanEstablished(
        uint8_t status, uint16_t sync_handle, uint8_t set_id,
        uint8_t adv_addr_type, const RawAddress& adv_addr, uint8_t adv_phy,
        uint16_t adv_interval, uint8_t adv_clock_accuracy) = 0;
    virtual void OnPeriodicScanLost(uint16_t sync_handle) = 0;
  };

  virtual void SetScanEventObserver(ScanEventObserver* observer) = 0;

  /**
   * Used to synchronize with a periodic advertising train from an advertiser
   * and begin receiving periodic advertising packets.
   *
   * @param options bit 0: whether to use advertiser list, adv_sid,
   * adv_addr_type and adv_addr parameters are being used otherwise, bit 1:
   * whether reporting is initially disabled, all other bits: Reserved for
   * future use
   * @param adv_sid advertising set ID
   * @param adv_addr_type advertiser device address type
   * @param adv_addr advertiser device address
   * @param skip_num the maximum number of periodic advertising events that can
   * be skipped after a successful receive. Range: 0x0000 to 0x01F3.
   * @param sync_timeout synchronization timeout for the periodic advertising
   * train, Range: 0x000A to 0x4000, Time = N*10 ms, Range: 100 ms to 163.84s
   * @param sync_cte_type bit 0: do not sync to packets with an AoA Constant
   * Tone Extension, bit 1: do not sync to packets with an AoD Constant Tone
   * Extension with 1 μs slots, bit 2: do not sync to packets with an AoD
   * Constant Tone Extension with 2 μs slots, bit 3: do not sync to packets with
   * a type 3 Constant Tone Extension (currently reserved for future use),
   * bit 4: do not sync to packets without a Constant Tone Extension, all other
   * bits: reserved for future use.
   */
  virtual void PeriodicScanStart(uint8_t options, uint8_t set_id,
                                 uint8_t adv_addr_type,
                                 const RawAddress& adv_addr, uint16_t skip_num,
                                 uint16_t sync_timeout,
                                 uint8_t sync_cte_type) = 0;

  /**
   * Used to cancel the HCI_LE_Periodic_Advertising_Create_Sync command while it
   * is pending.
   *
   * @param cb status callback
   */
  virtual void PeriodicScanCancelStart(status_cb cb) = 0;

  /**
   * Used to stop reception of the periodic advertising train identified by the
   * Sync_Handle parameter.
   *
   * @param sync_handle synced advertising handle
   * @param cb status callback
   */
  virtual void PeriodicScanTerminate(uint16_t sync_handle, status_cb cb) = 0;

  /**
   * Enable or disable reports for the periodic advertising train defined by the
   * sync_handle.
   *
   * @param sync_handle synced advewrtising handle
   * @param enable whether enable or disable the advertising reports
   * @param cb  status callback
   */
  virtual void PeriodicScanResultEvtEnable(uint16_t sync_handle, bool enable,
                                           status_cb cb) = 0;

  /**
   * Used to add an entry, consisting of a single device address and SID, to the
   * Periodic Advertiser list stored in the Controller. Any additions to the
   * Periodic Advertiser list take effect immediately. If the entry is already
   * on the list, the Controller shall return the error code Invalid HCI Command
   * Parameters (0x12).
   *
   * @param adv_addr_type advertiser device address type
   * @param adv_addr advertiser device address
   * @param adv_sid advertising set ID
   * @param cb status callback
   */
  virtual void PeriodicAdvertiserListAddDevice(uint8_t adv_addr_type,
                                               RawAddress& adv_addr,
                                               uint8_t adv_sid,
                                               status_cb cb) = 0;
  /**
   * Remove one entry from the list of Periodic Advertisers stored in the
   * Controller. Removals from the Periodic Advertisers List take effect
   * immediately.
   *
   * @param adv_addr_type advertiser device address type
   * @param adv_addr advertiser device address
   * @param adv_sid advertising set ID
   * @param cb status callback
   */
  virtual void PeriodicAdvertiserListRemoveDevice(uint8_t adv_addr_type,
                                                  RawAddress& adv_addr,
                                                  uint8_t adv_sid,
                                                  status_cb cb) = 0;

  /**
   * Remove all entries from the list of Periodic Advertisers in the Controller.
   *
   * @param cb status callback
   */
  virtual void PeriodicAdvertiserListClear(status_cb cb) = 0;

  /**
   * Read the total number of Periodic Advertiser list entries that can be
   * stored in the Controller.
   *
   * @param cb status and advertiser list size callback
   */
  virtual void PeriodicAdvertiserListGetSize(list_size_cb cb) = 0;

  /**
   * Send synchronization information about the periodic advertising train
   * identified by the sync_handle parameter to a connected device.
   *
   * @param bd_addr connected peer device address to whom sync data is
   * transferred
   * @param service_data a value provided by the Host
   * @param sync_handle synced advewrtising handle
   * @param cb status and connection handle callback
   */
  virtual void PeriodicAdvSyncTransfer(const RawAddress& bd_addr,
                                       uint16_t service_data,
                                       uint16_t sync_handle, handle_cb cb) = 0;

  /**
   * Send synchronization information about the periodic advertising in an
   * advertising set to a connected device.
   *
   * @param bd_addr connected peer device address to whom set info is
   * transferred
   * @param service_data a value provided by the Host
   * @param sync_handle synced advertising handle
   * @param cb status and connection handle callback
   */
  virtual void PeriodicAdvSetInfoTransfer(const RawAddress& bd_addr,
                                          uint16_t service_data,
                                          uint8_t sync_handle,
                                          handle_cb cb) = 0;

  /**
   * Specify how the Controller will process periodic advertising
   * synchronization information received from the device identified by the
   * bd_addr
   *
   * @param bd_addr connected peer device address who transfers the sync data
   * @param mode 0x00: No attempt is made to synchronize to the periodic
   * advertising and no HCI_LE_Periodic_Advertising_Sync_Transfer_Received event
   * is sent to the Host. 0x01: An
   * HCI_LE_Periodic_Advertising_Sync_Transfer_Received event is sent to the
   * Host. HCI_LE_Periodic_Advertising_Report events will be disabled. 0x02: An
   * HCI_LE_Periodic_Advertising_Sync_Transfer_Received event is sent to the
   * Host. HCI_LE_Periodic_Advertising_Report events will be enabled. All other
   * values: Reserved for future use.
   * @param skip The number of periodic advertising packets that can be skipped
   * after a successful receive, Range: 0x0000 to 0x01F3
   * @param sync_timeout Synchronization timeout for the periodic advertising
   * train. Range: 0x000A to 0x4000. Time = N*10 ms. Time Range: 100 ms to
   * 163.84 s
   * @param cte_type bit 0: do not sync to packets with an AoA Constant Tone
   * Extension, bit 1: do not sync to packets with an AoD Constant Tone
   * Extension with 1 μs slots, bit 2: do not sync to packets with an AoD
   * Constant Tone Extension with 2 μs slots, bit 4: do not sync to packets
   * without a Constant Tone Extension, all other values: reserved for future
   * use.
   * @param set_defaults whether to send
   * HCI_LE_SET_DEFAULT_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAM or
   * HCI_LE_SET_PERIODIC_ADVERTISING_SYNC_TRANSFER_PARAM.
   * @param cb status callback
   */
  virtual void SetPeriodicAdvSyncTransferParams(const RawAddress& bd_addr,
                                                uint8_t mode, uint16_t skip,
                                                uint16_t sync_timeout,
                                                uint8_t cte_type,
                                                bool set_defaults,
                                                status_cb cb) = 0;

  static constexpr uint8_t kOptUseAdvertiserList = 0x01;
  static constexpr uint8_t kOptReportsInitiallyEnabled = 0x02;
};

#endif  // BLE_SCANNER_HCI_INTERFACE_H

/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
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

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "btm_iso_api_types.h"

namespace bluetooth {
namespace hci {
namespace iso_manager {
struct CigCallbacks {
  virtual ~CigCallbacks() = default;
  virtual void OnSetupIsoDataPath(uint8_t status, uint16_t conn_handle,
                                  uint8_t cig_id) = 0;
  virtual void OnRemoveIsoDataPath(uint8_t status, uint16_t conn_handle,
                                   uint8_t cig_id) = 0;
  virtual void OnIsoLinkQualityRead(
      uint8_t conn_handle, uint8_t cig_id, uint32_t txUnackedPackets,
      uint32_t txFlushedPackets, uint32_t txLastSubeventPackets,
      uint32_t retransmittedPackets, uint32_t crcErrorPackets,
      uint32_t rxUnreceivedPackets, uint32_t duplicatePackets) = 0;

  virtual void OnCisEvent(uint8_t event, void* data) = 0;
  virtual void OnCigEvent(uint8_t event, void* data) = 0;
};

struct BigCallbacks {
  virtual ~BigCallbacks() = default;
  virtual void OnSetupIsoDataPath(uint8_t status, uint16_t conn_handle,
                                  uint8_t big_id) = 0;
  virtual void OnRemoveIsoDataPath(uint8_t status, uint16_t conn_handle,
                                   uint8_t big_id) = 0;

  virtual void OnBigEvent(uint8_t event, void* data) = 0;
};
}  // namespace iso_manager

class IsoManager {
 public:
  IsoManager();
  IsoManager(const IsoManager&) = delete;
  IsoManager& operator=(const IsoManager&) = delete;

  virtual ~IsoManager();

  static IsoManager* GetInstance() {
    static IsoManager* instance = new IsoManager();
    return instance;
  }

  /**
   * Set CIG and CIS related callbacks
   *
   * <p> Shall be set by the Le Audio Unicaster implementation
   *
   * @param callbacks CigCallbacks implementation
   */
  virtual void RegisterCigCallbacks(iso_manager::CigCallbacks* callbacks) const;

  /**
   * Set BIG related callbacks
   *
   * <p> Shall be set by the Le Audio Broadcaster implementation
   *
   * @param callbacks BigCallbacks implementation
   */
  virtual void RegisterBigCallbacks(iso_manager::BigCallbacks* callbacks) const;

  /**
   * Set true when CIG or BIG is active, false when CIG or BIG is closed
   *
   * @param callback function takes bool as parameter and return void
   */
  virtual void RegisterOnIsoTrafficActiveCallback(void callback(bool)) const;

  /**
   * Creates connected isochronous group (CIG) according to given params.
   *
   * @param cig_id connected isochronous group id
   * @param cig_params CIG parameters
   */
  virtual void CreateCig(uint8_t cig_id,
                         struct iso_manager::cig_create_params cig_params);

  /**
   * Reconfigures connected isochronous group (CIG) according to given params.
   *
   * @param cig_id connected isochronous group id
   * @param cig_params CIG parameters
   */
  virtual void ReconfigureCig(uint8_t cig_id,
                              struct iso_manager::cig_create_params cig_params);

  /**
   * Initiates removing of connected isochronous group (CIG).
   *
   * @param cig_id connected isochronous group id
   * @param force do not check if CIG exist
   */
  virtual void RemoveCig(uint8_t cig_id, bool force = false);

  /**
   * Initiates creation of connected isochronous stream (CIS).
   *
   * @param conn_params A set of cis and acl connection handles
   */
  virtual void EstablishCis(
      struct iso_manager::cis_establish_params conn_params);

  /**
   * Initiates disconnection of connected isochronous stream (CIS).
   *
   * @param conn_handle CIS connection handle
   * @param reason HCI reason for disconnection
   */
  virtual void DisconnectCis(uint16_t conn_handle, uint8_t reason);

  /**
   * Initiates creation of isochronous data path for connected isochronous
   * stream.
   *
   * @param conn_handle handle of BIS or CIS connection
   * @param path_params iso data path parameters
   */
  virtual void SetupIsoDataPath(
      uint16_t conn_handle,
      struct iso_manager::iso_data_path_params path_params);

  /**
   * Initiates removal of isochronous data path for connected isochronous
   * stream.
   *
   * @param conn_handle handle of BIS or CIS connection
   * @param data_path_dir iso data path direction
   */
  virtual void RemoveIsoDataPath(uint16_t conn_handle, uint8_t data_path_dir);

  /**
   * Reads the ISO link quality. OnIsoLinkQualityRead callback is invoked only
   * if read is successful.
   *
   * @param conn_handle handle of ISO connection
   */
  virtual void ReadIsoLinkQuality(uint16_t conn_handle);

  /**
   * Sends iso data to the controller
   *
   * @param conn_handle handle of BIS or CIS connection
   * @param data data buffer. The ownership of data is not being transferred.
   * @param data_len data buffer length
   */
  virtual void SendIsoData(uint16_t conn_handle, const uint8_t* data,
                           uint16_t data_len);

  /**
   * Creates the Broadcast Isochronous Group
   *
   * @param big_id host assigned BIG identifier
   * @param big_params BIG parameters
   */
  virtual void CreateBig(uint8_t big_id,
                         struct iso_manager::big_create_params big_params);

  /**
   * Terminates the Broadcast Isochronous Group
   *
   * @param big_id host assigned BIG identifier
   * @param reason termination reason data
   */
  virtual void TerminateBig(uint8_t big_id, uint8_t reason);

  /* Below are defined handlers called by the legacy code in btu_hcif.cc */

  /**
   * Handles Iso Data packets from the controller
   *
   * @param p_msg raw data packet. The ownership of p_msg is not being
   * transferred.
   */
  virtual void HandleIsoData(void* p_msg);

  /**
   * Handles disconnect HCI event
   *
   * <p> This callback can be called with handles other than ISO connection
   * handles.
   *
   * @param conn_handle connection handle
   * @param reason HCI reason for disconnection
   */
  virtual void HandleDisconnect(uint16_t conn_handle, uint8_t reason);

  /**
   * Handles HCI event for the number of completed packets
   *
   * @param p raw packet buffer for the event. The ownership of p is not being
   * transferred.
   * @param evt_len event packet buffer length
   */
  virtual void HandleNumComplDataPkts(uint8_t* p, uint8_t evt_len);
  virtual void HandleGdNumComplDataPkts(uint16_t handle, uint16_t credits);

  /**
   * Handle CIS and BIG related HCI events
   *
   * @param sub_code ble subcode for the HCI event
   * @param params raw packet buffer for the event. The ownership of params is
   * not being transferred
   * @param length event packet buffer length
   */
  virtual void HandleHciEvent(uint8_t sub_code, uint8_t* params,
                              uint16_t length);

  /**
   * Starts the IsoManager module
   */
  void Start();

  /**
   * Stops the IsoManager module
   */
  void Stop();

  /**
   * Dumps the IsoManager module state
   */
  void Dump(int fd);

 private:
  struct impl;
  std::unique_ptr<impl> pimpl_;
};

}  // namespace hci
}  // namespace bluetooth

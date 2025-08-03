/*
 * Copyright 2021 The Android Open Source Project
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

#include <memory>

#include "hci/address_with_type.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace l2cap {
namespace le {

/**
 * This is the listener interface for link property callbacks.
 */
class LinkPropertyListener {
 public:
  virtual ~LinkPropertyListener() = default;

  /**
   * Invoked when an ACL link is connected.
   */
  virtual void OnLinkConnected(
      hci::AddressWithType /* remote */, uint16_t /* handle */, hci::Role /* my_role */) {}

  /**
   * Invoked when an ACL link is disconnected.
   */
  virtual void OnLinkDisconnected(hci::AddressWithType /* remote */) {}

  /**
   * Invoked when received remote version information for a given link
   */
  virtual void OnReadRemoteVersionInformation(
      hci::ErrorCode /* hci_status */,
      hci::AddressWithType /* remote */,
      uint8_t /* lmp_version */,
      uint16_t /* manufacturer_name */,
      uint16_t /* sub_version */) {}

  /**
   * Invoked when received connection update for a given link
   */
  virtual void OnConnectionUpdate(
      hci::AddressWithType /* remote */,
      uint16_t /* connection_interval */,
      uint16_t /* connection_latency */,
      uint16_t /* supervision_timeout */) {}

  /**
   * Invoked when received PHY update for a given link
   */
  virtual void OnPhyUpdate(
      hci::AddressWithType /* remote */, uint8_t /* tx_phy */, uint8_t /* rx_phy */) {}

  /**
   * Invoked when received data length exchange for a given link
   */
  virtual void OnDataLengthChange(
      hci::AddressWithType /* remote */,
      uint16_t /* tx_octets */,
      uint16_t /* tx_time */,
      uint16_t /* rx_octets */,
      uint16_t /* rx_time */) {}
};

}  // namespace le
}  // namespace l2cap
}  // namespace bluetooth

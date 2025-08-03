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

#include "hci/address.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace l2cap {
namespace classic {

/**
 * This is the listener interface for link property callbacks.
 */
class LinkPropertyListener {
 public:
  virtual ~LinkPropertyListener() = default;

  /**
   * Invoked when an ACL link is connected.
   */
  virtual void OnLinkConnected(hci::Address /* remote */, uint16_t /* handle */) {}

  /**
   * Invoked when an ACL link is disconnected.
   */
  virtual void OnLinkDisconnected(hci::Address /* remote */) {}

  /**
   * Invoked when received remote version information for a given link
   */
  virtual void OnReadRemoteVersionInformation(
      hci::ErrorCode /* hci_status */,
      hci::Address /* remote */,
      uint8_t /* lmp_version */,
      uint16_t /* manufacturer_name */,
      uint16_t /* sub_version */) {}

  /**
   * Invoked when received remote features and remote supported features for a
   * given link
   */
  virtual void OnReadRemoteSupportedFeatures(hci::Address /* remote */, uint64_t /* features */) {}

  /**
   * Invoked when received remote features and remote extended features for a
   * given link
   */
  virtual void OnReadRemoteExtendedFeatures(
      hci::Address /* remote */,
      uint8_t /* page_number */,
      uint8_t /* max_page_number */,
      uint64_t /* features */) {}

  /**
   * Invoked when received role change
   */
  virtual void OnRoleChange(
      hci::ErrorCode /* status */, hci::Address /* remote */, hci::Role /* role */) {}

  /**
   * Invoked when received clock offset
   */
  virtual void OnReadClockOffset(hci::Address /* remote */, uint16_t /* clock_offset */) {}

  /**
   * Invoked when received mode change
   */
  virtual void OnModeChange(
      hci::ErrorCode /* hci_status */,
      hci::Address /* remote */,
      hci::Mode /* mode */,
      uint16_t /* interval */) {}

  /**
   * Invoked when received sniff subrating
   */
  virtual void OnSniffSubrating(
      hci::ErrorCode /* hci_status */,
      hci::Address /* remote */,
      uint16_t /* max_tx_lat */,
      uint16_t /* max_rx_lat */,
      uint16_t /* min_remote_timeout */,
      uint16_t /* min_local_timeout */) {}
};

}  // namespace classic
}  // namespace l2cap
}  // namespace bluetooth

/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <optional>
#include <variant>

#include "hci/address_with_type.h"
#include "hci/le_security_interface.h"
#include "os/handler.h"
#include "os/queue.h"
#include "security/ecdh_keys.h"
#include "security/pairing_failure.h"
#include "security/smp_packets.h"
#include "security/ui.h"

namespace bluetooth {
namespace security {

struct DistributedKeys {
  /* LE Keys*/
  std::optional<hci::Octet16> remote_ltk;
  std::optional<uint16_t> remote_ediv;
  std::optional<std::array<uint8_t, 8>> remote_rand;
  std::optional<hci::AddressWithType> remote_identity_address;
  std::optional<hci::Octet16> remote_irk;
  std::optional<hci::Octet16> remote_signature_key;
  std::optional<hci::Octet16> remote_link_key; /* BR/EDR Keys */

  std::optional<hci::Octet16> local_ltk;
  std::optional<uint16_t> local_ediv;
  std::optional<std::array<uint8_t, 8>> local_rand;
  std::optional<hci::Octet16> local_signature_key;
};

/* This class represents the result of pairing, as returned from Pairing Handler */
struct PairingResult {
  hci::AddressWithType connection_address;
  DistributedKeys distributed_keys;
  uint8_t key_size;
  uint8_t security_level;
};

using PairingResultOrFailure = std::variant<PairingResult, PairingFailure>;

/* Data we use for Out Of Band Pairing */
struct MyOobData {
  /*  private key is just for this single pairing only, so it might be safe to
   * expose it to other parts of stack. It should not be exposed to upper
   * layers though */
  std::array<uint8_t, 32> private_key;
  EcdhPublicKey public_key;
  hci::Octet16 c;
  hci::Octet16 r;
};

/* This structure is filled and send to PairingHandlerLe to initiate the Pairing process with remote device */
struct InitialInformations {
  hci::Role my_role;
  hci::AddressWithType my_connection_address;

  hci::AddressWithType my_identity_address;
  hci::Octet16 my_identity_resolving_key;

  /* My capabilities, as in pairing request/response */
  struct {
    IoCapability io_capability;
    OobDataFlag oob_data_flag;
    uint8_t auth_req;
    uint8_t maximum_encryption_key_size;
    uint8_t initiator_key_distribution;
    uint8_t responder_key_distribution;
  } myPairingCapabilities;

  /* was it remote device that initiated the Pairing ? */
  bool remotely_initiated;
  uint16_t connection_handle;
  hci::AddressWithType remote_connection_address;
  std::string remote_name;

  /* contains pairing request, if the pairing was remotely initiated */
  std::optional<PairingRequestView> pairing_request;

  struct out_of_band_data {
    hci::Octet16 le_sc_c; /* LE Secure Connections Confirmation Value */
    hci::Octet16 le_sc_r; /* LE Secure Connections Random Value */

    hci::Octet16 security_manager_tk_value; /* OOB data for LE Legacy Pairing */
  };

  // If we received OOB data from remote device, this field contains it.
  std::optional<out_of_band_data> remote_oob_data;
  std::optional<MyOobData> my_oob_data;

  /* Used by Pairing Handler to present user with requests*/
  UI* user_interface;
  os::Handler* user_interface_handler;

  /* HCI interface to use */
  hci::LeSecurityInterface* le_security_interface;

  os::EnqueueBuffer<packet::BasePacketBuilder>* proper_l2cap_interface;
  os::Handler* l2cap_handler;

  /* Callback to execute once the Pairing process is finished */
  std::function<void(PairingResultOrFailure)> OnPairingFinished;
};

}  // namespace security
}  // namespace bluetooth

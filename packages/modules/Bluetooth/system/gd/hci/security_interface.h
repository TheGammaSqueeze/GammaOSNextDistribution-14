/*
 * Copyright 2019 The Android Open Source Project
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

#include "hci/command_interface.h"
#include "hci/hci_packets.h"

namespace bluetooth {
namespace hci {

constexpr hci::EventCode SecurityEvents[] = {
    hci::EventCode::ENCRYPTION_CHANGE,
    hci::EventCode::CHANGE_CONNECTION_LINK_KEY_COMPLETE,
    hci::EventCode::CENTRAL_LINK_KEY_COMPLETE,
    hci::EventCode::RETURN_LINK_KEYS,
    hci::EventCode::PIN_CODE_REQUEST,
    hci::EventCode::LINK_KEY_REQUEST,
    hci::EventCode::LINK_KEY_NOTIFICATION,
    hci::EventCode::ENCRYPTION_KEY_REFRESH_COMPLETE,
    hci::EventCode::IO_CAPABILITY_REQUEST,
    hci::EventCode::IO_CAPABILITY_RESPONSE,
    hci::EventCode::REMOTE_OOB_DATA_REQUEST,
    hci::EventCode::SIMPLE_PAIRING_COMPLETE,
    hci::EventCode::USER_PASSKEY_NOTIFICATION,
    hci::EventCode::KEYPRESS_NOTIFICATION,
    hci::EventCode::USER_CONFIRMATION_REQUEST,
    hci::EventCode::USER_PASSKEY_REQUEST,
};

typedef CommandInterface<SecurityCommandBuilder> SecurityInterface;

}  // namespace hci
}  // namespace bluetooth

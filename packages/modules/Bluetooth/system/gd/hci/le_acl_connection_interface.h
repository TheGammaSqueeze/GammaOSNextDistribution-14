/*
 * Copyright 2020 The Android Open Source Project
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

constexpr SubeventCode LeConnectionManagementEvents[] = {
    SubeventCode::CONNECTION_COMPLETE,
    SubeventCode::ENHANCED_CONNECTION_COMPLETE,
    SubeventCode::CONNECTION_UPDATE_COMPLETE,
    SubeventCode::PHY_UPDATE_COMPLETE,
    SubeventCode::DATA_LENGTH_CHANGE,
    SubeventCode::REMOTE_CONNECTION_PARAMETER_REQUEST,
    SubeventCode::LE_SUBRATE_CHANGE,
    // TODO implement callback for READ_REMOTE_FEATURES_COMPLETE
    // SubeventCode::READ_REMOTE_FEATURES_COMPLETE
};

typedef CommandInterface<AclCommandBuilder> LeAclConnectionInterface;

}  // namespace hci
}  // namespace bluetooth

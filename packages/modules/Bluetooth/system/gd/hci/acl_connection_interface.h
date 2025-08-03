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

constexpr EventCode AclConnectionEvents[] = {
    EventCode::CONNECTION_PACKET_TYPE_CHANGED,
    EventCode::ROLE_CHANGE,
    EventCode::CONNECTION_COMPLETE,
    EventCode::AUTHENTICATION_COMPLETE,
    EventCode::READ_CLOCK_OFFSET_COMPLETE,
    EventCode::MODE_CHANGE,
    EventCode::SNIFF_SUBRATING,
    EventCode::QOS_SETUP_COMPLETE,
    EventCode::FLOW_SPECIFICATION_COMPLETE,
    EventCode::FLUSH_OCCURRED,
    EventCode::READ_REMOTE_SUPPORTED_FEATURES_COMPLETE,
    EventCode::READ_REMOTE_EXTENDED_FEATURES_COMPLETE,
    EventCode::LINK_SUPERVISION_TIMEOUT_CHANGED,
};

typedef CommandInterface<AclCommandBuilder> AclConnectionInterface;

}  // namespace hci
}  // namespace bluetooth

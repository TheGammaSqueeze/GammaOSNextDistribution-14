/*
 * Copyright 2023 The Android Open Source Project
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

constexpr hci::SubeventCode DistanceMeasurementEvents[] = {
    hci::SubeventCode::LE_CS_TEST_END_COMPLETE,
    hci::SubeventCode::LE_CS_SUBEVENT_RESULT_CONTINUE,
    hci::SubeventCode::LE_CS_SUBEVENT_RESULT,
    hci::SubeventCode::LE_CS_PROCEDURE_ENABLE_COMPLETE,
    hci::SubeventCode::LE_CS_CONFIG_COMPLETE,
    hci::SubeventCode::LE_CS_SECURITY_ENABLE_COMPLETE,
    hci::SubeventCode::LE_CS_READ_REMOTE_FAE_TABLE_COMPLETE,
    hci::SubeventCode::LE_CS_READ_REMOTE_SUPPORTED_CAPABILITIES_COMPLETE,
};

typedef CommandInterface<DistanceMeasurementCommandBuilder> DistanceMeasurementInterface;

}  // namespace hci
}  // namespace bluetooth

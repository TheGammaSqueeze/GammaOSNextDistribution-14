/******************************************************************************
 *
 *  Copyright (C) 2022 The Android Open Source Project
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

#include <inttypes.h>

namespace bluetooth {
namespace hal {

#define HCI_OP_NOP 0x0000

#define MGMT_EV_SIZE_MAX 1024
#define MGMT_PKT_HDR_SIZE 6
struct mgmt_pkt {
  uint16_t opcode;
  uint16_t index;
  uint16_t len;
  uint8_t data[MGMT_EV_SIZE_MAX];
} __attribute__((packed));

#define MGMT_EV_COMMAND_COMPLETE 0x1
struct mgmt_ev_cmd_complete {
  uint16_t opcode;
  uint8_t status;
  uint8_t data[];
} __attribute__((packed));

#define MGMT_OP_GET_VS_OPCODE 0x0102
#define MGMT_VS_OPCODE_MSFT 0x0001
struct mgmt_cp_get_vs_opcode {
  uint16_t hci_id;
  uint16_t vendor_specification;
} __attribute__((packed));

struct mgmt_rp_get_vs_opcode {
  uint16_t hci_id;
  uint16_t opcode;
} __attribute__((packed));

#define MGMT_POLL_TIMEOUT_MS 2000

// This class provides an interface to interact with the kernel.
class Mgmt {
 public:
  uint16_t get_vs_opcode(uint16_t vendor_specification);
};

}  // namespace hal
}  // namespace bluetooth

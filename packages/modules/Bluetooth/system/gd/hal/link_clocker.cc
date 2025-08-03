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

#include "hal/link_clocker.h"

#include <algorithm>

namespace bluetooth::hal {

static constexpr uint16_t kInvalidConnectionHandle = 0xFFFF;

static class : public bluetooth::audio::asrc::ClockHandler {
  void OnEvent(uint32_t, int, int) override {}
} g_empty_handler;

static std::atomic<bluetooth::audio::asrc::ClockHandler*> g_nocp_iso_handler = &g_empty_handler;

static struct {
  std::mutex mutex;
  bluetooth::audio::asrc::ClockHandler* handler;
  struct {
    uint16_t connection_handle;
    uint16_t stream_cid;
  } links[2];
} g_credit_ind_handler = {.handler = &g_empty_handler, .links = {{}, {}}};

NocpIsoEvents::~NocpIsoEvents() {
  g_nocp_iso_handler = &g_empty_handler;
}

void NocpIsoEvents::Bind(bluetooth::audio::asrc::ClockHandler* handler) {
  g_nocp_iso_handler = handler;
}

L2capCreditIndEvents::~L2capCreditIndEvents() {
  std::lock_guard<std::mutex> guard(g_credit_ind_handler.mutex);
  g_credit_ind_handler.handler = &g_empty_handler;
  g_credit_ind_handler.links[0].connection_handle = kInvalidConnectionHandle;
  g_credit_ind_handler.links[1].connection_handle = kInvalidConnectionHandle;
}

void L2capCreditIndEvents::Bind(bluetooth::audio::asrc::ClockHandler* handler) {
  std::lock_guard<std::mutex> guard(g_credit_ind_handler.mutex);
  g_credit_ind_handler.handler = handler;
  g_credit_ind_handler.links[0].connection_handle = kInvalidConnectionHandle;
  g_credit_ind_handler.links[1].connection_handle = kInvalidConnectionHandle;
}

void L2capCreditIndEvents::Update(int link_id, uint16_t connection_handle, uint16_t stream_cid) {
  std::lock_guard<std::mutex> guard(g_credit_ind_handler.mutex);
  g_credit_ind_handler.links[link_id].connection_handle = connection_handle;
  g_credit_ind_handler.links[link_id].stream_cid = stream_cid;
}

LinkClocker::LinkClocker() : cig_id_(-1), cis_handle_(-1) {}

void LinkClocker::OnHciEvent(const HciPacket& packet) {
  const int HCI_CMD_SET_CIG_PARAMETERS = 0x2062;
  const int HCI_EVT_COMMAND_COMPLETE = 0x0e;
  const int HCI_EVT_NUMBER_OF_COMPLETED_PACKETS = 0x13;

  // HCI Event [Core 4.E.5.4.4]
  // |  [0]  Event Code
  // |  [1]  Parameter Total Length
  // | [2+]  Parameters

  if (packet.size() < 2) return;

  const uint8_t* payload = packet.data() + 2;
  size_t payload_length = std::min(size_t(packet[1]), packet.size() - 2);

  switch (packet[0]) {
      // HCI Command Complete Event [Core 4.E.7.7.14]
      // |    [0]  Num_HCI_Command_Packets, Ignored
      // | [1..2]  Command_Opcode, catch `HCI_LE_Set_CIG_Parameters`
      // |   [3+]  Return Parameters

    case HCI_EVT_COMMAND_COMPLETE: {
      if (payload_length < 3) return;

      int cmd_opcode = payload[1] | (payload[2] << 8);
      if (cmd_opcode != HCI_CMD_SET_CIG_PARAMETERS) return;

      const uint8_t* parameters = payload + 3;
      size_t parameters_length = payload_length - 3;

      // HCI LE Set CIG Parameters return parameters [4.E.7.8.97]
      // |    [0]  Status, 0 when OK
      // |    [1]  CIG_ID
      // |    [2]  CIS_Count
      // | [3..4]  Connection_Handle[0]

      if (parameters_length < 3) return;

      int status = parameters[0];
      int cig_id = parameters[1];
      int cis_count = parameters[2];

      if (status != 0) return;

      if (cig_id_ >= 0 && cis_handle_ >= 0 && cig_id_ != cig_id) {
        LOG_WARN("Multiple groups not supported");
        return;
      }

      cig_id_ = -1;
      cis_handle_ = -1;

      if (cis_count > 0 && parameters_length >= 5) {
        cig_id_ = cig_id;
        cis_handle_ = (parameters[3] | (parameters[4] << 8)) & 0xfff;
      }

      break;
    }

      // HCI Number Of Completed Packets event [Core 4.E.7.7.19]
      // | [0]  Num_Handles
      // | FOR each `Num_Handles` connection handles
      // | | [0..1]  Connection_Handle, catch the CIS Handle
      // | | [2..3]  Num_Completed_Packets

    case HCI_EVT_NUMBER_OF_COMPLETED_PACKETS: {
      if (payload_length < 1) return;

      int i, num_handles = payload[0];
      const uint8_t* item = payload + 1;
      if (payload_length < size_t(1 + 4 * num_handles)) return;

      for (i = 0; i < num_handles && ((item[0] | (item[1] << 8)) & 0xfff) != cis_handle_;
           i++, item += 4)
        ;
      if (i >= num_handles) return;

      auto timestamp = std::chrono::system_clock::now().time_since_epoch();
      unsigned timestamp_us =
          std::chrono::duration_cast<std::chrono::microseconds>(timestamp).count();
      int num_of_completed_packets = item[2] | (item[3] << 8);
      (*g_nocp_iso_handler).OnEvent(timestamp_us, 0, num_of_completed_packets);

      break;
    }
  }
}

/// Filter received L2CAP PDUs for Credit acknowledgments for the registered
/// L2CAP channels.
void LinkClocker::OnAclDataReceived(const HciPacket& packet) {
  const int L2CAP_LE_U_CID = 0x0005;
  const int L2CAP_FLOW_CONTROL_CREDIT_IND = 0x16;

  // HCI ACL Data Packets [4.E.5.4.2]
  // | [0..1]  Handle | PBF | BC
  // | [2..3]  Data Total Length
  // | [4+]    Data

  if (packet.size() < 4) return;

  uint16_t handle = packet[0] | (packet[1] << 8);
  int packet_boundary_flag = (handle >> 12) & 0x3;
  handle &= 0xfff;
  uint16_t data_total_length = std::min(size_t(packet[2] | (packet[3] << 8)), packet.size() - 4);
  const uint8_t* data = packet.data() + 4;

  if (data_total_length < 4 || packet_boundary_flag == 0b01 || packet_boundary_flag == 0b11) return;

  // L2CAP Signalling PDU Format [3.A.4]
  // | [0..1]  PDU Length
  // | [2..3]  Channel ID
  // | [4+]    PDU
  uint16_t pdu_length = std::min(data[0] | (data[1] << 8), data_total_length - 4);
  uint16_t channel_id = data[2] | (data[3] << 8);
  data += 4;

  if (channel_id != L2CAP_LE_U_CID) return;

  while (pdu_length >= 4) {
    // | FOR each command in the PDU
    // | | [0]     Command Code
    // | | [1]     Command Identifier
    // | | [2..3]  Data Length
    // | | [4+]    Data
    uint8_t command_code = data[0];
    uint16_t data_length = std::min(data[2] | (data[3] << 8), pdu_length - 4);

    if (command_code == L2CAP_FLOW_CONTROL_CREDIT_IND && data_length == 4) {
      // | L2CAP Flow Control Credit Ind [3.A.4.24]
      // | | [4..5]  CID
      // | | [6..7]  Credits
      uint16_t channel_id = data[4] | (data[5] << 8);
      uint16_t credits = data[6] | (data[7] << 8);

      auto timestamp = std::chrono::system_clock::now().time_since_epoch();
      unsigned timestamp_us =
          std::chrono::duration_cast<std::chrono::microseconds>(timestamp).count();

      {
        std::lock_guard<std::mutex> guard(g_credit_ind_handler.mutex);
        for (int link_id = 0; link_id < 2; link_id++) {
          auto const& link = g_credit_ind_handler.links[link_id];
          if (link.connection_handle == handle && link.stream_cid == channel_id) {
            g_credit_ind_handler.handler->OnEvent(timestamp_us, link_id, credits);
          }
        }
      }
    }

    data += data_length + 4;
    pdu_length -= data_length + 4;
  }
}

const ModuleFactory LinkClocker::Factory = ModuleFactory([]() { return new LinkClocker(); });

}  // namespace bluetooth::hal

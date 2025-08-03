/******************************************************************************
 *
 *  Copyright 2022 The Android Open Source Project
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

/*
 * TODO(b/249193511): Replace this MGMT interface with sockopt/ioctl.
 * This file will be replaced such that it is not optimized for now.
 */

#include "hal/mgmt.h"

#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <cerrno>

#include "common/init_flags.h"
#include "os/log.h"

namespace bluetooth {
namespace hal {

#define RETRY_ON_INTR(fn) \
  do {                    \
  } while ((fn) == -1 && errno == EINTR)

struct sockaddr_hci {
  sa_family_t hci_family;
  unsigned short hci_dev;
  unsigned short hci_channel;
};

constexpr static uint8_t BTPROTO_HCI = 1;
constexpr static uint16_t HCI_CHANNEL_CONTROL = 3;
constexpr static uint16_t HCI_DEV_NONE = 0xffff;

static int btsocket_open_mgmt(uint16_t hci) {
  int fd = socket(PF_BLUETOOTH, SOCK_RAW | SOCK_NONBLOCK, BTPROTO_HCI);
  if (fd < 0) {
    LOG_ERROR("Failed to open BT socket.");
    return -errno;
  }

  struct sockaddr_hci addr = {
      .hci_family = AF_BLUETOOTH,
      .hci_dev = HCI_DEV_NONE,
      .hci_channel = HCI_CHANNEL_CONTROL,
  };

  int ret = bind(fd, (struct sockaddr*)&addr, sizeof(addr));
  if (ret < 0) {
    LOG_ERROR("Failed to bind BT socket.");
    close(fd);
    return -errno;
  }

  return fd;
}

/*
 * Given a vendor specification, e.g., MSFT extension, this function returns
 * the vendor specific opcode.
 *
 * If the controller does not support MSFT extension or there are errors
 * or failures in writing/reading the MGMT socket, the return opcode would
 * be HCI_OP_NOP (0x0000).
 */
uint16_t Mgmt::get_vs_opcode(uint16_t vendor_specification) {
  int hci = bluetooth::common::InitFlags::GetAdapterIndex();
  int fd = btsocket_open_mgmt(hci);
  uint16_t ret_opcode = HCI_OP_NOP;

  if (fd < 0) {
    LOG_ERROR("Failed to open mgmt channel for hci %d, error= %d.", hci, fd);
    return ret_opcode;
  }

  struct mgmt_pkt ev;
  ev.opcode = MGMT_OP_GET_VS_OPCODE;
  ev.index = HCI_DEV_NONE;
  ev.len = sizeof(struct mgmt_cp_get_vs_opcode);

  struct mgmt_cp_get_vs_opcode* cp = reinterpret_cast<struct mgmt_cp_get_vs_opcode*>(ev.data);
  cp->hci_id = hci;
  cp->vendor_specification = MGMT_VS_OPCODE_MSFT;

  int ret;
  struct pollfd writable[1];
  writable[0].fd = fd;
  writable[0].events = POLLOUT;

  do {
    ret = poll(writable, 1, MGMT_POLL_TIMEOUT_MS);
    if (ret > 0) {
      RETRY_ON_INTR(ret = write(fd, &ev, MGMT_PKT_HDR_SIZE + ev.len));
      if (ret < 0) {
        LOG_ERROR("Failed to call MGMT opcode 0x%4.4x, errno %d", ev.opcode, -errno);
        close(fd);
        return ret_opcode;
      };
      break;
    } else if (ret < 0) {
      LOG_ERROR("msft poll ret %d errno %d", ret, -errno);
    }
  } while (ret > 0);

  if (ret <= 0) {
    LOG_INFO("Skip because mgmt socket is not writable: ev.opcode 0x%4.4x ret %d", ev.opcode, ret);
    close(fd);
    return ret_opcode;
  }

  struct pollfd fds[1];
  struct mgmt_pkt cc_ev;
  fds[0].fd = fd;
  fds[0].events = POLLIN;

  do {
    ret = poll(fds, 1, MGMT_POLL_TIMEOUT_MS);
    if (ret > 0) {
      if (fds[0].revents & POLLIN) {
        RETRY_ON_INTR(ret = read(fd, &cc_ev, sizeof(cc_ev)));
        if (ret < 0) {
          LOG_ERROR("Failed to read mgmt socket: %d", -errno);
          close(fd);
          return ret_opcode;
        }

        if (cc_ev.opcode == MGMT_EV_COMMAND_COMPLETE) {
          struct mgmt_ev_cmd_complete* cc = reinterpret_cast<struct mgmt_ev_cmd_complete*>(cc_ev.data);
          if (cc->opcode == ev.opcode && cc->status == 0) {
            struct mgmt_rp_get_vs_opcode* rp = reinterpret_cast<struct mgmt_rp_get_vs_opcode*>(cc->data);
            if (rp->hci_id == hci) {
              // If the controller supports the MSFT extension, the returned opcode
              // will not be HCI_OP_NOP.
              if (rp->opcode != HCI_OP_NOP) {
                ret_opcode = rp->opcode;
              }
              close(fd);
              return ret_opcode;
            }
          }
        }
      }
    } else if (ret == 0) {
      LOG_ERROR("Timeout while waiting for response of calling MGMT opcode: 0x%4.4x", ev.opcode);
      ret = -1;
    }
  } while (ret > 0);
  close(fd);
  return ret_opcode;
}

}  // namespace hal
}  // namespace bluetooth

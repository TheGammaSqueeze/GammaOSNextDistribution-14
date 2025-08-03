/*
 * Copyright 2022 The Android Open Source Project
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

#include <bluetooth/log.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <vector>

#include "btm_sco_hfp_hal.h"
#include "common/init_flags.h"
#include "hci/controller_interface.h"
#include "main/shim/entry.h"
#include "os/log.h"
#include "osi/include/properties.h"
#include "stack/include/hcimsgs.h"
#include "stack/include/sdpdefs.h"

using namespace bluetooth;
using bluetooth::legacy::hci::GetInterface;

namespace hfp_hal_interface {
namespace {
bool offload_supported = false;
bool offload_enabled = false;

struct mgmt_bt_codec {
  uint8_t codec;
  uint8_t packet_size;
  uint8_t data_path;
  uint32_t data_length;
  uint8_t data[];
} __attribute__((packed));

typedef struct cached_codec_info {
  struct bt_codec inner;
  uint8_t pkt_size;
} cached_codec_info;

std::vector<cached_codec_info> cached_codecs;

#define RETRY_ON_INTR(fn) \
  do {                    \
  } while ((fn) == -1 && errno == EINTR)

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

#define MGMT_OP_GET_SCO_CODEC_CAPABILITIES 0x0100
#define MGMT_SCO_CODEC_CVSD 0x1
#define MGMT_SCO_CODEC_MSBC_TRANSPARENT 0x2
#define MGMT_SCO_CODEC_MSBC 0x3

struct mgmt_cp_get_codec_capabilities {
  uint16_t hci_dev;
} __attribute__((packed));

struct mgmt_rp_get_codec_capabilities {
  uint16_t hci_dev;
  uint8_t transparent_wbs_supported;
  uint8_t hci_data_path_id;
  uint32_t wbs_pkt_len;
} __attribute__((packed));

#define MGMT_POLL_TIMEOUT_MS 2000

void cache_codec_capabilities(struct mgmt_rp_get_codec_capabilities* rp) {
  const uint8_t kCodecCvsd = 0x2;
  const uint8_t kCodecTransparent = 0x3;
  const uint8_t kCodecMsbc = 0x5;

  auto codecs =
      bluetooth::shim::GetController()->GetLocalSupportedBrEdrCodecIds();

  for (uint8_t codec_id : codecs) {
    // TODO(b/323087725): Query the codec capabilities and fill in c.inner.data.
    // The capabilities are not used currently so it's safe to keep this for a
    // while.
    cached_codec_info c{};
    switch (codec_id) {
      case kCodecCvsd:
        c.inner.codec = codec::CVSD;
        break;
      case kCodecTransparent:
        if (!rp->transparent_wbs_supported) {
          // Transparent wideband speech not supported, skip it.
          continue;
        }
        c.inner.codec = codec::MSBC_TRANSPARENT;
        c.pkt_size = rp->wbs_pkt_len;
        break;
      case kCodecMsbc:
        offload_supported = true;
        c.inner.codec = codec::MSBC;
        c.inner.data_path = rp->hci_data_path_id;
        c.pkt_size = rp->wbs_pkt_len;
        break;
      default:
        log::debug("Unsupported codec ID: {}", codec_id);
        continue;
    }

    log::info("Caching HFP codec {}, data path {}, data len {}, pkt_size {}",
              (uint64_t)c.inner.codec, c.inner.data_path, c.inner.data.size(),
              c.pkt_size);

    cached_codecs.push_back(c);
  }
}

struct sockaddr_hci {
  sa_family_t hci_family;
  unsigned short hci_dev;
  unsigned short hci_channel;
};

constexpr uint8_t BTPROTO_HCI = 1;
constexpr uint16_t HCI_CHANNEL_CONTROL = 3;
constexpr uint16_t HCI_DEV_NONE = 0xffff;

int btsocket_open_mgmt(uint16_t hci) {
  int fd = socket(PF_BLUETOOTH, SOCK_RAW | SOCK_NONBLOCK, BTPROTO_HCI);
  if (fd < 0) {
    log::debug("Failed to open BT socket.");
    return -errno;
  }

  struct sockaddr_hci addr = {
      .hci_family = AF_BLUETOOTH,
      .hci_dev = HCI_DEV_NONE,
      .hci_channel = HCI_CHANNEL_CONTROL,
  };

  int ret = bind(fd, (struct sockaddr*)&addr, sizeof(addr));
  if (ret < 0) {
    log::debug("Failed to bind BT socket.");
    close(fd);
    return -errno;
  }

  return fd;
}

int mgmt_get_codec_capabilities(int fd, uint16_t hci) {
  struct mgmt_pkt ev;
  ev.opcode = MGMT_OP_GET_SCO_CODEC_CAPABILITIES;
  ev.index = HCI_DEV_NONE;
  ev.len = sizeof(struct mgmt_cp_get_codec_capabilities);

  struct mgmt_cp_get_codec_capabilities* cp =
      reinterpret_cast<struct mgmt_cp_get_codec_capabilities*>(ev.data);
  cp->hci_dev = hci;

  int ret;

  struct pollfd writable[1];
  writable[0].fd = fd;
  writable[0].events = POLLOUT;

  do {
    ret = poll(writable, 1, MGMT_POLL_TIMEOUT_MS);
    if (ret > 0) {
      RETRY_ON_INTR(ret = write(fd, &ev, MGMT_PKT_HDR_SIZE + ev.len));
      if (ret < 0) {
        log::debug("Failed to call MGMT_OP_GET_SCO_CODEC_CAPABILITIES: {}",
                   -errno);
        return -errno;
      };
      break;
    }
  } while (ret > 0);

  if (ret <= 0) {
    log::debug("Failed waiting for mgmt socket to be writable.");
    return -1;
  }

  struct pollfd fds[1];

  fds[0].fd = fd;
  fds[0].events = POLLIN;

  do {
    ret = poll(fds, 1, MGMT_POLL_TIMEOUT_MS);
    if (ret > 0) {
      if (fds[0].revents & POLLIN) {
        RETRY_ON_INTR(ret = read(fd, &ev, sizeof(ev)));
        if (ret < 0) {
          log::debug("Failed to read mgmt socket: {}", -errno);
          return -errno;
        }

        if (ev.opcode == MGMT_EV_COMMAND_COMPLETE) {
          struct mgmt_ev_cmd_complete* cc =
              reinterpret_cast<struct mgmt_ev_cmd_complete*>(ev.data);
          if (cc->opcode == MGMT_OP_GET_SCO_CODEC_CAPABILITIES &&
              cc->status == 0) {
            struct mgmt_rp_get_codec_capabilities* rp =
                reinterpret_cast<struct mgmt_rp_get_codec_capabilities*>(
                    cc->data);
            if (rp->hci_dev == hci) {
              cache_codec_capabilities(rp);
              return 0;
            }
          }
        }
      }
    } else if (ret == 0) {
      log::debug("Timeout while waiting for codec capabilities response.");
      ret = -1;
    }
  } while (ret > 0);

  return ret;
}

#define MGMT_OP_NOTIFY_SCO_CONNECTION_CHANGE 0x0101
struct mgmt_cp_notify_sco_connection_change {
  uint16_t hci_dev;
  uint8_t addr[6];
  uint8_t addr_type;
  uint8_t connected;
  uint8_t codec;
} __attribute__((packed));

int mgmt_notify_sco_connection_change(int fd, int hci, RawAddress device,
                                      bool is_connected, int codec) {
  struct mgmt_pkt ev;
  ev.opcode = MGMT_OP_NOTIFY_SCO_CONNECTION_CHANGE;
  ev.index = HCI_DEV_NONE;
  ev.len = sizeof(struct mgmt_cp_notify_sco_connection_change);

  struct mgmt_cp_notify_sco_connection_change* cp =
      reinterpret_cast<struct mgmt_cp_notify_sco_connection_change*>(ev.data);

  cp->hci_dev = hci;
  cp->connected = is_connected;
  cp->codec = codec;
  memcpy(cp->addr, device.address, sizeof(cp->addr));
  cp->addr_type = 0;

  int ret;

  struct pollfd writable[1];
  writable[0].fd = fd;
  writable[0].events = POLLOUT;

  do {
    ret = poll(writable, 1, MGMT_POLL_TIMEOUT_MS);
    if (ret > 0) {
      RETRY_ON_INTR(ret = write(fd, &ev, MGMT_PKT_HDR_SIZE + ev.len));
      if (ret < 0) {
        log::error("Failed to call MGMT_OP_NOTIFY_SCO_CONNECTION_CHANGE: {}",
                   -errno);
        return -errno;
      };
      break;
    }
  } while (ret > 0);

  if (ret <= 0) {
    log::debug("Failed waiting for mgmt socket to be writable.");
    return -1;
  }

  return 0;
}
}  // namespace

void init() {
  int hci = bluetooth::common::InitFlags::GetAdapterIndex();
  int fd = btsocket_open_mgmt(hci);
  if (fd < 0) {
    log::error("Failed to open mgmt channel, error= {}.", fd);
    return;
  }

  int ret = mgmt_get_codec_capabilities(fd, hci);
  if (ret) {
    log::error("Failed to get codec capabilities with error = {}.", ret);
  } else {
    log::info("Successfully queried SCO codec capabilities.");
  }

  close(fd);
}

// Check if the specified coding format is supported by the adapter.
bool is_coding_format_supported(esco_coding_format_t coding_format) {
  if (coding_format != ESCO_CODING_FORMAT_TRANSPNT &&
      coding_format != ESCO_CODING_FORMAT_MSBC) {
    log::warn("Unsupported coding format to query: {}", coding_format);
    return false;
  }

  for (cached_codec_info c : cached_codecs) {
    if (c.inner.codec == MSBC_TRANSPARENT &&
        coding_format == ESCO_CODING_FORMAT_TRANSPNT) {
      return true;
    }
    if (c.inner.codec == MSBC && coding_format == ESCO_CODING_FORMAT_MSBC) {
      return true;
    }
  }

  return false;
}

// Check if wideband speech is supported on local device
bool get_wbs_supported() {
  return is_coding_format_supported(ESCO_CODING_FORMAT_TRANSPNT) ||
         is_coding_format_supported(ESCO_CODING_FORMAT_MSBC);
}

// Check if super-wideband speech is supported on local device
bool get_swb_supported() {
  // We only support SWB via transparent mode.
  return is_coding_format_supported(ESCO_CODING_FORMAT_TRANSPNT);
}

// Checks the supported codecs
bt_codecs get_codec_capabilities(uint64_t codecs) {
  bt_codecs codec_list = {.offload_capable = offload_supported};

  for (auto c : cached_codecs) {
    if (c.inner.codec & codecs) {
      codec_list.codecs.push_back(c.inner);
    }
  }

  return codec_list;
}

// Check if hardware offload is supported
bool get_offload_supported() { return offload_supported; }

// Check if hardware offload is enabled
bool get_offload_enabled() { return offload_supported && offload_enabled; }

// Set offload enable/disable
bool enable_offload(bool enable) {
  if (!offload_supported && enable) {
    log::error("Cannot enable SCO-offload since it is not supported.");
    return false;
  }
  offload_enabled = enable;
  return true;
}

static bool get_single_codec(int codec, bt_codec** out) {
  for (cached_codec_info& c : cached_codecs) {
    if (c.inner.codec == codec) {
      *out = &c.inner;
      return true;
    }
  }

  return false;
}

constexpr uint8_t OFFLOAD_DATAPATH = 0x01;

// Notify the codec datapath to lower layer for offload mode
void set_codec_datapath(int codec_uuid) {
  bool found;
  bt_codec* codec;
  uint8_t codec_id;

  if (codec_uuid == UUID_CODEC_LC3 && get_offload_enabled()) {
    log::error("Offload path for LC3 is not implemented.");
    return;
  }

  switch (codec_uuid) {
    case UUID_CODEC_CVSD:
      codec_id = codec::CVSD;
      break;
    case UUID_CODEC_MSBC:
      codec_id = get_offload_enabled() ? codec::MSBC : codec::MSBC_TRANSPARENT;
      break;
    case UUID_CODEC_LC3:
      codec_id = get_offload_enabled() ? codec::LC3 : codec::MSBC_TRANSPARENT;
      break;
    default:
      log::warn("Unsupported codec ({}). Won't set datapath.", codec_uuid);
      return;
  }

  found = get_single_codec(codec_id, &codec);
  if (!found) {
    log::error(
        "Failed to find codec config for codec ({}). Won't set datapath.",
        codec_uuid);
    return;
  }

  log::info("Configuring datapath for codec ({})", codec_uuid);
  if (codec->codec == codec::MSBC && !get_offload_enabled()) {
    log::error(
        "Tried to configure offload data path for format ({}) with offload "
        "disabled. Won't set datapath.",
        codec_uuid);
    return;
  }

  if (get_offload_enabled()) {
    std::vector<uint8_t> data;
    switch (codec_uuid) {
      case UUID_CODEC_CVSD:
        data = {0x00};
        break;
      case UUID_CODEC_MSBC:
        data = {0x01};
        break;
      default:
        break;
    }

    GetInterface().ConfigureDataPath(hci_data_direction_t::CONTROLLER_TO_HOST,
                                     OFFLOAD_DATAPATH, data);
    GetInterface().ConfigureDataPath(hci_data_direction_t::HOST_TO_CONTROLLER,
                                     OFFLOAD_DATAPATH, data);
  }
}

int get_packet_size(int codec) {
  for (const cached_codec_info& c : cached_codecs) {
    if (c.inner.codec == codec) {
      return c.pkt_size;
    }
  }

  return kDefaultPacketSize;
}

void notify_sco_connection_change(RawAddress device, bool is_connected,
                                  int codec) {
  int hci = bluetooth::common::InitFlags::GetAdapterIndex();
  int fd = btsocket_open_mgmt(hci);
  if (fd < 0) {
    log::error("Failed to open mgmt channel, error= {}.", fd);
    return;
  }

  if (codec == codec::LC3) {
    log::error("Offload path for LC3 is not implemented.");
    return;
  }

  int converted_codec;

  switch (codec) {
    case codec::MSBC:
      converted_codec = MGMT_SCO_CODEC_MSBC;
      break;
    case codec::MSBC_TRANSPARENT:
      converted_codec = MGMT_SCO_CODEC_MSBC_TRANSPARENT;
      break;
    default:
      converted_codec = MGMT_SCO_CODEC_CVSD;
  }

  int ret = mgmt_notify_sco_connection_change(fd, hci, device, is_connected,
                                              converted_codec);
  if (ret) {
    log::error(
        "Failed to notify HAL of connection change: hci {}, device {}, "
        "connected {}, codec {}",
        hci, ADDRESS_TO_LOGGABLE_CSTR(device), is_connected, codec);
  } else {
    log::info(
        "Notified HAL of connection change: hci {}, device {}, connected {}, "
        "codec {}",
        hci, ADDRESS_TO_LOGGABLE_CSTR(device), is_connected, codec);
  }

  close(fd);
}

void update_esco_parameters(enh_esco_params_t* p_parms) {
  if (get_offload_enabled()) {
    p_parms->input_transport_unit_size = 0x01;
    p_parms->output_transport_unit_size = 0x01;
  } else {
    p_parms->input_transport_unit_size = 0x00;
    p_parms->output_transport_unit_size = 0x00;
  }
}
}  // namespace hfp_hal_interface

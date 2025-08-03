/******************************************************************************
 *
 *  Copyright 2014 Google, Inc.
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

#define LOG_TAG "bt_btif_sock"

#include "btif/include/btif_sock.h"

#include <base/functional/callback.h>
#include <base/logging.h>
#include <bluetooth/log.h>
#include <frameworks/proto_logging/stats/enums/bluetooth/enums.pb.h>
#include <hardware/bluetooth.h>
#include <hardware/bt_sock.h>
#include <time.h>

#include <atomic>

#include "bta/include/bta_api.h"
#include "bta_sec_api.h"
#include "btif_metrics_logging.h"
#include "btif_sock_l2cap.h"
#include "btif_sock_rfc.h"
#include "btif_sock_sco.h"
#include "btif_sock_thread.h"
#include "btif_uid.h"
#include "include/check.h"
#include "os/log.h"
#include "osi/include/osi.h"  // INVALID_FD
#include "osi/include/thread.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

bool btif_get_address_type(const RawAddress& bda, tBLE_ADDR_TYPE* p_addr_type);
bool btif_get_device_type(const RawAddress& bda, int* p_device_type);

using bluetooth::Uuid;
using namespace bluetooth;

static bt_status_t btsock_listen(btsock_type_t type, const char* service_name,
                                 const Uuid* uuid, int channel, int* sock_fd,
                                 int flags, int app_uid);
static bt_status_t btsock_connect(const RawAddress* bd_addr, btsock_type_t type,
                                  const Uuid* uuid, int channel, int* sock_fd,
                                  int flags, int app_uid);

static void btsock_request_max_tx_data_length(const RawAddress& bd_addr);
static bt_status_t btsock_control_req(uint8_t dlci, const RawAddress& bd_addr,
                                      uint8_t modem_signal,
                                      uint8_t break_signal,
                                      uint8_t discard_buffers,
                                      uint8_t break_signal_seq, bool fc);

static void btsock_signaled(int fd, int type, int flags, uint32_t user_id);
static bt_status_t btsock_disconnect_all(const RawAddress* bd_addr);

static std::atomic_int thread_handle{-1};
static thread_t* thread;

#define SOCK_LOGGER_SIZE_MAX 16

struct SockConnectionEvent {
  bool used;
  RawAddress addr;
  int state;
  int role;
  int channel;
  char server_name[64];
  struct timespec timestamp;

  void dump(const int fd);
};

static std::atomic<uint8_t> logger_index;

static SockConnectionEvent connection_logger[SOCK_LOGGER_SIZE_MAX];

const btsock_interface_t* btif_sock_get_interface(void) {
  static btsock_interface_t interface = {
      sizeof(interface),  btsock_listen,
      btsock_connect,     btsock_request_max_tx_data_length,
      btsock_control_req, btsock_disconnect_all,
  };

  return &interface;
}

bt_status_t btif_sock_init(uid_set_t* uid_set) {
  CHECK(thread_handle == -1);
  CHECK(thread == NULL);

  bt_status_t status;
  btsock_thread_init();
  thread_handle = btsock_thread_create(btsock_signaled, NULL);
  if (thread_handle == -1) {
    log::error("unable to create btsock_thread.");
    goto error;
  }

  status = btsock_rfc_init(thread_handle, uid_set);
  if (status != BT_STATUS_SUCCESS) {
    log::error("error initializing RFCOMM sockets: {}", status);
    goto error;
  }

  status = btsock_l2cap_init(thread_handle, uid_set);
  if (status != BT_STATUS_SUCCESS) {
    log::error("error initializing L2CAP sockets: {}", status);
    goto error;
  }

  thread = thread_new("btif_sock");
  if (!thread) {
    log::error("error creating new thread.");
    btsock_rfc_cleanup();
    goto error;
  }

  status = btsock_sco_init(thread);
  if (status != BT_STATUS_SUCCESS) {
    log::error("error initializing SCO sockets: {}", status);
    btsock_rfc_cleanup();
    goto error;
  }

  return BT_STATUS_SUCCESS;

error:;
  thread_free(thread);
  thread = NULL;
  if (thread_handle != -1) btsock_thread_exit(thread_handle);
  thread_handle = -1;
  uid_set = NULL;
  return BT_STATUS_FAIL;
}

void btif_sock_cleanup(void) {
  int saved_handle = thread_handle;
  if (std::atomic_exchange(&thread_handle, -1) == -1) return;

  btsock_thread_exit(saved_handle);
  btsock_rfc_cleanup();
  btsock_sco_cleanup();
  btsock_l2cap_cleanup();
  thread_free(thread);
  thread = NULL;
}

void btif_sock_connection_logger(int state, int role, const RawAddress& addr,
                                 int channel, const char* server_name) {
  log::info("address={}, state={}, role={}, server_name={}, channel={}",
            ADDRESS_TO_LOGGABLE_CSTR(addr), state, role, server_name, channel);

  uint8_t index = logger_index++ % SOCK_LOGGER_SIZE_MAX;

  connection_logger[index] = {
      .used = true,
      .addr = addr,
      .state = state,
      .role = role,
      .channel = channel,
      .server_name = {'\0'},
  };

  strncpy(connection_logger[index].server_name, server_name,
          sizeof(connection_logger[index].server_name) - 1);
  clock_gettime(CLOCK_REALTIME, &connection_logger[index].timestamp);
}

void btif_sock_dump(int fd) {
  dprintf(fd, "\nSocket Events: \n");
  dprintf(fd,
          "  Time        \tAddress          \tState             \tRole"
          "              \tChannel   \tServerName\n");

  const uint8_t head = logger_index.load() % SOCK_LOGGER_SIZE_MAX;

  uint8_t index = head;
  do {
    connection_logger[index].dump(fd);

    index++;
    index %= SOCK_LOGGER_SIZE_MAX;
  } while (index != head);
  dprintf(fd, "\n");
}

void SockConnectionEvent::dump(const int fd) {
  if (!used) {
    return;
  }

  char eventtime[20];
  char temptime[20];
  struct tm* tstamp = localtime(&timestamp.tv_sec);
  strftime(temptime, sizeof(temptime), "%H:%M:%S", tstamp);
  snprintf(eventtime, sizeof(eventtime), "%s.%03ld", temptime,
           timestamp.tv_nsec / 1000000);

  const char* str_state;
  switch (state) {
    case SOCKET_CONNECTION_STATE_LISTENING:
      str_state = "STATE_LISTENING";
      break;
    case SOCKET_CONNECTION_STATE_CONNECTING:
      str_state = "STATE_CONNECTING";
      break;
    case SOCKET_CONNECTION_STATE_CONNECTED:
      str_state = "STATE_CONNECTED";
      break;
    case SOCKET_CONNECTION_STATE_DISCONNECTING:
      str_state = "STATE_DISCONNECTING";
      break;
    case SOCKET_CONNECTION_STATE_DISCONNECTED:
      str_state = "STATE_DISCONNECTED";
      break;
    default:
      str_state = "STATE_UNKNOWN";
      break;
  }

  const char* str_role;
  switch (role) {
    case SOCKET_ROLE_LISTEN:
      str_role = "ROLE_LISTEN";
      break;
    case SOCKET_ROLE_CONNECTION:
      str_role = "ROLE_CONNECTION";
      break;
    default:
      str_role = "ROLE_UNKNOWN";
      break;
  }

  dprintf(fd, "  %s\t%s\t%s   \t%s      \t%d         \t%s\n", eventtime,
          ADDRESS_TO_LOGGABLE_CSTR(addr), str_state, str_role, channel,
          server_name);
}

static bt_status_t btsock_control_req(uint8_t dlci, const RawAddress& bd_addr,
                                      uint8_t modem_signal,
                                      uint8_t break_signal,
                                      uint8_t discard_buffers,
                                      uint8_t break_signal_seq, bool fc) {
  return btsock_rfc_control_req(dlci, bd_addr, modem_signal, break_signal,
                                discard_buffers, break_signal_seq, fc);
}

static bt_status_t btsock_listen(btsock_type_t type, const char* service_name,
                                 const Uuid* service_uuid, int channel,
                                 int* sock_fd, int flags, int app_uid) {
  if ((flags & BTSOCK_FLAG_NO_SDP) == 0) {
    CHECK(sock_fd != NULL);
  }

  *sock_fd = INVALID_FD;
  bt_status_t status = BT_STATUS_FAIL;
  int original_channel = channel;

  log_socket_connection_state(RawAddress::kEmpty, 0, type,
                              android::bluetooth::SocketConnectionstateEnum::
                                  SOCKET_CONNECTION_STATE_LISTENING,
                              0, 0, app_uid, channel,
                              android::bluetooth::SOCKET_ROLE_LISTEN);
  switch (type) {
    case BTSOCK_RFCOMM:
      status = btsock_rfc_listen(service_name, service_uuid, channel, sock_fd,
                                 flags, app_uid);
      break;
    case BTSOCK_L2CAP:
      status =
          btsock_l2cap_listen(service_name, channel, sock_fd, flags, app_uid);
      break;
    case BTSOCK_L2CAP_LE:
      if (flags & BTSOCK_FLAG_NO_SDP) {
        /* Set channel to zero so that it will be assigned */
        channel = 0;
      } else if (channel <= 0) {
        log::error("type BTSOCK_L2CAP_LE: invalid channel={}", channel);
        break;
      }
      flags |= BTSOCK_FLAG_LE_COC;
      log::info(
          "type=BTSOCK_L2CAP_LE, channel=0x{:x}, original=0x{:x}, flags=0x{:x}",
          channel, original_channel, flags);
      status =
          btsock_l2cap_listen(service_name, channel, sock_fd, flags, app_uid);
      break;
    case BTSOCK_SCO:
      status = btsock_sco_listen(sock_fd, flags);
      break;

    default:
      log::error("unknown/unsupported socket type: {}", type);
      status = BT_STATUS_UNSUPPORTED;
      break;
  }
  if (status != BT_STATUS_SUCCESS) {
    btif_sock_connection_logger(SOCKET_CONNECTION_STATE_DISCONNECTED,
                                SOCKET_ROLE_LISTEN, RawAddress::kEmpty, channel,
                                service_name);
    log_socket_connection_state(RawAddress::kEmpty, 0, type,
                                android::bluetooth::SocketConnectionstateEnum::
                                    SOCKET_CONNECTION_STATE_DISCONNECTED,
                                0, 0, app_uid, channel,
                                android::bluetooth::SOCKET_ROLE_LISTEN);
  }
  return status;
}

static bt_status_t btsock_connect(const RawAddress* bd_addr, btsock_type_t type,
                                  const Uuid* uuid, int channel, int* sock_fd,
                                  int flags, int app_uid) {
  CHECK(bd_addr != NULL);
  CHECK(sock_fd != NULL);

  log::info("");

  *sock_fd = INVALID_FD;
  bt_status_t status = BT_STATUS_FAIL;

  btif_sock_connection_logger(SOCKET_CONNECTION_STATE_CONNECTING,
                              SOCKET_ROLE_CONNECTION, *bd_addr, channel,
                              uuid->ToString().c_str());
  log_socket_connection_state(*bd_addr, 0, type,
                              android::bluetooth::SocketConnectionstateEnum::
                                  SOCKET_CONNECTION_STATE_CONNECTING,
                              0, 0, app_uid, channel,
                              android::bluetooth::SOCKET_ROLE_CONNECTION);
  switch (type) {
    case BTSOCK_RFCOMM:
      status =
          btsock_rfc_connect(bd_addr, uuid, channel, sock_fd, flags, app_uid);
      break;

    case BTSOCK_L2CAP:
      status = btsock_l2cap_connect(bd_addr, channel, sock_fd, flags, app_uid);
      break;

    case BTSOCK_L2CAP_LE: {
      flags |= BTSOCK_FLAG_LE_COC;

      // Ensure device is in inquiry database
      tBLE_ADDR_TYPE addr_type = BLE_ADDR_PUBLIC;
      int device_type = 0;

      if (btif_get_address_type(*bd_addr, &addr_type) &&
          btif_get_device_type(*bd_addr, &device_type) &&
          device_type != BT_DEVICE_TYPE_BREDR) {
        BTA_DmAddBleDevice(*bd_addr, addr_type, device_type);
      }

      log::info("type=BTSOCK_L2CAP_LE, channel=0x{:x}, flags=0x{:x}", channel,
                flags);
      status = btsock_l2cap_connect(bd_addr, channel, sock_fd, flags, app_uid);
      break;
    }

    case BTSOCK_SCO:
      status = btsock_sco_connect(bd_addr, sock_fd, flags);
      break;

    default:
      log::error("unknown/unsupported socket type: {}", type);
      status = BT_STATUS_UNSUPPORTED;
      break;
  }
  if (status != BT_STATUS_SUCCESS) {
    btif_sock_connection_logger(SOCKET_CONNECTION_STATE_DISCONNECTED,
                                SOCKET_ROLE_CONNECTION, *bd_addr, channel,
                                uuid->ToString().c_str());
    log_socket_connection_state(*bd_addr, 0, type,
                                android::bluetooth::SocketConnectionstateEnum::
                                    SOCKET_CONNECTION_STATE_DISCONNECTED,
                                0, 0, app_uid, channel,
                                android::bluetooth::SOCKET_ROLE_CONNECTION);
  }
  return status;
}

static void btsock_request_max_tx_data_length(const RawAddress& remote_device) {
  BTA_DmBleRequestMaxTxDataLength(remote_device);
}

static void btsock_signaled(int fd, int type, int flags, uint32_t user_id) {
  switch (type) {
    case BTSOCK_RFCOMM:
      btsock_rfc_signaled(fd, flags, user_id);
      break;
    case BTSOCK_L2CAP:
    case BTSOCK_L2CAP_LE:
      /* Note: The caller may not distinguish between BTSOCK_L2CAP and
       * BTSOCK_L2CAP_LE correctly */
      btsock_l2cap_signaled(fd, flags, user_id);
      break;
    default:
      log::fatal("Invalid socket type! type={} fd={} flags={} user_id={}", type,
                 fd, flags, user_id);
      break;
  }
}

static bt_status_t btsock_disconnect_all(const RawAddress* bd_addr) {
  CHECK(bd_addr != NULL);

  bt_status_t rfc_status = btsock_rfc_disconnect(bd_addr);
  bt_status_t l2cap_status = btsock_l2cap_disconnect(bd_addr);
  /* SCO is disconnected via btif_hf, so is not handled here. */

  log::info("rfc status: {}, l2cap status: {}", rfc_status, l2cap_status);

  /* Return error status, if any. */
  if (rfc_status == BT_STATUS_SUCCESS) {
    return l2cap_status;
  }
  return rfc_status;
}

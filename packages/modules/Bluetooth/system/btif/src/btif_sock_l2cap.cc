/*
 * Copyright 2014 Samsung System LSI
 * Copyright 2013 The Android Open Source Project
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

#include <base/logging.h>
#include <bluetooth/log.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <cstdint>
#include <cstring>

#include "bta/include/bta_jv_api.h"
#include "btif/include/btif_metrics_logging.h"
#include "btif/include/btif_sock.h"
#include "btif/include/btif_sock_thread.h"
#include "btif/include/btif_sock_util.h"
#include "btif/include/btif_uid.h"
#include "include/hardware/bluetooth.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "osi/include/osi.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

using namespace bluetooth;

struct packet {
  struct packet *next, *prev;
  uint32_t len;
  uint8_t* data;
};

typedef struct l2cap_socket {
  struct l2cap_socket* prev;  // link to prev list item
  struct l2cap_socket* next;  // link to next list item
  RawAddress addr;            // other side's address
  char name[256];             // user-friendly name of the service
  uint32_t id;                // just a tag to find this struct
  int app_uid;                // The UID of the app who requested this socket
  int handle;                 // handle from lower layers
  unsigned security;          // security flags
  int channel;                // PSM
  int our_fd;                 // fd from our side
  int app_fd;                 // fd from app's side

  unsigned bytes_buffered;
  struct packet* first_packet;  // fist packet to be delivered to app
  struct packet* last_packet;   // last packet to be delivered to app

  unsigned server : 1;            // is a server? (or connecting?)
  unsigned connected : 1;         // is connected?
  unsigned outgoing_congest : 1;  // should we hold?
  unsigned server_psm_sent : 1;   // The server shall only send PSM once.
  bool is_le_coc;                 // is le connection oriented channel?
  uint16_t rx_mtu;
  uint16_t tx_mtu;
  // Cumulative number of bytes transmitted on this socket
  int64_t tx_bytes;
  // Cumulative number of bytes received on this socket
  int64_t rx_bytes;
} l2cap_socket;

static void btsock_l2cap_server_listen(l2cap_socket* sock);

static std::mutex state_lock;

l2cap_socket* socks = NULL;
static uint32_t last_sock_id = 0;
static uid_set_t* uid_set = NULL;
static int pth = -1;

static void btsock_l2cap_cbk(tBTA_JV_EVT event, tBTA_JV* p_data,
                             uint32_t l2cap_socket_id);

/* TODO: Consider to remove this buffer, as we have a buffer in l2cap as well,
 * and we risk
 *       a buffer overflow with this implementation if the socket data is not
 * read from
 *       JAVA for a while. In such a case we should use flow control to tell the
 * sender to
 *       back off.
 *       BUT remember we need to avoid blocking the BTA task execution - hence
 * we cannot
 *       directly write to the socket.
 *       we should be able to change to store the data pointer here, and just
 * wait
 *       confirming the l2cap_ind until we have more space in the buffer. */

/* returns false if none - caller must free "data" memory when done with it */
static char packet_get_head_l(l2cap_socket* sock, uint8_t** data,
                              uint32_t* len) {
  struct packet* p = sock->first_packet;

  if (!p) return false;

  if (data) *data = sock->first_packet->data;
  if (len) *len = sock->first_packet->len;
  sock->first_packet = p->next;
  if (sock->first_packet)
    sock->first_packet->prev = NULL;
  else
    sock->last_packet = NULL;

  if (len) sock->bytes_buffered -= *len;

  osi_free(p);

  return true;
}

static struct packet* packet_alloc(const uint8_t* data, uint32_t len) {
  struct packet* p = (struct packet*)osi_calloc(sizeof(*p));
  uint8_t* buf = (uint8_t*)osi_malloc(len);

  p->data = buf;
  p->len = len;
  memcpy(p->data, data, len);
  return p;
}

/* makes a copy of the data, returns true on success */
static char packet_put_head_l(l2cap_socket* sock, const void* data,
                              uint32_t len) {
  struct packet* p = packet_alloc((const uint8_t*)data, len);

  /*
   * We do not check size limits here since this is used to undo "getting" a
   * packet that the user read incompletely. That is to say the packet was
   * already in the queue. We do check thos elimits in packet_put_tail_l() since
   * that function is used to put new data into the queue.
   */

  if (!p) return false;

  p->prev = NULL;
  p->next = sock->first_packet;
  sock->first_packet = p;
  if (p->next)
    p->next->prev = p;
  else
    sock->last_packet = p;

  sock->bytes_buffered += len;

  return true;
}

/* makes a copy of the data, returns true on success */
static char packet_put_tail_l(l2cap_socket* sock, const void* data,
                              uint32_t len) {
  if (sock->bytes_buffered >= L2CAP_MAX_RX_BUFFER) {
    log::error("Unable to add to buffer due to buffer overflow socket_id:{}",
               sock->id);
    return false;
  }

  struct packet* p = packet_alloc((const uint8_t*)data, len);
  p->next = NULL;
  p->prev = sock->last_packet;
  sock->last_packet = p;
  if (p->prev)
    p->prev->next = p;
  else
    sock->first_packet = p;

  sock->bytes_buffered += len;

  return true;
}

static char is_inited(void) {
  std::unique_lock<std::mutex> lock(state_lock);
  return pth != -1;
}

/* only call with std::mutex taken */
static l2cap_socket* btsock_l2cap_find_by_id_l(uint32_t id) {
  l2cap_socket* sock = socks;

  while (sock && sock->id != id) sock = sock->next;

  return sock;
}

static void btsock_l2cap_free_l(l2cap_socket* sock) {
  uint8_t* buf;
  l2cap_socket* t = socks;

  while (t && t != sock) t = t->next;

  if (!t) /* prever double-frees */
    return;

  btif_sock_connection_logger(
      SOCKET_CONNECTION_STATE_DISCONNECTED,
      sock->server ? SOCKET_ROLE_LISTEN : SOCKET_ROLE_CONNECTION, sock->addr,
      sock->channel, sock->name);

  // Whenever a socket is freed, the connection must be dropped
  log_socket_connection_state(
      sock->addr, sock->id, sock->is_le_coc ? BTSOCK_L2CAP_LE : BTSOCK_L2CAP,
      android::bluetooth::SOCKET_CONNECTION_STATE_DISCONNECTED, sock->tx_bytes,
      sock->rx_bytes, sock->app_uid, sock->channel,
      sock->server ? android::bluetooth::SOCKET_ROLE_LISTEN
                   : android::bluetooth::SOCKET_ROLE_CONNECTION);

  if (sock->next) sock->next->prev = sock->prev;

  if (sock->prev)
    sock->prev->next = sock->next;
  else
    socks = sock->next;

  shutdown(sock->our_fd, SHUT_RDWR);
  close(sock->our_fd);
  if (sock->app_fd != -1) {
    close(sock->app_fd);
  } else {
    log::info("Application has already closed l2cap socket socket_id:{}",
              sock->id);
  }

  while (packet_get_head_l(sock, &buf, NULL)) osi_free(buf);

  // lower-level close() should be idempotent... so let's call it and see...
  if (sock->is_le_coc) {
    // Only call if we are non server connections
    if (sock->handle >= 0 && (!sock->server)) {
      BTA_JvL2capClose(sock->handle);
    }
    if ((sock->channel >= 0) && (sock->server)) {
      BTA_JvFreeChannel(sock->channel, BTA_JV_CONN_TYPE_L2CAP_LE);
      log::info("Stopped L2CAP LE COC server socket_id:{} channel:{}", sock->id,
                sock->channel);
      BTA_JvL2capStopServer(sock->channel, sock->id);
    }
  } else {
    // Only call if we are non server connections
    if ((sock->handle >= 0) && (!sock->server)) {
      BTA_JvL2capClose(sock->handle);
    }
    if ((sock->channel >= 0) && (sock->server)) {
      BTA_JvFreeChannel(sock->channel, BTA_JV_CONN_TYPE_L2CAP);
      BTA_JvL2capStopServer(sock->channel, sock->id);
    }
  }

  osi_free(sock);
}

static l2cap_socket* btsock_l2cap_alloc_l(const char* name,
                                          const RawAddress* addr,
                                          char is_server, int flags) {
  unsigned security = 0;
  int fds[2];
  l2cap_socket* sock = (l2cap_socket*)osi_calloc(sizeof(*sock));
  int sock_type = SOCK_SEQPACKET;

  if (flags & BTSOCK_FLAG_ENCRYPT)
    security |= is_server ? BTM_SEC_IN_ENCRYPT : BTM_SEC_OUT_ENCRYPT;
  if (flags & BTSOCK_FLAG_AUTH)
    security |= is_server ? BTM_SEC_IN_AUTHENTICATE : BTM_SEC_OUT_AUTHENTICATE;
  if (flags & BTSOCK_FLAG_AUTH_MITM)
    security |= is_server ? BTM_SEC_IN_MITM : BTM_SEC_OUT_MITM;
  if (flags & BTSOCK_FLAG_AUTH_16_DIGIT)
    security |= BTM_SEC_IN_MIN_16_DIGIT_PIN;

    // For Floss, set socket as SOCK_STREAM
    // TODO(b:271828292): Set SOCK_STREAM for everyone after verification tests
#if TARGET_FLOSS
  sock_type = SOCK_STREAM;
#endif
  if (socketpair(AF_LOCAL, sock_type, 0, fds)) {
    log::error("socketpair failed:{}", strerror(errno));
    goto fail_sockpair;
  }

  sock->our_fd = fds[0];
  sock->app_fd = fds[1];
  sock->security = security;
  sock->server = is_server;
  sock->connected = false;
  sock->handle = 0;
  sock->server_psm_sent = false;
  sock->app_uid = -1;

  if (name) strncpy(sock->name, name, sizeof(sock->name) - 1);
  if (addr) sock->addr = *addr;

  sock->first_packet = NULL;
  sock->last_packet = NULL;

  sock->tx_mtu = L2CAP_LE_MIN_MTU;

  sock->next = socks;
  sock->prev = NULL;
  if (socks) socks->prev = sock;
  sock->id = last_sock_id + 1;
  sock->tx_bytes = 0;
  sock->rx_bytes = 0;
  socks = sock;
  /* paranoia cap on: verify no ID duplicates due to overflow and fix as needed
   */
  while (1) {
    l2cap_socket* t;
    t = socks->next;
    while (t && t->id != sock->id) {
      t = t->next;
    }
    if (!t && sock->id) /* non-zeor handle is unique -> we're done */
      break;
    /* if we're here, we found a duplicate */
    if (!++sock->id) /* no zero IDs allowed */
      sock->id++;
  }
  last_sock_id = sock->id;
  log::info("Allocated l2cap socket structure socket_id:{}", sock->id);
  return sock;

fail_sockpair:
  osi_free(sock);
  return NULL;
}

bt_status_t btsock_l2cap_init(int handle, uid_set_t* set) {
  std::unique_lock<std::mutex> lock(state_lock);
  pth = handle;
  socks = NULL;
  uid_set = set;
  return BT_STATUS_SUCCESS;
}

bt_status_t btsock_l2cap_cleanup() {
  std::unique_lock<std::mutex> lock(state_lock);
  pth = -1;
  while (socks) btsock_l2cap_free_l(socks);
  return BT_STATUS_SUCCESS;
}

static inline bool send_app_psm_or_chan_l(l2cap_socket* sock) {
  log::info("Sending l2cap socket socket_id:{} channel:{}", sock->id,
            sock->channel);
  return sock_send_all(sock->our_fd, (const uint8_t*)&sock->channel,
                       sizeof(sock->channel)) == sizeof(sock->channel);
}

static bool send_app_err_code(l2cap_socket* sock, tBTA_JV_L2CAP_REASON code) {
  log::info("Sending l2cap failure reason socket_id:{} reason code:{}",
            sock->id, code);
  int err_channel = 0;
  if (sock_send_all(sock->our_fd, (const uint8_t*)&err_channel,
                    sizeof(err_channel)) != sizeof(err_channel)) {
    return false;
  }
  return sock_send_all(sock->our_fd, (const uint8_t*)&code, sizeof(code)) ==
         sizeof(code);
}

static bool send_app_connect_signal(int fd, const RawAddress* addr, int channel,
                                    int status, int send_fd, uint16_t rx_mtu,
                                    uint16_t tx_mtu) {
  sock_connect_signal_t cs;
  cs.size = sizeof(cs);
  cs.bd_addr = *addr;
  cs.channel = channel;
  cs.status = status;
  cs.max_rx_packet_size = rx_mtu;
  cs.max_tx_packet_size = tx_mtu;
  if (send_fd != -1) {
    if (sock_send_fd(fd, (const uint8_t*)&cs, sizeof(cs), send_fd) ==
        sizeof(cs))
      return true;
  } else if (sock_send_all(fd, (const uint8_t*)&cs, sizeof(cs)) == sizeof(cs)) {
    return true;
  }

  log::error("Unable to send data to socket fd:{} send_fd:{}", fd, send_fd);
  return false;
}

static void on_srv_l2cap_listen_started(tBTA_JV_L2CAP_START* p_start,
                                        uint32_t id) {
  l2cap_socket* sock;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  if (p_start->status != BTA_JV_SUCCESS) {
    log::error("Unable to start l2cap server socket_id:{}", sock->id);
    btsock_l2cap_free_l(sock);
    return;
  }

  sock->handle = p_start->handle;

  btif_sock_connection_logger(
      SOCKET_CONNECTION_STATE_LISTENING,
      sock->server ? SOCKET_ROLE_LISTEN : SOCKET_ROLE_CONNECTION, sock->addr,
      sock->channel, sock->name);

  log_socket_connection_state(
      sock->addr, sock->id, sock->is_le_coc ? BTSOCK_L2CAP_LE : BTSOCK_L2CAP,
      android::bluetooth::SocketConnectionstateEnum::
          SOCKET_CONNECTION_STATE_LISTENING,
      0, 0, sock->app_uid, sock->channel,
      sock->server ? android::bluetooth::SOCKET_ROLE_LISTEN
                   : android::bluetooth::SOCKET_ROLE_CONNECTION);

  if (!sock->server_psm_sent) {
    if (!send_app_psm_or_chan_l(sock)) {
      // closed
      log::info("Unable to send socket to application socket_id:{}", sock->id);
      btsock_l2cap_free_l(sock);
    } else {
      sock->server_psm_sent = true;
    }
  }
}

static void on_cl_l2cap_init(tBTA_JV_L2CAP_CL_INIT* p_init, uint32_t id) {
  l2cap_socket* sock;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  if (p_init->status != BTA_JV_SUCCESS) {
    log::error("Initialization status failed socket_id:{}", id);
    btsock_l2cap_free_l(sock);
    return;
  }

  sock->handle = p_init->handle;
}

/**
 * Here we allocate a new sock instance to mimic the BluetoothSocket. The socket
 * will be a clone of the sock representing the BluetoothServerSocket.
 * */
static void on_srv_l2cap_psm_connect_l(tBTA_JV_L2CAP_OPEN* p_open,
                                       l2cap_socket* sock) {
  // std::mutex locked by caller
  l2cap_socket* accept_rs =
      btsock_l2cap_alloc_l(sock->name, &p_open->rem_bda, false, 0);
  accept_rs->connected = true;
  accept_rs->security = sock->security;
  accept_rs->channel = sock->channel;
  accept_rs->handle = sock->handle;
  accept_rs->app_uid = sock->app_uid;
  sock->handle =
      -1; /* We should no longer associate this handle with the server socket */
  accept_rs->is_le_coc = sock->is_le_coc;
  accept_rs->tx_mtu = sock->tx_mtu = p_open->tx_mtu;

  /* Swap IDs to hand over the GAP connection to the accepted socket, and start
     a new server on the newly create socket ID. */
  uint32_t new_listen_id = accept_rs->id;
  accept_rs->id = sock->id;
  sock->id = new_listen_id;

  btif_sock_connection_logger(
      SOCKET_CONNECTION_STATE_CONNECTED,
      accept_rs->server ? SOCKET_ROLE_LISTEN : SOCKET_ROLE_CONNECTION,
      accept_rs->addr, accept_rs->channel, accept_rs->name);

  log_socket_connection_state(
      accept_rs->addr, accept_rs->id,
      accept_rs->is_le_coc ? BTSOCK_L2CAP_LE : BTSOCK_L2CAP,
      android::bluetooth::SOCKET_CONNECTION_STATE_CONNECTED, 0, 0,
      accept_rs->app_uid, accept_rs->channel,
      accept_rs->server ? android::bluetooth::SOCKET_ROLE_LISTEN
                        : android::bluetooth::SOCKET_ROLE_CONNECTION);

  // start monitor the socket
  btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP,
                       SOCK_THREAD_FD_EXCEPTION, sock->id);
  btsock_thread_add_fd(pth, accept_rs->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_RD,
                       accept_rs->id);
  send_app_connect_signal(sock->our_fd, &accept_rs->addr, sock->channel, 0,
                          accept_rs->app_fd, sock->rx_mtu, p_open->tx_mtu);
  accept_rs->app_fd =
      -1;  // The fd is closed after sent to app in send_app_connect_signal()
  // But for some reason we still leak a FD - either the server socket
  // one or the accept socket one.
  btsock_l2cap_server_listen(sock);
}

static void on_cl_l2cap_psm_connect_l(tBTA_JV_L2CAP_OPEN* p_open,
                                      l2cap_socket* sock) {
  sock->addr = p_open->rem_bda;
  sock->tx_mtu = p_open->tx_mtu;

  if (!send_app_psm_or_chan_l(sock)) {
    log::error("Unable to send l2cap socket to application socket_id:{}",
               sock->id);
    return;
  }

  if (!send_app_connect_signal(sock->our_fd, &sock->addr, sock->channel, 0, -1,
                               sock->rx_mtu, p_open->tx_mtu)) {
    log::error("Unable to connect l2cap socket to application socket_id:{}",
               sock->id);
    return;
  }

  btif_sock_connection_logger(
      SOCKET_CONNECTION_STATE_CONNECTED,
      sock->server ? SOCKET_ROLE_LISTEN : SOCKET_ROLE_CONNECTION, sock->addr,
      sock->channel, sock->name);

  log_socket_connection_state(
      sock->addr, sock->id, sock->is_le_coc ? BTSOCK_L2CAP_LE : BTSOCK_L2CAP,
      android::bluetooth::SOCKET_CONNECTION_STATE_CONNECTED, 0, 0,
      sock->app_uid, sock->channel,
      sock->server ? android::bluetooth::SOCKET_ROLE_LISTEN
                   : android::bluetooth::SOCKET_ROLE_CONNECTION);

  // start monitoring the socketpair to get call back when app writing data
  btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_RD,
                       sock->id);
  log::info("Connected l2cap socket socket_id:{}", sock->id);
  sock->connected = true;
}

static void on_l2cap_connect(tBTA_JV* p_data, uint32_t id) {
  l2cap_socket* sock;
  tBTA_JV_L2CAP_OPEN* psm_open = &p_data->l2c_open;
  tBTA_JV_L2CAP_LE_OPEN* le_open = &p_data->l2c_le_open;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  sock->tx_mtu = le_open->tx_mtu;
  if (psm_open->status == BTA_JV_SUCCESS) {
    if (!sock->server) {
      on_cl_l2cap_psm_connect_l(psm_open, sock);
    } else {
      on_srv_l2cap_psm_connect_l(psm_open, sock);
    }
  } else {
    log::error("Unable to open socket after receiving connection socket_id:{}",
               sock->id);
    btsock_l2cap_free_l(sock);
  }
}

static void on_l2cap_close(tBTA_JV_L2CAP_CLOSE* p_close, uint32_t id) {
  l2cap_socket* sock;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::info(
        "Unable to find probably already closed l2cap socket with socket_id:{}",
        id);
    return;
  }

  btif_sock_connection_logger(
      SOCKET_CONNECTION_STATE_DISCONNECTING,
      sock->server ? SOCKET_ROLE_LISTEN : SOCKET_ROLE_CONNECTION, sock->addr,
      sock->channel, sock->name);

  log_socket_connection_state(
      sock->addr, sock->id, sock->is_le_coc ? BTSOCK_L2CAP_LE : BTSOCK_L2CAP,
      android::bluetooth::SOCKET_CONNECTION_STATE_DISCONNECTING, 0, 0,
      sock->app_uid, sock->channel,
      sock->server ? android::bluetooth::SOCKET_ROLE_LISTEN
                   : android::bluetooth::SOCKET_ROLE_CONNECTION);

  if (!send_app_err_code(sock, p_close->reason)) {
    log::error("Unable to send l2cap socket to application socket_id:{}",
               sock->id);
  }
  // TODO: This does not seem to be called...
  // I'm not sure if this will be called for non-server sockets?
  if (sock->server) {
    BTA_JvFreeChannel(sock->channel, BTA_JV_CONN_TYPE_L2CAP);
  }
  btsock_l2cap_free_l(sock);
}

static void on_l2cap_outgoing_congest(tBTA_JV_L2CAP_CONG* p, uint32_t id) {
  l2cap_socket* sock;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  sock->outgoing_congest = p->cong ? 1 : 0;

  if (!sock->outgoing_congest) {
    log::verbose("Monitoring l2cap socket for outgoing data socket_id:{}",
                 sock->id);
    btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_RD,
                         sock->id);
  }
}

static void on_l2cap_write_done(uint16_t len, uint32_t id) {
  std::unique_lock<std::mutex> lock(state_lock);
  l2cap_socket* sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  int app_uid = sock->app_uid;
  if (!sock->outgoing_congest) {
    btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_RD,
                         sock->id);
  } else {
    log::info("Socket congestion on socket_id:{}", sock->id);
  }

  sock->tx_bytes += len;
  uid_set_add_tx(uid_set, app_uid, len);
}

static void on_l2cap_data_ind(tBTA_JV* evt, uint32_t id) {
  l2cap_socket* sock;

  int app_uid = -1;
  uint32_t bytes_read = 0;

  std::unique_lock<std::mutex> lock(state_lock);
  sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  app_uid = sock->app_uid;

  uint32_t count;

  if (BTA_JvL2capReady(sock->handle, &count) == BTA_JV_SUCCESS) {
    std::vector<uint8_t> buffer(count);
    if (BTA_JvL2capRead(sock->handle, sock->id, buffer.data(), count) ==
        BTA_JV_SUCCESS) {
      if (packet_put_tail_l(sock, buffer.data(), count)) {
        bytes_read = count;
        btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_WR,
                             sock->id);
      } else {  // connection must be dropped
        log::warn(
            "Closing socket as unable to push data to socket socket_id:{}",
            sock->id);
        BTA_JvL2capClose(sock->handle);
        btsock_l2cap_free_l(sock);
        return;
      }
    }
  }

  sock->rx_bytes += bytes_read;
  uid_set_add_rx(uid_set, app_uid, bytes_read);
}

static void btsock_l2cap_cbk(tBTA_JV_EVT event, tBTA_JV* p_data,
                             uint32_t l2cap_socket_id) {
  switch (event) {
    case BTA_JV_L2CAP_START_EVT:
      on_srv_l2cap_listen_started(&p_data->l2c_start, l2cap_socket_id);
      break;

    case BTA_JV_L2CAP_CL_INIT_EVT:
      on_cl_l2cap_init(&p_data->l2c_cl_init, l2cap_socket_id);
      break;

    case BTA_JV_L2CAP_OPEN_EVT:
      on_l2cap_connect(p_data, l2cap_socket_id);
      BTA_JvSetPmProfile(p_data->l2c_open.handle, BTA_JV_PM_ID_1,
                         BTA_JV_CONN_OPEN);
      break;

    case BTA_JV_L2CAP_CLOSE_EVT:
      on_l2cap_close(&p_data->l2c_close, l2cap_socket_id);
      break;

    case BTA_JV_L2CAP_DATA_IND_EVT:
      on_l2cap_data_ind(p_data, l2cap_socket_id);
      break;

    case BTA_JV_L2CAP_READ_EVT:
      break;

    case BTA_JV_L2CAP_WRITE_EVT:
      on_l2cap_write_done(p_data->l2c_write.len, l2cap_socket_id);
      break;

    case BTA_JV_L2CAP_CONG_EVT:
      on_l2cap_outgoing_congest(&p_data->l2c_cong, l2cap_socket_id);
      break;

    default:
      log::error("Unhandled event:{} l2cap_socket_id:{}", event,
                 l2cap_socket_id);
      break;
  }
}

const tL2CAP_ERTM_INFO obex_l2c_etm_opt = {L2CAP_FCR_ERTM_MODE,
                                           /* Mandatory for OBEX over l2cap */};

/**
 * When using a dynamic PSM, a PSM allocation is requested from
 * btsock_l2cap_listen_or_connect().
 * The PSM allocation event is refeived in the JV-callback - currently located
 * in RFC-code -
 * and this function is called with the newly allocated PSM.
 */
void on_l2cap_psm_assigned(int id, int psm) {
  /* Setup ETM settings:
   *  mtu will be set below */
  std::unique_lock<std::mutex> lock(state_lock);
  l2cap_socket* sock = btsock_l2cap_find_by_id_l(id);
  if (!sock) {
    log::error("Unable to find l2cap socket with socket_id:{}", id);
    return;
  }

  sock->channel = psm;

  btsock_l2cap_server_listen(sock);
}

static void btsock_l2cap_server_listen(l2cap_socket* sock) {
  int connection_type =
      sock->is_le_coc ? BTA_JV_CONN_TYPE_L2CAP_LE : BTA_JV_CONN_TYPE_L2CAP;

  /* If we have a channel specified in the request, just start the server,
   * else we request a PSM and start the server after we receive a PSM. */
  if (sock->channel <= 0) {
    BTA_JvGetChannelId(connection_type, sock->id, 0);
    return;
  }

  /* Setup ETM settings: mtu will be set below */
  std::unique_ptr<tL2CAP_CFG_INFO> cfg = std::make_unique<tL2CAP_CFG_INFO>(
      tL2CAP_CFG_INFO{.fcr_present = true, .fcr = kDefaultErtmOptions});

  std::unique_ptr<tL2CAP_ERTM_INFO> ertm_info;
  if (!sock->is_le_coc) {
    ertm_info.reset(new tL2CAP_ERTM_INFO(obex_l2c_etm_opt));
  }

  BTA_JvL2capStartServer(connection_type, sock->security, 0,
                         std::move(ertm_info), sock->channel, sock->rx_mtu,
                         std::move(cfg), btsock_l2cap_cbk, sock->id);
}

static bt_status_t btsock_l2cap_listen_or_connect(const char* name,
                                                  const RawAddress* addr,
                                                  int channel, int* sock_fd,
                                                  int flags, char listen,
                                                  int app_uid) {
  bool is_le_coc = (flags & BTSOCK_FLAG_LE_COC) != 0;

  if (!sock_fd) {
    log::info("Invalid socket descriptor");
    return BT_STATUS_PARM_INVALID;
  }

  if (!is_inited()) return BT_STATUS_NOT_READY;

  // TODO: This is kind of bad to lock here, but it is needed for the current
  // design.
  std::unique_lock<std::mutex> lock(state_lock);
  l2cap_socket* sock = btsock_l2cap_alloc_l(name, addr, listen, flags);
  if (!sock) {
    return BT_STATUS_NOMEM;
  }

  sock->channel = channel;
  sock->app_uid = app_uid;
  sock->is_le_coc = is_le_coc;
  sock->rx_mtu = is_le_coc ? L2CAP_SDU_LENGTH_LE_MAX : L2CAP_SDU_LENGTH_MAX;

  /* "role" is never initialized in rfcomm code */
  if (listen) {
    btsock_l2cap_server_listen(sock);
  } else {
      int connection_type =
          sock->is_le_coc ? BTA_JV_CONN_TYPE_L2CAP_LE : BTA_JV_CONN_TYPE_L2CAP;

      /* Setup ETM settings: mtu will be set below */
      std::unique_ptr<tL2CAP_CFG_INFO> cfg = std::make_unique<tL2CAP_CFG_INFO>(
          tL2CAP_CFG_INFO{.fcr_present = true, .fcr = kDefaultErtmOptions});

      std::unique_ptr<tL2CAP_ERTM_INFO> ertm_info;
      if (!sock->is_le_coc) {
        ertm_info.reset(new tL2CAP_ERTM_INFO(obex_l2c_etm_opt));
      }

      BTA_JvL2capConnect(
          connection_type, sock->security, 0, std::move(ertm_info), channel,
          sock->rx_mtu, std::move(cfg), sock->addr, btsock_l2cap_cbk, sock->id);
  }

  *sock_fd = sock->app_fd;
  /* We pass the FD to JAVA, but since it runs in another process, we need to
   * also close it in native, either straight away, as done when accepting an
   * incoming connection, or when doing cleanup after this socket */
  sock->app_fd = -1;
  /*This leaks the file descriptor. The FD should be closed in JAVA but it
   * apparently do not work */
  btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP,
                       SOCK_THREAD_FD_EXCEPTION, sock->id);

  return BT_STATUS_SUCCESS;
}

bt_status_t btsock_l2cap_listen(const char* name, int channel, int* sock_fd,
                                int flags, int app_uid) {
  return btsock_l2cap_listen_or_connect(name, NULL, channel, sock_fd, flags, 1,
                                        app_uid);
}

bt_status_t btsock_l2cap_connect(const RawAddress* bd_addr, int channel,
                                 int* sock_fd, int flags, int app_uid) {
  return btsock_l2cap_listen_or_connect(NULL, bd_addr, channel, sock_fd, flags,
                                        0, app_uid);
}

/* return true if we have more to send and should wait for user readiness, false
 * else
 * (for example: unrecoverable error or no data)
 */
static bool flush_incoming_que_on_wr_signal_l(l2cap_socket* sock) {
  uint8_t* buf;
  uint32_t len;

  while (packet_get_head_l(sock, &buf, &len)) {
    ssize_t sent;
    OSI_NO_INTR(sent = send(sock->our_fd, buf, len, MSG_DONTWAIT));
    int saved_errno = errno;

    if (sent == (signed)len)
      osi_free(buf);
    else if (sent >= 0) {
      packet_put_head_l(sock, buf + sent, len - sent);
      osi_free(buf);
      if (!sent) /* special case if other end not keeping up */
        return true;
    } else {
      packet_put_head_l(sock, buf, len);
      osi_free(buf);
      return saved_errno == EWOULDBLOCK || saved_errno == EAGAIN;
    }
  }

  return false;
}

inline BT_HDR* malloc_l2cap_buf(uint16_t len) {
  // We need FCS only for L2CAP_FCR_ERTM_MODE, but it's just 2 bytes so it's ok
  BT_HDR* msg = (BT_HDR*)osi_malloc(BT_HDR_SIZE + L2CAP_MIN_OFFSET + len +
                                    L2CAP_FCS_LENGTH);
  msg->offset = L2CAP_MIN_OFFSET;
  msg->len = len;
  return msg;
}

inline uint8_t* get_l2cap_sdu_start_ptr(BT_HDR* msg) {
  return (uint8_t*)(msg) + BT_HDR_SIZE + msg->offset;
}

void btsock_l2cap_signaled(int fd, int flags, uint32_t user_id) {
  char drop_it = false;

  /* We use MSG_DONTWAIT when sending data to JAVA, hence it can be accepted to
   * hold the lock. */
  std::unique_lock<std::mutex> lock(state_lock);
  l2cap_socket* sock = btsock_l2cap_find_by_id_l(user_id);
  if (!sock) return;

  if ((flags & SOCK_THREAD_FD_RD) && !sock->server) {
    // app sending data
    if (sock->connected) {
      int size = 0;
      bool ioctl_success = ioctl(sock->our_fd, FIONREAD, &size) == 0;
      if (!(flags & SOCK_THREAD_FD_EXCEPTION) || (ioctl_success && size)) {
        /* FIONREAD return number of bytes that are immediately available for
           reading, might be bigger than awaiting packet.

           BluetoothSocket.write(...) guarantees that any packet send to this
           socket is broken into pieces no bigger than MTU bytes (as requested
           by BT spec). */
        size = std::min(size, (int)sock->tx_mtu);

        BT_HDR* buffer = malloc_l2cap_buf(size);
        /* The socket is created with SOCK_SEQPACKET, hence we read one message
         * at the time. */
        ssize_t count;
        OSI_NO_INTR(count = recv(fd, get_l2cap_sdu_start_ptr(buffer), size,
                                 MSG_NOSIGNAL | MSG_DONTWAIT | MSG_TRUNC));
        if (count > sock->tx_mtu) {
          /* This can't happen thanks to check in BluetoothSocket.java but leave
           * this in case this socket is ever used anywhere else*/
          log::error("recv more than MTU. Data will be lost: {}", count);
          count = sock->tx_mtu;
        }

        /* When multiple packets smaller than MTU are flushed to the socket, the
           size of the single packet read could be smaller than the ioctl
           reported total size of awaiting packets. Hence, we adjust the buffer
           length. */
        buffer->len = count;

        // will take care of freeing buffer
        BTA_JvL2capWrite(sock->handle, PTR_TO_UINT(buffer), buffer, user_id);
      }
    } else
      drop_it = true;
  }
  if (flags & SOCK_THREAD_FD_WR) {
    // app is ready to receive more data, tell stack to enable the data flow
    if (flush_incoming_que_on_wr_signal_l(sock) && sock->connected)
      btsock_thread_add_fd(pth, sock->our_fd, BTSOCK_L2CAP, SOCK_THREAD_FD_WR,
                           sock->id);
  }
  if (drop_it || (flags & SOCK_THREAD_FD_EXCEPTION)) {
    int size = 0;
    if (drop_it || ioctl(sock->our_fd, FIONREAD, &size) != 0 || size == 0)
      btsock_l2cap_free_l(sock);
  }
}

bt_status_t btsock_l2cap_disconnect(const RawAddress* bd_addr) {
  if (!bd_addr) return BT_STATUS_PARM_INVALID;
  if (!is_inited()) return BT_STATUS_NOT_READY;

  std::unique_lock<std::mutex> lock(state_lock);
  l2cap_socket* sock = socks;

  while (sock) {
    l2cap_socket* next = sock->next;
    if (sock->addr == *bd_addr) {
      btsock_l2cap_free_l(sock);
    }
    sock = next;
  }

  return BT_STATUS_SUCCESS;
}

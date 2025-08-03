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

#pragma once

#include <hardware/bt_sock.h>

#include "btif_uid.h"
#include "types/raw_address.h"

enum {
  SOCKET_CONNECTION_STATE_UNKNOWN,
  // Socket acts as a server waiting for connection
  SOCKET_CONNECTION_STATE_LISTENING,
  // Socket acts as a client trying to connect
  SOCKET_CONNECTION_STATE_CONNECTING,
  // Socket is connected
  SOCKET_CONNECTION_STATE_CONNECTED,
  // Socket tries to disconnect from remote
  SOCKET_CONNECTION_STATE_DISCONNECTING,
  // This socket is closed
  SOCKET_CONNECTION_STATE_DISCONNECTED,
};

enum {
  SOCKET_ROLE_UNKNOWN,
  SOCKET_ROLE_LISTEN,
  SOCKET_ROLE_CONNECTION,
};

const btsock_interface_t* btif_sock_get_interface(void);

bt_status_t btif_sock_init(uid_set_t* uid_set);
void btif_sock_cleanup(void);

void btif_sock_connection_logger(int state, int role, const RawAddress& addr,
                                 int channel, const char* server_name);
void btif_sock_dump(int fd);

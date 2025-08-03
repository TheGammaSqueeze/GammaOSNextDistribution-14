/*
 * ANT Stack
 *
 * Copyright 2009 Dynastream Innovations
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/******************************************************************************\
*
*   FILE NAME:      ant_hciutils.h
*
*   BRIEF:
*      This file defines the utility functions for a Bluetooth HCI implementation
*
*
\******************************************************************************/

#ifndef __ANT_HCIUTILS_H
#define __ANT_HCIUTILS_H

#include <errno.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>

#include "ant_log.h"
// -------------------------------------------------

static inline int create_hci_sock() {
   struct sockaddr_hci sa;
   int sk = socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI);
   if (sk < 0)
   {
      ANT_ERROR("Failed to create bluetooth hci socket: %s", strerror(errno));
   }
   else
   {
      memset(&sa, 0, sizeof(sa));
      // sockaddr_hci changed from kernel 2.6.37 to 2.6.38 adding member hci_channel
      // In order to be backwards compatible set it to 0 if it exists (HCI_CHANNEL_RAW)
      sa.hci_family = AF_BLUETOOTH;
      sa.hci_dev = 0;
      if (bind(sk, (struct sockaddr *) &sa, sizeof(sa)) < 0)
      {
         ANT_ERROR("Failed to bind socket %#x to hci0: %s", sk, strerror(errno));
         close(sk);
         sk = -1;
      }
   }
   return sk;
}

#endif /* __ANT_HCIUTILS_H */



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
*   FILE NAME:      ant_rx.c
*
*   BRIEF:
*      This file implements the receive thread for a BlueZ HCI implementation
*      using Vendor Specific messages.
*
*
\******************************************************************************/

#define _GNU_SOURCE /* needed for PTHREAD_MUTEX_RECURSIVE */

#include <errno.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/un.h>

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>

#include "ant_types.h"

#if USE_EXTERNAL_POWER_LIBRARY
#include "antradio_power.h"
#endif

#include "ant_rx.h"
#include "ant_hciutils.h"
#include "ant_framing.h"
#include "ant_log.h"

#undef LOG_TAG
#define LOG_TAG "antradio_rx"

/* Global Options */
ANTHCIRxParams RxParams = {
   .pfRxCallback = NULL,
   .pfStateCallback = NULL,
   .thread = 0
};

extern pthread_mutex_t enableLock;
extern ANTRadioEnabledStatus get_and_set_radio_status(void);
#ifndef USE_EXTERNAL_POWER_LIBRARY
extern ANTRadioEnabledStatus radio_status;
#endif

/*
 * This thread opens a Bluez HCI socket and waits for ANT messages.
 */
void *ANTHCIRxThread(void *pvHCIDevice)
{
   int ret = ANT_STATUS_SUCCESS;
   int rxSocket;
   int len;
   unsigned char buf[HCI_MAX_EVENT_SIZE];
   int result;
   struct hci_filter eventVendorFilter;
   ANT_FUNC_START();

   (void)pvHCIDevice; //unused waring

   ANT_DEBUG_D("Entering ANTHCIRxThread");

   rxSocket = create_hci_sock();
   if (rxSocket < 0)
   {
      ANT_DEBUG_E("can't open HCI socket in rx thread: %s", strerror(errno));

      ret = ANT_STATUS_FAILED;
      goto out;
   }

   eventVendorFilter.type_mask = TYPE_MASK_EVENT_PACKET;
   eventVendorFilter.event_mask[0] = 0;
   eventVendorFilter.event_mask[1] = EVENT_MASK_1_EVENT_VENDOR;
   eventVendorFilter.opcode = htobs(ANT_EVENT_VENDOR_CODE);

   if (setsockopt(rxSocket, SOL_HCI, HCI_FILTER, &eventVendorFilter, sizeof(eventVendorFilter)) < 0)
   {
      ANT_ERROR("failed to set socket options: %s", strerror(errno));

      ret = ANT_STATUS_FAILED;
      goto close;
   }

   /* continue running as long as not terminated */
   while (get_and_set_radio_status() == RADIO_STATUS_ENABLED)
   {
      struct pollfd p;
      int n;

      p.fd = rxSocket;
      p.events = POLLIN;

      ANT_DEBUG_V("    RX: Polling HCI for data...");

      /* poll socket, wait for ANT messages */
      while ((n = poll(&p, 1, 2500)) == -1)
      {
         if (errno == EAGAIN || errno == EINTR)
            continue;

         ANT_ERROR("failed to poll socket: %s", strerror(errno));

         ret = ANT_STATUS_FAILED;
         goto close;
      }

      /* we timeout once in a while */
      /* this let's us the chance to check if we were terminated */
      if (0 == n)
      {
         ANT_DEBUG_V("    RX: Timeout");
         continue;
      }

      ANT_DEBUG_D("New HCI data available, reading...");

      /* read newly arrived data */
      /* TBD: rethink assumption about single arrival */
      while ((len = read(rxSocket, buf, sizeof(buf))) < 0)
      {
         if (errno == EAGAIN || errno == EINTR)
            continue;

         ANT_ERROR("failed to read socket: %s", strerror(errno));

         ret = ANT_STATUS_FAILED;
         goto close;
      }

      hci_event_packet_t *event_packet = (hci_event_packet_t *)buf;
      int hci_payload_len = validate_hci_event_packet(event_packet, len);
      if (hci_payload_len == -1)
      {
         // part of the message is incorrect, ignore it. validate_event_packet will log error
         continue;
      }

      ANT_SERIAL(event_packet->hci_payload, hci_payload_len, 'R');

      if(RxParams.pfRxCallback != NULL)
      {
         RxParams.pfRxCallback(hci_payload_len, event_packet->hci_payload);
      }
      else
      {
         ANT_ERROR("Can't send rx message - no callback registered");
      }
   }

close:
   result = pthread_mutex_trylock(&enableLock);
   ANT_DEBUG_D("rx thread close: trylock enableLock returned %d", result);

   if (result == 0)
   {
      ANT_DEBUG_W("rx thread socket has unexpectedly crashed");
#if USE_EXTERNAL_POWER_LIBRARY
      if (RxParams.pfStateCallback)
         RxParams.pfStateCallback(RADIO_STATUS_DISABLING);
      ant_disable();
      get_and_set_radio_status();
#else
      radio_status = RADIO_STATUS_DISABLED;
#endif
      RxParams.thread = 0;
      pthread_mutex_unlock(&enableLock);
   }
   else if (result == EBUSY)
   {
      ANT_DEBUG_V("rx thread socket was closed");
   }
   else
   {
      ANT_ERROR("rx thread close: trylock failed: %s", strerror(result));
   }

   if (-1 == close(rxSocket))
   {
      ANT_ERROR("failed to close hci device (socket handle=%#x): %s", rxSocket, strerror(errno));
   }
   else
   {
      ANT_DEBUG_D("closed hci device (socket handle=%#x)", rxSocket);
   }

out:
   ANT_FUNC_END();

   pthread_exit((void *)ret);

#if defined(ANDROID)
   return 0;
#endif
}


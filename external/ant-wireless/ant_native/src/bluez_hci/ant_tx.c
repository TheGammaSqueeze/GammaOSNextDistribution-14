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
*   FILE NAME:      ant_tx.c
*
*   BRIEF:
*      This file Implements the transmit functionality for a BlueZ HCI
*      implementation using Vendor Specific messages.
*
*
\******************************************************************************/

#include <errno.h>
#include <poll.h>
#include <sys/uio.h>

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>

#include "ant_types.h"
#include "ant_hciutils.h"
#include "ant_framing.h"
#include "ant_utils.h"
#include "ant_log.h"

#undef LOG_TAG
#define LOG_TAG "antradio_tx"

int g_ant_cmd_socket = -1;

int ant_open_tx_transport(void)
{
   int socket = -1;
   struct hci_filter commandCompleteFilter;
   ANT_FUNC_START();

   socket = create_hci_sock();

   if (socket < 0)
   {
      ANT_DEBUG_E("failed to open HCI socket for tx: %s", strerror(errno));
   }
   else
   {
      g_ant_cmd_socket = socket;
      ANT_DEBUG_D("socket handle %#x", socket);

      commandCompleteFilter.type_mask = TYPE_MASK_EVENT_PACKET;
      commandCompleteFilter.event_mask[0] = EVENT_MASK_0_COMMAND_COMPLETE;
      commandCompleteFilter.event_mask[1] = 0;
      commandCompleteFilter.opcode = htobs(ANT_COMMAND_OPCODE);

      if (setsockopt(socket, SOL_HCI, HCI_FILTER, &commandCompleteFilter, sizeof(commandCompleteFilter)) < 0)
      {
         ANT_ERROR("failed to set socket options: %s", strerror(errno));
         close(socket);
         socket = -1;
      }
   }

   ANT_FUNC_END();
   return socket;
}

void ant_close_tx_transport(int socket)
{
   ANT_FUNC_START();

   if(0 < socket)
   {
      if (0 == close(socket))
      {
         ANT_DEBUG_D("closed hci device (socket handle=%#x)", socket);
      }
      else
      {
         ANT_ERROR("failed to close hci device (socket handle=%#x): %s", socket, strerror(errno));
      }
   }
   else
   {
      ANT_DEBUG_E("requested close on socket %#x. invalid param", socket);
   }

   ANT_FUNC_END();
}

/* 
Format of an HCI WRITE command to ANT chip:

HCI Header:
----------
- HCI Packet Type:                                                      1 byte

- HCI Opcode:                                                           2 bytes
                                                               (LSB, MSB - LE)
- HCI Parameters Total Len (total length of all subsequent fields):     1 byte

HCI Parameters:
--------------
- VS Parameters Len (total length of ANT Mesg inculding Len/ID)         2 bytes
                                                               (LSB, MSB - LE)
- ANT Mesg Len (N = number of bytes in ANT Mesg Data):                  1 byte
- ANT Mesg ID:                                                          1 byte
- ANT Mesg Data:                                                        N bytes 
*/

ANT_BOOL wait_for_message(int socket)
{
   struct pollfd p;
   int n;

   ANT_BOOL bReturn = ANT_FALSE;
   ANT_BOOL bRetry = ANT_FALSE;

   ANT_FUNC_START();

   p.fd = socket;
   p.events = POLLIN;

   do
   {
      bRetry = ANT_FALSE;
   
      ANT_DEBUG_V("    CC: Polling HCI for data...");
   
      /* poll socket, wait for ANT messages */
      n = poll(&p, 1, 2500);
      if (0 > n)
      {
         if (errno == EAGAIN || errno == EINTR)
         {
            ANT_DEBUG_W("    CC: error: %s", strerror(errno));
            bRetry = ANT_TRUE;
         }
         else
         {
            ANT_ERROR("failed to poll socket. error: %s", strerror(errno));
         }
      }

      /* timeout */
      else if (0 == n)
      {
         ANT_ERROR("SERIOUS: Timeouted getting Command Complete");
      }
      else if(0 < n)
      {
         // There is data to read.
         bReturn = ANT_TRUE;
      }

   } while(ANT_TRUE == bRetry);

   ANT_FUNC_END();
   
   return bReturn;
}

ANTStatus write_data(ANT_U8 ant_message[], int ant_message_len)
{
   ANTStatus ret = ANT_STATUS_FAILED;
   ANT_BOOL retry = ANT_FALSE;
   int bytes_written;
   struct iovec iov[2];
   hci_command_vendor_header_t hci_header;

   ANT_FUNC_START();

   create_command_header(&hci_header, ant_message_len);

   iov[0].iov_base = &hci_header;
   iov[0].iov_len = sizeof(hci_header);
   iov[1].iov_base = ant_message;
   iov[1].iov_len = ant_message_len;

   do //while retry
   {
      retry = ANT_FALSE;

      if (g_ant_cmd_socket < 0)
      {
         ANT_DEBUG_E("bad socket handle %#x", g_ant_cmd_socket);
         return ANT_STATUS_INTERNAL_ERROR;
      }

      ANT_SERIAL(ant_message, ant_message_len, 'T');

      bytes_written = writev(g_ant_cmd_socket, iov, 2);

      ANT_DEBUG_D("writing to socket %#x returned %d", g_ant_cmd_socket, 
                                                               bytes_written);

//      bytes_written < 0                   = error (check errno)
//      bytes_written = 0                   = No data written
//      bytes_written < sizeof(hci_message) = not all data written
//      bytes_written = sizeof(hci_message) = all data written

      if(bytes_written < 0)
      {
         ANT_ERROR("write to HCI failed: %s", strerror(errno));

         if (errno == EAGAIN || errno == EINTR)
         {
            ANT_DEBUG_D("Retrying write to HCI");
            retry = ANT_TRUE;
         }
         else
         {
            ret = ANT_STATUS_FAILED;
         }
      }
      else if(bytes_written < ((int)sizeof(hci_header) + ant_message_len))
      {
         ANT_DEBUG_D("Only %d bytes written to HCI.", bytes_written);
         ret = ANT_STATUS_FAILED;
      }
      else
      {
         ANT_DEBUG_V("writev successful");
         ret = ANT_STATUS_SUCCESS;
      }
   } while(retry);

   ANT_FUNC_END();
 
   return ret;
}

// Returns:
//  ANT_STATUS_NO_VALUE_AVAILABLE          if not a CC packet
//  ANT_STATUS_FAILED                      if could not read socket or not a 
//                                                       valid length CC packet
//  ANT_STATUS_TRANSPORT_UNSPECIFIED_ERROR if CC indicates an unspecified error
//  ANT_STATUS_COMMAND_WRITE_FAILED        if CC indicates a failure
//  ANT_STATUS_SUCCESS                     if CC indicates message was received 
ANTStatus get_command_complete_result(int socket)
{
   ANTStatus status = ANT_STATUS_NO_VALUE_AVAILABLE;
   int len;
   ANT_U8 ucResult = -1;
   ANT_U8 buf[HCI_MAX_EVENT_SIZE];

   ANT_FUNC_START();
   ANT_DEBUG_V("reading off socket %#x", socket);

   /* read newly arrived data */
   while ((len = read(socket, buf, sizeof(buf))) < 0)
   {
      if (errno == EAGAIN || errno == EINTR)
         continue;

      ANT_ERROR("failed to read socket. error: %s", strerror(errno));

      status = ANT_STATUS_FAILED;
      goto close;
   }

   ANT_SERIAL(buf, len, 'C');

   // validate that we have a single command complete packet
   if (len != sizeof(hci_command_complete_packet_t))
   {
       status = ANT_STATUS_FAILED;
   }
   else
   {
      hci_command_complete_packet_t *command_complete = (hci_command_complete_packet_t *)buf;

      if(command_complete->packet_type == HCI_EVENT_PKT)
      {
         ANT_DEBUG_D("Received Event Packet");

         if(command_complete->event_header.evt == EVT_CMD_COMPLETE)
         {
            ANT_U16 opcode = ANT_UTILS_LEtoHost16((ANT_U8 *)&command_complete->command_complete_hdr.opcode);
            if(opcode == ANT_COMMAND_OPCODE)
            {
               ANT_DEBUG_V("Received COMMAND COMPLETE");
               ucResult = command_complete->response;

               if(ucResult == 0)
               {
                  ANT_DEBUG_D("Command Complete = SUCCESS");
                  status = ANT_STATUS_SUCCESS;
               }
               else if(ucResult == HCI_UNSPECIFIED_ERROR)
               {
                  ANT_DEBUG_D("Command Complete = UNSPECIFIED_ERROR");
                  status = ANT_STATUS_TRANSPORT_UNSPECIFIED_ERROR;
               }
               else
               {
                  status = ANT_STATUS_COMMAND_WRITE_FAILED;
                  ANT_DEBUG_D("Command Complete = WRITE_FAILED");
               }
            }
            else
            {
               ANT_DEBUG_W("Command complete has wrong opcode, this should have been filtered out");
            }
         }
         else
         {
            ANT_DEBUG_W("Event is not a command complete, this should have been filtered out");
         }
      }
      else
      {
         ANT_DEBUG_W("Other Event Packet, this should have been filtered out");
      }
   }

close:
   ANT_FUNC_END();
   return status;
}


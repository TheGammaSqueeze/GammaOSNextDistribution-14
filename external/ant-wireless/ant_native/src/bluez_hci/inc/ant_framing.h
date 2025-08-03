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
*   FILE NAME:      ant_framing.h
*
*   BRIEF:
*      This file defines ANT specific HCI values used by the ANT chip with a
*      shared BlueZ HCI transport.
*
*
\******************************************************************************/

#ifndef __ANT_HCIFRAMING_H
#define __ANT_HCIFRAMING_H

#if defined(BOARD_ANT_DEVICE_WL12XX)

#define ANT_COMMAND_OPCODE                         0xFDD1
#define ANT_EVENT_VENDOR_CODE                      0x0500

typedef struct {
   ANT_U16                    vendor_msg_len;
} __attribute__ ((packed)) hci_cmd_vendor_hdr_t;
#define HCI_COMMAND_VENDOR_HDR_SIZE                2

typedef struct {
   ANT_U16                    vendor_code;
   ANT_U16                    vendor_msg_len;
} __attribute__ ((packed)) hci_evt_vendor_hdr_t;
#define HCI_EVENT_VENDOR_HDR_SIZE                  4

#elif defined(BOARD_ANT_DEVICE_BCM433X)

#define ANT_COMMAND_OPCODE                         0xFCEC
#define ANT_EVENT_VENDOR_CODE                      0x2D

typedef struct {
   ANT_U8                     command_id_padding;
} __attribute__ ((packed)) hci_cmd_vendor_hdr_t;
#define HCI_COMMAND_VENDOR_HDR_SIZE                1

typedef struct {
   ANT_U8                     vendor_code;
   ANT_U8                     status;
} __attribute__ ((packed)) hci_evt_vendor_hdr_t;
#define HCI_EVENT_VENDOR_HDR_SIZE                  2

#define  COMMAND_ID_PADDING                        0xFF

#endif

#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>

#include "ant_native.h"
#include "ant_types.h"
#include "ant_utils.h"

#define HCI_EVENT_HEADER_OVERHEAD (HCI_TYPE_LEN + HCI_EVENT_HDR_SIZE)
#define HCI_EVENT_TOTAL_OVERHEAD (HCI_EVENT_HEADER_OVERHEAD + HCI_EVENT_VENDOR_HDR_SIZE)

#define TYPE_MASK_EVENT_PACKET                     0x00000010
#define EVENT_MASK_0_COMMAND_COMPLETE              0x00004000
#define EVENT_MASK_1_EVENT_VENDOR                  0x80000000

// -------------------------------------------------

typedef struct {
   ANT_U8                     packet_type;         // HCI_COMMAND_PKT
   hci_command_hdr            command_header;      // from hci.h
   hci_cmd_vendor_hdr_t       vendor_header;
} __attribute__ ((packed)) hci_command_vendor_header_t;


typedef struct {
   ANT_U8                     packet_type;         // HCI_EVENT_PKT
   hci_event_hdr              event_header;        // from hci.h
   hci_evt_vendor_hdr_t       vendor_header;
   ANT_U8                     hci_payload[HCI_MAX_EVENT_SIZE];
} __attribute__ ((packed)) hci_event_packet_t;

typedef struct {
   ANT_U8                     packet_type;         // HCI_EVENT_PKT
   hci_event_hdr              event_header;        // from hci.h
   evt_cmd_complete           command_complete_hdr;// from hci.h
   ANT_U8                     response;
} __attribute__ ((packed)) hci_command_complete_packet_t;

// -------------------------------------------------

/**
 * @param header the struct to fill with chip specific hci header
 * @param ant_message_len total ant length, size, id, and data
 */
static inline void create_command_header(hci_command_vendor_header_t *header, ANT_U8 ant_message_len)
{
   header->packet_type = HCI_COMMAND_PKT;
   header->command_header.opcode = htobs(ANT_COMMAND_OPCODE);
   header->command_header.plen = ant_message_len + HCI_COMMAND_VENDOR_HDR_SIZE;
#if defined(BOARD_ANT_DEVICE_WL12XX)
   header->vendor_header.vendor_msg_len = htobs(ant_message_len);
#elif defined(BOARD_ANT_DEVICE_BCM433X)
   header->vendor_header.command_id_padding = COMMAND_ID_PADDING;
#endif
}

/**
 * @param packet the struct to check if is an ANT hci payload
 * @param packet_len the total length of the packet
 * @return 0 if valid, -1 if not valid
 */
static inline int validate_hci_event_vendor(hci_event_packet_t *packet, int packet_len)
{
#if defined(BOARD_ANT_DEVICE_WL12XX)
   ANT_U16 vendor_code = ANT_UTILS_LEtoHost16((ANT_U8 *)&packet->vendor_header.vendor_code);
#elif defined(BOARD_ANT_DEVICE_BCM433X)
   ANT_U8 vendor_code = packet->vendor_header.vendor_code;
#endif
   if (vendor_code != ANT_EVENT_VENDOR_CODE) {
      ANT_ERROR("HCI packet vendor event code is not ANT");
      return -1;
   }

#if defined(BOARD_ANT_DEVICE_WL12XX)
   ANT_U16 vendor_msg_len = ANT_UTILS_LEtoHost16((ANT_U8 *)&packet->vendor_header.vendor_msg_len);
   if (packet_len != vendor_msg_len + HCI_EVENT_TOTAL_OVERHEAD) {
      ANT_ERROR("HCI packet length (%d) should be %d bytes more than HCI payload length (%d)",
              packet_len, HCI_EVENT_TOTAL_OVERHEAD, vendor_msg_len);
      return -1;
   }
#elif defined(BOARD_ANT_DEVICE_BCM433X)
   (void)packet_len; // unused warning
#endif

   return 0;
}

/**
 * @param packet the struct to validate
 * @param packet_len the total length of the packet
 * @return -1 if not valid, or length of the ANT hci payload
 */
static inline int validate_hci_event_packet(hci_event_packet_t *packet, int packet_len)
{
   // Make sure we have at the minimum the hci header, vendor header, and ANT size/id bytes
   if (packet_len < HCI_EVENT_TOTAL_OVERHEAD) {
      ANT_ERROR("HCI packet length (%d) is less than minimum (%d)",
              packet_len, HCI_EVENT_TOTAL_OVERHEAD);
      return -1;
   }

   if (packet->packet_type != HCI_EVENT_PKT) {
      ANT_ERROR("HCI packet is not an event packet");
      return -1;
   }

   if (packet->event_header.evt != EVT_VENDOR) {
      ANT_ERROR("HCI packet is not a vendor event");
      return -1;
   }

   if (packet_len != packet->event_header.plen + HCI_EVENT_HEADER_OVERHEAD) {
      ANT_ERROR("HCI packet length (%d) should be %d bytes more than HCI event length (%d)",
              packet_len, HCI_EVENT_HDR_SIZE, packet->event_header.plen);
      return -1;
   }

   if (validate_hci_event_vendor(packet, packet_len) == -1)
   {
      return -1;
   }
   else
   {
      // return HCI payload len
      return packet_len - HCI_EVENT_TOTAL_OVERHEAD;
   }
}

#endif /* __ANT_HCIFRAMING_H */

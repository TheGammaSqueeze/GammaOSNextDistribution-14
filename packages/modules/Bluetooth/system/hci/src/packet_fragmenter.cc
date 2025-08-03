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

#define LOG_TAG "bt_hci_packet_fragmenter"

#include "packet_fragmenter.h"

#include <base/logging.h>
#include <string.h>

#include <unordered_map>

#include "hci/include/buffer_allocator.h"
#include "hci/include/hci_layer.h"
#include "include/check.h"
#include "internal_include/bt_target.h"
#include "os/log.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/bt_types.h"

#define HCI_ISO_BF_FIRST_FRAGMENTED_PACKET (0)
#define HCI_ISO_BF_CONTINUATION_FRAGMENT_PACKET (1)
#define HCI_ISO_BF_COMPLETE_PACKET (2)
#define HCI_ISO_BF_LAST_FRAGMENT_PACKET (3)

#define HCI_ISO_HEADER_TIMESTAMP_SIZE (4)
#define HCI_ISO_HEADER_ISO_LEN_SIZE (2)
#define HCI_ISO_HEADER_PACKET_SEQ_SIZE (2)

// ISO
// 2 bytes for handle, 2 bytes for data length (Volume 2, Part E, 5.4.5)
#define HCI_ISO_PREAMBLE_SIZE 4

#define HCI_ISO_HEADER_LEN_WITHOUT_TS \
  (HCI_ISO_HEADER_ISO_LEN_SIZE + HCI_ISO_HEADER_PACKET_SEQ_SIZE)
#define HCI_ISO_HEADER_LEN_WITH_TS \
  (HCI_ISO_HEADER_LEN_WITHOUT_TS + HCI_ISO_HEADER_TIMESTAMP_SIZE)

#define HCI_ISO_SET_CONTINUATION_FLAG(handle) \
  (((handle)&0x4FFF) | (0x0001 << 12))
#define HCI_ISO_SET_COMPLETE_FLAG(handle) (((handle)&0x4FFF) | (0x0002 << 12))
#define HCI_ISO_SET_END_FRAG_FLAG(handle) (((handle)&0x4FFF) | (0x0003 << 12))
#define HCI_ISO_SET_TIMESTAMP_FLAG(handle) (((handle)&0x3FFF) | (0x0001 << 14))

#define HCI_ISO_GET_TS_FLAG(handle) (((handle) >> 14) & 0x0001)
#define HCI_ISO_GET_PACKET_STATUS_FLAGS(iso_sdu_length) \
  (iso_sdu_length & 0xC000)
#define HCI_ISO_SDU_LENGTH_MASK 0x0FFF

#define APPLY_CONTINUATION_FLAG(handle) (((handle)&0xCFFF) | 0x1000)
#define APPLY_START_FLAG(handle) (((handle)&0xCFFF) | 0x2000)
#define SUB_EVENT(event) ((event)&MSG_SUB_EVT_MASK)
#define GET_BOUNDARY_FLAG(handle) (((handle) >> 12) & 0x0003)
#define GET_BROADCAST_FLAG(handle) (((handle) >> 14) & 0x0003)

#define HANDLE_MASK 0x0FFF
#define START_PACKET_BOUNDARY 2
#define POINT_TO_POINT 0
#define L2CAP_HEADER_PDU_LEN_SIZE 2
#define L2CAP_HEADER_CID_SIZE 2
#define L2CAP_HEADER_SIZE (L2CAP_HEADER_PDU_LEN_SIZE + L2CAP_HEADER_CID_SIZE)

// Our interface and callbacks

static const allocator_t* buffer_allocator;
static const packet_fragmenter_callbacks_t* callbacks;

static std::unordered_map<uint16_t /* handle */, BT_HDR*> partial_iso_packets;

static void init(const packet_fragmenter_callbacks_t* result_callbacks) {
  callbacks = result_callbacks;
}

static void cleanup() { partial_iso_packets.clear(); }

static void fragment_and_dispatch(BT_HDR* packet, uint16_t max_data_size) {
  CHECK(packet != NULL);

  uint16_t event = packet->event & MSG_EVT_MASK;

  CHECK(event == MSG_STACK_TO_HC_HCI_ISO);

  uint8_t* stream = packet->data + packet->offset;
  uint16_t max_packet_size = max_data_size + HCI_ISO_PREAMBLE_SIZE;
  uint16_t remaining_length = packet->len;

  uint16_t handle;
  STREAM_TO_UINT16(handle, stream);

  if (packet->layer_specific & BT_ISO_HDR_CONTAINS_TS) {
    // First packet might have timestamp
    handle = HCI_ISO_SET_TIMESTAMP_FLAG(handle);
  }

  if (remaining_length <= max_packet_size) {
    stream = packet->data + packet->offset;
    UINT16_TO_STREAM(stream, HCI_ISO_SET_COMPLETE_FLAG(handle));
  } else {
    while (remaining_length > max_packet_size) {
      // Make sure we use the right ISO packet size
      stream = packet->data + packet->offset;
      STREAM_SKIP_UINT16(stream);
      UINT16_TO_STREAM(stream, max_data_size);

      packet->len = max_packet_size;
      callbacks->fragmented(packet, false);

      packet->offset += max_data_size;
      remaining_length -= max_data_size;
      packet->len = remaining_length;

      // Write the ISO header for the next fragment
      stream = packet->data + packet->offset;
      if (remaining_length > max_packet_size) {
        UINT16_TO_STREAM(stream,
                         HCI_ISO_SET_CONTINUATION_FLAG(handle & HANDLE_MASK));
      } else {
        UINT16_TO_STREAM(stream,
                         HCI_ISO_SET_END_FRAG_FLAG(handle & HANDLE_MASK));
      }
      UINT16_TO_STREAM(stream, remaining_length - HCI_ISO_PREAMBLE_SIZE);
    }
  }
  callbacks->fragmented(packet, true);
}

static void reassemble_and_dispatch(BT_HDR* packet) {
  uint8_t* stream = packet->data;
  uint16_t handle;
  uint16_t iso_length;
  uint8_t iso_hdr_len = HCI_ISO_HEADER_LEN_WITHOUT_TS;
  BT_HDR* partial_packet;
  uint16_t iso_full_len;

  uint16_t event = packet->event & MSG_EVT_MASK;
  CHECK(event == MSG_HC_TO_STACK_HCI_ISO);

  STREAM_TO_UINT16(handle, stream);
  STREAM_TO_UINT16(iso_length, stream);
  // last 2 bits is RFU
  iso_length = iso_length & 0x3FFF;

  CHECK(iso_length == packet->len - HCI_ISO_PREAMBLE_SIZE);

  uint8_t boundary_flag = GET_BOUNDARY_FLAG(handle);
  uint8_t ts_flag = HCI_ISO_GET_TS_FLAG(handle);
  handle = handle & HANDLE_MASK;

  auto map_iter = partial_iso_packets.find(handle);

  switch (boundary_flag) {
    case HCI_ISO_BF_COMPLETE_PACKET:
    case HCI_ISO_BF_FIRST_FRAGMENTED_PACKET:
      uint16_t iso_sdu_length;
      uint8_t packet_status_flags;

      if (map_iter != partial_iso_packets.end()) {
        LOG_WARN(
            "%s found unfinished packet for the iso handle with start packet. "
            "Dropping old.",
            __func__);
        BT_HDR* hdl = map_iter->second;
        partial_iso_packets.erase(map_iter);
        buffer_allocator->free(hdl);
      }

      if (ts_flag) {
        /* Skip timestamp u32 */
        STREAM_SKIP_UINT32(stream);
        packet->layer_specific |= BT_ISO_HDR_CONTAINS_TS;
        iso_hdr_len = HCI_ISO_HEADER_LEN_WITH_TS;
      }

      if (iso_length < iso_hdr_len) {
        LOG_WARN("%s ISO packet too small (%d < %d). Dropping it.", __func__,
                 packet->len, iso_hdr_len);
        buffer_allocator->free(packet);
        return;
      }

      /* Skip packet_seq. */
      STREAM_SKIP_UINT16(stream);
      STREAM_TO_UINT16(iso_sdu_length, stream);

      /* Silently ignore empty report if there's no 'lost data' flag set. */
      if (iso_sdu_length == 0) {
        buffer_allocator->free(packet);
        return;
      }

      packet_status_flags = HCI_ISO_GET_PACKET_STATUS_FLAGS(iso_sdu_length);
      iso_sdu_length = iso_sdu_length & HCI_ISO_SDU_LENGTH_MASK;

      if (packet_status_flags)
        LOG_ERROR("%s packet status flags: 0x%02x", __func__,
                  packet_status_flags);

      iso_full_len = iso_sdu_length + iso_hdr_len + HCI_ISO_PREAMBLE_SIZE;
      if ((iso_full_len + sizeof(BT_HDR)) > BT_DEFAULT_BUFFER_SIZE) {
        LOG_ERROR("%s Dropping ISO packet with invalid length (%d).", __func__,
                  iso_sdu_length);
        buffer_allocator->free(packet);
        return;
      }

      if (((boundary_flag == HCI_ISO_BF_COMPLETE_PACKET) &&
           (iso_full_len != packet->len)) ||
          ((boundary_flag == HCI_ISO_BF_FIRST_FRAGMENTED_PACKET) &&
           (iso_full_len <= packet->len))) {
        LOG_ERROR("%s corrupted ISO frame", __func__);
        buffer_allocator->free(packet);
        return;
      }

      partial_packet =
          (BT_HDR*)buffer_allocator->alloc(iso_full_len + sizeof(BT_HDR));
      if (!partial_packet) {
        LOG_ERROR("%s cannot allocate partial packet", __func__);
        buffer_allocator->free(packet);
        return;
      }

      partial_packet->event = packet->event;
      partial_packet->len = iso_full_len;
      partial_packet->layer_specific = packet->layer_specific;

      memcpy(partial_packet->data, packet->data, packet->len);

      // Update the ISO data size to indicate the full expected length
      stream = partial_packet->data;
      STREAM_SKIP_UINT16(stream);  // skip the ISO handle
      UINT16_TO_STREAM(stream, iso_full_len - HCI_ISO_PREAMBLE_SIZE);

      if (boundary_flag == HCI_ISO_BF_FIRST_FRAGMENTED_PACKET) {
        partial_packet->offset = packet->len;
        partial_iso_packets[handle] = partial_packet;
      } else {
        packet->layer_specific |= BT_ISO_HDR_OFFSET_POINTS_DATA;
        partial_packet->offset = iso_hdr_len + HCI_ISO_PREAMBLE_SIZE;
        callbacks->reassembled(partial_packet);
      }

      buffer_allocator->free(packet);
      break;

    case HCI_ISO_BF_CONTINUATION_FRAGMENT_PACKET:
      // pass-through
    case HCI_ISO_BF_LAST_FRAGMENT_PACKET:
      if (map_iter == partial_iso_packets.end()) {
        LOG_WARN("%s got continuation for unknown packet. Dropping it.",
                 __func__);
        buffer_allocator->free(packet);
        return;
      }

      partial_packet = map_iter->second;
      if (partial_packet->len <
          (partial_packet->offset + packet->len - HCI_ISO_PREAMBLE_SIZE)) {
        LOG_ERROR(
            "%s got packet which would exceed expected length of %d. "
            "dropping full packet",
            __func__, partial_packet->len);
        buffer_allocator->free(packet);
        partial_iso_packets.erase(map_iter);
        buffer_allocator->free(partial_packet);
        return;
      }

      memcpy(partial_packet->data + partial_packet->offset,
             packet->data + HCI_ISO_PREAMBLE_SIZE,
             packet->len - HCI_ISO_PREAMBLE_SIZE);

      if (boundary_flag == HCI_ISO_BF_CONTINUATION_FRAGMENT_PACKET) {
        partial_packet->offset += packet->len - HCI_ISO_PREAMBLE_SIZE;
        buffer_allocator->free(packet);
        return;
      }

      if (partial_packet->len !=
          partial_packet->offset + packet->len - HCI_ISO_PREAMBLE_SIZE) {
        LOG_ERROR(
            "%s got last fragment, but it doesn't fill up the whole packet of "
            "size %d",
            __func__, partial_packet->len);
        buffer_allocator->free(packet);
        partial_iso_packets.erase(map_iter);
        buffer_allocator->free(partial_packet);
        return;
      }

      partial_packet->layer_specific |= BT_ISO_HDR_OFFSET_POINTS_DATA;
      partial_packet->offset = HCI_ISO_PREAMBLE_SIZE;
      if (partial_packet->layer_specific & BT_ISO_HDR_CONTAINS_TS)
        partial_packet->offset += HCI_ISO_HEADER_LEN_WITH_TS;
      else
        partial_packet->offset += HCI_ISO_HEADER_LEN_WITHOUT_TS;

      buffer_allocator->free(packet);

      partial_iso_packets.erase(map_iter);
      callbacks->reassembled(partial_packet);

      break;
    default:
      LOG_ERROR("%s Unexpected packet, dropping full packet", __func__);
      buffer_allocator->free(packet);
      break;
  }
}

static const packet_fragmenter_t interface = {init, cleanup,

                                              fragment_and_dispatch,
                                              reassemble_and_dispatch};

const packet_fragmenter_t* packet_fragmenter_get_interface() {
  buffer_allocator = buffer_allocator_get_interface();
  return &interface;
}

/******************************************************************************
 *
 *  Copyright 1999-2012 Broadcom Corporation
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

#ifndef BT_TYPES_H
#define BT_TYPES_H

#include <stdbool.h>

#include "stack/include/bt_device_type.h"
#include "stack/include/bt_hdr.h"
#ifdef __cplusplus
#include "include/hardware/bluetooth.h"
#include "stack/include/bt_octets.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"
#endif  // __cplusplus

/* READ WELL !!
 *
 * This section defines global events. These are events that cross layers.
 * Any event that passes between layers MUST be one of these events. Tasks
 * can use their own events internally, but a FUNDAMENTAL design issue is
 * that global events MUST be one of these events defined below.
 *
 * The convention used is the the event name contains the layer that the
 * event is going to.
 */
#define BT_EVT_MASK 0xFF00
#define BT_SUB_EVT_MASK 0x00FF
/* To Bluetooth Upper Layers        */
/************************************/
/* HCI Event                        */
#define BT_EVT_TO_BTU_HCI_EVT 0x1000
/* SCO Data from HCI                */
#define BT_EVT_TO_BTU_HCI_SCO 0x1200
/* HCI Transport Error              */
#define BT_EVT_TO_BTU_HCIT_ERR 0x1300

/* Serial Port Data                 */
#define BT_EVT_TO_BTU_SP_DATA 0x1500

/* ISO Data from HCI                */
#define BT_EVT_TO_BTU_HCI_ISO 0x1700

/* To LM                            */
/************************************/
/* HCI Command                      */
#define BT_EVT_TO_LM_HCI_CMD 0x2000
/* HCI ACL Data                     */
#define BT_EVT_TO_LM_HCI_ACL 0x2100
/* HCI SCO Data                     */
#define BT_EVT_TO_LM_HCI_SCO 0x2200
/* HCI ISO Data                     */
#define BT_EVT_TO_LM_HCI_ISO 0x2d00

#define BT_EVT_HCISU 0x5000

/* BTIF Events */
#define BT_EVT_BTIF 0xA000
#define BT_EVT_CONTEXT_SWITCH_EVT (0x0001 | BT_EVT_BTIF)

/* ISO Layer specific */
#define BT_ISO_HDR_CONTAINS_TS (0x0001)
#define BT_ISO_HDR_OFFSET_POINTS_DATA (0x0002)

/*******************************************************************************
 * Macros to get and put bytes to and from a stream (Little Endian format).
 */
#define UINT64_TO_BE_STREAM(p, u64)  \
  {                                  \
    *(p)++ = (uint8_t)((u64) >> 56); \
    *(p)++ = (uint8_t)((u64) >> 48); \
    *(p)++ = (uint8_t)((u64) >> 40); \
    *(p)++ = (uint8_t)((u64) >> 32); \
    *(p)++ = (uint8_t)((u64) >> 24); \
    *(p)++ = (uint8_t)((u64) >> 16); \
    *(p)++ = (uint8_t)((u64) >> 8);  \
    *(p)++ = (uint8_t)(u64);         \
  }
#define UINT32_TO_STREAM(p, u32)     \
  {                                  \
    *(p)++ = (uint8_t)(u32);         \
    *(p)++ = (uint8_t)((u32) >> 8);  \
    *(p)++ = (uint8_t)((u32) >> 16); \
    *(p)++ = (uint8_t)((u32) >> 24); \
  }
#define UINT24_TO_STREAM(p, u24)     \
  {                                  \
    *(p)++ = (uint8_t)(u24);         \
    *(p)++ = (uint8_t)((u24) >> 8);  \
    *(p)++ = (uint8_t)((u24) >> 16); \
  }
#define UINT16_TO_STREAM(p, u16)    \
  {                                 \
    *(p)++ = (uint8_t)(u16);        \
    *(p)++ = (uint8_t)((u16) >> 8); \
  }
#define UINT8_TO_STREAM(p, u8) \
  { *(p)++ = (uint8_t)(u8); }
#define INT8_TO_STREAM(p, u8) \
  { *(p)++ = (int8_t)(u8); }
#define ARRAY16_TO_STREAM(p, a)                                     \
  {                                                                 \
    int ijk;                                                        \
    for (ijk = 0; ijk < 16; ijk++) *(p)++ = (uint8_t)(a)[15 - ijk]; \
  }
#define ARRAY8_TO_STREAM(p, a)                                    \
  {                                                               \
    int ijk;                                                      \
    for (ijk = 0; ijk < 8; ijk++) *(p)++ = (uint8_t)(a)[7 - ijk]; \
  }
#define LAP_TO_STREAM(p, a)                     \
  {                                             \
    int ijk;                                    \
    for (ijk = 0; ijk < LAP_LEN; ijk++)         \
      *(p)++ = (uint8_t)(a)[LAP_LEN - 1 - ijk]; \
  }
#define ARRAY_TO_STREAM(p, a, len)                                \
  {                                                               \
    int ijk;                                                      \
    for (ijk = 0; ijk < (len); ijk++) *(p)++ = (uint8_t)(a)[ijk]; \
  }
#define STREAM_TO_INT8(u8, p)   \
  {                             \
    (u8) = (*((int8_t*)(p)));   \
    (p) += 1;                   \
  }
#define STREAM_TO_UINT8(u8, p) \
  {                            \
    (u8) = (uint8_t)(*(p));    \
    (p) += 1;                  \
  }
#define STREAM_TO_UINT16(u16, p)                                  \
  {                                                               \
    (u16) = ((uint16_t)(*(p)) + (((uint16_t)(*((p) + 1))) << 8)); \
    (p) += 2;                                                     \
  }
#define STREAM_TO_UINT24(u32, p)                                      \
  {                                                                   \
    (u32) = (((uint32_t)(*(p))) + ((((uint32_t)(*((p) + 1)))) << 8) + \
             ((((uint32_t)(*((p) + 2)))) << 16));                     \
    (p) += 3;                                                         \
  }
#define STREAM_TO_UINT32(u32, p)                                      \
  {                                                                   \
    (u32) = (((uint32_t)(*(p))) + ((((uint32_t)(*((p) + 1)))) << 8) + \
             ((((uint32_t)(*((p) + 2)))) << 16) +                     \
             ((((uint32_t)(*((p) + 3)))) << 24));                     \
    (p) += 4;                                                         \
  }
#define STREAM_TO_UINT64(u64, p)                                      \
  {                                                                   \
    (u64) = (((uint64_t)(*(p))) + ((((uint64_t)(*((p) + 1)))) << 8) + \
             ((((uint64_t)(*((p) + 2)))) << 16) +                     \
             ((((uint64_t)(*((p) + 3)))) << 24) +                     \
             ((((uint64_t)(*((p) + 4)))) << 32) +                     \
             ((((uint64_t)(*((p) + 5)))) << 40) +                     \
             ((((uint64_t)(*((p) + 6)))) << 48) +                     \
             ((((uint64_t)(*((p) + 7)))) << 56));                     \
    (p) += 8;                                                         \
  }
#define STREAM_TO_ARRAY16(a, p)                     \
  {                                                 \
    int ijk;                                        \
    uint8_t* _pa = (uint8_t*)(a) + 15;              \
    for (ijk = 0; ijk < 16; ijk++) *_pa-- = *(p)++; \
  }
#define STREAM_TO_ARRAY8(a, p)                     \
  {                                                \
    int ijk;                                       \
    uint8_t* _pa = (uint8_t*)(a) + 7;              \
    for (ijk = 0; ijk < 8; ijk++) *_pa-- = *(p)++; \
  }
#define STREAM_TO_LAP(a, p)                               \
  {                                                       \
    int ijk;                                              \
    uint8_t* plap = (uint8_t*)(a) + LAP_LEN - 1;          \
    for (ijk = 0; ijk < LAP_LEN; ijk++) *plap-- = *(p)++; \
  }
#define STREAM_TO_ARRAY(a, p, len)                                   \
  {                                                                  \
    int ijk;                                                         \
    for (ijk = 0; ijk < (len); ijk++) ((uint8_t*)(a))[ijk] = *(p)++; \
  }
#define STREAM_SKIP_UINT8(p) \
  do {                       \
    (p) += 1;                \
  } while (0)
#define STREAM_SKIP_UINT16(p) \
  do {                        \
    (p) += 2;                 \
  } while (0)
#define STREAM_SKIP_UINT32(p) \
  do {                        \
    (p) += 4;                 \
  } while (0)

/*******************************************************************************
 * Macros to get and put bytes to and from a stream (Big Endian format)
 */
#define UINT32_TO_BE_STREAM(p, u32)  \
  {                                  \
    *(p)++ = (uint8_t)((u32) >> 24); \
    *(p)++ = (uint8_t)((u32) >> 16); \
    *(p)++ = (uint8_t)((u32) >> 8);  \
    *(p)++ = (uint8_t)(u32);         \
  }
#define UINT24_TO_BE_STREAM(p, u24)  \
  {                                  \
    *(p)++ = (uint8_t)((u24) >> 16); \
    *(p)++ = (uint8_t)((u24) >> 8);  \
    *(p)++ = (uint8_t)(u24);         \
  }
#define UINT16_TO_BE_STREAM(p, u16) \
  {                                 \
    *(p)++ = (uint8_t)((u16) >> 8); \
    *(p)++ = (uint8_t)(u16);        \
  }
#define UINT8_TO_BE_STREAM(p, u8) \
  { *(p)++ = (uint8_t)(u8); }
#define ARRAY_TO_BE_STREAM(p, a, len)                             \
  {                                                               \
    int ijk;                                                      \
    for (ijk = 0; ijk < (len); ijk++) *(p)++ = (uint8_t)(a)[ijk]; \
  }
#define BE_STREAM_TO_UINT8(u8, p) \
  {                               \
    (u8) = (uint8_t)(*(p));       \
    (p) += 1;                     \
  }
#define BE_STREAM_TO_UINT16(u16, p)                                       \
  {                                                                       \
    (u16) = (uint16_t)(((uint16_t)(*(p)) << 8) + (uint16_t)(*((p) + 1))); \
    (p) += 2;                                                             \
  }
#define BE_STREAM_TO_UINT24(u32, p)                                     \
  {                                                                     \
    (u32) = (((uint32_t)(*((p) + 2))) + ((uint32_t)(*((p) + 1)) << 8) + \
             ((uint32_t)(*(p)) << 16));                                 \
    (p) += 3;                                                           \
  }
#define BE_STREAM_TO_UINT32(u32, p)                                      \
  {                                                                      \
    (u32) = ((uint32_t)(*((p) + 3)) + ((uint32_t)(*((p) + 2)) << 8) +    \
             ((uint32_t)(*((p) + 1)) << 16) + ((uint32_t)(*(p)) << 24)); \
    (p) += 4;                                                            \
  }
#define BE_STREAM_TO_UINT64(u64, p)                                            \
  {                                                                            \
    (u64) = ((uint64_t)(*((p) + 7)) + ((uint64_t)(*((p) + 6)) << 8) +          \
             ((uint64_t)(*((p) + 5)) << 16) + ((uint64_t)(*((p) + 4)) << 24) + \
             ((uint64_t)(*((p) + 3)) << 32) + ((uint64_t)(*((p) + 2)) << 40) + \
             ((uint64_t)(*((p) + 1)) << 48) + ((uint64_t)(*(p)) << 56));       \
    (p) += 8;                                                                  \
  }
#define BE_STREAM_TO_ARRAY(p, a, len)                                \
  {                                                                  \
    int ijk;                                                         \
    for (ijk = 0; ijk < (len); ijk++) ((uint8_t*)(a))[ijk] = *(p)++; \
  }

/*******************************************************************************
 * Macros to get and put bytes to and from a field (Big Endian format).
 * These are the same as to stream, except the pointer is not incremented.
 */
#define UINT32_TO_BE_FIELD(p, u32)                 \
  {                                                \
    *(uint8_t*)(p) = (uint8_t)((u32) >> 24);       \
    *((uint8_t*)(p) + 1) = (uint8_t)((u32) >> 16); \
    *((uint8_t*)(p) + 2) = (uint8_t)((u32) >> 8);  \
    *((uint8_t*)(p) + 3) = (uint8_t)(u32);         \
  }
#define UINT16_TO_BE_FIELD(p, u16)          \
  {                                         \
    *(uint8_t*)(p) = (uint8_t)((u16) >> 8); \
    *((uint8_t*)(p) + 1) = (uint8_t)(u16);  \
  }

/* Common Bluetooth field definitions */

#define BT_1SEC_TIMEOUT_MS (1 * 1000) /* 1 second */

#endif

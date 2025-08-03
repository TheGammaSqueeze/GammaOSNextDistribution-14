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

#pragma once

#include <cstdint>

#include "internal_include/bt_target.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

/* Masks for attr_value field of tSDP_DISC_ATTR */
#define SDP_DISC_ATTR_LEN_MASK 0x0FFF
#define SDP_DISC_ATTR_TYPE(len_type) ((len_type) >> 12)
#define SDP_DISC_ATTR_LEN(len_type) ((len_type)&SDP_DISC_ATTR_LEN_MASK)

#define SDP_MAX_LIST_ELEMS 3

/* Define a structure to hold the discovered service information. */
typedef struct {
  union {
    uint8_t u8;                         /* 8-bit integer            */
    uint16_t u16;                       /* 16-bit integer           */
    uint32_t u32;                       /* 32-bit integer           */
    struct t_sdp_disc_attr* p_sub_attr; /* Addr of first sub-attr (list)*/
    uint8_t array[];                    /* Variable length field    */
                                        /* flexible array member    */
                                        /* requiring backing store  */
                                        /* from SDP DB    */
  } v;

} tSDP_DISC_ATVAL;

typedef struct t_sdp_disc_attr {
  struct t_sdp_disc_attr* p_next_attr; /* Addr of next linked attr     */
  uint16_t attr_id;                    /* Attribute ID                 */
  uint16_t attr_len_type;              /* Length and type fields       */
  tSDP_DISC_ATVAL attr_value;          /* Variable length entry data   */
} tSDP_DISC_ATTR;

typedef struct t_sdp_disc_rec {
  tSDP_DISC_ATTR* p_first_attr;      /* First attribute of record    */
  struct t_sdp_disc_rec* p_next_rec; /* Addr of next linked record   */
  uint32_t time_read;                /* The time the record was read */
  RawAddress remote_bd_addr;         /* Remote BD address            */
} tSDP_DISC_REC;

typedef struct {
  uint32_t mem_size;          /* Memory size of the DB        */
  uint32_t mem_free;          /* Memory still available       */
  tSDP_DISC_REC* p_first_rec; /* Addr of first record in DB   */
  uint16_t num_uuid_filters;  /* Number of UUIds to filter    */
  bluetooth::Uuid uuid_filters[SDP_MAX_UUID_FILTERS]; /* UUIDs to filter */
  uint16_t num_attr_filters; /* Number of attribute filters  */
  uint16_t attr_filters[SDP_MAX_ATTR_FILTERS]; /* Attributes to filter */
  uint8_t* p_free_mem; /* Pointer to free memory       */
  uint8_t*
      raw_data; /* Received record from server. allocated/released by client  */
  uint32_t raw_size; /* size of raw_data */
  uint32_t raw_used; /* length of raw_data used */
} tSDP_DISCOVERY_DB;

/* This structure is used to add protocol lists and find protocol elements */
typedef struct {
  uint16_t protocol_uuid;
  uint16_t num_params;
  uint16_t params[SDP_MAX_PROTOCOL_PARAMS];
} tSDP_PROTOCOL_ELEM;

typedef struct {
  uint16_t num_elems;
  tSDP_PROTOCOL_ELEM list_elem[SDP_MAX_LIST_ELEMS];
} tSDP_PROTO_LIST_ELEM;

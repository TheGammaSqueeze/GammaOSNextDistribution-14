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

/* Device Identification (DI) data structure
 */
/* Used to set the DI record */
typedef struct t_sdp_di_record {
  uint16_t vendor;
  uint16_t vendor_id_source;
  uint16_t product;
  uint16_t version;
  bool primary_record;
  char client_executable_url[SDP_MAX_ATTR_LEN]; /* optional */
  char service_description[SDP_MAX_ATTR_LEN];   /* optional */
  char documentation_url[SDP_MAX_ATTR_LEN];     /* optional */
} tSDP_DI_RECORD;

/* Used to get the DI record */
typedef struct t_sdp_di_get_record {
  uint16_t spec_id;
  tSDP_DI_RECORD rec;
} tSDP_DI_GET_RECORD;

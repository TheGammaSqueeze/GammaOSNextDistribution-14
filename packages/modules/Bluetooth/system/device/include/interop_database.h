/******************************************************************************
 *
 *  Copyright 2015 Google, Inc.
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

#include "device/include/interop.h"
#include "raw_address.h"

typedef struct {
  RawAddress addr;
  size_t length;
  interop_feature_t feature;
} interop_addr_entry_t;

typedef struct {
  RawAddress addr_start;
  RawAddress addr_end;
  interop_feature_t feature;
} interop_addr_range_entry_t;

typedef struct {
  char name[249];
  size_t length;
  interop_feature_t feature;
} interop_name_entry_t;

typedef struct {
  uint16_t manufacturer;
  interop_feature_t feature;
} interop_manufacturer_t;

typedef struct {
  uint16_t vendor_id;
  uint16_t product_id;
  interop_feature_t feature;
} interop_hid_multitouch_t;
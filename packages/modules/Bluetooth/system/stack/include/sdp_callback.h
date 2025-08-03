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

#include "stack/include/sdp_status.h"
#include "types/raw_address.h"

/* Define a callback function for when discovery is complete. */
typedef void(tSDP_DISC_CMPL_CB)(const RawAddress& bd_addr, tSDP_RESULT result);
typedef void(tSDP_DISC_CMPL_CB2)(const RawAddress& bd_addr, tSDP_RESULT result,
                                 const void* user_data);

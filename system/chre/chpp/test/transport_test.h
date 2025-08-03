/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CHPP_TRANSPORT_TEST_H_
#define CHPP_TRANSPORT_TEST_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "chpp/app.h"
#include "chpp/macros.h"
#include "chpp/transport.h"

#ifdef __cplusplus
extern "C" {
#endif

CHPP_PACKED_START
struct ChppTestResponse {
  char preamble0;
  char preamble1;
  struct ChppTransportHeader transportHeader;
  struct ChppAppHeader appHeader;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

/************************************************
 *  Functions necessary for unit testing
 ***********************************************/

size_t chppDequeueTxDatagram(struct ChppTransportState *context);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_TRANSPORT_TEST_H_

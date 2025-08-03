/*
 * Copyright 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <string.h>

#include "apf_interpreter.h"
#include "test_buf_allocator.h"

uint8_t apf_test_buffer[sizeof(apf_test_buffer)];
uint32_t apf_test_tx_packet_len;
uint8_t apf_test_tx_dscp;

/**
 * Test implementation of apf_allocate_buffer()
 *
 * Clean up the apf_test_buffer and return the pointer to beginning of the buffer region.
 */
uint8_t* apf_allocate_buffer(__attribute__ ((unused)) void* ctx, uint32_t size) {
  if (size > sizeof(apf_test_buffer)) {
    return NULL;
  }
  return apf_test_buffer;
}

/**
 * Test implementation of apf_transmit_buffer()
 *
 * Copy the content of allocated buffer to the apf_test_tx_packet region.
 */
int apf_transmit_buffer(__attribute__((unused)) void* ctx, uint8_t* ptr,
                        uint32_t len, uint8_t dscp) {
  if (len && len < ETH_HLEN) return -1;
  if (ptr != apf_test_buffer) return -1;
  apf_test_tx_packet_len = len;
  apf_test_tx_dscp = dscp;
  return 0;
}

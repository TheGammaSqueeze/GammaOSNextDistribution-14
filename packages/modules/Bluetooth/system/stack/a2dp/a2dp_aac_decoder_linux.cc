/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "a2dp_aac_decoder"

#include <base/logging.h>

#include "a2dp_aac_decoder.h"
#include "stack/include/bt_hdr.h"

typedef struct {
  decoded_data_callback_t decode_callback;
} tA2DP_AAC_DECODER_CB;

bool A2DP_LoadDecoderAac(void) { return false; }

void A2DP_UnloadDecoderAac(void) {}

bool a2dp_aac_decoder_init(decoded_data_callback_t decode_callback) {
  return false;
}

void a2dp_aac_decoder_cleanup(void) {}

bool a2dp_aac_decoder_decode_packet(BT_HDR* p_buf) { return false; }

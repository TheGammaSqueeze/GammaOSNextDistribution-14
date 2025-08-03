/*
 * Copyright 2016 The Android Open Source Project
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

//
// Interface to the A2DP aptX Encoder
//

#ifndef A2DP_VENDOR_APTX_ENCODER_H
#define A2DP_VENDOR_APTX_ENCODER_H

#include <stddef.h>
#include <stdint.h>

#include "a2dp_codec_api.h"
#include "a2dp_vendor.h"

// Loads the A2DP aptX encoder.
// Return loading status
tLOADING_CODEC_STATUS A2DP_VendorLoadEncoderAptx(void);

// Unloads the A2DP aptX encoder.
void A2DP_VendorUnloadEncoderAptx(void);

// Initialize the A2DP aptX encoder.
// |p_peer_params| contains the A2DP peer information.
// The current A2DP codec config is in |a2dp_codec_config|.
// |read_callback| is the callback for reading the input audio data.
// |enqueue_callback| is the callback for enqueueing the encoded audio data.
void a2dp_vendor_aptx_encoder_init(
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    A2dpCodecConfig* a2dp_codec_config,
    a2dp_source_read_callback_t read_callback,
    a2dp_source_enqueue_callback_t enqueue_callback);

// Cleanup the A2DP aptX encoder.
void a2dp_vendor_aptx_encoder_cleanup(void);

// Reset the feeding for the A2DP aptX encoder.
void a2dp_vendor_aptx_feeding_reset(void);

// Flush the feeding for the A2DP aptX encoder.
void a2dp_vendor_aptx_feeding_flush(void);

// Get the A2DP aptX encoder interval (in milliseconds).
uint64_t a2dp_vendor_aptx_get_encoder_interval_ms(void);

// Get the A2DP aptX encoded maximum frame size
int a2dp_vendor_aptx_get_effective_frame_size();

// Prepare and send A2DP aptX encoded frames.
// |timestamp_us| is the current timestamp (in microseconds).
void a2dp_vendor_aptx_send_frames(uint64_t timestamp_us);

typedef int (*tAPTX_ENCODER_INIT)(void* state, short endian);

typedef int (*tAPTX_ENCODER_ENCODE_STEREO)(void* state, void* pcmL, void* pcmR,
                                           void* buffer);

typedef int (*tAPTX_ENCODER_SIZEOF_PARAMS)(void);

typedef struct {
  tAPTX_ENCODER_INIT init_func;
  tAPTX_ENCODER_ENCODE_STEREO encode_stereo_func;
  tAPTX_ENCODER_SIZEOF_PARAMS sizeof_params_func;
} tAPTX_API;

// Filled the |external_api| with the ptr to the codec api
// return true if the codec is loaded
// This is for test purpose and ensure we are testing the api in real life
// condition
bool A2DP_VendorCopyAptxApi(tAPTX_API& external_api);

#endif  // A2DP_VENDOR_APTX_ENCODER_H

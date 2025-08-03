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

//
// Interface to the HFP LC3 Encoder
//

#ifndef HFP_LC3_ENCODER_H
#define HFP_LC3_ENCODER_H

#include <stdint.h>

// Initialize the HFP LC3 encoder.
void hfp_lc3_encoder_init();

// Cleanup the HFP LC3 encoder.
void hfp_lc3_encoder_cleanup();

// Encode the frame.
// Returns number of PCM bytes consumed (should always be 480).
uint32_t hfp_lc3_encode_frames(int16_t* input, uint8_t* output);

#endif  // HFP_LC3_ENCODER_H

/*
 * Copyright (C) 2022 The Android Open Source Project
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
// Interface to the HFP mSBC Encoder
//

#ifndef HFP_MSBC_ENCODER_H
#define HFP_MSBC_ENCODER_H

#include <stdint.h>

// Initialize the HFP MSBC encoder.
void hfp_msbc_encoder_init();

// Cleanup the HFP MSBC encoder.
void hfp_msbc_encoder_cleanup(void);

// Get the HFP MSBC encoded maximum frame size
uint32_t hfp_msbc_encode_frames(int16_t* input, uint8_t* output);

#endif  // HFP_MSBC_ENCODER_H

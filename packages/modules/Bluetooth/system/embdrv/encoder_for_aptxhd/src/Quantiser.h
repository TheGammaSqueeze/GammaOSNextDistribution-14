/**
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
/*------------------------------------------------------------------------------
 *
 *  Function to calculate a quantised representation of an input
 *  difference signal, based on additional dither values and step-size inputs.
 *
 *-----------------------------------------------------------------------------*/

#ifndef QUANTISER_H
#define QUANTISER_H
#ifdef _GCC
#pragma GCC visibility push(hidden)
#endif

#include "AptxParameters.h"

void quantiseDifference_HDLL(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt);
void quantiseDifference_HDHL(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt);
void quantiseDifference_HDLH(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_pt);
void quantiseDifference_HDHH(const int32_t diffSignal, const int32_t ditherVal,
                             const int32_t delta, Quantiser_data* qdata_p);

#ifdef _GCC
#pragma GCC visibility pop
#endif
#endif  // QUANTISER_H

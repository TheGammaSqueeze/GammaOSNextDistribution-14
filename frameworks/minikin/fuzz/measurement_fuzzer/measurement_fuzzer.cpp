/******************************************************************************
 *
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *****************************************************************************
 */
#include <fuzzer/FuzzedDataProvider.h>

#include "minikin/Measurement.h"
using namespace minikin;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp(data, size);
    float advance = fdp.ConsumeFloatingPoint<float>();
    int sizeof_size = (int)sizeof(size_t);
    int sizeof_uint16 = (int)sizeof(uint16_t);
    int sizeof_float = (int)sizeof(float);
    int remaining = fdp.remaining_bytes();
    int limit = (int)((remaining - 3 * sizeof_size) / sizeof_uint16);
    limit = (limit < 0) ? 0 : limit;
    int buf_size = fdp.ConsumeIntegralInRange<int>(0, limit);
    if (buf_size == 0) return 0;
    uint16_t buf[buf_size];
    for (int i = 0; i < buf_size; i++) buf[i] = fdp.ConsumeIntegral<uint16_t>();
    size_t start = fdp.ConsumeIntegralInRange<size_t>(0, buf_size - 1);
    size_t count = fdp.ConsumeIntegralInRange<size_t>(0, buf_size - 1 - start);
    size_t offset = fdp.ConsumeIntegralInRange<size_t>(start, start + count);
    remaining = fdp.remaining_bytes();
    if (remaining / sizeof_float < count) return 0;
    if (offset == start + count) return 0;
    int advances_size = count;
    float advances[advances_size];
    for (int i = 0; i < advances_size; i++) advances[i] = fdp.ConsumeFloatingPoint<float>();
    float advance_run = getRunAdvance(advances, buf, start, count, offset);
    size_t advance_offset = getOffsetForAdvance(advances, buf, start, count, advance);
    return 0;
}

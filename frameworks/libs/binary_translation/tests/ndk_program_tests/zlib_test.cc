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

#include "gtest/gtest.h"

#include <string.h>
#include <zlib.h>

const char kTestString[] = "compressed string";

void* ZAlloc(void* opaque, uint32_t items, uint32_t size) {
  *reinterpret_cast<int*>(opaque) |= 1;
  return calloc(items, size);
}

void ZFree(void* opaque, void* address) {
  *reinterpret_cast<int*>(opaque) |= 2;
  free(address);
}

TEST(ZLib, Deflate) {
  char input[1024];
  char output[1024];

  z_stream encode_stream;
  memset(&encode_stream, 0, sizeof(encode_stream));
  strncpy(input, kTestString, sizeof(input));
  encode_stream.next_in = reinterpret_cast<Bytef*>(input);
  encode_stream.avail_in = strlen(input) + 1;
  encode_stream.next_out = reinterpret_cast<Bytef*>(output);
  encode_stream.avail_out = sizeof(output);
  encode_stream.zalloc = ZAlloc;
  encode_stream.zfree = ZFree;
  int opaque = 0;
  encode_stream.opaque = &opaque;
  ASSERT_EQ(deflateInit(&encode_stream, Z_BEST_COMPRESSION), Z_OK);
  ASSERT_EQ(deflate(&encode_stream, Z_FINISH), Z_STREAM_END);
  ASSERT_EQ(deflateEnd(&encode_stream), Z_OK);
  EXPECT_EQ(opaque, 3);

  z_stream decode_stream;
  memset(&decode_stream, 0, sizeof(decode_stream));
  decode_stream.next_in = reinterpret_cast<Bytef*>(output);
  decode_stream.avail_in = encode_stream.total_out;
  decode_stream.next_out = reinterpret_cast<Bytef*>(input);
  decode_stream.avail_out = sizeof(input);
  opaque = 0;
  decode_stream.zalloc = ZAlloc;
  decode_stream.zfree = ZFree;
  decode_stream.opaque = &opaque;
  ASSERT_EQ(inflateInit(&decode_stream), Z_OK);
  ASSERT_EQ(inflate(&decode_stream, Z_FINISH), Z_STREAM_END);
  ASSERT_EQ(inflateEnd(&decode_stream), Z_OK);
  EXPECT_EQ(decode_stream.total_out, sizeof(kTestString));
  EXPECT_EQ(strncmp(input, kTestString, sizeof(input)), 0);
  EXPECT_EQ(opaque, 3);
}

/////////////////////////////////////////////////////////////////////////
//
// Author: Mateusz Jurczyk (mjurczyk@google.com)
//
// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <cstdio>
#include <string>

#include "SkAlphaType.h"
#include "SkAndroidCodec.h"
#include "SkBitmap.h"
#include "SkCodec.h"
#include "SkColorType.h"
#include "SkImageInfo.h"
#include "SkStream.h"
#include "SkString.h"

#include "fuzzer/FuzzedDataProvider.h"

#include <stddef.h>
#include <stdint.h>

static int getAndroidPixels(std::string& contents, bool requestPremul) {
  // Generate stream contents
  std::unique_ptr<SkMemoryStream> stream = SkMemoryStream::MakeDirect(contents.data(), contents.size());
  if (!stream) {
    return 0;
  }

  std::unique_ptr<SkCodec> c = SkCodec::MakeFromStream(std::move(stream),
                                                       nullptr);
  if (!c) {
    return 0;
  }

  std::unique_ptr<SkAndroidCodec> codec;
  codec = SkAndroidCodec::MakeFromCodec(std::move(c));
  if (!codec) {
    return 0;
  }

  SkImageInfo info = codec->getInfo();
  const int width = info.width();
  const int height = info.height();

  SkColorType decodeColorType = kN32_SkColorType;
  SkAlphaType alphaType =
      codec->computeOutputAlphaType(requestPremul);
  const SkImageInfo decodeInfo =
      SkImageInfo::Make(width, height, decodeColorType, alphaType);

  SkImageInfo bitmapInfo = decodeInfo;
  SkBitmap decodingBitmap;
  if (!decodingBitmap.tryAllocPixels(bitmapInfo)) {
    return 0;
  }

  codec->getAndroidPixels(
      decodeInfo, decodingBitmap.getPixels(), decodingBitmap.rowBytes());
  return 0;
}

extern "C" int LLVMFuzzerTestOneInput(char *data, size_t size) {
  if (size == 0) {
    return 0;
  }

  std::string sdata1(data, size);
  getAndroidPixels(sdata1, true);
  std::string sdata2(data, size);
  getAndroidPixels(sdata2, false);
  return 0;
}

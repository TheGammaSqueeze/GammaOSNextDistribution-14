/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/tokenfree/byte_encoder.h"

#include <vector>
namespace libtextclassifier3 {

bool ByteEncoder::Encode(StringPiece input_text,
                         std::vector<int64_t>* encoded_text) const {
  const int len = input_text.size();
  if (len <= 0) {
    *encoded_text = {};
    return true;
  }

  int size = input_text.size();
  encoded_text->resize(size);

  const auto& text = input_text.ToString();
  for (int i = 0; i < size; i++) {
    int64_t encoding = static_cast<int64_t>(text[i]);
    (*encoded_text)[i] = encoding;
  }

  return true;
}

}  // namespace libtextclassifier3

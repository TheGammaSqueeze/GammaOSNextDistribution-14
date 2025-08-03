/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <fuzzer/FuzzedDataProvider.h>

#include <cstdint>
#include <vector>

#include "os/handler.h"

namespace bluetooth {
namespace fuzz {

std::vector<std::vector<uint8_t>> SplitInput(
    const uint8_t* data, size_t size, const uint8_t* separator, size_t separatorSize);

std::vector<uint8_t> GetArbitraryBytes(FuzzedDataProvider* fdp);

#define CONSTRUCT_VALID_UNIQUE_OTHERWISE_BAIL(T, name, data) \
  auto name = std::make_unique<T>(T::FromBytes(data));       \
  if (!name->IsValid()) {                                    \
    return;                                                  \
  }

template <typename TView>
void InvokeIfValid(common::ContextualOnceCallback<void(TView)> callback, std::vector<uint8_t> data) {
  auto packet = TView::FromBytes(data);
  if (!packet.IsValid()) {
    return;
  }
  callback.InvokeIfNotEmpty(packet);
}

template <typename TView>
void InvokeIfValid(common::ContextualCallback<void(TView)> callback, std::vector<uint8_t> data) {
  auto packet = TView::FromBytes(data);
  if (!packet.IsValid()) {
    return;
  }
  callback.InvokeIfNotEmpty(packet);
}

}  // namespace fuzz
}  // namespace bluetooth

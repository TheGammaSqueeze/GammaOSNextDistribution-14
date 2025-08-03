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

// Utility functions for working with FlatBuffers.

#ifndef LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_FLATBUFFERS_H_
#define LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_FLATBUFFERS_H_

#include <iostream>
#include <string>

#include "annotator/model_generated.h"
#include "flatbuffers/flatbuffers.h"

namespace libtextclassifier3 {

// Loads and interprets the buffer as 'FlatbufferMessage' and verifies its
// integrity.
template <typename FlatbufferMessage>
const FlatbufferMessage* LoadAndVerifyFlatbuffer(const void* buffer, int size) {
  if (size == 0) {
    return nullptr;
  }
  const FlatbufferMessage* message =
      flatbuffers::GetRoot<FlatbufferMessage>(buffer);
  if (message == nullptr) {
    return nullptr;
  }

  flatbuffers::Verifier verifier(reinterpret_cast<const uint8_t*>(buffer),
                                 size);
  if (message->Verify(verifier)) {
    return message;
  } else {
    // TODO(217577534): Need to figure out why the verifier is failing.
    return message;
  }
}

// Same as above but takes string.
template <typename FlatbufferMessage>
const FlatbufferMessage* LoadAndVerifyFlatbuffer(const std::string& buffer) {
  return LoadAndVerifyFlatbuffer<FlatbufferMessage>(buffer.c_str(),
                                                    buffer.size());
}

// Loads and interprets the buffer as 'FlatbufferMessage', verifies its
// integrity and returns its mutable version.
template <typename FlatbufferMessage>
std::unique_ptr<typename FlatbufferMessage::NativeTableType>
LoadAndVerifyMutableFlatbuffer(const void* buffer, int size) {
  const FlatbufferMessage* message =
      LoadAndVerifyFlatbuffer<FlatbufferMessage>(buffer, size);
  if (message == nullptr) {
    return nullptr;
  }
  return std::unique_ptr<typename FlatbufferMessage::NativeTableType>(
      message->UnPack());
}

// Same as above but takes string.
template <typename FlatbufferMessage>
std::unique_ptr<typename FlatbufferMessage::NativeTableType>
LoadAndVerifyMutableFlatbuffer(const std::string& buffer) {
  return LoadAndVerifyMutableFlatbuffer<FlatbufferMessage>(buffer.c_str(),
                                                           buffer.size());
}

template <typename FlatbufferMessage>
const char* FlatbufferFileIdentifier() {
  return nullptr;
}

template <>
inline const char* FlatbufferFileIdentifier<Model>() {
  return ModelIdentifier();
}

// Packs the mutable flatbuffer message to string.
template <typename FlatbufferMessage>
std::string PackFlatbuffer(
    const typename FlatbufferMessage::NativeTableType* mutable_message) {
  flatbuffers::FlatBufferBuilder builder;
  builder.Finish(FlatbufferMessage::Pack(builder, mutable_message),
                 FlatbufferFileIdentifier<FlatbufferMessage>());
  return std::string(reinterpret_cast<const char*>(builder.GetBufferPointer()),
                     builder.GetSize());
}

// A convenience flatbuffer object with its underlying buffer.
template <typename T, typename B = flatbuffers::DetachedBuffer>
class OwnedFlatbuffer {
 public:
  explicit OwnedFlatbuffer(B&& buffer) : buffer_(std::move(buffer)) {}

  // Cast as flatbuffer type.
  const T* get() const { return flatbuffers::GetRoot<T>(buffer_.data()); }

  const B& buffer() const { return buffer_; }

  const T* operator->() const {
    return flatbuffers::GetRoot<T>(buffer_.data());
  }

 private:
  B buffer_;
};

}  // namespace libtextclassifier3

#endif  // LIBTEXTCLASSIFIER_UTILS_FLATBUFFERS_FLATBUFFERS_H_

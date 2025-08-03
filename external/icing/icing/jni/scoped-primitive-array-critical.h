// Copyright (C) 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_JNI_SCOPED_PRIMITIVE_ARRAY_CRITICAL_H_
#define ICING_JNI_SCOPED_PRIMITIVE_ARRAY_CRITICAL_H_

#include <jni.h>

#include <utility>

namespace icing {
namespace lib {

template <typename T>
class ScopedPrimitiveArrayCritical {
 public:
  ScopedPrimitiveArrayCritical(JNIEnv* env, jarray array)
      : env_(env), array_(array) {
    if (array_ == nullptr) {
      array_critical_ = nullptr;
      array_critical_size_ = 0;
    } else {
      array_critical_size_ = env->GetArrayLength(array);
      array_critical_ = static_cast<T*>(
          env->GetPrimitiveArrayCritical(array, /*isCopy=*/nullptr));
    }
  }

  ScopedPrimitiveArrayCritical(ScopedPrimitiveArrayCritical&& rhs)
      : env_(nullptr),
        array_(nullptr),
        array_critical_(nullptr),
        array_critical_size_(0) {
    Swap(rhs);
  }

  ScopedPrimitiveArrayCritical(const ScopedPrimitiveArrayCritical&) = delete;

  ScopedPrimitiveArrayCritical& operator=(ScopedPrimitiveArrayCritical&& rhs) {
    Swap(rhs);
    return *this;
  }

  ScopedPrimitiveArrayCritical& operator=(const ScopedPrimitiveArrayCritical&) =
      delete;

  ~ScopedPrimitiveArrayCritical() {
    if (array_critical_ != nullptr && array_ != nullptr) {
      env_->ReleasePrimitiveArrayCritical(array_, array_critical_, /*mode=*/0);
    }
  }

  T* data() { return array_critical_; }
  const T* data() const { return array_critical_; }

  size_t size() const { return array_critical_size_; }

 private:
  void Swap(ScopedPrimitiveArrayCritical& other) {
    std::swap(env_, other.env_);
    std::swap(array_, other.array_);
    std::swap(array_critical_, other.array_critical_);
    std::swap(array_critical_size_, other.array_critical_size_);
  }

  JNIEnv* env_;
  jarray array_;
  T* array_critical_;
  size_t array_critical_size_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JNI_SCOPED_PRIMITIVE_ARRAY_CRITICAL_H_

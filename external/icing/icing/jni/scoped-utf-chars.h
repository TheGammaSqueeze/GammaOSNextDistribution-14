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

#ifndef ICING_JNI_SCOPED_UTF_CHARS_H_
#define ICING_JNI_SCOPED_UTF_CHARS_H_

#include <jni.h>

#include <cstddef>
#include <cstring>
#include <utility>

namespace icing {
namespace lib {

// An RAII class to manage access and allocation of a Java string's UTF chars.
class ScopedUtfChars {
 public:
  ScopedUtfChars(JNIEnv* env, jstring s) : env_(env), string_(s) {
    if (s == nullptr) {
      utf_chars_ = nullptr;
      size_ = 0;
    } else {
      utf_chars_ = env->GetStringUTFChars(s, /*isCopy=*/nullptr);
      size_ = strlen(utf_chars_);
    }
  }

  ScopedUtfChars(ScopedUtfChars&& rhs)
      : env_(nullptr), string_(nullptr), utf_chars_(nullptr) {
    Swap(rhs);
  }

  ScopedUtfChars(const ScopedUtfChars&) = delete;

  ScopedUtfChars& operator=(ScopedUtfChars&& rhs) {
    Swap(rhs);
    return *this;
  }

  ScopedUtfChars& operator=(const ScopedUtfChars&) = delete;

  ~ScopedUtfChars() {
    if (utf_chars_ != nullptr) {
      env_->ReleaseStringUTFChars(string_, utf_chars_);
    }
  }

  const char* c_str() const { return utf_chars_; }

  size_t size() const { return size_; }

 private:
  void Swap(ScopedUtfChars& other) {
    std::swap(env_, other.env_);
    std::swap(string_, other.string_);
    std::swap(utf_chars_, other.utf_chars_);
    std::swap(size_, other.size_);
  }

  JNIEnv* env_;
  jstring string_;
  const char* utf_chars_;
  size_t size_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JNI_SCOPED_UTF_CHARS_H_

// Copyright (C) 2019 Google LLC
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

#ifndef ICING_JNI_JNI_CACHE_H_
#define ICING_JNI_JNI_CACHE_H_

#ifndef ICING_REVERSE_JNI_SEGMENTATION
namespace icing {
namespace lib {

class JniCache {};  // Declare an empty class definition for non-Android builds.

}  // namespace lib
}  // namespace icing
#else  // ICING_REVERSE_JNI_SEGMENTATION

#include <jni.h>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/text_classifier/lib3/utils/java/jni-base.h"

namespace icing {
namespace lib {

// A helper class to cache class and method pointers for calls from JNI to Java.
// (for implementations such as Java ICU that need to make calls from C++ to
// Java)
struct JniCache {
  static libtextclassifier3::StatusOr<std::unique_ptr<JniCache>> Create(
      JNIEnv* env);

  // Returns the correct JNIEnv of the current thread. This allows multiple
  // threads, each accessing the same instance of JniCache, to retrieve their
  // unique JNIEnv pointers.
  JNIEnv* GetEnv() const;

  // Returns true if there are any pending exceptions from the execution of JNI
  // calls. Also clears the exception if any existed.
  bool ExceptionCheckAndClear() const;

  JavaVM* jvm = nullptr;

  // java.lang.String
  libtextclassifier3::ScopedGlobalRef<jclass> string_class;
  jmethodID string_constructor = nullptr;
  jmethodID string_code_point_count = nullptr;
  jmethodID string_length = nullptr;
  libtextclassifier3::ScopedGlobalRef<jstring> string_utf8;

  // java.util.Locale
  libtextclassifier3::ScopedGlobalRef<jclass> locale_class;
  libtextclassifier3::ScopedGlobalRef<jobject> locale_us;
  jmethodID locale_constructor = nullptr;
  jmethodID locale_for_language_tag = nullptr;

  // BreakIteratorBatcher
  libtextclassifier3::ScopedGlobalRef<jclass> breakiterator_class;
  jmethodID breakiterator_constructor = nullptr;
  jmethodID breakiterator_settext = nullptr;
  jmethodID breakiterator_next = nullptr;
  jmethodID breakiterator_first = nullptr;
  jmethodID breakiterator_following = nullptr;
  jmethodID breakiterator_preceding = nullptr;

  // Helper to convert lib3 UnicodeText to Java strings.
  libtextclassifier3::StatusOr<libtextclassifier3::ScopedLocalRef<jstring>>
  ConvertToJavaString(const char* utf8_text,
                      const int utf8_text_size_bytes) const;

 private:
  explicit JniCache(JavaVM* jvm);
};

}  // namespace lib
}  // namespace icing

#endif  // !ICING_REVERSE_JNI_SEGMENTATION

#endif  // ICING_JNI_JNI_CACHE_H_

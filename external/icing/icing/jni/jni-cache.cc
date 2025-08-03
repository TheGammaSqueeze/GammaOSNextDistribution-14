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

#include "icing/jni/jni-cache.h"

#ifdef ICING_REVERSE_JNI_SEGMENTATION

#include "icing/text_classifier/lib3/utils/java/jni-base.h"
#include "icing/text_classifier/lib3/utils/java/jni-helper.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

JniCache::JniCache(JavaVM* jvm)
    : jvm(jvm),
      string_class(nullptr, jvm),
      string_utf8(nullptr, jvm),
      locale_class(nullptr, jvm),
      locale_us(nullptr, jvm),
      breakiterator_class(nullptr, jvm) {}

// The macros below are intended to reduce the boilerplate in Create and avoid
// easily introduced copy/paste errors.
#define ICING_GET_CLASS_OR_RETURN_NULL(FIELD, NAME)                    \
  {                                                                    \
    ICING_ASSIGN_OR_RETURN(                                            \
        libtextclassifier3::ScopedLocalRef<jclass> clazz,              \
        libtextclassifier3::JniHelper::FindClass(env, NAME), nullptr); \
    result->FIELD##_class =                                            \
        libtextclassifier3::MakeGlobalRef(clazz.get(), env, jvm);      \
    if (result->FIELD##_class == nullptr) {                            \
      ICING_LOG(ERROR) << "Error finding class: " << NAME;             \
      return nullptr;                                                  \
    }                                                                  \
  }

#define ICING_GET_OPTIONAL_CLASS(FIELD, NAME)                                  \
  {                                                                            \
    libtextclassifier3::StatusOr<libtextclassifier3::ScopedLocalRef<jclass>>   \
        status_or_class = libtextclassifier3::JniHelper::FindClass(env, NAME); \
    if (status_or_class.ok()) {                                                \
      result->FIELD##_class = libtextclassifier3::MakeGlobalRef(               \
          std::move(status_or_class).ValueOrDie().get(), env, jvm);            \
    }                                                                          \
  }

#define ICING_GET_METHOD(CLASS, FIELD, NAME, SIGNATURE)               \
  result->CLASS##_##FIELD =                                           \
      env->GetMethodID(result->CLASS##_class.get(), NAME, SIGNATURE); \
  if (!result->CLASS##_##FIELD) {                                     \
    ICING_LOG(WARNING) << __FILE__ << ":" << __LINE__                 \
                       << "Error finding method: " << NAME;           \
    return absl_ports::AbortedError("Unable to get Java method.");    \
  }

#define ICING_GET_OPTIONAL_STATIC_METHOD(CLASS, FIELD, NAME, SIGNATURE)       \
  if (result->CLASS##_class != nullptr) {                                     \
    result->CLASS##_##FIELD =                                                 \
        env->GetStaticMethodID(result->CLASS##_class.get(), NAME, SIGNATURE); \
    env->ExceptionClear();                                                    \
  }

#define ICING_GET_STATIC_METHOD(CLASS, FIELD, NAME, SIGNATURE)              \
  result->CLASS##_##FIELD =                                                 \
      env->GetStaticMethodID(result->CLASS##_class.get(), NAME, SIGNATURE); \
  if (!result->CLASS##_##FIELD) {                                           \
    ICING_LOG(WARNING) << __FILE__ << ":" << __LINE__                       \
                       << "Error finding method: " << NAME;                 \
    return absl_ports::AbortedError("Unable to get Java static method.");   \
  }

#define ICING_GET_STATIC_OBJECT_FIELD_OR_RETURN_NULL(CLASS, FIELD, NAME,     \
                                                     SIGNATURE)              \
  {                                                                          \
    const jfieldID CLASS##_##FIELD##_field =                                 \
        env->GetStaticFieldID(result->CLASS##_class.get(), NAME, SIGNATURE); \
    if (!CLASS##_##FIELD##_field) {                                          \
      ICING_LOG(WARNING) << __FILE__ << ":" << __LINE__                      \
                         << "Error finding field id: " << NAME;              \
      return absl_ports::AbortedError("Unable to get Java field id.");       \
    }                                                                        \
    ICING_ASSIGN_OR_RETURN(                                                  \
        libtextclassifier3::ScopedLocalRef<jobject> static_object,           \
        libtextclassifier3::JniHelper::GetStaticObjectField(                 \
            env, result->CLASS##_class.get(), CLASS##_##FIELD##_field),      \
        nullptr);                                                            \
    result->CLASS##_##FIELD =                                                \
        libtextclassifier3::MakeGlobalRef(static_object.get(), env, jvm);    \
    if (result->CLASS##_##FIELD == nullptr) {                                \
      ICING_LOG(ERROR) << "Error finding field: " << NAME;                   \
      return nullptr;                                                        \
    }                                                                        \
  }

#define ICING_GET_STATIC_INT_FIELD(CLASS, FIELD, NAME)                       \
  const jfieldID CLASS##_##FIELD##_field =                                   \
      env->GetStaticFieldID(result->CLASS##_class.get(), NAME, "I");         \
  << "Error finding field id: " << NAME;                                     \
  if (!CLASS##_##FIELD##_field) {                                            \
    ICING_LOG(WARNING) << __FILE__ << ":" << __LINE__                        \
                       << "Error finding field id: " << NAME;                \
    return absl_ports::AbortedError(                                         \
        "Unable to get Java static int field id.");                          \
  }                                                                          \
  result->CLASS##_##FIELD = env->GetStaticIntField(                          \
      result->CLASS##_class.get(), CLASS##_##FIELD##_field);                 \
  if (!result->CLASS##_##FIELD) {                                            \
    ICING_LOG(WARNING) << __FILE__ << ":" << __LINE__                        \
                       << "Error finding field: " << NAME;                   \
    return absl_ports::AbortedError("Unable to get Java static int field."); \
  }

libtextclassifier3::StatusOr<std::unique_ptr<JniCache>> JniCache::Create(
    JNIEnv* env) {
  if (env == nullptr) {
    return nullptr;
  }
  JavaVM* jvm = nullptr;
  if (JNI_OK != env->GetJavaVM(&jvm) || jvm == nullptr) {
    return nullptr;
  }
  std::unique_ptr<JniCache> result(new JniCache(jvm));

  // String
  ICING_GET_CLASS_OR_RETURN_NULL(string, "java/lang/String");
  ICING_GET_METHOD(string, constructor, "<init>", "([BLjava/lang/String;)V");
  ICING_GET_METHOD(string, code_point_count, "codePointCount", "(II)I");
  ICING_GET_METHOD(string, length, "length", "()I");
  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jstring> result_string,
      libtextclassifier3::JniHelper::NewStringUTF(env, "UTF-8"), nullptr);
  result->string_utf8 =
      libtextclassifier3::MakeGlobalRef(result_string.get(), env, jvm);
  if (result->string_utf8 == nullptr) {
    return nullptr;
  }

  // Locale
  ICING_GET_CLASS_OR_RETURN_NULL(locale, "java/util/Locale");
  ICING_GET_STATIC_OBJECT_FIELD_OR_RETURN_NULL(locale, us, "US",
                                               "Ljava/util/Locale;");
  ICING_GET_METHOD(locale, constructor, "<init>", "(Ljava/lang/String;)V");
  ICING_GET_OPTIONAL_STATIC_METHOD(locale, for_language_tag, "forLanguageTag",
                                   "(Ljava/lang/String;)Ljava/util/Locale;");

  // BreakIteratorBatcher
  ICING_GET_CLASS_OR_RETURN_NULL(
      breakiterator, "com/google/android/icing/BreakIteratorBatcher");
  ICING_GET_METHOD(breakiterator, constructor, "<init>",
                   "(Ljava/util/Locale;)V");
  ICING_GET_METHOD(breakiterator, settext, "setText", "(Ljava/lang/String;)V");
  ICING_GET_METHOD(breakiterator, next, "next", "(I)[I");
  ICING_GET_METHOD(breakiterator, first, "first", "()I");
  ICING_GET_METHOD(breakiterator, following, "following", "(I)I");
  ICING_GET_METHOD(breakiterator, preceding, "preceding", "(I)I");

  return result;
}

#undef ICING_GET_STATIC_INT_FIELD
#undef ICING_GET_STATIC_OBJECT_FIELD_OR_RETURN_NULL
#undef ICING_GET_STATIC_METHOD
#undef ICING_GET_METHOD
#undef ICING_GET_CLASS_OR_RETURN_NULL
#undef ICING_GET_OPTIONAL_CLASS

JNIEnv* JniCache::GetEnv() const {
  void* env;
  if (JNI_OK == jvm->GetEnv(&env, JNI_VERSION_1_4)) {
    return reinterpret_cast<JNIEnv*>(env);
  } else {
    ICING_LOG(ERROR) << "Icing JniCache used on unattached thread";
    return nullptr;
  }
}

bool JniCache::ExceptionCheckAndClear() const {
  return libtextclassifier3::JniExceptionCheckAndClear(GetEnv());
}

libtextclassifier3::StatusOr<libtextclassifier3::ScopedLocalRef<jstring>>
JniCache::ConvertToJavaString(const char* utf8_text,
                              const int utf8_text_size_bytes) const {
  // Create java byte array.
  JNIEnv* jenv = GetEnv();
  ICING_ASSIGN_OR_RETURN(
      libtextclassifier3::ScopedLocalRef<jbyteArray> text_java_utf8,
      libtextclassifier3::JniHelper::NewByteArray(jenv, utf8_text_size_bytes));

  jenv->SetByteArrayRegion(text_java_utf8.get(), 0, utf8_text_size_bytes,
                           reinterpret_cast<const jbyte*>(utf8_text));

  // Create the string with a UTF-8 charset.
  ICING_ASSIGN_OR_RETURN(libtextclassifier3::ScopedLocalRef<jstring> result,
                         libtextclassifier3::JniHelper::NewObject<jstring>(
                             jenv, string_class.get(), string_constructor,
                             text_java_utf8.get(), string_utf8.get()));

  return result;
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_REVERSE_JNI_SEGMENTATION

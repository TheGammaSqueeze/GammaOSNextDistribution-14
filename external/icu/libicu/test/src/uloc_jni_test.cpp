/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <memory>

#include <gtest/gtest.h>

#include <jni.h>
#include <android/log.h>
#include <unicode/uloc.h>
#include <unicode/utypes.h>

// provided by cts/common/device-side/nativetesthelper
JavaVM* GetJavaVM();


/**
 * uloc_setDefault in unicode/uloc.h is not visible because the default Locale in ICU4C, ICU4J
 * and java.util.Locale is synchronized. This  function calls java.util.Locale#setDefault.
 * If possible, call java.util.Locale#setDefault directly in java.
 * @param locale ICU Locale ID
 * @param status error code, e.g. U_UNSUPPORTED_ERROR in case of ClassNotFoundException
 */
static void uloc_setDefault_java(JNIEnv* env, const char* localeID, UErrorCode* status) {
  if (U_FAILURE(*status)) {
    return;
  }
  if (localeID == nullptr || env == nullptr) {
    *status = U_ILLEGAL_ARGUMENT_ERROR;
    return;
  }
  if (env->ExceptionCheck()) {
    *status = U_INVALID_STATE_ERROR;
    return;
  }

  auto jdeleter = [env](jobject obj){ env->DeleteLocalRef(obj); };

  constexpr const char log_tag[] = "uloc_jni";
  // All java classes / methods used below are public in the Android SDK.
  constexpr const char ulocale_classname[] = "android/icu/util/ULocale";
  constexpr const char locale_classname[] = "java/util/Locale";
  constexpr const char ctor_methodname[] = "<init>";
  constexpr const char toLocale_methodname[] = "toLocale";
  constexpr const char setDefault_methodname[] = "setDefault";

  /* The below JNI code is equivalent to the following in java
     ULocale ulocale = new Ulocale(localeID);
     Locale.setDefault(ulocale.toLocale());
   */

  // Set error code, clear the exception to avoid throwing during an ongoing JNI call, and return.
  #define ANDROID_UNICODE_RETURN_ON_ERROR(ptr, msg, ...) \
    if (ptr == nullptr || env->ExceptionCheck()) { \
      __android_log_print(ANDROID_LOG_DEBUG, log_tag, msg, __VA_ARGS__); \
      *status = U_UNSUPPORTED_ERROR; \
      env->ExceptionClear(); \
      return; \
    };

  std::unique_ptr<_jclass, decltype(jdeleter)> ulocale_class(env->FindClass(ulocale_classname),
                                                             jdeleter);
  ANDROID_UNICODE_RETURN_ON_ERROR(ulocale_class.get(), "class not found: %s", ulocale_classname)

  std::unique_ptr<_jclass, decltype(jdeleter)> locale_class(env->FindClass(locale_classname),
                                                            jdeleter);
  ANDROID_UNICODE_RETURN_ON_ERROR(locale_class.get(), "class not found: %s", locale_classname)

  jmethodID ulocale_ctor = env->GetMethodID(ulocale_class.get(), ctor_methodname,
                                            "(Ljava/lang/String;)V");
  ANDROID_UNICODE_RETURN_ON_ERROR(ulocale_ctor, "method not found in class %s: %s",
                            ulocale_classname, ctor_methodname);

  jmethodID ulocale_toLocale = env->GetMethodID(ulocale_class.get(), toLocale_methodname,
                                                "()Ljava/util/Locale;");
  ANDROID_UNICODE_RETURN_ON_ERROR(ulocale_toLocale, "method not found in class %s: %s",
                            ulocale_classname, toLocale_methodname)

  jmethodID locale_setDefault = env->GetStaticMethodID(locale_class.get(), setDefault_methodname,
                                                       "(Ljava/util/Locale;)V");
  ANDROID_UNICODE_RETURN_ON_ERROR(locale_setDefault, "method not found in class %s: %s",
                            ulocale_classname, setDefault_methodname)

  // The following Java APIs are not expected to throw Exception.
  std::unique_ptr<_jstring, decltype(jdeleter)> locale_str(env->NewStringUTF(localeID), jdeleter);
  EXPECT_FALSE(env->ExceptionCheck());
  std::unique_ptr<_jobject, decltype(jdeleter)> ulocale(
      env->NewObject(ulocale_class.get(), ulocale_ctor, locale_str.get()),
      jdeleter);
  EXPECT_FALSE(env->ExceptionCheck());
  std::unique_ptr<_jobject, decltype(jdeleter)> locale(
      env->CallObjectMethod(ulocale.get(), ulocale_toLocale),
      jdeleter);
  EXPECT_FALSE(env->ExceptionCheck());
  env->CallStaticVoidMethod(locale_class.get(), locale_setDefault, locale.get());
  EXPECT_FALSE(env->ExceptionCheck());
  #undef ANDROID_UNICODE_RETURN_ON_ERROR
}

class Icu4cLocaleJniTest : public ::testing::Test {
 protected:
  JNIEnv* env;
  const char* orig_default_locale; // Do not release as specified by uloc_getDefault()

  virtual void SetUp() override {
    orig_default_locale = uloc_getDefault();

    JavaVM* jvm = GetJavaVM();
    int envStat = jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
    ASSERT_EQ(JNI_OK, envStat);  // It should be attached to the current thread already.
  }

  virtual void TearDown() override {
    UErrorCode status = U_ZERO_ERROR;
    uloc_setDefault_java(env, orig_default_locale, &status);
  }

};

/**
 * Test that java.util.Locale#setDefault changes the value returned from uloc_getDefault.
 */
TEST_F(Icu4cLocaleJniTest, test_uloc_getDefault) {
  UErrorCode status = U_ZERO_ERROR;
  uloc_setDefault_java(env, ULOC_JAPAN, &status);
  EXPECT_EQ(U_ZERO_ERROR, status);
  EXPECT_STREQ(ULOC_JAPAN, uloc_getDefault());

  uloc_setDefault_java(env, "" /* root locale */, &status);
  EXPECT_EQ(U_ZERO_ERROR, status);
  EXPECT_STREQ("", uloc_getDefault());

  // Canonicalize the locale by uloc_getName and then set it as the default.
  constexpr const char locale_ja_with_extension[] = "ja_JP@calendar=japanese;currency=usd";
  char localeID[ULOC_FULLNAME_CAPACITY];
  uloc_getName(locale_ja_with_extension, localeID, ULOC_FULLNAME_CAPACITY, &status);
  EXPECT_EQ(U_ZERO_ERROR, status);
  EXPECT_STREQ(locale_ja_with_extension, localeID);
  uloc_setDefault_java(env, localeID, &status);
  EXPECT_EQ(U_ZERO_ERROR, status);
  EXPECT_STREQ(locale_ja_with_extension, uloc_getDefault());
}

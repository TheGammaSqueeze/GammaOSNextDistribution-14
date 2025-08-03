/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <locale.h>
#include <time.h>
#include <stdlib.h>
#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL Java_libcore_java_time_TimeApisConsistencyTest_formatWithBionic(
    JNIEnv* env, jclass, jlong epochSeconds, jstring timeZoneId) {

  const char* oldTimeZone = getenv("TZ");
  const char* oldLocale = setlocale(LC_ALL, NULL);

  const char* nativeTimeZone = env->GetStringUTFChars(timeZoneId, 0);
  setenv("TZ", nativeTimeZone, /* overwrite */ 1);
  env->ReleaseStringUTFChars(timeZoneId, nativeTimeZone);

  setlocale(LC_ALL, "en_US");

  // Formatted string should fit into this buffer
  char buf[32];
  time_t t = epochSeconds;
  strftime(buf, 32, "%d %m %Y %H:%M:%S", localtime(&t));

  if (oldTimeZone) {
    setenv("TZ", oldTimeZone, /* overwrite */ 1);
  } else {
    unsetenv("TZ");
  }

  setlocale(LC_ALL, oldLocale);

  return env->NewStringUTF(buf);
}

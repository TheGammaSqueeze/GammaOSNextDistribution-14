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

#include <errno.h>
#include <jni.h>
#include <malloc.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/utsname.h>

#include <string>

extern "C" JNIEXPORT jboolean Java_android_cts_tagging_Utils_kernelSupportsTaggedPointers() {
#ifdef __aarch64__
  int res = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
  return res >= 0 && res & PR_TAGGED_ADDR_ENABLE;
#else
  return false;
#endif
}

static const int TAGGING_MODE_OFF = 100;
#ifdef __aarch64__
static const int TAGGING_MODE_ASYNC = 101;
static const int TAGGING_MODE_SYNC = 102;
#endif

extern "C" JNIEXPORT jboolean Java_android_cts_tagging_Utils_getCurrentTaggingMode() {
#ifdef __aarch64__
  int res = prctl(PR_GET_TAGGED_ADDR_CTRL, 0, 0, 0, 0);
  if (res < 0)
    return TAGGING_MODE_OFF;

  if (res & PR_MTE_TCF_SYNC)
    return TAGGING_MODE_SYNC;
  else if (res & PR_MTE_TCF_ASYNC)
    return TAGGING_MODE_ASYNC;
  else
    return TAGGING_MODE_OFF;
#else
  return TAGGING_MODE_OFF;
#endif
}

extern "C" JNIEXPORT jint JNICALL Java_android_cts_tagging_Utils_nativeHeapPointerTag(JNIEnv *) {
#ifdef __aarch64__
  void *p = malloc(10);
  jint tag = reinterpret_cast<uintptr_t>(p) >> 56;
  free(p);
  return tag;
#else
  return 0;
#endif
}

extern "C" __attribute__((no_sanitize("address", "hwaddress"))) JNIEXPORT void JNICALL
Java_android_cts_tagging_Utils_accessMistaggedPointer(JNIEnv *) {
  int *p = new int[4];
  int *mistagged_p = reinterpret_cast<int *>(reinterpret_cast<uintptr_t>(p) + (1ULL << 56));
  volatile int load = *mistagged_p;
  (void)load;
  delete[] p;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_Utils_mistaggedKernelUaccessFails(JNIEnv *) {
  auto *p = new utsname;
  utsname *mistagged_p = reinterpret_cast<utsname *>(reinterpret_cast<uintptr_t>(p) + (1ULL << 56));
  bool result = uname(mistagged_p) != 0 && errno == EFAULT;
  delete p;
  return result;
}

__attribute__((optnone)) static bool sizeIsZeroInitialized(size_t size) {
  const int kCount = 200;
  for (int i = 0; i < kCount; ++i) {
    char *volatile p = reinterpret_cast<char *>(malloc(size));
    for (int j = 0; j < size; ++j) {
      if (p[j] != 0) {
        free(p);
        return false;
      }
    }
    memset(p, 42, size);
    free(p);
  }
  return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_android_cts_tagging_Utils_heapIsZeroInitialized(JNIEnv *) {
  return sizeIsZeroInitialized(100) && sizeIsZeroInitialized(2000) && sizeIsZeroInitialized(200000);
}

extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_tagging_Utils_allocatorIsScudo(JNIEnv *) {
  const size_t kMallocInfoBufSize = 8192;
  const char* kScudoPrefix = "<malloc version=\"scudo";
  char buf[kMallocInfoBufSize];
  FILE *fp = fmemopen(&buf, kMallocInfoBufSize, "w+");
  malloc_info(0, fp);
  fclose(fp);
  return strncmp(buf, kScudoPrefix, strlen(kScudoPrefix)) == 0;
}

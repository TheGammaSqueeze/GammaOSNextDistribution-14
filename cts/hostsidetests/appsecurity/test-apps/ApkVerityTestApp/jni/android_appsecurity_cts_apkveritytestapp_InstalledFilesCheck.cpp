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

#define LOG_TAG "ApkVerityTestApp"

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedUtfChars.h>

#include <android/log.h>

#include <errno.h>
#include <fcntl.h>
#include <linux/fs.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_android_appsecurity_cts_apkveritytestapp_InstalledFilesCheck_hasFsverityNative(
    JNIEnv *env, jobject /*thiz*/, jstring filePath) {
  ScopedUtfChars path(env, filePath);

  // Call statx and check STATX_ATTR_VERITY.
  struct statx out = {};
  if (statx(AT_FDCWD, path.c_str(), 0 /* flags */, STATX_ALL, &out) != 0) {
    ALOGE("statx failed at %s", path.c_str());
    return JNI_FALSE;
  }

  if (out.stx_attributes_mask & STATX_ATTR_VERITY) {
    return (out.stx_attributes & STATX_ATTR_VERITY) != 0 ? JNI_TRUE : JNI_FALSE;
  }

  // STATX_ATTR_VERITY is not supported by kernel for the file path.
  // In this case, call ioctl(FS_IOC_GETFLAGS) and check FS_VERITY_FL.
  int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC);
  if (fd < 0) {
    ALOGE("failed to open %s", path.c_str());
    return JNI_FALSE;
  }

  unsigned int flags;
  int ret = ioctl(fd, FS_IOC_GETFLAGS, &flags);
  close(fd);

  if (ret < 0) {
    ALOGE("ioctl(FS_IOC_GETFLAGS) failed at %s", path.c_str());
    return JNI_FALSE;
  }

  return (flags & FS_VERITY_FL) != 0 ? JNI_TRUE : JNI_FALSE;
}

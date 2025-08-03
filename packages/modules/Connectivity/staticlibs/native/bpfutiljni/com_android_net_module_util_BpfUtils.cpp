/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android-base/unique_fd.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/ScopedLocalRef.h>
#include <nativehelper/scoped_utf_chars.h>
#include <string.h>
#include <sys/socket.h>

#include "BpfSyscallWrappers.h"

namespace android {

using base::unique_fd;

static jint com_android_net_module_util_BpfUtil_getProgramIdFromCgroup(JNIEnv *env,
        jclass clazz, jint type, jstring cgroupPath) {

    ScopedUtfChars dirPath(env, cgroupPath);
    unique_fd cg_fd(open(dirPath.c_str(), O_DIRECTORY | O_RDONLY | O_CLOEXEC));
    if (cg_fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to open the cgroup directory %s: %s",
                             dirPath.c_str(), strerror(errno));
        return -1;
    }

    int id = bpf::queryProgram(cg_fd, (bpf_attach_type) type);
    if (id < 0) {
        jniThrowExceptionFmt(env, "java/io/IOException",
                             "Failed to query bpf program %d at %s: %s",
                             type, dirPath.c_str(), strerror(errno));
        return -1;
    }
    return id;  // may return 0 meaning none
}


/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "native_getProgramIdFromCgroup", "(ILjava/lang/String;)I",
        (void*) com_android_net_module_util_BpfUtil_getProgramIdFromCgroup },
};

int register_com_android_net_module_util_BpfUtils(JNIEnv* env, char const* class_name) {
    return jniRegisterNativeMethods(env,
            class_name,
            gMethods, NELEM(gMethods));
}

}; // namespace android

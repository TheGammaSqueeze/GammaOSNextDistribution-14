/*
 * Copyright (C) 2010 The Android Open Source Project
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

#define LOG_TAG "JniConstants"

#include "JniConstants.h"

#include <atomic>
#include <mutex>
#include <stdlib.h>

#include <log/log.h>
#include <nativehelper/ScopedLocalRef.h>

namespace {

jclass findClass(JNIEnv* env, const char* name) {
    ScopedLocalRef<jclass> localClass(env, env->FindClass(name));
    jclass result = reinterpret_cast<jclass>(env->NewGlobalRef(localClass.get()));
    if (result == NULL) {
        ALOGE("failed to find class '%s'", name);
        abort();
    }
    return result;
}

// Mutex protecting static variables
static std::mutex g_constants_mutex;

// Flag indicating whether cached constants are valid
static bool g_constants_valid = false;

// Mapping between C++ names and java class descriptors.
#define JCLASS_CONSTANTS_LIST(V)                                                            \
    V(BooleanClass, "java/lang/Boolean")                                                    \
    V(ByteBufferClass, "java/nio/ByteBuffer")                                               \
    V(DoubleClass, "java/lang/Double")                                                      \
    V(ErrnoExceptionClass, "android/system/ErrnoException")                                 \
    V(FileDescriptorClass, "java/io/FileDescriptor")                                        \
    V(GaiExceptionClass, "android/system/GaiException")                                     \
    V(Inet6AddressClass, "java/net/Inet6Address")                                           \
    V(Inet6AddressHolderClass, "java/net/Inet6Address$Inet6AddressHolder")                  \
    V(InetAddressClass, "java/net/InetAddress")                                             \
    V(InetAddressHolderClass, "java/net/InetAddress$InetAddressHolder")                     \
    V(InetSocketAddressClass, "java/net/InetSocketAddress")                                 \
    V(InetSocketAddressHolderClass, "java/net/InetSocketAddress$InetSocketAddressHolder")   \
    V(IntegerClass, "java/lang/Integer")                                                    \
    V(LocaleDataClass, "libcore/icu/LocaleData")                                            \
    V(LongClass, "java/lang/Long")                                                          \
    V(NetlinkSocketAddressClass, "android/system/NetlinkSocketAddress")                     \
    V(PacketSocketAddressClass, "android/system/PacketSocketAddress")                       \
    V(VmSocketAddressClass, "android/system/VmSocketAddress")                               \
    V(PrimitiveByteArrayClass, "[B")                                                        \
    V(StringClass, "java/lang/String")                                                      \
    V(StructAddrinfoClass, "android/system/StructAddrinfo")                                 \
    V(StructCmsghdrClass, "android/system/StructCmsghdr")                                   \
    V(StructGroupReqClass, "android/system/StructGroupReq")                                 \
    V(StructIfaddrsClass, "android/system/StructIfaddrs")                                   \
    V(StructLingerClass, "android/system/StructLinger")                                     \
    V(StructMsghdrClass, "android/system/StructMsghdr")                                     \
    V(StructPasswdClass, "android/system/StructPasswd")                                     \
    V(StructPollfdClass, "android/system/StructPollfd")                                     \
    V(StructStatClass, "android/system/StructStat")                                         \
    V(StructStatVfsClass, "android/system/StructStatVfs")                                   \
    V(StructTimevalClass, "android/system/StructTimeval")                                   \
    V(StructTimespecClass, "android/system/StructTimespec")                                 \
    V(StructUcredClass, "android/system/StructUcred")                                       \
    V(StructUtsnameClass, "android/system/StructUtsname")                                   \
    V(UnixSocketAddressClass, "android/system/UnixSocketAddress")

#define DECLARE_JCLASS_CONSTANT(cppname, _) jclass g_ ## cppname;
JCLASS_CONSTANTS_LIST(DECLARE_JCLASS_CONSTANT)

// EnsureJniConstantsInitialized initializes cached constants. It should be
// called before returning a heap object from the cache to ensure cache is
// initialized. This pattern is only necessary because if a process finishes one
// runtime and starts another then JNI_OnLoad may not be called.
void EnsureJniConstantsInitialized(JNIEnv* env) {
    std::lock_guard guard(g_constants_mutex);
    if (g_constants_valid) {
        return;
    }

#define INITIALIZE_JCLASS_CONSTANT(cppname, javaname) g_ ## cppname = findClass(env, javaname);
JCLASS_CONSTANTS_LIST(INITIALIZE_JCLASS_CONSTANT)

    g_constants_valid = true;
}

}  // namespace

#define CONSTANT_GETTER(cppname, _)                                                         \
jclass JniConstants::Get ## cppname(JNIEnv* env) {                                          \
    EnsureJniConstantsInitialized(env);                                                     \
    return g_ ## cppname;                                                                   \
}
JCLASS_CONSTANTS_LIST(CONSTANT_GETTER)

void JniConstants::Initialize(JNIEnv* env) {
    EnsureJniConstantsInitialized(env);
}

void JniConstants::Invalidate() {
    // This method is called when a new runtime instance is created. There is no
    // notification of a runtime instance being destroyed in the JNI interface
    // so we piggyback on creation. Since only one runtime is supported at a
    // time, we know the constants are invalid when JNI_CreateJavaVM() is
    // called.
    //
    // Clean shutdown would require calling DeleteGlobalRef() for each of the
    // class references, but JavaVM is unavailable because ART only calls this
    // once all threads are unregistered.
    std::lock_guard guard(g_constants_mutex);
    g_constants_valid = false;
}

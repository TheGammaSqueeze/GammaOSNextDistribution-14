/*
 * Copyright (C) 2023 The Android Open Source Project
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

#define LOG_TAG "jniThreadTun"

#include <arpa/inet.h>
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <linux/if_arp.h>
#include <linux/if_tun.h>
#include <linux/ioctl.h>
#include <log/log.h>
#include <net/if.h>
#include <spawn.h>
#include <sys/wait.h>
#include <string>

#include <private/android_filesystem_config.h>

#include "jni.h"
#include "nativehelper/JNIHelp.h"
#include "nativehelper/scoped_utf_chars.h"

namespace android {
static jint com_android_server_thread_TunInterfaceController_createTunInterface(
        JNIEnv* env, jobject clazz, jstring interfaceName, jint mtu) {
    ScopedUtfChars ifName(env, interfaceName);

    int fd = open("/dev/net/tun", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (fd == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "open tun device failed (%s)",
                             strerror(errno));
        return -1;
    }

    struct ifreq ifr = {
            .ifr_flags = IFF_TUN | IFF_NO_PI | static_cast<short>(IFF_TUN_EXCL),
    };
    strlcpy(ifr.ifr_name, ifName.c_str(), sizeof(ifr.ifr_name));

    if (ioctl(fd, TUNSETIFF, &ifr, sizeof(ifr)) != 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl(TUNSETIFF) failed (%s)",
                             strerror(errno));
        close(fd);
        return -1;
    }

    int inet6 = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, IPPROTO_IP);
    if (inet6 == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "create inet6 socket failed (%s)",
                             strerror(errno));
        close(fd);
        return -1;
    }
    ifr.ifr_mtu = mtu;
    if (ioctl(inet6, SIOCSIFMTU, &ifr) != 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl(SIOCSIFMTU) failed (%s)",
                             strerror(errno));
        close(fd);
        close(inet6);
        return -1;
    }

    close(inet6);
    return fd;
}

static void com_android_server_thread_TunInterfaceController_setInterfaceUp(
        JNIEnv* env, jobject clazz, jstring interfaceName, jboolean isUp) {
    struct ifreq ifr;
    ScopedUtfChars ifName(env, interfaceName);

    ifr.ifr_flags = isUp ? IFF_UP : 0;
    strlcpy(ifr.ifr_name, ifName.c_str(), sizeof(ifr.ifr_name));

    int inet6 = socket(AF_INET6, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, IPPROTO_IP);
    if (inet6 == -1) {
        jniThrowExceptionFmt(env, "java/io/IOException", "create inet6 socket failed (%s)",
                             strerror(errno));
    }

    if (ioctl(inet6, SIOCSIFFLAGS, &ifr) != 0) {
        jniThrowExceptionFmt(env, "java/io/IOException", "ioctl(SIOCSIFFLAGS) failed (%s)",
                             strerror(errno));
    }

    close(inet6);
}

/*
 * JNI registration.
 */

static const JNINativeMethod gMethods[] = {
        /* name, signature, funcPtr */
        {"nativeCreateTunInterface",
         "(Ljava/lang/String;I)I",
         (void*)com_android_server_thread_TunInterfaceController_createTunInterface},
        {"nativeSetInterfaceUp",
         "(Ljava/lang/String;Z)V",
         (void*)com_android_server_thread_TunInterfaceController_setInterfaceUp},
};

int register_com_android_server_thread_TunInterfaceController(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/server/thread/TunInterfaceController",
                                    gMethods, NELEM(gMethods));
}

};  // namespace android

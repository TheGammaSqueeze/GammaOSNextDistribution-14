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

#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/scoped_utf_chars.h>
#include <tcutils/tcutils.h>

namespace android {

static void throwIOException(JNIEnv *env, const char *msg, int error) {
  jniThrowExceptionFmt(env, "java/io/IOException", "%s: %s", msg,
                       strerror(error));
}

static jboolean com_android_net_module_util_TcUtils_isEthernet(JNIEnv *env,
                                                               jclass clazz,
                                                               jstring iface) {
  ScopedUtfChars interface(env, iface);
  bool result = false;
  int error = isEthernet(interface.c_str(), result);
  if (error) {
    throwIOException(
        env, "com_android_net_module_util_TcUtils_isEthernet error: ", -error);
  }
  // result is not touched when error is returned; leave false.
  return result;
}

// tc filter add dev .. in/egress prio 1 protocol ipv6/ip bpf object-pinned
// /sys/fs/bpf/... direct-action
static void com_android_net_module_util_TcUtils_tcFilterAddDevBpf(
    JNIEnv *env, jclass clazz, jint ifIndex, jboolean ingress, jshort prio,
    jshort proto, jstring bpfProgPath) {
  ScopedUtfChars pathname(env, bpfProgPath);
  int error = tcAddBpfFilter(ifIndex, ingress, prio, proto, pathname.c_str());
  if (error) {
    throwIOException(
        env,
        "com_android_net_module_util_TcUtils_tcFilterAddDevBpf error: ", -error);
  }
}

// tc filter add dev .. ingress prio .. protocol .. matchall \
//     action police rate .. burst .. conform-exceed pipe/continue \
//     action bpf object-pinned .. \
//     drop
static void com_android_net_module_util_TcUtils_tcFilterAddDevIngressPolice(
    JNIEnv *env, jclass clazz, jint ifIndex, jshort prio, jshort proto,
    jint rateInBytesPerSec, jstring bpfProgPath) {
  ScopedUtfChars pathname(env, bpfProgPath);
  int error = tcAddIngressPoliceFilter(ifIndex, prio, proto, rateInBytesPerSec,
                                       pathname.c_str());
  if (error) {
    throwIOException(env,
                     "com_android_net_module_util_TcUtils_"
                     "tcFilterAddDevIngressPolice error: ",
                     -error);
  }
}

// tc filter del dev .. in/egress prio .. protocol ..
static void com_android_net_module_util_TcUtils_tcFilterDelDev(
    JNIEnv *env, jclass clazz, jint ifIndex, jboolean ingress, jshort prio,
    jshort proto) {
  int error = tcDeleteFilter(ifIndex, ingress, prio, proto);
  if (error) {
    throwIOException(
        env,
        "com_android_net_module_util_TcUtils_tcFilterDelDev error: ", -error);
  }
}

// tc qdisc add dev .. clsact
static void com_android_net_module_util_TcUtils_tcQdiscAddDevClsact(JNIEnv *env,
                                                                    jclass clazz,
                                                                    jint ifIndex) {
  int error = tcAddQdiscClsact(ifIndex);
  if (error) {
    throwIOException(
        env,
        "com_android_net_module_util_TcUtils_tcQdiscAddDevClsact error: ", -error);
  }
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    {"isEthernet", "(Ljava/lang/String;)Z",
     (void *)com_android_net_module_util_TcUtils_isEthernet},
    {"tcFilterAddDevBpf", "(IZSSLjava/lang/String;)V",
     (void *)com_android_net_module_util_TcUtils_tcFilterAddDevBpf},
    {"tcFilterAddDevIngressPolice", "(ISSILjava/lang/String;)V",
     (void *)com_android_net_module_util_TcUtils_tcFilterAddDevIngressPolice},
    {"tcFilterDelDev", "(IZSS)V",
     (void *)com_android_net_module_util_TcUtils_tcFilterDelDev},
    {"tcQdiscAddDevClsact", "(I)V",
     (void *)com_android_net_module_util_TcUtils_tcQdiscAddDevClsact},
};

int register_com_android_net_module_util_TcUtils(JNIEnv *env,
                                                 char const *class_name) {
  return jniRegisterNativeMethods(env, class_name, gMethods, NELEM(gMethods));
}

}; // namespace android

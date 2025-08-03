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

#include <jni.h>
#include <sys/auxv.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <sys/utsname.h>
#include <sys/hwprobe.h>

#include "android-base/macros.h"

jboolean android_cts_CpuFeatures_isArmCpu(JNIEnv* env, jobject thiz)
{
#if defined(__arm__)
  return JNI_TRUE;
#else
  return JNI_FALSE;
#endif
}

jboolean android_cts_CpuFeatures_isX86Cpu(JNIEnv* env, jobject thiz)
{
#if defined(__i386__)
  return JNI_TRUE;
#else
  return JNI_FALSE;
#endif
}

jboolean android_cts_CpuFeatures_isArm64Cpu(JNIEnv* env, jobject thiz)
{
#if defined(__aarch64__)
  return JNI_TRUE;
#else
  return JNI_FALSE;
#endif
}

jboolean android_cts_CpuFeatures_isRiscv64Cpu(JNIEnv* env, jobject thiz)
{
#if defined(__riscv)
  return JNI_TRUE;
#else
  return JNI_FALSE;
#endif
}

jboolean android_cts_CpuFeatures_isX86_64Cpu(JNIEnv* env, jobject thiz)
{
#if defined(__x86_64__)
  return JNI_TRUE;
#else
  return JNI_FALSE;
#endif
}

jint android_cts_CpuFeatures_getHwCaps(JNIEnv*, jobject)
{
    return (jint)getauxval(AT_HWCAP);
}

jboolean android_cts_CpuFeatures_isNativeBridgedCpu(JNIEnv* env, jobject thiz)
{
#if defined(__BIONIC__)
  return __system_property_find("ro.dalvik.vm.isa." ABI_STRING) != nullptr;
#else

  return false;

#endif
}

jboolean android_cts_CpuFeatures_isRiscv64MisalignedFast(JNIEnv* env, jobject thiz) {
#if defined(__riscv)
  // https://github.com/torvalds/linux/blob/master/arch/riscv/include/uapi/asm/hwprobe.h
  riscv_hwprobe probes[] = {{.key = RISCV_HWPROBE_KEY_CPUPERF_0}};
  __riscv_hwprobe(probes, 1, 0, nullptr, 0);
  if ((probes[0].value & RISCV_HWPROBE_MISALIGNED_MASK) == RISCV_HWPROBE_MISALIGNED_FAST) {
    return true;
  }
#endif
  return false;
}

static JNINativeMethod gMethods[] = {
    {  "isArmCpu", "()Z",
            (void *) android_cts_CpuFeatures_isArmCpu  },
    {  "isX86Cpu", "()Z",
            (void *) android_cts_CpuFeatures_isX86Cpu  },
    {  "isArm64Cpu", "()Z",
            (void *) android_cts_CpuFeatures_isArm64Cpu  },
    {  "isRiscv64Cpu", "()Z",
            (void *) android_cts_CpuFeatures_isRiscv64Cpu  },
    {  "isX86_64Cpu", "()Z",
            (void *) android_cts_CpuFeatures_isX86_64Cpu  },
    {  "getHwCaps", "()I",
            (void *) android_cts_CpuFeatures_getHwCaps  },
    {  "isRiscv64MisalignedFast", "()Z",
            (void *) android_cts_CpuFeatures_isRiscv64MisalignedFast },
    {  "isNativeBridgedCpu", "()Z",
            (void *) android_cts_CpuFeatures_isNativeBridgedCpu  },
};

int register_android_cts_CpuFeatures(JNIEnv* env)
{
    jclass clazz = env->FindClass("com/android/compatibility/common/util/CpuFeatures");

    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}

/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  The Android Open Source
 * Project designates this particular file as subject to the "Classpath"
 * exception as provided by The Android Open Source Project in the LICENSE
 * file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

#include "jni.h"
#include "jvm.h"

#include <nativehelper/JNIHelp.h>
#include <nativehelper/jni_macros.h>

JNIEXPORT jlong JNICALL VM_getNanoTimeAdjustment(jlong offsetInSeconds) {
    return JVM_GetNanoTimeAdjustment(nullptr, nullptr, offsetInSeconds);
}

static JNINativeMethod gMethods[] = {
  CRITICAL_NATIVE_METHOD(VM, getNanoTimeAdjustment, "(J)J"),
};

void register_jdk_internal_misc_VM(JNIEnv* env) {
    jniRegisterNativeMethods(env, "jdk/internal/misc/VM", gMethods, NELEM(gMethods));
}
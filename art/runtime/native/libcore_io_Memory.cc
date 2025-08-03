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

#include "libcore_io_Memory.h"

#include <stdlib.h>
#include <string.h>

#include "jni/jni_internal.h"
#include "native_util.h"
#include "nativehelper/jni_macros.h"
#include "nativehelper/scoped_primitive_array.h"
#include "scoped_fast_native_object_access-inl.h"

namespace art HIDDEN {

// Use packed structures for access to unaligned data on targets with alignment restrictions.
// The compiler will generate appropriate code to access these structures without
// generating alignment exceptions.
template <typename T>
static inline T get_unaligned(const T* address) {
  struct unaligned {
    T v;
  } __attribute__((packed));
  const unaligned* p = reinterpret_cast<const unaligned*>(address);
  return p->v;
}

template <typename T>
static inline void put_unaligned(T* address, T v) {
  struct unaligned {
    T v;
  } __attribute__((packed));
  unaligned* p = reinterpret_cast<unaligned*>(address);
  p->v = v;
}

template <typename T>
static T cast(jlong address) {
  return reinterpret_cast<T>(static_cast<uintptr_t>(address));
}

// Byte-swap 2 jshort values packed in a jint.
static inline jint bswap_2x16(jint v) {
  // v is initially ABCD
  v = __builtin_bswap32(v);              // v=DCBA
  v = (v << 16) | ((v >> 16) & 0xffff);  // v=BADC
  return v;
}

static inline void swapShorts(jshort* dstShorts, const jshort* srcShorts, size_t count) {
  // Do 32-bit swaps as long as possible...
  jint* dst = reinterpret_cast<jint*>(dstShorts);
  const jint* src = reinterpret_cast<const jint*>(srcShorts);
  for (size_t i = 0; i < count / 2; ++i) {
    jint v = get_unaligned<jint>(src++);
    put_unaligned<jint>(dst++, bswap_2x16(v));
  }
  if ((count % 2) != 0) {
    jshort v = get_unaligned<jshort>(reinterpret_cast<const jshort*>(src));
    put_unaligned<jshort>(reinterpret_cast<jshort*>(dst), __builtin_bswap16(v));
  }
}

static inline void swapInts(jint* dstInts, const jint* srcInts, size_t count) {
  for (size_t i = 0; i < count; ++i) {
    jint v = get_unaligned<int>(srcInts++);
    put_unaligned<jint>(dstInts++, __builtin_bswap32(v));
  }
}

static inline void swapLongs(jlong* dstLongs, const jlong* srcLongs, size_t count) {
  jint* dst = reinterpret_cast<jint*>(dstLongs);
  const jint* src = reinterpret_cast<const jint*>(srcLongs);
  for (size_t i = 0; i < count; ++i) {
    jint v1 = get_unaligned<jint>(src++);
    jint v2 = get_unaligned<jint>(src++);
    put_unaligned<jint>(dst++, __builtin_bswap32(v2));
    put_unaligned<jint>(dst++, __builtin_bswap32(v1));
  }
}

static void Memory_peekByteArray(
    JNIEnv* env, jclass, jlong srcAddress, jbyteArray dst, jint dstOffset, jint byteCount) {
  env->SetByteArrayRegion(dst, dstOffset, byteCount, cast<const jbyte*>(srcAddress));
}

// Implements the peekXArray methods:
// - For unswapped access, we just use the JNI SetXArrayRegion functions.
// - For swapped access, we use GetXArrayElements and our own copy-and-swap routines.
//   GetXArrayElements is disproportionately cheap on Dalvik because it doesn't copy (as opposed
//   to Hotspot, which always copies). The SWAP_FN copies and swaps in one pass, which is cheaper
//   than copying and then swapping in a second pass. Depending on future VM/GC changes, the
//   swapped case might need to be revisited.
#define PEEKER(SCALAR_TYPE, JNI_NAME, SWAP_TYPE, SWAP_FN)                                       \
  {                                                                                             \
    if (swap) {                                                                                 \
      Scoped##JNI_NAME##ArrayRW elements(env, dst);                                             \
      if (elements.get() == NULL) {                                                             \
        return;                                                                                 \
      }                                                                                         \
      const SWAP_TYPE* src = cast<const SWAP_TYPE*>(srcAddress);                                \
      SWAP_FN(reinterpret_cast<SWAP_TYPE*>(elements.get()) + dstOffset, src, count); /*NOLINT*/ \
    } else {                                                                                    \
      const SCALAR_TYPE* src = cast<const SCALAR_TYPE*>(srcAddress);                            \
      env->Set##JNI_NAME##ArrayRegion(dst, dstOffset, count, src);                              \
    }                                                                                           \
  }

static void Memory_peekCharArray(JNIEnv* env,
                                 jclass,
                                 jlong srcAddress,
                                 jcharArray dst,
                                 jint dstOffset,
                                 jint count,
                                 jboolean swap) {
  PEEKER(jchar, Char, jshort, swapShorts);
}

static void Memory_peekDoubleArray(JNIEnv* env,
                                   jclass,
                                   jlong srcAddress,
                                   jdoubleArray dst,
                                   jint dstOffset,
                                   jint count,
                                   jboolean swap) {
  PEEKER(jdouble, Double, jlong, swapLongs);
}

static void Memory_peekFloatArray(JNIEnv* env,
                                  jclass,
                                  jlong srcAddress,
                                  jfloatArray dst,
                                  jint dstOffset,
                                  jint count,
                                  jboolean swap) {
  PEEKER(jfloat, Float, jint, swapInts);
}

static void Memory_peekIntArray(JNIEnv* env,
                                jclass,
                                jlong srcAddress,
                                jintArray dst,
                                jint dstOffset,
                                jint count,
                                jboolean swap) {
  PEEKER(jint, Int, jint, swapInts);
}

static void Memory_peekLongArray(JNIEnv* env,
                                 jclass,
                                 jlong srcAddress,
                                 jlongArray dst,
                                 jint dstOffset,
                                 jint count,
                                 jboolean swap) {
  PEEKER(jlong, Long, jlong, swapLongs);
}

static void Memory_peekShortArray(JNIEnv* env,
                                  jclass,
                                  jlong srcAddress,
                                  jshortArray dst,
                                  jint dstOffset,
                                  jint count,
                                  jboolean swap) {
  PEEKER(jshort, Short, jshort, swapShorts);
}

// The remaining Memory methods are contained in libcore/luni/src/main/native/libcore_io_Memory.cpp
static JNINativeMethod gMethods[] = {
    FAST_NATIVE_METHOD(Memory, peekByteArray, "(J[BII)V"),
    FAST_NATIVE_METHOD(Memory, peekCharArray, "(J[CIIZ)V"),
    FAST_NATIVE_METHOD(Memory, peekDoubleArray, "(J[DIIZ)V"),
    FAST_NATIVE_METHOD(Memory, peekFloatArray, "(J[FIIZ)V"),
    FAST_NATIVE_METHOD(Memory, peekIntArray, "(J[IIIZ)V"),
    FAST_NATIVE_METHOD(Memory, peekLongArray, "(J[JIIZ)V"),
    FAST_NATIVE_METHOD(Memory, peekShortArray, "(J[SIIZ)V"),
};

void register_libcore_io_Memory(JNIEnv* env) { REGISTER_NATIVE_METHODS("libcore/io/Memory"); }

}  // namespace art

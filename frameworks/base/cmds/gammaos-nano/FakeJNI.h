/*
 * Copyright (C) 2026 GammaOS
 *
 * Minimal fake JNIEnv/JavaVM implementation for dlopen()ing
 * libdrastic_arm64.so inside NanoMenu during early boot, before
 * zygote is available. Satisfies the tiny callback surface that
 * drastic actually uses:
 *   - com/dsemu/drastic/filesystem/NativePathHandle (3 fields)
 *   - com/dsemu/drastic/filesystem/DraSticPathCache (3 static methods)
 *
 * Drastic's JNI_OnLoad only caches globalrefs + field/method IDs,
 * and at runtime only calls into DraSticPathCache for file I/O.
 * Everything else (GL, audio, input, JIT) is pure native.
 */

#pragma once

#include <jni.h>
#include <string>

namespace android {
namespace fakejni {

// Initialize the singleton fake JavaVM + JNIEnv and return a JavaVM*
// suitable for passing to drastic's JNI_OnLoad. Safe to call multiple
// times; subsequent calls are no-ops.
JavaVM* init();

// Cache root directory for DraSticPathCache virtual-path translation.
// Drastic's Java side (see DraSticPathCache.smali:141 getRealPath)
// maps:
//   "DraStic/<rel>" -> <sys_prefix>/<rel>
//   "User/<rel>"    -> <user_prefix>/<rel>
//   "/..."          -> absolute POSIX path
// Our fake implementation translates DraStic/ -> <root>/system/ and
// User/ -> <root>/user/, matching the layout nano_cache.sh will
// populate.
void setCacheRoot(const std::string& cacheRoot);

// Allocate a pooled FakeIntArray of `length` zero-initialized jint
// elements and return a jintArray handle suitable for passing into
// drastic's getScreenBuffers(II)V JNI export. The allocation lives
// for the lifetime of the process. Used by DrasticRunner to provide
// the top/bottom-screen pixel destination buffers.
jintArray allocIntArray(jsize length);

// Read back the pixels that drastic wrote into a FakeIntArray via
// getScreenBuffers. Returns a pointer to the internal storage --
// drastic has committed the data by the time getScreenBuffers
// returns, so no further sync is needed. The pointer stays valid
// until process teardown.
const jint* getIntArrayData(jintArray arr);
jsize getIntArrayLength(jintArray arr);

} // namespace fakejni
} // namespace android

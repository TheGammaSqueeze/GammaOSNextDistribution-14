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
#include <cstdint>
#include <string>
#include <vector>

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
// In the default (nano_cache) mode we translate DraStic/ -> <root>/
// and User/ -> <root>/user/, matching the layout nano_cache.sh
// populates. Callers that point cacheRoot straight at drastic's
// installed files dir (e.g. /data/user/0/com.dsemu.drastic/files/
// DraStic) should call setDirectUserMode(true) so User/ resolves to
// <root>/ as well -- drastic's real layout has config/backup/
// savestates/ etc directly under the files/DraStic/ root, with no
// /user subdirectory.
void setCacheRoot(const std::string& cacheRoot);
void setDirectUserMode(bool enabled);

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

// Read back a byte[] that drastic returned (e.g. getCheatName fills it
// via NewByteArray + SetByteArrayRegion). drastic allocates strlen+1 and
// copies strlen bytes, so the trailing slot stays NUL; callers can trim a
// single trailing NUL. Pointer/length stay valid until process teardown.
const jbyte* getByteArrayData(jbyteArray arr);
jsize getByteArrayLength(jbyteArray arr);

// RAM-backed savestate slots (run-ahead). Files named "<rom>_<slot>.dss" for
// a registered slot, and drastic's "<rom>_savestate_temp.dss" staging file,
// are backed by memfds instead of the disk (see the registry in FakeJNI.cpp).
// ramStateFd returns the registry memfd for a slot that has been saved at
// least once (do not close it; use pread/pwrite/fstat), else -1. The copy
// helpers move the whole state in or out of that memfd.
void addRamStateSlot(int slot);
int  ramStateFd(int slot);
bool ramStateCopyOut(int slot, std::vector<uint8_t>& out);
bool ramStateCopyIn(int slot, const void* data, size_t len);
// Make the slot file exist (created from the temp file's directory if it
// has never been saved) and report exactly len bytes; contents untouched.
bool ramStateSetSize(int slot, size_t len);
// Create (if needed) the RAM file for a virtual savestate path and size it.
bool ramStateEnsureVirtual(const char* vpath, size_t len);

} // namespace fakejni
} // namespace android

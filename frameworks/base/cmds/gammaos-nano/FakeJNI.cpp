/*
 * Copyright (C) 2026 GammaOS
 *
 * Minimal fake JNIEnv/JavaVM for drastic in-process quick resume.
 * Phase 1: implement just enough vtable entries to let drastic's
 * JNI_OnLoad complete cleanly (GetEnv + FindClass + NewGlobalRef +
 * GetFieldID x3 + GetStaticMethodID x3). Everything else is logged
 * but unimplemented -- later phases will flesh out the filesystem
 * dispatcher.
 */

#define LOG_TAG "GammaOSNano.FakeJNI"

#include "FakeJNI.h"

#include <cstring>
#include <cstdarg>
#include <cstdint>
#include <cstdlib>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
#include <vector>
#include <memory>
#include <utils/Log.h>

namespace android {
namespace fakejni {

// -------- Tag IDs returned from FindClass / GetFieldID / GetStaticMethodID
// We pack these into pointer-sized opaque handles so drastic can't tell them
// apart from real Dalvik refs. Using non-zero base offsets so that nullptr
// still means "not found".

namespace tag {
    constexpr uintptr_t kClassBase       = 0x1000;
    constexpr uintptr_t kClassPathHandle = kClassBase + 1;
    constexpr uintptr_t kClassPathCache  = kClassBase + 2;

    constexpr uintptr_t kFieldBase        = 0x2000;
    constexpr uintptr_t kFieldFilePath    = kFieldBase + 1;
    constexpr uintptr_t kFieldFileFd      = kFieldBase + 2;
    constexpr uintptr_t kFieldFileName    = kFieldBase + 3;

    constexpr uintptr_t kMethodBase        = 0x3000;
    constexpr uintptr_t kMethodOpen        = kMethodBase + 1;
    constexpr uintptr_t kMethodRename      = kMethodBase + 2;
    constexpr uintptr_t kMethodRemove      = kMethodBase + 3;
}

// -------- State --------

// Cache root (e.g. /data/system/nano_cache/drastic, or drastic's
// installed files dir at /data/user/0/com.dsemu.drastic/files/DraStic).
// Virtual paths resolve underneath this: "DraStic/foo" -> "<root>/foo",
// "User/foo" -> "<root>/user/foo" by default. See DraSticPathCache.smali:141
// getRealPath.
static std::string sCacheRoot;

// When true, "User/foo" resolves to "<root>/foo" (no /user/ prefix)
// -- matching drastic's real app layout where config/backup/
// savestates/ live directly under files/DraStic/. Used by drastic-nano
// to point FakeJNI straight at the installed app's data dir.
static bool sDirectUserMode = false;

// FakeString: our stand-in for jstring. Drastic calls NewStringUTF()
// before CallStaticObjectMethod(DraSticPathCache.open, path, mode),
// and we construct one of these. Later when drastic calls
// GetStringUTFChars() on a jstring we return, it's either one of
// these (via GetObjectField on a NativePathHandleShim) or one that
// drastic itself allocated.
//
// utf16 is an on-demand cache built by FakeGetStringChars when
// drastic calls setFirmwareUserdata (the only path we've seen so
// far that needs the UTF16 form). For ASCII input we just zero-
// extend each byte into a jchar; for non-ASCII we'd need proper
// UTF-8 → UTF-16 decoding but the preview path never hits that.
struct FakeString {
    std::string content;
    std::vector<jchar> utf16;  // built lazily on first GetStringChars
};

// NativePathHandleShim: stand-in for the Java NativePathHandle POJO
// returned from DraSticPathCache.open. Drastic reads .fileFd via
// GetIntField and .filePath/.fileName via GetObjectField. Layout
// intentionally mirrors the smali field declarations so the
// dispatchers stay straightforward.
struct NativePathHandleShim {
    int         fileFd;
    FakeString* filePath;
    FakeString* fileName;
};

// FakeIntArray: stand-in for jintArray. Drastic's getScreenBuffers
// writes pixel data into two caller-provided int arrays. We allocate
// these from DrasticRunner::initSurface and pass them into every
// renderOneFrame() call so drastic fills them with the current DS
// framebuffer contents.
// Both array pools start with an ArrayKind tag so the type-erased
// handlers (GetArrayLength, GetPrimitiveArrayCritical) can tell an int[]
// handle from a byte[] handle. Drastic mixes both: the cheat name/note
// getters return byte[] while getScreenBuffers / custom-cheat data use
// int[], and GetPrimitiveArrayCritical is shared between them.
enum ArrayKind : int { kArrInt = 0, kArrByte = 1 };

struct FakeIntArray {
    ArrayKind kind = kArrInt;   // MUST be first member
    std::vector<jint> data;
};

struct FakeByteArray {
    ArrayKind kind = kArrByte;  // MUST be first member
    std::vector<jbyte> data;
};

static inline ArrayKind arrayKindOf(jarray arr) {
    // Both structs keep `kind` as their first member, so this read is
    // valid for either array type.
    return arr ? *reinterpret_cast<const ArrayKind*>((void*)arr) : kArrInt;
}

// Trivial bump allocator for the above shims. Drastic opens files
// during init and then keeps the fds around for the lifetime of the
// session, so leaking these small structs is fine for our bounded
// preview. When we eventually shut down drastic we'll tear down the
// whole process, so no explicit free is needed.
//
// Using std::vector<unique_ptr> rather than raw new so that if we
// ever do want to reset() the pool we have the handles to do it
// cleanly.
static std::vector<std::unique_ptr<FakeString>> sStringPool;
static std::vector<std::unique_ptr<NativePathHandleShim>> sHandlePool;
static std::vector<std::unique_ptr<FakeIntArray>> sIntArrayPool;
static std::vector<std::unique_ptr<FakeByteArray>> sByteArrayPool;

static FakeString* allocString(const char* src) {
    auto s = std::make_unique<FakeString>();
    if (src) s->content = src;
    FakeString* raw = s.get();
    sStringPool.push_back(std::move(s));
    return raw;
}

// Public helper exposed to DrasticRunner: create a FakeIntArray with
// `length` zero-initialized jint elements. Stored in the pool; caller
// passes the returned pointer into JNI calls that expect a jintArray.
jintArray allocIntArray(jsize length) {
    auto a = std::make_unique<FakeIntArray>();
    a->data.resize(length, 0);
    FakeIntArray* raw = a.get();
    sIntArrayPool.push_back(std::move(a));
    return (jintArray)(void*)raw;
}

const jint* getIntArrayData(jintArray arr) {
    FakeIntArray* a = (FakeIntArray*)(void*)arr;
    return a ? a->data.data() : nullptr;
}

jsize getIntArrayLength(jintArray arr) {
    FakeIntArray* a = (FakeIntArray*)(void*)arr;
    return a ? (jsize)a->data.size() : 0;
}

// byte[] pool, mirroring the int[] pool above. drastic allocates these
// via NewByteArray (e.g. getCheatName) and fills them with
// SetByteArrayRegion; DrasticRunner reads them back via the helpers.
static jbyteArray allocByteArray(jsize length) {
    auto a = std::make_unique<FakeByteArray>();
    a->data.resize(length > 0 ? length : 0, 0);  // zero-init
    FakeByteArray* raw = a.get();
    sByteArrayPool.push_back(std::move(a));
    return (jbyteArray)(void*)raw;
}

const jbyte* getByteArrayData(jbyteArray arr) {
    FakeByteArray* a = (FakeByteArray*)(void*)arr;
    return (a && !a->data.empty()) ? a->data.data() : nullptr;
}

jsize getByteArrayLength(jbyteArray arr) {
    FakeByteArray* a = (FakeByteArray*)(void*)arr;
    return a ? (jsize)a->data.size() : 0;
}

static NativePathHandleShim* allocHandle(int fd, const char* path) {
    auto h = std::make_unique<NativePathHandleShim>();
    h->fileFd   = fd;
    h->filePath = allocString(path);

    // fileName = basename(path)
    const char* slash = path ? strrchr(path, '/') : nullptr;
    h->fileName = allocString(slash ? (slash + 1) : (path ? path : ""));

    NativePathHandleShim* raw = h.get();
    sHandlePool.push_back(std::move(h));
    return raw;
}

// -------- Virtual path translation --------
//
// Drastic's native code calls DraSticPathCache.open(virtualPath, mode)
// where virtualPath is either an absolute POSIX path (starts with /)
// or a virtual path prefixed with "DraStic/" or "User/" (see
// DraSticPathCache.smali:141 getRealPath).
//
// We translate:
//   "/foo/bar"        -> "/foo/bar"                       (absolute passthrough)
//   "DraStic/foo/bar" -> "<cacheRoot>/foo/bar"
//   "User/foo/bar"    -> "<cacheRoot>/user/foo/bar"
//   "foo/bar"         -> "<cacheRoot>/foo/bar" (best-effort fallback)
//
// Note: "DraStic/system/bios.bin" maps to "<cacheRoot>/system/bios.bin",
// matching the layout nano_cache.sh populate_drastic creates.
static std::string translateVirtualPath(const char* vpath) {
    if (!vpath || !*vpath) return {};
    if (vpath[0] == '/') return vpath;

    if (sCacheRoot.empty()) {
        ALOGW("FakeJNI: translateVirtualPath with empty cache root: %s", vpath);
        return vpath;
    }

    // Strip known prefixes
    const char* kSysPrefix  = "DraStic/";
    const char* kUserPrefix = "User/";
    size_t sysLen  = strlen(kSysPrefix);
    size_t userLen = strlen(kUserPrefix);

    if (strncmp(vpath, kSysPrefix, sysLen) == 0) {
        return sCacheRoot + "/" + (vpath + sysLen);
    }
    if (strncmp(vpath, kUserPrefix, userLen) == 0) {
        if (sDirectUserMode) {
            return sCacheRoot + "/" + (vpath + userLen);
        }
        return sCacheRoot + "/user/" + (vpath + userLen);
    }

    // Unknown prefix -- resolve under sys root as a best-effort guess.
    return sCacheRoot + "/" + vpath;
}

// -------- fopen-style mode string -> POSIX open flags --------
static int translateOpenMode(const char* mode) {
    if (!mode || !*mode) return O_RDONLY;
    bool hasPlus   = strchr(mode, '+') != nullptr;
    bool hasRead   = strchr(mode, 'r') != nullptr;
    bool hasWrite  = strchr(mode, 'w') != nullptr;
    bool hasAppend = strchr(mode, 'a') != nullptr;

    int flags = 0;
    if (hasRead && !hasWrite && !hasAppend) {
        flags = hasPlus ? O_RDWR : O_RDONLY;
    } else if (hasWrite) {
        flags = (hasPlus ? O_RDWR : O_WRONLY) | O_CREAT | O_TRUNC;
    } else if (hasAppend) {
        flags = (hasPlus ? O_RDWR : O_WRONLY) | O_CREAT | O_APPEND;
    } else {
        flags = O_RDONLY;
    }
    return flags;
}

// -------- dispatch implementations for DraSticPathCache --------

// Recursively mkdir -p equivalent. Creates all parent dirs of `path`
// up to but not including the final leaf. Errors are ignored -- the
// open() call that follows will report anything fatal.
static void ensureParentDirs(const std::string& path) {
    size_t pos = 0;
    while ((pos = path.find('/', pos + 1)) != std::string::npos) {
        std::string dir = path.substr(0, pos);
        mkdir(dir.c_str(), 0777);
    }
}

static NativePathHandleShim* dispatchOpen(const char* vpath, const char* mode) {
    std::string realPath = translateVirtualPath(vpath);
    int flags = translateOpenMode(mode);

    // If opening for write, make sure the parent directory exists --
    // drastic's error handling calls fclose(NULL) on open failures
    // (we observed a FORTIFY abort during Phase 3 testing when the
    // User/backup/ subdir was missing). Creating parents ahead of
    // time is safer than relying on populate_drastic covering every
    // possible subdir.
    if (flags & O_CREAT) {
        ensureParentDirs(realPath);
    }

    int fd = open(realPath.c_str(), flags, 0644);
    if (fd < 0) {
        ALOGW("FakeJNI: open failed: vpath=\"%s\" real=\"%s\" mode=\"%s\" errno=%d(%s)",
              vpath ? vpath : "(null)", realPath.c_str(),
              mode ? mode : "(null)", errno, strerror(errno));
        // Still return a shim with fd=-1 so drastic can distinguish
        // "file not found" from a hard failure by reading .fileFd.
        return allocHandle(-1, realPath.c_str());
    }
    ALOGI("FakeJNI: open ok: vpath=\"%s\" real=\"%s\" mode=\"%s\" fd=%d",
          vpath, realPath.c_str(), mode ? mode : "(null)", fd);
    return allocHandle(fd, realPath.c_str());
}

static jboolean dispatchRename(const char* fromVpath, const char* toVpath) {
    std::string from = translateVirtualPath(fromVpath);
    std::string to   = translateVirtualPath(toVpath);
    if (rename(from.c_str(), to.c_str()) == 0) {
        ALOGI("FakeJNI: rename ok: \"%s\" -> \"%s\"", from.c_str(), to.c_str());
        return JNI_TRUE;
    }
    ALOGW("FakeJNI: rename failed: \"%s\" -> \"%s\" errno=%d",
          from.c_str(), to.c_str(), errno);
    return JNI_FALSE;
}

static jboolean dispatchRemove(const char* vpath) {
    std::string real = translateVirtualPath(vpath);
    if (unlink(real.c_str()) == 0) {
        ALOGI("FakeJNI: remove ok: \"%s\"", real.c_str());
        return JNI_TRUE;
    }
    ALOGW("FakeJNI: remove failed: \"%s\" errno=%d", real.c_str(), errno);
    return JNI_FALSE;
}

static JNINativeInterface  sJniFunctions;
static JNIInvokeInterface  sVmFunctions;

// These two structs must start with a pointer to the vtable to match
// the layout Android's jni.h expects from _JNIEnv / _JavaVM. In C++
// JNIEnv is a typedef for _JNIEnv (a struct whose first field is
// `const JNINativeInterface* functions`) and JavaVM for _JavaVM (same
// pattern with JNIInvokeInterface). Drastic accesses the vtable via
// `(*env)->...` which dereferences the first pointer; our struct is
// ABI-compatible with that access pattern.
struct FakeEnvStruct  { const JNINativeInterface* functions; };
struct FakeVmStruct   { const JNIInvokeInterface*  functions; };

static FakeEnvStruct sFakeEnv;
static FakeVmStruct  sFakeVm;

static bool sInitialized = false;

// -------- JavaVM vtable entries --------

static jint JNICALL FakeGetEnv(JavaVM* vm, void** penv, jint version) {
    (void)vm;
    ALOGI("FakeJNI: GetEnv(version=0x%x)", version);
    *penv = &sFakeEnv;
    return JNI_OK;
}

static jint JNICALL FakeAttachCurrentThread(JavaVM* vm, JNIEnv** penv, void* args) {
    (void)vm; (void)args;
    ALOGI("FakeJNI: AttachCurrentThread");
    *penv = (JNIEnv*)(void*)&sFakeEnv;
    return JNI_OK;
}

static jint JNICALL FakeDetachCurrentThread(JavaVM* vm) {
    (void)vm;
    ALOGI("FakeJNI: DetachCurrentThread");
    return JNI_OK;
}

// -------- JNIEnv vtable entries --------

static jint JNICALL FakeGetVersion(JNIEnv* env) {
    (void)env;
    return JNI_VERSION_1_6;
}

static jclass JNICALL FakeFindClass(JNIEnv* env, const char* name) {
    (void)env;
    ALOGI("FakeJNI: FindClass(\"%s\")", name);
    if (strcmp(name, "com/dsemu/drastic/filesystem/NativePathHandle") == 0) {
        return (jclass)(void*)tag::kClassPathHandle;
    }
    if (strcmp(name, "com/dsemu/drastic/filesystem/DraSticPathCache") == 0) {
        return (jclass)(void*)tag::kClassPathCache;
    }
    ALOGE("FakeJNI: FindClass: unknown class \"%s\"", name);
    return nullptr;
}

static jobject JNICALL FakeNewGlobalRef(JNIEnv* env, jobject obj) {
    (void)env;
    // Identity: our refs are plain tag pointers, no refcount needed.
    return obj;
}

static void JNICALL FakeDeleteGlobalRef(JNIEnv* env, jobject obj) {
    (void)env; (void)obj;
}

static void JNICALL FakeDeleteLocalRef(JNIEnv* env, jobject obj) {
    (void)env; (void)obj;
}

static jfieldID JNICALL FakeGetFieldID(JNIEnv* env, jclass cls,
                                       const char* name, const char* sig) {
    (void)env; (void)sig;
    uintptr_t clsTag = (uintptr_t)cls;
    ALOGI("FakeJNI: GetFieldID(cls=0x%lx, name=\"%s\", sig=\"%s\")",
          (unsigned long)clsTag, name, sig);

    if (clsTag != tag::kClassPathHandle) {
        ALOGE("FakeJNI: GetFieldID on unexpected class tag 0x%lx",
              (unsigned long)clsTag);
        return nullptr;
    }
    if (strcmp(name, "filePath") == 0) return (jfieldID)(void*)tag::kFieldFilePath;
    if (strcmp(name, "fileFd")   == 0) return (jfieldID)(void*)tag::kFieldFileFd;
    if (strcmp(name, "fileName") == 0) return (jfieldID)(void*)tag::kFieldFileName;
    ALOGE("FakeJNI: GetFieldID: unknown field \"%s\"", name);
    return nullptr;
}

static jmethodID JNICALL FakeGetStaticMethodID(JNIEnv* env, jclass cls,
                                               const char* name, const char* sig) {
    (void)env; (void)sig;
    uintptr_t clsTag = (uintptr_t)cls;
    ALOGI("FakeJNI: GetStaticMethodID(cls=0x%lx, name=\"%s\", sig=\"%s\")",
          (unsigned long)clsTag, name, sig);

    if (clsTag != tag::kClassPathCache) {
        ALOGE("FakeJNI: GetStaticMethodID on unexpected class tag 0x%lx",
              (unsigned long)clsTag);
        return nullptr;
    }
    if (strcmp(name, "open")   == 0) return (jmethodID)(void*)tag::kMethodOpen;
    if (strcmp(name, "rename") == 0) return (jmethodID)(void*)tag::kMethodRename;
    if (strcmp(name, "remove") == 0) return (jmethodID)(void*)tag::kMethodRemove;
    ALOGE("FakeJNI: GetStaticMethodID: unknown method \"%s\"", name);
    return nullptr;
}

// -------- Defensive stubs for calls drastic might make after JNI_OnLoad --
// These exist so that if drastic takes an unexpected path in a later phase
// we get a loud log rather than a segfault. Phase 3+ will replace the
// filesystem-related ones with real implementations.

static jthrowable JNICALL FakeExceptionOccurred(JNIEnv* env) {
    (void)env;
    return nullptr;  // never an exception in our fake world
}

static void JNICALL FakeExceptionClear(JNIEnv* env) { (void)env; }
static void JNICALL FakeExceptionDescribe(JNIEnv* env) { (void)env; }
static jboolean JNICALL FakeExceptionCheck(JNIEnv* env) { (void)env; return JNI_FALSE; }

static jstring JNICALL FakeNewStringUTF(JNIEnv* env, const char* utf) {
    (void)env;
    FakeString* s = allocString(utf);
    return (jstring)(void*)s;
}

static const char* JNICALL FakeGetStringUTFChars(JNIEnv* env, jstring str, jboolean* isCopy) {
    (void)env;
    if (isCopy) *isCopy = JNI_FALSE;
    FakeString* s = (FakeString*)(void*)str;
    if (!s) return "";
    return s->content.c_str();
}

static void JNICALL FakeReleaseStringUTFChars(JNIEnv* env, jstring str, const char* chars) {
    // No-op: our strings live in the pool for the lifetime of the session
    (void)env; (void)str; (void)chars;
}

static jsize JNICALL FakeGetStringUTFLength(JNIEnv* env, jstring str) {
    (void)env;
    FakeString* s = (FakeString*)(void*)str;
    return s ? (jsize)s->content.size() : 0;
}

static jsize JNICALL FakeGetStringLength(JNIEnv* env, jstring str) {
    (void)env;
    FakeString* s = (FakeString*)(void*)str;
    // Good enough: drastic's use of GetStringLength is rare, and ASCII
    // paths have length == UTF8 length.
    return s ? (jsize)s->content.size() : 0;
}

static const jchar* JNICALL FakeGetStringChars(JNIEnv* env, jstring str,
                                                jboolean* isCopy) {
    (void)env;
    if (isCopy) *isCopy = JNI_FALSE;
    FakeString* s = (FakeString*)(void*)str;
    if (!s) return nullptr;
    // Build UTF-16 lazily on first call. For ASCII input (the only
    // form drastic feeds us for setFirmwareUserdata) zero-extending
    // each byte to a jchar is correct.
    if (s->utf16.empty() && !s->content.empty()) {
        s->utf16.reserve(s->content.size() + 1);
        for (unsigned char c : s->content) {
            s->utf16.push_back((jchar)c);
        }
        s->utf16.push_back(0);
    }
    return s->utf16.empty() ? (const jchar*)u"" : s->utf16.data();
}

static void JNICALL FakeReleaseStringChars(JNIEnv* env, jstring str,
                                            const jchar* chars) {
    (void)env; (void)str; (void)chars;
    // Lives in the string pool for the session; nothing to free.
}

static jobject JNICALL FakeGetObjectField(JNIEnv* env, jobject obj, jfieldID id) {
    (void)env;
    NativePathHandleShim* h = (NativePathHandleShim*)(void*)obj;
    if (!h) {
        ALOGW("FakeJNI: GetObjectField on null handle");
        return nullptr;
    }
    uintptr_t fid = (uintptr_t)id;
    if (fid == tag::kFieldFilePath) return (jobject)(void*)h->filePath;
    if (fid == tag::kFieldFileName) return (jobject)(void*)h->fileName;
    ALOGW("FakeJNI: GetObjectField unknown field 0x%lx", (unsigned long)fid);
    return nullptr;
}

static jint JNICALL FakeGetIntField(JNIEnv* env, jobject obj, jfieldID id) {
    (void)env;
    NativePathHandleShim* h = (NativePathHandleShim*)(void*)obj;
    if (!h) {
        ALOGW("FakeJNI: GetIntField on null handle");
        return -1;
    }
    uintptr_t fid = (uintptr_t)id;
    if (fid == tag::kFieldFileFd) return (jint)h->fileFd;
    ALOGW("FakeJNI: GetIntField unknown field 0x%lx", (unsigned long)fid);
    return -1;
}

// -------- DraSticPathCache method dispatchers --------
//
// The varargs and va_list variants both funnel into the shared
// dispatchFoo() helpers. Drastic's native code calls through the C
// JNI interface `(*env)->CallStaticObjectMethod(env, cls, mid, ...)`
// which hits the varargs vtable entry; the `V` variants are stubbed
// in for defensive completeness (C++ inline methods forward through
// them).

static jobject JNICALL FakeCallStaticObjectMethodV(JNIEnv* env, jclass cls,
                                                    jmethodID m, va_list args) {
    (void)env; (void)cls;
    uintptr_t mid = (uintptr_t)m;

    if (mid == tag::kMethodOpen) {
        jstring jpath = va_arg(args, jstring);
        jstring jmode = va_arg(args, jstring);
        FakeString* sPath = (FakeString*)(void*)jpath;
        FakeString* sMode = (FakeString*)(void*)jmode;
        const char* path = sPath ? sPath->content.c_str() : nullptr;
        const char* mode = sMode ? sMode->content.c_str() : nullptr;
        return (jobject)(void*)dispatchOpen(path, mode);
    }

    ALOGW("FakeJNI: CallStaticObjectMethodV unknown method 0x%lx",
          (unsigned long)mid);
    return nullptr;
}

static jobject JNICALL FakeCallStaticObjectMethod(JNIEnv* env, jclass cls,
                                                   jmethodID m, ...) {
    va_list ap;
    va_start(ap, m);
    jobject r = FakeCallStaticObjectMethodV(env, cls, m, ap);
    va_end(ap);
    return r;
}

static jboolean JNICALL FakeCallStaticBooleanMethodV(JNIEnv* env, jclass cls,
                                                      jmethodID m, va_list args) {
    (void)env; (void)cls;
    uintptr_t mid = (uintptr_t)m;

    if (mid == tag::kMethodRename) {
        jstring jfrom = va_arg(args, jstring);
        jstring jto   = va_arg(args, jstring);
        FakeString* sFrom = (FakeString*)(void*)jfrom;
        FakeString* sTo   = (FakeString*)(void*)jto;
        return dispatchRename(sFrom ? sFrom->content.c_str() : nullptr,
                              sTo ? sTo->content.c_str() : nullptr);
    }
    if (mid == tag::kMethodRemove) {
        jstring jpath = va_arg(args, jstring);
        FakeString* sPath = (FakeString*)(void*)jpath;
        return dispatchRemove(sPath ? sPath->content.c_str() : nullptr);
    }

    ALOGW("FakeJNI: CallStaticBooleanMethodV unknown method 0x%lx",
          (unsigned long)mid);
    return JNI_FALSE;
}

static jboolean JNICALL FakeCallStaticBooleanMethod(JNIEnv* env, jclass cls,
                                                     jmethodID m, ...) {
    va_list ap;
    va_start(ap, m);
    jboolean r = FakeCallStaticBooleanMethodV(env, cls, m, ap);
    va_end(ap);
    return r;
}

// NativePathHandle is never constructed via NewObject by drastic's
// native side (JNI_OnLoad only caches field IDs, not a <init>
// methodID), but we keep these stubs in case a later path tries it.

// -------- int array dispatchers (drastic getScreenBuffers path) --

static jsize JNICALL FakeGetArrayLength(JNIEnv* env, jarray arr) {
    (void)env;
    if (!arr) return 0;
    if (arrayKindOf(arr) == kArrByte) {
        return (jsize)((FakeByteArray*)(void*)arr)->data.size();
    }
    return (jsize)((FakeIntArray*)(void*)arr)->data.size();
}

static jbyteArray JNICALL FakeNewByteArray(JNIEnv* env, jsize length) {
    (void)env;
    return allocByteArray(length);
}

static jbyte* JNICALL FakeGetByteArrayElements(JNIEnv* env, jbyteArray arr,
                                               jboolean* isCopy) {
    (void)env;
    if (isCopy) *isCopy = JNI_FALSE;
    FakeByteArray* a = (FakeByteArray*)(void*)arr;
    return (a && !a->data.empty()) ? a->data.data() : nullptr;
}

static void JNICALL FakeReleaseByteArrayElements(JNIEnv* env, jbyteArray arr,
                                                 jbyte* elems, jint mode) {
    (void)env; (void)arr; (void)elems; (void)mode;  // direct pointer, no-op
}

static void JNICALL FakeGetByteArrayRegion(JNIEnv* env, jbyteArray arr,
                                           jsize start, jsize len, jbyte* buf) {
    (void)env;
    FakeByteArray* a = (FakeByteArray*)(void*)arr;
    if (!a || !buf) return;
    if (start < 0 || len <= 0) return;
    if ((size_t)(start + len) > a->data.size()) return;
    memcpy(buf, a->data.data() + start, (size_t)len);
}

static void JNICALL FakeSetByteArrayRegion(JNIEnv* env, jbyteArray arr,
                                           jsize start, jsize len,
                                           const jbyte* buf) {
    (void)env;
    FakeByteArray* a = (FakeByteArray*)(void*)arr;
    if (!a || !buf) return;
    if (start < 0 || len <= 0) return;
    if ((size_t)(start + len) > a->data.size()) return;
    memcpy(a->data.data() + start, buf, (size_t)len);
}

static jintArray JNICALL FakeNewIntArray(JNIEnv* env, jsize length) {
    (void)env;
    return allocIntArray(length);
}

static jint* JNICALL FakeGetIntArrayElements(JNIEnv* env, jintArray arr,
                                              jboolean* isCopy) {
    (void)env;
    if (isCopy) *isCopy = JNI_FALSE;
    FakeIntArray* a = (FakeIntArray*)(void*)arr;
    return a ? a->data.data() : nullptr;
}

static void JNICALL FakeReleaseIntArrayElements(JNIEnv* env, jintArray arr,
                                                 jint* elems, jint mode) {
    // No-op: we returned a direct pointer, commit is implicit.
    (void)env; (void)arr; (void)elems; (void)mode;
}

static void JNICALL FakeGetIntArrayRegion(JNIEnv* env, jintArray arr,
                                           jsize start, jsize len, jint* buf) {
    (void)env;
    FakeIntArray* a = (FakeIntArray*)(void*)arr;
    if (!a || !buf) return;
    if (start < 0 || len <= 0) return;
    if ((size_t)(start + len) > a->data.size()) return;
    memcpy(buf, a->data.data() + start, len * sizeof(jint));
}

static void JNICALL FakeSetIntArrayRegion(JNIEnv* env, jintArray arr,
                                           jsize start, jsize len,
                                           const jint* buf) {
    (void)env;
    FakeIntArray* a = (FakeIntArray*)(void*)arr;
    if (!a || !buf) return;
    if (start < 0 || len <= 0) return;
    if ((size_t)(start + len) > a->data.size()) return;
    memcpy(a->data.data() + start, buf, len * sizeof(jint));
}

// Primitive array critical access. Drastic's getScreenBuffers most
// likely uses this (not GetIntArrayElements) because pinning is
// cheaper. We treat our backing std::vector as always "pinned" --
// GetPrimitiveArrayCritical returns the raw data pointer, release
// is a no-op.
static void* JNICALL FakeGetPrimitiveArrayCritical(JNIEnv* env, jarray arr,
                                                    jboolean* isCopy) {
    (void)env;
    if (isCopy) *isCopy = JNI_FALSE;
    if (!arr) return nullptr;
    if (arrayKindOf(arr) == kArrByte) {
        auto* b = (FakeByteArray*)(void*)arr;
        return b->data.empty() ? nullptr : (void*)b->data.data();
    }
    auto* a = (FakeIntArray*)(void*)arr;
    return a->data.empty() ? nullptr : (void*)a->data.data();
}

static void JNICALL FakeReleasePrimitiveArrayCritical(JNIEnv* env, jarray arr,
                                                       void* carray, jint mode) {
    (void)env; (void)arr; (void)carray; (void)mode;
}

static jobject JNICALL FakeNewObject(JNIEnv* env, jclass cls, jmethodID m, ...) {
    (void)env; (void)cls;
    ALOGW("FakeJNI: NewObject(0x%lx) [stub]", (unsigned long)(uintptr_t)m);
    return nullptr;
}

static jobject JNICALL FakeNewObjectV(JNIEnv* env, jclass cls, jmethodID m, va_list args) {
    (void)env; (void)cls; (void)args;
    ALOGW("FakeJNI: NewObjectV(0x%lx) [stub]", (unsigned long)(uintptr_t)m);
    return nullptr;
}

static jclass JNICALL FakeGetObjectClass(JNIEnv* env, jobject obj) {
    (void)env;
    ALOGW("FakeJNI: GetObjectClass(%p) [stub]", obj);
    return nullptr;
}

static jboolean JNICALL FakeIsSameObject(JNIEnv* env, jobject a, jobject b) {
    (void)env;
    return (a == b) ? JNI_TRUE : JNI_FALSE;
}

// -------- Setup --------

static void setupVtables() {
    if (sInitialized) return;
    sInitialized = true;

    memset(&sJniFunctions, 0, sizeof(sJniFunctions));
    memset(&sVmFunctions, 0, sizeof(sVmFunctions));

    // JavaVM vtable
    sVmFunctions.GetEnv              = FakeGetEnv;
    sVmFunctions.AttachCurrentThread = FakeAttachCurrentThread;
    sVmFunctions.DetachCurrentThread = FakeDetachCurrentThread;

    // JNIEnv vtable -- entries JNI_OnLoad actually uses
    sJniFunctions.GetVersion         = FakeGetVersion;
    sJniFunctions.FindClass          = FakeFindClass;
    sJniFunctions.NewGlobalRef       = FakeNewGlobalRef;
    sJniFunctions.DeleteGlobalRef    = FakeDeleteGlobalRef;
    sJniFunctions.DeleteLocalRef     = FakeDeleteLocalRef;
    sJniFunctions.GetFieldID         = FakeGetFieldID;
    sJniFunctions.GetStaticMethodID  = FakeGetStaticMethodID;

    // Defensive stubs -- future phases will replace these with real
    // implementations, but for Phase 1 we just want a loud log if drastic
    // tries to use them rather than a segfault.
    sJniFunctions.GetObjectClass            = FakeGetObjectClass;
    sJniFunctions.IsSameObject              = FakeIsSameObject;
    sJniFunctions.NewStringUTF              = FakeNewStringUTF;
    sJniFunctions.GetStringUTFChars         = FakeGetStringUTFChars;
    sJniFunctions.ReleaseStringUTFChars     = FakeReleaseStringUTFChars;
    sJniFunctions.GetStringLength           = FakeGetStringLength;
    sJniFunctions.GetStringUTFLength        = FakeGetStringUTFLength;
    sJniFunctions.GetStringChars            = FakeGetStringChars;
    sJniFunctions.ReleaseStringChars        = FakeReleaseStringChars;
    sJniFunctions.GetObjectField            = FakeGetObjectField;
    sJniFunctions.GetIntField               = FakeGetIntField;
    sJniFunctions.GetArrayLength            = FakeGetArrayLength;
    sJniFunctions.NewIntArray               = FakeNewIntArray;
    sJniFunctions.GetIntArrayElements       = FakeGetIntArrayElements;
    sJniFunctions.ReleaseIntArrayElements   = FakeReleaseIntArrayElements;
    sJniFunctions.GetIntArrayRegion         = FakeGetIntArrayRegion;
    sJniFunctions.SetIntArrayRegion         = FakeSetIntArrayRegion;
    sJniFunctions.NewByteArray              = FakeNewByteArray;
    sJniFunctions.GetByteArrayElements      = FakeGetByteArrayElements;
    sJniFunctions.ReleaseByteArrayElements  = FakeReleaseByteArrayElements;
    sJniFunctions.GetByteArrayRegion        = FakeGetByteArrayRegion;
    sJniFunctions.SetByteArrayRegion        = FakeSetByteArrayRegion;
    sJniFunctions.GetPrimitiveArrayCritical = FakeGetPrimitiveArrayCritical;
    sJniFunctions.ReleasePrimitiveArrayCritical = FakeReleasePrimitiveArrayCritical;
    sJniFunctions.CallStaticObjectMethod    = FakeCallStaticObjectMethod;
    sJniFunctions.CallStaticObjectMethodV   = FakeCallStaticObjectMethodV;
    sJniFunctions.CallStaticBooleanMethod   = FakeCallStaticBooleanMethod;
    sJniFunctions.CallStaticBooleanMethodV  = FakeCallStaticBooleanMethodV;
    sJniFunctions.NewObject                 = FakeNewObject;
    sJniFunctions.NewObjectV                = FakeNewObjectV;
    sJniFunctions.ExceptionOccurred         = FakeExceptionOccurred;
    sJniFunctions.ExceptionClear            = FakeExceptionClear;
    sJniFunctions.ExceptionDescribe         = FakeExceptionDescribe;
    sJniFunctions.ExceptionCheck            = FakeExceptionCheck;

    sFakeEnv.functions = &sJniFunctions;
    sFakeVm.functions  = &sVmFunctions;
}

JavaVM* init() {
    setupVtables();
    return (JavaVM*)(void*)&sFakeVm;
}

void setCacheRoot(const std::string& cacheRoot) {
    sCacheRoot = cacheRoot;
}

void setDirectUserMode(bool enabled) {
    sDirectUserMode = enabled;
}

} // namespace fakejni
} // namespace android

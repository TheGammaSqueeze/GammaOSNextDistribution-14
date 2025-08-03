// Updated using config.h.meson
#define _GNU_SOURCE
#define VIRGL_RENDERER_UNSTABLE_APIS 1
#define HAVE___BUILTIN_BSWAP32 1
#define HAVE___BUILTIN_BSWAP64 1
#define HAVE___BUILTIN_CLZ 1
#define HAVE___BUILTIN_CLZLL 1
#define HAVE___BUILTIN_EXPECT 1
#define HAVE___BUILTIN_FFS 1
#define HAVE___BUILTIN_FFSLL 1
#define HAVE___BUILTIN_POPCOUNT 1
#define HAVE___BUILTIN_POPCOUNTLL 1
#define HAVE___BUILTIN_TYPES_COMPATIBLE_P 1
#define HAVE___BUILTIN_UNREACHABLE 1
#define HAVE_FUNC_ATTRIBUTE_CONST 1
#define HAVE_FUNC_ATTRIBUTE_FLATTEN 1
#define HAVE_FUNC_ATTRIBUTE_FORMAT 1
#define HAVE_FUNC_ATTRIBUTE_MALLOC 1
#define HAVE_FUNC_ATTRIBUTE_NORETURN 1
#define HAVE_FUNC_ATTRIBUTE_PACKED 1
#define HAVE_FUNC_ATTRIBUTE_PURE 1
#define HAVE_FUNC_ATTRIBUTE_RETURNS_NONNULL 1
#define HAVE_FUNC_ATTRIBUTE_UNUSED 1
#define HAVE_FUNC_ATTRIBUTE_WARN_UNUSED_RESULT 1
#define HAVE_FUNC_ATTRIBUTE_WEAK 1
// The glibc host toolchain lacks support for memfd, but bionic supports it,
// so this define is enabled only for 'android:' in Android.bp
//#define HAVE_MEMFD_CREATE 1
#define HAVE_STRTOK_R 1
#define HAVE_TIMESPEC_GET 1
#define HAVE_SYS_UIO_H 1
#define HAVE_PTHREAD 1

// Currently must be disabled because ANDROID code in virglrenderer
// is broken. This should be fixed upstream.
//#define HAVE_PTHREAD_SETAFFINITY 1

#define HAVE_EPOXY_EGL_H 1

// No X11/GLX support
//#define HAVE_EPOXY_GLX_H 1

// Performance impacting
//#define CHECK_GL_ERRORS 1

// Avoid dependency on minigbm
//#define ENABLE_MINIGBM_ALLOCATION 1

// Disable experimental venus support (for now)
//#define ENABLE_VENUS 1
//#define ENABLE_VENUS_VALIDATE 1

// Disable direct DRM support - only used by freedreno
//#define ENABLE_DRM 1
//#define ENABLE_DRM_MSM 1

// Disable render server (for now)
//#define ENABLE_RENDER_SERVER 1
//#define ENABLE_RENDER_SERVER_WORKER_PROCESS 1
//#define ENABLE_RENDER_SERVER_WORKER_THREAD 1
//#define ENABLE_RENDER_SERVER_WORKER_MINIJAIL 1
//#define RENDER_SERVER_EXEC_PATH 1

#define HAVE_EVENTFD_H 1
#define HAVE_DLFCN_H 1

// Disable tracing - performance impacting
//#define ENABLE_TRACING 1

// Android only supports little endian on target and host
#define UTIL_ARCH_LITTLE_ENDIAN 1
#define UTIL_ARCH_BIG_ENDIAN 0

// Architecture-specific CPU detection code
//#define PIPE_ARCH_X86 1

// Keep simple_mtx.h happy
#define HAVE_LINUX_FUTEX_H 1

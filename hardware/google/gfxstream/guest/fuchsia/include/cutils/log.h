#ifndef __CUTILS_LOG_H__
#define __CUTILS_LOG_H__

#ifndef LOG_TAG
#define LOG_TAG nullptr
#endif

/*
 * Normally we strip the effects of ALOGV (VERBOSE messages),
 * LOG_FATAL and LOG_FATAL_IF (FATAL assert messages) from the
 * release builds be defining NDEBUG.  You can modify this (for
 * example with "#define LOG_NDEBUG 0" at the top of your source
 * file) to change that behavior.
 */

#ifndef LOG_NDEBUG
#ifdef NDEBUG
#define LOG_NDEBUG 1
#else
#define LOG_NDEBUG 0
#endif
#endif

/*
 * Use __VA_ARGS__ if running a static analyzer,
 * to avoid warnings of unused variables in __VA_ARGS__.
 * Use constexpr function in C++ mode, so these macros can be used
 * in other constexpr functions without warning.
 */
#ifdef __clang_analyzer__
#ifdef __cplusplus
extern "C++" {
template <typename... Ts>
constexpr int __fake_use_va_args(Ts...) {
  return 0;
}
}
#else
extern int __fake_use_va_args(int, ...);
#endif /* __cplusplus */
#define __FAKE_USE_VA_ARGS(...) ((void)__fake_use_va_args(0, ##__VA_ARGS__))
#else
#define __FAKE_USE_VA_ARGS(...) ((void)(0))
#endif /* __clang_analyzer__ */

enum {
  ANDROID_LOG_UNKNOWN = 0,
  ANDROID_LOG_DEFAULT,
  ANDROID_LOG_VERBOSE,
  ANDROID_LOG_DEBUG,
  ANDROID_LOG_INFO,
  ANDROID_LOG_WARN,
  ANDROID_LOG_ERROR,
  ANDROID_LOG_FATAL,
  ANDROID_LOG_SILENT,
};

#define android_printLog(prio, tag, format, ...) \
  __android_log_print(prio, tag, __FILE__, __LINE__, "[prio %d] " format, prio, ##__VA_ARGS__)

#define LOG_PRI(priority, tag, ...) android_printLog(priority, tag, __VA_ARGS__)
#define ALOG(priority, tag, ...) LOG_PRI(ANDROID_##priority, tag, __VA_ARGS__)

#define __android_second(dummy, second, ...) second
#define __android_rest(first, ...) , ##__VA_ARGS__

#define android_printAssert(condition, tag, format, ...)                                    \
  __android_log_assert(condition, tag, __FILE__, __LINE__, "assert: condition: %s " format, \
                       condition, ##__VA_ARGS__)

#define LOG_ALWAYS_FATAL_IF(condition, ...)                              \
  ((condition)                                                           \
       ? ((void)android_printAssert(#condition, LOG_TAG, ##__VA_ARGS__)) \
       : (void)0)

#define LOG_ALWAYS_FATAL(...) \
  (((void)android_printAssert(NULL, LOG_TAG, ##__VA_ARGS__)))

/*
 * Simplified macro to send a verbose log message using the current LOG_TAG.
 */
#define __ALOGV(...) ((void)ALOG(LOG_VERBOSE, LOG_TAG, __VA_ARGS__))
#if LOG_NDEBUG
#define ALOGV(...)                   \
  do {                               \
    __FAKE_USE_VA_ARGS(__VA_ARGS__); \
    if (false) {                     \
      __ALOGV(__VA_ARGS__);          \
    }                                \
  } while (false)
#else
#define ALOGV(...) __ALOGV(__VA_ARGS__)
#endif

#define ALOGE(...) ((void)ALOG(LOG_ERROR, LOG_TAG, __VA_ARGS__))
#define ALOGW(...) ((void)ALOG(LOG_WARN, LOG_TAG, __VA_ARGS__))
#define ALOGI(...) ((void)ALOG(LOG_INFO, LOG_TAG, __VA_ARGS__))
#define ALOGD(...) ((void)ALOG(LOG_DEBUG, LOG_TAG, __VA_ARGS__))

#if LOG_NDEBUG

#define LOG_FATAL_IF(cond, ...) __FAKE_USE_VA_ARGS(__VA_ARGS__)

#define LOG_FATAL(...) __FAKE_USE_VA_ARGS(__VA_ARGS__)

#else

#define LOG_FATAL_IF(cond, ...) LOG_ALWAYS_FATAL_IF(cond, ##__VA_ARGS__)

#define LOG_FATAL(...) LOG_ALWAYS_FATAL(__VA_ARGS__)

#endif

#define ALOG_ASSERT(cond, ...) LOG_FATAL_IF(!(cond), ##__VA_ARGS__)

extern "C" {

int __android_log_print(int priority, const char* tag, const char* file, int line,
                        const char* format, ...);

[[noreturn]] void __android_log_assert(const char* condition, const char* tag, const char* file,
                                       int line, const char* format, ...);
}

#endif

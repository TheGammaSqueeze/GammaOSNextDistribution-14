#ifndef __LOG_LOG_H__
#define __LOG_LOG_H__

#include <cutils/log.h>
#include <stdarg.h>
#include <stdint.h>

#ifdef __Fuchsia__
extern "C" {
void gfxstream_fuchsia_log(int8_t severity, const char* tag, const char* file, int line,
                           const char* format, va_list va);
}
#endif

#endif

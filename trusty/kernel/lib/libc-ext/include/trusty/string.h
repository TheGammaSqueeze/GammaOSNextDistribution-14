#pragma once

#include <lk/compiler.h>
#include <stdarg.h>
#include <stddef.h>

__BEGIN_CDECLS

/*
 * scnprintf/vscnprintf is like snprintf/vsnprintf, but returns the number of
 * characters actually written to the buffer rather than the number it would
 * write if given arbitrary space.
 */

/**
 * scnprintf()
 * @buf - output buffer
 * @size - amount of space available in the output buffer
 * @fmt - printf-style format string
 * @... - arguments to format in the format string
 *
 * scnprintf() is like snprintf(), but returns the amount of space it used in
 * the buffer rather than how large the formatted string would be.
 *
 * Specifically, scnprintf will use printf semantics to expand @fmt with @...,
 * writing the first @size characters to the buffer.
 *
 * Return: The number of characters written to the buffer.
 */
__attribute__((__format__ (__printf__, 3, 4))) /* */
int scnprintf(char* buf, size_t size, const char* fmt, ...);

/**
 * vscnprintf()
 * @buf - output buffer
 * @size - amount of space available in the output buffer
 * @fmt - printf-style format string
 * @args - arguments to format in the format string
 *
 * vscnprintf() is like vsnprintf(), but returns the amount of space it used in
 * the buffer rather than how large the formatted string would be.
 *
 * Specifically, vscnprintf will use printf semantics to expand @fmt with
 * @args, writing the first @size characters to the buffer.
 *
 * Return: The number of characters written to the buffer.
 */
__attribute__((__format__ (__printf__, 3, 0))) /* */
int vscnprintf(char* buf, size_t size, const char* fmt, va_list args);

__END_CDECLS

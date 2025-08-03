/*
 * Copyright (C) 2018 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#ifndef _USFSTL_ASSERT_H_
#define _USFSTL_ASSERT_H_
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <stdbool.h>

/*
 * usfstl_abort - abort a test and print the given message(s)
 */
static inline void __attribute__((noreturn, format(printf, 4, 5)))
usfstl_abort(const char *fn, unsigned int line, const char *cond, const char *msg, ...)
{
	va_list va;

	printf("assertion failure in %s (line %d)\n", fn, line);
	printf("'%s' failed\n", cond);
	va_start(va, msg);
	vprintf(msg, va);
	va_end(va);
	abort();
}

#define USFSTL_BUILD_BUG_ON(expr)	extern void __bbo_##__LINE__(char[1 - 2*!!(expr)]);

#define _USFSTL_2STR(x)			#x
#define USFSTL_2STR(x)			_USFSTL_2STR(x)

#define __USFSTL_COUNTDOWN		10,9,8,7,6,5,4,3,2,1,0
#define __USFSTL_COUNT(_0, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, N, ...)	N
#define _USFSTL_COUNT(...)		__USFSTL_COUNT(__VA_ARGS__)
#define USFSTL_COUNT(...)		_USFSTL_COUNT(, ##__VA_ARGS__, __USFSTL_COUNTDOWN)
#define __USFSTL_ASSERT_HASARGS_LIST	1,1,1,1,1,1,1,1,1,1,0
#define USFSTL_HASARGS(...)		_USFSTL_COUNT(, ##__VA_ARGS__, __USFSTL_ASSERT_HASARGS_LIST)

#define __usfstl_dispatch(func, numargs) \
        func ## numargs
#define _usfstl_dispatch(func, numargs) \
        __usfstl_dispatch(func, numargs)
#define usfstl_dispatch(func, ...) \
	_usfstl_dispatch(func, USFSTL_HASARGS(__VA_ARGS__))

#define _USFSTL_ASSERT_0(cstr, cond) do {				\
	if (!(cond))							\
		usfstl_abort(__FILE__, __LINE__, cstr, "");		\
} while (0)

#define _USFSTL_ASSERT_1(cstr, cond, msg, ...) do {			\
	if (!(cond))							\
		usfstl_abort(__FILE__, __LINE__, cstr,			\
			     msg, ##__VA_ARGS__);			\
} while (0)

/*
 * assert, with or without message
 *
 * USFSTL_ASSERT(cond)
 * USFSTL_ASSERT(cond, msg, format)
 */
#define USFSTL_ASSERT(cond, ...)	\
	usfstl_dispatch(_USFSTL_ASSERT_, __VA_ARGS__)(#cond, cond, ##__VA_ARGS__)

#define _USFSTL_ASSERT_CMP_0(as, a, op, bs, b, fmt) do {		\
	typeof(a) _a = a;						\
	typeof(b) _b = b;						\
	if (!((_a) op (_b)))						\
		usfstl_abort(__FILE__, __LINE__, as " " #op " " bs,	\
			     "  " as " = " fmt "\n  " bs " = " fmt "\n",\
			     _a, _b);					\
} while (0)

#define _USFSTL_ASSERT_CMP_1(as, a, op, bs, b, fmt, prfn) do {		\
	typeof(a) _a = a;						\
	typeof(b) _b = b;						\
	if (!((_a) op (_b)))						\
		usfstl_abort(__FILE__, __LINE__, as " " #op " " bs,	\
			     "  " as " = " fmt "\n  " bs " = " fmt "\n",\
			     prfn(_a), prfn(_b));			\
} while (0)

/*
 * Assert that two values are equal.
 *
 * Note that this is a special case of USFSTL_ASSERT_CMP() below, so the
 * documentation for that applies.
 */
#define USFSTL_ASSERT_EQ(a, b, fmt, ...)				\
	usfstl_dispatch(_USFSTL_ASSERT_CMP_, __VA_ARGS__)(#a, a, ==,	\
							  #b, b, fmt,	\
							  ##__VA_ARGS__)

/*
 * Assert a comparison is true.
 *
 * Given a value, comparison operator and another value it checks that
 * the comparison is true, and aborts the test (or program, if used
 * outside a test) otherwise.
 *
 * You must pass a format string suitable for printing the values.
 *
 * You may additionally pass a formatting macro that evaluates the
 * data for the format string, e.g.
 *
 * #define val_and_addr(x) (x), &(x)
 * ...
 * int x = 1, y = 2;
 * USFSTL_ASSERT_CMP(x, ==, y, "%d (at %p)", val_and_addr)
 *
 * Will result in the printout
 * [...]
 *   x = 1 (at 0xffff1110)
 *   y = 2 (at 0xffff1114)
 */
#define USFSTL_ASSERT_CMP(a, op, b, fmt, ...)				\
	usfstl_dispatch(_USFSTL_ASSERT_CMP_, __VA_ARGS__)(#a, a, op,	\
							  #b, b, fmt,	\
							  ##__VA_ARGS__)

#endif // _USFSTL_ASSERT_H_

// SPDX-License-Identifier: MIT
/*
 * Utility functions for libfsverity
 *
 * Copyright 2020 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

#include "lib_private.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void *xmalloc(size_t size)
{
	void *p = malloc(size);

	if (!p)
		libfsverity_error_msg("out of memory (tried to allocate %zu bytes)",
				      size);
	return p;
}

void *libfsverity_zalloc(size_t size)
{
	void *p = xmalloc(size);

	if (!p)
		return NULL;
	return memset(p, 0, size);
}

void *libfsverity_memdup(const void *mem, size_t size)
{
	void *p = xmalloc(size);

	if (!p)
		return NULL;
	return memcpy(p, mem, size);
}

static void (*libfsverity_error_cb)(const char *msg);

LIBEXPORT void
libfsverity_set_error_callback(void (*cb)(const char *msg))
{
	libfsverity_error_cb = cb;
}

void libfsverity_do_error_msg(const char *format, va_list va)
{
	int saved_errno = errno;
	char *msg = NULL;

	if (!libfsverity_error_cb)
		return;

	if (vasprintf(&msg, format, va) < 0)
		goto out;

	(*libfsverity_error_cb)(msg);

	free(msg);
out:
	errno = saved_errno;
}

void libfsverity_error_msg(const char *format, ...)
{
	va_list va;

	va_start(va, format);
	libfsverity_do_error_msg(format, va);
	va_end(va);
}

void libfsverity_warn_on(const char *condition, const char *file, int line)
{
	fprintf(stderr, "libfsverity internal error! %s at %s:%d\n",
		condition, file, line);
}

void libfsverity_bug_on(const char *condition, const char *file, int line)
{
	fprintf(stderr, "libfsverity internal error! %s at %s:%d\n"
		"Non-recoverable, aborting program.\n", condition, file, line);
	abort();
}

bool libfsverity_mem_is_zeroed(const void *mem, size_t size)
{
	const u8 *p = mem;
	size_t i;

	for (i = 0; i < size; i++) {
		if (p[i])
			return false;
	}
	return true;
}

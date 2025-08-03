/* SPDX-License-Identifier: MIT */
/*
 * Utility functions for programs
 *
 * Copyright 2018 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */
#ifndef PROGRAMS_UTILS_H
#define PROGRAMS_UTILS_H

#include "libfsverity.h"
#include "../common/common_defs.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void *xmalloc(size_t size);
void *xzalloc(size_t size);
void *xmemdup(const void *mem, size_t size);
char *xstrdup(const char *s);

__printf(1, 2) __cold void error_msg(const char *format, ...);
__printf(1, 2) __cold void error_msg_errno(const char *format, ...);
__printf(1, 2) __cold __noreturn void fatal_error(const char *format, ...);
__cold __noreturn void assertion_failed(const char *expr,
					const char *file, int line);

#define ASSERT(e) ({ if (!(e)) assertion_failed(#e, __FILE__, __LINE__); })

void install_libfsverity_error_handler(void);

struct filedes {
	int fd;
	char *name;		/* filename, for logging or error messages */
};

bool open_file(struct filedes *file, const char *filename, int flags, int mode);
bool get_file_size(struct filedes *file, u64 *size_ret);
bool preallocate_file(struct filedes *file, u64 size);
bool full_read(struct filedes *file, void *buf, size_t count);
bool full_write(struct filedes *file, const void *buf, size_t count);
bool full_pwrite(struct filedes *file, const void *buf, size_t count,
		 u64 offset);
bool filedes_close(struct filedes *file);
int read_callback(void *file, void *buf, size_t count);

bool hex2bin(const char *hex, u8 *bin, size_t bin_len);
void bin2hex(const u8 *bin, size_t bin_len, char *hex);

#endif /* PROGRAMS_UTILS_H */

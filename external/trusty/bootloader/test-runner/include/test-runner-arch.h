/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#pragma once

#include <stddef.h>
#include <stdint.h>

/*
 * Functions to communicate with the host emulator or debugger. Provides
 * similar functionality to matching syscalls in a user-space process.
 */

enum {
    HOST_OPEN_MODE_R = 0,    /* r */
    HOST_OPEN_MODE_RB = 1,   /* rb */
    HOST_OPEN_MODE_RW = 2,   /* r+ */
    HOST_OPEN_MODE_RWB = 3,  /* r+b */
    HOST_OPEN_MODE_W = 4,    /* w */
    HOST_OPEN_MODE_WB = 5,   /* wb */
    HOST_OPEN_MODE_WR = 6,   /* w+ */
    HOST_OPEN_MODE_WRB = 7,  /* w+b */
    HOST_OPEN_MODE_A = 8,    /* a */
    HOST_OPEN_MODE_AB = 9,   /* ab */
    HOST_OPEN_MODE_AR = 10,  /* a+ */
    HOST_OPEN_MODE_ARB = 11, /* a+b */
};

void host_exit(uint32_t code);
size_t host_get_cmdline(char* strbuf, size_t strbufsize);
int host_open(const char* path, uint32_t mode);
int host_close(int handle);
int host_read(int handle, void* data, size_t size);
int host_write(int handle, const void* data, size_t size);
int host_system(const char* cmd);

int arch_start_secondary_cpus(void);

/*
 * Called from arch to enter test-runner on each cpu.
 */
void boot(int cpu);

/*
 * Boot next operating system.
 */
void boot_next(void);

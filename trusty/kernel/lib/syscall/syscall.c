/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/* System call table definition
 * ============================
 *
 * The user of this library must:
 *
 * - define WITH_SYSCALL_TABLE
 * - provide a file named syscall_table.h in the include path
 * - provide DEF_SYSCALL macros in syscall_table.h with
 *   number, name, #args followed by argument type defintions.
 */
#include <compiler.h>
#include <debug.h>
#include <err.h>
#include <kernel/thread.h>

long sys_undefined(int num) {
    dprintf(SPEW, "%p invalid syscall %d requested\n", get_current_thread(),
            num);
    return ERR_NOT_SUPPORTED;
}

#ifdef WITH_SYSCALL_TABLE

/* Generate fake function prototypes */
#define DEF_SYSCALL(nr, fn, rtype, nr_args, ...) rtype sys_##fn(void);
#include <syscall_table.h>
#undef DEF_SYSCALL

#endif

#define DEF_SYSCALL(nr, fn, rtype, nr_args, ...) \
    [(nr)] = (unsigned long)(sys_##fn),
const unsigned long syscall_table[] = {

#ifdef WITH_SYSCALL_TABLE
#include <syscall_table.h>
#endif

};
#undef DEF_SYSCALL

unsigned long nr_syscalls = countof(syscall_table);

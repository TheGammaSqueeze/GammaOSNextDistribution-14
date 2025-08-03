/*
 * Copyright (c) 2008-2014 Travis Geiselbrecht
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
#ifndef __ARCH_H
#define __ARCH_H

#include <compiler.h>
#include <sys/types.h>

__BEGIN_CDECLS

#define ARCH_ENTER_USPACE_FLAG_32BIT    (1<<0)

void arch_early_init(void);
void arch_init(void);
void arch_quiesce(void);
void arch_chain_load(void *entry, ulong arg0, ulong arg1, ulong arg2, ulong arg3) __NO_RETURN;
void arch_enter_uspace(vaddr_t entry_point, vaddr_t user_stack_top, vaddr_t shadow_stack_base, uint32_t flags, ulong arg0) __NO_RETURN;
void arch_set_user_tls(vaddr_t tls_ptr);

__END_CDECLS

/* arch specific bits */
#include <arch/defines.h>

/* Empty function decorator if not defined */
#ifndef __ARCH_NO_PAC
#define __ARCH_NO_PAC
#endif

#endif

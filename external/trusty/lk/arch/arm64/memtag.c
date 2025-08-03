/*
 * Copyright (c) 2022 Google Inc. All rights reserved
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

#include <arch/ops.h>
#include <kernel/vm.h>

/*
 * This storeTags function is borrowed from scudo, and can be deleted once we
 *  have scudo in the kernel
 */
typedef uintptr_t uptr;
#define DCHECK_EQ(x, y)
static uptr storeTags(uptr Begin, uptr End)
{
  DCHECK_EQ(0, Begin % 16);
  uptr LineSize, Next, Tmp;
  __asm__ __volatile__(
    ".arch_extension memtag\n"

    "// Compute the cache line size in bytes (DCZID_EL0 stores it as the log2\n"
    "// of the number of 4-byte words) and bail out to the slow path if DCZID_EL0\n"
    "// indicates that the DC instructions are unavailable.\n"
    "DCZID .req %[Tmp]\n"
    "mrs DCZID, dczid_el0\n"
    "tbnz DCZID, #4, 3f\n"
    "and DCZID, DCZID, #15\n"
    "mov %[LineSize], #4\n"
    "lsl %[LineSize], %[LineSize], DCZID\n"
    ".unreq DCZID\n"

    "// Our main loop doesn't handle the case where we don't need to perform any\n"
    "// DC GZVA operations. If the size of our tagged region is less than\n"
    "// twice the cache line size, bail out to the slow path since it's not\n"
    "// guaranteed that we'll be able to do a DC GZVA.\n"
    "Size .req %[Tmp]\n"
    "sub Size, %[End], %[Cur]\n"
    "cmp Size, %[LineSize], lsl #1\n"
    "b.lt 3f\n"
    ".unreq Size\n"

    "LineMask .req %[Tmp]\n"
    "sub LineMask, %[LineSize], #1\n"

    "// STZG until the start of the next cache line.\n"
    "orr %[Next], %[Cur], LineMask\n"
  "1:\n"
    "stzg %[Cur], [%[Cur]], #16\n"
    "cmp %[Cur], %[Next]\n"
    "b.lt 1b\n"

    "// DC GZVA cache lines until we have no more full cache lines.\n"
    "bic %[Next], %[End], LineMask\n"
    ".unreq LineMask\n"
  "2:\n"
    "dc gzva, %[Cur]\n"
    "add %[Cur], %[Cur], %[LineSize]\n"
    "cmp %[Cur], %[Next]\n"
    "b.lt 2b\n"

    "// STZG until the end of the tagged region. This loop is also used to handle\n"
    "// slow path cases.\n"
  "3:\n"
    "cmp %[Cur], %[End]\n"
    "b.ge 4f\n"
    "stzg %[Cur], [%[Cur]], #16\n"
    "b 3b\n"

  "4:\n"
      : [Cur] "+&r"(Begin), [LineSize] "=&r"(LineSize), [Next] "=&r"(Next),
        [Tmp] "=&r"(Tmp)
      : [End] "r"(End)
      : "memory");
  DCHECK_EQ(0, Begin % 16);
  return Begin;
}

void arch_clear_pages_and_tags(vaddr_t addr, size_t size)
{
    DEBUG_ASSERT(IS_PAGE_ALIGNED(addr));
    DEBUG_ASSERT(IS_PAGE_ALIGNED(size));
    addr &= 0x00ffffffffffffff;
    storeTags(addr, addr + size);
}

bool arm64_mte_enabled;

bool arch_tagging_enabled(void)
{
    return arm64_mte_enabled;
}

bool arm64_tagging_supported(void)
{
    uint64_t v = ARM64_READ_SYSREG(id_aa64pfr1_el1);
    return ((v & 0xf00) >= 0x200);
}

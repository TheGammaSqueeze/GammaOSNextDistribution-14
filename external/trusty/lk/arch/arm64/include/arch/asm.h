/*
 * Copyright (c) 2008-2013 Travis Geiselbrecht
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
#pragma once

#define GNU_NOTE_FEATURE_AARCH64_BTI 0x1

#ifdef BTI_ENABLED

/* Assembly function definitions with bti call landing pads */
#define WEAK_FUNCTION(x) .weak x; LOCAL_FUNCTION(x) bti c;
#define FUNCTION(x) .global x; LOCAL_FUNCTION(x) bti c;

/* Macro to add a note section to an object file to indicate BTI can be used.
 *  Note: If not all objects contain the BTI feature, the final ELF will not
 *        be marked as supporting BTI during link.
 */
#define SECTION_GNU_NOTE_PROPERTY_AARCH64_FEATURES(features)                \
    .pushsection ".note.gnu.property","",@note;                             \
    .balign 8;                                                              \
    .word   4; /* n_namsz */                                                \
    .word   16; /* n_descsz */                                              \
    .word   5; /* n_type = NT_GNU_PROPERTY_TYPE_0 */                        \
    .asciz "GNU";  /* n_name */                                             \
    .word   0xc0000000; /* pr_type = GNU_PROPERTY_AARCH64_FEATURE_1_AND */  \
    .word   4; /* pr_datasz */                                              \
    .word   features; /* pr_data = features */                              \
    .word   0; /* pr_padding */                                             \
    .popsection

#else

#define SECTION_GNU_NOTE_PROPERTY_AARCH64_FEATURES(features)

#endif  /* BTI_ENABLED */


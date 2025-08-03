/*
 * Copyright (c) 2019 Google Inc. All rights reserved
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

/*
 * For reference, see LLVM's ubsan_value.h
 * These structures and functions are used to access LLVM encoded UBSan
 * values.
 */
#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

struct source_location {
    const char* filename;
    uint32_t line;
    uint32_t column;
};

static inline bool location_is_valid(struct source_location loc) {
    return !!loc.filename;
}

struct type_descriptor {
    uint16_t kind;
    uint16_t info;
    char name[];
};

enum type_kind {
    TYPE_KIND_INTEGER = 0x0,
    TYPE_KIND_FLOAT = 0x1,
    TYPE_KIND_UNKNOWN = 0xFFFF,
};

typedef uintptr_t value_handle_t;
//#define value_handle_t uintptr_t

struct value_t {
    const struct type_descriptor* type;
    value_handle_t val;
};

static inline bool int_typeinfo_is_signed(uint16_t info) {
    return info & 1;
}

static inline size_t int_typeinfo_width_bits(uint16_t info) {
    size_t shift = info >> 1;
    return 1U << shift;
}

static inline size_t float_typeinfo_width_bits(uint16_t info) {
    return info;
}

static inline bool type_is_integer(const struct type_descriptor* type) {
    return (enum type_kind)type->kind == TYPE_KIND_INTEGER;
}

static inline bool type_is_float(const struct type_descriptor* type) {
    return (enum type_kind)type->kind == TYPE_KIND_FLOAT;
}

static inline size_t type_width_bits(const struct type_descriptor* type) {
    if (type_is_integer(type)) {
        return int_typeinfo_width_bits(type->info);
    } else {
        return float_typeinfo_width_bits(type->info);
    }
}

static inline bool type_is_inline(const struct type_descriptor* type) {
    return type_width_bits(type) <= sizeof(value_handle_t) * 8;
}

static inline bool type_is_signed_integer(const struct type_descriptor* type) {
    return type_is_integer(type) && int_typeinfo_is_signed(type->info);
}

static inline bool type_is_unsigned_integer(
        const struct type_descriptor* type) {
    return type_is_integer(type) && !int_typeinfo_is_signed(type->info);
}

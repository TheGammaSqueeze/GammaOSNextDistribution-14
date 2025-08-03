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
 * LLVM UBSan handler signatures and data structures.
 *
 * Clang's headers for this are C++, so we cannot use them directly.
 * This ia a C-styled rewrite.
 * See LLVM's ubsan_handlers.h for reference.
 */
#pragma once

#include <stdint.h>

#include "ubsan_value.h"

const char* type_check_kinds[] = {"load of",
                                  "store to",
                                  "reference binding to",
                                  "member access within",
                                  "member call on",
                                  "constructor call on",
                                  "downcast of",
                                  "downcast of",
                                  "upcast of",
                                  "cast to virtual base of",
                                  "_Nonnull binding to",
                                  "dynamic operation on"};

struct type_mismatch_data {
    struct source_location loc;
    const struct type_descriptor* type;
    uint8_t log_alignment;
    uint8_t type_check_kind;
};

struct alignment_assumption_data {
    struct source_location loc;
    struct source_location assumption_loc;
    const struct type_descriptor* type;
};

struct overflow_data {
    struct source_location loc;
    const struct type_descriptor* type;
};

struct shift_out_of_bounds_data {
    struct source_location loc;
    const struct type_descriptor* lhs_type;
    const struct type_descriptor* rhs_type;
};

struct out_of_bounds_data {
    struct source_location loc;
    const struct type_descriptor* array_type;
    const struct type_descriptor* index_type;
};

struct unreachable_data {
    struct source_location loc;
};

struct vla_bound_data {
    struct source_location loc;
    const struct type_descriptor* type;
};

struct float_cast_overflow_data {
    struct source_location loc;
    const struct type_descriptor* from_type;
    const struct type_descriptor* to_type;
};

struct invalid_value_data {
    struct source_location loc;
    const struct type_descriptor* type;
};

enum implicit_conversion_check_kind {
    ICK_LEGACY_TRUNC = 0,
    ICK_UNSIGNED_TRUNC = 1,
    ICK_SIGNED_TRUNC = 2,
    ICK_SIGN_CHANGE = 3,
    ICK_SIGNED_TRUNC_OR_SIGN_CHANGE = 4,
};

static const char* implicit_conversion_check_kinds[] = {
        "legacy truncation",
        "unsigned truncation",
        "signed truncation",
        "sign change",
        "signed truncation or sign change",
};

struct implicit_conversion_data {
    struct source_location loc;
    const struct type_descriptor* from_type;
    const struct type_descriptor* to_type;
    uint8_t check_kind;
};

enum builtin_check_kind {
    BCK_CTZ_PASSED_ZERO = 0,
    BCK_CLZ_PASSED_ZERO = 1,
};

struct invalid_builtin_data {
    struct source_location loc;
    uint8_t check_kind;
};

struct function_type_mismatch_data {
    struct source_location loc;
    const struct type_descriptor* type;
};

struct non_null_return_data {
    struct source_location attr_loc;
};

struct non_null_arg_data {
    struct source_location loc;
    struct source_location attr_loc;
    int arg_index;
};

struct pointer_overflow_data {
    struct source_location loc;
};

enum cfi_type_check_kind {
    CFI_TCK_VCALL,
    CFI_TCK_NVCALL,
    CFI_TCK_DERIVED_CAST,
    CFI_TCK_UNRELATED_CAST,
    CFI_TCK_ICALL,
    CFI_TCK_NVMFCALL,
    CFI_TCK_VMFCALL
};

struct cfi_check_fail_data {
    uint8_t check_kind;
    struct source_location loc;
    const struct type_descriptor* type;
};

#define UBSAN_HANDLER(checkname, ...) \
    __attribute__((noinline)) void __ubsan_handle_##checkname(__VA_ARGS__)

UBSAN_HANDLER(type_mismatch,
              struct type_mismatch_data* data,
              value_handle_t val);

UBSAN_HANDLER(alignment_assumption,
              struct alignment_assumption_data* data,
              value_handle_t val,
              value_handle_t alignment,
              value_handle_t offset);

UBSAN_HANDLER(add_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs);
UBSAN_HANDLER(sub_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs);
UBSAN_HANDLER(mul_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs);
UBSAN_HANDLER(negate_overflow, struct overflow_data* data, value_handle_t val);
UBSAN_HANDLER(divrem_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs);

UBSAN_HANDLER(shift_out_of_bounds,
              struct shift_out_of_bounds_data* data,
              value_handle_t lhs,
              value_handle_t rhs);

UBSAN_HANDLER(out_of_bounds,
              struct out_of_bounds_data* data,
              value_handle_t index);

UBSAN_HANDLER(builtin_unreachable, struct unreachable_data* data);
UBSAN_HANDLER(missing_return, struct unreachable_data* data);

UBSAN_HANDLER(vla_bound_not_positive,
              struct vla_bound_data* data,
              value_handle_t bound);

UBSAN_HANDLER(float_cast_overflow,
              struct float_cast_overflow_data* data,
              value_handle_t from);

UBSAN_HANDLER(load_invalid_value,
              struct invalid_value_data* data,
              value_handle_t val);

UBSAN_HANDLER(implicit_conversion,
              struct implicit_conversion_data* data,
              value_handle_t src,
              value_handle_t dst);

UBSAN_HANDLER(invalid_builtin, struct invalid_builtin_data* data);

UBSAN_HANDLER(function_type_mismatch,
              struct function_type_mismatch_data* data,
              value_handle_t val);

UBSAN_HANDLER(nonnull_return_v1,
              struct non_null_return_data* data,
              struct source_location loc);
UBSAN_HANDLER(nullability_return_v1,
              struct non_null_return_data* data,
              struct source_location loc);

UBSAN_HANDLER(nonnull_arg, struct non_null_arg_data* data);
UBSAN_HANDLER(nullability_arg, struct non_null_arg_data* data);

UBSAN_HANDLER(pointer_overflow,
              struct pointer_overflow_data* data,
              value_handle_t base,
              value_handle_t result);

UBSAN_HANDLER(cfi_check_fail,
              struct cfi_check_fail_data* data,
              value_handle_t func,
              uintptr_t vtable_is_valid);

UBSAN_HANDLER(cfi_check_fail_abort,
              struct cfi_check_fail_data* data,
              value_handle_t func,
              uintptr_t vtable_is_valid);

UBSAN_HANDLER(vla_bound_not_positive,
              struct vla_bound_data* data,
              value_handle_t val);

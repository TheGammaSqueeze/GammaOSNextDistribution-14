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

#include "ubsan.h"

#include <assert.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <trusty/string.h>

/*
 * in_ubsan_* is used to track whether we are currently processing a UBSan
 * report. This is useful information so that if UBSan gets tripped again
 * (due to e.g. a bug in printf, or the logging code) we don't sit in an
 * infinite recursion trying to report the bug over and over.
 */
static bool in_ubsan_get(void);
static void in_ubsan_set(bool);

#ifdef TRUSTY_USERSPACE
/* TODO Once TLS is available, make this __thread */
static bool in_ubsan = false;
static inline bool in_ubsan_get(void) {
    return in_ubsan;
}
static inline void in_ubsan_set(bool val) {
    in_ubsan = val;
}
#else
/*
 * Single copy of in_ubsan for when we don't have a current thread, e.g.,
 * during early boot
 */
static bool in_ubsan_early = false;

#include <kernel/thread.h>
static inline bool in_ubsan_get(void) {
    thread_t* curr = get_current_thread();
    return curr ? thread_tls_get(curr, TLS_ENTRY_UBSAN) : in_ubsan_early;
}
static inline void in_ubsan_set(bool val) {
    thread_t* curr = get_current_thread();
    if (curr) {
        thread_tls_set(curr, TLS_ENTRY_UBSAN, val);
    } else {
        in_ubsan_early = val;
    }
}
#endif

#define VALUE_RENDER_SIZE 64
#define DETAIL_RENDER_SIZE 1024

static int64_t val_signed(const struct type_descriptor* type,
                          value_handle_t val) {
    if (type_is_inline(type)) {
        /* Sign extend if smaller than ssize_t-bits */
        size_t undefined_bits = sizeof(size_t) * 8 - type_width_bits(type);
        val <<= undefined_bits;
        ssize_t out = (ssize_t)val;
        out >>= undefined_bits;
        return out;
    } else {
        /*
         * This truncates, but we don't have a good way to deal with
         * ints larger than 64 bits and it at least gets *some* data.
         *
         * For values larger than 64 bits, this will also have the wrong
         * sign.
         */
        return *(int64_t*)val;
    }
}

static uint64_t val_unsigned(const struct type_descriptor* type,
                             value_handle_t val) {
    if (type_is_inline(type)) {
        return (uint64_t)val;
    } else {
        /* This truncates, but gets some data out */
        return *(uint64_t*)val;
    }
}

static void render_val(char* out,
                       size_t out_size,
                       const struct type_descriptor* type,
                       value_handle_t val) {
    size_t width = type_width_bits(type);
    if (type_is_signed_integer(type)) {
        if (width > sizeof(int64_t) * 8) {
            size_t warn_len = scnprintf(out, out_size,
                                        "~int%zu_t->int64_t:truncated ", width);
            out += warn_len;
            out_size -= warn_len;
        }
        scnprintf(out, out_size, "%" PRId64, val_signed(type, val));
    } else if (type_is_unsigned_integer(type)) {
        if (width > sizeof(uint64_t) * 8) {
            size_t warn_len = scnprintf(
                    out, out_size, "~uint%zu_t->uint64_t:truncated ", width);
            out += warn_len;
            out_size -= warn_len;
        }
        scnprintf(out, out_size, "%" PRIu64, val_unsigned(type, val));
    } else if (type_is_float(type)) {
        /*
         * Printing floating point correctly requires a more powerful printf
         * which may not be available, and printing large floats will pull in
         * softfloat support.
         * Since it is unlikely the exact value of a float triggering a
         * sanitizer will be important, we don't format it.
         */
        scnprintf(out, out_size, "<floating point value>");
    } else {
        scnprintf(out, out_size, "<value with unknown type>");
    }
}

static void log(struct source_location* location,
                const char* kind,
                const char* details) {
    fprintf(stderr, "UBSan: (%s) %s:%d:%d\nDetails: %s\n", kind,
            location->filename, location->line, location->column, details);
}

static void ubsan_fail(const char* msg) {
#ifdef TRUSTY_USERSPACE
    fprintf(stderr, "ubsan panic: %s\n", msg);
    *(volatile char*)0 = 0;
#else
    panic("%s\n", msg);
#endif
}

static bool start(void) {
    if (in_ubsan_get()) {
        return false;
    }
    in_ubsan_set(true);
    return true;
}

static void finish(void) {
    assert(in_ubsan_get());
    ubsan_fail("UBSan violation");
}

/*
 * UBSAN_START should be used at the beginning of each ubsan handler.
 * It will abort if we are already processing a UBSan report, and set the
 * flag if we are.
 */
#define UBSAN_START \
    if (!start()) { \
        return;     \
    }

/*
 * UBSAN_FINISH should be used at the end of each ubsan handler.
 * It will mark us as having left the handler, and terminate due to the error
 * report.
 */
#define UBSAN_FINISH finish();

static void handle_overflow(struct overflow_data* data,
                            value_handle_t lhs,
                            value_handle_t rhs,
                            const char* op) {
    UBSAN_START;
    char rendered_lhs[VALUE_RENDER_SIZE];
    char rendered_rhs[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    char overflow_kind[16];

    scnprintf(overflow_kind, sizeof(overflow_kind), "overflow:%s", op);

    const struct type_descriptor* type = data->type;

    render_val(rendered_lhs, sizeof(rendered_lhs), type, lhs);
    render_val(rendered_rhs, sizeof(rendered_rhs), type, rhs);

    scnprintf(details, sizeof(details),
              "%s integer overflow: %s %s %s cannot be represented in type"
              " %s\n",
              type_is_signed_integer(type) ? "signed" : "unsigned",
              rendered_lhs, op, rendered_rhs, type->name);

    log(&data->loc, overflow_kind, details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(add_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs) {
    handle_overflow(data, lhs, rhs, "+");
}

UBSAN_HANDLER(sub_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs) {
    handle_overflow(data, lhs, rhs, "-");
}

UBSAN_HANDLER(mul_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs) {
    handle_overflow(data, lhs, rhs, "*");
}

UBSAN_HANDLER(divrem_overflow,
              struct overflow_data* data,
              value_handle_t lhs,
              value_handle_t rhs) {
    handle_overflow(data, lhs, rhs, "/%");
}

UBSAN_HANDLER(negate_overflow, struct overflow_data* data, value_handle_t val) {
    UBSAN_START;
    char rendered_val[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_val, sizeof(rendered_val), data->type, val);
    scnprintf(details, sizeof(details),
              "negation of %s cannot be represented in type %s", rendered_val,
              data->type->name);

    log(&data->loc, "negation overflow", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(pointer_overflow,
              struct pointer_overflow_data* data,
              uintptr_t base,
              uintptr_t result) {
    UBSAN_START;
    char details[DETAIL_RENDER_SIZE];
    snprintf_filtered(details, sizeof(details),
                      "pointer arithmetic on %p overflowed resulting in %p",
                      (void*)base, (void*)result);
    log(&data->loc, "pointer_overflow", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(implicit_conversion,
              struct implicit_conversion_data* data,
              value_handle_t src,
              value_handle_t dst) {
    UBSAN_START;
    char rendered_src[VALUE_RENDER_SIZE];
    char rendered_dst[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];
    const char* kind_str;

    if (data->check_kind <
        sizeof(implicit_conversion_check_kinds) / sizeof(const char*)) {
        kind_str = implicit_conversion_check_kinds[data->check_kind];
    } else {
        kind_str = "unknown";
    }

    render_val(rendered_src, sizeof(rendered_src), data->from_type, src);
    render_val(rendered_dst, sizeof(rendered_dst), data->to_type, dst);
    scnprintf(details, sizeof(details),
              "implicit conversion (%s) from %s to %s\n", kind_str,
              rendered_src, rendered_dst);

    log(&data->loc, "implicit conversion", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(invalid_builtin, struct invalid_builtin_data* data) {
    UBSAN_START;
    const char* details;
    switch (data->check_kind) {
        case BCK_CTZ_PASSED_ZERO:
            details = "zero passed to ctz";
            break;
        case BCK_CLZ_PASSED_ZERO:
            details = "zero passed to clz";
            break;
        default:
            details = "unknown builtin misuse kind";
    }
    log(&data->loc, "invalid builtin usage", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(type_mismatch_v1,
              struct type_mismatch_data* data,
              value_handle_t ptr) {
    UBSAN_START;
    char details[DETAIL_RENDER_SIZE];

    intptr_t alignment = 1 << data->log_alignment;
    if (!ptr) {
        scnprintf(details, sizeof(details), "%s null pointer type %s",
                  type_check_kinds[data->type_check_kind], data->type->name);
    } else if (ptr & (alignment - 1)) {
        snprintf_filtered(
                details, sizeof(details),
                "%s misaligned pointer %p for type %s which requires %d byte alignment",
                type_check_kinds[data->type_check_kind], (void*)ptr,
                data->type->name, (int)alignment);
    } else {
        snprintf_filtered(
                details, sizeof(details),
                "%s pointer %p points at a region with insufficient space for a value of type %s",
                type_check_kinds[data->type_check_kind], (void*)ptr,
                data->type->name);
    }
    log(&data->loc, "type mismatch", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(builtin_unreachable, struct unreachable_data* data) {
    UBSAN_START;
    log(&data->loc, "hit a supposedly unreachable point", "");
    UBSAN_FINISH;
    ubsan_fail("executing through unreachable would be unwise");
}

UBSAN_HANDLER(missing_return, struct unreachable_data* data) {
    UBSAN_START;
    log(&data->loc, "hit a missing return statement", "");
    UBSAN_FINISH;
    ubsan_fail("executing past the end of a function would be unwise");
}

static bool is_negative(const struct type_descriptor* type,
                        value_handle_t val) {
    if (type_is_signed_integer(type)) {
        return val_signed(type, val) < 0;
    }
    return false;
}

UBSAN_HANDLER(shift_out_of_bounds,
              struct shift_out_of_bounds_data* data,
              value_handle_t lhs,
              value_handle_t rhs) {
    UBSAN_START;
    char rendered_lhs[VALUE_RENDER_SIZE];
    char rendered_rhs[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_lhs, sizeof(rendered_lhs), data->lhs_type, lhs);
    render_val(rendered_rhs, sizeof(rendered_rhs), data->rhs_type, rhs);

    uint64_t rhs_u64 = val_unsigned(data->rhs_type, rhs);

    if (is_negative(data->rhs_type, rhs)) {
        scnprintf(details, sizeof(details), "shift amount is negative: %s",
                  rendered_rhs);
    } else if (type_width_bits(data->lhs_type) < rhs_u64) {
        scnprintf(details, sizeof(details),
                  "shift amount %s is too large for type %s", rendered_rhs,
                  data->lhs_type->name);
    } else if (is_negative(data->lhs_type, lhs)) {
        /* At this point, we know we are dealing with a left shift, as right
         * shift is covered by the above two cases */
        scnprintf(details, sizeof(details),
                  "left shifting a negative value: %s", rendered_lhs);
    } else {
        scnprintf(details, sizeof(details), "%s << %s does not fit in %s",
                  rendered_lhs, rendered_rhs, data->lhs_type->name);
    }

    log(&data->loc, "shift out of bounds", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(out_of_bounds,
              struct out_of_bounds_data* data,
              value_handle_t index) {
    UBSAN_START;
    char rendered_index[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_index, sizeof(rendered_index), data->index_type, index);
    scnprintf(details, sizeof(details), "index %s out of bounds for %s\n",
              rendered_index, data->array_type->name);

    log(&data->loc, "out of bounds access", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(load_invalid_value,
              struct invalid_value_data* data,
              value_handle_t val) {
    UBSAN_START;
    char rendered_val[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_val, sizeof(rendered_val), data->type, val);
    scnprintf(details, sizeof(details),
              "load of value %s outside of range for type %s", rendered_val,
              data->type->name);

    log(&data->loc, "invalid value", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(float_cast_overflow,
              struct float_cast_overflow_data* data,
              value_handle_t val) {
    UBSAN_START;
    /* Since we aren't rendering floats, there's not much point in details */
    log(&data->loc, "float cast overflow", "");
    UBSAN_FINISH;
}

UBSAN_HANDLER(cfi_check_fail_abort,
              struct cfi_check_fail_data* data,
              value_handle_t val,
              uintptr_t vtable_is_valid) {
    UBSAN_START;
    char rendered_val[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_val, sizeof(rendered_val), data->type, val);
    scnprintf(details, sizeof(details), "type of the value: %s  type name: %s",
              rendered_val, data->type->name);
    log(&data->loc, "cfi check fail abort", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(vla_bound_not_positive,
              struct vla_bound_data* data,
              value_handle_t val) {
    UBSAN_START;
    char rendered_val[VALUE_RENDER_SIZE];
    char details[DETAIL_RENDER_SIZE];

    render_val(rendered_val, sizeof(rendered_val), data->type, val);
    scnprintf(details, sizeof(details), "bound %s is not positive for type %s",
              rendered_val, data->type->name);
    log(&data->loc,
        "variable length array bound evaluates to non-positive value", details);
    UBSAN_FINISH;
}

UBSAN_HANDLER(alignment_assumption,
              struct alignment_assumption_data* data,
              value_handle_t pointer,
              value_handle_t alignment,
              value_handle_t offset) {
    UBSAN_START;
    char details[DETAIL_RENDER_SIZE];

    if (location_is_valid(data->assumption_loc)) {
        log(&data->assumption_loc, "alignment assumption was specified here",
            /* Details: */ "below");
    }

    if (!offset) {
        scnprintf(details, sizeof(details),
                  "assumption of %" PRIuPTR
                  " byte alignment for pointer of type %s failed",
                  alignment, data->type->name);
    } else {
        scnprintf(details, sizeof(details),
                  "assumption of %" PRIuPTR
                  " byte alignment (with offset of %" PRIuPTR
                  " bytes) for pointer of type %s failed",
                  alignment, offset, data->type->name);
    }

    log(&data->loc, "alignment assumption is incorrect", details);
    UBSAN_FINISH;
}

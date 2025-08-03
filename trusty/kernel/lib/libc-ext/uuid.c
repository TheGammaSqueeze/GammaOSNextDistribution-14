/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <inttypes.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <trusty/uuid.h>

void uuid_to_str(const struct uuid* uuid, char* str) {
    sprintf(str,
            "%08" PRIx32 "-%04" PRIx16 "-%04" PRIx16 "-%02" PRIx8 "%02" PRIx8
            "-%02" PRIx8 "%02" PRIx8 "%02" PRIx8 "%02" PRIx8 "%02" PRIx8
            "%02" PRIx8,
            uuid->time_low, uuid->time_mid, uuid->time_hi_and_version,
            uuid->clock_seq_and_node[0], uuid->clock_seq_and_node[1],
            uuid->clock_seq_and_node[2], uuid->clock_seq_and_node[3],
            uuid->clock_seq_and_node[4], uuid->clock_seq_and_node[5],
            uuid->clock_seq_and_node[6], uuid->clock_seq_and_node[7]);
}

static bool parse_dash(const char** str) {
    if (**str != '-') {
        return false;
    }

    *str += 1;
    return true;
}

static bool parse_hex_digit(const char** str, uint8_t* dst) {
    char c;

    c = **str;
    *str += 1;

    if (c >= '0' && c <= '9') {
        *dst = c - '0';
        return true;
    }

    if (c >= 'a' && c <= 'f') {
        *dst = c - 'a' + 10;
        return true;
    }

    return false;
}

static bool parse_u8(const char** str, uint8_t* dst) {
    uint8_t msn;
    uint8_t lsn;

    if (!parse_hex_digit(str, &msn)) {
        return false;
    }

    if (!parse_hex_digit(str, &lsn)) {
        return false;
    }

    *dst = (msn << 4) + lsn;
    return true;
}

static bool parse_u16(const char** str, uint16_t* dst) {
    uint8_t msb;
    uint8_t lsb;

    if (!parse_u8(str, &msb)) {
        return false;
    }

    if (!parse_u8(str, &lsb)) {
        return false;
    }

    *dst = ((uint16_t)msb << 8) + lsb;
    return true;
}

static bool parse_u32(const char** str, uint32_t* dst) {
    uint16_t msh;
    uint16_t lsh;

    if (!parse_u16(str, &msh)) {
        return false;
    }

    if (!parse_u16(str, &lsh)) {
        return false;
    }

    *dst = ((uint32_t)msh << 16) + lsh;
    return true;
}

int str_to_uuid(const char* str, struct uuid* uuid) {
    int len;

    len = strnlen(str, UUID_STR_SIZE);
    if (len == UUID_STR_SIZE) {
        return -1;
    }

    if (!parse_u32(&str, &uuid->time_low)) {
        return -1;
    }

    if (!parse_dash(&str)) {
        return -1;
    }

    if (!parse_u16(&str, &uuid->time_mid)) {
        return -1;
    }

    if (!parse_dash(&str)) {
        return -1;
    }

    if (!parse_u16(&str, &uuid->time_hi_and_version)) {
        return -1;
    }

    if (!parse_dash(&str)) {
        return -1;
    }

    if (!parse_u8(&str, uuid->clock_seq_and_node)) {
        return -1;
    }

    if (!parse_u8(&str, uuid->clock_seq_and_node + 1)) {
        return -1;
    }

    if (!parse_dash(&str)) {
        return -1;
    }

    for (int i = 2; i < 8; i++) {
        if (!parse_u8(&str, uuid->clock_seq_and_node + i)) {
            return -1;
        }
    }

    return 0;
}

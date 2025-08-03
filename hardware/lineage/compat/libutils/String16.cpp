/*
 * Copyright (C) 2023 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

#include <stdio.h>

extern "C" {
char16_t* strncpy16(char16_t* dst, const char16_t* src, size_t n) {
    char16_t* q = dst;
    const char16_t* p = src;
    char ch;
    while (n) {
        n--;
        *q++ = ch = *p++;
        if (!ch) break;
    }
    *q = 0;
    return dst;
}
}

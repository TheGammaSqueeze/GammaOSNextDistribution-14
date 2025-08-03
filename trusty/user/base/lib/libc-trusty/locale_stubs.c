/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "locale_impl.h"

#include <string.h>

locale_t newlocale(int mask, const char* name, locale_t loc) {
    return C_LOCALE;
}

locale_t duplocale(locale_t locobj) {
    return C_LOCALE;
}

locale_t uselocale(locale_t newloc) {
    return C_LOCALE;
}

char* setlocale(int cat, const char* name) {
    if (strcmp("", name) == 0 || strcmp("C", name) == 0 ||
        strcmp("POSIX", name) == 0) {
        return "";
    } else {
        return NULL;
    }
}

void freelocale(locale_t l) {}

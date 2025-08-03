/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include <iostream>

#define PRINT_SET(x) std::cout << #x << " set" << std::endl;
#define PRINT_UNSET(x) std::cout << #x << " unset" << std::endl;

int main() {
    #ifdef VALUE1
    PRINT_SET(VALUE1)
    #else
    PRINT_UNSET(VALUE1)
    #endif

    #ifdef VALUE1_PLUS_ANDROID
    PRINT_SET(VALUE1_PLUS_ANDROID)
    #else
    PRINT_UNSET(VALUE1_PLUS_ANDROID)
    #endif

    #ifdef VALUE1_PLUS_HOST
    PRINT_SET(VALUE1_PLUS_HOST)
    #else
    PRINT_UNSET(VALUE1_PLUS_HOST)
    #endif

    #ifdef VALUE2
    PRINT_SET(VALUE2)
    #else
    PRINT_UNSET(VALUE2)
    #endif

    #ifdef VALUE2_PLUS_ANDROID
    PRINT_SET(VALUE2_PLUS_ANDROID)
    #else
    PRINT_UNSET(VALUE2_PLUS_ANDROID)
    #endif

    #ifdef VALUE2_PLUS_HOST
    PRINT_SET(VALUE2_PLUS_HOST)
    #else
    PRINT_UNSET(VALUE2_PLUS_HOST)
    #endif

    #ifdef DEFAULT
    PRINT_SET(DEFAULT)
    #else
    PRINT_UNSET(DEFAULT)
    #endif

    #ifdef DEFAULT_PLUS_ANDROID
    PRINT_SET(DEFAULT_PLUS_ANDROID)
    #else
    PRINT_UNSET(DEFAULT_PLUS_ANDROID)
    #endif

    #ifdef DEFAULT_PLUS_HOST
    PRINT_SET(DEFAULT_PLUS_HOST)
    #else
    PRINT_UNSET(DEFAULT_PLUS_HOST)
    #endif

    #ifdef BOOL_VAR
    PRINT_SET(BOOL_VAR)
    #else
    PRINT_UNSET(BOOL_VAR)
    #endif

    #ifdef BOOL_VAR_PLUS_ANDROID
    PRINT_SET(BOOL_VAR_PLUS_ANDROID)
    #else
    PRINT_UNSET(BOOL_VAR_PLUS_ANDROID)
    #endif

    #ifdef BOOL_VAR_PLUS_HOST
    PRINT_SET(BOOL_VAR_PLUS_HOST)
    #else
    PRINT_UNSET(BOOL_VAR_PLUS_HOST)
    #endif

    #ifdef BOOL_VAR_DEFAULT
    PRINT_SET(BOOL_VAR_DEFAULT)
    #else
    PRINT_UNSET(BOOL_VAR_DEFAULT)
    #endif

    #ifdef BOOL_VAR_DEFAULT_PLUS_ANDROID
    PRINT_SET(BOOL_VAR_DEFAULT_PLUS_ANDROID)
    #else
    PRINT_UNSET(BOOL_VAR_DEFAULT_PLUS_ANDROID)
    #endif

    #ifdef BOOL_VAR_DEFAULT_PLUS_HOST
    PRINT_SET(BOOL_VAR_DEFAULT_PLUS_HOST)
    #else
    PRINT_UNSET(BOOL_VAR_DEFAULT_PLUS_HOST)
    #endif

    return 0;
}

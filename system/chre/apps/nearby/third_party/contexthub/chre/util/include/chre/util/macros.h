/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef CHRE_UTIL_MACROS_H_
#define CHRE_UTIL_MACROS_H_

#ifndef UNUSED_VAR
#define UNUSED_VAR(var) ((void)(var))
#endif

/**
 * Obtains the number of elements in a C-style array.
 */
#ifndef ARRAY_SIZE
#define ARRAY_SIZE(array) (sizeof(array) / sizeof((array)[0]))
#endif

#ifndef ARRAY_END
#define ARRAY_END(array) (array + ARRAY_SIZE(array))
#endif

/** Determines if the provided bit is set in the provided value. */
#ifndef IS_BIT_SET
#define IS_BIT_SET(value, bit) (((value) & (bit)) == (bit))
#endif

/**
 * Performs macro expansion then converts the value into a string literal
 */
#ifndef STRINGIFY
#define STRINGIFY(x) STRINGIFY2(x)
#define STRINGIFY2(x) #x
#endif

/**
 * Checks if a bitmask contains the specified value
 */
#ifndef BITMASK_HAS_VALUE
#define BITMASK_HAS_VALUE(mask, value) ((mask & value) == value)
#endif

/**
 * Min/max macros.
 */
#ifndef MIN
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#endif

#ifndef MAX
#define MAX(a, b) ((a) > (b) ? (a) : (b))
#endif

// Compiler-specific functionality
#if defined(__clang__) || defined(__GNUC__)

//! Exports a symbol so it is accessible outside the .so (symbols are hidden by
//! default)
#define DLL_EXPORT __attribute__((visibility("default")))

//! Marks a symbol as weak, so that it may be overridden at link time
#define WEAK_SYMBOL __attribute__((weak))

#else

#warning "Missing compiler-specific macros"

#endif

#endif  // CHRE_UTIL_MACROS_H_

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

/**
 * Obtain the number of arguments passed into a macro up till 20 args
 */
#define VA_NUM_ARGS(...)                                                       \
  VA_NUM_ARGS_IMPL(__VA_ARGS__, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, \
                   8, 7, 6, 5, 4, 3, 2, 1)
#define VA_NUM_ARGS_IMPL(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, \
                         _13, _14_, _15, _16, _17, _18, _19, _20, N, ...)   \
  N

/**
 * Concats two preprocessor tokens together.
 * If passed in args are macros, they are resolved first, then concat'd
 */
#define MACRO_CONCAT2(x, y) x##y
#define MACRO_CONCAT(x, y) MACRO_CONCAT2(x, y)

/**
 * Get a list of types of the passed in parameters to TYPE_LIST(...)
 */
#define TYPE_LIST_1(a) decltype(a)
#define TYPE_LIST_2(a, ...) decltype(a), TYPE_LIST_1(__VA_ARGS__)
#define TYPE_LIST_3(a, ...) decltype(a), TYPE_LIST_2(__VA_ARGS__)
#define TYPE_LIST_4(a, ...) decltype(a), TYPE_LIST_3(__VA_ARGS__)
#define TYPE_LIST_5(a, ...) decltype(a), TYPE_LIST_4(__VA_ARGS__)
#define TYPE_LIST_6(a, ...) decltype(a), TYPE_LIST_5(__VA_ARGS__)
#define TYPE_LIST_7(a, ...) decltype(a), TYPE_LIST_6(__VA_ARGS__)
#define TYPE_LIST_8(a, ...) decltype(a), TYPE_LIST_7(__VA_ARGS__)
#define TYPE_LIST_9(a, ...) decltype(a), TYPE_LIST_8(__VA_ARGS__)
#define TYPE_LIST_10(a, ...) decltype(a), TYPE_LIST_9(__VA_ARGS__)
#define TYPE_LIST_11(a, ...) decltype(a), TYPE_LIST_10(__VA_ARGS__)
#define TYPE_LIST_12(a, ...) decltype(a), TYPE_LIST_11(__VA_ARGS__)
#define TYPE_LIST_13(a, ...) decltype(a), TYPE_LIST_12(__VA_ARGS__)
#define TYPE_LIST_14(a, ...) decltype(a), TYPE_LIST_13(__VA_ARGS__)
#define TYPE_LIST_15(a, ...) decltype(a), TYPE_LIST_14(__VA_ARGS__)
#define TYPE_LIST_16(a, ...) decltype(a), TYPE_LIST_15(__VA_ARGS__)
#define TYPE_LIST_17(a, ...) decltype(a), TYPE_LIST_16(__VA_ARGS__)
#define TYPE_LIST_18(a, ...) decltype(a), TYPE_LIST_17(__VA_ARGS__)
#define TYPE_LIST_19(a, ...) decltype(a), TYPE_LIST_18(__VA_ARGS__)
#define TYPE_LIST_20(a, ...) decltype(a), TYPE_LIST_19(__VA_ARGS__)

#define TYPE_LIST(...) \
  MACRO_CONCAT(TYPE_LIST_, VA_NUM_ARGS(__VA_ARGS__))(__VA_ARGS__)

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

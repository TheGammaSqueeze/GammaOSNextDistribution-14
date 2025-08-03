/*****************************************************************************
 * Copyright ©2017-2019 Gemalto – a Thales Company. All rights Reserved.
 *
 * This copy is licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0 or https://www.apache.org/licenses/LICENSE-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.

 ****************************************************************************/

/**
 * @file
 * $Author$
 * $Revision$
 * $Date$
 *
 * Compiler specific definition. Currently target gcc and clang.
 *
 * We need offsetof() and containerof().
 *
 */

#ifndef COMPILER_H
#define COMPILER_H

#include <stdint.h>
# include <stddef.h>

#define container_of(ptr, type, member)                  \
    ({ const typeof(((type *)0)->member) * __mp = (ptr); \
       (type *)((char *)__mp - offsetof(type, member)); })

#define __COMPILE_ASSERT(cond) ((void)sizeof(char[1 - 2 * !(cond)]))
#define _COMPILE_ASSERT(cond) __COMPILE_ASSERT(cond)
#define COMPILE_ASSERT(cond) _COMPILE_ASSERT(!!(cond))

#ifdef __GNUC__
# define check_printf(f, v) __attribute__((format(printf, f, v)))
#else
# define check_printf(f, v)
#endif

#endif /* ifndef COMPILER_H */

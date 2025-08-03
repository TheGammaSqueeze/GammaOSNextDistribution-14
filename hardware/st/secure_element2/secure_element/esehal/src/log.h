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
 * Interface to log facilities.
 *
 */

#ifndef LOG_H
#define LOG_H

#ifdef ENABLE_LOGGING

# include "compiler.h"
# include <stdarg.h>

# define LOG_ESC "\001"
# define LOG_ERR     LOG_ESC "0"
# define LOG_WARN    LOG_ESC "1"
# define LOG_NOTICE  LOG_ESC "2"
# define LOG_INFO    LOG_ESC "3"
# define LOG_DEBUG   LOG_ESC "4"

# define err(fmt, ...)    say(ctx, LOG_ERR fmt, ## __VA_ARGS__)
# define warn(fmt, ...)   say(ctx, LOG_WARN fmt, ## __VA_ARGS__)
# define notice(fmt, ...) say(ctx, LOG_NOTICE fmt, ## __VA_ARGS__)
# define info(fmt, ...)   say(ctx, LOG_INFO fmt, ## __VA_ARGS__)
# ifdef ENABLE_DEBUG
#  define dbg(fmt, ...)   say(ctx, LOG_DEBUG fmt, ## __VA_ARGS__)
# else
#  define dbg(fmt, ...)   nosay(ctx, LOG_DEBUG fmt, ## __VA_ARGS__)
# endif

void vsay(struct se_gto_ctx *ctx, const char *fmt, va_list args);

static inline void
check_printf(2, 3)
say(struct se_gto_ctx *ctx, const char *fmt, ...) {
    va_list args;

    va_start(args, fmt);
    vsay(ctx, fmt, args);
    va_end(args);
}

#else /* ifdef ENABLE_LOGGING */

# define err(fmt, ...)    nosay(ctx, fmt, ## __VA_ARGS__)
# define warn(fmt, ...)   nosay(ctx, fmt, ## __VA_ARGS__)
# define notice(fmt, ...) nosay(ctx, fmt, ## __VA_ARGS__)
# define info(fmt, ...)   nosay(ctx, fmt, ## __VA_ARGS__)
# define dbg(fmt, ...)    nosay(ctx, fmt, ## __VA_ARGS__)

#endif /* ifdef ENABLE_LOGGING */

#if 0 /* when disabling logging compilation fail on nosay() definition */
static inline void check_printf(2, 3)
    nosay(struct se_gto_ctx *ctx, const char *fmt, ...) {}
#else
static inline void
nosay(struct se_gto_ctx *ctx, const char *fmt, ...) {}
#endif

void log_teardown(struct se_gto_ctx *ctx);

#endif /* ifndef LOG_H */

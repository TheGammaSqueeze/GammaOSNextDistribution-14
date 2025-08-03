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
 * Logging facilities.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <string.h>

#include "libse-gto-private.h"

#ifdef ENABLE_LOGGING

# define BUFSIZE 1024

static const char *levels[] = {
    "ERROR:", "WARNING:", "NOTICE:", "INFO:", "DEBUG:", "????:"
};

void
vsay(struct se_gto_ctx *ctx, const char *fmt, va_list args)
{
    static int k = 0;
    char      *buf;

    if (!ctx->log_fn)
        return;

    if (!ctx->log_buf) {
        ctx->log_buf = malloc(BUFSIZE);
        if (!ctx->log_buf)
            return;
    }

    if (fmt[0] == 1) {
        int n = fmt[1] - '0';
        n = (n < 0 || n > 4) ? 5 : n;
        if (n <= ctx->log_level) {
            if (k == 0)
                strcpy(ctx->log_buf, levels[n]);
            k += strlen(ctx->log_buf + k);
            k += vsnprintf(ctx->log_buf + k, BUFSIZE - k, fmt + 2, args);
        }
    } else
        k += vsnprintf(ctx->log_buf + k, 1024 - k, fmt, args);

    if (k >= BUFSIZE)
        k = BUFSIZE - 1;
    buf    = ctx->log_buf;
    if (k >= 0) {
        buf[k] = '\000';
        if ((k > 0 && (buf[k - 1] == '\n')) || (k == (BUFSIZE - 1))) {
            k = 0;
            ctx->log_fn(ctx, buf);
        }
    }
}

#endif /* ifdef ENABLE_LOGGING */

void
log_teardown(struct se_gto_ctx *ctx)
{
    if (ctx->log_buf) {
        free(ctx->log_buf);
        ctx->log_buf = NULL;
    }
}

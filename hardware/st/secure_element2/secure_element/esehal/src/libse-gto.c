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
 * libse-gto main functions.
 *
 */

#include <ctype.h>
#include <cutils/properties.h>
#include <errno.h>
#include <fcntl.h>
#include <log/log.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include "libse-gto-private.h"
#include "se-gto/libse-gto.h"
#include "spi.h"

#define SE_GTO_GTODEV "/dev/gto"

SE_GTO_EXPORT void *
se_gto_get_userdata(struct se_gto_ctx *ctx)
{
    if (ctx == NULL)
        return NULL;

    return ctx->userdata;
}

SE_GTO_EXPORT void
se_gto_set_userdata(struct se_gto_ctx *ctx, void *userdata)
{
    if (ctx == NULL)
        return;

    ctx->userdata = userdata;
}

static int
log_level(const char *priority)
{
    char *endptr;
    int   prio;

    prio = strtol(priority, &endptr, 10);
    if ((endptr[0] == '\0') || isspace(endptr[0]))
        return prio;

    if (strncmp(priority, "err", 3) == 0)
        return 0;

    if (strncmp(priority, "info", 4) == 0)
        return 3;

    if (strncmp(priority, "debug", 5) == 0)
        return 4;

    return 0;
}

static void
log_stderr(struct se_gto_ctx *ctx, const char *s)
{
    //fputs(s, stderr);
    ALOGD("%s",s);
}

SE_GTO_EXPORT int
se_gto_new(struct se_gto_ctx **c)
{
    const char        *env;
    struct se_gto_ctx *ctx;

    ctx = calloc(1, sizeof(struct se_gto_ctx));
    if (!ctx) {
        errno = ENOMEM;
        return -1;
    }

    isot1_init(&ctx->t1);

    ctx->log_fn = log_stderr;

    ctx->gtodev = SE_GTO_GTODEV;

    ctx->log_level = 2;
    /* environment overwrites config */
    env = getenv("SE_GTO_LOG");
    if (env != NULL)
        se_gto_set_log_level(ctx, log_level(env));

    dbg("ctx %p created\n", ctx);
    dbg("log_level=%d\n", ctx->log_level);
    *c = ctx;
    return 0;
}

SE_GTO_EXPORT int
se_gto_get_log_level(struct se_gto_ctx *ctx)
{
    return ctx->log_level;
}

SE_GTO_EXPORT void
se_gto_set_log_level(struct se_gto_ctx *ctx, int level)
{
    if (level < 0)
        level = 0;
    else if (level > 4)
        level = 4;
    ctx->log_level = level;
}

SE_GTO_EXPORT se_gto_log_fn *
se_gto_get_log_fn(struct se_gto_ctx *ctx)
{
    return ctx->log_fn;
}

SE_GTO_EXPORT void
se_gto_set_log_fn(struct se_gto_ctx *ctx, se_gto_log_fn *fn)
{
    ctx->log_fn = fn;
}

SE_GTO_EXPORT const char *
se_gto_get_gtodev(struct se_gto_ctx *ctx)
{
    return ctx->gtodev;
}

SE_GTO_EXPORT void
se_gto_set_gtodev(struct se_gto_ctx *ctx, const char *gtodev)
{
    ctx->gtodev = strdup(gtodev);
}

SE_GTO_EXPORT int
se_gto_reset(struct se_gto_ctx *ctx, void *atr, size_t r)
{
    int err;

    err = isot1_reset(&ctx->t1);
    if (err < 0) {
        errno = -err;
        ctx->check_alive = 1;
    }
    else {
        err = isot1_get_atr(&ctx->t1, atr, r);
        if (err < 0)
            errno = -err;
    }
    return err;
}

SE_GTO_EXPORT int
se_gto_apdu_transmit(struct se_gto_ctx *ctx, const void *apdu, int n, void *resp, int r)
{
    if (!apdu || (n < 4) || !resp || (r < 2)) {
        errno = EINVAL;
        return -1;
    }
    r = isot1_transceive(&ctx->t1, apdu, n, resp, r);
    dbg("isot1_transceive: r=%d\n", r);
    dbg("isot1_transceive: ctx->t1.recv.end - ctx->t1.recv.start = %ld\n", ctx->t1.recv.end - ctx->t1.recv.start);
    dbg("isot1_transceive: ctx->t1.recv.size = %zu\n", ctx->t1.recv.size);
    dbg("isot1_transceive: ctx->t1.buf[2] = %02X\n", ctx->t1.buf[2]);
    if (r < 0) {
        errno = -r;
        err("failed to read APDU response, %s\n", strerror(-r));
    } else if (r < 2) {
        err("APDU response too short, only %d bytes, needs 2 at least\n", r);
    }
    if (r < 2){
        ctx->check_alive = 1;
        return -1;
    } else
        return r;
}

SE_GTO_EXPORT int
se_gto_open(struct se_gto_ctx *ctx)
{
    info("eSE GTO: using %s\n", ctx->gtodev);

    if (spi_setup(ctx) < 0) {
        err("failed to set up se-gto.\n");
        return -1;
    }

    ctx->check_alive = 0;

    isot1_bind(&ctx->t1, 0x2, 0x1);

    dbg("fd: spi=%d\n", ctx->t1.spi_fd);
    return 0;
}

#define SPI_IOC_MAGIC    'k'
#define ST54SPI_IOC_WR_POWER _IOW(SPI_IOC_MAGIC, 99, __u32)

int se_gto_Spi_Reset(struct se_gto_ctx *ctx)
{
    uint32_t io_code;
    uint32_t power = 0;

    printf("Send software reset via ioctl\n");
    io_code = ST54SPI_IOC_WR_POWER;
    power = 1;
    if (-1 == ioctl (ctx->t1.spi_fd, io_code, &power)) {
        perror("unable to soft reset via ioctl\n");
        return -1;
    }

    isot1_resync(&ctx->t1);
    return 0;
}

int gtoSPI_checkAlive(struct se_gto_ctx *ctx);
int gtoSPI_checkAlive(struct se_gto_ctx *ctx)
{
  int ret = 0;
  unsigned char apdu[5]= {0x80,0xCA,0x9F,0x7F,0x2D};
  unsigned char resp[258] = {0,};

  /*Check Alive implem*/
  for(int count = 0; count < 3; count++) {
      ret = se_gto_apdu_transmit(ctx, apdu, 5, resp, sizeof(resp));
      if(ret < 0){
        if (count == 2) return -1;
        /*Run SPI reset*/
        se_gto_Spi_Reset(ctx);
      }
  }

  return 0;
}

SE_GTO_EXPORT int
se_gto_close(struct se_gto_ctx *ctx)
{
    int status = 0;
    const char ese_reset_property[] = "persist.vendor.se.reset";

    if (ctx){
        dbg("se_gto_close check_alive = %d\n", ctx->check_alive);
    }
    if (ctx->check_alive == 1) {
        if (gtoSPI_checkAlive(ctx) != 0) {
            status = -(0xDEAD);
            // eSE needs cold reset.
            if (strncmp(ctx->gtodev, "/dev/st54spi", 12) == 0 ) {
                property_set(ese_reset_property, "needed");
            }
        } else {
            // Set noneed if SPI worked normally.
            if (strncmp(ctx->gtodev, "/dev/st54spi", 12) == 0 ) {
                property_set(ese_reset_property, "noneed");
            }
        }
    } else {
        // Set noneed if SPI worked normally.
        if (strncmp(ctx->gtodev, "/dev/st54spi", 12) == 0 ) {
            property_set(ese_reset_property, "noneed");
        }
    }

    (void)isot1_release(&ctx->t1);
    (void)spi_teardown(ctx);
    log_teardown(ctx);
    if(ctx) free(ctx);
    return status;
}

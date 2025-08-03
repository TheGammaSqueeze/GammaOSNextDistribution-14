/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <assert.h>
#include <interface/spi/spi.h>
#include <lib/spi/common/utils.h>
#include <lk/macros.h>
#include <uapi/err.h>

int translate_srv_err(uint32_t srv_err) {
    switch (srv_err) {
    case SPI_SRV_NO_ERROR:
        return NO_ERROR;
    case SPI_SRV_ERR_BUSY:
        return ERR_BUSY;
    case SPI_SRV_ERR_INVALID_ARGS:
        return ERR_INVALID_ARGS;
    case SPI_SRV_ERR_NOT_SUPPORTED:
        return ERR_NOT_SUPPORTED;
    case SPI_SRV_ERR_NOT_IMPLEMENTED:
        return ERR_NOT_IMPLEMENTED;
    case SPI_SRV_ERR_TOO_BIG:
        return ERR_TOO_BIG;
    default:
        return ERR_GENERIC;
    }
}

uint32_t translate_lk_err(int rc) {
    switch (rc) {
    case NO_ERROR:
        return SPI_SRV_NO_ERROR;
    case ERR_BUSY:
        return SPI_SRV_ERR_BUSY;
    case ERR_INVALID_ARGS:
        return SPI_SRV_ERR_INVALID_ARGS;
    case ERR_NOT_SUPPORTED:
        return SPI_SRV_ERR_NOT_SUPPORTED;
    case ERR_NOT_IMPLEMENTED:
        return SPI_SRV_ERR_NOT_IMPLEMENTED;
    case ERR_TOO_BIG:
        return SPI_SRV_ERR_TOO_BIG;
    default:
        return SPI_SRV_ERR_GENERIC;
    }
}

void mb_init(struct mem_buf* mb, void* buf, size_t sz, size_t align) {
    assert(align && IS_ALIGNED(buf, align));

    mb->buf = buf;
    mb->capacity = sz;
    mb->align = align;
    mb->curr_size = 0;
    mb->pos = 0;
}

void mb_destroy(struct mem_buf* mb) {
    mb->buf = NULL;
    mb->capacity = 0;
    mb->align = 0;
    mb->curr_size = 0;
    mb->pos = 0;
}

static inline bool mb_is_destroyed(struct mem_buf* mb) {
    return !mb->buf;
}

bool mb_resize(struct mem_buf* mb, size_t sz) {
    if (mb_is_destroyed(mb)) {
        return false;
    }
    if (sz <= mb->capacity) {
        mb->curr_size = sz;
        mb->pos = 0;
        return true;
    }
    return false;
}

void* mb_advance_pos(struct mem_buf* mb, size_t sz) {
    void* p;

    if (mb_is_destroyed(mb)) {
        return NULL;
    }

    assert(IS_ALIGNED(mb->pos, mb->align));
    assert(mb->curr_size >= mb->pos);

    sz = round_up(sz, mb->align);

    if (sz > (mb->curr_size - mb->pos)) {
        return NULL;
    }

    p = (void*)(mb->buf + mb->pos);
    mb->pos += sz;
    assert(IS_ALIGNED(mb->pos, mb->align));
    return p;
}

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

#pragma once

#include <lk/compiler.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

__BEGIN_CDECLS

/**
 * translate_srv_err() - translate SPI error code to LK error code
 * @srv_err: SPI error code - one of &enum spi_srv_err
 *
 * Returns: one of LK error codes
 */
int translate_srv_err(uint32_t srv_err);

/**
 * translate_lk_err() - translate LK error code to SPI error code
 * @rc: LK error code
 *
 * Returns: one of &enum spi_srv_err
 */
uint32_t translate_lk_err(int rc);

/**
 * mem_buf - tracks state of a memory buffer
 * @buf:       pointer to the buffer
 * @capacity:  capacity of @buf
 * @align:     alignment requirement for @buf and @pos
 * @curr_size: current size of @buf, must not exceed @capacity
 * @pos:       offset to the current position in @buf
 */
struct mem_buf {
    uint8_t* buf;
    size_t capacity;
    size_t align;
    size_t curr_size;
    size_t pos;
};

/**
 * mb_curr_pos() - get current position of memory buffer
 * @mb: pointer to memory buffer - see &struct mem_buf
 *
 * Return: current position of @mb
 */
static inline size_t mb_curr_pos(struct mem_buf* mb) {
    return mb->pos;
}

/**
 * mb_size() - get current size of memory buffer
 * @mb: pointer to memory buffer - see &struct mem_buf
 *
 * return: size of @mb
 */
static inline size_t mb_size(struct mem_buf* mb) {
    return mb->curr_size;
}

/**
 * mb_space_left() - get number of bytes available in memory buffer
 * @mb: pointer to memory buffer - see &struct mem_buf
 *
 * return: amount of space available in @mb
 */
static inline size_t mb_space_left(struct mem_buf* mb) {
    return mb_size(mb) - mb_curr_pos(mb);
}

/**
 * mb_rewind_pos() - rewind position of memory buffer back to zero
 * @mb: pointer to memory buffer - see &struct mem_buf
 */
static inline void mb_rewind_pos(struct mem_buf* mb) {
    mb->pos = 0;
}

/**
 * mb_init() - initialize memory buffer
 * @mb:    pointer to memory buffer - see &struct mem_buf
 * @buf:   pointer to the buffer
 * @cap:   capacity of @buf
 * @align: alignment requirement
 *
 * Set initial @curr_size to 0. @buf must be aligned by @align.
 */
void mb_init(struct mem_buf* mb, void* buf, size_t cap, size_t align);

/**
 * mb_destroy() - destroy memory buffer
 * @mb: pointer to memory buffer - see &struct mem_buf
 *
 * After calling this routine, mb_init() is the only valid operation on @mb.
 * Other operations will return an error.
 */
void mb_destroy(struct mem_buf* mb);

/**
 * mb_resize() - resize memory buffer
 * @mb: pointer to memory buffer - see &struct mem_buf
 * @sz: new size of memory buffer in bytes
 *
 * Successfully resizing @mb resets @pos to zero. @sz can not exceed capacity of
 * @mb.
 *
 * Return: true on success, false otherwise
 */
bool mb_resize(struct mem_buf* mb, size_t sz);

/**
 * mb_advance_pos() - advance memory buffer position by a given amount
 * @mb: pointer to memory buffer - see &struct mem_buf
 * @sz: number of bytes to advance buffer position by
 *
 * This routine can only advance @mb's position by a multiple of the alignment
 * specified by mb_init(). It rounds @sz up to meet the alignment requirement.
 * Note that as consequence return pointer is also always aligned.
 *
 * Return: pointer to current position in memory buffer if possible to advance
 *         position by @size, NULL otherwise
 */
void* mb_advance_pos(struct mem_buf* mb, size_t sz);

__END_CDECLS

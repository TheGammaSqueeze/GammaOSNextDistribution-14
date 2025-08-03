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
#include <stdint.h>
#include <trusty_ipc.h>

__BEGIN_CDECLS

/**
 * struct cov_shm - structure tracking shared memory
 * @memref: handle to shared memory region
 * @base:   base address of shared memory region if mapped, NULL otherwise
 * @len:    length of shared memory region if mapped, 0 otherwise
 */
struct cov_shm {
    handle_t memref;
    void* base;
    size_t len;
};

static bool inline cov_shm_is_mapped(struct cov_shm* shm) {
    return shm->base;
}

static void inline cov_shm_init(struct cov_shm* shm,
                            handle_t memref,
                            void* base,
                            size_t len) {
    shm->memref = memref;
    shm->base = base;
    shm->len = len;
}

/**
 * cov_shm_alloc() - allocate shared memory
 * @shm: pointer to &struct shm to be initialized
 * @len: amount of memory requested
 *
 * Return: 0 on success, negative error code on error
 */
int cov_shm_alloc(struct cov_shm* shm, size_t len);

/**
 * cov_shm_free() - free shared memory
 * @shm: pointer to &struct shm previously initialized with shm_alloc()
 */
void cov_shm_free(struct cov_shm* shm);

/**
 * cov_shm_mmap() - map shared memory region
 * @shm:    pointer to &struct shm to be initialized
 * @memref: handle to memory to be mapped
 * @len:    length of memory region referenced by @memref
 *
 * Return: 0 on success, negative error code on error
 */
int cov_shm_mmap(struct cov_shm* shm, handle_t memref, size_t len);

/**
 * cov_shm_munmap() - unmap shared memory
 * @shm: pointer to &struct shm previously initialized with shm_mmap()
 */
void cov_shm_munmap(struct cov_shm* shm);

/**
 * cov_shm_munmap() - zero out contents of shared memory
 * @shm: pointer to &struct shm
 */
void cov_shm_clear(struct cov_shm* shm);

__END_CDECLS

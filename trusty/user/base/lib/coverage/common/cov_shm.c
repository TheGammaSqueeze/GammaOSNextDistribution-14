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

#define TLOG_TAG "coverage-common-shm"

#include <assert.h>
#include <lib/coverage/common/cov_shm.h>
#include <lk/macros.h>
#include <stdlib.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <trusty/memref.h>
#include <trusty_log.h>
#include <uapi/err.h>

#define PAGE_SIZE getauxval(AT_PAGESZ)
#define MMAP_FLAG_PROT_RW (MMAP_FLAG_PROT_READ | MMAP_FLAG_PROT_WRITE)

int cov_shm_alloc(struct cov_shm* shm, size_t len) {
    int rc;
    void* base;
    size_t shm_len;

    shm_len = round_up(len, PAGE_SIZE);
    base = memalign(PAGE_SIZE, shm_len);
    if (!base) {
        TLOGE("failed to allocate %zu bytes of shared memory\n", shm_len);
        return ERR_NO_MEMORY;
    }

    rc = memref_create(base, shm_len, MMAP_FLAG_PROT_RW);
    if (rc < 0) {
        TLOGE("failed (%d) to create memref\n", rc);
        free(shm);
        return rc;
    }

    shm->memref = (handle_t)rc;
    shm->base = base;
    shm->len = shm_len;

    cov_shm_clear(shm);
    return NO_ERROR;
}

void cov_shm_free(struct cov_shm* shm) {
    /*
     * TODO: HACK: No way to safely deallocate memory that has already been
     * shared, but works in practice.
     */
    free(shm->base);
    close(shm->memref);
    shm->memref = INVALID_IPC_HANDLE;
    shm->base = NULL;
    shm->len = 0;
}

int cov_shm_mmap(struct cov_shm* shm, handle_t memref, size_t len) {
    void* base = mmap(0, len, MMAP_FLAG_PROT_RW, 0, memref, 0);
    if (base == MAP_FAILED) {
        TLOGE("failed to mmap() shared memory\n");
        return ERR_BAD_HANDLE;
    }

    shm->memref = memref;
    shm->base = base;
    shm->len = len;
    return NO_ERROR;
}

void cov_shm_munmap(struct cov_shm* shm) {
    assert(cov_shm_is_mapped(shm));

    munmap(shm->base, shm->len);
    close(shm->memref);
    shm->memref = INVALID_IPC_HANDLE;
    shm->base = NULL;
    shm->len = 0;
}

void cov_shm_clear(struct cov_shm* shm) {
    assert(cov_shm_is_mapped(shm));

    for (size_t i = 0; i < shm->len; i++) {
        WRITE_ONCE(*((uint8_t*)(shm->base) + i), 0);
    }
}

/*
 * Copyright (c) 2013, Google, Inc. All rights reserved
 * Copyright (c) 2013, NVIDIA CORPORATION. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <assert.h>
#include <debug.h>
#include <err.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <kernel/usercopy.h>
#include <lk/macros.h>
#include <stdlib.h>
#include <string.h>
#include <trace.h>
#include <uapi/mm.h>

#include <lib/trusty/memref.h>
#include <lib/trusty/sys_fd.h>
#include <lib/trusty/trusty_app.h>
#include <lib/trusty/uctx.h>
#include <lib/trusty/uio.h>
#include <platform.h>
#if LK_LIBC_IMPLEMENTATION_IS_MUSL
#include <trusty/io_handle.h>
#endif

#include "util.h"

#define LOCAL_TRACE 0

static ssize_t sys_std_writev(uint32_t fd,
                              user_addr_t iov_uaddr,
                              uint32_t iov_cnt);

static mutex_t fd_lock = MUTEX_INITIAL_VALUE(fd_lock);

static const struct sys_fd_ops sys_std_fd_op = {
        .writev = sys_std_writev,
};

static struct sys_fd_ops const* sys_fds[MAX_SYS_FD_HADLERS] = {
        [1] = &sys_std_fd_op, /* stdout */
        [2] = &sys_std_fd_op, /* stderr */
};

status_t install_sys_fd_handler(uint32_t fd, const struct sys_fd_ops* ops) {
    status_t ret;

    if (fd >= countof(sys_fds))
        return ERR_INVALID_ARGS;

    mutex_acquire(&fd_lock);
    if (!sys_fds[fd]) {
        sys_fds[fd] = ops;
        ret = NO_ERROR;
    } else {
        ret = ERR_ALREADY_EXISTS;
    }
    mutex_release(&fd_lock);
    return ret;
}

static const struct sys_fd_ops* get_sys_fd_handler(uint32_t fd) {
    const struct sys_fd_ops* ops;

    ops = uctx_get_fd_ops(fd);
    if (ops)
        return ops;

    if (fd >= countof(sys_fds))
        return NULL;

    return sys_fds[fd];
}

static bool valid_address(vaddr_t addr, u_int size) {
    size = round_up(size + (addr & (PAGE_SIZE - 1)), PAGE_SIZE);
    addr = round_down(addr, PAGE_SIZE);

    while (size) {
        if (!is_user_address(addr) || !vaddr_to_paddr((void*)addr)) {
            return false;
        }
        addr += PAGE_SIZE;
        size -= PAGE_SIZE;
    }

    return true;
}

/* handle stdout/stderr */
static ssize_t sys_std_writev(uint32_t fd,
                              user_addr_t iov_uaddr,
                              uint32_t iov_cnt) {
    /*
     * Even if we're suppressing the output, we need to process the data to
     * produce the correct return code.
     */
    bool should_output = INFO <= LK_LOGLEVEL;
    io_handle_t* io_handle = fd_io_handle(fd);
    if (io_handle == NULL) {
        return ERR_BAD_HANDLE;
    }
    uint8_t buf[128];

    if (should_output) {
        io_lock(io_handle);
    }

    struct iovec_iter iter = iovec_iter_create(iov_cnt);
    size_t total_bytes = 0;
    int ret;

    while (iovec_iter_has_next(&iter)) {
        ret = user_iovec_to_membuf_iter(buf, sizeof(buf), iov_uaddr, &iter);
        if (ret < 0) {
            goto write_done;
        }
        total_bytes += ret;
        if (should_output) {
            ret = io_write(io_handle, (const void*)buf, ret);
            if (ret < 0) {
                goto write_done;
            }
        }
    }
    ret = total_bytes;

write_done:
    if (should_output) {
        io_write_commit(io_handle);
        io_unlock(io_handle);
    }
    return ret;
}

long sys_writev(uint32_t fd, user_addr_t iov_uaddr, uint32_t iov_cnt) {
    const struct sys_fd_ops* ops = get_sys_fd_handler(fd);

    if (ops && ops->writev)
        return ops->writev(fd, iov_uaddr, iov_cnt);

    return ERR_NOT_SUPPORTED;
}

void* sys_brk(void* u_brk) {
    vaddr_t brk = (vaddr_t)u_brk;
    struct trusty_app* trusty_app = current_trusty_app();
    if (!brk)
        return (void*)trusty_app->cur_brk;
    /* check if this is the first sbrk */
    if (!trusty_app->used_brk) {
        uint vmm_flags = VMM_FLAG_QUOTA;
        status_t ret;
        size_t size = round_up(trusty_app->end_brk - trusty_app->start_brk,
                               PAGE_SIZE);
        vmm_flags |= VMM_FLAG_VALLOC_SPECIFIC;
        ret = vmm_alloc(
                trusty_app->aspace, "brk_heap", size,
                (void*)&trusty_app->start_brk, 0, vmm_flags,
                ARCH_MMU_FLAG_PERM_USER | ARCH_MMU_FLAG_PERM_NO_EXECUTE);
        if (ret) {
            TRACEF("sbrk heap allocation failed!\n");
            return (void*)trusty_app->cur_brk;
        }
        trusty_app->used_brk = true;
    }

    /* update brk, if within range */
    if ((brk >= trusty_app->start_brk) && (brk <= trusty_app->end_brk)) {
        trusty_app->cur_brk = brk;
    }
    return (void*)trusty_app->cur_brk;
}

long sys_exit_etc(int32_t status, uint32_t flags) {
    thread_t* current = get_current_thread();
    LTRACEF("exit called, thread %p, name %s\n", current, current->name);
    trusty_app_exit(status);
    return 0L;
}

long sys_readv(uint32_t fd, user_addr_t iov_uaddr, uint32_t iov_cnt) {
    const struct sys_fd_ops* ops = get_sys_fd_handler(fd);

    if (ops && ops->readv)
        return ops->readv(fd, iov_uaddr, iov_cnt);

    return ERR_NOT_SUPPORTED;
}

long sys_ioctl(uint32_t fd, uint32_t req, user_addr_t user_ptr) {
    const struct sys_fd_ops* ops = get_sys_fd_handler(fd);

    if (ops && ops->ioctl)
        return ops->ioctl(fd, req, user_ptr);

    return ERR_NOT_SUPPORTED;
}

#if IS_64BIT && USER_32BIT
long sys_nanosleep(uint32_t clock_id,
                   uint32_t flags,
                   uint32_t sleep_time_l,
                   uint32_t sleep_time_h) {
    uint64_t sleep_time = sleep_time_l + ((uint64_t)sleep_time_h << 32);
    thread_sleep_ns(sleep_time);

    return NO_ERROR;
}
#else
long sys_nanosleep(uint32_t clock_id, uint32_t flags, uint64_t sleep_time) {
    thread_sleep_ns(sleep_time);

    return NO_ERROR;
}
#endif

long sys_gettime(uint32_t clock_id, uint32_t flags, user_addr_t time) {
    // return time in nanoseconds
    lk_time_ns_t t = current_time_ns();

    return copy_to_user(time, &t, sizeof(int64_t));
}

long sys_mmap(user_addr_t uaddr,
              uint32_t size,
              uint32_t flags,
              uint32_t handle_id) {
    struct trusty_app* trusty_app = current_trusty_app();
    long ret;

    if (flags & MMAP_FLAG_IO_HANDLE) {
        /*
         * Only allows mapping on IO region specified by handle (id) and uaddr
         * must be 0 for now.
         * TBD: Add support in to use uaddr as a hint.
         */
        if (uaddr != 0 || flags & MMAP_FLAG_ANONYMOUS) {
            return ERR_INVALID_ARGS;
        }

        ret = trusty_app_setup_mmio(trusty_app, handle_id, &uaddr, size);
        if (ret != NO_ERROR) {
            return ret;
        }

        return uaddr;
    } else if (flags & MMAP_FLAG_ANONYMOUS) {
        /*
         * Same as above, uaddr must be 0 for now.
         * TBD: Add support to use addr as a hint.
         */
        if (uaddr != 0 && !(flags & MMAP_FLAG_FIXED_NOREPLACE)) {
            return ERR_INVALID_ARGS;
        }

        uint32_t mmu_flags = 0;
        ret = xlat_flags(flags, flags, &mmu_flags);
        if (ret != NO_ERROR) {
            LTRACEF("error translating memory protection flags in mmap\n");
            return ret;
        }

        vaddr_t vaddr = uaddr;
        void* ptr = (void*)vaddr;
        uint vmm_flags = VMM_FLAG_QUOTA;
        if (flags & MMAP_FLAG_FIXED_NOREPLACE) {
            if (!uaddr) {
                LTRACEF("a fixed allocation requires a non-NULL pointer\n");
                return ERR_INVALID_ARGS;
            }
            vmm_flags |= VMM_FLAG_VALLOC_SPECIFIC;
        }
        if (flags & MMAP_FLAG_NO_PHYSICAL) {
            if (!(flags & MMAP_FLAG_PROT_WRITE)) {
                LTRACEF("a NO_PHYSICAL allocation must allow write access\n");
                return ERR_INVALID_ARGS;
            }
            vmm_flags |= VMM_FLAG_NO_PHYSICAL;
            if (uaddr) {
                LTRACEF("a NO_PHYSICAL allocation cannot be specific\n");
                return ERR_INVALID_ARGS;
            }
        }
        ret = vmm_alloc(trusty_app->aspace, "mmap", size, &ptr, 0, vmm_flags,
                        mmu_flags);
        if (ret != NO_ERROR) {
            LTRACEF("error mapping anonymous region\n");
            return ret;
        }

        return (long)ptr;
    } else {
        struct handle* handle;
        ret = uctx_handle_get(current_uctx(), handle_id, &handle);
        if (ret != NO_ERROR) {
            LTRACEF("mmapped nonexistent handle\n");
            return ret;
        }

        ret = handle_mmap(handle, 0, size, flags, &uaddr);
        handle_decref(handle);
        if (ret != NO_ERROR) {
            LTRACEF("handle_mmap failed\n");
            return ret;
        }

        return uaddr;
    }
}

long sys_munmap(user_addr_t uaddr, uint32_t size) {
    struct trusty_app* trusty_app = current_trusty_app();

    /*
     * vmm_free_region always unmaps whole region.
     * TBD: Add support to unmap partial region when there's use case.
     */
    return vmm_free_region_etc(trusty_app->aspace, uaddr, size, 0);
}

long sys_prepare_dma(user_addr_t uaddr,
                     uint32_t size,
                     uint32_t flags,
                     user_addr_t pmem) {
    struct dma_pmem kpmem;
    size_t mapped_size = 0;
    uint32_t entries = 0;
    long ret;
    vaddr_t vaddr = uaddr;

    LTRACEF("uaddr 0x%" PRIxPTR_USER
            ", size 0x%x, flags 0x%x, pmem 0x%" PRIxPTR_USER "\n",
            uaddr, size, flags, pmem);

    if (size == 0)
        return ERR_INVALID_ARGS;

    struct trusty_app* trusty_app = current_trusty_app();
    struct vmm_obj_slice slice;
    vmm_obj_slice_init(&slice);

    ret = vmm_get_obj(trusty_app->aspace, vaddr, size, &slice);
    if (ret != NO_ERROR)
        return ret;

    if (!slice.obj || !slice.obj->ops) {
        ret = ERR_NOT_VALID;
        goto err;
    }

    do {
        paddr_t paddr;
        size_t paddr_size;
        ret = slice.obj->ops->get_page(slice.obj, slice.offset + mapped_size,
                                       &paddr, &paddr_size);
        if (ret != NO_ERROR)
            goto err;

        memset(&kpmem, 0, sizeof(kpmem));
        kpmem.paddr = paddr;
        kpmem.size = MIN(size - mapped_size, paddr_size);

        /*
         * Here, kpmem.size is either the remaining mapping size
         * (size - mapping_size)
         * or the distance to a page boundary that is not physically
         * contiguous with the next page mapped in the given virtual
         * address range.
         * In either case it marks the end of the current kpmem record.
         */

        ret = copy_to_user(pmem, &kpmem, sizeof(struct dma_pmem));
        if (ret != NO_ERROR)
            goto err;

        pmem += sizeof(struct dma_pmem);

        mapped_size += kpmem.size;
        entries++;

    } while (mapped_size < size && (flags & DMA_FLAG_MULTI_PMEM));

    if (flags & DMA_FLAG_FROM_DEVICE)
        arch_clean_invalidate_cache_range(vaddr, mapped_size);
    else
        arch_clean_cache_range(vaddr, mapped_size);

    if (!(flags & DMA_FLAG_ALLOW_PARTIAL) && mapped_size != size) {
        ret = ERR_BAD_LEN;
        goto err;
    }

    ret = trusty_app_allow_dma_range(trusty_app, slice.obj, slice.offset,
                                     slice.size, vaddr, flags);
    if (ret != NO_ERROR) {
        goto err;
    }

    ret = entries; /* fallthrough */
err:
    vmm_obj_slice_release(&slice);
    return ret;
}

long sys_finish_dma(user_addr_t uaddr, uint32_t size, uint32_t flags) {
    LTRACEF("uaddr 0x%" PRIxPTR_USER ", size 0x%x, flags 0x%x\n", uaddr, size,
            flags);

    /* check buffer is in task's address space */
    if (!valid_address((vaddr_t)uaddr, size))
        return ERR_INVALID_ARGS;

    if (flags & DMA_FLAG_FROM_DEVICE)
        arch_clean_invalidate_cache_range(uaddr, size);

    /*
     * Check that app prepared dma on the provided virtual address range.
     * Returns ERR_NOT_FOUND if the range wasn't found. One way this can
     * happen is when an app finishes a dma range that it didn't prepare.
     */
    return trusty_app_destroy_dma_range((vaddr_t)uaddr, size);
}

long sys_set_user_tls(user_addr_t uaddr) {
    arch_set_user_tls(uaddr);
    return NO_ERROR;
}

long sys_memref_create(user_addr_t uaddr,
                       user_size_t size,
                       uint32_t mmap_prot) {
    struct trusty_app* app = current_trusty_app();
    struct handle* handle;
    handle_id_t id;
    status_t rc = memref_create_from_aspace(app->aspace, uaddr, size, mmap_prot,
                                            &handle);
    if (rc) {
        LTRACEF("failed to create memref\n");
        return rc;
    }

    int rc_uctx = uctx_handle_install(current_uctx(), handle, &id);
    /*
     * uctx_handle_install takes a reference to the handle, so we release
     * ours now. If it failed, this will release it. If it succeeded, this
     * prevents us from leaking when the application is destroyed.
     */
    handle_decref(handle);
    if (rc_uctx) {
        LTRACEF("failed to install handle\n");
        return rc_uctx;
    }

    LTRACEF("memref created: %d\n", id);
    return id;
}

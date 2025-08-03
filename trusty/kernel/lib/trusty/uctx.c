/*
 * Copyright (c) 2013-2018, Google, Inc. All rights reserved
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

#define LOCAL_TRACE 0

#include <assert.h>
#include <bits.h>
#include <err.h>
#include <kernel/usercopy.h>
#include <list.h>  // for containerof
#include <platform.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>

#include <kernel/event.h>
#include <kernel/thread.h>
#include <kernel/wait.h>

#include <lib/syscall.h>

#if WITH_TRUSTY_IPC

#include <lib/trusty/handle.h>
#include <lib/trusty/handle_set.h>
#include <lib/trusty/trusty_app.h>
#include <lib/trusty/uctx.h>
#include <lk/init.h>

#if !defined(IPC_MAX_HANDLES) || ((IPC_MAX_HANDLES + 0) == 0)
#error "IPC_MAX_HANDLES was not defined. Please check the project build definitions."
#endif

#define IPC_HANDLE_ID_BASE 1000

struct htbl_entry {
    struct handle* handle;
    struct list_node ref_list;
};

struct uctx {
    unsigned long inuse[BITMAP_NUM_WORDS(IPC_MAX_HANDLES)];
    struct htbl_entry htbl[IPC_MAX_HANDLES];

    void* priv;
    struct mutex mlock;

#if WITH_WAIT_ANY_SUPPORT
    struct handle* hset_all;
#endif

    handle_id_t handle_id_base;
};

static status_t _uctx_startup(struct trusty_app* app);
static status_t _uctx_shutdown(struct trusty_app* app);

static uint _uctx_slot_id;
static struct trusty_app_notifier _uctx_notifier = {
        .startup = _uctx_startup,
        .shutdown = _uctx_shutdown,
};

static status_t _uctx_startup(struct trusty_app* app) {
    struct uctx* uctx;

    int err = uctx_create(app, &uctx);
    if (err)
        return err;

    trusty_als_set(app, _uctx_slot_id, uctx);
    return NO_ERROR;
}

static status_t _uctx_shutdown(struct trusty_app* app) {
    LTRACEF("Destroying uctx for app: %d, %s\n", app->app_id,
            app->props.app_name);
    struct uctx* uctx;
    uctx = trusty_als_get(app, _uctx_slot_id);

    if (uctx)
        uctx_destroy(uctx);

    return NO_ERROR;
}

static void uctx_init(uint level) {
    int res;

    /* allocate als slot */
    res = trusty_als_alloc_slot();
    if (res < 0)
        panic("failed (%d) to alloc als slot\n", res);
    _uctx_slot_id = res;

    /* register notifier */
    res = trusty_register_app_notifier(&_uctx_notifier);
    if (res < 0)
        panic("failed (%d) to register uctx notifier\n", res);
}

LK_INIT_HOOK(uctx, uctx_init, LK_INIT_LEVEL_APPS - 2);

/*
 *  Get uctx context of the current app
 */
struct uctx* current_uctx(void) {
    struct trusty_app* tapp = current_trusty_app();
    return trusty_als_get(tapp, _uctx_slot_id);
}

/*
 *  Check if specified handle_id does represent a valid handle
 *  for specified user context.
 *
 *  On success return index of the handle in handle table,
 *  negative error otherwise
 */
static int _check_handle_id(struct uctx* ctx, handle_id_t handle_id) {
    uint32_t idx;

    DEBUG_ASSERT(ctx);

    /*
     * This check is technically not required because the wrapping of the
     * arithemtic combined with the bounds check will detect this case.
     * However, this makes it more obviously correct, guards against someone
     * making idx signed or IPC_MAX_HANDLES very high, and makes us pass
     * UBSan.
     */
    if (unlikely(handle_id < ctx->handle_id_base)) {
        LTRACEF("%d is below handle base\n", handle_id);
        return ERR_BAD_HANDLE;
    }

    idx = handle_id - ctx->handle_id_base;
    if (unlikely(idx >= IPC_MAX_HANDLES)) {
        LTRACEF("%d is invalid handle id\n", handle_id);
        return ERR_BAD_HANDLE;
    }

    if (!bitmap_test(ctx->inuse, idx)) {
        LTRACEF("%d is unused handle id\n", handle_id);
        return ERR_NOT_FOUND;
    }

    /* there should be a handle there */
    ASSERT(&ctx->htbl[idx].handle);

    return idx;
}

static struct handle* remove_handle(struct uctx* ctx, int idx) {
    struct handle* h;
    struct handle_ref* ref;
    struct handle_ref* tmp;

    h = ctx->htbl[idx].handle;
    if (!h)
        return NULL;

    /* clear all references */
    list_for_every_entry_safe(&ctx->htbl[idx].ref_list, ref, tmp,
                              struct handle_ref, uctx_node) {
        list_delete(&ref->uctx_node);
        if (list_in_list(&ref->set_node))
            handle_set_detach_ref(ref);
        handle_decref(ref->handle);
        free(ref);
    }

    ctx->htbl[idx].handle = NULL;
    bitmap_clear(ctx->inuse, idx);

    return h;
}

/*
 *  Create new handle ref and add it to specified handle set.
 *  Add resulting ref to head of specified list.
 */
static int _hset_add_handle(struct handle* hset,
                            struct handle* h,
                            uint32_t id,
                            uint32_t emask,
                            void* cookie,
                            struct list_node* ref_list) {
    int ret;
    struct handle_ref* ref;

    ASSERT(h);
    ASSERT(hset);
    ASSERT(ref_list);

    ref = calloc(1, sizeof(*ref));
    if (!ref)
        return ERR_NO_MEMORY;

    handle_incref(h);
    ref->handle = h;
    ref->emask = emask;
    ref->cookie = cookie;
    ref->id = id;

    ret = handle_set_attach(hset, ref);
    if (ret == NO_ERROR) {
        list_add_head(ref_list, &ref->uctx_node);
    } else {
        handle_decref(ref->handle);
        free(ref);
    }

    return ret;
}

#if WITH_WAIT_ANY_SUPPORT
/*
 *  Rebuild handle set containing all handles
 */
static int rebuild_hset_all(struct uctx* ctx) {
    int ret;
    int idx;
    struct handle* h;
    struct handle_ref* ref;
    struct handle* hset_all;

    mutex_acquire(&ctx->mlock);

    if (ctx->hset_all) {
        ret = NO_ERROR; /* Already exists. This is not an error */
        goto err_already_exists;
    }

    LTRACEF("Rebuilding all handles set\n");

    /* create all handle set */
    hset_all = handle_set_create();
    if (!hset_all) {
        LTRACEF("Out of memory\n");
        ret = ERR_NO_MEMORY;
        goto err_create;
    }

    for (idx = 0; idx < (int)countof(ctx->htbl); idx++) {
        h = ctx->htbl[idx].handle;
        if (!h || !h->ops->poll)
            continue; /* skip NULL and non-pollable handles */

        ret = _hset_add_handle(hset_all, h, ctx->handle_id_base + idx, ~0U,
                               NULL, &ctx->htbl[idx].ref_list);
        if (ret != NO_ERROR) {
            LTRACEF("Failed (%d) to add handle\n", ret);
            goto err_add_handle;
        }
    }
    ctx->hset_all = hset_all;
    mutex_release(&ctx->mlock);

    return NO_ERROR;

err_add_handle:
    for (idx--; idx >= 0; idx--) {
        h = ctx->htbl[idx].handle;
        if (!h || !h->ops->poll)
            continue; /* skip NULL and non-pollable handles */

        ref = list_remove_head_type(&ctx->htbl[idx].ref_list, struct handle_ref,
                                    uctx_node);
        ASSERT(ref && ref->parent == hset_all);
        handle_set_detach_ref(ref);
        handle_decref(ref->handle);
        free(ref);
    }
    handle_decref(hset_all);
err_create:
err_already_exists:
    mutex_release(&ctx->mlock);

    return ret;
}
#endif

/*
 *  Allocate and initialize user context - the structure that is used
 *  to keep track handles on behalf of user space app. Exactly one user
 *  context is created for each trusty app during it's nitialization.
 */
int uctx_create(void* priv, struct uctx** ctx) {
    struct uctx* new_ctx;

    DEBUG_ASSERT(ctx);

    new_ctx = calloc(1, sizeof(struct uctx));
    if (!new_ctx) {
        LTRACEF("Out of memory\n");
        return ERR_NO_MEMORY;
    }

    new_ctx->priv = priv;
    new_ctx->handle_id_base = IPC_HANDLE_ID_BASE;
    mutex_init(&new_ctx->mlock);

    for (uint i = 0; i < countof(new_ctx->htbl); i++)
        list_initialize(&new_ctx->htbl[i].ref_list);

    *ctx = new_ctx;

    return NO_ERROR;
}

/*
 *   Destroy user context previously created by uctx_create.
 */
void uctx_destroy(struct uctx* ctx) {
    int i;
    DEBUG_ASSERT(ctx);

    for (i = 0; i < IPC_MAX_HANDLES; i++) {
        struct handle* h = remove_handle(ctx, i);
        if (h)
            handle_close(h);
    }

#if WITH_WAIT_ANY_SUPPORT
    /* kill hset_all */
    if (ctx->hset_all)
        handle_decref(ctx->hset_all);
#endif

    free(ctx);
}

/*
 *  Returns private data associated with user context. (Currently unused)
 */
void* uctx_get_priv(struct uctx* ctx) {
    ASSERT(ctx);
    return ctx->priv;
}

/*
 * Install specified handle into user handle table and increment installed
 * handle ref count accordinly.
 */
int uctx_handle_install(struct uctx* ctx,
                        struct handle* handle,
                        handle_id_t* id) {
    int ret;
    int idx;

    DEBUG_ASSERT(ctx);
    DEBUG_ASSERT(handle);
    DEBUG_ASSERT(id);

    mutex_acquire(&ctx->mlock);
    idx = bitmap_ffz(ctx->inuse, IPC_MAX_HANDLES);

    if (idx < 0) {
        ret = ERR_NO_RESOURCES;
        goto err;
    }

    /* handle should be NULL and list should be empty */
    ASSERT(!ctx->htbl[idx].handle);
    ASSERT(list_is_empty(&ctx->htbl[idx].ref_list));

#if WITH_WAIT_ANY_SUPPORT
    /* if hset_all exists autoadd pollable handle */
    if (ctx->hset_all && handle->ops->poll) {
        ret = _hset_add_handle(ctx->hset_all, handle, ctx->handle_id_base + idx,
                               ~0U, NULL, &ctx->htbl[idx].ref_list);
        if (ret)
            goto err;
    }
#endif

    handle_incref(handle);
    ctx->htbl[idx].handle = handle;
    bitmap_set(ctx->inuse, idx);
    *id = ctx->handle_id_base + idx;
    ret = NO_ERROR;

err:
    mutex_release(&ctx->mlock);
    return ret;
}

static int uctx_handle_get_tmp_ref(struct uctx* ctx,
                                   handle_id_t handle_id,
                                   struct handle_ref* out) {
    int ret;

    DEBUG_ASSERT(ctx);
    DEBUG_ASSERT(out);

    mutex_acquire(&ctx->mlock);
    ret = _check_handle_id(ctx, handle_id);
    if (ret >= 0) {
        /* take a reference on the handle we looked up */
        struct handle* h = ctx->htbl[ret].handle;
        handle_incref(h);
        out->handle = h;
        out->id = handle_id;
        out->emask = ~0U;
        out->cookie = NULL;

#if WITH_WAIT_ANY_SUPPORT
        if (ctx->hset_all && h->ops->poll) {
            struct handle_ref* ref = list_peek_head_type(
                    &ctx->htbl[ret].ref_list, struct handle_ref, uctx_node);
            out->cookie = ref->cookie;
        }
#endif
        ret = NO_ERROR;
    }
    mutex_release(&ctx->mlock);
    return ret;
}

/*
 *   Retrieve handle from specified user context specified by
 *   given handle_id. Increment ref count for returned handle.
 */
int uctx_handle_get(struct uctx* ctx,
                    handle_id_t handle_id,
                    struct handle** handle_ptr) {
    struct handle_ref tmp_ref;

    DEBUG_ASSERT(ctx);
    DEBUG_ASSERT(handle_ptr);

    int ret = uctx_handle_get_tmp_ref(ctx, handle_id, &tmp_ref);
    if (ret == NO_ERROR) {
        *handle_ptr = tmp_ref.handle;
    }

    return ret;
}

/*
 *  Remove handle specified by handle ID from given user context and
 *  return it to caller if requested. In later case the caller becomes an owner
 *  of that handle.
 */
int uctx_handle_remove(struct uctx* ctx,
                       handle_id_t handle_id,
                       struct handle** handle_ptr) {
    int ret;
    struct handle* handle;

    DEBUG_ASSERT(ctx);

    mutex_acquire(&ctx->mlock);
    ret = _check_handle_id(ctx, handle_id);
    if (ret >= 0)
        handle = remove_handle(ctx, ret);
    mutex_release(&ctx->mlock);
    if (ret < 0)
        return ret;

    ASSERT(handle);
    if (handle_ptr) {
        handle_incref(handle);
        *handle_ptr = handle;
    }
    handle_decref(handle);

    return NO_ERROR;
}

/******************************************************************************/

/* definition shared with userspace */
struct uevent {
    uint32_t handle;
    uint32_t event;
    user_addr_t cookie;
};

static int _wait_for_uevent(const struct handle_ref* target,
                            user_addr_t user_event,
                            unsigned long timeout_msecs) {
    struct uevent uevent;
    struct handle_ref result;

    int ret = handle_ref_wait(target, &result, timeout_msecs);
    if (ret >= 0) {
        DEBUG_ASSERT(result.handle); /* there should be a handle */

        /* got an event */
        memset(&uevent, 0, sizeof(uevent));
        uevent.handle = result.id;
        uevent.event = result.emask;
        uevent.cookie = (user_addr_t)(uintptr_t)result.cookie;

        ret = copy_to_user(user_event, &uevent, sizeof(uevent));
        handle_decref(result.handle); /* drop ref taken by wait */
    }

    LTRACEF("[%p][%d]: ret = %d\n", current_trusty_thread(), result.id, ret);
    return ret;
}

/*
 *   wait on single handle specified by handle id
 */
long __SYSCALL sys_wait(uint32_t handle_id,
                        user_addr_t user_event,
                        uint32_t timeout_msecs) {
    int ret;
    struct handle_ref target;
    struct uctx* ctx = current_uctx();

    LTRACEF("[%p][%d]: %d msec\n", current_trusty_thread(), handle_id,
            timeout_msecs);

    ret = uctx_handle_get_tmp_ref(ctx, handle_id, &target);
    if (ret != NO_ERROR)
        return ret;

    ASSERT(target.handle);
    ASSERT(target.id == handle_id);

    ret = _wait_for_uevent(&target, user_event, timeout_msecs);

    /* drop handle_ref grabbed by uctx_handle_get */
    handle_decref(target.handle);
    return ret;
}

/*
 *   Wait on any handle existing in user context.
 */
long __SYSCALL sys_wait_any(user_addr_t user_event, uint32_t timeout_msecs) {
#if WITH_WAIT_ANY_SUPPORT
    int ret;
    struct handle_ref target = {0};
    struct uctx* ctx = current_uctx();

    LTRACEF("[%p]: %d msec\n", current_trusty_thread(), timeout_msecs);

    if (!ctx->hset_all) {
        ret = rebuild_hset_all(ctx);
        if (ret != NO_ERROR)
            return ret;
    }

    handle_incref(ctx->hset_all);
    target.handle = ctx->hset_all;

    ret = _wait_for_uevent(&target, user_event, timeout_msecs);
    handle_decref(target.handle); /* drop ref grabed above */
    return ret;
#else
    return (long)ERR_NOT_SUPPORTED;
#endif
}

long __SYSCALL sys_dup(uint32_t old_handle_id) {
    int rc;
    struct handle* h;
    struct uctx* ctx = current_uctx();
    handle_id_t new_handle_id;

    LTRACEF("[%p][%d]\n", current_trusty_thread(), old_handle_id);

    rc = uctx_handle_get(ctx, (handle_id_t)old_handle_id, &h);
    if (rc != NO_ERROR) {
        goto err_get;
    }

    rc = uctx_handle_install(ctx, h, &new_handle_id);
    if (rc != NO_ERROR) {
        goto err_install;
    }

    /* Drop ref grabbed by uctx_handle_get */
    handle_decref(h);
    return (long)new_handle_id;

err_install:
    handle_decref(h);
err_get:
    return rc;
}

long __SYSCALL sys_close(uint32_t handle_id) {
    struct handle* handle;

    LTRACEF("[%p][%d]\n", current_trusty_thread(), handle_id);

    int ret = uctx_handle_remove(current_uctx(), handle_id, &handle);
    if (ret != NO_ERROR)
        return ret;

    handle_close(handle);
    return NO_ERROR;
}

long __SYSCALL sys_set_cookie(uint32_t handle_id, user_addr_t cookie) {
#if WITH_WAIT_ANY_SUPPORT
    int ret;
    struct uctx* ctx = current_uctx();

    LTRACEF("[%p][%d]: cookie = 0x%08x\n", current_trusty_thread(), handle_id,
            (uint)cookie);

    if (!ctx->hset_all) {
        ret = rebuild_hset_all(ctx);
        if (ret != NO_ERROR)
            return ret;
    }

    mutex_acquire(&ctx->mlock);
    ret = _check_handle_id(ctx, handle_id);
    if (ret < 0)
        goto err;

    /* cookies are only relevant for pollable handles */
    if (!ctx->htbl[ret].handle->ops->poll) {
        /* not a pollable handle */
        ret = ERR_NOT_VALID;
        goto err;
    }

    struct handle_ref* ref = list_peek_head_type(&ctx->htbl[ret].ref_list,
                                                 struct handle_ref, uctx_node);
    ref->cookie = (void*)(uintptr_t)cookie;
    ret = NO_ERROR;

err:
    mutex_release(&ctx->mlock);
    return ret;
#else
    return (long)ERR_NOT_SUPPORTED;
#endif
}

/*****************************************************************************/

long __SYSCALL sys_handle_set_create(void) {
    int ret;
    handle_id_t id;
    struct handle* hset;
    struct uctx* ctx = current_uctx();

    hset = handle_set_create();
    if (!hset)
        return ERR_NO_MEMORY;

    /* install handle into user context */
    ret = uctx_handle_install(ctx, hset, &id);
    if (ret != NO_ERROR)
        goto err_install;

    handle_decref(hset);
    return (long)id;

err_install:
    handle_decref(hset);
    return ret;
}

static int _hset_add_item(struct handle* hset,
                          struct htbl_entry* item,
                          uint32_t id,
                          uint32_t emask,
                          void* cookie) {
    struct handle_ref* ref;

    /* find if we are inserting duplicate */
    list_for_every_entry(&item->ref_list, ref, struct handle_ref, uctx_node) {
        if (ref->parent == hset) {
            return ERR_ALREADY_EXISTS;
        }
    }

    /*
     * Note, we have to add at the end of the ref_list
     *
     * This is an artifact of how global cookies are handled in
     * presence of all handle set. When handle is added to all
     * handles set the corresponding handle_ref is added to the head
     * of the list so an implementation of set_cookie syscall just picks
     * first inte from the list and stores cookie there. An implementation
     * of set_cookie syscall also triggers rebuild of all handle set, so
     * it guaranties that entry is always present.
     */
    return _hset_add_handle(hset, item->handle, id, emask, cookie,
                            item->ref_list.prev);
}

static int _hset_del_item(struct handle* hset, struct htbl_entry* item) {
    uint del_cnt = 0;
    struct handle_ref* ref;
    struct handle_ref* tmp;

    list_for_every_entry_safe(&item->ref_list, ref, tmp, struct handle_ref,
                              uctx_node) {
        if (ref->parent == hset) {
            del_cnt++;
            LTRACEF("%p: %p\n", ref->parent, ref->handle);
            list_delete(&ref->uctx_node);
            handle_set_detach_ref(ref);
            handle_decref(ref->handle);
            free(ref);
        }
    }
    return del_cnt ? NO_ERROR : ERR_NOT_FOUND;
}

static int _hset_mod_item(struct handle* hset,
                          struct htbl_entry* item,
                          uint32_t emask,
                          void* cookie) {
    uint mod_cnt = 0;
    struct handle_ref* ref;
    struct handle_ref* tmp;

    list_for_every_entry_safe(&item->ref_list, ref, tmp, struct handle_ref,
                              uctx_node) {
        if (ref->parent == hset) {
            mod_cnt++;
            LTRACEF("%p: %p\n", ref->parent, ref->handle);
            handle_set_update_ref(ref, emask, cookie);
        }
    }
    return mod_cnt ? NO_ERROR : ERR_NOT_FOUND;
}

static int _hset_ctrl_locked(handle_id_t hset_id,
                             handle_id_t h_id,
                             uint32_t cmd,
                             uint32_t event,
                             void* cookie) {
    int ret;
    int h_idx, hset_idx;
    struct uctx* ctx = current_uctx();

    LTRACEF("%d: %d: cmd=%d\n", hset_id, h_id, cmd);

    hset_idx = _check_handle_id(ctx, hset_id);
    if (hset_idx < 0)
        return hset_idx;

    h_idx = _check_handle_id(ctx, h_id);
    if (h_idx < 0)
        return h_idx;

    switch (cmd) {
    case HSET_ADD:
        ret = _hset_add_item(ctx->htbl[hset_idx].handle, &ctx->htbl[h_idx],
                             h_id, event, cookie);
        break;

    case HSET_DEL:
        ret = _hset_del_item(ctx->htbl[hset_idx].handle, &ctx->htbl[h_idx]);
        break;

    case HSET_MOD:
        ret = _hset_mod_item(ctx->htbl[hset_idx].handle, &ctx->htbl[h_idx],
                             event, cookie);
        break;

    default:
        LTRACEF("Invalid hset command (%d)\n", cmd);
        ret = ERR_INVALID_ARGS;
    }

    return ret;
}

long __SYSCALL sys_handle_set_ctrl(handle_id_t hset_id,
                                   uint32_t cmd,
                                   user_addr_t user_event) {
    int ret = 0;
    struct uevent uevent;
    struct uctx* ctx = current_uctx();

    ret = copy_from_user(&uevent, user_event, sizeof(uevent));
    if (ret < 0)
        return ret;

    mutex_acquire(&ctx->mlock);
    ret = _hset_ctrl_locked(hset_id, uevent.handle, cmd, uevent.event,
                            (void*)(uintptr_t)uevent.cookie);
    mutex_release(&ctx->mlock);
    return ret;
}

static ssize_t uctx_handle_writev(uint32_t fd,
                                  user_addr_t iov_uaddr,
                                  uint32_t iov_cnt) {
    int rc;
    struct handle* h;
    struct uctx* ctx = current_uctx();

    rc = uctx_handle_get(ctx, (handle_id_t)fd, &h);
    if (rc != NO_ERROR)
        return rc;

    if (h->ops && h->ops->user_writev) {
        rc = h->ops->user_writev(h, iov_uaddr, iov_cnt);
    } else {
        rc = ERR_NOT_SUPPORTED;
    }

    handle_decref(h);
    return rc;
}

static ssize_t uctx_handle_readv(uint32_t fd,
                                 user_addr_t iov_uaddr,
                                 uint32_t iov_cnt) {
    int rc;
    struct handle* h;
    struct uctx* ctx = current_uctx();

    rc = uctx_handle_get(ctx, (handle_id_t)fd, &h);
    if (rc != NO_ERROR)
        return rc;

    if (h->ops && h->ops->user_readv) {
        rc = h->ops->user_readv(h, iov_uaddr, iov_cnt);
    } else {
        rc = ERR_NOT_SUPPORTED;
    }

    handle_decref(h);
    return rc;
}

static const struct sys_fd_ops fd_op = {
        .writev = uctx_handle_writev,
        .readv = uctx_handle_readv,
};

const struct sys_fd_ops* uctx_get_fd_ops(uint32_t fd) {
    if (fd >= IPC_HANDLE_ID_BASE &&
        fd < (IPC_HANDLE_ID_BASE + IPC_MAX_HANDLES)) {
        return &fd_op;
    }
    return NULL;
}

#else /* WITH_TRUSTY_IPC */

long __SYSCALL sys_wait(uint32_t handle_id,
                        user_addr_t user_event,
                        unsigned long timeout_msecs) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_wait_any(user_addr_t user_event,
                            unsigned long timeout_msecs) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_close(uint32_t handle_id) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_set_cookie(uint32_t handle_id, user_addr_t cookie) {
    return (long)ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_handle_set_create(void) {
    return ERR_NOT_SUPPORTED;
}

long __SYSCALL sys_handle_set_ctrl(handle_id_t hset_id,
                                   uint32_t cmd,
                                   user_addr_t user_event) {
    return ERR_NOT_SUPPORTED;
}

const struct sys_fd_ops* uctx_get_fd_ops(uint32_t fd) {
    return NULL;
}

#endif /* WITH_TRUSTY_IPC */

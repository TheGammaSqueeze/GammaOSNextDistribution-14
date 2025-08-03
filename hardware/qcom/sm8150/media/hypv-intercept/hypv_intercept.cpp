/*--------------------------------------------------------------------------
Copyright (c) 2017, 2019, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
--------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <fcntl.h>
#include <string.h>
#include <dlfcn.h>
#include <sys/ioctl.h>
#include <pthread.h>
#include <time.h>
#include <errno.h>
#include <unistd.h>
#include <sys/mman.h>
#include "hypv_intercept.h"
#ifdef _ANDROID_
#include <cutils/properties.h>
#endif

#define MAX_HVFE_HANDLE             32
#define HYPV_HANDLE_SIGNATURE       0x2bcd0000
#define HYPV_HANDLE_SIGNATURE_MASK  0xffff0000
#define HYPV_HANDLE_MASK            0x0000ffff
#define POLL_TIMEOUT                0xffffffff
#define MAX_EVENTS                  32

#define IS_HYPERVISOR_VIDEO_HANDLE(fd) ((fd & HYPV_HANDLE_SIGNATURE_MASK)==HYPV_HANDLE_SIGNATURE)
#define HYP_INITIALIZED  (g_hvfe_handle_count > 0)
#define NUM_PENDING_EVENTS(a, b) ((a > b) ? (a - b) : (b - a))

typedef void* HVFE_HANDLE;
typedef int (*hvfe_callback_handler_t)(void *context, void *message);

struct hypv_intercept {
    HVFE_HANDLE  handle;
    short event_flags[MAX_EVENTS];
    bool exit_flag;
    unsigned int event_q_front;
    unsigned int event_q_rear;
    pthread_t thread_id;
    pthread_cond_t cond;
    pthread_mutex_t lock;
};

struct hvfe_callback_t
{
    hvfe_callback_handler_t handler;
    void* context;
};

typedef void (*cb)(int flag);
typedef HVFE_HANDLE (*video_fe_open_func)(const char*, int, hvfe_callback_t*);
typedef int (*video_fe_ioctl_func)(HVFE_HANDLE, int, void*);
typedef int (*video_fe_close_func)(HVFE_HANDLE);

static void *hvfe_lib_handle = NULL;
static video_fe_open_func video_fe_open = NULL;
static video_fe_ioctl_func video_fe_ioctl = NULL;
static video_fe_close_func video_fe_close = NULL;
static pthread_mutex_t g_hvfe_handle_lock = PTHREAD_MUTEX_INITIALIZER;
static struct hypv_intercept g_hvfe_handle[MAX_HVFE_HANDLE];
static int g_hvfe_handle_count  = 0;
static int event_notify(void *context, void *messages);
int debug_level = 0x1;

static int add_handle_to_index(HVFE_HANDLE handle, int index)
{
    int rc = 0;

    memset(&g_hvfe_handle[index], 0, sizeof(struct hypv_intercept));
    if (pthread_mutex_init(&g_hvfe_handle[index].lock, NULL) != 0) {
        HYP_VIDEO_MSG_ERROR("error initializing pthread lock");
        rc = -1;
    } else if (pthread_cond_init(&g_hvfe_handle[index].cond, NULL) != 0) {
        HYP_VIDEO_MSG_ERROR("error initializing pthread cond");
        rc = -1;
    } else {
        g_hvfe_handle[index].handle = handle;
        g_hvfe_handle_count++;
    }

    return rc;
}

static int find_empty_handle_index(void)
{
    int rc = 0;

    if (g_hvfe_handle_count >= MAX_HVFE_HANDLE) {
        HYP_VIDEO_MSG_ERROR("reached max handle count. handle count %d",
                             g_hvfe_handle_count);
        rc = -1;
    } else {
        int i;

        for (i = 0; i < MAX_HVFE_HANDLE; i++) {
            if (g_hvfe_handle[i].handle == 0) {
                rc = i;
                break;
            }
        }

        if (i >= MAX_HVFE_HANDLE) {
            HYP_VIDEO_MSG_ERROR("failed to find empty slot");
            rc = -1;
        }
    }

    return rc;
}

static int hypv_init(void)
{
    int rc = 0;

    hvfe_lib_handle = dlopen("libhyp_video_fe.so", RTLD_NOW);
    if (hvfe_lib_handle == NULL) {
        HYP_VIDEO_MSG_ERROR("failed to open libhyp_video_fe");
        rc = -1;
    } else {
        video_fe_open = (video_fe_open_func)dlsym(hvfe_lib_handle, "video_fe_open");
        if (video_fe_open == NULL) {
            HYP_VIDEO_MSG_ERROR("failed to get video_fe_open handle");
            rc = -1;
        } else {
            video_fe_ioctl = (video_fe_ioctl_func)dlsym(hvfe_lib_handle, "video_fe_ioctl");
            if (video_fe_ioctl == NULL) {
                HYP_VIDEO_MSG_ERROR("failed to get video_fe_ioctl handle");
                rc = -1;
            } else {
                video_fe_close = (video_fe_close_func)dlsym(hvfe_lib_handle, "video_fe_close");
                if (video_fe_close == 0) {
                    HYP_VIDEO_MSG_ERROR("failed to get video_fe_close handle");
                    rc = -1;
                }//video_fe_close
            } //video_fe_ioctl
        } //video_fe_open
    } //hvfe_lib_handle

    if (rc < 0 && hvfe_lib_handle) {
        dlclose(hvfe_lib_handle);
        hvfe_lib_handle = NULL;
    }

    return rc;
}

static void hypv_deinit(void)
{
    dlclose(hvfe_lib_handle);
    hvfe_lib_handle = NULL;

    return;
}

int hypv_open(const char *str, int flag)
{
    int rc = 0;

#ifdef _LINUX_
    char *env_ptr = getenv("HYPV_DEBUG_LEVEL");
    debug_level = env_ptr ? atoi(env_ptr) : 0;
#elif defined _ANDROID_
    char property_value[PROPERTY_VALUE_MAX] = {0};

    property_get("vendor.hypv.debug.level", property_value, "1");
    debug_level = atoi(property_value);
#endif

    pthread_mutex_lock(&g_hvfe_handle_lock);

    if (!HYP_INITIALIZED) {
        if ((rc = hypv_init()) < 0) {
            HYP_VIDEO_MSG_ERROR("hypervisor init failed");
            pthread_mutex_unlock(&g_hvfe_handle_lock);
            return rc;
        }
    }

    int index = find_empty_handle_index();
    if (index < 0) {
        rc = -1;
    } else {
        struct hvfe_callback_t cb;

        cb.handler = event_notify;
        cb.context = &g_hvfe_handle[index];
        HVFE_HANDLE hvfe_handle = video_fe_open(str, flag, &cb);
        HYP_VIDEO_MSG_INFO("video fe open handle = %p", hvfe_handle);

        if (hvfe_handle == NULL) {
            HYP_VIDEO_MSG_ERROR("video fe open failed");
            rc = -1;
        } else {
            if (add_handle_to_index(hvfe_handle, index) < 0) {
                HYP_VIDEO_MSG_ERROR("failed to add hvfe handle");
                video_fe_close(hvfe_handle);
                rc = -1;
            } else {
                rc = (HYPV_HANDLE_SIGNATURE | index);
            }
        }
    }

    pthread_mutex_unlock(&g_hvfe_handle_lock);

    if (rc < 0)
        hypv_deinit();

    return rc;
}

int hypv_ioctl(int fd, int cmd, void *data)
{
    int rc = 0;

    if (!HYP_INITIALIZED) {
        HYP_VIDEO_MSG_ERROR("hypervisor not initialized");
        return -1;
    }

    if (IS_HYPERVISOR_VIDEO_HANDLE(fd)) {
        int fd_index = fd & HYPV_HANDLE_MASK;
        if (fd_index >= MAX_HVFE_HANDLE) {
            HYP_VIDEO_MSG_ERROR("invalid fd_index = %d", fd_index);
            rc = -1;
        } else {
            rc = video_fe_ioctl(g_hvfe_handle[fd_index].handle, cmd, data);
            HYP_VIDEO_MSG_INFO("fd %d, fd_index %d, cmd 0x%x, data 0x%p, rc %d",
                              fd, fd_index, cmd, data, rc);
        }
    } else {
        HYP_VIDEO_MSG_ERROR("native ioctl: fd %d, cmd 0x%x, data 0x%p",
                             fd, cmd, data);
        rc = ioctl(fd, cmd, data);
    }

    return rc;
}

static int event_notify(void *context, void *messages)
{
    struct hypv_intercept *handle = (struct hypv_intercept *)context;
    int flags = *(int *)messages;

    HYP_VIDEO_MSG_INFO("event flag 0x%x", flags);
    pthread_mutex_lock(&handle->lock);
    handle->event_flags[handle->event_q_rear++] = flags;
    handle->event_q_rear %= MAX_EVENTS;
    HYP_VIDEO_MSG_INFO("cond signal. num_pending_events %d event_q_front %d event_q_rear %d",
                        NUM_PENDING_EVENTS(handle->event_q_front, handle->event_q_rear),
                        handle->event_q_front, handle->event_q_rear);
    pthread_cond_signal(&handle->cond);
    pthread_mutex_unlock(&handle->lock);

    return 0;
}

static void* exit_thread(void *fds)
{
    struct pollfd *pfds = (struct pollfd *)fds;
    int fd_index = pfds[0].fd & HYPV_HANDLE_MASK;
    struct hypv_intercept *handle = &g_hvfe_handle[fd_index];
    struct pollfd exit_fd;

    HYP_VIDEO_MSG_INFO("exit thread created. fd = %d", fd_index);
    exit_fd.events = POLLIN | POLLERR;
    exit_fd.fd = pfds[1].fd;

    poll(&exit_fd, 1, POLL_TIMEOUT);

    if ((exit_fd.revents & POLLIN) || (exit_fd.revents & POLLERR)) {
       handle->exit_flag = true;
       pthread_cond_signal(&handle->cond);
    }

    return NULL;
}

int hypv_poll(struct pollfd *fds, nfds_t nfds, int timeout)
{
    struct timespec ts;
    int ret = 0;

    if (nfds == 0)
        return -1;

    if (!HYP_INITIALIZED) {
        HYP_VIDEO_MSG_ERROR("hypervisor not initialized");
        return -1;
    }

    if (IS_HYPERVISOR_VIDEO_HANDLE(fds[0].fd)) {
        int fd_index = fds[0].fd & HYPV_HANDLE_MASK;

        if (fd_index >= MAX_HVFE_HANDLE) {
            HYP_VIDEO_MSG_ERROR("invalid fd index %d", fd_index);
            ret = -1;
        } else {
            struct hypv_intercept *handle = &g_hvfe_handle[fd_index];

            clock_gettime(CLOCK_REALTIME, &ts);
            ts.tv_sec += timeout / 1000;
            ts.tv_nsec = 0;
            fds[1].revents = fds[0].revents = 0;

            if (handle->thread_id == 0) {
                if (pthread_create(&handle->thread_id, 0, exit_thread, fds)) {
                    handle->thread_id = 0;
                    return -1;
                }
            }

            pthread_mutex_lock(&handle->lock);
            if (!NUM_PENDING_EVENTS(handle->event_q_front, handle->event_q_rear) &&
                !handle->exit_flag) {
                ret = pthread_cond_timedwait(&handle->cond, &handle->lock, &ts);
            }
            else
            {
                HYP_VIDEO_MSG_INFO("hypv_poll: process pending flag");
            }

            if (ret == ETIMEDOUT) {
                HYP_VIDEO_MSG_INFO("hyp poll timeout");
                ret = 0;
            } else if (ret == 0) {
                if (handle->exit_flag == true) {
                    HYP_VIDEO_MSG_INFO("hyp poll exit");
                    fds[1].revents = POLLIN;
                    handle->exit_flag = false;
                    handle->thread_id = 0;
                } else {
                    fds[0].revents = handle->event_flags[handle->event_q_front++];
                    handle->event_q_front %= MAX_EVENTS;
                    HYP_VIDEO_MSG_INFO("hyp poll fd %d events 0x%x pending events %d",
                                        fds[0].fd, fds[0].revents,
                                        NUM_PENDING_EVENTS(handle->event_q_front, handle->event_q_rear));
                }
                ret = 1;
            }

            pthread_mutex_unlock(&handle->lock);
        }
    } else {
        HYP_VIDEO_MSG_ERROR("unknown fd = %d", fds[0].fd);
    }

    return ret;
}

int hypv_close(int fd)
{
    int rc = 0;

    if (!HYP_INITIALIZED) {
        HYP_VIDEO_MSG_ERROR("hypervisor not initialized");
        return -1;
    }

    if (IS_HYPERVISOR_VIDEO_HANDLE(fd)) {
        int fd_index = fd & HYPV_HANDLE_MASK;

        if ((fd_index >= MAX_HVFE_HANDLE) || (fd_index < 0)) {
            HYP_VIDEO_MSG_ERROR("invalid fd %d", fd_index);
            rc = -1;
        } else {
            pthread_mutex_lock(&g_hvfe_handle_lock);
            rc = video_fe_close(g_hvfe_handle[fd_index].handle);
            g_hvfe_handle[fd_index].handle = 0;
            pthread_cond_destroy(&g_hvfe_handle[fd_index].cond);
            pthread_mutex_destroy(&g_hvfe_handle[fd_index].lock);
            if (--g_hvfe_handle_count == 0)
                hypv_deinit();
            pthread_mutex_unlock(&g_hvfe_handle_lock);
        }
    } else {
        rc = close(fd);
    }

    return rc;
}
